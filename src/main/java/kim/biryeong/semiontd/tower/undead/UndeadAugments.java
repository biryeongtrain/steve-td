package kim.biryeong.semiontd.tower.undead;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaTowerTarget;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaTargetMode;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import net.minecraft.resources.ResourceLocation;

/** State belongs to the original logical tower and survives an ordinary upgrade. */
public final class UndeadAugments {
    public static final String DONATION = "job_undead_towers_s";
    public static final String BONES = "job_undead_towers_g1";
    public static final String SECOND_CHANCE = "job_undead_towers_g2";
    public static final String KING = "job_undead_towers_p";
    private static final Set<String> UNDEAD_TYPES = Set.of(UndeadTowers.T1_ZOMBIE_TOWER.id(),
            UndeadTowers.T2_ZOMBIE_TOWER.id(), UndeadTowers.T3_ZOMBIE_TOWER.id(),
            UndeadTowers.T1_SKELETON_TOWER.id(), UndeadTowers.T2_RANGED_SKELETON_TOWER.id(),
            UndeadTowers.T3_RANGED_SKELETON_TOWER.id(), UndeadTowers.T2_MELEE_TOWER.id(),
            UndeadTowers.T3_MELEE_TOWER.id(), UndeadTowers.T1_UNDEAD_ANIMAL_TOWER.id(),
            UndeadTowers.T2_UNDEAD_ANIMAL_TOWER.id());
    private static final TowerDataKey<Integer> BONE_KILLS = key("bone_kills", Integer.class);
    private static final TowerDataKey<Integer> BONE_CHARGES = key("bone_charges", Integer.class);
    private static final TowerDataKey<Integer> KING_KILLS = key("king_kills", Integer.class);
    private static final TowerDataKey<Boolean> REVIVE_USED = key("revive_used", Boolean.class);
    private static final TowerDataKey<Boolean> REVIVED = key("revived", Boolean.class);
    private static final TowerDataKey<Integer> REVIVE_TICKS = key("revive_ticks", Integer.class);
    private static final TowerDataKey<GridPosition> REVIVE_POSITION = key("revive_position", GridPosition.class);
    private static final TowerDataKey<Double> COPY_HEALTH = key("copy_health", Double.class);
    private static final TowerDataKey<Double> COPY_DAMAGE = key("copy_damage", Double.class);

    private UndeadAugments() {}

    public static boolean skeletonTarget(Tower tower) {
        if (tower == null || tower.isTemporaryCopy()) {return false;}
        String id = tower.type().id();
        return id.equals(UndeadTowers.T1_SKELETON_TOWER.id())
                || id.equals(UndeadTowers.T2_RANGED_SKELETON_TOWER.id())
                || id.equals(UndeadTowers.T3_RANGED_SKELETON_TOWER.id())
                || id.equals(UndeadTowers.T2_MELEE_TOWER.id())
                || id.equals(UndeadTowers.T3_MELEE_TOWER.id());
    }

    private static boolean undead(Tower tower) {
        return tower != null && !tower.isTemporaryCopy()
                && UNDEAD_TYPES.contains(tower.type().id());
    }

    private static boolean king(Tower tower) {
        return skeletonTarget(tower) && tower.augmentSnapshot().has(KING)
                && Objects.equals(tower.logicalId(), tower.augmentSnapshot().choice(KING).primaryTargetId());
    }

    public static double maxHealthBonus(Tower tower) {
        return king(tower) ? value(tower, KING, "statBonus", 1) : 0;
    }

    public static double damageBonus(Tower tower) {
        if (!undead(tower)) {return 0;}
        return maxHealthBonus(tower) + (tower.getDataOrDefault(REVIVED, false)
                ? value(tower, SECOND_CHANCE, "damageBonus", .6) : 0);
    }

    static double copyHealth(Tower tower, double ordinary) {
        return tower.getDataOrDefault(COPY_HEALTH, ordinary);
    }

    static double copyDamage(Tower tower, double ordinary) {
        return tower.getDataOrDefault(COPY_DAMAGE, ordinary);
    }

    static void healFromLifeSteal(SemionTowerEntity source, double amount) {
        if (source == null || amount <= 0) {return;}
        Tower tower = source.runtimeTower();
        double overflow = Math.max(0, amount - Math.max(0, source.getMaxHealth() - source.getHealth()));
        source.healTarget(source, amount);
        if (!AugmentCombat.allowsTriggers() || !undead(tower) || !tower.augmentSnapshot().has(DONATION)
                || overflow <= 0 || !source.isAlive()) {return;}
        List<AreaTowerTarget> allies = new ArrayList<>();
        var request = TowerAreaEffectRequest.aroundTower(AreaEffectIds.tower(tower, "augment_donation"), source,
                value(tower, DONATION, "radius", 4), TowerAreaTargetMode.REGISTERED_AND_CLONES, AreaVfxSpec.none());
        SemionTdApi.areaEffects().applyToTowers(request, ally -> {
            if (ally.tower().teamId() == tower.teamId() && ally.entity().isPresent()
                    && ally.entity().get().canReceiveHealing()) {allies.add(ally);}
            return AreaEffectOutcome.UNCHANGED;
        });
        double healing = overflow * value(tower, DONATION, "overflowRatio", .1);
        allies.stream().min(Comparator.comparingDouble(ally -> ally.tower().health())).ifPresent(ally -> {
            source.healTarget(ally.entity().orElseThrow(), healing);
            TowerVfxService.showSecondaryAttack(source, ally.entity().orElseThrow().position());
        });
    }

    static void onMonsterDeath(Tower tower, PlayerLane lane, Monster monster, boolean atGrowthCap) {
        if (!skeletonTarget(tower) || !AugmentCombat.allowsTriggers() || monster == null
                || monster.targetLaneId() != tower.laneId()) {return;}
        if (atGrowthCap && tower.augmentSnapshot().has(BONES)) {
            int kills = tower.getDataOrDefault(BONE_KILLS, 0) + 1;
            int interval = Math.max(1, (int) value(tower, BONES, "deathsPerCharge", 5));
            tower.setData(BONE_KILLS, kills % interval);
            tower.setData(BONE_CHARGES, tower.getDataOrDefault(BONE_CHARGES, 0) + kills / interval);
        }
        if (king(tower)) {
            int kills = tower.getDataOrDefault(KING_KILLS, 0) + 1;
            int interval = Math.max(1, (int) value(tower, KING, "deathsPerSummon", 5));
            tower.setData(KING_KILLS, kills % interval);
            if (kills >= interval && lane != null && tower.health() > 0) {summonCopies(tower, lane);}
        }
    }

    static void onBasicAttack(Tower tower, SemionTowerEntity source, SemionMonsterEntity primary, double dealt) {
        if (source == null || primary == null || dealt <= 0 || !AugmentCombat.allowsTriggers()
                || tower.isTemporaryCopy() || !consumeBoneCharge(tower)) {return;}
        var request = MonsterAreaEffectRequest.aroundTower(AreaEffectIds.tower(tower, "augment_bones"), source,
                source.attackRange(), AreaVfxSpec.none()).withFilter(target -> target != primary
                && source.isValidAttackTarget(target) && tower.canAttackTarget(source, target))
                .nearestTargets((int) value(tower, BONES, "targetCount", 3));
        List<SemionMonsterEntity> targets = new ArrayList<>();
        SemionTdApi.areaEffects().applyToMonsters(request, target -> {
            targets.add(target);
            return AreaEffectOutcome.UNCHANGED;
        });
        for (SemionMonsterEntity target : targets) {
            AugmentCombat.additionalAttack(source, target, value(tower, BONES, "damageRatio", .5));
        }
    }

    static boolean consumeBoneCharge(Tower tower) {
        int charges = tower.getDataOrDefault(BONE_CHARGES, 0);
        if (!AugmentCombat.allowsTriggers() || charges <= 0) {return false;}
        tower.setData(BONE_CHARGES, charges - 1);
        return true;
    }

    private static void summonCopies(Tower original, PlayerLane lane) {
        if (!(original instanceof EntityBackedTower backed)) {return;}
        SemionTowerEntity entity = backed.runtimeEntity(lane).orElse(null);
        if (entity == null || !entity.isAlive()) {return;}
        long living = lane.towers().stream().filter(tower -> tower.isTemporaryCopy()
                && original.logicalId().equals(tower.temporaryCopySourceId()) && tower.health() > 0).count();
        int count = Math.min((int) value(original, KING, "copiesPerSummon", 2),
                Math.max(0, (int) value(original, KING, "maxCopies", 6) - (int) living));
        double ratio = value(original, KING, "copyRatio", .35);
        double damage = entity.attackDamageAmount(null) * Math.max(0, 1 + AugmentCombat.damageBonus(original, entity)) * ratio;
        for (int i = 0; i < count; i++) {
            Tower copy = createCopy(original, original.currentMaxHealth() * ratio, damage);
            lane.addTower(copy);
        }
    }

    static Tower createCopy(Tower original, double health, double damage) {
        Tower copy = ProductionTowerCatalog.find(original.type().id()).orElseThrow()
                .create(original.ownerPlayer(), original.teamId(), original.laneId(), original.position());
        copy.markTemporaryCopy(original.logicalId());
        copy.setData(COPY_HEALTH, health);
        copy.setData(COPY_DAMAGE, damage);
        copy.syncHealth(health);
        if (original.deployedAtFinalDefense()) {copy.moveToFinalDefense(null, original.position());}
        return copy;
    }

    public static void onDeath(PlayerLane lane, Tower tower) {
        if (!undead(tower) || !tower.augmentSnapshot().has(SECOND_CHANCE)
                || tower.getDataOrDefault(REVIVE_USED, false)) {return;}
        tower.setData(REVIVE_USED, true);
        tower.setData(REVIVE_TICKS, (int) value(tower, SECOND_CHANCE, "reviveDelayTicks", 20));
        tower.setData(REVIVE_POSITION, tower.position());
    }

    public static void tick(PlayerLane lane) {
        for (Tower tower : lane.towers()) {tickRevival(lane, tower);}
    }

    public static boolean hasPendingRevival(Tower tower) {
        return tower.getDataOrDefault(REVIVE_TICKS, 0) > 0;
    }

    static void tickRevival(PlayerLane lane, Tower tower) {
        int ticks = tower.getDataOrDefault(REVIVE_TICKS, 0);
        if (ticks <= 0) {return;}
        tower.setData(REVIVE_TICKS, ticks - 1);
        if (ticks > 1) {return;}
        tower.onRemoved(lane);
        tower.setData(REVIVED, true);
        tower.syncPosition(tower.getDataOrDefault(REVIVE_POSITION, tower.position()));
        tower.syncHealth(tower.currentMaxHealth() * value(tower, SECOND_CHANCE, "reviveHealthRatio", .6));
        tower.markRevived();
        tower.onPlaced(lane);
    }

    public static void resetRound(PlayerLane lane) {
        for (Tower tower : lane.towers()) {resetTower(tower);}
    }

    static void resetTower(Tower tower) {
        tower.removeData(BONE_KILLS);
        tower.removeData(BONE_CHARGES);
        tower.removeData(KING_KILLS);
        tower.removeData(REVIVE_USED);
        tower.removeData(REVIVED);
        tower.removeData(REVIVE_TICKS);
        tower.removeData(REVIVE_POSITION);
    }

    public static void onRemoved(PlayerLane lane, Tower tower) {
        resetTower(tower);
    }

    public static List<String> detailLines(Tower tower) {
        if (!undead(tower)) {return List.of();}
        List<String> lines = new ArrayList<>();
        if (tower.augmentSnapshot().has(BONES)) {lines.add("뼈가 실린 공격 충전 " + tower.getDataOrDefault(BONE_CHARGES, 0));}
        int ticks = tower.getDataOrDefault(REVIVE_TICKS, 0);
        if (ticks > 0) {lines.add("두번째 기회 부활 " + ticks / 20.0 + "초");}
        if (tower.getDataOrDefault(REVIVED, false)) {lines.add("두번째 기회 공격력 +" + Math.round(value(tower, SECOND_CHANCE, "damageBonus", .6) * 100) + "%");}
        return List.copyOf(lines);
    }

    private static double value(Tower tower, String card, String parameter, double fallback) {
        return tower.augmentSnapshot().parameter(card, parameter, fallback);
    }

    private static <T> TowerDataKey<T> key(String name, Class<T> type) {
        return TowerDataKey.of(ResourceLocation.fromNamespaceAndPath("semiontd", "undead_augment/" + name), type);
    }
}
