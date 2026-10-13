package kim.biryeong.semiontd.tower.villager;

import java.util.ArrayList;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import kim.biryeong.semiontd.augment.AugmentCombatFixture;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.augment.AugmentConfig;

public final class VillagerTowerAugmentCombatTest extends AugmentCombatFixture {
    @GameTest
    public void villagerCorpseChainStopsAfterTwoGenerationsAndSuppressesOtherTriggers(GameTestHelper context) {
        PlayerLane lane = lane(context);
        var p = GridPosition.from(context.absolutePos(new BlockPos(1, 2, 3)));
        var cat = new kim.biryeong.semiontd.tower.villager.LaneClearCatTower(
                kim.biryeong.semiontd.tower.villager.VillagerTowers.T2_LANE_CLEAR_CAT_TOWER,
                lane.ownerPlayer(), TeamId.RED, 1, p);
        try {
            lane.addTower(cat);
            lane.assignAugmentSnapshot(snapshot("job_villager_towers_g2", AugmentChoice.none()));
            lane.markWaveStarted(5);
            var source = entity(context, cat);
            source.setNoAi(true);
            double radius = kim.biryeong.semiontd.config.TowerBalanceRuntime.ability(cat.type().id(), "explosionRadius") + 1;
            double spacing = radius - .1;
            var corpses = new ArrayList<SemionMonsterEntity>();
            for (int i = 0; i < 5; i++) {corpses.add(monster(context, lane, source.position().add(i * spacing, 0, 0), 1));}
            cat.damageTargetResult(source, corpses.getFirst(), 100);
            cat.onKill(source, corpses.getFirst(), 100);
            for (int i = 1; i <= 3; i++) {
                close(0, corpses.get(i).runtimeMonster().health(), "Initial explosion and two chained generations must resolve.");
                AugmentCombat.withKillOrigin(corpses.get(i).runtimeMonster(), () -> {
                    if (AugmentCombat.allowsTriggers()) {throw new AssertionError("Chain kills must not charge another augment.");}
                });
            }
            close(1, corpses.get(4).runtimeMonster().health(), "A third generation must not explode.");
            if (!AugmentCombat.allowsTriggers()) {throw new AssertionError("Chain guard must unwind.");}
            context.succeed();
        } finally {cleanup(lane);}
    }

    @GameTest
    public void villagerInheritanceUsesPermanentStacksAcrossTiersOnlyOnce(GameTestHelper context) {
        PlayerLane lane = lane(context);
        var p = GridPosition.from(context.absolutePos(new BlockPos(4, 2, 4)));
        var dead = new kim.biryeong.semiontd.tower.villager.VillagerThornTower(
                kim.biryeong.semiontd.tower.villager.VillagerTowers.T2_GOLEM_TOWER, lane.ownerPlayer(), TeamId.RED, 1, p);
        var next = new kim.biryeong.semiontd.tower.villager.VillagerThornTower(
                kim.biryeong.semiontd.tower.villager.VillagerTowers.T3_GOLEM_TOWER, lane.ownerPlayer(), TeamId.RED, 1,
                new GridPosition(p.x() + 2, p.y(), p.z()));
        try {
            lane.addTower(dead);
            lane.addTower(next);
            kim.biryeong.semiontd.tower.villager.VillagerAugments.onSelected(lane, AugmentConfig.defaults());
            lane.assignAugmentSnapshot(snapshot("job_villager_towers_s", AugmentChoice.none()));
            lane.markWaveStarted(5);
            double before = next.currentMaxHealth();
            dead.syncHealth(0);
            dead.notifyDeath(lane);
            if (next.currentMaxHealth() <= before) {throw new AssertionError("Cross-tier inheritance must increase health.");}
            close(3, kim.biryeong.semiontd.tower.villager.VillagerAugments.permanentStacks(next), "Inherited stacks must not become permanent.");
            double inherited = next.currentMaxHealth();
            dead.onDeath(lane);
            close(inherited, next.currentMaxHealth(), "Duplicate deaths must not inherit again.");
            lane.markWaveStarted(6);
            close(before, next.currentMaxHealth(), "Temporary inheritance expires at the next wave.");
            context.succeed();
        } finally {cleanup(lane);}
    }

    @GameTest
    public void villagerLongevityDoesNotConsumeGrowthOrRecurse(GameTestHelper context) {
        PlayerLane lane = lane(context);
        var p = GridPosition.from(context.absolutePos(new BlockPos(4, 2, 4)));
        var tower = new kim.biryeong.semiontd.tower.villager.VillagerThornTower(
                kim.biryeong.semiontd.tower.villager.VillagerTowers.T2_GOLEM_TOWER, lane.ownerPlayer(), TeamId.RED, 1, p);
        try {
            lane.addTower(tower);
            kim.biryeong.semiontd.tower.villager.VillagerAugments.onSelected(lane, AugmentConfig.defaults());
            lane.assignAugmentSnapshot(snapshot("job_villager_towers_g1", AugmentChoice.none()));
            lane.markWaveStarted(5);
            var source = entity(context, tower);
            source.setNoAi(true);
            var target = monster(context, lane, source.position().add(.5, 0, 0), 100000);
            double damage = tower.resolveBasicAttackOutgoingDamage(source, target, source.attackDamageAmount(target));
            double before = target.runtimeMonster().health();
            for (int i = 0; i < 6; i++) {source.recordAttack(target, 1, 1, 1, false);}
            close(damage * 3, before - target.runtimeMonster().health(), "Three growth stacks give exactly three additional attacks.");
            close(3, kim.biryeong.semiontd.tower.villager.VillagerAugments.permanentStacks(tower), "Attacks do not spend permanent growth.");
            if (!AugmentCombat.allowsTriggers()) {throw new AssertionError("Additional attack guard must unwind.");}
            context.succeed();
        } finally {cleanup(lane);}
    }

    @GameTest
    public void villagerGiantPulsesAfterSixtyTicksAndStopsAfterRound(GameTestHelper context) {
        PlayerLane lane = lane(context);
        var p = GridPosition.from(context.absolutePos(new BlockPos(4, 2, 4)));
        var tower = new kim.biryeong.semiontd.tower.villager.VillagerThornTower(
                kim.biryeong.semiontd.tower.villager.VillagerTowers.T3_GOLEM_TOWER, lane.ownerPlayer(), TeamId.RED, 1, p);
        try {
            lane.addTower(tower);
            lane.assignAugmentSnapshot(snapshot("job_villager_towers_p", new AugmentChoice(tower.logicalId(), null, "")));
            lane.markWaveStarted(5);
            var source = entity(context, tower);
            source.setNoAi(true);
            var target = monster(context, lane, source.position().add(2, 0, 0), 100000);
            var outside = monster(context, lane, source.position().add(5, 0, 0), 100000);
            double expected = tower.resolveOutgoingDamage(source, target, tower.currentMaxHealth() * .12);
            for (int i = 0; i < 59; i++) {tower.tick(lane);}
            close(100000, target.runtimeMonster().health(), "Pulse must wait sixty ticks.");
            tower.tick(lane);
            close(100000 - expected, target.runtimeMonster().health(), "Pulse uses current maximum health.");
            close(100000, outside.runtimeMonster().health(), "Pulse obeys four-block radius.");
            tower.resetForRound(lane);
            double remaining = target.runtimeMonster().health();
            for (int i = 0; i < 61; i++) {tower.tick(lane);}
            close(remaining, target.runtimeMonster().health(), "Preparation must not pulse.");
            context.succeed();
        } finally {cleanup(lane);}
    }
}
