package kim.biryeong.semiontd.tower.hero;

import com.mojang.authlib.GameProfile;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.progression.HeroCompanionSkinPreference;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

public final class HeroCompanionSkinInputGui extends AnvilInputGui {
    private static final long LOOKUP_TIMEOUT_SECONDS = 10L;

    private final ServerPlayer player;
    private final SemionGameManager gameManager;
    private final HeroCompanionRole role;
    private final SearchState searchState = new SearchState();
    private GameProfile preview;
    private String status;

    public HeroCompanionSkinInputGui(
            ServerPlayer player,
            SemionGameManager gameManager,
            HeroCompanionRole role
    ) {
        super(player, false);
        this.player = player;
        this.gameManager = gameManager;
        this.role = role;
        setTitle(Component.literal(role.displayName() + " 스킨 검색"));
        setLockPlayerInventory(true);
        setDefaultInputValue(HeroCompanionSkins.preference(player.getUUID(), role)
                .map(HeroCompanionSkinPreference::sourceName)
                .orElse(""));
        setSlot(1, new GuiElementBuilder(Items.ARROW)
                .setName(Component.literal("목록으로 돌아가기").withStyle(ChatFormatting.YELLOW))
                .setCallback((slot, type, action) -> new HeroCompanionSkinGui(player, gameManager).open()));
        refreshAction();
    }

    @Override
    public void onInput(String input) {
        searchState.inputChanged();
        preview = null;
        status = null;
        refreshAction();
    }

    private void refreshAction() {
        if (searchState.searching()) {
            setSlot(2, new GuiElementBuilder(Items.CLOCK)
                    .setName(Component.literal("검색 중...").withStyle(ChatFormatting.YELLOW))
                    .addLoreLine(Component.literal("잠시 기다려 주세요.").withStyle(ChatFormatting.GRAY)));
            return;
        }
        if (preview != null) {
            setSlot(2, new GuiElementBuilder(Items.PLAYER_HEAD)
                    .setSkullOwner(preview, player.getServer())
                    .setName(Component.literal(preview.getName() + " 스킨 적용")
                            .withStyle(ChatFormatting.GREEN))
                    .addLoreLine(Component.literal("검색 결과를 확인했습니다.").withStyle(ChatFormatting.GRAY))
                    .addLoreLine(Component.literal("클릭: 계정에 저장").withStyle(ChatFormatting.AQUA))
                    .glow()
                    .setCallback((slot, type, action) -> applyPreview()));
            return;
        }
        GuiElementBuilder searchButton = new GuiElementBuilder(status == null ? Items.LIME_DYE : Items.BARRIER)
                .setName(Component.literal(status == null ? "이름으로 검색" : status)
                        .withStyle(status == null ? ChatFormatting.GREEN : ChatFormatting.RED))
                .addLoreLine(Component.literal("정확한 Minecraft 플레이어 이름을 입력하세요.")
                        .withStyle(ChatFormatting.GRAY))
                .setCallback((slot, type, action) -> beginSearch());
        setSlot(2, searchButton);
    }

    private void beginSearch() {
        String query = getInput() == null ? "" : getInput().trim();
        if (!validPlayerName(query)) {
            status = "올바르지 않은 플레이어 이름";
            refreshAction();
            return;
        }
        long requestRevision = searchState.begin();
        if (requestRevision < 0) {
            return;
        }
        preview = null;
        status = null;
        refreshAction();
        MinecraftServer server = player.getServer();
        SkullBlockEntity.fetchGameProfile(query)
                .thenApply(result -> result)
                .orTimeout(LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((result, error) -> server.execute(() ->
                        completeSearch(requestRevision, query, result, error)));
    }

    private void completeSearch(
            long requestRevision,
            String query,
            Optional<GameProfile> result,
            Throwable error
    ) {
        if (!isOpen()) {
            return;
        }
        if (!searchState.accepts(requestRevision, query, getInput())) {
            searchState.finish(requestRevision);
            refreshAction();
            return;
        }
        searchState.finish(requestRevision);
        if (error != null) {
            Throwable cause = error instanceof CompletionException && error.getCause() != null
                    ? error.getCause()
                    : error;
            status = cause instanceof java.util.concurrent.TimeoutException
                    ? "검색 시간이 초과되었습니다"
                    : "스킨 조회에 실패했습니다";
            refreshAction();
            return;
        }
        preview = result == null ? null : result.orElse(null);
        if (HeroCompanionSkinPreference.fromProfile(preview).isEmpty()) {
            preview = null;
            status = "해당 플레이어를 찾지 못했습니다";
        }
        refreshAction();
    }

    private void applyPreview() {
        HeroCompanionSkinPreference skin = HeroCompanionSkinPreference.fromProfile(preview).orElse(null);
        if (skin == null) {
            status = "적용할 검색 결과가 없습니다";
            refreshAction();
            return;
        }
        if (!gameManager.saveHeroCompanionSkin(
                player.getServer(),
                player.getUUID(),
                player.getGameProfile().getName(),
                role,
                skin
        )) {
            status = "스킨 설정을 저장하지 못했습니다";
            refreshAction();
            return;
        }
        player.sendSystemMessage(Component.literal(role.displayName() + " 스킨을 " + skin.sourceName() + "(으)로 설정했습니다.")
                .withStyle(ChatFormatting.GREEN));
        new HeroCompanionSkinGui(player, gameManager).open();
    }

    static boolean validPlayerName(String value) {
        return value != null && value.matches("[A-Za-z0-9_]{1,16}");
    }

    static final class SearchState {
        private long revision;
        private boolean searching;

        void inputChanged() {
            revision++;
            searching = false;
        }

        long begin() {
            if (searching) {
                return -1L;
            }
            searching = true;
            return revision;
        }

        boolean accepts(long requestRevision, String submitted, String currentInput) {
            return searching
                    && requestRevision == revision
                    && submitted != null
                    && submitted.equals(currentInput == null ? "" : currentInput.trim());
        }

        void finish(long requestRevision) {
            if (requestRevision == revision) {
                searching = false;
            }
        }

        boolean searching() {
            return searching;
        }
    }
}
