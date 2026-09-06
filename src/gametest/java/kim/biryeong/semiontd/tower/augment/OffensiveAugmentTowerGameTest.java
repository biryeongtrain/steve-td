package kim.biryeong.semiontd.tower.augment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.augment.AugmentEconomyService;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.AugmentTelemetry;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.TowerCapacity;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class OffensiveAugmentTowerGameTest {
    @GameTest
    public void lowPressureStopsIdleChargeButConsumesPreviouslyChargedShot(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, AugmentTowers.CAPACITOR_POST)) {
            fixture.start(15);
            for (int tick = 0; tick < 40; tick++) fixture.tower.tick(fixture.lane);
            require(fixture.tower.charges() == 1, "One charge is stored before the weak income enters.");
            var source = fixture.source();
            var weak = fixture.target("weak_capacitor_target", source.position().add(4, 0, 0), 1000);
            markLowPressure(weak.runtimeMonster());
            for (int tick = 0; tick < 80; tick++) fixture.tower.tick(fixture.lane);
            require(fixture.tower.charges() == 1, "An attackable weak enemy still prevents idle charging.");
            var hit = fixture.tower.damagePrimaryAttackTargetResult(source, weak, source.attackDamageAmount(weak));
            requireClose(105, hit.dealtDamage(), "Previously charged basic damage still applies to low-pressure income.");
            fixture.tower.onPrimaryAttack(weak, hit.outgoingDamage(), DamageType.MAGIC, weak.position());
            require(fixture.tower.charges() == 0, "A successful charged basic shot still consumes its charge.");
            for (int tick = 0; tick < 80; tick++) fixture.tower.tick(fixture.lane);
            require(fixture.tower.charges() == 0, "The weak enemy must not manufacture fresh charges.");
            requireClose(35, source.attackDamageAmount(weak), "The next uncharged primary retains normal base damage.");
            context.succeed();
        }
    }

    @GameTest
    public void lowPressureCannotTriggerFactoryShellButTakesCollateralDamage(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, AugmentTowers.ORDNANCE_FACTORY)) {
            fixture.tower.beginPrepare(fixture.lane, 15);
            fixture.tower.onPaidSummon(UUID.randomUUID(), 100);
            fixture.start(15);
            var source = fixture.source();
            var weak = fixture.target("weak_factory_target", source.position().add(0, 0, 4), 1000);
            markLowPressure(weak.runtimeMonster());
            var first = fixture.tower.damagePrimaryAttackTargetResult(source, weak, source.attackDamageAmount(weak));
            fixture.tower.onPrimaryAttack(weak, first.outgoingDamage(), DamageType.PHYSICAL, weak.position());
            requireClose(980, weak.runtimeMonster().health(), "Low-pressure income takes ordinary primary damage only.");
            require(fixture.tower.preparedShells() == 1, "A weak primary does not trigger or consume a shell.");
            requireClose(0, fixture.tower.specialDamageDealt(), "A weak primary does not create a new artillery proc.");
            var normal = fixture.target("normal_factory_trigger", weak.position().add(.5, 0, 0), 1000);
            var next = fixture.tower.damagePrimaryAttackTargetResult(source, normal, source.attackDamageAmount(normal));
            fixture.tower.onPrimaryAttack(normal, next.outgoingDamage(), DamageType.PHYSICAL, normal.position());
            require(fixture.tower.preparedShells() == 0, "A normal primary can fire the preserved shell.");
            requireClose(860, weak.runtimeMonster().health(), "Low-pressure income still takes collateral shell damage.");
            context.succeed();
        }
    }

    @GameTest
    public void giantHunterRespectsDeadZoneAndKeepsLogicalTarget(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, AugmentTowers.GIANT_HUNTER)) {
            fixture.start(15);
            var source = fixture.source();
            var first = fixture.target("ordinary_giant", source.position().add(4, 0, 0), 1000);
            var boss = fixture.target("warden_boss_15", source.position().add(5, 0, 0), 1000);
            requireClose(150, source.attackDamageAmount(first), "Ordinary giant damage must include 8% max health.");
            requireClose(90, source.attackDamageAmount(boss), "The natural R15 Warden must use the 2% boss ratio.");
            require(fixture.tower.selectAttackTarget(source, List.of(boss, first)).orElseThrow() == first,
                    "Equal-health giants must choose the closer target.");
            var larger = fixture.target("larger_giant", source.position().add(6, 0, 0), 2000);
            require(fixture.tower.selectAttackTarget(source, List.of(larger, first)).orElseThrow() == first,
                    "A living target in range must remain locked even when a larger enemy arrives.");
            first.setPos(source.position().add(2.9, 0, 0));
            require(!fixture.tower.canAttackTarget(source, first), "The minimum range must guard actual attacks.");
            require(fixture.tower.selectAttackTarget(source, List.of(first, larger)).orElseThrow() == larger,
                    "A target entering the dead zone must release the lock.");
            first.runtimeMonster().setOrigin(MonsterOrigin.BUILDER_PROXY);
            first.setPos(source.position().add(4, 0, 0));
            require(!fixture.tower.canAttackTarget(source, first), "Builder proxies must not be eligible enemies.");
            require(!fixture.tower.canChaseTargets(), "The hunter must never chase an enemy.");
            context.succeed();
        }
    }

    @GameTest
    public void capacitorPreservesChargesWhenShieldsPreventHealthDamage(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, AugmentTowers.CAPACITOR_POST)) {
            for (int tick = 0; tick < 80; tick++) fixture.tower.tick(fixture.lane);
            require(fixture.tower.charges() == 0, "Preparation time must not charge the post.");
            fixture.start(15);
            for (int tick = 0; tick < 120; tick++) fixture.tower.tick(fixture.lane);
            require(fixture.tower.charges() == 3, "Three complete empty-range periods grant three charges.");
            var source = fixture.source();
            var target = fixture.target("capacitor_target", source.position().add(4, 0, 0), 1000);
            fixture.tower.tick(fixture.lane);
            requireClose(245, source.attackDamageAmount(target), "A fully charged shot must deal 35 + 70 * 3 magic damage.");
            target.runtimeMonster().grantShield(DamageType.MAGIC, 1000, 40, context.getLevel().getGameTime(), target.runtimeMonster());
            var blocked = fixture.tower.damagePrimaryAttackTargetResult(source, target, source.attackDamageAmount(target));
            requireClose(0, blocked.dealtDamage(), "The first shot must be fully absorbed by the magic shield.");
            require(fixture.tower.charges() == 3, "Damage calculation and blocked shots must not consume charges.");
            require(fixture.tower.telemetrySample(15, 40, 1, "ROUND_END").capacitorChargedShots() == 0,
                    "Shielded attempts must not be recorded as discharged capacitor shots.");
            target.runtimeMonster().expireShields(context.getLevel().getGameTime() + 40);
            var hit = fixture.tower.damagePrimaryAttackTargetResult(source, target, source.attackDamageAmount(target));
            requireClose(245, hit.dealtDamage(), "A later unshielded shot must still use all stored charges.");
            fixture.tower.onPrimaryAttack(target, hit.outgoingDamage(), DamageType.MAGIC, target.position());
            require(fixture.tower.charges() == 0, "One valid primary shot must consume all charges.");
            fixture.tower.onPrimaryAttack(target, hit.outgoingDamage(), DamageType.MAGIC, target.position());
            fixture.tower.onWaveStarted(fixture.lane, 15);
            fixture.tower.settleRound(fixture.lane, 15, true);
            var sample = fixture.tower.telemetrySample(15, 41, 7, "ROUND_END");
            require(sample.capacitorChargedShots() == 1, "One charged shot is counted, not its three charges or repeated wave callbacks.");
            require(sample.ordnanceShellsFired() == null && sample.cocoonSuccesses() == null && sample.hatched() == null,
                    "Metrics for other tower mechanics remain unmeasured rather than zero.");
            require(sample.round() == 15 && sample.tick() == 41 && sample.towerRef() == 7 && sample.slotWeight() == 1,
                    "A sample must retain its supplied observation coordinates and actual capacity.");
            OffensiveAugmentTower copy = copy(fixture.tower);
            require(copy.telemetrySample(15, 42, 7, "REMOVED").capacitorChargedShots() == 1,
                    "Logical tower copies preserve the final counter after round settlement.");
            require(copy.telemetrySample(16, 42, 7, "REMOVED").capacitorChargedShots() == 0,
                    "A new preparation event must not relabel the previous round's shots.");
            fixture.start(16);
            require(fixture.tower.telemetrySample(16, 43, 7, "ROUND_START").capacitorChargedShots() == 0,
                    "A new wave resets the action counter.");
            require(copy.telemetrySample(15, 42, 7, "REMOVED").capacitorChargedShots() == 1,
                    "Resetting the original cannot mutate an immutable copied observation.");
            context.succeed();
        }
    }

    @GameTest
    public void cocoonHatchesOnceWithoutReplacingTheLogicalTower(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, AugmentTowers.STARLIGHT_COCOON)) {
            UUID logicalId = fixture.tower.logicalId();
            GridPosition original = fixture.tower.originalPosition();
            requireClose(0, fixture.source().attackRange(), "A cocoon must not have a basic attack.");
            require(TowerCapacity.slotCost(fixture.tower) == 2, "The cocoon must reserve two slots before hatching.");
            fixture.start(15);
            fixture.tower.settleRound(fixture.lane, 15, true);
            require(fixture.tower.telemetrySample(15, 10, 1, "ROUND_END").cocoonSuccesses() == 1,
                    "A completed protected wave records one persistent cocoon success.");
            fixture.lane.resetForRound();
            fixture.tower.beginPrepare(fixture.lane, 16);
            fixture.start(16);
            fixture.lane.killTower(fixture.tower);
            fixture.tower.settleRound(fixture.lane, 16, true);
            require(fixture.tower.telemetrySample(16, 20, 1, "ROUND_END").cocoonSuccesses() == 1,
                    "A destroyed wave neither earns a success nor loses earlier successes.");
            fixture.lane.resetForRound();
            fixture.tower.beginPrepare(fixture.lane, 17);
            fixture.start(17);
            fixture.tower.settleRound(fixture.lane, 17, true);
            fixture.tower.settleRound(fixture.lane, 17, true);
            require(!fixture.tower.hatched(), "Settlement alone must not hatch the cocoon.");
            var awaiting = fixture.tower.telemetrySample(17, 30, 1, "ROUND_END");
            require(awaiting.cocoonSuccesses() == 2 && !awaiting.hatched(),
                    "Repeated settlement records two successes and no premature hatch.");
            fixture.lane.resetForRound();
            fixture.phaseRound = 18;
            fixture.tower.beginPrepare(fixture.lane, 18);
            fixture.tower.beginPrepare(fixture.lane, 18);
            require(fixture.tower.hatched(), "Two survived waves must hatch on the real next preparation.");
            require(fixture.tower.logicalId().equals(logicalId), "Hatching must preserve logical identity.");
            require(fixture.tower.originalPosition().equals(original), "Hatching must preserve the original position.");
            requireClose(600, fixture.tower.health(), "The hatched body must start at full 600 health.");
            requireClose(600, fixture.source().getMaxHealth(), "Entity and logical maximum health must agree.");
            requireClose(5, fixture.source().attackRange(), "The sentinel must gain its five-block range.");
            requireClose(110, fixture.source().attackDamageAmount(null), "The sentinel must gain its magic attack.");
            require(fixture.tower.primaryDamageType() == DamageType.MAGIC, "The sentinel must remain magical.");
            require(fixture.tower.visual().entityTypeId().equals("minecraft:iron_golem"), "Hatching must change the visible body.");
            var hatched = fixture.tower.telemetrySample(18, 31, 1, "HATCHED");
            require(hatched.cocoonSuccesses() == 2 && hatched.hatched() && hatched.slotWeight() == 2,
                    "Hatching records persistent progress and the existing two occupied slots.");
            var copied = copy(fixture.tower).telemetrySample(18, 31, 1, "HATCHED");
            require(copied.equals(hatched), "State copies preserve the complete hatched observation.");
            var hatchEvents = fixture.telemetry.snapshot().towerSamples().stream()
                    .filter(sample -> sample.eventType().equals("HATCHED")).toList();
            require(hatchEvents.size() == 1 && hatchEvents.getFirst().round() == 18 && hatchEvents.getFirst().hatched()
                            && hatchEvents.getFirst().cocoonSuccesses() == 2,
                    "The real transition records one HATCHED event after two successful waves, never on failed or repeated transitions.");
            context.succeed();
        }
    }

    @GameTest
    public void factoryShellIncludesPrimaryAndAtMostFourOtherEnemies(GameTestHelper context) {
        try (Fixture fixture = new Fixture(context, AugmentTowers.ORDNANCE_FACTORY)) {
            fixture.tower.beginPrepare(fixture.lane, 15);
            UUID transaction = UUID.randomUUID();
            fixture.tower.onPaidSummon(transaction, 250);
            fixture.tower.onPaidSummon(transaction, 250);
            fixture.start(15);
            require(fixture.tower.preparedShells() == 2, "Repeated purchase callbacks must not duplicate ammunition.");
            var source = fixture.source();
            Vec3 center = source.position().add(0, 0, 4);
            var primary = fixture.target("factory_primary", center, 1000);
            var result = fixture.tower.damagePrimaryAttackTargetResult(source, primary, source.attackDamageAmount(primary));
            primary.setPos(center.add(2.9, 0, 0));
            List<SemionMonsterEntity> others = new ArrayList<>();
            for (int i = 1; i <= 6; i++) others.add(fixture.target("factory_other_" + i, center.add(i * .2, 0, 0), 1000));
            fixture.tower.onPrimaryAttack(primary, result.outgoingDamage(), DamageType.PHYSICAL, center);
            requireClose(860, primary.runtimeMonster().health(), "The surviving primary must receive the shell after its basic hit.");
            require(others.stream().filter(target -> target.runtimeMonster().health() < 1000).count() == 4,
                    "Only four additional targets may receive one shell.");
            requireClose(600, fixture.tower.specialDamageDealt(), "Five shell hits must be attributed as separate special damage.");
            require(fixture.tower.preparedShells() == 1, "Exactly one shell must be consumed by the first attack.");
            fixture.tower.onPrimaryAttack(primary, result.outgoingDamage(), DamageType.PHYSICAL, center);
            require(fixture.tower.preparedShells() == 1, "Another attack in the same tick must not fire a second shell.");
            fixture.tower.onWaveStarted(fixture.lane, 15);
            require(fixture.tower.telemetrySample(15, 20, 1, "ROUND_END").ordnanceShellsFired() == 1,
                    "The observation counts fired shells, not prepared ammo or targets damaged.");
            fixture.tower.settleRound(fixture.lane, 15, true);
            fixture.tower.beginPrepare(fixture.lane, 16);
            require(fixture.tower.preparedShells() == 0, "Remaining shells and the fifty-emerald remainder must not carry over.");
            require(fixture.tower.telemetrySample(15, 21, 1, "ROUND_END").ordnanceShellsFired() == 1,
                    "Settlement and preparation must not erase the previous wave's measured fire count.");
            require(fixture.tower.telemetrySample(16, 21, 1, "REMOVED").ordnanceShellsFired() == 0,
                    "Next-round preparation samples must not duplicate the preceding round's artillery count.");
            OffensiveAugmentTower copy = copy(fixture.tower);
            require(copy.telemetrySample(15, 21, 1, "REMOVED").ordnanceShellsFired() == 1,
                    "A copied logical factory retains its final measured count.");
            fixture.start(16);
            fixture.start(16);
            require(fixture.tower.telemetrySample(16, 22, 1, "ROUND_START").ordnanceShellsFired() == 0,
                    "Only the real next wave resets the measured fire count.");
            context.succeed();
        }
    }

    static void markLowPressure(Monster monster) {
        var economy = new PlayerEconomy(EconomyConfig.defaultConfig());
        economy.overrideStartingValues(0, 1000, 0, 0);
        var buyer = new SemionPlayer(UUID.randomUUID(), "low-pressure-test", TeamId.BLUE, 1, economy);
        AugmentEconomyService.beginPrepare(buyer, 15);
        AugmentEconomyService.onSelected(buyer, "low_pressure_high_yield", 15, Map.of());
        require(AugmentEconomyService.setContract(buyer, 15, AugmentEconomyService.Contract.LOW_PRESSURE), "The test contract is available.");
        var plan = AugmentEconomyService.quotePurchase(buyer, UUID.randomUUID(), 15, true, true, false, true, true, 100, 4);
        monster.setOrigin(MonsterOrigin.NORMAL_PAID);
        require(AugmentEconomyService.commitPurchase(buyer, plan, monster), "The successful purchase records its weak-body mark.");
        require(AugmentEconomyService.isLowPressure(monster), "The test monster carries the real contract mark.");
    }

    private static OffensiveAugmentTower copy(OffensiveAugmentTower original) {
        var copy = new OffensiveAugmentTower(original.type(), original.ownerPlayer(), original.teamId(), original.laneId(),
                original.originalPosition(), original.position());
        copy.copyFrom(original, 0);
        return copy;
    }

    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper context;
        private final PlayerLane lane;
        private final OffensiveAugmentTower tower;
        private final List<SemionMonsterEntity> monsters = new ArrayList<>();
        private final AugmentTelemetry telemetry = new AugmentTelemetry();
        private int phaseRound = 15;

        private Fixture(GameTestHelper context, TowerType type) {
            this.context = context;
            UUID owner = UUID.randomUUID();
            var layout = new LaneRegionLayout(1, Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 2, 1))),
                    List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 7)))),
                    Vec3.atCenterOf(context.absolutePos(new BlockPos(12, 2, 12))),
                    BlockBounds.of(context.absolutePos(new BlockPos(0, 1, 0)), context.absolutePos(new BlockPos(14, 6, 14))),
                    List.of(GridPosition.from(context.absolutePos(new BlockPos(10, 2, 11)))));
            lane = new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
            telemetry.bindClock(context.getLevel()::getGameTime, () -> phaseRound);
            lane.assignAugmentTelemetry(telemetry);
            AreaEffectLaneIndex.register(lane);
            GridPosition position = GridPosition.from(context.absolutePos(new BlockPos(3, 2, 3)));
            tower = new OffensiveAugmentTower(type, owner, TeamId.RED, 1, position, position);
            lane.addTower(tower);
            source().setNoAi(true);
        }

        private SemionTowerEntity source() { return tower.runtimeEntity(lane).orElseThrow(); }
        private void start(int round) { phaseRound = round; lane.markWaveStarted(round); }
        private SemionMonsterEntity target(String id, Vec3 position, double health) {
            Monster monster = new Monster(id, TeamId.RED, 1, Optional.empty(), Optional.empty(), health, 0, 1,
                    AttackKind.MELEE, "minecraft:zombie", 0);
            monster.setOrigin(MonsterOrigin.NATURAL_WAVE);
            var entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
            entity.configureFrom(monster, lane.laneLayout());
            entity.setNoAi(true);
            entity.setPos(position);
            require(context.getLevel().addFreshEntity(entity), "The test target must spawn.");
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

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void requireClose(double expected, double actual, String message) {
        require(Math.abs(expected - actual) < .001, message + " Expected " + expected + ", got " + actual);
    }
}
