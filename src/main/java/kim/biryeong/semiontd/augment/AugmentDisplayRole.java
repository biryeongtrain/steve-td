package kim.biryeong.semiontd.augment;

import java.util.Set;

public enum AugmentDisplayRole {
    ATTACK("공격"), DEFENSE("방어"), OTHER("기타");

    private static final Set<String> ATTACK_CARDS = Set.of(
            "engagement_plan_quick", "overheat_core", "pulse_relay_blueprint", "forecast_offensive",
            "winning_barrage", "domino_fire", "giant_hunter_call", "capacitor_post_blueprint", "ambush_workshop_blueprint",
            "ordnance_factory_call", "job_villager_towers_s", "job_villager_towers_g1", "job_villager_towers_g2", "job_villager_adv_towers_p",
            "job_undead_towers_g1", "job_animal_towers_g2", "job_warlock_towers_s", "job_warlock_towers_g1",
            "job_warlock_towers_g2", "job_legion_towers_g2", "job_resonance_towers_g2", "job_resonance_towers_p",
            "job_illager_towers_s", "job_illager_towers_g1", "job_illager_towers_g2", "job_illager_towers_p",
            "job_nether_s", "job_end_towers_s", "job_end_towers_g1", "job_end_towers_g2", "job_end_towers_p",
            "job_ocean_g2", "job_ancient_city_g2", "job_ancient_city_p", "job_hero_party_s", "job_hero_party_g2",
            "job_adversary_towers_s", "job_engineer_towers_s", "job_engineer_towers_g1", "job_engineer_towers_g2",
            "job_engineer_towers_p", "job_queen_towers_g1", "job_queen_towers_p", "job_atlantis_towers_g1",
            "job_atlantis_towers_g2", "job_atlantis_towers_p", "job_succubus_s", "job_succubus_g1",
            "job_succubus_p", "job_plant_towers_g2", "job_thunder_s", "job_thunder_g2", "job_thunder_p",
            "job_demon_lord_towers_s", "job_demon_lord_towers_g2", "job_demon_lord_towers_p",
            "job_army_g1", "job_pet_towers_g1", "job_developer_towers_g1", "job_frost_g2",
            "job_pirate_g2", "job_mage_towers_g2", "job_mage_towers_p");
    private static final Set<String> DEFENSE_CARDS = Set.of(
            "folding_barricade_blueprint", "barrier_core_call", "emergency_bell_blueprint", "additional_payload",
            "biased_armor_physical", "biased_armor_magic", "job_undead_towers_s", "job_undead_towers_g2",
            "job_legion_towers_s", "job_nether_g1", "job_nether_g2", "job_nether_p", "job_body_g2",
            "job_insect_towers_s", "job_insect_towers_s2", "job_insect_towers_g1", "job_insect_towers_g3",
            "job_insect_towers_p", "job_future_agency_towers_g2", "job_future_agency_towers_p");
    private static final Set<String> OTHER_CARDS = Set.of(
            "battlefield_mastery", "beneficial_effect_1",
            "beneficial_effect_2", "beneficial_effect_3", "cash_settlement",
            "emergency_loan", "engagement_plan_long", "forbidden_blueprint",
            "frontline_specialization", "independent_position", "job_adversary_towers_g1",
            "job_adversary_towers_g2", "job_adversary_towers_p", "job_ancient_city_g1",
            "job_ancient_city_s", "job_animal_towers_g1", "job_animal_towers_p",
            "job_animal_towers_s", "job_army_g2", "job_army_p",
            "job_army_s", "job_atlantis_towers_s", "job_body_g1",
            "job_body_p", "job_body_s", "job_demon_lord_towers_g1",
            "job_developer_towers_g2", "job_developer_towers_p", "job_developer_towers_s",
            "job_frost_g1", "job_frost_p", "job_frost_s",
            "job_future_agency_towers_g1", "job_future_agency_towers_s", "job_gamble_g1",
            "job_gamble_g2", "job_gamble_p", "job_gamble_s",
            "job_hero_party_g1", "job_hero_party_p", "job_insect_towers_g2",
            "job_legion_towers_g1", "job_legion_towers_p", "job_mage_towers_g1",
            "job_mage_towers_s", "job_magic_school_g1", "job_magic_school_g2",
            "job_magic_school_p", "job_magic_school_s", "job_ocean_g1",
            "job_ocean_p", "job_ocean_s", "job_pet_towers_g2",
            "job_pet_towers_p", "job_pet_towers_s", "job_pirate_g1",
            "job_pirate_p", "job_pirate_s", "job_plant_towers_g1",
            "job_plant_towers_p", "job_plant_towers_s", "job_queen_towers_g2",
            "job_queen_towers_s", "job_resonance_towers_g1", "job_resonance_towers_s",
            "job_succubus_g2", "job_thunder_g1", "job_undead_towers_p",
            "job_villager_adv_towers_g1", "job_villager_adv_towers_g2", "job_villager_adv_towers_s",
            "job_villager_towers_p", "job_warlock_towers_p", "low_pressure_high_yield",
            "one_man_show", "reserve_diamonds_gold", "reserve_diamonds_prismatic",
            "reserve_diamonds_silver", "reserve_income_gold", "reserve_income_prismatic",
            "reserve_income_silver", "reserve_production_gold", "reserve_production_prismatic",
            "reserve_production_silver", "starlight_cocoon_call", "support_performance",
            "triangle_formation", "twin_squadron", "wartime_economy");
    private final String label;

    AugmentDisplayRole(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    static boolean hasClassification(AugmentDefinition card) {
        String id = card.id().substring(card.id().indexOf(':') + 1);
        return ATTACK_CARDS.contains(id) || DEFENSE_CARDS.contains(id) || OTHER_CARDS.contains(id)
                || id.matches("tactical_designation_[123]_(assault|cover)") || id.matches("finishing_fire_[123]");
    }

    public static AugmentDisplayRole of(AugmentDefinition card) {
        String id = card.id().substring(card.id().indexOf(':') + 1);
        if (id.startsWith("tactical_designation_") && id.endsWith("_cover")) return DEFENSE;
        if (id.startsWith("tactical_designation_") && id.endsWith("_assault")) return ATTACK;
        if (id.startsWith("finishing_fire_") || ATTACK_CARDS.contains(id)) return ATTACK;
        if (DEFENSE_CARDS.contains(id)) return DEFENSE;
        return OTHER;
    }
}
