package kim.biryeong.semiontd.tower.developer;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.tower;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.entity.visual.VillagerVisual;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.level.block.Blocks;

/**
 * Tower types for the 개발자 builder.
 *
 * <p>The family is built around one number per tower: how well it absorbs a patch. Nothing here
 * fights better than an ordinary tower of the same tier on placement day — the whole edge comes
 * from the patch pipeline, so base stats sit close to the live medians and the multipliers in
 * {@link DeveloperBalance} do the work.
 *
 * <p>Two visual groups keep the roles readable. Everything on the growth line is a creative-only
 * developer block (structure block, jigsaw, command block), so a lane of them reads as placed by
 * someone with operator access. The ability line mixes workshop blocks with villagers: the
 * villagers are the team, the blocks are their equipment.
 */
public final class DeveloperTowers {
    // ------------------------------------------------------------------ 성장 라인

    /**
     * The cheapest tower in the family and the only one meant to be thrown away.
     *
     * <p>Alpha absorbs patches worse than anything else ({@link DeveloperBalance#ALPHA_PATCH_SCALE})
     * but produces bugs far more often, which makes it the family mining rig: place several, patch
     * them until something useful falls out, then carry that bug forward with 재현.
     */
    public static final TowerType ALPHA = tower(
            "developer_alpha", "알파", 35, 110.0, 6.0, 12.0, 20, 25,
            BlockDisplayVisual.builder(Blocks.STRUCTURE_BLOCK.defaultBlockState()).scale(0.7).build(),
            List.of(
                    "<gray> 자리만 잡아둔 미완성 빌드입니다. </gray>",
                    "<red> 패치 효율이 <yellow>{ability.patchScale:percent}</yellow>로 이 계열에서 가장 낮습니다. </red>",
                    "<light_purple> 대신 정식 패치로 <yellow>버그가 가장 자주</yellow> 발생합니다. </light_purple>",
                    "<gray> 싸게 여러 기를 세워 버그를 캐내는 용도입니다. </gray>"
            )
    );

    public static final TowerType BETA = tower(
            "developer_beta", "베타", 130, 150.0, 6.8, 22.4, 16, 30,
            BlockDisplayVisual.builder(Blocks.JIGSAW.defaultBlockState()).scale(0.85).build(),
            List.of(
                    "<gray> 형태가 잡히기 시작한 중간 빌드입니다. </gray>",
                    "<aqua> 패치 효율 <yellow>{ability.patchScale:percent}</yellow>. 기준이 되는 타워입니다. </aqua>",
                    "<gray> 정식판과 LTS로 갈라집니다. </gray>"
            )
    );

    /**
     * The support branch. Deliberately weak on offence: its reason to exist is the aura, and a
     * tower that both buffs the pipeline and carries the lane would make the branch mandatory.
     */
    public static final TowerType TEST_BUILD = tower(
            "developer_test_build", "테스트 빌드", 120, 220.0, 5.5, 7.2, 16, 45,
            BlockDisplayVisual.builder(Blocks.SCAFFOLDING.defaultBlockState()).scale(0.85).build(),
            List.of(
                    "<gray> 공사 중인 비계입니다. 공격은 약하지만 단단합니다. </gray>",
                    "<green> 반경 <yellow>{ability.developer_global.testBuildAuraRadius:blocks}</yellow> 안의 아군 타워가 받는 패치 효율이 <yellow>{ability.developer_global.testBuildAuraBonus:percent}</yellow> 오릅니다. </green>",
                    "<gray> 앞을 막으면서 뒤쪽 타워의 성장을 밀어주는 자리입니다. </gray>"
            )
    );

    /**
     * The 정식 패치 endpoint.
     *
     * <p>Highest patch absorption in the family, and hotfixes land at half strength — a release
     * build is supposed to change through the reviewed path, not by someone pushing straight to
     * production.
     */
    public static final TowerType RELEASE = tower(
            "developer_release", "정식판", 290, 200.0, 7.3, 29.6, 16, 35,
            BlockDisplayVisual.builder(Blocks.COMMAND_BLOCK.defaultBlockState()).scale(1.0).build(),
            List.of(
                    "<gray> 검증을 마친 정식 빌드입니다. </gray>",
                    "<aqua> 패치 효율 <yellow>{ability.patchScale:percent}</yellow>로 이 계열에서 가장 높습니다. </aqua>",
                    "<green> <yellow>무결성</yellow> : 정식 패치로는 버그가 발생하지 않습니다. </green>",
                    "<red> 핫픽스 효과는 <yellow>{ability.hotfixScale:percent}</yellow>만 적용됩니다. </red>"
            )
    );

    /**
     * The 핫픽스 endpoint.
     *
     * <p>Immune to instability, so hotfixes can be poured in every round forever. The cost is that
     * hotfixes always leave a bug regardless of tier, so an LTS accumulates defects faster than a
     * single 디버거 can clear them.
     */
    public static final TowerType LTS = tower(
            "developer_lts", "LTS", 280, 200.0, 6.8, 24.0, 16, 35,
            BlockDisplayVisual.builder(Blocks.CHAIN_COMMAND_BLOCK.defaultBlockState()).scale(1.0).build(),
            List.of(
                    "<gray> 장기 지원 빌드입니다. 새 기능보다 유지보수를 받습니다. </gray>",
                    "<green> <yellow>불안정에 면역</yellow>입니다. 핫픽스를 무제한으로 받습니다. </green>",
                    "<aqua> 핫픽스 배율이 <yellow>{ability.hotfixScale:percent}</yellow>입니다. </aqua>",
                    "<red> 정식 패치 효율은 <yellow>{ability.patchScale:percent}</yellow>에 머뭅니다. </red>",
                    "<red> 핫픽스는 티어와 무관하게 항상 버그를 남깁니다. </red>"
            )
    );

    // ------------------------------------------------------------------ 능력 라인 (작업대)

    public static final TowerType WORKBENCH = abilityTower(
            "developer_workbench", "작업대", 40, 70.0,
            BlockDisplayVisual.builder(Blocks.CRAFTING_TABLE.defaultBlockState()).scale(0.8).build(),
            List.of(
                    "<gray> 공격하지 않고 <yellow>타워 슬롯도 사용하지 않습니다</yellow>. </gray>",
                    "<aqua> 기본 <yellow>{ability.developer_global.basePatchSlots:integer}건</yellow> + 작업대 <yellow>{ability.patchSlots:integer}건</yellow>의 정식 패치를 발행합니다. </aqua>",
                    "<gray> 전투 타워 <yellow>{ability.developer_global.patchSlotsPerTowers:integer}기</yellow>마다 1건이 더 늘어납니다. </gray>"
            )
    );

    public static final TowerType DEPLOY_SERVER = abilityTower(
            "developer_deploy_server", "배포 서버", 120, 100.0,
            BlockDisplayVisual.builder(Blocks.CRAFTER.defaultBlockState()).scale(0.9).build(),
            List.of(
                    "<aqua> 기본 <yellow>{ability.developer_global.basePatchSlots:integer}건</yellow> + 배포 서버 <yellow>{ability.patchSlots:integer}건</yellow>의 정식 패치를 발행합니다. </aqua>",
                    "<gray> 전투 타워 <yellow>{ability.developer_global.patchSlotsPerTowers:integer}기</yellow>마다 1건이 더 늘어납니다. </gray>",
                    "<light_purple> <yellow>핫픽스</yellow>를 해금합니다. 라운드당 {ability.hotfixesPerRound:integer}회. </light_purple>",
                    "<red> 핫픽스는 즉시 적용되지만 불안정 1과 버그를 남깁니다. </red>"
            )
    );

    public static final TowerType OPS_CENTER = abilityTower(
            "developer_ops_center", "운영 센터", 280, 140.0,
            BlockDisplayVisual.builder(Blocks.BEACON.defaultBlockState()).scale(1.0).build(),
            List.of(
                    "<aqua> 기본 <yellow>{ability.developer_global.basePatchSlots:integer}건</yellow> + 운영 센터 <yellow>{ability.patchSlots:integer}건</yellow>의 정식 패치를 발행합니다. </aqua>",
                    "<gray> 전투 타워 <yellow>{ability.developer_global.patchSlotsPerTowers:integer}기</yellow>마다 1건이 더 늘어납니다. </gray>",
                    "<light_purple> 핫픽스가 라운드당 <yellow>{ability.hotfixesPerRound:integer}회</yellow>로 늘어납니다. </light_purple>",
                    "<green> <yellow>긴급 점검</yellow>을 해금합니다. 라운드당 {ability.developer_global.maintenancePerRound:integer}기. </green>",
                    "<gray> 점검한 타워는 한 라운드를 쉬고 완전히 회복해 돌아옵니다. </gray>"
            )
    );

    // ------------------------------------------------------------------ 능력 라인 (검수)

    public static final TowerType TESTER = abilityTower(
            "developer_tester", "테스터", 40, 70.0,
            VillagerVisual.builder()
                    .profession(VillagerProfession.CARTOGRAPHER)
                    .type(VillagerType.PLAINS)
                    .level(1)
                    .build(),
            List.of(
                    "<gray> 공격하지 않고 <yellow>타워 슬롯도 사용하지 않습니다</yellow>. </gray>",
                    "<aqua> 타워에 붙은 <yellow>버그의 정체가 보이게</yellow> 됩니다. </aqua>",
                    "<red> 테스터가 없으면 버그가 무엇인지 모른 채 굴려야 합니다. </red>"
            )
    );

    public static final TowerType DEBUGGER = abilityTower(
            "developer_debugger", "디버거", 110, 100.0,
            BlockDisplayVisual.builder(Blocks.LECTERN.defaultBlockState()).scale(0.9).build(),
            List.of(
                    "<aqua> 라운드마다 버그 <yellow>{ability.developer_global.debugRemovalsPerRound:integer}개를 제거</yellow>할 수 있습니다. </aqua>",
                    "<gray> 디버거가 없으면 한번 붙은 버그는 지울 수 없습니다. </gray>"
            )
    );

    public static final TowerType DEVELOPER = abilityTower(
            "developer_dev", "개발자", 250, 140.0,
            VillagerVisual.builder()
                    .profession(VillagerProfession.LIBRARIAN)
                    .type(VillagerType.SNOW)
                    .level(5)
                    .build()
                    .withScale(1.05),
            List.of(
                    "<light_purple> <yellow>재현</yellow>을 해금합니다. 라운드당 {ability.developer_global.reproducePerRound:integer}회 버그를 다른 타워에 심습니다. </light_purple>",
                    "<green> <yellow>버전 고정</yellow> 슬롯 {ability.developer_global.versionPinSlots:integer}개를 제공합니다. </green>",
                    "<gray> 고정된 타워는 패치도 버그도 재현도 걸리지 않습니다. </gray>"
            )
    );

    // ------------------------------------------------------------------ 능력 라인 (단독)

    public static final TowerType PROFILER = abilityTower(
            "developer_profiler", "프로파일러", 90, 100.0,
            VillagerVisual.builder()
                    .profession(VillagerProfession.ARMORER)
                    .type(VillagerType.DESERT)
                    .level(3)
                    .build(),
            List.of(
                    "<gray> 공격하지 않고 <yellow>타워 슬롯도 사용하지 않습니다</yellow>. </gray>",
                    "<aqua> <yellow>최적화</yellow>를 해금합니다. 매치 전체에서 {ability.developer_global.optimizationsPerMatch:integer}회. </aqua>",
                    "<gray> 기능 하나를 영구히 버리고 나머지를 크게 올립니다. </gray>"
            )
    );

    private static final List<TowerType> GROWTH_LINE = List.of(ALPHA, BETA, TEST_BUILD, RELEASE, LTS);

    private static final List<TowerType> ABILITY_LINE =
            List.of(WORKBENCH, DEPLOY_SERVER, OPS_CENTER, TESTER, DEBUGGER, DEVELOPER, PROFILER);

    private static final List<TowerType> ALL =
            Stream.concat(GROWTH_LINE.stream(), ABILITY_LINE.stream()).toList();

    private static final Set<String> ALL_IDS = ALL.stream()
            .map(TowerType::id)
            .collect(Collectors.toUnmodifiableSet());

    private static final Set<String> ABILITY_IDS = ABILITY_LINE.stream()
            .map(TowerType::id)
            .collect(Collectors.toUnmodifiableSet());

    static {
        ALL.forEach(type -> TowerDescriptionRegistry.registerTemplate(type, type.description()));
    }

    private DeveloperTowers() {
    }

    /**
     * Ability towers never attack and never take a lane slot.
     *
     * <p>A zero range is what actually switches the combat goal off:
     * {@code TowerAttackMonsterGoal.tick} bails out on {@code attackRange() <= 0.0}. The deeply
     * negative aggro keeps monsters from picking one while any real tower is still standing.
     */
    private static TowerType abilityTower(
            String id,
            String displayName,
            long mineralCost,
            double maxHealth,
            EntityVisual visual,
            List<String> description
    ) {
        return tower(id, displayName, mineralCost, maxHealth, 0.0, 0.0, 20, -80, visual, description);
    }

    public static List<TowerType> all() {
        return ALL;
    }

    public static List<TowerType> growthLine() {
        return GROWTH_LINE;
    }

    public static List<TowerType> abilityLine() {
        return ABILITY_LINE;
    }

    public static boolean isDeveloperTower(TowerType type) {
        return type != null && ALL_IDS.contains(type.id());
    }

    /** Ability towers open a capability instead of fighting; they hold no patches and no bugs. */
    public static boolean isAbilityTower(TowerType type) {
        return type != null && ABILITY_IDS.contains(type.id());
    }

    public static boolean isGrowthTower(TowerType type) {
        return isDeveloperTower(type) && !isAbilityTower(type);
    }

    /** Release and LTS never pick up a bug from the reviewed patch path. */
    public static boolean hasIntegrity(TowerType type) {
        return isRelease(type) || isLts(type);
    }

    public static boolean isLts(TowerType type) {
        return type != null && LTS.id().equals(type.id());
    }

    public static boolean isRelease(TowerType type) {
        return type != null && RELEASE.id().equals(type.id());
    }

    public static boolean isTestBuild(TowerType type) {
        return type != null && TEST_BUILD.id().equals(type.id());
    }

    public static int tier(TowerType type) {
        if (type == null) {
            return 0;
        }
        String id = type.id();
        // 프로파일러 is tier one despite its price: it has no line of its own, so it has to be
        // buyable directly or it would be unreachable.
        if (ALPHA.id().equals(id) || WORKBENCH.id().equals(id) || TESTER.id().equals(id)
                || PROFILER.id().equals(id)) {
            return 1;
        }
        if (BETA.id().equals(id) || TEST_BUILD.id().equals(id)
                || DEPLOY_SERVER.id().equals(id) || DEBUGGER.id().equals(id)) {
            return 2;
        }
        if (RELEASE.id().equals(id) || LTS.id().equals(id)
                || OPS_CENTER.id().equals(id) || DEVELOPER.id().equals(id)) {
            return 3;
        }
        return 0;
    }
}
