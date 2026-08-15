package kim.biryeong.semiontd.tower.hero;

import java.util.Locale;

public enum HeroCompanionRole {
    KNIGHT("knight", "기사"),
    ARCHER("archer", "궁수"),
    MAGE("mage", "마법사"),
    PRIEST("priest", "사제"),
    ROGUE("rogue", "도적"),
    BARD("bard", "음유시인");

    private final String id;
    private final String displayName;

    HeroCompanionRole(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static HeroCompanionRole byId(String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (HeroCompanionRole role : values()) {
            if (role.id.equals(normalized)) {
                return role;
            }
        }
        return null;
    }
}
