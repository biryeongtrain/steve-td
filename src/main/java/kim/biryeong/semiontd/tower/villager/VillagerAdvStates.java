package kim.biryeong.semiontd.tower.villager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.config.TowerBalanceConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.SemionGame;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.job.VillagerAdvTowerJob;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;
import net.minecraft.resources.ResourceLocation;

public final class VillagerAdvStates {
    public static final TowerDataKey<Double> EXPERIENCE = TowerDataKey.of(id("experience"), Double.class);
    private static final TowerDataKey<Boolean> ADV_TOWER = TowerDataKey.of(id("tower"), Boolean.class);
    private static final double SURVIVAL_BONUS_MULTIPLIER = 0.5;

    private static final Map<UUID, Double> REPUTATION = new ConcurrentHashMap<>();
    private static final Map<UUID, ConcurrentLinkedQueue<ExperienceGainBatch>> PENDING_EXPERIENCE_GAINS = new ConcurrentHashMap<>();
    private static final ExecutorService EXPERIENCE_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "SemionTD Villager ADV Experience");
        thread.setDaemon(true);
        return thread;
    });
    private static final ResourceLocation DAMAGE_SOURCE = source("damage");
    private static final ResourceLocation ATTACK_SPEED_SOURCE = source("attack_speed");
    private static final ResourceLocation DAMAGE_REDUCTION_SOURCE = source("damage_reduction");
    private static final ResourceLocation MAX_HEALTH_SOURCE = source("max_health");
    private static final ResourceLocation INCOME_DAMAGE_SOURCE = source("income_damage");
    private static final ResourceLocation WAVE_DAMAGE_SOURCE = source("wave_damage");
    private static final ResourceLocation HEAL_AMOUNT_SOURCE = source("heal_amount");
    private static final ResourceLocation ABILITY_INTERVAL_SOURCE = source("ability_interval");

    private VillagerAdvStates() {
    }

    public static void clear(UUID playerId) {
        if (playerId != null) {
            REPUTATION.remove(playerId);
            PENDING_EXPERIENCE_GAINS.remove(playerId);
        }
    }

    public static void clearAll() {
        REPUTATION.clear();
        PENDING_EXPERIENCE_GAINS.clear();
    }

    public static double experience(Tower tower) {
        return tower == null ? 0.0 : tower.getDataOrDefault(EXPERIENCE, 0.0);
    }

    public static boolean isAdvTower(Tower tower) {
        return tower != null && tower.getDataOrDefault(ADV_TOWER, false);
    }

    public static double reputation(UUID playerId) {
        return playerId == null ? 0.0 : REPUTATION.getOrDefault(playerId, 0.0);
    }

    public static void onWaveStarted(SemionGame game, int round) {
        if (game == null) {
            return;
        }

        TowerBalanceConfig.VillagerAdvConfig config = TowerBalanceRuntime.villagerAdv();
        for (SemionPlayer player : game.players().values()) {
            if (!isAdvPlayer(player)) {
                continue;
            }
            game.playerLane(player.uuid()).ifPresent(lane -> {
                List<ExperienceGainSnapshot> snapshots = new ArrayList<>();
                VillagerAdvAugments.startWave(lane);
                for (Tower tower : lane.towers()) {
                    if (VillagerTowers.isVillagerTower(tower.type())) {
                        snapshots.add(new ExperienceGainSnapshot(
                                player.uuid(),
                                lane,
                                tower,
                                tier(tower),
                                experience(tower),
                                normalExperienceGain(tower, config),
                                tower.augmentSnapshot().has(VillagerAdvAugments.MENTOR)
                                        ? tower.augmentSnapshot().parameter(VillagerAdvAugments.MENTOR, "experienceRatio", .50) : 0
                        ));
                        refreshTowerEffects(player, lane, tower);
                    }
                }
                if (!snapshots.isEmpty()) {
                    ConcurrentLinkedQueue<ExperienceGainBatch> queue = PENDING_EXPERIENCE_GAINS
                            .computeIfAbsent(player.uuid(), ignored -> new ConcurrentLinkedQueue<>());
                    CompletableFuture.supplyAsync(() -> calculateExperienceGains(List.copyOf(snapshots), config), EXPERIENCE_EXECUTOR)
                            .thenAccept(results -> queue.add(new ExperienceGainBatch(lane, results)));
                }
            });
        }
    }

    public static void applyPending(SemionGame game) {
        if (game == null) {
            return;
        }
        for (UUID playerId : game.players().keySet()) {
            ConcurrentLinkedQueue<ExperienceGainBatch> queue = PENDING_EXPERIENCE_GAINS.get(playerId);
            if (queue == null) {
                continue;
            }
            ExperienceGainBatch batch;
            while ((batch = queue.poll()) != null) {
                SemionPlayer player = game.players().get(playerId);
                if (!isAdvPlayer(player) || game.playerLane(playerId).orElse(null) != batch.lane()) continue;
                for (ExperienceGainResult result : batch.results()) {
                    if (!result.lane().towers().contains(result.tower())) continue;
                    result.tower().setData(EXPERIENCE, result.nextExperience());
                    refreshTowerEffects(player, result.lane(), result.tower());
                }
                VillagerAdvAugments.captureGraduate(batch.lane());
            }
        }
    }

    public static void onWaveCleared(SemionGame game, int round) {
        if (game == null) {
            return;
        }
        TowerBalanceConfig.VillagerAdvConfig config = TowerBalanceRuntime.villagerAdv();
        for (SemionPlayer player : game.players().values()) {
            if (!isAdvPlayer(player)) {
                continue;
            }
            game.playerLane(player.uuid())
                    .filter(lane -> !lane.leakedThisRound())
                    .ifPresent(lane -> {
                        addReputation(player.uuid(), Math.max(0, round) * config.resolvedReputationGainRoundMultiplier(), config);
                        for (Tower tower : lane.towers()) {
                            refreshTowerEffects(player, lane, tower);
                        }
                    });
        }
    }

    public static void onLaneLeak(SemionPlayer laneOwner, PlayerLane lane) {
        if (!isAdvPlayer(laneOwner)) {
            return;
        }
        TowerBalanceConfig.VillagerAdvConfig config = TowerBalanceRuntime.villagerAdv();
        addReputation(laneOwner.uuid(), -config.resolvedReputationLossPerLeak(), config);
        if (lane != null) {
            for (Tower tower : lane.towers()) {
                refreshTowerEffects(laneOwner, lane, tower);
            }
        }
    }

    public static boolean canUpgrade(SemionPlayer player, Tower tower, TowerUpgradeOption upgrade) {
        if (!isAdvPlayer(player)) {
            return true;
        }
        if (tower == null || upgrade == null) {
            return false;
        }
        if (tower.augmentSnapshot().has(VillagerAdvAugments.EARLY)) return true;
        double requirement = TowerBalanceRuntime.villagerAdvUpgradeRequirement(tower.type(), upgrade.id());
        return requirement <= 0.0 || experience(tower) + 1.0E-6 >= requirement;
    }

    public static double survivalBonusMultiplier(Tower tower) {
        return tower != null && tower.getDataOrDefault(ADV_TOWER, false) ? SURVIVAL_BONUS_MULTIPLIER : 1.0;
    }

    public static void refreshTowerEffects(SemionPlayer player, PlayerLane lane, Tower tower) {
        if (!isAdvPlayer(player) || lane == null || tower == null || !VillagerTowers.isVillagerTower(tower.type())) {
            return;
        }
        tower.setData(ADV_TOWER, true);
        towerEntity(tower, lane).ifPresent(entity -> refreshTowerEffects(player, tower, entity));
    }

    private static void refreshTowerEffects(SemionPlayer player, Tower tower, SemionTowerEntity entity) {
        TowerBalanceConfig.VillagerAdvConfig config = TowerBalanceRuntime.villagerAdv();
        double experience = experience(tower);
        double reputation = reputation(player.uuid());
        int durationTicks = config.resolvedEffectDurationTicks();

        String towerId = tower.type().id();
        double reputationDamage = reputationBonus(config, towerId, reputation, "reputationDamagePerPoint");
        double reputationAttackSpeed = reputationBonus(config, towerId, reputation, "reputationAttackSpeedPerPoint");
        double reputationHealth = reputationBonus(config, towerId, reputation, "reputationHealthPerPoint");
        double reputationDamageReduction = reputationBonus(config, towerId, reputation, "reputationDamageReductionPerPoint");

        double damage = reputationDamage;
        double attackSpeed = reputationAttackSpeed;
        double maxHealth = reputationHealth;
        double damageReduction = reputationDamageReduction;
        double incomeDamage = 0.0;
        double waveDamage = 0.0;
        double healAmount = 0.0;
        double abilityInterval = 0.0;

        if (isGolem(tower)) {
            maxHealth += experienceBonus(config, towerId, experience, "golemHealthPerExperience");
            damageReduction += experienceBonus(config, towerId, experience, "golemDamageReductionPerExperience");
        } else if (isRanged(tower)) {
            damage += experienceBonus(config, towerId, experience, "rangedDamagePerExperience");
            attackSpeed += experienceBonus(config, towerId, experience, "rangedAttackSpeedPerExperience");
        } else if (isCat(tower)) {
            damage += experienceBonus(config, towerId, experience, "catDamagePerExperience");
            attackSpeed += experienceBonus(config, towerId, experience, "catAttackSpeedPerExperience");
            if (isAntiTankerCat(tower)) {
                incomeDamage += experienceBonus(config, towerId, experience, "catIncomeDamagePerExperience");
            }
            if (isLaneClearCat(tower)) {
                waveDamage += experienceBonus(config, towerId, experience, "catWaveDamagePerExperience");
            }
        } else if (isAllayLine(tower)) {
            healAmount += experienceBonus(config, towerId, experience, "allayHealAmountPerExperience");
            abilityInterval += experienceBonus(config, towerId, experience, "allayIntervalReductionPerExperience");
        }

        refresh(entity, TimedEffectType.TOWER_DAMAGE_BONUS, DAMAGE_SOURCE, damage, durationTicks);
        refresh(entity, TimedEffectType.TOWER_ATTACK_SPEED_BONUS, ATTACK_SPEED_SOURCE, attackSpeed, durationTicks);
        refresh(entity, TimedEffectType.TOWER_MAX_HEALTH_BONUS, MAX_HEALTH_SOURCE, maxHealth, durationTicks);
        refresh(entity, TimedEffectType.TOWER_DAMAGE_REDUCTION, DAMAGE_REDUCTION_SOURCE, damageReduction, durationTicks);
        refresh(entity, TimedEffectType.TOWER_INCOME_DAMAGE_BONUS, INCOME_DAMAGE_SOURCE, incomeDamage, durationTicks);
        refresh(entity, TimedEffectType.TOWER_WAVE_DAMAGE_BONUS, WAVE_DAMAGE_SOURCE, waveDamage, durationTicks);
        refresh(entity, TimedEffectType.TOWER_HEAL_AMOUNT_BONUS, HEAL_AMOUNT_SOURCE, healAmount, durationTicks);
        refresh(entity, TimedEffectType.TOWER_ABILITY_INTERVAL_REDUCTION, ABILITY_INTERVAL_SOURCE, abilityInterval, durationTicks);
    }

    static double normalExperienceGain(Tower tower, TowerBalanceConfig.VillagerAdvConfig config) {
        double tierMultiplier = tower.augmentSnapshot().has(VillagerAdvAugments.EARLY)
                ? tower.augmentSnapshot().parameter(VillagerAdvAugments.EARLY, "tierExperienceMultiplier", 2.0) : 1.0;
        return config.resolvedExperiencePerTower() + tier(tower) * config.resolvedExperiencePerTier() * tierMultiplier;
    }

    static List<ExperienceGainResult> calculateExperienceGains(
            List<ExperienceGainSnapshot> snapshots,
            TowerBalanceConfig.VillagerAdvConfig config
    ) {
        ExperienceGainSnapshot mentor = snapshots.stream()
                .max(java.util.Comparator.comparingDouble(ExperienceGainSnapshot::currentExperience)).orElse(null);
        ExperienceGainSnapshot mentee = snapshots.stream()
                .min(java.util.Comparator.comparingDouble(ExperienceGainSnapshot::currentExperience)).orElse(null);
        return snapshots.stream()
                .map(snapshot -> new ExperienceGainResult(
                        snapshot.ownerPlayer(),
                        snapshot.lane(),
                        snapshot.tower(),
                        Math.min(
                                config.resolvedExperienceMax(),
                                snapshot.currentExperience() + snapshot.normalGain()
                                        + (snapshot == mentee && mentor != null ? mentor.normalGain() * snapshot.mentorRatio() : 0.0)
                        )
                ))
                .toList();
    }

    private static void addReputation(UUID playerId, double amount, TowerBalanceConfig.VillagerAdvConfig config) {
        if (playerId == null || amount == 0.0) {
            return;
        }
        REPUTATION.compute(playerId, (ignored, previous) -> Math.max(0.0, Math.min(
                config.resolvedReputationMax(),
                (previous == null ? 0.0 : previous) + amount
        )));
    }

    private static boolean isAdvPlayer(SemionPlayer player) {
        return player != null && player.job()
                .map(job -> VillagerAdvTowerJob.ID.equals(job.id()))
                .orElse(false);
    }

    private static java.util.Optional<SemionTowerEntity> towerEntity(Tower tower, PlayerLane lane) {
        if (!(tower instanceof EntityBackedTower entityBackedTower) || entityBackedTower.entityId().isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(lane.arenaWorld().getEntity(entityBackedTower.entityId().getAsInt()))
                .filter(SemionTowerEntity.class::isInstance)
                .map(SemionTowerEntity.class::cast);
    }

    private static void refresh(
            SemionTowerEntity entity,
            TimedEffectType type,
            ResourceLocation source,
            double magnitude,
            int durationTicks
    ) {
        entity.refreshTimedEffect(type, source, magnitude, durationTicks);
    }

    private static double experienceBonus(TowerBalanceConfig.VillagerAdvConfig config, String towerId, double experience, String key) {
        return Math.min(
                config.resolvedExperienceBuffCap(),
                Math.max(0.0, experience) / config.buffInterval(towerId, key) * config.buff(towerId, key)
        );
    }

    private static double reputationBonus(TowerBalanceConfig.VillagerAdvConfig config, String towerId, double reputation, String key) {
        return Math.min(
                config.resolvedReputationBuffCap(),
                Math.max(0.0, reputation) / config.buffInterval(towerId, key) * config.buff(towerId, key)
        );
    }

    private static boolean isGolem(Tower tower) {
        TowerType type = tower.type();
        return VillagerTowers.matches(type, VillagerTowers.T1_GOLEM_TOWER)
                || VillagerTowers.matches(type, VillagerTowers.T2_GOLEM_TOWER)
                || VillagerTowers.matches(type, VillagerTowers.T3_GOLEM_TOWER);
    }

    private static boolean isRanged(Tower tower) {
        TowerType type = tower.type();
        return VillagerTowers.matches(type, VillagerTowers.T1_SPLASH_TOWER)
                || VillagerTowers.matches(type, VillagerTowers.T2_LIBRARIAN_TOWER)
                || VillagerTowers.matches(type, VillagerTowers.T3_CLERIC_TOWER);
    }

    private static boolean isCat(Tower tower) {
        return VillagerTowers.matches(tower.type(), VillagerTowers.T1_CAT_TOWER)
                || isAntiTankerCat(tower)
                || isLaneClearCat(tower);
    }

    private static boolean isAntiTankerCat(Tower tower) {
        TowerType type = tower.type();
        return VillagerTowers.matches(type, VillagerTowers.T2_ANTI_TANKER_CAT_TOWER)
                || VillagerTowers.matches(type, VillagerTowers.T3_ANTI_TANKER_CAT_TOWER);
    }

    private static boolean isLaneClearCat(Tower tower) {
        TowerType type = tower.type();
        return VillagerTowers.matches(type, VillagerTowers.T2_LANE_CLEAR_CAT_TOWER)
                || VillagerTowers.matches(type, VillagerTowers.T3_LANE_CLEAR_CAT_TOWER);
    }

    private static boolean isAllayLine(Tower tower) {
        TowerType type = tower.type();
        return VillagerTowers.matches(type, VillagerTowers.T1_ALLAY_TOWER)
                || VillagerTowers.matches(type, VillagerTowers.T2_ALLAY_TOWER)
                || VillagerTowers.matches(type, VillagerTowers.T2_WEAPON_SMITH_TOWER)
                || VillagerTowers.matches(type, VillagerTowers.T3_ARMORER_TOWER)
                || VillagerTowers.matches(type, VillagerTowers.T3_WEAPON_SMITH_TOWER);
    }

    private static int tier(Tower tower) {
        TowerType type = tower.type();
        if (VillagerTowers.matches(type, VillagerTowers.T3_CLERIC_TOWER)
                || VillagerTowers.matches(type, VillagerTowers.T3_GOLEM_TOWER)
                || VillagerTowers.matches(type, VillagerTowers.T3_ARMORER_TOWER)
                || VillagerTowers.matches(type, VillagerTowers.T3_WEAPON_SMITH_TOWER)
                || VillagerTowers.matches(type, VillagerTowers.T3_ANTI_TANKER_CAT_TOWER)
                || VillagerTowers.matches(type, VillagerTowers.T3_LANE_CLEAR_CAT_TOWER)) {
            return 3;
        }
        if (VillagerTowers.matches(type, VillagerTowers.T2_LIBRARIAN_TOWER)
                || VillagerTowers.matches(type, VillagerTowers.T2_GOLEM_TOWER)
                || VillagerTowers.matches(type, VillagerTowers.T2_ALLAY_TOWER)
                || VillagerTowers.matches(type, VillagerTowers.T2_WEAPON_SMITH_TOWER)
                || VillagerTowers.matches(type, VillagerTowers.T2_ANTI_TANKER_CAT_TOWER)
                || VillagerTowers.matches(type, VillagerTowers.T2_LANE_CLEAR_CAT_TOWER)) {
            return 2;
        }
        return 1;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "villager_adv/" + path);
    }

    private static ResourceLocation source(String path) {
        return ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "villager_adv/effect/" + path);
    }

    record ExperienceGainSnapshot(UUID ownerPlayer, PlayerLane lane, Tower tower, int tier, double currentExperience,
                                  double normalGain, double mentorRatio) {
    }

    record ExperienceGainResult(UUID ownerPlayer, PlayerLane lane, Tower tower, double nextExperience) {
    }

    private record ExperienceGainBatch(PlayerLane lane, List<ExperienceGainResult> results) {
    }
}
