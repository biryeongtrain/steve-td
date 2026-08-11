package kim.biryeong.semiontd.config;

import static kim.biryeong.semiontd.tower.end.EndConfig.Ability.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.ancientcity.AncientCityStates;
import kim.biryeong.semiontd.tower.ancientcity.AncientCityTowers;
import kim.biryeong.semiontd.tower.adversary.AdversaryBalance;
import kim.biryeong.semiontd.tower.adversary.AdversaryTowers;
import kim.biryeong.semiontd.tower.adversary.FoxForm;
import kim.biryeong.semiontd.tower.adversary.RivalKind;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import kim.biryeong.semiontd.tower.end.EndTowers;
import kim.biryeong.semiontd.tower.end.EndConfig.Ability;
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
        VillagerAdvConfig villagerAdv,
        int schemaVersion
) {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    public TowerBalanceConfig(Map<String, TowerStats> towers, Map<String, Long> upgradeCosts, Map<String, Map<String, Double>> abilities) {
        this(
                towers,
                upgradeCosts,
                abilities,
                IllusionCloneQueueConfig.defaultConfig(),
                VillagerAdvConfig.defaultConfig(),
                CURRENT_SCHEMA_VERSION
        );
    }

    public TowerBalanceConfig(
            Map<String, TowerStats> towers,
            Map<String, Long> upgradeCosts,
            Map<String, Map<String, Double>> abilities,
            IllusionCloneQueueConfig illusionCloneQueue
    ) {
        this(
                towers,
                upgradeCosts,
                abilities,
                illusionCloneQueue,
                VillagerAdvConfig.defaultConfig(),
                CURRENT_SCHEMA_VERSION
        );
    }

    public TowerBalanceConfig(
            Map<String, TowerStats> towers,
            Map<String, Long> upgradeCosts,
            Map<String, Map<String, Double>> abilities,
            IllusionCloneQueueConfig illusionCloneQueue,
            VillagerAdvConfig villagerAdv
    ) {
        this(towers, upgradeCosts, abilities, illusionCloneQueue, villagerAdv, CURRENT_SCHEMA_VERSION);
    }

    public TowerBalanceConfig {
        towers = towers == null ? Map.of() : copyTowerStats(towers);
        upgradeCosts = upgradeCosts == null ? Map.of() : copyUpgradeCosts(upgradeCosts);
        abilities = abilities == null ? Map.of() : copyAbilities(abilities);
        illusionCloneQueue = illusionCloneQueue == null ? IllusionCloneQueueConfig.defaultConfig() : illusionCloneQueue;
        villagerAdv = villagerAdv == null ? VillagerAdvConfig.defaultConfig() : villagerAdv;
        schemaVersion = schemaVersion <= 0 ? CURRENT_SCHEMA_VERSION : schemaVersion;
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
        addTower(towers, AnimalTowers.T4_PIG_LEADER_TOWER);
        addTower(towers, AnimalTowers.T1_WOLF_TOWER);
        addTower(towers, AnimalTowers.T2_WOLF_DPS_TOWER);
        addTower(towers, AnimalTowers.T3_WOLF_DPS_TOWER);
        addTower(towers, AnimalTowers.T4_WOLF_LEADER_TOWER);
        addTower(towers, AnimalTowers.T1_RABBIT_TOWER);
        addTower(towers, AnimalTowers.T2_RABBIT_TOWER);
        addTower(towers, AnimalTowers.T3_RABBIT_TOWER);
        addTower(towers, AnimalTowers.T4_RABBIT_LEADER_TOWER);
        addTower(towers, AnimalTowers.T1_FOX_TOWER);
        addTower(towers, AnimalTowers.T2_FOX_TOWER);
        addTower(towers, AnimalTowers.T3_FOX_TOWER);
        addTower(towers, AnimalTowers.T4_FOX_LEADER_TOWER);
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
        addAncientCityTowers(towers);
        addAdversaryTowers(towers);

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
        putUpgrade(upgradeCosts, AnimalTowers.T3_PIG_TOWER, "t4_pig_leader_tower", 350);
        putUpgrade(upgradeCosts, AnimalTowers.T1_WOLF_TOWER, "t2_wolf_dps_tower", 90);
        putUpgrade(upgradeCosts, AnimalTowers.T2_WOLF_DPS_TOWER, "t3_wolf_dps_tower", 180);
        putUpgrade(upgradeCosts, AnimalTowers.T3_WOLF_DPS_TOWER, "t4_wolf_leader_tower", 400);
        putUpgrade(upgradeCosts, AnimalTowers.T1_RABBIT_TOWER, "t2_rabbit_tower", 100);
        putUpgrade(upgradeCosts, AnimalTowers.T2_RABBIT_TOWER, "t3_rabbit_tower", 200);
        putUpgrade(upgradeCosts, AnimalTowers.T3_RABBIT_TOWER, "t4_rabbit_leader_tower", 450);
        putUpgrade(upgradeCosts, AnimalTowers.T1_FOX_TOWER, "t2_fox_tower", 150);
        putUpgrade(upgradeCosts, AnimalTowers.T2_FOX_TOWER, "t3_fox_tower", 225);
        putUpgrade(upgradeCosts, AnimalTowers.T3_FOX_TOWER, "t4_fox_leader_tower", 500);
        putUpgrade(upgradeCosts, LegionTowers.T1_BEE_TOWER, "t2_bee_tower", 160);
        putUpgrade(upgradeCosts, LegionTowers.T2_BEE_TOWER, "t3_bee_tower", 310);
        putUpgrade(upgradeCosts, WarlockTowers.BASE_WARLOCK_TOWER, "ranged_warlock_tower", 0);
        putUpgrade(upgradeCosts, WarlockTowers.BASE_WARLOCK_TOWER, "melee_warlock_tower", 0);
        putUpgrade(upgradeCosts, WarlockTowers.T1_SLAVE, "t2_slave", 85);
        putUpgrade(upgradeCosts, WarlockTowers.T2_SLAVE, "t3_slave", 135);
        putUpgrade(upgradeCosts, WarlockTowers.T1_RANGED_SLAVE, "t2_ranged_slave", 90);
        putUpgrade(upgradeCosts, WarlockTowers.T2_RANGED_SLAVE, "t3_ranged_slave", 140);
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
        putAncientCityUpgrades(upgradeCosts);
        putAdversaryUpgrades(upgradeCosts);

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
        putAbilities(abilities, AnimalTowers.T4_PIG_LEADER_TOWER.id(), Map.of(
                "maxStacks", 2.0,
                "healthPerStack", 90.0,
                "damagePerStack", 15.0,
                "damageReduction", 0.30,
                "splashRadius", 1.0,
                "splashDamageRatio", 0.50,
                "leaderAuraRadius", 4.0,
                "leaderMaxHealthBonus", 0.15,
                "leaderDamageReductionBonus", 0.05
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
        putAbilities(abilities, AnimalTowers.T4_WOLF_LEADER_TOWER.id(), Map.ofEntries(
                Map.entry("maxStacks", 4.0),
                Map.entry("damagePerStack", 10.0),
                Map.entry("intervalReductionPerStack", 1.25),
                Map.entry("splashRadius", 2.0),
                Map.entry("splashDamageRatio", 0.75),
                Map.entry("maxStackExtraIntervalReduction", 5.0),
                Map.entry("maxStackDamageBonus", 5.0),
                Map.entry("leaderAuraRadius", 6.0),
                Map.entry("leaderAttackIntervalReductionTicks", 1.0),
                Map.entry("leaderSplashDamageRatioBonus", 0.10)
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
        putAbilities(abilities, AnimalTowers.T4_RABBIT_LEADER_TOWER.id(), Map.of(
                "maxStacks", 4.0,
                "damagePerStack", 12.5,
                "maxStackExtraIntervalReduction", 5.0,
                "extraAttackDamageRatio", 2.0,
                "leaderAuraRadius", 7.0,
                "leaderDamageBonus", 0.08,
                "leaderRangeBonus", 1.0
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
        putAbilities(abilities, AnimalTowers.T4_FOX_LEADER_TOWER.id(), Map.ofEntries(
                Map.entry("maxStacks", 4.0),
                Map.entry("executeHealthThreshold", 0.40),
                Map.entry("executeThresholdPerStack", 0.04),
                Map.entry("maxExecuteHealthThreshold", 0.60),
                Map.entry("executeDamageBonusRatio", 0.75),
                Map.entry("executeDamageBonusPerStack", 0.30),
                Map.entry("killBonusDamage", 0.4),
                Map.entry("killBonusDamageCap", 40.0),
                Map.entry("leaderAuraRadius", 8.0),
                Map.entry("leaderExecuteThresholdBonus", 0.05),
                Map.entry("leaderExecuteThresholdCap", 0.65),
                Map.entry("leaderExecuteDamageBonus", 0.25)
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
        putAbilities(abilities, WarlockTowers.CONFIG_ID, Map.ofEntries(
                Map.entry("damageSoftCap", 180.0),
                Map.entry("sacrificeRadius", 25.0),
                Map.entry("minInterval", 5.0),
                Map.entry("speedCap", 15.0),
                Map.entry("awakeningAbsorptions", 20.0),
                Map.entry("awakeningThreshold", 0.40)
        ));
        putAbilities(abilities, WarlockTowers.BASE_WARLOCK_TOWER.id(), Map.of(
                "fatalHeal", 0.35,
                "sacrificeRadius", 6.0,
                "permanentHealth", 0.025,
                "permanentDamage", 0.05
        ));
        putAbilities(abilities, WarlockTowers.RANGED_WARLOCK_TOWER.id(), Map.ofEntries(
                Map.entry("threshold", 0.55),
                Map.entry("roundStat", 0.40),
                Map.entry("permanentHealth", 0.025),
                Map.entry("permanentDamage", 0.05),
                Map.entry("lifeEvery", 5.0),
                Map.entry("lifeStep", 0.005),
                Map.entry("lifeCap", 0.085),
                Map.entry("awakeningHeal", 400.0),
                Map.entry("awakeningRegeneration", 40.0),
                Map.entry("awakeningRegenerationTicks", 20.0),
                Map.entry("splashStep", 0.1),
                Map.entry("splashCap", 8.0),
                Map.entry("splashDamage", 0.50),
                Map.entry("defenseThreshold", 3.0),
                Map.entry("defense", 0.10),
                Map.entry("petHealth", 0.05),
                Map.entry("petHealthCap", 0.25),
                Map.entry("petDamage", 0.15),
                Map.entry("petDamageCap", 0.75)
        ));
        putAbilities(abilities, WarlockTowers.MELEE_WARLOCK_TOWER.id(), Map.ofEntries(
                Map.entry("threshold", 0.55),
                Map.entry("roundStat", 0.60),
                Map.entry("permanentHealth", 0.05),
                Map.entry("permanentDamage", 0.025),
                Map.entry("lifeStep", 0.01),
                Map.entry("lifeCap", 0.16),
                Map.entry("speedStep", 1.0),
                Map.entry("awakeningDamage", 75.0),
                Map.entry("awakeningMoveSpeed", 0.30),
                Map.entry("splashStep", 0.25),
                Map.entry("splashCap", 2.0),
                Map.entry("splashDamage", 0.75),
                Map.entry("defenseEvery", 5.0),
                Map.entry("defenseStep", 0.025),
                Map.entry("defenseCap", 0.25),
                Map.entry("petHealth", 0.15),
                Map.entry("petHealthCap", 0.75),
                Map.entry("petDamage", 0.05),
                Map.entry("petDamageCap", 0.25)
        ));
        putAbilities(abilities, WarlockTowers.T2_SLAVE.id(), Map.of(
                "deathEffectRadius", 20.0,
                "deathEffectDurationTicks", 72000.0,
                "towerDamageTakenBonus", 0.05
        ));
        putAbilities(abilities, WarlockTowers.T3_SLAVE.id(), Map.of(
                "deathEffectRadius", 20.0,
                "deathEffectDurationTicks", 72000.0,
                "towerDamageTakenBonus", 0.10
        ));
        putAbilities(abilities, WarlockTowers.T2_RANGED_SLAVE.id(), Map.of(
                "deathEffectRadius", 20.0,
                "deathEffectDurationTicks", 72000.0,
                "attackSpeedReduction", 0.05
        ));
        putAbilities(abilities, WarlockTowers.T3_RANGED_SLAVE.id(), Map.of(
                "deathEffectRadius", 20.0,
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
        putAncientCityAbilities(abilities);
        putAdversaryAbilities(abilities);

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
        return roundedNonNegativeInt(ability(towerId, key, fallback), fallback);
    }

    public int abilityInt(String towerId, String key, int fallback) {
        return roundedNonNegativeInt(ability(towerId, key, fallback), fallback);
    }

    public void validateForRuntime() {
        if (schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported tower balance schema version: " + schemaVersion
            );
        }
        towers.forEach(TowerBalanceConfig::validateTowerStats);
        upgradeCosts.forEach((key, cost) -> {
            if (cost < 0L) {
                throw new IllegalArgumentException(
                        "Tower upgrade cost must be non-negative: " + key
                );
            }
        });

        validateDamageScaling(WarlockTowers.CONFIG_ID);
        validateAdversaryBalance();

        Map<String, Double> end = abilities.get(EndTowers.CONFIG_ID);
        if (end == null) {
            return;
        }

        end.forEach((key, value) -> {
            if (value == null || !Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException("End balance ability must be finite and non-negative: " + key);
            }
        });

        requirePositive(end,
                DRAGON_EVOLUTION,
                TRANSFER_TICKS,
                TRANSFER_ATTACK_SPEED_STACKS,
                ATTACK_SPEED_STACKS,
                ATTACK_SPEED_MINIMUM_TICKS,
                ATTACK_RANGE_STACKS,
                SPLASH_1,
                SPLASH_2,
                SPLASH_3,
                SPLASH_4,
                SPLASH_5,
                LIFE_STEAL_STACKS,
                REGENERATION_STACKS,
                DAMAGE_REDUCTION_STACKS,
                PHANTOM_SCALE_HEALTH,
                HEALTH_THRESHOLD,
                HEALTH_SCALE,
                DAMAGE_THRESHOLD,
                DAMAGE_SCALE
        );
        requireRatio(end,
                ROUND_HEALTH_RATIO,
                ROUND_DAMAGE_RATIO,
                PERMANENT_HEALTH_RATIO,
                PERMANENT_DAMAGE_RATIO,
                TRANSFER_HEAL_RATIO,
                SPLASH_DAMAGE_RATIO,
                LIFE_STEAL_STEP,
                LIFE_STEAL_CAP,
                DAMAGE_REDUCTION_STEP,
                DAMAGE_REDUCTION_CAP
        );
        requirePositive(end, PHANTOM_SCALE_BASE, PHANTOM_SCALE_CAP);
        validateAtLeast(end, PHANTOM_SCALE_CAP, PHANTOM_SCALE_BASE);
        validateStrictlyIncreasing(end,
                SPLASH_1,
                SPLASH_2,
                SPLASH_3,
                SPLASH_4,
                SPLASH_5
        );
        requireIntegralIntRange(end,
                TRANSFER_TICKS,
                TRANSFER_ATTACK_SPEED_STACKS,
                TRANSFER_ATTACK_SPEED_STEP,
                ATTACK_SPEED_STACKS,
                ATTACK_SPEED_STEP,
                ATTACK_SPEED_CAP,
                ATTACK_SPEED_MINIMUM_TICKS,
                ATTACK_RANGE_STACKS,
                SPLASH_1,
                SPLASH_2,
                SPLASH_3,
                SPLASH_4,
                SPLASH_5,
                LIFE_STEAL_STACKS,
                REGENERATION_STACKS,
                DAMAGE_REDUCTION_STACKS
        );
        validateMinimumAttackInterval(end);
        validateTowerRatio(EndTowers.T1_SHULKER_TOWER.id(), "damageReduction");
        validateTowerRatio(EndTowers.T2_SHULKER_TOWER.id(), "damageReduction");
        validateTowerRatio(EndTowers.T3_SHULKER_TOWER.id(), "damageReduction");
    }

    private void validateAdversaryBalance() {
        Map<String, Double> global = abilities.get(AdversaryBalance.GLOBAL_CONFIG_ID);
        if (global != null) {
            validateAdversaryValues(AdversaryBalance.GLOBAL_CONFIG_ID, global);
            requireAdversaryPositive(global,
                    "rivalArmorRoundInterval",
                    "baseSplashRadius",
                    "goldenExtraAttackEvery",
                    "shieldCounterDamage",
                    "shieldCounterCooldownTicks",
                    "teamEffectScanIntervalTicks",
                    "teamEffectDurationTicks",
                    "fireworkWaveDamageMultiplier",
                    "fireworkIncomeDamageMultiplier",
                    "fireworkMaxTargets",
                    "bigGameWaveDamageMultiplier",
                    "bigGameIncomeDamageMultiplier",
                    "maceFocusTicks",
                    "maceSweepRadius",
                    "sculkDelayTicks",
                    "sculkRadius",
                    "sculkMaxTargets"
            );
            requireAdversaryRatio(global,
                    "rivalRoundHealthGrowth",
                    "rivalRoundDamageGrowth",
                    "baseSplashDamageRatio",
                    "breezeExtraTargetDamageRatio",
                    "goldenExtraDamageRatio",
                    "bellTeamDamageBonus",
                    "beaconTeamDamageBonus",
                    "beaconTeamAttackSpeedBonus",
                    "beaconTeamMaxHealthBonus",
                    "ominousMonsterDamageReduction",
                    "ominousMonsterAttackSpeedReduction",
                    "ominousMonsterTowerDamageTakenBonus",
                    "fireworkSecondary2Ratio",
                    "fireworkSecondary3Ratio",
                    "fireworkSecondary4Ratio",
                    "fireworkSecondary5Ratio",
                    "echoBonusPerHit",
                    "maceBreakHealthRatio",
                    "maceSweepDamageRatio",
                    "sculkSelfDamageRatio",
                    "sculkSelfDamageFloorRatio"
            );
            requireAdversaryIntegral(global,
                    "rivalArmorRoundInterval",
                    "baseSplashExtraTargets",
                    "breezeExtraTargets",
                    "goldenExtraAttackEvery",
                    "shieldCounterCooldownTicks",
                    "teamEffectScanIntervalTicks",
                    "teamEffectDurationTicks",
                    "fireworkMaxTargets",
                    "echoMaxBonusStacks",
                    "maceFocusTicks",
                    "maceSweepExtraTargets",
                    "sculkDelayTicks",
                    "sculkMaxTargets"
            );
            validateAdversaryNonIncreasing(global,
                    "fireworkSecondary2Ratio",
                    "fireworkSecondary3Ratio",
                    "fireworkSecondary4Ratio",
                    "fireworkSecondary5Ratio"
            );
            validateAdversaryNonDecreasing(global, "bigGameStreak2", "bigGameStreak3");
            validateAdversaryNonDecreasing(global, "maceStreak2", "maceStreak3", "maceStreak4", "maceStreak5");
        }

        for (FoxForm form : FoxForm.values()) {
            String configId = AdversaryBalance.formConfigId(form);
            Map<String, Double> values = abilities.get(configId);
            if (values == null) {
                continue;
            }
            validateAdversaryValues(configId, values);
            requireAdversaryPositive(values, "maxHealth", "range", "damage", "attackIntervalTicks");
            requireAdversaryRatio(values, "damageReduction");
            requireAdversaryIntegral(values, "attackIntervalTicks");
            for (RivalKind kind : RivalKind.values()) {
                String requirementKey = AdversaryBalance.requirementKey(kind);
                requireAdversaryPositive(values, requirementKey);
                requireAdversaryIntegral(values, requirementKey);
            }
        }

        for (RivalKind kind : RivalKind.values()) {
            validateAdversaryRivalAbilities(kind, false);
            validateAdversaryRivalAbilities(kind, true);
        }
    }

    private void validateAdversaryRivalAbilities(RivalKind kind, boolean enhanced) {
        String configId = AdversaryBalance.rivalTowerId(kind, enhanced);
        Map<String, Double> values = abilities.get(configId);
        if (values == null) {
            return;
        }
        validateAdversaryValues(configId, values);
        requireAdversaryPositive(values, "scorePerKill");
        requireAdversaryIntegral(values, "scorePerKill");
    }

    private static void validateAdversaryValues(String configId, Map<String, Double> values) {
        values.forEach((key, value) -> {
            if (value == null || !Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException(
                        "Adversary balance value must be finite and non-negative: " + configId + "." + key
                );
            }
        });
    }

    private static void requireAdversaryPositive(Map<String, Double> values, String... keys) {
        for (String key : keys) {
            Double value = values.get(key);
            if (value != null && value <= 0.0) {
                throw new IllegalArgumentException("Adversary balance value must be positive: " + key);
            }
        }
    }

    private static void requireAdversaryRatio(Map<String, Double> values, String... keys) {
        for (String key : keys) {
            Double value = values.get(key);
            if (value != null && value > 1.0) {
                throw new IllegalArgumentException("Adversary balance ratio must be between 0 and 1: " + key);
            }
        }
    }

    private static void requireAdversaryIntegral(Map<String, Double> values, String... keys) {
        for (String key : keys) {
            Double value = values.get(key);
            if (value != null && (value > Integer.MAX_VALUE || value != Math.rint(value))) {
                throw new IllegalArgumentException("Adversary balance integer must not be fractional or oversized: " + key);
            }
        }
    }

    private static void validateAdversaryNonDecreasing(Map<String, Double> values, String... keys) {
        Double previous = 1.0;
        for (String key : keys) {
            Double value = values.get(key);
            if (value == null) {
                return;
            }
            if (value < previous) {
                throw new IllegalArgumentException("Adversary balance streak multipliers must be non-decreasing.");
            }
            previous = value;
        }
    }

    private static void validateAdversaryNonIncreasing(Map<String, Double> values, String... keys) {
        Double previous = 1.0;
        for (String key : keys) {
            Double value = values.get(key);
            if (value == null) {
                return;
            }
            if (value > previous) {
                throw new IllegalArgumentException("Adversary secondary damage ratios must be non-increasing.");
            }
            previous = value;
        }
    }

    private static void requirePositive(Map<String, Double> values, String... keys) {
        for (String key : keys) {
            Double value = values.get(key);
            if (value != null && value <= 0.0) {
                throw new IllegalArgumentException("End balance ability must be positive: " + key);
            }
        }
    }

    private void validateDamageScaling(String configId) {
        Map<String, Double> values = abilities.get(configId);
        if (values == null) {
            return;
        }
        Double softCap = values.get("damageSoftCap");
        if (softCap != null && (!Double.isFinite(softCap) || softCap <= 0.0)) {
            throw new IllegalArgumentException(configId + ".damageSoftCap must be finite and positive.");
        }
    }

    private static void requirePositive(Map<String, Double> values, Ability... abilities) {
        requirePositive(values, abilityKeys(abilities));
    }

    private static void requireRatio(Map<String, Double> values, String... keys) {
        for (String key : keys) {
            Double value = values.get(key);
            if (value != null && value > 1.0) {
                throw new IllegalArgumentException("End balance ratio must be between 0 and 1: " + key);
            }
        }
    }

    private static void requireRatio(Map<String, Double> values, Ability... abilities) {
        requireRatio(values, abilityKeys(abilities));
    }

    private static void validateStrictlyIncreasing(Map<String, Double> values, String... keys) {
        Double previous = null;
        for (String key : keys) {
            Double value = values.get(key);
            if (value == null) {
                return;
            }
            if (previous != null && value <= previous) {
                throw new IllegalArgumentException("End balance thresholds must be strictly increasing.");
            }
            previous = value;
        }
    }

    private static void validateStrictlyIncreasing(Map<String, Double> values, Ability... abilities) {
        validateStrictlyIncreasing(values, abilityKeys(abilities));
    }

    private static void validateAtLeast(
            Map<String, Double> values,
            String valueKey,
            String minimumKey
    ) {
        Double value = values.get(valueKey);
        Double minimum = values.get(minimumKey);
        if (value != null && minimum != null && value < minimum) {
            throw new IllegalArgumentException(
                    "End balance " + valueKey + " must be at least " + minimumKey
            );
        }
    }

    private static void validateAtLeast(Map<String, Double> values, Ability value, Ability minimum) {
        validateAtLeast(values, value.key(), minimum.key());
    }

    private static void requireIntegralIntRange(Map<String, Double> values, String... keys) {
        for (String key : keys) {
            Double value = values.get(key);
            if (value == null) {
                continue;
            }
            if (value > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("End balance integer is too large: " + key);
            }
            if (value != Math.rint(value)) {
                throw new IllegalArgumentException("End balance integer must not be fractional: " + key);
            }
        }
    }

    private static void requireIntegralIntRange(Map<String, Double> values, Ability... abilities) {
        requireIntegralIntRange(values, abilityKeys(abilities));
    }

    private static String[] abilityKeys(Ability[] abilities) {
        String[] keys = new String[abilities.length];
        for (int index = 0; index < abilities.length; index++) {keys[index] = abilities[index].key();}
        return keys;
    }

    private void validateMinimumAttackInterval(Map<String, Double> end) {
        Double minimum = end.get(ATTACK_SPEED_MINIMUM_TICKS.key());
        TowerStats base = towers.get(EndTowers.BASE_END_TOWER.id());
        if (minimum != null
                && base != null
                && base.attackIntervalTicks() != null
                && minimum > base.attackIntervalTicks()) {
            throw new IllegalArgumentException(
                    "End attackSpeedMinimumTicks cannot exceed the base End tower attack interval"
            );
        }
    }

    private static void validateTowerStats(String towerId, TowerStats stats) {
        if (stats == null) {
            return;
        }
        if (stats.mineralCost != null && stats.mineralCost < 0) {
            throw new IllegalArgumentException("Tower mineral cost must be non-negative: " + towerId);
        }
        validateFiniteAtLeast(towerId, "maxHealth", stats.maxHealth, 1.0);
        validateFiniteAtLeast(towerId, "range", stats.range, 0.0);
        validateFiniteAtLeast(towerId, "damage", stats.damage, 0.0);
        if (stats.attackIntervalTicks != null && stats.attackIntervalTicks < 1) {
            throw new IllegalArgumentException("Tower attack interval must be positive: " + towerId);
        }
    }

    private static void validateFiniteAtLeast(
            String towerId,
            String field,
            Double value,
            double minimum
    ) {
        if (value != null && (!Double.isFinite(value) || value < minimum)) {
            throw new IllegalArgumentException(
                    "Tower " + field + " must be finite and at least " + minimum + ": " + towerId
            );
        }
    }

    private static int roundedNonNegativeInt(double value, int fallback) {
        double resolved = Double.isFinite(value) ? value : fallback;
        if (!Double.isFinite(resolved) || resolved <= 0.0) {
            return 0;
        }
        if (resolved >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, (int) Math.round(resolved));
    }

    private void validateTowerRatio(String towerId, String key) {
        Map<String, Double> values = abilities.get(towerId);
        if (values == null) {
            return;
        }
        Double value = values.get(key);
        if (value != null && (!Double.isFinite(value) || value < 0.0 || value > 1.0)) {
            throw new IllegalArgumentException("Tower balance ratio must be between 0 and 1: " + towerId + "." + key);
        }
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

        return new TowerBalanceConfig(
                mergedTowers,
                mergedUpgradeCosts,
                mergedAbilities,
                mergedIllusionCloneQueue,
                mergedVillagerAdv,
                schemaVersion
        );
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

    private static void addAncientCityTowers(Map<String, TowerStats> towers) {
        AncientCityTowers.all().forEach(type -> addTower(towers, type));
    }

    private static void addAdversaryTowers(Map<String, TowerStats> towers) {
        AdversaryTowers.all().forEach(type -> addTower(towers, type));
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
        putUpgrade(upgrades, EndTowers.T1_ENDERMITE_TOWER, EndTowers.T2_ENDERMAN_TOWER.id(), 100);
        putUpgrade(upgrades, EndTowers.T2_ENDERMAN_TOWER, EndTowers.T3_END_CRYSTAL_TOWER.id(), 150);
        putUpgrade(upgrades, EndTowers.T1_SHULKER_TOWER, EndTowers.T2_SHULKER_TOWER.id(), 100);
        putUpgrade(upgrades, EndTowers.T2_SHULKER_TOWER, EndTowers.T3_SHULKER_TOWER.id(), 150);
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

    private static void putAncientCityUpgrades(Map<String, Long> upgrades) {
        putUpgrade(upgrades, AncientCityTowers.CATALYST_T1, AncientCityTowers.CATALYST_T2.id(), 110);
        putUpgrade(upgrades, AncientCityTowers.CATALYST_T2, AncientCityTowers.CATALYST_T3.id(), 230);
        putUpgrade(upgrades, AncientCityTowers.SENSOR_T1, AncientCityTowers.SENSOR_T2.id(), 90);
        putUpgrade(upgrades, AncientCityTowers.SENSOR_T2, AncientCityTowers.SENSOR_T3.id(), 190);
        putUpgrade(upgrades, AncientCityTowers.SHRIEKER_T1, AncientCityTowers.SHRIEKER_T2.id(), 110);
        putUpgrade(upgrades, AncientCityTowers.SHRIEKER_T2, AncientCityTowers.SHRIEKER_T3.id(), 220);
        putUpgrade(upgrades, AncientCityTowers.WARDEN_T1, AncientCityTowers.WARDEN_T2.id(), 160);
        putUpgrade(upgrades, AncientCityTowers.WARDEN_T2, AncientCityTowers.WARDEN_T3.id(), 300);
    }

    private static void putAdversaryUpgrades(Map<String, Long> upgrades) {
        for (RivalKind kind : RivalKind.values()) {
            TowerType base = AdversaryTowers.baseRival(kind);
            TowerType enhanced = AdversaryTowers.enhancedRival(kind);
            putUpgrade(upgrades, base, enhanced.id(), AdversaryBalance.defaultRivalBaseCost(kind));
        }
    }

    private static void putAdversaryAbilities(Map<String, Map<String, Double>> abilities) {
        putAbilities(abilities, AdversaryBalance.GLOBAL_CONFIG_ID, Map.ofEntries(
                Map.entry("rivalRoundHealthGrowth", AdversaryBalance.RIVAL_ROUND_HEALTH_GROWTH),
                Map.entry("rivalRoundDamageGrowth", AdversaryBalance.RIVAL_ROUND_DAMAGE_GROWTH),
                Map.entry("rivalArmorRoundInterval", (double) AdversaryBalance.RIVAL_ARMOR_ROUND_INTERVAL),
                Map.entry("baseSplashRadius", AdversaryBalance.BASE_SPLASH_RADIUS),
                Map.entry("baseSplashExtraTargets", (double) AdversaryBalance.BASE_SPLASH_EXTRA_TARGETS),
                Map.entry("baseSplashDamageRatio", AdversaryBalance.BASE_SPLASH_DAMAGE_RATIO),
                Map.entry("breezeExtraTargets", (double) AdversaryBalance.BREEZE_EXTRA_TARGETS),
                Map.entry("breezeExtraTargetDamageRatio", AdversaryBalance.BREEZE_EXTRA_TARGET_DAMAGE_RATIO),
                Map.entry("goldenExtraAttackEvery", (double) AdversaryBalance.GOLDEN_FANG_EXTRA_ATTACK_EVERY),
                Map.entry("goldenExtraDamageRatio", AdversaryBalance.GOLDEN_FANG_EXTRA_DAMAGE_RATIO),
                Map.entry("shieldCounterDamage", AdversaryBalance.SHIELD_COUNTER_DAMAGE),
                Map.entry("shieldCounterCooldownTicks", (double) AdversaryBalance.SHIELD_COUNTER_COOLDOWN_TICKS),
                Map.entry("bellTeamDamageBonus", AdversaryBalance.BELL_TEAM_DAMAGE_BONUS),
                Map.entry("beaconTeamDamageBonus", AdversaryBalance.BEACON_TEAM_DAMAGE_BONUS),
                Map.entry("beaconTeamAttackSpeedBonus", AdversaryBalance.BEACON_TEAM_ATTACK_SPEED_BONUS),
                Map.entry("beaconTeamMaxHealthBonus", AdversaryBalance.BEACON_TEAM_MAX_HEALTH_BONUS),
                Map.entry("ominousMonsterDamageReduction", AdversaryBalance.OMINOUS_MONSTER_DAMAGE_REDUCTION),
                Map.entry("ominousMonsterAttackSpeedReduction", AdversaryBalance.OMINOUS_MONSTER_ATTACK_SPEED_REDUCTION),
                Map.entry("ominousMonsterTowerDamageTakenBonus", AdversaryBalance.OMINOUS_MONSTER_TOWER_DAMAGE_TAKEN_BONUS),
                Map.entry("teamEffectScanIntervalTicks", (double) AdversaryBalance.TEAM_EFFECT_SCAN_INTERVAL_TICKS),
                Map.entry("teamEffectDurationTicks", (double) AdversaryBalance.TEAM_EFFECT_DURATION_TICKS),
                Map.entry("fireworkWaveDamageMultiplier", AdversaryBalance.FIREWORK_WAVE_DAMAGE_MULTIPLIER),
                Map.entry("fireworkIncomeDamageMultiplier", AdversaryBalance.FIREWORK_INCOME_DAMAGE_MULTIPLIER),
                Map.entry("fireworkMaxTargets", (double) AdversaryBalance.FIREWORK_MAX_TARGETS),
                Map.entry("fireworkSecondary2Ratio", AdversaryBalance.FIREWORK_TARGET_DAMAGE_RATIOS[1]),
                Map.entry("fireworkSecondary3Ratio", AdversaryBalance.FIREWORK_TARGET_DAMAGE_RATIOS[2]),
                Map.entry("fireworkSecondary4Ratio", AdversaryBalance.FIREWORK_TARGET_DAMAGE_RATIOS[3]),
                Map.entry("fireworkSecondary5Ratio", AdversaryBalance.FIREWORK_TARGET_DAMAGE_RATIOS[4]),
                Map.entry("bigGameWaveDamageMultiplier", AdversaryBalance.BIG_GAME_WAVE_DAMAGE_MULTIPLIER),
                Map.entry("bigGameIncomeDamageMultiplier", AdversaryBalance.BIG_GAME_INCOME_DAMAGE_MULTIPLIER),
                Map.entry("bigGameStreak2", AdversaryBalance.BIG_GAME_STREAK_MULTIPLIERS[1]),
                Map.entry("bigGameStreak3", AdversaryBalance.BIG_GAME_STREAK_MULTIPLIERS[2]),
                Map.entry("echoBonusPerHit", AdversaryBalance.ECHO_STREAK_DAMAGE_BONUS_PER_HIT),
                Map.entry("echoMaxBonusStacks", (double) AdversaryBalance.ECHO_MAX_STREAK_BONUS_STACKS),
                Map.entry("maceFocusTicks", (double) AdversaryBalance.MACE_FOCUS_TICKS),
                Map.entry("maceBreakHealthRatio", AdversaryBalance.MACE_FOCUS_BREAK_MAX_HEALTH_RATIO),
                Map.entry("maceStreak2", AdversaryBalance.MACE_STREAK_MULTIPLIERS[1]),
                Map.entry("maceStreak3", AdversaryBalance.MACE_STREAK_MULTIPLIERS[2]),
                Map.entry("maceStreak4", AdversaryBalance.MACE_STREAK_MULTIPLIERS[3]),
                Map.entry("maceStreak5", AdversaryBalance.MACE_STREAK_MULTIPLIERS[4]),
                Map.entry("maceSweepRadius", AdversaryBalance.MACE_SWEEP_RADIUS),
                Map.entry("maceSweepExtraTargets", (double) AdversaryBalance.MACE_SWEEP_EXTRA_TARGETS),
                Map.entry("maceSweepDamageRatio", AdversaryBalance.MACE_SWEEP_DAMAGE_RATIO),
                Map.entry("sculkDelayTicks", (double) AdversaryBalance.SCULK_DETONATION_DELAY_TICKS),
                Map.entry("sculkRadius", AdversaryBalance.SCULK_DETONATION_RADIUS),
                Map.entry("sculkMaxTargets", (double) AdversaryBalance.SCULK_MAX_TARGETS),
                Map.entry("sculkSelfDamageRatio", AdversaryBalance.SCULK_SELF_DAMAGE_MAX_HEALTH_RATIO),
                Map.entry("sculkSelfDamageFloorRatio", AdversaryBalance.SCULK_SELF_DAMAGE_HEALTH_FLOOR_RATIO)
        ));

        putAdversaryFormAbilities(abilities, FoxForm.BASE);
        putAdversaryFormAbilities(abilities, FoxForm.BREEZE, RivalKind.BREEZE, 12);
        putAdversaryFormAbilities(abilities, FoxForm.GOLDEN_FANG, RivalKind.BREEZE, 50);
        putAdversaryFormAbilities(abilities, FoxForm.SHIELD_BEARER, RivalKind.BREEZE, 30, RivalKind.POLAR_BEAR, 20);
        putAdversaryFormAbilities(abilities, FoxForm.BELL_KEEPER, RivalKind.PHANTOM, 14);
        putAdversaryFormAbilities(abilities, FoxForm.BEACON_KEEPER, RivalKind.PHANTOM, 50, RivalKind.POLAR_BEAR, 25);
        putAdversaryFormAbilities(abilities, FoxForm.OMINOUS_HEXER, RivalKind.PHANTOM, 50, RivalKind.CREEPER, 30);
        putAdversaryFormAbilities(abilities, FoxForm.TRACKER, RivalKind.CREEPER, 16);
        putAdversaryFormAbilities(abilities, FoxForm.FIREWORK_PIERCER, RivalKind.CREEPER, 60, RivalKind.BREEZE, 30);
        putAdversaryFormAbilities(abilities, FoxForm.BIG_GAME_TRACKER, RivalKind.CREEPER, 60, RivalKind.POLAR_BEAR, 30);
        putAdversaryFormAbilities(abilities, FoxForm.ECHO_FOX, RivalKind.POLAR_BEAR, 18);
        putAdversaryFormAbilities(abilities, FoxForm.MACE_EXECUTIONER, RivalKind.POLAR_BEAR, 80, RivalKind.BREEZE, 40);
        putAdversaryFormAbilities(
                abilities,
                FoxForm.SCULK_CORE,
                RivalKind.POLAR_BEAR,
                100,
                RivalKind.PHANTOM,
                50,
                RivalKind.CREEPER,
                40
        );

        for (RivalKind kind : RivalKind.values()) {
            putAdversaryRivalAbilities(abilities, kind, false);
            putAdversaryRivalAbilities(abilities, kind, true);
        }
    }

    private static void putAdversaryFormAbilities(
            Map<String, Map<String, Double>> abilities,
            FoxForm form,
            Object... requirements
    ) {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>();
        values.put("maxHealth", AdversaryBalance.defaultFormValue(form, "maxHealth"));
        values.put("range", AdversaryBalance.defaultFormValue(form, "range"));
        values.put("damage", AdversaryBalance.defaultFormValue(form, "damage"));
        values.put("attackIntervalTicks", AdversaryBalance.defaultFormValue(form, "attackIntervalTicks"));
        values.put("damageReduction", AdversaryBalance.defaultFormValue(form, "damageReduction"));
        for (int index = 0; index + 1 < requirements.length; index += 2) {
            RivalKind kind = (RivalKind) requirements[index];
            Number score = (Number) requirements[index + 1];
            values.put(AdversaryBalance.requirementKey(kind), score.doubleValue());
        }
        putAbilities(abilities, AdversaryBalance.formConfigId(form), values);
    }

    private static void putAdversaryRivalAbilities(
            Map<String, Map<String, Double>> abilities,
            RivalKind kind,
            boolean enhanced
    ) {
        putAbilities(abilities, AdversaryBalance.rivalTowerId(kind, enhanced), Map.of(
                "baseArmor", AdversaryBalance.defaultRivalBaseArmor(kind)
                        + (enhanced ? AdversaryBalance.ENHANCED_RIVAL_ARMOR_BONUS : 0.0),
                "scorePerKill", (double) (enhanced
                        ? AdversaryBalance.ENHANCED_RIVAL_SCORE_PER_KILL
                        : AdversaryBalance.BASE_RIVAL_SCORE_PER_KILL)
        ));
    }

    private static void putAncientCityAbilities(Map<String, Map<String, Double>> abilities) {
        putAbilities(abilities, AncientCityStates.CONFIG_ID, Map.of(
                "maxSculk", 256.0,
                "resonanceFullAt", 224.0,
                "initialSculk", 9.0,
                "waveStartSpread", 4.0,
                "deathSpreadCapPerRound", 6.0,
                "resonanceDamageCap", 2.25,
                "maxCombinedDamageBonus", 2.55,
                "finalDefenseSeedCount", 5.0,
                "incomeMagicDamageMultiplier", 1.75
        ));
        putCatalystAbilities(abilities, AncientCityTowers.CATALYST_T1, 6, 60, 2.0, 0.10);
        putCatalystAbilities(abilities, AncientCityTowers.CATALYST_T2, 9, 50, 2.5, 0.15);
        putCatalystAbilities(abilities, AncientCityTowers.CATALYST_T3, 30, 40, 3.0, 0.20);
        putSensorAbilities(abilities, AncientCityTowers.SENSOR_T1, 5, 40, 0.10, 60);
        putSensorAbilities(abilities, AncientCityTowers.SENSOR_T2, 8, 36, 0.20, 80);
        putSensorAbilities(abilities, AncientCityTowers.SENSOR_T3, 26, 30, 0.30, 100);
        putShriekerAbilities(abilities, AncientCityTowers.SHRIEKER_T1, 4, 60, 2.0, 0.10, 40);
        putShriekerAbilities(abilities, AncientCityTowers.SHRIEKER_T2, 8, 50, 2.5, 0.15, 50);
        putShriekerAbilities(abilities, AncientCityTowers.SHRIEKER_T3, 34, 40, 3.0, 0.20, 60);
        putWardenAbilities(abilities, AncientCityTowers.WARDEN_T1, 10, 60, 2);
        putWardenAbilities(abilities, AncientCityTowers.WARDEN_T2, 16, 50, 3);
        putWardenAbilities(abilities, AncientCityTowers.WARDEN_T3, 55, 40, 4);
    }

    private static void putCatalystAbilities(
            Map<String, Map<String, Double>> abilities,
            TowerType type,
            double damage,
            double cooldown,
            double radius,
            double damageReduction
    ) {
        putAbilities(abilities, type.id(), Map.of(
                "magicDamage", damage,
                "retaliationCooldownTicks", cooldown,
                "retaliationRadius", radius,
                "sculkDamageReduction", damageReduction
        ));
    }

    private static void putSensorAbilities(
            Map<String, Map<String, Double>> abilities,
            TowerType type,
            double damage,
            double cooldown,
            double markBonus,
            double markDuration
    ) {
        putAbilities(abilities, type.id(), Map.of(
                "magicDamage", damage,
                "magicCooldownTicks", cooldown,
                "markDamageBonus", markBonus,
                "markDurationTicks", markDuration
        ));
    }

    private static void putShriekerAbilities(
            Map<String, Map<String, Double>> abilities,
            TowerType type,
            double damage,
            double cooldown,
            double radius,
            double slow,
            double slowDuration
    ) {
        putAbilities(abilities, type.id(), Map.of(
                "magicDamage", damage,
                "magicCooldownTicks", cooldown,
                "magicRadius", radius,
                "slowMagnitude", slow,
                "slowDurationTicks", slowDuration
        ));
    }

    private static void putWardenAbilities(
            Map<String, Map<String, Double>> abilities,
            TowerType type,
            double damage,
            double cooldown,
            double targets
    ) {
        putAbilities(abilities, type.id(), Map.of(
                "magicDamage", damage,
                "magicCooldownTicks", cooldown,
                "targetCount", targets,
                "sculkExtraTargets", 1.0,
                "secondaryDamageRatio", 0.25
        ));
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
        putAbilities(abilities, EndTowers.CONFIG_ID, endAbilities());
    }

    private static Map<String, Double> endAbilities() {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>();
        values.put(DRAGON_EVOLUTION.key(), 2000.0);
        values.put(PHANTOM_SCALE_HEALTH.key(), 100.0);
        values.put(PHANTOM_SCALE_STEP.key(), 0.2);
        values.put(PHANTOM_SCALE_BASE.key(), 1.0);
        values.put(PHANTOM_SCALE_CAP.key(), 5.0);
        values.put(TRANSFER_TICKS.key(), 200.0);
        values.put(TRANSFER_HEAL.key(), 30.0);
        values.put(TRANSFER_HEAL_RATIO.key(), 0.05);
        values.put(ROUND_HEALTH_RATIO.key(), 0.50);
        values.put(PERMANENT_HEALTH_RATIO.key(), 0.04);
        values.put(HEALTH_THRESHOLD.key(), 3000.0);
        values.put(HEALTH_SCALE.key(), 500.0);
        values.put(ROUND_DAMAGE_RATIO.key(), 0.66);
        values.put(PERMANENT_DAMAGE_RATIO.key(), 0.04);
        values.put(DAMAGE_THRESHOLD.key(), 150.0);
        values.put(DAMAGE_SCALE.key(), 25.0);
        values.put(LIFE_STEAL_STACKS.key(), 30.0);
        values.put(LIFE_STEAL_STEP.key(), 0.01);
        values.put(LIFE_STEAL_CAP.key(), 0.10);
        values.put(DAMAGE_REDUCTION_STACKS.key(), 15.0);
        values.put(DAMAGE_REDUCTION_STEP.key(), 0.01);
        values.put(DAMAGE_REDUCTION_CAP.key(), 0.20);
        values.put(REGENERATION_STACKS.key(), 10.0);
        values.put(REGENERATION_STEP.key(), 1.0);
        values.put(REGENERATION_CAP.key(), 30.0);
        values.put(SPLASH_1.key(), 10.0);
        values.put(SPLASH_2.key(), 35.0);
        values.put(SPLASH_3.key(), 75.0);
        values.put(SPLASH_4.key(), 150.0);
        values.put(SPLASH_5.key(), 300.0);
        values.put(SPLASH_STEP.key(), 1.0);
        values.put(SPLASH_CAP.key(), 5.0);
        values.put(SPLASH_DAMAGE_RATIO.key(), 0.66);
        values.put(ATTACK_SPEED_STACKS.key(), 30.0);
        values.put(ATTACK_SPEED_STEP.key(), 1.0);
        values.put(ATTACK_SPEED_CAP.key(), 10.0);
        values.put(ATTACK_SPEED_MINIMUM_TICKS.key(), 5.0);
        values.put(TRANSFER_ATTACK_SPEED_STACKS.key(), 1.0);
        values.put(TRANSFER_ATTACK_SPEED_STEP.key(), 1.0);
        values.put(ATTACK_RANGE_STACKS.key(), 50.0);
        values.put(ATTACK_RANGE_STEP.key(), 0.5);
        values.put(ATTACK_RANGE_CAP.key(), 3.0);
        values.put(DRAGON_FINAL_DAMAGE.key(), 0.10);
        values.put(DRAGON_RANGE_BONUS.key(), 2.0);
        return Collections.unmodifiableMap(values);
    }

    private static void putOceanAbilities(Map<String, Map<String, Double>> abilities) {
        putAbilities(abilities, OceanTower.CONFIG_ID, Map.ofEntries(
                Map.entry("initialWater", 50.0),
                Map.entry("waterScale", 100.0),
                Map.entry("waterSoftCap", 1_000.0),
                Map.entry("waterSupplyStopThreshold", 2_500.0),
                Map.entry("waterSupplyStackDecay", 0.60),
                Map.entry("incomeCoefficientMultiplier", 1.50),
                Map.entry("empoweredAbilityWaterThreshold", 100.0),
                Map.entry("empoweredAbilityWaterCostMultiplier", 2.0),
                Map.entry("empoweredAbilityEffectMultiplier", 1.5),
                Map.entry("dehydratedDamageMultiplier", 0.30),
                Map.entry("dehydratedAttackSpeedReduction", 0.60),
                Map.entry("dehydrationMaxHealthDamagePerSecond", 0.02)
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
                values.put("focusLevel1AttackSpeedBonus", tierValue(tier, 0.20, 0.30, 0.30));
                values.put("focusLevel2AttackSpeedBonus", tierValue(tier, 0.40, 0.60, 0.60));
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
                values.put("wavePulseDamageRatio", tierValue(tier, 2.0, 2.0, 2.0));
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
                values.put("frostLevel2AuraDamageVsSlowedBonus", tierValue(tier, 0.50, 0.50, 0.50));
                values.put("frostLevel3AuraDamageVsSlowedBonus", tierValue(tier, 1.50, 1.50, 1.50));
                values.put("frostAuraRange", 1.0);
                values.put("frostPulseAttackSpeedReductionMagnitude", tierValue(tier, 0.75, 0.75, 0.75));
                values.put("frostLevel2DamageVsSlowedBonus", 0.15);
                values.put("frostLevel3DamageVsSlowedBonus", 0.30);
            }
            case AMPLIFY -> {
                values.put("bloomLevel1DamageReduction", tierValue(tier, 0.15, 0.15, 0.15));
                values.put("bloomLevel2DamageReduction", tierValue(tier, 0.30, 0.25, 0.25));
                values.put("bloomLevel2AuraAttackSpeedBonus", tierValue(tier, 0.20, 0.20, 0.25));
                values.put("bloomLevel3DamageReduction", tierValue(tier, 0.35, 0.35, 0.40));
                values.put("bloomLevel3AuraAttackSpeedBonus", tierValue(tier, 0.50, 0.50, 0.50));
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
                copy.put(key, value);
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
            validateTowerStats(defaults.id(), this);
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
