package kim.biryeong.semiontd.cosmetic;

import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import java.util.ArrayList;
import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class SemionCosmeticItems {
    private static final List<Definition> DEFINITIONS = List.of(
            define("hats/uniques/pirate_hat", "해적선장 모자", ChatFormatting.LIGHT_PURPLE),
            define("hats/uniques/cook_hat", "토그 브란슈", ChatFormatting.AQUA),
            define("hats/uniques/newbie1_hat", "쑥쑥 자라다오 Lv.1", ChatFormatting.AQUA),
            define("hats/uniques/newbie2_hat", "쑥쑥 자라다오 Lv.2", ChatFormatting.AQUA),
            define("hats/uniques/newbie3_hat", "쑥쑥 자라다오 Lv.3", ChatFormatting.AQUA),
            define("hats/cats/persian", "머리 위 고양이(페르시안)", ChatFormatting.YELLOW),
            define("hats/cats/tabby", "머리 위 고양이(테비)", ChatFormatting.YELLOW),
            define("hats/cats/tuxedo", "머리 위 고양이(턱시도)", ChatFormatting.YELLOW),
            define("hats/cats/red", "머리 위 고양이(치즈)", ChatFormatting.YELLOW),
            define("hats/cats/siamese", "머리 위 고양이(샴)", ChatFormatting.YELLOW),
            define("hats/cats/british_shorthair", "머리 위 고양이(잉글랜드 숏헤어)", ChatFormatting.YELLOW),
            define("hats/cats/calico", "머리 위 고양이(캘리코)", ChatFormatting.YELLOW),
            define("hats/cats/ocelot", "머리 위 고양이(오셀롯)", ChatFormatting.YELLOW),
            define("hats/cats/ragdoll", "머리 위 고양이(레그돌)", ChatFormatting.YELLOW),
            define("hats/cats/white", "머리 위 고양이(화이트)", ChatFormatting.YELLOW),
            define("hats/cats/jellie", "머리 위 고양이(젤리)", ChatFormatting.YELLOW),
            define("hats/cats/black", "머리 위 고양이(블랙)", ChatFormatting.YELLOW),
            define("hats/villagers/armorer_hat", "갑옷제조인의 고글", ChatFormatting.YELLOW),
            define("hats/villagers/butcher_hat", "도살업자의 머리끈", ChatFormatting.YELLOW),
            define("hats/villagers/farmer_hat", "농부의 밀짚모자", ChatFormatting.YELLOW),
            define("hats/villagers/fisherman_hat", "어부의 낚시모자", ChatFormatting.YELLOW),
            define("hats/villagers/fletcher_hat", "화살제조인의 모자", ChatFormatting.YELLOW),
            define("hats/villagers/shepherd_hat", "양치기의 페도라", ChatFormatting.YELLOW),
            define("hats/villagers/cartographer_hat", "지도제작자의 외안경", ChatFormatting.YELLOW),
            define("hats/villagers/librarian_hat", "사서의 책모자", ChatFormatting.YELLOW),
            define("hats/villagers/weaponsmith_hat", "도구 대장장이의 안대", ChatFormatting.YELLOW),
            define("hats/uniques/wool_hat", "군밤장수 모자", ChatFormatting.YELLOW),
            define("hats/uniques/red_bini_hat", "빨간색 비니", ChatFormatting.YELLOW),
            define("hats/uniques/green_bini_hat", "초록색 비니", ChatFormatting.YELLOW),
            define("hats/uniques/black_bini_hat", "검은색 비니", ChatFormatting.YELLOW),
            define("hats/uniques/red_magic_hat", "빨간색 마술모자", ChatFormatting.YELLOW),
            define("hats/uniques/blue_magic_hat", "파란색 마술모자", ChatFormatting.YELLOW),
            define("hats/uniques/miner_hat", "광부 헬멧", ChatFormatting.YELLOW),
            define("hats/uniques/newtro_brown_hat", "뉴트로 브라운햇", ChatFormatting.YELLOW),
            define("hats/uniques/newtro_green_hat", "뉴트로 그린햇", ChatFormatting.YELLOW),
            define("hats/uniques/mask_white_hat", "하얀색 마스크", ChatFormatting.YELLOW),
            define("hats/uniques/mask_black_hat", "검은색 마스크", ChatFormatting.YELLOW),
            define("hats/uniques/glasses/classic_glasses_t", "검은색 뿔테안경[상]", ChatFormatting.YELLOW),
            define("hats/uniques/glasses/classic_glasses_b", "검은색 뿔테안경[하]", ChatFormatting.YELLOW),
            define("hats/uniques/glasses/model_student_glasses_b", "검은색 반무테안경[하]", ChatFormatting.YELLOW),
            define("hats/uniques/glasses/model_student_glasses_t", "검은색 반무테안경[상]", ChatFormatting.YELLOW),
            define("hats/uniques/glasses/white_glasses_b", "하얀색 뿔테안경[하]", ChatFormatting.YELLOW),
            define("hats/uniques/glasses/white_glasses_t", "하얀색 뿔테안경[상]", ChatFormatting.YELLOW),
            define("hats/uniques/glasses/white2_glasses_b", "하얀색 반 무테 안경[하]", ChatFormatting.YELLOW),
            define("hats/uniques/glasses/white2_glasses_t", "하얀색 반 무테 안경[상]", ChatFormatting.YELLOW),
            define("hats/uniques/glasses/blue_snorkel_glasses_b", "하늘색 스노클[하]", ChatFormatting.YELLOW),
            define("hats/uniques/glasses/blue_snorkel_glasses_t", "하늘색 스노클[상]", ChatFormatting.YELLOW),
            define("hats/uniques/glasses/yellow_snorkel_glasses_b", "노란색 스노클[하]", ChatFormatting.YELLOW),
            define("hats/uniques/glasses/yellow_snorkel_glasses_t", "노란색 스노클[상]", ChatFormatting.YELLOW),
            define("hats/uniques/santa_green_hat", "초록색 산타모자", ChatFormatting.YELLOW),
            define("hats/uniques/red_rose_hat", "빨간 장미꽃", ChatFormatting.YELLOW),
            define("hats/uniques/blue_rose_hat", "파란 장미꽃", ChatFormatting.YELLOW),
            define("hats/uniques/red_baseball_hat", "빨간색 베이스볼 캡", ChatFormatting.YELLOW),
            define("hats/uniques/blue_baseball_hat", "파란색 베이스볼 캡", ChatFormatting.YELLOW),
            define("hats/uniques/bread_hat", "빵 모자", ChatFormatting.AQUA),
            define("hats/uniques/full_hat", "넓은 챙 플로피", ChatFormatting.AQUA),
            define("hats/uniques/whitecap_hat", "화이트 캡", ChatFormatting.AQUA),
            define("hats/uniques/rabbit_hat", "토끼 귀", ChatFormatting.AQUA),
            define("hats/uniques/brown_beret_hat", "갈색 베레모", ChatFormatting.AQUA),
            define("hats/uniques/black_beret_hat", "검은색 베레모", ChatFormatting.AQUA),
            define("hats/uniques/rainbow_magic_hat", "무지개 마술모자", ChatFormatting.AQUA),
            define("hats/uniques/devil_hat", "악마 뿔", ChatFormatting.AQUA),
            define("hats/uniques/crocodile_snap_hat", "악어 스냅백", ChatFormatting.AQUA),
            define("hats/uniques/sansmask_hat", "샌즈 마스크", ChatFormatting.AQUA),
            define("hats/uniques/friskmask_hat", "프리스크 마스크", ChatFormatting.AQUA),
            define("hats/uniques/brown_ribbon_hat", "갈색 리본", ChatFormatting.AQUA),
            define("hats/uniques/flower_hat", "머리위의 백합", ChatFormatting.AQUA),
            define("hats/uniques/glasses/sunglasses_b", "검은색 선글라스[하]", ChatFormatting.AQUA),
            define("hats/uniques/glasses/sunglasses_t", "검은색 선글라스[상]", ChatFormatting.AQUA),
            define("hats/uniques/glasses/a_ing", "아잉 눈", ChatFormatting.AQUA),
            define("hats/uniques/glasses/do_ing", "도발적인 아잉 눈", ChatFormatting.AQUA),
            define("hats/uniques/glasses/rainbow_glasses_b", "무지개 선글라스[하]", ChatFormatting.AQUA),
            define("hats/uniques/glasses/rainbow_glasses_t", "무지개 선글라스[상]", ChatFormatting.AQUA),
            define("hats/uniques/santa_red_hat", "산타할아버지의 모자", ChatFormatting.AQUA),
            define("hats/uniques/giftbox_hat", "선물상자", ChatFormatting.AQUA),
            define("hats/uniques/super_snow_hat", "눈꽃모자", ChatFormatting.AQUA),
            define("hats/uniques/black_tiger_hat", "흑호 모자", ChatFormatting.LIGHT_PURPLE),
            define("hats/horses/horse_unicorn", "라떼는 말이야..(유니콘)", ChatFormatting.AQUA),
            define("hats/horses/horse_black", "라떼는 말이야..(블랙)", ChatFormatting.AQUA),
            define("hats/horses/horse_creamy", "라떼는 말이야..(크림)", ChatFormatting.AQUA),
            define("hats/horses/horse_zombie", "라떼는 말이야..(좀비)", ChatFormatting.AQUA),
            define("hats/horses/horse_skeleton", "라떼는 말이야..(스켈레톤)", ChatFormatting.AQUA),
            define("hats/horses/horse_brown", "라떼는 말이야..(브라운)", ChatFormatting.AQUA),
            define("hats/horses/horse_white", "라떼는 말이야..(화이트)", ChatFormatting.AQUA),
            define("hats/horses/donkey", "라떼는 말이야..(동키)", ChatFormatting.AQUA),
            define("hats/horses/horse_skeleton_sans", "라떼는 말이야..(삭제)", ChatFormatting.AQUA),
            define("hats/uniques/ikea_hat", "이케아 직원", ChatFormatting.AQUA),
            define("hats/uniques/agent_r_hat", "Agent R Hat", ChatFormatting.AQUA),
            define("hats/uniques/airline_hat", "Airline Hat", ChatFormatting.AQUA),
            define("hats/uniques/bandage_eyepatch_hat", "Bandage Eyepatch", ChatFormatting.AQUA),
            define("hats/uniques/bloodoath_hat", "Blood Oath Hat", ChatFormatting.AQUA),
            define("hats/uniques/creeper_snap_hat", "Creeper Snap Hat", ChatFormatting.AQUA),
            define("hats/uniques/duck_hat", "Duck Hat", ChatFormatting.AQUA),
            define("hats/uniques/duck_snaphat", "Duck Snap Hat", ChatFormatting.AQUA),
            define("hats/uniques/feedback_hat", "Feedback Hat", ChatFormatting.AQUA),
            define("hats/uniques/firespirit_hat", "Fire Spirit Hat", ChatFormatting.AQUA),
            define("hats/uniques/glasses/bit_sunglasses_b", "Bit Sunglasses [Lower]", ChatFormatting.AQUA),
            define("hats/uniques/glasses/bit_sunglasses_t", "Bit Sunglasses [Upper]", ChatFormatting.AQUA),
            define("hats/uniques/glasses/popcorn_sunglasses_b", "Popcorn Sunglasses [Lower]", ChatFormatting.AQUA),
            define("hats/uniques/glasses/popcorn_sunglasses_t", "Popcorn Sunglasses [Upper]", ChatFormatting.AQUA),
            define("hats/uniques/glasses/square2_glasses_b", "Square 2 Glasses [Lower]", ChatFormatting.AQUA),
            define("hats/uniques/glasses/square2_glasses_t", "Square 2 Glasses [Upper]", ChatFormatting.AQUA),
            define("hats/uniques/glasses/square_glasses_b", "Square Glasses [Lower]", ChatFormatting.AQUA),
            define("hats/uniques/glasses/square_glasses_t", "Square Glasses [Upper]", ChatFormatting.AQUA),
            define("hats/uniques/likey_hat", "Likey Hat", ChatFormatting.AQUA),
            define("hats/uniques/mask_creeper_hat", "Creeper Mask", ChatFormatting.AQUA),
            define("hats/uniques/melonsoda_hat", "Melon Soda Hat", ChatFormatting.AQUA),
            define("hats/uniques/neck_hat", "Neck Hat", ChatFormatting.AQUA),
            define("hats/uniques/plaza_hat", "Plaza Hat", ChatFormatting.AQUA),
            define("hats/uniques/rainbow_cap_hat", "Rainbow Cap", ChatFormatting.AQUA),
            define("hats/uniques/rascal_bear_hat", "Rascal Bear Hat", ChatFormatting.AQUA),
            define("hats/uniques/samurai_hat", "Samurai Hat", ChatFormatting.AQUA),
            define("hats/uniques/santa_mustache_hat", "Santa Mustache", ChatFormatting.AQUA),
            define("hats/uniques/snow_flame_hat", "Snow Flame Hat", ChatFormatting.AQUA),
            define("hats/uniques/starfin_hat", "Starfin Hat", ChatFormatting.AQUA),
            define("hats/uniques/white_duck_hat", "White Duck Hat", ChatFormatting.AQUA),
            define("hats/uniques/white_duck_snaphat", "White Duck Snap Hat", ChatFormatting.AQUA),
            define("hats/uniques/winter_hat", "Winter Hat", ChatFormatting.AQUA),
            define("hats/uniques/leiheng_hat", "뇌횡 모자", ChatFormatting.AQUA),
            define("cosmetics/rabbit_body", "토끼 슬링백", ChatFormatting.AQUA, EquipmentSlot.OFFHAND)
    );

    private static final List<Item> ITEMS = new ArrayList<>(DEFINITIONS.size());

    private SemionCosmeticItems() {
    }

    public static void register() {
        if (!ITEMS.isEmpty()) {
            return;
        }
        DEFINITIONS.forEach(definition -> ITEMS.add(register(definition)));
    }

    public static List<Item> items() {
        return List.copyOf(ITEMS);
    }

    private static Item register(Definition definition) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, definition.path());
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Item.Properties properties = new Item.Properties()
                .setId(key)
                .stacksTo(1)
                .equippable(definition.slot())
                .component(DataComponents.ITEM_NAME, Component.literal(definition.name()).withStyle(definition.color()));
        return Registry.register(
                BuiltInRegistries.ITEM,
                key,
                new SimplePolymerItem(properties, Items.PAPER, true)
        );
    }

    private static Definition define(String path, String name, ChatFormatting color) {
        return define(path, name, color, EquipmentSlot.HEAD);
    }

    private static Definition define(String path, String name, ChatFormatting color, EquipmentSlot slot) {
        return new Definition(path, name, color, slot);
    }

    private record Definition(String path, String name, ChatFormatting color, EquipmentSlot slot) {
    }
}
