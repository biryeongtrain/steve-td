package kim.biryeong.semiontd.gametest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentEconomyService;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.goal.SiegeTrueDamageGoal;
import kim.biryeong.semiontd.entity.goal.UtilitySupportGoal;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.summon.UtilitySupportProfile;
import kim.biryeong.semiontd.test.tower.TestTower;
import kim.biryeong.semiontd.test.tower.TestTowerTypes;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

public final class UtilitySupportGameTest {
    @GameTest(maxTicks = 60)
    public void payloadHealingAndSupportNamePreserveBuyerAndProgressAcrossRecreation(GameTestHelper context) {
        int lane = 17008;
        SemionPlayer buyer = new SemionPlayer(UUID.randomUUID(), "지원 구매자", TeamId.BLUE, 1,
                new PlayerEconomy(EconomyConfig.defaultConfig()));
        AugmentEconomyService.beginPrepare(buyer, 15);
        AugmentEconomyService.onSelected(buyer, "semiontd:support_performance", 15, Map.of());
        AugmentEconomyService.onSelected(buyer, "semiontd:additional_payload", 15, Map.of());
        AugmentEconomyService.setAdditionalPayload(buyer, 15, true);
        Monster logical = new Monster("ghast", TeamId.RED, lane, Optional.of(buyer.uuid()), Optional.of(TeamId.BLUE),
                100, 0, 10, AttackKind.MELEE, "minecraft:zombie", 0);
        logical.setOrigin(MonsterOrigin.NORMAL_PAID);
        logical.setSenderName(buyer.name());
        var plan = AugmentEconomyService.quotePurchase(buyer, UUID.randomUUID(), 15, true, true, true, false, false, 100, 10);
        AugmentEconomyService.applyPurchaseBody(logical, plan);
        if (!AugmentEconomyService.commitPurchase(buyer, plan, logical)) {
            throw new AssertionError("The support purchase should commit exactly once.");
        }
        SemionMonsterEntity caster = supportCaster(context, logical);
        var senderColor = caster.getCustomName().getStyle().getColor();
        if (!caster.getCustomName().getString().contains("지원 구매자 · 지원 0/3")) {
            throw new AssertionError("Initial support name must preserve the buyer and show zero progress.");
        }
        SemionMonsterEntity target = monster(context, "natural-target", lane, 2, MonsterOrigin.NATURAL_WAVE, 100, 20);
        target.setNoGravity(true);
        new UtilitySupportGoal(caster, profile("ghast", 1)).tick();
        equal(80.75, target.runtimeMonster().health(), "Additional payload must multiply actual healing exactly once.");
        equal(135, logical.maxHealth(), "Payload health must be applied before entity creation.");
        context.runAfterDelay(21, () -> {
            if (!caster.getCustomName().getString().contains("지원 구매자 · 지원 1/3")
                    || !java.util.Objects.equals(senderColor, caster.getCustomName().getStyle().getColor())) {
                throw new AssertionError("Support progress must update within 20 ticks without changing buyer name/color.");
            }
            UUID id = logical.logicalId();
            caster.discard();
            SemionMonsterEntity recreated = supportCaster(context, logical);
            if (!recreated.getCustomName().getString().contains("지원 1/3") || !id.equals(recreated.runtimeMonster().logicalId())) {
                throw new AssertionError("Recreation must retain the same support purchase and logical progress.");
            }
            equal(135, recreated.getMaxHealth(), "Recreation must retain the payload health modifier.");
            context.succeed();
        });
    }

    @GameTest
    public void guardianUsesNearestEligibleTargetsAndExcludesSelfBossProxyAndOtherLane(GameTestHelper context) {
        int lane = 17001;
        SemionMonsterEntity caster = monster(context, "guardian", lane, 1, MonsterOrigin.NORMAL_PAID, 1000, 1000);
        SemionMonsterEntity nearest = monster(context, "nearest", lane, 2, MonsterOrigin.NATURAL_WAVE, 1000, 1000);
        SemionMonsterEntity second = monster(context, "second", lane, 3, MonsterOrigin.NORMAL_PAID, 1000, 1000);
        SemionMonsterEntity third = monster(context, "third", lane, 4, MonsterOrigin.NATURAL_WAVE, 1000, 1000);
        SemionMonsterEntity boss = monster(context, "warden_boss_15", lane, 1.1, MonsterOrigin.NATURAL_WAVE, 1000, 1000);
        SemionMonsterEntity proxy = monster(context, "proxy", lane, 1.2, MonsterOrigin.BUILDER_PROXY, 1000, 1000);
        SemionMonsterEntity otherLane = monster(context, "other", lane + 1, 1.3, MonsterOrigin.NATURAL_WAVE, 1000, 1000);
        new UtilitySupportGoal(caster, profile("guardian", 2)).tick();
        checkShield(nearest, 30, 0);
        checkShield(second, 30, 0);
        for (SemionMonsterEntity excluded : List.of(caster, third, boss, proxy, otherLane)) {
            checkShield(excluded, 0, 0);
        }
        context.succeed();
    }

    @GameTest
    public void ghastPrioritizesMissingHealthAndWitherPrioritizesHealthRatioIncludingSelf(GameTestHelper context) {
        SemionMonsterEntity ghast = monster(context, "ghast", 17003, 1, MonsterOrigin.NORMAL_PAID, 1000, 100);
        SemionMonsterEntity close = monster(context, "close", 17003, 2, MonsterOrigin.NATURAL_WAVE, 1000, 980);
        SemionMonsterEntity injured = monster(context, "injured", 17003, 3, MonsterOrigin.NATURAL_WAVE, 1000, 600);
        new UtilitySupportGoal(ghast, profile("ghast", 1)).tick();
        equal(645, injured.runtimeMonster().health(), "Ghast should heal the largest injury.");
        equal(980, close.runtimeMonster().health(), "Nearer small injury must not take priority.");
        equal(100, ghast.runtimeMonster().health(), "Ghast must not heal itself.");

        SemionMonsterEntity wither = monster(context, "wither_skeleton", 17004, 1, MonsterOrigin.NORMAL_PAID, 1000, 250);
        SemionMonsterEntity largerInjury = monster(context, "larger", 17004, 2, MonsterOrigin.NATURAL_WAVE, 2000, 800);
        new UtilitySupportGoal(wither, profile("wither_skeleton", 1)).tick();
        equal(285, wither.runtimeMonster().health(), "Wither must select the lowest ratio, including itself.");
        checkShield(wither, 25, 25);
        equal(800, largerInjury.runtimeMonster().health(), "Missing-health ordering must not replace ratio ordering.");
        checkShield(largerInjury, 0, 0);
        context.succeed();
    }

    @GameTest
    public void shieldSurvivesEntityRecreationAndReportsAttemptedDamageBeforeHealthCap(GameTestHelper context) {
        SemionMonsterEntity caster = monster(context, "warden", 17005, 1, MonsterOrigin.NORMAL_PAID, 1000, 1000);
        SemionMonsterEntity target = monster(context, "target", 17005, 2, MonsterOrigin.NATURAL_WAVE, 1000, 10);
        Monster logical = target.runtimeMonster();
        logical.grantShield(DamageType.PHYSICAL, 30, 100, context.getLevel().getGameTime(), caster.runtimeMonster());
        UUID id = logical.logicalId();
        target.discard();
        SemionMonsterEntity recreated = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        recreated.configureFrom(logical, null);
        recreated.setNoAi(true);
        recreated.setPos(caster.position().add(1, 0, 0));
        context.getLevel().addFreshEntity(recreated);
        checkShield(recreated, 30, 0);
        var result = recreated.applySemionDamageResult(caster.damageSources().mobAttack(caster), 100, DamageType.PHYSICAL);
        equal(70, result.healthDamageAttempted(), "Attempted damage excludes the shield, not the HP cap.");
        equal(10, result.appliedDamage(), "Actual damage is capped by remaining HP.");
        equal(30, result.absorbedDamage(), "Shield absorption must remain attributed across recreation.");
        if (!result.killed() || !id.equals(logical.logicalId()) || recreated.receiveHealing(100)) {
            throw new AssertionError("Recreation must preserve logical identity, and healing must not revive a dead unit.");
        }
        context.succeed();
    }

    @GameTest
    public void wardenPhysicalCanaryUsesNormalDamageReduction(GameTestHelper context) {
        SemionMonsterEntity warden = monster(context, "warden", 17006, 1, MonsterOrigin.NORMAL_PAID, 1000, 1000);
        var position = context.absolutePos(new BlockPos(2, 1, 1));
        TestTower runtime = new TestTower(TestTowerTypes.TEST_DIRECT, UUID.randomUUID(), TeamId.RED, 17006, GridPosition.from(position));
        SemionTowerEntity tower = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        tower.configure(runtime, null);
        tower.setPos(Vec3.atBottomCenterOf(position));
        tower.setNoAi(true);
        context.getLevel().addFreshEntity(tower);
        tower.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_REDUCTION, 0.50, 100);
        warden.setTarget(tower);
        double before = tower.getHealth();
        new SiegeTrueDamageGoal(warden, 20, 60, 20, 0, false).tick();
        equal(10, before - tower.getHealth(), "The canary must respect the ordinary damage-reduction path.");
        context.succeed();
    }

    private static UtilitySupportProfile profile(String id, int maxTargets) {
        var definition = SummonConfig.defaultConfig().summons().get(id);
        return UtilitySupportProfile.from(definition.withAbilityValues(Map.of("supportMaxTargets", (double) maxTargets)));
    }

    private static SemionMonsterEntity supportCaster(GameTestHelper context, Monster logical) {
        SemionMonsterEntity caster = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        caster.configureFrom(logical, null);
        caster.setPos(context.absoluteVec(new Vec3(1, 1, 1)));
        caster.setNoAi(true);
        caster.setNoGravity(true);
        context.getLevel().addFreshEntity(caster);
        return caster;
    }

    private static SemionMonsterEntity monster(GameTestHelper context, String id, int lane, double x,
            MonsterOrigin origin, double maxHealth, double health) {
        Monster logical = new Monster(id, TeamId.RED, lane, Optional.of(UUID.randomUUID()), Optional.of(TeamId.BLUE),
                maxHealth, 0, 10, AttackKind.MELEE, "minecraft:zombie", 0);
        logical.setOrigin(origin);
        logical.syncHealth(health);
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(logical, null);
        entity.setPos(context.absoluteVec(new Vec3(x, 1, 1)));
        entity.setNoAi(true);
        context.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static void checkShield(SemionMonsterEntity entity, double physical, double magic) {
        long now = entity.level().getGameTime();
        equal(physical, entity.runtimeMonster().shieldRemaining(DamageType.PHYSICAL, now), "Unexpected physical shield on " + entity.runtimeMonster().id());
        equal(magic, entity.runtimeMonster().shieldRemaining(DamageType.MAGIC, now), "Unexpected magic shield on " + entity.runtimeMonster().id());
    }

    private static void equal(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.0001) {
            throw new AssertionError(message + " Expected " + expected + ", got " + actual);
        }
    }
}
