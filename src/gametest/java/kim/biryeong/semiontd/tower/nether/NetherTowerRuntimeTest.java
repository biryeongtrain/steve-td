package kim.biryeong.semiontd.tower.nether;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.job.NetherTowerJob;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.tower.nether.NetherTower;
import kim.biryeong.semiontd.tower.nether.NetherTowers;
import kim.biryeong.semiontd.trait.BuiltInTraits;
import kim.biryeong.semiontd.trait.TraitLoadout;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import kim.biryeong.semiontd.gametest.GameTestParticipantFixture;

public final class NetherTowerRuntimeTest extends GameTestParticipantFixture {
    @GameTest
    public void criticalPiglinBruteBonusesTankAndHighHealthTargets(GameTestHelper context) {
        NetherTower tower = new NetherTower(
                TowerBalanceRuntime.resolve(NetherTowers.T3_PIGLIN_BRUTE),
                stableUuid("piglin-brute-bonus-owner"),
                TeamId.RED,
                1,
                new GridPosition(0, 64, 0)
        );
        tower.syncHealth(tower.currentMaxHealth() * 0.30);

        SemionMonsterEntity normal = spawnRoleMonsterEntity(
                context, "piglin-brute-normal", Optional.empty(), TeamId.RED, 1,
                Vec3.ZERO, 100.0, List.of(SummonRole.RUSH)
        );
        SemionMonsterEntity tank = spawnRoleMonsterEntity(
                context, "piglin-brute-tank", Optional.empty(), TeamId.RED, 1,
                Vec3.ZERO.add(1.0, 0.0, 0.0), 100.0, List.of(SummonRole.TANK)
        );
        SemionMonsterEntity highHealth = spawnRoleMonsterEntity(
                context, "piglin-brute-high-health", Optional.empty(), TeamId.RED, 1,
                Vec3.ZERO.add(2.0, 0.0, 0.0), 200.0, List.of(SummonRole.RUSH)
        );
        SemionMonsterEntity income = spawnRoleMonsterEntity(
                context, "piglin-brute-income", Optional.of(TeamId.BLUE), TeamId.RED, 1,
                Vec3.ZERO.add(3.0, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH)
        );

        if (!assertClose(context, 100.0, tower.modifyAttackDamage(null, normal, 100.0), "Piglin brute should not bonus ordinary targets.")) {
            return;
        }
        if (!assertClose(context, 175.0, tower.modifyAttackDamage(null, tank, 100.0), "Critical piglin brute should deal 75% bonus damage to tank targets.")) {
            return;
        }
        if (!assertClose(context, 175.0, tower.modifyAttackDamage(null, highHealth, 100.0), "Critical piglin brute should deal 75% bonus damage to high-health targets.")) {
            return;
        }
        if (!assertClose(context, 200.0, tower.modifyAttackDamage(null, income, 100.0), "Piglin brute should retain the piglin income damage bonus.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void criticalGhastAppliesTwentyPercentDamageTakenMark(GameTestHelper context) {
        NetherTower tower = new NetherTower(
                TowerBalanceRuntime.resolve(NetherTowers.T3_GHAST),
                stableUuid("ghast-mark-owner"),
                TeamId.RED,
                1,
                new GridPosition(0, 64, 0)
        );
        tower.syncHealth(tower.currentMaxHealth() * 0.30);
        SemionMonsterEntity target = spawnRoleMonsterEntity(
                context, "ghast-mark-target", Optional.empty(), TeamId.RED, 1,
                Vec3.ZERO, 100.0, List.of(SummonRole.RUSH)
        );

        tower.onAttack(null, target, 10.0, false);

        if (!assertClose(
                context,
                0.40,
                target.activeTimedEffectMagnitude(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS),
                "Critical ghast should apply a 40% tower-damage-taken mark."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void criticalMagmaCubePulseScalesWithBaseAttackDamage(GameTestHelper context) {
        UUID playerId = stableUuid("magma-pulse-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, NetherTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        lane.assignTraitLoadout(new TraitLoadout(BuiltInTraits.IGNITE_ID, BuiltInTraits.NONE_ID));
        NetherTower tower = new NetherTower(
                TowerBalanceRuntime.resolve(NetherTowers.T1_MAGMA_CUBE),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(towerPlacementPos(lane))
        );
        lane.addTower(tower);
        tower.syncHealth(tower.currentMaxHealth() * 0.30);
        SemionTowerEntity towerEntity = lane.arenaWorld().getEntity(tower.entityId().orElseThrow()) instanceof SemionTowerEntity entity
                ? entity
                : null;
        if (!assertPresent(context, Optional.ofNullable(towerEntity), "Placed magma cube tower entity should exist.")) {
            return;
        }
        SemionMonsterEntity target = spawnRoleMonsterEntity(
                context,
                "magma-pulse-target",
                Optional.empty(),
                TeamId.RED,
                1,
                towerEntity.position().add(4.0, 0.0, 0.0),
                100.0,
                100.0,
                0.0,
                List.of(SummonRole.RUSH)
        );

        tower.onAttack(towerEntity, target, tower.type().damage(), false);

        if (!assertClose(context, 91.0, target.getHealth(), "Magma cube pulse should use magic resistance instead of 100 armor.")) {
            return;
        }
        if (!assertTrue(context, target.activeTimedEffectTicks(TimedEffectType.MONSTER_IGNITED) == 0,
                "Magma cube magic pulse should not apply ignite.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void netherTransitionPulseAndExtraAttackUseMagicDamageWithoutIgnite(GameTestHelper context) {
        UUID playerId = stableUuid("nether-magic-abilities-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, NetherTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        lane.assignTraitLoadout(new TraitLoadout(BuiltInTraits.IGNITE_ID, BuiltInTraits.NONE_ID));

        GridPosition magmaPosition = GridPosition.from(towerPlacementPos(lane));
        NetherTower magmaCube = new NetherTower(
                TowerBalanceRuntime.resolve(NetherTowers.T1_MAGMA_CUBE),
                playerId,
                TeamId.RED,
                1,
                magmaPosition
        );
        lane.addTower(magmaCube);
        SemionTowerEntity magmaEntity = (SemionTowerEntity) lane.arenaWorld()
                .getEntity(magmaCube.entityId().orElseThrow());
        SemionMonsterEntity transitionTarget = spawnRoleMonsterEntity(
                context,
                "nether-transition-magic-target",
                Optional.empty(),
                TeamId.RED,
                1,
                magmaEntity.position().add(1.0, 0.0, 0.0),
                1_000.0,
                100.0,
                0.0,
                List.of(SummonRole.RUSH)
        );
        transitionTarget.setNoAi(true);
        lane.activeMonsters().add(transitionTarget.runtimeMonster());
        magmaCube.syncHealth(0.01);
        magmaEntity.setHealth(0.01F);
        magmaCube.tick(lane);

        double transitionBaseDamage = magmaCube.type().damage()
                * TowerBalanceRuntime.ability(magmaCube.type().id(), "zombieTransitionPulseDamageRatio");
        double transitionDamage = magmaCube.resolveOutgoingDamage(magmaEntity, transitionTarget, transitionBaseDamage);
        if (!assertClose(context, 1_000.0 - transitionDamage, transitionTarget.getHealth(),
                "Zombie transition pulse should use magic resistance instead of armor.")) {
            return;
        }
        if (!assertTrue(context, transitionTarget.activeTimedEffectTicks(TimedEffectType.MONSTER_IGNITED) == 0,
                "Zombie transition magic pulse should not apply ignite.")) {
            return;
        }
        transitionTarget.discard();

        GridPosition blazePosition = new GridPosition(magmaPosition.x() + 8, magmaPosition.y(), magmaPosition.z());
        NetherTower blaze = new NetherTower(
                TowerBalanceRuntime.resolve(NetherTowers.T2_BLAZE),
                playerId,
                TeamId.RED,
                1,
                blazePosition
        );
        lane.addTower(blaze);
        blaze.syncHealth(blaze.currentMaxHealth() * 0.30);
        SemionTowerEntity blazeEntity = (SemionTowerEntity) lane.arenaWorld()
                .getEntity(blaze.entityId().orElseThrow());
        SemionMonsterEntity extraTarget = spawnRoleMonsterEntity(
                context,
                "nether-extra-magic-target",
                Optional.empty(),
                TeamId.RED,
                1,
                blazeEntity.position().add(2.0, 0.0, 0.0),
                1_000.0,
                100.0,
                0.0,
                List.of(SummonRole.RUSH)
        );
        extraTarget.setNoAi(true);
        int extraAttackEvery = TowerBalanceRuntime.abilityInt(blaze.type().id(), "extraAttackEvery");
        for (int attack = 1; attack < extraAttackEvery; attack++) {
            blaze.syncHealth(blaze.currentMaxHealth() * 0.30);
            blazeEntity.setHealth((float) blaze.health());
            blaze.onAttack(blazeEntity, extraTarget, blaze.type().damage(), false);
        }
        blaze.syncHealth(blaze.currentMaxHealth() * 0.30);
        blazeEntity.setHealth((float) blaze.health());
        double beforeExtraAttack = extraTarget.getHealth();
        blaze.onAttack(blazeEntity, extraTarget, blaze.type().damage(), false);
        double expectedExtraDamage = blaze.resolveOutgoingDamage(
                blazeEntity,
                extraTarget,
                blaze.type().damage() * TowerBalanceRuntime.ability(blaze.type().id(), "extraAttackDamageRatio")
        );
        if (!assertClose(context, beforeExtraAttack - expectedExtraDamage, extraTarget.getHealth(),
                "Blaze extra attack should use magic resistance instead of armor.")) {
            return;
        }
        if (!assertTrue(context, extraTarget.activeTimedEffectTicks(TimedEffectType.MONSTER_IGNITED) == 0,
                "Blaze magic extra attack should not apply ignite.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void ghastAttackSpeedScalesWithMissingHealth(GameTestHelper context) {
        UUID playerId = stableUuid("ghast-missing-health-speed-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, NetherTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        NetherTower tower = new NetherTower(
                TowerBalanceRuntime.resolve(NetherTowers.T3_GHAST),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(towerPlacementPos(lane))
        );
        lane.addTower(tower);
        tower.syncHealth(tower.currentMaxHealth() * 0.50);
        SemionTowerEntity towerEntity = lane.arenaWorld().getEntity(tower.entityId().orElseThrow()) instanceof SemionTowerEntity entity
                ? entity
                : null;
        if (!assertPresent(context, Optional.ofNullable(towerEntity), "Placed ghast tower entity should exist.")) {
            return;
        }

        tower.tick(lane);

        if (!assertClose(
                context,
                0.375,
                towerEntity.activeTimedEffectMagnitude(TimedEffectType.TOWER_ATTACK_SPEED_BONUS),
                "Ghast at half health should receive half of its 75% attack-speed cap."
        )) {
            return;
        }
        if (!assertEquals(context, 8, towerEntity.attackIntervalTicks(), "Ghast attack interval should reflect the missing-health speed bonus.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void witherPrioritizesBossTargetsThenLowestHealthTargets(GameTestHelper context) {
        NetherTower tower = new NetherTower(
                TowerBalanceRuntime.resolve(NetherTowers.T3_WITHER),
                stableUuid("wither-priority-owner"),
                TeamId.RED,
                1,
                new GridPosition(0, 64, 0)
        );
        SemionMonsterEntity boss = spawnRoleMonsterEntity(
                context, "wither-priority-boss", Optional.empty(), TeamId.RED, 1,
                Vec3.ZERO, 600.0, List.of(SummonRole.TANK)
        );
        SemionMonsterEntity lowHealth = spawnRoleMonsterEntity(
                context, "wither-priority-low", Optional.empty(), TeamId.RED, 1,
                Vec3.ZERO.add(1.0, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH)
        );
        lowHealth.setHealth(10.0F);
        SemionMonsterEntity ordinary = spawnRoleMonsterEntity(
                context, "wither-priority-ordinary", Optional.empty(), TeamId.RED, 1,
                Vec3.ZERO.add(2.0, 0.0, 0.0), 200.0, List.of(SummonRole.RUSH)
        );

        SemionMonsterEntity selectedBoss = tower.selectAttackTarget(null, List.of(lowHealth, boss)).orElse(null);
        if (!assertEquals(context, boss, selectedBoss, "Wither should prioritize monsters above its high-health threshold.")) {
            return;
        }
        SemionMonsterEntity selectedLowHealth = tower.selectAttackTarget(null, List.of(ordinary, lowHealth)).orElse(null);
        if (!assertEquals(context, lowHealth, selectedLowHealth, "Wither should fall back to the lowest-health target when no boss target exists.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void destroyedNetherTowerRespawnsWithConfiguredProxy(GameTestHelper context) {
        UUID playerId = stableUuid("nether-proxy-respawn-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, NetherTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        NetherTower tower = new NetherTower(
                TowerBalanceRuntime.resolve(NetherTowers.T1_STRIDER),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(towerPlacementPos(lane))
        );
        lane.addTower(tower);

        int originalEntityId = tower.entityId().orElseThrow();
        if (!(lane.arenaWorld().getEntity(originalEntityId) instanceof SemionTowerEntity originalEntity)) {
            context.fail(Component.literal("Placed nether tower entity should exist."));
            return;
        }
        if (!assertEquals(context, net.minecraft.world.entity.EntityTypes.STRIDER, originalEntity.getPolymerEntityType(null), "Initial nether tower proxy should be a strider.")) {
            return;
        }

        originalEntity.discard();
        game.teams().get(TeamId.RED).resetForRound();

        int respawnedEntityId = tower.entityId().orElseThrow();
        if (!assertTrue(context, respawnedEntityId != originalEntityId, "Destroyed nether tower should use a fresh entity id after reset.")) {
            return;
        }
        if (!(lane.arenaWorld().getEntity(respawnedEntityId) instanceof SemionTowerEntity respawnedEntity)) {
            context.fail(Component.literal("Respawned nether tower entity should exist."));
            return;
        }
        if (!assertEquals(context, net.minecraft.world.entity.EntityTypes.STRIDER, respawnedEntity.getPolymerEntityType(null), "Respawned nether tower proxy should remain a strider.")) {
            return;
        }
        long visibleTowerEntities = lane.arenaWorld().getEntitiesOfClass(
                SemionTowerEntity.class,
                respawnedEntity.getBoundingBox().inflate(64.0),
                entity -> !entity.isRemoved() && entity.runtimeTower() == tower
        ).size();
        if (!assertEquals(context, 1L, visibleTowerEntities, "Round reset should leave one live tower entity.")) {
            return;
        }
        context.succeed();
    }
}
