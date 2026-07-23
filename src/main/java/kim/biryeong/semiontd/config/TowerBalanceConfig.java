package kim.biryeong.semiontd.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import kim.biryeong.semiontd.tower.end.EndTower;
import kim.biryeong.semiontd.tower.end.EndTowers;
import kim.biryeong.semiontd.tower.illager.IllagerRaidStates;
import kim.biryeong.semiontd.tower.illager.IllagerTowers;
import kim.biryeong.semiontd.tower.legion.LegionTowers;
import kim.biryeong.semiontd.tower.nether.NetherTower;
import kim.biryeong.semiontd.tower.nether.NetherTowers;
import kim.biryeong.semiontd.tower.ocean.OceanTower;
import kim.biryeong.semiontd.tower.ocean.OceanTowers;
import kim.biryeong.semiontd.tower.resonance.ResonanceAspect;
import kim.biryeong.semiontd.tower.resonance.ResonanceTowers;
import kim.biryeong.semiontd.tower.undead.UndeadTowers;
import kim.biryeong.semiontd.tower.villager.VillagerTowers;
import kim.biryeong.semiontd.tower.warlock.WarlockTowers;

public record TowerBalanceConfig(
        Map<String, TowerStats> towers,
        Map<String, Long> upgradeCosts,
        Map<String, Map<String, Double>> abilities,
        IllusionCloneQueueConfig illusionCloneQueue,
        VillagerAdvConfig villagerAdv
) {
    public TowerBalanceConfig(Map<String, TowerStats> towers, Map<String, Long> upgradeCosts, Map<String, Map<String, Double>> abilities) {
        this(towers, upgradeCosts, abilities, IllusionCloneQueueConfig.defaultConfig(), VillagerAdvConfig.defaultConfig());
    }

    public TowerBalanceConfig(
            Map<String, TowerStats> towers,
            Map<String, Long> upgradeCosts,
            Map<String, Map<String, Double>> abilities,
            IllusionCloneQueueConfig illusionCloneQueue
    ) {
        this(towers, upgradeCosts, abilities, illusionCloneQueue, VillagerAdvConfig.defaultConfig());
    }

    public TowerBalanceConfig {
        towers = towers == null ? Map.of() : copyTowerStats(towers);
        upgradeCosts = upgradeCosts == null ? Map.of() : copyUpgradeCosts(upgradeCosts);
        abilities = abilities == null ? Map.of() : copyAbilities(abilities);
        illusionCloneQueue = illusionCloneQueue == null ? IllusionCloneQueueConfig.defaultConfig() : illusionCloneQueue;
        villagerAdv = villagerAdv == null ? VillagerAdvConfig.defaultConfig() : villagerAdv;
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
        addVillagerAdvTowers(towers);
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
        addTower(towers, AnimalTowers.T1_FOX_TOWER);
        addTower(towers, AnimalTowers.T2_FOX_TOWER);
        addTower(towers, AnimalTowers.T3_FOX_TOWER);
        addTower(towers, LegionTowers.T1_BEE_TOWER);
        addTower(towers, LegionTowers.T2_BEE_TOWER);
        addTower(towers, LegionTowers.T3_BEE_TOWER);
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
        addTower(towers, LegionTowers.T1_GOAT_TOWER);
        addTower(towers, LegionTowers.T2_STRONG_GOAT_TOWER);
        addTower(towers, LegionTowers.T3_EXTREME_GOAT_TOWER);
        addTower(towers, LegionTowers.ILLUSION_TOWER);
        addTower(towers, ResonanceTowers.FOCUS_CRYSTAL);
        addTower(towers, ResonanceTowers.FOCUS_PRISM);
        addTower(towers, ResonanceTowers.FOCUS_CORE);
        addTower(towers, ResonanceTowers.WAVE_CRYSTAL);
        addTower(towers, ResonanceTowers.WAVE_PRISM);
        addTower(towers, ResonanceTowers.WAVE_CORE);
        addTower(towers, ResonanceTowers.FROST_CRYSTAL);
        addTower(towers, ResonanceTowers.FROST_PRISM);
        addTower(towers, ResonanceTowers.FROST_CORE);
        addTower(towers, ResonanceTowers.AMPLIFY_CRYSTAL);
        addTower(towers, ResonanceTowers.AMPLIFY_PRISM);
        addTower(towers, ResonanceTowers.AMPLIFY_CORE);
        addTower(towers, IllagerTowers.T1_VINDICATOR);
        addTower(towers, IllagerTowers.T2_VINDICATOR_CAPTAIN);
        addTower(towers, IllagerTowers.T3_RAVAGER);
        addTower(towers, IllagerTowers.T1_PILLAGER);
        addTower(towers, IllagerTowers.T2_PILLAGER_CAPTAIN_SINGLE);
        addTower(towers, IllagerTowers.T2_PILLAGER_CAPTAIN_SPLASH);
        addTower(towers, IllagerTowers.T3_EVOKER_SINGLE);
        addTower(towers, IllagerTowers.T3_EVOKER_SPLASH);
        addTower(towers, IllagerTowers.T1_VEX);
        addTower(towers, IllagerTowers.T2_WITCH_LOW);
        addTower(towers, IllagerTowers.T2_WITCH_HIGH);
        addTower(towers, IllagerTowers.T3_ILLUSIONER_LOW);
        addTower(towers, IllagerTowers.T3_ILLUSIONER_HIGH);
        addNetherTowers(towers);
        addEndTowers(towers);
        addOceanTowers(towers);

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
        putVillagerAdvUpgrades(upgradeCosts);
        putUpgrade(upgradeCosts, UndeadTowers.T1_ZOMBIE_TOWER, "t2_zombie_tower", 180);
        putUpgrade(upgradeCosts, UndeadTowers.T2_ZOMBIE_TOWER, "t3_zombie_tower", 350);
        putUpgrade(upgradeCosts, UndeadTowers.T1_SKELETON_TOWER, "t2_ranged_skeleton_tower", 110);
        putUpgrade(upgradeCosts, UndeadTowers.T1_SKELETON_TOWER, "t2_melee_tower", 150);
        putUpgrade(upgradeCosts, UndeadTowers.T2_RANGED_SKELETON_TOWER, "t3_ranged_skeleton_tower", 200);
        putUpgrade(upgradeCosts, UndeadTowers.T2_MELEE_TOWER, "t3_melee_tower", 250);
        putUpgrade(upgradeCosts, UndeadTowers.T1_UNDEAD_ANIMAL_TOWER, "t2_undead_animal_tower", 300);
        putUpgrade(upgradeCosts, AnimalTowers.T1_PIG_TOWER, "t2_pig_tower", 95);
        putUpgrade(upgradeCosts, AnimalTowers.T2_PIG_TOWER, "t3_pig_tower", 150);
        putUpgrade(upgradeCosts, AnimalTowers.T1_WOLF_TOWER, "t2_wolf_dps_tower", 90);
        putUpgrade(upgradeCosts, AnimalTowers.T2_WOLF_DPS_TOWER, "t3_wolf_dps_tower", 180);
        putUpgrade(upgradeCosts, AnimalTowers.T1_RABBIT_TOWER, "t2_rabbit_tower", 100);
        putUpgrade(upgradeCosts, AnimalTowers.T2_RABBIT_TOWER, "t3_rabbit_tower", 200);
        putUpgrade(upgradeCosts, AnimalTowers.T1_FOX_TOWER, "t2_fox_tower", 150);
        putUpgrade(upgradeCosts, AnimalTowers.T2_FOX_TOWER, "t3_fox_tower", 225);
        putUpgrade(upgradeCosts, LegionTowers.T1_BEE_TOWER, "t2_bee_tower", 160);
        putUpgrade(upgradeCosts, LegionTowers.T2_BEE_TOWER, "t3_bee_tower", 310);
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
        putUpgrade(upgradeCosts, LegionTowers.T1_GOAT_TOWER, LegionTowers.T2_STRONG_GOAT_TOWER.id(), 150);
        putUpgrade(upgradeCosts, LegionTowers.T2_STRONG_GOAT_TOWER, LegionTowers.T3_EXTREME_GOAT_TOWER.id(), 250);
        putUpgrade(upgradeCosts, ResonanceTowers.FOCUS_CRYSTAL, ResonanceTowers.FOCUS_PRISM.id(), 60);
        putUpgrade(upgradeCosts, ResonanceTowers.FOCUS_PRISM, ResonanceTowers.FOCUS_CORE.id(), 180);
        putUpgrade(upgradeCosts, ResonanceTowers.WAVE_CRYSTAL, ResonanceTowers.WAVE_PRISM.id(), 60);
        putUpgrade(upgradeCosts, ResonanceTowers.WAVE_PRISM, ResonanceTowers.WAVE_CORE.id(), 200);
        putUpgrade(upgradeCosts, ResonanceTowers.FROST_CRYSTAL, ResonanceTowers.FROST_PRISM.id(), 60);
        putUpgrade(upgradeCosts, ResonanceTowers.FROST_PRISM, ResonanceTowers.FROST_CORE.id(), 220);
        putUpgrade(upgradeCosts, ResonanceTowers.AMPLIFY_CRYSTAL, ResonanceTowers.AMPLIFY_PRISM.id(), 60);
        putUpgrade(upgradeCosts, ResonanceTowers.AMPLIFY_PRISM, ResonanceTowers.AMPLIFY_CORE.id(), 220);
        putUpgrade(upgradeCosts, IllagerTowers.T1_VINDICATOR, IllagerTowers.T2_VINDICATOR_CAPTAIN.id(), 170);
        putUpgrade(upgradeCosts, IllagerTowers.T2_VINDICATOR_CAPTAIN, IllagerTowers.T3_RAVAGER.id(), 330);
        putUpgrade(upgradeCosts, IllagerTowers.T1_PILLAGER, IllagerTowers.T2_PILLAGER_CAPTAIN_SINGLE.id(), 160);
        putUpgrade(upgradeCosts, IllagerTowers.T1_PILLAGER, IllagerTowers.T2_PILLAGER_CAPTAIN_SPLASH.id(), 155);
        putUpgrade(upgradeCosts, IllagerTowers.T2_PILLAGER_CAPTAIN_SINGLE, IllagerTowers.T3_EVOKER_SINGLE.id(), 310);
        putUpgrade(upgradeCosts, IllagerTowers.T2_PILLAGER_CAPTAIN_SPLASH, IllagerTowers.T3_EVOKER_SPLASH.id(), 300);
        putUpgrade(upgradeCosts, IllagerTowers.T1_VEX, IllagerTowers.T2_WITCH_LOW.id(), 150);
        putUpgrade(upgradeCosts, IllagerTowers.T1_VEX, IllagerTowers.T2_WITCH_HIGH.id(), 150);
        putUpgrade(upgradeCosts, IllagerTowers.T2_WITCH_LOW, IllagerTowers.T3_ILLUSIONER_LOW.id(), 280);
        putUpgrade(upgradeCosts, IllagerTowers.T2_WITCH_HIGH, IllagerTowers.T3_ILLUSIONER_HIGH.id(), 280);
        putNetherUpgrades(upgradeCosts);
        putEndUpgrades(upgradeCosts);
        putOceanUpgrades(upgradeCosts);

        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>();
        putAbilities(abilities, IllagerRaidStates.RAID_CONFIG_ID, Map.of(
                "gaugeMax", 100.0,
                "waveKillGauge", 3.0,
                "incomeKillGauge", 8.0,
                "markedKillBonusGauge", 7.0,
                "illagerTowerDeathGauge", 20.0,
                "attackSpeedPercentPerTower", 0.02,
                "damagePercentPerTower", 0.05,
                "timedEffectDurationTicks", 40.0
        ));
        putAbilities(abilities, IllagerTowers.T1_VINDICATOR.id(), Map.of(
                "raidDamageReduction", 0.10
        ));
        putAbilities(abilities, IllagerTowers.T2_VINDICATOR_CAPTAIN.id(), Map.of(
                "raidDamageReduction", 0.18
        ));
        putAbilities(abilities, IllagerTowers.T3_RAVAGER.id(), Map.of(
                "raidDamageReduction", 0.25,
                "splashRadius", 1.25,
                "splashDamageRatio", 0.35,
                "raidSplashRadiusBonus", 0.50,
                "raidSplashDamageRatioBonus", 0.15
        ));
        putAbilities(abilities, IllagerTowers.T1_PILLAGER.id(), Map.of(
                "raidMarkedDamageBonus", 0.15
        ));
        putAbilities(abilities, IllagerTowers.T2_PILLAGER_CAPTAIN_SINGLE.id(), Map.of(
                "incomeDamageBonus", 0.35,
                "raidIncomeDamageBonus", 0.25,
                "raidMarkedDamageBonus", 0.15
        ));
        putAbilities(abilities, IllagerTowers.T3_EVOKER_SINGLE.id(), Map.of(
                "incomeDamageBonus", 0.65,
                "raidIncomeDamageBonus", 0.35,
                "raidMarkedDamageBonus", 0.25,
                "markDamageTakenBonus", 0.08,
                "markDurationTicks", 60.0,
                "raidMarkDurationBonusTicks", 20.0
        ));
        putAbilities(abilities, IllagerTowers.T2_PILLAGER_CAPTAIN_SPLASH.id(), Map.of(
                "splashRadius", 1.25,
                "splashDamageRatio", 0.45,
                "raidSplashRadiusBonus", 0.25,
                "raidSplashDamageRatioBonus", 0.10
        ));
        putAbilities(abilities, IllagerTowers.T3_EVOKER_SPLASH.id(), Map.of(
                "splashRadius", 1.75,
                "splashDamageRatio", 0.55,
                "raidSplashRadiusBonus", 0.50,
                "raidSplashDamageRatioBonus", 0.15
        ));
        putAbilities(abilities, IllagerTowers.T1_VEX.id(), Map.of(
                "markDamageTakenBonus", 0.08,
                "markDurationTicks", 60.0,
                "raidMarkDamageTakenBonus", 0.04,
                "raidMarkDurationBonusTicks", 20.0
        ));
        putAbilities(abilities, IllagerTowers.T2_WITCH_LOW.id(), Map.of(
                "markDamageTakenBonus", 0.14,
                "raidMarkDamageTakenBonus", 0.04,
                "raidLowHealthMarkDamageTakenBonus", 0.08,
                "markDurationTicks", 80.0,
                "raidMarkDurationBonusTicks", 20.0,
                "forceTargetRadius", 1.0,
                "raidForceTargetRadiusBonus", 0.5
        ));
        putAbilities(abilities, IllagerTowers.T2_WITCH_HIGH.id(), Map.of(
                "markDamageTakenBonus", 0.14,
                "raidMarkDamageTakenBonus", 0.04,
                "raidHighHealthMarkDamageTakenBonus", 0.08,
                "markDurationTicks", 80.0,
                "raidMarkDurationBonusTicks", 20.0,
                "forceTargetRadius", 1.0,
                "raidForceTargetRadiusBonus", 0.5
        ));
        putAbilities(abilities, IllagerTowers.T3_ILLUSIONER_LOW.id(), Map.of(
                "markDamageTakenBonus", 0.22,
                "raidMarkDamageTakenBonus", 0.08,
                "raidLowHealthMarkDamageTakenBonus", 0.12,
                "markDurationTicks", 100.0,
                "raidMarkDurationBonusTicks", 30.0,
                "forceTargetRadius", 1.0,
                "raidForceTargetRadiusBonus", 1.0
        ));
        putAbilities(abilities, IllagerTowers.T3_ILLUSIONER_HIGH.id(), Map.of(
                "markDamageTakenBonus", 0.22,
                "raidMarkDamageTakenBonus", 0.08,
                "raidHighHealthMarkDamageTakenBonus", 0.12,
                "markDurationTicks", 100.0,
                "raidMarkDurationBonusTicks", 30.0,
                "forceTargetRadius", 1.0,
                "raidForceTargetRadiusBonus", 1.0
        ));
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
                "stackDamage", 0.02,
                "stackDamageCap", 10.0
        ));
        putAbilities(abilities, VillagerTowers.T3_ANTI_TANKER_CAT_TOWER.id(), Map.of(
                "nonWaveBonus", 1.0,
                "tankBonus", 4.0,
                "stackDamage", 0.04,
                "stackDamageCap", 20.0
        ));
        putAbilities(abilities, VillagerTowers.T2_LANE_CLEAR_CAT_TOWER.id(), Map.of(
                "waveBonus", 0.5,
                "stackDamage", 0.025,
                "stackDamageCap", 5.0,
                "explosionRadius", 1.0
        ));
        putAbilities(abilities, VillagerTowers.T3_LANE_CLEAR_CAT_TOWER.id(), Map.of(
                "waveBonus", 0.75,
                "stackDamage", 0.05,
                "stackDamageCap", 20.0,
                "explosionRadius", 1.5
        ));
        putVillagerAdvAbilities(abilities);
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
                "extraTargetRangeBonus", 0.0,
                "lifeStealRatio", 0.10,
                "stackDamage", 0.1,
                "stackDamageCap", 20.0
        ));
        putAbilities(abilities, UndeadTowers.T3_RANGED_SKELETON_TOWER.id(), Map.of(
                "extraTargets", 2.0,
                "extraTargetRangeBonus", 2.0,
                "lifeStealRatio", 0.15,
                "stackDamage", 0.3,
                "stackDamageCap", 30.0
        ));
        putAbilities(abilities, UndeadTowers.T2_MELEE_TOWER.id(), Map.of(
                "splashRadius", 1.25,
                "splashDamageRatio", 0.80,
                "lifeStealRatio", 0.05,
                "damagePerStack", 0.02,
                "healthPerStack", 0.2,
                "stackCap", 250.0,
                "deathStackRange", 5.0
        ));
        putAbilities(abilities, UndeadTowers.T3_MELEE_TOWER.id(), Map.of(
                "splashRadius", 1.75,
                "splashDamageRatio", 0.90,
                "lifeStealRatio", 0.07,
                "damagePerStack", 0.03,
                "healthPerStack", 0.3,
                "stackCap", 500.0,
                "deathStackRange", 5.0
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
                "maxStacks", 2.0,
                "healthPerStack", 90.0,
                "damagePerStack", 15.0,
                "damageReduction", 0.30,
                "splashRadius", 1.0,
                "splashDamageRatio", 0.50
        ));
        putAbilities(abilities, AnimalTowers.T1_WOLF_TOWER.id(), Map.of(
                "maxStacks", 4.0,
                "damagePerStack", 2.0,
                "intervalReductionPerStack", 1.25
        ));
        putAbilities(abilities, AnimalTowers.T2_WOLF_DPS_TOWER.id(), Map.of(
                "maxStacks", 4.0,
                "damagePerStack", 5.0,
                "intervalReductionPerStack", 1.25,
                "splashRadius", 1.25,
                "splashDamageRatio", 0.50,
                "maxStackExtraIntervalReduction", 3.0
        ));
        putAbilities(abilities, AnimalTowers.T3_WOLF_DPS_TOWER.id(), Map.of(
                "maxStacks", 4.0,
                "damagePerStack", 10.0,
                "intervalReductionPerStack", 1.25,
                "splashRadius", 2.0,
                "splashDamageRatio", 0.75,
                "maxStackExtraIntervalReduction", 5.0,
                "maxStackDamageBonus", 5.0
        ));
        putAbilities(abilities, AnimalTowers.T1_RABBIT_TOWER.id(), Map.of(
                "maxStacks", 4.0,
                "damagePerStack", 2.5
        ));
        putAbilities(abilities, AnimalTowers.T2_RABBIT_TOWER.id(), Map.of(
                "maxStacks", 4.0,
                "damagePerStack", 6.25,
                "maxStackExtraIntervalReduction", 5.0
        ));
        putAbilities(abilities, AnimalTowers.T3_RABBIT_TOWER.id(), Map.of(
                "maxStacks", 4.0,
                "damagePerStack", 12.5,
                "maxStackExtraIntervalReduction", 5.0,
                "extraAttackDamageRatio", 2.0
        ));
        putAbilities(abilities, AnimalTowers.T1_FOX_TOWER.id(), Map.of(
                "maxStacks", 4.0,
                "executeHealthThreshold", 0.30,
                "executeThresholdPerStack", 0.02,
                "maxExecuteHealthThreshold", 0.40,
                "executeDamageBonusRatio", 0.50,
                "executeDamageBonusPerStack", 0.20,
                "killBonusDamage", 0.1,
                "killBonusDamageCap", 10.0
        ));
        putAbilities(abilities, AnimalTowers.T2_FOX_TOWER.id(), Map.of(
                "maxStacks", 4.0,
                "executeHealthThreshold", 0.35,
                "executeThresholdPerStack", 0.025,
                "maxExecuteHealthThreshold", 0.50,
                "executeDamageBonusRatio", 0.50,
                "executeDamageBonusPerStack", 0.25,
                "killBonusDamage", 0.2,
                "killBonusDamageCap", 20.0
        ));
        putAbilities(abilities, AnimalTowers.T3_FOX_TOWER.id(), Map.of(
                "maxStacks", 4.0,
                "executeHealthThreshold", 0.40,
                "executeThresholdPerStack", 0.04,
                "maxExecuteHealthThreshold", 0.60,
                "executeDamageBonusRatio", 0.75,
                "executeDamageBonusPerStack", 0.30,
                "killBonusDamage", 0.4,
                "killBonusDamageCap", 40.0
        ));
        putAbilities(abilities, LegionTowers.T1_BEE_TOWER.id(), Map.of(
                "maxSwarmStacks", 4.0,
                "poisonDamagePerStack", 3.0,
                "poisonDamagePerSwarmStack", 0.5,
                "maxPoisonStacks", 4.0,
                "poisonStacksPerSwarmStack", 1.0,
                "poisonDurationTicks", 80.0,
                "poisonTickIntervalTicks", 20.0
        ));
        putAbilities(abilities, LegionTowers.T2_BEE_TOWER.id(), Map.of(
                "maxSwarmStacks", 4.0,
                "poisonDamagePerStack", 4.5,
                "poisonDamagePerSwarmStack", 0.75,
                "maxPoisonStacks", 5.0,
                "poisonStacksPerSwarmStack", 1.0,
                "poisonDurationTicks", 100.0,
                "poisonTickIntervalTicks", 20.0
        ));
        putAbilities(abilities, LegionTowers.T3_BEE_TOWER.id(), Map.of(
                "maxSwarmStacks", 4.0,
                "poisonDamagePerStack", 6.0,
                "poisonDamagePerSwarmStack", 1.0,
                "maxPoisonStacks", 6.0,
                "poisonStacksPerSwarmStack", 1.0,
                "poisonDurationTicks", 140.0,
                "poisonTickIntervalTicks", 20.0
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
        putAbilities(abilities, LegionTowers.T1_GOAT_TOWER.id(), Map.of(
                "radius", 5.0,
                "damageBonus", 0.02,
                "damageReduction", 0.02,
                "cloneDamageBonus", 0.015,
                "cloneDamageReduction", 0.02,
                "maxStacks", 3.0,
                "buffDurationTicks", 120.0
        ));
        putAbilities(abilities, LegionTowers.T2_STRONG_GOAT_TOWER.id(), Map.of(
                "radius", 6.0,
                "damageBonus", 0.035,
                "damageReduction", 0.04,
                "cloneDamageBonus", 0.03,
                "cloneDamageReduction", 0.04,
                "maxStacks", 3.0,
                "buffDurationTicks", 120.0
        ));
        putAbilities(abilities, LegionTowers.T3_EXTREME_GOAT_TOWER.id(), Map.of(
                "radius", 7.0,
                "damageBonus", 0.05,
                "damageReduction", 0.065,
                "cloneDamageBonus", 0.065,
                "cloneDamageReduction", 0.065,
                "maxStacks", 3.0,
                "buffDurationTicks", 120.0
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
        putAbilities(abilities, ResonanceTowers.FOCUS_CRYSTAL.id(), resonanceAbilities(1, ResonanceAspect.FOCUS));
        putAbilities(abilities, ResonanceTowers.FOCUS_PRISM.id(), resonanceAbilities(2, ResonanceAspect.FOCUS));
        putAbilities(abilities, ResonanceTowers.FOCUS_CORE.id(), resonanceAbilities(3, ResonanceAspect.FOCUS));
        putAbilities(abilities, ResonanceTowers.WAVE_CRYSTAL.id(), resonanceAbilities(1, ResonanceAspect.WAVE));
        putAbilities(abilities, ResonanceTowers.WAVE_PRISM.id(), resonanceAbilities(2, ResonanceAspect.WAVE));
        putAbilities(abilities, ResonanceTowers.WAVE_CORE.id(), resonanceAbilities(3, ResonanceAspect.WAVE));
        putAbilities(abilities, ResonanceTowers.FROST_CRYSTAL.id(), resonanceAbilities(1, ResonanceAspect.FROST));
        putAbilities(abilities, ResonanceTowers.FROST_PRISM.id(), resonanceAbilities(2, ResonanceAspect.FROST));
        putAbilities(abilities, ResonanceTowers.FROST_CORE.id(), resonanceAbilities(3, ResonanceAspect.FROST));
        putAbilities(abilities, ResonanceTowers.AMPLIFY_CRYSTAL.id(), resonanceAbilities(1, ResonanceAspect.AMPLIFY));
        putAbilities(abilities, ResonanceTowers.AMPLIFY_PRISM.id(), resonanceAbilities(2, ResonanceAspect.AMPLIFY));
        putAbilities(abilities, ResonanceTowers.AMPLIFY_CORE.id(), resonanceAbilities(3, ResonanceAspect.AMPLIFY));
        putNetherAbilities(abilities);
        putEndAbilities(abilities);
        putOceanAbilities(abilities);

        return new TowerBalanceConfig(
                towers,
                upgradeCosts,
                abilities,
                IllusionCloneQueueConfig.defaultConfig(),
                VillagerAdvConfig.defaultConfig()
        );
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

        IllusionCloneQueueConfig mergedIllusionCloneQueue = illusionCloneQueue.withMissingDefaults(defaults.illusionCloneQueue);
        VillagerAdvConfig mergedVillagerAdv = villagerAdv.withMissingDefaults(defaults.villagerAdv);

        return new TowerBalanceConfig(mergedTowers, mergedUpgradeCosts, mergedAbilities, mergedIllusionCloneQueue, mergedVillagerAdv);
    }

    public static String upgradeKey(String fromTowerId, String upgradeId) {
        return fromTowerId + "->" + upgradeId;
    }

    private static void addTower(Map<String, TowerStats> towers, TowerType type) {
        towers.put(type.id(), TowerStats.from(type));
    }

    private static void addTower(
            Map<String, TowerStats> towers,
            TowerType type,
            long mineralCost,
            double maxHealth,
            double range,
            double damage,
            int attackIntervalTicks,
            int aggroPriority
    ) {
        towers.put(type.id(), new TowerStats(mineralCost, maxHealth, range, damage, attackIntervalTicks, aggroPriority));
    }

    private static void addVillagerAdvTowers(Map<String, TowerStats> towers) {
        addTower(towers, VillagerTowers.ADV_T1_SPLASH_TOWER, 50, 40.0, 5.5, 5.0, 10, 0);
        addTower(towers, VillagerTowers.ADV_T2_LIBRARIAN_TOWER, 110, 60.0, 7.0, 8.0, 10, 5);
        addTower(towers, VillagerTowers.ADV_T3_CLERIC_TOWER, 180, 80.0, 7.0, 10.0, 10, 10);
        addTower(towers, VillagerTowers.ADV_T1_GOLEM_TOWER, 50, 120.0, 2.0, 5.0, 20, 35);
        addTower(towers, VillagerTowers.ADV_T2_GOLEM_TOWER, 180, 200.0, 2.0, 8.0, 20, 50);
        addTower(towers, VillagerTowers.ADV_T3_GOLEM_TOWER, 350, 300.0, 3.0, 10.0, 20, 80);
        addTower(towers, VillagerTowers.ADV_T1_ALLAY_TOWER, 80, 40.0, 5.0, 2.0, 15, -5);
        addTower(towers, VillagerTowers.ADV_T2_ALLAY_TOWER, 200, 50.0, 5.0, 4.0, 15, -5);
        addTower(towers, VillagerTowers.ADV_T2_WEAPON_SMITH_TOWER, 250, 50.0, 12.0, 5.0, 15, -5);
        addTower(towers, VillagerTowers.ADV_T3_ARMORER_TOWER, 300, 70.0, 7.0, 10.0, 15, -5);
        addTower(towers, VillagerTowers.ADV_T3_WEAPON_SMITH_TOWER, 350, 60.0, 12.0, 7.0, 15, -5);
        addTower(towers, VillagerTowers.ADV_T1_CAT_TOWER, 60, 50.0, 10.0, 10.0, 15, 5);
        addTower(towers, VillagerTowers.ADV_T2_ANTI_TANKER_CAT_TOWER, 180, 50.0, 12.0, 20.0, 15, 5);
        addTower(towers, VillagerTowers.ADV_T2_LANE_CLEAR_CAT_TOWER, 200, 50.0, 10.0, 15.0, 15, 5);
        addTower(towers, VillagerTowers.ADV_T3_ANTI_TANKER_CAT_TOWER, 250, 50.0, 15.0, 25.0, 15, 5);
        addTower(towers, VillagerTowers.ADV_T3_LANE_CLEAR_CAT_TOWER, 275, 50.0, 10.0, 20.0, 10, 5);
    }

    private static void putUpgrade(Map<String, Long> upgrades, TowerType from, String upgradeId, long cost) {
        upgrades.put(upgradeKey(from.id(), upgradeId), cost);
    }

    private static void putVillagerAdvUpgrades(Map<String, Long> upgrades) {
        putUpgrade(upgrades, VillagerTowers.ADV_T1_SPLASH_TOWER, "villager_splash_t2", 80);
        putUpgrade(upgrades, VillagerTowers.ADV_T2_LIBRARIAN_TOWER, "villager_splash_t3", 150);
        putUpgrade(upgrades, VillagerTowers.ADV_T1_GOLEM_TOWER, "t2_golem_tower", 100);
        putUpgrade(upgrades, VillagerTowers.ADV_T2_GOLEM_TOWER, "t3_golem_tower", 200);
        putUpgrade(upgrades, VillagerTowers.ADV_T1_ALLAY_TOWER, "t2_allay_tower", 150);
        putUpgrade(upgrades, VillagerTowers.ADV_T1_ALLAY_TOWER, "t2_weapon_smith_tower", 180);
        putUpgrade(upgrades, VillagerTowers.ADV_T2_ALLAY_TOWER, "t3_armorer_tower", 200);
        putUpgrade(upgrades, VillagerTowers.ADV_T2_WEAPON_SMITH_TOWER, "t3_weapon_smith_tower", 200);
        putUpgrade(upgrades, VillagerTowers.ADV_T1_CAT_TOWER, "t2_anti_tanker_cat_tower", 120);
        putUpgrade(upgrades, VillagerTowers.ADV_T1_CAT_TOWER, "t2_lane_clear_cat_tower", 120);
        putUpgrade(upgrades, VillagerTowers.ADV_T2_ANTI_TANKER_CAT_TOWER, "t3_anti_tanker_cat_tower", 210);
        putUpgrade(upgrades, VillagerTowers.ADV_T2_LANE_CLEAR_CAT_TOWER, "t3_lane_clear_cat_tower", 210);
    }

    private static void addNetherTowers(Map<String, TowerStats> towers) {
        addTower(towers, NetherTowers.T1_STRIDER);
        addTower(towers, NetherTowers.T2_PIGLIN);
        addTower(towers, NetherTowers.T3_PIGLIN_BRUTE);
        addTower(towers, NetherTowers.T1_HOGLIN);
        addTower(towers, NetherTowers.T2_ZOGLIN);
        addTower(towers, NetherTowers.T3_ZOMBIFIED_PIGLIN);
        addTower(towers, NetherTowers.T1_MAGMA_CUBE);
        addTower(towers, NetherTowers.T2_BLAZE);
        addTower(towers, NetherTowers.T3_GHAST);
        addTower(towers, NetherTowers.T1_SKELETON);
        addTower(towers, NetherTowers.T2_WITHER_SKELETON);
        addTower(towers, NetherTowers.T3_WITHER);
    }

    private static void addEndTowers(Map<String, TowerStats> towers) {
        addTower(towers, EndTowers.BASE_END_TOWER);
        addTower(towers, EndTowers.T1_ENDERMITE_TOWER);
        addTower(towers, EndTowers.T2_ENDERMAN_TOWER);
        addTower(towers, EndTowers.T3_END_CRYSTAL_TOWER);
        addTower(towers, EndTowers.T1_SHULKER_TOWER);
        addTower(towers, EndTowers.T2_SHULKER_TOWER);
        addTower(towers, EndTowers.T3_SHULKER_TOWER);
    }

    private static void addOceanTowers(Map<String, TowerStats> towers) {
        addTower(towers, OceanTowers.T1_WATER);
        addTower(towers, OceanTowers.T2_SPRING_WATER);
        addTower(towers, OceanTowers.T3_CURRENT);
        addTower(towers, OceanTowers.T1_PUFFERFISH);
        addTower(towers, OceanTowers.T2_GUARDIAN);
        addTower(towers, OceanTowers.T3_ELDER_GUARDIAN);
        addTower(towers, OceanTowers.T1_TROPICAL_FISH);
        addTower(towers, OceanTowers.T2_LARGE_TROPICAL_FISH);
        addTower(towers, OceanTowers.T3_GIANT_TROPICAL_FISH);
        addTower(towers, OceanTowers.T1_SQUID);
        addTower(towers, OceanTowers.T2_GLOW_SQUID);
        addTower(towers, OceanTowers.T3_DOLPHIN);
        addTower(towers, OceanTowers.T1_SALMON);
        addTower(towers, OceanTowers.T2_LARGE_SALMON);
        addTower(towers, OceanTowers.T3_GIANT_SALMON);
        addTower(towers, OceanTowers.T1_COD);
        addTower(towers, OceanTowers.T2_LARGE_COD);
        addTower(towers, OceanTowers.T3_GIANT_COD);
    }

    private static void putNetherUpgrades(Map<String, Long> upgrades) {
        putUpgrade(upgrades, NetherTowers.T1_STRIDER, NetherTowers.T2_PIGLIN.id(), 100);
        putUpgrade(upgrades, NetherTowers.T2_PIGLIN, NetherTowers.T3_PIGLIN_BRUTE.id(), 180);
        putUpgrade(upgrades, NetherTowers.T1_HOGLIN, NetherTowers.T2_ZOGLIN.id(), 110);
        putUpgrade(upgrades, NetherTowers.T2_ZOGLIN, NetherTowers.T3_ZOMBIFIED_PIGLIN.id(), 190);
        putUpgrade(upgrades, NetherTowers.T1_MAGMA_CUBE, NetherTowers.T2_BLAZE.id(), 95);
        putUpgrade(upgrades, NetherTowers.T2_BLAZE, NetherTowers.T3_GHAST.id(), 180);
        putUpgrade(upgrades, NetherTowers.T1_SKELETON, NetherTowers.T2_WITHER_SKELETON.id(), 95);
        putUpgrade(upgrades, NetherTowers.T2_WITHER_SKELETON, NetherTowers.T3_WITHER.id(), 180);
    }

    private static void putEndUpgrades(Map<String, Long> upgrades) {
        putUpgrade(upgrades, EndTowers.T1_ENDERMITE_TOWER, EndTowers.T2_ENDERMAN_TOWER.id(), 80);
        putUpgrade(upgrades, EndTowers.T2_ENDERMAN_TOWER, EndTowers.T3_END_CRYSTAL_TOWER.id(), 130);
        putUpgrade(upgrades, EndTowers.T1_SHULKER_TOWER, EndTowers.T2_SHULKER_TOWER.id(), 80);
        putUpgrade(upgrades, EndTowers.T2_SHULKER_TOWER, EndTowers.T3_SHULKER_TOWER.id(), 130);
    }

    private static void putOceanUpgrades(Map<String, Long> upgrades) {
        putUpgrade(upgrades, OceanTowers.T1_WATER, OceanTowers.T2_SPRING_WATER.id(), 60);
        putUpgrade(upgrades, OceanTowers.T2_SPRING_WATER, OceanTowers.T3_CURRENT.id(), 150);
        putUpgrade(upgrades, OceanTowers.T1_PUFFERFISH, OceanTowers.T2_GUARDIAN.id(), 130);
        putUpgrade(upgrades, OceanTowers.T2_GUARDIAN, OceanTowers.T3_ELDER_GUARDIAN.id(), 210);
        putUpgrade(upgrades, OceanTowers.T1_TROPICAL_FISH, OceanTowers.T2_LARGE_TROPICAL_FISH.id(), 110);
        putUpgrade(upgrades, OceanTowers.T2_LARGE_TROPICAL_FISH, OceanTowers.T3_GIANT_TROPICAL_FISH.id(), 190);
        putUpgrade(upgrades, OceanTowers.T1_SQUID, OceanTowers.T2_GLOW_SQUID.id(), 120);
        putUpgrade(upgrades, OceanTowers.T2_GLOW_SQUID, OceanTowers.T3_DOLPHIN.id(), 210);
        putUpgrade(upgrades, OceanTowers.T1_SALMON, OceanTowers.T2_LARGE_SALMON.id(), 100);
        putUpgrade(upgrades, OceanTowers.T2_LARGE_SALMON, OceanTowers.T3_GIANT_SALMON.id(), 200);
        putUpgrade(upgrades, OceanTowers.T1_COD, OceanTowers.T2_LARGE_COD.id(), 100);
        putUpgrade(upgrades, OceanTowers.T2_LARGE_COD, OceanTowers.T3_GIANT_COD.id(), 210);
    }

    private static void putNetherAbilities(Map<String, Map<String, Double>> abilities) {
        putAbilities(abilities, NetherTower.CONFIG_ID, Map.ofEntries(
                Map.entry("netherDecayMaxHealthRatioPerSecond", 0.0667),
                Map.entry("zombieDecayMaxHealthRatioPerSecond", 0.143),
                Map.entry("zombieReviveHealthRatio", 1.0),
                Map.entry("lowHealthThreshold", 0.60),
                Map.entry("criticalHealthThreshold", 0.30),
                Map.entry("damagePerMissingHealth", 0.80),
                Map.entry("lowHealthDamageBonusCap", 0.75),
                Map.entry("netherLifeStealRatio", 0.30),
                Map.entry("zombieLifeStealRatio", 0.04),
                Map.entry("lifeStealPerMissingHealth", 0.40),
                Map.entry("lifeStealBonusCap", 0.50),
                Map.entry("effectRefreshTicks", 25.0)
        ));
        putAbilities(abilities, NetherTowers.T1_STRIDER.id(), Map.of(
                "lifeStealBonus", 0.08,
                "decayReductionRatio", 0.50,
                "decayReductionTicks", 40.0
        ));
        putAbilities(abilities, NetherTowers.T2_PIGLIN.id(), Map.ofEntries(
                Map.entry("lifeStealBonus", 0.12),
                Map.entry("decayReductionRatio", 0.60),
                Map.entry("decayReductionTicks", 50.0),
                Map.entry("incomeDamageBonus", 0.35),
                Map.entry("killDamageBonus", 0.15),
                Map.entry("killDamageBonusTicks", 60.0),
                Map.entry("zombieAttackSpeedBonus", 0.35)
        ));
        putAbilities(abilities, NetherTowers.T3_PIGLIN_BRUTE.id(), Map.ofEntries(
                Map.entry("lifeStealBonus", 0.16),
                Map.entry("decayReductionRatio", 0.70),
                Map.entry("decayReductionTicks", 60.0),
                Map.entry("incomeDamageBonus", 0.50),
                Map.entry("killDamageBonus", 0.25),
                Map.entry("killDamageBonusTicks", 80.0),
                Map.entry("zombieAttackSpeedBonus", 0.35),
                Map.entry("tankDamageBonus", 0.75),
                Map.entry("tankLifeStealBonus", 0.25),
                Map.entry("highHealthThreshold", 120.0),
                Map.entry("zombieTransitionDamageBonus", 0.40),
                Map.entry("zombieTransitionDamageBonusTicks", 80.0)
        ));
        putAbilities(abilities, NetherTowers.T1_HOGLIN.id(), Map.of(
                "splashRadius", 0.75,
                "splashDamageRatio", 0.50,
                "criticalDamageReduction", 0.20
        ));
        putAbilities(abilities, NetherTowers.T2_ZOGLIN.id(), Map.ofEntries(
                Map.entry("splashRadius", 1.25),
                Map.entry("splashDamageRatio", 0.75),
                Map.entry("criticalDamageReduction", 0.25),
                Map.entry("missingHealthAttackSpeedBonusCap", 0.35),
                Map.entry("zombieSplashRadiusBonus", 0.50)
        ));
        putAbilities(abilities, NetherTowers.T3_ZOMBIFIED_PIGLIN.id(), Map.ofEntries(
                Map.entry("splashRadius", 1.50),
                Map.entry("splashDamageRatio", 1.00),
                Map.entry("criticalDamageReduction", 0.30),
                Map.entry("missingHealthAttackSpeedBonusCap", 0.50),
                Map.entry("zombieMissingHealthAttackSpeedBonusCap", 0.75),
                Map.entry("zombieSplashRadiusBonus", 0.75)
        ));
        putAbilities(abilities, NetherTowers.T1_MAGMA_CUBE.id(), Map.ofEntries(
                Map.entry("splashRadius", 0.75),
                Map.entry("splashDamageRatio", 0.50),
                Map.entry("missingHealthAttackSpeedBonusCap", 0.30),
                Map.entry("pulseRadius", 2.0),
                Map.entry("pulseDamageRatio", 1.50),
                Map.entry("pulseIntervalTicks", 40.0),
                Map.entry("zombieTransitionPulseRadius", 2.5),
                Map.entry("zombieTransitionPulseDamageRatio", 2.50)
        ));
        putAbilities(abilities, NetherTowers.T2_BLAZE.id(), Map.ofEntries(
                Map.entry("splashRadius", 1.25),
                Map.entry("splashDamageRatio", 0.75),
                Map.entry("missingHealthAttackSpeedBonusCap", 0.50),
                Map.entry("pulseRadius", 2.25),
                Map.entry("pulseDamageRatio", 1.75),
                Map.entry("pulseIntervalTicks", 40.0),
                Map.entry("zombieTransitionPulseRadius", 2.75),
                Map.entry("zombieTransitionPulseDamageRatio", 3.00),
                Map.entry("extraAttackEvery", 3.0),
                Map.entry("secondaryRange", 7.0),
                Map.entry("extraAttackDamageRatio", 0.60)
        ));
        putAbilities(abilities, NetherTowers.T3_GHAST.id(), Map.ofEntries(
                Map.entry("splashRadius", 1.75),
                Map.entry("splashDamageRatio", 1.00),
                Map.entry("lowHealthSplashRadiusBonus", 0.75),
                Map.entry("missingHealthAttackSpeedBonusCap", 0.75),
                Map.entry("pulseRadius", 2.50),
                Map.entry("pulseDamageRatio", 2.00),
                Map.entry("pulseIntervalTicks", 40.0),
                Map.entry("zombieTransitionPulseRadius", 3.00),
                Map.entry("zombieTransitionPulseDamageRatio", 3.50),
                Map.entry("extraAttackEvery", 2.0),
                Map.entry("secondaryRange", 9.5),
                Map.entry("extraAttackDamageRatio", 0.75),
                Map.entry("criticalMarkDamageTakenBonus", 0.20),
                Map.entry("markDurationTicks", 60.0)
        ));
        putAbilities(abilities, NetherTowers.T1_SKELETON.id(), Map.of(
                "lowTargetHealthThreshold", 0.40,
                "lowTargetDamageBonus", 0.35,
                "criticalKillLifeStealRatio", 0.30
        ));
        putAbilities(abilities, NetherTowers.T2_WITHER_SKELETON.id(), Map.ofEntries(
                Map.entry("lowTargetHealthThreshold", 0.45),
                Map.entry("lowTargetDamageBonus", 0.50),
                Map.entry("criticalKillLifeStealRatio", 0.35),
                Map.entry("markDamageTakenBonus", 0.05),
                Map.entry("markDurationTicks", 80.0),
                Map.entry("maxMarkStacks", 3.0),
                Map.entry("zombieMarkDamageTakenBonus", 0.04)
        ));
        putAbilities(abilities, NetherTowers.T3_WITHER.id(), Map.ofEntries(
                Map.entry("lowTargetHealthThreshold", 0.50),
                Map.entry("lowTargetDamageBonus", 0.75),
                Map.entry("criticalKillLifeStealRatio", 0.40),
                Map.entry("markDamageTakenBonus", 0.10),
                Map.entry("markDurationTicks", 80.0),
                Map.entry("maxMarkStacks", 3.0),
                Map.entry("zombieMarkDamageTakenBonus", 0.0),
                Map.entry("highHealthThreshold", 500.0),
                Map.entry("highHealthDamageBonus", 0.60),
                Map.entry("criticalSplashRadius", 2.0),
                Map.entry("criticalSplashDamageRatio", 0.90),
                Map.entry("criticalMarkDamageTakenBonus", 0.75),
                Map.entry("zombieExecuteThreshold", 0.40),
                Map.entry("zombieExecuteDamageBonus", 0.90),
                Map.entry("zombieLifeStealRatio", 0.0)
        ));
    }

    private static void putEndAbilities(Map<String, Map<String, Double>> abilities) {
        putAbilities(abilities, EndTowers.T1_SHULKER_TOWER.id(), Map.of(
                "damageReduction", 0.10
        ));
        putAbilities(abilities, EndTowers.T2_SHULKER_TOWER.id(), Map.of(
                "damageReduction", 0.30
        ));
        putAbilities(abilities, EndTowers.T3_SHULKER_TOWER.id(), Map.of(
                "damageReduction", 0.50
        ));
        putAbilities(abilities, EndTower.CONFIG_ID, Map.ofEntries(
                Map.entry("dragonEvolutionMaxHealth", 2000.0),
                Map.entry("absorptionDurationTicks", 200.0),
                Map.entry("absorptionHealAmount", 25.0),
                Map.entry("transferHealingPerTower", 1.0),
                Map.entry("transferHealingIntervalTicks", 20.0),
                Map.entry("roundAbsorptionAttackIntervalEvery", 1.0),
                Map.entry("roundAbsorptionAttackIntervalReductionTicks", 1.0),
                Map.entry("roundHealthRatio", 0.30),
                Map.entry("roundDamageRatio", 0.30),
                Map.entry("permanentHealthRatio", 0.05),
                Map.entry("permanentDamageRatio", 0.05),
                Map.entry("dragonAttackRangeBonus", 2.0),
                Map.entry("dragonDamageBonus", 0.10),
                Map.entry("endCrystalAttackIntervalEvery", 30.0),
                Map.entry("attackIntervalReductionPerStep", 1.0),
                Map.entry("endCrystalAttackRangeEvery", 60.0),
                Map.entry("attackRangePerStep", 0.5),
                Map.entry("attackRangeCap", 3.0),
                Map.entry("shulkerLifeStealEvery", 20.0),
                Map.entry("lifeStealPerStep", 0.01),
                Map.entry("lifeStealCap", 0.15),
                Map.entry("shulkerRegenerationEvery", 20.0),
                Map.entry("regenerationPerStep", 1.0),
                Map.entry("regenerationCap", 10.0),
                Map.entry("regenerationIntervalTicks", 20.0),
                Map.entry("endCrystalSplashThreshold1", 15.0),
                Map.entry("endCrystalSplashThreshold2", 60.0),
                Map.entry("endCrystalSplashThreshold3", 150.0),
                Map.entry("endCrystalSplashThreshold4", 300.0),
                Map.entry("splashRadiusCap", 4.0),
                Map.entry("splashDamageRatio", 0.60),
                Map.entry("shulkerReductionEvery", 60.0),
                Map.entry("damageReductionPerStep", 0.04),
                Map.entry("damageReductionCap", 0.20),
                Map.entry("maxAttackIntervalReductionTicks", 10.0),
                Map.entry("minimumAttackIntervalTicks", 5.0),
                Map.entry("dragonFinalDamageBonus", 0.10),
                Map.entry("dragonIncomeDebuffResistance", 0.10)
        ));
    }

    private static void putOceanAbilities(Map<String, Map<String, Double>> abilities) {
        putAbilities(abilities, OceanTower.CONFIG_ID, Map.of(
                "initialWater", 50.0,
                "waterScale", 100.0,
                "incomeCoefficientMultiplier", 2.0,
                "empoweredAbilityWaterThreshold", 100.0,
                "empoweredAbilityWaterCostMultiplier", 2.0,
                "empoweredAbilityEffectMultiplier", 1.5,
                "dehydratedDamageMultiplier", 0.30,
                "dehydratedAttackSpeedReduction", 0.60,
                "dehydrationMaxHealthDamagePerSecond", 0.02
        ));

        putAbilities(abilities, OceanTowers.T1_WATER.id(), oceanSupplyAbilities(20.0, 1.0));
        putAbilities(abilities, OceanTowers.T2_SPRING_WATER.id(), oceanSupplyAbilities(30.0, 1.75));
        putAbilities(abilities, OceanTowers.T3_CURRENT.id(), oceanSupplyAbilities(40.0, 2.5));

        putAbilities(abilities, OceanTowers.T1_PUFFERFISH.id(), oceanTankAbilities(0.50, 2.0, 0.05, 1.0, 24.0));
        putAbilities(abilities, OceanTowers.T2_GUARDIAN.id(), oceanTankAbilities(0.75, 3.0, 0.10, 1.0, 50.0));
        putAbilities(abilities, OceanTowers.T3_ELDER_GUARDIAN.id(), oceanTankAbilities(1.00, 5.0, 0.15, 2.0, 90.0));

        putAbilities(abilities, OceanTowers.T1_TROPICAL_FISH.id(), oceanSupportAbilities(8.0, 0.08, 0.10, 100.0));
        putAbilities(abilities, OceanTowers.T2_LARGE_TROPICAL_FISH.id(), oceanSupportAbilities(14.0, 0.12, 0.15, 90.0));
        putAbilities(abilities, OceanTowers.T3_GIANT_TROPICAL_FISH.id(), oceanSupportAbilities(20.0, 0.18, 0.22, 80.0));

        putAbilities(abilities, OceanTowers.T1_SQUID.id(), oceanHealAbilities(6.0, 2.0, 15.0, 100.0));
        putAbilities(abilities, OceanTowers.T2_GLOW_SQUID.id(), oceanHealAbilities(10.0, 2.5, 40.0, 90.0));
        putAbilities(abilities, OceanTowers.T3_DOLPHIN.id(), oceanHealAbilities(16.0, 3.0, 80.0, 80.0));

        putAbilities(abilities, OceanTowers.T1_SALMON.id(), oceanSplashAbilities(0.50, 1.0, 1.0, 1.0, 0.50));
        putAbilities(abilities, OceanTowers.T2_LARGE_SALMON.id(), oceanSplashAbilities(0.75, 1.0, 2.0, 1.5, 0.65));
        putAbilities(abilities, OceanTowers.T3_GIANT_SALMON.id(), oceanSplashAbilities(1.00, 1.0, 3.0, 2.0, 0.80));

        putAbilities(abilities, OceanTowers.T1_COD.id(), oceanHunterAbilities(0.50, 2.0));
        putAbilities(abilities, OceanTowers.T2_LARGE_COD.id(), oceanHunterAbilities(0.75, 3.0));
        putAbilities(abilities, OceanTowers.T3_GIANT_COD.id(), oceanHunterAbilities(1.00, 3.0));
    }

    private static Map<String, Double> oceanSupplyAbilities(double waveStartWater, double waterPerSecond) {
        return Map.of(
                "supplyRadius", 2.0,
                "waveStartWater", waveStartWater,
                "waterPerSupply", waterPerSecond,
                "supplyIntervalTicks", 20.0
        );
    }

    private static Map<String, Double> oceanTankAbilities(
            double coefficient,
            double attackCost,
            double damageReduction,
            double transferCost,
            double transferCap
    ) {
        LinkedHashMap<String, Double> values = oceanAttackAbilities(coefficient, attackCost);
        values.put("damageReduction", damageReduction);
        values.put("transferWaterCost", transferCost);
        values.put("transferRadius", 2.0);
        values.put("transferCap", transferCap);
        values.put("transferCooldownTicks", 50.0);
        return values;
    }

    private static Map<String, Double> oceanSupportAbilities(
            double waterCost,
            double damageBonus,
            double attackSpeedBonus,
            double intervalTicks
    ) {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>();
        values.put("abilityWaterCost", waterCost);
        values.put("supportRadius", 2.0);
        values.put("damageBonus", damageBonus);
        values.put("attackSpeedBonus", attackSpeedBonus);
        values.put("buffDurationTicks", 100.0);
        values.put("supportIntervalTicks", intervalTicks);
        return values;
    }

    private static Map<String, Double> oceanSplashAbilities(
            double coefficient,
            double attackCost,
            double splashCost,
            double splashRadius,
            double splashRatio
    ) {
        LinkedHashMap<String, Double> values = oceanAttackAbilities(coefficient, attackCost);
        values.put("splashWaterCost", splashCost);
        values.put("splashRadius", splashRadius);
        values.put("splashDamageRatio", splashRatio);
        return values;
    }

    private static Map<String, Double> oceanHealAbilities(
            double waterCost,
            double radius,
            double healAmount,
            double intervalTicks
    ) {
        return Map.of(
                "abilityWaterCost", waterCost,
                "healRadius", radius,
                "healAmount", healAmount,
                "healIntervalTicks", intervalTicks
        );
    }

    private static Map<String, Double> oceanHunterAbilities(double coefficient, double attackCost) {
        LinkedHashMap<String, Double> values = oceanAttackAbilities(coefficient, attackCost);
        values.put("incomeWaterCost", 1.0);
        return values;
    }

    private static LinkedHashMap<String, Double> oceanAttackAbilities(double coefficient, double attackCost) {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>();
        values.put("waterDamageCoefficient", coefficient);
        values.put("attackWaterCost", attackCost);
        return values;
    }

    private static void putAbilities(Map<String, Map<String, Double>> abilities, String towerId, Map<String, Double> values) {
        abilities.put(towerId, values);
    }

    private static void putVillagerAdvAbilities(Map<String, Map<String, Double>> abilities) {
        copyAbility(abilities, VillagerTowers.T2_LIBRARIAN_TOWER, VillagerTowers.ADV_T2_LIBRARIAN_TOWER);
        copyAbility(abilities, VillagerTowers.T3_CLERIC_TOWER, VillagerTowers.ADV_T3_CLERIC_TOWER);
        copyAbility(abilities, VillagerTowers.T2_GOLEM_TOWER, VillagerTowers.ADV_T2_GOLEM_TOWER);
        copyAbility(abilities, VillagerTowers.T3_GOLEM_TOWER, VillagerTowers.ADV_T3_GOLEM_TOWER);
        copyAbility(abilities, VillagerTowers.T1_ALLAY_TOWER, VillagerTowers.ADV_T1_ALLAY_TOWER);
        copyAbility(abilities, VillagerTowers.T2_ALLAY_TOWER, VillagerTowers.ADV_T2_ALLAY_TOWER);
        copyAbility(abilities, VillagerTowers.T2_WEAPON_SMITH_TOWER, VillagerTowers.ADV_T2_WEAPON_SMITH_TOWER);
        copyAbility(abilities, VillagerTowers.T3_ARMORER_TOWER, VillagerTowers.ADV_T3_ARMORER_TOWER);
        copyAbility(abilities, VillagerTowers.T3_WEAPON_SMITH_TOWER, VillagerTowers.ADV_T3_WEAPON_SMITH_TOWER);
        copyAbility(abilities, VillagerTowers.T2_ANTI_TANKER_CAT_TOWER, VillagerTowers.ADV_T2_ANTI_TANKER_CAT_TOWER);
        copyAbility(abilities, VillagerTowers.T3_ANTI_TANKER_CAT_TOWER, VillagerTowers.ADV_T3_ANTI_TANKER_CAT_TOWER);
        copyAbility(abilities, VillagerTowers.T2_LANE_CLEAR_CAT_TOWER, VillagerTowers.ADV_T2_LANE_CLEAR_CAT_TOWER);
        copyAbility(abilities, VillagerTowers.T3_LANE_CLEAR_CAT_TOWER, VillagerTowers.ADV_T3_LANE_CLEAR_CAT_TOWER);
    }

    private static void copyAbility(Map<String, Map<String, Double>> abilities, TowerType from, TowerType to) {
        Map<String, Double> values = abilities.get(from.id());
        if (values != null) {
            putAbilities(abilities, to.id(), values);
        }
    }

    private static Map<String, Double> resonanceAbilities(int tier, ResonanceAspect aspect) {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>();
        values.put("linkRange", 1.0);
        values.put("maxLinksPerTower", 6.0);
        values.put("maxResonanceLevel", (double) tier);
        values.put("level1RequiredLinks", 1.0);
        values.put("level2RequiredLinks", 3.0);
        values.put("level3RequiredLinks", 5.0);
        switch (aspect) {
            case FOCUS -> {
                values.put("focusLevel1AttackSpeedBonus", tierValue(tier, 0.20, 0.30, 0.20));
                values.put("focusLevel2AttackSpeedBonus", tierValue(tier, 0.40, 0.60, 0.40));
                values.put("focusLevel2DamageBonus", 0.40);
                values.put("focusLevel3AttackSpeedBonus", tierValue(tier, 0.60, 0.60, 0.80));
                values.put("focusLevel3DamageBonus", 0.80);
                values.put("focusStrikeEveryAttacks", 2.0);
                values.put("focusStrikeDamageRatio", 2.50);
            }
            case WAVE -> {
                values.put("waveLevel1AttackSpeedBonus", tierValue(tier, 0.30, 0.30, 0.40));
                values.put("waveLevel2SplashRadius", 1.25);
                values.put("waveLevel2SplashDamageRatio", 0.50);
                values.put("waveLevel3SplashRadius", tierValue(tier, 1.50, 1.25, 1.25));
                values.put("waveLevel3SplashDamageRatio", tierValue(tier, 0.80, 0.80, 1.00));
                values.put("wavePulseEveryAttacks", 2.0);
                values.put("wavePulseRadius", tierValue(tier, 2.0, 1.5, 1.5));
                values.put("wavePulseDamageRatio", tierValue(tier, 2.0, 2.0, 1.75));
            }
            case FROST -> {
                values.put("frostLevel1SlowMagnitude", 0.15);
                values.put("frostLevel1SlowTicks", 20.0);
                values.put("frostLevel2SlowMagnitude", 0.30);
                values.put("frostLevel2SlowTicks", 30.0);
                values.put("frostLevel3SlowMagnitude", 0.40);
                values.put("frostLevel3SlowTicks", 50.0);
                values.put("frostPulseEveryAttacks", tierValue(tier, 2.0, 3.0, 2.0));
                values.put("frostPulseRadius", tierValue(tier, 2.0, 1.5, 1.75));
                values.put("frostPulseDamageRatio", 0.60);
                values.put("frostPulseSlowMagnitude", 0.75);
                values.put("frostPulseSlowTicks", 60.0);
                values.put("frostLevel1AttackSpeedReductionMagnitude", 0.15);
                values.put("frostLevel2AttackSpeedReductionMagnitude", 0.30);
                values.put("frostLevel3AttackSpeedReductionMagnitude", 0.40);
                values.put("frostLevel2AuraDamageVsSlowedBonus", tierValue(tier, 0.50, 0.50, 0.35));
                values.put("frostLevel3AuraDamageVsSlowedBonus", tierValue(tier, 1.50, 1.50, 1.00));
                values.put("frostAuraRange", 1.0);
                values.put("frostPulseAttackSpeedReductionMagnitude", tierValue(tier, 0.75, 0.75, 0.50));
                values.put("frostLevel2DamageVsSlowedBonus", 0.15);
                values.put("frostLevel3DamageVsSlowedBonus", 0.30);
            }
            case AMPLIFY -> {
                values.put("bloomLevel1DamageReduction", tierValue(tier, 0.15, 0.12, 0.12));
                values.put("bloomLevel2DamageReduction", tierValue(tier, 0.30, 0.20, 0.20));
                values.put("bloomLevel2AuraAttackSpeedBonus", tierValue(tier, 0.20, 0.20, 0.25));
                values.put("bloomLevel3DamageReduction", tierValue(tier, 0.35, 0.35, 0.40));
                values.put("bloomLevel3AuraAttackSpeedBonus", tierValue(tier, 0.50, 0.50, 0.35));
                values.put("bloomAuraRange", 1.0);
                values.put("bloomProtectEveryAttacks", 3.0);
                values.put("bloomProtectRadius", 2.0);
                values.put("bloomProtectHealRatio", tierValue(tier, 0.40, 0.40, 0.60));
                values.put("bloomProtectDamageReduction", 0.15);
                values.put("bloomProtectTicks", 60.0);
            }
        }
        return values;
    }

    private static double tierValue(int tier, double tierOne, double tierTwo, double tierThree) {
        return switch (tier) {
            case 1 -> tierOne;
            case 2 -> tierTwo;
            default -> tierThree;
        };
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

    public record IllusionCloneQueueConfig(Integer spreadTicks, Integer maxSpawnsPerTick) {
        public static IllusionCloneQueueConfig defaultConfig() {
            return new IllusionCloneQueueConfig(40, 8);
        }

        public IllusionCloneQueueConfig {
            spreadTicks = spreadTicks == null ? null : Math.max(1, spreadTicks);
            maxSpawnsPerTick = maxSpawnsPerTick == null ? null : Math.max(1, maxSpawnsPerTick);
        }

        public int resolvedSpreadTicks() {
            return spreadTicks == null ? defaultConfig().spreadTicks() : spreadTicks;
        }

        public int resolvedMaxSpawnsPerTick() {
            return maxSpawnsPerTick == null ? defaultConfig().maxSpawnsPerTick() : maxSpawnsPerTick;
        }

        public IllusionCloneQueueConfig withMissingDefaults(IllusionCloneQueueConfig defaults) {
            if (defaults == null) {
                return this;
            }
            return new IllusionCloneQueueConfig(
                    spreadTicks == null ? defaults.spreadTicks() : spreadTicks,
                    maxSpawnsPerTick == null ? defaults.maxSpawnsPerTick() : maxSpawnsPerTick
            );
        }
    }

    public record VillagerAdvConfig(
            Double experienceMax,
            Double experiencePerTower,
            Double experiencePerTier,
            Double reputationMax,
            Double reputationGainRoundMultiplier,
            Double reputationLossPerLeak,
            Integer effectDurationTicks,
            Double experienceBuffCap,
            Double reputationBuffCap,
            Map<String, Double> upgradeRequirements,
            Map<String, Map<String, Double>> buffs
    ) {
        public static VillagerAdvConfig defaultConfig() {
            LinkedHashMap<String, Double> upgradeRequirements = new LinkedHashMap<>();
            putAdvUpgrade(upgradeRequirements, VillagerTowers.ADV_T1_SPLASH_TOWER, "villager_splash_t2", 15.0);
            putAdvUpgrade(upgradeRequirements, VillagerTowers.ADV_T2_LIBRARIAN_TOWER, "villager_splash_t3", 45.0);
            putAdvUpgrade(upgradeRequirements, VillagerTowers.ADV_T1_GOLEM_TOWER, "t2_golem_tower", 15.0);
            putAdvUpgrade(upgradeRequirements, VillagerTowers.ADV_T2_GOLEM_TOWER, "t3_golem_tower", 45.0);
            putAdvUpgrade(upgradeRequirements, VillagerTowers.ADV_T1_ALLAY_TOWER, "t2_allay_tower", 15.0);
            putAdvUpgrade(upgradeRequirements, VillagerTowers.ADV_T1_ALLAY_TOWER, "t2_weapon_smith_tower", 15.0);
            putAdvUpgrade(upgradeRequirements, VillagerTowers.ADV_T2_ALLAY_TOWER, "t3_armorer_tower", 45.0);
            putAdvUpgrade(upgradeRequirements, VillagerTowers.ADV_T2_WEAPON_SMITH_TOWER, "t3_weapon_smith_tower", 45.0);
            putAdvUpgrade(upgradeRequirements, VillagerTowers.ADV_T1_CAT_TOWER, "t2_anti_tanker_cat_tower", 15.0);
            putAdvUpgrade(upgradeRequirements, VillagerTowers.ADV_T1_CAT_TOWER, "t2_lane_clear_cat_tower", 15.0);
            putAdvUpgrade(upgradeRequirements, VillagerTowers.ADV_T2_ANTI_TANKER_CAT_TOWER, "t3_anti_tanker_cat_tower", 45.0);
            putAdvUpgrade(upgradeRequirements, VillagerTowers.ADV_T2_LANE_CLEAR_CAT_TOWER, "t3_lane_clear_cat_tower", 45.0);

            LinkedHashMap<String, Map<String, Double>> buffs = new LinkedHashMap<>();
            putAdvBuffs(buffs, VillagerTowers.ADV_T1_SPLASH_TOWER, rangedBuffs());
            putAdvBuffs(buffs, VillagerTowers.ADV_T2_LIBRARIAN_TOWER, rangedBuffs());
            putAdvBuffs(buffs, VillagerTowers.ADV_T3_CLERIC_TOWER, rangedBuffs());
            putAdvBuffs(buffs, VillagerTowers.ADV_T1_GOLEM_TOWER, golemBuffs());
            putAdvBuffs(buffs, VillagerTowers.ADV_T2_GOLEM_TOWER, golemBuffs());
            putAdvBuffs(buffs, VillagerTowers.ADV_T3_GOLEM_TOWER, golemBuffs());
            putAdvBuffs(buffs, VillagerTowers.ADV_T1_ALLAY_TOWER, allayHealBuffs());
            putAdvBuffs(buffs, VillagerTowers.ADV_T2_ALLAY_TOWER, allayHealBuffs());
            putAdvBuffs(buffs, VillagerTowers.ADV_T2_WEAPON_SMITH_TOWER, supportIntervalBuffs());
            putAdvBuffs(buffs, VillagerTowers.ADV_T3_ARMORER_TOWER, allayHealBuffs());
            putAdvBuffs(buffs, VillagerTowers.ADV_T3_WEAPON_SMITH_TOWER, supportIntervalBuffs());
            putAdvBuffs(buffs, VillagerTowers.ADV_T1_CAT_TOWER, catBuffs());
            putAdvBuffs(buffs, VillagerTowers.ADV_T2_ANTI_TANKER_CAT_TOWER, antiTankerCatBuffs());
            putAdvBuffs(buffs, VillagerTowers.ADV_T2_LANE_CLEAR_CAT_TOWER, laneClearCatBuffs());
            putAdvBuffs(buffs, VillagerTowers.ADV_T3_ANTI_TANKER_CAT_TOWER, antiTankerCatBuffs());
            putAdvBuffs(buffs, VillagerTowers.ADV_T3_LANE_CLEAR_CAT_TOWER, laneClearCatBuffs());
            return new VillagerAdvConfig(
                    100.0,
                    1.0,
                    1.0,
                    100.0,
                    1.0,
                    0.5,
                    72000,
                    0.50,
                    0.30,
                    upgradeRequirements,
                    buffs
            );
        }

        public VillagerAdvConfig {
            experienceMax = experienceMax == null ? null : Math.max(0.0, experienceMax);
            experiencePerTower = experiencePerTower == null ? null : Math.max(0.0, experiencePerTower);
            experiencePerTier = experiencePerTier == null ? null : Math.max(0.0, experiencePerTier);
            reputationMax = reputationMax == null ? null : Math.max(0.0, reputationMax);
            reputationGainRoundMultiplier = reputationGainRoundMultiplier == null ? null : Math.max(0.0, reputationGainRoundMultiplier);
            reputationLossPerLeak = reputationLossPerLeak == null ? null : Math.max(0.0, reputationLossPerLeak);
            effectDurationTicks = effectDurationTicks == null ? null : Math.max(1, effectDurationTicks);
            experienceBuffCap = experienceBuffCap == null ? null : Math.max(0.0, experienceBuffCap);
            reputationBuffCap = reputationBuffCap == null ? null : Math.max(0.0, reputationBuffCap);
            upgradeRequirements = copyDoubleMap(upgradeRequirements);
            buffs = copyNestedDoubleMap(buffs);
        }

        public double resolvedExperienceMax() {
            return experienceMax == null ? defaultConfig().experienceMax() : experienceMax;
        }

        public double resolvedExperiencePerTower() {
            return experiencePerTower == null ? defaultConfig().experiencePerTower() : experiencePerTower;
        }

        public double resolvedExperiencePerTier() {
            return experiencePerTier == null ? defaultConfig().experiencePerTier() : experiencePerTier;
        }

        public double resolvedReputationMax() {
            return reputationMax == null ? defaultConfig().reputationMax() : reputationMax;
        }

        public double resolvedReputationGainRoundMultiplier() {
            return reputationGainRoundMultiplier == null ? defaultConfig().reputationGainRoundMultiplier() : reputationGainRoundMultiplier;
        }

        public double resolvedReputationLossPerLeak() {
            return reputationLossPerLeak == null ? defaultConfig().reputationLossPerLeak() : reputationLossPerLeak;
        }

        public int resolvedEffectDurationTicks() {
            return effectDurationTicks == null ? defaultConfig().effectDurationTicks() : effectDurationTicks;
        }

        public double resolvedExperienceBuffCap() {
            return experienceBuffCap == null ? defaultConfig().experienceBuffCap() : experienceBuffCap;
        }

        public double resolvedReputationBuffCap() {
            return reputationBuffCap == null ? defaultConfig().reputationBuffCap() : reputationBuffCap;
        }

        public double upgradeRequirement(String fromTowerId, String upgradeId) {
            Double configured = upgradeRequirements.get(upgradeKey(fromTowerId, upgradeId));
            if (configured == null) {
                configured = upgradeRequirements.get(upgradeId);
            }
            return configured == null ? 0.0 : Math.max(0.0, configured);
        }

        public double buff(String towerId, String key) {
            Map<String, Double> values = buffs.get(towerId);
            return values == null ? 0.0 : values.getOrDefault(key, 0.0);
        }

        public double buffInterval(String towerId, String key) {
            Map<String, Double> values = buffs.get(towerId);
            if (values == null) {
                return 1.0;
            }
            return Math.max(1.0E-6, values.getOrDefault(key + "Interval", 1.0));
        }

        public VillagerAdvConfig withMissingDefaults(VillagerAdvConfig defaults) {
            if (defaults == null) {
                return this;
            }
            LinkedHashMap<String, Double> mergedUpgradeRequirements = new LinkedHashMap<>(upgradeRequirements);
            defaults.upgradeRequirements.forEach(mergedUpgradeRequirements::putIfAbsent);
            LinkedHashMap<String, Map<String, Double>> mergedBuffs = new LinkedHashMap<>(buffs);
            defaults.buffs.forEach((towerId, values) -> mergedBuffs.merge(
                    towerId,
                    values,
                    (configured, defaultValues) -> {
                        LinkedHashMap<String, Double> merged = new LinkedHashMap<>(configured);
                        defaultValues.forEach(merged::putIfAbsent);
                        return Collections.unmodifiableMap(merged);
                    }
            ));
            return new VillagerAdvConfig(
                    experienceMax == null ? defaults.experienceMax() : experienceMax,
                    experiencePerTower == null ? defaults.experiencePerTower() : experiencePerTower,
                    experiencePerTier == null ? defaults.experiencePerTier() : experiencePerTier,
                    reputationMax == null ? defaults.reputationMax() : reputationMax,
                    reputationGainRoundMultiplier == null ? defaults.reputationGainRoundMultiplier() : reputationGainRoundMultiplier,
                    reputationLossPerLeak == null ? defaults.reputationLossPerLeak() : reputationLossPerLeak,
                    effectDurationTicks == null ? defaults.effectDurationTicks() : effectDurationTicks,
                    experienceBuffCap == null ? defaults.experienceBuffCap() : experienceBuffCap,
                    reputationBuffCap == null ? defaults.reputationBuffCap() : reputationBuffCap,
                    mergedUpgradeRequirements,
                    mergedBuffs
            );
        }

        private static void putAdvUpgrade(Map<String, Double> upgrades, TowerType from, String upgradeId, double requirement) {
            upgrades.put(upgradeKey(from.id(), upgradeId), requirement);
        }

        private static void putAdvBuffs(Map<String, Map<String, Double>> buffs, TowerType tower, Map<String, Double> values) {
            buffs.put(tower.id(), values);
        }

        private static Map<String, Double> rangedBuffs() {
            LinkedHashMap<String, Double> values = reputationBuffs();
            putBuff(values, "rangedDamagePerExperience", 0.0015);
            putBuff(values, "rangedAttackSpeedPerExperience", 0.0015);
            return values;
        }

        private static Map<String, Double> golemBuffs() {
            LinkedHashMap<String, Double> values = reputationBuffs();
            putBuff(values, "golemHealthPerExperience", 0.0025);
            putBuff(values, "golemDamageReductionPerExperience", 0.001);
            return values;
        }

        private static Map<String, Double> allayHealBuffs() {
            LinkedHashMap<String, Double> values = reputationBuffs();
            putBuff(values, "allayHealAmountPerExperience", 0.003);
            putBuff(values, "allayIntervalReductionPerExperience", 0.0015);
            return values;
        }

        private static Map<String, Double> supportIntervalBuffs() {
            LinkedHashMap<String, Double> values = reputationBuffs();
            putBuff(values, "allayIntervalReductionPerExperience", 0.0015);
            return values;
        }

        private static Map<String, Double> catBuffs() {
            LinkedHashMap<String, Double> values = reputationBuffs();
            putBuff(values, "catDamagePerExperience", 0.0015);
            putBuff(values, "catAttackSpeedPerExperience", 0.0015);
            return values;
        }

        private static Map<String, Double> antiTankerCatBuffs() {
            LinkedHashMap<String, Double> values = new LinkedHashMap<>(catBuffs());
            putBuff(values, "catIncomeDamagePerExperience", 0.001);
            return values;
        }

        private static Map<String, Double> laneClearCatBuffs() {
            LinkedHashMap<String, Double> values = new LinkedHashMap<>(catBuffs());
            putBuff(values, "catWaveDamagePerExperience", 0.001);
            return values;
        }

        private static LinkedHashMap<String, Double> reputationBuffs() {
            LinkedHashMap<String, Double> values = new LinkedHashMap<>();
            putBuff(values, "reputationDamagePerPoint", 0.001);
            putBuff(values, "reputationAttackSpeedPerPoint", 0.001);
            putBuff(values, "reputationHealthPerPoint", 0.001);
            putBuff(values, "reputationDamageReductionPerPoint", 0.0005);
            return values;
        }

        private static void putBuff(Map<String, Double> values, String key, double amount) {
            values.put(key, amount);
            values.put(key + "Interval", 1.0);
        }

        private static Map<String, Double> copyDoubleMap(Map<String, Double> values) {
            if (values == null) {
                return Map.of();
            }
            LinkedHashMap<String, Double> copy = new LinkedHashMap<>();
            values.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    copy.put(key, Math.max(0.0, value));
                }
            });
            return Collections.unmodifiableMap(copy);
        }

        private static Map<String, Map<String, Double>> copyNestedDoubleMap(Map<String, Map<String, Double>> values) {
            if (values == null) {
                return Map.of();
            }
            LinkedHashMap<String, Map<String, Double>> copy = new LinkedHashMap<>();
            values.forEach((towerId, buffValues) -> {
                if (towerId != null && !towerId.isBlank() && buffValues != null) {
                    copy.put(towerId, copyDoubleMap(buffValues));
                }
            });
            return Collections.unmodifiableMap(copy);
        }
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
