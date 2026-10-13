package kim.biryeong.semiontd.skybox;

import net.minecraft.resources.Identifier;

public record SemionSkybox(
        String id,
        String displayName,
        Identifier itemModelId,
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
