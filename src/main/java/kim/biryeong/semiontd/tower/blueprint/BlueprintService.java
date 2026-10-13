package kim.biryeong.semiontd.tower.blueprint;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.job.BlueprintTowerJob;
import net.minecraft.server.level.ServerPlayer;

/**
 * 설계도 저장·삭제의 한 곳. 명령과 편집 창이 같은 길을 씁니다: 검사 → 계정 목록에 더하기 → 계정에 저장 →
 * 지금 빌더 빌더로 경기 중이면 그 경기에도 바로 넣기.
 */
public final class BlueprintService {
    private BlueprintService() {
    }

    /** @param messages 플레이어에게 보여 줄 줄(성공이면 안내, 실패면 이유) */
    public record Outcome(boolean success, List<String> messages) {
    }

    /** 프로필을 읽어 계정 설계도 목록을 서버에 불러 둡니다. */
    public static void ensureLoaded(ServerPlayer player, SemionGameManager gameManager) {
        gameManager.profile(player.level().getServer(), player.getUUID(), player.getGameProfile().name());
    }

    public static Outcome save(ServerPlayer player, SemionGameManager gameManager, BlueprintDesign design) {
        ensureLoaded(player, gameManager);
        Optional<String> problem = BlueprintLibrary.check(player.getUUID(), design);
        if (problem.isPresent()) {
            return new Outcome(false, List.of(problem.get()));
        }
        List<BlueprintDesign> updated = BlueprintLibrary.add(player.getUUID(), design);
        List<String> messages = new ArrayList<>();
        if (!gameManager.saveBlueprints(player.getUUID(), player.getGameProfile().name(), updated)) {
            messages.add("설계도를 계정에 저장하지 못했습니다. 이번 접속 동안만 남습니다.");
        }
        BlueprintStats stats = design.stats();
        long price = BlueprintPricing.price(stats);
        messages.add("설계도 '" + BlueprintStates.sanitizeName(design.name()) + "'를 저장했습니다. 가격 " + price
                + ", 타워 수 " + BlueprintPricing.slotCost(price) + " (" + updated.size() + "번)");
        activeParticipant(player, gameManager).ifPresent(participant -> {
            BlueprintStates.Creation creation = BlueprintStates.create(participant.uuid(), design.name(), stats,
                    design.visualSourceId());
            messages.add(creation.success() ? "이번 경기에서 바로 세울 수 있습니다." : "이번 경기에는 넣지 못했습니다: " + creation.message());
        });
        return new Outcome(true, messages);
    }

    public static Outcome delete(ServerPlayer player, SemionGameManager gameManager, int index) {
        ensureLoaded(player, gameManager);
        Optional<List<BlueprintDesign>> updated = BlueprintLibrary.remove(player.getUUID(), index);
        if (updated.isEmpty()) {
            return new Outcome(false, List.of((index + 1) + "번 설계도가 없습니다."));
        }
        if (!gameManager.saveBlueprints(player.getUUID(), player.getGameProfile().name(), updated.get())) {
            return new Outcome(false, List.of("계정에 저장하지 못했습니다."));
        }
        return new Outcome(true, List.of((index + 1) + "번 설계도를 지웠습니다. 진행 중인 경기의 설계도는 경기가 끝날 때까지 남습니다."));
    }

    /** 지금 경기에서 빌더 빌더로 뛰는 참가자. */
    public static Optional<SemionPlayer> activeParticipant(ServerPlayer player, SemionGameManager gameManager) {
        Optional<SemionGame> game = gameManager.playableGame(player.getUUID());
        return game.map(value -> value.players().get(player.getUUID()))
                .filter(participant -> participant.job().orElse(null) instanceof BlueprintTowerJob);
    }
}
