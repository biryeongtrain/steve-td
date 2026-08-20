package kim.biryeong.semiontd.tower.demonlord;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Tower types of the demon lord builder: ten skills, four tiers each.
 *
 * <p>Every one of these towers has zero damage, zero range and zero aggro. They are altars, not
 * guns - the whole point of the builder is that the player fights in person and the towers only
 * decide which skills are in the hotbar. Because they never fight, they are also invulnerable and
 * invisible to monster targeting (see {@link DemonLordSkillTower}).
 *
 * <p>Only one tower per skill can exist at a time; the shop hides a skill once its altar is up.
 * Tiers raise skill power and shave one second off the cooldown each step.
 */
public final class DemonLordTowers {
    public static final String GLOBAL_CONFIG_ID = "demon_lord_global";

    /** Must stay above the tower tables: the factory below fills them during class init. */
    private static final Map<String, Definition> DEFINITIONS = new HashMap<>();

    private static final String NO_COMBAT_LINE =
            "<red>이 타워는 공격도, 방어도, 어그로도 없습니다. 마왕 본인이 싸웁니다.</red>";

    /** 표기된 피해는 레벨 1 기준입니다. 실제 피해는 레벨 배율이 곱해집니다. */
    private static final String LEVEL_SCALING_LINE =
            "<gray>표기 피해는 <yellow>레벨 1</yellow> 기준이며, 레벨이 오르면 함께 증가합니다.</gray>";

    private static final Map<DemonLordSkill, List<TowerType>> TOWERS = new EnumMap<>(DemonLordSkill.class);

    static {
        register(
                DemonLordSkill.WAVE_OF_MALICE,
                Blocks.CRYING_OBSIDIAN,
                new long[] {55, 130, 240, 380},
                new double[] {100, 200, 350, 550},
                List.of(
                        "<gray>전방 <aqua>{ability.coneDegrees:number}도</aqua> 부채꼴, 사거리 "
                                + "<aqua>{ability.range:blocks}</aqua>를 쓸어버립니다.</gray>",
                        "<green>범위 안의 모든 적에게 <yellow>{ability.damage:number}</yellow> 피해를 주고 "
                                + "뒤로 밀어냅니다.</green>",
                        "<yellow>넓게 퍼진 무리를 한 번에 정리하는 기본기입니다.</yellow>"
                )
        );
        register(
                DemonLordSkill.DEMON_WINGS,
                Blocks.SOUL_LANTERN,
                new long[] {40, 100, 190, 300},
                new double[] {90, 180, 320, 500},
                List.of(
                        "<gray>바라보는 방향으로 도약합니다.</gray>",
                        "<green>주위 <aqua>{ability.radius:blocks}</aqua> 안의 적에게 "
                                + "<yellow>{ability.damage:number}</yellow> 피해를 주고 밀어냅니다.</green>",
                        "<yellow>포위를 빠져나오며 진형을 다시 잡는 이동기입니다.</yellow>"
                )
        );
        register(
                DemonLordSkill.SKY_BREAKER,
                Blocks.RESPAWN_ANCHOR,
                new long[] {75, 170, 300, 460},
                new double[] {110, 220, 380, 600},
                List.of(
                        "<gray>전방 <aqua>{ability.dashDistance:blocks}</aqua>를 돌진합니다.</gray>",
                        "<green>경로 반경 <aqua>{ability.hitRadius:blocks}</aqua> 안의 적을 하늘로 띄우고 "
                                + "<yellow>{ability.damage:number}</yellow> 피해를 줍니다.</green>",
                        "<green>적중한 적은 <aqua>{ability.stunTicks:seconds}</aqua>간 기절해 이동도 공격도 못 합니다.</green>",
                        "<yellow>적진을 가르며 여러 적을 띄우는 돌진 광역 제어기입니다.</yellow>"
                )
        );
        register(
                DemonLordSkill.ARCANE_BOMBARDMENT,
                Blocks.MAGMA_BLOCK,
                new long[] {75, 170, 300, 460},
                new double[] {105, 210, 360, 570},
                List.of(
                        "<gray>공중으로 솟아오른 뒤 <aqua>{ability.castDelayTicks:seconds}</aqua> 후 "
                                + "정점에서 포격합니다.</gray>",
                        "<green>최대 <aqua>{ability.projectileRange:blocks}</aqua> 앞 착탄 지점 반경 "
                                + "<aqua>{ability.blastRadius:blocks}</aqua>에 "
                                + "<yellow>{ability.damage:number}</yellow> 광역 피해를 줍니다.</green>",
                        "<gray>조준은 시전 순간이 아니라 <yellow>발사 순간의 시선</yellow>을 씁니다.</gray>",
                        "<yellow>거리를 두고 뭉친 무리를 때리는 원거리 기술입니다.</yellow>"
                )
        );
        register(
                DemonLordSkill.DEMON_BARRIER,
                Blocks.OBSIDIAN,
                new long[] {60, 140, 250, 390},
                new double[] {120, 240, 420, 660},
                List.of(
                        "<gray>최대 체력의 <aqua>{ability.shieldRatio:percent}</aqua>만큼 방어막을 두릅니다.</gray>",
                        "<green><aqua>{ability.shieldDurationTicks:seconds}</aqua> 동안 받는 피해를 대신 흡수합니다.</green>",
                        "<gray>중첩되지 않고 더 큰 쪽으로 갱신됩니다.</gray>",
                        "<yellow>쿨타임이 가장 길어 위험한 순간을 골라 써야 합니다.</yellow>"
                )
        );
        register(
                DemonLordSkill.HELLFIRE_BRAND,
                Blocks.SOUL_CAMPFIRE,
                new long[] {65, 150, 265, 410},
                new double[] {100, 200, 350, 550},
                List.of(
                        "<gray>바라보는 지점(최대 <aqua>{ability.placementRange:blocks}</aqua>)에 반경 "
                                + "<aqua>{ability.zoneRadius:blocks}</aqua>의 지옥불 낙인을 새깁니다.</gray>",
                        "<green><aqua>{ability.zoneDurationTicks:seconds}</aqua> 동안 위에 선 적이 "
                                + "<aqua>{ability.tickIntervalTicks:seconds}</aqua>마다 "
                                + "<yellow>{ability.damage:number}</yellow> 피해를 입습니다.</green>",
                        "<green>낙인 위의 적은 받는 피해가 <aqua>{ability.damageTakenBonus:percent}</aqua> 증가합니다.</green>",
                        "<yellow>길목을 미리 막아 두는 지속 장판입니다.</yellow>"
                )
        );
        register(
                DemonLordSkill.SOUL_DRAIN,
                Blocks.SOUL_SAND,
                new long[] {45, 110, 200, 315},
                new double[] {95, 190, 330, 520},
                List.of(
                        "<gray>전방 <aqua>{ability.range:blocks}</aqua> 직선 위의 적에게서 영혼을 뽑아냅니다.</gray>",
                        "<green>맞은 적마다 <yellow>{ability.damage:number}</yellow> 피해를 주고, "
                                + "입힌 피해의 <aqua>{ability.lifeStealRatio:percent}</aqua>를 체력으로 흡수합니다.</green>",
                        "<gray>흡수량은 한 번에 최대 체력의 <aqua>{ability.lifeStealCap:percent}</aqua>까지입니다.</gray>",
                        "<green>꿰뚫린 적은 <aqua>{ability.rootDurationTicks:seconds}</aqua>간 이동이 완전히 묶입니다. "
                                + "묶여도 공격은 계속합니다.</green>",
                        "<yellow>여럿을 꿰뚫을수록 많이 회복하는 지속력 기술입니다.</yellow>"
                )
        );
        register(
                DemonLordSkill.ROAR_OF_DREAD,
                Blocks.BELL,
                new long[] {60, 140, 250, 395},
                new double[] {110, 220, 380, 600},
                List.of(
                        "<gray>주위 <aqua>{ability.radius:blocks}</aqua>에 공포의 포효를 터뜨립니다.</gray>",
                        "<green>범위 안의 적을 밀어내고 <yellow>{ability.damage:number}</yellow> 피해를 줍니다.</green>",
                        "<green>맞은 적은 <aqua>{ability.dreadDurationTicks:seconds}</aqua> 동안 이동 속도가 "
                                + "<aqua>{ability.moveSpeedReduction:percent}</aqua> 느려지고 공격이 막힙니다.</green>",
                        "<yellow>포위됐을 때 판을 리셋하는 광역 제어기입니다.</yellow>"
                )
        );
        register(
                DemonLordSkill.GRIP_OF_DOOM,
                Blocks.WITHER_SKELETON_SKULL,
                new long[] {80, 180, 320, 490},
                new double[] {105, 210, 360, 570},
                List.of(
                        "<gray>정면 <aqua>{ability.range:blocks}</aqua> 안, 가장 가까운 적 "
                                + "<yellow>하나</yellow>를 움켜쥡니다.</gray>",
                        "<green>▶ 대상 체력이 <aqua>{ability.executeHealthRatio:percent}</aqua> 이하면 "
                                + "<red>즉사</red>시키고, 시체가 반경 "
                                + "<aqua>{ability.explosionRadius:blocks}</aqua>에서 터집니다.</green>",
                        "<green>　 폭발 피해 = 죽은 적 체력의 "
                                + "<yellow>{ability.explosionHealthRatio:percent}</yellow> + "
                                + "<yellow>{ability.areaDamage:number}</yellow>"
                                + "<dark_gray> · </dark_gray>쿨타임 "
                                + "<aqua>{ability.killRefundTicks:seconds}</aqua> 환급</green>",
                        "<gray>▶ 체력이 더 높으면 <yellow>{ability.damage:number}</yellow> 피해만 들어갑니다"
                                + "<dark_gray> (</dark_gray>대상이 잃은 체력의 "
                                + "<aqua>{ability.missingHealthRatio:percent}</aqua> 추가<dark_gray>)</dark_gray>.</gray>",
                        "<yellow>먼저 깎아 두고 끊는 마무리기입니다. 단단한 적일수록 크게 터집니다.</yellow>"
                )
        );
        register(
                DemonLordSkill.HELL_GUILLOTINE,
                Blocks.ANVIL,
                new long[] {70, 160, 285, 440},
                new double[] {105, 210, 360, 570},
                List.of(
                        "<gray>바라보는 지점(최대 <aqua>{ability.range:blocks}</aqua>)으로 순간이동해 내리찍습니다.</gray>",
                        "<green>착지 지점 반경 <aqua>{ability.radius:blocks}</aqua>에 "
                                + "<yellow>{ability.damage:number}</yellow> 광역 피해를 줍니다.</green>",
                        "<red>내 체력이 낮을수록 피해가 커집니다. 빈사 상태에서 최대 "
                                + "<yellow>+{ability.missingHealthDamageBonus:percent}</yellow>.</red>",
                        "<yellow>몰릴수록 강해지는 역전기이자, 순간이동이라 탈출로도 씁니다.</yellow>"
                )
        );
    }

    private DemonLordTowers() {
    }

    /** Every demon lord tower, tier 1 first within each skill. */
    public static List<TowerType> all() {
        List<TowerType> all = new ArrayList<>();
        for (DemonLordSkill skill : DemonLordSkill.values()) {
            all.addAll(TOWERS.get(skill));
        }
        return List.copyOf(all);
    }

    public static TowerType tower(DemonLordSkill skill, int tier) {
        List<TowerType> tiers = TOWERS.get(skill);
        if (tiers == null || tier < 1 || tier > tiers.size()) {
            throw new IllegalArgumentException("No demon lord tower for " + skill + " tier " + tier);
        }
        return tiers.get(tier - 1);
    }

    public static boolean isDemonLordTower(TowerType type) {
        return type != null && DEFINITIONS.containsKey(type.id());
    }

    public static DemonLordSkill skillOf(TowerType type) {
        Definition definition = definition(type);
        return definition == null ? null : definition.skill();
    }

    public static int tierOf(TowerType type) {
        Definition definition = definition(type);
        return definition == null ? 0 : definition.tier();
    }

    /**
     * Cooldown in ticks for a placed skill tower.
     *
     * <p>Defaults to the tier-adjusted value from {@link DemonLordSkill}, but a live config can
     * override any single tier through {@code cooldownTicks} without a rebuild.
     */
    public static int cooldownTicks(TowerType type) {
        Definition definition = definition(type);
        if (definition == null) {
            return 0;
        }
        int fallback = definition.skill().cooldownSecondsForTier(definition.tier()) * 20;
        return Math.max(1, TowerBalanceRuntime.abilityInt(type.id(), "cooldownTicks", fallback));
    }

    private static Definition definition(TowerType type) {
        return type == null ? null : DEFINITIONS.get(type.id());
    }

    private static void register(
            DemonLordSkill skill,
            Block altarBlock,
            long[] mineralCosts,
            double[] maxHealths,
            List<String> flavour
    ) {
        List<TowerType> tiers = new ArrayList<>(DemonLordSkill.MAX_TIER);
        for (int tier = 1; tier <= DemonLordSkill.MAX_TIER; tier++) {
            List<String> lines = new ArrayList<>();
            lines.add("<gray>마왕에게 <yellow>" + skill.displayName() + "</yellow> 스킬을 부여합니다.</gray>");
            lines.addAll(flavour);
            lines.add("<green>쿨타임 <aqua>{ability.cooldownTicks:seconds}</aqua> "
                    + "<dark_gray>|</dark_gray> 코스트 <aqua>" + skill.slotCost() + "</aqua></green>");
            lines.add(LEVEL_SCALING_LINE);
            lines.add(NO_COMBAT_LINE);

            String id = skill.towerId(tier);
            TowerType type = ProductionTowerDefinitions.tower(
                    id,
                    tierName(skill, tier),
                    mineralCosts[tier - 1],
                    maxHealths[tier - 1],
                    0.0,
                    0.0,
                    20,
                    0,
                    altarVisual(altarBlock, tier),
                    List.copyOf(lines)
            );
            DEFINITIONS.put(id, new Definition(skill, tier));
            TowerDescriptionRegistry.registerTemplate(type, type.description());
            tiers.add(type);
        }
        TOWERS.put(skill, List.copyOf(tiers));
    }

    /** Tier 1 keeps the bare skill name so the shop reads cleanly; upgrades get a rank suffix. */
    private static String tierName(DemonLordSkill skill, int tier) {
        return switch (tier) {
            case 1 -> skill.displayName();
            case 2 -> skill.displayName() + " II";
            case 3 -> skill.displayName() + " III";
            default -> skill.displayName() + " IV";
        };
    }

    private static EntityVisual altarVisual(Block block, int tier) {
        double scale = 0.7 + (tier - 1) * 0.15;
        return BlockDisplayVisual.builder(block.defaultBlockState()).scale(scale).build();
    }

    private record Definition(DemonLordSkill skill, int tier) {
    }
}
