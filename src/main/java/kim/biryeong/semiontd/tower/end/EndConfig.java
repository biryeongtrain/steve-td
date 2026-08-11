package kim.biryeong.semiontd.tower.end;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.TowerType;

public final class EndConfig {
    static final EndConfig RUNTIME = new EndConfig();

    private EndConfig() {
    }

    double value(Ability ability) {return TowerBalanceRuntime.ability(EndTowers.CONFIG_ID, ability.key());}
    int integer(Ability ability) {return TowerBalanceRuntime.abilityInt(EndTowers.CONFIG_ID, ability.key());}
    int transferTicks() {return TowerBalanceRuntime.abilityTicks(EndTowers.CONFIG_ID, Ability.TRANSFER_TICKS.key());}
    double towerDamageReduction(TowerType type) {return TowerBalanceRuntime.ability(type.id(), "damageReduction");}

    public enum Ability {
        DRAGON_EVOLUTION("dragonEvolution"),
        PHANTOM_SCALE_HEALTH("phantomScaleHealth"),
        PHANTOM_SCALE_STEP("phantomScaleStep"),
        PHANTOM_SCALE_BASE("phantomScaleBase"),
        PHANTOM_SCALE_CAP("phantomScaleCap"),
        TRANSFER_TICKS("transferTicks"),
        TRANSFER_HEAL("transferHeal"),
        TRANSFER_HEAL_RATIO("transferHealRatio"),
        ROUND_HEALTH_RATIO("roundHealthRatio"),
        PERMANENT_HEALTH_RATIO("permanentHealthRatio"),
        HEALTH_THRESHOLD("healthThreshold"),
        HEALTH_SCALE("healthScale"),
        ROUND_DAMAGE_RATIO("roundDamageRatio"),
        PERMANENT_DAMAGE_RATIO("permanentDamageRatio"),
        DAMAGE_THRESHOLD("damageThreshold"),
        DAMAGE_SCALE("damageScale"),
        LIFE_STEAL_STACKS("lifeStealStacks"),
        LIFE_STEAL_STEP("lifeStealStep"),
        LIFE_STEAL_CAP("lifeStealCap"),
        DAMAGE_REDUCTION_STACKS("damageReductionStacks"),
        DAMAGE_REDUCTION_STEP("damageReductionStep"),
        DAMAGE_REDUCTION_CAP("damageReductionCap"),
        REGENERATION_STACKS("regenerationStacks"),
        REGENERATION_STEP("regenerationStep"),
        REGENERATION_CAP("regenerationCap"),
        SPLASH_1("splash1"),
        SPLASH_2("splash2"),
        SPLASH_3("splash3"),
        SPLASH_4("splash4"),
        SPLASH_5("splash5"),
        SPLASH_STEP("splashStep"),
        SPLASH_CAP("splashCap"),
        SPLASH_DAMAGE_RATIO("splashDamageRatio"),
        ATTACK_SPEED_STACKS("attackSpeedStacks"),
        ATTACK_SPEED_STEP("attackSpeedStep"),
        ATTACK_SPEED_CAP("attackSpeedCap"),
        ATTACK_SPEED_MINIMUM_TICKS("attackSpeedMinimumTicks"),
        TRANSFER_ATTACK_SPEED_STACKS("transferAttackSpeedStacks"),
        TRANSFER_ATTACK_SPEED_STEP("transferAttackSpeedStep"),
        ATTACK_RANGE_STACKS("attackRangeStacks"),
        ATTACK_RANGE_STEP("attackRangeStep"),
        ATTACK_RANGE_CAP("attackRangeCap"),
        DRAGON_FINAL_DAMAGE("dragonFinalDamage"),
        DRAGON_RANGE_BONUS("dragonRangeBonus");

        private final String key;
        Ability(String key) {
            this.key = key;
        }
        public String key() {return key;}
    }
}
