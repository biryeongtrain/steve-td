package kim.biryeong.semiontd.tower.end;

import java.util.Objects;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;

final class EndTransferFactory {
    private final EndConfig config;

    EndTransferFactory(EndConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    EndTransferState.Progress create(Tower source) {
        return create(source, 1.0);
    }

    EndTransferState.Progress create(Tower source, double durationMultiplier) {
        EndConfig.TransferRule rule = config.transfer();
        EndTransferState.Progress initial = create(source.type(), rule);
        EndTransferState.Progress progress = new EndTransferState.Progress(
                Math.max(1, (int) Math.ceil(initial.durationTicks * durationMultiplier)),
                initial.roundHealthBonus, initial.permanentHealthBonus, initial.roundDamageBonus,
                initial.permanentDamageBonus, initial.completionHealing, initial.periodicHealingPerSecond);
        progress.sourceMaxHealth = source.currentMaxHealth();
        return progress;
    }

    static EndTransferState.Progress create(TowerType sourceType, EndConfig.TransferRule rule) {
        boolean shulkerLine = EndTowers.isShulkerLine(sourceType);
        boolean endCrystalLine = EndTowers.isEndCrystalLine(sourceType);
        double maxHealth = sourceType.maxHealth();
        double damage = sourceType.damage();
        return new EndTransferState.Progress(
                rule.durationTicks(),
                shulkerLine ? maxHealth * rule.roundHealthRatio() : 0.0,
                shulkerLine ? maxHealth * rule.permanentHealthRatio() : 0.0,
                endCrystalLine ? damage * rule.roundDamageRatio() : 0.0,
                endCrystalLine ? damage * rule.permanentDamageRatio() : 0.0,
                rule.completionHealing(),
                shulkerLine ? maxHealth * rule.periodicHealingRatio() : 0.0
        );
    }
}
