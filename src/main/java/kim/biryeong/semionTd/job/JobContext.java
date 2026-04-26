package kim.biryeong.semionTd.job;

import java.util.Objects;
import kim.biryeong.semionTd.game.SemionGame;
import kim.biryeong.semionTd.game.SemionPlayer;

public record JobContext(
        SemionGame game,
        SemionPlayer player
) {
    public JobContext {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(player, "player");
    }
}
