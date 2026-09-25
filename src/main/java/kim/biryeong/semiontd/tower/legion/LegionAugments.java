package kim.biryeong.semiontd.tower.legion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Per-wave material ownership is retained even after different bodies' clones merge. */
public final class LegionAugments {
    public static final String TRICK = "job_legion_towers_s";
    public static final String MERGE = "job_legion_towers_g1";
    public static final String CHARISMA = "job_legion_towers_g2";
    public static final String FACTORY = "job_legion_towers_p";
    private static final TowerDataKey<Clone> CLONE = key("clone", Clone.class);
    private static final TowerDataKey<Integer> ATTACKS = key("attacks", Integer.class);
    private static final TowerDataKey<Boolean> SAVED = key("saved", Boolean.class);
    private static final Map<PlayerLane, Wave> WAVES = new HashMap<>();

    private LegionAugments() {}

    public static void onWaveStarted(PlayerLane lane) {
        clear(lane);
        if (lane == null) return;
        for (Tower tower : lane.towers()) {
            tower.removeData(ATTACKS);
            tower.removeData(SAVED);
        }
        if (!lane.augmentSnapshot().has(TRICK) && !lane.augmentSnapshot().has(MERGE)
                && !lane.augmentSnapshot().has(CHARISMA) && !lane.augmentSnapshot().has(FACTORY)) return;
        Tower chosen = lane.towers().stream().filter(tower -> !tower.isAugmentTower() && tower.health() > 0.0)
                .max(Comparator.comparingLong(Tower::paidMineralCost)
                        .thenComparing(tower -> tower.logicalId().toString())).orElse(null);
        WAVES.put(lane, new Wave(chosen == null ? null : chosen.logicalId()));
    }

    static boolean register(IllusionSummonerTower summoner, PlayerLane lane, Tower original,
                            SemionTowerEntity entity, IllusionProfile profile) {
        Wave wave = WAVES.get(lane);
        if (wave == null) return false;
        Material material = new Material(original.logicalId(), 1, entity.runtimeTower().currentMaxHealth(),
                entity.attackDamageAmount(null), profile.durationTicks() <= 0 ? Integer.MAX_VALUE : wave.ticks + profile.durationTicks());
        register(summoner, lane, entity, material);
        return true;
    }

    static void register(IllusionSummonerTower summoner, PlayerLane lane, SemionTowerEntity entity, Material material) {
        Wave wave = WAVES.get(lane);
        if (wave == null || material.expiresAt <= wave.ticks) {
            entity.discard();
            return;
        }
        Clone clone = new Clone(summoner, entity, material);
        entity.runtimeTower().setData(CLONE, clone);
        wave.clones.add(clone);
        if (lane.augmentSnapshot().has(MERGE)) {
            Clone survivor = wave.clones.stream().filter(other -> other != clone && alive(other)
                            && sameType(other.entity.runtimeTower(), entity.runtimeTower()))
                    .findFirst().orElse(null);
            if (survivor != null) {
                snapshotMaterials(survivor);
                double health = survivor.entity.getHealth() + entity.getHealth();
                survivor.materials.addAll(clone.materials);
                rebuild(survivor, lane, health);
                entity.discard();
                wave.clones.remove(clone);
            } else {
                rebuild(clone, lane, entity.getHealth());
            }
        }
        if (wave.finalDefense) moveToFinalDefense(lane);
    }

    public static boolean sameType(Tower first, Tower second) {
        return first != null && second != null && first.ownerPlayer().equals(second.ownerPlayer())
                && first.type().id().replace("#illusion", "").equals(second.type().id().replace("#illusion", ""));
    }

    public static double attackDamage(Tower tower, double damage) {
        Clone clone = tower == null ? null : tower.getData(CLONE).orElse(null);
        if (clone == null) return damage;
        damage *= clone.damageScale;
        return clone.empowered && AugmentCombat.allowsTriggers()
                ? damage * tower.augmentSnapshot().parameter(CHARISMA, "damageRatio", 3.0) : damage;
    }

    public static void onAttack(Tower tower, SemionTowerEntity entity, SemionMonsterEntity target,
                                double attemptedDamage, double dealtDamage) {
        if (tower == null || entity == null || dealtDamage <= 0.0) return;
        PlayerLane lane = tower.attachedLane();
        Wave wave = WAVES.get(lane);
        if (wave == null) return;
        Clone clone = tower.getData(CLONE).orElse(null);
        if (clone != null) {
            snapshotMaterials(clone);
            // Factory is the explicit exception: one generation per material per attack.
            if (tower.augmentSnapshot().has(FACTORY)) {
                for (Material material : List.copyOf(clone.materials)) {
                    if (material.reproduced || material.generation >= tower.augmentSnapshot().parameter(FACTORY, "maxGeneration", 3)
                            || !material.original.equals(wave.factorySource)) continue;
                    material.reproduced = true;
                    if (wave.additional >= tower.augmentSnapshot().parameter(FACTORY, "maxAdditional", 12)) continue;
                    wave.additional++;
                    double ratio = tower.augmentSnapshot().parameter(FACTORY, "childRatio", .80);
                    Material child = new Material(material.original, material.generation + 1,
                            material.maxHealth * ratio, material.damage * ratio, material.expiresAt);
                    IllusionCloneSpawnQueue.enqueueChild(clone.summoner, lane, tower, child,
                            entity.position(), wave);
                }
            }
            if (!AugmentCombat.allowsTriggers()) return;
            clone.empowered = false;
            if (tower.augmentSnapshot().has(MERGE) && target != null) {
                MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(AreaEffectIds.tower(tower, "merged_clone"),
                        entity, target.position(), tower.augmentSnapshot().parameter(MERGE, "radius", 2),
                        Set.of(), null, AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH));
                AugmentCombat.runWithoutTriggers(() -> TowerAreaDamage.apply(tower, entity, request,
                        ignored -> attemptedDamage * tower.augmentSnapshot().parameter(MERGE, "damageRatio", .25),
                        false, (enemy, damage, killed) -> tower.recordAugmentSpecialDamage(damage), tower.primaryDamageType()));
            }
            return;
        }
        if (!AugmentCombat.allowsTriggers() || !tower.augmentSnapshot().has(CHARISMA)
                || !lane.towers().contains(tower)) return;
        int attacks = tower.getDataOrDefault(ATTACKS, 0) + 1;
        if (attacks >= tower.augmentSnapshot().parameter(CHARISMA, "attacks", 5)) {
            attacks = 0;
            for (Clone candidate : wave.clones) {
                if (alive(candidate) && candidate.materials.stream().anyMatch(material -> material.original.equals(tower.logicalId()))) {
                    candidate.empowered = true;
                }
            }
        }
        tower.setData(ATTACKS, attacks);
    }

    public static boolean preventLethal(Tower tower, SemionTowerEntity entity, double damage) {
        if (!AugmentCombat.allowsTriggers() || tower == null || entity == null || !tower.augmentSnapshot().has(TRICK)
                || tower.hasData(CLONE) || tower.getDataOrDefault(SAVED, false)
                || damage < entity.getHealth() + entity.getAbsorptionAmount()) return false;
        Wave wave = WAVES.get(tower.attachedLane());
        if (wave == null) return false;
        Clone own = wave.clones.stream().filter(LegionAugments::alive)
                .filter(clone -> clone.materials.stream().anyMatch(material -> material.original.equals(tower.logicalId())))
                .findFirst().orElse(null);
        if (own == null) return false;
        own.entity.discard();
        wave.clones.remove(own);
        tower.setData(SAVED, true);
        double previous = entity.getHealth();
        tower.syncHealth(Math.min(tower.currentMaxHealth(), previous
                + tower.currentMaxHealth() * tower.augmentSnapshot().parameter(TRICK, "healRatio", .30)));
        entity.setHealth((float) tower.health());
        tower.recordHealingDone(tower.health() - previous);
        return true;
    }

    public static void tick(PlayerLane lane) {
        Wave wave = WAVES.get(lane);
        if (wave == null) return;
        wave.ticks++;
        for (Clone clone : List.copyOf(wave.clones)) {
            if (!alive(clone)) {
                clone.entity.discard();
                wave.clones.remove(clone);
                continue;
            }
            double previousMax = clone.materials.stream().mapToDouble(material -> material.maxHealth).sum();
            boolean expired = clone.materials.removeIf(material -> material.expiresAt <= wave.ticks);
            if (clone.materials.isEmpty()) {
                clone.entity.discard();
                wave.clones.remove(clone);
                continue;
            }
            if (expired) {
                double remainingMax = clone.materials.stream().mapToDouble(material -> material.maxHealth).sum();
                rebuild(clone, lane, clone.entity.getHealth() * remainingMax / previousMax);
            }
            Tower runtime = clone.entity.runtimeTower();
            runtime.syncHealth(clone.entity.getHealth());
            runtime.syncPosition(GridPosition.from(BlockPos.containing(clone.entity.getX(), clone.entity.getY() - 1, clone.entity.getZ())));
            runtime.tick(lane);
            clone.entity.syncTowerState(runtime);
        }
    }

    public static void onRemoved(Tower original, PlayerLane lane) {
        Wave wave = WAVES.get(lane);
        if (wave == null || original == null) return;
        IllusionCloneSpawnQueue.cancelAugmentChildren(lane, original.logicalId());
        for (Clone clone : List.copyOf(wave.clones)) {
            double previousMax = clone.materials.stream().mapToDouble(material -> material.maxHealth).sum();
            if (!clone.materials.removeIf(material -> material.original.equals(original.logicalId()))) continue;
            if (clone.materials.isEmpty()) {
                clone.entity.discard();
                wave.clones.remove(clone);
            } else {
                double remainingMax = clone.materials.stream().mapToDouble(material -> material.maxHealth).sum();
                rebuild(clone, lane, clone.entity.getHealth() * remainingMax / previousMax);
            }
        }
    }

    static void moveToFinalDefense(PlayerLane lane) {
        Wave wave = WAVES.get(lane);
        if (wave == null) return;
        wave.finalDefense = true;
        for (Clone clone : wave.clones) {
            if (!alive(clone) || clone.finalDefense) continue;
            clone.finalDefense = true;
            Tower runtime = clone.entity.runtimeTower();
            GridPosition position = lane.nextFinalDefenseTowerPosition(runtime);
            runtime.moveToFinalDefense(lane, position);
            clone.entity.syncTowerState(runtime);
            clone.entity.setPos(position.x() + .5, position.y() + 1, position.z() + .5);
            clone.entity.getNavigation().stop();
        }
    }

    public static void clear(PlayerLane lane) {
        IllusionCloneSpawnQueue.cancelAugmentChildren(lane, null);
        Wave wave = WAVES.remove(lane);
        if (wave == null) return;
        for (Clone clone : wave.clones) clone.entity.discard();
        wave.clones.clear();
    }

    public static List<String> detailLines(Tower tower) {
        if (tower == null) return List.of();
        List<String> lines = new ArrayList<>();
        Wave wave = WAVES.get(tower.attachedLane());
        if (tower.augmentSnapshot().has(TRICK) && !tower.hasData(CLONE)) {
            lines.add("트릭 쇼: " + (tower.getDataOrDefault(SAVED, false) ? "이번 라운드 사용" : "대기"));
        }
        if (tower.augmentSnapshot().has(CHARISMA) && !tower.hasData(CLONE)) {
            lines.add("카리스마: " + tower.getDataOrDefault(ATTACKS, 0) + "/"
                    + (int) tower.augmentSnapshot().parameter(CHARISMA, "attacks", 5));
        }
        if (wave != null && tower.augmentSnapshot().has(FACTORY) && tower.logicalId().equals(wave.factorySource)) {
            lines.add("복제 공장 원본 · 추가 복제: " + wave.additional + "/"
                    + (int) tower.augmentSnapshot().parameter(FACTORY, "maxAdditional", 12));
        }
        Clone clone = tower.getData(CLONE).orElse(null);
        if (clone != null) {
            lines.add("합체 재료: " + clone.materials.size() + "기 · 증식 가능: "
                    + clone.materials.stream().filter(material -> !material.reproduced
                    && material.generation < tower.augmentSnapshot().parameter(FACTORY, "maxGeneration", 3)
                    && wave != null && material.original.equals(wave.factorySource)).count() + "기");
            if (clone.empowered) lines.add("카리스마: 다음 공격 강화");
        }
        return List.copyOf(lines);
    }

    static boolean validChild(PlayerLane lane, Wave expected, Material material) {
        return WAVES.get(lane) == expected && material.expiresAt > expected.ticks
                && lane.towers().stream().anyMatch(tower -> tower.logicalId().equals(material.original));
    }

    static List<SemionTowerEntity> clones(PlayerLane lane) {
        Wave wave = WAVES.get(lane);
        return wave == null ? List.of() : wave.clones.stream().filter(LegionAugments::alive).map(clone -> clone.entity).toList();
    }

    static int additionalSpawns(PlayerLane lane) {
        Wave wave = WAVES.get(lane);
        return wave == null ? 0 : wave.additional;
    }

    static EntityVisual originalVisual(Tower tower) {
        Clone clone = tower.getData(CLONE).orElse(null);
        return clone == null ? tower.visual() : clone.visual;
    }

    private static void snapshotMaterials(Clone clone) {
        double health = clone.materials.stream().mapToDouble(material -> material.maxHealth).sum();
        double damage = clone.materials.stream().mapToDouble(material -> material.damage).sum();
        double[] attack = {0};
        AugmentCombat.runWithoutTriggers(() -> attack[0] = clone.entity.attackDamageAmount(null));
        for (Material material : clone.materials) {
            material.maxHealth *= clone.entity.runtimeTower().currentMaxHealth() / health;
            if (damage > 0) material.damage *= attack[0] / damage;
        }
    }

    private static boolean alive(Clone clone) {
        return clone.entity.isAlive() && !clone.entity.isRemoved() && clone.entity.runtimeTower().health() > 0;
    }

    private static void rebuild(Clone clone, PlayerLane lane, double currentHealth) {
        Tower runtime = clone.entity.runtimeTower();
        TowerType type = runtime.type();
        double health = clone.materials.stream().mapToDouble(material -> material.maxHealth).sum();
        double damage = clone.materials.stream().mapToDouble(material -> material.damage).sum();
        double baseHealth = (health / (1 + clone.entity.activeEffectMagnitude(TimedEffectType.TOWER_TRAIT_MAX_HEALTH_BONUS))
                - clone.entity.activeEffectMagnitude(TimedEffectType.TOWER_FLAT_MAX_HEALTH_BONUS)
                + clone.entity.activeEffectMagnitude(TimedEffectType.TOWER_FLAT_MAX_HEALTH_REDUCTION))
                / (1 + clone.entity.activeEffectMagnitude(TimedEffectType.TOWER_MAX_HEALTH_BONUS));
        boolean merged = lane.augmentSnapshot().has(MERGE);
        EntityVisual visual = clone.visual.withScale(clone.visual.scale()
                * (merged ? 1 + clone.materials.size() * lane.augmentSnapshot().parameter(MERGE, "scalePerClone", .05) : 1));
        runtime.refreshType(new TowerType(type.id(), type.displayName(), type.category(), 0, Math.max(.01, baseHealth), type.range(), damage,
                type.attackIntervalTicks(), type.aggroPriority(), type.description(), visual, List.of(), type.primaryDamageType()), lane);
        clone.entity.refreshMaxHealthEffects(false);
        runtime.syncHealth(currentHealth);
        clone.entity.syncTowerState(runtime);
        clone.damageScale = 1;
        double[] actualDamage = {0};
        AugmentCombat.runWithoutTriggers(() -> actualDamage[0] = clone.entity.attackDamageAmount(null));
        if (actualDamage[0] > 0) clone.damageScale = damage / actualDamage[0];
        if (merged) clone.entity.useAttackTargetFrom(null);
    }

    private static <T> TowerDataKey<T> key(String path, Class<T> type) {
        return TowerDataKey.of(ResourceLocation.fromNamespaceAndPath("semion-td", "legion_augment_" + path), type);
    }

    static final class Wave {
        final UUID factorySource;
        final List<Clone> clones = new ArrayList<>();
        int additional;
        int ticks;
        boolean finalDefense;

        Wave(UUID factorySource) {this.factorySource = factorySource;}
    }

    static final class Material {
        final UUID original;
        final int generation;
        double maxHealth;
        double damage;
        final int expiresAt;
        boolean reproduced;

        Material(UUID original, int generation, double maxHealth, double damage, int expiresAt) {
            this.original = original;
            this.generation = generation;
            this.maxHealth = maxHealth;
            this.damage = damage;
            this.expiresAt = expiresAt;
        }
    }

    private static final class Clone {
        final IllusionSummonerTower summoner;
        final SemionTowerEntity entity;
        final EntityVisual visual;
        final List<Material> materials = new ArrayList<>();
        boolean empowered;
        boolean finalDefense;
        double damageScale = 1;

        Clone(IllusionSummonerTower summoner, SemionTowerEntity entity, Material material) {
            this.summoner = summoner;
            this.entity = entity;
            this.visual = entity.runtimeTower().visual();
            materials.add(material);
        }
    }
}
