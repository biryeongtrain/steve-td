package kim.biryeong.semiontd.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

final class SemionCommandsTeamMoneyFeedbackTest {
    @Test
    void towerPlacementSuccessFeedbackIsConciseAndOmitsPosition() {
        assertEquals("주민 원거리 기본 타워를 소환했습니다", SemionCommands.towerPlacementSuccessMessage("주민 원거리 기본 타워"));
        assertEquals("닭 타워를 소환했습니다", SemionCommands.towerPlacementSuccessMessage("닭"));
    }

    @Test
    void summonSuccessFeedbackUsesTargetOwnerInsteadOfTeamAndLane() {
        String message = SemionCommands.summonSuccessMarkup("chicken", "<blue>blue-owner</blue>", 1, 1);

        assertEquals("소환했습니다: chicken, 대상=<blue>blue-owner</blue>", message);
        assertFalse(message.contains("팀="));
        assertFalse(message.contains("라인="));
    }

    @Test
    void reservedSummonSuccessFeedbackUsesTargetOwnerInsteadOfTeamAndLane() {
        String message = SemionCommands.summonSuccessMarkup("chicken", "<blue>blue-owner</blue>", 1, 2);

        assertEquals("다음 라운드에 소환 예약했습니다: chicken, 대상=<blue>blue-owner</blue>, 라운드=2", message);
        assertFalse(message.contains("팀="));
        assertFalse(message.contains("라인="));
    }

    @Test
    void requestSuccessFeedbackDoesNotExposeInternalRequestIdOrNotifyCount() {
        String message = SemionCommands.teamMoneyRequestSuccessMessage(30);

        assertEquals("다이아 30개 지원 요청을 보냈습니다.", message);
        assertFalse(message.contains("ID"));
        assertFalse(message.contains("알림"));
    }
}
