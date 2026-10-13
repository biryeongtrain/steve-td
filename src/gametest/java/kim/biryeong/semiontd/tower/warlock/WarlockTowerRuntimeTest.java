package kim.biryeong.semiontd.tower.warlock;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.goal.ApplyTowerTimedEffectGoal;
import kim.biryeong.semiontd.entity.goal.SiegeTrueDamageGoal;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.EconomyService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.game.TowerUpgradeResult;
import kim.biryeong.semiontd.job.WarlockTowerJob;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.summon.SummonBalancePolicy;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import kim.biryeong.semiontd.tower.warlock.WarlockSacrificeTower;
import kim.biryeong.semiontd.tower.warlock.WarlockAwakeningProgress;
import kim.biryeong.semiontd.tower.warlock.WarlockTower;
import kim.biryeong.semiontd.tower.warlock.WarlockTowers;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import kim.biryeong.semiontd.gametest.GameTestParticipantFixture;

public final class WarlockTowerRuntimeTest extends GameTestParticipantFixture {
    @GameTest
    public void warlockIncomeDebuffResistanceReducesNullImpMagnitude(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));
        SemionMonsterEntity caster = spawnSummonEntity(
                context,
                "warlock_resistance_null_imp",
                TeamId.RED,
                TeamId.BLUE,
                1,
                origin,
                100.0,
                0.0
        );
        Vec3 targetPosition = origin.add(2.0, 0.0, 0.0);
        WarlockTower runtimeTower = new WarlockTower(
                TowerBalanceRuntime.resolve(WarlockTowers.RANGED_WARLOCK_TOWER),
                stableUuid("warlock-income-debuff-resistance"),
                TeamId.BLUE,
                1,
                new GridPosition(
                        (int) Math.floor(targetPosition.x),
                        (int) Math.floor(targetPosition.y),
                        (int) Math.floor(targetPosition.z)
                )
        );
        SemionTowerEntity target = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        target.configure(runtimeTower, null);
        target.setPos(targetPosition);
        context.getLevel().addFreshEntity(target);

        new ApplyTowerTimedEffectGoal(
                caster,
                TimedEffectType.TOWER_RANGE_REDUCTION,
                SummonBalancePolicy.NULL_IMP_RANGE_REDUCTION,
                SummonBalancePolicy.NULL_IMP_RANGE_RADIUS,
                SummonBalancePolicy.NULL_IMP_RANGE_DURATION_TICKS,
                SummonBalancePolicy.NULL_IMP_RANGE_COOLDOWN_TICKS,
                SummonBalancePolicy.SUPPORT_HEAL_RETRY_TICKS,
                1
        ).tick();

        if (!assertClose(
                context,
                SummonBalancePolicy.NULL_IMP_RANGE_REDUCTION * 0.70,
                target.activeTimedEffectMagnitude(TimedEffectType.TOWER_RANGE_REDUCTION),
                "Ranged Warlock income-debuff resistance should reduce the applied null-imp magnitude by thirty percent."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void warlockTowerJobLimitsCoreToOneAndAllowsSacrifices(GameTestHelper context) {
        UUID playerId = stableUuid("warlock-job-tower-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, WarlockTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        BlockPos secondTowerPos = nearbyTowerPlacementPos(lane, towerPos);

        Set<String> starterIds = ProductionTowerService.availableTowers(game, playerId).stream()
                .map(entry -> entry.type().id())
                .collect(java.util.stream.Collectors.toSet());
        if (!assertEquals(
                context,
                Set.of(
                        WarlockTowers.BASE_WARLOCK_TOWER.id(),
                        WarlockTowers.T1_SLAVE.id(),
                        WarlockTowers.T1_RANGED_SLAVE.id()
                ),
                starterIds,
                "Warlock job should expose only the core and sacrifice starter towers."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                ProductionTowerService.placeTower(game, playerId, towerPos, WarlockTowers.BASE_WARLOCK_TOWER.id()),
                "Warlock job should be allowed to place the first core tower."
        )) {
            return;
        }
        if (!assertTrue(context, lane.towers().getFirst() instanceof WarlockTower, "Placed warlock core should use WarlockTower runtime behavior.")) {
            return;
        }
        Set<String> upgradeIds = ProductionTowerService.availableUpgrades(game, playerId, towerPos).stream()
                .map(TowerUpgradeOption::targetType)
                .map(TowerType::id)
                .collect(java.util.stream.Collectors.toSet());
        if (!assertEquals(
                context,
                Set.of(WarlockTowers.RANGED_WARLOCK_TOWER.id(), WarlockTowers.MELEE_WARLOCK_TOWER.id()),
                upgradeIds,
                "Base warlock core should branch to ranged and melee cores."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                TowerPlacementResult.TOWER_NOT_ALLOWED,
                ProductionTowerService.placeTower(game, playerId, secondTowerPos, WarlockTowers.BASE_WARLOCK_TOWER.id()),
                "Warlock job should reject a second core tower."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                TowerUpgradeResult.SUCCESS,
                ProductionTowerService.upgradeTower(game, playerId, towerPos, "ranged_warlock_tower"),
                "Warlock core should upgrade to the ranged branch."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                TowerPlacementResult.TOWER_NOT_ALLOWED,
                ProductionTowerService.placeTower(game, playerId, secondTowerPos, WarlockTowers.BASE_WARLOCK_TOWER.id()),
                "Upgraded warlock core should still block an extra core tower."
        )) {
            return;
        }
        if (!assertEquals(
                context,
                TowerPlacementResult.SUCCESS,
                ProductionTowerService.placeTower(game, playerId, secondTowerPos, WarlockTowers.T1_SLAVE.id()),
                "Warlock sacrifice towers should still be placeable while one core exists."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void siegeTrueDamageTriggersWarlockSacrifice(GameTestHelper context) {
        UUID playerId = stableUuid("warlock-siege-damage-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, WarlockTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos corePos = towerPlacementPos(lane);
        WarlockTower core = new WarlockTower(
                TowerBalanceRuntime.resolve(WarlockTowers.BASE_WARLOCK_TOWER),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(corePos)
        );
        WarlockSacrificeTower sacrifice = new WarlockSacrificeTower(
                TowerBalanceRuntime.resolve(WarlockTowers.T1_SLAVE),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(nearbyTowerPlacementPos(lane, corePos))
        );
        if (!assertClose(context, 0.02, TowerBalanceRuntime.ability(core.type().id(), "permanentHealth"), "Requested permanent health growth must be active.")) return;
        if (!assertClose(context, 0.02, TowerBalanceRuntime.ability(core.type().id(), "permanentDamage"), "Requested permanent damage growth must be active.")) return;
        lane.addTower(core);
        lane.addTower(sacrifice);
        core.markWaveStarted(1);
        SemionTowerEntity coreEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(core.entityId().orElseThrow());
        core.syncHealth(10.0);
        coreEntity.setHealth(10.0F);

        SemionMonsterEntity warden = spawnSummonEntity(
                context,
                "warden-special-damage",
                TeamId.BLUE,
                TeamId.RED,
                1,
                coreEntity.position().add(1.0, 0.0, 0.0),
                100.0,
                0.0
        );
        warden.setTarget(coreEntity);
        new SiegeTrueDamageGoal(warden, 50.0, 20, 1, 0.0).tick();

        if (!assertEquals(context, 0.0, sacrifice.health(), "Warlock should absorb an allied tower after siege true damage.")) {
            return;
        }
        if (!assertClose(
                context,
                31.5,
                core.health(),
                "Base warlock should heal only the absorbed max-health increase plus the flat absorption heal."
        )) {
            return;
        }
        if (!assertClose(
                context,
                31.5,
                core.roundMetricsTracker().snapshot().healingDone(),
                "Warlock sacrifice healing should be recorded in round metrics."
        )) {
            return;
        }
        String details = String.join("\n", core.runtimeDetailLines()).replaceAll("<[^>]+>", "");
        if (!assertTrue(
                context,
                details.contains("영구 흡수: 1기") && details.contains("라운드 흡수: 1기"),
                "Base warlock sacrifice should advance both permanent and round progression."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void rangedWarlockAbsorbsLowPriorityTowerAndGainsConfiguredStats(GameTestHelper context) {
        UUID playerId = stableUuid("warlock-ranged-absorb-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, WarlockTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos corePos = towerPlacementPos(lane);
        WarlockTower core = new WarlockTower(
                TowerBalanceRuntime.resolve(WarlockTowers.RANGED_WARLOCK_TOWER),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(corePos)
        );
        if (!assertClose(context, 0.04, TowerBalanceRuntime.ability(core.type().id(), "permanentHealth"), "Requested permanent health growth must be active.")) return;
        if (!assertClose(context, 0.07, TowerBalanceRuntime.ability(core.type().id(), "permanentDamage"), "Requested permanent damage growth must be active.")) return;
        lane.addTower(core);
        BlockPos t1Pos = nearbyTowerPlacementPos(lane, corePos);
        WarlockSacrificeTower t1Ranged = new WarlockSacrificeTower(
                TowerBalanceRuntime.resolve(WarlockTowers.T1_RANGED_SLAVE),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(t1Pos)
        );
        lane.addTower(t1Ranged);
        BlockPos t3Pos = nearbyTowerPlacementPos(lane, t1Pos);
        WarlockSacrificeTower t3Ranged = new WarlockSacrificeTower(
                TowerBalanceRuntime.resolve(WarlockTowers.T3_RANGED_SLAVE),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(t3Pos)
        );
        lane.addTower(t3Ranged);
        int sacrificedEntityId = t3Ranged.entityId().orElseThrow();
        double sacrificedMaxHealth = t3Ranged.currentMaxHealth();
        double sacrificedDamage = t3Ranged.modifyAttackDamage(null, null, t3Ranged.type().damage());
        double expectedMaxHealth = core.type().maxHealth()
                * (1.0 + TowerBalanceRuntime.ability(core.type().id(), "petHealth"))
                + sacrificedMaxHealth * (
                        TowerBalanceRuntime.ability(core.type().id(), "roundStat")
                                + TowerBalanceRuntime.ability(core.type().id(), "permanentHealth")
                );
        double maxHealthBeforeAbsorption = core.type().maxHealth()
                * (1.0 + TowerBalanceRuntime.ability(core.type().id(), "petHealth"));
        double expectedHealthAfterAbsorption = Math.min(
                expectedMaxHealth,
                10.0 + (expectedMaxHealth - maxHealthBeforeAbsorption)
                        + TowerBalanceRuntime.ability(WarlockTower.CONFIG_ID, "absorptionHeal")
        );
        double expectedDamage = (
                core.type().damage()
                        + sacrificedDamage * (
                                TowerBalanceRuntime.ability(core.type().id(), "roundStat")
                                        + TowerBalanceRuntime.ability(core.type().id(), "permanentDamage")
                        )
        ) * (1.0 + TowerBalanceRuntime.ability(core.type().id(), "petDamage"));
        int expectedInterval = Math.max(
                TowerBalanceRuntime.abilityInt(WarlockTower.CONFIG_ID, "minInterval"),
                core.type().attackIntervalTicks() - Math.min(
                        TowerBalanceRuntime.abilityInt(WarlockTower.CONFIG_ID, "speedCap"),
                        core.type().attackIntervalTicks() - t3Ranged.type().attackIntervalTicks()
                )
        );

        SemionTowerEntity coreEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(core.entityId().orElseThrow());
        core.syncHealth(10.0);
        coreEntity.setHealth(10.0F);
        core.onDamaged(coreEntity, null, 50.0, 60.0, 10.0);

        if (!assertClose(context, expectedHealthAfterAbsorption, core.health(), "Ranged warlock should heal its max-health increase plus 30 after a successful absorption.")) {
            return;
        }

        if (!assertTrue(context, lane.towers().contains(t1Ranged), "Ranged warlock should leave the higher-numbered aggro tower alive after absorbing lowest priority.")) {
            return;
        }
        if (!assertTrue(context, lane.towers().contains(t3Ranged), "Ranged warlock sacrifice target should stay in the lane for next-round respawn.")) {
            return;
        }
        if (!assertEquals(context, 0.0, t3Ranged.health(), "Ranged warlock sacrifice target should be dead for the current round.")) {
            return;
        }
        if (!assertClose(context, expectedMaxHealth, core.currentMaxHealth(), "Ranged warlock should gain round, permanent, and surviving-pet health bonuses.")) {
            return;
        }
        if (!assertClose(context, expectedDamage, core.modifyAttackDamage(null, null, core.type().damage()), "Ranged warlock should gain round, permanent, and surviving-pet damage bonuses.")) {
            return;
        }
        if (!assertEquals(context, expectedInterval, core.adjustAttackInterval(core.type().attackIntervalTicks()), "Ranged warlock should gain attack interval reduction from absorbed faster tower.")) {
            return;
        }
        coreEntity.applyTimedEffect(TimedEffectType.TOWER_ATTACK_SPEED_BONUS, 10.0, 40);
        if (!assertEquals(context, 5, coreEntity.attackIntervalTicks(), "Ranged warlock attack interval should never fall below the configured five-tick minimum.")) {
            return;
        }
        core.syncHealth(10.0);
        coreEntity.setHealth(10.0F);
        core.onDamaged(coreEntity, null, 50.0, 60.0, 10.0);
        if (!assertEquals(context, 0.0, t1Ranged.health(), "Ranged warlock should absorb the next living tower instead of reabsorbing a dead target.")) {
            return;
        }
        game.teams().get(TeamId.RED).resetForRound();
        if (!assertEquals(context, 20, core.adjustAttackInterval(20), "Ranged warlock should lose absorbed attack interval reduction after the round.")) {
            return;
        }
        if (!assertClose(context, core.currentMaxHealth(), core.health(), "Ranged warlock should finish the two-phase round reset at full health after pets respawn.")) {
            return;
        }
        if (!assertEquals(context, t3Ranged.currentMaxHealth(), t3Ranged.health(), "Ranged warlock sacrifice target should respawn with full health next round.")) {
            return;
        }
        if (!assertTrue(context, t3Ranged.entityId().isPresent() && t3Ranged.entityId().getAsInt() != sacrificedEntityId, "Ranged warlock sacrifice target should get a fresh entity on next-round respawn.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void meleeWarlockAbsorbsHighPriorityTowerAndGainsConfiguredStats(GameTestHelper context) {
        UUID playerId = stableUuid("warlock-melee-absorb-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, WarlockTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos corePos = towerPlacementPos(lane);
        WarlockTower core = new WarlockTower(
                TowerBalanceRuntime.resolve(WarlockTowers.MELEE_WARLOCK_TOWER),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(corePos)
        );
        if (!assertClose(context, 0.07, TowerBalanceRuntime.ability(core.type().id(), "permanentHealth"), "Requested permanent health growth must be active.")) return;
        if (!assertClose(context, 0.04, TowerBalanceRuntime.ability(core.type().id(), "permanentDamage"), "Requested permanent damage growth must be active.")) return;
        lane.addTower(core);
        BlockPos t1Pos = nearbyTowerPlacementPos(lane, corePos);
        WarlockSacrificeTower t1Melee = new WarlockSacrificeTower(
                TowerBalanceRuntime.resolve(WarlockTowers.T1_SLAVE),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(t1Pos)
        );
        lane.addTower(t1Melee);
        BlockPos t3Pos = nearbyTowerPlacementPos(lane, t1Pos);
        WarlockSacrificeTower t3Melee = new WarlockSacrificeTower(
                TowerBalanceRuntime.resolve(WarlockTowers.T3_SLAVE),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(t3Pos)
        );
        lane.addTower(t3Melee);
        int sacrificedEntityId = t3Melee.entityId().orElseThrow();
        double sacrificedMaxHealth = t3Melee.currentMaxHealth();
        double sacrificedDamage = t3Melee.modifyAttackDamage(null, null, t3Melee.type().damage());
        double expectedMaxHealth = core.type().maxHealth()
                * (1.0 + TowerBalanceRuntime.ability(core.type().id(), "petHealth"))
                + sacrificedMaxHealth * (
                        TowerBalanceRuntime.ability(core.type().id(), "roundStat")
                                + TowerBalanceRuntime.ability(core.type().id(), "permanentHealth")
                );
        double maxHealthBeforeAbsorption = core.type().maxHealth()
                * (1.0 + TowerBalanceRuntime.ability(core.type().id(), "petHealth"));
        double expectedHealthAfterAbsorption = Math.min(
                expectedMaxHealth,
                20.0 + (expectedMaxHealth - maxHealthBeforeAbsorption)
                        + TowerBalanceRuntime.ability(WarlockTower.CONFIG_ID, "absorptionHeal")
        );
        double expectedDamage = (
                core.type().damage()
                        + sacrificedDamage * (
                                TowerBalanceRuntime.ability(core.type().id(), "roundStat")
                                        + TowerBalanceRuntime.ability(core.type().id(), "permanentDamage")
                        )
        ) * (1.0 + TowerBalanceRuntime.ability(core.type().id(), "petDamage"));

        SemionTowerEntity coreEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(core.entityId().orElseThrow());
        core.syncHealth(20.0);
        coreEntity.setHealth(20.0F);
        core.onDamaged(coreEntity, null, 80.0, 100.0, 20.0);

        if (!assertClose(context, expectedHealthAfterAbsorption, core.health(), "Melee warlock should heal its max-health increase plus 30 after a successful absorption.")) {
            return;
        }

        if (!assertTrue(context, lane.towers().contains(t1Melee), "Melee warlock should leave the lower-priority sacrifice tower alive.")) {
            return;
        }
        if (!assertTrue(context, lane.towers().contains(t3Melee), "Melee warlock sacrifice target should stay in the lane for next-round respawn.")) {
            return;
        }
        if (!assertEquals(context, 0.0, t3Melee.health(), "Melee warlock sacrifice target should be dead for the current round.")) {
            return;
        }
        if (!assertClose(context, expectedMaxHealth, core.currentMaxHealth(), "Melee warlock should gain round, permanent, and surviving-sacrifice health bonuses.")) {
            return;
        }
        if (!assertClose(context, expectedDamage, core.modifyAttackDamage(null, null, core.type().damage()), "Melee warlock should gain round, permanent, and surviving-sacrifice damage bonuses.")) {
            return;
        }
        if (!assertEquals(context, 19, core.adjustAttackInterval(20), "Melee warlock should gain one tick of attack interval reduction per round sacrifice.")) {
            return;
        }
        coreEntity.applyTimedEffect(TimedEffectType.TOWER_ATTACK_SPEED_BONUS, 10.0, 40);
        if (!assertEquals(context, 5, coreEntity.attackIntervalTicks(), "Melee warlock attack interval should never fall below the configured five-tick minimum.")) {
            return;
        }
        if (!assertClose(context, 100.0, core.modifyIncomingDamage(null, null, 100.0), "Melee warlock should not reduce incoming damage before five absorbed towers.")) {
            return;
        }
        t1Melee.syncHealth(0.0);
        game.teams().get(TeamId.RED).resetForRound();
        if (!assertEquals(context, 20, core.adjustAttackInterval(20), "Melee warlock should lose sacrifice attack interval reduction after the round.")) {
            return;
        }
        if (!assertClose(context, core.currentMaxHealth(), core.health(), "Melee warlock should finish the two-phase round reset at full health after sacrifices respawn.")) {
            return;
        }
        if (!assertEquals(context, t3Melee.currentMaxHealth(), t3Melee.health(), "Melee warlock sacrifice target should respawn with full health next round.")) {
            return;
        }
        if (!assertTrue(context, t3Melee.entityId().isPresent() && t3Melee.entityId().getAsInt() != sacrificedEntityId, "Melee warlock sacrifice target should get a fresh entity on next-round respawn.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void rangedWarlockAbsorbsSheepOnDamageWithoutCrossFamilyPassive(GameTestHelper context) {
        assertCrossFamilyAbsorption(context, WarlockTowers.RANGED_WARLOCK_TOWER, WarlockTowers.T1_SLAVE);
    }

    @GameTest
    public void meleeWarlockAbsorbsBatOnDamageWithoutCrossFamilyPassive(GameTestHelper context) {
        assertCrossFamilyAbsorption(context, WarlockTowers.MELEE_WARLOCK_TOWER, WarlockTowers.T1_RANGED_SLAVE);
    }

    private void assertCrossFamilyAbsorption(GameTestHelper context, TowerType coreType, TowerType petType) {
        UUID playerId = stableUuid("warlock-cross-family-" + coreType.id());
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, WarlockTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos corePos = towerPlacementPos(lane);
        WarlockTower core = new WarlockTower(TowerBalanceRuntime.resolve(coreType), playerId,
                TeamId.RED, 1, GridPosition.from(corePos));
        WarlockSacrificeTower pet = new WarlockSacrificeTower(TowerBalanceRuntime.resolve(petType), playerId,
                TeamId.RED, 1, GridPosition.from(nearbyTowerPlacementPos(lane, corePos)));
        lane.addTower(core);
        lane.addTower(pet);
        core.markWaveStarted(1);
        if (!assertClose(context, core.type().maxHealth(), core.currentMaxHealth(),
                "Opposite-family pets should not grant passive health before absorption.")) {
            return;
        }
        if (!assertClose(context, core.type().damage(), core.modifyAttackDamage(null, null, core.type().damage()),
                "Opposite-family pets should not grant passive damage before absorption.")) {
            return;
        }
        double expectedMaxHealth = core.type().maxHealth() + pet.currentMaxHealth()
                * (TowerBalanceRuntime.ability(core.type().id(), "roundStat")
                + TowerBalanceRuntime.ability(core.type().id(), "permanentHealth"));
        double expectedDamage = core.type().damage()
                + pet.modifyAttackDamage(null, null, pet.type().damage())
                * (TowerBalanceRuntime.ability(core.type().id(), "roundStat")
                + TowerBalanceRuntime.ability(core.type().id(), "permanentDamage"));
        SemionTowerEntity coreEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(core.entityId().orElseThrow());
        core.syncHealth(20.0);
        coreEntity.setHealth(20.0F);
        coreEntity.hurtIgnoringReductions(coreEntity.damageSources().generic(), 1.0F);
        if (!assertEquals(context, 0.0, pet.health(), "Damage should trigger absorption of the opposite-family pet.")) {
            return;
        }
        if (!assertClose(context, expectedMaxHealth, core.currentMaxHealth(),
                "Cross-family absorption should grant the configured health growth.")) {
            return;
        }
        if (!assertClose(context, expectedDamage, core.modifyAttackDamage(null, null, core.type().damage()),
                "Cross-family absorption should grant the configured damage growth.")) {
            return;
        }
        game.teams().get(TeamId.RED).resetForRound();
        if (!assertClose(context, pet.currentMaxHealth(), pet.health(),
                "Cross-family sacrifices should respawn next round.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void warlockSacrificeRejectsInvalidTargetsWithoutGrowth(GameTestHelper context) {
        UUID playerId = stableUuid("warlock-invalid-sacrifice-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, WarlockTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos corePos = towerPlacementPos(lane);
        WarlockTower core = new WarlockTower(
                TowerBalanceRuntime.resolve(WarlockTowers.RANGED_WARLOCK_TOWER),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(corePos)
        );
        WarlockSacrificeTower foreign = new WarlockSacrificeTower(
                TowerBalanceRuntime.resolve(WarlockTowers.T1_RANGED_SLAVE),
                stableUuid("warlock-invalid-sacrifice-foreign"),
                TeamId.RED,
                1,
                GridPosition.from(nearbyTowerPlacementPos(lane, corePos))
        );
        WarlockSacrificeTower distant = new WarlockSacrificeTower(
                TowerBalanceRuntime.resolve(WarlockTowers.T1_RANGED_SLAVE),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(corePos.offset(30, 0, 0))
        );
        WarlockTower otherCore = new WarlockTower(
                TowerBalanceRuntime.resolve(WarlockTowers.BASE_WARLOCK_TOWER),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(corePos.offset(2, 0, 2))
        );
        lane.addTower(core);
        lane.addTower(foreign);
        lane.addTower(distant);
        lane.addTower(otherCore);

        SemionTowerEntity coreEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(core.entityId().orElseThrow());
        core.syncHealth(100.0);
        coreEntity.setHealth(100.0F);
        core.onDamaged(coreEntity, null, 10.0, 110.0, 100.0);

        if (!assertTrue(context, foreign.health() > 0.0, "Warlock must not sacrifice another owner's tower.")) {
            return;
        }
        if (!assertTrue(context, distant.health() > 0.0, "Warlock must not sacrifice a tower outside the configured radius.")) {
            return;
        }
        if (!assertTrue(context, otherCore.health() > 0.0, "Warlock must never sacrifice another core tower.")) {
            return;
        }
        if (!assertClose(context, 100.0, core.health(), "Failed sacrifice must not heal or grow the warlock.")) {
            return;
        }
        String details = String.join("\n", core.runtimeDetailLines()).replaceAll("<[^>]+>", "");
        if (!assertTrue(context, details.contains("영구 흡수: 0기") && details.contains("라운드 흡수: 0기"), "Failed sacrifice must not advance absorption progress.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void rangedWarlockAwakensWithoutSacrificeRequirementAndResetsNextRound(GameTestHelper context) {
        UUID playerId = stableUuid("warlock-ranged-awakening-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, WarlockTowerJob.ID);
        for (int kill = 0; kill < 1399; kill++) {
            WarlockAwakeningProgress.recordKill(playerId);
        }
        if (!assertEquals(context, 1399L, WarlockAwakeningProgress.snapshot(playerId).kills(), "Warlock awakening progress should remain locked before the configured kill requirement.")) {
            return;
        }
        PlayerLane lane = redLane(game, 1);
        BlockPos corePos = towerPlacementPos(lane);
        WarlockTower core = new WarlockTower(
                TowerBalanceRuntime.resolve(WarlockTowers.RANGED_WARLOCK_TOWER),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(corePos)
        );
        lane.addTower(core);
        SemionTowerEntity coreEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(core.entityId().orElseThrow());
        core.syncHealth(40.0);
        coreEntity.setHealth(40.0F);

        String lockedDetails = String.join("\n", core.runtimeDetailLines()).replaceAll("<[^>]+>", "");
        if (!assertTrue(context, lockedDetails.contains("각성 해금: 1399/1400킬"), "Ranged awakening should stay locked before the kill requirement.")) {
            return;
        }
        Monster creditedKill = new Monster(
                "warlock-awakening-progress",
                TeamId.RED,
                1,
                Optional.empty(),
                Optional.empty(),
                20.0,
                0.0,
                5.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                1L
        );
        creditedKill.recordLastHit(playerId, KillSourceKind.TOWER);
        creditedKill.syncHealth(0.0);
        new EconomyService(game.economyConfig(), game).awardMonsterKillReward(creditedKill, game.players());
        if (!assertEquals(context, 1400L, WarlockAwakeningProgress.snapshot(playerId).kills(), "The credited 1400th kill should unlock awakening.")) {
            return;
        }

        String awakenedDetails = String.join("\n", core.runtimeDetailLines()).replaceAll("<[^>]+>", "");
        if (!assertTrue(context, awakenedDetails.contains("각성 상태: 각성 완료"), "The 1400th kill should immediately awaken a ranged warlock that already satisfies the combat conditions.")) {
            return;
        }
        if (!assertTrue(context, awakenedDetails.contains("라운드 흡수: 0기"), "Ranged awakening should not require sacrifices.")) {
            return;
        }
        if (!assertTrue(context, coreEntity.isCurrentlyGlowing(), "Awakened ranged warlock should expose its active VFX state.")) {
            return;
        }
        if (!assertTrue(context, core.health() > 40.0, "Ranged awakening should apply its configured recovery.")) {
            return;
        }
        if (!assertTrue(context, awakenedDetails.contains("재생: +40 HP/s"), "Ranged awakening should expose its regeneration in the tower UI.")) {
            return;
        }
        if (!assertTrue(context, awakenedDetails.indexOf("디버프 저항:") < awakenedDetails.indexOf("재생:"), "Ranged awakening regeneration should appear below debuff resistance.")) {
            return;
        }

        game.teams().get(TeamId.RED).resetForRound();
        String resetDetails = String.join("\n", core.runtimeDetailLines()).replaceAll("<[^>]+>", "");
        if (!assertTrue(context, resetDetails.contains("각성 해금: 완료"), "Ranged awakening should remain unlocked after round reset.")) {
            return;
        }
        if (!assertTrue(context, !coreEntity.isCurrentlyGlowing(), "Ranged awakening glow should clear at round reset.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void rangedWarlockUsesPostSacrificeHealthForAwakening(GameTestHelper context) {
        UUID playerId = stableUuid("warlock-post-sacrifice-awakening-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, WarlockTowerJob.ID);
        for (int kill = 0; kill < 1400; kill++) {
            WarlockAwakeningProgress.recordKill(playerId);
        }
        PlayerLane lane = redLane(game, 1);
        BlockPos corePos = towerPlacementPos(lane);
        WarlockTower core = new WarlockTower(
                TowerBalanceRuntime.resolve(WarlockTowers.RANGED_WARLOCK_TOWER),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(corePos)
        );
        WarlockSacrificeTower sacrifice = new WarlockSacrificeTower(
                TowerBalanceRuntime.resolve(WarlockTowers.T1_RANGED_SLAVE),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(nearbyTowerPlacementPos(lane, corePos))
        );
        lane.addTower(core);
        lane.addTower(sacrifice);

        SemionTowerEntity coreEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(core.entityId().orElseThrow());
        core.syncHealth(40.0);
        coreEntity.setHealth(40.0F);
        core.onDamaged(coreEntity, null, 1.0, 41.0, 40.0);

        if (!assertTrue(context, sacrifice.health() <= 0.0, "Low health should still trigger the ranged sacrifice.")) {
            return;
        }
        double postSacrificeHealthRatio = core.health() / core.currentMaxHealth();
        if (!assertTrue(context, postSacrificeHealthRatio > 0.40, "Sacrifice recovery should raise the current health ratio above the awakening threshold.")) {
            return;
        }
        String details = String.join("\n", core.runtimeDetailLines()).replaceAll("<[^>]+>", "");
        if (!assertTrue(context, !details.contains("각성 상태: 각성 완료"), "Awakening must use post-sacrifice health instead of the stale damaged ratio.")) {
            return;
        }
        if (!assertTrue(context, !coreEntity.isCurrentlyGlowing(), "A warlock above the post-sacrifice health threshold must not enter awakening VFX.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void meleeWarlockAwakeningCreatesRoundBurstAndResetsNextRound(GameTestHelper context) {
        UUID playerId = stableUuid("warlock-melee-awakening-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, WarlockTowerJob.ID);
        for (int kill = 0; kill < 1400; kill++) {
            WarlockAwakeningProgress.recordKill(playerId);
        }
        PlayerLane lane = redLane(game, 1);
        BlockPos corePos = towerPlacementPos(lane);
        WarlockTower core = new WarlockTower(
                TowerBalanceRuntime.resolve(WarlockTowers.MELEE_WARLOCK_TOWER),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(corePos)
        );
        lane.addTower(core);
        for (int index = 0; index < 20; index++) {
            BlockPos sacrificePos = corePos.offset(index % 5 + 1, 0, index / 5 + 1);
            lane.addTower(new WarlockSacrificeTower(
                    TowerBalanceRuntime.resolve(WarlockTowers.T1_SLAVE),
                    playerId,
                    TeamId.RED,
                    1,
                    GridPosition.from(sacrificePos)
            ));
        }

        SemionTowerEntity coreEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(core.entityId().orElseThrow());
        for (int index = 0; index < 20; index++) {
            core.syncHealth(70.0);
            coreEntity.setHealth(70.0F);
            core.onDamaged(coreEntity, null, 1.0, 71.0, 70.0);
        }

        double awakenedDamage = core.modifyAttackDamage(null, null, core.type().damage());
        String awakenedDetails = String.join("\n", core.runtimeDetailLines()).replaceAll("<[^>]+>", "");
        if (!assertTrue(context, awakenedDetails.contains("각성 상태: 각성 완료"), "Melee warlock should awaken after satisfying all three conditions.")) {
            return;
        }
        if (!assertTrue(context, awakenedDamage >= 82.0, "Melee awakening should add its configured 75 attack damage burst.")) {
            return;
        }
        if (!assertTrue(context, core.health() > 800.0, "Melee awakening should apply its configured 800 health recovery.")) {
            return;
        }
        if (!assertClose(context, 1.30, core.adjustMovementSpeed(1.0), "Melee awakening should add thirty percent movement speed.")) {
            return;
        }
        if (!assertTrue(context, awakenedDetails.contains("피해: 75"), "Melee awakening should expose its attack damage bonus in the tower UI.")) {
            return;
        }
        if (!assertTrue(context, awakenedDetails.contains("이동 속도: +30%"), "Melee awakening should expose its movement speed bonus in the tower UI.")) {
            return;
        }
        if (!assertTrue(context,
                awakenedDetails.indexOf("디버프 저항:") < awakenedDetails.indexOf("피해: 75")
                        && awakenedDetails.indexOf("피해: 75") < awakenedDetails.indexOf("이동 속도: +30%"),
                "Melee awakening bonuses should appear below debuff resistance in damage and movement-speed order.")) {
            return;
        }

        game.teams().get(TeamId.RED).resetForRound();
        if (!assertClose(context, 1.0, core.adjustMovementSpeed(1.0), "Melee awakening movement speed should reset after the round.")) {
            return;
        }
        if (!assertTrue(context, core.modifyAttackDamage(null, null, core.type().damage()) < awakenedDamage, "Melee awakening damage should reset after the round.")) {
            return;
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120)
    public void warlockTowerMovesTowardOutOfRangeMonster(GameTestHelper context) {
        UUID playerId = stableUuid("warlock-move-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, WarlockTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos corePos = towerPlacementPos(lane);
        WarlockTower core = new WarlockTower(
                TowerBalanceRuntime.resolve(WarlockTowers.MELEE_WARLOCK_TOWER),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(corePos)
        );
        lane.addTower(core);
        SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(core.entityId().orElseThrow());
        Vec3 initialTowerPosition = towerEntity.position();
        SemionMonsterEntity target = spawnSummonEntity(
                context,
                "warlock-move-target",
                TeamId.BLUE,
                TeamId.RED,
                1,
                initialTowerPosition.add(8.0, 0.0, 0.0),
                200.0,
                0.0
        );
        target.setNoAi(true);
        double initialDistance = initialTowerPosition.distanceTo(target.position());

        context.runAfterDelay(40, () -> {
            if (!(lane.arenaWorld().getEntity(core.entityId().orElseThrow()) instanceof SemionTowerEntity currentTowerEntity)) {
                context.fail(Component.literal("Warlock tower entity should still exist while checking movement."));
                return;
            }
            Vec3 currentTowerPosition = currentTowerEntity.position();
            if (!assertTrue(
                    context,
                    currentTowerPosition.distanceTo(initialTowerPosition) > 0.1,
                    "Warlock tower should move away from its initial position toward an out-of-range target."
            )) {
                return;
            }
            if (!assertTrue(
                    context,
                    currentTowerPosition.distanceTo(target.position()) < initialDistance,
                    "Warlock tower should get closer to the out-of-range target."
            )) {
                return;
            }
            context.succeed();
        });
    }

    @GameTest
    public void warlockSacrificeSlaveDeathAppliesMonsterEffect(GameTestHelper context) {
        UUID playerId = stableUuid("warlock-slave-death-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, WarlockTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);
        WarlockSacrificeTower tower = new WarlockSacrificeTower(
                TowerBalanceRuntime.resolve(WarlockTowers.T2_SLAVE),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(towerPos)
        );
        lane.addTower(tower);
        SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(tower.entityId().orElseThrow());
        SemionMonsterEntity monster = spawnSummonEntity(
                context,
                "warlock-slave-death-target",
                TeamId.BLUE,
                TeamId.RED,
                1,
                towerEntity.position().add(1.0, 0.0, 0.0),
                100.0,
                0.0
        );

        towerEntity.setHealth(0.0F);
        lane.tick(context.getLevel().getServer());

        if (!assertTrue(context, lane.towers().contains(tower), "Destroyed sacrifice tower should stay in the lane until round reset.")) {
            return;
        }
        if (!assertEquals(context, 0.0, tower.health(), "Destroyed sacrifice tower runtime health should sync to zero before reset.")) {
            return;
        }
        if (!assertClose(
                context,
                0.10,
                monster.activeTimedEffectMagnitude(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS),
                "Sacrifice tower death should apply configured monster damage-taken bonus."
        )) {
            return;
        }
        if (!assertTrue(
                context,
                monster.activeTimedEffectTicks(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS) > 0,
                "Sacrifice tower death effect should have a positive duration."
        )) {
            return;
        }
        int originalEntityId = tower.entityId().orElseThrow();
        game.teams().get(TeamId.RED).resetForRound();
        if (!assertTrue(context, tower.entityId().isPresent(), "Destroyed sacrifice tower should respawn a tower entity on round reset.")) {
            return;
        }
        if (!assertTrue(context, tower.entityId().getAsInt() != originalEntityId, "Respawned sacrifice tower should use a fresh entity id.")) {
            return;
        }
        if (!assertEquals(context, tower.currentMaxHealth(), tower.health(), "Respawned sacrifice tower should reset to full health.")) {
            return;
        }
        context.succeed();
    }
}
