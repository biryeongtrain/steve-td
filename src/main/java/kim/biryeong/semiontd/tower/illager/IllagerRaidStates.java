package kim.biryeong.semiontd.tower.illager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.job.JobContext;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class IllagerRaidStates {
    public static final String RAID_CONFIG_ID = "illager_raid";

    private static final Map<UUID, IllagerRaidState> STATES = new HashMap<>();

    private IllagerRaidStates() {
    }

    public static void onRoundStarted(JobContext context) {
        UUID playerId = context.player().uuid();
        int towerCount = context.game().playerLane(playerId)
                .map(IllagerRaidStates::countAliveIllagerTowers)
                .orElse(0);
        state(playerId).resetForRound(towerCount);
    }

    public static void clear(UUID playerId) {
        if (playerId != null) {
            STATES.remove(playerId);
        }
    }

    public static void clearAllForTesting() {
        STATES.clear();
    }

    public static Optional<IllagerRaidState> get(UUID playerId) {
        return Optional.ofNullable(STATES.get(playerId));
    }

    public static boolean active(UUID playerId) {
        return get(playerId).map(IllagerRaidState::active).orElse(false);
    }

    public static double attackSpeedBonus(UUID playerId) {
        return get(playerId)
                .filter(IllagerRaidState::active)
                .map(state -> attackSpeedBonusForTowerCount(state.roundStartTowerCount()))
                .orElse(0.0);
    }

    public static double damageBonus(UUID playerId) {
        return get(playerId)
                .filter(IllagerRaidState::active)
                .map(state -> damageBonusForTowerCount(state.roundStartTowerCount()))
                .orElse(0.0);
    }

    static double attackSpeedBonusForTowerCount(int towerCount) {
        return Math.min(
                ability("attackSpeedBonusCap", 0.20),
                Math.max(0, towerCount) * ability("attackSpeedPercentPerTower", 0.02)
        );
    }

    static double damageBonusForTowerCount(int towerCount) {
        return Math.min(
                ability("damageBonusCap", 0.60),
                Math.max(0, towerCount) * ability("damagePercentPerTower", 0.06)
        );
    }

    public static void onMonsterKilled(Map<UUID, SemionPlayer> players, Monster monster) {
        if (players == null || monster == null || monster.lastHitPlayerId().isEmpty()) {
            return;
        }
        SemionPlayer player = players.get(monster.lastHitPlayerId().get());
        if (player == null || player.job().filter(job -> job instanceof kim.biryeong.semiontd.job.IllagerTowerJob).isEmpty()) {
            return;
        }
        int amount = monster.ownerPlayer().isPresent()
                ? abilityInt("incomeKillGauge", 8)
                : abilityInt("waveKillGauge", 3);
        if (IllagerMarks.activeMark(monster, player.uuid()).isPresent()) {
            amount += abilityInt("markedKillBonusGauge", 7);
        }
        addGauge(player.uuid(), amount);
    }

    public static void onTowerDeath(PlayerLane lane, Tower destroyedTower) {
        if (lane == null || destroyedTower == null || !IllagerTowers.isIllagerTower(destroyedTower.type())) {
            return;
        }
        get(destroyedTower.ownerPlayer()).ifPresent(state ->
                addGauge(destroyedTower.ownerPlayer(), abilityInt("illagerTowerDeathGauge", 20))
        );
    }

    public static int playPendingActivationEffects(MinecraftServer server, PlayerLane lane) {
        if (server == null || lane == null) {
            return 0;
        }
        IllagerRaidState state = STATES.get(lane.ownerPlayer());
        if (state == null || !state.consumePendingActivationEffects()) {
            return 0;
        }

        ServerPlayer player = server.getPlayerList().getPlayer(lane.ownerPlayer());
        if (player != null) {
            player.playNotifySound(SoundEvents.APPLY_EFFECT_RAID_OMEN, SoundSource.HOSTILE, 1.0F, 1.0F);
        }

        int affectedTowers = 0;
        for (Tower tower : lane.towers()) {
            if (!IllagerTowers.isIllagerTower(tower.type()) || tower.health() <= 0.0
                    || !(tower instanceof EntityBackedTower entityBackedTower) || entityBackedTower.entityId().isEmpty()) {
                continue;
            }
            if (lane.arenaWorld().getEntity(entityBackedTower.entityId().getAsInt()) instanceof SemionTowerEntity towerEntity
                    && towerEntity.isAlive() && !towerEntity.isRemoved()) {
                TowerVfxService.showIllagerRaidActivation(towerEntity);
                affectedTowers++;
            }
        }
        return affectedTowers;
    }

    static int gaugeMax() {
        return Math.max(1, abilityInt("gaugeMax", 100));
    }

    static double ability(String key, double fallback) {
        return TowerBalanceRuntime.ability(RAID_CONFIG_ID, key, fallback);
    }

    static int abilityInt(String key, int fallback) {
        return TowerBalanceRuntime.abilityInt(RAID_CONFIG_ID, key, fallback);
    }

    private static void addGauge(UUID playerId, int amount) {
        state(playerId).addGauge(amount, gaugeMax());
    }

    private static IllagerRaidState state(UUID playerId) {
        return STATES.computeIfAbsent(playerId, ignored -> new IllagerRaidState());
    }

    private static int countAliveIllagerTowers(PlayerLane lane) {
        int count = 0;
        for (Tower tower : lane.towers()) {
            if (IllagerTowers.isIllagerTower(tower.type()) && tower.health() > 0) {
                count++;
            }
        }
        return count;
    }
}
