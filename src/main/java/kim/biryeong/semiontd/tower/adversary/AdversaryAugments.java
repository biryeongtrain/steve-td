package kim.biryeong.semiontd.tower.adversary;

import java.util.Set;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterDataKey;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.resources.ResourceLocation;

public final class AdversaryAugments {
    static final String DARK_HERO = "job_adversary_towers_s";
    static final String ADAPTATION = "job_adversary_towers_g2";
    static final String FINALE = "job_adversary_towers_p";
    private static final ResourceLocation CORPSE_EXPLOSION = ResourceLocation.fromNamespaceAndPath("semiontd", DARK_HERO);
    private static final MonsterDataKey<Boolean> EXPLODED = MonsterDataKey.of(CORPSE_EXPLOSION, Boolean.class);

    private AdversaryAugments() {}

    public static void captureWaveStart(PlayerLane lane) {
        if (lane == null) {
            return;
        }
        for (Tower tower : lane.towers()) {
            if (tower instanceof AdversaryFoxTower fox) {
                fox.captureAugmentOpeningStats(lane);
            }
        }
    }

    public static void onKill(Tower source, SemionTowerEntity sourceEntity, SemionMonsterEntity killed) {
        Monster rival = killed == null ? null : killed.runtimeMonster();
        if (source == null || sourceEntity == null || source instanceof AdversaryFoxTower
                || !AugmentCombat.allowsTriggers() || !source.augmentSnapshot().has(DARK_HERO)
                || !AdversaryRivalTower.isOwnedRival(rival, source.ownerPlayer())
                || rival.getData(EXPLODED).orElse(false)) {
            return;
        }
        rival.setData(EXPLODED, true);
        double damage = rival.maxHealth() * source.augmentSnapshot().parameter(DARK_HERO, "healthRatio", .25);
        MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(CORPSE_EXPLOSION, sourceEntity,
                killed.position(), source.augmentSnapshot().parameter(DARK_HERO, "radius", 3),
                Set.of(killed.getUUID()), null, AreaVfxSpec.onTrigger(AreaVfxStyles.CORPSE_EXPLOSION));
        AugmentCombat.runWithoutTriggers(() -> TowerAreaDamage.apply(source, sourceEntity, request,
                ignored -> damage, true, (target, dealt, dead) -> {}, DamageType.MAGIC));
    }

    static double absorb(double current, double openingStat, double capRatio, double rivalStat, double ratio) {
        return Math.min(Math.max(0.0, openingStat * capRatio), current + Math.max(0.0, rivalStat * ratio));
    }
}
