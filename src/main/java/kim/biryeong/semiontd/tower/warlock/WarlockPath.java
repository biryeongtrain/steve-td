package kim.biryeong.semiontd.tower.warlock;

import kim.biryeong.semiontd.tower.TowerType;

public enum WarlockPath {
    BASE,
    RANGED,
    MELEE;

    static WarlockPath fromCore(TowerType type) {
        if (sameType(type, WarlockTowers.BASE_WARLOCK_TOWER)) {return BASE;}
        if (sameType(type, WarlockTowers.RANGED_WARLOCK_TOWER)) {return RANGED;}
        if (sameType(type, WarlockTowers.MELEE_WARLOCK_TOWER)) {return MELEE;}
        throw new IllegalArgumentException("Not a Warlock core tower: " + (type == null ? "null" : type.id()));
    }

    static WarlockPath fromTower(TowerType type) {
        if (WarlockTowers.isMeleeSlave(type)) {return MELEE;}
        if (WarlockTowers.isRangedSlave(type)) {return RANGED;}
        return fromCore(type);
    }

    boolean specialized() {
        return this != BASE;
    }

    boolean acceptsSacrificeTower(TowerType type) {
        return switch (this) {
            case BASE -> !WarlockTowers.isWarlockCore(type);
            case RANGED, MELEE -> WarlockTowers.isRangedSlave(type) || WarlockTowers.isMeleeSlave(type);
        };
    }

    boolean acceptsPassiveTower(TowerType type) {
        return switch (this) {
            case BASE -> false;
            case RANGED -> WarlockTowers.isRangedSlave(type);
            case MELEE -> WarlockTowers.isMeleeSlave(type);
        };
    }

    private static boolean sameType(TowerType first, TowerType second) {
        return first != null && second != null && first.id().equals(second.id());
    }
}
