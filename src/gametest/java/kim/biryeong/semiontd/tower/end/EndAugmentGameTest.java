package kim.biryeong.semiontd.tower.end;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import kim.biryeong.semiontd.tower.warlock.WarlockSacrificeTower;
import kim.biryeong.semiontd.tower.warlock.WarlockTower;
import kim.biryeong.semiontd.tower.warlock.WarlockTowers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class EndAugmentGameTest {
    @GameTest
    public void warlockAbsorptionFiresOnceAndSharesOnlyGrowth(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, "job_warlock_towers_s", "job_warlock_towers_g1", "job_warlock_towers_p")) {
            WarlockTower core = fixture.warlock(0);
            WarlockTower partner = fixture.warlock(1);
            WarlockSacrificeTower donor = new WarlockSacrificeTower(WarlockTowers.T1_RANGED_SLAVE,
                    fixture.owner, TeamId.RED, 1, fixture.position(2));
            fixture.add(donor);
            double donatedHealth = donor.currentMaxHealth();
            double donatedDamage = donor.sacrificeAttackDamage();
            SemionMonsterEntity target = fixture.target(fixture.entity(donor).position().add(0, 0, .5), 1000);
            SemionTowerEntity source = fixture.entity(core);
            source.setHealth(1);
            core.syncHealth(1);
            core.onDamaged(source, context.getLevel().damageSources().generic(), 1, 2, 1);
            require(donor.health() == 0, "The sacrifice is committed once.");
            requireClose(donatedDamage * 3, core.roundPhysicalDamageDealt(), "Testament uses the donor's physical damage type.");
            requireClose(donatedHealth, core.roundMagicDamageDealt(), "Only the absorbing core explodes the sacrifice.");
            requireClose(1000 - donatedDamage * 3 - donatedHealth, target.runtimeMonster().health(), "Both effects damage the target once.");
            requireClose(core.currentMaxHealth(), partner.currentMaxHealth(), "The partner receives the committed growth.");
            requireClose(0, partner.roundDamageDealt(), "Sharing never repeats testament or explosion.");
            context.succeed();
        }
    }

    @GameTest
    public void trueAwakeningUnlocksAtSixtyPercentWithAnotherCoreAlive(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, "job_warlock_towers_g2")) {
            WarlockTower core = fixture.warlock(0);
            fixture.warlock(1);
            SemionTowerEntity source = fixture.entity(core);
            source.setHealth((float) (core.currentMaxHealth() * .60));
            core.syncHealth(source.getHealth());
            core.onDamaged(source, context.getLevel().damageSources().generic(), 1,
                    core.currentMaxHealth(), source.getHealth());
            requireClose(.20, core.finalDamageBonus(), "Awakening is unlocked without kills and ignores the living ally.");
            core.resetForRound(fixture.lane);
            requireClose(0, core.finalDamageBonus(), "The awakening damage bonus ends with the round.");
            context.succeed();
        }
    }

    @GameTest
    public void voidMineSurvivesCoreDeathAndUsesEightTargetCap(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, EndAugments.MINE)) {
            EndTower core = fixture.end(EndTowers.BASE_END_TOWER);
            core.onWaveStarted(fixture.lane, 5);
            core.tick(fixture.lane);
            EndAugments effects = new EndAugments();
            effects.onTransferCompleted(core, core, 100);
            effects.tickMines(core, fixture.lane);
            Vec3 center = new Vec3(core.position().x() + .5, core.position().y() + 1, core.position().z() + .5);
            for (int i = 0; i < 9; i++) {fixture.target(center.add(.1 * i, 0, .5), 1000);}
            fixture.lane.killTower(core);
            effects.tickMines(core, fixture.lane);
            requireClose(800, core.roundMagicDamageDealt(), "A stored mine survives the core's combat death and hits eight targets.");
            require(effects.mineCount() == 0, "The mine is consumed once.");
            effects.tickMines(core, fixture.lane);
            requireClose(800, core.roundMagicDamageDealt(), "A consumed mine cannot explode twice.");
            context.succeed();
        }
    }

    @GameTest
    public void growthHitsNativeSecondaryTargetsOnceAndExtraAttacksCannotConsumeItsCharge(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, EndAugments.GROWTH)) {
            EndTower core = fixture.end(EndTowers.BASE_END_TOWER);
            for (int offset = 1; offset <= 4; offset++) {
                fixture.add(new EndTower(EndTowers.T3_END_CRYSTAL_TOWER, fixture.owner, TeamId.RED, 1,
                        fixture.position(offset)));
            }
            core.onWaveStarted(fixture.lane, 5);
            int ticks = (int) Math.ceil(EndConfig.RUNTIME.transfer().durationTicks() * core.transferDurationMultiplier());
            for (int tick = 0; tick < ticks; tick++) {core.tick(fixture.lane);}
            require(core.transferStats().endCrystalCount() == 12, "Four native crystal transfers must unlock splash.");
            require(core.runtimeDetailLines().stream().anyMatch(line -> line.contains("전달 4기 / 추가 피해 충전")),
                    "Three completed transfers must leave one charge for the next ordinary attack.");
            SemionTowerEntity source = fixture.entity(core);
            SemionMonsterEntity primary = fixture.target(source.position().add(1, 0, 0), 1000);
            SemionMonsterEntity secondary = fixture.target(primary.position().add(0, 0, core.splashRadius() * .5), 1000);
            SemionMonsterEntity outside = fixture.target(primary.position().add(0, 0, core.splashRadius() + .25), 1000);
            double attackDamage = source.attackDamageAmount(primary);
            double nativeDamage = core.resolveBasicAttackOutgoingDamage(source, primary, attackDamage);
            double splashDamage = nativeDamage * EndConfig.RUNTIME.splash().damageRatio();
            double growthDamage = core.resolveBasicAttackOutgoingDamage(source, primary, attackDamage * 1.5);

            AugmentCombat.additionalAttack(source, primary, 1);
            requireClose(nativeDamage, 1000 - primary.runtimeMonster().health(), "An extra attack retains its native primary damage.");
            requireClose(splashDamage, 1000 - secondary.runtimeMonster().health(), "An extra attack retains native splash targeting.");
            requireClose(0, core.roundMagicDamageDealt(), "An augment extra attack must not trigger growth magic damage.");
            require(core.runtimeDetailLines().stream().anyMatch(line -> line.contains("추가 피해 충전")),
                    "An augment extra attack must preserve the pending growth charge.");

            attack(source, primary);
            requireClose(nativeDamage * 2 + growthDamage, 1000 - primary.runtimeMonster().health(),
                    "The ordinary primary target must take exactly 150% extra magic damage.");
            requireClose(splashDamage * 2 + growthDamage, 1000 - secondary.runtimeMonster().health(),
                    "The native secondary target must receive the same 150% extra magic damage.");
            requireClose(growthDamage * 2, core.roundMagicDamageDealt(), "Growth damage must be attributed as magic for both native targets.");
            require(core.runtimeDetailLines().stream().anyMatch(line -> line.contains("추가 피해 대기")),
                    "The ordinary attack must consume the stored charge.");

            attack(source, primary);
            requireClose(nativeDamage * 3 + growthDamage, 1000 - primary.runtimeMonster().health(), "The consumed charge cannot hit the primary again.");
            requireClose(splashDamage * 3 + growthDamage, 1000 - secondary.runtimeMonster().health(), "The consumed charge cannot hit the secondary again.");
            requireClose(growthDamage * 2, core.roundMagicDamageDealt(), "A consumed charge cannot produce more magic damage.");
            requireClose(1000, outside.runtimeMonster().health(), "Growth cannot expand the native secondary target set.");
            context.succeed();
        }
    }

    @GameTest
    public void breathFiresTwiceFourTicksApartAndReselectsInsideRange(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, EndAugments.BREATH)) {
            TowerType base = EndTowers.BASE_END_TOWER;
            TowerType giant = new TowerType(base.id(), base.displayName(), base.category(), base.mineralCost(),
                    1_000_000, base.range(), base.damage(), base.attackIntervalTicks(), base.aggroPriority(),
                    base.description(), base.visual(), base.upgradeOptions());
            EndTower core = fixture.end(giant);
            core.onWaveStarted(fixture.lane, 5);
            core.tick(fixture.lane);
            require(core.state() == EndTowerState.DRAGON, "The large core evolves before the breath timer.");
            SemionTowerEntity source = fixture.entity(core);
            SemionMonsterEntity first = fixture.target(source.position().add(2, 0, 0), 1000);
            source.recordCurrentAttackTarget(first);
            for (int i = 1; i < 120; i++) {core.tick(fixture.lane);}
            double firstShot = core.roundMagicDamageDealt();
            require(firstShot > 0, "The first breath fires at six seconds.");
            first.setPos(source.position().add(100, 0, 0));
            SemionMonsterEntity replacement = fixture.target(source.position().add(0, 0, 2), 1000);
            for (int i = 0; i < 3; i++) {core.tick(fixture.lane);}
            requireClose(firstShot, core.roundMagicDamageDealt(), "The second breath waits four ticks.");
            core.tick(fixture.lane);
            require(replacement.runtimeMonster().health() < 1000, "The second shot reselects a target inside native range.");
            requireClose(firstShot * 2, core.roundMagicDamageDealt(), "Exactly two equal breaths fired.");
            core.tick(fixture.lane);
            requireClose(firstShot * 2, core.roundMagicDamageDealt(), "The card name does not create a third breath.");
            context.succeed();
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper context;
        private final UUID owner = UUID.randomUUID();
        private final PlayerLane lane;
        private final List<SemionMonsterEntity> monsters = new ArrayList<>();

        private Fixture(GameTestHelper context, String... ids) {
            this.context = context;
            var layout = new LaneRegionLayout(1, Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 2, 1))),
                    List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 7)))),
                    Vec3.atCenterOf(context.absolutePos(new BlockPos(12, 2, 12))),
                    BlockBounds.of(context.absolutePos(new BlockPos(0, 1, 0)), context.absolutePos(new BlockPos(14, 6, 14))),
                    List.of(GridPosition.from(context.absolutePos(new BlockPos(10, 2, 11)))));
            lane = new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
            List<PlayerAugmentState.Selection> selections = new ArrayList<>();
            for (int i = 0; i < ids.length; i++) {
                selections.add(new PlayerAugmentState.Selection(5 + i * 10, AugmentRarity.GOLD,
                        ids[i], PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none()));
            }
            lane.assignAugmentSnapshot(new AugmentSnapshot(AugmentConfig.defaults(), selections));
            AreaEffectLaneIndex.register(lane);
        }

        private GridPosition position(int offset) {return GridPosition.from(context.absolutePos(new BlockPos(3 + offset, 2, 3)));}
        private SemionTowerEntity entity(EntityBackedTower tower) {return tower.runtimeEntity(lane).orElseThrow();}
        private void add(EntityBackedTower tower) {lane.addTower(tower); entity(tower).setNoAi(true);}
        private WarlockTower warlock(int offset) {
            WarlockTower tower = new WarlockTower(WarlockTowers.RANGED_WARLOCK_TOWER, owner, TeamId.RED, 1, position(offset));
            add(tower);
            return tower;
        }
        private EndTower end(TowerType type) {
            EndTower tower = new EndTower(type, owner, TeamId.RED, 1, position(0));
            add(tower);
            return tower;
        }
        private SemionMonsterEntity target(Vec3 position, double health) {
            Monster monster = new Monster("augment_target", TeamId.RED, 1, Optional.empty(), Optional.empty(), health, 0, 1,
                    AttackKind.MELEE, "minecraft:zombie", 0);
            monster.setOrigin(MonsterOrigin.NATURAL_WAVE);
            var entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
            entity.configureFrom(monster, lane.laneLayout());
            entity.setNoAi(true);
            entity.setPos(position);
            require(context.getLevel().addFreshEntity(entity), "The target spawns.");
            monster.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
            lane.activeMonsters().add(monster);
            monsters.add(entity);
            return entity;
        }
        @Override public void close() {
            monsters.forEach(SemionMonsterEntity::discard);
            lane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
        }
    }

    private static void attack(SemionTowerEntity source, SemionMonsterEntity target) {
        double damage = source.attackDamageAmount(target);
        var result = source.damageTargetResult(target, damage);
        source.recordAttack(target, damage, result.outgoingDamage(), result.dealtDamage(), result.killed());
    }

    private static void require(boolean condition, String message) {if (!condition) {throw new AssertionError(message);}}
    private static void requireClose(double expected, double actual, String message) {
        require(Math.abs(expected - actual) < .001, message + " Expected " + expected + ", got " + actual);
    }
}
