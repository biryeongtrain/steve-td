package kim.biryeong.semiontd.summon;

import java.util.Map;

public final class SummonDisplayNames {
    private static final Map<String, Name> NAMES = Map.ofEntries(
            Map.entry("goat", new Name("Messi", "메시")),
            Map.entry("pillager", new Name("Pillager", "약탈자")),
            Map.entry("piglin_brute", new Name("Piglin Brute", "피글린 야수")),
            Map.entry("ravager", new Name("Ravager", "파괴수")),
            Map.entry("hoglin", new Name("Hoglin", "호글린")),
            Map.entry("horse", new Name("Horse", "말")),
            Map.entry("llama", new Name("Llama", "라마")),
            Map.entry("phantom", new Name("Phantom", "팬텀")),
            Map.entry("enderman", new Name("Enderman", "엔더맨")),
            Map.entry("breeze", new Name("Breeze", "브리즈")),
            Map.entry("guardian", new Name("Guardian", "가디언")),
            Map.entry("magma_cube", new Name("Magma Cube", "마그마 큐브")),
            Map.entry("ocelot", new Name("Ocelot", "오실롯")),
            Map.entry("vindicator", new Name("Vindicator", "변명자")),
            Map.entry("witch", new Name("Witch", "마녀")),
            Map.entry("iron_golem", new Name("Iron Golem", "철 골렘")),
            Map.entry("blaze", new Name("Blaze", "블레이즈")),
            Map.entry("shulker", new Name("Shulker", "셜커")),
            Map.entry("ghast", new Name("Ghast", "가스트")),
            Map.entry("zoglin", new Name("Zoglin", "조글린")),
            Map.entry("wither_skeleton", new Name("Wither Skeleton", "위더 스켈레톤")),
            Map.entry("evoker", new Name("Evoker", "소환사")),
            Map.entry("elder_guardian", new Name("Elder Guardian", "엘더 가디언")),
            Map.entry("warden", new Name("Warden", "워든"))
    );

    private SummonDisplayNames() {
    }

    public static String localize(String id, String configuredName) {
        Name name = NAMES.get(id);
        return name != null && name.legacy().equals(configuredName) ? name.korean() : configuredName;
    }

    private record Name(String legacy, String korean) {
    }
}
