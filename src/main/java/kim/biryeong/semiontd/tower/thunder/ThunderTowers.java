package kim.biryeong.semiontd.tower.thunder;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.tower;
import static kim.biryeong.semiontd.util.EntityTypeUtil.byId;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;

/**
 * Tower types for the 람쥐썬더 builder.
 *
 * <p>The family is built around one number: the power load factor (consumption / generation).
 * Generation towers carry no attack at all, tanks either refuse to draw power or spend it to mark
 * targets, and the squirrel damage line scales directly with how much headroom the grid has.
 *
 * <p>Three visual groups keep the roles readable at a glance: lightning rods are block displays,
 * tanks are armadillos, and only the damage line uses the squirrel Blockbench model.
 */
public final class ThunderTowers {
    /** Blockbench model for the standard squirrel damage line. */
    public static final String SQUIRREL_MODEL = "semion-td:tower/squirrel";

    /**
     * Blue-tinted variant for the surge line.
     *
     * <p>Both T2 towers branch from the same T1 and can stand side by side on one lane, so they
     * cannot share an appearance the way an upgrade chain can.
     */
    public static final String SURGE_MODEL = "semion-td:tower/squirrel_surge";

    // ------------------------------------------------------------------ 발전 (block display)

    public static final TowerType ROD_T1 = tower(
            "thunder_rod_t1", "피뢰침", 30, 70.0, 0.0, 0.0, 20, -5,
            BlockDisplayVisual.builder(Blocks.LIGHTNING_ROD.defaultBlockState()).scale(0.8).build(),
            List.of(
                    "<gray> 공격하지 않고 전력만 생산하는 설비입니다. </gray>",
                    "<aqua> 전력 <yellow>{ability.powerOutput:integer}</yellow>을 꾸준히 공급합니다. </aqua>",
                    "<gray> 전력이 남으면 모든 타워의 공격력이 오르고, 모자라면 함께 떨어집니다. </gray>"
            )
    );

    public static final TowerType ROD_COPPER = tower(
            "thunder_rod_copper", "구리 피뢰탑", 75, 130.0, 0.0, 0.0, 20, -5,
            BlockDisplayVisual.builder(Blocks.LIGHTNING_ROD.defaultBlockState()).scale(1.15).build(),
            List.of(
                    "<gray> 출력이 흔들리지 않는 안정형 발전 설비입니다. </gray>",
                    "<aqua> 전력 <yellow>{ability.powerOutput:integer}</yellow>를 고정으로 공급합니다. </aqua>",
                    "<green> 계산이 되는 전력이라 소비가 큰 타워를 안심하고 굴릴 수 있습니다. </green>"
            )
    );

    public static final TowerType ROD_STORM = tower(
            "thunder_rod_storm", "폭풍 유도침", 75, 100.0, 0.0, 0.0, 20, -5,
            BlockDisplayVisual.builder(Blocks.LIGHTNING_ROD.defaultBlockState()).scale(1.15).build(),
            List.of(
                    "<gray> 낙뢰를 끌어오는 도박형 발전 설비입니다. </gray>",
                    "<aqua> 웨이브마다 전력 <yellow>{ability.stormMinOutput:integer}~{ability.stormMaxOutput:integer}</yellow> 사이를 뽑습니다. </aqua>",
                    "<light_purple> {ability.thunder_global.stormWaveInterval:integer}웨이브마다 <yellow>뇌우</yellow>가 와서 최대 출력이 확정됩니다. </light_purple>",
                    "<red> 운이 나쁜 웨이브에는 출력이 바닥나 라인 전체가 약해집니다. </red>"
            )
    );

    // ------------------------------------------------------------------ 탱커 (armadillo)

    public static final TowerType ARMADILLO_T1 = tower(
            "thunder_armadillo_t1", "꼬마 아르마딜로", 50, 250.0, 2.4, 8.0, 20, 55,
            EntityVisual.builder(byId(EntityType.ARMADILLO)).scale(0.8).build(),
            List.of(
                    "<gray> 몬스터를 자기 쪽으로 끌어당기는 앞라인 타워입니다. </gray>",
                    "<aqua> 공격할 때 대상을 <yellow>감전</yellow>시켜 잠시 멈춥니다. </aqua>",
                    "<green> 전력을 쓰지 않아 전력이 모자라도 성능이 떨어지지 않습니다. </green>",
                    "<gray> 두꺼운 껍질로 버티는 기본 앞라인입니다. </gray>"
            )
    );

    public static final TowerType ARMADILLO_INSULATED = tower(
            "thunder_armadillo_insulated", "절연 아르마딜로", 105, 440.0, 2.6, 15.0, 19, 85,
            EntityVisual.builder(byId(EntityType.ARMADILLO)).scale(1.0).build(),
            List.of(
                    "<gray> 전기를 통과시키지 않는 껍질로 전력망에서 분리된 타워입니다. </gray>",
                    "<green> 전력을 <yellow>전혀 소비하지 않습니다</yellow>. 전력이 부족해도 항상 100% 성능입니다. </green>",
                    "<aqua> 체력이 깎일수록 전력으로 바꿔 공급합니다. 최대 <yellow>{ability.healthToPower:integer}</yellow>. </aqua>",
                    "<gray> 맞아주는 것 자체가 발전이 되는 앞라인입니다. </gray>"
            )
    );

    public static final TowerType ARMADILLO_RUBBER = tower(
            "thunder_armadillo_rubber", "고무 아르마딜로", 220, 900.0, 2.8, 26.0, 17, 110,
            EntityVisual.builder(byId(EntityType.ARMADILLO)).scale(1.2).build(),
            List.of(
                    "<gray> 절연 계열의 최종 형태입니다. 전기를 막아내는 두꺼운 껍질로 버팁니다. </gray>",
                    "<green> 전력을 <yellow>전혀 소비하지 않습니다</yellow>. </green>",
                    "<aqua> 잃은 체력을 최대 <yellow>{ability.healthToPower:integer}</yellow>의 전력으로 바꿉니다. </aqua>",
                    "<gray> 발전 설비 없이도 라인을 굴릴 수 있는 최후의 보루입니다. </gray>"
            )
    );

    public static final TowerType ARMADILLO_GROUNDED = tower(
            "thunder_armadillo_grounded", "접지 아르마딜로", 100, 300.0, 2.6, 15.0, 19, 85,
            EntityVisual.builder(byId(EntityType.ARMADILLO)).scale(1.0).build(),
            List.of(
                    "<gray> 전기를 땅으로 흘려 적을 취약하게 만드는 앞라인 타워입니다. </gray>",
                    "<aqua> 공격한 대상에게 <yellow>접지 표식</yellow>을 남깁니다. </aqua>",
                    "<green> 표식이 붙은 적은 받는 피해가 <yellow>{ability.markDamageBonus:percent}</yellow> 증가합니다. </green>",
                    "<green> 표식이 붙은 적의 공격력이 <yellow>{ability.markAttackReduction:percent}</yellow> 감소합니다. </green>",
                    "<aqua> 받는 피해의 <yellow>{ability.damageAbsorb:percent}</yellow>를 땅으로 흘려보냅니다. </aqua>",
                    "<red> 전력을 소비하므로 전력이 부족하면 함께 약해집니다. </red>"
            )
    );

    public static final TowerType ARMADILLO_EARTH = tower(
            "thunder_armadillo_earth", "대지 아르마딜로", 215, 560.0, 2.8, 26.0, 17, 110,
            EntityVisual.builder(byId(EntityType.ARMADILLO)).scale(1.2).build(),
            List.of(
                    "<gray> 접지 계열의 최종 형태입니다. </gray>",
                    "<aqua> 받는 피해를 <yellow>{ability.markDamageBonus:percent}</yellow> 올리고 적 공격력을 <yellow>{ability.markAttackReduction:percent}</yellow> 낮춥니다. </aqua>",
                    "<aqua> 받는 피해의 <yellow>{ability.damageAbsorb:percent}</yellow>를 땅으로 흘려보냅니다. </aqua>",
                    "<light_purple> 파괴될 때 반경 {ability.dischargeRadius:blocks}에 <yellow>{ability.dischargeDamage:attack_damage}</yellow> 방전 피해를 줍니다. </light_purple>",
                    "<red> 전력을 소비합니다. </red>"
            )
    );

    // ------------------------------------------------------------------ 딜러 (squirrel)

    public static final TowerType SQUIRREL_T1 = tower(
            "thunder_squirrel_t1", "아기 람쥐", 50, 90.0, 6.0, 15.0, 13, 0,
            squirrel(0.9),
            List.of(
                    "<gray> 도토리를 던지는 평범한 다람쥐입니다. </gray>",
                    "<aqua> 전력 <yellow>{ability.powerDraw:integer}</yellow>만 소비합니다. </aqua>",
                    "<gray> 소비가 적어 발전 설비 없이도 여러 기를 굴릴 수 있습니다. </gray>"
            )
    );

    public static final TowerType SQUIRREL_T2 = tower(
            "thunder_squirrel_t2", "람쥐썬더", 130, 140.0, 6.5, 33.0, 12, 0,
            squirrel(1.1),
            List.of(
                    "<gray> 전기를 다루기 시작한 주력 딜러입니다. </gray>",
                    "<light_purple> 공격이 주변 적 {ability.chainTargets:integer}명에게 <yellow>{ability.chainDamageRatio:percent}</yellow> 피해로 전이됩니다. </light_purple>",
                    "<aqua> 전력 <yellow>{ability.powerDraw:integer}</yellow>를 소비합니다. </aqua>",
                    "<green> 전력이 남을수록 공격력이 오릅니다. </green>"
            )
    );

    public static final TowerType SQUIRREL_T3 = tower(
            "thunder_squirrel_t3", "뇌신 람쥐썬더", 280, 190.0, 7.0, 58.0, 11, 0,
            squirrel(1.35),
            List.of(
                    "<gray> 계열 최종 딜러입니다. 단일 화력과 광역을 함께 냅니다. </gray>",
                    "<light_purple> 공격이 반경 {ability.chainRadius:blocks} 안의 주변 적 {ability.chainTargets:integer}명에게 <yellow>{ability.chainDamageRatio:percent}</yellow> 피해로 전이됩니다. </light_purple>",
                    "<aqua> 전력 <yellow>{ability.powerDraw:integer}</yellow>를 소비합니다. </aqua>"
            )
    );

    public static final TowerType SURGE_T2 = tower(
            "thunder_surge_t2", "폭주 람쥐", 130, 110.0, 6.0, 28.0, 12, 0,
            surgeSquirrel(1.1),
            List.of(
                    "<gray> 남는 전력을 통째로 먹고 폭주하는 단일 대상 딜러입니다. </gray>",
                    "<green> 전력 여유에 비례해 공격력이 최대 <yellow>{ability.surgeMaxMultiplier:number}배</yellow>까지 오릅니다. </green>",
                    "<aqua> 전력 <yellow>{ability.powerDraw:integer}</yellow>을 소비합니다. </aqua>",
                    "<gray> 광역이 필요하면 뇌신, 한 대상을 빨리 녹이려면 이쪽입니다. </gray>"
            )
    );

    public static final TowerType SURGE_T3 = tower(
            "thunder_surge_t3", "과부하 람쥐", 280, 150.0, 6.5, 52.0, 12, 0,
            surgeSquirrel(1.35),
            List.of(
                    "<gray> 폭주 계열의 최종 형태입니다. 계열 최고의 단일 화력을 냅니다. </gray>",
                    "<green> 전력 여유에 비례해 공격력이 최대 <yellow>{ability.surgeMaxMultiplier:number}배</yellow>까지 오릅니다. </green>",
                    "<aqua> 전력 <yellow>{ability.powerDraw:integer}</yellow>을 소비합니다. </aqua>",
                    "<red> 여유가 없으면 기본 공격력만 냅니다. 발전에 투자한 빌드 전용입니다. </red>"
            )
    );

    // ------------------------------------------------------------------ 분류

    private static final Set<String> ROD_IDS = ids(ROD_T1, ROD_COPPER, ROD_STORM);
    private static final Set<String> ARMADILLO_IDS =
            ids(ARMADILLO_T1, ARMADILLO_INSULATED, ARMADILLO_RUBBER, ARMADILLO_GROUNDED, ARMADILLO_EARTH);
    private static final Set<String> INSULATED_IDS = ids(ARMADILLO_INSULATED, ARMADILLO_RUBBER);
    private static final Set<String> GROUNDED_IDS = ids(ARMADILLO_GROUNDED, ARMADILLO_EARTH);
    private static final Set<String> SQUIRREL_IDS =
            ids(SQUIRREL_T1, SQUIRREL_T2, SQUIRREL_T3, SURGE_T2, SURGE_T3);
    private static final Set<String> SURGE_IDS = ids(SURGE_T2, SURGE_T3);

    private static final List<TowerType> ALL = List.of(
            ROD_T1, ROD_COPPER, ROD_STORM,
            ARMADILLO_T1, ARMADILLO_INSULATED, ARMADILLO_RUBBER, ARMADILLO_GROUNDED, ARMADILLO_EARTH,
            SQUIRREL_T1, SQUIRREL_T2, SQUIRREL_T3, SURGE_T2, SURGE_T3
    );

    static {
        ALL.forEach(type -> TowerDescriptionRegistry.registerTemplate(type, type.description()));
    }

    private ThunderTowers() {
    }

    public static List<TowerType> all() {
        return ALL;
    }

    public static boolean isThunderTower(TowerType type) {
        return type != null && (ROD_IDS.contains(type.id())
                || ARMADILLO_IDS.contains(type.id())
                || SQUIRREL_IDS.contains(type.id()));
    }

    /** Generation towers: no attack, they only feed the grid. */
    public static boolean isRod(TowerType type) {
        return type != null && ROD_IDS.contains(type.id());
    }

    public static boolean isArmadillo(TowerType type) {
        return type != null && ARMADILLO_IDS.contains(type.id());
    }

    /** Insulated route: draws no power and converts lost health into generation. */
    public static boolean isInsulated(TowerType type) {
        return type != null && INSULATED_IDS.contains(type.id());
    }

    /** Grounded route: draws power and marks targets instead. */
    public static boolean isGrounded(TowerType type) {
        return type != null && GROUNDED_IDS.contains(type.id());
    }

    public static boolean isSquirrel(TowerType type) {
        return type != null && SQUIRREL_IDS.contains(type.id());
    }

    /** Surge line: converts spare power headroom into raw single-target damage. */
    public static boolean isSurge(TowerType type) {
        return type != null && SURGE_IDS.contains(type.id());
    }

    private static EntityVisual squirrel(double scale) {
        return squirrel(SQUIRREL_MODEL, scale);
    }

    private static EntityVisual surgeSquirrel(double scale) {
        return squirrel(SURGE_MODEL, scale);
    }

    private static EntityVisual squirrel(String model, double scale) {
        // The rabbit is gameplay fallback data only; players see the Blockbench model.
        return EntityVisual.builder(byId(EntityType.RABBIT))
                .blockbenchModel(model)
                .scale(scale)
                .build();
    }

    private static Set<String> ids(TowerType... types) {
        return Arrays.stream(types).map(TowerType::id).collect(Collectors.toUnmodifiableSet());
    }
}
