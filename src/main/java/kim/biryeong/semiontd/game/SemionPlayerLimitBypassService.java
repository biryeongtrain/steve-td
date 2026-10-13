package kim.biryeong.semiontd.game;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.players.NameAndId;

public final class SemionPlayerLimitBypassService {
    private static volatile SemionGameManager gameManager;

    private SemionPlayerLimitBypassService() {
    }

    public static SemionGameManager configure(SemionGameManager manager) {
        SemionGameManager previous = gameManager;
        gameManager = manager;
        return previous;
    }

    public static boolean canBypassPlayerLimitIdentity(NameAndId profile) {
        SemionGameManager manager = gameManager;
        return manager != null
                && profile != null
                && profile.id() != null
                && manager.canBypassPlayerLimit(profile.id());
    }
    public static boolean canBypassPlayerLimit(GameProfile profile) {
        SemionGameManager manager = gameManager;
        return manager != null
                && profile != null
                && profile.id() != null
                && manager.canBypassPlayerLimit(profile.id());
    }
}
