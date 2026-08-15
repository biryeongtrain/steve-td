package kim.biryeong.semiontd.tower.engineer;

import java.util.List;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.core.Direction;

public final class EngineerTowerCatalogs {
    private static final List<Direction> ROTATION = List.of(
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    );

    private EngineerTowerCatalogs() {
    }

    public static void register() {
        registerStarter(EngineerTowers.COPPER_GOLEM, EngineerGolemTower::new);
        registerStarter(EngineerTowers.REDSTONE_DUST, EngineerCircuitTower::new);
        EngineerTowers.repeaters().values().forEach(type -> register(type, EngineerCircuitTower::new, 2));
        for (EngineerTowers.PlateKind kind : EngineerTowers.PlateKind.values()) {
            TowerType type = EngineerTowers.plate(kind);
            if (kind == EngineerTowers.PlateKind.WOOD) {
                registerStarter(type, EngineerCircuitTower::new);
            } else {
                register(type, EngineerCircuitTower::new, kind.ordinal() + 1);
            }
        }
        EngineerTowers.traps().forEach((kind, types) -> {
            registerStarter(types.get(0), EngineerTrapTower::new);
            register(types.get(1), EngineerTrapTower::new, 2);
            register(types.get(2), EngineerTrapTower::new, 3);
        });

        for (Direction direction : ROTATION) {
            TowerType target = resolved(EngineerTowers.repeater(direction));
            ProductionTowerCatalog.linkUpgrade(
                    resolved(EngineerTowers.REDSTONE_DUST),
                    target.id(),
                    "중계기 · " + target.displayName(),
                    target,
                    TowerBalanceRuntime.upgradeCost(EngineerTowers.REDSTONE_DUST, target.id())
            );
        }
        for (int index = 0; index < ROTATION.size(); index++) {
            TowerType fromDefaults = EngineerTowers.repeater(ROTATION.get(index));
            TowerType to = resolved(EngineerTowers.repeater(ROTATION.get((index + 1) % ROTATION.size())));
            ProductionTowerCatalog.linkUpgrade(
                    resolved(fromDefaults),
                    to.id(),
                    "90도 회전",
                    to,
                    TowerBalanceRuntime.upgradeCost(fromDefaults, to.id())
            );
        }
        for (EngineerTowers.PlateKind kind : EngineerTowers.PlateKind.values()) {
            kind.next().ifPresent(next -> {
                TowerType fromDefaults = EngineerTowers.plate(kind);
                TowerType to = resolved(EngineerTowers.plate(next));
                ProductionTowerCatalog.linkUpgrade(
                        resolved(fromDefaults), to.id(), "강화", to,
                        TowerBalanceRuntime.upgradeCost(fromDefaults, to.id())
                );
            });
        }
        EngineerTowers.traps().forEach((kind, types) -> {
            for (int index = 0; index < 2; index++) {
                TowerType fromDefaults = types.get(index);
                TowerType to = resolved(types.get(index + 1));
                ProductionTowerCatalog.linkUpgrade(
                        resolved(fromDefaults),
                        to.id(),
                        "강화",
                        to,
                        TowerBalanceRuntime.upgradeCost(fromDefaults, to.id())
                );
            }
        });
    }

    private static void registerStarter(TowerType defaults, ProductionTowerCatalog.TowerFactory factory) {
        ProductionTowerCatalog.registerStarter(TowerBalanceRuntime.resolve(defaults), factory);
    }

    private static void register(TowerType defaults, ProductionTowerCatalog.TowerFactory factory, int tier) {
        ProductionTowerCatalog.register(TowerBalanceRuntime.resolve(defaults), factory, tier);
    }

    private static TowerType resolved(TowerType defaults) {
        return ProductionTowerCatalog.find(defaults.id())
                .map(ProductionTowerCatalog.CatalogEntry::type)
                .orElse(defaults);
    }
}
