package kim.biryeong.semiontd.tower.blueprint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.pirate.PirateTowers;

/**
 * 설계도가 고를 수 있는 겉모습: 지금 게임에 있는 빌더 타워의 겉모습 전부(바닐라 몹, 전용 모델, 블록).
 *
 * <p>겉모습은 "어느 타워의 모습을 빌려 오는지"로 고릅니다. 같은 겉모습은 한 번만 나옵니다. 모두 이미 게임에 있는
 * 겉모습이라 서버 시작 때 미리 데워 둔 것을 그대로 씁니다. 해적 타워는 가짜 플레이어로 그려지는데 그 방식이 해적
 * 타워 id에 묶여 있어서 빌려 올 수 없고, 증강·다른 설계도의 겉모습도 목록에서 뺍니다.
 */
public final class BlueprintVisuals {
    private BlueprintVisuals() {
    }

    /** @param sourceTowerId 겉모습을 빌려 오는 타워 id, sourceName 그 타워 이름 */
    public record Option(String sourceTowerId, String sourceName, EntityVisual visual) {
    }

    public static List<Option> options() {
        Map<EntityVisual, Option> distinct = new LinkedHashMap<>();
        for (ProductionTowerCatalog.CatalogEntry entry : ProductionTowerCatalog.all()) {
            TowerType type = entry.type();
            if (borrowable(entry)) {
                distinct.putIfAbsent(type.visual(), new Option(type.id(), type.displayName(), type.visual()));
            }
        }
        return new ArrayList<>(distinct.values());
    }

    public static Optional<Option> find(String sourceTowerId) {
        return ProductionTowerCatalog.find(sourceTowerId)
                .filter(BlueprintVisuals::borrowable)
                .map(entry -> new Option(entry.type().id(), entry.type().displayName(), entry.type().visual()));
    }

    /** 편집 창에 보일 아이콘: 블록 겉모습은 그 블록, 전용 모델은 갑옷 거치대, 몹은 스폰 알. */
    public static net.minecraft.world.item.ItemStack icon(EntityVisual visual) {
        if (kim.biryeong.semiontd.entity.visual.BlockDisplayVisual.matches(visual)) {
            var state = kim.biryeong.semiontd.entity.visual.BlockDisplayVisual.blockState(visual);
            if (state != null && state.getBlock().asItem() != net.minecraft.world.item.Items.AIR) {
                return new net.minecraft.world.item.ItemStack(state.getBlock());
            }
            return new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GRASS_BLOCK);
        }
        if (visual == null || visual.blockbenchModel().isPresent()) {
            return new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARMOR_STAND);
        }
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(visual.entityTypeId());
        var type = id == null ? null : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null) {
            return new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EGG);
        }
        return net.minecraft.world.item.SpawnEggItem.byId(type)
                .map(net.minecraft.world.item.ItemStack::new)
                .orElseGet(() -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EGG));
    }

    private static boolean borrowable(ProductionTowerCatalog.CatalogEntry entry) {
        TowerType type = entry.type();
        return entry.availability() == ProductionTowerCatalog.Availability.JOB
                && !BlueprintTowers.isBlueprintTower(type)
                && !PirateTowers.isPirateTower(type)
                && type.visual() != null;
    }
}
