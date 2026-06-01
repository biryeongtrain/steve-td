package kim.biryeong.semiontd.game;

public enum TeamSizeBalancePolicy {
    ALLOW_UNEVEN_WITH_SIZE_NORMALIZATION,
    PREFER_EQUAL_SIZE_FOR_RATED_MATCHES;

    public static TeamSizeBalancePolicy defaultPolicy() {
        return ALLOW_UNEVEN_WITH_SIZE_NORMALIZATION;
    }
}
