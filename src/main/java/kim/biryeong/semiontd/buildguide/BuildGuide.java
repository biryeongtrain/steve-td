package kim.biryeong.semiontd.buildguide;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import kim.biryeong.semiontd.trait.TraitLoadoutSnapshot;

public record BuildGuide(
        String code,
        String title,
        UUID authorId,
        String authorName,
        String jobId,
        TraitLoadoutSnapshot traitLoadout,
        int finalRound,
        long publishedAtEpochMillis,
        String visibility,
        List<BuildAction> actions
) {
    public static final String VISIBILITY_PRIVATE = "private";
    public static final String VISIBILITY_PUBLIC = "public";

    public BuildGuide {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Build code cannot be blank.");
        }
        title = normalizeTitle(title);
        authorName = authorName == null ? "" : authorName;
        jobId = jobId == null ? "" : jobId;
        traitLoadout = traitLoadout == null ? TraitLoadoutSnapshot.none() : traitLoadout;
        finalRound = Math.max(1, finalRound);
        publishedAtEpochMillis = Math.max(0L, publishedAtEpochMillis);
        visibility = normalizeVisibility(visibility);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public static String normalizeTitle(String title) {
        String normalized = title == null ? "" : title.trim();
        if (normalized.isEmpty()) {
            return "이름 없는 빌드";
        }
        return normalized.length() > 48 ? normalized.substring(0, 48) : normalized;
    }

    public static String normalizeVisibility(String visibility) {
        if (visibility == null || visibility.isBlank()) {
            return VISIBILITY_PUBLIC;
        }
        String normalized = visibility.trim().toLowerCase(Locale.ROOT);
        return VISIBILITY_PRIVATE.equals(normalized) ? VISIBILITY_PRIVATE : VISIBILITY_PUBLIC;
    }

    public boolean isPublic() {
        return VISIBILITY_PUBLIC.equals(visibility);
    }

    public boolean isPrivate() {
        return !isPublic();
    }

    public boolean ownedBy(UUID playerId) {
        return authorId != null && authorId.equals(playerId);
    }

    public boolean viewableBy(UUID playerId) {
        return isPublic() || ownedBy(playerId);
    }

    public BuildGuide withVisibility(String visibility) {
        return new BuildGuide(
                code,
                title,
                authorId,
                authorName,
                jobId,
                traitLoadout,
                finalRound,
                publishedAtEpochMillis,
                normalizeVisibility(visibility),
                actions
        );
    }
}
