package kim.biryeong.semiontd.progression;

public record MatchProgressionReward(
        boolean winner,
        long currencyAwarded,
        SemionPlayerProfile profile
) {
}