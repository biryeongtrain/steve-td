package kim.biryeong.semiontd.tower.animal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.augment.*;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.summon.SummonTier;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalogs;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import kim.biryeong.semiontd.tower.resonance.ResonanceService;
import kim.biryeong.semiontd.tower.resonance.ResonanceTower;
import kim.biryeong.semiontd.tower.resonance.ResonanceTowers;
import kim.biryeong.semiontd.tower.villager.VillagerAdvAugments;
import kim.biryeong.semiontd.tower.villager.VillagerAdvStates;
import kim.biryeong.semiontd.tower.villager.VillagerTowers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class JobAdvAnimalResonanceGameTest {
    @GameTest
    public void animalFifthFamilyHitCallsOnlyThreeAlliesAndDoesNotRecharge(GameTestHelper context) {
        PlayerLane lane = lane(context, "job_animal_towers_g2");
        try {
            List<RabbitTower> rabbits = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                RabbitTower rabbit = new RabbitTower(AnimalTowers.T1_RABBIT_TOWER, lane.ownerPlayer(), TeamId.RED, 1, position(context));
                lane.addTower(rabbit);
                entity(context, rabbit).setNoAi(true);
                rabbits.add(rabbit);
            }
            SemionTowerEntity source = entity(context, rabbits.getFirst());
            SemionMonsterEntity target = monster(context, lane, source.position().add(1, 0, 0));
            double damage = source.attackDamageAmount(target);
            for (int i = 0; i < 4; i++) attack(source, target);
            close(10000 - 4 * damage, target.runtimeMonster().health(), "Four primary hits do not trigger pack attacks.");
            attack(source, target);
            close(10000 - 8 * damage, target.runtimeMonster().health(), "Fifth hit calls exactly three other rabbits.");
            attack(source, target);
            close(10000 - 9 * damage, target.runtimeMonster().health(), "Extra attacks cannot recharge the shared counter.");
            context.succeed();
        } finally {cleanup(lane);}
    }

    @GameTest
    public void leaderSplashDealsSixtyPercentInsideTwoPointFiveBlocks(GameTestHelper context) {
        PlayerLane lane = lane(context, "job_animal_towers_g1");
        try {
            RabbitTower leader = new RabbitTower(AnimalTowers.T4_RABBIT_LEADER_TOWER, lane.ownerPlayer(), TeamId.RED, 1, position(context));
            lane.addTower(leader);
            SemionTowerEntity source = entity(context, leader);
            SemionMonsterEntity target = monster(context, lane, source.position().add(1, 0, 0));
            SemionMonsterEntity near = monster(context, lane, target.position().add(2.4, 0, 0));
            SemionMonsterEntity outside = monster(context, lane, target.position().add(2.7, 0, 0));
            double damage = source.attackDamageAmount(target);
            attack(source, target);
            close(10000 - damage * 1.6, target.runtimeMonster().health(), "Primary also takes the leader area strike.");
            close(10000 - damage * .6, near.runtimeMonster().health(), "Nearby target takes 60 percent extra physical damage.");
            close(10000, outside.runtimeMonster().health(), "Target outside radius stays untouched.");
            context.succeed();
        } finally {cleanup(lane);}
    }

    @GameTest
    public void harmonyUsesCurrentTierCapAndHitsAtMostTwelveIncludingPrimary(GameTestHelper context) {
        PlayerLane lane = lane(context, "job_resonance_towers_g2");
        try {
            GridPosition pos = position(context);
            ResonanceTower focus = new ResonanceTower(ResonanceTowers.FOCUS_CRYSTAL, lane.ownerPlayer(), TeamId.RED, 1, pos, pos);
            GridPosition linkPos = new GridPosition(pos.x() + 1, pos.y(), pos.z());
            ResonanceTower wave = new ResonanceTower(ResonanceTowers.WAVE_CRYSTAL, lane.ownerPlayer(), TeamId.RED, 1, linkPos, linkPos);
            lane.addTower(focus);
            lane.addTower(wave);
            ResonanceService.captureWaveStart(lane);
            require(focus.resonanceLevel() == 1, "Tier one reaches its current maximum at level one.");
            SemionTowerEntity source = entity(context, focus);
            SemionMonsterEntity target = monster(context, lane, source.position().add(2, 0, 0));
            List<SemionMonsterEntity> nearby = new ArrayList<>();
            for (int i = 0; i < 12; i++) nearby.add(monster(context, lane, target.position().add(.1 + i * .05, 0, 0)));
            SemionMonsterEntity outside = monster(context, lane, target.position().add(3.2, 0, 0));
            double damage = source.attackDamageAmount(target);
            for (int i = 0; i < 6; i++) AugmentCombat.additionalAttack(source, target, 1);
            require(nearby.stream().allMatch(other -> other.runtimeMonster().health() == 10000), "Augment attacks cannot charge harmony.");
            attack(source, target);
            attack(source, target);
            require(nearby.stream().allMatch(other -> other.runtimeMonster().health() == 10000), "Harmony waits for its third normal hit.");
            attack(source, target);
            require(nearby.stream().filter(other -> other.runtimeMonster().health() < 10000).count() == 11,
                    "Harmony is limited to twelve targets including its primary.");
            close(10000 - damage * 2, nearby.getFirst().runtimeMonster().health(), "Harmony adds 200 percent magic damage.");
            close(10000, outside.runtimeMonster().health(), "Harmony does not exceed its three-block radius.");
            context.succeed();
        } finally {cleanup(lane);}
    }

    @GameTest
    public void topGraduateAttacksTwoExtraTargetsWithoutRecursiveShots(GameTestHelper context) {
        PlayerLane lane = lane(context, VillagerAdvAugments.GRADUATE);
        try {
            ProductionTower graduate = new ProductionTower(VillagerTowers.ADV_T1_SPLASH_TOWER,
                    lane.ownerPlayer(), TeamId.RED, 1, position(context));
            lane.addTower(graduate);
            graduate.setData(VillagerAdvStates.EXPERIENCE, 10.0);
            VillagerAdvAugments.startWave(lane);
            VillagerAdvAugments.captureGraduate(lane);
            SemionTowerEntity source = entity(context, graduate);
            source.refreshCombatStats();
            require(source.attackRange() > graduate.type().range(), "Graduate gets the resolved whole-lane range.");
            SemionMonsterEntity target = monster(context, lane, source.position().add(5, 0, 3));
            require(source.distanceToSqr(target) > graduate.type().range() * graduate.type().range(),
                    "The primary target must be beyond the original tower range.");
            List<SemionMonsterEntity> extras = List.of(monster(context, lane, source.position().add(3, 0, 0)),
                    monster(context, lane, source.position().add(4, 0, 0)), monster(context, lane, source.position().add(5, 0, 0)));
            double damage = source.attackDamageAmount(target);
            attack(source, target);
            close(10000 - damage * .6, extras.get(0).runtimeMonster().health(), "First additional target takes 60 percent.");
            close(10000 - damage * .6, extras.get(1).runtimeMonster().health(), "Second additional target takes 60 percent.");
            close(10000, extras.get(2).runtimeMonster().health(), "The third extra target is not hit recursively.");
            context.succeed();
        } finally {cleanup(lane);}
    }

    @GameTest(maxTicks = 160)
    public void roleContestExecutesSupportActionAfterSixSecondsAndResetCancelsIt(GameTestHelper context) {
        PlayerLane lane = lane(context, VillagerAdvAugments.CONTEST);
        int[] supportActions = {0};
        ProductionTower support = new ProductionTower(VillagerTowers.ADV_T1_ALLAY_TOWER,
                lane.ownerPlayer(), TeamId.RED, 1, position(context)) {
            @Override protected boolean execute(PlayerLane currentLane) {
                require(!AugmentCombat.allowsTriggers(), "Extra support action must suppress augment triggers.");
                supportActions[0]++;
                return true;
            }
        };
        lane.addTower(support);
        lane.addTower(new ProductionTower(VillagerTowers.ADV_T1_SPLASH_TOWER,
                lane.ownerPlayer(), TeamId.RED, 1, position(context)));
        lane.addTower(new ProductionTower(VillagerTowers.ADV_T1_GOLEM_TOWER,
                lane.ownerPlayer(), TeamId.RED, 1, position(context)));
        for (Tower tower : lane.towers()) entity(context, (EntityBackedTower) tower);
        VillagerAdvAugments.startWave(lane);
        VillagerAdvAugments.tick(lane);
        require(supportActions[0] == 0, "The first action must wait for six full seconds.");
        context.runAfterDelay(120, () -> {
            try {
                VillagerAdvAugments.tick(lane);
                require(supportActions[0] == 1, "Three living starter families allow the highest-XP support to act.");
                VillagerAdvAugments.tick(lane);
                require(supportActions[0] == 1, "One tick cannot trigger another support action.");
                VillagerAdvAugments.resetWave(lane);
                VillagerAdvAugments.tick(lane);
                require(supportActions[0] == 1, "Preparation reset cancels the pending contest.");
                context.succeed();
            } finally {cleanup(lane);}
        });
    }

    @GameTest(maxTicks = 160)
    public void cycleAttacksFiveHighestResonanceTowersWithoutChargingHarmonyOrRepeating(GameTestHelper context) {
        PlayerLane lane = lane(context, "job_resonance_towers_p", "job_resonance_towers_g2");
        GridPosition pos = position(context);
        List<ResonanceTower> high = new ArrayList<>();
        List<ResonanceTower> low = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            GridPosition link = new GridPosition(pos.x() + 1, pos.y(), pos.z());
            ResonanceTower tower = new ResonanceTower(ResonanceTowers.WAVE_CRYSTAL,
                    lane.ownerPlayer(), TeamId.RED, 1, link, link);
            lane.addTower(tower);
            entity(context, tower);
            low.add(tower);
        }
        for (int i = 0; i < 5; i++) {
            ResonanceTower tower = new ResonanceTower(ResonanceTowers.FOCUS_PRISM,
                    lane.ownerPlayer(), TeamId.RED, 1, pos, pos);
            lane.addTower(tower);
            entity(context, tower);
            high.add(tower);
        }
        lane.markWaveStarted(5);
        require(high.stream().allMatch(tower -> tower.resonanceLevel() == 2)
                        && low.stream().allMatch(tower -> tower.resonanceLevel() == 1),
                "Five level-two towers must outrank the three level-one towers inserted first.");
        SemionTowerEntity source = entity(context, high.getFirst());
        SemionMonsterEntity target = monster(context, lane, source.position().add(2, 0, 0));
        SemionMonsterEntity nearby = monster(context, lane, target.position().add(0, 0, 2));
        double damage = source.attackDamageAmount(target);
        attack(source, target);
        attack(source, target);
        double beforeCycle = target.runtimeMonster().health();
        for (Tower tower : lane.towers()) tower.markWaveStarted(5);
        ResonanceService.tickAugments(lane);
        close(beforeCycle, target.runtimeMonster().health(), "Cycle must not attack before six seconds.");
        context.runAfterDelay(119, () -> {
            ResonanceService.tickAugments(lane);
            close(beforeCycle, target.runtimeMonster().health(), "The first cycle must wait all 120 ticks.");
        });
        context.runAfterDelay(120, () -> {
            try {
                ResonanceService.tickAugments(lane);
                close(beforeCycle - damage * 5, target.runtimeMonster().health(),
                        "Exactly the five highest-resonance towers each make one immediate full-damage attack.");
                for (Tower tower : high) close(damage, tower.roundDamageDealt(), "Every selected tower actually attacks.");
                for (Tower tower : low) close(0, tower.roundDamageDealt(), "Lower-resonance towers cannot exceed the five-tower cap.");
                close(10_000, nearby.runtimeMonster().health(), "Cycle attacks must not complete a precharged harmony trigger.");
                ResonanceService.tickAugments(lane);
                close(beforeCycle - damage * 5, target.runtimeMonster().health(), "The same tick cannot trigger a second cycle.");
                attack(source, target);
                close(10_000 - damage * 2, nearby.runtimeMonster().health(),
                        "The next native attack completes harmony, proving cycle attacks neither charged nor consumed it.");
                context.succeed();
            } finally {cleanup(lane);}
        });
    }

    @GameTest(maxTicks = 160)
    public void cycleSkipsOutOfRangeEnemiesAndDoesNotDeferAMissedAttack(GameTestHelper context) {
        PlayerLane lane = lane(context, "job_resonance_towers_p");
        GridPosition pos = position(context);
        ResonanceTower tower = new ResonanceTower(ResonanceTowers.FOCUS_CRYSTAL,
                lane.ownerPlayer(), TeamId.RED, 1, pos, pos);
        lane.addTower(tower);
        SemionTowerEntity source = entity(context, tower);
        lane.markWaveStarted(5);
        SemionMonsterEntity outside = monster(context, lane, source.position().add(source.attackRange() + .5, 0, 0));
        context.runAfterDelay(120, () -> {
            try {
                ResonanceService.tickAugments(lane);
                close(10_000, outside.runtimeMonster().health(), "An immediate cycle attack must respect actual attack range.");
                close(0, tower.roundDamageDealt(), "No target in range means no dealt damage.");
                outside.setPos(source.position().add(1, 0, 0));
                ResonanceService.tickAugments(lane);
                close(10_000, outside.runtimeMonster().health(), "A missed cycle cannot attack a target entering range on the same tick.");
                tower.resetForRound(lane);
                ResonanceService.tickAugments(lane);
                close(10_000, outside.runtimeMonster().health(), "Round reset cancels cycle attacks during preparation.");
                context.succeed();
            } finally {cleanup(lane);}
        });
    }

    private static void attack(SemionTowerEntity source, SemionMonsterEntity target) {
        Tower tower = source.runtimeTower();
        double damage = source.attackDamageAmount(target);
        Tower.DamageResult hit = tower.damagePrimaryAttackTargetResult(source, target, damage);
        AugmentCombat.onPrimaryAttackResolved(source, target, hit);
        source.recordAttack(target, damage, hit.outgoingDamage(), hit.dealtDamage(), hit.killed());
    }

    private static GridPosition position(GameTestHelper context) {
        return GridPosition.from(context.absolutePos(new BlockPos(1, 2, 3)));
    }

    private static SemionTowerEntity entity(GameTestHelper context, EntityBackedTower tower) {
        SemionTowerEntity entity = (SemionTowerEntity) context.getLevel().getEntity(tower.entityId().orElseThrow());
        entity.setNoAi(true);
        entity.setNoGravity(true);
        return entity;
    }

    private static SemionMonsterEntity monster(GameTestHelper context, PlayerLane lane, Vec3 position) {
        Monster monster = new Monster("job_augment_target_" + UUID.randomUUID(), TeamId.RED, 1,
                Optional.empty(), Optional.empty(), 10000, 0, 0, AttackKind.MELEE, "minecraft:zombie", null,
                DamageType.PHYSICAL, 0, SummonTier.T1, List.of(SummonRole.RUSH), 0);
        monster.setOrigin(MonsterOrigin.NATURAL_WAVE);
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, null);
        entity.setNoAi(true);
        entity.setNoGravity(true);
        entity.setPos(position);
        context.getLevel().addFreshEntity(entity);
        monster.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        lane.activeMonsters().add(monster);
        return entity;
    }

    private static PlayerLane lane(GameTestHelper context, String... cards) {
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
        Vec3 spawn = Vec3.atCenterOf(context.absolutePos(new BlockPos(2, 2, 2)));
        Vec3 goal = Vec3.atCenterOf(context.absolutePos(new BlockPos(6, 2, 6)));
        LaneRegionLayout layout = new LaneRegionLayout(1, spawn, List.of(goal), goal,
                BlockBounds.of(context.absolutePos(new BlockPos(0, 1, 0)), context.absolutePos(new BlockPos(7, 6, 7))),
                List.of(GridPosition.from(context.absolutePos(new BlockPos(6, 2, 6)))));
        PlayerLane lane = new PlayerLane(TeamId.RED, 1, UUID.randomUUID(), context.getLevel(), layout);
        lane.assignAugmentSnapshot(new AugmentSnapshot(AugmentConfig.defaults(), Arrays.stream(cards)
                .map(card -> new PlayerAugmentState.Selection(5, AugmentRarity.GOLD, card,
                        PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none())).toList()));
        AreaEffectLaneIndex.register(lane);
        return lane;
    }

    private static void cleanup(PlayerLane lane) {
        for (Monster monster : lane.activeMonsters()) {
            if (monster.hasMinecraftEntity() && lane.arenaWorld().getEntity(monster.minecraftEntityId()) != null) {
                lane.arenaWorld().getEntity(monster.minecraftEntityId()).discard();
            }
        }
        lane.activeMonsters().clear();
        for (Tower tower : List.copyOf(lane.towers())) lane.removeTower(tower);
        AreaEffectLaneIndex.unregister(lane);
        ProductionTowerCatalogs.reloadBuiltIns(TowerBalanceConfig.defaultConfig());
    }

    private static void close(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > .01) throw new AssertionError(message + " expected=" + expected + ", actual=" + actual);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
