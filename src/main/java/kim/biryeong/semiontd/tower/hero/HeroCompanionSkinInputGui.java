package kim.biryeong.semiontd.tower.hero;

import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.progression.HeroCompanionSkinPreference;
import kim.biryeong.semiontd.ui.PlayerSkinInputGui;
import net.minecraft.server.level.ServerPlayer;

public final class HeroCompanionSkinInputGui extends PlayerSkinInputGui {
    public HeroCompanionSkinInputGui(ServerPlayer player, SemionGameManager gameManager, HeroCompanionRole role) {
        super(player, role.displayName(), HeroCompanionSkins.preference(player.getUUID(), role)
                        .map(HeroCompanionSkinPreference::sourceName).orElse(""),
                skin -> gameManager.saveHeroCompanionSkin(player.level().getServer(), player.getUUID(),
                        player.getGameProfile().name(), role, skin),
                () -> new HeroCompanionSkinGui(player, gameManager).open(), () -> true);
    }
}
