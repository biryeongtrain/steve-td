package kim.biryeong.semiontd.cosmetic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.progression.ProgressionService.CosmeticUpdateResult;
import kim.biryeong.semiontd.progression.SemionPlayerProfile;
import kim.biryeong.semiontd.ui.SemionText;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

public final class CosmeticService {
    private final SemionGameManager gameManager;
    private final CosmeticCatalog catalog;

    public CosmeticService(SemionGameManager gameManager, Path catalogPath) {
        this.gameManager = gameManager;
        this.catalog = new CosmeticCatalog(catalogPath);
    }

    public void registerUseProtection() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClientSide()) {
                return InteractionResult.PASS;
            }
            ItemStack held = player.getItemInHand(hand);
            Equippable equippable = held.get(DataComponents.EQUIPPABLE);
            return equippable != null
                    && CosmeticItemSupport.supports(equippable.slot())
                    && CosmeticItemSupport.isCosmetic(player.getItemBySlot(equippable.slot()))
                    ? InteractionResult.FAIL
                    : InteractionResult.PASS;
        });
    }

    public void load(MinecraftServer server) {
        catalog.load(server.registryAccess());
    }

    public boolean reload(MinecraftServer server) {
        if (!catalog.load(server.registryAccess())) {
            return false;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncPlayer(player);
        }
        return true;
    }

    public void openShop(ServerPlayer player) {
        if (!catalog.available()) {
            player.sendSystemMessage(SemionText.prefixedError("치장 상점 데이터를 불러오지 못했습니다."));
            return;
        }
        new CosmeticShopGui(player, this).open();
    }

    public List<CosmeticCatalog.Entry> entries() {
        return catalog.entries();
    }

    public SemionPlayerProfile profile(ServerPlayer player) {
        return gameManager.profile(player.level().getServer(), player.getUUID(), player.getGameProfile().name());
    }

    public void handleCatalogClick(ServerPlayer player, String cosmeticId) {
        CosmeticCatalog.Entry entry = catalog.find(cosmeticId).orElse(null);
        if (entry == null) {
            player.sendSystemMessage(SemionText.prefixedError("더 이상 판매하지 않는 치장 아이템입니다."));
            return;
        }
        SemionPlayerProfile profile = profile(player);
        if (!profile.ownsCosmetic(cosmeticId)) {
            handlePurchase(player, entry);
            return;
        }
        handleToggle(player, entry, profile);
    }

    private void handlePurchase(ServerPlayer player, CosmeticCatalog.Entry entry) {
        CosmeticUpdateResult result = gameManager.purchaseCosmetic(
                player.getUUID(),
                player.getGameProfile().name(),
                entry.id(),
                entry.price()
        );
        switch (result) {
            case SUCCESS -> player.sendSystemMessage(SemionText.prefixedPlain(
                    "치장 아이템을 구매했습니다. 다시 클릭하면 착용합니다."
            ));
            case ALREADY_OWNED -> player.sendSystemMessage(SemionText.prefixedPlain("이미 보유한 치장 아이템입니다."));
            case INSUFFICIENT_FUNDS -> player.sendSystemMessage(SemionText.prefixedError("치장 포인트가 부족합니다."));
            case PERSISTENCE_FAILED -> player.sendSystemMessage(SemionText.prefixedError("구매 정보를 저장하지 못해 결제를 취소했습니다."));
            default -> player.sendSystemMessage(SemionText.prefixedError("치장 아이템을 구매할 수 없습니다."));
        }
    }

    private void handleToggle(ServerPlayer player, CosmeticCatalog.Entry entry, SemionPlayerProfile profile) {
        boolean selected = profile.isCosmeticSelected(entry.id());
        if (!selected) {
            ItemStack equipped = player.getItemBySlot(entry.slot());
            if (!equipped.isEmpty() && !CosmeticItemSupport.isCosmetic(equipped)) {
                player.sendSystemMessage(SemionText.prefixedError(
                        CosmeticItemSupport.slotName(entry.slot()) + " 슬롯의 아이템을 먼저 치워야 합니다."
                ));
                return;
            }
        }

        List<String> selections = new ArrayList<>(profile.selectedCosmeticIds());
        if (selected) {
            selections.remove(entry.id());
        } else {
            selections.removeIf(id -> catalog.find(id).map(existing -> existing.slot() == entry.slot()).orElse(false));
            selections.add(entry.id());
        }
        CosmeticUpdateResult result = gameManager.selectCosmetics(
                player.getUUID(),
                player.getGameProfile().name(),
                selections
        );
        if (result != CosmeticUpdateResult.SUCCESS) {
            player.sendSystemMessage(SemionText.prefixedError(result == CosmeticUpdateResult.PERSISTENCE_FAILED
                    ? "착용 정보를 저장하지 못했습니다."
                    : "보유하지 않은 치장 아이템입니다."));
            return;
        }

        syncPlayer(player);
        player.sendSystemMessage(SemionText.prefixedPlain(selected
                ? "치장 아이템을 해제했습니다."
                : "치장 아이템을 착용했습니다."));
    }

    public void syncPlayer(ServerPlayer player) {
        if (!catalog.available()) {
            return;
        }
        SemionPlayerProfile profile = profile(player);
        List<CosmeticCatalog.Entry> selectedEntries = new ArrayList<>();
        EnumSet<EquipmentSlot> selectedSlots = EnumSet.noneOf(EquipmentSlot.class);
        for (String selectedId : profile.selectedCosmeticIds()) {
            CosmeticCatalog.Entry entry = catalog.find(selectedId).orElse(null);
            if (entry != null && profile.ownsCosmetic(selectedId) && selectedSlots.add(entry.slot())) {
                selectedEntries.add(entry);
            }
        }

        List<String> validIds = selectedEntries.stream().map(CosmeticCatalog.Entry::id).toList();
        if (!validIds.equals(profile.selectedCosmeticIds())
                && gameManager.selectCosmetics(player.getUUID(), player.getGameProfile().name(), validIds)
                != CosmeticUpdateResult.SUCCESS) {
            return;
        }
        for (EquipmentSlot slot : CosmeticItemSupport.supportedSlots()) {
            CosmeticCatalog.Entry selected = selectedEntries.stream()
                    .filter(entry -> entry.slot() == slot)
                    .findFirst()
                    .orElse(null);
            ItemStack equipped = player.getItemBySlot(slot);
            if (selected == null) {
                if (CosmeticItemSupport.isCosmetic(equipped)) {
                    player.setItemSlot(slot, ItemStack.EMPTY);
                }
            } else if (equipped.isEmpty() || CosmeticItemSupport.isCosmetic(equipped)) {
                player.setItemSlot(slot, CosmeticItemSupport.equippedCopy(selected));
            }
        }
    }

    public CosmeticCatalog.MutationResult add(MinecraftServer server, String id, long price, ItemStack item) {
        return catalog.add(server.registryAccess(), id, price, item);
    }

    public CosmeticCatalog.MutationResult add(
            MinecraftServer server,
            String id,
            long price,
            EquipmentSlot slot,
            ItemStack item
    ) {
        return catalog.add(server.registryAccess(), id, price, slot, item);
    }

    public CosmeticCatalog.MutationResult update(MinecraftServer server, String id, long price, ItemStack item) {
        CosmeticCatalog.MutationResult result = catalog.update(server.registryAccess(), id, price, item);
        syncUpdatedEntry(server, id, result);
        return result;
    }

    public CosmeticCatalog.MutationResult update(
            MinecraftServer server,
            String id,
            long price,
            EquipmentSlot slot,
            ItemStack item
    ) {
        CosmeticCatalog.MutationResult result = catalog.update(server.registryAccess(), id, price, slot, item);
        syncUpdatedEntry(server, id, result);
        return result;
    }

    private void syncUpdatedEntry(MinecraftServer server, String id, CosmeticCatalog.MutationResult result) {
        if (result == CosmeticCatalog.MutationResult.SUCCESS) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (profile(player).isCosmeticSelected(id)) {
                    syncPlayer(player);
                }
            }
        }
    }

    public RemoveResult remove(MinecraftServer server, String id) {
        CosmeticCatalog.MutationResult result = catalog.remove(server.registryAccess(), id);
        if (result != CosmeticCatalog.MutationResult.SUCCESS) {
            return new RemoveResult(result, true);
        }
        boolean profilesSaved = gameManager.clearSelectedCosmetic(id);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            removeEquipped(player, id);
        }
        return new RemoveResult(result, profilesSaved);
    }

    private void removeEquipped(ServerPlayer player, String id) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (CosmeticItemSupport.cosmeticId(inventory.getItem(slot)).equals(id)) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        if (CosmeticItemSupport.cosmeticId(player.containerMenu.getCarried()).equals(id)) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }

    public record RemoveResult(CosmeticCatalog.MutationResult catalogResult, boolean profilesSaved) {
    }
}
