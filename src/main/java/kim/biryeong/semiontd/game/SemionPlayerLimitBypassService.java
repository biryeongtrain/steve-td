package kim.biryeong.semiontd.game;

import com.mojang.authlib.GameProfile;

public final class SemionPlayerLimitBypassService {
    private static volatile SemionGameManager gameManager;

    private SemionPlayerLimitBypassService() {
    }

    public static SemionGameManager configure(SemionGameManager manager) {
        SemionGameManager previous = gameManager;
        gameManager = manager;
        return previous;
    }

    public static boolean canBypassPlayerLimit(GameProfile profile) {
        SemionGameManager manager = gameManager;
        return manager != null
                && profile != null
                && profile.getId() != null
                && manager.canBypassPlayerLimit(profile.getId());
    }
}
