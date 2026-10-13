package kim.biryeong.semiontd.tower.undead;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.goal.SiegeTrueDamageGoal;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TowerPlacementResult;
import kim.biryeong.semiontd.job.UndeadTowerJob;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.summon.SummonTier;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.ProductionTowerService;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.undead.UndeadAnimalTower;
import kim.biryeong.semiontd.tower.undead.UndeadDrownedTower;
import kim.biryeong.semiontd.tower.undead.UndeadHuskTower;
import kim.biryeong.semiontd.tower.undead.UndeadMeleeSkeletonTower;
import kim.biryeong.semiontd.tower.undead.UndeadRangedSkeletonTower;
import kim.biryeong.semiontd.tower.undead.UndeadTowerCatalogs;
import kim.biryeong.semiontd.tower.undead.UndeadTowers;
import kim.biryeong.semiontd.tower.undead.UndeadZombieTower;
import net.minecraft.core.BlockPos;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import kim.biryeong.semiontd.gametest.GameTestParticipantFixture;

public final class UndeadTowerRuntimeTest extends GameTestParticipantFixture {
    @GameTest
    public void undeadAnimalTowerDebuffsMonsterAttackAndTowerDamageTaken(GameTestHelper context) {
        UUID playerId = stableUuid("undead-animal-debuff-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED);
        PlayerLane lane = redLane(game, 1);
        BlockPos base = towerPlacementPos(lane);
        UndeadAnimalTower firstTower = new UndeadAnimalTower(
                UndeadTowers.T2_UNDEAD_ANIMAL_TOWER,
                playerId,
                TeamId.RED,
                1,
                new GridPosition(base.getX(), base.getY(), base.getZ())
        );
        UndeadAnimalTower secondTower = new UndeadAnimalTower(
                UndeadTowers.T2_UNDEAD_ANIMAL_TOWER,
                playerId,
                TeamId.RED,
                1,
                new GridPosition(base.getX() + 1, base.getY(), base.getZ())
        );
        lane.addTower(firstTower);
        lane.addTower(secondTower);

        SemionTowerEntity towerEntity = (SemionTowerEntity) lane.arenaWorld().getEntity(firstTower.entityId().orElseThrow());
        SemionMonsterEntity monster = spawnAttackMonsterEntity(
                context,
                "undead-animal-target",
                TeamId.RED,
                1,
                towerEntity.position().add(1.0, 0.0, 0.0),
                100.0,
                20.0
        );

        firstTower.tick(lane);
        secondTower.tick(lane);
        if (!assertClose(context, 0.20, monster.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION), "Undead animal tower should apply non-stacking monster attack damage reduction.")) {
            return;
        }
        if (!assertClose(context, 0.10, monster.activeTimedEffectMagnitude(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS), "T2 undead animal tower should apply non-stacking tower damage taken bonus.")) {
            return;
        }
        if (!assertClose(context, 16.0, monster.attackDamageAmount(), "Monster attack damage reduction should lower runtime attack damage.")) {
            return;
        }
        if (!assertClose(context, 110.0, monster.towerDamageTaken(100.0), "Tower damage taken bonus should increase runtime tower damage.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void undeadTowerCatalogRegistersAndLinksAllFamilies(GameTestHelper context) {
        ProductionTowerCatalog.clear();
        UndeadTowerCatalogs.register();

        if (!assertEquals(context, 3L, ProductionTowerCatalog.all().stream().filter(ProductionTowerCatalog.CatalogEntry::starter).count(), "Undead catalog should expose three starter tower families.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(UndeadTowers.T1_ZOMBIE_TOWER).size(), "Zombie starter should link to husk tower.")) {
            return;
        }
        if (!assertEquals(context, 2, ProductionTowerCatalog.upgrades(UndeadTowers.T1_SKELETON_TOWER).size(), "Skeleton starter should branch to ranged and melee towers.")) {
            return;
        }
        if (!assertEquals(context, 1, ProductionTowerCatalog.upgrades(UndeadTowers.T1_UNDEAD_ANIMAL_TOWER).size(), "Undead animal starter should link to skeleton horse tower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(UndeadTowers.T1_ZOMBIE_TOWER).orElseThrow().create(stableUuid("undead-zombie-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof UndeadZombieTower, "Zombie catalog entry should create UndeadZombieTower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(UndeadTowers.T2_ZOMBIE_TOWER).orElseThrow().create(stableUuid("undead-husk-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof UndeadHuskTower, "Husk catalog entry should create UndeadHuskTower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(UndeadTowers.T3_ZOMBIE_TOWER).orElseThrow().create(stableUuid("undead-drowned-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof UndeadDrownedTower, "Drowned catalog entry should create UndeadDrownedTower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(UndeadTowers.T2_RANGED_SKELETON_TOWER).orElseThrow().create(stableUuid("undead-ranged-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof UndeadRangedSkeletonTower, "Ranged skeleton catalog entry should create UndeadRangedSkeletonTower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(UndeadTowers.T2_MELEE_TOWER).orElseThrow().create(stableUuid("undead-melee-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof UndeadMeleeSkeletonTower, "Melee skeleton catalog entry should create UndeadMeleeSkeletonTower.")) {
            return;
        }
        if (!assertTrue(context, ProductionTowerCatalog.entry(UndeadTowers.T1_UNDEAD_ANIMAL_TOWER).orElseThrow().create(stableUuid("undead-animal-catalog-owner"), TeamId.RED, 1, new GridPosition(0, 0, 0)) instanceof UndeadAnimalTower, "Undead animal catalog entry should create UndeadAnimalTower.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void undeadTowerJobUsesUndeadStarterAndUpgradeTree(GameTestHelper context) {
        UUID playerId = stableUuid("undead-job-tower-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, UndeadTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        BlockPos towerPos = towerPlacementPos(lane);

        Set<String> starterIds = ProductionTowerService.availableTowers(game, playerId).stream()
                .map(entry -> entry.type().id())
                .collect(java.util.stream.Collectors.toSet());
        if (!assertEquals(
                context,
                Set.of(
                        UndeadTowers.T1_ZOMBIE_TOWER.id(),
                        UndeadTowers.T1_SKELETON_TOWER.id(),
                        UndeadTowers.T1_UNDEAD_ANIMAL_TOWER.id()
                ),
                starterIds,
                "Undead job should expose only undead starter towers."
        )) {
            return;
        }
        TowerPlacementResult placement = ProductionTowerService.placeTower(game, playerId, towerPos, UndeadTowers.T1_ZOMBIE_TOWER.id());
        if (!assertEquals(context, TowerPlacementResult.SUCCESS, placement, "Undead job should be allowed to place zombie tower.")) {
            return;
        }

        Set<String> upgradeIds = ProductionTowerService.availableUpgrades(game, playerId, towerPos).stream()
                .map(option -> option.targetType().id())
                .collect(java.util.stream.Collectors.toSet());
        if (!assertEquals(
                context,
                Set.of(UndeadTowers.T2_ZOMBIE_TOWER.id()),
                upgradeIds,
                "Undead zombie starter should connect to the husk upgrade for undead players."
        )) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void wardenFixedDamageTriggersDrownedLastStand(GameTestHelper context) {
        UUID playerId = stableUuid("drowned-warden-fixed-damage-owner");
        SemionGame game = startedSinglePlayerGame(context, playerId, TeamId.RED, UndeadTowerJob.ID);
        PlayerLane lane = redLane(game, 1);
        UndeadDrownedTower drowned = new UndeadDrownedTower(
                TowerBalanceRuntime.resolve(UndeadTowers.T3_ZOMBIE_TOWER),
                playerId,
                TeamId.RED,
                1,
                GridPosition.from(towerPlacementPos(lane))
        );
        lane.addTower(drowned);
        SemionTowerEntity drownedEntity = (SemionTowerEntity) lane.arenaWorld()
                .getEntity(drowned.entityId().orElseThrow());
        drowned.syncHealth(50.0);
        drownedEntity.setHealth(50.0F);

        SemionMonsterEntity warden = spawnSummonEntity(
                context,
                "warden-special-damage",
                TeamId.BLUE,
                TeamId.RED,
                1,
                drownedEntity.position().add(1.0, 0.0, 0.0),
                100.0,
                0.0
        );
        warden.setTarget(drownedEntity);
        new SiegeTrueDamageGoal(warden, 100.0, 60, 1, 0.0).tick();
        float lastStandHealth = drownedEntity.getHealth();
        if (!assertTrue(context, lastStandHealth > 0.0F, "Warden fixed damage should trigger Drowned Last Stand instead of killing it.")) {
            return;
        }

        new SiegeTrueDamageGoal(warden, 100.0, 60, 1, 0.0).tick();
        if (!assertEquals(context, lastStandHealth, drownedEntity.getHealth(), "Drowned should ignore Warden fixed damage during Last Stand.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void strayAdditionalTargetsUseTwoBlockRangeBonus(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        Vec3 origin = Vec3.atCenterOf(context.absolutePos(BlockPos.ZERO));
        UndeadRangedSkeletonTower stray = new UndeadRangedSkeletonTower(
                TowerBalanceRuntime.resolve(UndeadTowers.T3_RANGED_SKELETON_TOWER),
                stableUuid("stray-extra-range-owner"),
                TeamId.RED,
                1,
                GridPosition.from(context.absolutePos(BlockPos.ZERO))
        );
        SemionTowerEntity towerEntity = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        towerEntity.configure(stray, null);
        towerEntity.setNoGravity(true);
        towerEntity.setPos(origin);
        context.getLevel().addFreshEntity(towerEntity);

        SemionMonsterEntity primary = spawnRoleMonsterEntity(
                context, "stray-primary", Optional.empty(), TeamId.RED, 1,
                origin.add(1.0, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH)
        );
        SemionMonsterEntity insideBonusRange = spawnRoleMonsterEntity(
                context, "stray-inside-bonus-range", Optional.empty(), TeamId.RED, 1,
                origin.add(7.5, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH)
        );
        SemionMonsterEntity outsideBonusRange = spawnRoleMonsterEntity(
                context, "stray-outside-bonus-range", Optional.empty(), TeamId.RED, 1,
                origin.add(8.5, 0.0, 0.0), 100.0, List.of(SummonRole.RUSH)
        );
        stray.onAttack(towerEntity, primary, 10.0, false);

        if (!assertTrue(context, insideBonusRange.getHealth() < 100.0F, "Stray should acquire an extra target within attack range +2 blocks.")) {
            return;
        }
        if (!assertClose(context, 100.0, outsideBonusRange.getHealth(), "Stray should not acquire an extra target beyond attack range +2 blocks.")) {
            return;
        }
        context.succeed();
    }

    @GameTest
    public void witherSkeletonDeathStacksUseFiveBlockRange(GameTestHelper context) {
        TowerBalanceRuntime.apply(TowerBalanceConfig.defaultConfig());
        GridPosition position = GridPosition.from(context.absolutePos(BlockPos.ZERO));
        Vec3 center = new Vec3(position.x() + 0.5, position.y() + 1.0, position.z() + 0.5);

        for (TowerType type : List.of(UndeadTowers.T2_MELEE_TOWER, UndeadTowers.T3_MELEE_TOWER)) {
            UndeadMeleeSkeletonTower tower = new UndeadMeleeSkeletonTower(
                    TowerBalanceRuntime.resolve(type),
                    stableUuid("wither-stack-range-" + type.id()),
                    TeamId.RED,
                    1,
                    position
            );
            double baseDamage = tower.modifyAttackDamage(null, null, type.damage());
            tower.onNearbyMonsterDeath(null, null, center.add(4.0, 0.0, 0.0));
            double damageAfterInsideDeath = tower.modifyAttackDamage(null, null, type.damage());
            if (!assertTrue(context, damageAfterInsideDeath > baseDamage, type.id() + " should gain a stack from a death four blocks away.")) {
                return;
            }
            tower.onNearbyMonsterDeath(null, null, center.add(5.1, 0.0, 0.0));
            if (!assertClose(
                    context,
                    damageAfterInsideDeath,
                    tower.modifyAttackDamage(null, null, type.damage()),
                    type.id() + " should not gain a stack from beyond five blocks."
            )) {
                return;
            }
        }
        context.succeed();
    }

    private static SemionMonsterEntity spawnAttackMonsterEntity(
            GameTestHelper context,
            String id,
            TeamId targetTeam,
            int targetLaneId,
            Vec3 position,
            double maxHealth,
            double attackDamage
    ) {
        Monster monster = new Monster(
                id,
                targetTeam,
                targetLaneId,
                Optional.empty(),
                Optional.empty(),
                maxHealth,
                0,
                attackDamage,
                AttackKind.MELEE,
                "minecraft:zombie",
                null,
                DamageType.PHYSICAL,
                0,
                SummonTier.T1,
                List.of(SummonRole.RUSH),
                0
        );
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, null);
        entity.setNoGravity(true);
        entity.setPos(position);
        context.getLevel().addFreshEntity(entity);
        return entity;
    }
}
