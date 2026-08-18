package kim.biryeong.semiontd.tower.demonlord;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class DemonLordVfx {
    private DemonLordVfx() {
    }

    public static boolean show(
            DemonLordSkillTower altar,
            PlayerLane lane,
            Vec3 center,
            double radius,
            ResourceLocation style
    ) {
        SemionTowerEntity source = altar == null ? null : altar.entity(lane);
        if (source == null) {
            return false;
        }
        TowerVfxService.showAreaEffect(
                source,
                ResourceLocation.fromNamespaceAndPath(
                        SemionTd.MOD_ID, "demon_lord/" + altar.skill().key()),
                style,
                center,
                radius,
                List.of(),
                0,
                0,
                0
        );
        return true;
    }

    public static boolean showDebug(DemonLordSkillTower altar, PlayerLane lane, Vec3 center) {
        return show(altar, lane, center, 4.0, styleFor(altar.skill()));
    }

    public static ResourceLocation styleFor(DemonLordSkill skill) {
        return switch (skill) {
            case WAVE_OF_MALICE, SOUL_DRAIN, SKY_BREAKER -> AreaVfxStyles.SPLASH;
            case DEMON_WINGS, ARCANE_BOMBARDMENT, HELL_GUILLOTINE -> AreaVfxStyles.PULSE;
            case DEMON_BARRIER -> AreaVfxStyles.BUFF;
            case HELLFIRE_BRAND, ROAR_OF_DREAD -> AreaVfxStyles.DEBUFF;
            case GRIP_OF_DOOM -> AreaVfxStyles.CORPSE_EXPLOSION;
        };
    }
}
