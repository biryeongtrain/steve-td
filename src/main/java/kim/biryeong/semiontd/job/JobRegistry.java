package kim.biryeong.semiontd.job;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import kim.biryeong.semiontd.config.JobAvailabilityConfig;
import net.minecraft.resources.ResourceLocation;

public final class JobRegistry {
    private static final Map<ResourceLocation, SemionJob> JOBS = new LinkedHashMap<>();
    private static final Set<ResourceLocation> OFFICIAL_BUILDER_IDS = Set.of(
            VillagerTowerJob.ID,
            VillagerAdvTowerJob.ID,
            UndeadTowerJob.ID,
            AnimalTowerJob.ID,
            WarlockTowerJob.ID,
            LegionTowerJob.ID,
            ResonanceTowerJob.ID,
            IllagerTowerJob.ID,
            NetherTowerJob.ID,
            OceanTowerJob.ID,
            AncientCityTowerJob.ID,
            HeroPartyTowerJob.ID
    );
    private static final SemionJob DEFAULT_JOB = register(new DefaultJob());
    private static JobAvailabilityConfig availability = JobAvailabilityConfig.defaultConfig();

    static {
        registerBuiltIns();
    }

    private JobRegistry() {
    }

    public static synchronized SemionJob defaultJob() {
        return DEFAULT_JOB;
    }

    public static synchronized SemionJob register(SemionJob job) {
        Objects.requireNonNull(job, "job");
        SemionJob previous = JOBS.putIfAbsent(job.id(), job);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate job id: " + job.id());
        }
        return job;
    }

    public static synchronized SemionJob registerIfAbsent(SemionJob job) {
        Objects.requireNonNull(job, "job");
        SemionJob existing = JOBS.putIfAbsent(job.id(), job);
        return existing == null ? job : existing;
    }

    public static synchronized void registerBuiltIns() {
        registerIfAbsent(new VillagerTowerJob());
        registerIfAbsent(new VillagerAdvTowerJob());
        registerIfAbsent(new UndeadTowerJob());
        registerIfAbsent(new AnimalTowerJob());
        registerIfAbsent(new WarlockTowerJob());
        registerIfAbsent(new LegionTowerJob());
        registerIfAbsent(new ResonanceTowerJob());
        registerIfAbsent(new IllagerTowerJob());
        registerIfAbsent(new NetherTowerJob());
        registerIfAbsent(new EndTowerJob());
        registerIfAbsent(new OceanTowerJob());
        registerIfAbsent(new AncientCityTowerJob());
        registerIfAbsent(new AdversaryTowerJob());
        registerIfAbsent(new MageTowerJob());
        registerIfAbsent(new EngineerTowerJob());
        registerIfAbsent(new InsectTowerJob());
        registerIfAbsent(new FutureAgencyTowerJob());
        registerIfAbsent(new QueenTowerJob());
        registerIfAbsent(new HeroPartyTowerJob());
        registerIfAbsent(new AtlantisTowerJob());
        registerIfAbsent(new PlantTowerJob());
        registerIfAbsent(new ArmyTowerJob());
        registerIfAbsent(new ThunderTowerJob());
    }

    public static synchronized Optional<SemionJob> find(ResourceLocation id) {
        return Optional.ofNullable(JOBS.get(id));
    }

    public static synchronized void configureAvailability(JobAvailabilityConfig config) {
        availability = config == null ? JobAvailabilityConfig.defaultConfig() : config;
    }

    public static synchronized boolean isEnabled(ResourceLocation id) {
        return DEFAULT_JOB.id().equals(id) || availability.isEnabled(id);
    }

    public static synchronized boolean isEnabled(SemionJob job) {
        return job != null && isEnabled(job.id());
    }

    public static synchronized Collection<SemionJob> all() {
        return List.copyOf(JOBS.values());
    }

    public static synchronized List<SemionJob> officialBuilders() {
        return JOBS.values().stream()
                .filter(job -> OFFICIAL_BUILDER_IDS.contains(job.id()))
                .toList();
    }

    public static synchronized List<SemionJob> creativeBuilders() {
        return JOBS.values().stream()
                .filter(job -> job != DEFAULT_JOB && !OFFICIAL_BUILDER_IDS.contains(job.id()))
                .toList();
    }
}
