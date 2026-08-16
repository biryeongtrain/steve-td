package kim.biryeong.semiontd.tower.queen;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;

public final class QueenBalance {
    public static final String GLOBAL_ID = "queen_global";

    private QueenBalance() {}

    public static double shrinkFactorPerPoint() {return ability("shrinkFactorPerPoint", 0.98);}
    public static double minimumStatScale() {return ability("minimumStatScale", 0.20);}
    public static double minimumVisualScale() {return ability("minimumVisualScale", 0.50);}
    public static double queenShrinkPoints() {return ability("queenShrinkPoints", 5.0);}
    public static double cardShrinkPoints() {return ability("cardShrinkPoints", 0.75);}
    public static double cardDeathShrinkPoints() {return ability("cardDeathShrinkPoints", 1.5);}
    public static double cardDeathRadius() {return ability("cardDeathRadius", 3.0);}
    public static int heartHealIntervalTicks() {return abilityInt("heartHealIntervalTicks", 60);}
    public static double heartHealAmount() {return ability("heartHealAmount", 12.0);}
    public static double heartHealRadius() {return ability("heartHealRadius", 5.0);}
    public static double clubDamageReduction() {return ability("clubDamageReduction", 0.15);}
    public static double cardSplashRadius() {return ability("cardSplashRadius", 1.25);}
    public static int cardSplashExtraTargets() {return abilityInt("cardSplashExtraTargets", 1);}
    public static double spadeRadius() {return ability("spadeRadius", 1.5);}
    public static int spadeExtraTargets() {return abilityInt("spadeExtraTargets", 3);}
    public static int giantChargeTicks() {return abilityInt("giantChargeTicks", 400);}
    public static double giantAccelerationRadius() {return ability("giantAccelerationRadius", 6.0);}
    public static int giantAccelerationMemoryTicks() {return abilityInt("giantAccelerationMemoryTicks", 40);}
    public static double giantInitialExecutionHealth() {return ability("giantInitialExecutionHealth", 5.0);}
    public static double giantExecutionGrowthRatio() {return ability("giantExecutionGrowthRatio", 0.05);}
    public static double giantGrowthTargetCapMultiplier() {return ability("giantGrowthTargetCapMultiplier", 4.0);}
    public static double queenMaxHealthPerRound() {return ability("queenMaxHealthPerRound", 8.0);}
    public static double giantContactRadius() {return ability("giantContactRadius", 4.0);}
    public static double giantSpeed() {return ability("giantSpeed", 0.65);}
    public static double giantSlow() {return ability("giantSlow", 0.55);}
    public static int giantSlowTicks() {return abilityInt("giantSlowTicks", 40);}
    public static int rangeVfxIntervalTicks() {return abilityInt("rangeVfxIntervalTicks", 80);}
    public static double handBonus(PokerHand hand) {
        return ability("hand." + hand.name().toLowerCase(), hand.defaultBonus());
    }
    public static double cardMaxHealth(QueenCard.Suit suit) {return ability("card." + key(suit) + ".maxHealth", switch (suit) {case HEART -> 60; case DIAMOND -> 45; case CLUB -> 125; case SPADE -> 75;});}
    public static double cardRange(QueenCard.Suit suit) {return ability("card." + key(suit) + ".range", switch (suit) {case HEART -> 6; case DIAMOND -> 8; case CLUB -> 2.5; case SPADE -> 3;});}
    public static int cardInterval(QueenCard.Suit suit) {return abilityInt("card." + key(suit) + ".intervalTicks", switch (suit) {case HEART -> 20; case DIAMOND -> 10; case CLUB -> 24; case SPADE -> 18;});}
    public static int cardAggro(QueenCard.Suit suit) {
        return abilityInt("card." + key(suit) + ".aggro", switch (suit) {
            case HEART -> 55;
            case DIAMOND -> 45;
            case CLUB -> 110;
            case SPADE -> 80;
        });
    }

    private static String key(QueenCard.Suit suit) {return suit.name().toLowerCase();}

    private static double ability(String key, double fallback) {
        return TowerBalanceRuntime.ability(GLOBAL_ID, key, fallback);
    }

    private static int abilityInt(String key, int fallback) {
        return TowerBalanceRuntime.abilityInt(GLOBAL_ID, key, fallback);
    }
}
