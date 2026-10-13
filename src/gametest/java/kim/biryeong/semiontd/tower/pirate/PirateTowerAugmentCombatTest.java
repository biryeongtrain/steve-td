package kim.biryeong.semiontd.tower.pirate;

import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.goal.TowerAttackMonsterGoal;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.pirate.PirateTower;
import kim.biryeong.semiontd.tower.pirate.PirateTowers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import kim.biryeong.semiontd.augment.AugmentCombatFixture;
import kim.biryeong.semiontd.augment.AugmentChoice;

public final class PirateTowerAugmentCombatTest extends AugmentCombatFixture {
    @GameTest(maxTicks = 100)
    public void pirateGrowthAndAugmentsSurviveUpgradeWithoutDoubleMultiplying(GameTestHelper context) {
        PlayerLane lane = lane(context);
        GridPosition position = GridPosition.from(context.absolutePos(new BlockPos(4, 2, 4)));
        PirateTower tower = (PirateTower) ProductionTowerCatalog.entry(PirateTowers.SWORDSMAN).orElseThrow()
                .create(lane.ownerPlayer(), TeamId.RED, 1, position);
        try {
            lane.addTower(tower);
            tower.addPermanentMaxHealthBonus(10, lane);
            tower.addPermanentFlatDamageBonus(4, lane);
            lane.assignAugmentSnapshot(snapshot("wartime_economy", AugmentChoice.none()));
            lane.markWaveStarted(5);
            SemionTowerEntity source = entity(context, tower);
            source.setNoGravity(true);
            source.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS, .10, 100);
            close(14, tower.permanentMaxHealthBonus(), "Swordsman amplifies the stored ten health by forty percent.");
            close(5.6, tower.permanentFlatDamageBonus(), "Swordsman amplifies the stored four damage by forty percent.");
            close((tower.type().maxHealth() + 14) * 1.40, tower.currentMaxHealth(), "Wartime health applies once after permanent pirate growth.");
            close(tower.currentMaxHealth(), source.getMaxHealth(), "The live entity must share the augmented health cap.");
            SemionMonsterEntity target = monster(context, lane, source.position().add(1, 0, 0), 1000);
            new TowerAttackMonsterGoal(source).tick();
            close(1000 - (tower.type().damage() + 5.6) * 1.14 * 1.65, target.runtimeMonster().health(),
                    "Pirate flat growth, amplified timed damage and wartime damage each apply once.");

            PirateTower upgraded = (PirateTower) ProductionTowerCatalog.entry(PirateTowers.IRON_SWORDSMAN).orElseThrow()
                    .create(lane.ownerPlayer(), TeamId.RED, 1, position);
            upgraded.copyFrom(tower, 1200);
            if (!lane.replaceTower(tower, upgraded)) throw new AssertionError("The pirate upgrade must replace its existing tower.");
            if (!tower.logicalId().equals(upgraded.logicalId()) || !upgraded.augmentSnapshot().has("wartime_economy")) {
                throw new AssertionError("Upgrade must preserve logical identity and the chosen augment.");
            }
            lane.markWaveStarted(6);
            SemionTowerEntity nextSource = entity(context, upgraded);
            nextSource.setNoGravity(true);
            nextSource.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS, .10, 100);
            close(18, upgraded.permanentMaxHealthBonus(), "Iron swordsman multiplies the original stored ten health by 1.8.");
            close(7.2, upgraded.permanentFlatDamageBonus(), "Iron swordsman multiplies the original stored four damage by 1.8.");
            close((upgraded.type().maxHealth() + 18) * 1.40, upgraded.currentMaxHealth(), "The upgraded health keeps pirate growth and wartime once.");
            close(upgraded.currentMaxHealth(), nextSource.getMaxHealth(), "The replacement entity must receive the augmented health cap.");
            double before = target.runtimeMonster().health();
            new TowerAttackMonsterGoal(nextSource).tick();
            close(before - (upgraded.type().damage() + 7.2) * 1.18 * 1.75 * 1.65, target.runtimeMonster().health(),
                    "The iron first strike retains its multiplier alongside timed and augment bonuses.");
            context.succeed();
        } finally { cleanup(lane); }
    }
}
