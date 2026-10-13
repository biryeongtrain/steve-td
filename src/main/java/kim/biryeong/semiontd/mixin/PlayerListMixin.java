package kim.biryeong.semiontd.mixin;

import net.minecraft.server.players.NameAndId;
import kim.biryeong.semiontd.game.SemionPlayerLimitBypassService;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Redirect(
            method = "canPlayerLogin",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/players/PlayerList;canBypassPlayerLimit(Lnet/minecraft/server/players/NameAndId;)Z"
            )
    )
    private boolean semiontd$allowMatchParticipantsWhenServerIsFull(PlayerList playerList, NameAndId profile) {
        return playerList.canBypassPlayerLimit(profile) || SemionPlayerLimitBypassService.canBypassPlayerLimitIdentity(profile);
    }
}
