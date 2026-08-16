package kim.biryeong.semiontd.config;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public record JobAvailabilityConfig(Set<String> disabledJobs) {
    public JobAvailabilityConfig {
        LinkedHashSet<String> validated = new LinkedHashSet<>();
        if (disabledJobs != null) {
            for (String jobId : disabledJobs) {
                if (jobId == null || ResourceLocation.tryParse(jobId) == null) {
                    throw new IllegalArgumentException("Invalid disabled job id: " + jobId);
                }
                validated.add(jobId);
            }
        }
        disabledJobs = Collections.unmodifiableSet(validated);
    }

    public static JobAvailabilityConfig defaultConfig() {
        return new JobAvailabilityConfig(Set.of());
    }

    public boolean isEnabled(ResourceLocation jobId) {
        return jobId != null && !disabledJobs.contains(jobId.toString());
    }

    public JobAvailabilityConfig withEnabled(ResourceLocation jobId, boolean enabled) {
        LinkedHashSet<String> updated = new LinkedHashSet<>(disabledJobs);
        if (enabled) {
            updated.remove(jobId.toString());
        } else {
            updated.add(jobId.toString());
        }
        return new JobAvailabilityConfig(updated);
    }
}
