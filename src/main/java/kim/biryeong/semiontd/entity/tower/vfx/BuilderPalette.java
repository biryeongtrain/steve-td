package kim.biryeong.semiontd.entity.tower.vfx;

import kim.biryeong.semiontd.api.area.AreaVfxPalette;
import kim.biryeong.semiontd.api.area.AreaVfxParticle;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;

public enum BuilderPalette {
    VILLAGER(0xD9A441, 0xFFF1A8, "minecraft:wax_on", "minecraft:end_rod"),
    VILLAGER_ADV(0x39B56A, 0xF4D35E, "minecraft:happy_villager", "minecraft:wax_on"),
    UNDEAD(0x50B7C1, 0xB2EBF2, "minecraft:soul_fire_flame", "minecraft:sculk_soul"),
    ANIMAL(0x7CB342, 0xF4E04D, "minecraft:composter", "minecraft:happy_villager"),
    WARLOCK(0x8E5BD9, 0xE04462, "minecraft:witch", "minecraft:dragon_breath"),
    LEGION(0x26C6DA, 0xB2F7EF, "minecraft:electric_spark", "minecraft:end_rod"),
    RESONANCE(0xEC6FA8, 0x65D6E8, "minecraft:cherry_leaves", "minecraft:electric_spark"),
    ILLAGER(0x78909C, 0xC3423F, "minecraft:ash", "minecraft:damage_indicator"),
    NETHER(0xF57C00, 0x8AAA45, "minecraft:flame", "minecraft:smoke"),
    OCEAN(0x2196F3, 0x80DEEA, "minecraft:nautilus", "minecraft:splash"),
    DEFAULT(0xE0E0E0, 0xFFFFFF, "minecraft:end_rod", "minecraft:crit");

    private final DustParticleOptions rayParticle;
    private final DustParticleOptions accentParticle;
    private final String gcbRayParticle;
    private final String gcbAccentParticle;

    BuilderPalette(int rayColor, int accentColor, String gcbRayParticle, String gcbAccentParticle) {
        this.rayParticle = new DustParticleOptions(rayColor, 1.2F);
        this.accentParticle = new DustParticleOptions(accentColor, 0.95F);
        this.gcbRayParticle = gcbRayParticle;
        this.gcbAccentParticle = gcbAccentParticle;
    }

    public DustParticleOptions rayParticle() {
        return rayParticle;
    }

    public DustParticleOptions accentParticle() {
        return accentParticle;
    }

    public String gcbRayParticle() {
        return gcbRayParticle;
    }

    public String gcbAccentParticle() {
        return gcbAccentParticle;
    }

    public AreaVfxPalette areaPalette() {
        return new AreaVfxPalette(
                new AreaVfxParticle(rayParticle, particleId(gcbRayParticle)),
                new AreaVfxParticle(accentParticle, particleId(gcbAccentParticle))
        );
    }

    private static ResourceLocation particleId(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid particle id: " + id);
        }
        return parsed;
    }
}
