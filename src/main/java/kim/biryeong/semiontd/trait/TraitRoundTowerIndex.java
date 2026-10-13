package kim.biryeong.semiontd.trait;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.tower.Tower;

public final class TraitRoundTowerIndex {
    private final Map<UUID, Map<String, Long>> countsByOwner;

    private TraitRoundTowerIndex(Map<UUID, Map<String, Long>> countsByOwner) {
        this.countsByOwner = countsByOwner;
    }

    public static TraitRoundTowerIndex capture(Collection<? extends Tower> towers) {
        Map<UUID, Map<String, Long>> countsByOwner = new HashMap<>();
        for (Tower tower : towers) {
            if (tower.health() > 0.0) {
                countsByOwner.computeIfAbsent(tower.ownerPlayer(), ignored -> new HashMap<>())
                        .merge(tower.type().id(), 1L, Long::sum);
            }
        }
        return new TraitRoundTowerIndex(countsByOwner);
    }

    public double sameTypeDamageBonus(TraitLoadout loadout, Tower tower) {
        if (tower == null) {
            return 0.0;
        }
        Map<String, Long> counts = countsByOwner.get(tower.ownerPlayer());
        long sameTypeTowers = counts == null ? 0L : counts.getOrDefault(tower.type().id(), 0L);
        return TraitEffects.sameTypeDamageBonus(loadout, sameTypeTowers);
    }

    public double diversityDamageBonus(TraitLoadout loadout, Tower tower) {
        if (tower == null) {
            return 0.0;
        }
        Map<String, Long> counts = countsByOwner.get(tower.ownerPlayer());
        long distinctTypes = counts == null ? 0L : counts.size();
        return TraitEffects.diversityDamageBonus(loadout, distinctTypes);
    }
}
