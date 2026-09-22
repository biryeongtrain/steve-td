package kim.biryeong.semiontd.tower.warlock;

import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.PlayerLane;

public final class WarlockAwakeningController {
    private final WarlockConfig config;
    private final WarlockState state;
    private final WarlockPath path;
    private final UUID ownerPlayer;
    private int regenerationTicks;
    private int vfxTicks;

    WarlockAwakeningController(WarlockConfig config, WarlockState state, WarlockPath path, UUID ownerPlayer) {
        this.config = config;
        this.state = state;
        this.path = path;
        this.ownerPlayer = ownerPlayer;
    }

    boolean tryActivate(WarlockTower tower, PlayerLane lane, SemionTowerEntity towerEntity) {
        if (towerEntity == null || !path.specialized() || state.awakenedThisRound()) {
            return false;
        }
        WarlockAwakeningProgress.Snapshot progress = WarlockAwakeningProgress.snapshot(ownerPlayer);
        WarlockRules.AwakeningRule rule = config.awakening(path);
        tower.syncFromEntityHealth(towerEntity.getHealth());
        boolean augmented = tower.augmentSnapshot().has(WarlockAugments.AWAKENING)
                && AugmentCombat.allowsTriggers();
        if (!new WarlockRules.AwakeningRule(augmented ? tower.awakeningHealthThreshold() : rule.healthThreshold(), rule.bonus()).canActivate(
                progress.unlocked() || augmented, tower.currentHealthRatio(),
                augmented || tower.isLastSurvivingTower(lane)) || !state.awaken()) {
            return false;
        }

        regenerationTicks = 0;
        vfxTicks = 0;
        towerEntity.setGlowingTag(true);
        TowerVfxService.showWarlockAwakening(towerEntity);
        tower.heal(towerEntity, rule.bonus().healing());
        tower.onStateChanged(lane);
        return true;
    }

    void tick(WarlockTower tower, PlayerLane lane) {
        tickRegeneration(tower, lane);
        tickVfx(tower, lane);
    }

    void resetRound(WarlockTower tower, PlayerLane lane) {
        setGlow(tower, lane, false);
        regenerationTicks = 0;
        vfxTicks = 0;
    }

    boolean awakenedThisRound() {
        return state.awakenedThisRound();
    }

    double regenerationPerSecond() {
        return awakenedThisRound() ? config.awakening(path).bonus().regenerationPerSecond() : 0.0;
    }

    double attackDamageBonus() {
        return awakenedThisRound() ? config.awakening(path).bonus().attackDamage() : 0.0;
    }

    double movementSpeedBonus() {
        return awakenedThisRound() ? config.awakening(path).bonus().movementSpeed() : 0.0;
    }

    private void tickRegeneration(WarlockTower tower, PlayerLane lane) {
        double amount = regenerationPerSecond();
        if (tower.health() <= 0.0 || amount <= 0.0 || tower.health() >= tower.currentMaxHealth()) {
            regenerationTicks = 0;
            return;
        }
        int intervalTicks = config.awakening(path).bonus().regenerationIntervalTicks();
        regenerationTicks++;
        if (regenerationTicks < intervalTicks) {
            return;
        }
        regenerationTicks %= intervalTicks;
        tower.applyRegeneration(lane, amount);
    }

    private void tickVfx(WarlockTower tower, PlayerLane lane) {
        if (!awakenedThisRound()) {
            vfxTicks = 0;
            return;
        }
        if (lane == null || lane.arenaWorld() == null) {
            return;
        }
        vfxTicks++;
        tower.entityId().ifPresent(id -> {
            var entity = lane.arenaWorld().getEntity(id);
            if (!(entity instanceof SemionTowerEntity towerEntity) || !towerEntity.isAlive()) {
                return;
            }
            if (vfxTicks % 2 == 0) {
                TowerVfxService.showWarlockAwakeningAura(towerEntity);
            }
            if (vfxTicks % 10 == 0) {
                TowerVfxService.showWarlockAwakeningSparkBurst(towerEntity);
            }
        });
    }

    private static void setGlow(WarlockTower tower, PlayerLane lane, boolean glowing) {
        if (lane == null || lane.arenaWorld() == null) {
            return;
        }
        tower.entityId().ifPresent(id -> {
            var entity = lane.arenaWorld().getEntity(id);
            if (entity instanceof SemionTowerEntity towerEntity) {
                towerEntity.setGlowingTag(glowing);
            }
        });
    }

}
