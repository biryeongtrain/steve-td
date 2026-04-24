package kim.biryeong.semionTd.summon;

import kim.biryeong.semionTd.config.AttackKind;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class SummonShop {
    private final Map<String, SummonMonsterType> summons = new LinkedHashMap<>();

    public SummonShop() {
        register(new SummonMonsterType(
                "grunt",
                "Grunt",
                20,
                2,
                50,
                0,
                5,
                AttackKind.MELEE,
                "minecraft:zombie",
                5
        ));
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
