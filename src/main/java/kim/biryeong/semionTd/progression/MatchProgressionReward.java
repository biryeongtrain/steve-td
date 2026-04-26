package kim.biryeong.semionTd.progression;

public record MatchProgressionReward(
        boolean winner,
        long currencyAwarded,
        SemionPlayerProfile profile
) {
}