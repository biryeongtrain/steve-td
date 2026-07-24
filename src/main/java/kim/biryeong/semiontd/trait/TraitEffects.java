package kim.biryeong.semiontd.trait;

import java.util.Objects;
import kim.biryeong.semiontd.config.TraitBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.end.EndTowers;
import kim.biryeong.semiontd.tower.warlock.WarlockTowers;
import net.minecraft.resources.ResourceLocation;

public final class TraitEffects {
    private static final double BASE_WAVE_STARTED_SELL_REFUND_RATE = 0.50;

    private TraitEffects() {
    }

    public static double effectScale(TraitLoadout loadout, ResourceLocation traitId) {
        if (loadout == null || traitId == null) {
            return 0.0;
        }
        double scale = 0.0;
        if (traitId.equals(loadout.primaryTraitId())) {
            scale += TraitSlot.PRIMARY.effectScale();
        }
        if (traitId.equals(loadout.secondaryTraitId())) {
            scale += TraitSlot.SECONDARY.effectScale();
        }
        return scale;
    }

    public static long startingMineralBonus(TraitLoadout loadout) {
        return Math.round(value(BuiltInTraits.MOBILIZATION_GRANT_ID, "startingDiamond")
                * effectScale(loadout, BuiltInTraits.MOBILIZATION_GRANT_ID));
    }

    public static long cleanLaneBonus(TraitLoadout loadout, long income) {
        return Math.round(Math.max(0L, income)
                * value(BuiltInTraits.CLEAN_LANE_BONUS_ID, "incomeRatio")
                * effectScale(loadout, BuiltInTraits.CLEAN_LANE_BONUS_ID));
    }

    public static double incomeAttackDamageMultiplier(TraitLoadout loadout) {
        return 1.0 + value(BuiltInTraits.BERSERK_SUMMONS_ID, "attackDamageBonus")
                * effectScale(loadout, BuiltInTraits.BERSERK_SUMMONS_ID);
    }

    public static double incomeAttackSpeedMultiplier(TraitLoadout loadout) {
        return 1.0 + value(BuiltInTraits.BERSERK_SUMMONS_ID, "attackSpeedBonus")
                * effectScale(loadout, BuiltInTraits.BERSERK_SUMMONS_ID);
    }

    public static double sellRefundRate(TraitLoadout loadout, boolean waveStartedAfterPlacement) {
        if (!waveStartedAfterPlacement) {
            return 1.0;
        }
        double scale = effectScale(loadout, BuiltInTraits.RAPID_DEPLOYMENT_ID);
        return scaledSellRefundRate(scale);
    }

    public static double openingAttackSpeedBonus(TraitLoadout loadout) {
        return value(BuiltInTraits.OPENING_SALVO_ID, "attackSpeedBonus")
                * effectScale(loadout, BuiltInTraits.OPENING_SALVO_ID);
    }

    public static int towerLimitBonus(TraitLoadout loadout) {
        return Math.max(0, (int) Math.round(
                value(BuiltInTraits.SUPPLY_DEPOT_ID, "towerLimitBonus")
                        * effectScale(loadout, BuiltInTraits.SUPPLY_DEPOT_ID)
        ));
    }

    public static double transcendenceDamageBonus(TraitLoadout loadout) {
        return Math.max(0.0, value(BuiltInTraits.TRANSCENDENCE_ID, "damageBonus")
                * effectScale(loadout, BuiltInTraits.TRANSCENDENCE_ID));
    }

    public static int transcendenceActivationDelayTicks() {
        return Math.max(0, (int) Math.round(
                value(BuiltInTraits.TRANSCENDENCE_ID, "activationDelaySeconds") * 20.0
        ));
    }

    public static int openingAttackSpeedDurationTicks() {
        return Math.max(0, (int) Math.round(openingAttackSpeedDurationSeconds() * 20.0));
    }

    public static double towerMaxHealthBonus(TraitLoadout loadout, Tower tower) {
        boolean isCoreTower = tower != null && (WarlockTowers.isWarlockCore(tower.type()) || EndTowers.isBaseEndTower(tower.type()));

        double fullPower = isCoreTower
                ? value(BuiltInTraits.FORTITUDE_ID, "CoreMaxHealthBonus")
                : value(BuiltInTraits.FORTITUDE_ID, "maxHealthBonus");

        return fullPower * effectScale(loadout, BuiltInTraits.FORTITUDE_ID);
    }

    public static double targetDamageBonus(TraitLoadout loadout, Monster target) {
        if (target == null) {
            return 0.0;
        }
        return target.senderTeam().isPresent()
                ? incomeTargetDamageBonus(loadout)
                : waveTargetDamageBonus(loadout);
    }

    public static double incomeTargetDamageBonus(TraitLoadout loadout) {
        return value(BuiltInTraits.INTERCEPTION_DOCTRINE_ID, "damageBonus")
                * effectScale(loadout, BuiltInTraits.INTERCEPTION_DOCTRINE_ID);
    }

    public static double waveTargetDamageBonus(TraitLoadout loadout) {
        return value(BuiltInTraits.WAVEBREAKER_DOCTRINE_ID, "damageBonus")
                * effectScale(loadout, BuiltInTraits.WAVEBREAKER_DOCTRINE_ID);
    }

    public static double doubleEdgedOutgoingDamageBonus(TraitLoadout loadout) {
        return value(BuiltInTraits.DOUBLE_EDGED_SWORD_ID, "outgoingDamageBonus")
                * effectScale(loadout, BuiltInTraits.DOUBLE_EDGED_SWORD_ID);
    }

    public static double doubleEdgedIncomingDamageBonus(TraitLoadout loadout) {
        return value(BuiltInTraits.DOUBLE_EDGED_SWORD_ID, "incomingDamageBonus")
                * effectScale(loadout, BuiltInTraits.DOUBLE_EDGED_SWORD_ID);
    }

    public static double sameTypeDamageBonus(TraitLoadout loadout, PlayerLane lane, Tower tower) {
        if (lane == null || tower == null) {
            return 0.0;
        }
        long sameTypeTowers = lane.towers().stream()
                .filter(candidate -> candidate.health() > 0.0)
                .filter(candidate -> Objects.equals(candidate.ownerPlayer(), tower.ownerPlayer()))
                .filter(candidate -> Objects.equals(candidate.type().id(), tower.type().id()))
                .count();
        return sameTypeTowers
                * value(BuiltInTraits.STRENGTH_IN_NUMBERS_ID, "damageBonusPerTower")
                * effectScale(loadout, BuiltInTraits.STRENGTH_IN_NUMBERS_ID);
    }

    public static double diversityDamageBonus(TraitLoadout loadout, PlayerLane lane, Tower tower) {
        if (lane == null || tower == null) {
            return 0.0;
        }
        long distinctTypes = lane.towers().stream()
                .filter(candidate -> candidate.health() > 0.0)
                .filter(candidate -> Objects.equals(candidate.ownerPlayer(), tower.ownerPlayer()))
                .map(candidate -> candidate.type().id())
                .distinct()
                .count();
        return distinctTypes
                * value(BuiltInTraits.DIVERSITY_ID, "damageBonusPerType")
                * effectScale(loadout, BuiltInTraits.DIVERSITY_ID);
    }

    static double sellRefundRate(TraitSlot slot) {
        return scaledSellRefundRate(slot.effectScale());
    }

    static double openingAttackSpeedDurationSeconds() {
        return value(BuiltInTraits.OPENING_SALVO_ID, "durationSeconds");
    }

    private static double scaledSellRefundRate(double scale) {
        double configuredRate = value(BuiltInTraits.RAPID_DEPLOYMENT_ID, "refundRateAfterWave");
        return Math.clamp(
                BASE_WAVE_STARTED_SELL_REFUND_RATE
                        + (configuredRate - BASE_WAVE_STARTED_SELL_REFUND_RATE) * scale,
                0.0,
                1.0
        );
    }

    private static double value(ResourceLocation traitId, String key) {
        return TraitBalanceRuntime.value(traitId, key);
    }
}
