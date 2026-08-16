package kim.biryeong.semiontd.tower.hero;

import java.util.Locale;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public enum HeroWeapon {
    SWORD("sword", "검", Items.IRON_SWORD, 0, 12.0, 3.5, 12, 1.25, 60, false),
    GREATSWORD("greatsword", "대검", Items.DIAMOND_SWORD, 100, 20.0, 3.5, 20, 1.15, 40, false),
    LONGBOW("longbow", "장궁", Items.BOW, 110, 11.0, 10.0, 11, 0.85, 5, false),
    STAFF("staff", "마법 지팡이", Items.BLAZE_ROD, 120, 16.0, 8.0, 16, 0.90, 0, true),
    TOME("tome", "치유서", Items.ENCHANTED_BOOK, 100, 8.0, 7.0, 16, 1.05, -10, true);

    private final String id;
    private final String displayName;
    private final Item item;
    private final long purchaseCost;
    private final double damage;
    private final double range;
    private final int attackIntervalTicks;
    private final double maxHealthMultiplier;
    private final int aggroPriority;
    private final boolean magic;

    HeroWeapon(
            String id,
            String displayName,
            Item item,
            long purchaseCost,
            double damage,
            double range,
            int attackIntervalTicks,
            double maxHealthMultiplier,
            int aggroPriority,
            boolean magic
    ) {
        this.id = id;
        this.displayName = displayName;
        this.item = item;
        this.purchaseCost = purchaseCost;
        this.damage = damage;
        this.range = range;
        this.attackIntervalTicks = attackIntervalTicks;
        this.maxHealthMultiplier = maxHealthMultiplier;
        this.aggroPriority = aggroPriority;
        this.magic = magic;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Item item() {
        return item;
    }

    public String configId() {
        return "hero_party_weapon_" + id;
    }

    public long defaultPurchaseCost() {
        return purchaseCost;
    }

    public double defaultDamage() {
        return damage;
    }

    public double defaultRange() {
        return range;
    }

    public int defaultAttackIntervalTicks() {
        return attackIntervalTicks;
    }

    public double defaultMaxHealthMultiplier() {
        return maxHealthMultiplier;
    }

    public int defaultAggroPriority() {
        return aggroPriority;
    }

    public boolean magic() {
        return magic;
    }

    public static HeroWeapon byId(String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (HeroWeapon weapon : values()) {
            if (weapon.id.equals(normalized)) {
                return weapon;
            }
        }
        return null;
    }
}
