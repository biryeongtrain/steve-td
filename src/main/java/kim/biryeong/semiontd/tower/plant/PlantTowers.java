package kim.biryeong.semiontd.tower.plant;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.tower;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Tower types of the plant builder.
 *
 * <p>The builder is split in two halves. <b>Terraform towers</b> do not fight at all; they convert
 * lane tiles into their family's {@link PlantSoil}, and their tier decides the radius.
 * <b>Combat towers</b> can only be planted on their own family's soil, and take their power from the
 * soil rather than from raw stats - base damage never exceeds 50 on any tier.
 *
 * <p>Roles are split across the four soils: 잔디 supports, 회백토 deals damage, 균사 mines and
 * weakens, 사암 tanks by reflecting. 회백토 branches into three different T3 finishers.
 */
public final class PlantTowers {
    public static final String GLOBAL_CONFIG_ID = "plant_global";

    /** Must stay above the tower constants: their factories populate it during class init. */
    private static final Map<String, Definition> DEFINITIONS = new HashMap<>();

    private static final String ROOTED_LINE = "<red>뿌리를 내려 사거리 밖 적을 쫓아가지 않습니다.</red>";

    /**
     * 잔디 위에 선 전투 타워는 계열과 무관하게 주변 아군을 회복시킵니다
     * ({@code PlantCombatTower#applyMeadowSupport}). 민들레 계열만이 아니라 튤립 계열도 해당하므로
     * 두 라인 모두에 같은 줄을 붙입니다.
     */
    private static final String MEADOW_HEAL_LINE =
            "<green>주변 <aqua>{ability.plant_soil_meadow.supportRadius:blocks}</aqua> 안 아군 타워를 "
                    + "<aqua>{ability.plant_global.soilPulseIntervalTicks:seconds}</aqua>마다 최대 체력의 "
                    + "<yellow>{ability.plant_soil_meadow.healPercentPerPulse:percent}</yellow>만큼 회복시킵니다.</green>";

    /**
     * 회백토 계열은 전 티어가 치명타를 가집니다(T1 부터 최종 형태까지).
     *
     * <p>확률이 티어마다 다를 뿐 없는 티어는 없는데, 예전에는 장미 덤불 툴팁에만 적혀 있어
     * 고사리·큰 고사리·라일락·물병 식물은 치명타가 아예 없는 것처럼 보였습니다.
     */
    private static final String PODZOL_CRIT_LINE =
            "<green>공격이 <yellow>{ability.critChance:percent}</yellow> 확률로 치명타가 되어 "
                    + "피해가 <yellow>{ability.critMultiplier:number}배</yellow>가 됩니다.</green>";

    /**
     * 잔디 성장: 자기 지형 위에서 라운드를 넘길 때마다 최대 체력이 오르고, 그 일부를 라인 전체가
     * 나눠 받습니다. 수치가 툴팁에 없으면 "성장한다"는 말만 있고 얼마나인지 알 수 없습니다.
     */
    private static final String MEADOW_GROWTH_LINE =
            "<green>자기 지형 위에서 라운드를 넘길 때마다 최대 체력이 "
                    + "<yellow>{ability.plant_soil_meadow.maxHealthGrowthPerRound:percent}</yellow>씩 오릅니다"
                    + "<dark_gray> (</dark_gray>누적 상한 "
                    + "<aqua>{ability.plant_soil_meadow.maxHealthGrowthCap:percent}</aqua><dark_gray>)</dark_gray>.</green>";

    private static final String MEADOW_SHARE_LINE =
            "<green>성장 체력의 <yellow>{ability.plant_soil_meadow.growthShareRatio:percent}</yellow>가 "
                    + "라인 전체 최대 체력 보너스로 합산됩니다"
                    + "<dark_gray> (</dark_gray>합계 상한 "
                    + "<aqua>{ability.plant_soil_meadow.growthShareCap:percent}</aqua><dark_gray>)</dark_gray>.</green>";

    /** 회백토 성장: 잔디가 체력을 키우듯 피해를 키우고, 마찬가지로 라인 전체가 나눠 받습니다. */
    private static final String PODZOL_GROWTH_LINE =
            "<green>자기 지형 위에서 라운드를 넘길 때마다 피해가 "
                    + "<yellow>{ability.plant_soil_podzol.damageGrowthPerRound:percent}</yellow>씩 오릅니다"
                    + "<dark_gray> (</dark_gray>누적 상한 "
                    + "<aqua>{ability.plant_soil_podzol.damageGrowthCap:percent}</aqua><dark_gray>)</dark_gray>.</green>";

    private static final String PODZOL_SHARE_LINE =
            "<green>성장 피해의 <yellow>{ability.plant_soil_podzol.growthShareRatio:percent}</yellow>가 "
                    + "라인 전체 피해 보너스로 합산됩니다"
                    + "<dark_gray> (</dark_gray>합계 상한 "
                    + "<aqua>{ability.plant_soil_podzol.growthShareCap:percent}</aqua><dark_gray>)</dark_gray>.</green>";

    /** 지형 수치는 계열 공용이고 티어별 배율이 따로 곱해지므로, 그 사실을 한 줄로 밝혀 둡니다. */
    private static final String SOIL_POWER_LINE =
            "<gray>위 지형 수치에는 이 티어의 계열 배율 "
                    + "<aqua>{ability.soilPower:percent}</aqua>가 곱해집니다.</gray>";

    /** 라운드당 한 번. 라운드 안에서 다시 장전하게 두면 지뢰 하나가 광역 기관총이 됩니다. */
    private static final String MYCELIUM_REARM_LINE =
            "<green>한 라운드에 한 번 터집니다. 터진 뒤에는 그 라운드 동안 빈 껍데기로 남습니다.</green>";

    /** 즉발이 아니라는 사실은 상대도 알아야 공평합니다. 툴팁에 도화선 길이를 밝혀 둡니다. */
    private static final String MYCELIUM_FUSE_LINE =
            "<gray>밟으면 섬광이 뜨고 <aqua>{ability.fuseTicks:seconds}</aqua> 뒤에 터집니다. "
                    + "그 사이에 빠져나간 적은 맞지 않습니다.</gray>";

    /** 라운드마다 한 단계씩 삭습니다. 지뢰가 치르는 값이 폭발 한 번에서 라운드 하나로 옮겨갔습니다. */
    private static final String MYCELIUM_DECAY_LINE =
            "<red>라운드가 끝나면 한 단계 아래로 삭습니다. 붉은 버섯은 사라집니다.</red>";

    // ------------------------------------------------------------------
    // 테라포밍 타워 - 전투 능력 없음 (사거리 0, 피해 0). 지형만 깝니다.
    // ------------------------------------------------------------------
    public static final TowerType T1_OAK_SEED_TOWER = terraformTower(
            "t1_oak_seed_tower", "참나무 묘목", 25, 120, Blocks.OAK_SAPLING, 0.7, PlantSoil.MEADOW, 1);
    public static final TowerType T2_OAK_SEED_TOWER = terraformTower(
            "t2_oak_seed_tower", "자란 참나무 묘목", 85, 260, Blocks.OAK_SAPLING, 0.85, PlantSoil.MEADOW, 2);
    public static final TowerType T3_OAK_SEED_TOWER = terraformTower(
            "t3_oak_seed_tower", "짙은 참나무 묘목", 180, 480, Blocks.DARK_OAK_SAPLING, 1.0, PlantSoil.MEADOW, 3);

    public static final TowerType T1_MUSHROOM_SPORE_TOWER = terraformTower(
            "t1_mushroom_spore_tower", "갈색 버섯", 25, 120, Blocks.BROWN_MUSHROOM, 0.7, PlantSoil.MYCELIUM, 1);
    public static final TowerType T2_MUSHROOM_SPORE_TOWER = terraformTower(
            "t2_mushroom_spore_tower", "자란 갈색 버섯", 85, 260, Blocks.BROWN_MUSHROOM, 0.85, PlantSoil.MYCELIUM, 2);
    public static final TowerType T3_MUSHROOM_SPORE_TOWER = terraformTower(
            "t3_mushroom_spore_tower", "포자 군체", 180, 480, Blocks.BROWN_MUSHROOM, 1.0, PlantSoil.MYCELIUM, 3);

    public static final TowerType T1_DRY_GRASS_SEED_TOWER = terraformTower(
            "t1_dry_grass_seed_tower", "마른 풀", 25, 120, Blocks.SHORT_DRY_GRASS, 0.8, PlantSoil.DESERT, 1);
    public static final TowerType T2_DRY_GRASS_SEED_TOWER = terraformTower(
            "t2_dry_grass_seed_tower", "자란 마른 풀", 85, 260, Blocks.TALL_DRY_GRASS, 0.9, PlantSoil.DESERT, 2);
    public static final TowerType T3_DRY_GRASS_SEED_TOWER = terraformTower(
            "t3_dry_grass_seed_tower", "마른 풀 군락", 180, 480, Blocks.TALL_DRY_GRASS, 1.05, PlantSoil.DESERT, 3);

    public static final TowerType T1_SPRUCE_SEED_TOWER = terraformTower(
            "t1_spruce_seed_tower", "가문비나무 묘목", 25, 120, Blocks.SPRUCE_SAPLING, 0.7, PlantSoil.PODZOL, 1);
    public static final TowerType T2_SPRUCE_SEED_TOWER = terraformTower(
            "t2_spruce_seed_tower", "자란 가문비나무 묘목", 85, 260, Blocks.SPRUCE_SAPLING, 0.85, PlantSoil.PODZOL, 2);
    public static final TowerType T3_SPRUCE_SEED_TOWER = terraformTower(
            "t3_spruce_seed_tower", "가문비 묘상", 180, 480, Blocks.SPRUCE_SAPLING, 1.0, PlantSoil.PODZOL, 3);

    // ------------------------------------------------------------------
    // 전투 타워 - 자기 계열 지형 위에만 설치할 수 있습니다.
    // ------------------------------------------------------------------

    // 잔디 - 후방 지원. 두 갈래로 나뉩니다.
    // 민들레 계열: 웨이브 정산 다이아를 만드는 경제 라인. 어그로가 낮아 뒤에 섭니다.
    public static final TowerType T1_MEADOW_TOWER = combatTower(
            "t1_meadow_tower", "민들레", 50, 200, 8.0, 4, 28, 30,
            plantVisual(Blocks.DANDELION, 1.0), PlantSoil.MEADOW, 1,
            List.of(
                    "<gray>잔디 위에만 심는 후방 지원 타워입니다.</gray>",
                    "<green>웨이브 정산 시 다이아를 {ability.diamondPerWave:integer}개 얻습니다.</green>",
                    MEADOW_HEAL_LINE,
                    MEADOW_GROWTH_LINE,
                    MEADOW_SHARE_LINE,
                    SOIL_POWER_LINE
            ));
    public static final TowerType T2_MEADOW_TOWER = combatTower(
            "t2_meadow_tower", "데이지", 150, 380, 10.0, 10, 28, 35,
            plantVisual(Blocks.OXEYE_DAISY, 1.15), PlantSoil.MEADOW, 2,
            List.of(
                    "<gray>잔디 위에만 심는 후방 지원 타워입니다.</gray>",
                    "<green>웨이브 정산 시 다이아를 {ability.diamondPerWave:integer}개 얻습니다.</green>",
                    MEADOW_HEAL_LINE,
                    MEADOW_GROWTH_LINE,
                    MEADOW_SHARE_LINE,
                    SOIL_POWER_LINE
            ));
    public static final TowerType T3_MEADOW_TOWER = combatTower(
            "t3_meadow_tower", "해바라기", 240, 700, 12.0, 20, 28, 40,
            plantVisual(Blocks.SUNFLOWER, 1.35), PlantSoil.MEADOW, 3,
            List.of(
                    "<gray>식물 빌더의 최종 경제 타워입니다.</gray>",
                    "<green>웨이브 정산 시 다이아를 {ability.diamondPerWave:integer}개 얻습니다.</green>",
                    MEADOW_HEAL_LINE,
                    MEADOW_GROWTH_LINE,
                    MEADOW_SHARE_LINE,
                    SOIL_POWER_LINE
            ));

    // 튤립 계열: 자기 자신을 중심으로 터지는 광역 딜러.
    public static final TowerType T1_MEADOW_NOVA_TOWER = combatTower(
            "t1_meadow_nova_tower", "빨간 튤립", 65, 220, 5.0, 7.5, 26, 45,
            plantVisual(Blocks.RED_TULIP, 1.0), PlantSoil.MEADOW, 1,
            List.of(
                    "<gray>잔디 위에만 심는 광역 타워입니다.</gray>",
                    "<green>공격할 때 자기 주변 <aqua>{ability.novaRadius:blocks}</aqua> 안의 적을 "
                            + "주 대상 피해의 <yellow>{ability.novaDamageRatio:percent}</yellow>로 함께 휩씁니다.</green>",
                    MEADOW_HEAL_LINE,
                    MEADOW_GROWTH_LINE,
                    MEADOW_SHARE_LINE,
                    SOIL_POWER_LINE
            ));
    public static final TowerType T2_MEADOW_NOVA_TOWER = combatTower(
            "t2_meadow_nova_tower", "양귀비", 175, 440, 5.0, 19, 24, 52,
            plantVisual(Blocks.POPPY, 1.15), PlantSoil.MEADOW, 2,
            List.of(
                    "<gray>잔디 위에만 심는 광역 타워입니다.</gray>",
                    "<green>자기 주변 <aqua>{ability.novaRadius:blocks}</aqua>를 "
                            + "주 대상 피해의 <yellow>{ability.novaDamageRatio:percent}</yellow>로 휩씁니다.</green>",
                    MEADOW_HEAL_LINE,
                    MEADOW_GROWTH_LINE,
                    MEADOW_SHARE_LINE,
                    SOIL_POWER_LINE
            ));
    public static final TowerType T3_MEADOW_NOVA_TOWER = combatTower(
            "t3_meadow_nova_tower", "횃불꽃", 275, 780, 7.0, 34, 20, 60,
            plantVisual(Blocks.TORCHFLOWER, 1.35), PlantSoil.MEADOW, 3,
            List.of(
                    "<gray>식물 빌더의 최종 광역 타워입니다.</gray>",
                    "<green>자기 주변 <aqua>{ability.novaRadius:blocks}</aqua>의 적에게 "
                            + "주 대상 피해의 <yellow>{ability.novaDamageRatio:percent}</yellow>를 줍니다.</green>",
                    MEADOW_HEAL_LINE,
                    MEADOW_GROWTH_LINE,
                    MEADOW_SHARE_LINE,
                    SOIL_POWER_LINE
            ));

    // 균사 - 라운드당 한 번 터지는 지뢰. 라운드가 끝나면 한 단계씩 삭습니다.
    public static final TowerType T1_MYCELIUM_TOWER = combatTower(
            "t1_mycelium_tower", "붉은 버섯", 30, 110, 0.0, 30, 20, 35,
            plantVisual(Blocks.RED_MUSHROOM, 1.0), PlantSoil.MYCELIUM, 1,
            List.of(
                    "<gray>균사 위에만 심는 지뢰입니다.</gray>",
                    "<green>적이 밟으면 터져 주변에 피해를 줍니다.</green>",
                    "<green>맞은 적은 느려지고 잠시 공격하지 못합니다.</green>",
                    MYCELIUM_FUSE_LINE,
                    MYCELIUM_REARM_LINE,
                    "<red>라운드가 끝나면 사라집니다.</red>"
            ));
    public static final TowerType T2_MYCELIUM_TOWER = combatTower(
            "t2_mycelium_tower", "진홍빛 버섯", 80, 260, 0.0, 45, 20, 40,
            plantVisual(Blocks.CRIMSON_FUNGUS, 1.1), PlantSoil.MYCELIUM, 2,
            List.of(
                    "<gray>균사 위에만 심는 지뢰입니다.</gray>",
                    "<green>폭발 범위와 피해, 무력화 시간이 늘어납니다.</green>",
                    MYCELIUM_FUSE_LINE,
                    MYCELIUM_REARM_LINE,
                    MYCELIUM_DECAY_LINE
            ));
    public static final TowerType T3_MYCELIUM_TOWER = combatTower(
            "t3_mycelium_tower", "뒤틀린 버섯", 130, 460, 0.0, 50, 20, 45,
            plantVisual(Blocks.WARPED_FUNGUS, 1.25), PlantSoil.MYCELIUM, 3,
            List.of(
                    "<gray>식물 빌더의 최종 지뢰입니다.</gray>",
                    "<green>폭발 범위와 피해, 무력화 시간이 가장 깁니다.</green>",
                    MYCELIUM_FUSE_LINE,
                    MYCELIUM_REARM_LINE,
                    MYCELIUM_DECAY_LINE
            ));

    // 사암 - 반사 탱커. 스스로 공격하지 않고 맞은 만큼 되돌려줍니다.
    public static final TowerType T1_DESERT_TOWER = combatTower(
            "t1_desert_tower", "죽은 덤불", 50, 100, 0.0, 8, 22, 58,
            plantVisual(Blocks.DEAD_BUSH, 1.0), PlantSoil.DESERT, 1,
            List.of(
                    "<gray>사암 위에만 심는 반사 탱커입니다.</gray>",
                    "<red>스스로 공격하지 않습니다.</red>",
                    "<green>맞으면 받은 피해의 일부와 자기 공격력을 함께 되돌려줍니다.</green>",
                    "<green>주변 사암 위의 적은 공격 속도가 느려집니다.</green>"
            ));
    public static final TowerType T2_DESERT_TOWER = combatTower(
            "t2_desert_tower", "선인장", 190, 420, 0.0, 20, 22, 66,
            plantVisual(Blocks.CACTUS, 1.15), PlantSoil.DESERT, 2,
            List.of(
                    "<gray>사암 위에만 심는 반사 탱커입니다.</gray>",
                    "<red>스스로 공격하지 않습니다.</red>",
                    "<green>반사 비율과 얹히는 공격력이 늘어납니다.</green>"
            ));
    public static final TowerType T3_DESERT_TOWER = combatTower(
            "t3_desert_tower", "꽃선인장", 300, 750, 0.0, 32, 20, 74,
            stackedPlantVisual(Blocks.CACTUS, Blocks.CACTUS_FLOWER, 1.3), PlantSoil.DESERT, 3,
            List.of(
                    "<gray>식물 빌더의 최종 반사 탱커입니다.</gray>",
                    "<red>스스로 공격하지 않습니다.</red>",
                    "<green>공속 감소와 가시 반사가 가장 강합니다.</green>"
            ));

    // 회백토 - 딜러. T2 에서 세 갈래로 갈라집니다.
    public static final TowerType T1_PODZOL_TOWER = combatTower(
            "t1_podzol_tower", "고사리", 60, 80, 12.0, 9, 20, 25,
            plantVisual(Blocks.FERN, 1.0), PlantSoil.PODZOL, 1,
            List.of(
                    "<gray>회백토 위에만 심는 딜러 타워입니다.</gray>",
                    "<green>사거리와 공격 속도가 오릅니다.</green>",
                    PODZOL_CRIT_LINE,
                    PODZOL_GROWTH_LINE,
                    PODZOL_SHARE_LINE,
                    SOIL_POWER_LINE
            ));
    public static final TowerType T2_PODZOL_TOWER = combatTower(
            "t2_podzol_tower", "큰 고사리", 170, 130, 14.0, 26, 18, 30,
            plantVisual(Blocks.LARGE_FERN, 1.2), PlantSoil.PODZOL, 2,
            List.of(
                    "<gray>회백토 위에만 심는 딜러 타워입니다.</gray>",
                    "<green>사거리와 공격 속도가 오릅니다.</green>",
                    PODZOL_CRIT_LINE,
                    PODZOL_GROWTH_LINE,
                    PODZOL_SHARE_LINE,
                    SOIL_POWER_LINE,
                    "<yellow>세 갈래 최종 형태로 갈라집니다.</yellow>"
            ));
    public static final TowerType T3_PODZOL_LILAC_TOWER = combatTower(
            "t3_podzol_lilac_tower", "라일락", 285, 210, 14.0, 34, 18, 35,
            plantVisual(Blocks.LILAC, 1.35), PlantSoil.PODZOL, 3,
            List.of(
                    "<gray>회백토 최종 형태 중 광역형입니다.</gray>",
                    "<green>맞은 자리에서 <yellow>{ability.splashConeDegrees:number}도 부채꼴</yellow>, "
                            + "반경 <aqua>{ability.splashRadius:blocks}</aqua>로 꽃가루를 뿌립니다.</green>",
                    "<green>이미 체력이 깎인 적일수록 꽃가루가 더 아픕니다.</green>",
                    PODZOL_CRIT_LINE,
                    PODZOL_GROWTH_LINE,
                    PODZOL_SHARE_LINE,
                    SOIL_POWER_LINE,
                    "<gray>단일 피해는 세 형태 중 가장 낮습니다.</gray>"
            ));
    public static final TowerType T3_PODZOL_ROSE_TOWER = combatTower(
            "t3_podzol_rose_tower", "장미 덤불", 285, 210, 16.0, 46, 16, 35,
            plantVisual(Blocks.ROSE_BUSH, 1.35), PlantSoil.PODZOL, 3,
            List.of(
                    "<gray>회백토 최종 형태 중 단일 극딜형입니다.</gray>",
                    "<green>치명타 확률 <yellow>{ability.critChance:percent}</yellow>로 피해가 {ability.critMultiplier:number}배가 됩니다.</green>",
                    "<green>그 위에 <yellow>{ability.superCritChance:percent}</yellow> 확률로 초치명타가 터져 {ability.superCritMultiplier:number}배가 됩니다.</green>",
                    PODZOL_GROWTH_LINE,
                    PODZOL_SHARE_LINE,
                    SOIL_POWER_LINE,
                    "<gray>한 번에 한 대상만 때립니다.</gray>"
            ));
    public static final TowerType T3_PODZOL_PITCHER_TOWER = combatTower(
            "t3_podzol_pitcher_tower", "물병 식물", 285, 210, 30.0, 48, 38, 35,
            plantVisual(Blocks.PITCHER_PLANT, 1.35), PlantSoil.PODZOL, 3,
            List.of(
                    "<gray>회백토 최종 형태 중 곡사 포대입니다.</gray>",
                    "<green>사거리 {stat.range:number}으로 라인 전체를 덮습니다.</green>",
                    "<green>착탄 지점 반경 <aqua>{ability.splashRadius:blocks}</aqua>에 "
                            + "주 대상 피해의 <yellow>{ability.splashDamageRatio:percent}</yellow>가 퍼집니다.</green>",
                    "<green>포격에 맞은 적은 <yellow>포충낭</yellow>에 걸려 "
                            + "<aqua>{ability.snareDurationTicks:seconds}</aqua> 동안 이동 속도가 "
                            + "<yellow>{ability.snareMoveSpeedReduction:percent}</yellow> 느려집니다.</green>",
                    PODZOL_CRIT_LINE,
                    PODZOL_GROWTH_LINE,
                    PODZOL_SHARE_LINE,
                    SOIL_POWER_LINE,
                    "<gray>공격 속도는 가장 느립니다.</gray>"
            ));

    public static final List<TowerType> TERRAFORM_TOWERS = List.of(
            T1_OAK_SEED_TOWER, T2_OAK_SEED_TOWER, T3_OAK_SEED_TOWER,
            T1_MUSHROOM_SPORE_TOWER, T2_MUSHROOM_SPORE_TOWER, T3_MUSHROOM_SPORE_TOWER,
            T1_DRY_GRASS_SEED_TOWER, T2_DRY_GRASS_SEED_TOWER, T3_DRY_GRASS_SEED_TOWER,
            T1_SPRUCE_SEED_TOWER, T2_SPRUCE_SEED_TOWER, T3_SPRUCE_SEED_TOWER
    );

    public static final List<TowerType> COMBAT_TOWERS = List.of(
            T1_MEADOW_TOWER, T2_MEADOW_TOWER, T3_MEADOW_TOWER,
            T1_MEADOW_NOVA_TOWER, T2_MEADOW_NOVA_TOWER, T3_MEADOW_NOVA_TOWER,
            T1_MYCELIUM_TOWER, T2_MYCELIUM_TOWER, T3_MYCELIUM_TOWER,
            T1_DESERT_TOWER, T2_DESERT_TOWER, T3_DESERT_TOWER,
            T1_PODZOL_TOWER, T2_PODZOL_TOWER,
            T3_PODZOL_LILAC_TOWER, T3_PODZOL_ROSE_TOWER, T3_PODZOL_PITCHER_TOWER
    );

    static {
        TERRAFORM_TOWERS.forEach(type -> TowerDescriptionRegistry.registerTemplate(type, type.description()));
        COMBAT_TOWERS.forEach(type -> TowerDescriptionRegistry.registerTemplate(type, type.description()));
    }

    private PlantTowers() {
    }

    public static boolean isPlantTower(TowerType type) {
        return type != null && DEFINITIONS.containsKey(type.id());
    }

    public static boolean isTerraformTower(TowerType type) {
        Definition definition = definition(type);
        return definition != null && definition.terraformer();
    }

    public static boolean isCombatTower(TowerType type) {
        Definition definition = definition(type);
        return definition != null && !definition.terraformer();
    }

    public static PlantSoil soilOf(TowerType type) {
        Definition definition = definition(type);
        return definition == null ? null : definition.soil();
    }

    public static int tierOf(TowerType type) {
        Definition definition = definition(type);
        return definition == null ? 0 : definition.tier();
    }

    /**
     * Terraform radius grows with the terraformer tier: T1 a 3x3, T2 a 5x5, T3 a 7x7.
     *
     * <p>The terraformer occupies its own tile, so a radius of 0 would leave no room for a combat
     * tower at all. T1 has to open real estate on its own or the family cannot start.
     *
     * <p>Tunable per tower through {@code terraformRadius} so playtests do not need a rebuild.
     * Combat towers never terraform.
     */
    public static int terraformRadius(TowerType type) {
        Definition definition = definition(type);
        if (definition == null || !definition.terraformer()) {
            return -1;
        }
        return Math.max(0, TowerBalanceRuntime.abilityInt(type.id(), "terraformRadius", definition.tier()));
    }

    /**
     * 균사 지뢰가 라운드 끝에 삭아 내려갈 한 단계 아래 타워. 붉은 버섯이면 {@code null} 입니다.
     *
     * <p>업그레이드 그래프를 거꾸로 읽는 대신 계열과 티어로 찾습니다. 균사 계열은 갈래가 없어
     * 한 티어에 타워가 하나뿐이라, 역방향 탐색이 애매해질 여지가 없습니다.
     */
    public static TowerType previousMyceliumTier(TowerType type) {
        if (soilOf(type) != PlantSoil.MYCELIUM) {
            return null;
        }
        return switch (tierOf(type)) {
            case 3 -> T2_MYCELIUM_TOWER;
            case 2 -> T1_MYCELIUM_TOWER;
            default -> null;
        };
    }

    public static boolean matches(TowerType type, TowerType other) {
        return type != null && other != null && type.id().equals(other.id());
    }

    private static Definition definition(TowerType type) {
        return type == null ? null : DEFINITIONS.get(type.id());
    }

    private static TowerType terraformTower(
            String id,
            String displayName,
            long mineralCost,
            double maxHealth,
            Block block,
            double scale,
            PlantSoil soil,
            int tier
    ) {
        TowerType type = tower(
                id,
                displayName,
                mineralCost,
                maxHealth,
                0.0,
                0.0,
                20,
                12,
                plantVisual(block, scale),
                List.of(
                        "<gray>공격하지 않는 지형 전용 타워입니다.</gray>",
                        "<green>주변 반경 <aqua>{ability.terraformRadius:blocks}</aqua>을 "
                                + soil.displayName() + "로 바꿉니다.</green>",
                        "<green>" + soil.displayName() + " 위에만 같은 계열 전투 타워를 심습니다.</green>",
                        ROOTED_LINE
                )
        );
        DEFINITIONS.put(id, new Definition(soil, tier, true));
        return type;
    }

    private static TowerType combatTower(
            String id,
            String displayName,
            long mineralCost,
            double maxHealth,
            double range,
            double damage,
            int attackIntervalTicks,
            int aggroPriority,
            EntityVisual visual,
            PlantSoil soil,
            int tier,
            List<String> description
    ) {
        List<String> lines = new ArrayList<>(description);
        lines.add(ROOTED_LINE);
        TowerType type = tower(
                id,
                displayName,
                mineralCost,
                maxHealth,
                range,
                damage,
                attackIntervalTicks,
                aggroPriority,
                visual,
                List.copyOf(lines)
        );
        DEFINITIONS.put(id, new Definition(soil, tier, false));
        return type;
    }

    /**
     * Renders a plant, including the top half of two-block plants.
     *
     * <p>{@code defaultBlockState()} of a {@link DoublePlantBlock} is the <b>lower</b> half, so
     * drawing it alone chops 장미 덤불·라일락·큰 고사리·물병 식물·해바라기 in half. Detecting the
     * property rather than listing the blocks means any tall plant added later is handled too.
     */
    private static EntityVisual plantVisual(Block block, double scale) {
        BlockState base = block.defaultBlockState();
        var builder = BlockDisplayVisual.builder(base).scale(scale);
        if (base.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            builder = builder.topBlockState(base.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        }
        return builder.build();
    }

    /** Renders {@code topBlock} sitting one block above {@code block} (선인장 위의 선인장꽃). */
    private static EntityVisual stackedPlantVisual(Block block, Block topBlock, double scale) {
        return BlockDisplayVisual.builder(block.defaultBlockState())
                .topBlockState(topBlock.defaultBlockState())
                .scale(scale)
                .build();
    }

    private record Definition(PlantSoil soil, int tier, boolean terraformer) {
    }
}
