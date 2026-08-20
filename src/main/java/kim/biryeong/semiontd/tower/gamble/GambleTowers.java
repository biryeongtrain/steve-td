package kim.biryeong.semiontd.tower.gamble;

import java.util.List;
import kim.biryeong.semiontd.entity.visual.BlockDisplayVisual;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.entity.visual.VillagerVisual;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

public final class GambleTowers {
    private static final EntityVisual GAMBLER_VISUAL = EntityVisual.builder("minecraft:wandering_trader")
            .scale(1.0).build();

    public static final TowerType DICE_T1 = support(
            "gamble_dice_t1", "주사위 타워 I", 45, 10, 3.5,
            BlockDisplayVisual.builder(Blocks.WHITE_CONCRETE.defaultBlockState()).scale(0.75).build(),
            List.of(
                    "각 주사위 타워는 라운드마다 주사위 한 개를 굴려 범위 안의 내 전투 타워에 같은 효과를 줍니다.",
                    "눈 1~2는 약화, 3~4는 단일 강화, 5~6은 복합 강화입니다.",
                    "강화하면 약화 수치는 유지되고, 긍정 효과와 지원 범위만 증가합니다.",
                    "공격하지 않으며 지원 범위와 이번 라운드 눈금이 타워 머리 위에 표시됩니다."
            )
    );
    public static final TowerType DICE_T2 = support(
            "gamble_dice_t2", "주사위 타워 II", 0, 10, 5,
            BlockDisplayVisual.builder(Blocks.WHITE_CONCRETE.defaultBlockState()).scale(0.90).build(),
            List.of(
                    "긍정 효과가 같은 눈의 I단계보다 2배로 증가하며, 약화 수치는 증가하지 않습니다.",
                    "지원 범위가 5칸으로 넓어집니다.",
                    "눈 1~2는 약화, 3~4는 단일 강화, 5~6은 복합 강화입니다.",
                    "공격하지 않으며 지원 범위와 이번 라운드 눈금이 타워 머리 위에 표시됩니다."
            )
    );
    public static final TowerType DICE_T3 = support(
            "gamble_dice_t3", "주사위 타워 III", 0, 10, 6.5,
            BlockDisplayVisual.builder(Blocks.WHITE_CONCRETE.defaultBlockState()).scale(1.05).build(),
            List.of(
                    "긍정 효과가 같은 눈의 I단계보다 3.5배로 증가하며, 약화 수치는 증가하지 않습니다.",
                    "지원 범위가 6.5칸으로 넓어집니다.",
                    "눈 1~2는 약화, 3~4는 단일 강화, 5~6은 복합 강화입니다.",
                    "공격하지 않으며 지원 범위와 이번 라운드 눈금이 타워 머리 위에 표시됩니다."
            )
    );
    public static final TowerType GAMBLER = TowerType.builder("gamble_gambler", "도박꾼 타워")
            .mineralCost(60).maxHealth(110).range(6.5).damage(10).attackIntervalTicks(13)
            .visual(GAMBLER_VISUAL)
            .description(List.of(
                    "준비 시간에 홀수·짝수는 80 다이아, 주사위 두 개는 160 다이아를 내고 반복할 수 있습니다.",
                    "주사위 눈에 따라 최대 체력·공격력·사거리 중 무작위 능력치가 오르거나 내려갑니다.",
                    "주사위 두 개의 합이 {ability.gamble_global.twoDiceCompoundMinSum:integer} 이상이면 서로 다른 능력치 두 개가 보상을 나눠 받습니다.",
                    "기본 공격은 반경 {ability.gamble_global.baseSplashRadius:blocks} 안의 적에게도 피해를 줍니다.",
                    "좋은 결과가 나오면 {ability.gamble_global.abilityRewardChance:percent} 확률로 능력치 상승 대신 손실 보험을 얻습니다.",
                    "손실 보험은 능력치 감소량을 {ability.gamble_global.lossInsuranceReduction:percent} 줄입니다.",
                    "도박 직후 누적 점수가 +{ability.gamble_global.kingPromotionScore:integer} 이상이면 도박왕, "
                            + "-{ability.gamble_global.darkKingPromotionScoreMagnitude:integer} 이하이면 어둠의 도박왕으로 전직합니다.",
                    "누적 점수가 +{ability.gamble_global.maxGambleScore:integer}에 도달하면 세 가지 도박 업그레이드가 종료됩니다."
            )).build();
    public static final TowerType KING = TowerType.builder("gamble_king", "도박왕")
            .mineralCost(0).maxHealth(400).range(7.5).damage(40).attackIntervalTicks(8)
            .visual(GAMBLER_VISUAL)
            .description(List.of(
                    "누적 도박 점수 +{ability.gamble_global.kingPromotionScore:integer}을 달성한 도박꾼의 최종 전직입니다.",
                    "전직 전 도박 횟수·누적 점수·능력치 변화·손실 보험을 모두 유지합니다.",
                    "기본 체력과 공격력, 공격 속도가 대폭 증가하고 사거리와 범위 피해 반경이 증가합니다.",
                    "범위 피해 반경이 {ability.splashRadiusBonus:blocks}만큼 증가합니다.",
                    "누적 점수 +{ability.gamble_global.maxGambleScore:integer}에 도달하기 전까지 세 가지 도박을 계속할 수 있습니다."
            )).build();
    public static final TowerType DARK_KING = TowerType.builder("gamble_dark_king", "어둠의 도박왕")
            .mineralCost(0).maxHealth(440).range(8).damage(44).attackIntervalTicks(8)
            .visual(GAMBLER_VISUAL)
            .description(List.of(
                    "누적 도박 점수 -{ability.gamble_global.darkKingPromotionScoreMagnitude:integer} 이하에 도달한 도박꾼의 최종 전직입니다.",
                    "전직 전 도박 횟수·누적 점수·능력치 변화·손실 보험을 모두 유지합니다.",
                    "도박왕보다 기본 체력·공격력·사거리와 범위 피해 반경이 소폭 높습니다.",
                    "범위 피해 반경이 {ability.splashRadiusBonus:blocks}만큼 증가합니다.",
                    "누적 점수 +{ability.gamble_global.maxGambleScore:integer}에 도달하기 전까지 세 가지 도박을 계속할 수 있습니다."
            )).build();
    public static final TowerType SPECTATOR_T1 = support(
            "gamble_spectator_t1", "구경꾼 타워 I", 45, 10, 3.5,
            VillagerVisual.builder().profession(VillagerProfession.NITWIT).build(),
            List.of(
                    "누적 도박 점수가 가장 높은 도박꾼 하나를 집중 지원합니다.",
                    "도박꾼 하나에는 구경꾼이 최대 {ability.gamble_global.maxSpectatorsPerGambler:integer}기까지 연결됩니다.",
                    "눈 1~2는 능력치 2개 약화, 3~4는 능력치 2개 강화, 5~6은 네 능력치 모두 강화입니다.",
                    "눈 6이 나오면 다이아 {ability.faceSixDiamondReward:integer}개를 얻습니다.",
                    "강화하면 약화 수치는 유지되고, 긍정 효과와 연결 범위만 증가합니다.",
                    "공격하지 않으며 연결선과 이번 라운드 눈금이 타워 머리 위에 표시됩니다."
            )
    );
    public static final TowerType SPECTATOR_T2 = support(
            "gamble_spectator_t2", "구경꾼 타워 II", 0, 10, 5,
            VillagerVisual.builder().profession(VillagerProfession.LIBRARIAN).build(),
            List.of(
                    "누적 도박 점수가 가장 높은 도박꾼 하나를 집중 지원합니다.",
                    "도박꾼 하나에는 구경꾼이 최대 {ability.gamble_global.maxSpectatorsPerGambler:integer}기까지 연결됩니다.",
                    "긍정 효과가 같은 눈의 I단계보다 2배로 증가하며, 약화 수치는 증가하지 않습니다.",
                    "눈 1~2는 능력치 2개 약화, 3~4는 능력치 2개 강화, 5~6은 네 능력치 모두 강화입니다.",
                    "눈 6이 나오면 다이아 {ability.faceSixDiamondReward:integer}개를 얻습니다.",
                    "공격하지 않으며 연결선과 이번 라운드 눈금이 타워 머리 위에 표시됩니다."
            )
    );
    public static final TowerType SPECTATOR_T3 = support(
            "gamble_spectator_t3", "구경꾼 타워 III", 0, 10, 6.5,
            VillagerVisual.builder().profession(VillagerProfession.CLERIC).build(),
            List.of(
                    "누적 도박 점수가 가장 높은 도박꾼 하나를 집중 지원합니다.",
                    "도박꾼 하나에는 구경꾼이 최대 {ability.gamble_global.maxSpectatorsPerGambler:integer}기까지 연결됩니다.",
                    "긍정 효과가 같은 눈의 I단계보다 3.5배로 증가하며, 약화 수치는 증가하지 않습니다.",
                    "눈 1~2는 능력치 2개 약화, 3~4는 능력치 2개 강화, 5~6은 네 능력치 모두 강화입니다.",
                    "눈 6이 나오면 다이아 {ability.faceSixDiamondReward:integer}개를 얻습니다.",
                    "공격하지 않으며 연결선과 이번 라운드 눈금이 타워 머리 위에 표시됩니다."
            )
    );

    private static final List<TowerType> ALL = List.of(
            DICE_T1, DICE_T2, DICE_T3, GAMBLER, KING, DARK_KING,
            SPECTATOR_T1, SPECTATOR_T2, SPECTATOR_T3
    );

    static {
        ALL.forEach(type -> TowerDescriptionRegistry.registerTemplate(type, type.description()));
    }

    private GambleTowers() {
    }

    public static List<TowerType> all() {
        return ALL;
    }

    public static boolean isGambleTower(TowerType type) {
        return type != null && ALL.stream().anyMatch(candidate -> candidate.id().equals(type.id()));
    }

    public static boolean isDice(TowerType type) {
        return matches(type, DICE_T1) || matches(type, DICE_T2) || matches(type, DICE_T3);
    }

    public static boolean isSpectator(TowerType type) {
        return matches(type, SPECTATOR_T1) || matches(type, SPECTATOR_T2) || matches(type, SPECTATOR_T3);
    }

    public static boolean isGambler(TowerType type) {
        return matches(type, GAMBLER) || matches(type, KING) || matches(type, DARK_KING);
    }

    public static boolean isKing(TowerType type) {
        return matches(type, KING);
    }

    public static boolean isDarkKing(TowerType type) {
        return matches(type, DARK_KING);
    }

    public static TowerType promotionTarget(TowerType current, double cumulativeScore) {
        if (!matches(current, GAMBLER)) {
            return null;
        }
        if (cumulativeScore <= GambleBalance.darkKingPromotionScore()) {
            return DARK_KING;
        }
        if (cumulativeScore >= GambleBalance.kingPromotionScore()) {
            return KING;
        }
        return null;
    }

    private static boolean matches(TowerType actual, TowerType expected) {
        return actual != null && actual.id().equals(expected.id());
    }

    private static TowerType support(
            String id, String name, long cost, double health, double range,
            EntityVisual visual, List<String> description
    ) {
        return TowerType.builder(id, name).category(TowerCategory.SUPPORT).mineralCost(cost)
                .maxHealth(health).range(range).damage(0).attackIntervalTicks(20).aggroPriority(-20)
                .visual(visual).description(description)
                .build();
    }
}
