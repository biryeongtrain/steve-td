package kim.biryeong.semiontd.ui;

import com.mojang.authlib.GameProfile;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.AnvilInputGui;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import kim.biryeong.semiontd.progression.HeroCompanionSkinPreference;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.util.Util;
import java.util.concurrent.CompletableFuture;

public class PlayerSkinInputGui extends AnvilInputGui {
    private static final long LOOKUP_TIMEOUT_SECONDS = 10L;

    private final ServerPlayer player;
    private final String skinName;
    private final Predicate<HeroCompanionSkinPreference> saveSkin;
    private final Runnable returnToList;
    private final BooleanSupplier canManage;
    private final SearchState searchState = new SearchState();
    private GameProfile preview;
    private String status;

    public PlayerSkinInputGui(ServerPlayer player, String skinName, String initialName,
            Predicate<HeroCompanionSkinPreference> saveSkin, Runnable returnToList, BooleanSupplier canManage) {
        super(player, false);
        this.player = player;
        this.skinName = skinName;
        this.saveSkin = saveSkin;
        this.returnToList = returnToList;
        this.canManage = canManage;
        setTitle(Component.literal(skinName + " 스킨 검색"));
        setLockPlayerInventory(true);
        setDefaultInputValue(initialName);
        setSlot(1, new GuiElementBuilder(Items.ARROW)
                .setName(Component.literal("목록으로 돌아가기").withStyle(ChatFormatting.YELLOW))
                .setCallback((slot, type, action, clickedGui) -> { if (checkAccess()) returnToList.run(); }));
        refreshAction();
    }

    private boolean checkAccess() {
        if (canManage.getAsBoolean()) return true;
        close();
        return false;
    }

    @Override
    public void onTick() {
        super.onTick();
        checkAccess();
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
                    .setProfile(preview)
                    .setName(Component.literal(preview.name() + " 스킨 적용")
                            .withStyle(ChatFormatting.GREEN))
                    .addLoreLine(Component.literal("검색 결과를 확인했습니다.").withStyle(ChatFormatting.GRAY))
                    .addLoreLine(Component.literal("클릭: 계정에 저장").withStyle(ChatFormatting.AQUA))
                    .glow()
                    .setCallback((slot, type, action, clickedGui) -> applyPreview()));
            return;
        }
        GuiElementBuilder searchButton = new GuiElementBuilder(status == null ? Items.DYE.lime() : Items.BARRIER)
                .setName(Component.literal(status == null ? "이름으로 검색" : status)
                        .withStyle(status == null ? ChatFormatting.GREEN : ChatFormatting.RED))
                .addLoreLine(Component.literal("정확한 Minecraft 플레이어 이름을 입력하세요.")
                        .withStyle(ChatFormatting.GRAY))
                .setCallback((slot, type, action, clickedGui) -> beginSearch());
        setSlot(2, searchButton);
    }

    private void beginSearch() {
        if (!checkAccess()) return;
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
        MinecraftServer server = player.level().getServer();
        CompletableFuture.supplyAsync(() -> server.services().profileResolver().fetchByName(query), Util.ioPool())
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
        if (!isOpen() || !checkAccess()) {
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
        if (!checkAccess()) return;
        HeroCompanionSkinPreference skin = HeroCompanionSkinPreference.fromProfile(preview).orElse(null);
        if (skin == null) {
            status = "적용할 검색 결과가 없습니다";
            refreshAction();
            return;
        }
        if (!saveSkin.test(skin)) {
            status = "스킨 설정을 저장하지 못했습니다";
            refreshAction();
            return;
        }
        player.sendSystemMessage(Component.literal(skinName + " 스킨을 " + skin.sourceName() + "(으)로 설정했습니다.")
                .withStyle(ChatFormatting.GREEN));
        returnToList.run();
    }

    public static boolean validPlayerName(String value) {
        return value != null && value.matches("[A-Za-z0-9_]{1,16}");
    }

    public static final class SearchState {
        private long revision;
        private boolean searching;

        public void inputChanged() {
            revision++;
            searching = false;
        }

        public long begin() {
            if (searching) {
                return -1L;
            }
            searching = true;
            return revision;
        }

        public boolean accepts(long requestRevision, String submitted, String currentInput) {
            return searching
                    && requestRevision == revision
                    && submitted != null
                    && submitted.equals(currentInput == null ? "" : currentInput.trim());
        }

        public void finish(long requestRevision) {
            if (requestRevision == revision) {
                searching = false;
            }
        }

        public boolean searching() {
            return searching;
        }
    }
}
