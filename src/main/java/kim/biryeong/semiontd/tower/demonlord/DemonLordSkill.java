package kim.biryeong.semiontd.tower.demonlord;

import java.util.Locale;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The ten demon lord skills.
 *
 * <p>Unlike every other builder, a demon lord tower never fights. It exists only to hand its skill
 * to the owning player, who is the actual weapon. Each skill therefore owns a fixed hotbar slot:
 * slots 0-2 stay with the shared match tools, slots 3-7 hold whichever skills the player bought,
 * and slot {@link #BLADE_SLOT} always holds the 마검 the hand snaps back to after a cast.
 *
 * <p>{@link #slotCost()} is the builder's "코스트" and feeds the existing
 * {@code towerSlotCost} capacity system, so the round tower limit decides how many skills can be
 * live at once. All ten together cost 32, which is far more than an early limit allows.
 */
public enum DemonLordSkill {
    WAVE_OF_MALICE("wave_of_malice", "악의 파동", 3, 8, Items.BREEZE_ROD),
    DEMON_WINGS("demon_wings", "악마의 날개", 2, 6, Items.PHANTOM_MEMBRANE),
    SKY_BREAKER("sky_breaker", "하늘 부수기", 4, 10, Items.MACE),
    ARCANE_BOMBARDMENT("arcane_bombardment", "마도 폭격", 4, 10, Items.FIRE_CHARGE),
    DEMON_BARRIER("demon_barrier", "악마 배리어", 3, 20, Items.SHIELD),
    HELLFIRE_BRAND("hellfire_brand", "지옥불 낙인", 3, 12, Items.BLAZE_POWDER),
    SOUL_DRAIN("soul_drain", "영혼 흡수", 2, 9, Items.GHAST_TEAR),
    ROAR_OF_DREAD("roar_of_dread", "공포의 포효", 3, 14, Items.GOAT_HORN),
    GRIP_OF_DOOM("grip_of_doom", "파멸의 손아귀", 4, 12, Items.WITHER_SKELETON_SKULL),
    HELL_GUILLOTINE("hell_guillotine", "지옥의 단두대", 4, 13, Items.NETHERITE_AXE);

    /** Hotbar slot the hand is forced back to after a cast. Holds the 마검. */
    public static final int BLADE_SLOT = 8;

    /** Number of upgrade tiers. Every tier past the first shaves one second off the cooldown. */
    public static final int MAX_TIER = 4;

    private final String key;
    private final String displayName;
    private final int slotCost;
    private final int baseCooldownSeconds;
    private final Item item;

    DemonLordSkill(String key, String displayName, int slotCost, int baseCooldownSeconds, Item item) {
        this.key = key;
        this.displayName = displayName;
        this.slotCost = slotCost;
        this.baseCooldownSeconds = baseCooldownSeconds;
        this.item = item;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    /** The builder's 코스트: how much of the round tower limit this skill occupies. */
    public int slotCost() {
        return slotCost;
    }

    public int baseCooldownSeconds() {
        return baseCooldownSeconds;
    }

    public Item item() {
        return item;
    }

    /** Config bucket for values shared by every tier of this skill. */
    public String configId() {
        return "demon_lord_" + key;
    }

    /** Tower id for a given tier, e.g. {@code t1_wave_of_malice_tower}. */
    public String towerId(int tier) {
        return "t" + tier + "_" + key + "_tower";
    }

    /**
     * Cooldown before per-tier reduction. Tier 1 pays the full price and each upgrade removes one
     * second, so a tier 4 악의 파동 fires every 5 seconds instead of 8.
     */
    public int cooldownSecondsForTier(int tier) {
        return Math.max(1, baseCooldownSeconds - (Math.max(1, tier) - 1));
    }

    public static DemonLordSkill fromKey(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        for (DemonLordSkill skill : values()) {
            if (skill.key.equals(normalized)) {
                return skill;
            }
        }
        return null;
    }

}
