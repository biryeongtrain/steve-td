package kim.biryeong.semiontd.tower.plant;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Terrain a plant family lays down under itself.
 *
 * <p>A tile only ever belongs to one soil, so families compete for lane space: a family cannot be
 * planted on top of another family's soil.
 *
 * <p>Never use a {@code FallingBlock} here. Lane floors can be a single block thick, so sand or
 * gravel would drop into the void and delete the terrain the moment it is placed.
 */
public enum PlantSoil {
    MEADOW("meadow", "잔디", Blocks.GRASS_BLOCK),
    MYCELIUM("mycelium", "균사", Blocks.MYCELIUM),
    DESERT("desert", "사암", Blocks.SANDSTONE),
    PODZOL("podzol", "회백토", Blocks.PODZOL);

    private final String key;
    private final String displayName;
    private final Block block;

    PlantSoil(String key, String displayName, Block block) {
        this.key = key;
        this.displayName = displayName;
        this.block = block;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public Block block() {
        return block;
    }

    /**
     * Balance config id holding this soil's ability values.
     */
    public String configId() {
        return "plant_soil_" + key;
    }
}
