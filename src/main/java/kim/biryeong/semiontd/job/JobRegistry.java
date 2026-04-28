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

    private JobRegistry() {
    }

    public static SemionJob defaultJob() {
        return DEFAULT_JOB;
    }

    public static SemionJob register(SemionJob job) {
        Objects.requireNonNull(job, "job");
        SemionJob previous = JOBS.putIfAbsent(job.id(), job);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate job id: " + job.id());
        }
        return job;
    }

    public static Optional<SemionJob> find(ResourceLocation id) {
        return Optional.ofNullable(JOBS.get(id));
    }

    public static Collection<SemionJob> all() {
        return java.util.List.copyOf(JOBS.values());
    }
}
