package kim.biryeong.semiontd.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.MonsterDimensions;
import kim.biryeong.semiontd.summon.SummonAbilityActivation;
import kim.biryeong.semiontd.summon.SummonRole;
import kim.biryeong.semiontd.summon.SummonTier;

public record SummonConfig(Map<String, SummonDefinition> summons) {
    public SummonConfig {
        summons = summons == null ? Map.of() : copySummons(summons);
    }

    public static SummonConfig defaultConfig() {
        LinkedHashMap<String, SummonDefinition> summons = new LinkedHashMap<>();
        add(summons, def("chicken", "Chicken", "minecraft:chicken", 20, 1, 1, SummonTier.T1, roles(SummonRole.SWARM), AttackKind.MELEE, DamageType.PHYSICAL, 18, 0, 0, 1, 0.4, 0.7, acts(SummonAbilityActivation.PASSIVE), Map.of(), "가장 약한 최저가 물량 인컴 유닛입니다."));
        add(summons, def("rabbit", "Rabbit", "minecraft:rabbit", 40, 2, 2, SummonTier.T1, roles(SummonRole.RUSH, SummonRole.SWARM), AttackKind.MELEE, DamageType.PHYSICAL, 26, 0, 0, 2, 0.4, 0.5, acts(SummonAbilityActivation.PASSIVE), Map.of(), "빠른 이동으로 타겟을 분산시키는 초반 유닛입니다."));
        add(summons, def("silverfish", "Silverfish", "minecraft:silverfish", 40, 2, 2, SummonTier.T1, roles(SummonRole.SWARM), AttackKind.MELEE, DamageType.PHYSICAL, 24, 0, 0, 2, 0.4, 0.3, acts(SummonAbilityActivation.PASSIVE), Map.of(), "작은 크기로 초반 화력을 분산시키는 물량 유닛입니다."));
        add(summons, def("zombie", "Zombie", "minecraft:zombie", 40, 2, 2, SummonTier.T1, roles(SummonRole.RUSH), AttackKind.MELEE, DamageType.PHYSICAL, 42, 1, 0, 3, 0.6, 1.95, acts(SummonAbilityActivation.PASSIVE), Map.of(), "기준이 되는 초반 근접 인컴 유닛입니다."));
        add(summons, def("husk", "Husk", "minecraft:husk", 60, 3, 3, SummonTier.T1, roles(SummonRole.TANK), AttackKind.MELEE, DamageType.PHYSICAL, 58, 3, 0, 4, 0.6, 1.95, acts(SummonAbilityActivation.PASSIVE), Map.of(), "초반에 조금 더 오래 버티는 약한 탱커입니다."));
        add(summons, def("skeleton", "Skeleton", "minecraft:skeleton", 60, 3, 3, SummonTier.T1, roles(SummonRole.RUSH), AttackKind.RANGED, DamageType.PHYSICAL, 36, 0, 0, 4, 0.6, 1.95, acts(SummonAbilityActivation.PASSIVE), Map.of(), "접근 전부터 피해를 누적하는 초반 원거리 유닛입니다."));
        add(summons, def("wolf", "Wolf", "minecraft:wolf", 60, 2, 3, SummonTier.T1, roles(SummonRole.DISRUPTOR), AttackKind.MELEE, DamageType.PHYSICAL, 52, 1, 0, 5, 0.6, 0.85, acts(SummonAbilityActivation.CONDITIONAL), Map.of("radius", 5.0, "magnitude", 0.10, "durationTicks", 60.0, "cooldownTicks", 80.0, "maxTargets", 1.0), "가까운 타워의 공격 속도를 짧게 낮춥니다."));
        add(summons, def("spider", "Spider", "minecraft:spider", 80, 4, 4, SummonTier.T1, roles(SummonRole.RUSH), AttackKind.MELEE, DamageType.PHYSICAL, 70, 1, 0, 5, 1.4, 0.9, acts(SummonAbilityActivation.PASSIVE), Map.of(), "넓은 충돌 크기와 속도로 초반 압박을 넣습니다."));
        add(summons, def("cave_spider", "Cave Spider", "minecraft:cave_spider", 80, 4, 4, SummonTier.T1, roles(SummonRole.DISRUPTOR), AttackKind.MELEE, DamageType.PHYSICAL, 60, 1, 0, 4, 0.7, 0.5, acts(SummonAbilityActivation.CONDITIONAL), Map.of("radius", 5.0, "magnitude", 0.08, "durationTicks", 80.0, "cooldownTicks", 90.0, "maxTargets", 1.0), "가까운 타워의 공격 속도를 약하게 방해합니다."));
        add(summons, def("bee", "Bee", "minecraft:bee", 100, 5, 4, SummonTier.T1, roles(SummonRole.RUSH), AttackKind.MELEE, DamageType.PHYSICAL, 82, 0, 2, 6, 0.7, 0.6, acts(SummonAbilityActivation.PASSIVE), Map.of(), "공중 느낌의 고기동 초반 인컴 유닛입니다."));
        add(summons, def("turtle", "Turtle", "minecraft:turtle", 100, 4, 5, SummonTier.T2, roles(SummonRole.TANK, SummonRole.SUPPORT), AttackKind.MELEE, DamageType.PHYSICAL, 150, 8, 0, 3, 1.2, 0.4, acts(SummonAbilityActivation.COOLDOWN), Map.of("radius", 5.5, "magnitude", 0.25, "durationTicks", 80.0, "cooldownTicks", 60.0, "maxTargets", 8.0), "주변 아군 인컴 유닛의 타워 피해를 줄입니다."));
        add(summons, def("sheep", "Sheep", "minecraft:sheep", 100, 5, 4, SummonTier.T2, roles(SummonRole.SWARM), AttackKind.MELEE, DamageType.PHYSICAL, 88, 1, 0, 3, 0.9, 1.3, acts(SummonAbilityActivation.PASSIVE), Map.of(), "전투력은 낮지만 수입 효율이 좋은 중가 물량입니다."));
        add(summons, def("zombie_villager", "Zombie Villager", "minecraft:zombie_villager", 120, 6, 6, SummonTier.T2, roles(SummonRole.TANK), AttackKind.MELEE, DamageType.PHYSICAL, 125, 3, 2, 6, 0.6, 1.95, acts(SummonAbilityActivation.PASSIVE), Map.of(), "T2 기준의 안정적인 근접 탱커입니다."));
        add(summons, def("stray", "Stray", "minecraft:stray", 120, 5, 6, SummonTier.T2, roles(SummonRole.DISRUPTOR), AttackKind.RANGED, DamageType.PHYSICAL, 90, 1, 2, 6, 0.6, 1.95, acts(SummonAbilityActivation.CONDITIONAL), Map.of("radius", 6.0, "magnitude", 0.12, "durationTicks", 80.0, "cooldownTicks", 90.0, "maxTargets", 1.0), "원거리에서 타워 공격 속도를 낮춥니다."));
        add(summons, def("allay", "Allay", "minecraft:allay", 140, 6, 6, SummonTier.T2, roles(SummonRole.SUPPORT), AttackKind.RANGED, DamageType.MAGIC, 78, 0, 6, 2, 0.6, 0.95, acts(SummonAbilityActivation.COOLDOWN), Map.of("radius", 6.0, "healAmount", 8.0, "maxTargets", 6.0, "cooldownTicks", 120.0), "주변 아군 인컴 유닛을 광역 회복합니다."));
        add(summons, def("vex", "Vex", "minecraft:vex", 140, 7, 6, SummonTier.T2, roles(SummonRole.RUSH, SummonRole.DISRUPTOR), AttackKind.MELEE, DamageType.MAGIC, 86, 2, 6, 8, 0.4, 0.8, acts(SummonAbilityActivation.PASSIVE), Map.of(), "빠른 속도와 마법 피해로 진형을 흔듭니다."));
        add(summons, def("fox", "Fox", "minecraft:fox", 160, 7, 7, SummonTier.T2, roles(SummonRole.SUPPORT), AttackKind.MELEE, DamageType.PHYSICAL, 98, 1, 0, 6, 0.6, 0.7, acts(SummonAbilityActivation.COOLDOWN), Map.of("radius", 6.0, "magnitude", 0.25, "durationTicks", 80.0, "cooldownTicks", 60.0, "maxTargets", 8.0), "주변 아군 인컴 유닛의 공격력을 높입니다."));
        add(summons, def("slime", "Slime", "minecraft:slime", 160, 8, 7, SummonTier.T2, roles(SummonRole.SWARM, SummonRole.TANK), AttackKind.MELEE, DamageType.PHYSICAL, 145, 2, 0, 6, 1.2, 1.2, acts(SummonAbilityActivation.PASSIVE), Map.of(), "분열 없이 단일 탱커 물량으로 처리됩니다."));
        add(summons, def("goat", "Goat", "minecraft:goat", 180, 9, 8, SummonTier.T2, roles(SummonRole.DISRUPTOR), AttackKind.MELEE, DamageType.PHYSICAL, 135, 3, 0, 9, 0.9, 1.3, acts(SummonAbilityActivation.COOLDOWN), Map.of("radius", 5.5, "magnitude", 0.15, "durationTicks", 60.0, "cooldownTicks", 90.0, "maxTargets", 1.0), "가까운 타워의 공격 템포를 방해합니다."));
        add(summons, def("bogged", "Bogged", "minecraft:bogged", 180, 8, 8, SummonTier.T2, roles(SummonRole.DISRUPTOR), AttackKind.RANGED, DamageType.PHYSICAL, 115, 2, 3, 7, 0.6, 1.95, acts(SummonAbilityActivation.CONDITIONAL), Map.of("radius", 6.0, "magnitude", 0.12, "durationTicks", 100.0, "cooldownTicks", 90.0, "maxTargets", 1.0), "원거리 독 컨셉으로 타워 공격 속도를 낮춥니다."));
        add(summons, def("pillager", "Pillager", "minecraft:pillager", 180, 9, 8, SummonTier.T3, roles(SummonRole.RUSH), AttackKind.RANGED, DamageType.PHYSICAL, 115, 2, 0, 8, 0.6, 1.95, acts(SummonAbilityActivation.PASSIVE), Map.of(), "중반 원거리 기준 인컴 유닛입니다."));
        add(summons, def("piglin_brute", "Piglin Brute", "minecraft:piglin_brute", 180, 9, 8, SummonTier.T3, roles(SummonRole.RUSH), AttackKind.MELEE, DamageType.PHYSICAL, 145, 4, 0, 12, 0.6, 1.95, acts(SummonAbilityActivation.PASSIVE), Map.of(), "강한 단일 근접 압박을 제공합니다."));
        add(summons, def("ravager", "Ravager", "minecraft:ravager", 200, 10, 10, SummonTier.T3, roles(SummonRole.TANK), AttackKind.MELEE, DamageType.PHYSICAL, 260, 10, 2, 15, 1.95, 1.35, acts(SummonAbilityActivation.PASSIVE), Map.of(), "대형 체력과 방어를 가진 중반 딜탱입니다."));
        add(summons, def("hoglin", "Hoglin", "minecraft:hoglin", 200, 10, 9, SummonTier.T3, roles(SummonRole.RUSH, SummonRole.TANK), AttackKind.MELEE, DamageType.PHYSICAL, 210, 6, 0, 16, 1.4, 1.4, acts(SummonAbilityActivation.PASSIVE), Map.of(), "빠르고 공격적인 대형 근접 유닛입니다."));
        add(summons, def("horse", "Horse", "minecraft:horse", 250, 12, 10, SummonTier.T3, roles(SummonRole.RUSH, SummonRole.DISRUPTOR), AttackKind.MELEE, DamageType.PHYSICAL, 175, 4, 0, 10, 1.4, 1.6, acts(SummonAbilityActivation.CONDITIONAL), Map.of("radius", 6.0, "magnitude", 0.10, "durationTicks", 60.0, "cooldownTicks", 80.0, "maxTargets", 1.0), "빠르게 전진하며 가까운 타워 사거리를 낮춥니다."));
        add(summons, def("llama", "Llama", "minecraft:llama", 250, 12, 10, SummonTier.T3, roles(SummonRole.SUPPORT, SummonRole.RUSH), AttackKind.RANGED, DamageType.PHYSICAL, 155, 3, 2, 9, 0.9, 1.87, acts(SummonAbilityActivation.PASSIVE), Map.of(), "원거리 지원 성향의 중반 동물 유닛입니다."));
        add(summons, def("phantom", "Phantom", "minecraft:phantom", 280, 13, 10, SummonTier.T3, roles(SummonRole.RUSH), AttackKind.MELEE, DamageType.PHYSICAL, 165, 2, 4, 12, 0.9, 0.5, acts(SummonAbilityActivation.PASSIVE), Map.of(), "공중 시각을 가진 일반 경로 압박 유닛입니다."));
        add(summons, def("enderman", "Enderman", "minecraft:enderman", 300, 30, 12, SummonTier.T3, roles(SummonRole.SIEGE), AttackKind.MELEE, DamageType.MAGIC, 260, 4, 10, 22, 0.6, 2.9, acts(SummonAbilityActivation.PASSIVE), Map.of(), "고수입 선택지로 강한 단일 압박을 제공합니다."));
        add(summons, def("breeze", "Breeze", "minecraft:breeze", 300, 14, 12, SummonTier.T3, roles(SummonRole.DISRUPTOR), AttackKind.RANGED, DamageType.MAGIC, 190, 3, 12, 13, 0.6, 1.77, acts(SummonAbilityActivation.COOLDOWN), Map.of("radius", 7.0, "magnitude", 0.15, "durationTicks", 60.0, "cooldownTicks", 80.0, "maxTargets", 2.0), "풍압 컨셉으로 주변 타워 공격 속도를 낮춥니다."));
        add(summons, def("guardian", "Guardian", "minecraft:guardian", 320, 15, 12, SummonTier.T3, roles(SummonRole.SIEGE), AttackKind.RANGED, DamageType.MAGIC, 230, 6, 14, 16, 0.85, 0.85, acts(SummonAbilityActivation.CONDITIONAL), Map.of("progressThreshold", 0.70, "bonusDamage", 15.0, "cooldownTicks", 80.0), "진행도가 높을 때 방어 대상에게 추가 고정 피해를 줍니다."));
        add(summons, def("polar_bear", "Polar Bear", "minecraft:polar_bear", 350, 17, 14, SummonTier.T4, roles(SummonRole.TANK), AttackKind.MELEE, DamageType.PHYSICAL, 410, 13, 4, 20, 1.4, 1.4, acts(SummonAbilityActivation.PASSIVE), Map.of(), "후반 대형 탱커입니다."));
        add(summons, def("magma_cube", "Magma Cube", "minecraft:magma_cube", 380, 18, 14, SummonTier.T4, roles(SummonRole.SWARM, SummonRole.TANK), AttackKind.MELEE, DamageType.MAGIC, 360, 9, 12, 18, 1.4, 1.4, acts(SummonAbilityActivation.PASSIVE), Map.of(), "분열 없이 마법 저항이 높은 단일 유닛입니다."));
        add(summons, def("ocelot", "Ocelot", "minecraft:ocelot", 400, 18, 15, SummonTier.T4, roles(SummonRole.SUPPORT, SummonRole.RUSH), AttackKind.MELEE, DamageType.PHYSICAL, 205, 3, 3, 10, 0.6, 0.7, acts(SummonAbilityActivation.COOLDOWN), Map.of("radius", 7.0, "magnitude", 0.30, "durationTicks", 80.0, "cooldownTicks", 60.0, "maxTargets", 8.0), "주변 아군 인컴 유닛의 이동속도를 높입니다."));
        add(summons, def("vindicator", "Vindicator", "minecraft:vindicator", 420, 19, 15, SummonTier.T4, roles(SummonRole.DISRUPTOR), AttackKind.MELEE, DamageType.PHYSICAL, 285, 8, 2, 24, 0.6, 1.95, acts(SummonAbilityActivation.CONDITIONAL), Map.of("radius", 6.5, "magnitude", 0.18, "durationTicks", 80.0, "cooldownTicks", 80.0, "maxTargets", 1.0), "강한 근접 교란으로 타워 공격 속도를 방해합니다."));
        add(summons, def("witch", "Witch", "minecraft:witch", 450, 19, 16, SummonTier.T4, roles(SummonRole.SUPPORT, SummonRole.DISRUPTOR), AttackKind.RANGED, DamageType.MAGIC, 265, 3, 16, 12, 0.6, 1.95, acts(SummonAbilityActivation.COOLDOWN), Map.of("radius", 7.0, "moveMagnitude", 0.30, "attackMagnitude", 0.25, "attackSpeedMagnitude", 0.25, "durationTicks", 80.0, "cooldownTicks", 60.0, "maxTargets", 8.0), "주변 아군 인컴 유닛의 공격과 이동 템포를 올립니다."));
        add(summons, def("iron_golem", "Iron Golem", "minecraft:iron_golem", 460, 20, 16, SummonTier.T4, roles(SummonRole.TANK), AttackKind.MELEE, DamageType.PHYSICAL, 520, 18, 5, 30, 1.4, 2.2, acts(SummonAbilityActivation.PASSIVE), Map.of(), "느리지만 단단한 중후반 고방어 탱커입니다."));
        add(summons, def("blaze", "Blaze", "minecraft:blaze", 500, 22, 18, SummonTier.T4, roles(SummonRole.SIEGE), AttackKind.RANGED, DamageType.MAGIC, 350, 5, 16, 28, 0.6, 1.8, acts(SummonAbilityActivation.CONDITIONAL), Map.of("progressThreshold", 0.65, "bonusDamage", 25.0, "cooldownTicks", 80.0), "후반 진행도에서 방어 대상에게 추가 고정 피해를 줍니다."));
        add(summons, def("shulker", "Shulker", "minecraft:shulker", 520, 23, 18, SummonTier.T4, roles(SummonRole.DISRUPTOR, SummonRole.TANK), AttackKind.RANGED, DamageType.MAGIC, 430, 16, 14, 18, 1.0, 1.0, acts(SummonAbilityActivation.COOLDOWN), Map.of("radius", 7.0, "magnitude", 0.15, "durationTicks", 80.0, "cooldownTicks", 90.0, "maxTargets", 2.0), "가까운 타워들의 사거리를 낮춥니다."));
        add(summons, def("ghast", "Ghast", "minecraft:ghast", 560, 25, 20, SummonTier.T4, roles(SummonRole.SIEGE), AttackKind.RANGED, DamageType.MAGIC, 460, 4, 18, 34, 2.0, 2.0, acts(SummonAbilityActivation.CONDITIONAL), Map.of("progressThreshold", 0.75, "bonusDamage", 30.0, "cooldownTicks", 90.0), "후반 라인에서 강한 공성 고정 피해를 추가합니다."));
        add(summons, def("zoglin", "Zoglin", "minecraft:zoglin", 600, 30, 22, SummonTier.T5, roles(SummonRole.TANK, SummonRole.RUSH), AttackKind.MELEE, DamageType.PHYSICAL, 650, 16, 6, 38, 1.4, 1.4, acts(SummonAbilityActivation.PASSIVE), Map.of(), "빠른 최종 근접 탱커입니다."));
        add(summons, def("wither_skeleton", "Wither Skeleton", "minecraft:wither_skeleton", 650, 32, 24, SummonTier.T5, roles(SummonRole.DISRUPTOR, SummonRole.SIEGE), AttackKind.MELEE, DamageType.MAGIC, 620, 14, 18, 34, 0.7, 2.4, acts(SummonAbilityActivation.CONDITIONAL), Map.of("progressThreshold", 0.70, "bonusDamage", 35.0, "cooldownTicks", 80.0), "위더 컨셉의 후반 교란/공성 유닛입니다."));
        add(summons, def("evoker", "Evoker", "minecraft:evoker", 680, 33, 24, SummonTier.T5, roles(SummonRole.SUPPORT, SummonRole.DISRUPTOR), AttackKind.RANGED, DamageType.MAGIC, 560, 8, 24, 26, 0.6, 1.95, acts(SummonAbilityActivation.COOLDOWN), Map.of("radius", 8.0, "attackMagnitude", 0.25, "damageReductionMagnitude", 0.25, "durationTicks", 80.0, "cooldownTicks", 60.0, "maxTargets", 10.0), "주변 아군 인컴 유닛을 공격과 방어 양쪽으로 강화합니다."));
        add(summons, def("elder_guardian", "Elder Guardian", "minecraft:elder_guardian", 700, 34, 26, SummonTier.T5, roles(SummonRole.TANK, SummonRole.DISRUPTOR), AttackKind.RANGED, DamageType.MAGIC, 760, 22, 28, 32, 2.0, 2.0, acts(SummonAbilityActivation.COOLDOWN), Map.of("radius", 8.0, "attackSpeedMagnitude", 0.30, "rangeMagnitude", 0.20, "durationTicks", 100.0, "cooldownTicks", 80.0, "maxTargets", 3.0), "여러 타워의 공격 속도와 사거리를 동시에 낮춥니다."));
        add(summons, def("warden", "Warden", "minecraft:warden", 800, 40, 30, SummonTier.T5, roles(SummonRole.TANK, SummonRole.SIEGE), AttackKind.MELEE, DamageType.PHYSICAL, 1050, 28, 22, 46, 1.55, 2.6, acts(SummonAbilityActivation.CONDITIONAL), Map.of("progressThreshold", 0.70, "bonusDamage", 50.0, "cooldownTicks", 90.0), "최종 고체력 탱커이자 보스 압박 유닛입니다."));
        return new SummonConfig(summons);
    }

    public SummonConfig withMissingDefaults(SummonConfig defaults) {
        if (defaults == null || defaults.summons().isEmpty()) {
            return this;
        }
        LinkedHashMap<String, SummonDefinition> merged = new LinkedHashMap<>(summons);
        boolean changed = false;
        for (Map.Entry<String, SummonDefinition> entry : defaults.summons().entrySet()) {
            if (!merged.containsKey(entry.getKey())) {
                merged.put(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        return changed ? new SummonConfig(merged) : this;
    }

    private static SummonDefinition def(
            String id,
            String displayName,
            String entityTypeId,
            long emeraldCost,
            long incomeGain,
            long diamondReward,
            SummonTier tier,
            List<SummonRole> roles,
            AttackKind attackKind,
            DamageType damageType,
            double maxHealth,
            double armor,
            double resistance,
            double attackDamage,
            double width,
            double height,
            List<SummonAbilityActivation> abilityActivations,
            Map<String, Double> abilityValues,
            String description
    ) {
        return new SummonDefinition(
                id,
                displayName,
                true,
                emeraldCost,
                incomeGain,
                maxHealth,
                armor,
                resistance,
                attackDamage,
                attackKind,
                damageType,
                entityTypeId,
                "",
                new DimensionConfig(width, height),
                diamondReward,
                tier,
                roles,
                abilityActivations,
                List.of(description),
                abilityValues
        );
    }

    private static void add(LinkedHashMap<String, SummonDefinition> summons, SummonDefinition definition) {
        summons.put(definition.id(), definition);
    }

    private static List<SummonRole> roles(SummonRole... roles) {
        return List.of(roles);
    }

    private static List<SummonAbilityActivation> acts(SummonAbilityActivation... activations) {
        return List.of(activations);
    }

    private static Map<String, SummonDefinition> copySummons(Map<String, SummonDefinition> input) {
        LinkedHashMap<String, SummonDefinition> copy = new LinkedHashMap<>();
        for (Map.Entry<String, SummonDefinition> entry : input.entrySet()) {
            SummonDefinition value = entry.getValue();
            if (entry.getKey() == null || entry.getKey().isBlank() || value == null) {
                continue;
            }
            copy.put(entry.getKey(), value.withId(entry.getKey()));
        }
        return Collections.unmodifiableMap(copy);
    }

    public record SummonDefinition(
            String id,
            String displayName,
            boolean enabled,
            long emeraldCost,
            long incomeGain,
            double maxHealth,
            double armor,
            double resistance,
            double attackDamage,
            AttackKind attackKind,
            DamageType damageType,
            String entityTypeId,
            String blockbenchModelId,
            DimensionConfig dimensions,
            long diamondReward,
            SummonTier tier,
            List<SummonRole> roles,
            List<SummonAbilityActivation> abilityActivations,
            List<String> description,
            Map<String, Double> abilityValues
    ) {
        public SummonDefinition {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Summon id cannot be blank.");
            }
            if (emeraldCost < 0 || incomeGain < 0 || maxHealth <= 0.0 || armor < 0.0
                    || resistance < 0.0 || attackDamage < 0.0 || diamondReward < 0) {
                throw new IllegalArgumentException("Summon numeric values are invalid: " + id);
            }
            displayName = displayName == null || displayName.isBlank() ? id : displayName;
            attackKind = attackKind == null ? AttackKind.MELEE : attackKind;
            damageType = damageType == null ? DamageType.PHYSICAL : damageType;
            entityTypeId = entityTypeId == null || entityTypeId.isBlank() ? "minecraft:zombie" : entityTypeId;
            blockbenchModelId = blockbenchModelId == null || blockbenchModelId.isBlank() ? null : blockbenchModelId;
            dimensions = dimensions == null ? DimensionConfig.defaultConfig() : dimensions;
            tier = tier == null ? SummonTier.T1 : tier;
            roles = roles == null || roles.isEmpty() ? List.of(SummonRole.RUSH) : List.copyOf(roles);
            abilityActivations = abilityActivations == null || abilityActivations.isEmpty()
                    ? List.of(SummonAbilityActivation.PASSIVE)
                    : List.copyOf(abilityActivations);
            description = description == null ? List.of() : List.copyOf(description);
            abilityValues = abilityValues == null ? Map.of() : Map.copyOf(abilityValues);
        }

        public SummonDefinition withId(String id) {
            if (this.id.equals(id)) {
                return this;
            }
            return new SummonDefinition(
                    id,
                    displayName,
                    enabled,
                    emeraldCost,
                    incomeGain,
                    maxHealth,
                    armor,
                    resistance,
                    attackDamage,
                    attackKind,
                    damageType,
                    entityTypeId,
                    blockbenchModelId,
                    dimensions,
                    diamondReward,
                    tier,
                    roles,
                    abilityActivations,
                    description,
                    abilityValues
            );
        }

        public MonsterDimensions monsterDimensions() {
            return MonsterDimensions.of(dimensions.width(), dimensions.height());
        }
    }

    public record DimensionConfig(double width, double height) {
        public DimensionConfig {
            if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0.0 || height <= 0.0) {
                throw new IllegalArgumentException("Summon dimensions must be finite positive values.");
            }
        }

        public static DimensionConfig defaultConfig() {
            return new DimensionConfig(MonsterDimensions.DEFAULT_WIDTH, MonsterDimensions.DEFAULT_HEIGHT);
        }
    }
}
