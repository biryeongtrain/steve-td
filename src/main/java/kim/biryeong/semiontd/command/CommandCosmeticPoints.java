package kim.biryeong.semiontd.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.progression.ProgressionCurrencyChange;
import kim.biryeong.semiontd.progression.ProgressionCurrencyChange.Operation;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

final class CommandCosmeticPoints {
    private CommandCosmeticPoints() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> all(String name, SemionGameManager manager, Operation operation) {
        return literal(name)
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(argument("amount", LongArgumentType.longArg(1))
                        .executes(context -> changeAll(context.getSource(), manager,
                                LongArgumentType.getLong(context, "amount"), operation)));
    }

    static LiteralArgumentBuilder<CommandSourceStack> take(SemionGameManager manager) {
        return literal("take")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(argument("player", GameProfileArgument.gameProfile())
                        .then(argument("amount", LongArgumentType.longArg(1))
                                .executes(context -> takeOne(context.getSource(), manager,
                                        GameProfileArgument.getGameProfiles(context, "player"),
                                        LongArgumentType.getLong(context, "amount")))));
    }

    private static int takeOne(CommandSourceStack source, SemionGameManager manager, Collection<NameAndId> targets, long amount) {
        if (targets.size() != 1) {
            source.sendFailure(SemionText.prefixedPlain("단일 회수는 정확히 한 플레이어를 지정해야 합니다. 실제 반영 0명 / 0포인트."));
            return 0;
        }
        NameAndId target = targets.iterator().next();
        String name = target.name() == null || target.name().isBlank() ? target.id().toString() : target.name();
        ProgressionCurrencyChange result = manager.changeCosmeticCurrency(Map.of(target.id(), name), amount, Operation.TAKE);
        if (!result.succeeded()) {
            source.sendFailure(SemionText.prefixedPlain(failureReason(result.status()) + " 대상 1명, 실제 반영 0명 / 0포인트."));
            return 0;
        }
        source.sendSuccess(() -> SemionText.prefixedPlain(name + "님에게 치장 포인트 " + result.totalAmount()
                + "포인트를 회수했습니다. 실제 반영 1명 / " + result.totalAmount() + "포인트."), true);
        return 1;
    }

    static Map<UUID, String> onlineTargets(List<ServerPlayer> players) {
        Map<UUID, String> targets = new LinkedHashMap<>();
        for (ServerPlayer player : List.copyOf(players)) {
            targets.putIfAbsent(player.getUUID(), player.getGameProfile().name());
        }
        return targets;
    }

    private static int changeAll(CommandSourceStack source, SemionGameManager manager, long amount, Operation operation) {
        Map<UUID, String> targets = onlineTargets(source.getServer().getPlayerList().getPlayers());
        ProgressionCurrencyChange result = manager.changeCosmeticCurrency(targets, amount, operation);
        if (!result.succeeded()) {
            source.sendFailure(SemionText.prefixedPlain(failureReason(result.status())
                    + " 대상 " + result.targetCount() + "명, 실제 반영 0명 / 0포인트."));
            return 0;
        }
        String verb = operation == Operation.GIVE ? "지급" : "회수";
        String feedback = "현재 접속자 " + result.targetCount() + "명에게 치장 포인트를 1인당 " + amount
                + "씩 " + verb + "했습니다. 실제 " + verb + " 총량: " + result.totalAmount() + "포인트.";
        source.sendSuccess(() -> SemionText.prefixedPlain(feedback), true);
        return result.targetCount();
    }

    private static String failureReason(ProgressionCurrencyChange.Status status) {
        return switch (status) {
            case INVALID_AMOUNT -> "수량은 1 이상의 정수여야 합니다.";
            case INVALID_TARGET -> "대상 정보가 올바르지 않아 전체 변경을 취소했습니다.";
            case NO_TARGETS -> "현재 접속자가 없습니다.";
            case OVERFLOW -> "개인 잔액 또는 총량의 최대치를 초과해 전체 변경을 취소했습니다.";
            case INSUFFICIENT_FUNDS -> "잔액이 부족한 대상이 있어 회수를 취소했습니다.";
            case PERSISTENCE_FAILED -> "보상 데이터를 읽거나 저장하지 못해 전체 변경을 취소했습니다.";
            case SUCCESS -> throw new IllegalArgumentException("Successful changes have no failure reason.");
        };
    }
}
