package kim.biryeong.semiontd.tower.gamble;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import kim.biryeong.semiontd.ui.rp.GambleGlyphs;
import java.util.UUID;
import java.util.WeakHashMap;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.Tower;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;

/** Keeps round results above their support towers. */
final class GambleRollLabels {
    private static final Map<PlayerLane, Map<UUID, Map<Tower, Label>>> LABELS = new WeakHashMap<>();

    private GambleRollLabels() {
    }

    static synchronized void show(
            PlayerLane lane, UUID owner, Tower target, ResourceLocation sourceId, int face
    ) {
        if (face < 1 || face > 6) return;
        showResult(lane, owner, target, sourceId, Component.literal("[" + face + "]")
                .withStyle(face <= 2 ? ChatFormatting.RED : ChatFormatting.GREEN));
    }

    static synchronized void showSymbols(PlayerLane lane, UUID owner, Tower target, ResourceLocation sourceId,
                                         List<GambleSlots.Symbol> symbols) {
        MutableComponent text = Component.empty();
        for (var symbol : symbols) text.append(GambleGlyphs.slot(symbol.ordinal())).append(" ");
        showResult(lane, owner, target, sourceId, text);
    }

    private static void showResult(PlayerLane lane, UUID owner, Tower target, ResourceLocation sourceId, Component text) {
        if (lane == null || owner == null || target == null || sourceId == null) return;
        SemionTowerEntity targetEntity = GambleRoundEffects.towerEntity(target, lane).orElse(null);
        if (targetEntity == null || !(targetEntity.level() instanceof ServerLevel level)) {
            return;
        }
        Label label = LABELS.computeIfAbsent(lane, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(owner, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(target, ignored -> new Label());
        label.faces.put(sourceId, text);
        if (label.visual == null || label.visual.isRemoved()) {
            label.visual = create(level, targetEntity);
        } else {
            moveAbove(label.visual, targetEntity);
        }
        if (label.visual != null) {
            label.visual.setCustomName(component(label.faces));
            label.visual.setCustomNameVisible(true);
        }
    }

    static synchronized void clearSource(
            PlayerLane lane, UUID owner, ResourceLocation sourceId
    ) {
        Map<Tower, Label> byTarget = LABELS.getOrDefault(lane, Map.of()).get(owner);
        if (byTarget == null) {
            return;
        }
        byTarget.entrySet().removeIf(entry -> {
            Label label = entry.getValue();
            label.faces.remove(sourceId);
            if (label.faces.isEmpty()) {
                remove(label.visual);
                return true;
            }
            if (label.visual != null && !label.visual.isRemoved()) {
                label.visual.setCustomName(component(label.faces));
            }
            return false;
        });
        prune(lane, owner);
    }

    static synchronized void clearAll(PlayerLane lane, UUID owner) {
        Map<UUID, Map<Tower, Label>> byOwner = LABELS.get(lane);
        if (byOwner == null) {
            return;
        }
        Map<Tower, Label> removed = byOwner.remove(owner);
        if (removed != null) {
            removed.values().forEach(label -> remove(label.visual));
        }
        if (byOwner.isEmpty()) {
            LABELS.remove(lane);
        }
    }

    static synchronized int count(PlayerLane lane, UUID owner) {
        return LABELS.getOrDefault(lane, Map.of()).getOrDefault(owner, Map.of()).size();
    }

    static synchronized void sync(PlayerLane lane, UUID owner, Tower target) {
        Label label = LABELS.getOrDefault(lane, Map.of())
                .getOrDefault(owner, Map.of())
                .get(target);
        if (label == null || label.visual == null || label.visual.isRemoved()) {
            return;
        }
        GambleRoundEffects.towerEntity(target, lane)
                .ifPresent(entity -> moveAbove(label.visual, entity));
    }

    static synchronized boolean hasVisibleLabel(PlayerLane lane, UUID owner, Tower target) {
        Label label = LABELS.getOrDefault(lane, Map.of())
                .getOrDefault(owner, Map.of())
                .get(target);
        return label != null
                && label.visual != null
                && !label.visual.isRemoved()
                && label.visual.isCustomNameVisible()
                && label.visual.getCustomName() != null
                && !label.visual.getCustomName().getString().isBlank();
    }

    private static ArmorStand create(ServerLevel level, SemionTowerEntity target) {
        ArmorStand visual = new ArmorStand(level, target.getX(), target.getY(), target.getZ());
        visual.setInvisible(true);
        visual.setInvulnerable(true);
        visual.setNoGravity(true);
        visual.setSilent(true);
        visual.setNoBasePlate(true);
        visual.addTag(SemionEntityTypes.RUNTIME_NO_SAVE_TAG);
        visual.getEntityData().set(ArmorStand.DATA_CLIENT_FLAGS,
                (byte) (visual.getEntityData().get(ArmorStand.DATA_CLIENT_FLAGS)
                        | ArmorStand.CLIENT_FLAG_MARKER));
        if (!level.addFreshEntity(visual)) {
            return null;
        }
        moveAbove(visual, target);
        return visual;
    }

    private static void moveAbove(ArmorStand visual, SemionTowerEntity target) {
        visual.teleportTo(target.getX(), target.getY(), target.getZ());
    }

    private static Component component(Map<ResourceLocation, Component> faces) {
        MutableComponent result = Component.empty();
        boolean first = true;
        for (Component face : faces.values()) {
            if (!first) {
                result.append(Component.literal(" "));
            }
            result.append(face);
            first = false;
        }
        return result;
    }

    private static void prune(PlayerLane lane, UUID owner) {
        Map<UUID, Map<Tower, Label>> byOwner = LABELS.get(lane);
        if (byOwner == null) {
            return;
        }
        Map<Tower, Label> byTarget = byOwner.get(owner);
        if (byTarget != null && byTarget.isEmpty()) {
            byOwner.remove(owner);
        }
        if (byOwner.isEmpty()) {
            LABELS.remove(lane);
        }
    }

    private static void remove(ArmorStand visual) {
        if (visual != null && !visual.isRemoved()) {
            visual.discard();
        }
    }

    private static final class Label {
        private final Map<ResourceLocation, Component> faces = new LinkedHashMap<>();
        private ArmorStand visual;
    }
}
