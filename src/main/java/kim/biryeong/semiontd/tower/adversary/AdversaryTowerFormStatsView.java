package kim.biryeong.semiontd.tower.adversary;

import java.util.List;
import java.util.Locale;
import static kim.biryeong.semiontd.tower.adversary.AdversaryBalance.globalValue;
import static kim.biryeong.semiontd.tower.adversary.AdversaryBalance.globalInt;

final class AdversaryTowerFormStatsView {
    private AdversaryTowerFormStatsView() {}

    record State(int goldenTargetHits, int spyglassTargetHits, int echoTargetHits, int maceTicksUntilStrike, int maceSuccessfulStrikes, int pendingBlasts) {}

    static void append(List<String> lines, FoxForm form, State state) {
        switch (form) {
            case BASE -> lines.add("기본 공격이 반경 "
                    + number(globalValue("baseSplashRadius", AdversaryBalance.BASE_SPLASH_RADIUS))
                    + "블록 안의 다른 적 최대 "
                    + globalInt("baseSplashExtraTargets", AdversaryBalance.BASE_SPLASH_EXTRA_TARGETS)
                    + "기에게 공격력의 "
                    + percent(globalValue("baseSplashDamageRatio", AdversaryBalance.BASE_SPLASH_DAMAGE_RATIO))
                    + "만큼 피해를 줍니다.");
            case BREEZE -> lines.add("기본 공격이 다른 적 "
                    + globalInt("breezeExtraTargets", AdversaryBalance.BREEZE_EXTRA_TARGETS)
                    + "기에게 공격력의 "
                    + percent(globalValue(
                    "breezeExtraTargetDamageRatio",
                    AdversaryBalance.BREEZE_EXTRA_TARGET_DAMAGE_RATIO
            )) + "만큼 연쇄 마법 피해를 줍니다.");
            case GOLDEN_FANG -> {
                int every = globalInt(
                        "goldenExtraAttackEvery",
                        AdversaryBalance.GOLDEN_FANG_EXTRA_ATTACK_EVERY
                );
                lines.add("같은 적을 " + every + "번 공격할 때마다 공격력의 "
                        + percent(globalValue(
                        "goldenExtraDamageRatio",
                        AdversaryBalance.GOLDEN_FANG_EXTRA_DAMAGE_RATIO
                )) + "만큼 추가 피해를 줍니다.");
                lines.add("연속 공격: " + state.goldenTargetHits() + "/" + every);
            }
            case SHIELD_BEARER -> {
                lines.add("받는 피해 " + percent(form.damageReduction()) + " 감소");
                lines.add("반격 피해 "
                        + number(globalValue("shieldCounterDamage", AdversaryBalance.SHIELD_COUNTER_DAMAGE))
                        + " / 재사용 대기시간 "
                        + globalInt(
                        "shieldCounterCooldownTicks",
                        AdversaryBalance.SHIELD_COUNTER_COOLDOWN_TICKS
                ) + "틱");
            }
            case BELL_KEEPER -> lines.add(number(globalInt(
                    "bellHealIntervalTicks",
                    AdversaryBalance.BELL_HEAL_INTERVAL_TICKS
            ) / 20.0) + "초마다 반경 "
                    + number(globalValue("bellHealRadius", AdversaryBalance.BELL_HEAL_RADIUS))
                    + "블록 내 체력 비율이 가장 낮은 다른 여우 "
                    + globalInt("bellHealTargetCount", AdversaryBalance.BELL_HEAL_TARGET_COUNT)
                    + "기의 최대 체력을 "
                    + percent(globalValue("bellHealMaxHealthRatio", AdversaryBalance.BELL_HEAL_MAX_HEALTH_RATIO))
                    + " 회복합니다.");
            case BEACON_KEEPER -> {
                lines.add("받는 피해 " + percent(form.damageReduction()) + " 감소");
                lines.add(number(globalInt(
                        "beaconHealIntervalTicks",
                        AdversaryBalance.BEACON_HEAL_INTERVAL_TICKS
                ) / 20.0) + "초마다 반경 "
                        + number(globalValue("beaconHealRadius", AdversaryBalance.BEACON_HEAL_RADIUS))
                        + "블록 내 체력 비율이 가장 낮은 다른 여우 최대 "
                        + globalInt("beaconHealTargetCount", AdversaryBalance.BEACON_HEAL_TARGET_COUNT)
                        + "기의 최대 체력을 각각 "
                        + percent(globalValue(
                        "beaconHealMaxHealthRatio",
                        AdversaryBalance.BEACON_HEAL_MAX_HEALTH_RATIO
                )) + " 회복합니다.");
            }
            case OMINOUS_HEXER -> {
                lines.add("받는 피해 " + percent(form.damageReduction()) + " 감소");
                lines.add(number(globalInt(
                        "bellHealIntervalTicks",
                        AdversaryBalance.BELL_HEAL_INTERVAL_TICKS
                ) / 20.0) + "초마다 반경 "
                        + number(globalValue("bellHealRadius", AdversaryBalance.BELL_HEAL_RADIUS))
                        + "블록 내 체력 비율이 가장 낮은 다른 여우 "
                        + globalInt("bellHealTargetCount", AdversaryBalance.BELL_HEAL_TARGET_COUNT)
                        + "기의 최대 체력을 "
                        + percent(globalValue(
                        "bellHealMaxHealthRatio",
                        AdversaryBalance.BELL_HEAL_MAX_HEALTH_RATIO
                )) + " 회복합니다.");
                lines.add("아군을 노리는 적: 공격력 -"
                        + percent(globalValue(
                        "ominousMonsterDamageReduction",
                        AdversaryBalance.OMINOUS_MONSTER_DAMAGE_REDUCTION
                )) + " / 공격 속도 -"
                        + percent(globalValue(
                        "ominousMonsterAttackSpeedReduction",
                        AdversaryBalance.OMINOUS_MONSTER_ATTACK_SPEED_REDUCTION
                )) + " / 타워에게 받는 피해 +"
                        + percent(globalValue(
                        "ominousMonsterTowerDamageTakenBonus",
                        AdversaryBalance.OMINOUS_MONSTER_TOWER_DAMAGE_TAKEN_BONUS
                )));
                lines.add("숙적에게는 적용되지 않습니다.");
            }
            case TRACKER -> lines.add("라인에서 가장 앞선 적을 우선 공격합니다.");
            case FIREWORK_PIERCER -> {
                lines.add("웨이브 적에게 "
                        + number(globalValue(
                    "fireworkWaveDamageMultiplier",
                    AdversaryBalance.FIREWORK_WAVE_DAMAGE_MULTIPLIER
                )) + "배, 인컴 적에게 "
                        + number(globalValue(
                    "fireworkIncomeDamageMultiplier",
                    AdversaryBalance.FIREWORK_INCOME_DAMAGE_MULTIPLIER
                )) + "배의 피해를 줍니다.");
                lines.add("직선상의 적 최대 "
                        + globalInt("fireworkMaxTargets", AdversaryBalance.FIREWORK_MAX_TARGETS)
                        + "기를 관통하며 " + percentList(AdversaryBalance.fireworkTargetDamageRatios())
                        + "의 물리 피해를 줍니다.");
            }
            case BIG_GAME_TRACKER -> {
                int stages = AdversaryBalance.bigGameStreakMultipliers().length;
                lines.add("인컴 적에게 "
                        + number(globalValue(
                        "bigGameIncomeDamageMultiplier",
                        AdversaryBalance.BIG_GAME_INCOME_DAMAGE_MULTIPLIER
                )) + "배, 웨이브 적에게 "
                        + number(globalValue(
                        "bigGameWaveDamageMultiplier",
                        AdversaryBalance.BIG_GAME_WAVE_DAMAGE_MULTIPLIER
                )) + "배의 피해를 줍니다.");
                lines.add("같은 인컴 적을 계속 공격하면 피해가 "
                        + multiplierList(AdversaryBalance.bigGameStreakMultipliers())
                        + "로 증가합니다.");
                lines.add("조준 단계: " + Math.min(stages, state.spyglassTargetHits() + 1) + "/" + stages);
            }
            case ECHO_FOX -> {
                lines.add("같은 적을 공격할 때마다 피해가 "
                        + percent(globalValue(
                        "echoBonusPerHit",
                        AdversaryBalance.ECHO_STREAK_DAMAGE_BONUS_PER_HIT
                )) + " 증가합니다. 최대 "
                        + globalInt("echoMaxBonusStacks", AdversaryBalance.ECHO_MAX_STREAK_BONUS_STACKS)
                        + "중첩.");
                lines.add("피격되거나 대상을 바꾸면 중첩이 초기화됩니다.");
                lines.add("메아리 중첩: " + state.echoTargetHits() + "/"
                        + globalInt("echoMaxBonusStacks", AdversaryBalance.ECHO_MAX_STREAK_BONUS_STACKS));
            }
            case MACE_EXECUTIONER -> {
                lines.add(globalInt("maceFocusTicks", AdversaryBalance.MACE_FOCUS_TICKS)
                        + "틱 동안 집중한 뒤 " + number(form.damage()) + "의 물리 피해를 줍니다.");
                lines.add("연속 적중 시 피해가 "
                        + multiplierList(AdversaryBalance.maceStreakMultipliers())
                        + "로 증가합니다.");
                lines.add("집중 중 최대 체력의 "
                        + percent(globalValue(
                        "maceBreakHealthRatio",
                        AdversaryBalance.MACE_FOCUS_BREAK_MAX_HEALTH_RATIO
                )) + "만큼 피해를 받으면 공격이 취소됩니다.");
                lines.add("적중 시 주변 적 최대 "
                        + globalInt("maceSweepExtraTargets", AdversaryBalance.MACE_SWEEP_EXTRA_TARGETS)
                        + "기에게 공격력의 "
                        + percent(globalValue("maceSweepDamageRatio", AdversaryBalance.MACE_SWEEP_DAMAGE_RATIO))
                        + "만큼 피해를 줍니다.");
                lines.add("집중: " + Math.max(0, state.maceTicksUntilStrike())
                        + "틱 / 연속 적중: " + state.maceSuccessfulStrikes() + "/"
                        + (AdversaryBalance.maceStreakMultipliers().length - 1));
            }
            case SCULK_CORE -> {
                lines.add("조준한 위치에 "
                        + globalInt("sculkDelayTicks", AdversaryBalance.SCULK_DETONATION_DELAY_TICKS)
                        + "틱 뒤 폭발을 일으킵니다.");
                lines.add("반경 "
                        + number(globalValue("sculkRadius", AdversaryBalance.SCULK_DETONATION_RADIUS))
                        + "블록 안의 적 최대 "
                        + globalInt("sculkMaxTargets", AdversaryBalance.SCULK_MAX_TARGETS)
                        + "기에게 " + number(form.damage()) + "의 마법 피해를 줍니다.");
                lines.add("폭발할 때 방어를 무시하고 최대 체력의 "
                        + percent(globalValue(
                        "sculkSelfDamageRatio",
                        AdversaryBalance.SCULK_SELF_DAMAGE_MAX_HEALTH_RATIO
                )) + "를 잃지만 체력은 "
                        + percent(globalValue(
                        "sculkSelfDamageFloorRatio",
                        AdversaryBalance.SCULK_SELF_DAMAGE_HEALTH_FLOOR_RATIO
                )) + " 아래로 내려가지 않습니다.");
                lines.add("대기 중인 폭발: " + state.pendingBlasts() + "개");
            }
        }
    }

    static String number(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replaceFirst("\\.?0+$", "");
    }

    static String percent(double ratio) {
        return number(ratio * 100.0) + "%";
    }

    static String multiplierList(double[] values) {
        return java.util.Arrays.stream(values)
                .mapToObj(value -> number(value) + "배")
                .collect(java.util.stream.Collectors.joining(" / "));
    }

    static String percentList(double[] values) {
        return java.util.Arrays.stream(values)
                .mapToObj(AdversaryTowerFormStatsView::percent)
                .collect(java.util.stream.Collectors.joining(" / "));
    }
}
