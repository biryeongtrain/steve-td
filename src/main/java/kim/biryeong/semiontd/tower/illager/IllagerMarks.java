package kim.biryeong.semiontd.tower.illager;

import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterDataKey;
import kim.biryeong.semiontd.game.GridPosition;
import net.minecraft.resources.ResourceLocation;

public final class IllagerMarks {
    private static final MonsterDataKey<IllagerMark> MARK = MonsterDataKey.of(
            ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "illager_mark"),
            IllagerMark.class
    );

    private IllagerMarks() {
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
                Math.max(0.0, forceTargetRadius)
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
}
