package kim.biryeong.semiontd.tower.plant;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.entity.monster.MonsterDataKey;
import kim.biryeong.semiontd.augment.AugmentCombat;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Terrain effects applied to monsters standing on claimed soil.
 *
 * <p>균사 weakens what stands on it. 사암 slows attacks and attributes its periodic magic damage to
 * the living terraformer that created the tile. 잔디 and 회백토 are friendly terrain and do nothing here.
 */
public final class PlantSoilEnvironment {
    private PlantSoilEnvironment() {
    }

    public static void tick(PlayerLane lane) {
        if (lane == null || lane.arenaWorld() == null) {
            return;
        }
        UUID owner = lane.ownerPlayer();
        if (owner == null) {
            return;
        }
        long gameTime = lane.arenaWorld().getGameTime();
        if (PlantSoilStates.totalCount(owner) == 0) {
            return;
        }
        int interval = Math.max(1, globalTicks("environmentTickIntervalTicks"));
        boolean pulse = gameTime % interval == 0;
        boolean roots = lane.augmentSnapshot().has("job_plant_towers_s") && AugmentCombat.allowsTriggers();
        if (!pulse && !roots) {
            return;
        }
        if (pulse) applyMeadowGrowthShare(lane, interval);
        double fieldFrailty = pulse ? myceliumFieldFrailty(owner) : 0.0;
        double desertBurn = pulse ? desertFieldBurnPerSecond(owner) : 0.0;
        PlantTerraformTower desertSource = desertBurn > 0.0 ? desertTerraformer(lane, owner) : null;
        SemionTowerEntity desertSourceEntity = sourceEntity(lane, desertSource);
        int fieldTicks = Math.max(interval * 2, soilTicks(PlantSoil.MYCELIUM, "environmentDurationTicks"));

        for (Monster monster : List.copyOf(lane.activeMonsters())) {
            if (monster == null || !monster.isAlive() || !monster.hasMinecraftEntity()) {
                continue;
            }
            if (!(lane.arenaWorld().getEntity(monster.minecraftEntityId()) instanceof SemionMonsterEntity entity)
                    || entity.isRemoved()) {
                continue;
            }
            if (fieldFrailty > 0.0) {
                // 균사 지형의 취약은 균사를 밟지 않아도 라인의 모든 적에게 겁니다. 크기는 균사 칸 수가 정합니다.
                entity.applyTimedEffect(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS, fieldFrailty, fieldTicks);
            }
            if (desertBurn > 0.0 && desertSourceEntity != null) {
                // 사암 지형의 도트 피해도 밟지 않아도 라인의 모든 적에게 들어갑니다. 크기는 사암 칸 수가 정합니다.
                burn(desertSource, desertSourceEntity, monster, entity, desertBurn, interval);
            }
            PlantSoil soil = PlantSoilStates.soilAtColumn(owner, Mth.floor(entity.getX()), Mth.floor(entity.getZ()));
            if (soil == null) {
                continue;
            }
            if (roots) {
                MonsterDataKey<Integer> stunnedRound = new MonsterDataKey<>(Identifier.fromNamespaceAndPath(
                        "semiontd", "plant_root_entry/" + owner), Integer.class);
                int round = lane.towers().stream().findFirst().map(Tower::currentRound).orElse(0);
                if (monster.getData(stunnedRound).orElse(-1) != round) {
                    monster.setData(stunnedRound, round);
                    entity.applyTimedEffect(TimedEffectType.MONSTER_STUN, 1.0,
                            (int) lane.augmentSnapshot().parameter("job_plant_towers_s", "stunTicks", 20));
                }
            }
            if (pulse) applyEnvironment(lane, owner, monster, entity, soil, interval);
        }
    }

    /**
     * 잔디 타워들이 키운 성장 체력을 합산해 라인 안 모든 타워에게 같은 값으로 겁니다.
     *
     * <p>거리 제한이 없고 여러 잔디 타워가 있으면 그만큼 더해집니다. 합계를 한 번 계산해 모두에게
     * 같은 값으로 걸기 때문에, 최댓값만 잡는 소스 없는 효과를 써도 결과가 동일합니다.
     */
    private static void applyMeadowGrowthShare(PlayerLane lane, int intervalTicks) {
        applyGrowthShare(lane, intervalTicks, PlantSoil.MEADOW,
                PlantCombatTower::sharedGrowthBonus, TimedEffectType.TOWER_MAX_HEALTH_BONUS);
        // 회백토도 같은 방식으로 피해를 나눕니다. 잔디가 체력을, 회백토가 피해를 담당합니다.
        applyGrowthShare(lane, intervalTicks, PlantSoil.PODZOL,
                PlantCombatTower::sharedDamageGrowthBonus, TimedEffectType.TOWER_DAMAGE_BONUS);
    }

    private static void applyGrowthShare(
            PlayerLane lane,
            int intervalTicks,
            PlantSoil soil,
            java.util.function.ToDoubleFunction<PlantCombatTower> share,
            TimedEffectType effect
    ) {
        double total = 0.0;
        for (Tower tower : List.copyOf(lane.towers())) {
            if (tower instanceof PlantCombatTower plant && tower.health() > 0.0) {
                total += share.applyAsDouble(plant);
            }
        }
        if (total <= 0.0) {
            return;
        }
        // 같은 계열 타워를 늘릴수록 합계가 커지므로 라인 전체 버프에는 상한을 둡니다.
        double cap = TowerBalanceRuntime.ability(soil.configId(), "growthShareCap", 0.0);
        if (cap > 0.0) {
            total = Math.min(cap, total);
        }
        int durationTicks = Math.max(
                intervalTicks * 2,
                TowerBalanceRuntime.abilityTicks(soil.configId(), "supportDurationTicks", 0)
        );
        for (Tower tower : List.copyOf(lane.towers())) {
            if (!(tower instanceof EntityBackedTower backed) || backed.entityId().isEmpty()) {
                continue;
            }
            if (lane.arenaWorld().getEntity(backed.entityId().getAsInt()) instanceof SemionTowerEntity entity) {
                entity.applyTimedEffect(effect, total, durationTicks);
            }
        }
    }

    private static void applyEnvironment(
            PlayerLane lane,
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

        double moveSpeedReduction = soilValue(soil, "environmentMoveSpeedReduction");
        if (moveSpeedReduction > 0.0) {
            entity.applyTimedEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, moveSpeedReduction, durationTicks);
        }

        double attackSpeedReduction = soilValue(soil, "environmentAttackSpeedReduction");
        if (attackSpeedReduction > 0.0) {
            entity.applyTimedEffect(TimedEffectType.MONSTER_ATTACK_SPEED_REDUCTION, attackSpeedReduction, durationTicks);
        }

    }

    /**
     * 사암 도트 한 번. 최대 체력 비례라 라운드가 올라가 몬스터가 단단해져도 사암이 계속 값을 합니다. 피해는 사암
     * 테라포머의 것으로 쳐서 전과·막타가 그 타워에 남습니다.
     */
    private static void burn(PlantTerraformTower source, SemionTowerEntity sourceEntity, Monster monster,
            SemionMonsterEntity entity, double ratioPerSecond, int intervalTicks) {
        double damage = monster.maxHealth() * ratioPerSecond * (intervalTicks / 20.0);
        if (damage <= 0.0) {
            return;
        }
        Tower.DamageResult result = source.damageResolvedTargetResult(sourceEntity, entity, damage, DamageType.MAGIC);
        if (result.killed()) {
            source.onKill(sourceEntity, entity, damage);
        }
    }

    /**
     * 사암 칸 수에 비례한 라인 전체 도트(초당 최대 체력 비율). 칸당 {@code maxHealthDamagePerSecondPerTile},
     * 상한 {@code maxHealthDamagePerSecondCap}입니다.
     */
    public static double desertFieldBurnPerSecond(UUID owner) {
        int tiles = PlantSoilStates.count(owner, PlantSoil.DESERT);
        if (tiles <= 0) {
            return 0.0;
        }
        double perTile = soilValue(PlantSoil.DESERT, "maxHealthDamagePerSecondPerTile");
        double cap = soilValue(PlantSoil.DESERT, "maxHealthDamagePerSecondCap");
        double burn = tiles * perTile;
        return cap > 0.0 ? Math.min(cap, burn) : burn;
    }

    /** 사암 도트를 떠맡을 살아 있는 사암 테라포머. 없으면 도트도 없습니다(주인 없는 지형은 피해를 주지 않습니다). */
    private static PlantTerraformTower desertTerraformer(PlayerLane lane, UUID owner) {
        for (Tower tower : lane.towers()) {
            if (tower instanceof PlantTerraformTower terraformer && tower.health() > 0.0
                    && owner.equals(tower.ownerPlayer()) && PlantTowers.soilOf(tower.type()) == PlantSoil.DESERT) {
                return terraformer;
            }
        }
        return null;
    }

    /**
     * 균사 칸 수에 비례한 라인 전체 취약(적이 타워에게 받는 피해 증가). 칸당 {@code damageTakenBonusPerTile},
     * 상한 {@code damageTakenBonusCap}입니다. 균사 전투 타워는 지뢰라 상주하지 않으므로 딜증은 지형이 맡습니다.
     */
    public static double myceliumFieldFrailty(UUID owner) {
        int tiles = PlantSoilStates.count(owner, PlantSoil.MYCELIUM);
        if (tiles <= 0) {
            return 0.0;
        }
        double perTile = soilValue(PlantSoil.MYCELIUM, "damageTakenBonusPerTile");
        double cap = soilValue(PlantSoil.MYCELIUM, "damageTakenBonusCap");
        double bonus = tiles * perTile;
        return cap > 0.0 ? Math.min(cap, bonus) : bonus;
    }

    private static SemionTowerEntity sourceEntity(PlayerLane lane, PlantTerraformTower source) {
        if (source == null || source.entityId().isEmpty()) {
            return null;
        }
        return lane.arenaWorld().getEntity(source.entityId().getAsInt()) instanceof SemionTowerEntity entity
                && !entity.isRemoved()
                ? entity
                : null;
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
