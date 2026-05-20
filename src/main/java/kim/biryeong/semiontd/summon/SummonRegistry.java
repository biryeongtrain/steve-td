package kim.biryeong.semiontd.summon;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SummonRegistry {
    private static final Map<String, SummonMonsterType> SUMMONS = new LinkedHashMap<>();

    private SummonRegistry() {
    }

    public static SummonMonsterType register(SummonMonsterType summonType) {
        Objects.requireNonNull(summonType, "summonType");
        SummonMonsterType previous = SUMMONS.putIfAbsent(summonType.id(), summonType);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate summon id: " + summonType.id());
        }
        return summonType;
    }

    public static void reload(Collection<SummonMonsterType> summonTypes) {
        Objects.requireNonNull(summonTypes, "summonTypes");
        LinkedHashMap<String, SummonMonsterType> reloaded = new LinkedHashMap<>();
        for (SummonMonsterType summonType : summonTypes) {
            Objects.requireNonNull(summonType, "summonType");
            SummonMonsterType previous = reloaded.putIfAbsent(summonType.id(), summonType);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate summon id: " + summonType.id());
            }
        }
        SUMMONS.clear();
        SUMMONS.putAll(reloaded);
    }

    public static void clearForTesting() {
        SUMMONS.clear();
    }

    public static Optional<SummonMonsterType> find(String id) {
        return Optional.ofNullable(SUMMONS.get(id));
    }

    public static Collection<SummonMonsterType> all() {
        return java.util.List.copyOf(SUMMONS.values());
    }
}
