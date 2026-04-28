package kim.biryeong.semiontd.job;

import java.util.Objects;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;

public record JobContext(
        SemionGame game,
        SemionPlayer player
) {
    public JobContext {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(player, "player");
    }
}
