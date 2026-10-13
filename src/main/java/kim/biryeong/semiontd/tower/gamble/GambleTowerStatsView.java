package kim.biryeong.semiontd.tower.gamble;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import kim.biryeong.semiontd.tower.TowerUpgradeOption;

final class GambleTowerStatsView {
    private GambleTowerStatsView() {
    }

    static List<String> upgradeTooltipLines(GamblerTower tower, TowerUpgradeOption option) {
        boolean bottomKing = tower.augmentSnapshot().has("job_gamble_g2");
        ArrayList<String> lines = new ArrayList<>(GambleBet.fromUpgradeId(option.id()).map(bet -> switch (bet) {
            case ODD -> List.of(
                    "주사위 한 개를 굴려 홀수가 나오면 성공, 짝수가 나오면 실패합니다.",
                    "성공하면 " + statRewardSummary(GambleBalance.oddEvenWinScore()) + " 중 하나를 얻습니다.",
                    "실패하면 " + statRewardSummary(-GambleBalance.oddEvenLossScore())
                            + " 중 하나가 적용됩니다.",
                    "손실 보험 보유 시 실패 수치는 " + statRewardSummary(-GambleBalance.oddEvenLossScore()
                            * (1.0 - GambleBalance.lossInsuranceReduction())) + "로 완화됩니다.",
                    "비용은 판매 환불가에 포함되지 않습니다."
            );
            case EVEN -> List.of(
                    "주사위 한 개를 굴려 짝수가 나오면 성공, 홀수가 나오면 실패합니다.",
                    "성공하면 " + statRewardSummary(GambleBalance.oddEvenWinScore()) + " 중 하나를 얻습니다.",
                    "실패하면 " + statRewardSummary(-GambleBalance.oddEvenLossScore())
                            + " 중 하나가 적용됩니다.",
                    "손실 보험 보유 시 실패 수치는 " + statRewardSummary(-GambleBalance.oddEvenLossScore()
                            * (1.0 - GambleBalance.lossInsuranceReduction())) + "로 완화됩니다.",
                    "비용은 판매 환불가에 포함되지 않습니다."
            );
            case TWO_DICE -> List.of(
                    "내 라인에 살아 있는 내 주사위 타워가 필요합니다 (단계·거리 무관).",
                    "주사위 두 개를 굴려 눈금의 합에 비례해 유닛을 업그레이드합니다.",
                    "합이 2~5면 능력치가 크게 내려가고, 6~12면 크게 올라갑니다.",
                    "합이 " + GambleBalance.twoDiceCompoundMinSum()
                            + " 이상이면 보상을 서로 다른 능력치 두 개가 절반씩 나눠 받습니다.",
                    "가장 자주 나오는 합 7은 " + statRewardSummary(GambleBalance.twoDiceScore(7))
                            + " 중 하나를 줍니다.",
                    "성공 시 " + oneDecimal(GambleBalance.abilityRewardChance() * 100) + "% 확률로 손실 보험을 얻으며, " + oneDecimal(GambleBalance.oddEvenWinScore())
                            + "점까지만 보험으로 바뀌고 나머지는 능력치로 지급됩니다.",
                    "같은 눈이 나오면 변화량이 두 배가 되며 비용은 판매 환불가에 포함되지 않습니다."
            );
            case SLOTS -> slotTooltipLines();
        }).orElseGet(List::of));
        boolean diceBet = GambleBet.fromUpgradeId(option.id()).filter(bet -> bet != GambleBet.SLOTS).isPresent();
        if (bottomKing && diceBet) {
            lines.add("바닥의 왕: 실패마다 점수 손실 "
                    + oneDecimal(tower.augmentSnapshot().parameter("job_gamble_g2", "failureScoreMultiplier", 2))
                    + "배. " + oneDecimal(tower.augmentSnapshot().parameter("job_gamble_g2", "statReversalChance", .2) * 100)
                    + "% 확률로 능력치 감소가 같은 양의 증가로 바뀝니다.");
        }
        if (diceBet && tower.augmentSnapshot().has("job_gamble_p")) {
            lines.add("끝장을 보자: 한 번 결제하고 성공할 때까지 최대 "
                    + (int) tower.augmentSnapshot().parameter("job_gamble_p", "maxAttempts", 3)
                    + "회 시도하며 매 시도를 정산합니다.");
        }
        return List.copyOf(lines);
    }

    static List<String> runtimeDetailLines(GamblerTower tower) {
        GambleState state = tower.state();
        ArrayList<String> lines = new ArrayList<>();
        lines.add("도박 횟수: " + state.totalBets());
        lines.add("누적 도박 점수: " + signed(state.cumulativeScore())
                + " / +" + oneDecimal(GambleBalance.maxGambleScore()));
        if (state.atScoreCap()) {
            lines.add("도박 상태: 종료 (최대 점수 도달)");
        }
        lines.add("최대 체력 변화: " + signed(state.maxHealthDelta()));
        lines.add("공격력 변화: " + signed(state.damageDelta()));
        lines.add("마법 공격력 변화: " + signed(state.magicDamageDelta()));
        lines.add("기본 공격 구성: 일반 " + oneDecimal(state.resolvedValue(GambleStat.DAMAGE, tower.type().damage()))
                + " / 마법 " + oneDecimal(tower.magicAttackDamage(null)));
        lines.add("사거리 변화: " + signed(state.rangeDelta()));
        lines.add("고정 공격 범위: " + oneDecimal(tower.splashRadius()) + "칸");
        if (state.abilities().isEmpty()) {
            lines.add("보유 능력: 없음");
        } else {
            lines.add("보유 능력:");
            for (GambleAbility ability : GambleAbility.values()) {
                if (state.has(ability)) {
                    lines.add(ability.detailLine());
                }
            }
        }
        lines.add("최근 결과: " + state.lastResult());
        if (tower.augmentSnapshot().has("job_gamble_p")) {
            lines.add("잭팟 폭발: " + tower.jackpotCharges() + "/"
                    + (int) tower.augmentSnapshot().parameter("job_gamble_p", "maxCharges", 3));
        }
        return List.copyOf(lines);
    }

    private static List<String> slotTooltipLines() {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("내 라인에 살아 있는 내 슬롯머신 타워가 필요합니다 (단계·거리 무관).");
        lines.add("6종 심볼을 같은 확률로 세 칸에 뽑습니다. 순서와 관계없이 일치를 판정합니다.");
        lines.add("전부 다름 55.56% / 2개 일치 41.67% / 3개 일치 2.78%");
        GambleSlots.Symbol[] symbols = GambleSlots.Symbol.values();
        lines.add("전부 다르면 " + statRewardSummary(GambleSlots.resolve(
                symbols[0], symbols[1], symbols[2]).score()) + " 중 하나를 얻습니다.");
        for (GambleSlots.Symbol symbol : symbols) {
            GambleSlots.Symbol other = symbols[(symbol.ordinal() + 1) % symbols.length];
            lines.add(symbol.displayName() + ": 2개 +" + oneDecimal(GambleSlots.resolve(symbol, symbol, other).score())
                    + "점 / 3개 +" + oneDecimal(GambleSlots.resolve(symbol, symbol, symbol).score()) + "점");
        }
        lines.add("3개 일치는 서로 다른 능력치 두 개가 보상을 절반씩 나눠 받습니다.");
        lines.add("능력치 감소와 손실 보험 획득은 없으며 비용은 판매 환불가에 포함되지 않습니다.");
        lines.add("끝장을 보자의 재시도·잭팟 폭발 충전은 적용되지 않습니다.");
        return List.copyOf(lines);
    }

    static String signed(double value) {
        return (value >= 0.0 ? "+" : "") + oneDecimal(value);
    }

    private static String statRewardSummary(double score) {
        return "체력 " + signed(GambleBalance.statDelta(GambleStat.MAX_HEALTH, score))
                + "·공격력 " + signed(GambleBalance.statDelta(GambleStat.DAMAGE, score))
                + "·마법 공격력 " + signed(GambleBalance.statDelta(GambleStat.MAGIC_DAMAGE, score))
                + "·사거리 " + signed(GambleBalance.statDelta(GambleStat.RANGE, score));
    }

    private static String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
