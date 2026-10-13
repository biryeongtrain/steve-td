package kim.biryeong.semiontd.tower.magicschool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.magicschool.MagicSchoolCurriculum.Upgrade;

public final class MagicSchoolTransfiguration {
    private static final Map<UUID, Props> PROPS = new HashMap<>();

    private MagicSchoolTransfiguration() { }

    private static final class Props {
        final List<MagicSchoolBarrelTower> barrels = new ArrayList<>();
        final List<MagicSchoolBombBarrelEntity> bombs = new ArrayList<>();
    }

    static void onKill(MagicSchoolWizardTower wizard, SemionTowerEntity source, SemionMonsterEntity target) {
        var lane = wizard.attachedLane();
        if (lane == null || source == null || target == null || target instanceof MagicSchoolBombBarrelEntity
                || wizard.isTemporaryCopy() || target.runtimeMonster() == null || target.runtimeMonster().health() > 0
                || !MagicSchoolCurriculum.beginBarrel(wizard.ownerPlayer(), wizard.currentRound(), source.level().getGameTime())) return;
        Props props = PROPS.computeIfAbsent(wizard.ownerPlayer(), ignored -> new Props());
        props.bombs.removeIf(SemionMonsterEntity::isRemoved);
        props.barrels.removeIf(barrel -> {
            if (barrel.health() > 0 && barrel.attachedLane() != null) return false;
            if (barrel.attachedLane() != null) barrel.attachedLane().removeTower(barrel);
            return true;
        });
        if (MagicSchoolCurriculum.purchased(wizard.ownerPlayer(), Upgrade.EXPLOSIVE_BARRELS)) {
            spawnBomb(props, wizard.combatLane(), target.position());
        } else {
            var barrel = new MagicSchoolBarrelTower(lane, target.position(), wizard.currentRound(), source.deployedAtFinalDefense());
            lane.addTower(barrel);
            if (wizard.reinforcementLane() != null) barrel.reinforceLane(wizard.combatLane(), barrel.position());
            props.barrels.add(barrel);
        }
    }

    static void upgradeBarrels(UUID owner) {
        Props props = PROPS.get(owner);
        if (props == null) return;
        for (var barrel : List.copyOf(props.barrels)) {
            var lane = barrel.attachedLane();
            if (lane == null) continue;
            barrel.runtimeEntity(lane).filter(entity -> entity.isAlive() && !entity.isRemoved())
                    .ifPresent(entity -> spawnBomb(props, barrel.combatLane(), entity.position()));
            lane.removeTower(barrel);
        }
        props.barrels.clear();
    }

    private static void spawnBomb(Props props, kim.biryeong.semiontd.game.PlayerLane lane, net.minecraft.world.phys.Vec3 position) {
        var bomb = new MagicSchoolBombBarrelEntity(lane, position);
        if (lane.arenaWorld().addFreshEntity(bomb)) {
            bomb.runtimeMonster().markMinecraftEntitySpawned(bomb.getId(), bomb.getX(), bomb.getY(), bomb.getZ());
            bomb.attachBarrelVisual();
            props.bombs.add(bomb);
        }
    }

    static void onWizardDeath(PlayerLane lane) {
        Props props = PROPS.get(lane.ownerPlayer());
        if (props == null || props.bombs.isEmpty()) return;
        boolean wizardSurvives = lane.towers().stream()
                .anyMatch(tower -> tower instanceof MagicSchoolWizardTower && !tower.isDestroyed(lane));
        if (wizardSurvives) return;

        props.bombs.forEach(SemionMonsterEntity::discard);
        props.bombs.clear();
    }

    public static void clear(UUID owner) {
        Props props = PROPS.remove(owner);
        if (props == null) return;
        for (var barrel : props.barrels) {
            if (barrel.attachedLane() != null) barrel.attachedLane().removeTower(barrel);
        }
        props.bombs.forEach(SemionMonsterEntity::discard);
    }

    public static void clearCombatLane(PlayerLane lane) {
        for (Props props : PROPS.values()) {
            props.barrels.removeIf(barrel -> {
                if (barrel.combatLane() != lane) return false;
                if (barrel.attachedLane() != null) barrel.attachedLane().removeTower(barrel);
                return true;
            });
            props.bombs.removeIf(bomb -> {
                if (bomb.level() != lane.arenaWorld() || bomb.runtimeMonster().targetTeam() != lane.teamId()
                        || bomb.runtimeMonster().targetLaneId() != lane.laneId()) return false;
                bomb.discard();
                return true;
            });
        }
    }
}
