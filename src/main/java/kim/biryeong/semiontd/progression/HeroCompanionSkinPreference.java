package kim.biryeong.semiontd.progression;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;

public record HeroCompanionSkinPreference(
        String sourceName,
        String sourceUuid,
        String textureValue,
        String textureSignature
) {
    public static final Codec<HeroCompanionSkinPreference> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("sourceName", "").forGetter(HeroCompanionSkinPreference::sourceName),
            Codec.STRING.optionalFieldOf("sourceUuid", "").forGetter(HeroCompanionSkinPreference::sourceUuid),
            Codec.STRING.optionalFieldOf("textureValue", "").forGetter(HeroCompanionSkinPreference::textureValue),
            Codec.STRING.optionalFieldOf("textureSignature", "").forGetter(HeroCompanionSkinPreference::textureSignature)
    ).apply(instance, HeroCompanionSkinPreference::new));

    public HeroCompanionSkinPreference {
        sourceName = sourceName == null ? "" : sourceName.trim();
        sourceUuid = sourceUuid == null ? "" : sourceUuid.trim();
        textureValue = textureValue == null ? "" : textureValue;
        textureSignature = textureSignature == null ? "" : textureSignature;
    }

    public static Optional<HeroCompanionSkinPreference> fromProfile(GameProfile profile) {
        if (profile == null || profile.getId() == null || profile.getName() == null || profile.getName().isBlank()) {
            return Optional.empty();
        }
        Property texture = profile.getProperties().get("textures").stream().findFirst().orElse(null);
        return Optional.of(new HeroCompanionSkinPreference(
                profile.getName(),
                profile.getId().toString(),
                texture == null ? "" : texture.value(),
                texture == null || texture.signature() == null ? "" : texture.signature()
        ));
    }

    public Optional<UUID> sourceId() {
        try {
            return Optional.of(UUID.fromString(sourceUuid));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public boolean valid() {
        return !sourceName.isBlank() && sourceId().isPresent();
    }

    public Optional<Property> textureProperty() {
        if (textureValue.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(textureSignature.isBlank()
                ? new Property("textures", textureValue)
                : new Property("textures", textureValue, textureSignature));
    }
}
