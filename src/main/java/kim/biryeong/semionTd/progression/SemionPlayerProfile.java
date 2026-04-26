package kim.biryeong.semionTd.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record SemionPlayerProfile(
        String lastKnownName,
        int gamesPlayed,
        int wins,
        int losses,
        long cosmeticCurrency
) {
    public static final Codec<SemionPlayerProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("lastKnownName", "").forGetter(SemionPlayerProfile::lastKnownName),
            Codec.INT.optionalFieldOf("gamesPlayed", 0).forGetter(SemionPlayerProfile::gamesPlayed),
            Codec.INT.optionalFieldOf("wins", 0).forGetter(SemionPlayerProfile::wins),
            Codec.INT.optionalFieldOf("losses", 0).forGetter(SemionPlayerProfile::losses),
            Codec.LONG.optionalFieldOf("cosmeticCurrency", 0L).forGetter(SemionPlayerProfile::cosmeticCurrency)
    ).apply(instance, SemionPlayerProfile::new));

    public SemionPlayerProfile {
        if (lastKnownName == null) {
            lastKnownName = "";
        }
        if (gamesPlayed < 0 || wins < 0 || losses < 0 || cosmeticCurrency < 0) {
            throw new IllegalArgumentException("Profile values cannot be negative.");
        }
    }

    public static SemionPlayerProfile fresh(String playerName) {
        return new SemionPlayerProfile(playerName == null ? "" : playerName, 0, 0, 0, 0);
    }

    public SemionPlayerProfile updateName(String playerName) {
        String normalized = playerName == null ? "" : playerName;
        if (normalized.equals(lastKnownName)) {
            return this;
        }
        return new SemionPlayerProfile(normalized, gamesPlayed, wins, losses, cosmeticCurrency);
    }

    public SemionPlayerProfile recordMatch(String playerName, boolean winner, long reward) {
        String normalized = playerName == null ? "" : playerName;
        return new SemionPlayerProfile(
                normalized,
                gamesPlayed + 1,
                wins + (winner ? 1 : 0),
                losses + (winner ? 0 : 1),
                cosmeticCurrency + Math.max(0L, reward)
        );
    }
}