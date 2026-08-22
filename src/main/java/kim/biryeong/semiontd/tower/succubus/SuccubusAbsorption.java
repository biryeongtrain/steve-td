package kim.biryeong.semiontd.tower.succubus;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.PlayerLane;

public final class SuccubusAbsorption {
    private static final Map<UUID, State> STATES = new HashMap<>();

    private SuccubusAbsorption() {
    }

    static boolean absorb(SuccubusTower tower, SemionTowerEntity entity,
                          SemionMonsterEntity target, PlayerLane lane) {
        if (tower == null || entity == null || !entity.isAlive() || target == null
                || target.runtimeMonster() == null || lane == null
                || !SuccubusTowers.isSuccubus(tower.type())) return false;
        double attack = Math.max(0.0, target.runtimeMonster().attackDamage()) * SuccubusBalance.absorbAttackRatio();
        double health = Math.max(0.0, target.runtimeMonster().maxHealth()) * SuccubusBalance.absorbMaxHealthRatio();
        State state = STATES.computeIfAbsent(tower.ownerPlayer(), ignored -> new State());
        state.kills++;
        state.attack += attack;
        state.health += health;
        tower.syncHealth(tower.health() + health);
        tower.onStateChanged(lane);
        SuccubusVfx.showAbsorption(entity, target.position());
        return true;
    }

    public static int kills(UUID owner) {
        State state = STATES.get(owner);
        return state == null ? 0 : state.kills;
    }

    public static double attack(UUID owner) {
        State state = STATES.get(owner);
        return state == null ? 0.0 : state.attack;
    }

    public static double health(UUID owner) {
        State state = STATES.get(owner);
        return state == null ? 0.0 : state.health;
    }

    public static void clear(UUID owner) {
        if (owner != null) STATES.remove(owner);
    }

    private static final class State {
        private int kills;
        private double attack;
        private double health;
    }
}
