package kim.biryeong.semiontd.augment;

import net.kyori.adventure.text.minimessage.MiniMessage;

public enum AugmentRarity {
    SILVER, GOLD, PRISMATIC;

    public String markup(String text) {
        String tag = switch (this) {
            case SILVER -> "color:#cbd5e1";
            case GOLD -> "gradient:#fbbf24:#ffffff";
            case PRISMATIC -> "gradient:#c084fc:#ffffff";
        };
        return "<" + tag + "><bold>" + MiniMessage.miniMessage().escapeTags(text)
                + "</bold></" + (this == SILVER ? "color" : "gradient") + ">";
    }
}
