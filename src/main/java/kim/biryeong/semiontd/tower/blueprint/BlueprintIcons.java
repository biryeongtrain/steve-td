package kim.biryeong.semiontd.tower.blueprint;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** 모듈 아이콘. */
final class BlueprintIcons {
    private BlueprintIcons() {
    }

    static Item module(BlueprintModule module) {
        return switch (module) {
            case MULTISHOT -> Items.SPECTRAL_ARROW;
            case SPLASH -> Items.FIRE_CHARGE;
            case LINE -> Items.BLAZE_ROD;
            case CHAIN -> Items.LIGHTNING_ROD.weathering().unaffected();
            case SLOW -> Items.COBWEB;
            case STUN -> Items.ANVIL;
            case POISON -> Items.SPIDER_EYE;
            case VULNERABILITY -> Items.FERMENTED_SPIDER_EYE;
            case CRIT -> Items.DIAMOND_SWORD;
            case EXECUTE -> Items.NETHERITE_AXE;
            case KILL_EXPLOSION -> Items.TNT;
            case LIFESTEAL -> Items.REDSTONE;
            case THORNS -> Items.CACTUS;
            case ARMOR -> Items.SHIELD;
            case REGEN -> Items.GLISTERING_MELON_SLICE;
            case HEAL_AURA -> Items.BEACON;
            case HASTE_AURA -> Items.SUGAR;
            case DETECTION -> Items.ENDER_EYE;
            case BOSS_SLAYER -> Items.WITHER_SKELETON_SKULL;
            case FOCUS -> Items.CROSSBOW;
            case FRENZY -> Items.BLAZE_POWDER;
            case KNOCKBACK -> Items.PISTON;
            case PLUNDER -> Items.GOLD_INGOT;
            case TAUNT -> Items.BELL;
            case RANGE_AURA -> Items.SPYGLASS;
            case SUMMON -> Items.ZOMBIE_HEAD;
        };
    }
}
