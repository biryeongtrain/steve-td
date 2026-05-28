package kim.biryeong.semiontd.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import kim.biryeong.semiontd.tower.legion.LegionTowers;
import kim.biryeong.semiontd.tower.undead.UndeadTowers;
import kim.biryeong.semiontd.tower.villager.VillagerTowers;
import kim.biryeong.semiontd.tower.warlock.WarlockTowers;

public record TowerBalanceConfig(
        Map<String, TowerStats> towers,
        Map<String, Long> upgradeCosts,
        Map<String, Map<String, Double>> abilities
) {
    public TowerBalanceConfig {
        towers = towers == null ? Map.of() : copyTowerStats(towers);
        upgradeCosts = upgradeCosts == null ? Map.of() : copyUpgradeCosts(upgradeCosts);
        abilities = abilities == null ? Map.of() : copyAbilities(abilities);
    }

    public static TowerBalanceConfig defaultConfig() {
        LinkedHashMap<String, TowerStats> towers = new LinkedHashMap<>();
        addTower(towers, VillagerTowers.T1_SPLASH_TOWER);
        addTower(towers, VillagerTowers.T2_LIBRARIAN_TOWER);
        addTower(towers, VillagerTowers.T3_CLERIC_TOWER);
        addTower(towers, VillagerTowers.T1_GOLEM_TOWER);
        addTower(towers, VillagerTowers.T2_GOLEM_TOWER);
        addTower(towers, VillagerTowers.T3_GOLEM_TOWER);
        addTower(towers, VillagerTowers.T1_ALLAY_TOWER);
        addTower(towers, VillagerTowers.T2_ALLAY_TOWER);
        addTower(towers, VillagerTowers.T2_WEAPON_SMITH_TOWER);
        addTower(towers, VillagerTowers.T3_ARMORER_TOWER);
        addTower(towers, VillagerTowers.T3_WEAPON_SMITH_TOWER);
        addTower(towers, VillagerTowers.T1_CAT_TOWER);
        addTower(towers, VillagerTowers.T2_ANTI_TANKER_CAT_TOWER);
        addTower(towers, VillagerTowers.T2_LANE_CLEAR_CAT_TOWER);
        addTower(towers, VillagerTowers.T3_ANTI_TANKER_CAT_TOWER);
        addTower(towers, VillagerTowers.T3_LANE_CLEAR_CAT_TOWER);
        addTower(towers, UndeadTowers.T1_ZOMBIE_TOWER);
        addTower(towers, UndeadTowers.T2_ZOMBIE_TOWER);
        addTower(towers, UndeadTowers.T3_ZOMBIE_TOWER);
        addTower(towers, UndeadTowers.T1_SKELETON_TOWER);
        addTower(towers, UndeadTowers.T2_RANGED_SKELETON_TOWER);
        addTower(towers, UndeadTowers.T2_MELEE_TOWER);
        addTower(towers, UndeadTowers.T3_RANGED_SKELETON_TOWER);
        addTower(towers, UndeadTowers.T3_MELEE_TOWER);
        addTower(towers, UndeadTowers.T1_UNDEAD_ANIMAL_TOWER);
        addTower(towers, UndeadTowers.T2_UNDEAD_ANIMAL_TOWER);
        addTower(towers, AnimalTowers.T1_PIG_TOWER);
        addTower(towers, AnimalTowers.T2_PIG_TOWER);
        addTower(towers, AnimalTowers.T3_PIG_TOWER);
        addTower(towers, AnimalTowers.T1_WOLF_TOWER);
        addTower(towers, AnimalTowers.T2_WOLF_DPS_TOWER);
        addTower(towers, AnimalTowers.T3_WOLF_DPS_TOWER);
        addTower(towers, AnimalTowers.T1_RABBIT_TOWER);
        addTower(towers, AnimalTowers.T2_RABBIT_TOWER);
        addTower(towers, AnimalTowers.T3_RABBIT_TOWER);
        addTower(towers, WarlockTowers.BASE_WARLOCK_TOWER);
        addTower(towers, WarlockTowers.RANGED_WARLOCK_TOWER);
        addTower(towers, WarlockTowers.MELEE_WARLOCK_TOWER);
        addTower(towers, WarlockTowers.T1_SLAVE);
        addTower(towers, WarlockTowers.T2_SLAVE);
        addTower(towers, WarlockTowers.T3_SLAVE);
        addTower(towers, WarlockTowers.T1_RANGED_SLAVE);
        addTower(towers, WarlockTowers.T2_RANGED_SLAVE);
        addTower(towers, WarlockTowers.T3_RANGED_SLAVE);
        addTower(towers, LegionTowers.T1_CHICKEN);
        addTower(towers, LegionTowers.T2_CHICKEN_TOWER);
        addTower(towers, LegionTowers.T2_DPS_CHICKEN_TOWER);
        addTower(towers, LegionTowers.T1_SLIME_TOWER);
        addTower(towers, LegionTowers.T2_SLIME_TOWER);
        addTower(towers, LegionTowers.T1_PENGUIN);
        addTower(towers, LegionTowers.T2_PENGUIN);
        addTower(towers, LegionTowers.T1_PARROT_TOWER);
        addTower(towers, LegionTowers.T2_PARROT_TOWER);
        addTower(towers, LegionTowers.ILLUSION_TOWER);

        LinkedHashMap<String, Long> upgradeCosts = new LinkedHashMap<>();
        putUpgrade(upgradeCosts, VillagerTowers.T1_SPLASH_TOWER, "villager_splash_t2", 110);
        putUpgrade(upgradeCosts, VillagerTowers.T2_LIBRARIAN_TOWER, "villager_splash_t3", 180);
        putUpgrade(upgradeCosts, VillagerTowers.T1_GOLEM_TOWER, "t2_golem_tower", 180);
        putUpgrade(upgradeCosts, VillagerTowers.T2_GOLEM_TOWER, "t3_golem_tower", 350);
        putUpgrade(upgradeCosts, VillagerTowers.T1_ALLAY_TOWER, "t2_allay_tower", 200);
        putUpgrade(upgradeCosts, VillagerTowers.T1_ALLAY_TOWER, "t2_weapon_smith_tower", 250);
        putUpgrade(upgradeCosts, VillagerTowers.T2_ALLAY_TOWER, "t3_armorer_tower", 300);
        putUpgrade(upgradeCosts, VillagerTowers.T2_WEAPON_SMITH_TOWER, "t3_weapon_smith_tower", 350);
        putUpgrade(upgradeCosts, VillagerTowers.T1_CAT_TOWER, "t2_anti_tanker_cat_tower", 250);
        putUpgrade(upgradeCosts, VillagerTowers.T1_CAT_TOWER, "t2_lane_clear_cat_tower", 200);
        putUpgrade(upgradeCosts, VillagerTowers.T2_ANTI_TANKER_CAT_TOWER, "t3_anti_tanker_cat_tower", 450);
        putUpgrade(upgradeCosts, VillagerTowers.T2_LANE_CLEAR_CAT_TOWER, "t3_lane_clear_cat_tower", 375);
        putUpgrade(upgradeCosts, UndeadTowers.T1_ZOMBIE_TOWER, "t2_zombie_tower", 180);
        putUpgrade(upgradeCosts, UndeadTowers.T2_ZOMBIE_TOWER, "t3_zombie_tower", 350);
        putUpgrade(upgradeCosts, UndeadTowers.T1_SKELETON_TOWER, "t2_ranged_skeleton_tower", 110);
        putUpgrade(upgradeCosts, UndeadTowers.T1_SKELETON_TOWER, "t2_melee_tower", 150);
        putUpgrade(upgradeCosts, UndeadTowers.T2_RANGED_SKELETON_TOWER, "t3_ranged_skeleton_tower", 200);
        putUpgrade(upgradeCosts, UndeadTowers.T2_MELEE_TOWER, "t3_melee_tower", 250);
        putUpgrade(upgradeCosts, UndeadTowers.T1_UNDEAD_ANIMAL_TOWER, "t2_undead_animal_tower", 300);
        putUpgrade(upgradeCosts, AnimalTowers.T1_PIG_TOWER, "t2_pig_tower", 180);
        putUpgrade(upgradeCosts, AnimalTowers.T2_PIG_TOWER, "t3_pig_tower", 300);
        putUpgrade(upgradeCosts, AnimalTowers.T1_WOLF_TOWER, "t2_wolf_dps_tower", 110);
        putUpgrade(upgradeCosts, AnimalTowers.T2_WOLF_DPS_TOWER, "t3_wolf_dps_tower", 110);
        putUpgrade(upgradeCosts, AnimalTowers.T1_RABBIT_TOWER, "t2_rabbit_tower", 180);
        putUpgrade(upgradeCosts, AnimalTowers.T2_RABBIT_TOWER, "t3_rabbit_tower", 300);
        putUpgrade(upgradeCosts, WarlockTowers.BASE_WARLOCK_TOWER, "ranged_warlock_tower", 0);
        putUpgrade(upgradeCosts, WarlockTowers.BASE_WARLOCK_TOWER, "melee_warlock_tower", 0);
        putUpgrade(upgradeCosts, WarlockTowers.T1_SLAVE, "t2_slave", 130);
        putUpgrade(upgradeCosts, WarlockTowers.T2_SLAVE, "t3_slave", 280);
        putUpgrade(upgradeCosts, WarlockTowers.T1_RANGED_SLAVE, "t2_ranged_slave", 100);
        putUpgrade(upgradeCosts, WarlockTowers.T2_RANGED_SLAVE, "t3_ranged_slave", 240);
        putUpgrade(upgradeCosts, LegionTowers.T1_CHICKEN, LegionTowers.T2_CHICKEN_TOWER.id(), 100);
        putUpgrade(upgradeCosts, LegionTowers.T1_CHICKEN, LegionTowers.T2_DPS_CHICKEN_TOWER.id(), 100);
        putUpgrade(upgradeCosts, LegionTowers.T1_SLIME_TOWER, LegionTowers.T2_SLIME_TOWER.id(), 85);
        putUpgrade(upgradeCosts, LegionTowers.T1_PENGUIN, LegionTowers.T2_PENGUIN.id(), 225);
        putUpgrade(upgradeCosts, LegionTowers.T1_PARROT_TOWER, LegionTowers.T2_PARROT_TOWER.id(), 225);

        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>();
        putAbilities(abilities, VillagerTowers.T2_LIBRARIAN_TOWER.id(), Map.of(
                "bonusPerSurvivedRound", 0.05,
                "maxSurvivalStacks", 6.0,
                "splashRadius", 1.25,
                "splashDamageRatio", 0.75
        ));
        putAbilities(abilities, VillagerTowers.T3_CLERIC_TOWER.id(), Map.of(
                "bonusPerSurvivedRound", 0.075,
                "maxSurvivalStacks", 6.0,
                "splashRadius", 1.75,
                "splashDamageRatio", 0.75,
                "extraAttackEvery", 3.0
        ));
        putAbilities(abilities, VillagerTowers.T2_GOLEM_TOWER.id(), Map.of(
                "thornCooldownTicks", 40.0,
                "thornDamage", 10.0,
                "thornRadius", 1.5,
                "healthBonusPerSurvivedRound", 0.10,
                "maxSurvivalStacks", 5.0
        ));
        putAbilities(abilities, VillagerTowers.T3_GOLEM_TOWER.id(), Map.of(
                "thornCooldownTicks", 30.0,
                "thornDamage", 10.0,
                "thornRadius", 2.0,
                "healthBonusPerSurvivedRound", 0.20,
                "maxSurvivalStacks", 5.0
        ));
        putAbilities(abilities, VillagerTowers.T1_ALLAY_TOWER.id(), Map.of(
                "supportBlockTicks", 100.0,
                "healAmount", 10.0,
                "radius", 2.0
        ));
        putAbilities(abilities, VillagerTowers.T2_ALLAY_TOWER.id(), Map.of(
                "supportBlockTicks", 100.0,
                "healAmount", 50.0,
                "radius", 3.0
        ));
        putAbilities(abilities, VillagerTowers.T2_WEAPON_SMITH_TOWER.id(), Map.of(
                "supportBlockTicks", 100.0,
                "buffDurationTicks", 60.0,
                "weaponBuff", 0.10,
                "radius", 2.0
        ));
        putAbilities(abilities, VillagerTowers.T3_ARMORER_TOWER.id(), Map.of(
                "supportBlockTicks", 100.0,
                "buffDurationTicks", 60.0,
                "healAmount", 80.0,
                "damageReduction", 0.10,
                "radius", 3.0
        ));
        putAbilities(abilities, VillagerTowers.T3_WEAPON_SMITH_TOWER.id(), Map.of(
                "supportBlockTicks", 100.0,
                "buffDurationTicks", 60.0,
                "weaponBuff", 0.15,
                "radius", 3.0
        ));
        putAbilities(abilities, VillagerTowers.T2_ANTI_TANKER_CAT_TOWER.id(), Map.of(
                "nonWaveBonus", 0.5,
                "tankBonus", 1.0,
                "stackDamage", 0.1,
                "stackDamageCap", 10.0
        ));
        putAbilities(abilities, VillagerTowers.T3_ANTI_TANKER_CAT_TOWER.id(), Map.of(
                "nonWaveBonus", 1.0,
                "tankBonus", 4.0,
                "stackDamage", 0.2,
                "stackDamageCap", 20.0
        ));
        putAbilities(abilities, VillagerTowers.T2_LANE_CLEAR_CAT_TOWER.id(), Map.of(
                "waveBonus", 0.5,
                "stackDamage", 0.125,
                "stackDamageCap", 5.0,
                "explosionRadius", 1.0
        ));
        putAbilities(abilities, VillagerTowers.T3_LANE_CLEAR_CAT_TOWER.id(), Map.of(
                "waveBonus", 0.75,
                "stackDamage", 0.25,
                "stackDamageCap", 20.0,
                "explosionRadius", 1.5
        ));
        putAbilities(abilities, UndeadTowers.T1_ZOMBIE_TOWER.id(), Map.of(
                "lifeStealRatio", 0.20,
                "killDamageBoost", 2.0,
                "damageBoostTicks", 100.0
        ));
        putAbilities(abilities, UndeadTowers.T2_ZOMBIE_TOWER.id(), Map.of(
                "lifeStealRatio", 0.30,
                "damageBoostOnHit", 3.0,
                "damageBoostTicks", 100.0,
                "thornRadius", 3.0,
                "thornCooldownTicks", 80.0,
                "thornHealPerHit", 2.0
        ));
        putAbilities(abilities, UndeadTowers.T3_ZOMBIE_TOWER.id(), Map.of(
                "lifeStealRatio", 0.30,
                "damageBoostOnHit", 4.0,
                "damageBoostTicks", 100.0,
                "thornRadius", 4.0,
                "thornCooldownTicks", 40.0,
                "thornHealPerHit", 2.0,
                "lastStandTicks", 60.0
        ));
        putAbilities(abilities, UndeadTowers.T2_RANGED_SKELETON_TOWER.id(), Map.of(
                "extraTargets", 1.0,
                "lifeStealRatio", 0.10,
                "stackDamage", 0.5,
                "stackDamageCap", 20.0
        ));
        putAbilities(abilities, UndeadTowers.T3_RANGED_SKELETON_TOWER.id(), Map.of(
                "extraTargets", 2.0,
                "lifeStealRatio", 0.15,
                "stackDamage", 1.5,
                "stackDamageCap", 30.0
        ));
        putAbilities(abilities, UndeadTowers.T2_MELEE_TOWER.id(), Map.of(
                "splashRadius", 1.25,
                "splashDamageRatio", 0.80,
                "lifeStealRatio", 0.05,
                "damagePerStack", 0.1,
                "healthPerStack", 1.0,
                "stackCap", 50.0
        ));
        putAbilities(abilities, UndeadTowers.T3_MELEE_TOWER.id(), Map.of(
                "splashRadius", 1.75,
                "splashDamageRatio", 0.90,
                "lifeStealRatio", 0.07,
                "damagePerStack", 0.15,
                "healthPerStack", 1.5,
                "stackCap", 100.0
        ));
        putAbilities(abilities, UndeadTowers.T1_UNDEAD_ANIMAL_TOWER.id(), Map.of(
                "scanIntervalTicks", 100.0,
                "debuffDurationTicks", 40.0,
                "radius", 4.0,
                "attackDamageReduction", 0.10
        ));
        putAbilities(abilities, UndeadTowers.T2_UNDEAD_ANIMAL_TOWER.id(), Map.of(
                "scanIntervalTicks", 100.0,
                "debuffDurationTicks", 40.0,
                "radius", 4.0,
                "attackDamageReduction", 0.10,
                "towerDamageTakenBonus", 0.10
        ));
        putAbilities(abilities, AnimalTowers.T1_PIG_TOWER.id(), Map.of(
                "maxStacks", 2.0,
                "healthPerStack", 10.0,
                "damagePerStack", 2.5
        ));
        putAbilities(abilities, AnimalTowers.T2_PIG_TOWER.id(), Map.of(
                "maxStacks", 2.0,
                "healthPerStack", 25.0,
                "damagePerStack", 5.0,
                "damageReduction", 0.10
        ));
        putAbilities(abilities, AnimalTowers.T3_PIG_TOWER.id(), Map.of(
                "maxStacks", 3.0,
                "healthPerStack", 40.0,
                "damagePerStack", 10.0,
                "damageReduction", 0.10,
                "splashRadius", 1.0,
                "splashDamageRatio", 0.50
        ));
        putAbilities(abilities, AnimalTowers.T1_WOLF_TOWER.id(), Map.of(
                "maxStacks", 5.0,
                "damagePerStack", 1.5,
                "intervalReductionPerStack", 1.0
        ));
        putAbilities(abilities, AnimalTowers.T2_WOLF_DPS_TOWER.id(), Map.of(
                "maxStacks", 5.0,
                "damagePerStack", 4.0,
                "intervalReductionPerStack", 1.0,
                "splashRadius", 1.25,
                "splashDamageRatio", 0.50,
                "maxStackExtraIntervalReduction", 3.0
        ));
        putAbilities(abilities, AnimalTowers.T3_WOLF_DPS_TOWER.id(), Map.of(
                "maxStacks", 5.0,
                "damagePerStack", 6.0,
                "intervalReductionPerStack", 1.0,
                "splashRadius", 1.5,
                "splashDamageRatio", 0.50,
                "maxStackExtraIntervalReduction", 5.0,
                "maxStackDamageBonus", 5.0
        ));
        putAbilities(abilities, AnimalTowers.T1_RABBIT_TOWER.id(), Map.of(
                "maxStacks", 5.0,
                "damagePerStack", 2.0
        ));
        putAbilities(abilities, AnimalTowers.T2_RABBIT_TOWER.id(), Map.of(
                "maxStacks", 5.0,
                "damagePerStack", 5.0,
                "maxStackExtraIntervalReduction", 5.0
        ));
        putAbilities(abilities, AnimalTowers.T3_RABBIT_TOWER.id(), Map.of(
                "maxStacks", 5.0,
                "damagePerStack", 8.0,
                "maxStackExtraIntervalReduction", 7.0,
                "extraAttackDamageRatio", 1.0
        ));
        putAbilities(abilities, WarlockTowers.BASE_WARLOCK_TOWER.id(), Map.ofEntries(
                Map.entry("baseSacrificeRadius", 6.0),
                Map.entry("baseFatalHealRatio", 0.35),
                Map.entry("basePermanentHealthRatio", 0.025),
                Map.entry("basePermanentDamageRatio", 0.05),
                Map.entry("sacrificeHealRatio", 0.0)
        ));
        putAbilities(abilities, WarlockTowers.RANGED_WARLOCK_TOWER.id(), Map.ofEntries(
                Map.entry("lowHealthSacrificeThreshold", 0.30),
                Map.entry("sacrificeRadius", 0.0),
                Map.entry("roundStatRatio", 0.40),
                Map.entry("roundIntervalReductionCap", 15.0),
                Map.entry("permanentHealthRatio", 0.025),
                Map.entry("permanentDamageRatio", 0.05),
                Map.entry("lifeStealEvery", 5.0),
                Map.entry("lifeStealPerStep", 0.005),
                Map.entry("lifeStealCap", 0.30),
                Map.entry("splashEvery", 10.0),
                Map.entry("splashRadiusPerStep", 0.5),
                Map.entry("splashDamageRatio", 0.75),
                Map.entry("roundAbsorbDefenseThreshold", 5.0),
                Map.entry("roundDamageReduction", 0.10),
                Map.entry("healthBonusPerStack", 0.05),
                Map.entry("maxHealthBonus", 0.25),
                Map.entry("damageBonusPerStack", 0.15),
                Map.entry("maxDamageBonus", 0.75)
        ));
        putAbilities(abilities, WarlockTowers.MELEE_WARLOCK_TOWER.id(), Map.ofEntries(
                Map.entry("lowHealthSacrificeThreshold", 0.30),
                Map.entry("sacrificeRadius", 0.0),
                Map.entry("roundStatRatio", 0.60),
                Map.entry("roundSplashRadiusPerSacrifice", 0.25),
                Map.entry("splashDamageRatio", 0.75),
                Map.entry("permanentHealthRatio", 0.05),
                Map.entry("permanentDamageRatio", 0.025),
                Map.entry("damageReductionEvery", 5.0),
                Map.entry("damageReductionPerStep", 0.025),
                Map.entry("damageReductionCap", 0.25),
                Map.entry("lifeStealPerSacrifice", 0.01),
                Map.entry("lifeStealCap", 0.30),
                Map.entry("healthBonusPerStack", 0.15),
                Map.entry("maxHealthBonus", 0.75),
                Map.entry("damageBonusPerStack", 0.05),
                Map.entry("maxDamageBonus", 0.25)
        ));
        putAbilities(abilities, WarlockTowers.T2_SLAVE.id(), Map.of(
                "deathEffectRadius", 5.0,
                "deathEffectDurationTicks", 72000.0,
                "towerDamageTakenBonus", 0.05
        ));
        putAbilities(abilities, WarlockTowers.T3_SLAVE.id(), Map.of(
                "deathEffectRadius", 5.0,
                "deathEffectDurationTicks", 72000.0,
                "towerDamageTakenBonus", 0.10
        ));
        putAbilities(abilities, WarlockTowers.T2_RANGED_SLAVE.id(), Map.of(
                "deathEffectRadius", 10.0,
                "deathEffectDurationTicks", 72000.0,
                "attackSpeedReduction", 0.05
        ));
        putAbilities(abilities, WarlockTowers.T3_RANGED_SLAVE.id(), Map.of(
                "deathEffectRadius", 10.0,
                "deathEffectDurationTicks", 72000.0,
                "attackSpeedReduction", 0.10
        ));
        putAbilities(abilities, LegionTowers.T1_CHICKEN.id(), Map.ofEntries(
                Map.entry("cloneCount", 1.0),
                Map.entry("cloneHealthRatio", 0.50),
                Map.entry("cloneDamageRatio", 0.50),
                Map.entry("cloneRangeRatio", 1.0),
                Map.entry("cloneAttackIntervalMultiplier", 1.0),
                Map.entry("cloneSpawnRadius", 1.5),
                Map.entry("cloneAggroPriorityBonus", 5.0)
        ));
        putAbilities(abilities, LegionTowers.T2_CHICKEN_TOWER.id(), Map.ofEntries(
                Map.entry("cloneCount", 1.0),
                Map.entry("cloneHealthRatio", 0.50),
                Map.entry("cloneDamageRatio", 0.50),
                Map.entry("cloneRangeRatio", 1.0),
                Map.entry("cloneAttackIntervalMultiplier", 1.0),
                Map.entry("cloneSpawnRadius", 1.5),
                Map.entry("cloneAggroPriorityBonus", 5.0),
                Map.entry("splashRadius", 0.75),
                Map.entry("splashDamageRatio", 0.25)
        ));
        putAbilities(abilities, LegionTowers.T2_DPS_CHICKEN_TOWER.id(), Map.ofEntries(
                Map.entry("cloneCount", 0.0),
                Map.entry("cloneHealthRatio", 0.50),
                Map.entry("cloneDamageRatio", 0.50),
                Map.entry("cloneRangeRatio", 1.0),
                Map.entry("cloneAttackIntervalMultiplier", 1.0),
                Map.entry("cloneSpawnRadius", 1.5),
                Map.entry("cloneAggroPriorityBonus", 5.0),
                Map.entry("splashRadius", 0.75),
                Map.entry("splashDamageRatio", 0.75)
        ));
        putAbilities(abilities, LegionTowers.T1_SLIME_TOWER.id(), Map.ofEntries(
                Map.entry("cloneCount", 1.0),
                Map.entry("cloneHealthRatio", 0.65),
                Map.entry("cloneDamageRatio", 0.65),
                Map.entry("cloneRangeRatio", 1.0),
                Map.entry("cloneAttackIntervalMultiplier", 1.0),
                Map.entry("cloneSpawnRadius", 1.5),
                Map.entry("cloneAggroPriorityBonus", 5.0)
        ));
        putAbilities(abilities, LegionTowers.T2_SLIME_TOWER.id(), Map.ofEntries(
                Map.entry("cloneCount", 2.0),
                Map.entry("cloneHealthRatio", 0.65),
                Map.entry("cloneDamageRatio", 0.65),
                Map.entry("cloneRangeRatio", 1.0),
                Map.entry("cloneAttackIntervalMultiplier", 1.0),
                Map.entry("cloneSpawnRadius", 1.5),
                Map.entry("cloneAggroPriorityBonus", 5.0),
                Map.entry("regenAmount", 3.0),
                Map.entry("regenIntervalTicks", 20.0)
        ));
        putAbilities(abilities, LegionTowers.T1_PENGUIN.id(), Map.ofEntries(
                Map.entry("cloneCount", 2.0),
                Map.entry("cloneHealthRatio", 0.65),
                Map.entry("cloneDamageRatio", 0.65),
                Map.entry("cloneRangeRatio", 1.0),
                Map.entry("cloneAttackIntervalMultiplier", 1.0),
                Map.entry("cloneSpawnRadius", 1.5),
                Map.entry("cloneAggroPriorityBonus", 5.0)
        ));
        putAbilities(abilities, LegionTowers.T2_PENGUIN.id(), Map.ofEntries(
                Map.entry("cloneCount", 3.0),
                Map.entry("cloneHealthRatio", 0.65),
                Map.entry("cloneDamageRatio", 0.65),
                Map.entry("cloneRangeRatio", 1.0),
                Map.entry("cloneAttackIntervalMultiplier", 1.0),
                Map.entry("cloneSpawnRadius", 1.5),
                Map.entry("cloneAggroPriorityBonus", 5.0),
                Map.entry("splashRadius", 0.75),
                Map.entry("splashDamageRatio", 0.60)
        ));
        putAbilities(abilities, LegionTowers.T1_PARROT_TOWER.id(), Map.of(
                "attackStackBonus", 0.10,
                "maxAttackStacks", 5.0
        ));
        putAbilities(abilities, LegionTowers.T2_PARROT_TOWER.id(), Map.of(
                "attackStackBonus", 0.20,
                "maxAttackStacks", 5.0
        ));
        putAbilities(abilities, LegionTowers.ILLUSION_TOWER.id(), Map.ofEntries(
                Map.entry("cloneCount", 1.0),
                Map.entry("cloneHealthRatio", 0.65),
                Map.entry("cloneDamageRatio", 0.65),
                Map.entry("cloneRangeRatio", 1.0),
                Map.entry("cloneAttackIntervalMultiplier", 1.0),
                Map.entry("cloneSpawnRadius", 1.5),
                Map.entry("cloneAggroPriorityBonus", 5.0)
        ));

        return new TowerBalanceConfig(towers, upgradeCosts, abilities);
    }

    public TowerStats statsFor(TowerType defaults) {
        if (defaults == null) {
            throw new IllegalArgumentException("Default tower type cannot be null.");
        }
        return towers.getOrDefault(defaults.id(), TowerStats.from(defaults)).mergedWith(defaults);
    }

    public long upgradeCost(String fromTowerId, String upgradeId, long fallback) {
        Long configured = upgradeCosts.get(upgradeKey(fromTowerId, upgradeId));
        if (configured == null) {
            configured = upgradeCosts.get(upgradeId);
        }
        return configured == null ? Math.max(0, fallback) : Math.max(0, configured);
    }

    public double ability(String towerId, String key, double fallback) {
        Map<String, Double> values = abilities.get(towerId);
        if (values == null) {
            return fallback;
        }
        return values.getOrDefault(key, fallback);
    }

    public int abilityTicks(String towerId, String key, int fallback) {
        return Math.max(0, (int) Math.round(ability(towerId, key, fallback)));
    }

    public int abilityInt(String towerId, String key, int fallback) {
        return Math.max(0, (int) Math.round(ability(towerId, key, fallback)));
    }

    public TowerBalanceConfig withMissingDefaults(TowerBalanceConfig defaults) {
        if (defaults == null) {
            return this;
        }

        LinkedHashMap<String, TowerStats> mergedTowers = new LinkedHashMap<>();
        towers.forEach((towerId, stats) -> {
            TowerStats defaultStats = defaults.towers.get(towerId);
            mergedTowers.put(towerId, defaultStats == null ? stats : stats.withMissingDefaults(defaultStats));
        });
        defaults.towers.forEach((towerId, stats) -> mergedTowers.putIfAbsent(towerId, stats));

        LinkedHashMap<String, Long> mergedUpgradeCosts = new LinkedHashMap<>(upgradeCosts);
        defaults.upgradeCosts.forEach(mergedUpgradeCosts::putIfAbsent);

        LinkedHashMap<String, Map<String, Double>> mergedAbilities = new LinkedHashMap<>();
        abilities.forEach((towerId, values) -> {
            LinkedHashMap<String, Double> mergedValues = new LinkedHashMap<>(values);
            Map<String, Double> defaultValues = defaults.abilities.get(towerId);
            if (defaultValues != null) {
                defaultValues.forEach(mergedValues::putIfAbsent);
            }
            mergedAbilities.put(towerId, mergedValues);
        });
        defaults.abilities.forEach((towerId, values) -> mergedAbilities.putIfAbsent(towerId, values));

        return new TowerBalanceConfig(mergedTowers, mergedUpgradeCosts, mergedAbilities);
    }

    public static String upgradeKey(String fromTowerId, String upgradeId) {
        return fromTowerId + "->" + upgradeId;
    }

    private static void addTower(Map<String, TowerStats> towers, TowerType type) {
        towers.put(type.id(), TowerStats.from(type));
    }

    private static void putUpgrade(Map<String, Long> upgrades, TowerType from, String upgradeId, long cost) {
        upgrades.put(upgradeKey(from.id(), upgradeId), cost);
    }

    private static void putAbilities(Map<String, Map<String, Double>> abilities, String towerId, Map<String, Double> values) {
        abilities.put(towerId, values);
    }

    private static Map<String, TowerStats> copyTowerStats(Map<String, TowerStats> values) {
        LinkedHashMap<String, TowerStats> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                copy.put(key, value);
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Long> copyUpgradeCosts(Map<String, Long> values) {
        LinkedHashMap<String, Long> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                copy.put(key, Math.max(0, value));
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Map<String, Double>> copyAbilities(Map<String, Map<String, Double>> values) {
        LinkedHashMap<String, Map<String, Double>> copy = new LinkedHashMap<>();
        values.forEach((towerId, abilityValues) -> {
            if (towerId == null || towerId.isBlank() || abilityValues == null) {
                return;
            }
            LinkedHashMap<String, Double> inner = new LinkedHashMap<>();
            abilityValues.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    inner.put(key, value);
                }
            });
            copy.put(towerId, Collections.unmodifiableMap(inner));
        });
        return Collections.unmodifiableMap(copy);
    }

    public record TowerStats(
            Long mineralCost,
            Double maxHealth,
            Double range,
            Double damage,
            Integer attackIntervalTicks,
            Integer aggroPriority
    ) {
        public static TowerStats from(TowerType type) {
            return new TowerStats(
                    type.mineralCost(),
                    type.maxHealth(),
                    type.range(),
                    type.damage(),
                    type.attackIntervalTicks(),
                    type.aggroPriority()
            );
        }

        public TowerStats mergedWith(TowerType defaults) {
            return new TowerStats(
                    mineralCost == null ? defaults.mineralCost() : Math.max(0, mineralCost),
                    maxHealth == null ? defaults.maxHealth() : Math.max(1.0, maxHealth),
                    range == null ? defaults.range() : Math.max(0.0, range),
                    damage == null ? defaults.damage() : Math.max(0.0, damage),
                    attackIntervalTicks == null ? defaults.attackIntervalTicks() : Math.max(1, attackIntervalTicks),
                    aggroPriority == null ? defaults.aggroPriority() : aggroPriority
            );
        }

        public TowerStats withMissingDefaults(TowerStats defaults) {
            if (defaults == null) {
                return this;
            }
            return new TowerStats(
                    mineralCost == null ? defaults.mineralCost() : mineralCost,
                    maxHealth == null ? defaults.maxHealth() : maxHealth,
                    range == null ? defaults.range() : range,
                    damage == null ? defaults.damage() : damage,
                    attackIntervalTicks == null ? defaults.attackIntervalTicks() : attackIntervalTicks,
                    aggroPriority == null ? defaults.aggroPriority() : aggroPriority
            );
        }
    }
}
