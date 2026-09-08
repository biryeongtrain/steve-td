package kim.biryeong.semiontd.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.tower.gamble.GambleReveal;
import kim.biryeong.semiontd.ui.rp.GambleGlyphs;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Per-player action-bar ownership, advanced only on the server thread. Rewards are already committed. */
public final class GambleRevealService {
    private static final Map<UUID, ActiveReveal> ACTIVE = new HashMap<>();

    private GambleRevealService() {
    }

    public static void start(ServerPlayer player, GambleReveal reveal) {
        if (player == null) return;
        ActiveReveal previous = ACTIVE.get(player.getUUID());
        // A new bet skips the old presentation, never its already-earned result.
        if (previous != null && previous.age < previous.reveal.revealTick()) {
            player.sendSystemMessage(resultMessage(previous.reveal));
        }
        player.connection.send(ClientboundClearDialogPacket.INSTANCE);
        ActiveReveal active = new ActiveReveal(reveal, player.level().dimension());
        ACTIVE.put(player.getUUID(), active);
        player.displayClientMessage(render(reveal, reveal.frameAt(0)), true);
    }

    public static boolean isRolling(UUID player) {
        ActiveReveal active = ACTIVE.get(player);
        return active != null && active.age < active.reveal.revealTick();
    }

    public static Optional<Component> actionbar(UUID player) {
        ActiveReveal active = ACTIVE.get(player);
        return active == null ? Optional.empty() : Optional.of(render(active.reveal, active.reveal.frameAt(active.age)));
    }

    public static Component render(GambleReveal reveal, GambleReveal.Frame frame) {
        return render(reveal, frame, reveal.caption(), true);
    }

    public static Component resultMessage(GambleReveal reveal) {
        return Component.literal("\n\n")
                .append(render(reveal, reveal.frameAt(reveal.revealTick()), reveal.result(), false))
                .append("\n");
    }

    private static Component render(GambleReveal reveal, GambleReveal.Frame frame, String finalText, boolean showLabel) {
        MutableComponent text = Component.empty();
        if (showLabel) text.append(Component.literal(reveal.label() + "  ").withStyle(ChatFormatting.GOLD));
        for (int glyph : frame.glyphs()) {
            text.append(switch (reveal.kind()) {
                case CARDS -> GambleGlyphs.card(glyph);
                case DICE -> GambleGlyphs.die(glyph);
                case SLOTS -> GambleGlyphs.slot(glyph);
            }).append(Component.literal(" "));
        }
        text.append(Component.literal(frame.revealed() ? "  " + finalText : "  …")
                .withStyle(frame.revealed() ? (reveal.positive() ? ChatFormatting.GREEN : ChatFormatting.RED) : ChatFormatting.GRAY));
        return text;
    }

    public static void tick(MinecraftServer server) {
        var iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ActiveReveal active = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.level().dimension().equals(active.dimension)) {
                iterator.remove();
                continue;
            }
            active.age++;
            if (active.age >= active.reveal.durationTicks()) {
                iterator.remove();
                player.displayClientMessage(Component.empty(), true);
                continue;
            }
            GambleReveal.Frame frame = active.reveal.frameAt(active.age);
            if (active.age % 3 == 0 || frame.cue() != GambleReveal.Cue.NONE) {
                player.displayClientMessage(render(active.reveal, frame), true);
            }
            switch (frame.cue()) {
                case DRAW -> sound(player, SoundEvents.BOOK_PAGE_TURN, 0.8F, 0.95F + active.age / 120.0F);
                case ROLL -> sound(player, active.reveal.kind() == GambleReveal.Kind.SLOTS
                        ? SoundEvents.NOTE_BLOCK_HAT.value() : SoundEvents.BAMBOO_WOOD_BUTTON_CLICK_ON, 0.45F, 1.2F);
                case STOP -> sound(player, SoundEvents.WOODEN_BUTTON_CLICK_ON, 0.7F, 0.9F);
                case NONE -> { }
            }
            if (active.age == active.reveal.revealTick()) {
                sound(player, active.reveal.positive() ? SoundEvents.NOTE_BLOCK_CHIME.value() : SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 1.2F);
                player.sendSystemMessage(resultMessage(active.reveal));
            }
        }
    }

    private static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.playNotifySound(sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static void clear(UUID player) {
        ACTIVE.remove(player);
    }

    public static void clearAll() {
        ACTIVE.clear();
    }

    private static final class ActiveReveal {
        private final GambleReveal reveal;
        private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        private int age;

        private ActiveReveal(GambleReveal reveal, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
            this.reveal = reveal;
            this.dimension = dimension;
        }
    }
}
