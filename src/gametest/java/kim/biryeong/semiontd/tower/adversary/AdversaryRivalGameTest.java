package kim.biryeong.semiontd.tower.adversary;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.trait.BuiltInTraits;
import kim.biryeong.semiontd.trait.TraitLoadout;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class AdversaryRivalGameTest {
    @GameTest
    public void rivalDoesNotReceiveTowerTraitEffects(GameTestHelper context) {
        UUID owner = stableUuid("adversary-rival-no-traits");
        PlayerLane lane = testLane(context, owner);
        AdversaryRivalTower rival = new AdversaryRivalTower(
                AdversaryTowers.POLAR_BEAR_RIVAL,
                owner,
                TeamId.RED,
                1,
                GridPosition.from(context.absolutePos(new BlockPos(3, 2, 3)))
        );

        try {
            lane.addTower(rival);
            lane.assignTraitLoadout(new TraitLoadout(
                    BuiltInTraits.FORTITUDE_ID,
                    BuiltInTraits.DOUBLE_EDGED_SWORD_ID
            ));
            SemionTowerEntity entity = (SemionTowerEntity) context.getLevel()
                    .getEntity(rival.entityId().orElseThrow());

            require(!rival.receivesTraitEffects(), "Rivals must opt out of tower traits.");
            require(TraitLoadout.isNone(rival.traitLoadout().primaryTraitId())
                            && TraitLoadout.isNone(rival.traitLoadout().secondaryTraitId()),
                    "Rivals must not retain the owner's trait loadout.");
            requireClose(rival.type().maxHealth(), rival.currentMaxHealth(),
                    "Fortitude must not increase rival health.");
            requireClose(0.0, entity.activeEffectMagnitude(TimedEffectType.TOWER_FINAL_DAMAGE_BONUS),
                    "Damage traits must not increase rival damage.");
            requireClose(0.0, entity.activeEffectMagnitude(TimedEffectType.TOWER_DAMAGE_TAKEN_BONUS),
                    "Double-edged sword must not alter rival incoming damage.");
            context.succeed();
        } finally {
            lane.clearTowers();
            AdversaryProgressStates.clear(owner);
        }
    }

    @GameTest
    public void rivalConvertsAtItsSlotAndReturnsWithoutFakeTowerDeath(GameTestHelper context) {
        UUID owner = UUID.nameUUIDFromBytes(
                "adversary-rival-lifecycle".getBytes(StandardCharsets.UTF_8)
        );
        PlayerLane lane = testLane(context, owner);
        GridPosition rivalPosition = GridPosition.from(context.absolutePos(new BlockPos(3, 2, 3)));
        DeathObserverTower fox = new DeathObserverTower(
                AdversaryTowers.FOX,
                owner,
                new GridPosition(rivalPosition.x() - 1, rivalPosition.y(), rivalPosition.z())
        );
        AdversaryRivalTower rival = new AdversaryRivalTower(
                AdversaryTowers.BREEZE_RIVAL,
                owner,
                TeamId.RED,
                1,
                rivalPosition
        );

        try {
            lane.addTower(fox);
            lane.addTower(rival);
            int preparationEntityId = rival.entityId().orElseThrow();

            lane.markWaveStarted(1);

            require(rival.convertedForWave(), "Rival should convert when the wave starts.");
            require(rival.entityId().isEmpty(), "Converted rival should hide its tower entity.");
            require(lane.towers().contains(rival), "Converted rival must keep occupying its logical tower slot.");
            require(lane.activeMonsters().size() == 1, "One rival proxy should be active.");
            require(context.getLevel().getEntity(preparationEntityId) == null
                            || context.getLevel().getEntity(preparationEntityId).isRemoved(),
                    "Preparation tower entity should be discarded during conversion.");

            Monster proxy = lane.activeMonsters().getFirst();
            require(proxy.mineralReward() == 0L, "Rival proxy must never award minerals.");
            require(proxy.hasMinecraftEntity(), "Rival proxy should have a live Minecraft entity.");
            SemionMonsterEntity proxyEntity = requireProxyEntity(context, proxy);
            Vec3 expected = new Vec3(
                    rivalPosition.x() + 0.5,
                    rivalPosition.y() + 1.0,
                    rivalPosition.z() + 0.5
            );
            require(proxyEntity.position().distanceToSqr(expected) < 0.01,
                    "Rival proxy should spawn at its installed slot.");

            lane.forceFinalDefense();
            require(proxy.inFinalDefenseCombat(),
                    "A converted rival must enter final-defense combat.");
            require(!proxyEntity.isRemoved(),
                    "The live rival proxy must not be removed during final-defense transfer.");
            require(proxyEntity.position().distanceToSqr(expected) > 1.0,
                    "The live rival proxy must leave its installed slot for final defense.");
            require(fox.deployedAtFinalDefense(),
                    "The fox must move to final defense with its rival proxy.");

            lane.tick(context.getLevel().getServer());
            require(fox.nearbyTowerDeaths == 0,
                    "Wave conversion must not fan out a fake nearby tower-death event.");
            require(!lane.laneDefenseBroken(), "A living fox should keep the lane defense active.");

            fox.syncHealth(0.0);
            lane.tick(context.getLevel().getServer());
            require(lane.laneDefenseBroken(),
                    "Hidden rivals must not prevent defense collapse after the fox dies.");

            lane.resetForRound();
            require(!rival.convertedForWave(), "Rival should return for the next preparation.");
            require(rival.entityId().isPresent(), "Returned rival should respawn its tower entity.");
            require(lane.activeMonsters().stream()
                            .noneMatch(monster -> AdversaryRivalTower.logicalRivalIdOf(monster)
                                    .filter(rival.rivalId()::equals)
                                    .isPresent()),
                    "No rival proxy may survive the preparation reset.");
            require(!lane.laneDefenseBroken(), "Round reset should restore lane defense state.");

            lane.markWaveStarted(2);
            Monster nextProxy = lane.activeMonsters().getFirst();
            SemionMonsterEntity nextProxyEntity = requireProxyEntity(context, nextProxy);
            lane.forceFinalDefense();
            require(nextProxy.inFinalDefenseCombat(),
                    "A restored rival must enter final-defense combat again next round.");
            require(!nextProxyEntity.isRemoved(),
                    "The restored rival proxy must remain live during final-defense transfer.");
            require(nextProxyEntity.position().distanceToSqr(expected) > 1.0,
                    "The restored rival proxy must move again instead of remaining in the lane.");
            context.succeed();
        } finally {
            lane.clearTowers();
            AdversaryProgressStates.clear(owner);
        }
    }

    @GameTest
    public void igniteKillCreditsRivalScoreWithoutInflatingDamageStatistics(GameTestHelper context) {
        UUID owner = stableUuid("adversary-rival-ignite");
        PlayerLane lane = testLane(context, owner);
        GridPosition foxPosition = GridPosition.from(context.absolutePos(new BlockPos(2, 2, 3)));
        GridPosition rivalPosition = GridPosition.from(context.absolutePos(new BlockPos(3, 2, 3)));
        AdversaryFoxTower fox = new AdversaryFoxTower(
                AdversaryTowers.FOX,
                owner,
                TeamId.RED,
                1,
                foxPosition
        );
        AdversaryRivalTower rival = new AdversaryRivalTower(
                AdversaryTowers.BREEZE_RIVAL,
                owner,
                TeamId.RED,
                1,
                rivalPosition
        );

        try {
            lane.addTower(fox);
            lane.addTower(rival);
            lane.markWaveStarted(1);

            SemionTowerEntity foxEntity = (SemionTowerEntity) context.getLevel()
                    .getEntity(fox.entityId().orElseThrow());
            Monster proxy = lane.activeMonsters().getFirst();
            SemionMonsterEntity proxyEntity = (SemionMonsterEntity) context.getLevel()
                    .getEntity(proxy.minecraftEntityId());
            proxyEntity.setNoAi(true);
            fox.syncHealth(100.0);
            foxEntity.setHealth(100.0F);

            fox.damageTargetResult(foxEntity, proxyEntity, 1.0);
            requireClose(0.0, fox.roundPhysicalDamageDealt(),
                    "Damage dealt to an owned rival must not inflate physical damage statistics.");

            proxyEntity.applyIgnite(
                    owner,
                    fox,
                    TraitLoadout.none(),
                    100.0,
                    0.0,
                    1.0,
                    1,
                    1
            );
            proxyEntity.aiStep();

            require(rival.contributedScore() == 2,
                    "An ignite last hit from the fox must credit the rival's evolution score.");
            requireClose(160.0, fox.health(),
                    "A base rival kill must heal twenty percent of the fox's maximum health.");
            requireClose(0.0, fox.roundMagicDamageDealt(),
                    "Ignite damage to an owned rival must not inflate magic damage statistics.");

            SemionMonsterEntity ordinary = spawnOrdinaryMonster(
                    context,
                    lane,
                    new Vec3(foxPosition.x() + 4.5, foxPosition.y() + 1.0, foxPosition.z() + 0.5)
            );
            fox.damageTargetResult(foxEntity, ordinary, 10.0);
            requireClose(10.0, fox.roundPhysicalDamageDealt(),
                    "Damage dealt to an ordinary monster must remain in round statistics.");
            context.succeed();
        } finally {
            lane.clearTowers();
            AdversaryProgressStates.clear(owner);
        }
    }

    private static PlayerLane testLane(GameTestHelper context, UUID owner) {
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(10, 5, 14));
        Vec3 spawn = Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 2, 1)));
        Vec3 waypoint = Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 8)));
        Vec3 boss = Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 13)));
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                spawn,
                List.of(waypoint),
                boss,
                BlockBounds.of(min, max),
                List.of(GridPosition.from(context.absolutePos(new BlockPos(7, 2, 11))))
        );
        return new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
    }

    private static SemionMonsterEntity spawnOrdinaryMonster(
            GameTestHelper context,
            PlayerLane lane,
            Vec3 position
    ) {
        Monster monster = new Monster(
                "adversary-statistics-control",
                lane.teamId(),
                lane.laneId(),
                Optional.empty(),
                Optional.empty(),
                100.0,
                0.0,
                1.0,
                AttackKind.MELEE,
                "minecraft:zombie",
                0L
        );
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, lane.laneLayout());
        entity.setPos(position);
        context.getLevel().addFreshEntity(entity);
        monster.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
        lane.activeMonsters().add(monster);
        entity.setNoAi(true);
        return entity;
    }

    private static UUID stableUuid(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private static SemionMonsterEntity requireProxyEntity(GameTestHelper context, Monster proxy) {
        var entity = context.getLevel().getEntity(proxy.minecraftEntityId());
        require(entity instanceof SemionMonsterEntity && !entity.isRemoved(),
                "Rival proxy entity should use the shared live Semion monster runtime.");
        return (SemionMonsterEntity) entity;
    }

    private static void requireClose(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.0001) {
            throw new AssertionError(message + " Expected " + expected + ", got " + actual + '.');
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class DeathObserverTower extends Tower {
        private int nearbyTowerDeaths;

        private DeathObserverTower(TowerType type, UUID owner, GridPosition position) {
            super(type, owner, TeamId.RED, 1, position);
        }

        @Override
        public void onNearbyTowerDeath(PlayerLane lane, Tower destroyedTower) {
            nearbyTowerDeaths++;
        }

        @Override
        protected boolean execute(PlayerLane lane) {
            return false;
        }
    }
}
