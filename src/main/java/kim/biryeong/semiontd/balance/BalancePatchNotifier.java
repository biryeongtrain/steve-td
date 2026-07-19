package kim.biryeong.semiontd.balance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class BalancePatchNotifier implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(BalancePatchNotifier.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final URI DEFAULT_ENDPOINT =
            URI.create("https://api.github.com/repos/biryeongtrain/semiontd-balance/commits/main");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final long POLL_INTERVAL_MINUTES = 5;
    private static final int CHANGE_PREVIEW_LIMIT = 3;

    private final Path statePath;
    private final URI endpoint;
    private final HttpClient httpClient;
    private ScheduledExecutorService scheduler;
    private boolean stateLoaded;
    private String lastSeenSha;

    public BalancePatchNotifier(Path statePath) {
        this(statePath, DEFAULT_ENDPOINT);
    }

    BalancePatchNotifier(Path statePath, URI endpoint) {
        this.statePath = statePath;
        this.endpoint = endpoint;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public synchronized void start(MinecraftServer server) {
        if (scheduler != null) {
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "semion-td-balance-patch");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(
                () -> poll(server),
                0,
                POLL_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );
    }

    private void poll(MinecraftServer server) {
        checkForUpdate().ifPresent(patch ->
                server.execute(() -> broadcast(server, patch)));
    }

    Optional<BalancePatch> checkForUpdate() {
        try {
            loadState();
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "SemionTD")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() != 200) {
                LOGGER.warn("Balance patch check returned HTTP {}.", response.statusCode());
                return Optional.empty();
            }

            BalancePatch patch = parsePatch(response.body());
            if (patch.sha().equals(lastSeenSha)) {
                return Optional.empty();
            }

            String previousSha = lastSeenSha;
            if (!saveState(patch.sha())) {
                return Optional.empty();
            }
            lastSeenSha = patch.sha();
            return previousSha == null ? Optional.empty() : Optional.of(patch);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Balance patch check was interrupted.");
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Failed to check the latest balance patch: {}", exception.getMessage());
        }
        return Optional.empty();
    }

    private void loadState() {
        if (stateLoaded) {
            return;
        }
        stateLoaded = true;
        if (!Files.exists(statePath)) {
            return;
        }

        try {
            JsonObject state = JsonParser.parseString(Files.readString(statePath)).getAsJsonObject();
            if (state.has("lastSeenSha") && !state.get("lastSeenSha").isJsonNull()) {
                if (!state.get("lastSeenSha").isJsonPrimitive()
                        || !state.get("lastSeenSha").getAsJsonPrimitive().isString()) {
                    throw new JsonParseException("lastSeenSha must be a string.");
                }
                String storedSha = state.get("lastSeenSha").getAsString().trim();
                if (!storedSha.isEmpty()) {
                    lastSeenSha = storedSha;
                }
            }
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Failed to read balance notification state; the next successful check will initialize it: {}",
                    exception.getMessage());
        }
    }

    private boolean saveState(String sha) {
        Path temporaryPath = statePath.resolveSibling(statePath.getFileName() + ".tmp");
        JsonObject state = new JsonObject();
        state.addProperty("lastSeenSha", sha);

        try {
            Files.createDirectories(statePath.getParent());
            Files.writeString(temporaryPath, GSON.toJson(state) + System.lineSeparator());
            try {
                Files.move(
                        temporaryPath,
                        statePath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryPath, statePath, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException exception) {
            LOGGER.warn("Failed to save balance notification state: {}", exception.getMessage());
            return false;
        }
    }

    private static BalancePatch parsePatch(String responseBody) {
        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
        String sha = requiredString(root, "sha");
        URI commitUrl = URI.create(requiredString(root, "html_url"));
        if (!"https".equalsIgnoreCase(commitUrl.getScheme())) {
            throw new JsonParseException("Commit URL must use HTTPS.");
        }

        JsonObject commit = root.getAsJsonObject("commit");
        String message = "";
        if (commit != null && commit.has("message") && !commit.get("message").isJsonNull()) {
            if (!commit.get("message").isJsonPrimitive()
                    || !commit.get("message").getAsJsonPrimitive().isString()) {
                throw new JsonParseException("commit.message must be a string.");
            }
            message = commit.get("message").getAsString();
        }
        String[] lines = message.split("\\R", -1);
        String title = lines.length == 0 || lines[0].isBlank()
                ? "새 밸런스 패치"
                : lines[0].trim();
        List<String> changes = new ArrayList<>();
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.startsWith("- ") || line.startsWith("* ")) {
                String change = line.substring(2).trim();
                if (!change.isEmpty()) {
                    changes.add(change);
                }
            }
        }
        return new BalancePatch(sha, title, List.copyOf(changes), commitUrl);
    }

    private static String requiredString(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            throw new JsonParseException("Missing " + key + ".");
        }
        if (!object.get(key).isJsonPrimitive() || !object.get(key).getAsJsonPrimitive().isString()) {
            throw new JsonParseException(key + " must be a string.");
        }
        String value = object.get(key).getAsString().trim();
        if (value.isEmpty()) {
            throw new JsonParseException("Empty " + key + ".");
        }
        return value;
    }

    private static void broadcast(MinecraftServer server, BalancePatch patch) {
        Component header = SemionText.prefixed(
                Component.literal("밸런스 패치: ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(patch.title()).withStyle(ChatFormatting.YELLOW))
        );
        List<Component> lines = new ArrayList<>();
        lines.add(header);
        for (String change : patch.previewChanges()) {
            lines.add(Component.literal("  • " + change).withStyle(ChatFormatting.GRAY));
        }

        MutableComponent details = Component.literal("  ");
        if (patch.remainingChangeCount() > 0) {
            details.append(Component.literal("외 " + patch.remainingChangeCount() + "건  ")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        details.append(Component.literal("[GitHub에서 전체 내역 보기]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.OpenUrl(patch.commitUrl()))));
        lines.add(details);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (Component line : lines) {
                player.sendSystemMessage(line);
            }
        }
    }

    @Override
    public synchronized void close() {
        if (scheduler == null) {
            return;
        }
        scheduler.shutdownNow();
        scheduler = null;
    }

    record BalancePatch(String sha, String title, List<String> changes, URI commitUrl) {
        List<String> previewChanges() {
            return changes.subList(0, Math.min(CHANGE_PREVIEW_LIMIT, changes.size()));
        }

        int remainingChangeCount() {
            return Math.max(0, changes.size() - CHANGE_PREVIEW_LIMIT);
        }
    }
}
