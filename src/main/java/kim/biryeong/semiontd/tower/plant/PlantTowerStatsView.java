package kim.biryeong.semiontd.tower.plant;

import java.util.ArrayList;
import java.util.List;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;

final class PlantTowerStatsView {
    private PlantTowerStatsView() {
    }

    static List<String> create(PlantCombatTower tower) {
        PlantSoil soil = tower.standingSoil();
        List<String> lines = new ArrayList<>();
        if (soil == null) {
            lines.add("맨땅 위라 지형 효과가 없습니다.");
            return lines;
        }
        lines.add(soil.displayName() + " 위 · 지형 " + PlantSoilStates.count(tower.ownerPlayer(), soil) + "칸");
        lines.add("개화 피해 +" + percentInteger(tower.bloomBonus()));
        switch (soil) {
            case MEADOW -> {
                lines.add("성장 최대 체력 +" + percentInteger(tower.growthBonus())
                        + " · " + tower.growthRounds() + "라운드째");
                // 툴팁은 지형 기본값을 보여 주므로, 여기서는 배율까지 곱한 실제 적용값을 보여 줍니다.
                double healPercent = tower.scaled(PlantSoil.MEADOW, "healPercentPerPulse");
                if (healPercent > 0.0) {
                    lines.add("회복 " + percentInteger(healPercent) + "/펄스 · 범위 "
                            + oneDecimal(tower.scaled(PlantSoil.MEADOW, "supportRadius")));
                }
                long diamondPerWave = tower.diamondPerWave();
                if (diamondPerWave > 0L) {
                    lines.add("웨이브 정산 다이아 +" + diamondPerWave);
                }
                double novaRadius = TowerBalanceRuntime.ability(tower.type().id(), "novaRadius", 0.0);
                if (novaRadius > 0.0) {
                    lines.add("광역 반경 " + oneDecimal(novaRadius)
                            + " · 피해 " + percentInteger(TowerBalanceRuntime.ability(tower.type().id(), "novaDamageRatio", 0.0)));
                }
            }
            case MYCELIUM -> lines.add("균사 " + PlantSoilStates.count(tower.ownerPlayer(), PlantSoil.MYCELIUM)
                    + "칸 · 라인 전체 취약 +" + percentInteger(PlantSoilEnvironment.myceliumFieldFrailty(tower.ownerPlayer())));
            case DESERT -> lines.add("공속 감소 -" + percentInteger(tower.scaled(soil, "attackSpeedReduction"))
                    + ", 가시 반사 " + percentInteger(tower.scaled(soil, "thornReflectRatio"))
                    + " +" + oneDecimal(tower.type().damage()));
            case PODZOL -> {
                lines.add("사거리 +" + oneDecimal(tower.scaled(soil, "rangeBonus"))
                        + ", 공격 속도 +" + percentInteger(tower.scaled(soil, "attackSpeedBonus")));
                lines.add("성장 피해 +" + percentInteger(tower.damageGrowthBonus())
                        + " · " + tower.growthRounds() + "라운드째");
            }
        }
        return lines;
    }

    private static String oneDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static String percentInteger(double value) {
        return Math.round(value * 100.0) + "%";
    }
}
