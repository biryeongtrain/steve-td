package kim.biryeong.semiontd.summon;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SummonRegistry {
    private static final Map<String, SummonMonsterType> SUMMONS = new LinkedHashMap<>();

    public static final SummonMonsterType GRUNT = register(new GruntSummon());
    public static final SummonMonsterType SKITTER_SWARM = register(new SkitterSwarmSummon());
    public static final SummonMonsterType IRONCLAD_TANK = register(new IroncladTankSummon());
    public static final SummonMonsterType WARD_TANK = register(new WardTankSummon());
    public static final SummonMonsterType STATIC_DISRUPTOR = register(new StaticDisruptorSummon());
    public static final SummonMonsterType PULSE_SUPPORT = register(new PulseSupportSummon());
    public static final SummonMonsterType SIEGE_BREAKER = register(new SiegeBreakerSummon());

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

    public static Optional<SummonMonsterType> find(String id) {
        return Optional.ofNullable(SUMMONS.get(id));
    }

    public static Collection<SummonMonsterType> all() {
        return java.util.List.copyOf(SUMMONS.values());
    }
}
