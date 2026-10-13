package kim.biryeong.semiontd.tower.pet;

import java.util.ArrayList;
import java.util.List;

final class PetTowerStatsView {
    private PetTowerStatsView() {
    }

    static List<String> create(PetTower tower, List<String> inherited) {
        List<String> lines = new ArrayList<>(inherited);
        if (tower.isOwner()) {
            lines.add("마당 범위 " + tower.yardRadius() + "칸");
            return lines;
        }
        if (tower.isLost()) {
            lines.add("길잃음 주인이 없어 출력 " + percent(PetBalance.lostPetMultiplier()));
            return lines;
        }
        lines.add("유대 " + oneDecimal(tower.bond()) + "/" + oneDecimal(tower.bondCap())
                + " (공격력 +" + percent(PetBalance.attackMultiplier(tower.bond()) - 1.0)
                + ", 체력 +" + percent(PetBalance.healthMultiplier(tower.bond()) - 1.0) + ")");
        double required = PetBalance.bondToUpgrade(tower.type());
        if (required > 0.0) {
            lines.add("승급 자격 " + (tower.isAdult() ? "충족 (성체)" : "유대 " + oneDecimal(required) + " 필요"));
        }
        lines.add("마당 반려 " + tower.yardCompanions() + "/" + ((tower.yardRadius() * 2 + 1) * (tower.yardRadius() * 2 + 1) - 1));
        if (tower.hasFamilyYard()) {
            lines.add("우리 가족 활성: 모든 종 무리 참여, 고양이 독립, 새 동시 회복");
        }
        if (tower.augmentSnapshot().has(PetTower.LEADER)
                && tower.logicalId().equals(tower.augmentSnapshot().choice(PetTower.LEADER).primaryTargetId())) {
            lines.add("우리 동네 대장: 공동 공격 적중 " + tower.leaderHits() + "/"
                    + (int) tower.augmentSnapshot().parameter(PetTower.LEADER, "hitsRequired", 3));
        }
        switch (tower.role()) {
            case DOG -> {
                lines.add("무리 " + tower.packSize() + "마리, 공격력 +"
                        + percent(PetBalance.packBonus(tower.type(), tower.packSize())) + ", 체력 +"
                        + percent(PetBalance.packHealthBonus(tower.type(), tower.packSize())));
                if (tower.hasAdultCombatAbilities()) {
                    lines.add("성체 효과 받는 피해 -" + percent(PetBalance.adultDamageReduction(tower.type())));
                }
            }
            case CAT -> {
                lines.add("독립 " + (tower.isSoloCat()
                        ? "활성, 공격력 +" + percent(PetBalance.soloBonus(tower.type()))
                        : "비활성 (같은 마당에 다른 고양이가 있습니다)"));
                if (tower.hasAdultCombatAbilities()) {
                    lines.add("성체 효과 스플래시 " + oneDecimal(PetBalance.adultSplashRadius(tower.type()))
                            + "칸, 최대 " + PetBalance.adultSplashMaxTargets(tower.type()) + "마리, 피해 "
                            + percent(PetBalance.adultSplashDamageRatio(tower.type())));
                }
            }
            case BIRD -> {
                int targets = tower.hasFamilyYard() ? (int) tower.augmentSnapshot().parameter(PetTower.FAMILY, "healTargets", 3)
                        : (int) tower.augmentSnapshot().parameter(PetTower.GROWN_UP, "healTargets", 1);
                lines.add("회복 입힌 피해의 " + percent(PetBalance.healRatio(tower.type()))
                        + ", " + (tower.augmentSnapshot().has(PetTower.GROWN_UP) ? "자신 + 다른 반려 " : "반려 ")
                        + "최대 " + targets + "마리");
            }
            default -> {
            }
        }
        return lines;
    }

    private static String oneDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static String percent(double value) {
        return oneDecimal(value * 100.0) + "%";
    }

}
