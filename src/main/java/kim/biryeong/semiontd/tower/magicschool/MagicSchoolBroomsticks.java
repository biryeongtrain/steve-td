package kim.biryeong.semiontd.tower.magicschool;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.game.TeamLaneGroup;
import kim.biryeong.semiontd.job.DemonLordTowerJob;
import kim.biryeong.semiontd.job.MagicSchoolTowerJob;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import net.minecraft.core.BlockPos;

public final class MagicSchoolBroomsticks {
    private MagicSchoolBroomsticks() {}

    public static void beforeFinalDefense(TeamLaneGroup team, PlayerLane departing, Map<UUID, SemionPlayer> players) {
        if (departing.towersMovedToFinalDefense()) return;
        boolean sendingWizards = isSchool(departing, players)
                && departing.augmentSnapshot().has(MagicSchoolAugments.BROOMSTICK);
        if (!sendingWizards && !eligiblePartner(departing, players)) return;
        for (PlayerLane recipient : team.lanes().stream().sorted(Comparator.comparingInt(PlayerLane::laneId)).toList()) {
            if (recipient == departing || !recipient.hasLaneCombatRemaining()) continue;
            boolean eligible = sendingWizards ? eligiblePartner(recipient, players)
                    : isSchool(recipient, players) && recipient.augmentSnapshot().has(MagicSchoolAugments.BROOMSTICK);
            if (!eligible) continue;
            departing.towers().stream()
                    .filter(tower -> tower instanceof EntityBackedTower && tower.participatesInFinalDefense()
                            && tower.countsForLaneDefense() && !tower.isTemporaryCopy()
                            && tower.reinforcementLane() == null && !tower.deployedAtFinalDefense()
                            && !tower.isDestroyed(departing)
                            && (!sendingWizards || tower instanceof MagicSchoolWizardTower))
                    .sorted(Comparator.comparingDouble(MagicSchoolBroomsticks::maximumHealth).reversed()
                            .thenComparing(Comparator.comparingDouble(Tower::health).reversed())
                            .thenComparing(Tower::logicalId))
                    .findFirst().ifPresent(tower -> tower.reinforceLane(recipient, arrivalPosition(recipient)));
        }
    }

    private static double maximumHealth(Tower tower) {
        return ((EntityBackedTower) tower).runtimeEntity(tower.attachedLane())
                .map(entity -> (double) entity.getMaxHealth()).orElse(tower.currentMaxHealth());
    }

    private static boolean isSchool(PlayerLane lane, Map<UUID, SemionPlayer> players) {
        SemionPlayer player = players.get(lane.ownerPlayer());
        return player != null && player.job().map(job -> job.id().equals(MagicSchoolTowerJob.ID)).orElse(false);
    }

    private static boolean eligiblePartner(PlayerLane lane, Map<UUID, SemionPlayer> players) {
        SemionPlayer player = players.get(lane.ownerPlayer());
        return player != null && player.job().map(job -> !job.id().equals(MagicSchoolTowerJob.ID)
                && !job.id().equals(DemonLordTowerJob.ID)).orElse(false);
    }

    private static GridPosition arrivalPosition(PlayerLane lane) {
        GridPosition anchor = lane.towers().stream().filter(tower -> tower.health() > 0 && tower.countsForLaneDefense())
                .findFirst().map(Tower::position)
                .orElseGet(() -> GridPosition.from(BlockPos.containing(lane.laneLayout().positionAt(.3))));
        for (int radius = 1; radius <= 3; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                    GridPosition candidate = new GridPosition(anchor.x() + x, anchor.y(), anchor.z() + z);
                    if (lane.canPlaceTowerAt(new BlockPos(candidate.x(), candidate.y(), candidate.z()))
                            && !lane.hasTowerAt(candidate)
                            && lane.reinforcingTowers().stream().noneMatch(tower -> tower.position().equals(candidate))) return candidate;
                }
            }
        }
        return anchor;
    }
}
