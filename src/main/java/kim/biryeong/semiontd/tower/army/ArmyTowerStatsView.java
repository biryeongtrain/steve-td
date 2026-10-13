package kim.biryeong.semiontd.tower.army;

import java.util.ArrayList;
import java.util.List;

final class ArmyTowerStatsView {
    private ArmyTowerStatsView() {
    }

    static List<String> create(ArmyTower tower) {
        ArrayList<String> lines = new ArrayList<>();
        if (tower.isTemporaryCopy()) {
            lines.add("예비군 · 라운드 종료까지 참전 · 계급 오라와 전역 보상 없음");
            return lines;
        }
        if (!tower.ranks()) {
            lines.add("계급 없음 · 짬의 영향을 받지 않습니다");
            addSupportLines(tower, lines);
            return lines;
        }

        ArmyRank current = tower.rank();
        lines.add("계급 " + tower.rankTitle(current) + " (짬 " + tower.service() + ")");
        lines.add("공격력 " + percentInteger(current.attackMultiplier()) + " · 후임 버프 +"
                + percentInteger(current.damageBuff()));

        int untilPromotion = ArmyRank.wavesUntilPromotion(tower.service());
        int untilDischarge = ArmyRank.wavesUntilDischarge(tower.service());
        if (untilDischarge <= ArmyBalance.dischargeNoticeWaves()) {
            lines.add("<red>전역까지 " + untilDischarge + "웨이브</red>");
        } else if (untilPromotion > 0) {
            lines.add("다음 진급까지 " + untilPromotion + "웨이브 · 전역까지 " + untilDischarge + "웨이브");
        } else {
            lines.add("전역까지 " + untilDischarge + "웨이브");
        }

        double medal = ArmyStates.medalBonus(tower.ownerPlayer());
        if (medal > 0.0) {
            lines.add("훈장 " + ArmyStates.medalCount(tower.ownerPlayer()) + "개 · 공격력 +" + percentInteger(medal));
        }
        return lines;
    }

    private static void addSupportLines(ArmyTower tower, ArrayList<String> lines) {
        double bonus = ArmyBalance.serviceRateBonus(tower.type().id());
        if (bonus != 0.0) {
            String sign = bonus > 0 ? "+" : "";
            lines.add("주변 아군 짬 " + sign + Math.round(bonus) + "/웨이브");
        }
    }

    private static String percentInteger(double value) {
        return Math.round(value * 100.0) + "%";
    }
}
