package kim.biryeong.semiontd.tower.warlock;

import java.util.Comparator;
import java.util.Set;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.world.phys.Vec3;

final class WarlockAugments {
    static final String TESTAMENT = "job_warlock_towers_s";
    static final String EXPLOSIVE = "job_warlock_towers_g1";
    static final String AWAKENING = "job_warlock_towers_g2";
    static final String PARTNERSHIP = "job_warlock_towers_p";

    private WarlockAugments() {}

    static void onAbsorbed(WarlockTower tower, SemionTowerEntity source, PlayerLane lane,
            Tower sacrifice, WarlockSacrifice.Snapshot snapshot, WarlockSacrifice.Gain gain,
            double healing, Vec3 center) {
        if (tower.augmentSnapshot().has(PARTNERSHIP)) {
            for (Tower other : lane.towers()) {
                if (other instanceof WarlockTower partner && partner != tower && partner.health() > 0.0
                        && partner.ownerPlayer().equals(tower.ownerPlayer())) {
                    partner.receiveSharedGrowth(lane, gain, healing);
                }
            }
        }
        if (tower.augmentSnapshot().has(TESTAMENT)) {
            source.level().getEntitiesOfClass(SemionMonsterEntity.class, source.targetSearchBox(),
                    source::isValidAttackTarget).stream()
                    .min(Comparator.comparingDouble(target -> target.position().distanceToSqr(center)))
                    .ifPresent(target -> {
                        double damage = snapshot.attackDamage() * tower.augmentSnapshot().parameter(
                                TESTAMENT, "damageRatio", 3.0);
                        Tower.DamageResult result = tower.damageTargetResult(source, target, damage, sacrifice.primaryDamageType());
                        if (result.killed()) {tower.onKill(source, target, damage);}
                    });
        }
        if (tower.augmentSnapshot().has(EXPLOSIVE)) {
            MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                    AreaEffectIds.tower(tower, "augment_sacrifice_explosion"), source, center,
                    tower.augmentSnapshot().parameter(EXPLOSIVE, "radius", 3), Set.of(), null,
                    AreaVfxSpec.onTrigger(AreaVfxStyles.CORPSE_EXPLOSION)).nearestTargets(
                    (int) tower.augmentSnapshot().parameter(EXPLOSIVE, "maxTargets", 12));
            TowerAreaDamage.apply(tower, source, request,
                    ignored -> snapshot.maxHealth() * tower.augmentSnapshot().parameter(EXPLOSIVE, "healthRatio", 1),
                    true, (target, damage, killed) -> {}, DamageType.MAGIC);
        }
    }
}
