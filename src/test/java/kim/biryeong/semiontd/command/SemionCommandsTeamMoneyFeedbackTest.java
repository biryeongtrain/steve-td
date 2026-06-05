package kim.biryeong.semiontd.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

final class SemionCommandsTeamMoneyFeedbackTest {
    @Test
    void requestSuccessFeedbackDoesNotExposeInternalRequestIdOrNotifyCount() {
        String message = SemionCommands.teamMoneyRequestSuccessMessage(30);

        assertEquals("다이아 30개 지원 요청을 보냈습니다.", message);
        assertFalse(message.contains("ID"));
        assertFalse(message.contains("알림"));
    }
}
