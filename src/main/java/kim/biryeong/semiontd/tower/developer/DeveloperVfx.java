package kim.biryeong.semiontd.tower.developer;

import java.util.List;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import net.minecraft.resources.ResourceLocation;

public final class DeveloperVfx {
    public enum DebugKind {
        ATTACK,
        PATCH,
        HOTFIX,
        REPRODUCE,
        MAINTENANCE,
        PIN
    }

    private DeveloperVfx() {
    }

    static void show(DeveloperTower tower, ResourceLocation style, String event) {
        SemionTowerEntity entity = tower == null ? null : tower.spawnedEntity();
        if (entity == null) {
            return;
        }
        TowerVfxService.showAreaEffect(
                entity,
                AreaEffectIds.tower(tower, event),
                style,
                entity.position(),
                1.25,
                List.of(entity.position()),
                1,
                1,
                0
        );
    }

    static void reproduce(DeveloperTower source, DeveloperTower target) {
        SemionTowerEntity sourceEntity = source == null ? null : source.spawnedEntity();
        SemionTowerEntity targetEntity = target == null ? null : target.spawnedEntity();
        if (sourceEntity != null && targetEntity != null) {
            TowerVfxService.showSecondaryAttack(sourceEntity, targetEntity.position());
        }
        show(target, kim.biryeong.semiontd.api.area.AreaVfxStyles.DEBUFF, "reproduce");
    }

    public static boolean showDebug(List<DeveloperTower> towers, DebugKind kind) {
        List<DeveloperTower> alive = towers.stream()
                .filter(tower -> tower.spawnedEntity() != null && tower.spawnedEntity().isAlive())
                .toList();
        if (alive.isEmpty() || kind == DebugKind.REPRODUCE && alive.size() < 2) {
            return false;
        }
        DeveloperTower source = alive.getFirst();
        switch (kind) {
            case ATTACK -> TowerVfxService.showSecondaryAttack(
                    source.spawnedEntity(), source.spawnedEntity().position().add(4.0, 0.5, 0.0));
            case PATCH -> show(source, kim.biryeong.semiontd.api.area.AreaVfxStyles.BUFF, "patch_debug");
            case HOTFIX -> show(source, kim.biryeong.semiontd.api.area.AreaVfxStyles.DEBUFF, "hotfix_debug");
            case REPRODUCE -> reproduce(source, alive.get(1));
            case MAINTENANCE -> show(source, kim.biryeong.semiontd.api.area.AreaVfxStyles.BUFF, "maintenance_debug");
            case PIN -> show(source, kim.biryeong.semiontd.api.area.AreaVfxStyles.PULSE, "pin_debug");
        }
        return true;
    }
}
