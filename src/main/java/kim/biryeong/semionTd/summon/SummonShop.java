package kim.biryeong.semionTd.summon;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import kim.biryeong.semionTd.config.SummonConfig;
import kim.biryeong.semionTd.config.SummonMonsterEntry;

public final class SummonShop {
    private final Map<String, SummonMonsterType> summons = new LinkedHashMap<>();

    public SummonShop() {
        this(SummonConfig.defaultConfig());
    }

    public SummonShop(SummonConfig config) {
        if (config != null) {
            for (SummonMonsterEntry entry : config.summons()) {
                register(entry.toType());
            }
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