package kim.biryeong.semiontd.entity.defender;

import kim.biryeong.semiontd.game.TeamId;
import java.util.UUID;

public final class DefenderEntity {
    private final UUID ownerPlayer;
    private final String sourceTowerId;
    private final TeamId teamId;
    private int currentLaneId;
    private DefenderEntityState state = DefenderEntityState.DEFENDING_LANE;
    private double health;
    private final double attackDamage;
    private final boolean persistent;

    public DefenderEntity(
            UUID ownerPlayer,
            String sourceTowerId,
            TeamId teamId,
            int currentLaneId,
            double health,
            double attackDamage,
            boolean persistent
    ) {
        this.ownerPlayer = ownerPlayer;
        this.sourceTowerId = sourceTowerId;
        this.teamId = teamId;
        this.currentLaneId = currentLaneId;
        this.health = health;
        this.attackDamage = attackDamage;
        this.persistent = persistent;
    }

    public UUID ownerPlayer() {
        return ownerPlayer;
    }

    public String sourceTowerId() {
        return sourceTowerId;
    }

    public TeamId teamId() {
        return teamId;
    }

    public int currentLaneId() {
        return currentLaneId;
    }

    public DefenderEntityState state() {
        return state;
    }

    public double attackDamage() {
        return attackDamage;
    }

    public boolean persistent() {
        return persistent;
    }

    public double health() {
        return health;
    }

    public void moveToFinalDefense() {
        state = DefenderEntityState.MOVING_TO_FINAL_DEFENSE;
        currentLaneId = 0;
    }

    public void arriveFinalDefense() {
        if (state == DefenderEntityState.MOVING_TO_FINAL_DEFENSE) {
            state = DefenderEntityState.DEFENDING_FINAL_LINE;
        }
    }

    public void damage(double amount) {
        if (state == DefenderEntityState.DEAD || state == DefenderEntityState.REMOVED || amount <= 0) {
            return;
        }
        health = Math.max(0, health - amount);
        if (health <= 0) {
            state = DefenderEntityState.DEAD;
        }
    }

    public void remove() {
        state = DefenderEntityState.REMOVED;
        health = 0;
    }
}
