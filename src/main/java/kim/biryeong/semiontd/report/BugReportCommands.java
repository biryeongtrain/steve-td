package kim.biryeong.semiontd.report;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.persistence.SQLiteBugReportRepository;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class BugReportCommands implements AutoCloseable {
    private final Path path;
    private final SemionGameManager games;
    private final Map<UUID, Long> nextReport = new HashMap<>();
    // ponytail: one bounded I/O worker; separate read/write pools only if report traffic warrants it.
    private final ThreadPoolExecutor io = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), Thread.ofPlatform().daemon().name("semion-bug-reports").factory());
    private SQLiteBugReportRepository repository;

    public BugReportCommands(Path path, SemionGameManager games) { this.path = path; this.games = games; }

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("버그신고").requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(context -> { message(context.getSource(), "사용법: /버그신고 <내용> · 신고 시 같은 경기의 타워 상태가 운영자에게 전달됩니다."); return 1; })
                .then(argument("내용", StringArgumentType.greedyString()).executes(context ->
                        submit(context.getSource(), context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "내용")))));
        dispatcher.register(literal("버그신고목록").requires(source -> source.hasPermission(2))
                .executes(context -> list(context.getSource(), 1))
                .then(argument("페이지", IntegerArgumentType.integer(1, 100_000)).executes(context ->
                        list(context.getSource(), IntegerArgumentType.getInteger(context, "페이지")))));
        dispatcher.register(literal("버그신고조회").requires(source -> source.hasPermission(2))
                .then(argument("ID", StringArgumentType.word())
                        .executes(context -> detail(context.getSource(), StringArgumentType.getString(context, "ID"), 1))
                        .then(argument("페이지", IntegerArgumentType.integer(1, 100_000)).executes(context ->
                                detail(context.getSource(), StringArgumentType.getString(context, "ID"), IntegerArgumentType.getInteger(context, "페이지"))))));
    }

    private int submit(CommandSourceStack source, ServerPlayer player, String input) {
        final String content;
        try { content = BugReport.validateContent(input); }
        catch (IllegalArgumentException exception) { source.sendFailure(Component.literal(exception.getMessage())); return 0; }
        long now = System.nanoTime();
        nextReport.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (nextReport.containsKey(player.getUUID())) {
            source.sendFailure(Component.literal("신고를 저장 중이거나 최근에 접수했습니다. 최대 60초 뒤 다시 시도해 주세요."));
            return 0;
        }
        nextReport.put(player.getUUID(), now + TimeUnit.SECONDS.toNanos(60));
        final BugReport report;
        try {
            var snapshot = BugReportSnapshot.capture(games.protectionGame(player.getUUID()));
            snapshot.addProperty("reporterDimension", player.level().dimension().location().toString());
            snapshot.addProperty("reporterX", player.getX()); snapshot.addProperty("reporterY", player.getY()); snapshot.addProperty("reporterZ", player.getZ());
            report = new BugReport(UUID.randomUUID(), System.currentTimeMillis(), player.getUUID(), player.getGameProfile().getName(), content, snapshot);
        } catch (RuntimeException exception) {
            nextReport.remove(player.getUUID());
            source.sendFailure(Component.literal("게임 상태를 기록하지 못했습니다. 다시 시도하거나 운영자에게 문의해 주세요."));
            SemionTd.LOGGER.warn("Bug report snapshot failed: {}", exception.getClass().getSimpleName());
            return 0;
        }
        return query(source, false, () -> { store().save(report); return report.id(); }, id ->
                message(source, "버그 신고가 접수되었습니다. ID: " + id), () -> nextReport.remove(player.getUUID()));
    }

    private int list(CommandSourceStack source, int page) {
        return query(source, true, () -> store().list(page), rows -> {
            message(source, "버그 신고 목록 · " + page + "페이지 · /버그신고목록 <페이지>");
            if (rows.isEmpty()) message(source, "신고가 없습니다.");
            for (var row : rows) {
                Component line = Component.literal(row.playerName() + " · " + Instant.ofEpochMilli(row.createdAtEpochMillis()) + " · " + shorten(row.content(), 80))
                        .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/버그신고조회 " + row.id()))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("클릭하여 상세 보기\n" + row.id()))));
                source.sendSuccess(() -> line, false);
            }
        }, () -> {});
    }

    private int detail(CommandSourceStack source, String value, int page) {
        final UUID id;
        try { id = UUID.fromString(value); }
        catch (IllegalArgumentException exception) { source.sendFailure(Component.literal("올바른 신고 ID를 입력해 주세요.")); return 0; }
        return query(source, true, () -> store().find(id), found -> {
            if (found.isEmpty()) { source.sendFailure(Component.literal("신고를 찾을 수 없습니다.")); return; }
            BugReport report = found.get();
            var snapshot = report.snapshot();
            message(source, report.playerName() + " · " + Instant.ofEpochMilli(report.createdAtEpochMillis()) + " · " + report.id());
            message(source, report.content());
            message(source, snapshot.get("hasGame").getAsBoolean()
                    ? "R" + snapshot.get("round") + " · " + snapshot.get("phase").getAsString() + " · 경기 " + snapshot.get("matchId")
                    : "진행 중인 경기 없음");
            var towers = snapshot.getAsJsonArray("towers");
            message(source, "타워 " + towers.size() + "기 · " + page + "페이지 · /버그신고조회 " + id + " <페이지>");
            for (int i = (page - 1) * 10; i < Math.min(page * 10, towers.size()); i++) {
                var tower = towers.get(i).getAsJsonObject();
                Component line = Component.literal(tower.get("name").getAsString() + " · " + tower.get("team").getAsString()
                        + " " + tower.get("lane") + "번 · 공격 " + tower.get("attack") + " · 체력 " + tower.get("health") + "/" + tower.get("maxHealth"))
                        .withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(shorten(tower.toString(), 8000)))));
                source.sendSuccess(() -> line, false);
            }
        }, () -> {});
    }

    private SQLiteBugReportRepository store() {
        if (repository == null) repository = new SQLiteBugReportRepository(path);
        return repository;
    }

    private <T> int query(CommandSourceStack source, boolean opOnly, Supplier<T> work, Consumer<T> done, Runnable failed) {
        try {
            CompletableFuture.supplyAsync(work, io).whenComplete((result, error) -> source.getServer().execute(() -> {
                if (error != null) {
                    failed.run();
                    SemionTd.LOGGER.warn("Bug report storage operation failed: {}", error.getClass().getSimpleName());
                }
                if (opOnly && !(source.getEntity() instanceof ServerPlayer player
                        ? player.createCommandSourceStack().hasPermission(2) : source.hasPermission(2))) return;
                if (error != null) source.sendFailure(Component.literal("신고 저장소를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."));
                else done.accept(result);
            }));
            return 1;
        } catch (java.util.concurrent.RejectedExecutionException exception) {
            failed.run();
            source.sendFailure(Component.literal("신고 처리 요청이 많습니다. 잠시 후 다시 시도해 주세요."));
            return 0;
        }
    }

    private static String shorten(String text, int length) { return text.length() <= length ? text : text.substring(0, length) + "…"; }
    private static void message(CommandSourceStack source, String text) { source.sendSuccess(() -> Component.literal(text), false); }

    @Override public void close() {
        io.shutdown();
        try {
            if (!io.awaitTermination(30, TimeUnit.SECONDS)) SemionTd.LOGGER.warn("Bug report storage is still draining at shutdown; unconfirmed submissions may be lost.");
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
    }
}
