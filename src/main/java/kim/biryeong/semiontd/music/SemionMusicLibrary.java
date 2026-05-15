package kim.biryeong.semiontd.music;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import kim.biryeong.semiontd.SemionTd;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public final class SemionMusicLibrary {
    private final List<SemionMusicTrack> tracks;
    private final long totalDurationTicks;

    public SemionMusicLibrary(List<SemionMusicTrack> tracks) {
        this.tracks = List.copyOf(tracks);
        long total = 0L;
        for (SemionMusicTrack track : this.tracks) {
            total += track.durationTicks();
        }
        this.totalDurationTicks = total;
    }

    public static SemionMusicLibrary load(Path musicDir, Logger logger) {
        try {
            Files.createDirectories(musicDir);
        } catch (IOException exception) {
            logger.warn("Failed to create Semion TD music directory {}; music is disabled.", musicDir, exception);
            return empty();
        }

        try (var paths = Files.walk(musicDir)) {
            List<SemionMusicTrack> tracks = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg"))
                    .sorted(Comparator.comparing(path -> musicDir.relativize(path).toString()))
                    .map(path -> readTrack(musicDir, path, logger))
                    .flatMap(java.util.Optional::stream)
                    .toList();
            return new SemionMusicLibrary(tracks);
        } catch (IOException exception) {
            logger.warn("Failed to scan Semion TD music directory {}; music is disabled.", musicDir, exception);
            return empty();
        }
    }

    public static SemionMusicLibrary empty() {
        return new SemionMusicLibrary(List.of());
    }

    private static java.util.Optional<SemionMusicTrack> readTrack(Path musicDir, Path path, Logger logger) {
        try {
            String id = sanitizeTrackId(musicDir.relativize(path));
            if (id.isBlank()) {
                logger.warn("Skipping Semion TD music file with unusable name: {}", path);
                return java.util.Optional.empty();
            }
            long durationTicks = OggVorbisDurationReader.readDurationTicks(path);
            return java.util.Optional.of(new SemionMusicTrack(
                    id,
                    path,
                    ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "music." + id.replace('/', '.')),
                    ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "music/" + id),
                    durationTicks
            ));
        } catch (IOException | IllegalArgumentException exception) {
            logger.warn("Skipping invalid Semion TD music file {}.", path, exception);
            return java.util.Optional.empty();
        }
    }

    private static String sanitizeTrackId(Path relativePath) {
        String raw = relativePath.toString().replace('\\', '/');
        if (raw.toLowerCase(Locale.ROOT).endsWith(".ogg")) {
            raw = raw.substring(0, raw.length() - 4);
        }
        raw = raw.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        boolean previousUnderscore = false;
        for (int index = 0; index < raw.length(); index++) {
            char character = raw.charAt(index);
            if (character == '/') {
                if (!builder.isEmpty() && builder.charAt(builder.length() - 1) != '/') {
                    builder.append('/');
                    previousUnderscore = false;
                }
            } else if ((character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')) {
                builder.append(character);
                previousUnderscore = false;
            } else if (!previousUnderscore && !builder.isEmpty() && builder.charAt(builder.length() - 1) != '/') {
                builder.append('_');
                previousUnderscore = true;
            }
        }
        while (!builder.isEmpty() && (builder.charAt(builder.length() - 1) == '_' || builder.charAt(builder.length() - 1) == '/')) {
            builder.setLength(builder.length() - 1);
        }
        return builder.toString();
    }

    public List<SemionMusicTrack> tracks() {
        return tracks;
    }

    public boolean isEmpty() {
        return tracks.isEmpty();
    }

    public long totalDurationTicks() {
        return totalDurationTicks;
    }

    public java.util.Optional<TrackWindow> trackAt(long musicTick) {
        if (tracks.isEmpty() || totalDurationTicks < 1) {
            return java.util.Optional.empty();
        }
        long cursor = Math.floorMod(musicTick, totalDurationTicks);
        long startTick = musicTick - cursor;
        for (SemionMusicTrack track : tracks) {
            long end = cursor + track.durationTicks();
            if (cursor < track.durationTicks()) {
                return java.util.Optional.of(new TrackWindow(track, cursor, startTick));
            }
            cursor -= track.durationTicks();
            startTick += track.durationTicks();
        }
        SemionMusicTrack first = tracks.getFirst();
        return java.util.Optional.of(new TrackWindow(first, 0L, musicTick));
    }

    public record TrackWindow(SemionMusicTrack track, long elapsedTicks, long startedAtTick) {
        public long remainingTicks() {
            return Math.max(0L, track.durationTicks() - elapsedTicks);
        }
    }
}
