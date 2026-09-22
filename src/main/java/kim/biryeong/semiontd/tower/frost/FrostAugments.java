package kim.biryeong.semiontd.tower.frost;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterDataKey;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class FrostAugments {
    public static final String ICE = "job_frost_s";
    public static final String FREEZE = "job_frost_g1";
    public static final String THAW = "job_frost_g2";
    public static final String AGE = "job_frost_p";
    private static final MonsterDataKey<Long> LAST_STUN = MonsterDataKey.of(
            ResourceLocation.fromNamespaceAndPath("semiontd", "frost_augment_stun"), Long.class);
    private static final MonsterDataKey<Long> LAST_THAW = MonsterDataKey.of(
            ResourceLocation.fromNamespaceAndPath("semiontd", "frost_augment_thaw"), Long.class);
    private static final Map<UUID, Wave> WAVES = new java.util.concurrent.ConcurrentHashMap<>();

    private FrostAugments() { }

    public static double emissionChill(Tower tower) {
        return FrostBalance.chillPerHit() * (tower.augmentSnapshot().has(FREEZE)
                ? tower.augmentSnapshot().parameter(FREEZE, "chillMultiplier", 2) : 1);
    }

    public static void beginWave(PlayerLane lane) {
        if (lane != null) {
            Wave wave = new Wave();
            wave.nextPropagation = now(lane) + (int) lane.augmentSnapshot().parameter(AGE, "intervalTicks", 40);
            WAVES.put(lane.ownerPlayer(), wave);
        }
    }

    public static void endWave(PlayerLane lane) {
        if (lane != null) clearPlayer(lane.ownerPlayer());
    }

    public static void clearPlayer(UUID owner) {
        WAVES.remove(owner);
    }

    static void onChill(SemionTowerEntity source, SemionMonsterEntity target,
                        FrostMonsterStates.ChillResult result) {
        Tower tower = source == null ? null : source.runtimeTower();
        if (tower == null || (!tower.augmentSnapshot().has(ICE) && !tower.augmentSnapshot().has(AGE))) return;
        Wave wave = WAVES.computeIfAbsent(tower.ownerPlayer(), ignored -> new Wave());
        wave.source = source;
        if (!result.becameRefrigerated() || !tower.augmentSnapshot().has(AGE)) return;
        if (!AugmentCombat.allowsTriggers()) return;
        wave.refrigerated.put(target.runtimeMonster().logicalId(), new Refrigerant(source, target));
        long time = source.level().getGameTime();
        int cooldown = (int) tower.augmentSnapshot().parameter(AGE, "stunCooldownTicks", 160);
        if (claimCooldown(target.runtimeMonster(), LAST_STUN, time, cooldown)) {
            target.applyTimedEffect(TimedEffectType.MONSTER_STUN, 1,
                    (int) tower.augmentSnapshot().parameter(AGE, "stunTicks", 40));
        }
    }

    static void onThawed(Tower tower, SemionTowerEntity source, SemionMonsterEntity target) {
        if (!AugmentCombat.allowsTriggers() || !tower.augmentSnapshot().has(THAW)) return;
        if (!claimCooldown(target.runtimeMonster(), LAST_THAW, source.level().getGameTime(),
                (int) tower.augmentSnapshot().parameter(THAW, "cooldownTicks", 60))) return;
        double damage = source.attackDamageAmount(null) * tower.augmentSnapshot().parameter(THAW, "damageRatio", 3);
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTarget(
                AreaEffectIds.tower(tower, "augment_thaw"), source, target,
                tower.augmentSnapshot().parameter(THAW, "radius", 3), AreaVfxSpec.onTrigger(AreaVfxStyles.PULSE))
                .including(target.getUUID()).nearestTargets((int) tower.augmentSnapshot().parameter(THAW, "maxTargets", 12));
        AugmentCombat.runWithoutTriggers(() -> TowerAreaDamage.apply(tower, source, request,
                ignored -> damage, true, (enemy, dealt, killed) -> { }, DamageType.MAGIC));
    }

    static void removeRefrigerant(Monster monster) {
        UUID owner = FrostMonsterStates.refrigerantOwner(monster);
        Wave wave = owner == null ? null : WAVES.get(owner);
        if (wave != null) wave.refrigerated.remove(monster.logicalId());
    }

    public static void onMonsterDeath(PlayerLane lane, Monster monster, Vec3 position) {
        if (lane == null || !AugmentCombat.allowsTriggers() || !lane.augmentSnapshot().has(ICE)
                || !FrostMonsterStates.isRefrigerated(monster) || position == null) return;
        Wave wave = WAVES.computeIfAbsent(lane.ownerPlayer(), ignored -> new Wave());
        if (!wave.iceDeaths.add(monster.logicalId())) return;
        SemionTowerEntity source = lane.towers().stream().filter(tower -> lane.ownerPlayer().equals(tower.ownerPlayer()))
                .filter(EntityBackedTower.class::isInstance).map(EntityBackedTower.class::cast)
                .map(tower -> tower.runtimeEntity(lane).orElse(null)).filter(java.util.Objects::nonNull)
                .findFirst().orElse(wave.source);
        if (source == null) return;
        Ice patch = new Ice(source, position, now(lane) + (int) lane.augmentSnapshot().parameter(ICE, "durationTicks", 80));
        wave.ice.add(patch);
        applyIce(lane, patch, now(lane));
    }

    public static void tick(PlayerLane lane) {
        if (lane != null && lane.arenaWorld() != null) tick(lane, now(lane));
    }

    static void tick(PlayerLane lane, long time) {
        Wave wave = WAVES.get(lane.ownerPlayer());
        if (wave == null) return;
        wave.ice.removeIf(patch -> time >= patch.expiresAt());
        for (Ice patch : wave.ice) applyIce(lane, patch, time);
        if (!lane.augmentSnapshot().has(AGE) || time < wave.nextPropagation) return;
        wave.nextPropagation = time + (int) lane.augmentSnapshot().parameter(AGE, "intervalTicks", 40);
        wave.refrigerated.values().removeIf(refrigerant -> !refrigerant.target().isAlive()
                || refrigerant.target().isRemoved() || !FrostMonsterStates.isRefrigerated(refrigerant.target().runtimeMonster())
                || !lane.ownerPlayer().equals(FrostMonsterStates.refrigerantOwner(refrigerant.target().runtimeMonster())));
        List<Refrigerant> sources = wave.refrigerated.values().stream()
                .limit((int) lane.augmentSnapshot().parameter(AGE, "maxSources", 3)).toList();
        for (Refrigerant refrigerant : sources) {
            var request = MonsterAreaEffectRequest.aroundTarget(
                    AreaEffectIds.tower(refrigerant.source().runtimeTower(), "augment_ice_age"), refrigerant.source(),
                    refrigerant.target(), lane.augmentSnapshot().parameter(AGE, "radius", 3),
                    AreaVfxSpec.onTrigger(AreaVfxStyles.DEBUFF))
                    .nearestTargets((int) lane.augmentSnapshot().parameter(AGE, "maxTargets", 8));
            SemionTdApi.areaEffects().applyToMonsters(request, target -> {
                var result = FrostMonsterStates.applyChill(refrigerant.source(), target,
                        lane.augmentSnapshot().parameter(AGE, "chill", .25));
                return result.currentChill() > result.previousChill() ? AreaEffectOutcome.APPLIED : AreaEffectOutcome.UNCHANGED;
            });
        }
    }

    private static void applyIce(PlayerLane lane, Ice patch, long time) {
        var request = new MonsterAreaEffectRequest(AreaEffectIds.tower(patch.source().runtimeTower(), "augment_ice"),
                patch.source(), patch.position(), lane.augmentSnapshot().parameter(ICE, "radius", 2), Set.of(),
                null, time % 10 == 0 ? AreaVfxSpec.onTrigger(AreaVfxStyles.DEBUFF) : AreaVfxSpec.none());
        SemionTdApi.areaEffects().applyToMonsters(request, target -> {
            target.applyTimedEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION,
                    lane.augmentSnapshot().parameter(ICE, "slow", .3), (int) Math.min(2, patch.expiresAt() - time));
            return AreaEffectOutcome.APPLIED;
        });
    }

    private static boolean claimCooldown(Monster monster, MonsterDataKey<Long> key, long time, int ticks) {
        Long previous = monster.getData(key).orElse(null);
        if (previous != null && time - previous < ticks) return false;
        monster.setData(key, time);
        return true;
    }

    private static long now(PlayerLane lane) { return lane.arenaWorld() == null ? 0 : lane.arenaWorld().getGameTime(); }
    private record Ice(SemionTowerEntity source, Vec3 position, long expiresAt) { }
    private record Refrigerant(SemionTowerEntity source, SemionMonsterEntity target) { }
    private static final class Wave {
        private SemionTowerEntity source;
        private long nextPropagation = 40;
        private final List<Ice> ice = new ArrayList<>();
        private final Set<UUID> iceDeaths = new java.util.HashSet<>();
        private final Map<UUID, Refrigerant> refrigerated = new LinkedHashMap<>();
    }
}
