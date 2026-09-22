package kim.biryeong.semiontd.tower.gamble;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.TowerAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaTargetMode;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class GambleSupportTower extends ProductionTower {
    private final int[] lastRollCounts = new int[6];
    private final List<GridPosition> linkedTargetPositions = new ArrayList<>();
    private List<GambleSupportEffect> activeEffects = List.of();
    private int lastFace;
    private long lastDiamondReward;
    private int affectedTargets;
    private boolean waveActive;
    private int rangeVfxTicks;
    private int effectRefreshTicks;

    public GambleSupportTower(
            TowerType type, UUID ownerPlayer, TeamId teamId, int laneId,
            GridPosition originalPosition, GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    @Override
    public boolean canChaseTargets() {
        return false;
    }

    /**
     * The configured range is a support radius, not a combat range. Keeping the
     * entity attack range at zero also prevents attack animations and zero-damage hits.
     */
    @Override
    public double adjustAttackRange(double baseRange) {
        return 0.0;
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        super.onPlaced(lane);
        GambleRoundEffects.towerEntity(this, lane).ifPresent(this::showRange);
        rangeVfxTicks = GambleBalance.supportVfxIntervalTicks();
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        waveActive = false;
        linkedTargetPositions.clear();
        activeEffects = List.of();
        lastFace = 0;
        lastDiamondReward = 0L;
        super.resetForRound(lane);
    }

    @Override
    public void tick(PlayerLane lane) {
        super.tick(lane);
        GambleRollLabels.sync(lane, ownerPlayer(), this);
        if (isDestroyed(lane)) {
            return;
        }
        if (waveActive && --effectRefreshTicks <= 0) {
            restoreLinkedEffects(lane);
            effectRefreshTicks = 20;
        }
        if (--rangeVfxTicks > 0) return;
        GambleRoundEffects.towerEntity(this, lane).ifPresent(source -> {
            if (waveActive) {
                showPersistentVfx(source, lane);
            } else {
                showRange(source);
            }
        });
        rangeVfxTicks = GambleBalance.supportVfxIntervalTicks();
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        waveActive = true;
        Arrays.fill(lastRollCounts, 0);
        linkedTargetPositions.clear();
        activeEffects = List.of();
        lastFace = 0;
        lastDiamondReward = 0L;
        affectedTargets = 0;
        SemionTowerEntity source = GambleRoundEffects.towerEntity(this, lane).orElse(null);
        if (source == null || isDestroyed(lane)) {
            return;
        }

        ResourceLocation sourceId = GambleRoundEffects.sourceId(this);
        GambleRoundEffects.rememberSource(lane, ownerPlayer(), sourceId);
        GambleRoundEffects.clearSource(lane, ownerPlayer(), sourceId);
        int minimum = GambleBalance.minimumRoll(type());
        int face = GambleSupportRolls.rollFace(minimum, source.getRandom(),
                AugmentCombat.allowsTriggers() && augmentSnapshot().has("job_gamble_g1"));
        double insurance = AugmentCombat.allowsTriggers() && augmentSnapshot().has("job_gamble_s")
                ? augmentSnapshot().parameter("job_gamble_s", "oppositeEffectRatio", .5) : 0.0;
        activeEffects = GambleSupportRolls.roll(type(), face, source.getRandom(), insurance);
        lastFace = face;
        lastRollCounts[face - 1] = 1;
        lastDiamondReward = GambleSpectatorRewards.awardFaceSix(ownerPlayer(), type(), face);
        GambleRollLabels.show(lane, ownerPlayer(), this, sourceId, face);
        List<Vec3> positiveHits = new ArrayList<>();
        List<Vec3> negativeHits = new ArrayList<>();
        Tower spectatorTarget = GambleTowers.isSpectator(type())
                ? GambleRoundEffects.assignSpectator(
                        lane, ownerPlayer(), sourceId, source, type().range()).orElse(null)
                : null;
        TowerAreaEffectRequest request = TowerAreaEffectRequest.aroundTower(
                AreaEffectIds.tower(this, "round_roll"), source, type().range(),
                TowerAreaTargetMode.REGISTERED, AreaVfxSpec.none()
        ).withFilter(target -> target.tower() != this
                && ownerPlayer().equals(target.tower().ownerPlayer())
                && acceptsTarget(target.tower())
                && (!GambleTowers.isSpectator(type()) || target.tower() == spectatorTarget)
                && target.entity().isPresent());

        SemionTdApi.areaEffects().applyToTowers(request, target -> {
            boolean changed = applyActiveEffects(target.entity().orElseThrow(), sourceId);
            linkedTargetPositions.add(target.tower().originalPosition());
            affectedTargets++;
            Vec3 hit = target.entity().orElseThrow().position().add(0.0, 0.7, 0.0);
            (face <= 2 ? negativeHits : positiveHits).add(hit);
            return changed ? AreaEffectOutcome.APPLIED : AreaEffectOutcome.UNCHANGED;
        });

        showRollVfx(source, positiveHits, negativeHits);
        if (positiveHits.isEmpty() && negativeHits.isEmpty()) {
            showRange(source);
        }
        rangeVfxTicks = GambleBalance.supportVfxIntervalTicks();
        effectRefreshTicks = 20;
    }

    @Override
    public void onLaneCleared(PlayerLane lane) {
        GambleRollLabels.clearSource(lane, ownerPlayer(), GambleRoundEffects.sourceId(this));
    }

    @Override
    public void onDeath(PlayerLane lane) {
        ResourceLocation sourceId = GambleRoundEffects.sourceId(this);
        GambleRoundEffects.clearSource(lane, ownerPlayer(), sourceId);
        linkedTargetPositions.clear();
        activeEffects = List.of();
        waveActive = false;
        lastFace = 0;
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        if (previousTower instanceof GambleSupportTower previous) {
            System.arraycopy(previous.lastRollCounts, 0, lastRollCounts, 0, lastRollCounts.length);
            linkedTargetPositions.addAll(previous.linkedTargetPositions);
            activeEffects = List.copyOf(previous.activeEffects);
            lastFace = previous.lastFace;
            lastDiamondReward = previous.lastDiamondReward;
            affectedTargets = previous.affectedTargets;
            waveActive = previous.waveActive;
            rangeVfxTicks = previous.rangeVfxTicks;
            effectRefreshTicks = previous.effectRefreshTicks;
        }
    }

    @Override
    public List<String> runtimeDetailLines() {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("이번 라운드 대상: " + affectedTargets + "기");
        lines.add("이번 라운드 눈: " + (lastFace == 0 ? "아직 굴리지 않음" : Integer.toString(lastFace)));
        if (lastDiamondReward > 0L) {
            lines.add("눈 6 보상: 다이아 +" + lastDiamondReward);
        }
        activeEffects.forEach(effect -> lines.add("적용 효과: " + effect.displayLine()));
        lines.add("지원 범위: " + oneDecimal(type().range()) + "칸");
        return List.copyOf(lines);
    }

    int[] lastRollCounts() {
        return lastRollCounts.clone();
    }

    int affectedTargets() {
        return affectedTargets;
    }

    int linkedTargets() {
        return linkedTargetPositions.size();
    }

    List<GambleSupportEffect> activeEffects() {
        return activeEffects;
    }

    private boolean acceptsTarget(Tower target) {
        return target.type().category() != TowerCategory.SUPPORT
                && (!GambleTowers.isSpectator(type())
                || target instanceof GamblerTower
                || target.type().id().equals(GambleTowers.GAMBLER.id()));
    }

    private void showPersistentVfx(SemionTowerEntity source, PlayerLane lane) {
        List<Vec3> positiveHits = new ArrayList<>();
        List<Vec3> negativeHits = new ArrayList<>();
        linkedTargetPositions.forEach(position -> linkedTarget(lane, position)
                .flatMap(target -> GambleRoundEffects.towerEntity(target, lane)).ifPresent(entity -> {
            Vec3 hit = entity.position().add(0.0, 0.7, 0.0);
            (lastFace <= 2 ? negativeHits : positiveHits).add(hit);
        }));
        if (positiveHits.isEmpty() && negativeHits.isEmpty()) {
            showRange(source);
            return;
        }
        showConnectionVfx(source, positiveHits, negativeHits);
    }

    private void showRollVfx(SemionTowerEntity source, List<Vec3> positiveHits, List<Vec3> negativeHits) {
        showConnectionVfx(source, positiveHits, negativeHits);
        int faceParticles = IntStream.range(0, lastRollCounts.length)
                .map(index -> (index + 1) * lastRollCounts[index]).sum();
        if (source.level() instanceof net.minecraft.server.level.ServerLevel level) {
            level.sendParticles(ParticleTypes.END_ROD, source.getX(), source.getY() + 1.1, source.getZ(),
                    Math.min(36, faceParticles), 0.25, 0.25, 0.25, 0.01);
        }
    }

    private void showConnectionVfx(
            SemionTowerEntity source, List<Vec3> positiveHits, List<Vec3> negativeHits
    ) {
        if (!positiveHits.isEmpty()) {
            TowerVfxService.showAreaEffect(source, AreaEffectIds.tower(this, "positive_rolls"),
                    AreaVfxStyles.BUFF, source.position(), type().range(), positiveHits,
                    affectedTargets, positiveHits.size(), 0);
        }
        if (!negativeHits.isEmpty()) {
            TowerVfxService.showAreaEffect(source, AreaEffectIds.tower(this, "negative_rolls"),
                    AreaVfxStyles.DEBUFF, source.position(), type().range(), negativeHits,
                    affectedTargets, negativeHits.size(), 0);
        }
    }

    private void showRange(SemionTowerEntity source) {
        TowerVfxService.showAreaEffect(source, AreaEffectIds.tower(this, "support_range"),
                AreaVfxStyles.BUFF, source.position(), type().range(), List.of(), 0, 0, 0);
    }

    private boolean applyActiveEffects(SemionTowerEntity entity, ResourceLocation sourceId) {
        boolean changed = false;
        for (GambleSupportEffect effect : activeEffects) {
            changed |= entity.setPersistentEffect(effect.type(), sourceId, effect.magnitude());
        }
        return changed;
    }

    private void restoreLinkedEffects(PlayerLane lane) {
        ResourceLocation sourceId = GambleRoundEffects.sourceId(this);
        linkedTargetPositions.forEach(position -> linkedTarget(lane, position)
                .flatMap(target -> GambleRoundEffects.towerEntity(target, lane))
                .ifPresent(entity -> applyActiveEffects(entity, sourceId)));
    }

    private java.util.Optional<Tower> linkedTarget(PlayerLane lane, GridPosition position) {
        return lane.towers().stream()
                .filter(target -> ownerPlayer().equals(target.ownerPlayer()))
                .filter(target -> position.equals(target.originalPosition()))
                .findFirst();
    }

}
