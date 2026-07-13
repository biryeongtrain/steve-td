package kim.biryeong.semiontd.config;

import java.util.List;

public record TipConfig(
        boolean enabled,
        boolean joinEnabled,
        String joinMessage,
        int intervalSeconds,
        List<String> messages
) {
    private static final int MIN_INTERVAL_SECONDS = 1;
    private static final int MAX_INTERVAL_SECONDS = 86_400;

    public TipConfig {
        joinMessage = joinMessage == null ? "" : joinMessage;
        intervalSeconds = Math.max(MIN_INTERVAL_SECONDS, Math.min(MAX_INTERVAL_SECONDS, intervalSeconds));
        messages = messages == null
                ? List.of()
                : messages.stream()
                        .filter(message -> message != null && !message.isBlank())
                        .toList();
    }

    public static TipConfig defaultConfig() {
        return new TipConfig(
                true,
                true,
                "<gold><bold>TIP</bold></gold> <gray>나침반을 우클릭하면 타워 설치 창을 열 수 있습니다.</gray>",
                120,
                List.of(
                        "<gold><bold>TIP</bold></gold> <gray>업그레이드 가격은 대상 타워의 설치 가격과 별도로 설정됩니다.</gray>",
                        "<gold><bold>TIP</bold></gold> <gray>팀원에게 다이아를 요청하려면 <yellow>/요청 &lt;수량&gt;</yellow>을 사용하세요.</gray>",
                        "<gold><bold>TIP</bold></gold> <gray><yellow>/스카이박스</yellow>에서 개인 스카이박스를 선택할 수 있습니다.</gray>",
                        "<gold><bold>TIP</bold></gold> <gray>팁을 끄려면 <yellow>/semiontd tip off</yellow>를 사용하세요.</gray>"
                )
        );
    }

    public int intervalTicks() {
        return intervalSeconds * 20;
    }
}
