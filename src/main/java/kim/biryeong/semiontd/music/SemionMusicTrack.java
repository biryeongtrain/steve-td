package kim.biryeong.semiontd.music;

import java.nio.file.Path;
import net.minecraft.resources.ResourceLocation;

public record SemionMusicTrack(
        String id,
        Path source,
        ResourceLocation eventId,
        ResourceLocation soundFileId,
        long durationTicks
) {
    public SemionMusicTrack {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Music track id cannot be blank.");
        }
        if (source == null) {
            throw new IllegalArgumentException("Music track source cannot be null.");
        }
        if (eventId == null || soundFileId == null) {
            throw new IllegalArgumentException("Music track resource ids cannot be null.");
        }
        if (durationTicks < 1) {
            throw new IllegalArgumentException("Music track duration must be positive.");
        }
    }
}
