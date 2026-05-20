package kim.biryeong.semiontd.summon;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class SummonShop {
    private final Map<String, SummonMonsterType> summons = new LinkedHashMap<>();

    public SummonShop() {
        reloadFromRegistry();
    }

    public void reloadFromRegistry() {
        summons.clear();
        for (SummonMonsterType summonType : SummonRegistry.all()) {
            register(summonType);
        }
    }

    public Optional<SummonMonsterType> find(String id) {
        return Optional.ofNullable(summons.get(id));
    }

    public Collection<SummonMonsterType> all() {
        return summons.values();
    }

    private void register(SummonMonsterType type) {
        summons.put(type.id(), type);
    }
}
