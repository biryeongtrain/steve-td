package kim.biryeong.semiontd.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import kim.biryeong.semiontd.game.TeamMoneyTransferResult;
import kim.biryeong.semiontd.game.TeamMoneyTransferResultType;
import org.junit.jupiter.api.Test;

final class SemionCommandsTeamMoneyFeedbackTest {
    @Test
    void pirateRestrictionAndInsufficientFundsHaveDifferentMessages() {
        assertEquals("해적 빌더는 팀원 간 다이아 지원을 요청하거나 보내거나 받을 수 없습니다.",
                SemionCommands.teamMoneyFailureMessage(TeamMoneyTransferResult.failure(TeamMoneyTransferResultType.BUILDER_RESTRICTED)));
        assertEquals("보낼 다이아가 부족합니다.",
                SemionCommands.teamMoneyFailureMessage(TeamMoneyTransferResult.failure(TeamMoneyTransferResultType.NOT_ENOUGH_DIAMOND)));
    }

    @Test
    void towerPlacementSuccessFeedbackIsConciseAndOmitsPosition() {
        assertEquals("주민 원거리 기본 타워를 소환했습니다", SemionCommands.towerPlacementSuccessMessage("주민 원거리 기본 타워"));
        assertEquals("닭 타워를 소환했습니다", SemionCommands.towerPlacementSuccessMessage("닭"));
    }

    @Test
    void summonSuccessFeedbackUsesIncomeNameAndTargetOwnerInsteadOfTeamAndLane() {
        String message = SemionCommands.summonSuccessMarkup("Chicken", "<blue>blue-owner</blue>", 1, 1);

        assertEquals("Chicken 이(가) <blue>blue-owner</blue> 의 라인으로 공격합니다!", message);
        assertFalse(message.contains("팀="));
        assertFalse(message.contains("라인="));
    }

    @Test
    void reservedSummonFallbackFeedbackUsesSameAttackMessage() {
        String message = SemionCommands.summonSuccessMarkup("Chicken", "<blue>blue-owner</blue>", 1, 2);

        assertEquals("Chicken 이(가) <blue>blue-owner</blue> 의 라인으로 공격합니다!", message);
        assertFalse(message.contains("팀="));
        assertFalse(message.contains("라인="));
        assertFalse(message.contains("예약"));
    }

    @Test
    void requestSuccessFeedbackDoesNotExposeInternalRequestIdOrNotifyCount() {
        String message = SemionCommands.teamMoneyRequestSuccessMessage(30);

        assertEquals("다이아 30개 지원 요청을 보냈습니다.", message);
        assertFalse(message.contains("ID"));
        assertFalse(message.contains("알림"));
    }
}
