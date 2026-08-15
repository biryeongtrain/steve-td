package kim.biryeong.semiontd.tower.mage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.Tower;

final class MageTowerRuntime {
    private MageTowerRuntime() {
    }

    static boolean hasCore(PlayerLane lane, UUID owner) {
        return lane != null && lane.towers().stream()
                .anyMatch(tower -> owner.equals(tower.ownerPlayer())
                        && MageTowers.isCore(tower.type())
                        && !tower.isDestroyed(lane));
    }

    static List<SemionMonsterEntity> liveMonsters(PlayerLane lane) {
        if (lane == null || lane.arenaWorld() == null) {
            return List.of();
        }
        return lane.activeMonsters().stream()
                .filter(monster -> monster != null && monster.hasMinecraftEntity() && monster.health() > 0.0)
                .map(monster -> lane.arenaWorld().getEntity(monster.minecraftEntityId()))
                .filter(SemionMonsterEntity.class::isInstance)
                .map(SemionMonsterEntity.class::cast)
                .filter(entity -> entity.isAlive() && !entity.isRemoved() && entity.runtimeMonster() != null)
                .toList();
    }

    static List<SemionMonsterEntity> prioritizedMonsters(PlayerLane lane) {
        return liveMonsters(lane).stream()
                .sorted(Comparator
                        .comparing((SemionMonsterEntity entity) -> isIncome(entity.runtimeMonster()))
                        .thenComparingDouble(entity -> -entity.runtimeMonster().laneProgress()))
                .toList();
    }

    static boolean isIncome(Monster monster) {
        return monster != null && (monster.ownerPlayer().isPresent() || monster.senderTeam().isPresent());
    }

    static SemionTowerEntity entity(PlayerLane lane, Tower tower) {
        if (lane == null || lane.arenaWorld() == null || !(tower instanceof EntityBackedTower backed)) {
            return null;
        }
        return backed.entityId().stream()
                .mapToObj(lane.arenaWorld()::getEntity)
                .filter(SemionTowerEntity.class::isInstance)
                .map(SemionTowerEntity.class::cast)
                .filter(entity -> entity.isAlive() && !entity.isRemoved())
                .findFirst()
                .orElse(null);
    }

    static List<MageWizardTower> nearbyWizards(PlayerLane lane, MageWizardTower source, double radius) {
        if (lane == null || source == null) {
            return List.of();
        }
        double radiusSqr = radius * radius;
        return lane.towers().stream()
                .filter(MageWizardTower.class::isInstance)
                .map(MageWizardTower.class::cast)
                .filter(tower -> source.ownerPlayer().equals(tower.ownerPlayer()))
                .filter(tower -> tower.health() > 0.0)
                .filter(tower -> distanceSqr(source, tower) <= radiusSqr)
                .toList();
    }

    static Set<UUID> ids(List<SemionMonsterEntity> monsters) {
        return monsters.stream().map(SemionMonsterEntity::getUUID).collect(Collectors.toUnmodifiableSet());
    }

    static void restoreTemporaryTowers(PlayerLane lane, UUID owner) {
        if (lane == null) {
            return;
        }
        for (Tower old : new ArrayList<>(lane.towers())) {
            if (!owner.equals(old.ownerPlayer()) || !MageTowers.isTemporary(old.type())) {
                continue;
            }
            var baseType = MageTowers.isWizard(old.type()) ? MageTowers.WIZARD : MageTowers.PROPHET;
            Optional<ProductionTowerCatalog.CatalogEntry> entry = ProductionTowerCatalog.find(baseType.id());
            if (entry.isEmpty()) {
                continue;
            }
            Tower replacement = entry.get().create(
                    old.ownerPlayer(), old.teamId(), old.laneId(), old.originalPosition(), old.position()
            );
            replacement.copyFrom(old, 0);
            lane.replaceTower(old, replacement);
        }
    }

    static void cancelReservations(PlayerLane lane, UUID owner) {
        if (lane == null) {
            return;
        }
        for (Tower old : new ArrayList<>(lane.towers())) {
            if (!owner.equals(old.ownerPlayer()) || (!MageTowers.isWizard(old.type()) && !MageTowers.isProphet(old.type()))) {
                continue;
            }
            var baseType = MageTowers.isWizard(old.type()) ? MageTowers.WIZARD : MageTowers.PROPHET;
            Optional<ProductionTowerCatalog.CatalogEntry> entry = ProductionTowerCatalog.find(baseType.id());
            if (entry.isEmpty()) {
                continue;
            }
            Tower replacement = entry.get().create(
                    old.ownerPlayer(), old.teamId(), old.laneId(), old.originalPosition(), old.position()
            );
            replacement.copyFrom(old, 0);
            lane.replaceTower(old, replacement);
        }
    }

    private static double distanceSqr(Tower first, Tower second) {
        double dx = first.position().x() - second.position().x();
        double dy = first.position().y() - second.position().y();
        double dz = first.position().z() - second.position().z();
        return dx * dx + dy * dy + dz * dz;
    }
}
