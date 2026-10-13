package kim.biryeong.semiontd.trait;

import java.util.List;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public abstract class SemionTrait {
    private final Identifier id;
    private final int version;
    private final Component displayName;
    private final List<Component> description;
    private final Component primaryEffectSummary;
    private final Component secondaryEffectSummary;

    protected SemionTrait(Identifier id, Component displayName, List<Component> description) {
        this(id, 1, displayName, description);
    }

    protected SemionTrait(Identifier id, int version, Component displayName, List<Component> description) {
        this(id, version, displayName, description, Component.empty(), Component.empty());
    }

    protected SemionTrait(
            Identifier id,
            int version,
            Component displayName,
            List<Component> description,
            Component primaryEffectSummary,
            Component secondaryEffectSummary
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.version = Math.max(0, version);
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.description = List.copyOf(description == null ? List.of() : description);
        this.primaryEffectSummary = Objects.requireNonNull(primaryEffectSummary, "primaryEffectSummary");
        this.secondaryEffectSummary = Objects.requireNonNull(secondaryEffectSummary, "secondaryEffectSummary");
    }

    public Identifier id() {
        return id;
    }

    public int version() {
        return version;
    }

    public Component displayName() {
        return displayName;
    }

    public List<Component> description() {
        return description;
    }

    public Component effectSummary(TraitSlot slot) {
        return slot == TraitSlot.PRIMARY ? primaryEffectSummary : secondaryEffectSummary;
    }

    public long modifyStartingMineral(TraitContext context, TraitSlot slot, long value) {
        return value;
    }

    public long modifyStartingGas(TraitContext context, TraitSlot slot, long value) {
        return value;
    }

    public long modifyStartingIncome(TraitContext context, TraitSlot slot, long value) {
        return value;
    }

    public long modifyStartingGasPerSec(TraitContext context, TraitSlot slot, long value) {
        return value;
    }

    public void onRoundStarted(TraitContext context, TraitSlot slot, int round) {
    }

    public void onRoundEnded(TraitContext context, TraitSlot slot, int round) {
    }
}
