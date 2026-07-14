package kim.biryeong.semiontd.cosmetic;

import java.nio.file.Path;
import java.util.List;
import kim.biryeong.semiontd.game.SemionGameManager;
import kim.biryeong.semiontd.progression.ProgressionService.CosmeticUpdateResult;
import kim.biryeong.semiontd.progression.SemionPlayerProfile;
import kim.biryeong.semiontd.ui.SemionText;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
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
            if (world.isClientSide() || !CosmeticItemSupport.isCosmetic(player.getItemBySlot(EquipmentSlot.HEAD))) {
                return InteractionResult.PASS;
            }
            ItemStack held = player.getItemInHand(hand);
            Equippable equippable = held.get(DataComponents.EQUIPPABLE);
            return equippable != null && equippable.slot() == EquipmentSlot.HEAD
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
        return gameManager.profile(player.getServer(), player.getUUID(), player.getGameProfile().getName());
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
                player.getGameProfile().getName(),
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
        boolean selected = profile.selectedCosmeticId().equals(entry.id());
        if (!selected) {
            ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
            if (!head.isEmpty() && !CosmeticItemSupport.isCosmetic(head)) {
                player.sendSystemMessage(SemionText.prefixedError("머리 슬롯의 아이템을 먼저 치워야 합니다."));
                return;
            }
        }

        String selection = selected ? "" : entry.id();
        CosmeticUpdateResult result = gameManager.selectCosmetic(
                player.getUUID(),
                player.getGameProfile().getName(),
                selection
        );
        if (result != CosmeticUpdateResult.SUCCESS) {
            player.sendSystemMessage(SemionText.prefixedError(result == CosmeticUpdateResult.PERSISTENCE_FAILED
                    ? "착용 정보를 저장하지 못했습니다."
                    : "보유하지 않은 치장 아이템입니다."));
            return;
        }

        if (selected) {
            removeEquipped(player, entry.id());
            player.sendSystemMessage(SemionText.prefixedPlain("치장 아이템을 해제했습니다."));
        } else {
            player.setItemSlot(EquipmentSlot.HEAD, CosmeticItemSupport.equippedCopy(entry));
            player.sendSystemMessage(SemionText.prefixedPlain("치장 아이템을 착용했습니다."));
        }
    }

    public void syncPlayer(ServerPlayer player) {
        if (!catalog.available()) {
            return;
        }
        SemionPlayerProfile profile = profile(player);
        String selectedId = profile.selectedCosmeticId();
        if (selectedId.isBlank()) {
            if (CosmeticItemSupport.isCosmetic(player.getItemBySlot(EquipmentSlot.HEAD))) {
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            }
            return;
        }

        CosmeticCatalog.Entry entry = catalog.find(selectedId).orElse(null);
        if (entry == null || !profile.ownsCosmetic(selectedId)) {
            if (gameManager.selectCosmetic(player.getUUID(), player.getGameProfile().getName(), "")
                    == CosmeticUpdateResult.SUCCESS) {
                removeEquipped(player, selectedId);
            }
            return;
        }

        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (head.isEmpty() || CosmeticItemSupport.isCosmetic(head)) {
            player.setItemSlot(EquipmentSlot.HEAD, CosmeticItemSupport.equippedCopy(entry));
        }
    }

    public CosmeticCatalog.MutationResult add(MinecraftServer server, String id, long price, ItemStack item) {
        return catalog.add(server.registryAccess(), id, price, item);
    }

    public CosmeticCatalog.MutationResult update(MinecraftServer server, String id, long price, ItemStack item) {
        CosmeticCatalog.MutationResult result = catalog.update(server.registryAccess(), id, price, item);
        if (result == CosmeticCatalog.MutationResult.SUCCESS) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (profile(player).selectedCosmeticId().equals(id)) {
                    syncPlayer(player);
                }
            }
        }
        return result;
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

    private static void removeEquipped(ServerPlayer player, String id) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (CosmeticItemSupport.cosmeticId(head).equals(id)) {
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        }
    }

    public record RemoveResult(CosmeticCatalog.MutationResult catalogResult, boolean profilesSaved) {
    }
}
