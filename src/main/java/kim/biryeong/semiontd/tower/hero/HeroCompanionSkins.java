package kim.biryeong.semiontd.tower.hero;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kim.biryeong.semiontd.progression.HeroCompanionSkinPreference;

public final class HeroCompanionSkins {
    private static final int DEFAULT_SKIN_COUNT = 18;
    private static final Map<UUID, Map<HeroCompanionRole, HeroCompanionSkinPreference>> SELECTIONS =
            new ConcurrentHashMap<>();

    private HeroCompanionSkins() {
    }

    public static void load(UUID ownerId, Map<String, HeroCompanionSkinPreference> stored) {
        if (ownerId == null) {
            return;
        }
        EnumMap<HeroCompanionRole, HeroCompanionSkinPreference> loaded = new EnumMap<>(HeroCompanionRole.class);
        if (stored != null) {
            stored.forEach((roleId, skin) -> {
                HeroCompanionRole role = HeroCompanionRole.byId(roleId);
                if (role != null && skin != null && skin.valid()) {
                    loaded.put(role, skin);
                }
            });
        }
        if (loaded.isEmpty()) {
            SELECTIONS.remove(ownerId);
        } else {
            SELECTIONS.put(ownerId, Map.copyOf(loaded));
        }
    }

    public static Optional<HeroCompanionSkinPreference> preference(UUID ownerId, HeroCompanionRole role) {
        if (ownerId == null || role == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(SELECTIONS.getOrDefault(ownerId, Map.of()).get(role));
    }

    public static void set(UUID ownerId, HeroCompanionRole role, HeroCompanionSkinPreference skin) {
        if (ownerId == null || role == null || skin != null && !skin.valid()) {
            return;
        }
        EnumMap<HeroCompanionRole, HeroCompanionSkinPreference> updated = new EnumMap<>(HeroCompanionRole.class);
        updated.putAll(SELECTIONS.getOrDefault(ownerId, Map.of()));
        if (skin == null) {
            updated.remove(role);
        } else {
            updated.put(role, skin);
        }
        if (updated.isEmpty()) {
            SELECTIONS.remove(ownerId);
        } else {
            SELECTIONS.put(ownerId, Map.copyOf(updated));
        }
    }

    public static void clearAll() {
        SELECTIONS.clear();
    }

    static GameProfile profile(UUID ownerId, HeroCompanionRole role) {
        return profile(ownerId, role, preference(ownerId, role).orElse(null));
    }

    static GameProfile profile(
            UUID ownerId,
            HeroCompanionRole role,
            HeroCompanionSkinPreference skin
    ) {
        UUID profileId = visualProfileId(ownerId, role, skin);
        GameProfile profile = new GameProfile(profileId, profileName(role, profileId));
        if (skin != null) {
            skin.textureProperty().ifPresent(property -> profile.getProperties().put("textures", property));
        }
        return profile;
    }

    static UUID visualProfileId(
            UUID ownerId,
            HeroCompanionRole role,
            HeroCompanionSkinPreference skin
    ) {
        UUID resolvedOwner = ownerId == null ? new UUID(0L, 0L) : ownerId;
        HeroCompanionRole resolvedRole = role == null ? HeroCompanionRole.KNIGHT : role;
        String fingerprint = skin == null
                ? "default"
                : skin.sourceUuid() + ":" + skin.textureValue();
        String base = "semion-td:hero-party:" + resolvedOwner + ":" + resolvedRole.id() + ":" + fingerprint;
        int defaultSkinIndex = skin == null
                ? Math.floorMod(roleDefaultProfileId(resolvedRole).hashCode(), DEFAULT_SKIN_COUNT)
                : skin.textureValue().isBlank()
                        ? skin.sourceId().map(id -> Math.floorMod(id.hashCode(), DEFAULT_SKIN_COUNT)).orElse(-1)
                        : -1;
        for (int salt = 0; salt < 1024; salt++) {
            UUID candidate = UUID.nameUUIDFromBytes((base + ":" + salt).getBytes(StandardCharsets.UTF_8));
            if (defaultSkinIndex < 0 || Math.floorMod(candidate.hashCode(), DEFAULT_SKIN_COUNT) == defaultSkinIndex) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a Hero companion skin profile UUID.");
    }

    static UUID roleDefaultProfileId(HeroCompanionRole role) {
        return UUID.nameUUIDFromBytes(
                ("semion-td:hero-party:" + role.id()).getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String profileName(HeroCompanionRole role, UUID profileId) {
        String prefix = "Hero" + Character.toUpperCase(role.id().charAt(0)) + role.id().substring(1);
        String suffix = profileId.toString().replace("-", "").substring(0, 6);
        String name = prefix + suffix;
        return name.length() <= 16 ? name : name.substring(0, 16);
    }
}
