package kim.biryeong.semiontd.map.gen;

import java.util.Arrays;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;

public class GeneratorBlockSamples {
    public static final NoiseColumn VOID = new NoiseColumn(0, new BlockState[0]);

    public static NoiseColumn heightmap(int minY, int maxY, BlockState block) {
        int height = maxY - minY + 1;

        var sample = new BlockState[height];
        Arrays.fill(sample, block);
        return new NoiseColumn(minY, sample);
    }
}
