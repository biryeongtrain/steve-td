package kim.biryeong.semiontd.tower.illager;

import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterDataKey;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.resources.ResourceLocation;

public final class IllagerMarks {
    private static final MonsterDataKey<Boolean> MARK_TRANSFERRED = MonsterDataKey.of(
            ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "illager_raid/mark_transferred"), Boolean.class);
    private static final MonsterDataKey<IllagerMark> MARK = MonsterDataKey.of(
            ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "illager_mark"),
            IllagerMark.class
    );

    private IllagerMarks() {
    }

    private static MonsterDataKey<IllagerMark> omenKey(UUID owner) {
        return MonsterDataKey.of(ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID,
                "illager_omen/" + owner), IllagerMark.class);
    }

    public static void applyOmen(Monster monster, UUID owner, double bonus, int ticks) {
        if (monster == null || owner == null || ticks <= 0) {return;}
        monster.setData(omenKey(owner), new IllagerMark(owner, bonus, monster.activeTicks() + ticks, null, 0, ticks));
    }

    public static double omenBonus(Monster monster, UUID owner) {
        if (monster == null || owner == null) {return 0;}
        var key = omenKey(owner);
        var mark = monster.getData(key).orElse(null);
        if (mark == null) {return 0;}
        if (monster.activeTicks() >= mark.expiresAtMonsterTick()) {
            monster.removeData(key);
            return 0;
        }
        return mark.damageTakenBonus();
    }

    public static void apply(
            Monster monster,
            UUID ownerPlayer,
            double damageTakenBonus,
            int durationTicks,
            GridPosition forceTargetCenter,
            double forceTargetRadius
    ) {
        if (monster == null || ownerPlayer == null || durationTicks <= 0) {
            return;
        }
        monster.setData(MARK, new IllagerMark(
                ownerPlayer,
                Math.max(0.0, damageTakenBonus),
                monster.activeTicks() + durationTicks,
                forceTargetCenter,
                Math.max(0.0, forceTargetRadius),
                durationTicks
        ));
    }

    public static Optional<IllagerMark> activeMark(Monster monster, UUID ownerPlayer) {
        if (monster == null || ownerPlayer == null) {
            return Optional.empty();
        }
        Optional<IllagerMark> mark = monster.getData(MARK);
        if (mark.isEmpty()) {
            return Optional.empty();
        }
        if (!mark.get().activeFor(monster, ownerPlayer)) {
            monster.removeData(MARK);
            return Optional.empty();
        }
        return mark;
    }

    static boolean hasTransferableMark(Monster monster, UUID owner) {
        return activeMark(monster, owner).isPresent() || omenBonus(monster, owner) > 0;
    }

    /** Called once at the attributed tower-damage boundary, including common augment tower kills. */
    public static void onAttributedKill(Tower tower, SemionTowerEntity source, SemionMonsterEntity target) {
        Monster corpse = target == null ? null : target.runtimeMonster();
        String card = IllagerTower.NEXT_TARGET;
        if (!AugmentCombat.allowsTriggers() || tower == null || tower.isTemporaryCopy() || source == null || corpse == null
                || !tower.augmentSnapshot().has(card) || corpse.getData(MARK_TRANSFERRED).orElse(false)
                || !hasTransferableMark(corpse, tower.ownerPlayer())) {return;}
        corpse.setData(MARK_TRANSFERRED, true);
        var request = MonsterAreaEffectRequest.aroundTarget(AreaEffectIds.tower(tower, "next_target"), source, target,
                tower.augmentSnapshot().parameter(card, "radius", 3), AreaVfxSpec.onTrigger(AreaVfxStyles.DEBUFF))
                .nearestTargets((int) tower.augmentSnapshot().parameter(card, "targetCount", 3));
        double damage = source.attackDamageAmount(null) * tower.augmentSnapshot().parameter(card, "damageRatio", 1.5);
        AugmentCombat.runWithoutTriggers(() -> TowerAreaDamage.apply(tower, source, request, recipient -> {
            int ticks = transfer(corpse, recipient.runtimeMonster(), tower.ownerPlayer());
            if (ticks > 0) {recipient.applyTimedEffect(TimedEffectType.MONSTER_MARKED, 1, ticks);}
            return damage;
        }, true, (recipient, dealt, killed) -> {}, DamageType.MAGIC));
    }

    static int transfer(Monster corpse, Monster target, UUID owner) {
        if (corpse == null || target == null || owner == null) {return 0;}
        int duration = 0;
        IllagerMark mark = activeMark(corpse, owner).orElse(null);
        if (mark != null) {
            duration = mark.durationTicks();
            apply(target, owner, mark.damageTakenBonus(), duration, mark.forceTargetCenter(), mark.forceTargetRadius());
        }
        if (omenBonus(corpse, owner) > 0) {
            IllagerMark omen = corpse.getData(omenKey(owner)).orElseThrow();
            applyOmen(target, owner, omen.damageTakenBonus(), omen.durationTicks());
            duration = Math.max(duration, omen.durationTicks());
        }
        return duration;
    }
}
