package kim.biryeong.semiontd.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Sends a notification sound exclusively to its intended player. */
public final class SemionPlayerPackets {
    private SemionPlayerPackets() {
    }

    public static void playSound(ServerPlayer player, SoundEvent sound, SoundSource source, float volume, float pitch) {
        player.connection.send(new ClientboundSoundEntityPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), source, player, volume, pitch,
                player.getRandom().nextLong()
        ));
    }
}
