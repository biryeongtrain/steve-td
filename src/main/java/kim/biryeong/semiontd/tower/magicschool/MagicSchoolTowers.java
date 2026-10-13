package kim.biryeong.semiontd.tower.magicschool;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.tower;

import java.util.List;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.world.level.block.Blocks;

public final class MagicSchoolTowers {
    public static final String CONFIG_ID = "magic_school_global";
    public static final TowerType HOGWARTS = hogwarts();

    public static final TowerType FRESHMAN = wizardTower(tower(
            "magic_school_freshman_t1", "신입생", 100, 200.0, 8.0, 30.0, 22, 0,
            BlockDisplayVisual.builder(Blocks.LIGHT.defaultBlockState()).build(),
            List.of(
                    "<gray>기본 주문은 " + MagicSchoolSpell.EXPELLIARMUS.displayName() + "입니다.</gray>",
                    "<aqua>기본 공격이 공격력 {ability.magic_school_spell_expelliarmus.damageMultiplier*100:number}%의 마법 피해를 입힙니다. 라운드 첫 공격 대상은 {ability.magic_school_spell_expelliarmus.disarmTicks/20:number}초간 공격할 수 없습니다.</aqua>",
                    "<gray>우클릭 후 주문 버튼으로 지정된 주문을 확인하고 선택합니다.</gray>",
                    proficiencyDescription(),
                    "<yellow>기숙사 배정 모자 구매와 최대 숙련도 달성 후 기숙사 마법사로 진급할 수 있습니다.</yellow>"
            )
    ));
    public static final TowerType GRYFFINDOR = houseWizard("gryffindor", "그리핀도르", 340, 50, 17,
            "<gold>기본 공격 주기 {stat.attackIntervalTicks:integer}틱. 최대 체력이 가장 높은 적을 공격합니다.</gold>");
    public static final TowerType HUFFLEPUFF = houseWizard("hufflepuff", "후플푸프", 374, 50, 18,
            "<yellow>기본 체력 {stat.maxHealth:number}. 사거리 안의 가장 먼 적을 공격합니다.</yellow>");
    public static final TowerType RAVENCLAW = houseWizard("ravenclaw", "래번클로", 340, 50, 18,
            "<aqua>주문 변경 비용이 무료이며 숙련도 획득량이 {ability.proficiencyGainBonus:percent} 증가합니다. 가장 가까운 적을 공격합니다.</aqua>");
    public static final TowerType SLYTHERIN = houseWizard("slytherin", "슬리데린", 340, 55, 18,
            "<green>기본 공격력 {stat.damage:number}. 현재 체력이 가장 낮은 적을 공격합니다.</green>");
    private static final List<TowerType> HOUSE_WIZARDS = List.of(GRYFFINDOR, HUFFLEPUFF, RAVENCLAW, SLYTHERIN);
    public static final TowerType BRAVE_ARCHWIZARD = archWizard(GRYFFINDOR, "용감한 대마법사", 500, 80, 11);
    public static final TowerType KIND_ARCHWIZARD = archWizard(HUFFLEPUFF, "친절한 대마법사", 550, 80, 12);
    public static final TowerType WISE_ARCHWIZARD = archWizard(RAVENCLAW, "지혜로운 대마법사", 500, 80, 12);
    public static final TowerType CUNNING_ARCHWIZARD = archWizard(SLYTHERIN, "교활한 대마법사", 500, 88, 12);
    private static final List<TowerType> ARCHWIZARDS = List.of(BRAVE_ARCHWIZARD, KIND_ARCHWIZARD, WISE_ARCHWIZARD, CUNNING_ARCHWIZARD);
    private static final List<TowerType> ALL = List.of(HOGWARTS, FRESHMAN,
            GRYFFINDOR, HUFFLEPUFF, RAVENCLAW, SLYTHERIN,
            BRAVE_ARCHWIZARD, KIND_ARCHWIZARD, WISE_ARCHWIZARD, CUNNING_ARCHWIZARD);

    static {
        ALL.forEach(type -> TowerDescriptionRegistry.registerTemplate(type, type.description()));
    }

    private MagicSchoolTowers() {
    }

    private static TowerType hogwarts() {
        return tower(
                "magic_school_hogwarts_t1", "호그와트", 0, 1, 0.0, 0.0, 20, 0,
                BlockDisplayVisual.builder(Blocks.ENCHANTING_TABLE.defaultBlockState()).build(),
                List.of(
                        "<gray>이동하거나 공격하지 않으며, 공격받지 않는 마법학교입니다.</gray>",
                        "<yellow>플레이어마다 하나만 설치할 수 있습니다.</yellow>",
                        "<gray>타워 수용량을 사용하지 않으며, 혼자 남으면 방어에 실패합니다.</gray>",
                        "<aqua>커리큘럼에서 라운드당 한 번 수업·기숙사·주문 단계를 해금할 수 있습니다.</aqua>"
                )
        );
    }

    private static TowerType wizardTower(TowerType type) {
        return type.withPrimaryDamageType(DamageType.MAGIC);
    }

    private static TowerType houseWizard(String house, String name, double health, double damage, int interval, String specialty) {
        return wizardTower(tower("magic_school_" + house + "_t2", name + " 마법사", 200, health, 10, damage, interval, 0,
                BlockDisplayVisual.builder(Blocks.LIGHT.defaultBlockState()).build(),
                List.of(specialty, proficiencyDescription(),
                        "<gray>진급 시 주문을 유지하고 숙련도는 초기화합니다. 최대 숙련도 달성 후 같은 기숙사의 대마법사로 진급합니다.</gray>")));
    }

    private static TowerType archWizard(TowerType house, String name, double health, double damage, int interval) {
        return wizardTower(tower(house.id().replace("_t2", "_t3"), name, 450, health, 10, damage, interval, 0,
                BlockDisplayVisual.builder(Blocks.LIGHT.defaultBlockState()).build(),
                List.of(house.description().getFirst(), proficiencyDescription(),
                        "<gray>기숙사 특성과 맞춤형 지팡이 효과를 유지합니다. 진급 시 주문을 유지하고 숙련도는 초기화합니다.</gray>")));
    }

    private static String proficiencyDescription() {
        return "<aqua>최대 주문 숙련도 {ability.maxProficiency:integer}. 전투 시작 시 "
                + "{ability.magic_school_global.waveProficiencyBase:integer} + 현재 라운드만큼 획득합니다. "
                + "1당 공격력·체력이 각각 {ability.proficiencyDamagePerPoint:percent}·"
                + "{ability.proficiencyHealthPerPoint:percent} 증가합니다.</aqua>";
    }

    public static List<TowerType> houseWizards() {
        return HOUSE_WIZARDS;
    }

    public static boolean isHouseWizard(TowerType type) {
        return type != null && HOUSE_WIZARDS.stream().anyMatch(tower -> tower.id().equals(type.id()));
    }

    public static List<TowerType> archWizards() {
        return ARCHWIZARDS;
    }

    public static boolean isArchWizard(TowerType type) {
        return type != null && ARCHWIZARDS.stream().anyMatch(tower -> tower.id().equals(type.id()));
    }

    public static TowerType archWizardFor(TowerType house) {
        return switch (house.id()) {
            case "magic_school_gryffindor_t2" -> BRAVE_ARCHWIZARD;
            case "magic_school_hufflepuff_t2" -> KIND_ARCHWIZARD;
            case "magic_school_ravenclaw_t2" -> WISE_ARCHWIZARD;
            case "magic_school_slytherin_t2" -> CUNNING_ARCHWIZARD;
            default -> throw new IllegalArgumentException("Not a T2 house wizard: " + house.id());
        };
    }

    public static boolean belongsToHouse(TowerType type, TowerType house) {
        return type != null && (house.id().equals(type.id()) || archWizardFor(house).id().equals(type.id()));
    }

    public static List<TowerType> all() {
        return ALL;
    }

    public static boolean isMagicSchoolTower(TowerType type) {
        return type != null && ALL.stream().anyMatch(tower -> tower.id().equals(type.id()));
    }

    public static boolean isHogwarts(TowerType type) {
        return type != null && HOGWARTS.id().equals(type.id());
    }

    public static boolean isFreshman(TowerType type) {
        return type != null && FRESHMAN.id().equals(type.id());
    }

    public static double proficiencyPerPoint(TowerType type) {
        return isFreshman(type) ? .0015 : isHouseWizard(type) ? .0008 : .0005;
    }

    public static boolean isWizard(TowerType type) {
        return isMagicSchoolTower(type) && !isHogwarts(type);
    }
}
