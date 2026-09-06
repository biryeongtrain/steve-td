package kim.biryeong.semiontd.tower.pet;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.TowerType;

public final class PetBalance {
    public static final String CONFIG_ID = "pet_global";

    public static final int YARD_RADIUS = 1;
    public static final int YARD_TILES = 8;

    public static final double PUP_SCALE = 0.7;
    public static final double ADULT_SCALE = 1.0;

    public static final double BOND_PER_ROUND = 1.0;
    public static final int PRAISE_KILLS_PER_BOND = 10;
    public static final double PRAISE_CAP_PER_ROUND = 5.0;
    public static final double BOND_ATTACK_PER_POINT = 0.008;
    public static final double BOND_HEALTH_PER_POINT = 0.004;
    public static final double LOST_PET_MULTIPLIER = 0.4;

    public static final String KEY_BOND_GRANT_BASE = "bondGrantBase";
    public static final String KEY_BOND_GRANT_EXPONENT = "bondGrantExponent";
    public static final String KEY_BOND_CAP_MULTIPLIER = "bondCapMultiplier";
    public static final String KEY_WALK_BOND_BASE = "walkBondBase";
    public static final String KEY_WALK_BOND_PER_EMPTY_TILE = "walkBondPerEmptyTile";
    public static final String KEY_WALK_BOND_FLAT = "walkBondFlat";
    public static final String KEY_BOND_CAP = "bondCap";
    public static final String KEY_BOND_TO_UPGRADE = "bondToUpgrade";
    public static final String KEY_PACK_DAMAGE_PER_MATE = "packDamagePerPackMate";
    public static final String KEY_PACK_HEALTH_PER_MATE = "packHealthPerPackMate";
    public static final String KEY_ADULT_DAMAGE_REDUCTION = "adultDamageReduction";
    public static final String KEY_SOLO_DAMAGE_BONUS = "soloDamageBonus";
    public static final String KEY_ADULT_SPLASH_RADIUS = "adultSplashRadius";
    public static final String KEY_ADULT_SPLASH_MAX_TARGETS = "adultSplashMaxTargets";
    public static final String KEY_ADULT_SPLASH_DAMAGE_RATIO = "adultSplashDamageRatio";
    public static final String KEY_HEAL_RATIO = "healRatio";

    private PetBalance() {
    }

    public static double global(String key, double fallback) {
        return TowerBalanceRuntime.ability(CONFIG_ID, key, fallback);
    }

    public static int globalInt(String key, int fallback) {
        return Math.max(0, TowerBalanceRuntime.abilityInt(CONFIG_ID, key, fallback));
    }

    public static double bondPerRound() {
        return Math.max(0.0, global("bondPerRound", BOND_PER_ROUND));
    }

    public static int praiseKillsPerBond() {
        return Math.max(1, globalInt("praiseKillsPerBond", PRAISE_KILLS_PER_BOND));
    }

    public static double praiseCapPerRound() {
        return Math.max(0.0, global("praiseCapPerRound", PRAISE_CAP_PER_ROUND));
    }

    public static double bondAttackPerPoint() {
        return Math.max(0.0, global("bondAttackPerPoint", BOND_ATTACK_PER_POINT));
    }

    public static double bondHealthPerPoint() {
        return Math.max(0.0, global("bondHealthPerPoint", BOND_HEALTH_PER_POINT));
    }

    /** Stored as a positive multiplier; a lost companion keeps this fraction of its output. */
    public static double lostPetMultiplier() {
        return clamp(global("lostPetMultiplier", LOST_PET_MULTIPLIER), 0.0, 1.0);
    }

    public static double ability(TowerType type, String key, double fallback) {
        return type == null ? fallback : TowerBalanceRuntime.ability(type.id(), key, fallback);
    }

    public static int abilityInt(TowerType type, String key, int fallback) {
        return type == null ? fallback : TowerBalanceRuntime.abilityInt(type.id(), key, fallback);
    }

    /** Bond granted to a single companion each round, given {@code companions} pets in the yard. */
    public static double bondGrant(TowerType ownerType, int companions) {
        if (ownerType == null || companions <= 0) {
            return 0.0;
        }
        double base = Math.max(0.0, ability(ownerType, KEY_BOND_GRANT_BASE, 0.0));
        double exponent = Math.max(0.0, ability(ownerType, KEY_BOND_GRANT_EXPONENT, 1.0));
        return base / Math.pow(companions, exponent);
    }

    /** Bond granted by the between-round walk; empty yard tiles make the walk worth more. */
    public static double walkBond(TowerType ownerType, int companions) {
        if (ownerType == null) {
            return 0.0;
        }
        double flat = Math.max(0.0, ability(ownerType, KEY_WALK_BOND_FLAT, 0.0));
        if (flat > 0.0) {
            return flat;
        }
        double base = Math.max(0.0, ability(ownerType, KEY_WALK_BOND_BASE, 0.0));
        double perTile = Math.max(0.0, ability(ownerType, KEY_WALK_BOND_PER_EMPTY_TILE, 0.0));
        int emptyTiles = Math.max(0, YARD_TILES - Math.max(0, companions));
        return base + perTile * emptyTiles;
    }

    public static double bondCap(TowerType companionType, TowerType ownerType) {
        double cap = Math.max(0.0, ability(companionType, KEY_BOND_CAP, 0.0));
        double multiplier = ownerType == null
                ? 1.0
                : Math.max(1.0, ability(ownerType, KEY_BOND_CAP_MULTIPLIER, 1.0));
        return cap * multiplier;
    }

    public static double bondToUpgrade(TowerType companionType) {
        return Math.max(0.0, ability(companionType, KEY_BOND_TO_UPGRADE, 0.0));
    }

    public static double attackMultiplier(double bond) {
        return 1.0 + Math.max(0.0, bond) * bondAttackPerPoint();
    }

    public static double healthMultiplier(double bond) {
        return 1.0 + Math.max(0.0, bond) * bondHealthPerPoint();
    }

    /**
     * @param packSize dogs in the connected pack, counting the dog itself. Only its pack mates pay
     *                 out, so a lone dog gets nothing. There is no ceiling: a longer chain of dogs
     *                 keeps paying, and the lane's tower slots are the only limit.
     */
    public static double packBonus(TowerType dogType, int packSize) {
        int mates = Math.max(0, packSize - 1);
        return mates * Math.max(0.0, ability(dogType, KEY_PACK_DAMAGE_PER_MATE, 0.0));
    }

    public static double packHealthBonus(TowerType dogType, int packSize) {
        int mates = Math.max(0, packSize - 1);
        return mates * Math.max(0.0, ability(dogType, KEY_PACK_HEALTH_PER_MATE, 0.0));
    }

    public static double adultDamageReduction(TowerType dogType) {
        double fallback = switch (PetTowers.tier(dogType)) {
            case 1 -> 0.10;
            case 2 -> 0.15;
            default -> 0.20;
        };
        return clamp(ability(dogType, KEY_ADULT_DAMAGE_REDUCTION, fallback), 0.0, 1.0);
    }

    public static double soloBonus(TowerType catType) {
        return Math.max(0.0, ability(catType, KEY_SOLO_DAMAGE_BONUS, 0.0));
    }

    public static double adultSplashRadius(TowerType catType) {
        double fallback = switch (PetTowers.tier(catType)) {
            case 1 -> 1.5;
            case 2 -> 2.0;
            default -> 2.5;
        };
        return Math.max(0.0, ability(catType, KEY_ADULT_SPLASH_RADIUS, fallback));
    }

    public static int adultSplashMaxTargets(TowerType catType) {
        int fallback = switch (PetTowers.tier(catType)) {
            case 1 -> 2;
            case 2 -> 4;
            default -> 6;
        };
        return Math.max(0, abilityInt(catType, KEY_ADULT_SPLASH_MAX_TARGETS, fallback));
    }

    public static double adultSplashDamageRatio(TowerType catType) {
        return clamp(ability(catType, KEY_ADULT_SPLASH_DAMAGE_RATIO, 0.30), 0.0, 1.0);
    }

    public static double healRatio(TowerType birdType) {
        return clamp(ability(birdType, KEY_HEAL_RATIO, 0.0), 0.0, 2.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
