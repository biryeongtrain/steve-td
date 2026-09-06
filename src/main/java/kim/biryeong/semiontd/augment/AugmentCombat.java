package kim.biryeong.semiontd.augment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.AugmentTelemetry;
import kim.biryeong.semiontd.game.AugmentTelemetrySnapshot;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.army.ArmyTower;
import kim.biryeong.semiontd.tower.demonlord.DemonLordTowers;
import kim.biryeong.semiontd.tower.developer.DeveloperBug;
import kim.biryeong.semiontd.tower.developer.DeveloperTower;
import kim.biryeong.semiontd.tower.end.EndTowers;
import kim.biryeong.semiontd.tower.hero.HeroTower;
import kim.biryeong.semiontd.tower.insect.InsectUnitTower;
import kim.biryeong.semiontd.tower.mage.MageTowers;
import kim.biryeong.semiontd.tower.legion.IllusionRuntimeTower;
import kim.biryeong.semiontd.tower.queen.QueenCardTower;
import kim.biryeong.semiontd.tower.plant.PlantMineTower;
import kim.biryeong.semiontd.tower.warlock.WarlockTower;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

/** Normal-tower augment effects. Selection state remains owned by the player. */
public final class AugmentCombat {
    private static final TowerDataKey<Wave> WAVE = key("wave", Wave.class);
    private static final TowerDataKey<Integer> HEAT = key("heat", Integer.class);
    private static final TowerDataKey<Integer> MASTERY = key("mastery", Integer.class);
    private static final TowerDataKey<Integer> LAST_SETTLED = key("last_settled", Integer.class);
    private static final TowerDataKey<Boolean> MASTERY_ELIGIBLE = key("mastery_eligible", Boolean.class);
    private static final TowerDataKey<Double> ENEMY_DAMAGE = key("enemy_damage", Double.class);
    private static final TowerDataKey<Integer> BARRAGE = key("barrage", Integer.class);
    private static final TowerDataKey<Integer> FINISHING_HITS = key("finishing_hits", Integer.class);
    private static final TowerDataKey<Long> BARRAGE_HITS = key("barrage_hits", Long.class);
    private static final TowerDataKey<Long> DOMINO_TRANSFERS = key("domino_transfers", Long.class);
    private static final TowerDataKey<Double> PHYSICAL_DIRECT_HP = key("physical_direct_hp", Double.class);
    private static final TowerDataKey<Double> MAGIC_DIRECT_HP = key("magic_direct_hp", Double.class);
    private static final TowerDataKey<Double> ARMOR_REDUCED = key("armor_reduced", Double.class);
    private static final TowerDataKey<Double> ARMOR_INCREASED = key("armor_increased", Double.class);
    private static final TowerDataKey<Integer> LAST_START_SAMPLE = key("last_start_sample", Integer.class);
    private static final TowerDataKey<Integer> LAST_END_SAMPLE = key("last_end_sample", Integer.class);
    private static final TowerDataKey<Integer> LAST_REMOVED_SAMPLE = key("last_removed_sample", Integer.class);
    private static final TowerDataKey<Boolean> ROLES_BROKEN = key("roles_broken", Boolean.class);
    private static final String FRONTLINE = "frontline_specialization";
    private static final Wave NO_WAVE = new Wave(0, 0, false, false, false, false, 0.0, 0, 0.0);

    private AugmentCombat() {}

    public static boolean isNormalPermanent(Tower tower) {
        return isNormalPermanentType(tower) && tower.attachedLane() != null
                && tower.attachedLane().towers().contains(tower);
    }

    /** Structural eligibility for an unplaced catalog preview; combat also requires lane membership. */
    public static boolean isNormalPermanentType(Tower tower) {
        return tower != null && !tower.isAugmentTower() && !tower.invulnerable()
                && tower.slotWeight() > 0 && tower.canBeSold()
                && ProductionTowerCatalog.entry(tower.type()).isPresent()
                && !(tower instanceof HeroTower) && !(tower instanceof WarlockTower)
                && !(tower instanceof QueenCardTower) && !(tower instanceof InsectUnitTower)
                && !(tower instanceof IllusionRuntimeTower)
                && !EndTowers.isBaseEndTower(tower.type()) && !EndTowers.isTransferableTower(tower.type())
                && !MageTowers.isCore(tower.type()) && !DemonLordTowers.isDemonLordTower(tower.type());
    }

    public static boolean isNormalAttacker(Tower tower) {
        return isNormalPermanent(tower) && tower.type().damage() > 0.0 && tower.type().range() > 0.0;
    }

    public static boolean isOverheatEligible(Tower tower) {
        return isNormalPermanent(tower) && !(tower instanceof ArmyTower army && army.ranks())
                && !(tower instanceof PlantMineTower)
                && !(tower instanceof DeveloperTower developer && developer.hasBug(DeveloperBug.PRICE_TAG))
                && heatStacks(tower) < integer(tower, "overheat_core", "maxStacks", 5);
    }

    public static int heatStacks(Tower tower) {
        return tower == null ? 0 : tower.getDataOrDefault(HEAT, 0);
    }

    public static int masteryStacks(Tower tower) {
        return tower == null ? 0 : tower.getDataOrDefault(MASTERY, 0);
    }

    /** Low-pressure bodies cannot create a new condition; already held bonuses still apply. */
    public static boolean canBuildCondition(Monster monster) {
        return monster != null && !AugmentEconomyService.isLowPressure(monster);
    }

    public static boolean isMasteryEligible(Tower tower) {
        return isNormalAttacker(tower) && tower.health() > 0.0
                && tower.getDataOrDefault(MASTERY_ELIGIBLE, false);
    }

    public static int triangleEligibleCount(PlayerLane lane) {
        return lane == null ? 0 : (int) lane.towers().stream().filter(tower -> triangleEligible(lane, tower)).count();
    }

    public static int independentEligibleCount(PlayerLane lane) {
        return lane == null ? 0 : (int) lane.towers().stream().filter(tower -> independentEligible(lane, tower)).count();
    }

    public static void startWave(PlayerLane lane, int round) {
        long startTick = lane.arenaWorld() == null ? 0 : lane.arenaWorld().getGameTime();
        for (Tower tower : lane.towers()) {
            AugmentSnapshot snapshot = tower.augmentSnapshot();
            boolean twin = snapshot.has("twin_squadron") && isNormalAttacker(tower)
                    && lane.towers().stream().filter(AugmentCombat::isNormalAttacker)
                    .filter(other -> other.type().id().equals(tower.type().id())).count() == 2;
            boolean overheat = snapshot.has("overheat_core") && isOverheatEligible(tower)
                    && selected(tower, "overheat_core");
            tower.setData(WAVE, new Wave(round, startTick,
                    snapshot.has("triangle_formation") && triangleEligible(lane, tower), twin,
                    snapshot.has("independent_position") && independentEligible(lane, tower), overheat,
                    tower.currentMaxHealth(), 0, 0.0));
            tower.setData(ENEMY_DAMAGE, 0.0);
            tower.setData(BARRAGE, 0);
            tower.setData(FINISHING_HITS, 0);
            tower.setData(BARRAGE_HITS, 0L);
            tower.setData(DOMINO_TRANSFERS, 0L);
            tower.setData(PHYSICAL_DIRECT_HP, 0.0);
            tower.setData(MAGIC_DIRECT_HP, 0.0);
            tower.setData(ARMOR_REDUCED, 0.0);
            tower.setData(ARMOR_INCREASED, 0.0);
            tower.setData(ROLES_BROKEN, false);
        }
        int twinGroups = (int) lane.towers().stream().filter(tower -> wave(tower).twin())
                .map(tower -> tower.type().id()).distinct().count();
        int occupiedSlots = lane.towers().stream().mapToInt(Tower::slotWeight).sum();
        int twinSlots = lane.towers().stream().filter(tower -> wave(tower).twin()).mapToInt(Tower::slotWeight).sum();
        double twinRatio = occupiedSlots == 0 ? 0.0 : (double) twinSlots / occupiedSlots;
        for (Tower tower : lane.towers()) {
            Wave wave = wave(tower);
            tower.setData(WAVE, new Wave(wave.round(), wave.startTick(), wave.triangle(), wave.twin(),
                    wave.independent(), wave.overheated(), wave.openingHealth(), twinGroups, twinRatio));
        }
        showFormationLinks(lane);
    }

    /** Called after builder/trait wave-start effects, so mastery uses the actual opening maximum. */
    public static void captureWaveStartHealth(PlayerLane lane) {
        for (Tower tower : lane.towers()) {
            Wave wave = wave(tower);
            tower.setData(WAVE, new Wave(wave.round(), wave.startTick(), wave.triangle(), wave.twin(),
                    wave.independent(), wave.overheated(), tower.currentMaxHealth(), wave.twinGroups(), wave.twinRatio()));
            if (tower.roundMetricsTracker() != null) {
                tower.roundMetricsTracker().captureWaveStartMaxHealth(tower.currentMaxHealth());
            }
            recordCombat(lane, tower, wave.round(), "START", null, "PENDING", 0);
        }
    }

    public static void settleWave(PlayerLane lane, int round) {
        for (Tower tower : lane.towers()) {
            if (tower.getDataOrDefault(LAST_SETTLED, 0) >= round || wave(tower).round() != round) {
                continue;
            }
            double threshold = parameter(tower, "battlefield_mastery", "damageThreshold", .40);
            boolean survived = tower.health() > 0.0;
            boolean qualified = survived && wave(tower).openingHealth() > 0.0
                    && tower.getDataOrDefault(ENEMY_DAMAGE, 0.0) + 1.0e-9 >= wave(tower).openingHealth() * threshold;
            tower.setData(MASTERY_ELIGIBLE, qualified);
            int previousMastery = masteryStacks(tower);
            if (selected(tower, "battlefield_mastery") && qualified) {
                double ratio = tower.health() / Math.max(1.0, tower.currentMaxHealth());
                tower.setData(MASTERY, Math.min(integer(tower, "battlefield_mastery", "maxStacks", 4), masteryStacks(tower) + 1));
                tower.syncHealth(tower.currentMaxHealth() * ratio);
                tower.onStateChanged(lane);
            }
            if (wave(tower).overheated()) {
                tower.setData(HEAT, Math.min(integer(tower, "overheat_core", "maxStacks", 5), heatStacks(tower) + 1));
            }
            String masteryResult = qualified
                    ? (masteryStacks(tower) > previousMastery ? "STACK_GAINED" : "CAP_REACHED")
                    : (survived ? "INSUFFICIENT_DAMAGE" : "DIED");
            recordCombat(lane, tower, round, "END", qualified, masteryResult, 0);
            tower.setData(LAST_SETTLED, round);
            tower.removeData(WAVE);
            tower.setData(BARRAGE, 0);
        }
    }

    /** Observes a terminal round without granting growth or applying a second settlement. */
    public static void captureRoundEnd(PlayerLane lane, int round) {
        for (Tower tower : lane.towers()) {
            if (wave(tower).round() != round) continue;
            boolean qualified = masteryQualified(tower);
            recordCombat(lane, tower, round, "END", qualified, qualified ? "QUALIFIED_UNSETTLED"
                    : tower.health() > 0.0 ? "INSUFFICIENT_DAMAGE" : "DIED", 0);
        }
    }

    /** Called only for a permanent removal, not entity death or an upgrade replacement. */
    public static void onTowerRemoved(PlayerLane lane, Tower tower) {
        if (lane == null || lane.augmentTelemetry() == null) return;
        Integer observedRound = lane.augmentTelemetry().currentRound();
        int round = wave(tower).round() > 0 ? wave(tower).round()
                : observedRound == null ? tower.currentRound() : observedRound;
        recordCombat(lane, tower, round, "REMOVED", false, "REMOVED", masteryStacks(tower));
    }

    public static double maxHealthBonus(Tower tower) {
        if (tower.augmentSnapshot().selections().isEmpty() || !isNormalPermanentType(tower)) return 0.0;
        double bonus = 0.0;
        if (selected(tower, "one_man_show")) bonus += parameter(tower, "one_man_show", "maxHealthBonus", .30);
        if (tower.augmentSnapshot().has("wartime_economy")) bonus += parameter(tower, "wartime_economy", "maxHealthBonus", .20);
        if (selected(tower, "battlefield_mastery")) bonus += masteryStacks(tower) * parameter(tower, "battlefield_mastery", "bonusPerStack", .04);
        return bonus;
    }

    public static double damageBonus(Tower tower, SemionTowerEntity entity) {
        if (tower.augmentSnapshot().selections().isEmpty() || !isNormalPermanent(tower)) return 0.0;
        AugmentSnapshot snapshot = tower.augmentSnapshot();
        double bonus = -heatStacks(tower) * parameter(tower, "overheat_core", "penaltyPerStack", .06);
        for (int tier = 1; tier <= 3; tier++) {
            String id = "tactical_designation_" + tier;
            if (!isRoleTarget(tower) && selected(tower, id) && mode(tower, id, "ASSAULT")) {
                bonus += parameter(tower, id, "damageBonus", new double[]{.15, .25, .40}[tier - 1]);
            }
        }
        Wave wave = wave(tower);
        if (wave.triangle()) bonus += parameter(tower, "triangle_formation", "damageBonus", .08);
        if (wave.twin()) bonus += parameter(tower, "twin_squadron", "damageBonus", .10);
        if (wave.independent()) bonus += parameter(tower, "independent_position", "damageBonus", .12);
        if (wave.overheated()) bonus += parameter(tower, "overheat_core", "damageBonus", .40);
        if (engagementActive(tower, entity, "QUICK")) bonus += parameter(tower, "engagement_plan", "quickDamageBonus", .18);
        if (engagementActive(tower, entity, "LONG")) bonus += parameter(tower, "engagement_plan", "longDamageBonus", .10);
        if (rolesActive(tower)) {
            bonus += isVanguard(tower)
                    ? -parameter(tower, FRONTLINE, "vanguardDamagePenalty", .25)
                    : parameter(tower, FRONTLINE, "artilleryDamageBonus", .30);
        }
        if (selected(tower, "battlefield_mastery")) bonus += masteryStacks(tower) * parameter(tower, "battlefield_mastery", "bonusPerStack", .04);
        if (snapshot.has("one_man_show")) bonus += selected(tower, "one_man_show")
                ? parameter(tower, "one_man_show", "damageBonus", 1.0) : -parameter(tower, "one_man_show", "otherDamagePenalty", .20);
        if (snapshot.has("wartime_economy")) bonus += parameter(tower, "wartime_economy", "damageBonus", .35);
        return bonus;
    }

    public static double primaryDamageBonus(Tower tower, SemionMonsterEntity target) {
        if (tower.augmentSnapshot().selections().isEmpty() || !isNormalAttacker(tower) || target == null) return 0.0;
        double bonus = 0.0;
        for (int tier = 1; tier <= 3; tier++) {
            String id = "finishing_fire_" + tier;
            if (tower.augmentSnapshot().has(id) && canBuildCondition(target.runtimeMonster())
                    && target.getHealth() <= target.getMaxHealth() * .5) {
                bonus += parameter(tower, id, "damageBonus", new double[]{.20, .35, .55}[tier - 1]);
            }
        }
        if (tower.augmentSnapshot().has("winning_barrage") && tower.getDataOrDefault(BARRAGE, 0) > 0) {
            bonus += parameter(tower, "winning_barrage", "damageBonus", .30);
        }
        return bonus;
    }

    public static void onPrimaryAttackResolved(SemionTowerEntity entity, SemionMonsterEntity target, Tower.DamageResult result) {
        Tower tower = entity.runtimeTower();
        if (!isNormalAttacker(tower) || result.dealtDamage() <= 0.0) return;
        boolean eligibleKill = result.killed() && canBuildCondition(target.runtimeMonster())
                && (target.runtimeMonster().origin() == MonsterOrigin.NATURAL_WAVE
                || target.runtimeMonster().origin() == MonsterOrigin.NORMAL_PAID);
        if (tower.augmentSnapshot().has("winning_barrage")) {
            // A use means an already charged primary actually dealt HP damage, not a reload.
            if (tower.getDataOrDefault(BARRAGE, 0) > 0) {
                tower.setData(BARRAGE_HITS, tower.getDataOrDefault(BARRAGE_HITS, 0L) + 1);
            }
            int remaining = Math.max(0, tower.getDataOrDefault(BARRAGE, 0) - 1);
            tower.setData(BARRAGE, eligibleKill ? integer(tower, "winning_barrage", "charges", 3) : remaining);
        }
        if (canBuildCondition(target.runtimeMonster()) && result.healthBeforeHit() <= target.getMaxHealth() * .5
                && (tower.augmentSnapshot().has("finishing_fire_1") || tower.augmentSnapshot().has("finishing_fire_2")
                || tower.augmentSnapshot().has("finishing_fire_3"))) {
            tower.setData(FINISHING_HITS, tower.getDataOrDefault(FINISHING_HITS, 0) + 1);
        }
        if (eligibleKill && tower.augmentSnapshot().has("domino_fire") && tower.primaryDamageType() != DamageType.TRUE) {
            double amount = dominoDamage(result.healthDamageAttempted(), result.healthBeforeHit(),
                    parameter(tower, "domino_fire", "overkillRatio", .60), parameter(tower, "domino_fire", "damageCapRatio", .50));
            if (amount <= 0.0) return;
            var origin = target.runtimeMonster();
            MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                    ResourceLocation.fromNamespaceAndPath("semiontd", "augment_domino_fire"), entity,
                    target.position(), parameter(tower, "domino_fire", "radius", 4), Set.of(target.getUUID()),
                    other -> other.runtimeMonster().targetTeam() == origin.targetTeam()
                            && other.runtimeMonster().targetLaneId() == origin.targetLaneId(),
                    AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH)).nearestTargets(1);
            SemionTdApi.areaEffects().applyToMonsters(request, other -> {
                Tower.DamageResult transferred = tower.damageAugmentTargetResult(entity, other, amount, tower.primaryDamageType());
                if (transferred.dealtDamage() > 0.0) {
                    tower.setData(DOMINO_TRANSFERS, tower.getDataOrDefault(DOMINO_TRANSFERS, 0L) + 1);
                }
                tower.recordAugmentSpecialDamage(transferred.dealtDamage());
                if (transferred.killed()) {
                    tower.onKill(entity, other, amount);
                    return AreaEffectOutcome.KILLED;
                }
                return transferred.dealtDamage() > 0 ? AreaEffectOutcome.APPLIED : AreaEffectOutcome.UNCHANGED;
            });
        }
    }

    public static double dominoDamage(double attempted, double health, double overkillRatio, double capRatio) {
        if (!Double.isFinite(attempted) || !Double.isFinite(health) || attempted <= 0.0) return 0.0;
        return Math.min(Math.max(0.0, attempted - health) * overkillRatio, attempted * capRatio);
    }

    public static double incomingDamage(Tower tower, SemionTowerEntity entity, DamageSource source, double original, double reduced) {
        if (tower.augmentSnapshot().selections().isEmpty() || !isNormalPermanent(tower)) return reduced;
        double reduction = 0.0;
        for (int tier = 1; tier <= 3; tier++) {
            String id = "tactical_designation_" + tier;
            if (!isRoleTarget(tower) && selected(tower, id) && mode(tower, id, "COVER")) {
                reduction += parameter(tower, id, "damageReduction", new double[]{.12, .20, .30}[tier - 1]);
            }
        }
        Wave wave = wave(tower);
        if (wave.triangle()) reduction += parameter(tower, "triangle_formation", "damageReduction", .08);
        if (wave.independent()) reduction += parameter(tower, "independent_position", "damageReduction", .08);
        if (engagementActive(tower, entity, "LONG")) reduction += parameter(tower, "engagement_plan", "longDamageReduction", .10);
        if (rolesActive(tower) && isVanguard(tower)) reduction += parameter(tower, FRONTLINE, "vanguardDamageReduction", .30);
        double result = reduced * (1.0 - Math.min(.60, reduction));
        if (wave.round() > 0 && tower.augmentSnapshot().has("biased_armor")
                && source.getEntity() instanceof SemionMonsterEntity monster && source.getDirectEntity() == monster
                && monster.runtimeMonster() != null && monster.runtimeMonster().damageType() != DamageType.TRUE) {
            boolean selectedType = mode(tower, "biased_armor", monster.runtimeMonster().damageType().name());
            double beforeArmor = result;
            result = biasedDamage(original, result, selectedType,
                    parameter(tower, "biased_armor", "selectedMultiplier", .75),
                    parameter(tower, "biased_armor", "oppositeMultiplier", 1.35));
            tower.setData(ARMOR_REDUCED, tower.getDataOrDefault(ARMOR_REDUCED, 0.0) + Math.max(0.0, beforeArmor - result));
            tower.setData(ARMOR_INCREASED, tower.getDataOrDefault(ARMOR_INCREASED, 0.0) + Math.max(0.0, result - beforeArmor));
        }
        if (rolesActive(tower) && !isVanguard(tower)) result *= parameter(tower, FRONTLINE, "artilleryIncomingMultiplier", 1.25);
        return result;
    }

    public static double biasedDamage(double original, double reduced, boolean selectedType, double selectedMultiplier, double oppositeMultiplier) {
        return selectedType ? Math.max(.40 * original, selectedMultiplier * reduced) : oppositeMultiplier * reduced;
    }

    public static void recordEnemyHealthDamage(Tower tower, DamageSource source, double amount) {
        if (source.getEntity() instanceof SemionMonsterEntity monster && Double.isFinite(amount) && amount > 0.0) {
            if (tower.roundMetricsTracker() != null) tower.roundMetricsTracker().recordEnemyHealthDamage(amount);
            if (wave(tower).round() > 0 && canBuildCondition(monster.runtimeMonster())) {
                tower.setData(ENEMY_DAMAGE, tower.getDataOrDefault(ENEMY_DAMAGE, 0.0) + amount);
            }
            if (wave(tower).round() > 0 && tower.augmentSnapshot().has("biased_armor")
                    && source.getDirectEntity() == monster && monster.runtimeMonster() != null) {
                TowerDataKey<Double> key = switch (monster.runtimeMonster().damageType()) {
                    case PHYSICAL -> PHYSICAL_DIRECT_HP;
                    case MAGIC -> MAGIC_DIRECT_HP;
                    case TRUE -> null;
                };
                if (key != null) tower.setData(key, tower.getDataOrDefault(key, 0.0) + amount);
            }
        }
    }

    private static boolean masteryQualified(Tower tower) {
        return tower.health() > 0.0 && wave(tower).openingHealth() > 0.0
                && tower.getDataOrDefault(ENEMY_DAMAGE, 0.0) + 1.0e-9 >= wave(tower).openingHealth()
                * parameter(tower, "battlefield_mastery", "damageThreshold", .40);
    }

    private static void recordCombat(PlayerLane lane, Tower tower, int round, String stage,
                                     Boolean qualified, String masteryResult, int masteryLost) {
        AugmentTelemetry telemetry = lane.augmentTelemetry();
        if (telemetry == null || !isNormalPermanentType(tower) || round < 1) return;
        TowerDataKey<Integer> marker = switch (stage) {
            case "START" -> LAST_START_SAMPLE;
            case "END" -> LAST_END_SAMPLE;
            case "REMOVED" -> LAST_REMOVED_SAMPLE;
            default -> throw new IllegalArgumentException("Unknown combat observation stage: " + stage);
        };
        if (tower.getDataOrDefault(marker, 0) >= round
                || (stage.equals("END") && tower.getDataOrDefault(LAST_REMOVED_SAMPLE, 0) >= round)) return;
        boolean activeWave = wave(tower).round() == round;
        AugmentSnapshot snapshot = tower.augmentSnapshot();
        boolean mastery = selected(tower, "battlefield_mastery");
        boolean twins = snapshot.has("twin_squadron") && activeWave;
        boolean armor = snapshot.has("biased_armor") && activeWave;
        boolean finishing = activeWave && (snapshot.has("finishing_fire_1")
                || snapshot.has("finishing_fire_2") || snapshot.has("finishing_fire_3"));
        var state = new AugmentTelemetrySnapshot.CombatState(
                activeWave ? wave(tower).openingHealth() : null,
                activeWave && tower.roundMetricsTracker() != null ? tower.roundMetricsTracker().snapshot().enemyHpDamage() : null,
                snapshot.has("overheat_core") ? heatStacks(tower) : null,
                mastery ? masteryStacks(tower) : null,
                mastery ? qualified : null,
                mastery ? masteryResult : null,
                mastery ? masteryLost : null,
                twins ? wave(tower).twin() : null,
                twins ? wave(tower).twinGroups() : null,
                twins ? wave(tower).twinRatio() : null,
                armor ? snapshot.choice("biased_armor").mode() : null,
                armor ? tower.getDataOrDefault(PHYSICAL_DIRECT_HP, 0.0) : null,
                armor ? tower.getDataOrDefault(MAGIC_DIRECT_HP, 0.0) : null,
                armor ? tower.getDataOrDefault(ARMOR_REDUCED, 0.0) : null,
                armor ? tower.getDataOrDefault(ARMOR_INCREASED, 0.0) : null,
                finishing ? tower.getDataOrDefault(FINISHING_HITS, 0).longValue() : null,
                activeWave && snapshot.has("winning_barrage") ? tower.getDataOrDefault(BARRAGE_HITS, 0L) : null,
                activeWave && snapshot.has("domino_fire") ? tower.getDataOrDefault(DOMINO_TRANSFERS, 0L) : null,
                activeWave && mastery ? tower.getDataOrDefault(ENEMY_DAMAGE, 0.0) : null);
        telemetry.recordCombat(new AugmentTelemetrySnapshot.CombatRoundSample(
                round, telemetry.towerRef(tower.logicalId()), tower.type().id(), stage, state));
        tower.setData(marker, round);
    }

    public static void onTowerUnavailable(PlayerLane lane, Tower tower) {
        if (lane == null || !isRoleTarget(tower)) return;
        for (Tower other : lane.towers()) {
            if (isRoleTarget(other)) other.setData(ROLES_BROKEN, true);
        }
    }

    public static int aggroPriority(Tower tower, int base) {
        return base;
    }

    public static boolean prefersEqualDistance(Tower tower) {
        return rolesActive(tower) && isVanguard(tower);
    }

    public static boolean isVanguard(Tower tower) {
        return selected(tower, FRONTLINE);
    }

    public static List<String> detailLines(Tower tower) {
        if (!isNormalPermanent(tower)) return List.of();
        List<String> lines = new ArrayList<>();
        if (heatStacks(tower) > 0) lines.add("열화 " + heatStacks(tower) + "/5");
        if (selected(tower, "battlefield_mastery")) lines.add("숙련 " + masteryStacks(tower) + "/4 · 조건 피해 "
                + Math.round(tower.getDataOrDefault(ENEMY_DAMAGE, 0.0)) + "/" + Math.round(wave(tower).openingHealth() * .40));
        if (tower.augmentSnapshot().has("winning_barrage")) lines.add("연승 탄환 " + tower.getDataOrDefault(BARRAGE, 0) + "/3");
        if (tower.getDataOrDefault(FINISHING_HITS, 0) > 0) lines.add("마무리 사격 " + tower.getDataOrDefault(FINISHING_HITS, 0) + "회");
        if (wave(tower).triangle()) lines.add("삼각 진형 활성");
        if (wave(tower).twin()) lines.add("쌍둥이 편대 활성");
        if (wave(tower).independent()) lines.add("독립 진지 활성");
        return List.copyOf(lines);
    }

    public static String nameplateSuffix(Tower tower) {
        if (tower == null || tower.augmentSnapshot().selections().isEmpty() || !isNormalPermanent(tower)) return "";
        List<String> counters = new ArrayList<>();
        if (selected(tower, "battlefield_mastery")) counters.add("숙련 " + masteryStacks(tower) + "/"
                + integer(tower, "battlefield_mastery", "maxStacks", 4) + " · 조건 피해 "
                + Math.round(tower.getDataOrDefault(ENEMY_DAMAGE, 0.0)) + "/"
                + Math.round(wave(tower).openingHealth() * parameter(tower, "battlefield_mastery", "damageThreshold", .40)));
        if (tower.augmentSnapshot().has("winning_barrage")) counters.add("연승 탄환 "
                + tower.getDataOrDefault(BARRAGE, 0) + "/" + integer(tower, "winning_barrage", "charges", 3));
        return counters.isEmpty() ? "" : " · " + String.join(" · ", counters);
    }

    private static boolean triangleEligible(PlayerLane lane, Tower tower) {
        double radius = parameter(tower, "triangle_formation", "radius", 4);
        return isNormalPermanent(tower) && !isRoleTarget(tower)
                && lane.towers().stream().filter(other -> other != tower && isNormalPermanent(other)
                && distanceSquared(lane, tower, other) <= radius * radius).count()
                >= integer(tower, "triangle_formation", "neighborCount", 2);
    }

    private static boolean independentEligible(PlayerLane lane, Tower tower) {
        double radius = parameter(tower, "independent_position", "radius", 4);
        return isNormalAttacker(tower) && lane.towers().stream().noneMatch(other -> other != tower
                && other.slotWeight() > 0 && ProductionTowerCatalog.entry(other.type()).isPresent()
                && !(other instanceof QueenCardTower) && !(other instanceof InsectUnitTower)
                && !(other instanceof IllusionRuntimeTower)
                && distanceSquared(lane, tower, other) <= radius * radius);
    }

    private static void showFormationLinks(PlayerLane lane) {
        if (lane.arenaWorld() == null) return;
        for (Tower tower : lane.towers()) {
            SemionTowerEntity source = entity(lane, tower);
            if (source == null) continue;
            if (wave(tower).triangle()) {
                double radius = parameter(tower, "triangle_formation", "radius", 4);
                lane.towers().stream().filter(other -> other != tower && isNormalPermanent(other)
                                && distanceSquared(lane, tower, other) <= radius * radius)
                        .sorted(Comparator.comparingDouble((Tower other) -> distanceSquared(lane, tower, other))
                                .thenComparing(Tower::logicalId)).limit(2)
                        .filter(other -> tower.logicalId().compareTo(other.logicalId()) < 0)
                        .forEach(other -> TowerVfxService.showSecondaryAttack(source, position(lane, other)));
            }
            if (prefersEqualDistance(tower)) {
                UUID artillery = tower.augmentSnapshot().choice(FRONTLINE).secondaryTargetId();
                lane.towers().stream().filter(other -> other.logicalId().equals(artillery)).findFirst()
                        .ifPresent(other -> TowerVfxService.showSecondaryAttack(source, position(lane, other)));
            }
        }
    }

    private static double distanceSquared(PlayerLane lane, Tower first, Tower second) {
        return position(lane, first).distanceToSqr(position(lane, second));
    }

    private static Vec3 position(PlayerLane lane, Tower tower) {
        SemionTowerEntity entity = entity(lane, tower);
        return entity == null ? new Vec3(tower.position().x(), tower.position().y(), tower.position().z()) : entity.position();
    }

    private static SemionTowerEntity entity(PlayerLane lane, Tower tower) {
        if (tower instanceof kim.biryeong.semiontd.tower.EntityBackedTower backed && lane.arenaWorld() != null
                && backed.entityId().isPresent() && lane.arenaWorld().getEntity(backed.entityId().getAsInt()) instanceof SemionTowerEntity entity) {
            return entity;
        }
        return null;
    }

    private static boolean engagementActive(Tower tower, SemionTowerEntity entity, String mode) {
        Wave wave = wave(tower);
        if (wave.round() == 0 || !tower.augmentSnapshot().has("engagement_plan") || !mode(tower, "engagement_plan", mode)) return false;
        long now = entity == null || entity.level() == null ? wave.startTick() : entity.level().getGameTime();
        boolean opening = now - wave.startTick() < integer(tower, "engagement_plan", "transitionTicks", 160);
        return "QUICK".equals(mode) == opening;
    }

    private static boolean isRoleTarget(Tower tower) {
        if (tower == null || !tower.augmentSnapshot().has(FRONTLINE)) return false;
        AugmentChoice choice = tower.augmentSnapshot().choice(FRONTLINE);
        return Objects.equals(tower.logicalId(), choice.primaryTargetId()) || Objects.equals(tower.logicalId(), choice.secondaryTargetId());
    }

    private static boolean rolesActive(Tower tower) {
        if (!isRoleTarget(tower) || wave(tower).round() == 0 || tower.getDataOrDefault(ROLES_BROKEN, false) || tower.attachedLane() == null) return false;
        AugmentChoice choice = tower.augmentSnapshot().choice(FRONTLINE);
        return tower.attachedLane().towers().stream().filter(other -> other.health() > 0.0
                && (Objects.equals(other.logicalId(), choice.primaryTargetId()) || Objects.equals(other.logicalId(), choice.secondaryTargetId()))).count() == 2;
    }

    private static boolean selected(Tower tower, String id) {
        return tower != null && tower.augmentSnapshot().has(id)
                && Objects.equals(tower.logicalId(), tower.augmentSnapshot().choice(id).primaryTargetId());
    }

    private static boolean mode(Tower tower, String id, String mode) {
        return mode.equalsIgnoreCase(tower.augmentSnapshot().choice(id).mode());
    }

    private static double parameter(Tower tower, String id, String name, double fallback) {
        return tower.augmentSnapshot().parameter(id, name, fallback);
    }

    private static int integer(Tower tower, String id, String name, int fallback) {
        return (int) parameter(tower, id, name, fallback);
    }

    private static Wave wave(Tower tower) {
        return tower.getDataOrDefault(WAVE, NO_WAVE);
    }

    private static <T> TowerDataKey<T> key(String id, Class<T> type) {
        return TowerDataKey.of(ResourceLocation.fromNamespaceAndPath("semiontd", "augment_" + id), type);
    }

    private record Wave(int round, long startTick, boolean triangle, boolean twin, boolean independent,
                        boolean overheated, double openingHealth, int twinGroups, double twinRatio) {}
}
