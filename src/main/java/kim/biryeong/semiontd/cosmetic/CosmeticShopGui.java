package kim.biryeong.semiontd.cosmetic;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.List;
import kim.biryeong.semiontd.progression.SemionPlayerProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public final class CosmeticShopGui extends SimpleGui {
    static final int PAGE_SIZE = 45;

    private final ServerPlayer player;
    private final CosmeticService service;
    private int page;

    public CosmeticShopGui(ServerPlayer player, CosmeticService service) {
        super(MenuType.GENERIC_9x6, player, false);
        this.player = player;
        this.service = service;
        setTitle(Component.literal("치장 아이템 상점"));
        setLockPlayerInventory(true);
        refresh();
    }

    void refresh() {
        List<CosmeticCatalog.Entry> entries = service.entries();
        int lastPage = Math.max(0, (entries.size() - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, lastPage));
        for (int slot = 0; slot < 54; slot++) {
            clearSlot(slot);
        }

        SemionPlayerProfile profile = service.profile(player);
        int start = page * PAGE_SIZE;
        int end = Math.min(entries.size(), start + PAGE_SIZE);
        for (int index = start; index < end; index++) {
            CosmeticCatalog.Entry entry = entries.get(index);
            boolean owned = profile.ownsCosmetic(entry.id());
            boolean selected = profile.selectedCosmeticId().equals(entry.id());
            GuiElementBuilder builder = GuiElementBuilder.from(entry.item())
                    .addLoreLineRaw(Component.literal("가격: " + entry.price() + " 치장 포인트")
                            .withStyle(ChatFormatting.AQUA))
                    .addLoreLineRaw(Component.literal(owned ? "보유 중" : "미보유")
                            .withStyle(owned ? ChatFormatting.GREEN : ChatFormatting.GRAY))
                    .addLoreLineRaw(Component.literal(selected ? "착용 중" : "미착용")
                            .withStyle(selected ? ChatFormatting.YELLOW : ChatFormatting.GRAY))
                    .addLoreLineRaw(Component.literal(selected
                                    ? "클릭: 착용 해제"
                                    : owned ? "클릭: 착용" : "클릭: 구매")
                            .withStyle(selected ? ChatFormatting.YELLOW : ChatFormatting.WHITE))
                    .setCallback((slot, type, action) -> {
                        service.handleCatalogClick(player, entry.id());
                        refresh();
                    });
            setSlot(index - start, builder);
        }

        if (page > 0) {
            setSlot(45, new GuiElementBuilder(Items.ARROW)
                    .setName(Component.literal("이전 페이지"))
                    .setCallback((slot, type, action) -> {
                        page--;
                        refresh();
                    }));
        }
        setSlot(48, new GuiElementBuilder(Items.AMETHYST_SHARD)
                .setName(Component.literal("잔액: " + profile.cosmeticCurrency() + " 치장 포인트")
                        .withStyle(ChatFormatting.AQUA)));
        setSlot(49, new GuiElementBuilder(Items.BARRIER)
                .setName(Component.literal("닫기").withStyle(ChatFormatting.RED))
                .setCallback((slot, type, action) -> close()));
        if (page < lastPage) {
            setSlot(53, new GuiElementBuilder(Items.ARROW)
                    .setName(Component.literal("다음 페이지"))
                    .setCallback((slot, type, action) -> {
                        page++;
                        refresh();
                    }));
        }
    }
}
