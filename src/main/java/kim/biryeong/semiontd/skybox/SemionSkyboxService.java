package kim.biryeong.semiontd.skybox;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ManualAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.progression.SemionPlayerProfile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;

public final class SemionSkyboxService {
    public static final String OFF_SELECTION = "off";
    private static final float SKYBOX_SCALE = 1000.0F;

    private SemionSkyboxLibrary library;
    private final SemionGameManager gameManager;
    private final Map<UUID, String> selections = new HashMap<>();
    private final Map<UUID, ActiveSkybox> activeSkyboxes = new HashMap<>();

    public SemionSkyboxService(SemionSkyboxLibrary library, SemionGameManager gameManager) {
        this.library = library;
        this.gameManager = gameManager;
    }

    public void tick(MinecraftServer server) {
        List<UUID> disconnected = activeSkyboxes.keySet().stream()
                .filter(playerId -> server.getPlayerList().getPlayer(playerId) == null)
                .toList();
        disconnected.forEach(this::destroy);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            updatePlayer(player);
        }
    }

    public List<SemionSkybox> availableSkyboxes() {
        return library.skyboxes();
    }

    public SemionSkyboxLibrary library() {
        return library;
    }

    public void replaceLibrary(SemionSkyboxLibrary replacement) {
        library = Objects.requireNonNull(replacement, "replacement");
    }

    public Optional<SemionSkybox> selectedSkybox(ServerPlayer player) {
        String selection = selection(player);
        if (OFF_SELECTION.equals(selection)) {
            return Optional.empty();
        }
        if (!selection.isBlank()) {
            Optional<SemionSkybox> selected = library.skybox(selection);
            if (selected.isPresent()) {
                return selected;
            }
        }
        return library.defaultSkybox();
    }

    public boolean select(ServerPlayer player, String requestedId) {
        if (player == null || requestedId == null) {
            return false;
        }
        String normalized = requestedId.trim().toLowerCase(Locale.ROOT);
        if (!OFF_SELECTION.equals(normalized) && library.skybox(normalized).isEmpty()) {
            return false;
        }
        gameManager.saveSelectedSkybox(
                player.getServer(),
                player.getUUID(),
                player.getGameProfile().getName(),
                normalized
        );
        selections.put(player.getUUID(), normalized);
        destroy(player.getUUID());
        updatePlayer(player);
        return true;
    }

    public void handlePlayerDisconnect(ServerPlayer player) {
        if (player == null) {
            return;
        }
        selections.remove(player.getUUID());
        destroy(player.getUUID());
    }

    public void handlePlayerWorldChanged(ServerPlayer player) {
        if (player != null) {
            destroy(player.getUUID());
        }
    }

    public void shutdown() {
        for (ActiveSkybox activeSkybox : new ArrayList<>(activeSkyboxes.values())) {
            activeSkybox.destroy();
        }
        activeSkyboxes.clear();
        selections.clear();
    }

    private void updatePlayer(ServerPlayer player) {
        if (library.isEmpty() || !shouldShow(player)) {
            destroy(player.getUUID());
            return;
        }
        Optional<SemionSkybox> selected = selectedSkybox(player);
        if (selected.isEmpty()) {
            destroy(player.getUUID());
            return;
        }

        SemionSkybox skybox = selected.get();
        ActiveSkybox active = activeSkyboxes.get(player.getUUID());
        if (active == null || !active.matches(player, skybox.id())) {
            destroy(player.getUUID());
            active = create(player, skybox);
            activeSkyboxes.put(player.getUUID(), active);
        }
        active.tick();
    }

    private boolean shouldShow(ServerPlayer player) {
        if (!PolymerResourcePackUtils.hasMainPack(player)) {
            return false;
        }
        SemionGame game = gameManager.protectionGame(player.getUUID());
        return game != null && game.arena().containsWorld((ServerLevel) player.level());
    }

    private String selection(ServerPlayer player) {
        return selections.computeIfAbsent(player.getUUID(), ignored -> {
            SemionPlayerProfile profile = gameManager.profile(
                    player.getServer(),
                    player.getUUID(),
                    player.getGameProfile().getName()
            );
            return profile.selectedSkyboxId();
        });
    }

    private static ActiveSkybox create(ServerPlayer player, SemionSkybox skybox) {
        ItemStack stack = Items.STICK.getDefaultInstance();
        stack.set(DataComponents.ITEM_MODEL, skybox.itemModelId());

        ItemDisplayElement display = new ItemDisplayElement(stack);
        display.setScale(new Vector3f(SKYBOX_SCALE, SKYBOX_SCALE, SKYBOX_SCALE));
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);
        display.setViewRange(1000.0F);

        ElementHolder holder = new ElementHolder();
        holder.addElement(display);
        ServerLevel world = (ServerLevel) player.level();
        new ManualAttachment(holder, world, player::getEyePosition);
        holder.startWatching(player);
        return new ActiveSkybox(world, skybox.id(), holder);
    }

    private void destroy(UUID playerId) {
        ActiveSkybox active = activeSkyboxes.remove(playerId);
        if (active != null) {
            active.destroy();
        }
    }

    private record ActiveSkybox(
            ServerLevel world,
            String skyboxId,
            ElementHolder holder
    ) {
        private boolean matches(ServerPlayer player, String requestedSkyboxId) {
            return world == player.level() && skyboxId.equals(requestedSkyboxId);
        }

        private void tick() {
            holder.tick();
        }

        private void destroy() {
            holder.destroy();
        }
    }
}
