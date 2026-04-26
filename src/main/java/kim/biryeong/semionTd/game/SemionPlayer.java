package kim.biryeong.semionTd.game;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semionTd.job.SemionJob;

public final class SemionPlayer {
    private final UUID uuid;
    private final String name;
    private final TeamId teamId;
    private final int laneId;
    private final PlayerEconomy economy;
    private final PlayerMatchStats matchStats = new PlayerMatchStats();
    private SemionJob job;

    public SemionPlayer(UUID uuid, String name, TeamId teamId, int laneId, PlayerEconomy economy) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.name = Objects.requireNonNull(name, "name");
        this.teamId = Objects.requireNonNull(teamId, "teamId");
        this.laneId = laneId;
        this.economy = Objects.requireNonNull(economy, "economy");
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

    public Optional<SemionJob> job() {
        return Optional.ofNullable(job);
    }

    public void assignJob(SemionJob job) {
        this.job = Objects.requireNonNull(job, "job");
    }
}
