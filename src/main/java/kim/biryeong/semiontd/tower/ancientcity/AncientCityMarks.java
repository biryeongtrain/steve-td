package kim.biryeong.semiontd.tower.ancientcity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterDataKey;
import net.minecraft.resources.Identifier;

public final class AncientCityMarks {
    private static final MonsterDataKey<MarkSet> MARKS = MonsterDataKey.of(
            Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "ancient_city_marks"),
            MarkSet.class
    );

    private AncientCityMarks() {
    }

    public static void apply(
            Monster monster,
            UUID ownerPlayer,
            UUID sourceId,
            double damageBonus,
            int durationTicks
    ) {
        if (monster == null || ownerPlayer == null || sourceId == null || damageBonus <= 0.0 || durationTicks <= 0) {
            return;
        }
        MarkSet marks = monster.getData(MARKS).orElseGet(() -> {
            MarkSet created = new MarkSet();
            monster.setData(MARKS, created);
            return created;
        });
        marks.apply(ownerPlayer, sourceId, damageBonus, monster.activeTicks() + durationTicks);
    }

    public static double damageBonus(Monster monster, UUID ownerPlayer) {
        if (monster == null || ownerPlayer == null) {
            return 0.0;
        }
        MarkSet marks = monster.getData(MARKS).orElse(null);
        if (marks == null) {
            return 0.0;
        }
        double bonus = marks.damageBonus(ownerPlayer, monster.activeTicks());
        if (marks.empty()) {
            monster.removeData(MARKS);
        }
        return bonus;
    }

    public static boolean hasAnyActive(Monster monster) {
        if (monster == null) {
            return false;
        }
        MarkSet marks = monster.getData(MARKS).orElse(null);
        if (marks == null) {
            return false;
        }
        marks.prune(monster.activeTicks());
        if (marks.empty()) {
            monster.removeData(MARKS);
            return false;
        }
        return true;
    }

    static final class MarkSet {
        private final Map<UUID, Map<UUID, ActiveMark>> marksByOwner = new HashMap<>();

        void apply(UUID ownerPlayer, UUID sourceId, double damageBonus, int expiresAtTick) {
            marksByOwner.computeIfAbsent(ownerPlayer, ignored -> new HashMap<>())
                    .put(sourceId, new ActiveMark(Math.max(0.0, damageBonus), expiresAtTick));
        }

        double damageBonus(UUID ownerPlayer, int activeTick) {
            prune(activeTick);
            Map<UUID, ActiveMark> marks = marksByOwner.get(ownerPlayer);
            return marks == null ? 0.0 : marks.values().stream().mapToDouble(ActiveMark::damageBonus).max().orElse(0.0);
        }

        void prune(int activeTick) {
            Iterator<Map.Entry<UUID, Map<UUID, ActiveMark>>> owners = marksByOwner.entrySet().iterator();
            while (owners.hasNext()) {
                Map<UUID, ActiveMark> marks = owners.next().getValue();
                marks.values().removeIf(mark -> mark.expiresAtTick() <= activeTick);
                if (marks.isEmpty()) {
                    owners.remove();
                }
            }
        }

        boolean empty() {
            return marksByOwner.isEmpty();
        }
    }

    private record ActiveMark(double damageBonus, int expiresAtTick) {
    }
}
