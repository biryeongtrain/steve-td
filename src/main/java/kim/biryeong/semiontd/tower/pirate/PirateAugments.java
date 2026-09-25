package kim.biryeong.semiontd.tower.pirate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import kim.biryeong.semiontd.tower.succubus.SuccubusDreams;
import net.minecraft.resources.ResourceLocation;

public final class PirateAugments {
    public static final String LOOT = "job_pirate_s";
    public static final String LOCKSMITH = "job_pirate_g1";
    public static final String FLEET = "job_pirate_g2";
    public static final String CANNON = "job_pirate_p";
    private static final TowerDataKey<Integer> OPENING = TowerDataKey.of(
            ResourceLocation.fromNamespaceAndPath("semiontd", "pirate_augment_opening"), Integer.class);
    private static final Map<UUID, State> STATES = new java.util.concurrent.ConcurrentHashMap<>();

    private PirateAugments() { }

    public static void clearPlayer(UUID owner) { STATES.remove(owner); }

    public static void onSelected(PlayerLane lane, String cardId, AugmentConfig config) {
        if (lane == null || !(CANNON.equals(cardId) || ("semiontd:" + CANNON).equals(cardId))) return;
        State state = state(lane);
        if (!state.initialCannonGranted) {
            state.initialCannonGranted = true;
            state.cannons += (int) config.parameter(CANNON, "initialStacks", 1);
        }
    }

    public static long placementCost(PlayerLane lane, TowerType type, long normalCost) {
        return lane != null && lane.augmentSnapshot().has(LOOT) && PirateTowers.matches(type, PirateTowers.SHABBY_CHEST)
                && freeChestAvailable(lane.ownerPlayer()) ? 0 : normalCost;
    }

    public static void onPlaced(PlayerLane lane, Tower tower) {
        if (lane != null && lane.towers().contains(tower) && lane.ownerPlayer().equals(tower.ownerPlayer())
                && tower.paidMineralCost() == 0 && PirateTowers.matches(tower.type(), PirateTowers.SHABBY_CHEST)
                && lane.augmentSnapshot().has(LOOT)) state(lane).freeChest = false;
    }

    static long maturityReward(Tower chest, long reward) {
        return chest.augmentSnapshot().has(LOCKSMITH)
                ? (long) Math.floor(reward * (1 + chest.augmentSnapshot().parameter(LOCKSMITH, "rewardBonus", .5))) : reward;
    }

    static void onMatured(PlayerLane lane, PirateTower chest, int round) {
        if (!AugmentCombat.allowsTriggers()) return;
        State state = state(lane);
        if (!state.settled.add(chest.logicalId())) return;
        if (chest.augmentSnapshot().has(LOOT) && state.lootRound != round) {
            state.lootRound = round;
            state.freeChest = true;
        }
        if (chest.augmentSnapshot().has(CANNON)) {
            state.opened++;
            int threshold = (int) chest.augmentSnapshot().parameter(CANNON, "chestsPerStack", 3);
            if (state.opened >= threshold) {
                state.opened -= threshold;
                state.cannons++;
                var player = PirateStates.player(chest.ownerPlayer());
                if (player != null) player.economy().addDiamond((long) chest.augmentSnapshot().parameter(CANNON, "diamondReward", 100));
            }
        }
        if (chest.augmentSnapshot().has(LOCKSMITH) && state.locksmithRound != round) {
            state.locksmithRound = round;
            for (Tower candidate : List.copyOf(lane.towers())) {
                if (candidate instanceof PirateTower remaining && chest.ownerPlayer().equals(remaining.ownerPlayer())
                        && PirateTowers.isChest(remaining.type())) {
                    remaining.advanceChestTimer(lane, (int) chest.augmentSnapshot().parameter(LOCKSMITH, "roundReduction", 1), round);
                }
            }
        }
    }

    static void startTowerWave(Tower tower) {
        tower.setData(OPENING, tower.augmentSnapshot().has(FLEET)
                ? (int) tower.augmentSnapshot().parameter(FLEET, "openingAttacks", 5) : 0);
    }

    static void onAttack(Tower tower, SemionTowerEntity source, SemionMonsterEntity target, double dealt) {
        if (!AugmentCombat.allowsTriggers() || !tower.augmentSnapshot().has(FLEET) || dealt <= 0
                || source == null || target == null || !fleetTower(tower.type()) || tower.attachedLane() == null) return;
        int remaining = tower.getData(OPENING).orElse(0);
        if (remaining <= 0) return;
        tower.setData(OPENING, remaining - 1);
        State state = state(tower.attachedLane());
        int spacing = (int) tower.augmentSnapshot().parameter(FLEET, "shotSpacingTicks", 4);
        for (int shot = 1; shot <= (int) tower.augmentSnapshot().parameter(FLEET, "extraShots", 2); shot++) {
            state.shells.add(new Shell(tower, target, source.level().getGameTime() + (long) shot * spacing));
        }
    }

    public static void beginWave(PlayerLane lane) {
        if (lane == null) return;
        State state = state(lane);
        state.shells.clear();
        state.bombardmentSource = null;
        state.shots = 0;
        if (!lane.augmentSnapshot().has(CANNON) || state.cannons <= 0) return;
        state.cannons--;
        state.bombardmentSource = lane.towers().stream()
                .filter(tower -> lane.ownerPlayer().equals(tower.ownerPlayer()) && !tower.isAugmentTower()
                        && !tower.isTemporaryCopy() && tower.slotWeight() > 0 && tower.type().damage() > 0 && tower.type().range() > 0)
                .filter(EntityBackedTower.class::isInstance).map(EntityBackedTower.class::cast)
                .map(tower -> tower.runtimeEntity(lane).orElse(null)).filter(source -> source != null && source.isAlive())
                .max(Comparator.comparingDouble(source -> source.attackDamageAmount(null))).orElse(null);
        state.bombardmentDamage = state.bombardmentSource == null ? 0 : state.bombardmentSource.attackDamageAmount(null);
        state.shots = (int) lane.augmentSnapshot().parameter(CANNON, "shots", 5);
        state.nextShot = now(lane) + (int) lane.augmentSnapshot().parameter(CANNON, "intervalTicks", 40);
    }

    public static void endWave(PlayerLane lane) {
        if (lane == null) return;
        State state = STATES.get(lane.ownerPlayer());
        if (state != null) {
            state.shells.clear();
            state.shots = 0;
            state.bombardmentSource = null;
        }
        lane.towers().stream().filter(tower -> lane.ownerPlayer().equals(tower.ownerPlayer()))
                .forEach(tower -> tower.setData(OPENING, 0));
    }

    public static void tick(PlayerLane lane) {
        if (lane != null && lane.arenaWorld() != null) tick(lane, now(lane));
    }

    static void tick(PlayerLane lane, long time) {
        State state = STATES.get(lane.ownerPlayer());
        if (state == null) return;
        List<Shell> ready = state.shells.stream().filter(shell -> time >= shell.at()).toList();
        state.shells.removeAll(ready);
        for (Shell shell : ready) fireShell(lane, shell);
        if (state.shots <= 0 || time < state.nextShot) return;
        state.shots--;
        state.nextShot += (int) lane.augmentSnapshot().parameter(CANNON, "intervalTicks", 40);
        SemionTowerEntity source = state.bombardmentSource;
        if (source == null) return;
        if (source.runtimeTower() instanceof EntityBackedTower tower) source = tower.runtimeEntity(lane).orElse(source);
        SemionTowerEntity cannonSource = source;
        SemionMonsterEntity target = PirateStates.combatLanes(lane).stream().flatMap(other -> other.activeMonsters().stream())
                .map(monster -> lane.arenaWorld().getEntity(monster.minecraftEntityId()))
                .filter(SemionMonsterEntity.class::isInstance).map(SemionMonsterEntity.class::cast)
                .filter(cannonSource::isValidAttackTarget)
                .max(Comparator.comparingDouble(enemy -> enemy.runtimeMonster().laneProgress())).orElse(null);
        if (target == null) return;
        double damage = state.bombardmentDamage * lane.augmentSnapshot().parameter(CANNON, "damageRatio", 1.6);
        var request = MonsterAreaEffectRequest.aroundTarget(AreaEffectIds.tower(source.runtimeTower(), "augment_cannon"),
                source, target, lane.augmentSnapshot().parameter(CANNON, "radius", 3), AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH))
                .including(target.getUUID()).nearestTargets((int) lane.augmentSnapshot().parameter(CANNON, "maxTargets", 12));
        AugmentCombat.runWithoutTriggers(() -> TowerAreaDamage.applyResolved(cannonSource.runtimeTower(), cannonSource,
                request, ignored -> damage, true, (enemy, dealt, killed) -> { }, DamageType.PHYSICAL));
    }

    private static void fireShell(PlayerLane lane, Shell shell) {
        if (!lane.towers().contains(shell.tower()) || !(shell.tower() instanceof EntityBackedTower tower)) return;
        SemionTowerEntity source = tower.runtimeEntity(lane).orElse(null);
        if (source == null || !source.isAlive() || source.attackRange() <= 0 || SuccubusDreams.isAsleep(source)) return;
        SemionMonsterEntity target = shell.target();
        if (!validTarget(source, target)) {
            List<SemionMonsterEntity> candidates = new ArrayList<>();
            SemionTdApi.areaEffects().applyToMonsters(MonsterAreaEffectRequest.aroundTower(
                    AreaEffectIds.tower(tower, "augment_shell_target"), source, source.attackRange(), AreaVfxSpec.none())
                    .withFilter(enemy -> validTarget(source, enemy)), enemy -> {
                candidates.add(enemy);
                return AreaEffectOutcome.UNCHANGED;
            });
            target = tower.selectAttackTarget(source, candidates).orElseGet(() -> candidates.stream()
                    .max(Comparator.comparingDouble((SemionMonsterEntity enemy) -> enemy.runtimeMonster().targetPriorityScore())
                            .thenComparingDouble(enemy -> -source.distanceToSqr(enemy))).orElse(null));
        }
        if (target == null) return;
        SemionMonsterEntity primary = target;
        double ratio = tower.augmentSnapshot().parameter(FLEET, "damageRatio", .8);
        double damage = source.attackDamageAmount(primary) * ratio;
        int cap = (int) tower.augmentSnapshot().parameter(FLEET, "maxTargets", 12);
        var request = MonsterAreaEffectRequest.aroundTarget(AreaEffectIds.tower(tower, "augment_shell"), source, primary,
                tower.augmentSnapshot().parameter(FLEET, "radius", 2.5), AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH))
                .nearestTargets(Math.max(1, cap - 1));
        AugmentCombat.runWithoutTriggers(() -> {
            AugmentCombat.additionalAttack(source, primary, ratio);
            if (cap > 1) TowerAreaDamage.apply(tower, source, request, ignored -> damage, true,
                    (enemy, dealt, killed) -> { }, DamageType.PHYSICAL);
        });
    }

    private static boolean validTarget(SemionTowerEntity source, SemionMonsterEntity target) {
        return source.isValidAttackTarget(target) && source.distanceToSqr(target) <= source.attackRange() * source.attackRange()
                && source.runtimeTower().canAttackTarget(source, target);
    }

    static boolean fleetTower(TowerType type) {
        return PirateTowers.isAdmiral(type) || PirateTowers.matches(type, PirateTowers.NAVIGATOR)
                || PirateTowers.matches(type, PirateTowers.FIRST_NAVIGATOR);
    }

    public static boolean freeChestAvailable(UUID owner) { State state = STATES.get(owner); return state != null && state.freeChest; }
    public static int cannonStacks(UUID owner) { State state = STATES.get(owner); return state == null ? 0 : state.cannons; }
    static int pendingShells(UUID owner) { State state = STATES.get(owner); return state == null ? 0 : state.shells.size(); }
    static int pendingCannonShots(UUID owner) { State state = STATES.get(owner); return state == null ? 0 : state.shots; }
    static double cannonDamage(UUID owner) { State state = STATES.get(owner); return state == null ? 0 : state.bombardmentDamage; }
    private static long now(PlayerLane lane) { return lane.arenaWorld() == null ? 0 : lane.arenaWorld().getGameTime(); }
    private static State state(PlayerLane lane) { return STATES.computeIfAbsent(lane.ownerPlayer(), ignored -> new State()); }
    private record Shell(Tower tower, SemionMonsterEntity target, long at) { }
    private static final class State {
        private boolean initialCannonGranted;
        private boolean freeChest;
        private int lootRound = -1;
        private int locksmithRound = -1;
        private int opened;
        private int cannons;
        private final Set<UUID> settled = new HashSet<>();
        private final List<Shell> shells = new ArrayList<>();
        private SemionTowerEntity bombardmentSource;
        private double bombardmentDamage;
        private int shots;
        private long nextShot;
    }
}
