package kim.biryeong.semiontd.tower.hero;

import static kim.biryeong.semiontd.tower.hero.HeroCompanionAbilityDefaults.*;

import java.util.List;

final class HeroCompanionStatsView {
    private HeroCompanionStatsView() {
    }

    static List<String> abilities(HeroCompanionRole role, int tier, String configId) {
        int index = Math.max(0, Math.min(3, tier - 1));
        if (tier < 2) {
            return List.of();
        }
        String first = switch (role) {
            case KNIGHT -> HeroPartyTowers.firstAbilityName(role) + ": "
                    + configuredInt(configId, "shieldBashEvery", KNIGHT_BASH_EVERY[index])
                    + "번째 공격, 이동/공격 속도 -"
                    + HeroCompanionTower.percent(configured(configId, "shieldBashSlow", KNIGHT_BASH_SLOW[index]))
                    + " (" + seconds(configuredInt(configId, "shieldBashDurationTicks", KNIGHT_BASH_TICKS[index])) + ")";
            case ARCHER -> HeroPartyTowers.firstAbilityName(role) + ": "
                    + configuredInt(configId, "pierceEvery", ARCHER_PIERCE_EVERY[index])
                    + "번째 공격이 다른 적에게 "
                    + HeroCompanionTower.percent(configured(configId, "pierceDamageRatio", ARCHER_PIERCE_RATIO[index])) + " 피해";
            case MAGE -> HeroPartyTowers.firstAbilityName(role) + ": 폭발 대상 이동 속도 -"
                    + HeroCompanionTower.percent(configured(configId, "splashSlow", MAGE_SLOW[index]))
                    + " (" + seconds(configuredInt(configId, "splashSlowDurationTicks", MAGE_SLOW_TICKS[index])) + ")";
            case PRIEST -> HeroPartyTowers.firstAbilityName(role) + ": 치유 대상이 받는 피해 -"
                    + HeroCompanionTower.percent(configured(configId, "healGuardReduction", PRIEST_GUARD[index]))
                    + " (" + seconds(configuredInt(configId, "healGuardDurationTicks", PRIEST_GUARD_TICKS[index])) + ")";
            case ROGUE -> HeroPartyTowers.firstAbilityName(role) + ": "
                    + configuredInt(configId, "comboEvery", ROGUE_COMBO_EVERY[index])
                    + "번째 공격에 "
                    + HeroCompanionTower.percent(configured(configId, "comboDamageRatio", ROGUE_COMBO_RATIO[index])) + " 추가타";
            case BARD -> HeroPartyTowers.firstAbilityName(role) + ": 주변 파티원 공격력 +"
                    + HeroCompanionTower.percent(configured(configId, "damageBonus", BARD_DAMAGE[index]))
                    + ", 공격 속도 +" + HeroCompanionTower.percent(configured(configId, "attackSpeedBonus", BARD_SPEED[index]));
        };
        if (tier < 3) {
            return List.of(first);
        }
        String second = switch (role) {
            case KNIGHT -> HeroPartyTowers.secondAbilityName(role) + ": 반경 "
                    + number(configured(configId, "guardRadius", KNIGHT_GUARD_RADIUS[index]))
                    + ", 파티원이 받는 피해 -"
                    + HeroCompanionTower.percent(configured(configId, "guardDamageReduction", KNIGHT_GUARD_REDUCTION[index]));
            case ARCHER -> HeroPartyTowers.secondAbilityName(role) + ": 대상이 받는 타워 피해 +"
                    + HeroCompanionTower.percent(configured(configId, "markDamageBonus", ARCHER_MARK_BONUS[index]))
                    + " (" + seconds(configuredInt(configId, "markDurationTicks", ARCHER_MARK_TICKS[index])) + ")";
            case MAGE -> HeroPartyTowers.secondAbilityName(role) + ": "
                    + configuredInt(configId, "empoweredEvery", MAGE_EMPOWERED_EVERY[index])
                    + "번째 공격, 폭발 "
                    + number(configured(configId, "empoweredSplashMultiplier", MAGE_EMPOWERED_MULTIPLIER[index]))
                    + "배 / 반경 +"
                    + number(configured(configId, "empoweredRadiusBonus", MAGE_EMPOWERED_RADIUS[index]));
            case PRIEST -> HeroPartyTowers.secondAbilityName(role) + ": 두 번째 파티원을 "
                    + HeroCompanionTower.percent(configured(configId, "secondTargetRatio", PRIEST_SECOND[index])) + "만큼 치유";
            case ROGUE -> HeroPartyTowers.secondAbilityName(role) + ": 처치 시 공격 속도 +"
                    + HeroCompanionTower.percent(configured(configId, "killAttackSpeedBonus", ROGUE_HASTE_BONUS[index]))
                    + " (" + seconds(configuredInt(
                            configId, "killAttackSpeedDurationTicks", ROGUE_HASTE_TICKS[index]
                    )) + ")";
            case BARD -> HeroPartyTowers.secondAbilityName(role) + ": "
                    + configuredInt(configId, "encoreEveryPulses", BARD_ENCORE_EVERY[index])
                    + "번째 노래마다 공격력/공격 속도 +"
                    + HeroCompanionTower.percent(configured(configId, "encoreDamageBonus", BARD_ENCORE_BONUS[index]))
                    + " (" + seconds(configuredInt(configId, "encoreDurationTicks", BARD_ENCORE_TICKS[index])) + ")";
        };
        return List.of(first, second);
    }

    private static double configured(String configId, String key, double fallback) {
        return HeroPartyBalance.tower(configId, key, fallback);
    }

    private static int configuredInt(String configId, String key, int fallback) {
        return HeroPartyBalance.towerInt(configId, key, fallback);
    }

    private static String seconds(int ticks) {
        return number(ticks / 20.0) + "초";
    }

    private static String number(double value) {
        long rounded = Math.round(value);
        return Math.abs(value - rounded) < 0.000_001 ? Long.toString(rounded) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
