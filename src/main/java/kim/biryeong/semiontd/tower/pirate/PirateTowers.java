package kim.biryeong.semiontd.tower.pirate;

import static kim.biryeong.semiontd.tower.catalog.ProductionTowerDefinitions.tower;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Stable public definitions for every pirate tower. */
public final class PirateTowers {
    private static final Pattern ABILITY_NUMBER = Pattern.compile("(?<![\\w])[-+]?\\d+(?:,\\d{3})*(?:\\.\\d+)?(?:~[-+]?\\d+(?:,\\d{3})*(?:\\.\\d+)?)?%?");

    public static final TowerType ADMIRAL = fighter("pirate_admiral", "제독", 1000, 100, 8, 15, 13, 25, player(), abilities(
            "다이아를 200 소비할 때마다 아래 재테크 효과 중 하나를 무작위로 발동합니다.",
            "재테크: 다이아를 50~75 얻습니다.",
            "재테크: 이 타워를 포함해, 이 타워의 소유자가 설치한 모든 타워의 최대 체력을 영구히 2~4 증가시킵니다.",
            "재테크: 이 타워를 포함해, 이 타워의 소유자가 설치한 모든 타워의 공격력을 영구히 0.5 증가시킵니다."
    ));
    public static final TowerType GOLDEN_ADMIRAL = fighter("pirate_golden_admiral", "황금빛 제독", 0, 400, 8, 15, 13, 25, player(), abilities(
            "다이아를 200 소비할 때마다 아래 재테크 효과 중 서로 다른 둘을 무작위로 발동합니다.",
            "재테크: 다이아를 50~75 얻습니다.",
            "재테크: 이 타워를 포함해, 이 타워의 소유자가 설치한 모든 타워의 최대 체력을 영구히 2~4 증가시킵니다.",
            "재테크: 이 타워를 포함해, 이 타워의 소유자가 설치한 모든 타워의 공격력을 영구히 0.5 증가시킵니다."
    ));
    public static final TowerType DECKHAND = fighter("pirate_deckhand", "갑판원", 75, 90, 2, 5, 17, 15, player(), abilities("처치할 때마다 다이아 1~3을 얻습니다.", "라운드 시작 후 6초간 공격력이 50% 증가합니다."));
    public static final TowerType LOOKOUT_DECKHAND = fighter("pirate_lookout_deckhand", "전망대 갑판원", 0, 150, 2, 7.5, 18, 15, player(), abilities("처치 시 다이아 2~3을 얻습니다.", "대상 주변 1.5칸에 50% 피해를 줍니다.", "라운드 시작 후 6초간 공격력이 50% 증가합니다."));
    public static final TowerType SOUL_REAVER_DECKHAND = fighter("pirate_soul_reaver_deckhand", "영혼 약탈자 갑판원", 0, 300, 2, 16, 17, 15, player(), abilities("처치 시 다이아 3~5를 얻습니다.", "대상 주변 2칸에 60% 피해를 줍니다.", "사망 시 주변 아군에게 3초간 공격 속도 +40%.", "라운드 시작 후 6초간 공격력이 50% 증가합니다."));
    public static final TowerType FERRYMAN = fighter("pirate_ferryman", "뱃사공", 200, 90, 6, 8, 20, 5, player(), abilities("다이아를 얻은 뒤, 추가로 2만큼 얻습니다.", "직접 판매 시 양수 순이익은 절반으로 줄이며 소수점은 버립니다.", "순이익: 환급액 + 뱃사공 보너스 합계 - 구매·업그레이드 비용.", "이 효과로 뱃사공의 효과를 발동시키지 않습니다."));
    public static final TowerType VETERAN_FERRYMAN = fighter("pirate_veteran_ferryman", "노련한 뱃사공", 0, 125, 6, 11, 18, 5, player(), abilities("다이아를 얻은 뒤, 추가로 3만큼 얻습니다.", "직접 판매 시 양수 순이익은 절반으로 줄이며 소수점은 버립니다.", "순이익: 환급액 + 뱃사공 보너스 합계 - 구매·업그레이드 비용.", "이 효과로 뱃사공의 효과를 발동시키지 않습니다."));
    public static final TowerType LEGENDARY_FERRYMAN = fighter("pirate_legendary_ferryman", "전설적인 뱃사공", 0, 165, 6, 15, 17, 5, player(), abilities("다이아를 얻은 뒤, 추가로 4만큼 얻습니다.", "직접 판매 시 양수 순이익은 절반으로 줄이며 소수점은 버립니다.", "순이익: 환급액 + 뱃사공 보너스 합계 - 구매·업그레이드 비용.", "이 효과로 뱃사공의 효과를 발동시키지 않습니다."));
    public static final TowerType BOAT_SINGER = fighter("pirate_boat_singer", "뱃노래꾼", 0, 110, 10, 10.5, 13, 10, player(), abilities("판매 시 현재 설치된 내 모든 보물상자가 1라운드 더 빠르게 열립니다."));
    public static final TowerType SWEET_BOAT_SINGER = fighter("pirate_sweet_boat_singer", "감미로운 뱃노래꾼", 0, 120, 10, 12, 11, 10, player(), abilities("판매 시 현재 설치된 내 모든 보물상자가 2라운드 더 빠르게 열립니다."));
    public static final TowerType SWORDSMAN = fighter("pirate_swordsman", "칼잡이", 550, 180, 3, 5, 17, 15, player(), abilities("받는 이로운 버프와 영구 능력치 보너스의 수치가 50% 증가합니다."));
    public static final TowerType IRON_SWORDSMAN = fighter("pirate_iron_swordsman", "무쇠 칼잡이", 0, 325, 3, 8, 17, 15, player(), abilities("받는 이로운 버프의 수치가 100% 증가합니다.", "라운드 첫 공격은 2배 피해와 2초간 이동 속도 -80%."));
    public static final TowerType PARROT = fighter("pirate_parrot", "앵무", 600, 80, 10, 20, 18, 5, parrot(.9), abilities("보유 다이아 100당 공격력 +2%, 최대 +500%.", "라운드 시작 후 3회 공격하기까지 사거리가 10, 공격력이 2.5배 증가합니다."));
    public static final TowerType ONE_EYED_PARROT = fighter("pirate_one_eyed_parrot", "애꾸눈 앵무", 0, 150, 10, 35, 17, 5, parrot(1), abilities("보유 다이아 100당 공격력 +3.5%, 최대 +500%.", "라운드 시작 후 3회 공격하기까지 사거리가 10, 공격력이 2.5배 증가합니다."));
    public static final TowerType CAPABLANCA = fighter("pirate_capablanca", "카파블랑카", 0, 330, 10, 60, 15, 5, parrot(1.25), abilities("보유 다이아 100당 공격력 +5%, 최대 +500%.", "에메랄드 100당 공격 속도 +5%, 최대 +100%.", "라운드 시작 후 3회 공격하기까지 사거리가 10, 공격력이 2.5배 증가합니다."));
    public static final TowerType HELMSMAN = fighter("pirate_helmsman", "조타수", 900, 300, 10, 20, 17, 15, player(), abilities("이번 라운드 소비 다이아 100당 최대 체력 +2.", "이번 라운드 소비 다이아 100당 공격력 +0.5."));
    public static final TowerType GUIDE = fighter("pirate_guide", "길잡이", 0, 350, 10, 25, 17, 5, player(), abilities("이번 라운드 소비 에메랄드 100당 최대 체력 +3.", "최대 체력 1,200 이상 시 대상의 잃은 체력 3.5% 추가 피해."));
    public static final TowerType NAVIGATOR = fighter("pirate_navigator", "항해사", 0, 500, 10, 30, 17, 15, player(), abilities("이번 라운드 소비 다이아 75당 최대 체력 +3.", "이번 라운드 소비 다이아 75당 공격력 +0.5."));
    public static final TowerType FIRST_NAVIGATOR = fighter("pirate_first_navigator", "일등 항해사", 0, 700, 10, 40, 15, 15, player(), abilities("이번 라운드 소비 다이아 50당 최대 체력 +5.", "이번 라운드 소비 다이아 50당 공격력 +0.5.", "대상 주변 2칸에 80% 피해."));
    public static final TowerType SHABBY_CHEST = support("pirate_shabby_chest", "허름한 보물상자", 50, 200, 50, block(Blocks.CHEST, 1.05), abilities("라운드 종료 5회 뒤 준비 시간에 다이아 90을 얻고 판매됩니다.", "판매 시 무작위 아군 최대 체력 +5."));
    public static final TowerType EMPIRE_CHEST = support("pirate_empire_chest", "제국의 보물상자", 0, 250, 50, block(Blocks.VAULT, 1.05), abilities("라운드 종료 5회 뒤 준비 시간에 다이아 200을 얻고 판매됩니다.", "어떤 방식으로 판매되어도 무작위 아군 공격력이 영구히 +1."));
    public static final TowerType DEEP_CHEST = support("pirate_deep_chest", "심해의 보물상자", 0, 250, 50, block(Blocks.VAULT, 1.05), abilities("라운드 종료 5회 뒤 준비 시간에 다이아 250을 얻고 판매됩니다.", "판매 시 무작위 아군 최대 체력 +10."));
    public static final TowerType FANTASY_CHEST = support("pirate_fantasy_chest", "환상의 보물상자", 0, 300, 50, block(Blocks.ENDER_CHEST, 1.05), abilities("라운드 종료 5회 뒤 준비 시간에 다이아 600을 얻고 판매됩니다.", "판매 시 모든 아군 최대 체력 +10."));
    public static final TowerType DROPPED_ANCHOR = support("pirate_dropped_anchor", "내려진 닻", 300, 220, 50, block(Blocks.DAMAGED_ANVIL, 1.25), abilities("라운드 시작 시 주변 2칸 아군 피해 감소 1%, 4초, 최대 2중첩.", "주변 판매마다 감소량 +0.1%, 최대 10%."));
    public static final TowerType DEEP_ANCHOR = support("pirate_deep_anchor", "깊게 박힌 닻", 0, 360, 50, block(Blocks.CHIPPED_ANVIL, 1.25), abilities("라운드 시작 시 주변 2칸 아군 피해 감소 2%, 7초, 최대 2중첩.", "주변 판매마다 감소량 +0.1%, 최대 10%."));
    public static final TowerType ANCIENT_ANCHOR = support("pirate_ancient_anchor", "고대의 닻", 0, 550, 50, block(Blocks.ANVIL, 1.25), abilities("라운드 시작 시 주변 2칸 아군 피해 감소 5%, 12초, 최대 2중첩.", "주변 판매마다 감소량 +0.1%, 최대 10%."));

    private static final List<TowerType> ALL = List.of(ADMIRAL, GOLDEN_ADMIRAL, DECKHAND, LOOKOUT_DECKHAND, SOUL_REAVER_DECKHAND, FERRYMAN, VETERAN_FERRYMAN, LEGENDARY_FERRYMAN, BOAT_SINGER, SWEET_BOAT_SINGER, SWORDSMAN, IRON_SWORDSMAN, PARROT, ONE_EYED_PARROT, CAPABLANCA, HELMSMAN, GUIDE, NAVIGATOR, FIRST_NAVIGATOR, SHABBY_CHEST, EMPIRE_CHEST, DEEP_CHEST, FANTASY_CHEST, DROPPED_ANCHOR, DEEP_ANCHOR, ANCIENT_ANCHOR);
    static { ALL.forEach(type -> TowerDescriptionRegistry.registerTemplate(type, type.description())); }
    private PirateTowers() { }
    public static List<TowerType> all() { return ALL; }
    public static boolean isPirateTower(TowerType type) { return ALL.stream().anyMatch(candidate -> matches(type, candidate)); }
    public static boolean isAdmiral(TowerType type) { return matches(type, ADMIRAL) || matches(type, GOLDEN_ADMIRAL); }
    public static boolean isDeckhand(TowerType type) { return matches(type, DECKHAND) || matches(type, LOOKOUT_DECKHAND) || matches(type, SOUL_REAVER_DECKHAND); }
    public static boolean isFerryman(TowerType type) { return matches(type, FERRYMAN) || matches(type, VETERAN_FERRYMAN) || matches(type, LEGENDARY_FERRYMAN); }
    public static boolean isFerrymanFamily(TowerType type) { return isFerryman(type) || isBoatSinger(type); }
    public static boolean isBoatSinger(TowerType type) { return matches(type, BOAT_SINGER) || matches(type, SWEET_BOAT_SINGER); }
    public static boolean isSwordsman(TowerType type) { return matches(type, SWORDSMAN) || matches(type, IRON_SWORDSMAN); }
    public static boolean isParrot(TowerType type) { return matches(type, PARROT) || matches(type, ONE_EYED_PARROT) || matches(type, CAPABLANCA); }
    public static boolean isHelmsman(TowerType type) { return matches(type, HELMSMAN) || matches(type, GUIDE) || matches(type, NAVIGATOR) || matches(type, FIRST_NAVIGATOR); }
    public static boolean isChest(TowerType type) { return matches(type, SHABBY_CHEST) || matches(type, EMPIRE_CHEST) || matches(type, DEEP_CHEST) || matches(type, FANTASY_CHEST); }
    public static boolean isAnchor(TowerType type) { return matches(type, DROPPED_ANCHOR) || matches(type, DEEP_ANCHOR) || matches(type, ANCIENT_ANCHOR); }
    public static boolean isPlayerVisual(TowerType type) { return isAdmiral(type) || isDeckhand(type) || isFerrymanFamily(type) || isSwordsman(type) || isHelmsman(type); }
    public static boolean matches(TowerType actual, TowerType expected) { return actual != null && actual.id().equals(expected.id()); }
    private static EntityVisual player() { return EntityVisual.builder("minecraft:armor_stand").build(); }
    private static EntityVisual parrot(double scale) { return EntityVisual.builder("minecraft:parrot").scale(scale).build(); }
    private static EntityVisual block(Block type, double scale) { return BlockDisplayVisual.builder(type.defaultBlockState()).scale(scale).build(); }
    public static String highlightAbilityNumbers(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        Matcher matcher = ABILITY_NUMBER.matcher(text);
        StringBuffer highlighted = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(highlighted, Matcher.quoteReplacement("<gold>" + matcher.group() + "</gold>"));
        }
        matcher.appendTail(highlighted);
        return highlighted.toString();
    }
    private static List<String> abilities(String... descriptions) { return Arrays.stream(descriptions).map(description -> " ▶ " + description).toList(); }
    private static TowerType fighter(String id, String name, long cost, double health, double range, double damage, int interval, int aggro, EntityVisual visual, String description) { return fighter(id, name, cost, health, range, damage, interval, aggro, visual, abilities(description)); }
    private static TowerType fighter(String id, String name, long cost, double health, double range, double damage, int interval, int aggro, EntityVisual visual, List<String> description) { return tower(id, name, cost, health, range, damage, interval, aggro, visual, description.stream().map(PirateTowers::highlightAbilityNumbers).toList()); }
    private static TowerType support(String id, String name, long cost, double health, int aggro, EntityVisual visual, List<String> description) { return TowerType.builder(id, name).category(TowerCategory.SUPPORT).mineralCost(cost).maxHealth(health).range(0).damage(0).attackIntervalTicks(20).aggroPriority(aggro).visual(visual).description(description.stream().map(PirateTowers::highlightAbilityNumbers).toList()).build(); }
}
