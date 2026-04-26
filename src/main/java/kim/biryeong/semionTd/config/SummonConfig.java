package kim.biryeong.semionTd.config;

import java.util.List;

public record SummonConfig(List<SummonMonsterEntry> summons) {
    public SummonConfig {
        summons = summons == null ? List.of() : List.copyOf(summons);
    }

    public static SummonConfig defaultConfig() {
        return new SummonConfig(List.of(
                new SummonMonsterEntry(
                        "grunt",
                        "Grunt",
                        20,
                        2,
                        50,
                        0,
                        5,
                        AttackKind.MELEE,
                        "minecraft:zombie",
                        5
                )
        ));
    }
}