package kim.biryeong.semiontd.game;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record TeamMoneyTransferResult(
        TeamMoneyTransferResultType type,
        Optional<String> requestId,
        Optional<UUID> requesterId,
        Optional<UUID> senderId,
        long amount,
        int remainingCooldownRounds,
        long maxAllowedAmount
) {
    public TeamMoneyTransferResult {
        Objects.requireNonNull(type, "type");
        requestId = requestId == null ? Optional.empty() : requestId;
        requesterId = requesterId == null ? Optional.empty() : requesterId;
        senderId = senderId == null ? Optional.empty() : senderId;
        amount = Math.max(0, amount);
        remainingCooldownRounds = Math.max(0, remainingCooldownRounds);
        maxAllowedAmount = Math.max(0, maxAllowedAmount);
    }

    public static TeamMoneyTransferResult failure(TeamMoneyTransferResultType type) {
        return failure(type, 0, 0);
    }

    public static TeamMoneyTransferResult failure(TeamMoneyTransferResultType type, int remainingCooldownRounds, long maxAllowedAmount) {
        return new TeamMoneyTransferResult(
                type,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0,
                remainingCooldownRounds,
                maxAllowedAmount
        );
    }

    public static TeamMoneyTransferResult requestCreated(TeamMoneyTransferRequest request, long maxAllowedAmount) {
        return new TeamMoneyTransferResult(
                TeamMoneyTransferResultType.SUCCESS,
                Optional.of(request.id()),
                Optional.of(request.requesterId()),
                Optional.empty(),
                request.amount(),
                0,
                maxAllowedAmount
        );
    }

    public static TeamMoneyTransferResult success(TeamMoneyTransferRequest request, UUID senderId) {
        return new TeamMoneyTransferResult(
                TeamMoneyTransferResultType.SUCCESS,
                Optional.of(request.id()),
                Optional.of(request.requesterId()),
                Optional.of(senderId),
                request.amount(),
                0,
                0
        );
    }
}
