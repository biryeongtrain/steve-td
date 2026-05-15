package kim.biryeong.semiontd.summon;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SummonRegistry {
    private static final Map<String, SummonMonsterType> SUMMONS = new LinkedHashMap<>();

    public static final SummonMonsterType FOX_KIT = register(new GruntSummon());
    public static final SummonMonsterType HONEY_BEE = register(new SkitterSwarmSummon());
    public static final SummonMonsterType SHELL_TURTLE = register(new QuiltGuardSummon());
    public static final SummonMonsterType SPARK_AXOLOTL = register(new StaticBobbinSummon());
    public static final SummonMonsterType MEDIC_DUCK = register(new ButtonNurseSummon());
    public static final SummonMonsterType PINCER_CRAB = register(new PopperPodSummon());
    public static final SummonMonsterType IRONCLAD_BOAR = register(new IroncladTankSummon());
    public static final SummonMonsterType WARD_RAM = register(new WardTankSummon());
    public static final SummonMonsterType STATIC_OWL = register(new StaticDisruptorSummon());
    public static final SummonMonsterType PULSE_FAWN = register(new PulseSupportSummon());
    public static final SummonMonsterType GALE_FERRET = register(new GaleFerretSummon());
    public static final SummonMonsterType BULWARK_BISON = register(new BulwarkBisonSummon());
    public static final SummonMonsterType WIZARD_CAT = register(new WizardCatSummon());
    public static final SummonMonsterType GROVE_ALPACA = register(new GroveAlpacaSummon());
    public static final SummonMonsterType STORM_LYNX = register(new StormLynxSummon());
    public static final SummonMonsterType AEGIS_GOLEM = register(new AegisGolemSummon());
    public static final SummonMonsterType NULL_IMP = register(new NullImpSummon());
    public static final SummonMonsterType ELDER_SPRITE = register(new ElderSpriteSummon());
    public static final SummonMonsterType BOMBARD_TOAD = register(new BombardToadSummon());
    public static final SummonMonsterType SIEGE_BREAKER = register(new SiegeBreakerSummon());
    public static final SummonMonsterType APEX_WARDEN = register(new ApexWardenSummon());
    public static final SummonMonsterType ORACLE_PHOENIX = register(new OraclePhoenixSummon());

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
