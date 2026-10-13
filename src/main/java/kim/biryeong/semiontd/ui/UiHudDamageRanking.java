package kim.biryeong.semiontd.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.demonlord.DemonLordState;

final class UiHudDamageRanking {
    private UiHudDamageRanking() {
    }

    static List<Summary> summarize(Iterable<Tower> towers, DemonLordState demonLord) {
        Map<String, Summary> byType = new HashMap<>();
        for (Tower tower : towers) {
            TowerType type = tower.roundCombatType();
            if (type == null) {
                continue;
            }
            byType.merge(
                    type.id(),
                    new Summary(
                            type.id(),
                            type.displayName(),
                            tower.roundPhysicalDamageDealt(),
                            tower.roundMagicDamageDealt(),
                            tower.roundDamageTaken()
                    ),
                    Summary::merge
            );
        }
        if (demonLord != null && (demonLord.roundPhysicalDamageDealt() > 0.0
                || demonLord.roundMagicDamageDealt() > 0.0)) {
            byType.put("semion-td:demon_lord", new Summary(
                    "semion-td:demon_lord",
                    "마왕",
                    demonLord.roundPhysicalDamageDealt(),
                    demonLord.roundMagicDamageDealt(),
                    0.0
            ));
        }

        return List.copyOf(byType.values());
    }

    static List<Summary> top(List<Summary> summaries, boolean dealt) {
        Comparator<Summary> comparator = Comparator
                .comparingDouble((Summary summary) -> dealt ? summary.dealt() : summary.taken())
                .reversed()
                .thenComparing(Summary::displayName)
                .thenComparing(Summary::id);
        List<Summary> top = new ArrayList<>(5);
        for (Summary summary : summaries) {
            if (!((dealt ? summary.dealt() : summary.taken()) > 0.0)) {
                continue;
            }
            int index = 0;
            while (index < top.size() && comparator.compare(top.get(index), summary) <= 0) {
                index++;
            }
            if (index < 5) {
                top.add(index, summary);
                if (top.size() > 5) {
                    top.removeLast();
                }
            }
        }
        return List.copyOf(top);
    }

    record Summary(String id, String displayName, double physical, double magic, double taken) {
        double dealt() {
            return physical + magic;
        }

        private Summary merge(Summary other) {
            return new Summary(
                    id,
                    displayName,
                    physical + other.physical,
                    magic + other.magic,
                    taken + other.taken
            );
        }
    }

}
