package kim.biryeong.semiontd.config;

import static kim.biryeong.semiontd.tower.end.EndConfig.Ability.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kim.biryeong.semiontd.tower.TowerCapacity;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.ancientcity.AncientCityStates;
import kim.biryeong.semiontd.tower.ancientcity.AncientCityTowers;
import kim.biryeong.semiontd.tower.adversary.AdversaryBalance;
import kim.biryeong.semiontd.tower.adversary.AdversaryTowers;
import kim.biryeong.semiontd.tower.adversary.FoxForm;
import kim.biryeong.semiontd.tower.adversary.FoxRoute;
import kim.biryeong.semiontd.tower.adversary.RivalKind;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import kim.biryeong.semiontd.tower.army.ArmyBalance;
import kim.biryeong.semiontd.tower.army.ArmyTowers;
import kim.biryeong.semiontd.tower.atlantis.AtlantisBalance;
import kim.biryeong.semiontd.tower.atlantis.AtlantisTowers;
import kim.biryeong.semiontd.tower.end.EndTowers;
import kim.biryeong.semiontd.tower.engineer.EngineerBalance;
import kim.biryeong.semiontd.tower.engineer.EngineerTowers;
import kim.biryeong.semiontd.tower.engineer.EngineerTrapTower;
import kim.biryeong.semiontd.tower.futureagency.FutureAgencyBalance;
import kim.biryeong.semiontd.tower.futureagency.FutureAgencyLeaderTower;
import kim.biryeong.semiontd.tower.futureagency.FutureAgencyPolicy;
import kim.biryeong.semiontd.tower.futureagency.FutureAgencyRole;
import kim.biryeong.semiontd.tower.futureagency.FutureAgencyTowers;
import kim.biryeong.semiontd.tower.gamble.GambleBalance;
import kim.biryeong.semiontd.tower.gamble.GambleBet;
import kim.biryeong.semiontd.tower.gamble.GambleTowers;
import kim.biryeong.semiontd.tower.hero.HeroCompanionRole;
import kim.biryeong.semiontd.tower.hero.HeroPartyBalance;
import kim.biryeong.semiontd.tower.hero.HeroPartyTowers;
import kim.biryeong.semiontd.tower.hero.HeroWeapon;
import kim.biryeong.semiontd.tower.illager.IllagerRaidStates;
import kim.biryeong.semiontd.tower.illager.IllagerTowers;
import kim.biryeong.semiontd.tower.insect.InsectBalance;
import kim.biryeong.semiontd.tower.insect.InsectTowers;
import kim.biryeong.semiontd.tower.legion.LegionTowers;
import kim.biryeong.semiontd.tower.mage.MageBalance;
import kim.biryeong.semiontd.tower.mage.MageSpell;
import kim.biryeong.semiontd.tower.mage.MageTowers;
import kim.biryeong.semiontd.tower.succubus.SuccubusBalance;
import kim.biryeong.semiontd.tower.succubus.SuccubusTowers;
import kim.biryeong.semiontd.tower.nether.NetherTower;
import kim.biryeong.semiontd.tower.nether.NetherTowers;
import kim.biryeong.semiontd.tower.ocean.OceanTower;
import kim.biryeong.semiontd.tower.ocean.OceanTowers;
import kim.biryeong.semiontd.tower.queen.PokerHand;
import kim.biryeong.semiontd.tower.queen.QueenBalance;
import kim.biryeong.semiontd.tower.queen.QueenTowers;
import kim.biryeong.semiontd.tower.demonlord.DemonLordSkill;
import kim.biryeong.semiontd.tower.demonlord.DemonLordTowers;
import kim.biryeong.semiontd.tower.plant.PlantSoil;
import kim.biryeong.semiontd.tower.plant.PlantTowers;
import kim.biryeong.semiontd.tower.resonance.ResonanceAspect;
import kim.biryeong.semiontd.tower.resonance.ResonanceTowers;
import kim.biryeong.semiontd.tower.thunder.ThunderBalance;
import kim.biryeong.semiontd.tower.thunder.ThunderTowers;
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
        return BundledBalanceDefaults.load("tower_balance.json", TowerBalanceConfig.class, codeDefaults());
    }

    /**
     * The numbers this build ships in code, before the bundled resource replaces them.
     *
     * <p>{@code BundledBalanceDefaults.load} returns the resource verbatim rather than merging, so a
     * builder whose values only land here would silently run on fallbacks. Exposing the code-side
     * config lets tooling diff the two and regenerate the bundled resource.
     */
    public static TowerBalanceConfig codeDefaults() {
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
        addMageTowers(towers);
        addEngineerTowers(towers);
        addInsectTowers(towers);
        addFutureAgencyTowers(towers);
        addQueenTowers(towers);
        addHeroPartyTowers(towers);
        addAtlantisTowers(towers);
        addPlantTowers(towers);
        addArmyTowers(towers);
        addThunderTowers(towers);
        addDemonLordTowers(towers);
        addGambleTowers(towers);
        addSuccubusTowers(towers);

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
        putMageUpgrades(upgradeCosts);
        putEngineerUpgrades(upgradeCosts);
        putInsectUpgrades(upgradeCosts);
        putFutureAgencyUpgrades(upgradeCosts);
        putHeroPartyUpgrades(upgradeCosts);
        putAtlantisUpgrades(upgradeCosts);
        putPlantUpgrades(upgradeCosts);
        putArmyUpgrades(upgradeCosts);
        putThunderUpgrades(upgradeCosts);
        putDemonLordUpgrades(upgradeCosts);
        putGambleUpgrades(upgradeCosts);
        putSuccubusUpgrades(upgradeCosts);

        LinkedHashMap<String, Map<String, Double>> abilities = new LinkedHashMap<>();
        putAbilities(abilities, IllagerRaidStates.RAID_CONFIG_ID, Map.of(
                "gaugeMax", 100.0,
                "waveKillGauge", 3.0,
                "incomeKillGauge", 8.0,
                "markedKillBonusGauge", 7.0,
                "illagerTowerDeathGauge", 20.0,
                "attackSpeedPercentPerTower", 0.02,
                "damagePercentPerTower", 0.06,
                "attackSpeedBonusCap", 0.20,
                "damageBonusCap", 0.60,
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
        putAbilities(abilities, WarlockTowers.CONFIG_ID, warlockGlobalAbilities());
        putAbilities(abilities, WarlockTowers.BASE_WARLOCK_TOWER.id(), baseWarlockAbilities());
        putAbilities(abilities, WarlockTowers.RANGED_WARLOCK_TOWER.id(), rangedWarlockAbilities());
        putAbilities(abilities, WarlockTowers.MELEE_WARLOCK_TOWER.id(), meleeWarlockAbilities());
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
        putMageAbilities(abilities);
        putEngineerAbilities(abilities);
        putInsectAbilities(abilities);
        putFutureAgencyAbilities(abilities);
        putQueenAbilities(abilities);
        putHeroPartyAbilities(abilities);
        putAtlantisAbilities(abilities);
        putPlantAbilities(abilities);
        putArmyAbilities(abilities);
        putThunderAbilities(abilities);
        putDemonLordAbilities(abilities);
        putGambleAbilities(abilities);
        putSuccubusAbilities(abilities);

        TowerBalanceConfig fallback = new TowerBalanceConfig(
                towers,
                upgradeCosts,
                abilities,
                IllusionCloneQueueConfig.defaultConfig(),
                VillagerAdvConfig.defaultConfig()
        );
        return fallback;
    }

    private static void addSuccubusTowers(Map<String, TowerStats> towers) {
        SuccubusTowers.all().forEach(type -> addTower(towers, type));
    }

    private static void putSuccubusUpgrades(Map<String, Long> upgrades) {
        putUpgrade(upgrades, SuccubusTowers.DREAM_DUST_T1, SuccubusTowers.DREAM_DUST_T2.id(), 100);
        putUpgrade(upgrades, SuccubusTowers.DREAM_DUST_T2, SuccubusTowers.DREAM_DUST_T3.id(), 210);
        putUpgrade(upgrades, SuccubusTowers.SLEEPWALKER_T1, SuccubusTowers.SLEEPWALKER_T2.id(), 110);
        putUpgrade(upgrades, SuccubusTowers.SLEEPWALKER_T2, SuccubusTowers.SLEEPWALKER_T3.id(), 230);
        putUpgrade(upgrades, SuccubusTowers.LULLABY_T1, SuccubusTowers.LULLABY_T2.id(), 120);
        putUpgrade(upgrades, SuccubusTowers.LULLABY_T2, SuccubusTowers.LULLABY_T3.id(), 240);
        putUpgrade(upgrades, SuccubusTowers.NIGHTMARE_T1, SuccubusTowers.NIGHTMARE_T2.id(), 135);
        putUpgrade(upgrades, SuccubusTowers.NIGHTMARE_T2, SuccubusTowers.NIGHTMARE_T3.id(), 270);
    }

    private static void putSuccubusAbilities(Map<String, Map<String, Double>> abilities) {
        putAbilities(abilities, SuccubusBalance.CONFIG_ID, Map.ofEntries(
                Map.entry("maxStacks", (double) SuccubusBalance.MAX_STACKS),
                Map.entry("stackDurationTicks", (double) SuccubusBalance.STACK_DURATION_TICKS),
                Map.entry("sleepDurationTicks", (double) SuccubusBalance.SLEEP_DURATION_TICKS),
                Map.entry("towerSleepDurationTicks", (double) SuccubusBalance.TOWER_SLEEP_DURATION_TICKS),
                Map.entry("awakenedImmunityTicks", (double) SuccubusBalance.AWAKENED_IMMUNITY_TICKS),
                Map.entry("spreadStacks", (double) SuccubusBalance.SPREAD_STACKS),
                Map.entry("spreadRadius", SuccubusBalance.SPREAD_RADIUS),
                Map.entry("allyDamagePerStack", SuccubusBalance.ALLY_DAMAGE_PER_STACK),
                Map.entry("allyAttackSpeedPerStack", SuccubusBalance.ALLY_ATTACK_SPEED_PER_STACK),
                Map.entry("enemyAttackSpeedPerStack", SuccubusBalance.ENEMY_ATTACK_SPEED_PER_STACK),
                Map.entry("enemyMoveSpeedPerStack", SuccubusBalance.ENEMY_MOVE_SPEED_PER_STACK),
                Map.entry("succubusAmplification", SuccubusBalance.SUCCUBUS_AMPLIFICATION),
                Map.entry("monsterWakeDamageThreshold", SuccubusBalance.MONSTER_WAKE_DAMAGE_THRESHOLD),
                Map.entry("towerWakeDamageThreshold", SuccubusBalance.TOWER_WAKE_DAMAGE_THRESHOLD),
                Map.entry("monsterWakeBonusDamage", SuccubusBalance.MONSTER_WAKE_BONUS_DAMAGE),
                Map.entry("towerWakeBonusDamage", SuccubusBalance.TOWER_WAKE_BONUS_DAMAGE),
                Map.entry("executionSleepCount", (double) SuccubusBalance.EXECUTION_SLEEP_COUNT),
                Map.entry("absorbAttackRatio", SuccubusBalance.ABSORB_ATTACK_RATIO),
                Map.entry("absorbMaxHealthRatio", SuccubusBalance.ABSORB_MAX_HEALTH_RATIO)
        ));
        putAbilities(abilities, SuccubusTowers.DREAM_DUST_T1.id(), Map.of("stackEvery", 3.0));
        putAbilities(abilities, SuccubusTowers.DREAM_DUST_T2.id(), Map.of("stackEvery", 2.0));
        putAbilities(abilities, SuccubusTowers.DREAM_DUST_T3.id(), Map.of("stackEvery", 1.0));
        putSleepwalker(abilities, SuccubusTowers.SLEEPWALKER_T1, 60, 1, 0.10);
        putSleepwalker(abilities, SuccubusTowers.SLEEPWALKER_T2, 40, 2, 0.15);
        putSleepwalker(abilities, SuccubusTowers.SLEEPWALKER_T3, 30, 3, 0.20);
        putLullaby(abilities, SuccubusTowers.LULLABY_T1, 120, 4.5, 2, 3);
        putLullaby(abilities, SuccubusTowers.LULLABY_T2, 100, 5.0, 3, 5);
        putLullaby(abilities, SuccubusTowers.LULLABY_T3, 80, 5.5, 4, 7);
        putNightmare(abilities, SuccubusTowers.NIGHTMARE_T1, 5, 0.0);
        putNightmare(abilities, SuccubusTowers.NIGHTMARE_T2, 3, 0.25);
        putNightmare(abilities, SuccubusTowers.NIGHTMARE_T3, 0, 0.50);
    }

    private static void putSleepwalker(Map<String, Map<String, Double>> abilities, TowerType type,
                                       int cooldownTicks, int counterStacks, double reduction) {
        putAbilities(abilities, type.id(), Map.of(
                "counterCooldownTicks", (double) cooldownTicks,
                "counterStacks", (double) counterStacks,
                "dreamDamageReduction", reduction
        ));
    }

    private static void putLullaby(Map<String, Map<String, Double>> abilities, TowerType type,
                                   int intervalTicks, double radius, int allyMaxTargets, int enemyMaxTargets) {
        putAbilities(abilities, type.id(), Map.of(
                "pulseIntervalTicks", (double) intervalTicks,
                "radius", radius,
                "allyMaxTargets", (double) allyMaxTargets,
                "enemyMaxTargets", (double) enemyMaxTargets
        ));
    }

    private static void putNightmare(Map<String, Map<String, Double>> abilities, TowerType type,
                                     int minimumStacks, double sleepingDamageBonus) {
        putAbilities(abilities, type.id(), Map.of(
                "minimumStacks", (double) minimumStacks,
                "sleepingDamageBonus", sleepingDamageBonus
        ));
    }

    private static void addPlantTowers(LinkedHashMap<String, TowerStats> towers) {
        PlantTowers.TERRAFORM_TOWERS.forEach(type -> addTower(towers, type));
        PlantTowers.COMBAT_TOWERS.forEach(type -> addTower(towers, type));
    }

    private static void putPlantUpgrades(LinkedHashMap<String, Long> upgradeCosts) {
        // 실서버 기준(시작 다이아 150, 탱커 업그레이드 160/250)에 맞춘 비용입니다.
        putUpgrade(upgradeCosts, PlantTowers.T1_OAK_SEED_TOWER, PlantTowers.T2_OAK_SEED_TOWER.id(), 85);
        putUpgrade(upgradeCosts, PlantTowers.T2_OAK_SEED_TOWER, PlantTowers.T3_OAK_SEED_TOWER.id(), 180);
        putUpgrade(upgradeCosts, PlantTowers.T1_MUSHROOM_SPORE_TOWER, PlantTowers.T2_MUSHROOM_SPORE_TOWER.id(), 85);
        putUpgrade(upgradeCosts, PlantTowers.T2_MUSHROOM_SPORE_TOWER, PlantTowers.T3_MUSHROOM_SPORE_TOWER.id(), 180);
        putUpgrade(upgradeCosts, PlantTowers.T1_DRY_GRASS_SEED_TOWER, PlantTowers.T2_DRY_GRASS_SEED_TOWER.id(), 85);
        putUpgrade(upgradeCosts, PlantTowers.T2_DRY_GRASS_SEED_TOWER, PlantTowers.T3_DRY_GRASS_SEED_TOWER.id(), 180);
        putUpgrade(upgradeCosts, PlantTowers.T1_SPRUCE_SEED_TOWER, PlantTowers.T2_SPRUCE_SEED_TOWER.id(), 85);
        putUpgrade(upgradeCosts, PlantTowers.T2_SPRUCE_SEED_TOWER, PlantTowers.T3_SPRUCE_SEED_TOWER.id(), 180);

        putUpgrade(upgradeCosts, PlantTowers.T1_MEADOW_TOWER, PlantTowers.T2_MEADOW_TOWER.id(), 150);
        putUpgrade(upgradeCosts, PlantTowers.T2_MEADOW_TOWER, PlantTowers.T3_MEADOW_TOWER.id(), 240);
        putUpgrade(upgradeCosts, PlantTowers.T1_MEADOW_NOVA_TOWER, PlantTowers.T2_MEADOW_NOVA_TOWER.id(), 175);
        putUpgrade(upgradeCosts, PlantTowers.T2_MEADOW_NOVA_TOWER, PlantTowers.T3_MEADOW_NOVA_TOWER.id(), 275);
        putUpgrade(upgradeCosts, PlantTowers.T1_MYCELIUM_TOWER, PlantTowers.T2_MYCELIUM_TOWER.id(), 80);
        putUpgrade(upgradeCosts, PlantTowers.T2_MYCELIUM_TOWER, PlantTowers.T3_MYCELIUM_TOWER.id(), 130);
        putUpgrade(upgradeCosts, PlantTowers.T1_DESERT_TOWER, PlantTowers.T2_DESERT_TOWER.id(), 190);
        putUpgrade(upgradeCosts, PlantTowers.T2_DESERT_TOWER, PlantTowers.T3_DESERT_TOWER.id(), 300);
        putUpgrade(upgradeCosts, PlantTowers.T1_PODZOL_TOWER, PlantTowers.T2_PODZOL_TOWER.id(), 170);
        putUpgrade(upgradeCosts, PlantTowers.T2_PODZOL_TOWER, PlantTowers.T3_PODZOL_LILAC_TOWER.id(), 285);
        putUpgrade(upgradeCosts, PlantTowers.T2_PODZOL_TOWER, PlantTowers.T3_PODZOL_ROSE_TOWER.id(), 285);
        putUpgrade(upgradeCosts, PlantTowers.T2_PODZOL_TOWER, PlantTowers.T3_PODZOL_PITCHER_TOWER.id(), 285);
    }

    private static void addArmyTowers(Map<String, TowerStats> towers) {
        ArmyTowers.all().forEach(type -> addTower(towers, type));
    }

    private static void putArmyUpgrades(Map<String, Long> upgrades) {
        putUpgrade(upgrades, ArmyTowers.CLERK, ArmyTowers.DRILL_SERGEANT.id(), 75);
        putUpgrade(upgrades, ArmyTowers.CLERK, ArmyTowers.QUARTERMASTER.id(), 75);
        putUpgrade(upgrades, ArmyTowers.GUARD, ArmyTowers.MILITARY_POLICE.id(), 105);
        putUpgrade(upgrades, ArmyTowers.GUARD, ArmyTowers.GOP_SENTRY.id(), 100);
        putUpgrade(upgrades, ArmyTowers.MILITARY_POLICE, ArmyTowers.MP_COMMANDER.id(), 220);
        putUpgrade(upgrades, ArmyTowers.GOP_SENTRY, ArmyTowers.OUTPOST_CHIEF.id(), 215);
        putUpgrade(upgrades, ArmyTowers.RECRUIT, ArmyTowers.SPECIALIST.id(), 130);
        putUpgrade(upgrades, ArmyTowers.RECRUIT, ArmyTowers.GUNNER.id(), 130);
        putUpgrade(upgrades, ArmyTowers.SPECIALIST, ArmyTowers.PLATOON_LEADER.id(), 280);
        putUpgrade(upgrades, ArmyTowers.GUNNER, ArmyTowers.BATTERY_CHIEF.id(), 280);
    }

    private static void putArmyAbilities(Map<String, Map<String, Double>> abilities) {
        LinkedHashMap<String, Double> global = new LinkedHashMap<>();
        global.put("commandRadius", ArmyBalance.COMMAND_RADIUS);
        global.put("maxCommandBonus", ArmyBalance.MAX_COMMAND_BONUS);
        global.put("dischargeRefundRatio", ArmyBalance.DISCHARGE_REFUND_RATIO);
        global.put("corporalService", (double) ArmyBalance.CORPORAL_SERVICE);
        global.put("corporalAttackMultiplier", ArmyBalance.CORPORAL_ATTACK_MULTIPLIER);
        global.put("corporalDamageBuff", ArmyBalance.CORPORAL_DAMAGE_BUFF);
        global.put("sergeantService", (double) ArmyBalance.SERGEANT_SERVICE);
        global.put("sergeantAttackMultiplier", ArmyBalance.SERGEANT_ATTACK_MULTIPLIER);
        global.put("sergeantDamageBuff", ArmyBalance.SERGEANT_DAMAGE_BUFF);
        global.put("staffSergeantService", (double) ArmyBalance.STAFF_SERGEANT_SERVICE);
        global.put("staffSergeantAttackMultiplier", ArmyBalance.STAFF_SERGEANT_ATTACK_MULTIPLIER);
        global.put("staffSergeantDamageBuff", ArmyBalance.STAFF_SERGEANT_DAMAGE_BUFF);
        global.put("staffSergeantAttackSpeedBuff", ArmyBalance.STAFF_SERGEANT_ATTACK_SPEED_BUFF);
        global.put("dischargeService", (double) ArmyBalance.DISCHARGE_SERVICE);
        global.put("dischargeNoticeWaves", (double) ArmyBalance.DISCHARGE_NOTICE_WAVES);
        global.put("medalDamageBonus", ArmyBalance.MEDAL_DAMAGE_BONUS);
        global.put("maxMedals", (double) ArmyBalance.MAX_MEDALS);
        putAbilities(abilities, ArmyBalance.CONFIG_ID, global);

        // 본부: 진급 속도를 올리는 쪽과, 회전 1회당 수확을 올리는 쪽으로 갈린다.
        putAbilities(abilities, ArmyTowers.CLERK.id(), Map.of(
                "serviceRateBonus", 1.0,
                "serviceRateRadius", 6.0
        ));
        putAbilities(abilities, ArmyTowers.DRILL_SERGEANT.id(), Map.of(
                "serviceRateBonus", 2.0,
                "serviceRateRadius", 7.0
        ));
        putAbilities(abilities, ArmyTowers.QUARTERMASTER.id(), Map.of(
                "serviceRateBonus", 1.0,
                "serviceRateRadius", 6.0,
                "dischargeRefundBonus", 0.4,
                "medalValueBonus", 0.5
        ));

        // 경계: 계급 영향을 받지 않는 대신 후임 버프도 주지 않는다.
        putAbilities(abilities, ArmyTowers.MILITARY_POLICE.id(), Map.of("damageReduction", 0.18));
        putAbilities(abilities, ArmyTowers.MP_COMMANDER.id(), Map.of("damageReduction", 0.30));
        // 초소장 계열은 조교의 정확한 반대 손잡이다.
        putAbilities(abilities, ArmyTowers.GOP_SENTRY.id(), Map.of(
                "serviceRatePenalty", 1.0,
                "serviceRateRadius", 6.0
        ));
        putAbilities(abilities, ArmyTowers.OUTPOST_CHIEF.id(), Map.of(
                "serviceRatePenalty", 2.0,
                "serviceRateRadius", 7.0
        ));

        // 전투: 광역 분기만 splash 를 가진다.
        putAbilities(abilities, ArmyTowers.GUNNER.id(), Map.of(
                "splashDamageRatio", 0.50,
                "splashRadius", 2.5
        ));
        putAbilities(abilities, ArmyTowers.BATTERY_CHIEF.id(), Map.of(
                "splashDamageRatio", 0.70,
                "splashRadius", 4.0
        ));
    }

    private static void putPlantAbilities(LinkedHashMap<String, Map<String, Double>> abilities) {
        // 테라포밍 반경. 타워가 자기 칸을 차지하므로 T1 도 최소 3x3 은 열어야 전투 타워를 놓을 수 있습니다.
        for (TowerType type : PlantTowers.TERRAFORM_TOWERS) {
            putAbilities(abilities, type.id(), Map.of("terraformRadius", (double) PlantTowers.tierOf(type)));
        }
        // 개화: T3 테라포머가 만든 7x7 지형에서 상한(+60%)에 도달합니다.
        putAbilities(abilities, PlantTowers.GLOBAL_CONFIG_ID, Map.of(
                "bloomDamagePerTile", 0.015,
                "bloomDamageCap", 0.6,
                "soilPulseIntervalTicks", 20.0,
                // 지형 효과 범위는 사거리를 따라가되, 사거리를 2배로 늘린 뒤에도 장판이 과해지지 않게 상한을 둡니다.
                "soilAuraMinRadius", 3.0,
                "soilAuraMaxRadius", 6.0,
                "environmentTickIntervalTicks", 20.0,
                // 한 대상을 여러 잔디가 함께 회복시킬 때, 두 번째부터 깎는 비율입니다. 겹치기
                // 자체는 유효하되 잔디 개수만큼 선형으로 늘어나지는 않게 합니다.
                "meadowHealOverlapReduction", 0.5
        ));
        // 잔디는 후방 지원 지형입니다. 자기 회복이 아니라 주변 아군을 회복시키고 성장 체력을 나눠 줍니다.
        putAbilities(abilities, PlantSoil.MEADOW.configId(), Map.of(
                "supportRadius", 6.0,
                "healPercentPerPulse", 0.012,
                // T3 기준 자기 최대 체력은 라운드당 +2.1%, 최대 +70%까지 성장합니다.
                "maxHealthGrowthPerRound", 0.015,
                "maxHealthGrowthCap", 0.5,
                // 라인 전체 분배는 잔디 타워 수만큼 합산되므로 비율을 낮추고 합계 상한을 둡니다.
                "growthShareRatio", 0.15,
                "growthShareCap", 0.25,
                "supportDurationTicks", 60.0
        ));
        // environment* 값은 타워 없이 지형만으로 걸리는 효과입니다.
        // 균사 전투 타워는 지뢰라 상주하지 않으므로 딜증(취약)도 지형이 직접 담당합니다.
        putAbilities(abilities, PlantSoil.MYCELIUM.configId(), Map.of(
                "environmentWeakness", 0.15,
                "environmentDamageTakenBonus", 0.25,
                "environmentMoveSpeedReduction", 0.25,
                "environmentDurationTicks", 60.0
        ));
        putAbilities(abilities, PlantSoil.DESERT.configId(), Map.of(
                "environmentAttackSpeedReduction", 0.15,
                "environmentMaxHealthDamagePerSecond", 0.0075,
                "environmentDurationTicks", 60.0,
                // 타워 오라는 지형 자체 값보다 세게 잡아, 겹치면 타워 쪽이 적용됩니다.
                "attackSpeedReduction", 0.25,
                "debuffDurationTicks", 60.0,
                // 사암 계열은 공격을 안 해 사거리가 0 이라 장판 크기를 지형에서 직접 정합니다.
                "auraRadius", 5.0,
                "thornReflectRatio", 0.30
        ));
        putAbilities(abilities, PlantSoil.PODZOL.configId(), Map.of(
                "rangeBonus", 4.0,
                "attackSpeedBonus", 0.25,
                // 잔디가 체력을 키우듯 회백토는 피해를 키웁니다. 40라운드까지 계속 오릅니다.
                "damageGrowthPerRound", 0.015,
                "damageGrowthCap", 0.6,
                // 잔디와 같은 방식으로 라인 전체에 나눠 줍니다. 회백토 타워 수만큼 합산되므로
                // 비율을 낮게 잡고 합계 상한을 둡니다.
                "growthShareRatio", 0.2,
                "growthShareCap", 0.4,
                "supportDurationTicks", 60.0
        ));

        // 지형 효과는 계열 공용이고, soilPower 가 티어별 배율을 담당합니다.
        // 민들레 계열은 지원 배율에 더해 생존한 웨이브 정산 다이아를 만들어 냅니다.
        putAbilities(abilities, PlantTowers.T1_MEADOW_TOWER.id(), Map.of(
                "soilPower", 0.6,
                "diamondPerWave", 4.0
        ));
        putAbilities(abilities, PlantTowers.T2_MEADOW_TOWER.id(), Map.of(
                "soilPower", 1.0,
                "diamondPerWave", 11.0
        ));
        putAbilities(abilities, PlantTowers.T3_MEADOW_TOWER.id(), Map.of(
                "soilPower", 1.4,
                "diamondPerWave", 28.0
        ));

        // 튤립 계열은 자기 중심 광역이라 novaRadius/novaDamageRatio 를 씁니다.
        putAbilities(abilities, PlantTowers.T1_MEADOW_NOVA_TOWER.id(), Map.of(
                "soilPower", 0.6,
                "novaRadius", 4.0,
                "novaDamageRatio", 0.4
        ));
        putAbilities(abilities, PlantTowers.T2_MEADOW_NOVA_TOWER.id(), Map.of(
                "soilPower", 1.0,
                "novaRadius", 4.5,
                "novaDamageRatio", 0.6
        ));
        putAbilities(abilities, PlantTowers.T3_MEADOW_NOVA_TOWER.id(), Map.of(
                "soilPower", 1.4,
                "novaRadius", 5.5,
                "novaDamageRatio", 0.75
        ));
        // 균사 계열은 라운드당 한 번 터지는 지뢰입니다.
        putPlantMine(abilities, PlantTowers.T1_MYCELIUM_TOWER, 1.5, 3.0, 0.35, 40.0, 8.0);
        putPlantMine(abilities, PlantTowers.T2_MYCELIUM_TOWER, 1.8, 3.5, 0.45, 60.0, 10.0);
        putPlantMine(abilities, PlantTowers.T3_MYCELIUM_TOWER, 2.0, 4.0, 0.55, 80.0, 12.0);
        plantSoilPower(abilities, PlantTowers.T1_DESERT_TOWER, 0.6);
        plantSoilPower(abilities, PlantTowers.T2_DESERT_TOWER, 1.0);
        plantSoilPower(abilities, PlantTowers.T3_DESERT_TOWER, 1.4);
        // 회백토 계열은 치명타를 가집니다. 티어가 오를수록 확률이 높아집니다.
        putAbilities(abilities, PlantTowers.T1_PODZOL_TOWER.id(), Map.of(
                "soilPower", 0.6,
                "critChance", 0.08,
                "critMultiplier", 2.0
        ));
        putAbilities(abilities, PlantTowers.T2_PODZOL_TOWER.id(), Map.of(
                "soilPower", 1.0,
                "critChance", 0.18,
                "critMultiplier", 2.0
        ));

        // 라일락: 대상 지점에서 130도 부채꼴로 꽃가루를 뿌리고, 잃은 체력에 비례해 더 아픕니다.
        putAbilities(abilities, PlantTowers.T3_PODZOL_LILAC_TOWER.id(), Map.of(
                "soilPower", 1.2,
                "critChance", 0.20,
                "critMultiplier", 2.0,
                "splashRadius", 5.0,
                "splashDamageRatio", 0.45,
                "splashConeDegrees", 130.0,
                "splashMissingHealthRatio", 0.03
        ));

        // 장미 덤불: 치명타 특화. 초치명타는 3배입니다.
        putAbilities(abilities, PlantTowers.T3_PODZOL_ROSE_TOWER.id(), Map.of(
                "soilPower", 1.4,
                "critChance", 0.35,
                "critMultiplier", 2.0,
                "superCritChance", 0.05,
                "superCritMultiplier", 3.0
        ));

        // 물병 식물: 곡사 포대. 착탄 지점의 적을 강하게 속박합니다.
        putAbilities(abilities, PlantTowers.T3_PODZOL_PITCHER_TOWER.id(), Map.of(
                "soilPower", 1.2,
                "critChance", 0.20,
                "critMultiplier", 2.0,
                "splashRadius", 4.0,
                "splashDamageRatio", 0.60,
                "snareMoveSpeedReduction", 0.7,
                // 실효 공격 간격(35틱)보다 짧아야 재장전 사이에 적이 움직일 틈이 생깁니다.
                "snareDurationTicks", 20.0,
                // 곡사 연출용 포물선 높이입니다. 0 이면 궤적을 그리지 않습니다.
                "lobArcHeight", 5.0
        ));
    }

    private static void putPlantMine(
            LinkedHashMap<String, Map<String, Double>> abilities,
            TowerType type,
            double triggerRadius,
            double explosionRadius,
            double moveSpeedReduction,
            double disableTicks,
            double fuseTicks
    ) {
        putAbilities(abilities, type.id(), Map.of(
                "triggerRadius", triggerRadius,
                "triggerIntervalTicks", 5.0,
                "explosionRadius", explosionRadius,
                "explosionDamageMultiplier", 2.0,
                // 남은 체력도 함께 터집니다. 온전할수록 세게 터집니다.
                "explosionHealthRatio", 0.25,
                "explosionMoveSpeedReduction", moveSpeedReduction,
                "explosionDisableTicks", disableTicks,
                // 밟은 뒤 터지기까지의 도화선 길이. 이 동안 섬광이 떠 있고, 빠져나가면 맞지
                // 않습니다. 길수록 경고가 후하고 짧을수록 즉발에 가깝습니다.
                "fuseTicks", fuseTicks
        ));
    }

    private static void plantSoilPower(
            LinkedHashMap<String, Map<String, Double>> abilities,
            TowerType type,
            double power
    ) {
        putAbilities(abilities, type.id(), Map.of("soilPower", power));
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
        abilities.forEach((configId, values) -> values.forEach((key, value) -> {
            if (!isValidAbilityValue(configId, key, value)) {
                throw new IllegalArgumentException(
                        "Tower balance ability must be finite and non-negative: " + configId + "." + key
                );
            }
        }));
        validateIntegralAbility(WarlockTowers.CONFIG_ID, "awakeningKills");
        validateIntegralAbility(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "lifeEvery");
        validateIntegralAbility(WarlockTowers.RANGED_WARLOCK_TOWER.id(), "splashEvery");
        validateIntegralAbility(WarlockTowers.MELEE_WARLOCK_TOWER.id(), "defenseEvery");
        validateRatios(IllagerRaidStates.RAID_CONFIG_ID,
                "attackSpeedPercentPerTower", "damagePercentPerTower",
                "attackSpeedBonusCap", "damageBonusCap");
        validateMageBalance();
        validateEngineerBalance();
        validateInsectBalance();
        validateFutureAgencyBalance();
        validateQueenBalance();
        validateHeroPartyBalance();
        validateAtlantisAbilities();
        validatePlantAbilities();
        validateArmyAbilities();
        validateThunderAbilities();
        validateDemonLordAbilities();
        validateGambleAbilities();
        validateSuccubusAbilities();
    }

    private void validateSuccubusAbilities() {
        String global = SuccubusBalance.CONFIG_ID;
        validatePositive(global, "maxStacks", "stackDurationTicks", "sleepDurationTicks", "towerSleepDurationTicks",
                "awakenedImmunityTicks", "spreadStacks", "spreadRadius", "executionSleepCount");
        validateIntegral(global, false, "maxStacks", "stackDurationTicks", "sleepDurationTicks", "towerSleepDurationTicks",
                "awakenedImmunityTicks", "spreadStacks", "executionSleepCount");
        validateRatios(global, "allyDamagePerStack", "allyAttackSpeedPerStack",
                "enemyAttackSpeedPerStack", "enemyMoveSpeedPerStack", "succubusAmplification",
                "monsterWakeDamageThreshold", "towerWakeDamageThreshold",
                "monsterWakeBonusDamage", "towerWakeBonusDamage",
                "absorbAttackRatio", "absorbMaxHealthRatio");
        for (TowerType type : List.of(SuccubusTowers.SLEEPWALKER_T1, SuccubusTowers.SLEEPWALKER_T2,
                SuccubusTowers.SLEEPWALKER_T3)) {
            validateRatios(type.id(), "dreamDamageReduction");
            validatePositive(type.id(), "counterCooldownTicks", "counterStacks");
            validateIntegral(type.id(), false, "counterCooldownTicks", "counterStacks");
        }
        for (TowerType type : List.of(SuccubusTowers.LULLABY_T1, SuccubusTowers.LULLABY_T2,
                SuccubusTowers.LULLABY_T3)) {
            validatePositive(type.id(), "pulseIntervalTicks", "radius", "allyMaxTargets", "enemyMaxTargets");
            validateIntegral(type.id(), false, "pulseIntervalTicks", "allyMaxTargets", "enemyMaxTargets");
        }
    }

    private void validateGambleAbilities() {
        String global = GambleBalance.GLOBAL_ID;
        validatePositive(global,
                "oddEvenWinScore", "oddEvenLossScore", "maxHealthPerScore", "damagePerScore",
                "rangePerScore", "splashRadiusPerScore", "baseSplashRadius",
                "supportVfxIntervalTicks",
                "supportPositiveRangeUnit", "supportPositiveRegenUnit",
                "supportPositiveDamageUnit", "supportPositiveMaxHealthUnit",
                "supportNegativeRangeUnit", "supportNegativeHealthLossUnit",
                "supportNegativeDamageUnit", "supportNegativeMaxHealthUnit",
                "maxSpectatorsPerGambler",
                "kingPromotionScore", "darkKingPromotionScoreMagnitude", "maxGambleScore",
                "twoDiceCompoundMinSum",
                "twoDiceLoss2", "twoDiceLoss3", "twoDiceLoss4", "twoDiceLoss5",
                "twoDiceGain6", "twoDiceGain7", "twoDiceGain8", "twoDiceGain9",
                "twoDiceGain10", "twoDiceGain11", "twoDiceGain12");
        validateRatios(global,
                "abilityRewardChance", "lossInsuranceReduction", "splashDamageRatio");
        validateIntegral(global, false, "supportVfxIntervalTicks", "maxSpectatorsPerGambler");
        validateRange(global, "twoDiceCompoundMinSum", 2.0, 12.0);
        validateIntegral(global, false, "twoDiceCompoundMinSum");
        validateIntegral(global, false,
                "kingPromotionScore", "darkKingPromotionScoreMagnitude", "maxGambleScore");
        double kingPromotionScore = ability(global, "kingPromotionScore", GambleBalance.KING_PROMOTION_SCORE);
        double maxGambleScore = ability(global, "maxGambleScore", GambleBalance.MAX_GAMBLE_SCORE);
        if (maxGambleScore < kingPromotionScore) {
            throw new IllegalArgumentException(
                    "Gamble maximum score must be greater than or equal to the king promotion score."
            );
        }
        validatePositive(GambleTowers.KING.id(), "splashRadiusBonus");
        validatePositive(GambleTowers.DARK_KING.id(), "splashRadiusBonus");

        for (TowerType type : List.of(
                GambleTowers.DICE_T1, GambleTowers.DICE_T2, GambleTowers.DICE_T3,
                GambleTowers.SPECTATOR_T1, GambleTowers.SPECTATOR_T2, GambleTowers.SPECTATOR_T3)) {
            validateRange(type.id(), "minimumRoll", 1.0, 6.0);
            validateIntegral(type.id(), false, "minimumRoll");
            validatePositive(type.id(), "supportPowerMultiplier");
            validateIntegral(type.id(), true, "faceSixDiamondReward");
        }
        if (configuredGambleExpectedScore() <= 0.0) {
            throw new IllegalArgumentException("Gamble two-dice score must have a positive expectation.");
        }
    }

    private double configuredGambleExpectedScore() {
        double[] defaults = {0, 0, -70, -50, -30, -10, 20, 40, 50, 60, 90, 120, 150};
        double total = 0.0;
        for (int first = 1; first <= 6; first++) {
            for (int second = 1; second <= 6; second++) {
                int sum = first + second;
                String key = sum <= 5 ? "twoDiceLoss" + sum : "twoDiceGain" + sum;
                double score = ability(GambleBalance.GLOBAL_ID, key, Math.abs(defaults[sum]));
                if (sum <= 5) score = -score;
                total += score * (first == second ? 2.0 : 1.0);
            }
        }
        return total / 36.0;
    }

    private void validateDemonLordAbilities() {
        String global = DemonLordTowers.GLOBAL_CONFIG_ID;
        validatePositive(global,
                "baseMaxHealth", "experienceBase", "experienceGrowth", "bladeAttackIntervalTicks",
                "healthBonusThreshold", "healthBonusScale", "damageBonusThreshold", "damageBonusScale");
        validateAtLeast(global, 0.0,
                "maxHealthPerLevel", "experiencePerMaxHealth", "damagePerLevel", "bladeDamage");
        validateIntegral(global, false, "maxLevel", "bladeAttackIntervalTicks");
        validateAtLeast(global, 1.0, "experienceGrowth");

        for (DemonLordSkill skill : DemonLordSkill.values()) {
            for (int tier = 1; tier <= DemonLordSkill.MAX_TIER; tier++) {
                String id = skill.towerId(tier);
                validatePositive(id, TowerCapacity.CONFIG_KEY, "cooldownTicks");
                validateIntegral(id, false, TowerCapacity.CONFIG_KEY, "cooldownTicks");
                switch (skill) {
                    case WAVE_OF_MALICE -> {
                        validateRange(id, "coneDegrees", Double.MIN_VALUE, 360.0);
                        validatePositive(id, "range");
                    }
                    case DEMON_WINGS -> {
                        validatePositive(id, "leapPower", "radius");
                    }
                    case SKY_BREAKER -> {
                        validatePositive(id, "dashDistance", "hitRadius", "liftPower", "stunTicks");
                        validateIntegral(id, false, "stunTicks");
                    }
                    case ARCANE_BOMBARDMENT -> {
                        validatePositive(id, "jumpPower", "castDelayTicks", "projectileRange", "blastRadius");
                        validateIntegral(id, false, "castDelayTicks");
                    }
                    case DEMON_BARRIER -> {
                        validateRatios(id, "shieldRatio");
                        validatePositive(id, "shieldDurationTicks");
                        validateIntegral(id, false, "shieldDurationTicks");
                    }
                    case HELLFIRE_BRAND -> {
                        validatePositive(id,
                                "placementRange", "zoneRadius", "zoneDurationTicks", "tickIntervalTicks");
                        validateIntegral(id, false, "zoneDurationTicks", "tickIntervalTicks");
                        validateRatios(id, "damageTakenBonus");
                    }
                    case SOUL_DRAIN -> {
                        validatePositive(id, "range", "width", "rootDurationTicks");
                        validateIntegral(id, false, "rootDurationTicks");
                        validateRatios(id, "lifeStealRatio", "lifeStealCap");
                    }
                    case ROAR_OF_DREAD -> {
                        validatePositive(id, "radius", "dreadDurationTicks");
                        validateIntegral(id, false, "dreadDurationTicks");
                        validateRatios(id, "moveSpeedReduction");
                    }
                    case GRIP_OF_DOOM -> {
                        validatePositive(id, "range", "explosionRadius");
                        validateRatios(id, "missingHealthRatio");
                        validateIntegral(id, true, "killRefundTicks");
                        Double executeRatio = configuredAbility(id, "executeHealthRatio");
                        if (executeRatio != null && (executeRatio < 0.0 || executeRatio >= 1.0)) {
                            throw new IllegalArgumentException(
                                    "Demon lord execute ratio must be at least 0 and below 1: " + id);
                        }
                    }
                    case HELL_GUILLOTINE -> validatePositive(id, "range", "radius");
                }
            }
        }
    }

    private void validateIntegralAbility(String configId, String key) {
        Map<String, Double> values = abilities.get(configId);
        Double value = values == null ? null : values.get(key);
        if (value != null && (value > Integer.MAX_VALUE || value != Math.rint(value))) {
            throw new IllegalArgumentException(
                    "Tower balance count ability must be a whole number no greater than "
                            + Integer.MAX_VALUE + ": " + configId + "." + key
            );
        }
    }

    private void validateThunderAbilities() {
        String global = ThunderBalance.CONFIG_ID;
        validatePositive(global,
                "basePower", "shortageCeiling", "stunTicks", "stunCooldownTicks",
                "stunImmunityTicks", "markDurationTicks", "stormWaveInterval");
        validateIntegral(global, false,
                "stunTicks", "stunCooldownTicks", "stunImmunityTicks", "markDurationTicks", "stormWaveInterval");
        validateRange(global, "surplusFloor", 0.0, 1.0);
        validateRatios(global,
                "surplusDamageBonus", "shortageDamagePenalty", "shortageAttackSpeedPenalty");
        Double stunTicks = configuredAbility(global, "stunTicks");
        Double immunityTicks = configuredAbility(global, "stunImmunityTicks");
        if (stunTicks != null && immunityTicks != null && immunityTicks < stunTicks) {
            throw new IllegalArgumentException("Thunder stun immunity must not be shorter than the stun.");
        }
        Double shortageCeiling = configuredAbility(global, "shortageCeiling");
        if (shortageCeiling != null && shortageCeiling <= 1.0) {
            throw new IllegalArgumentException("Thunder shortage ceiling must be greater than 1.");
        }

        for (TowerType type : ThunderTowers.all()) {
            String id = type.id();
            validateAtLeast(id, 0.0,
                    "powerOutput", "stormMinOutput", "stormMaxOutput", "powerDraw", "healthToPower",
                    "dischargeDamage", "dischargeRadius", "chainTargets", "chainRadius", "chainDamageRatio");
            validateRatios(id, "damageAbsorb", "markAttackReduction", "markDamageBonus", "chainDamageRatio");
            validateAtLeast(id, 1.0, "surgeMaxMultiplier");
        }

        Double stormMin = configuredAbility(ThunderTowers.ROD_STORM.id(), "stormMinOutput");
        Double stormMax = configuredAbility(ThunderTowers.ROD_STORM.id(), "stormMaxOutput");
        if (stormMin != null && stormMax != null && stormMin > stormMax) {
            throw new IllegalArgumentException("Thunder storm minimum output must not exceed its maximum output.");
        }
        validatePositive(ThunderTowers.ARMADILLO_EARTH.id(), "dischargeDamage", "dischargeRadius");
        for (TowerType type : List.of(ThunderTowers.SQUIRREL_T2, ThunderTowers.SQUIRREL_T3)) {
            validatePositive(type.id(), "chainRadius", "chainDamageRatio");
            validateIntegral(type.id(), false, "chainTargets");
        }
    }

    private void validateAtlantisAbilities() {
        String global = AtlantisBalance.CONFIG_ID;
        validateRatios(global,
                "slowPerStack", "maxSlow", "maxZoneAllyDamageReduction", "waterPressureDamageRatio");
        validatePositive(global,
                "maxPressureStacks", "stackDurationTicks", "waterPressureDamageCap", "waterPressureRadius",
                "zoneStackMultiplier", "maxZoneCount", "zoneSpacingBlocks", "zoneScanIntervalTicks",
                "zoneVfxIntervalTicks");
        validateIntegral(global, false,
                "maxPressureStacks", "stackDurationTicks", "maxZoneCount", "zoneScanIntervalTicks",
                "zoneVfxIntervalTicks");
        validateIntegral(global, true, "maxChainDepth");
        validateAtLeast(global, 1.0, "waterPressureDamageCap", "zoneStackMultiplier");

        Double slowPerStack = configuredAbility(global, "slowPerStack");
        Double maxSlow = configuredAbility(global, "maxSlow");
        if (slowPerStack != null && maxSlow != null && slowPerStack > maxSlow) {
            throw new IllegalArgumentException("Atlantis slow per stack must not exceed the maximum slow.");
        }
        Double scanTicks = configuredAbility(global, "zoneScanIntervalTicks");
        Double vfxTicks = configuredAbility(global, "zoneVfxIntervalTicks");
        if (scanTicks != null && vfxTicks != null && vfxTicks < scanTicks) {
            throw new IllegalArgumentException("Atlantis zone VFX interval must not be shorter than the scan interval.");
        }

        Double maxReduction = configuredAbility(global, "maxZoneAllyDamageReduction");
        for (TowerType type : List.of(
                AtlantisTowers.TURTLE_T1, AtlantisTowers.TURTLE_T2, AtlantisTowers.TURTLE_T3)) {
            String id = type.id();
            validatePositive(id, "zoneCapacity", "zoneRadius");
            validateIntegral(id, false, "zoneCapacity");
            validateRatios(id, "zoneAllyDamageReduction");
            Double reduction = configuredAbility(id, "zoneAllyDamageReduction");
            if (reduction != null && maxReduction != null && reduction > maxReduction) {
                throw new IllegalArgumentException("Atlantis turtle reduction exceeds the global cap: " + id);
            }
        }
        for (TowerType type : List.of(
                AtlantisTowers.DOLPHIN_T1, AtlantisTowers.DOLPHIN_T2, AtlantisTowers.DOLPHIN_T3)) {
            validatePositive(type.id(), "stackPerHit");
            validateIntegral(type.id(), false, "stackPerHit");
            validateRatios(type.id(), "waterPressureRatioBonus");
        }
        for (TowerType type : List.of(
                AtlantisTowers.AXOLOTL_T1, AtlantisTowers.AXOLOTL_T2, AtlantisTowers.AXOLOTL_T3)) {
            validatePositive(type.id(), "regenAmount", "supportRadius", "supportIntervalTicks");
            validateIntegral(type.id(), false, "supportIntervalTicks");
            validateRatios(type.id(), "attackSpeedBonus", "waterPressureRatioBonus");
        }
        validatePositive(AtlantisTowers.AXOLOTL_T3.id(), "stackBonus");
        validateIntegral(AtlantisTowers.AXOLOTL_T3.id(), false, "stackBonus");
        for (TowerType type : List.of(
                AtlantisTowers.CONDUIT_T1, AtlantisTowers.CONDUIT_T2, AtlantisTowers.CONDUIT_T3)) {
            validatePositive(type.id(), "amplifyRadius", "maxStackBonus");
            validateIntegral(type.id(), false, "maxStackBonus");
            validateRatios(type.id(), "waterPressureRatioBonus");
        }
    }

    private void validateArmyAbilities() {
        String global = ArmyBalance.CONFIG_ID;
        validateRatios(global,
                "dischargeRefundRatio", "medalDamageBonus", "corporalAttackMultiplier",
                "corporalDamageBuff", "sergeantAttackMultiplier", "sergeantDamageBuff",
                "staffSergeantAttackMultiplier", "staffSergeantDamageBuff", "staffSergeantAttackSpeedBuff");
        validatePositive(global,
                "commandRadius", "maxCommandBonus", "maxMedals", "corporalService", "sergeantService",
                "staffSergeantService", "dischargeService");
        validateIntegral(global, false,
                "maxMedals", "corporalService", "sergeantService", "staffSergeantService",
                "dischargeService");
        validateIntegral(global, true, "dischargeNoticeWaves");

        Double corporal = configuredAbility(global, "corporalService");
        Double sergeant = configuredAbility(global, "sergeantService");
        Double staffSergeant = configuredAbility(global, "staffSergeantService");
        Double discharge = configuredAbility(global, "dischargeService");
        Double notice = configuredAbility(global, "dischargeNoticeWaves");
        if (corporal != null && sergeant != null && staffSergeant != null && discharge != null
                && !(corporal < sergeant && sergeant < staffSergeant && staffSergeant < discharge)) {
            throw new IllegalArgumentException("Army service thresholds must be strictly increasing.");
        }
        if (notice != null && discharge != null && notice > discharge) {
            throw new IllegalArgumentException("Army discharge notice must not exceed total service.");
        }

        for (TowerType type : ArmyTowers.all()) {
            String id = type.id();
            validateRatios(id, "damageReduction", "splashDamageRatio");
            // Service rate is stored as two non-negative keys; a tower carrying both would make the
            // 조교 / 초소장 opposition meaningless, so reject it rather than silently netting out.
            Double bonus = configuredAbility(id, "serviceRateBonus");
            Double penalty = configuredAbility(id, "serviceRatePenalty");
            if (bonus != null && penalty != null) {
                throw new IllegalArgumentException(
                        "Army tower must not set both serviceRateBonus and serviceRatePenalty: " + id);
            }
            if ((bonus != null || penalty != null) && configuredAbility(id, "serviceRateRadius") == null) {
                throw new IllegalArgumentException("Army service rate change needs a radius: " + id);
            }
        }
    }

    private void validatePlantAbilities() {
        validateRatios(PlantTowers.GLOBAL_CONFIG_ID,
                "bloomDamagePerTile", "bloomDamageCap", "meadowHealOverlapReduction");
        validateRatios(PlantSoil.MEADOW.configId(),
                "healPercentPerPulse", "growthShareRatio");
        validateRatios(PlantSoil.MYCELIUM.configId(),
                "environmentWeakness", "environmentDamageTakenBonus", "environmentMoveSpeedReduction");
        validateRatios(PlantSoil.DESERT.configId(),
                "environmentAttackSpeedReduction", "environmentMaxHealthDamagePerSecond",
                "attackSpeedReduction", "thornReflectRatio");
        validateRatios(PlantSoil.PODZOL.configId(),
                "attackSpeedBonus", "growthShareRatio");
        validateAtLeast(PlantSoil.MEADOW.configId(), 0.0,
                "maxHealthGrowthPerRound", "maxHealthGrowthCap", "growthShareCap");
        validateAtLeast(PlantSoil.PODZOL.configId(), 0.0,
                "damageGrowthPerRound", "damageGrowthCap", "growthShareCap");

        validatePositive(PlantTowers.GLOBAL_CONFIG_ID,
                "soilAuraMinRadius", "soilAuraMaxRadius", "soilPulseIntervalTicks", "environmentTickIntervalTicks");
        validatePositive(PlantSoil.MEADOW.configId(), "supportRadius", "supportDurationTicks");
        validatePositive(PlantSoil.PODZOL.configId(), "supportDurationTicks");
        validatePositive(PlantSoil.MYCELIUM.configId(), "environmentDurationTicks");
        validatePositive(PlantSoil.DESERT.configId(), "environmentDurationTicks", "debuffDurationTicks", "auraRadius");
        validateIntegral(PlantTowers.GLOBAL_CONFIG_ID, false, "soilPulseIntervalTicks", "environmentTickIntervalTicks");
        validateIntegral(PlantSoil.MEADOW.configId(), false, "supportDurationTicks");
        validateIntegral(PlantSoil.PODZOL.configId(), false, "supportDurationTicks");
        validateIntegral(PlantSoil.MYCELIUM.configId(), false, "environmentDurationTicks");
        validateIntegral(PlantSoil.DESERT.configId(), false, "environmentDurationTicks", "debuffDurationTicks");

        for (TowerType mine : List.of(
                PlantTowers.T1_MYCELIUM_TOWER, PlantTowers.T2_MYCELIUM_TOWER, PlantTowers.T3_MYCELIUM_TOWER)) {
            validatePositive(mine.id(), "triggerIntervalTicks", "fuseTicks");
            validateIntegral(mine.id(), false, "triggerIntervalTicks", "fuseTicks");
        }

        Double minRadius = configuredAbility(PlantTowers.GLOBAL_CONFIG_ID, "soilAuraMinRadius");
        Double maxRadius = configuredAbility(PlantTowers.GLOBAL_CONFIG_ID, "soilAuraMaxRadius");
        if (minRadius != null && maxRadius != null && minRadius > maxRadius) {
            throw new IllegalArgumentException("Plant soil aura minimum radius must not exceed its maximum radius.");
        }

        for (TowerType type : PlantTowers.TERRAFORM_TOWERS) {
            validateIntegral(type.id(), false, "terraformRadius");
        }
        for (TowerType type : PlantTowers.COMBAT_TOWERS) {
            String id = type.id();
            validateRatios(id,
                    "novaDamageRatio", "explosionHealthRatio", "explosionMoveSpeedReduction",
                    "critChance", "superCritChance", "splashDamageRatio",
                    "splashMissingHealthRatio", "snareMoveSpeedReduction");
            validatePositive(id,
                    "soilPower", "novaRadius", "triggerRadius", "explosionRadius",
                    "explosionDamageMultiplier", "critMultiplier", "superCritMultiplier", "splashRadius");
            validateIntegral(id, true, "diamondPerWave");
            validateIntegral(id, false, "triggerIntervalTicks", "explosionDisableTicks", "snareDurationTicks");
            validateAtLeast(id, 1.0, "explosionDamageMultiplier", "critMultiplier", "superCritMultiplier");
            validateRange(id, "splashConeDegrees", 0.0, 360.0);
        }
    }

    private void validateRatios(String configId, String... keys) {
        for (String key : keys) {
            validateRange(configId, key, 0.0, 1.0);
        }
    }

    private void validateRange(String configId, String key, double minimum, double maximum) {
        Double value = configuredAbility(configId, key);
        if (value != null && (value < minimum || value > maximum)) {
            throw new IllegalArgumentException(
                    "Tower balance ability must be between " + minimum + " and " + maximum
                            + ": " + configId + "." + key
            );
        }
    }

    private void validatePositive(String configId, String... keys) {
        for (String key : keys) {
            Double value = configuredAbility(configId, key);
            if (value != null && value <= 0.0) {
                throw new IllegalArgumentException(
                        "Tower balance ability must be positive: " + configId + "." + key
                );
            }
        }
    }

    private void validateAtLeast(String configId, double minimum, String... keys) {
        for (String key : keys) {
            Double value = configuredAbility(configId, key);
            if (value != null && value < minimum) {
                throw new IllegalArgumentException(
                        "Tower balance ability must be at least " + minimum + ": " + configId + "." + key
                );
            }
        }
    }

    private void validateIntegral(String configId, boolean allowZero, String... keys) {
        for (String key : keys) {
            Double value = configuredAbility(configId, key);
            if (value != null && (value != Math.rint(value) || value < (allowZero ? 0.0 : 1.0))) {
                throw new IllegalArgumentException(
                        "Tower balance ability must be " + (allowZero ? "a non-negative" : "a positive")
                                + " integer: " + configId + "." + key
                );
            }
        }
    }

    private Double configuredAbility(String configId, String key) {
        Map<String, Double> values = abilities.get(configId);
        return values == null ? null : values.get(key);
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
                for (Map.Entry<String, Double> entry : defaultValues.entrySet()) {
                    mergedValues.putIfAbsent(entry.getKey(), entry.getValue());
                }
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

    public TowerBalanceConfig withInvalidNumericValuesFrom(TowerBalanceConfig fallback) {
        if (fallback == null) {
            return this;
        }

        LinkedHashMap<String, TowerStats> repairedTowers = new LinkedHashMap<>();
        towers.forEach((towerId, stats) -> repairedTowers.put(
                towerId,
                stats.withInvalidNumericValuesFrom(fallback.towers.get(towerId))
        ));

        LinkedHashMap<String, Long> repairedUpgradeCosts = new LinkedHashMap<>();
        upgradeCosts.forEach((key, value) -> {
            Long repaired = value >= 0L ? value : fallback.upgradeCosts.get(key);
            if (repaired != null && repaired >= 0L) {
                repairedUpgradeCosts.put(key, repaired);
            }
        });

        LinkedHashMap<String, Map<String, Double>> repairedAbilities = new LinkedHashMap<>();
        abilities.forEach((configId, values) -> {
            Map<String, Double> fallbackValues = fallback.abilities.getOrDefault(configId, Map.of());
            LinkedHashMap<String, Double> repairedValues = new LinkedHashMap<>();
            values.forEach((key, value) -> {
                Double repaired = isValidAbilityValue(configId, key, value) ? value : fallbackValues.get(key);
                if (isValidAbilityValue(configId, key, repaired)) {
                    repairedValues.put(key, repaired);
                }
            });
            repairedAbilities.put(configId, repairedValues);
        });

        return new TowerBalanceConfig(
                repairedTowers,
                repairedUpgradeCosts,
                repairedAbilities,
                illusionCloneQueue,
                villagerAdv,
                schemaVersion
        );
    }

    private static boolean isValidAbilityValue(String configId, String key, Double value) {
        if (value == null || !Double.isFinite(value)) {
            return false;
        }
        boolean signedHeroWeaponAggro = "aggroPriority".equals(key)
                && configId.startsWith("hero_party_weapon_")
                && HeroWeapon.byId(configId.substring("hero_party_weapon_".length())) != null;
        return value >= 0.0 || signedHeroWeaponAggro;
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
        AdversaryTowers.configurableTowers().forEach(type -> addTower(towers, type));
    }

    private static void addThunderTowers(Map<String, TowerStats> towers) {
        ThunderTowers.all().forEach(type -> addTower(towers, type));
    }

    private static void putThunderUpgrades(Map<String, Long> upgrades) {
        putUpgrade(upgrades, ThunderTowers.ROD_T1, ThunderTowers.ROD_COPPER.id(), 75);
        putUpgrade(upgrades, ThunderTowers.ROD_T1, ThunderTowers.ROD_STORM.id(), 75);
        putUpgrade(upgrades, ThunderTowers.ARMADILLO_T1, ThunderTowers.ARMADILLO_INSULATED.id(), 105);
        putUpgrade(upgrades, ThunderTowers.ARMADILLO_T1, ThunderTowers.ARMADILLO_GROUNDED.id(), 100);
        putUpgrade(upgrades, ThunderTowers.ARMADILLO_INSULATED, ThunderTowers.ARMADILLO_RUBBER.id(), 220);
        putUpgrade(upgrades, ThunderTowers.ARMADILLO_GROUNDED, ThunderTowers.ARMADILLO_EARTH.id(), 215);
        putUpgrade(upgrades, ThunderTowers.SQUIRREL_T1, ThunderTowers.SQUIRREL_T2.id(), 130);
        putUpgrade(upgrades, ThunderTowers.SQUIRREL_T1, ThunderTowers.SURGE_T2.id(), 130);
        putUpgrade(upgrades, ThunderTowers.SQUIRREL_T2, ThunderTowers.SQUIRREL_T3.id(), 280);
        putUpgrade(upgrades, ThunderTowers.SURGE_T2, ThunderTowers.SURGE_T3.id(), 280);
    }

    private static void putThunderAbilities(Map<String, Map<String, Double>> abilities) {
        LinkedHashMap<String, Double> global = new LinkedHashMap<>();
        global.put("basePower", ThunderBalance.BASE_POWER);
        global.put("surplusFloor", ThunderBalance.SURPLUS_FLOOR);
        global.put("surplusDamageBonus", ThunderBalance.SURPLUS_DAMAGE_BONUS);
        global.put("shortageCeiling", ThunderBalance.SHORTAGE_CEILING);
        global.put("shortageDamagePenalty", ThunderBalance.SHORTAGE_DAMAGE_PENALTY);
        global.put("shortageAttackSpeedPenalty", ThunderBalance.SHORTAGE_ATTACK_SPEED_PENALTY);
        global.put("stunTicks", (double) ThunderBalance.STUN_TICKS);
        global.put("stunCooldownTicks", (double) ThunderBalance.STUN_COOLDOWN_TICKS);
        global.put("stunImmunityTicks", (double) ThunderBalance.STUN_IMMUNITY_TICKS);
        global.put("markDurationTicks", (double) ThunderBalance.MARK_DURATION_TICKS);
        global.put("stormWaveInterval", (double) ThunderBalance.STORM_WAVE_INTERVAL);
        putAbilities(abilities, ThunderBalance.CONFIG_ID, global);


        // 발전: 고정 출력 두 종과, 저점과 고점이 크게 벌어진 변동 출력 한 종.
        putAbilities(abilities, ThunderTowers.ROD_T1.id(), Map.of("powerOutput", 26.0));
        putAbilities(abilities, ThunderTowers.ROD_COPPER.id(), Map.of("powerOutput", 75.0));
        putAbilities(abilities, ThunderTowers.ROD_STORM.id(), Map.of(
                "stormMinOutput", 18.0,
                "stormMaxOutput", 135.0
        ));

        // 절연 루트: 전력을 쓰지 않고, 잃은 체력만큼을 발전으로 돌려준다.
        putAbilities(abilities, ThunderTowers.ARMADILLO_INSULATED.id(), Map.of("healthToPower", 55.0));
        putAbilities(abilities, ThunderTowers.ARMADILLO_RUBBER.id(), Map.of("healthToPower", 120.0));

        // 접지 루트: 전력을 쓰는 대신 표식으로 아군 전체의 피해를 키운다.
        putAbilities(abilities, ThunderTowers.ARMADILLO_GROUNDED.id(), Map.of(
                "powerDraw", 7.0,
                "markDamageBonus", 0.30,
                "markAttackReduction", 0.20,
                "damageAbsorb", 0.22
        ));
        putAbilities(abilities, ThunderTowers.ARMADILLO_EARTH.id(), Map.of(
                "powerDraw", 10.0,
                "markDamageBonus", 0.48,
                "markAttackReduction", 0.30,
                "damageAbsorb", 0.35,
                "dischargeDamage", 210.0,
                "dischargeRadius", 4.0
        ));

        putAbilities(abilities, ThunderTowers.SQUIRREL_T1.id(), Map.of("powerDraw", 6.0));
        putAbilities(abilities, ThunderTowers.SQUIRREL_T2.id(), Map.of(
                "powerDraw", 14.0,
                "chainTargets", 2.0,
                "chainRadius", 3.0,
                "chainDamageRatio", 0.35
        ));
        // 뇌신: 광역 담당. 직선 관통은 실전에서 거의 발동하지 않아 인접 전이로 대체했다.
        putAbilities(abilities, ThunderTowers.SQUIRREL_T3.id(), Map.of(
                "powerDraw", 24.0,
                "chainTargets", 4.0,
                "chainRadius", 4.0,
                "chainDamageRatio", 0.48
        ));
        // 폭주: 단일 대상 담당. 여유 전력을 그대로 화력으로 환산한다.
        putAbilities(abilities, ThunderTowers.SURGE_T2.id(), Map.of(
                "powerDraw", 18.0,
                "surgeMaxMultiplier", 1.85
        ));
        putAbilities(abilities, ThunderTowers.SURGE_T3.id(), Map.of(
                "powerDraw", 30.0,
                "surgeMaxMultiplier", 1.50
        ));
    }

    private static void addMageTowers(Map<String, TowerStats> towers) {
        MageTowers.all().forEach(type -> addTower(towers, type));
    }

    private static void addEngineerTowers(Map<String, TowerStats> towers) {
        EngineerTowers.all().forEach(type -> addTower(towers, type));
    }

    private static void addInsectTowers(Map<String, TowerStats> towers) {
        InsectTowers.all().forEach(type -> addTower(towers, type));
    }

    private static void addFutureAgencyTowers(Map<String, TowerStats> towers) {
        FutureAgencyTowers.all().forEach(type -> addTower(towers, type));
    }

    private static void addQueenTowers(Map<String, TowerStats> towers) {
        QueenTowers.all().forEach(type -> addTower(towers, type));
    }

    private static void addAtlantisTowers(Map<String, TowerStats> towers) {
        AtlantisTowers.all().forEach(type -> addTower(towers, type));
    }

    private static void putAtlantisUpgrades(Map<String, Long> upgrades) {
        putUpgrade(upgrades, AtlantisTowers.TURTLE_T1, AtlantisTowers.TURTLE_T2.id(), 115);
        putUpgrade(upgrades, AtlantisTowers.TURTLE_T2, AtlantisTowers.TURTLE_T3.id(), 240);
        putUpgrade(upgrades, AtlantisTowers.DOLPHIN_T1, AtlantisTowers.DOLPHIN_T2.id(), 120);
        putUpgrade(upgrades, AtlantisTowers.DOLPHIN_T2, AtlantisTowers.DOLPHIN_T3.id(), 250);
        putUpgrade(upgrades, AtlantisTowers.AXOLOTL_T1, AtlantisTowers.AXOLOTL_T2.id(), 95);
        putUpgrade(upgrades, AtlantisTowers.AXOLOTL_T2, AtlantisTowers.AXOLOTL_T3.id(), 200);
        putUpgrade(upgrades, AtlantisTowers.CONDUIT_T1, AtlantisTowers.CONDUIT_T2.id(), 105);
        putUpgrade(upgrades, AtlantisTowers.CONDUIT_T2, AtlantisTowers.CONDUIT_T3.id(), 215);
    }

    private static void putAtlantisAbilities(Map<String, Map<String, Double>> abilities) {
        LinkedHashMap<String, Double> global = new LinkedHashMap<>();
        global.put("maxPressureStacks", (double) AtlantisBalance.MAX_PRESSURE_STACKS);
        global.put("stackDurationTicks", (double) AtlantisBalance.STACK_DURATION_TICKS);
        global.put("slowPerStack", AtlantisBalance.SLOW_PER_STACK);
        global.put("maxSlow", AtlantisBalance.MAX_SLOW);
        global.put("maxZoneAllyDamageReduction", AtlantisBalance.MAX_ZONE_ALLY_DAMAGE_REDUCTION);
        global.put("waterPressureDamageRatio", AtlantisBalance.WATER_PRESSURE_DAMAGE_RATIO);
        global.put("waterPressureDamageCap", AtlantisBalance.WATER_PRESSURE_DAMAGE_CAP);
        global.put("waterPressureRadius", AtlantisBalance.WATER_PRESSURE_RADIUS);
        global.put("zoneStackMultiplier", AtlantisBalance.ZONE_STACK_MULTIPLIER);
        global.put("maxZoneCount", (double) AtlantisBalance.MAX_ZONE_COUNT);
        global.put("zoneSpacingBlocks", AtlantisBalance.ZONE_SPACING_BLOCKS);
        global.put("zoneScanIntervalTicks", (double) AtlantisBalance.ZONE_SCAN_INTERVAL_TICKS);
        global.put("zoneVfxIntervalTicks", (double) AtlantisBalance.ZONE_VFX_INTERVAL_TICKS);
        global.put("maxChainDepth", (double) AtlantisBalance.MAX_CHAIN_DEPTH);
        putAbilities(abilities, AtlantisBalance.CONFIG_ID, global);

        putAtlantisTurtle(abilities, AtlantisTowers.TURTLE_T1, 1.0, 3.0, 0.10);
        putAtlantisTurtle(abilities, AtlantisTowers.TURTLE_T2, 2.0, 3.5, 0.18);
        putAtlantisTurtle(abilities, AtlantisTowers.TURTLE_T3, 3.0, 4.0, 0.25);

        putAtlantisDolphin(abilities, AtlantisTowers.DOLPHIN_T1, 1.0, 0.03);
        putAtlantisDolphin(abilities, AtlantisTowers.DOLPHIN_T2, 2.0, 0.05);
        putAtlantisDolphin(abilities, AtlantisTowers.DOLPHIN_T3, 3.0, 0.08);

        putAbilities(abilities, AtlantisTowers.AXOLOTL_T1.id(), Map.of(
                "regenAmount", 6.0,
                "supportRadius", 4.5,
                "supportIntervalTicks", 40.0
        ));
        putAbilities(abilities, AtlantisTowers.AXOLOTL_T2.id(), Map.of(
                "regenAmount", 16.0,
                "attackSpeedBonus", 0.08,
                "supportRadius", 5.5,
                "supportIntervalTicks", 40.0
        ));
        putAbilities(abilities, AtlantisTowers.AXOLOTL_T3.id(), Map.of(
                "regenAmount", 32.0,
                "attackSpeedBonus", 0.15,
                "stackBonus", 1.0,
                "waterPressureRatioBonus", 0.04,
                "supportRadius", 6.5,
                "supportIntervalTicks", 40.0
        ));

        putAtlantisConduit(abilities, AtlantisTowers.CONDUIT_T1, 6.0, 2.0, 0.02);
        putAtlantisConduit(abilities, AtlantisTowers.CONDUIT_T2, 7.0, 3.0, 0.04);
        putAtlantisConduit(abilities, AtlantisTowers.CONDUIT_T3, 8.0, 4.0, 0.06);
    }

    private static void putAtlantisTurtle(
            Map<String, Map<String, Double>> abilities,
            TowerType type,
            double zoneCapacity,
            double zoneRadius,
            double allyDamageReduction
    ) {
        putAbilities(abilities, type.id(), Map.of(
                "zoneCapacity", zoneCapacity,
                "zoneRadius", zoneRadius,
                "zoneAllyDamageReduction", allyDamageReduction
        ));
    }

    private static void putAtlantisDolphin(
            Map<String, Map<String, Double>> abilities,
            TowerType type,
            double stackPerHit,
            double waterPressureRatioBonus
    ) {
        putAbilities(abilities, type.id(), Map.of(
                "stackPerHit", stackPerHit,
                "waterPressureRatioBonus", waterPressureRatioBonus
        ));
    }

    private static void putAtlantisConduit(
            Map<String, Map<String, Double>> abilities,
            TowerType type,
            double amplifyRadius,
            double maxStackBonus,
            double waterPressureRatioBonus
    ) {
        putAbilities(abilities, type.id(), Map.of(
                "amplifyRadius", amplifyRadius,
                "maxStackBonus", maxStackBonus,
                "waterPressureRatioBonus", waterPressureRatioBonus
        ));
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
        putUpgrade(upgrades, AncientCityTowers.WARDEN_T3, AncientCityTowers.WARDEN_T4.id(), 650);
    }

    private static void putAdversaryUpgrades(Map<String, Long> upgrades) {
        for (FoxRoute route : FoxRoute.values()) {
            FoxForm intermediate = FoxForm.intermediateFor(route);
            putUpgrade(
                    upgrades,
                    AdversaryTowers.FOX,
                    AdversaryTowers.typeFor(intermediate).id(),
                    AdversaryBalance.FIRST_EVOLUTION_COST
            );
            for (FoxForm finalForm : FoxForm.finalsFor(route)) {
                putUpgrade(
                        upgrades,
                        AdversaryTowers.typeFor(intermediate),
                        AdversaryTowers.typeFor(finalForm).id(),
                        AdversaryBalance.FINAL_EVOLUTION_COST
                );
            }
        }
        for (RivalKind kind : RivalKind.values()) {
            TowerType base = AdversaryTowers.baseRival(kind);
            TowerType enhanced = AdversaryTowers.enhancedRival(kind);
            putUpgrade(upgrades, base, enhanced.id(), AdversaryBalance.defaultRivalBaseCost(kind));
        }
    }

    private static void putMageUpgrades(Map<String, Long> upgrades) {
        for (MageSpell spell : MageSpell.values()) {
            putUpgrade(upgrades, MageTowers.WIZARD, MageTowers.spellType(spell).id(), 0);
        }
        MageTowers.predictionTypes().values().forEach(type ->
                putUpgrade(upgrades, MageTowers.PROPHET, type.id(), 0));
    }

    private static void putEngineerUpgrades(Map<String, Long> upgrades) {
        EngineerTowers.repeaters().values().forEach(type ->
                putUpgrade(upgrades, EngineerTowers.REDSTONE_DUST, type.id(), 8));
        java.util.List<net.minecraft.core.Direction> directions = java.util.List.of(
                net.minecraft.core.Direction.NORTH,
                net.minecraft.core.Direction.EAST,
                net.minecraft.core.Direction.SOUTH,
                net.minecraft.core.Direction.WEST
        );
        for (int index = 0; index < directions.size(); index++) {
            TowerType from = EngineerTowers.repeater(directions.get(index));
            TowerType to = EngineerTowers.repeater(directions.get((index + 1) % directions.size()));
            putUpgrade(upgrades, from, to.id(), 0);
        }
        for (EngineerTowers.PlateKind kind : EngineerTowers.PlateKind.values()) {
            kind.next().ifPresent(next ->
                    putUpgrade(upgrades, EngineerTowers.plate(kind), EngineerTowers.plate(next).id(), 15));
        }
        long[][] costs = {
                {70, 120},
                {100, 160},
                {80, 130},
                {120, 180},
                {85, 140}
        };
        for (EngineerTowers.TrapKind kind : EngineerTowers.TrapKind.values()) {
            int row = kind.ordinal();
            putUpgrade(upgrades, EngineerTowers.trap(kind, 1), EngineerTowers.trap(kind, 2).id(), costs[row][0]);
            putUpgrade(upgrades, EngineerTowers.trap(kind, 2), EngineerTowers.trap(kind, 3).id(), costs[row][1]);
        }
    }

    private static void putInsectUpgrades(Map<String, Long> upgrades) {
        putUpgrade(upgrades, InsectTowers.SILVERFISH, InsectTowers.ENDERMITE.id(), 75);
        putUpgrade(upgrades, InsectTowers.ENDERMITE, InsectTowers.ENHANCED_ENDERMITE.id(), 140);
        putUpgrade(upgrades, InsectTowers.CAVE_SPIDER, InsectTowers.SPIDER.id(), 90);
        putUpgrade(upgrades, InsectTowers.SPIDER, InsectTowers.ENHANCED_SPIDER.id(), 160);
        putUpgrade(upgrades, InsectTowers.BEE, InsectTowers.ENHANCED_BEE.id(), 90);
        putUpgrade(upgrades, InsectTowers.ENHANCED_BEE, InsectTowers.QUEEN_BEE.id(), 170);
    }

    private static void putFutureAgencyUpgrades(Map<String, Long> upgrades) {
        putUpgrade(upgrades, FutureAgencyTowers.ESCAPEE, FutureAgencyLeaderTower.RECONSTRUCT, 0);
        putUpgrade(upgrades, FutureAgencyTowers.REBUILDER, FutureAgencyLeaderTower.PROMOTE_COMMANDER, 800);
        for (TowerType leader : java.util.List.of(FutureAgencyTowers.REBUILDER, FutureAgencyTowers.COMMANDER)) {
            for (FutureAgencyPolicy policy : FutureAgencyPolicy.values()) {
                putUpgrade(upgrades, leader, policy.upgradeId(), 0);
            }
        }
        putUpgrade(upgrades, FutureAgencyTowers.REBUILDER, FutureAgencyLeaderTower.SAVE_WORLD, 1500);
        putUpgrade(upgrades, FutureAgencyTowers.COMMANDER, FutureAgencyLeaderTower.SAVE_WORLD, 1500);
        long[] costs = {100, 180, 360, 700};
        for (FutureAgencyRole role : FutureAgencyRole.values()) {
            for (int grade = 5; grade > 1; grade--) {
                putUpgrade(upgrades, FutureAgencyTowers.agent(role, grade),
                        FutureAgencyTowers.agent(role, grade - 1).id(), costs[5 - grade]);
            }
        }
    }

    private static void addHeroPartyTowers(Map<String, TowerStats> towers) {
        for (TowerType type : HeroPartyTowers.all()) {
            addTower(towers, type);
        }
    }

    private static void putHeroPartyUpgrades(Map<String, Long> upgrades) {
        for (HeroCompanionRole role : HeroCompanionRole.values()) {
            for (int tier = 1; tier < 4; tier++) {
                TowerType from = HeroPartyTowers.companion(role, tier);
                TowerType to = HeroPartyTowers.companion(role, tier + 1);
                putUpgrade(upgrades, from, to.id(), to.mineralCost());
            }
        }
    }

    private static void putEngineerAbilities(Map<String, Map<String, Double>> abilities) {
        LinkedHashMap<String, Double> global = new LinkedHashMap<>();
        global.put("activeTicks", (double) EngineerBalance.ACTIVE_TICKS);
        global.put("doorActiveTicks", (double) EngineerBalance.DOOR_ACTIVE_TICKS);
        global.put("plateCooldownTicks", (double) EngineerBalance.PLATE_COOLDOWN_TICKS);
        global.put("golemMoveSpeed", EngineerBalance.GOLEM_MOVE_SPEED);
        global.put("pistonImmunityTicks", (double) EngineerBalance.PISTON_IMMUNITY_TICKS);
        global.put("doorRetargetTicks", (double) EngineerBalance.DOOR_RETARGET_TICKS);
        global.put("tntFuseTicks", (double) EngineerBalance.TNT_FUSE_TICKS);
        global.put("maxRedstone", (double) EngineerBalance.MAX_REDSTONE);
        global.put("maxPlates", (double) EngineerBalance.MAX_PLATES);
        global.put("maxPistons", (double) EngineerBalance.MAX_PISTONS);
        global.put("plateDamageBonusPerTier", EngineerBalance.PLATE_DAMAGE_BONUS_PER_TIER);
        global.put("dispenserDamagePerPlateBlock", EngineerBalance.DISPENSER_DAMAGE_PER_PLATE_BLOCK);
        global.put("dispenserMaxPlateDistance", (double) EngineerBalance.DISPENSER_MAX_PLATE_DISTANCE);
        global.put("dispenserDamageBonusPerGolemPress", EngineerBalance.DISPENSER_DAMAGE_BONUS_PER_GOLEM_PRESS);
        global.put("dispenserDamageBonusCap", EngineerBalance.DISPENSER_DAMAGE_BONUS_CAP);
        global.put("doorDamageReductionPerGolemPress", EngineerBalance.DOOR_DAMAGE_REDUCTION_PER_GOLEM_PRESS);
        global.put("doorDamageReductionCap", EngineerBalance.DOOR_DAMAGE_REDUCTION_CAP);
        global.put("golemPressesPerExtraTarget", (double) EngineerBalance.GOLEM_PRESSES_PER_EXTRA_TARGET);
        global.put("tntExtraTargetCap", (double) EngineerBalance.TNT_EXTRA_TARGET_CAP);
        global.put("pistonExtraTargetCap", (double) EngineerBalance.PISTON_EXTRA_TARGET_CAP);
        global.put("slimeSlowPerGolemPress", EngineerBalance.SLIME_SLOW_PER_GOLEM_PRESS);
        global.put("slimeSlowCap", EngineerBalance.SLIME_SLOW_CAP);
        global.put("activeVfxIntervalTicks", (double) EngineerBalance.ACTIVE_VFX_INTERVAL_TICKS);
        global.put("tntFuseVfxIntervalTicks", (double) EngineerBalance.TNT_FUSE_VFX_INTERVAL_TICKS);
        putAbilities(abilities, EngineerBalance.GLOBAL_ID, global);
        putAbilities(abilities, EngineerTowers.REDSTONE_DUST.id(), Map.of(TowerCapacity.CONFIG_KEY, 0.0));
        EngineerTowers.repeaters().values().forEach(type ->
                putAbilities(abilities, type.id(), Map.of(TowerCapacity.CONFIG_KEY, 0.0)));
        for (EngineerTowers.TrapKind kind : EngineerTowers.TrapKind.values()) {
            for (int tier = 1; tier <= 3; tier++) {
                LinkedHashMap<String, Double> values = new LinkedHashMap<>();
                switch (kind) {
                    case DOOR -> values.put("radius", EngineerTrapTower.doorRadius(tier));
                    case TNT -> {
                        values.put("damage", EngineerTrapTower.tntDamage(tier));
                        values.put("radius", EngineerTrapTower.tntRadius(tier));
                        values.put("maxTargets", (double) EngineerTrapTower.tntMaxTargets(tier));
                    }
                    case DISPENSER -> {
                        values.put("damage", EngineerTrapTower.dispenserDamage(tier));
                        values.put("intervalTicks", (double) EngineerTrapTower.dispenserInterval(tier));
                        values.put("range", EngineerTrapTower.dispenserRange(tier));
                    }
                    case PISTON -> {
                        values.put("radius", EngineerTrapTower.pistonRadius(tier));
                        values.put("maxTargets", (double) EngineerTrapTower.pistonMaxTargets(tier));
                    }
                    case SLIME -> {
                        values.put("radius", EngineerTrapTower.slimeRadius(tier));
                        values.put("slow", EngineerTrapTower.slimeSlow(tier));
                    }
                }
                putAbilities(abilities, EngineerTowers.trap(kind, tier).id(), values);
            }
        }
    }

    private static void putInsectAbilities(Map<String, Map<String, Double>> abilities) {
        putAbilities(abilities, InsectBalance.GLOBAL_ID, Map.of(
                "freshPowerMultiplier", InsectBalance.FRESH_POWER_MULTIPLIER,
                "freshPowerScale", InsectBalance.FRESH_POWER_SCALE,
                "reviveBaseTicks", (double) InsectBalance.REVIVE_BASE_TICKS,
                "reviveIncrementTicks", (double) InsectBalance.REVIVE_INCREMENT_TICKS,
                "radiusVfxIntervalTicks", (double) InsectBalance.RADIUS_VFX_INTERVAL_TICKS,
                "deathDamageTakenPerStack", InsectBalance.DEATH_DAMAGE_TAKEN_PER_STACK
        ));
        putAbilities(abilities, InsectTowers.SPAWNER.id(), Map.of(
                "reviveRadius", InsectBalance.SPAWNER_RADIUS
        ));
        for (int tier = 1; tier <= 3; tier++) {
            putAbilities(abilities, InsectTowers.spider(tier).id(), Map.of(
                    "damageReduction", switch (tier) {
                        case 1 -> 0.08;
                        case 2 -> 0.16;
                        default -> 0.25;
                    }
            ));
        }
    }

    private static void putHeroPartyAbilities(Map<String, Map<String, Double>> abilities) {
        putAbilities(abilities, HeroPartyBalance.GLOBAL_CONFIG_ID, Map.ofEntries(
                Map.entry("weaponUpgradeCost1", 80.0),
                Map.entry("weaponUpgradeCost2", 140.0),
                Map.entry("weaponUpgradeCost3", 220.0),
                Map.entry("weaponUpgradeCost4", 320.0),
                Map.entry("weaponUpgradeCost5", 450.0),
                Map.entry("weaponMultiplier1", 1.15),
                Map.entry("weaponMultiplier2", 1.32),
                Map.entry("weaponMultiplier3", 1.50),
                Map.entry("weaponMultiplier4", 1.72),
                Map.entry("weaponMultiplier5", 2.00),
                Map.entry("weaponAttackIntervalReductionPerLevel",
                        (double) HeroPartyBalance.WEAPON_ATTACK_INTERVAL_REDUCTION_PER_LEVEL),
                Map.entry("armorUpgradeCost1", 126.0),
                Map.entry("armorUpgradeCost2", 210.0),
                Map.entry("armorUpgradeCost3", 322.0),
                Map.entry("armorUpgradeCost4", 476.0),
                Map.entry("armorUpgradeCost5", 672.0),
                Map.entry("armorHealth1", 60.0),
                Map.entry("armorHealth2", 140.0),
                Map.entry("armorHealth3", 240.0),
                Map.entry("armorHealth4", 380.0),
                Map.entry("armorHealth5", 560.0),
                Map.entry("armorReduction1", 0.04),
                Map.entry("armorReduction2", 0.08),
                Map.entry("armorReduction3", 0.12),
                Map.entry("armorReduction4", 0.16),
                Map.entry("armorReduction5", 0.20),
                Map.entry("adventureDamagePerPoint", 0.0030),
                Map.entry("adventureHealingPerPoint", 0.0030),
                Map.entry("adventureHealthPerPoint", 0.0045),
                Map.entry(
                        "focusFireDamageReductionPerExtraAttacker",
                        HeroPartyBalance.FOCUS_FIRE_REDUCTION_PER_EXTRA_ATTACKER
                ),
                Map.entry("focusFireDamageReductionCap", HeroPartyBalance.FOCUS_FIRE_REDUCTION_CAP)
        ));
        for (HeroWeapon weapon : HeroWeapon.values()) {
            putAbilities(abilities, weapon.configId(), Map.of(
                    "purchaseCost", (double) weapon.defaultPurchaseCost(),
                    "damage", weapon.defaultDamage(),
                    "range", weapon.defaultRange(),
                    "attackIntervalTicks", (double) weapon.defaultAttackIntervalTicks(),
                    "maxHealthMultiplier", weapon.defaultMaxHealthMultiplier(),
                    "aggroPriority", (double) weapon.defaultAggroPriority()
            ));
            if (weapon == HeroWeapon.SWORD || weapon == HeroWeapon.LONGBOW) {
                mergeAbilities(abilities, weapon.configId(), Map.of(
                        "incomeDamageBonus", HeroPartyBalance.INCOME_DAMAGE_BONUS
                ));
            }
        }
        putAbilities(abilities, HeroPartyTowers.HERO.id(), Map.of("towerSlotCost", 3.0));
        for (HeroCompanionRole role : HeroCompanionRole.values()) {
            for (int tier = 1; tier <= 4; tier++) {
                putAbilities(abilities, HeroPartyTowers.companion(role, tier).id(), Map.of(
                        "towerSlotCost", (double) (tier + 1)
                ));
            }
        }
        putHeroCompanionAbilities(abilities);
    }

    private static void putHeroCompanionAbilities(Map<String, Map<String, Double>> abilities) {
        double[] knightReduction = {0.0, 0.07, 0.13, 0.20};
        double[] knightBashEvery = {0.0, 4.0, 4.0, 3.0};
        double[] knightBashSlow = {0.0, 0.25, 0.25, 0.35};
        double[] knightBashTicks = {0.0, 40.0, 40.0, 60.0};
        double[] knightGuardRadius = {0.0, 0.0, 5.0, 6.0};
        double[] knightGuardReduction = {0.0, 0.0, 0.08, 0.12};
        double[] knightGuardTicks = {0.0, 0.0, 40.0, 40.0};
        double[] archerBoss = {0.0, 0.12, 0.23, 0.35};
        double[] archerPierceEvery = {0.0, 4.0, 4.0, 3.0};
        double[] archerPierceRatio = {0.0, 0.60, 0.60, 0.75};
        double[] archerMarkBonus = {0.0, 0.0, 0.12, 0.15};
        double[] archerMarkTicks = {0.0, 0.0, 60.0, 80.0};
        double[] mageRatio = {0.30, 0.40, 0.50, 0.60};
        double[] mageRadius = {2.0, 2.3, 2.6, 3.0};
        double[] mageSlow = {0.0, 0.20, 0.20, 0.30};
        double[] mageSlowTicks = {0.0, 40.0, 40.0, 60.0};
        double[] mageEmpoweredEvery = {0.0, 0.0, 5.0, 4.0};
        double[] mageEmpoweredMultiplier = {0.0, 0.0, 1.50, 1.75};
        double[] mageEmpoweredRadius = {0.0, 0.0, 0.50, 0.75};
        double[] priestHeal = {28.0, 42.0, 62.0, 90.0};
        double[] priestInterval = {40.0, 38.0, 34.0, 30.0};
        double[] priestSecond = {0.0, 0.0, 0.50, 1.0};
        double[] priestGuard = {0.0, 0.08, 0.10, 0.15};
        double[] priestGuardTicks = {0.0, 60.0, 60.0, 60.0};
        double[] rogueExecute = {0.25, 0.35, 0.47, 0.60};
        double[] rogueComboEvery = {0.0, 4.0, 4.0, 3.0};
        double[] rogueComboRatio = {0.0, 0.40, 0.40, 0.60};
        double[] rogueHaste = {0.0, 0.0, 0.20, 0.30};
        double[] rogueHasteTicks = {0.0, 0.0, 60.0, 80.0};
        double[] bardSpeed = {0.08, 0.11, 0.14, 0.18};
        double[] bardDamage = {0.0, 0.03, 0.06, 0.10};
        double[] bardRadius = {8.0, 9.0, 10.0, 12.0};
        double[] bardEncoreEvery = {0.0, 0.0, 5.0, 4.0};
        double[] bardEncoreBonus = {0.0, 0.0, 0.10, 0.15};
        double[] bardEncoreTicks = {0.0, 0.0, 40.0, 40.0};
        for (int index = 0; index < 4; index++) {
            int tier = index + 1;
            mergeAbilities(abilities, HeroPartyTowers.companion(HeroCompanionRole.KNIGHT, tier).id(), Map.of(
                    "damageReduction", knightReduction[index],
                    "shieldBashEvery", knightBashEvery[index],
                    "shieldBashSlow", knightBashSlow[index],
                    "shieldBashDurationTicks", knightBashTicks[index],
                    "guardRadius", knightGuardRadius[index],
                    "guardDamageReduction", knightGuardReduction[index],
                    "guardDurationTicks", knightGuardTicks[index]
            ));
            mergeAbilities(abilities, HeroPartyTowers.companion(HeroCompanionRole.ARCHER, tier).id(), Map.of(
                    "bossDamageBonus", archerBoss[index],
                    "incomeDamageBonus", HeroPartyBalance.INCOME_DAMAGE_BONUS,
                    "pierceEvery", archerPierceEvery[index],
                    "pierceDamageRatio", archerPierceRatio[index],
                    "markDamageBonus", archerMarkBonus[index],
                    "markDurationTicks", archerMarkTicks[index]
            ));
            mergeAbilities(abilities, HeroPartyTowers.companion(HeroCompanionRole.MAGE, tier).id(), Map.of(
                    "splashDamageRatio", mageRatio[index],
                    "splashRadius", mageRadius[index],
                    "splashSlow", mageSlow[index],
                    "splashSlowDurationTicks", mageSlowTicks[index],
                    "empoweredEvery", mageEmpoweredEvery[index],
                    "empoweredSplashMultiplier", mageEmpoweredMultiplier[index],
                    "empoweredRadiusBonus", mageEmpoweredRadius[index]
            ));
            mergeAbilities(abilities, HeroPartyTowers.companion(HeroCompanionRole.PRIEST, tier).id(), Map.of(
                    "healAmount", priestHeal[index],
                    "healIntervalTicks", priestInterval[index],
                    "secondTargetRatio", priestSecond[index],
                    "healGuardReduction", priestGuard[index],
                    "healGuardDurationTicks", priestGuardTicks[index]
            ));
            mergeAbilities(abilities, HeroPartyTowers.companion(HeroCompanionRole.ROGUE, tier).id(), Map.of(
                    "executeThreshold", 0.30,
                    "executeDamageBonus", rogueExecute[index],
                    "comboEvery", rogueComboEvery[index],
                    "comboDamageRatio", rogueComboRatio[index],
                    "killAttackSpeedBonus", rogueHaste[index],
                    "killAttackSpeedDurationTicks", rogueHasteTicks[index]
            ));
            mergeAbilities(abilities, HeroPartyTowers.companion(HeroCompanionRole.BARD, tier).id(), Map.of(
                    "attackSpeedBonus", bardSpeed[index],
                    "damageBonus", bardDamage[index],
                    "auraRadius", bardRadius[index],
                    "encoreEveryPulses", bardEncoreEvery[index],
                    "encoreAttackSpeedBonus", bardEncoreBonus[index],
                    "encoreDamageBonus", bardEncoreBonus[index],
                    "encoreDurationTicks", bardEncoreTicks[index]
            ));
        }
    }

    private static void putFutureAgencyAbilities(Map<String, Map<String, Double>> abilities) {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>();
        values.put("rebuilderDamageBonus", FutureAgencyBalance.REBUILDER_DAMAGE);
        values.put("rebuilderMaxHealthBonus", FutureAgencyBalance.REBUILDER_HEALTH);
        values.put("commanderDamageBonus", FutureAgencyBalance.COMMANDER_DAMAGE);
        values.put("commanderMaxHealthBonus", FutureAgencyBalance.COMMANDER_HEALTH);
        values.put("commanderAttackSpeedBonus", FutureAgencyBalance.COMMANDER_ATTACK_SPEED);
        values.put("survivorDamagePerCopy", FutureAgencyBalance.SURVIVOR_DAMAGE_PER_COPY);
        values.put("escapeeSurvivorDamageMultiplier", FutureAgencyBalance.ESCAPEE_SURVIVOR_MULTIPLIER);
        values.put("rebuilderSurvivorDamageMultiplier", FutureAgencyBalance.REBUILDER_SURVIVOR_MULTIPLIER);
        values.put("commanderSurvivorDamageMultiplier", FutureAgencyBalance.COMMANDER_SURVIVOR_MULTIPLIER);
        values.put("survivorDamageCap", FutureAgencyBalance.SURVIVOR_DAMAGE_CAP);
        values.put("damageReductionCap", FutureAgencyBalance.DAMAGE_REDUCTION_CAP);
        values.put("slowCap", FutureAgencyBalance.SLOW_CAP);
        values.put("suppressionDenseCap", FutureAgencyBalance.SUPPRESSION_DENSE_CAP);
        values.put("suppressionDenseRadius", FutureAgencyBalance.SUPPRESSION_DENSE_RADIUS);
        values.put("escortRadius", FutureAgencyBalance.ESCORT_RADIUS);
        for (FutureAgencyPolicy policy : FutureAgencyPolicy.values()) {
            values.put("policy." + policy.id(), policy.defaultValue());
        }
        putAbilities(abilities, FutureAgencyBalance.GLOBAL_ID, values);
        double[] suppressionRadius = {1.25, 1.5, 1.75, 2.0, 2.5};
        double[] suppressionTargets = {3, 4, 5, 6, 7};
        double[] suppressionRatio = {.40, .45, .50, .55, .60};
        double[] suppressionSlow = {.08, .12, .16, .20, .25};
        double[] protectionReduction = {.08, .12, .16, .20, .25};
        for (int index = 0; index < 5; index++) {
            int grade = 5 - index;
            putAbilities(abilities, FutureAgencyTowers.agent(FutureAgencyRole.SUPPRESSION, grade).id(), Map.of(
                    "suppressionRadius", suppressionRadius[index],
                    "suppressionMaxTargets", suppressionTargets[index],
                    "suppressionDamageRatio", suppressionRatio[index],
                    "suppressionSlow", suppressionSlow[index]
            ));
            putAbilities(abilities, FutureAgencyTowers.agent(FutureAgencyRole.PROTECTION, grade).id(), Map.of(
                    "damageReduction", protectionReduction[index]
            ));
        }
    }

    private static void putQueenAbilities(Map<String, Map<String, Double>> abilities) {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>();
        values.put("shrinkFactorPerPoint", 0.98);
        values.put("minimumStatScale", 0.20);
        values.put("minimumVisualScale", 0.50);
        values.put("queenShrinkPoints", 7.0);
        values.put("cardShrinkPoints", 0.75);
        values.put("cardDeathShrinkPoints", 1.5);
        values.put("cardDeathRadius", 3.0);
        values.put("heartHealIntervalTicks", 60.0);
        values.put("heartHealAmount", 12.0);
        values.put("heartHealRadius", 5.0);
        values.put("clubDamageReduction", 0.15);
        values.put("cardSplashRadius", 2.0);
        values.put("cardSplashExtraTargets", 5.0);
        values.put("spadeRadius", 2.5);
        values.put("spadeExtraTargets", 5.0);
        values.put("giantChargeTicks", 400.0);
        values.put("giantAccelerationRadius", 6.0);
        values.put("giantAccelerationMemoryTicks", 40.0);
        values.put("giantExecutionVisualShrink", 0.20);
        values.put("giantInitialExecutionHealth", 5.0);
        values.put("giantExecutionGrowthRatio", 0.05);
        values.put("giantGrowthTargetCapMultiplier", 2.0);
        values.put("queenMaxHealthPerRound", 8.0);
        values.put("queenPokerHealthBonusCap", 3.0);
        values.put("giantContactRadius", 4.0);
        values.put("giantSpeed", 0.65);
        values.put("giantSlow", 0.55);
        values.put("giantSlowTicks", 40.0);
        values.put("rangeVfxIntervalTicks", 80.0);
        values.put("card.heart.maxHealth", 60.0);
        values.put("card.heart.range", 6.0);
        values.put("card.heart.intervalTicks", 20.0);
        values.put("card.heart.aggro", 55.0);
        values.put("card.diamond.maxHealth", 45.0);
        values.put("card.diamond.range", 8.0);
        values.put("card.diamond.intervalTicks", 10.0);
        values.put("card.diamond.aggro", 45.0);
        values.put("card.club.maxHealth", 125.0);
        values.put("card.club.range", 2.5);
        values.put("card.club.intervalTicks", 24.0);
        values.put("card.club.aggro", 110.0);
        values.put("card.spade.maxHealth", 75.0);
        values.put("card.spade.range", 3.0);
        values.put("card.spade.intervalTicks", 18.0);
        values.put("card.spade.aggro", 80.0);
        for (PokerHand hand : PokerHand.values()) {
            values.put("hand." + hand.name().toLowerCase(), hand.defaultBonus());
        }
        putAbilities(abilities, QueenBalance.GLOBAL_ID, values);
    }

    private void validateQueenBalance() {
        Map<String, Double> values = abilities.get(QueenBalance.GLOBAL_ID);
        if (values == null) return;
        values.forEach((key, value) -> {
            if (value == null || !Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException("Queen balance must be finite and non-negative: " + key);
            }
        });
        double shrinkFactor = values.getOrDefault("shrinkFactorPerPoint", 0.0);
        if (shrinkFactor <= 0.0 || shrinkFactor >= 1.0) {
            throw new IllegalArgumentException("Queen shrinkFactorPerPoint must be between 0 and 1 (exclusive).");
        }
        double minimumVisualScale = values.getOrDefault("minimumVisualScale", 0.0);
        if (minimumVisualScale <= 0.0 || minimumVisualScale > 1.0) {
            throw new IllegalArgumentException("Queen minimumVisualScale must be between 0 (exclusive) and 1.");
        }
        double minimumStatScale = values.getOrDefault("minimumStatScale", 0.0);
        if (minimumStatScale <= 0.0 || minimumStatScale > 1.0) {
            throw new IllegalArgumentException("Queen minimumStatScale must be between 0 (exclusive) and 1.");
        }
        for (String key : java.util.List.of(
                "clubDamageReduction", "giantExecutionVisualShrink", "giantExecutionGrowthRatio", "giantSlow")) {
            double value = values.getOrDefault(key, -1.0);
            if (value < 0.0 || value > 1.0) throw new IllegalArgumentException("Queen ratio must be between 0 and 1: " + key);
        }
        for (String key : java.util.List.of("heartHealIntervalTicks", "cardSplashExtraTargets", "spadeExtraTargets", "giantChargeTicks",
                "giantAccelerationMemoryTicks", "giantSlowTicks", "rangeVfxIntervalTicks", "card.heart.intervalTicks",
                "card.diamond.intervalTicks", "card.club.intervalTicks", "card.spade.intervalTicks",
                "card.heart.aggro", "card.diamond.aggro", "card.club.aggro", "card.spade.aggro")) {
            double value = values.getOrDefault(key, 0.0);
            if (value <= 0.0 || value != Math.rint(value)) {
                throw new IllegalArgumentException("Queen integer must be positive: " + key);
            }
        }
        for (String key : java.util.List.of("queenShrinkPoints", "cardShrinkPoints", "cardDeathShrinkPoints",
                "cardDeathRadius", "heartHealAmount", "heartHealRadius", "cardSplashRadius", "spadeRadius", "giantAccelerationRadius",
                "giantInitialExecutionHealth", "giantGrowthTargetCapMultiplier", "queenMaxHealthPerRound", "queenPokerHealthBonusCap", "giantContactRadius", "giantSpeed",
                "card.heart.maxHealth", "card.heart.range", "card.diamond.maxHealth", "card.diamond.range",
                "card.club.maxHealth", "card.club.range", "card.spade.maxHealth", "card.spade.range")) {
            if (values.getOrDefault(key, 0.0) <= 0.0) {
                throw new IllegalArgumentException("Queen value must be positive: " + key);
            }
        }
        double previousHandBonus = -1.0;
        for (PokerHand hand : PokerHand.values()) {
            String key = "hand." + hand.name().toLowerCase();
            double value = values.getOrDefault(key, -1.0);
            if (value < 0.0 || value > 1.0 || value < previousHandBonus) {
                throw new IllegalArgumentException("Queen poker bonuses must be ordered ratios: " + key);
            }
            previousHandBonus = value;
        }
        if (values.getOrDefault("spadeRadius", 0.0) < values.getOrDefault("cardSplashRadius", 0.0)
                || values.getOrDefault("spadeExtraTargets", 0.0) < values.getOrDefault("cardSplashExtraTargets", 0.0)) {
            throw new IllegalArgumentException("Queen spade splash must not be weaker than the common card splash.");
        }
    }

    private void validateHeroPartyBalance() {
        for (HeroWeapon weapon : HeroWeapon.values()) {
            validateRatios(weapon.configId(), "incomeDamageBonus");
            validatePositive(weapon.configId(), "maxHealthMultiplier");
            validateRange(weapon.configId(), "aggroPriority", -100.0, 100.0);
            Double aggroPriority = configuredAbility(weapon.configId(), "aggroPriority");
            if (aggroPriority != null && aggroPriority != Math.rint(aggroPriority)) {
                throw new IllegalArgumentException(
                        "Hero Party weapon aggro priority must be an integer: " + weapon.configId()
                );
            }
        }
        validateIntegral(HeroPartyBalance.GLOBAL_CONFIG_ID, false,
                "weaponAttackIntervalReductionPerLevel");
        for (int tier = 1; tier <= 4; tier++) {
            String knight = HeroPartyTowers.companion(HeroCompanionRole.KNIGHT, tier).id();
            validateRatios(knight, "damageReduction", "shieldBashSlow", "guardDamageReduction");
            validateIntegral(knight, true, "shieldBashEvery", "shieldBashDurationTicks", "guardDurationTicks");
            validateRange(knight, "guardRadius", 0.0, 96.0);

            String archer = HeroPartyTowers.companion(HeroCompanionRole.ARCHER, tier).id();
            validateRatios(archer,
                    "bossDamageBonus", "incomeDamageBonus", "pierceDamageRatio", "markDamageBonus");
            validateIntegral(archer, true, "pierceEvery", "markDurationTicks");

            String mage = HeroPartyTowers.companion(HeroCompanionRole.MAGE, tier).id();
            validateRatios(mage, "splashDamageRatio", "splashSlow");
            validateIntegral(mage, true, "splashSlowDurationTicks", "empoweredEvery");
            validateRange(mage, "empoweredSplashMultiplier", 0.0, 4.0);
            validateRange(mage, "empoweredRadiusBonus", 0.0, 16.0);
            if (tier >= 3) {
                validateAtLeast(mage, 1.0, "empoweredSplashMultiplier");
            }

            String priest = HeroPartyTowers.companion(HeroCompanionRole.PRIEST, tier).id();
            validateRatios(priest, "secondTargetRatio", "healGuardReduction");
            validateIntegral(priest, true, "healGuardDurationTicks");

            String rogue = HeroPartyTowers.companion(HeroCompanionRole.ROGUE, tier).id();
            validateRatios(rogue,
                    "executeThreshold", "executeDamageBonus", "comboDamageRatio", "killAttackSpeedBonus");
            validateIntegral(rogue, true, "comboEvery", "killAttackSpeedDurationTicks");

            String bard = HeroPartyTowers.companion(HeroCompanionRole.BARD, tier).id();
            validateRatios(bard,
                    "attackSpeedBonus", "damageBonus", "encoreAttackSpeedBonus", "encoreDamageBonus");
            validateIntegral(bard, true, "encoreEveryPulses", "encoreDurationTicks");
            validateRange(bard, "auraRadius", 0.0, 96.0);
        }
        validateHeroPartyTierFour();
        Map<String, Double> values = abilities.get(HeroPartyBalance.GLOBAL_CONFIG_ID);
        if (values == null) {
            return;
        }
        Double perExtraAttacker = values.get("focusFireDamageReductionPerExtraAttacker");
        Double cap = values.get("focusFireDamageReductionCap");
        if (perExtraAttacker != null && perExtraAttacker >= 1.0) {
            throw new IllegalArgumentException("Hero Party focus-fire reduction must be in [0, 1).");
        }
        if (cap != null && cap >= 1.0) {
            throw new IllegalArgumentException("Hero Party focus-fire reduction cap must be in [0, 1).");
        }
        if (perExtraAttacker != null && cap != null && perExtraAttacker > cap) {
            throw new IllegalArgumentException("Hero Party focus-fire reduction must not exceed its cap.");
        }
    }

    private void validateHeroPartyTierFour() {
        validateTierFourAtLeast(HeroCompanionRole.KNIGHT,
                "shieldBashSlow", "shieldBashDurationTicks", "guardRadius", "guardDamageReduction",
                "guardDurationTicks");
        validateTierFourAtMost(HeroCompanionRole.KNIGHT, "shieldBashEvery");
        validateTierFourAtLeast(HeroCompanionRole.ARCHER,
                "pierceDamageRatio", "markDamageBonus", "markDurationTicks");
        validateTierFourAtMost(HeroCompanionRole.ARCHER, "pierceEvery");
        validateTierFourAtLeast(HeroCompanionRole.MAGE,
                "splashDamageRatio", "splashRadius", "splashSlow", "splashSlowDurationTicks",
                "empoweredSplashMultiplier", "empoweredRadiusBonus");
        validateTierFourAtMost(HeroCompanionRole.MAGE, "empoweredEvery");
        validateTierFourAtLeast(HeroCompanionRole.PRIEST,
                "secondTargetRatio", "healGuardReduction", "healGuardDurationTicks");
        validateTierFourAtLeast(HeroCompanionRole.ROGUE,
                "comboDamageRatio", "killAttackSpeedBonus", "killAttackSpeedDurationTicks");
        validateTierFourAtMost(HeroCompanionRole.ROGUE, "comboEvery");
        validateTierFourAtLeast(HeroCompanionRole.BARD,
                "attackSpeedBonus", "damageBonus", "auraRadius",
                "encoreAttackSpeedBonus", "encoreDamageBonus", "encoreDurationTicks");
        validateTierFourAtMost(HeroCompanionRole.BARD, "encoreEveryPulses");
    }

    private void validateTierFourAtLeast(HeroCompanionRole role, String... keys) {
        for (String key : keys) {
            Double tierThree = configuredAbility(HeroPartyTowers.companion(role, 3).id(), key);
            Double tierFour = configuredAbility(HeroPartyTowers.companion(role, 4).id(), key);
            if (tierThree != null && tierFour != null && tierFour < tierThree) {
                throw new IllegalArgumentException("Hero Party T4 ability must not be weaker than T3: "
                        + role.id() + "." + key);
            }
        }
    }

    private void validateTierFourAtMost(HeroCompanionRole role, String... keys) {
        for (String key : keys) {
            Double tierThree = configuredAbility(HeroPartyTowers.companion(role, 3).id(), key);
            Double tierFour = configuredAbility(HeroPartyTowers.companion(role, 4).id(), key);
            if (tierThree != null && tierFour != null
                    && (tierFour <= 0.0 || tierFour > tierThree)) {
                throw new IllegalArgumentException("Hero Party T4 trigger must be at least as frequent as T3: "
                        + role.id() + "." + key);
            }
        }
    }

    private void validateFutureAgencyBalance() {
        Map<String, Double> values = abilities.get(FutureAgencyBalance.GLOBAL_ID);
        if (values == null) return;
        values.forEach((key, value) -> {
            if (value == null || !Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException("Future agency balance must be finite and non-negative: " + key);
            }
        });
        for (String key : java.util.List.of(
                "rebuilderDamageBonus", "rebuilderMaxHealthBonus", "commanderDamageBonus",
                "commanderMaxHealthBonus", "commanderAttackSpeedBonus", "damageReductionCap",
                "slowCap", "suppressionDenseCap", "survivorDamagePerCopy")) {
            double value = values.getOrDefault(key, 0.0);
            if (value < 0.0 || value > 1.0) {
                throw new IllegalArgumentException("Future agency ratio must be between 0 and 1: " + key);
            }
        }
        for (FutureAgencyPolicy policy : FutureAgencyPolicy.values()) {
            if (policy == FutureAgencyPolicy.LONG_RANGE_OPTICS
                    || policy == FutureAgencyPolicy.AREA_SUPPRESSION
                    || policy == FutureAgencyPolicy.MULTI_TARGET
                    || policy == FutureAgencyPolicy.FORCED_TAUNT) continue;
            double value = values.getOrDefault("policy." + policy.id(), -1.0);
            if (value < 0.0 || value > 1.0) {
                throw new IllegalArgumentException("Future agency policy ratio must be between 0 and 1: " + policy.id());
            }
        }
        if (values.getOrDefault("escortRadius", 0.0) <= 0.0
                || values.getOrDefault("suppressionDenseRadius", 0.0) <= 0.0) {
            throw new IllegalArgumentException("Future agency radii must be positive.");
        }
        if (values.getOrDefault("policy.dense_control", 0.0)
                > values.getOrDefault("suppressionDenseCap", 0.0)) {
            throw new IllegalArgumentException("Future agency dense-control bonus exceeds its cap.");
        }
        for (int grade = 5; grade >= 1; grade--) {
            Map<String, Double> suppression = abilities.get(
                    FutureAgencyTowers.agent(FutureAgencyRole.SUPPRESSION, grade).id());
            if (suppression != null) {
                double targetCount = suppression.getOrDefault("suppressionMaxTargets", 0.0);
                if (targetCount <= 0.0 || targetCount != Math.rint(targetCount)) {
                    throw new IllegalArgumentException("Future agency suppression target count must be a positive integer.");
                }
                double ratio = suppression.getOrDefault("suppressionDamageRatio", 0.0);
                double slow = suppression.getOrDefault("suppressionSlow", 0.0);
                if (suppression.getOrDefault("suppressionRadius", 0.0) <= 0.0
                        || ratio <= 0.0 || ratio > 1.0 || slow < 0.0 || slow > 1.0
                        || slow > values.getOrDefault("slowCap", 0.0)) {
                    throw new IllegalArgumentException("Future agency suppression values are invalid.");
                }
            }
            Map<String, Double> protection = abilities.get(
                    FutureAgencyTowers.agent(FutureAgencyRole.PROTECTION, grade).id());
            if (protection != null) {
                double reduction = protection.getOrDefault("damageReduction", -1.0);
                if (reduction < 0.0 || reduction > 1.0
                        || reduction > values.getOrDefault("damageReductionCap", 0.0)) {
                    throw new IllegalArgumentException("Future agency protection reduction must be between 0 and 1.");
                }
            }
        }
    }

    private void validateEngineerBalance() {
        Map<String, Double> global = abilities.get(EngineerBalance.GLOBAL_ID);
        if (global == null) {
            return;
        }
        validateEngineerValues(EngineerBalance.GLOBAL_ID, global);
        requireEngineerPositive(global,
                "activeTicks", "doorActiveTicks", "plateCooldownTicks", "golemMoveSpeed", "pistonImmunityTicks",
                "doorRetargetTicks", "tntFuseTicks", "maxRedstone", "maxPlates", "maxPistons",
                "dispenserMaxPlateDistance", "golemPressesPerExtraTarget",
                "activeVfxIntervalTicks", "tntFuseVfxIntervalTicks");
        requireEngineerIntegral(global,
                "activeTicks", "doorActiveTicks", "plateCooldownTicks", "pistonImmunityTicks", "doorRetargetTicks", "tntFuseTicks",
                "maxRedstone", "maxPlates", "maxPistons", "dispenserMaxPlateDistance",
                "golemPressesPerExtraTarget", "tntExtraTargetCap", "pistonExtraTargetCap",
                "activeVfxIntervalTicks", "tntFuseVfxIntervalTicks");
        double distanceBonus = global.getOrDefault("dispenserDamagePerPlateBlock", -1.0);
        if (distanceBonus < 0.0 || distanceBonus > 1.0) {
            throw new IllegalArgumentException("Engineer dispenser distance bonus must be in [0, 1].");
        }
        double plateBonus = global.getOrDefault("plateDamageBonusPerTier", -1.0);
        if (plateBonus < 0.0 || plateBonus > 1.0) {
            throw new IllegalArgumentException("Engineer plate damage bonus must be in [0, 1].");
        }
        requireEngineerRatio(global, "dispenserDamageBonusPerGolemPress", true);
        requireEngineerRatio(global, "doorDamageReductionPerGolemPress", true);
        requireEngineerRatio(global, "doorDamageReductionCap", false);
        requireEngineerRatio(global, "slimeSlowPerGolemPress", true);
        requireEngineerRatio(global, "slimeSlowCap", false);
        if (global.get("doorDamageReductionPerGolemPress") > global.get("doorDamageReductionCap")) {
            throw new IllegalArgumentException("Engineer door damage reduction per press must not exceed its cap.");
        }
        if (global.get("slimeSlowPerGolemPress") > global.get("slimeSlowCap")) {
            throw new IllegalArgumentException("Engineer slime slow per press must not exceed its cap.");
        }
        if (global.getOrDefault("dispenserMaxPlateDistance", 0.0)
                > global.getOrDefault("maxRedstone", 0.0)) {
            throw new IllegalArgumentException("Engineer dispenser distance cap must not exceed maxRedstone.");
        }
        validateIntegral(EngineerTowers.REDSTONE_DUST.id(), true, TowerCapacity.CONFIG_KEY);
        EngineerTowers.repeaters().values().forEach(type ->
                validateIntegral(type.id(), true, TowerCapacity.CONFIG_KEY));
        for (EngineerTowers.TrapKind kind : EngineerTowers.TrapKind.values()) {
            for (int tier = 1; tier <= 3; tier++) {
                String id = EngineerTowers.trap(kind, tier).id();
                Map<String, Double> values = abilities.get(id);
                if (values == null) {
                    continue;
                }
                validateEngineerValues(id, values);
                values.forEach((key, value) -> {
                    if (!key.equals("slow") && value <= 0.0) {
                        throw new IllegalArgumentException("Engineer balance ability must be positive: " + id + "." + key);
                    }
                });
                if (values.containsKey("maxTargets")) {
                    requireEngineerIntegral(values, "maxTargets");
                }
                if (values.containsKey("intervalTicks")) {
                    requireEngineerIntegral(values, "intervalTicks");
                }
                if (values.containsKey("slow") && values.get("slow") >= 1.0) {
                    throw new IllegalArgumentException("Engineer slow ratio must be in [0, 1): " + id);
                }
                if (kind == EngineerTowers.TrapKind.SLIME
                        && values.getOrDefault("slow", 0.0) > global.get("slimeSlowCap")) {
                    throw new IllegalArgumentException("Engineer slime slow cap must cover every configured tier.");
                }
            }
        }
        validateEngineerTierOrder(EngineerTowers.TrapKind.DOOR, "radius", true);
        validateEngineerTierOrder(EngineerTowers.TrapKind.TNT, "damage", true);
        validateEngineerTierOrder(EngineerTowers.TrapKind.TNT, "radius", true);
        validateEngineerTierOrder(EngineerTowers.TrapKind.TNT, "maxTargets", true);
        validateEngineerTierOrder(EngineerTowers.TrapKind.DISPENSER, "damage", true);
        validateEngineerTierOrder(EngineerTowers.TrapKind.DISPENSER, "range", true);
        validateEngineerTierOrder(EngineerTowers.TrapKind.DISPENSER, "intervalTicks", false);
        validateEngineerTierOrder(EngineerTowers.TrapKind.PISTON, "radius", true);
        validateEngineerTierOrder(EngineerTowers.TrapKind.PISTON, "maxTargets", true);
        validateEngineerTierOrder(EngineerTowers.TrapKind.SLIME, "radius", true);
        validateEngineerTierOrder(EngineerTowers.TrapKind.SLIME, "slow", true);

        Double previousHealth = null;
        for (int tier = 1; tier <= 3; tier++) {
            TowerStats stats = towers.get(EngineerTowers.trap(EngineerTowers.TrapKind.DOOR, tier).id());
            if (stats != null && stats.maxHealth() != null) {
                if (previousHealth != null && stats.maxHealth() < previousHealth) {
                    throw new IllegalArgumentException("Engineer door health must not decrease by tier.");
                }
                previousHealth = stats.maxHealth();
            }
        }
    }

    private static void requireEngineerRatio(Map<String, Double> values, String key, boolean inclusiveOne) {
        double value = values.getOrDefault(key, -1.0);
        if (value < 0.0 || (inclusiveOne ? value > 1.0 : value >= 1.0)) {
            throw new IllegalArgumentException("Engineer ratio is out of range: " + key);
        }
    }

    private void validateEngineerTierOrder(EngineerTowers.TrapKind kind, String key, boolean nondecreasing) {
        Double previous = null;
        for (int tier = 1; tier <= 3; tier++) {
            Double current = configuredAbility(EngineerTowers.trap(kind, tier).id(), key);
            if (current == null) {
                continue;
            }
            if (previous != null && (nondecreasing ? current < previous : current > previous)) {
                throw new IllegalArgumentException("Engineer " + kind + "." + key + " tier order is invalid.");
            }
            previous = current;
        }
    }

    private void validateInsectBalance() {
        Map<String, Double> global = abilities.get(InsectBalance.GLOBAL_ID);
        if (global != null) {
            validateEngineerValues(InsectBalance.GLOBAL_ID, global);
            requireEngineerPositive(global,
                    "freshPowerMultiplier", "freshPowerScale", "reviveBaseTicks", "reviveIncrementTicks",
                    "radiusVfxIntervalTicks");
            requireEngineerIntegral(global, "reviveBaseTicks", "reviveIncrementTicks", "radiusVfxIntervalTicks");
            double power = global.getOrDefault("freshPowerMultiplier", 0.0);
            double scale = global.getOrDefault("freshPowerScale", 0.0);
            double vulnerability = global.getOrDefault("deathDamageTakenPerStack", -1.0);
            if (power < 1.0 || scale < 1.0 || scale > 1.25 || vulnerability < 0.0 || vulnerability > 1.0) {
                throw new IllegalArgumentException("Insect global multipliers are outside their supported range.");
            }
        }
        Map<String, Double> spawner = abilities.get(InsectTowers.SPAWNER.id());
        if (spawner != null) {
            validateEngineerValues(InsectTowers.SPAWNER.id(), spawner);
            requireEngineerPositive(spawner, "reviveRadius");
        }
        Double previousReduction = null;
        for (int tier = 1; tier <= 3; tier++) {
            String id = InsectTowers.spider(tier).id();
            Map<String, Double> values = abilities.get(id);
            if (values == null) {
                continue;
            }
            double reduction = values.getOrDefault("damageReduction", -1.0);
            if (!Double.isFinite(reduction) || reduction < 0.0 || reduction >= 1.0) {
                throw new IllegalArgumentException("Insect spider damage reduction must be in [0, 1): " + id);
            }
            if (previousReduction != null && reduction < previousReduction) {
                throw new IllegalArgumentException("Insect spider damage reduction must not decrease by tier.");
            }
            previousReduction = reduction;
        }
        validateInsectTierOrder(List.of(InsectTowers.SILVERFISH, InsectTowers.ENDERMITE, InsectTowers.ENHANCED_ENDERMITE));
        validateInsectTierOrder(List.of(InsectTowers.CAVE_SPIDER, InsectTowers.SPIDER, InsectTowers.ENHANCED_SPIDER));
        validateInsectTierOrder(List.of(InsectTowers.BEE, InsectTowers.ENHANCED_BEE, InsectTowers.QUEEN_BEE));
    }

    private void validateInsectTierOrder(List<TowerType> types) {
        TowerStats previous = null;
        for (TowerType type : types) {
            TowerStats current = towers.get(type.id());
            if (current != null && previous != null
                    && (current.maxHealth() < previous.maxHealth()
                    || current.damage() < previous.damage()
                    || current.range() < previous.range()
                    || current.attackIntervalTicks() > previous.attackIntervalTicks())) {
                throw new IllegalArgumentException("Insect tower stats must improve by tier: " + type.id());
            }
            if (current != null) {
                previous = current;
            }
        }
    }

    private static void validateEngineerValues(String id, Map<String, Double> values) {
        values.forEach((key, value) -> {
            if (value == null || !Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException("Engineer balance ability must be finite and non-negative: " + id + "." + key);
            }
        });
    }

    private static void requireEngineerPositive(Map<String, Double> values, String... keys) {
        for (String key : keys) {
            if (values.getOrDefault(key, 0.0) <= 0.0) {
                throw new IllegalArgumentException("Engineer balance ability must be positive: " + key);
            }
        }
    }

    private static void requireEngineerIntegral(Map<String, Double> values, String... keys) {
        for (String key : keys) {
            double value = values.getOrDefault(key, -1.0);
            if (value < 0.0 || value != Math.rint(value) || value > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Engineer balance ability must be an integer: " + key);
            }
        }
    }

    private static void putMageAbilities(Map<String, Map<String, Double>> abilities) {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>();
        values.put("manaCapacity", (double) MageBalance.MANA_CAPACITY);
        values.put("startingMana", (double) MageBalance.STARTING_MANA);
        values.put("idleWizardMana", (double) MageBalance.IDLE_WIZARD_MANA);
        values.put("prophetMana", (double) MageBalance.PROPHET_MANA);
        values.put("coreMana", (double) MageBalance.CORE_MANA);
        values.put("coreBreakManaLossRatio", MageBalance.CORE_BREAK_MANA_LOSS_RATIO);
        values.put("prophecyReward", (double) MageBalance.PROPHECY_REWARD);
        values.put("supportRadius", MageBalance.SUPPORT_RADIUS);
        values.put("amplificationBonus", MageBalance.AMPLIFICATION_BONUS);
        values.put("manaDamageBonusAtCapacity", MageBalance.MANA_DAMAGE_BONUS_AT_CAPACITY);
        values.put("rangedBarrierReduction", MageBalance.RANGED_BARRIER_REDUCTION);
        values.put("intermediateCasts", (double) MageBalance.INTERMEDIATE_CASTS);
        values.put("archmageCasts", (double) MageBalance.ARCHMAGE_CASTS);
        values.put("intermediateDamageMultiplier", MageBalance.INTERMEDIATE_DAMAGE_MULTIPLIER);
        values.put("archmageDamageMultiplier", MageBalance.ARCHMAGE_DAMAGE_MULTIPLIER);
        values.put("maxSpellDamageMultiplier", MageBalance.MAX_SPELL_DAMAGE_MULTIPLIER);
        values.put("manaRetryTicks", (double) MageBalance.MANA_RETRY_TICKS);
        for (MageSpell spell : MageSpell.values()) {
            values.put(spell.id() + "ManaCost", (double) spell.defaultManaCost());
            values.put(spell.id() + "CooldownTicks", (double) spell.defaultCooldownTicks());
            values.put(spell.id() + "Range", spell.defaultRange());
        }
        values.put("missileDamage", MageBalance.MISSILE_DAMAGE);
        values.put("missileCount", (double) MageBalance.MISSILE_COUNT);
        values.put("missileIntervalTicks", (double) MageBalance.MISSILE_INTERVAL_TICKS);
        values.put("windCutterDamage", MageBalance.WIND_CUTTER_DAMAGE);
        values.put("windCutterWidth", MageBalance.WIND_CUTTER_WIDTH);
        values.put("windCutterMaxTargets", (double) MageBalance.WIND_CUTTER_MAX_TARGETS);
        values.put("manaBombDamage", MageBalance.MANA_BOMB_DAMAGE);
        values.put("manaBombRadius", MageBalance.MANA_BOMB_RADIUS);
        values.put("manaBombMaxTargets", (double) MageBalance.MANA_BOMB_MAX_TARGETS);
        values.put("manaBombDelayTicks", (double) MageBalance.MANA_BOMB_DELAY_TICKS);
        for (int index = 0; index < MageBalance.CHAIN_LIGHTNING_DAMAGE.length; index++) {
            values.put("chainDamage" + (index + 1), MageBalance.CHAIN_LIGHTNING_DAMAGE[index]);
        }
        values.put("chainJumpRange", MageBalance.CHAIN_LIGHTNING_JUMP_RANGE);
        values.put("frostWaveDamage", MageBalance.FROST_WAVE_DAMAGE);
        values.put("frostWaveRadius", MageBalance.FROST_WAVE_RADIUS);
        values.put("frostWaveMaxTargets", (double) MageBalance.FROST_WAVE_MAX_TARGETS);
        values.put("frostWaveSlow", MageBalance.FROST_WAVE_SLOW);
        values.put("frostWaveDurationTicks", (double) MageBalance.FROST_WAVE_DURATION_TICKS);
        values.put("collapseDamage", MageBalance.DIMENSIONAL_COLLAPSE_DAMAGE);
        values.put("collapseRadius", MageBalance.DIMENSIONAL_COLLAPSE_RADIUS);
        values.put("collapseDelayTicks", (double) MageBalance.DIMENSIONAL_COLLAPSE_DELAY_TICKS);
        putAbilities(abilities, MageBalance.GLOBAL_ID, values);
    }

    private void validateMageBalance() {
        Map<String, Double> values = abilities.get(MageBalance.GLOBAL_ID);
        if (values == null) {
            return;
        }
        values.forEach((key, value) -> {
            if (value == null || !Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException("Mage balance ability must be finite and non-negative: " + key);
            }
        });
        requireMagePositive(values,
                "manaCapacity", "supportRadius", "intermediateCasts", "archmageCasts",
                "intermediateDamageMultiplier", "archmageDamageMultiplier", "maxSpellDamageMultiplier", "manaRetryTicks",
                "missileCount", "missileIntervalTicks",
                "windCutterWidth", "windCutterMaxTargets", "manaBombRadius", "manaBombMaxTargets", "manaBombDelayTicks",
                "chainJumpRange", "frostWaveRadius", "frostWaveMaxTargets", "frostWaveDurationTicks",
                "collapseRadius", "collapseDelayTicks", "missileDamage", "windCutterDamage",
                "manaBombDamage", "chainDamage1", "chainDamage2", "chainDamage3", "chainDamage4",
                "chainDamage5", "chainDamage6", "frostWaveDamage", "collapseDamage");
        requireMageRatio(values, "amplificationBonus", "manaDamageBonusAtCapacity",
                "rangedBarrierReduction", "frostWaveSlow", "coreBreakManaLossRatio");
        requireMageIntegral(values,
                "manaCapacity", "startingMana", "idleWizardMana", "prophetMana", "coreMana", "prophecyReward",
                "intermediateCasts", "archmageCasts", "manaRetryTicks", "missileCount", "missileIntervalTicks",
                "windCutterMaxTargets", "manaBombMaxTargets",
                "manaBombDelayTicks", "frostWaveMaxTargets", "frostWaveDurationTicks", "collapseDelayTicks");
        for (MageSpell spell : MageSpell.values()) {
            requireMagePositive(values, spell.id() + "ManaCost");
            requireMagePositive(values, spell.id() + "CooldownTicks", spell.id() + "Range");
            requireMageIntegral(values, spell.id() + "ManaCost");
            requireMageIntegral(values, spell.id() + "CooldownTicks");
        }
        if (values.get("archmageCasts") <= values.get("intermediateCasts")) {
            throw new IllegalArgumentException("Mage archmage cast threshold must exceed intermediate threshold");
        }
        if (values.get("archmageDamageMultiplier") < values.get("intermediateDamageMultiplier")) {
            throw new IllegalArgumentException("Mage rank damage multipliers must not decrease");
        }
        if (values.get("intermediateDamageMultiplier") < 1.0
                || values.get("archmageDamageMultiplier") > values.get("maxSpellDamageMultiplier")) {
            throw new IllegalArgumentException("Mage rank damage multipliers must stay between 1 and the spell cap");
        }
        for (int index = 2; index <= MageBalance.CHAIN_LIGHTNING_DAMAGE.length; index++) {
            if (values.get("chainDamage" + index) > values.get("chainDamage" + (index - 1))) {
                throw new IllegalArgumentException("Mage chain lightning damage must not increase at jump " + index);
            }
        }
    }

    private static void requireMagePositive(Map<String, Double> values, String... keys) {
        for (String key : keys) {
            if (values.getOrDefault(key, 0.0) <= 0.0) {
                throw new IllegalArgumentException("Mage balance ability must be positive: " + key);
            }
        }
    }

    private static void requireMageRatio(Map<String, Double> values, String... keys) {
        for (String key : keys) {
            double value = values.getOrDefault(key, -1.0);
            if (value < 0.0 || value > 1.0) {
                throw new IllegalArgumentException("Mage balance ratio must be between 0 and 1: " + key);
            }
        }
    }

    private static void requireMageIntegral(Map<String, Double> values, String... keys) {
        for (String key : keys) {
            double value = values.getOrDefault(key, -1.0);
            if (value < 0.0 || value != Math.rint(value) || value > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Mage balance ability must be an integer: " + key);
            }
        }
    }

    private static void mergeAbilities(
            Map<String, Map<String, Double>> abilities,
            String configId,
            Map<String, Double> values
    ) {
        LinkedHashMap<String, Double> merged = new LinkedHashMap<>(abilities.getOrDefault(configId, Map.of()));
        merged.putAll(values);
        abilities.put(configId, Collections.unmodifiableMap(merged));
    }

    private static void putAdversaryAbilities(Map<String, Map<String, Double>> abilities) {
        putAbilities(abilities, AdversaryBalance.GLOBAL_CONFIG_ID, Map.ofEntries(
                Map.entry("maxFoxTowers", (double) AdversaryBalance.MAX_FOX_TOWERS),
                Map.entry("rivalRoundHealthGrowth", AdversaryBalance.RIVAL_ROUND_HEALTH_GROWTH),
                Map.entry("rivalRoundDamageGrowth", AdversaryBalance.RIVAL_ROUND_DAMAGE_GROWTH),
                Map.entry("rivalArmorRoundInterval", (double) AdversaryBalance.RIVAL_ARMOR_ROUND_INTERVAL),
                Map.entry("baseSplashRadius", AdversaryBalance.BASE_SPLASH_RADIUS),
                Map.entry("baseSplashExtraTargets", (double) AdversaryBalance.BASE_SPLASH_EXTRA_TARGETS),
                Map.entry("baseSplashDamageRatio", AdversaryBalance.BASE_SPLASH_DAMAGE_RATIO),
                Map.entry("evolvedSplashDamageRatio", AdversaryBalance.EVOLVED_SPLASH_DAMAGE_RATIO),
                Map.entry("postEvolutionDamageBonusPerScore", AdversaryBalance.POST_EVOLUTION_DAMAGE_BONUS_PER_SCORE),
                Map.entry("postEvolutionDamageBonusCap", AdversaryBalance.POST_EVOLUTION_DAMAGE_BONUS_CAP),
                Map.entry("baseRivalKillHealRatio", AdversaryBalance.BASE_RIVAL_KILL_HEAL_RATIO),
                Map.entry("enhancedRivalKillHealRatio", AdversaryBalance.ENHANCED_RIVAL_KILL_HEAL_RATIO),
                Map.entry("rivalKillHealCapRatioPerWave", AdversaryBalance.RIVAL_KILL_HEAL_CAP_RATIO_PER_WAVE),
                Map.entry("focusFireDamageReductionPerExtraAttacker", AdversaryBalance.FOCUS_FIRE_DAMAGE_REDUCTION_PER_EXTRA_ATTACKER),
                Map.entry("focusFireDamageReductionCap", AdversaryBalance.FOCUS_FIRE_DAMAGE_REDUCTION_CAP),
                Map.entry("breezeExtraTargets", (double) AdversaryBalance.BREEZE_EXTRA_TARGETS),
                Map.entry("breezeExtraTargetDamageRatio", AdversaryBalance.BREEZE_EXTRA_TARGET_DAMAGE_RATIO),
                Map.entry("goldenExtraAttackEvery", (double) AdversaryBalance.GOLDEN_FANG_EXTRA_ATTACK_EVERY),
                Map.entry("goldenExtraDamageRatio", AdversaryBalance.GOLDEN_FANG_EXTRA_DAMAGE_RATIO),
                Map.entry("shieldCounterDamage", AdversaryBalance.SHIELD_COUNTER_DAMAGE),
                Map.entry("shieldCounterCooldownTicks", (double) AdversaryBalance.SHIELD_COUNTER_COOLDOWN_TICKS),
                Map.entry("bellHealIntervalTicks", (double) AdversaryBalance.BELL_HEAL_INTERVAL_TICKS),
                Map.entry("bellHealRadius", AdversaryBalance.BELL_HEAL_RADIUS),
                Map.entry("bellHealTargetCount", (double) AdversaryBalance.BELL_HEAL_TARGET_COUNT),
                Map.entry("bellHealMaxHealthRatio", AdversaryBalance.BELL_HEAL_MAX_HEALTH_RATIO),
                Map.entry("beaconHealIntervalTicks", (double) AdversaryBalance.BEACON_HEAL_INTERVAL_TICKS),
                Map.entry("beaconHealRadius", AdversaryBalance.BEACON_HEAL_RADIUS),
                Map.entry("beaconHealTargetCount", (double) AdversaryBalance.BEACON_HEAL_TARGET_COUNT),
                Map.entry("beaconHealMaxHealthRatio", AdversaryBalance.BEACON_HEAL_MAX_HEALTH_RATIO),
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
                Map.entry("fireworkSecondary6Ratio", AdversaryBalance.FIREWORK_TARGET_DAMAGE_RATIOS[5]),
                Map.entry("fireworkSecondary7Ratio", AdversaryBalance.FIREWORK_TARGET_DAMAGE_RATIOS[6]),
                Map.entry("fireworkSecondary8Ratio", AdversaryBalance.FIREWORK_TARGET_DAMAGE_RATIOS[7]),
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
        putWardenAbilities(abilities, AncientCityTowers.WARDEN_T1, 10, 60, 2, 1, 0.40);
        putWardenAbilities(abilities, AncientCityTowers.WARDEN_T2, 16, 50, 3, 1, 0.50);
        putWardenAbilities(abilities, AncientCityTowers.WARDEN_T3, 55, 50, 4, 2, 0.75);
        putWardenAbilities(abilities, AncientCityTowers.WARDEN_T4, 68, 46, 5, 2, 0.75);
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
            double targets,
            double extraTargets,
            double secondaryDamageRatio
    ) {
        putAbilities(abilities, type.id(), Map.of(
                "magicDamage", damage,
                "magicCooldownTicks", cooldown,
                "targetCount", targets,
                "sculkExtraTargets", extraTargets,
                "secondaryDamageRatio", secondaryDamageRatio
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
        values.put(DAMAGE_THRESHOLD.key(), 140.0);
        values.put(DAMAGE_SCALE.key(), 20.0);
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

    private static Map<String, Double> warlockGlobalAbilities() {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>();
        values.put("sacrificeRadius", 25.0);
        values.put("absorptionHeal", 30.0);
        values.put("minInterval", 5.0);
        values.put("speedCap", 15.0);
        values.put("awakeningKills", 1200.0);
        values.put("awakeningThreshold", 0.40);
        return values;
    }

    private static Map<String, Double> baseWarlockAbilities() {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>();
        values.put("sacrificeRadius", 6.0);
        values.put("fatalHeal", 0.35);
        values.put("permanentHealth", 0.025);
        values.put("permanentDamage", 0.05);
        return values;
    }

    private static Map<String, Double> rangedWarlockAbilities() {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>();
        values.put("threshold", 0.55);
        values.put("roundStat", 0.50);
        values.put("permanentHealth", 0.025);
        values.put("healthThreshold", 2000.0);
        values.put("healthScale", 500.0);
        values.put("permanentDamage", 0.05);
        values.put("damageThreshold", 145.0);
        values.put("damageScale", 20.0);
        values.put("lifeEvery", 10.0);
        values.put("lifeStep", 0.005);
        values.put("lifeCap", 0.08);
        values.put("splashEvery", 2.0);
        values.put("splashStep", 0.1);
        values.put("splashCap", 8.0);
        values.put("splashDamage", 0.50);
        values.put("defenseThreshold", 3.0);
        values.put("defense", 0.15);
        values.put("petHealth", 0.04);
        values.put("petHealthCap", 0.20);
        values.put("petDamage", 0.10);
        values.put("petDamageCap", 0.50);
        values.put("awakeningHeal", 600.0);
        values.put("awakeningRegeneration", 40.0);
        values.put("awakeningRegenerationTicks", 20.0);
        return values;
    }

    private static Map<String, Double> meleeWarlockAbilities() {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>();
        values.put("threshold", 0.55);
        values.put("roundStat", 0.60);
        values.put("permanentHealth", 0.05);
        values.put("healthThreshold", 3500.0);
        values.put("healthScale", 500.0);
        values.put("permanentDamage", 0.025);
        values.put("damageThreshold", 200.0);
        values.put("damageScale", 20.0);
        values.put("lifeStep", 0.01);
        values.put("lifeCap", 0.14);
        values.put("speedStep", 1.0);
        values.put("splashStep", 0.25);
        values.put("splashCap", 2.0);
        values.put("splashDamage", 0.75);
        values.put("defenseEvery", 10.0);
        values.put("defenseStep", 0.025);
        values.put("defenseCap", 0.30);
        values.put("petHealth", 0.10);
        values.put("petHealthCap", 0.50);
        values.put("petDamage", 0.04);
        values.put("petDamageCap", 0.20);
        values.put("awakeningHeal", 600.0);
        values.put("awakeningDamage", 75.0);
        values.put("awakeningMoveSpeed", 0.30);
        return values;
    }

    private static void addDemonLordTowers(LinkedHashMap<String, TowerStats> towers) {
        DemonLordTowers.all().forEach(type -> addTower(towers, type));
    }

    private static void addGambleTowers(LinkedHashMap<String, TowerStats> towers) {
        GambleTowers.all().forEach(type -> addTower(towers, type));
    }

    private static void putGambleUpgrades(LinkedHashMap<String, Long> upgradeCosts) {
        putUpgrade(upgradeCosts, GambleTowers.DICE_T1, GambleTowers.DICE_T2.id(), 100);
        putUpgrade(upgradeCosts, GambleTowers.DICE_T2, GambleTowers.DICE_T3.id(), 200);
        putUpgrade(upgradeCosts, GambleTowers.SPECTATOR_T1, GambleTowers.SPECTATOR_T2.id(), 100);
        putUpgrade(upgradeCosts, GambleTowers.SPECTATOR_T2, GambleTowers.SPECTATOR_T3.id(), 200);
        for (TowerType gambler : List.of(
                GambleTowers.GAMBLER, GambleTowers.KING, GambleTowers.DARK_KING)) {
            for (GambleBet bet : GambleBet.values()) {
                putUpgrade(upgradeCosts, gambler, bet.upgradeId(),
                        bet == GambleBet.TWO_DICE ? 160 : 80);
            }
        }
    }

    private static void putGambleAbilities(LinkedHashMap<String, Map<String, Double>> abilities) {
        LinkedHashMap<String, Double> global = new LinkedHashMap<>();
        global.put("oddEvenWinScore", GambleBalance.ODD_EVEN_WIN_SCORE);
        global.put("oddEvenLossScore", GambleBalance.ODD_EVEN_LOSS_SCORE);
        global.put("maxHealthPerScore", GambleBalance.MAX_HEALTH_PER_SCORE);
        global.put("damagePerScore", GambleBalance.DAMAGE_PER_SCORE);
        global.put("rangePerScore", GambleBalance.RANGE_PER_SCORE);
        global.put("splashRadiusPerScore", GambleBalance.SPLASH_RADIUS_PER_SCORE);
        global.put("baseSplashRadius", GambleBalance.BASE_SPLASH_RADIUS);
        global.put("splashDamageRatio", GambleBalance.SPLASH_DAMAGE_RATIO);
        global.put("twoDiceLoss2", 70.0);
        global.put("twoDiceLoss3", 50.0);
        global.put("twoDiceLoss4", 30.0);
        global.put("twoDiceLoss5", 10.0);
        global.put("twoDiceGain6", 20.0);
        global.put("twoDiceGain7", 40.0);
        global.put("twoDiceGain8", 50.0);
        global.put("twoDiceGain9", 60.0);
        global.put("twoDiceGain10", 90.0);
        global.put("twoDiceGain11", 120.0);
        global.put("twoDiceGain12", 150.0);
        global.put("abilityRewardChance", GambleBalance.ABILITY_REWARD_CHANCE);
        global.put("lossInsuranceReduction", GambleBalance.LOSS_INSURANCE_REDUCTION);
        global.put("twoDiceCompoundMinSum", (double) GambleBalance.TWO_DICE_COMPOUND_MIN_SUM);
        global.put("supportVfxIntervalTicks", (double) GambleBalance.SUPPORT_VFX_INTERVAL_TICKS);
        global.put("supportPositiveRangeUnit", GambleBalance.SUPPORT_POSITIVE_RANGE_UNIT);
        global.put("supportPositiveRegenUnit", GambleBalance.SUPPORT_POSITIVE_REGEN_UNIT);
        global.put("supportPositiveDamageUnit", GambleBalance.SUPPORT_POSITIVE_DAMAGE_UNIT);
        global.put("supportPositiveMaxHealthUnit", GambleBalance.SUPPORT_POSITIVE_MAX_HEALTH_UNIT);
        global.put("supportNegativeRangeUnit", GambleBalance.SUPPORT_NEGATIVE_RANGE_UNIT);
        global.put("supportNegativeHealthLossUnit", GambleBalance.SUPPORT_NEGATIVE_HEALTH_LOSS_UNIT);
        global.put("supportNegativeDamageUnit", GambleBalance.SUPPORT_NEGATIVE_DAMAGE_UNIT);
        global.put("supportNegativeMaxHealthUnit", GambleBalance.SUPPORT_NEGATIVE_MAX_HEALTH_UNIT);
        global.put("maxSpectatorsPerGambler", (double) GambleBalance.MAX_SPECTATORS_PER_GAMBLER);
        global.put("kingPromotionScore", GambleBalance.KING_PROMOTION_SCORE);
        global.put("darkKingPromotionScoreMagnitude", Math.abs(GambleBalance.DARK_KING_PROMOTION_SCORE));
        global.put("maxGambleScore", GambleBalance.MAX_GAMBLE_SCORE);
        putAbilities(abilities, GambleBalance.GLOBAL_ID, global);

        putAbilities(abilities, GambleTowers.KING.id(), Map.of(
                "splashRadiusBonus", GambleBalance.KING_SPLASH_RADIUS_BONUS
        ));
        putAbilities(abilities, GambleTowers.DARK_KING.id(), Map.of(
                "splashRadiusBonus", GambleBalance.DARK_KING_SPLASH_RADIUS_BONUS
        ));

        putGambleSupportAbilities(abilities, GambleTowers.DICE_T1, 1.0, 0);
        putGambleSupportAbilities(abilities, GambleTowers.DICE_T2, 2.0, 0);
        putGambleSupportAbilities(abilities, GambleTowers.DICE_T3, 3.5, 0);
        putGambleSupportAbilities(abilities, GambleTowers.SPECTATOR_T1, 1.0, 5);
        putGambleSupportAbilities(abilities, GambleTowers.SPECTATOR_T2, 2.0, 15);
        putGambleSupportAbilities(abilities, GambleTowers.SPECTATOR_T3, 3.5, 35);
    }

    private static void putGambleSupportAbilities(
            LinkedHashMap<String, Map<String, Double>> abilities,
            TowerType type,
            double supportPowerMultiplier,
            int faceSixDiamondReward
    ) {
        putAbilities(abilities, type.id(), Map.of(
                "minimumRoll", 1.0,
                "supportPowerMultiplier", supportPowerMultiplier,
                "faceSixDiamondReward", (double) faceSixDiamondReward
        ));
    }

    /** Demon lord upgrades cost 1.5 times the target tier's placement price, rounded up. */
    private static void putDemonLordUpgrades(LinkedHashMap<String, Long> upgradeCosts) {
        for (DemonLordSkill skill : DemonLordSkill.values()) {
            for (int tier = 1; tier < DemonLordSkill.MAX_TIER; tier++) {
                TowerType next = DemonLordTowers.tower(skill, tier + 1);
                putUpgrade(upgradeCosts, DemonLordTowers.tower(skill, tier), next.id(),
                        (long) Math.ceil(next.mineralCost() * 1.5));
            }
        }
    }

    private static void putDemonLordAbilities(LinkedHashMap<String, Map<String, Double>> abilities) {
        // 마왕 본체. 레벨은 라운드를 넘어 유지되고, 체력 풀은 라운드마다 이 값으로 다시 채워집니다.
        LinkedHashMap<String, Double> global = new LinkedHashMap<>();
        global.put("baseMaxHealth", 450.0);
        global.put("maxHealthPerLevel", 52.5);
        global.put("maxLevel", 30.0);
        // 처치한 몹의 최대 체력에 비례해 경험치를 줍니다. 단단한 적을 잡을수록 크게 성장합니다.
        global.put("experiencePerMaxHealth", 0.02);
        global.put("experienceBase", 12.0);
        global.put("experienceGrowth", 1.25);
        // 스킬과 평타 모두에 곱해집니다. 레벨 보너스만 로그 소프트캡을 적용하고,
        // 공격력 포인트는 DemonLordState 에서 제한 없이 더합니다.
        global.put("damagePerLevel", 0.05);
        global.put("healthBonusThreshold", 500.0);
        global.put("healthBonusScale", 500.0);
        global.put("damageBonusThreshold", 0.5);
        global.put("damageBonusScale", 0.5);
        global.put("bladeDamage", 19.0);
        global.put("bladeAttackIntervalTicks", 12.0);
        // 몹을 하나도 못 잡은 라운드에도 주는 기본 경험치입니다. 한 번 밀린 마왕이 영영
        // 따라잡지 못하는 상황을 막습니다. 직접 잡는 편이 여전히 훨씬 빠릅니다.
        global.put("passiveExperiencePerRound", 6.0);
        // 스탯 포인트. 레벨업마다 받아 원하는 능력치에 넣습니다.
        global.put("statPointsPerLevel", 3.0);
        global.put("statHealthPerPoint", 40.0);
        global.put("statAttackPerPoint", 0.04);
        global.put("statDefensePerPoint", 0.02);
        global.put("statDefenseCap", 0.6);
        // 쿨감은 이 포인트마다 절반이 되는 곱연산입니다. 60 이면 50%, 120 이면 25% 가 되고
        // 0 에는 닿지 않습니다. 선형이면 어느 지점에서 쿨타임이 사라져 버립니다.
        //
        // 다른 스탯보다 포인트를 많이 요구합니다. 쿨감은 모든 스킬에 한꺼번에 곱해지는 데다
        // 딜뿐 아니라 생존기와 이동기 회전율까지 같이 올려서, 같은 효율로 두면 다른 선택지가
        // 존재할 이유가 없어집니다.
        global.put("statCooldownHalvingPoints", 60.0);
        global.put("statSkillRangePerPoint", 0.03);
        global.put("statMoveSpeedPerPoint", 0.03);
        global.put("statMoveSpeedCap", 0.5);
        putAbilities(abilities, DemonLordTowers.GLOBAL_CONFIG_ID, global);

        for (DemonLordSkill skill : DemonLordSkill.values()) {
            for (int tier = 1; tier <= DemonLordSkill.MAX_TIER; tier++) {
                LinkedHashMap<String, Double> values = new LinkedHashMap<>();
                // 빌더의 "코스트". 라운드 타워 한도를 이만큼 차지합니다.
                values.put(TowerCapacity.CONFIG_KEY, (double) skill.slotCost());
                values.put("cooldownTicks", (double) (skill.cooldownSecondsForTier(tier) * 20));
                values.putAll(demonLordSkillValues(skill, tier));
                putAbilities(abilities, skill.towerId(tier), values);
            }
        }
    }

    /** Per-tier skill numbers. Index 0 is tier 1. */
    private static Map<String, Double> demonLordSkillValues(DemonLordSkill skill, int tier) {
        int index = tier - 1;
        LinkedHashMap<String, Double> values = new LinkedHashMap<>();
        switch (skill) {
            case WAVE_OF_MALICE -> {
                values.put("coneDegrees", 60.0);
                values.put("range", new double[] {6.0, 6.5, 7.0, 8.0}[index]);
                values.put("damage", new double[] {34.0, 53.0, 75.0, 105.0}[index]);
                values.put("knockback", new double[] {0.8, 0.9, 1.0, 1.2}[index]);
            }
            case DEMON_WINGS -> {
                values.put("leapPower", new double[] {1.0, 1.1, 1.2, 1.3}[index]);
                values.put("radius", new double[] {4.0, 4.5, 5.0, 5.5}[index]);
                values.put("damage", new double[] {23.0, 36.0, 53.0, 71.0}[index]);
                values.put("knockback", new double[] {0.7, 0.8, 0.9, 1.0}[index]);
            }
            case SKY_BREAKER -> {
                values.put("dashDistance", new double[] {8.0, 9.0, 10.0, 12.0}[index]);
                values.put("hitRadius", new double[] {2.0, 2.2, 2.4, 2.6}[index]);
                values.put("damage", new double[] {68.0, 105.0, 150.0, 206.0}[index]);
                values.put("liftPower", new double[] {0.8, 0.85, 0.9, 1.0}[index]);
                values.put("stunTicks", new double[] {40.0, 45.0, 50.0, 60.0}[index]);
            }
            case ARCANE_BOMBARDMENT -> {
                values.put("jumpPower", new double[] {0.9, 0.95, 1.0, 1.1}[index]);
                // 솟아오른 뒤 정점에서 발사할 때까지의 대기 시간입니다.
                values.put("castDelayTicks", new double[] {10.0, 10.0, 9.0, 8.0}[index]);
                values.put("projectileRange", new double[] {18.0, 20.0, 22.0, 25.0}[index]);
                values.put("blastRadius", new double[] {4.0, 4.5, 5.0, 5.5}[index]);
                values.put("damage", new double[] {53.0, 83.0, 116.0, 158.0}[index]);
            }
            case DEMON_BARRIER -> {
                values.put("shieldRatio", new double[] {0.25, 0.32, 0.40, 0.50}[index]);
                values.put("shieldDurationTicks", new double[] {160.0, 180.0, 200.0, 240.0}[index]);
            }
            case HELLFIRE_BRAND -> {
                // 시선이 닿는 지점에 깝니다. 길목에 미리 깔거나 뭉친 무리를 노릴 수 있어야 합니다.
                values.put("placementRange", new double[] {10.0, 11.0, 12.0, 14.0}[index]);
                values.put("zoneRadius", new double[] {3.5, 4.0, 4.5, 5.0}[index]);
                values.put("zoneDurationTicks", new double[] {100.0, 120.0, 140.0, 160.0}[index]);
                values.put("tickIntervalTicks", 20.0);
                // 장판은 지속으로 여러 번 들어가므로 1회 피해를 낮게 잡습니다.
                values.put("damage", new double[] {14.0, 21.0, 30.0, 41.0}[index]);
                values.put("damageTakenBonus", new double[] {0.10, 0.15, 0.20, 0.25}[index]);
            }
            case SOUL_DRAIN -> {
                values.put("range", new double[] {7.0, 8.0, 9.0, 10.0}[index]);
                values.put("width", new double[] {1.6, 1.8, 2.0, 2.2}[index]);
                values.put("damage", new double[] {26.0, 41.0, 60.0, 83.0}[index]);
                values.put("lifeStealRatio", new double[] {0.25, 0.30, 0.35, 0.40}[index]);
                // 다수를 꿰뚫어도 한 번에 회복할 수 있는 양에 상한을 둡니다.
                values.put("lifeStealCap", new double[] {0.12, 0.15, 0.18, 0.22}[index]);
                // 꿰뚫린 적은 이동 속도가 100% 깎여 그 자리에 묶입니다. 공격은 계속하므로
                // 붙어 있는 적에게 쓰면 의미가 없고, 지나가려는 줄을 세우는 데 씁니다.
                values.put("rootDurationTicks", new double[] {40.0, 50.0, 60.0, 70.0}[index]);
            }
            case GRIP_OF_DOOM -> {
                values.put("range", new double[] {9.0, 10.0, 11.0, 12.0}[index]);
                // 처형 임계값. 대상 최대 체력의 이 비율 이하면 즉사시킵니다. 1.0 으로 올리면
                // 체력과 무관하게 무조건 즉사하지만, 상대가 비싸게 산 유닛을 대응 없이 지우게 됩니다.
                values.put("executeHealthRatio", new double[] {0.50, 0.55, 0.60, 0.70}[index]);
                // 처형 시 시체가 터집니다. 폭발 피해 = 처형 시점 체력 × 비율 + areaDamage.
                values.put("explosionHealthRatio", new double[] {0.80, 0.90, 1.00, 1.20}[index]);
                values.put("explosionRadius", new double[] {4.0, 4.5, 5.0, 6.0}[index]);
                values.put("areaDamage", new double[] {30.0, 49.0, 71.0, 98.0}[index]);
                // 임계값 위인 대상에게 들어가는 일반 피해입니다.
                values.put("damage", new double[] {98.0, 150.0, 214.0, 293.0}[index]);
                values.put("missingHealthRatio", new double[] {0.10, 0.14, 0.18, 0.24}[index]);
                values.put("killRefundTicks", new double[] {60.0, 70.0, 80.0, 100.0}[index]);
                values.put("pullStrength", new double[] {0.5, 0.55, 0.6, 0.7}[index]);
            }
            case HELL_GUILLOTINE -> {
                values.put("range", new double[] {10.0, 12.0, 14.0, 16.0}[index]);
                values.put("radius", new double[] {4.0, 4.5, 5.0, 5.5}[index]);
                values.put("damage", new double[] {45.0, 71.0, 101.0, 139.0}[index]);
                // 마왕이 잃은 체력 비율에 비례해 피해가 커집니다. 체력 0 에 가까울 때의 최대 증가폭.
                values.put("missingHealthDamageBonus", new double[] {1.00, 1.20, 1.40, 1.80}[index]);
            }
            case ROAR_OF_DREAD -> {
                values.put("radius", new double[] {5.0, 5.5, 6.0, 7.0}[index]);
                values.put("damage", new double[] {19.0, 30.0, 44.0, 60.0}[index]);
                values.put("knockback", new double[] {1.0, 1.1, 1.2, 1.4}[index]);
                values.put("moveSpeedReduction", new double[] {0.50, 0.58, 0.66, 0.75}[index]);
                values.put("dreadDurationTicks", new double[] {50.0, 60.0, 70.0, 80.0}[index]);
            }
            default -> {
            }
        }
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

        private TowerStats withInvalidNumericValuesFrom(TowerStats fallback) {
            return new TowerStats(
                    mineralCost != null && mineralCost >= 0L
                            ? mineralCost
                            : fallback == null ? null : fallback.mineralCost,
                    isFiniteAtLeast(maxHealth, 1.0)
                            ? maxHealth
                            : fallback == null ? null : fallback.maxHealth,
                    isFiniteAtLeast(range, 0.0)
                            ? range
                            : fallback == null ? null : fallback.range,
                    isFiniteAtLeast(damage, 0.0)
                            ? damage
                            : fallback == null ? null : fallback.damage,
                    attackIntervalTicks != null && attackIntervalTicks >= 1
                            ? attackIntervalTicks
                            : fallback == null ? null : fallback.attackIntervalTicks,
                    aggroPriority
            );
        }

        private static boolean isFiniteAtLeast(Double value, double minimum) {
            return value != null && Double.isFinite(value) && value >= minimum;
        }
    }
}
