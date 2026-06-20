package kim.biryeong.semiontd.tower.illager;

import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.game.GridPosition;

public record IllagerMark(
        UUID ownerPlayer,
        double damageTakenBonus,
        int expiresAtMonsterTick,
        GridPosition forceTargetCenter,
        double forceTargetRadius
) {
    public boolean activeFor(Monster monster, UUID playerId) {
        return monster != null
                && ownerPlayer != null
                && ownerPlayer.equals(playerId)
                && monster.activeTicks() <= expiresAtMonsterTick;
    }

    public boolean forcesTargetFor(GridPosition towerPosition) {
        if (towerPosition == null || forceTargetCenter == null || forceTargetRadius <= 0) {
            return false;
        }
        double dx = towerPosition.x() - forceTargetCenter.x();
        double dy = towerPosition.y() - forceTargetCenter.y();
        double dz = towerPosition.z() - forceTargetCenter.z();
        return dx * dx + dy * dy + dz * dz <= forceTargetRadius * forceTargetRadius;
    }
}
