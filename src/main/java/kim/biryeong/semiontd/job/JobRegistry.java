package kim.biryeong.semiontd.job;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public final class JobRegistry {
    private static final Map<ResourceLocation, SemionJob> JOBS = new LinkedHashMap<>();
    private static final SemionJob DEFAULT_JOB = register(new DefaultJob());

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
        registerIfAbsent(new PlantTowerJob());
    }

    public static synchronized Optional<SemionJob> find(ResourceLocation id) {
        return Optional.ofNullable(JOBS.get(id));
    }

    public static synchronized Collection<SemionJob> all() {
        return java.util.List.copyOf(JOBS.values());
    }
}
