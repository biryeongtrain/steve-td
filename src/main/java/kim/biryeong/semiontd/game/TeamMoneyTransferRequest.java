package kim.biryeong.semiontd.game;

import java.util.UUID;

public record TeamMoneyTransferRequest(
        String id,
        UUID requesterId,
        TeamId teamId,
        long amount,
        int requestedRound
) {
}
