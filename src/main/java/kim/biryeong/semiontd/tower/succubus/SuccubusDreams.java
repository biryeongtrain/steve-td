package kim.biryeong.semiontd.tower.succubus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaTargetMode;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

public final class SuccubusDreams {
    private static final ResourceLocation EFFECT_SOURCE = ResourceLocation.fromNamespaceAndPath("semion-td", "succubus_dream");
    private static final ResourceLocation SPREAD_EFFECT = ResourceLocation.fromNamespaceAndPath("semion-td", "succubus_dream_spread");
    private static final Map<TowerKey, DreamState> TOWERS = new HashMap<>();
    private static final Map<UUID, DreamState> MONSTERS = new HashMap<>();
    private static final Map<TowerKey, Long> LULLABY_READY_AT = new HashMap<>();
    private static final int SLEEP_SMOKE_INTERVAL_TICKS = 10;
    private static boolean propagatingWake;

    private SuccubusDreams() {
    }

    public static boolean add(Tower target, PlayerLane lane, Tower source, int amount) {
        if (target == null || lane == null || source == null || amount <= 0 || target.health() <= 0.0
                || SuccubusTowers.isSuccubus(target.type())) return false;
        DreamState state = TOWERS.computeIfAbsent(TowerKey.of(target), ignored -> new DreamState());
        boolean wasAsleep = state.asleep;
        boolean changed = add(state, lane, source, amount, SuccubusBalance.towerSleepDurationTicks());
        syncTowerEffects(target, lane, state);
        if (changed) {
            SemionTowerEntity sourceEntity = sourceEntity(lane, source, source.ownerPlayer());
            SemionTowerEntity targetEntity = towerEntity(target, lane);
            if (sourceEntity != null && targetEntity != null) {
                SuccubusVfx.showDreamStack(sourceEntity, targetEntity.position());
                if (!wasAsleep && state.asleep) SuccubusVfx.showSleep(sourceEntity, targetEntity.position());
            }
        }
        return changed;
    }

    public static boolean add(SemionMonsterEntity target, PlayerLane lane, Tower source, int amount) {
        if (target == null || lane == null || source == null || amount <= 0 || !target.isAlive()) return false;
        DreamState state = MONSTERS.computeIfAbsent(target.getUUID(), ignored -> new DreamState());
        boolean wasAsleep = state.asleep;
        boolean changed = add(state, lane, source, amount, SuccubusBalance.sleepDurationTicks());
        syncMonsterEffects(target, state);
        if (changed) {
            SemionTowerEntity sourceEntity = sourceEntity(lane, source, source.ownerPlayer());
            if (sourceEntity != null) {
                SuccubusVfx.showDreamStack(sourceEntity, target.position());
                if (!wasAsleep && state.asleep) SuccubusVfx.showSleep(sourceEntity, target.position());
            }
        }
        if (state.asleep && state.sleepCount >= SuccubusBalance.executionSleepCount()
                && hasLivingSuccubus(lane, state.sourceOwner)) {
            execute(target, state);
        }
        return changed;
    }

    public static boolean addFromLullaby(Tower target, PlayerLane lane, Tower source, int amount) {
        if (target == null || lane == null || lane.arenaWorld() == null) return false;
        TowerKey key = TowerKey.of(target);
        long now = lane.arenaWorld().getGameTime();
        if (now < LULLABY_READY_AT.getOrDefault(key, 0L)) return false;
        if (!add(target, lane, source, amount)) return false;
        LULLABY_READY_AT.put(key, now + 80L);
        return true;
    }

    private static boolean add(DreamState state, PlayerLane lane, Tower source, int amount, int sleepDurationTicks) {
        if (state.asleep || state.immunityTicks > 0) return false;
        if (state.stacks == 0) state.sourceOwner = source.ownerPlayer();
        int previous = state.stacks;
        state.stacks = Math.min(SuccubusBalance.maxStacks(), state.stacks + amount);
        state.remainingTicks = SuccubusBalance.stackDurationTicks();
        state.lastSource = source;
        state.lane = lane;
        if (state.stacks >= SuccubusBalance.maxStacks()) {
            state.asleep = true;
            state.asleepTicks = sleepDurationTicks;
            state.sleepLostHealth = 0.0;
            state.sleepCount++;
        }
        return state.stacks != previous || state.asleep;
    }

    public static int stacks(Tower tower) {
        DreamState state = tower == null ? null : TOWERS.get(TowerKey.of(tower));
        return state == null ? 0 : state.stacks;
    }

    public static int stacks(SemionMonsterEntity monster) {
        DreamState state = monster == null ? null : MONSTERS.get(monster.getUUID());
        return state == null ? 0 : state.stacks;
    }

    public static int sleepCount(SemionMonsterEntity monster) {
        DreamState state = monster == null ? null : MONSTERS.get(monster.getUUID());
        return state == null ? 0 : state.sleepCount;
    }

    public static boolean isAsleep(Tower tower) {
        DreamState state = tower == null ? null : TOWERS.get(TowerKey.of(tower));
        return state != null && state.asleep;
    }

    public static boolean isAsleep(SemionTowerEntity tower) {
        return tower != null && isAsleep(tower.runtimeTower());
    }

    public static boolean isAsleep(SemionMonsterEntity monster) {
        DreamState state = monster == null ? null : MONSTERS.get(monster.getUUID());
        return state != null && state.asleep;
    }

    public static void onMonsterDamaged(SemionMonsterEntity target, Tower source, double dealtDamage) {
        DreamState state = target == null ? null : MONSTERS.get(target.getUUID());
        if (state == null || !state.asleep || dealtDamage <= 0.0) return;
        state.lastSource = source == null ? state.lastSource : source;
        state.sleepLostHealth += dealtDamage;
        if (!target.isAlive() || target.runtimeMonster() == null) {
            clearMonster(target);
            return;
        }
        if (state.sleepLostHealth + 1.0e-9 >= target.runtimeMonster().maxHealth()
                * SuccubusBalance.monsterWakeDamageThreshold()) {
            wakeMonster(target, state);
        }
    }

    public static void onTowerDamaged(SemionTowerEntity target, DamageSource source,
                                      double previousHealth, double currentHealth) {
        if (target == null || target.runtimeTower() == null
                || source == null || !(source.getEntity() instanceof SemionMonsterEntity)) return;
        DreamState state = TOWERS.get(TowerKey.of(target.runtimeTower()));
        double lost = Math.max(0.0, previousHealth - currentHealth);
        if (state == null || !state.asleep || lost <= 0.0) return;
        state.sleepLostHealth += lost;
        if (currentHealth <= 0.0) {
            clearTower(target.runtimeTower(), state.lane);
            return;
        }
        if (state.sleepLostHealth + 1.0e-9 >= target.runtimeTower().currentMaxHealth()
                * SuccubusBalance.towerWakeDamageThreshold()) {
            wakeTower(target.runtimeTower(), target, state);
        }
    }

    public static void tick(PlayerLane lane) {
        if (lane == null) return;
        for (Map.Entry<TowerKey, DreamState> entry : List.copyOf(TOWERS.entrySet())) {
            DreamState state = entry.getValue();
            if (state.lane != lane) continue;
            Tower tower = lane.towers().stream().filter(candidate -> entry.getKey().equals(TowerKey.of(candidate))).findFirst().orElse(null);
            if (tower == null || tower.health() <= 0.0) {
                removeTowerEffects(tower, lane);
                TOWERS.remove(entry.getKey());
                continue;
            }
            tickTower(tower, lane, state);
        }

        for (Map.Entry<UUID, DreamState> entry : List.copyOf(MONSTERS.entrySet())) {
            DreamState state = entry.getValue();
            if (state.lane != lane) continue;
            SemionMonsterEntity monster = monsterEntity(lane, entry.getKey());
            if (monster == null || !monster.isAlive()) {
                if (monster != null) removeMonsterEffects(monster);
                MONSTERS.remove(entry.getKey());
                continue;
            }
            tickMonster(monster, state);
        }
    }

    private static void tickTower(Tower tower, PlayerLane lane, DreamState state) {
        tickCounters(state);
        if (state.asleep && state.asleepTicks <= 0) {
            SemionTowerEntity entity = towerEntity(tower, lane);
            if (entity != null) wakeTower(tower, entity, state);
        } else if (state.asleep && state.asleepTicks % SLEEP_SMOKE_INTERVAL_TICKS == 0) {
            SemionTowerEntity source = sourceEntity(lane, state.lastSource, state.sourceOwner);
            SemionTowerEntity target = towerEntity(tower, lane);
            if (source != null && target != null) SuccubusVfx.showSleepSmoke(source, target);
        } else if (!state.asleep && state.stacks > 0 && state.remainingTicks <= 0) {
            clearStacks(state);
        }
        syncTowerEffects(tower, lane, state);
    }

    private static void tickMonster(SemionMonsterEntity monster, DreamState state) {
        tickCounters(state);
        if (state.asleep && state.asleepTicks <= 0) {
            wakeMonster(monster, state);
        } else if (state.asleep && state.asleepTicks % SLEEP_SMOKE_INTERVAL_TICKS == 0) {
            SemionTowerEntity source = sourceEntity(state.lane, state.lastSource, state.sourceOwner);
            if (source != null) SuccubusVfx.showSleepSmoke(source, monster);
        } else if (!state.asleep && state.stacks > 0 && state.remainingTicks <= 0) clearStacks(state);
        syncMonsterEffects(monster, state);
    }

    private static void tickCounters(DreamState state) {
        if (state.immunityTicks > 0) state.immunityTicks--;
        if (state.asleep) state.asleepTicks--;
        else if (state.stacks > 0) state.remainingTicks--;
    }

    private static void wakeMonster(SemionMonsterEntity monster, DreamState state) {
        PlayerLane lane = state.lane;
        Tower source = state.lastSource;
        double bonus = state.sleepLostHealth * SuccubusBalance.monsterWakeBonusDamage();
        clearStacksForWake(state);
        SemionTowerEntity sourceEntity = sourceEntity(lane, source, state.sourceOwner);
        if (bonus > 0.0 && source != null && sourceEntity != null && monster.isAlive()) {
            source.damageResolvedTargetResult(sourceEntity, monster, bonus, DamageType.MAGIC);
        }
        if (monster.isAlive()) propagate(lane, source, sourceEntity, monster.position(), monster.getUUID(), null, bonus);
    }

    private static void wakeTower(Tower tower, SemionTowerEntity entity, DreamState state) {
        PlayerLane lane = state.lane;
        double bonus = state.sleepLostHealth * SuccubusBalance.towerWakeBonusDamage();
        clearStacksForWake(state);
        if (bonus > 0.0 && entity.isAlive()) {
            entity.hurt(entity.damageSources().magic(), (float) bonus);
        }
        if (entity.isAlive()) propagate(lane, state.lastSource, sourceEntity(lane, state.lastSource, state.sourceOwner),
                entity.position(), null, tower, bonus);
    }

    private static void execute(SemionMonsterEntity target, DreamState state) {
        SuccubusTower source = livingSuccubus(state.lane, state.sourceOwner);
        SemionTowerEntity entity = source == null ? null : source.entity(state.lane);
        if (source != null && entity != null && target.runtimeMonster() != null) {
            Tower.DamageResult result = source.damageResolvedTargetResult(entity, target,
                    target.runtimeMonster().maxHealth() * 1000.0, DamageType.TRUE);
            if (result.killed()) SuccubusAbsorption.absorb(source, entity, target, state.lane);
        }
        clearMonster(target);
    }

    private static void propagate(PlayerLane lane, Tower source, SemionTowerEntity sourceEntity, Vec3 center,
                                  UUID excludedMonster, Tower excludedTower, double wakeDamage) {
        if (lane == null || source == null || sourceEntity == null || center == null || propagatingWake) return;
        propagatingWake = true;
        try {
            MonsterAreaEffectRequest monsterRequest = new MonsterAreaEffectRequest(
                    SPREAD_EFFECT, sourceEntity, center, SuccubusBalance.spreadRadius(),
                    excludedMonster == null ? Set.of() : Set.of(excludedMonster), null,
                    AreaVfxSpec.onChange(AreaVfxStyles.DEBUFF));
            SemionTdApi.areaEffects().applyToMonsters(monsterRequest, target ->
                    add(target, lane, source, SuccubusBalance.spreadStacks())
                            ? AreaEffectOutcome.APPLIED : AreaEffectOutcome.UNCHANGED);
            TowerAreaEffectRequest towerRequest = new TowerAreaEffectRequest(
                    SPREAD_EFFECT, sourceEntity, center, SuccubusBalance.spreadRadius(),
                    TowerAreaTargetMode.REGISTERED, true,
                    target -> target.tower() != excludedTower, AreaVfxSpec.onChange(AreaVfxStyles.BUFF));
            SemionTdApi.areaEffects().applyToTowers(towerRequest, target ->
                    add(target.tower(), lane, source, SuccubusBalance.spreadStacks())
                            ? AreaEffectOutcome.APPLIED : AreaEffectOutcome.UNCHANGED);
            if (wakeDamage > 0.0) {
                MonsterAreaEffectRequest damageRequest = new MonsterAreaEffectRequest(
                        SPREAD_EFFECT, sourceEntity, center, SuccubusBalance.spreadRadius(),
                        excludedMonster == null ? Set.of() : Set.of(excludedMonster), null, AreaVfxSpec.none());
                TowerAreaDamage.applyResolved(source, sourceEntity, damageRequest, ignored -> wakeDamage,
                        false, (target, damage, killed) -> {}, DamageType.MAGIC);
            }
        } finally {
            propagatingWake = false;
        }
    }

    private static void syncTowerEffects(Tower tower, PlayerLane lane, DreamState state) {
        SemionTowerEntity entity = towerEntity(tower, lane);
        if (entity == null) return;
        double multiplier = hasLivingSuccubus(lane, state.sourceOwner) ? 1.0 + SuccubusBalance.amplification() : 1.0;
        entity.setPersistentEffect(TimedEffectType.TOWER_DAMAGE_BONUS, EFFECT_SOURCE,
                state.stacks * SuccubusBalance.allyDamagePerStack() * multiplier);
        entity.setPersistentEffect(TimedEffectType.TOWER_ATTACK_SPEED_BONUS, EFFECT_SOURCE,
                state.stacks * SuccubusBalance.allyAttackSpeedPerStack() * multiplier);
    }

    private static void syncMonsterEffects(SemionMonsterEntity entity, DreamState state) {
        double multiplier = hasLivingSuccubus(state.lane, state.sourceOwner) ? 1.0 + SuccubusBalance.amplification() : 1.0;
        entity.setPersistentEffect(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION, EFFECT_SOURCE,
                state.stacks * SuccubusBalance.enemyAttackSpeedPerStack() * multiplier);
        entity.setPersistentEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, EFFECT_SOURCE,
                state.stacks * SuccubusBalance.enemyMoveSpeedPerStack() * multiplier);
    }

    private static void clearStacks(DreamState state) {
        state.stacks = 0;
        state.remainingTicks = 0;
        state.asleep = false;
        state.asleepTicks = 0;
        state.sleepLostHealth = 0.0;
    }

    private static void clearStacksForWake(DreamState state) {
        clearStacks(state);
        state.immunityTicks = SuccubusBalance.awakenedImmunityTicks();
    }

    public static boolean hasLivingSuccubus(PlayerLane lane, UUID owner) {
        return livingSuccubus(lane, owner) != null;
    }

    static SuccubusTower livingSuccubus(PlayerLane lane, UUID owner) {
        if (lane == null || owner == null) return null;
        return lane.towers().stream()
                .filter(tower -> owner.equals(tower.ownerPlayer()) && tower.health() > 0.0)
                .filter(tower -> SuccubusTowers.isSuccubus(tower.type()))
                .filter(SuccubusTower.class::isInstance).map(SuccubusTower.class::cast)
                .findFirst().orElse(null);
    }

    public static List<String> detailLines(Tower tower) {
        DreamState state = tower == null ? null : TOWERS.get(TowerKey.of(tower));
        if (state == null || state.stacks <= 0 && state.immunityTicks <= 0) return List.of();
        ArrayList<String> lines = new ArrayList<>();
        lines.add("꿈: " + state.stacks + "/" + SuccubusBalance.maxStacks());
        if (state.asleep) lines.add("꿈나라: " + oneDecimal(state.asleepTicks / 20.0) + "초");
        else if (state.immunityTicks > 0) lines.add("각성: " + oneDecimal(state.immunityTicks / 20.0) + "초");
        else lines.add("꿈 지속: " + oneDecimal(state.remainingTicks / 20.0) + "초");
        return List.copyOf(lines);
    }

    public static void clearLane(PlayerLane lane) {
        if (lane == null) return;
        TOWERS.entrySet().removeIf(entry -> {
            if (entry.getValue().lane != lane) return false;
            Tower tower = lane.towers().stream().filter(candidate -> entry.getKey().equals(TowerKey.of(candidate))).findFirst().orElse(null);
            removeTowerEffects(tower, lane);
            return true;
        });
        LULLABY_READY_AT.keySet().removeIf(key -> lane.towers().stream()
                .anyMatch(tower -> key.equals(TowerKey.of(tower))));
        MONSTERS.entrySet().removeIf(entry -> {
            if (entry.getValue().lane != lane) return false;
            SemionMonsterEntity monster = monsterEntity(lane, entry.getKey());
            if (monster != null) removeMonsterEffects(monster);
            return true;
        });
    }

    public static void clearPlayer(UUID playerId) {
        if (playerId == null) return;
        TOWERS.entrySet().removeIf(entry -> {
            DreamState state = entry.getValue();
            if (!playerId.equals(entry.getKey().owner) && !playerId.equals(state.sourceOwner)) return false;
            Tower tower = state.lane == null ? null : state.lane.towers().stream()
                    .filter(candidate -> entry.getKey().equals(TowerKey.of(candidate))).findFirst().orElse(null);
            removeTowerEffects(tower, state.lane);
            return true;
        });
        LULLABY_READY_AT.keySet().removeIf(key -> playerId.equals(key.owner()));
        MONSTERS.entrySet().removeIf(entry -> {
            DreamState state = entry.getValue();
            if (!playerId.equals(state.sourceOwner)) return false;
            SemionMonsterEntity monster = monsterEntity(state.lane, entry.getKey());
            if (monster != null) removeMonsterEffects(monster);
            return true;
        });
    }

    private static void clearTower(Tower tower, PlayerLane lane) {
        if (tower == null) return;
        TowerKey key = TowerKey.of(tower);
        TOWERS.remove(key);
        LULLABY_READY_AT.remove(key);
        removeTowerEffects(tower, lane);
    }

    private static void clearMonster(SemionMonsterEntity monster) {
        if (monster == null) return;
        MONSTERS.remove(monster.getUUID());
        removeMonsterEffects(monster);
    }

    private static void removeTowerEffects(Tower tower, PlayerLane lane) {
        SemionTowerEntity entity = towerEntity(tower, lane);
        if (entity == null) return;
        entity.setPersistentEffect(TimedEffectType.TOWER_DAMAGE_BONUS, EFFECT_SOURCE, 0.0);
        entity.setPersistentEffect(TimedEffectType.TOWER_ATTACK_SPEED_BONUS, EFFECT_SOURCE, 0.0);
    }

    private static void removeMonsterEffects(SemionMonsterEntity entity) {
        entity.setPersistentEffect(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION, EFFECT_SOURCE, 0.0);
        entity.setPersistentEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, EFFECT_SOURCE, 0.0);
    }

    private static SemionTowerEntity sourceEntity(PlayerLane lane, Tower preferred, UUID owner) {
        SemionTowerEntity entity = towerEntity(preferred, lane);
        if (entity != null && entity.isAlive()) return entity;
        if (lane == null) return null;
        for (Tower tower : lane.towers()) {
            if (owner != null && !owner.equals(tower.ownerPlayer())) continue;
            entity = towerEntity(tower, lane);
            if (entity != null && entity.isAlive()) return entity;
        }
        return null;
    }

    private static SemionTowerEntity towerEntity(Tower tower, PlayerLane lane) {
        if (!(tower instanceof EntityBackedTower backed) || lane == null || lane.arenaWorld() == null
                || backed.entityId().isEmpty()) return null;
        return lane.arenaWorld().getEntity(backed.entityId().getAsInt()) instanceof SemionTowerEntity entity ? entity : null;
    }

    private static SemionMonsterEntity monsterEntity(PlayerLane lane, UUID id) {
        if (lane == null || id == null) return null;
        return lane.activeMonsters().stream().filter(monster -> monster.minecraftEntityId() >= 0)
                .map(monster -> lane.arenaWorld().getEntity(monster.minecraftEntityId()))
                .filter(SemionMonsterEntity.class::isInstance).map(SemionMonsterEntity.class::cast)
                .filter(entity -> id.equals(entity.getUUID())).findFirst().orElse(null);
    }

    private static String oneDecimal(double value) {return String.format(java.util.Locale.ROOT, "%.1f", value);}

    private record TowerKey(UUID owner, GridPosition originalPosition) {
        private static TowerKey of(Tower tower) {return new TowerKey(tower.ownerPlayer(), tower.originalPosition());}
    }

    private static final class DreamState {
        private UUID sourceOwner;
        private Tower lastSource;
        private PlayerLane lane;
        private int stacks;
        private int remainingTicks;
        private int asleepTicks;
        private int immunityTicks;
        private int sleepCount;
        private double sleepLostHealth;
        private boolean asleep;
    }
}
