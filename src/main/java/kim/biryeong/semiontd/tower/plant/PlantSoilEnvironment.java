package kim.biryeong.semiontd.tower.plant;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import net.minecraft.util.Mth;

/**
 * Terrain that hurts whatever walks on it, with no tower involved.
 *
 * <p>Tower auras need a living combat tower nearby; this runs off the soil itself, so simply owning
 * the ground is worth something. 균사 weakens what stands on it, 사암 slows attacks and bleeds health.
 * 잔디 and 회백토 are friendly terrain and do nothing here.
 */
public final class PlantSoilEnvironment {
    private static final int TICKS_PER_SECOND = 20;

    private PlantSoilEnvironment() {
    }

    public static void tick(PlayerLane lane, Map<UUID, SemionPlayer> players) {
        if (lane == null || lane.arenaWorld() == null) {
            return;
        }
        UUID owner = lane.ownerPlayer();
        if (owner == null) {
            return;
        }
        long gameTime = lane.arenaWorld().getGameTime();
        // 다이아 지급은 초당 값이라 환경 틱 주기와 무관하게 정확히 1초마다 돕니다.
        if (gameTime % TICKS_PER_SECOND == 0 && players != null) {
            payMeadowIncome(lane, players.get(owner));
        }
        if (PlantSoilStates.totalCount(owner) == 0) {
            return;
        }
        int interval = Math.max(1, globalTicks("environmentTickIntervalTicks"));
        if (gameTime % interval != 0) {
            return;
        }
        applyMeadowGrowthShare(lane, interval);

        for (Monster monster : List.copyOf(lane.activeMonsters())) {
            if (monster == null || !monster.isAlive() || !monster.hasMinecraftEntity()) {
                continue;
            }
            if (!(lane.arenaWorld().getEntity(monster.minecraftEntityId()) instanceof SemionMonsterEntity entity)
                    || entity.isRemoved()) {
                continue;
            }
            PlantSoil soil = PlantSoilStates.soilAtColumn(owner, Mth.floor(entity.getX()), Mth.floor(entity.getZ()));
            if (soil == null) {
                continue;
            }
            applyEnvironment(owner, monster, entity, soil, interval);
        }
    }

    /**
     * 민들레 계열이 초당 만들어 내는 다이아를 합산해 라인 주인에게 지급합니다.
     */
    private static void payMeadowIncome(PlayerLane lane, SemionPlayer player) {
        if (player == null) {
            return;
        }
        long total = 0L;
        for (Tower tower : List.copyOf(lane.towers())) {
            if (tower instanceof PlantCombatTower plant && tower.health() > 0.0) {
                total += plant.diamondPerSecond();
            }
        }
        if (total > 0L) {
            player.economy().addMineral(total);
        }
    }

    /**
     * 잔디 타워들이 키운 성장 체력을 합산해 라인 안 모든 타워에게 같은 값으로 겁니다.
     *
     * <p>거리 제한이 없고 여러 잔디 타워가 있으면 그만큼 더해집니다. 합계를 한 번 계산해 모두에게
     * 같은 값으로 걸기 때문에, 최댓값만 잡는 소스 없는 효과를 써도 결과가 동일합니다.
     */
    private static void applyMeadowGrowthShare(PlayerLane lane, int intervalTicks) {
        double total = 0.0;
        for (Tower tower : List.copyOf(lane.towers())) {
            if (tower instanceof PlantCombatTower plant && tower.health() > 0.0) {
                total += plant.sharedGrowthBonus();
            }
        }
        if (total <= 0.0) {
            return;
        }
        // 잔디 타워를 늘릴수록 합계가 커지므로 라인 전체 버프에는 상한을 둡니다.
        double cap = TowerBalanceRuntime.ability(PlantSoil.MEADOW.configId(), "growthShareCap", 0.0);
        if (cap > 0.0) {
            total = Math.min(cap, total);
        }
        int durationTicks = Math.max(
                intervalTicks * 2,
                TowerBalanceRuntime.abilityTicks(PlantSoil.MEADOW.configId(), "supportDurationTicks", 0)
        );
        for (Tower tower : List.copyOf(lane.towers())) {
            if (!(tower instanceof EntityBackedTower backed) || backed.entityId().isEmpty()) {
                continue;
            }
            if (lane.arenaWorld().getEntity(backed.entityId().getAsInt()) instanceof SemionTowerEntity entity) {
                entity.applyTimedEffect(TimedEffectType.TOWER_MAX_HEALTH_BONUS, total, durationTicks);
            }
        }
    }

    private static void applyEnvironment(
            UUID owner,
            Monster monster,
            SemionMonsterEntity entity,
            PlantSoil soil,
            int intervalTicks
    ) {
        // 다음 펄스까지는 효과가 끊기지 않도록 간격보다 넉넉하게 겁니다.
        int durationTicks = Math.max(intervalTicks * 2, soilTicks(soil, "environmentDurationTicks"));

        double weakness = soilValue(soil, "environmentWeakness");
        if (weakness > 0.0) {
            entity.applyTimedEffect(TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION, weakness, durationTicks);
        }

        // 균사는 지뢰 계열이라 상주 타워가 없습니다. 딜증(취약)도 지형이 직접 겁니다.
        double damageTakenBonus = soilValue(soil, "environmentDamageTakenBonus");
        if (damageTakenBonus > 0.0) {
            entity.applyTimedEffect(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS, damageTakenBonus, durationTicks);
        }

        double moveSpeedReduction = soilValue(soil, "environmentMoveSpeedReduction");
        if (moveSpeedReduction > 0.0) {
            entity.applyTimedEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, moveSpeedReduction, durationTicks);
        }

        double attackSpeedReduction = soilValue(soil, "environmentAttackSpeedReduction");
        if (attackSpeedReduction > 0.0) {
            entity.applyTimedEffect(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION, attackSpeedReduction, durationTicks);
        }

        // 최대 체력 비례라 라운드가 올라가 몬스터가 단단해져도 사암이 계속 값을 합니다.
        double ratioPerSecond = soilValue(soil, "environmentMaxHealthDamagePerSecond");
        if (ratioPerSecond > 0.0) {
            double damage = monster.maxHealth() * ratioPerSecond * (intervalTicks / 20.0);
            if (damage > 0.0) {
                double previousHealth = monster.health();
                entity.applyRuntimeDamage(entity.damageSources().cactus(), damage, DamageType.MAGIC);
                if (monster.health() < previousHealth) {
                    monster.recordLastHit(owner, KillSourceKind.TOWER);
                }
            }
        }
    }

    private static double soilValue(PlantSoil soil, String key) {
        return TowerBalanceRuntime.ability(soil.configId(), key, 0.0);
    }

    private static int soilTicks(PlantSoil soil, String key) {
        return TowerBalanceRuntime.abilityTicks(soil.configId(), key, 0);
    }

    private static int globalTicks(String key) {
        return TowerBalanceRuntime.abilityTicks(PlantTowers.GLOBAL_CONFIG_ID, key, 20);
    }
}
