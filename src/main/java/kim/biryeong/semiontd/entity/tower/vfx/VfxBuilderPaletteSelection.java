package kim.biryeong.semiontd.entity.tower.vfx;

import java.util.Set;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.adversary.AdversaryTowers;
import kim.biryeong.semiontd.tower.ancientcity.AncientCityTowers;
import kim.biryeong.semiontd.tower.animal.AnimalTowers;
import kim.biryeong.semiontd.tower.army.ArmyTowers;
import kim.biryeong.semiontd.tower.body.BodyTowers;
import kim.biryeong.semiontd.tower.developer.DeveloperTowers;
import kim.biryeong.semiontd.tower.end.EndTowers;
import kim.biryeong.semiontd.tower.engineer.EngineerTowers;
import kim.biryeong.semiontd.tower.futureagency.FutureAgencyTowers;
import kim.biryeong.semiontd.tower.frost.FrostTowers;
import kim.biryeong.semiontd.tower.gamble.GambleTowers;
import kim.biryeong.semiontd.tower.hero.HeroPartyTowers;
import kim.biryeong.semiontd.tower.illager.IllagerTowers;
import kim.biryeong.semiontd.tower.insect.InsectTowers;
import kim.biryeong.semiontd.tower.legion.LegionTowers;
import kim.biryeong.semiontd.tower.mage.MageTowers;
import kim.biryeong.semiontd.tower.succubus.SuccubusTowers;
import kim.biryeong.semiontd.tower.nether.NetherTowers;
import kim.biryeong.semiontd.tower.ocean.OceanTowers;
import kim.biryeong.semiontd.tower.pet.PetTowers;
import kim.biryeong.semiontd.tower.pirate.PirateTowers;
import kim.biryeong.semiontd.tower.plant.PlantTowers;
import kim.biryeong.semiontd.tower.queen.QueenTowers;
import kim.biryeong.semiontd.tower.resonance.ResonanceTowers;
import kim.biryeong.semiontd.tower.thunder.ThunderTowers;
import kim.biryeong.semiontd.tower.demonlord.DemonLordTowers;
import kim.biryeong.semiontd.tower.undead.UndeadTowers;
import kim.biryeong.semiontd.tower.villager.VillagerTowers;
import kim.biryeong.semiontd.tower.warlock.WarlockTowers;

final class VfxBuilderPaletteSelection {
    private VfxBuilderPaletteSelection() {}

    private static final Set<String> UNDEAD_TOWER_IDS = Set.of(
            UndeadTowers.T1_ZOMBIE_TOWER.id(), UndeadTowers.T2_ZOMBIE_TOWER.id(), UndeadTowers.T3_ZOMBIE_TOWER.id(),
            UndeadTowers.T1_SKELETON_TOWER.id(), UndeadTowers.T2_RANGED_SKELETON_TOWER.id(),
            UndeadTowers.T2_MELEE_TOWER.id(), UndeadTowers.T3_RANGED_SKELETON_TOWER.id(),
            UndeadTowers.T3_MELEE_TOWER.id(), UndeadTowers.T1_UNDEAD_ANIMAL_TOWER.id(),
            UndeadTowers.T2_UNDEAD_ANIMAL_TOWER.id()
    );

    private static final Set<String> ANIMAL_TOWER_IDS = Set.of(
            AnimalTowers.T1_PIG_TOWER.id(), AnimalTowers.T2_PIG_TOWER.id(), AnimalTowers.T3_PIG_TOWER.id(), AnimalTowers.T4_PIG_LEADER_TOWER.id(),
            AnimalTowers.T1_WOLF_TOWER.id(), AnimalTowers.T2_WOLF_DPS_TOWER.id(), AnimalTowers.T3_WOLF_DPS_TOWER.id(), AnimalTowers.T4_WOLF_LEADER_TOWER.id(),
            AnimalTowers.T1_RABBIT_TOWER.id(), AnimalTowers.T2_RABBIT_TOWER.id(), AnimalTowers.T3_RABBIT_TOWER.id(), AnimalTowers.T4_RABBIT_LEADER_TOWER.id(),
            AnimalTowers.T1_FOX_TOWER.id(), AnimalTowers.T2_FOX_TOWER.id(), AnimalTowers.T3_FOX_TOWER.id(), AnimalTowers.T4_FOX_LEADER_TOWER.id()
    );

    static BuilderPalette paletteFor(TowerType type) {
        if (kim.biryeong.semiontd.tower.augment.AugmentTowers.isAugment(type)) {
            return BuilderPalette.AUGMENT;
        }
        if (kim.biryeong.semiontd.tower.blueprint.BlueprintTowers.isBlueprintTower(type)) {
            return BuilderPalette.BLUEPRINT;
        }
        if (VillagerTowers.isAdvVillagerTower(type)) {
            return BuilderPalette.VILLAGER_ADV;
        }
        if (VillagerTowers.isBaseVillagerTower(type)) {
            return BuilderPalette.VILLAGER;
        }
        if (type != null && UNDEAD_TOWER_IDS.contains(type.id())) {
            return BuilderPalette.UNDEAD;
        }
        if (type != null && ANIMAL_TOWER_IDS.contains(type.id())) {
            return BuilderPalette.ANIMAL;
        }
        if (WarlockTowers.isWarlockTower(type)) {
            return BuilderPalette.WARLOCK;
        }
        if (LegionTowers.isLegionTower(type)) {
            return BuilderPalette.LEGION;
        }
        if (ResonanceTowers.isResonanceTower(type)) {
            return BuilderPalette.RESONANCE;
        }
        if (IllagerTowers.isIllagerTower(type)) {
            return BuilderPalette.ILLAGER;
        }
        if (NetherTowers.isNetherTower(type)) {
            return BuilderPalette.NETHER;
        }
        if (EndTowers.isEndTower(type)) {
            return BuilderPalette.END;
        }
        if (OceanTowers.isOceanTower(type)) {
            return BuilderPalette.OCEAN;
        }
        if (AncientCityTowers.isAncientCityTower(type)) {
            return BuilderPalette.ANCIENT_CITY;
        }
        if (AdversaryTowers.isAdversaryTower(type)) {
            return BuilderPalette.ADVERSARY;
        }
        if (FutureAgencyTowers.isFutureAgencyTower(type)) {
            return BuilderPalette.FUTURE_AGENCY;
        }
        if (QueenTowers.isQueenTower(type)) {
            return BuilderPalette.QUEEN;
        }
        if (EngineerTowers.isEngineerTower(type)) {
            return BuilderPalette.ENGINEER;
        }
        if (MageTowers.isMageTower(type)) {
            return BuilderPalette.MAGE;
        }
        if (HeroPartyTowers.isHeroPartyTower(type)) {
            return BuilderPalette.HERO_PARTY;
        }
        if (InsectTowers.isInsectTower(type)) {
            return BuilderPalette.INSECT;
        }
        if (PlantTowers.isPlantTower(type)) {
            return BuilderPalette.PLANT;
        }
        if (ArmyTowers.isArmyTower(type)) {
            return BuilderPalette.ARMY;
        }
        if (ThunderTowers.isThunderTower(type)) {
            return BuilderPalette.THUNDER;
        }
        if (DemonLordTowers.isDemonLordTower(type)) {
            return BuilderPalette.DEMON_LORD;
        }
        if (GambleTowers.isGambleTower(type)) {
            return BuilderPalette.GAMBLE;
        }
        if (DeveloperTowers.isDeveloperTower(type)) {
            return BuilderPalette.DEVELOPER;
        }
        if (SuccubusTowers.isSuccubusTower(type)) {
            return BuilderPalette.SUCCUBUS;
        }
        if (BodyTowers.isBodyTower(type)) {
            return BuilderPalette.BODY;
        }
        if (FrostTowers.isFrostTower(type)) {
            return BuilderPalette.FROST;
        }
        if (PetTowers.isPetTower(type)) {
            return BuilderPalette.PET;
        }
        if (PirateTowers.isPirateTower(type)) {
            return BuilderPalette.PIRATE;
        }
        if (kim.biryeong.semiontd.tower.magicschool.MagicSchoolTowers.isMagicSchoolTower(type)) {
            return BuilderPalette.MAGIC_SCHOOL;
        }
        return BuilderPalette.DEFAULT;
    }
}
