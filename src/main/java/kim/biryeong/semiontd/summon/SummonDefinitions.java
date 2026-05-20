package kim.biryeong.semiontd.summon;

import static kim.biryeong.semiontd.summon.SummonDefinition.summon;

import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.MonsterDimensions;

final class SummonDefinitions {
    static final SummonDefinition GRUNT = summon("grunt", SummonDisplayNames.FOX_KIT)
            .economy(20, 2, 5)
            .combat(50, 0, 4, AttackKind.MELEE, DamageType.PHYSICAL, 0)
            .visual("minecraft:zombie", "semion-td:summon/t1_fox_kit")
            .tier(SummonTier.T1)
            .roles(SummonRole.RUSH)
            .abilities(SummonAbilityActivation.PASSIVE)
            .build();

    static final SummonDefinition SKITTER_SWARM = summon("skitter_swarm", SummonDisplayNames.HONEY_BEE)
            .economy(30, 3, 3)
            .combat(24, 0, 2, AttackKind.MELEE, DamageType.PHYSICAL, 0)
            .visual("minecraft:silverfish", "semion-td:summon/t1_honey_bee")
            .tier(SummonTier.T1)
            .roles(SummonRole.SWARM)
            .abilities(SummonAbilityActivation.PASSIVE)
            .build();

    static final SummonDefinition QUILT_GUARD = summon("quilt_guard", SummonDisplayNames.SHELL_TURTLE)
            .economy(35, 3, 6)
            .combat(88, 4, 2, AttackKind.MELEE, DamageType.PHYSICAL, 1)
            .visual("minecraft:husk", "semion-td:summon/t1_shell_turtle")
            .dimensions(MonsterDimensions.of(0.75, 1.3))
            .tier(SummonTier.T1)
            .roles(SummonRole.TANK)
            .abilities(SummonAbilityActivation.PASSIVE)
            .build();

    static final SummonDefinition STATIC_BOBBIN = summon("static_bobbin", SummonDisplayNames.SPARK_AXOLOTL)
            .economy(25, 2, 4)
            .combat(34, 0, 1, AttackKind.RANGED, DamageType.MAGIC, 2)
            .visual("minecraft:breeze", "semion-td:summon/t1_spark_axolotl")
            .dimensions(MonsterDimensions.of(0.6, 1.0))
            .tier(SummonTier.T1)
            .roles(SummonRole.DISRUPTOR)
            .abilities(SummonAbilityActivation.PASSIVE)
            .build();

    static final SummonDefinition BUTTON_NURSE = summon("button_nurse", SummonDisplayNames.MEDIC_DUCK)
            .economy(35, 3, 5)
            .combat(38, 0, 1, AttackKind.RANGED, DamageType.MAGIC, 2)
            .visual("minecraft:allay", "semion-td:summon/t1_medic_duck")
            .dimensions(MonsterDimensions.of(0.6, 0.95))
            .tier(SummonTier.T1)
            .roles(SummonRole.SUPPORT)
            .abilities(SummonAbilityActivation.COOLDOWN)
            .build();

    static final SummonDefinition POPPER_POD = summon("popper_pod", SummonDisplayNames.PINCER_CRAB)
            .economy(35, 3, 7)
            .combat(68, 1, 6, AttackKind.MELEE, DamageType.PHYSICAL, 0)
            .visual("minecraft:armadillo", "semion-td:summon/t1_pincer_crab")
            .dimensions(MonsterDimensions.of(0.95, 0.9))
            .tier(SummonTier.T1)
            .roles(SummonRole.SIEGE)
            .abilities(SummonAbilityActivation.CONDITIONAL)
            .build();

    static final SummonDefinition IRONCLAD_TANK = summon("ironclad_tank", SummonDisplayNames.IRONCLAD_BOAR)
            .economy(70, 5, 8)
            .combat(130, 8, 5, AttackKind.MELEE, DamageType.PHYSICAL, 1)
            .visual("minecraft:husk", "semion-td:summon/t2_ironclad_boar")
            .tier(SummonTier.T2)
            .roles(SummonRole.TANK)
            .abilities(SummonAbilityActivation.PASSIVE)
            .build();

    static final SummonDefinition WARD_TANK = summon("ward_tank", SummonDisplayNames.WARD_RAM)
            .economy(75, 5, 8)
            .combat(115, 1, 4, AttackKind.MELEE, DamageType.MAGIC, 8)
            .visual("minecraft:zombie_villager", "semion-td:summon/t2_ward_ram")
            .tier(SummonTier.T2)
            .roles(SummonRole.TANK)
            .abilities(SummonAbilityActivation.PASSIVE)
            .build();

    static final SummonDefinition STATIC_DISRUPTOR = summon("static_disruptor", SummonDisplayNames.STATIC_OWL)
            .economy(65, 4, 7)
            .combat(55, 0, 3, AttackKind.RANGED, DamageType.MAGIC, 3)
            .visual("minecraft:witch", "semion-td:summon/t2_static_owl")
            .tier(SummonTier.T2)
            .roles(SummonRole.DISRUPTOR)
            .abilities(SummonAbilityActivation.COOLDOWN)
            .build();

    static final SummonDefinition PULSE_SUPPORT = summon("pulse_support", SummonDisplayNames.PULSE_FAWN)
            .economy(75, 5, 7)
            .combat(60, 0, 2, AttackKind.RANGED, DamageType.MAGIC, 4)
            .visual("minecraft:evoker", "semion-td:summon/t2_pulse_fawn")
            .tier(SummonTier.T2)
            .roles(SummonRole.SUPPORT)
            .abilities(SummonAbilityActivation.COOLDOWN)
            .build();

    static final SummonDefinition GALE_FERRET = summon("gale_ferret", SummonDisplayNames.GALE_FERRET)
            .economy(100, 7, 12)
            .combat(95, 1, 11, AttackKind.MELEE, DamageType.PHYSICAL, 2)
            .visual("minecraft:fox", "semion-td:summon/t3_gale_ferret")
            .tier(SummonTier.T3)
            .roles(SummonRole.RUSH)
            .abilities(SummonAbilityActivation.PASSIVE)
            .build();

    static final SummonDefinition BULWARK_BISON = summon("bulwark_bison", SummonDisplayNames.BULWARK_BISON)
            .economy(150, 10, 17)
            .combat(210, 12, 9, AttackKind.MELEE, DamageType.PHYSICAL, 4)
            .visual("minecraft:hoglin", "semion-td:summon/t3_bulwark_bison")
            .dimensions(MonsterDimensions.of(1.35, 1.15))
            .tier(SummonTier.T3)
            .roles(SummonRole.TANK)
            .abilities(SummonAbilityActivation.PASSIVE)
            .build();

    static final SummonDefinition WIZARD_CAT = summon("wizard_cat", SummonDisplayNames.WIZARD_CAT)
            .economy(125, 8, 14)
            .combat(67, 0, 6, AttackKind.RANGED, DamageType.MAGIC, 9)
            .visual("minecraft:cat", "semion-td:summon/t3_wizard_cat")
            .tier(SummonTier.T3)
            .roles(SummonRole.DISRUPTOR)
            .abilities(SummonAbilityActivation.COOLDOWN)
            .build();

    static final SummonDefinition GROVE_ALPACA = summon("grove_alpaca", SummonDisplayNames.GROVE_ALPACA)
            .economy(140, 9, 15)
            .combat(88, 1, 4, AttackKind.RANGED, DamageType.MAGIC, 7)
            .visual("minecraft:llama", "semion-td:summon/t3_grove_alpaca")
            .tier(SummonTier.T3)
            .roles(SummonRole.SUPPORT)
            .abilities(SummonAbilityActivation.COOLDOWN)
            .build();

    static final SummonDefinition STORM_LYNX = summon("storm_lynx", SummonDisplayNames.STORM_LYNX)
            .economy(190, 10, 22)
            .combat(133, 2, 15, AttackKind.MELEE, DamageType.PHYSICAL, 4)
            .visual("minecraft:ocelot", null)
            .tier(SummonTier.T4)
            .roles(SummonRole.RUSH)
            .abilities(SummonAbilityActivation.PASSIVE)
            .build();

    static final SummonDefinition AEGIS_GOLEM = summon("aegis_golem", SummonDisplayNames.AEGIS_GOLEM)
            .economy(270, 15, 31)
            .combat(378, 18, 13, AttackKind.MELEE, DamageType.PHYSICAL, 8)
            .visual("minecraft:iron_golem", null)
            .dimensions(MonsterDimensions.of(1.4, 2.2))
            .tier(SummonTier.T4)
            .roles(SummonRole.TANK)
            .abilities(SummonAbilityActivation.PASSIVE)
            .build();

    static final SummonDefinition NULL_IMP = summon("null_imp", SummonDisplayNames.NULL_IMP)
            .economy(220, 12, 25)
            .combat(119, 1, 9, AttackKind.RANGED, DamageType.MAGIC, 14)
            .visual("minecraft:vex", null)
            .tier(SummonTier.T4)
            .roles(SummonRole.DISRUPTOR)
            .abilities(SummonAbilityActivation.COOLDOWN)
            .build();

    static final SummonDefinition ELDER_SPRITE = summon("elder_sprite", SummonDisplayNames.ELDER_SPRITE)
            .economy(250, 14, 28)
            .combat(130, 1, 6, AttackKind.RANGED, DamageType.MAGIC, 12)
            .visual("minecraft:allay", null)
            .tier(SummonTier.T4)
            .roles(SummonRole.SUPPORT)
            .abilities(SummonAbilityActivation.COOLDOWN)
            .build();

    static final SummonDefinition BOMBARD_TOAD = summon("bombard_toad", SummonDisplayNames.BOMBARD_TOAD)
            .economy(310, 16, 35)
            .combat(266, 8, 21, AttackKind.RANGED, DamageType.PHYSICAL, 6)
            .visual("minecraft:frog", null)
            .dimensions(MonsterDimensions.of(1.25, 1.0))
            .tier(SummonTier.T4)
            .roles(SummonRole.SIEGE)
            .abilities(SummonAbilityActivation.CONDITIONAL)
            .build();

    static final SummonDefinition SIEGE_BREAKER = summon("siege_breaker", SummonDisplayNames.SIEGE_BREAKER)
            .economy(380, 18, 34)
            .combat(364, 14, 80, AttackKind.RANGED, DamageType.PHYSICAL, 4)
            .visual("minecraft:ravager", "semion-td:summon/t5_siege")
            .dimensions(MonsterDimensions.of(2.0, 1.35))
            .tier(SummonTier.T5)
            .roles(SummonRole.SIEGE)
            .abilities(SummonAbilityActivation.CONDITIONAL)
            .build();

    static final SummonDefinition APEX_WARDEN = summon("apex_warden", SummonDisplayNames.APEX_WARDEN)
            .economy(430, 19, 48)
            .combat(700, 22, 40, AttackKind.MELEE, DamageType.PHYSICAL, 18)
            .visual("minecraft:warden", null)
            .dimensions(MonsterDimensions.of(1.55, 2.6))
            .tier(SummonTier.T5)
            .roles(SummonRole.TANK, SummonRole.DISRUPTOR)
            .abilities(SummonAbilityActivation.PASSIVE, SummonAbilityActivation.COOLDOWN)
            .build();

    static final SummonDefinition ORACLE_PHOENIX = summon("oracle_phoenix", SummonDisplayNames.ORACLE_PHOENIX)
            .economy(470, 21, 52)
            .combat(392, 4, 17, AttackKind.RANGED, DamageType.MAGIC, 20)
            .visual("minecraft:blaze", null)
            .tier(SummonTier.T5)
            .roles(SummonRole.SUPPORT, SummonRole.DISRUPTOR)
            .abilities(SummonAbilityActivation.COOLDOWN)
            .build();

    private SummonDefinitions() {
    }
}
