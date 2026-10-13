package kim.biryeong.semiontd.tower.magicschool;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kim.biryeong.semiontd.progression.HeroCompanionSkinPreference;
import kim.biryeong.semiontd.tower.TowerType;

public final class MagicSchoolSkins {
    public enum Kind {
        FRESHMAN("freshman", "신입생", MagicSchoolTowers.FRESHMAN),
        GRYFFINDOR("gryffindor", "그리핀도르 마법사", MagicSchoolTowers.GRYFFINDOR),
        HUFFLEPUFF("hufflepuff", "후플푸프 마법사", MagicSchoolTowers.HUFFLEPUFF),
        RAVENCLAW("ravenclaw", "래번클로 마법사", MagicSchoolTowers.RAVENCLAW),
        SLYTHERIN("slytherin", "슬리데린 마법사", MagicSchoolTowers.SLYTHERIN);

        private final String id;
        private final String displayName;
        private final TowerType type;

        Kind(String id, String displayName, TowerType type) {
            this.id = id;
            this.displayName = displayName;
            this.type = type;
        }

        public String id() { return id; }
        public String displayName() { return displayName; }
        public boolean matches(TowerType actual) {
            return this == FRESHMAN ? MagicSchoolTowers.isFreshman(actual) : MagicSchoolTowers.belongsToHouse(actual, type);
        }
        public static Kind byId(String id) {
            for (Kind kind : values()) if (kind.id.equals(id)) return kind;
            return null;
        }
        public static Kind forTower(TowerType type) {
            for (Kind kind : values()) if (kind.matches(type)) return kind;
            return null;
        }
    }

    private static final Map<UUID, Map<Kind, HeroCompanionSkinPreference>> SELECTIONS = new ConcurrentHashMap<>();

    private MagicSchoolSkins() {}

    public static void load(UUID owner, Map<String, HeroCompanionSkinPreference> stored) {
        if (owner == null) return;
        var loaded = new EnumMap<Kind, HeroCompanionSkinPreference>(Kind.class);
        if (stored != null) stored.forEach((id, skin) -> {
            Kind kind = Kind.byId(id);
            if (kind != null && skin != null && skin.valid()) loaded.put(kind, skin);
        });
        if (loaded.isEmpty()) SELECTIONS.remove(owner);
        else SELECTIONS.put(owner, Map.copyOf(loaded));
    }

    public static Optional<HeroCompanionSkinPreference> preference(UUID owner, Kind kind) {
        return owner == null || kind == null ? Optional.empty()
                : Optional.ofNullable(SELECTIONS.getOrDefault(owner, Map.of()).get(kind));
    }

    public static void set(UUID owner, Kind kind, HeroCompanionSkinPreference skin) {
        if (owner == null || kind == null || skin != null && !skin.valid()) return;
        var updated = new EnumMap<Kind, HeroCompanionSkinPreference>(Kind.class);
        updated.putAll(SELECTIONS.getOrDefault(owner, Map.of()));
        if (skin == null) updated.remove(kind);
        else updated.put(kind, skin);
        if (updated.isEmpty()) SELECTIONS.remove(owner);
        else SELECTIONS.put(owner, Map.copyOf(updated));
    }

    public static GameProfile profile(UUID owner, Kind kind, GameProfile fallback) {
        var skin = preference(owner, kind).orElse(null);
        String fingerprint = skin == null ? "default" : skin.sourceUuid() + ":" + skin.textureValue();
        UUID visualId = UUID.nameUUIDFromBytes(("semion-td:wizard-skin:" + owner + ":" + kind + ":" + fingerprint)
                .getBytes(StandardCharsets.UTF_8));
        var properties = skin == null
                ? fallback == null ? com.mojang.authlib.properties.PropertyMap.EMPTY : fallback.properties()
                : skin.textureProperty().map(texture -> new com.mojang.authlib.properties.PropertyMap(
                        com.google.common.collect.ImmutableMultimap.of("textures", texture)))
                        .orElse(com.mojang.authlib.properties.PropertyMap.EMPTY);
        return new GameProfile(visualId, "WizardSkin", properties);
    }

    public static void clearAll() { SELECTIONS.clear(); }
}
