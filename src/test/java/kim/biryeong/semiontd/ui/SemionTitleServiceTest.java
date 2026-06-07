package kim.biryeong.semiontd.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class SemionTitleServiceTest {
    @Test
    void emeraldIncomeBoostTitleIsRedBoldKoreanNotice() {
        assertEquals(
                "<red><bold>에메랄드 수급량 2배 활성화!</bold></red>",
                SemionTitleService.emeraldIncomeBoostActivatedMarkup()
        );
    }
}
