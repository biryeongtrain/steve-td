package kim.biryeong.semiontd.tower.developer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Runtime dialog lines for a 개발자 tower.
 *
 * <p>This family carries far more hidden state than any other — accumulated patches per stat,
 * instability, up to four defects and permanent trades — so the dialog is the only way a player can
 * tell two identical-looking command blocks apart. Everything the interface offers has to be
 * legible here first.
 *
 * <p>Bug names are hidden until the player owns a 테스터. That is the tower's whole purpose: before
 * it exists the player knows something is wrong but not what, which is the intended early-game
 * experience rather than an oversight.
 */
public final class DeveloperTowerLines {
    private DeveloperTowerLines() {
    }

    public static List<String> describe(DeveloperTower tower) {
        if (tower == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        appendPatches(tower, lines);
        appendInstability(tower, lines);
        appendBugs(tower, lines);
        appendOptimizations(tower, lines);
        appendStatus(tower, lines);
        return List.copyOf(lines);
    }

    private static void appendPatches(DeveloperTower tower, List<String> lines) {
        List<String> parts = new ArrayList<>();
        for (DeveloperPatch patch : DeveloperPatch.values()) {
            double active = DeveloperTowerData.activeAmount(tower, patch);
            if (active <= 0.0) {
                continue;
            }
            parts.add("<yellow>" + patch.displayName() + "</yellow> " + format(patch, active));
        }
        if (!parts.isEmpty()) {
            lines.add("<aqua>누적 패치</aqua> <gray>" + String.join("  ", parts) + "</gray>");
        }

        List<String> pending = new ArrayList<>();
        for (DeveloperPatch patch : DeveloperPatch.values()) {
            double amount = DeveloperTowerData.pendingAmount(tower, patch);
            if (amount <= 0.0) {
                continue;
            }
            pending.add("<yellow>" + patch.displayName() + "</yellow> " + format(patch, amount));
        }
        if (!pending.isEmpty()) {
            lines.add("<dark_aqua>다음 라운드 적용</dark_aqua> <gray>" + String.join("  ", pending) + "</gray>");
        }
    }

    private static String format(DeveloperPatch patch, double amount) {
        if (patch.isFlat()) {
            return "+" + Math.round(amount);
        }
        // 연사는 간격을 나누는 배수라 표기도 증가 방향이다.
        return "+" + String.format(Locale.ROOT, "%.0f%%", amount * 100.0);
    }

    private static void appendInstability(DeveloperTower tower, List<String> lines) {
        if (DeveloperTowers.isLts(tower.type())) {
            lines.add("<green>불안정 면역</green> <gray>핫픽스를 제한 없이 받습니다.</gray>");
            return;
        }
        int instability = DeveloperTowerData.instability(tower);
        if (instability <= 0) {
            return;
        }
        int max = DeveloperBalance.maxInstability();
        double chance = Math.min(1.0, instability * DeveloperBalance.instabilityStallChance());
        lines.add("<red>불안정</red> <gray>" + bar(instability, max) + " " + instability + "</gray>"
                + " <dark_gray>웨이브 중 정지 확률 " + Math.round(chance * 100.0) + "%</dark_gray>");
    }

    private static void appendBugs(DeveloperTower tower, List<String> lines) {
        Set<DeveloperBug> bugs = DeveloperTowerData.bugs(tower);
        if (bugs.isEmpty()) {
            return;
        }
        boolean visible = DeveloperStates.of(tower.ownerPlayer()).bugsVisible();
        if (!visible) {
            lines.add("<dark_red>버그</dark_red> <gray>정체불명의 결함 " + bugs.size() + "건</gray>"
                    + " <dark_gray>테스터를 지으면 내용이 보입니다</dark_gray>");
            return;
        }
        List<String> names = new ArrayList<>(bugs.size());
        bugs.forEach(bug -> names.add("<light_purple>" + bug.displayName() + "</light_purple>"));
        lines.add("<dark_red>버그</dark_red> <gray>" + String.join(" · ", names) + "</gray>");
    }

    private static void appendOptimizations(DeveloperTower tower, List<String> lines) {
        Set<DeveloperOptimization> optimizations = DeveloperTowerData.optimizations(tower);
        if (optimizations.isEmpty()) {
            return;
        }
        List<String> names = new ArrayList<>(optimizations.size());
        optimizations.forEach(entry -> names.add("<gold>" + entry.displayName() + "</gold>"));
        lines.add("<yellow>최적화</yellow> <gray>" + String.join(" · ", names) + "</gray>");
    }

    private static void appendStatus(DeveloperTower tower, List<String> lines) {
        if (DeveloperTowerData.isPinned(tower)) {
            lines.add("<green>버전 고정됨</green> <gray>패치도 버그도 걸리지 않습니다.</gray>");
        }
        int round = tower.currentRoundNumber();
        if (DeveloperTowerData.underMaintenance(tower, round)) {
            lines.add("<gold>긴급 점검 중</gold> <gray>이번 라운드는 작동하지 않습니다.</gray>");
        } else if (DeveloperTowerData.hasMaintenanceBonus(tower, round)) {
            lines.add("<green>점검 완료</green> <gray>이번 라운드 공격력이 "
                    + Math.round(DeveloperBalance.maintenanceDamageBonus() * 100.0) + "% 높습니다.</gray>");
        }
        if (tower.stalledByInstability()) {
            lines.add("<red>불안정으로 정지했습니다.</red>");
        }
    }

    private static String bar(int value, int max) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < max; index++) {
            builder.append(index < value ? '█' : '░');
        }
        return builder.toString();
    }
}
