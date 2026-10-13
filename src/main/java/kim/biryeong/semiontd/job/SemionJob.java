package kim.biryeong.semiontd.job;

import java.util.List;
import java.util.Objects;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.summon.SummonMonsterType;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public abstract class SemionJob {
    private final Identifier id;
    private final Component displayName;
    private final List<Component> description;

    protected SemionJob(Identifier id, Component displayName, List<Component> description) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.description = List.copyOf(description == null ? List.of() : description);
    }

    protected SemionJob(String id, String displayName, String... description) {
        this(parseId(id), Component.literal(displayName), components(description));
    }

    public final Identifier id() {
        return id;
    }

    public final Component displayName() {
        return displayName;
    }

    public List<Component> description() {
        return description;
    }

    public boolean isHyperCarry() {
        return false;
    }

    public void onSelected(JobContext context) {
    }

    public void onMatchStarted(JobContext context) {
    }

    public void onRoundStarted(JobContext context, int round) {
    }

    public void onRoundEnded(JobContext context, int round) {
    }

    public void onEliminated(JobContext context) {
    }

    /**
     * Called when match runtime state is being closed, including cancellation and server shutdown.
     * Implementations must keep this idempotent because an eliminated player may already be cleaned up.
     */
    public void onMatchClosed(JobContext context) {
    }

    public long modifyStartingMineral(JobContext context, long baseMineral) {
        return baseMineral;
    }

    public long modifyStartingGas(JobContext context, long baseGas) {
        return baseGas;
    }

    public long modifyStartingIncome(JobContext context, long baseIncome) {
        return baseIncome;
    }

    public long modifyStartingGasPerSec(JobContext context, long baseGasPerSec) {
        return baseGasPerSec;
    }

    public boolean canUseSummon(JobContext context, SummonMonsterType summonType) {
        return true;
    }

    public boolean canUseTower(JobContext context, TowerType towerType) {
        return false;
    }

    /**
     * Optional shop category for a tower.
     *
     * <p>When every buildable tower of a job returns a group, the tower dialog shows those groups as
     * a first step instead of one long button grid. Returning {@code null} (the default) keeps the
     * flat list, so other builders are unaffected.
     */
    public String towerGroup(TowerType towerType) {
        return null;
    }

    public boolean includesTowerInCatalog(TowerType towerType) {
        return canUseTower(null, towerType);
    }

    public long modifySummonGasCost(JobContext context, SummonMonsterType summonType, long baseCost) {
        return baseCost;
    }

    public long modifySummonIncomeGain(JobContext context, SummonMonsterType summonType, long baseIncomeGain) {
        return baseIncomeGain;
    }

    public void onSummonedMonster(JobContext context, SummonMonsterType summonType, Monster monster) {
    }

    public long modifyKillMineralReward(JobContext context, Monster monster, long baseReward) {
        return baseReward;
    }

    public void onMonsterKilled(JobContext context, Monster monster, long mineralReward) {
    }

    private static Identifier parseId(String id) {
        Identifier parsed = Identifier.tryParse(Objects.requireNonNull(id, "id"));
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid job id: " + id);
        }
        return parsed;
    }

    private static List<Component> components(String[] lines) {
        if (lines == null || lines.length == 0) {
            return List.of();
        }
        return java.util.Arrays.stream(lines)
                .<Component>map(line -> Component.literal(line == null ? "" : line))
                .toList();
    }
}
