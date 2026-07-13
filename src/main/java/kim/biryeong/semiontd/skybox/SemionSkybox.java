package kim.biryeong.semiontd.skybox;

import net.minecraft.resources.ResourceLocation;

public record SemionSkybox(
        String id,
        String displayName,
        ResourceLocation itemModelId,
        byte[] textureData
) {
    public SemionSkybox {
        textureData = textureData.clone();
    }

    @Override
    public byte[] textureData() {
        return textureData.clone();
    }
}
