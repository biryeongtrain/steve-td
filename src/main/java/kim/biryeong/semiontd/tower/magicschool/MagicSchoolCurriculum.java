package kim.biryeong.semiontd.tower.magicschool;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.SemionGame;

public final class MagicSchoolCurriculum {
    private static final Map<UUID, State> STATES = new HashMap<>();

    private MagicSchoolCurriculum() {
    }

    public enum PurchaseResult {
        PURCHASED, ALREADY_PURCHASED, NOT_ENOUGH_DIAMONDS, INVALID_SCHOOL, ROUND_LIMIT_REACHED,
        PREREQUISITE_REQUIRED, PREVIOUS_SPELL_TIER_REQUIRED, AUGMENT_REQUIRED, TOGGLED
    }

    public enum Upgrade {
        SPELL_POWER("spellPower", "주문 위력 수업", 0, 6, 40, 60, true),
        DARK_ARTS_DEFENSE("darkArtsDefense", "어둠의 마법 방어술 수업", 1, 6, 40, 60, true),
        MAGIC_HISTORY("magicHistory", "마법의 역사 수업", 2, 5, 120, 120, false),
        SORTING_HAT("sortingHat", "기숙사 배정 모자", 9, 1, 80, 0, false),
        CUSTOM_WANDS("customWands", "맞춤형 지팡이 지급", 10, 1, 250, 0, false),
        ADVANCED_SPELLS("advancedSpells", "고등 주문 수업", 11, 1, 350, 0, false),
        DEATH_EATER("deathEater", "죽음을 먹는 자 퇴치", 21, 1, 250, 0, false),
        MENTOR("mentor", "멘토-멘티", 20, 1, 200, 0, false),
        DUELING_PRACTICE("duelingPractice", "결투 실습", 19, 1, 150, 0, false),
        SPELL_PRACTICE("spellPractice", "주문 연마 수업", 18, 1, 125, 0, false),
        SPELL_TRANSFER("spellTransfer", "주문 전이 수업", 28, 1, 300, 0, false),
        POTIONS("potions", "마법약 제조 수업", 29, 1, 300, 0, false),
        QUIDDITCH("quidditch", "퀴디치의 역사", 27, 1, 200, 0, false),
        TRANSFIGURATION("transfiguration", "변신술 수업", 36, 1, 200, 0, false),
        EXPLOSIVE_BARRELS("explosiveBarrels", "폭탄통 개조", 37, 1, 500, 0, false);

        private final String key;
        private final String displayName;
        private final int slot;
        private final int defaultMaxLevel;
        private final int defaultCost;
        private final int defaultCostIncrease;
        private final boolean firstFree;

        Upgrade(String key, String displayName, int slot, int maxLevel, int cost, int costIncrease, boolean firstFree) {
            this.key = key;
            this.displayName = displayName;
            this.slot = slot;
            this.defaultMaxLevel = maxLevel;
            this.defaultCost = cost;
            this.defaultCostIncrease = costIncrease;
            this.firstFree = firstFree;
        }

        public String key() { return key; }
        public String displayName() { return displayName; }
        public int slot() { return slot; }
        public boolean toggleable() { return this == DEATH_EATER || this == DUELING_PRACTICE; }
        public int maxLevel() {
            return defaultMaxLevel == 1 ? 1 : integer(key + "MaxLevel", defaultMaxLevel);
        }
        public long cost(int existingLevel) {
            if (existingLevel == 0 && firstFree) return 0;
            return (long) integer(key + "Cost", defaultCost)
                    + (long) Math.max(0, existingLevel) * integer(key + "CostIncrease", defaultCostIncrease);
        }
    }

    private static final class State {
        final Map<Upgrade, Integer> levels = new EnumMap<>(Upgrade.class);
        final Set<Integer> spellTiers = new HashSet<>();
        boolean deathEaterEnabled;
        boolean duelingPracticeEnabled;
        int lastDeathEaterRound;
        int lastUpgradeRound = -1;
        int quidditchIncreases;
        int lastQuidditchRound;
        int lastBarrelRound = -1;
        long nextBarrelTick;
    }

    public static Map<String, Double> defaultAbilities() {
        var values = new LinkedHashMap<String, Double>();
        for (Upgrade upgrade : Upgrade.values()) {
            values.put(upgrade.key + "Cost", (double) upgrade.defaultCost);
            if (upgrade.defaultMaxLevel > 1) {
                values.put(upgrade.key + "CostIncrease", (double) upgrade.defaultCostIncrease);
                values.put(upgrade.key + "MaxLevel", (double) upgrade.defaultMaxLevel);
            }
        }
        values.put("spellPowerPerLevel", 0.04);
        values.put("darkArtsDefensePerLevel", 0.03);
        values.put("magicHistoryPerLevel", 0.25);
        values.put("mentorRadius", 1.0);
        values.put("deathEaterProficiencyPerRound", 2.0);
        values.put("duelingPracticeHealthRatio", .15);
        values.put("duelingPracticeProficiencyRatio", .30);
        values.put("spellPracticePerTier", 2.0);
        values.put("spellPracticeRepeatBonus", 2.0);
        values.put("spellTransferDamageRatio", 0.10);
        values.put("spellTransferMaxTargets", 5.0);
        values.put("spellTransferCooldownTicks", 70.0);
        values.put("potionsHealRatio", 0.08);
        values.put("quidditchBaseReward", 5.0);
        values.put("quidditchRewardIncrease", 5.0);
        values.put("quidditchMaxIncreases", 5.0);
        values.put("transfigurationCooldownTicks", 60.0);
        values.put("barrelAggro", 100.0);
        values.put("barrelHealth", 1.0);
        values.put("barrelHealthRound1", 5.0);
        values.put("barrelHealthRound2", 15.0);
        values.put("barrelHealthRound3", 25.0);
        values.put("explosiveBarrelRadius", 2.0);
        values.put("explosiveBarrelDamageRatio", 0.30);
        for (int tier = 2; tier <= 6; tier++) values.put("spellTier" + tier + "Cost", (double) defaultSpellTierCost(tier));
        for (int tier = 1; tier <= 6; tier++) values.put("spellChangeTier" + tier + "Cost", (double) MagicSchoolSpell.defaultChangeCost(tier));
        values.put("curseProficiencyThreshold", 500.0);
        values.put("curseLowProficiencyAttackSpeedMultiplier", .1);
        return values;
    }

    public static int level(UUID owner, Upgrade upgrade) {
        State state = STATES.get(owner);
        return state == null ? 0 : Math.min(upgrade.maxLevel(), state.levels.getOrDefault(upgrade, 0));
    }

    public static boolean purchased(UUID owner, Upgrade upgrade) {
        return level(owner, upgrade) > 0;
    }

    public static double lessonMultiplier(UUID owner, Upgrade upgrade) {
        double fallback = upgrade == Upgrade.MAGIC_HISTORY ? 0.25 : upgrade == Upgrade.SPELL_POWER ? 0.04 : 0.03;
        return 1 + level(owner, upgrade) * TowerBalanceRuntime.ability(MagicSchoolTowers.CONFIG_ID, upgrade.key + "PerLevel", fallback);
    }

    public static long sortingHatCost() {
        return Upgrade.SORTING_HAT.cost(0);
    }

    public static boolean hasSortingHat(UUID owner) {
        return purchased(owner, Upgrade.SORTING_HAT);
    }

    public static boolean canManageSchool(SemionGame game, UUID owner, HogwartsTower school) {
        return game != null && school != null && game.isActiveParticipant(owner) && owner.equals(school.ownerPlayer())
                && game.playerLane(owner).map(lane -> lane.towers().contains(school)).orElse(false);
    }

    public static PurchaseResult purchaseSortingHat(SemionGame game, UUID owner, HogwartsTower school) {
        return purchase(game, owner, school, Upgrade.SORTING_HAT);
    }

    public static boolean canUpgradeThisRound(UUID owner, int round) {
        State state = STATES.get(owner);
        return state == null || state.lastUpgradeRound != round;
    }

    public static PurchaseResult purchase(SemionGame game, UUID owner, HogwartsTower school, Upgrade upgrade) {
        if (!canManageSchool(game, owner, school)) return PurchaseResult.INVALID_SCHOOL;
        if (level(owner, upgrade) >= upgrade.maxLevel()) return PurchaseResult.ALREADY_PURCHASED;
        if (!canUpgradeThisRound(owner, game.currentRound())) return PurchaseResult.ROUND_LIMIT_REACHED;
        PurchaseResult result = purchase(owner, upgrade, game.players().get(owner).economy());
        if (result == PurchaseResult.PURCHASED) {
            STATES.get(owner).lastUpgradeRound = game.currentRound();
            if (upgrade == Upgrade.EXPLOSIVE_BARRELS) MagicSchoolTransfiguration.upgradeBarrels(owner);
            game.playerLane(owner).ifPresent(lane -> lane.towers().forEach(tower -> {
                if (tower instanceof MagicSchoolWizardTower wizard && owner.equals(wizard.ownerPlayer()) && wizard.health() > 0) {
                    wizard.refreshCurriculumStats(lane);
                }
            }));
        }
        return result;
    }

    static PurchaseResult purchase(UUID owner, Upgrade upgrade, PlayerEconomy economy) {
        int level = level(owner, upgrade);
        if (level >= upgrade.maxLevel()) return PurchaseResult.ALREADY_PURCHASED;
        if (upgrade == Upgrade.EXPLOSIVE_BARRELS && !purchased(owner, Upgrade.TRANSFIGURATION)) {
            return PurchaseResult.PREREQUISITE_REQUIRED;
        }
        if (!economy.spendDiamond(upgrade.cost(level))) return PurchaseResult.NOT_ENOUGH_DIAMONDS;
        State state = STATES.computeIfAbsent(owner, ignored -> new State());
        state.levels.put(upgrade, level + 1);
        if (upgrade == Upgrade.DEATH_EATER) state.deathEaterEnabled = true;
        if (upgrade == Upgrade.DUELING_PRACTICE) state.duelingPracticeEnabled = true;
        return PurchaseResult.PURCHASED;
    }

    public static boolean deathEaterEnabled(UUID owner) {
        State state = STATES.get(owner);
        return state != null && state.deathEaterEnabled;
    }

    public static PurchaseResult toggleDeathEater(SemionGame game, UUID owner, HogwartsTower school) {
        return toggle(game, owner, school, Upgrade.DEATH_EATER);
    }

    public static boolean enabled(UUID owner, Upgrade upgrade) {
        State state = STATES.get(owner);
        if (state == null) return false;
        return upgrade == Upgrade.DEATH_EATER ? state.deathEaterEnabled
                : upgrade == Upgrade.DUELING_PRACTICE && state.duelingPracticeEnabled;
    }

    public static PurchaseResult toggle(SemionGame game, UUID owner, HogwartsTower school, Upgrade upgrade) {
        if (!upgrade.toggleable()) throw new IllegalArgumentException("Not a toggleable curriculum: " + upgrade);
        if (!canManageSchool(game, owner, school)) return PurchaseResult.INVALID_SCHOOL;
        if (!purchased(owner, upgrade)) return purchase(game, owner, school, upgrade);
        State state = STATES.get(owner);
        if (upgrade == Upgrade.DEATH_EATER) state.deathEaterEnabled = !state.deathEaterEnabled;
        else state.duelingPracticeEnabled = !state.duelingPracticeEnabled;
        return PurchaseResult.TOGGLED;
    }

    static boolean beginDeathEaterWave(UUID owner, int round) {
        State state = STATES.get(owner);
        if (state == null || round <= 0 || state.lastDeathEaterRound >= round) return false;
        state.lastDeathEaterRound = round;
        return state.deathEaterEnabled;
    }

    public static boolean isSpellTierUnlocked(UUID owner, int tier) {
        if (tier < 1 || tier > 6) return false;
        State state = STATES.get(owner);
        return tier == 1 || state != null && state.spellTiers.contains(tier);
    }

    public static long spellTierCost(int tier) {
        return integer("spellTier" + tier + "Cost", defaultSpellTierCost(tier));
    }

    public static String spellTierName(int tier) {
        return tier == 6 ? "용서받지 못할 저주" : tier + "단계 주문";
    }

    private static int defaultSpellTierCost(int tier) {
        return switch (tier) {
            case 2 -> 175;
            case 3 -> 320;
            case 4 -> 500;
            case 5 -> 700;
            case 6 -> 10000;
            default -> throw new IllegalArgumentException("Purchasable spell tiers are 2 through 6: " + tier);
        };
    }

    public static PurchaseResult unlockSpellTier(SemionGame game, UUID owner, HogwartsTower school, int tier) {
        if (!canManageSchool(game, owner, school)) return PurchaseResult.INVALID_SCHOOL;
        if (tier == 6) return PurchaseResult.AUGMENT_REQUIRED;
        if (isSpellTierUnlocked(owner, tier)) return PurchaseResult.ALREADY_PURCHASED;
        if (!canUpgradeThisRound(owner, game.currentRound())) return PurchaseResult.ROUND_LIMIT_REACHED;
        PurchaseResult result = unlockSpellTier(owner, tier, game.players().get(owner).economy());
        if (result == PurchaseResult.PURCHASED) STATES.get(owner).lastUpgradeRound = game.currentRound();
        return result;
    }

    static PurchaseResult unlockSpellTier(UUID owner, int tier, PlayerEconomy economy) {
        if (tier == 6) return PurchaseResult.AUGMENT_REQUIRED;
        long cost = spellTierCost(tier);
        if (isSpellTierUnlocked(owner, tier)) return PurchaseResult.ALREADY_PURCHASED;
        if (!isSpellTierUnlocked(owner, tier - 1)) return PurchaseResult.PREVIOUS_SPELL_TIER_REQUIRED;
        if (!economy.spendDiamond(cost)) return PurchaseResult.NOT_ENOUGH_DIAMONDS;
        STATES.computeIfAbsent(owner, ignored -> new State()).spellTiers.add(tier);
        return PurchaseResult.PURCHASED;
    }

    static int integer(String key, int fallback) {
        return TowerBalanceRuntime.abilityInt(MagicSchoolTowers.CONFIG_ID, key, fallback);
    }

    static double value(String key, double fallback) {
        return TowerBalanceRuntime.ability(MagicSchoolTowers.CONFIG_ID, key, fallback);
    }

    public static long nextQuidditchReward(UUID owner) {
        State state = STATES.get(owner);
        return integer("quidditchBaseReward", 5) + (long) integer("quidditchRewardIncrease", 5)
                * Math.min(integer("quidditchMaxIncreases", 5), state == null ? 0 : state.quidditchIncreases);
    }

    static long claimQuidditchReward(UUID owner, int round) {
        State state = STATES.get(owner);
        if (!purchased(owner, Upgrade.QUIDDITCH) || round <= 0 || state.lastQuidditchRound >= round) return 0;
        long reward = nextQuidditchReward(owner);
        state.lastQuidditchRound = round;
        state.quidditchIncreases = Math.min(integer("quidditchMaxIncreases", 5), state.quidditchIncreases + 1);
        return reward;
    }

    public static void onLaneCleared(PlayerLane lane, int round, Map<UUID, SemionPlayer> players) {
        if (lane.leakedThisRound() || lane.laneDefenseBroken()) return;
        SemionPlayer player = players.get(lane.ownerPlayer());
        if (player == null) return;
        player.economy().addDiamond(claimQuidditchReward(lane.ownerPlayer(), round));
    }

    static boolean beginBarrel(UUID owner, int round, long now) {
        if (!purchased(owner, Upgrade.TRANSFIGURATION) || round <= 0) return false;
        State state = STATES.get(owner);
        if (state.lastBarrelRound == round && now < state.nextBarrelTick) return false;
        state.lastBarrelRound = round;
        state.nextBarrelTick = now + integer("transfigurationCooldownTicks", 60);
        return true;
    }

    static int barrelHealth(int round) {
        int health = integer("barrelHealth", 1);
        for (int i = 1; i <= 3; i++) {
            if (round >= integer("barrelHealthRound" + i, 5 + (i - 1) * 10)) health++;
        }
        return health;
    }

    public static void clear(UUID owner) {
        MagicSchoolTransfiguration.clear(owner);
        STATES.remove(owner);
    }
}
