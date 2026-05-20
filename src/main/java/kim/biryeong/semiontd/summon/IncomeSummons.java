package kim.biryeong.semiontd.summon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.effect.TimedEffectType;

public final class IncomeSummons {
    private IncomeSummons() {
    }

    public static void reloadBuiltIns(SummonConfig config) {
        SummonConfig safeConfig = config == null ? SummonConfig.defaultConfig() : config;
        ArrayList<SummonMonsterType> summons = new ArrayList<>();
        safeConfig.summons().values().stream()
                .filter(SummonConfig.SummonDefinition::enabled)
                .sorted(Comparator.comparingInt(definition -> definition.tier().ordinal()))
                .forEach(definition -> summons.add(create(definition)));
        SummonRegistry.reload(summons);
    }

    private static SummonMonsterType create(SummonConfig.SummonDefinition definition) {
        return switch (definition.id()) {
            case "wolf", "cave_spider", "stray", "goat", "bogged", "breeze", "vindicator" ->
                    new TowerDebuffIncomeSummon(definition, TimedEffectType.TOWER_ATTACK_SPEED_REDUCTION);
            case "horse", "shulker" ->
                    new TowerDebuffIncomeSummon(definition, TimedEffectType.TOWER_RANGE_REDUCTION);
            case "elder_guardian" ->
                    new ElderGuardianSummon(definition);
            case "turtle" ->
                    new AllyTimedEffectIncomeSummon(definition, TimedEffectType.MONSTER_DAMAGE_REDUCTION);
            case "allay" ->
                    new AreaHealIncomeSummon(definition);
            case "fox" ->
                    new AllyTimedEffectIncomeSummon(definition, TimedEffectType.MONSTER_ATTACK_DAMAGE_BONUS);
            case "ocelot" ->
                    new AllyTimedEffectIncomeSummon(definition, TimedEffectType.MONSTER_MOVE_SPEED_BONUS);
            case "witch" ->
                    new AllyTimedEffectIncomeSummon(definition, List.of(
                            TimedEffectType.MONSTER_MOVE_SPEED_BONUS,
                            TimedEffectType.MONSTER_ATTACK_DAMAGE_BONUS,
                            TimedEffectType.MONSTER_ATTACK_SPEED_BONUS
                    ));
            case "evoker" ->
                    new AllyTimedEffectIncomeSummon(definition, List.of(
                            TimedEffectType.MONSTER_ATTACK_DAMAGE_BONUS,
                            TimedEffectType.MONSTER_DAMAGE_REDUCTION
                    ));
            case "guardian", "blaze", "ghast", "wither_skeleton", "warden" ->
                    new SiegeIncomeSummon(definition);
            default ->
                    new BasicIncomeSummon(definition);
        };
    }
}
