package kim.biryeong.semiontd.game;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.augment.AugmentEconomyState;
import kim.biryeong.semiontd.job.JobRegistry;
import kim.biryeong.semiontd.job.SemionJob;
import kim.biryeong.semiontd.trait.TraitLoadout;
import kim.biryeong.semiontd.trait.TraitLoadoutSnapshot;

public final class SemionPlayer {
    private final UUID uuid;
    private final String name;
    private final TeamId teamId;
    private final int laneId;
    private final PlayerEconomy economy;
    private final PlayerMatchStats matchStats = new PlayerMatchStats();
    private final PlayerAugmentState augments;
    private final AugmentEconomyState economyAugments = new AugmentEconomyState();
    private final AugmentTelemetry augmentTelemetry = new AugmentTelemetry();
    private TraitLoadout traitLoadout = TraitLoadout.none();
    private TraitLoadoutSnapshot traitLoadoutSnapshot = TraitLoadoutSnapshot.none();
    private SemionJob job;
    private String builderOrigin;
    private Boolean builderEnabled;

    public SemionPlayer(UUID uuid, String name, TeamId teamId, int laneId, PlayerEconomy economy) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.name = Objects.requireNonNull(name, "name");
        this.teamId = Objects.requireNonNull(teamId, "teamId");
        this.laneId = laneId;
        this.economy = Objects.requireNonNull(economy, "economy");
        this.augments = new PlayerAugmentState(uuid);
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public TeamId teamId() {
        return teamId;
    }

    public int laneId() {
        return laneId;
    }

    public PlayerEconomy economy() {
        return economy;
    }

    public PlayerMatchStats matchStats() {
        return matchStats;
    }

    public PlayerAugmentState augments() {
        return augments;
    }

    public AugmentEconomyState economyAugments() {
        return economyAugments;
    }

    public AugmentTelemetry augmentTelemetry() {
        return augmentTelemetry;
    }

    public String builderOrigin() {
        return builderOrigin;
    }

    public Boolean builderEnabled() {
        return builderEnabled;
    }

    public Optional<SemionJob> job() {
        return Optional.ofNullable(job);
    }

    public TraitLoadout traitLoadout() {
        return traitLoadout;
    }

    public TraitLoadoutSnapshot traitLoadoutSnapshot() {
        return traitLoadoutSnapshot;
    }

    public void assignTraitLoadout(TraitLoadout traitLoadout) {
        this.traitLoadout = traitLoadout == null ? TraitLoadout.none() : traitLoadout;
        this.traitLoadoutSnapshot = TraitLoadoutSnapshot.from(this.traitLoadout);
    }

    public void assignJob(SemionJob job) {
        this.job = Objects.requireNonNull(job, "job");
        this.builderOrigin = JobRegistry.officialBuilders().contains(job) ? "OFFICIAL"
                : JobRegistry.creativeBuilders().contains(job) ? "CREATIVE" : null;
        this.builderEnabled = JobRegistry.isEnabled(job);
    }
}
