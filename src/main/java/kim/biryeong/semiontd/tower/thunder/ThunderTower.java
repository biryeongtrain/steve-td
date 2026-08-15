package kim.biryeong.semiontd.tower.thunder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

/**
 * Runtime tower for the 람쥐썬더 family.
 *
 * <p>Every tower that draws power reads the same grid snapshot, so a single load factor scales the
 * whole family at once. Towers that draw nothing — the insulated tank route — deliberately skip
 * that scaling: a tank whose job is to hold the line cannot be allowed to weaken exactly when the
 * grid is failing.
 */
public class ThunderTower extends ProductionTower {
    /** How often the grid is recomputed. Walking the lane roster every tick is wasteful. */
    private static final int GRID_SCAN_INTERVAL_TICKS = 10;

    private ThunderPower.Snapshot grid = ThunderPower.empty();
    private long lastGridScanTick = Long.MIN_VALUE;
    private long lastStunTick = Long.MIN_VALUE;

    /**
     * Kept so the discharge can still name a source after the tank dies.
     *
     * <p>{@link MonsterAreaEffectRequest} requires a non-null tower entity and {@code onDeath}
     * receives only the lane, so the reference has to be captured while the tower is alive.
     */
    private SemionTowerEntity spawnedEntity;

    public ThunderTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public ThunderTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    /** Current grid snapshot as of the last scan. */
    public ThunderPower.Snapshot grid() {
        return grid;
    }

    /** Whether this tower is plugged into the grid at all. */
    public boolean drawsPower() {
        return ThunderBalance.powerDraw(type().id()) > 0.0;
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        super.onPlaced(lane);
        refreshGrid(lane, true);
    }

    @Override
    protected void configureEntityAfterSpawn(SemionTowerEntity entity, PlayerLane lane) {
        super.configureEntityAfterSpawn(entity, lane);
        spawnedEntity = entity;
    }

    @Override
    public void tick(PlayerLane lane) {
        super.tick(lane);
        refreshGrid(lane, false);
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        super.onWaveStarted(lane, currentRound);
        // The storm roll for this wave is already in place; force a rescan so the first attack of
        // the wave uses the real output rather than the previous wave's number.
        refreshGrid(lane, true);
    }

    private void refreshGrid(PlayerLane lane, boolean force) {
        if (lane == null || lane.arenaWorld() == null) {
            return;
        }
        long now = lane.arenaWorld().getGameTime();
        if (!force && now - lastGridScanTick < GRID_SCAN_INTERVAL_TICKS) {
            return;
        }
        lastGridScanTick = now;
        grid = ThunderPower.snapshot(ownerPlayer(), lane);
    }

    // ------------------------------------------------------------------ power scaling

    @Override
    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        double damage = super.modifyAttackDamage(towerEntity, target, damageAmount);
        if (!drawsPower()) {
            return damage;
        }
        damage *= grid.damageMultiplier();

        if (ThunderTowers.isSurge(type())) {
            // The surge line converts leftover headroom into raw damage, which is what puts it in
            // direct competition with everything else that wants the same spare power.
            double peak = ThunderBalance.surgeMaxMultiplier(type().id());
            damage *= 1.0 + (peak - 1.0) * grid.headroom();
        }
        return damage;
    }

    @Override
    public int adjustAttackInterval(int baseIntervalTicks) {
        int interval = super.adjustAttackInterval(baseIntervalTicks);
        if (!drawsPower()) {
            return interval;
        }
        double speed = grid.attackSpeedMultiplier();
        if (speed >= 1.0) {
            return interval;
        }
        // A slower tower has a longer interval, so divide.
        return (int) Math.max(1, Math.round(interval / Math.max(0.1, speed)));
    }

    // ------------------------------------------------------------------ abilities

    @Override
    public void onAttackResolved(
            SemionTowerEntity towerEntity,
            SemionMonsterEntity target,
            double attemptedDamage,
            double resolvedOutgoingDamage,
            double dealtDamage,
            boolean killedTarget
    ) {
        super.onAttackResolved(towerEntity, target, attemptedDamage, resolvedOutgoingDamage, dealtDamage, killedTarget);
        if (towerEntity == null || target == null) {
            return;
        }

        if (ThunderBalance.chainTargets(type().id()) > 0) {
            fireChain(towerEntity, target, resolvedOutgoingDamage);
        }
        if (killedTarget || !target.isAlive()) {
            return;
        }

        if (ThunderTowers.isArmadillo(type())) {
            applyStun(towerEntity, target);
        }
        if (ThunderTowers.isGrounded(type())) {
            applyGroundingMark(towerEntity, target);
        }
    }

    /**
     * Electric shock as a full movement stop.
     *
     * <p>Implemented as a 100% move-speed reduction rather than a new mechanic, so it inherits the
     * existing timed-effect stacking and expiry. Stun is strong in a tower defense, so it is gated
     * behind its own cooldown instead of firing on every hit.
     */
    private void applyStun(SemionTowerEntity towerEntity, SemionMonsterEntity target) {
        var world = towerEntity.level();
        if (world == null) {
            return;
        }
        long now = world.getGameTime();
        if (now - lastStunTick < ThunderBalance.stunCooldownTicks()) {
            return;
        }
        ResourceLocation immunitySource = ResourceLocation.fromNamespaceAndPath(
                SemionTd.MOD_ID,
                "thunder_stun/" + ownerPlayer()
        );
        if (!target.applyTimedEffect(
                TimedEffectType.MONSTER_STUN_IMMUNITY,
                immunitySource,
                1.0,
                ThunderBalance.stunImmunityTicks()
        )) {
            return;
        }
        lastStunTick = now;
        target.applyTimedEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, 1.0, ThunderBalance.stunTicks());
        TowerVfxService.showAreaEffect(
                towerEntity,
                AreaEffectIds.tower(this, "stun"),
                AreaVfxStyles.DEBUFF,
                target.position(),
                0.8,
                List.of(target.position()),
                1,
                1,
                0
        );
    }

    /**
     * Grounding mark: the target takes more damage from every ally and deals less damage to
     * everything it attacks.
     *
     * <p>The second half is why this route can afford lower health than the insulated one. It does
     * not protect the tank that applied it — it protects the whole lane from that monster.
     */
    private void applyGroundingMark(SemionTowerEntity towerEntity, SemionMonsterEntity target) {
        String id = type().id();
        int duration = ThunderBalance.markDurationTicks();

        double bonus = ThunderBalance.markDamageBonus(id);
        double weaken = ThunderBalance.markAttackReduction(id);
        boolean newMark = target.activeTimedEffectMagnitude(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS) < bonus
                || target.activeTimedEffectMagnitude(TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION) < weaken;
        if (bonus > 0.0) {
            target.applyTimedEffect(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS, bonus, duration);
        }
        if (weaken > 0.0) {
            target.applyTimedEffect(TimedEffectType.MONSTER_ATTACK_DAMAGE_REDUCTION, weaken, duration);
        }
        if (newMark) {
            TowerVfxService.showAreaEffect(
                    towerEntity,
                    AreaEffectIds.tower(this, "grounding_mark"),
                    AreaVfxStyles.PULSE,
                    target.position(),
                    1.1,
                    List.of(target.position()),
                    1,
                    1,
                    0
            );
        }
    }

    /**
     * The insulated route conducts part of every hit into the ground.
     *
     * <p>Deliberately not a timed effect: it is an always-on property of the tower, and routing it
     * through {@code modifyIncomingDamage} matches how the ocean family applies its own reduction.
     */
    @Override
    public double modifyIncomingDamage(SemionTowerEntity towerEntity, DamageSource damageSource, double damageAmount) {
        double damage = super.modifyIncomingDamage(towerEntity, damageSource, damageAmount);
        double absorb = ThunderBalance.damageAbsorb(type().id());
        if (absorb <= 0.0) {
            return damage;
        }
        return damage * (1.0 - absorb);
    }

    /**
     * Arcs the hit to nearby monsters.
     *
     * <p>This replaced a straight-line pierce. The corridor filter it used was 1.2 blocks wide
     * along the tower-to-target axis, which almost never held a second monster on a real lane, so
     * the ability was invisible in play. Arcing off the struck target hits whatever is actually
     * bunched up around it.
     *
     * <p>The struck target is excluded and the arc count is capped by decrementing a counter inside
     * the damage function, so a dense wave cannot turn one attack into unlimited splash.
     */
    private void fireChain(SemionTowerEntity towerEntity, SemionMonsterEntity target, double outgoingDamage) {
        String id = type().id();
        int targets = ThunderBalance.chainTargets(id);
        double radius = ThunderBalance.chainRadius(id);
        double ratio = ThunderBalance.chainDamageRatio(id);
        if (targets <= 0 || radius <= 0.0 || ratio <= 0.0 || outgoingDamage <= 0.0) {
            return;
        }

        double chainDamage = outgoingDamage * ratio;
        AtomicInteger remaining = new AtomicInteger(targets);

        MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                AreaEffectIds.tower(this, "chain"),
                towerEntity,
                target.position(),
                radius,
                Set.of(target.getUUID()),
                null,
                AreaVfxSpec.onTrigger(ThunderVfx.ARC)
        );

        TowerAreaDamage.applyResolved(
                this,
                towerEntity,
                request,
                monster -> remaining.getAndDecrement() > 0 ? chainDamage : 0.0,
                true,
                (monster, damage, killed) -> {}
        );
    }

    /**
     * The earth armadillo dumps its stored charge when destroyed.
     *
     * <p>Deliberately on {@code onDeath} and not {@code onRemoved}, so selling or upgrading the
     * tank does not hand the player a free nuke.
     */
    @Override
    public void onDeath(PlayerLane lane) {
        super.onDeath(lane);
        String id = type().id();
        double damage = ThunderBalance.dischargeDamage(id);
        double radius = ThunderBalance.dischargeRadius(id);
        if (lane == null || damage <= 0.0 || radius <= 0.0) {
            return;
        }
        GridPosition position = position();
        SemionTowerEntity source = spawnedEntity;
        if (position == null || source == null) {
            return;
        }
        Vec3 center = new Vec3(position.x() + 0.5, position.y(), position.z() + 0.5);

        MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                AreaEffectIds.tower(this, "discharge"),
                source,
                center,
                radius,
                Set.of(),
                null,
                AreaVfxSpec.onTrigger(ThunderVfx.DISCHARGE)
        );
        // Kills are not propagated: the tank is already dead and must not keep earning on-kill
        // effects from beyond the grave.
        TowerAreaDamage.apply(this, source, request, monster -> damage, false);
    }

    // ------------------------------------------------------------------ dialog

    @Override
    public List<String> runtimeDetailLines() {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("발전 " + round(grid.generation()) + " / 소비 " + round(grid.consumption())
                + " (가동률 " + loadFactorText(grid.loadFactor()) + ")");
        lines.add(powerStateLine());
        if (ThunderBalance.isStormRod(type().id())) {
            addStormLines(lines);
        }
        return lines;
    }

    /**
     * What the current load factor is actually doing to this tower.
     *
     * <p>Both numbers are spelled out on the shortage side. Reporting only the damage penalty made
     * the grid look like a damage stat, when a shortage also costs attack speed — a tower reading
     * "27% 감소" was in fact losing 27% damage <em>and</em> 20% attack speed.
     */
    private String powerStateLine() {
        if (!drawsPower()) {
            return "전력 미사용 · 가동률의 영향을 받지 않습니다";
        }
        String damage = signedPercent(grid.damageMultiplier() - 1.0);
        if (grid.shortage()) {
            return "<red>전력 부족 · 공격력 " + damage
                    + " / 공격 속도 " + signedPercent(grid.attackSpeedMultiplier() - 1.0) + "</red>";
        }
        if (ThunderTowers.isSurge(type())) {
            double peak = ThunderBalance.surgeMaxMultiplier(type().id());
            return "전력 여유 · 공격력 " + damage
                    + " · 폭주 " + oneDecimal(1.0 + (peak - 1.0) * grid.headroom()) + "배";
        }
        return "전력 여유 · 공격력 " + damage;
    }

    /**
     * The storm rod's forecast.
     *
     * <p>Its description promises the storm is announced in advance, which was not true anywhere in
     * the UI before this: the roll happened silently at wave start and the player only saw the
     * result as a swing in everyone else's damage.
     */
    private void addStormLines(ArrayList<String> lines) {
        String id = type().id();
        double[] range = ThunderBalance.stormRange(id);
        lines.add("이번 웨이브 출력 " + round(ThunderPower.generationOf(ownerPlayer(), this))
                + " (" + round(range[0]) + "~" + round(range[1]) + ")");

        int remaining = ThunderStates.wavesUntilStorm(ownerPlayer());
        lines.add(remaining == 0
                ? "<light_purple>뇌우 · 이번 웨이브는 최대 출력</light_purple>"
                : "다음 뇌우까지 " + remaining + "웨이브");
    }

    /**
     * Load factor as a percentage.
     *
     * <p>{@link ThunderPower#snapshot} reports {@code Double.MAX_VALUE} when a grid supplies nothing
     * at all, which is reachable from configuration alone: {@code basePower} is only clamped to be
     * non-negative, so a server that zeroes it and owns no rod would otherwise print a 19-digit
     * percentage into the dialog.
     */
    private static String loadFactorText(double loadFactor) {
        if (!Double.isFinite(loadFactor) || loadFactor > 100.0) {
            return "∞";
        }
        return percentInteger(loadFactor);
    }

    private static String signedPercent(double delta) {
        long percent = Math.round(delta * 100.0);
        return (percent > 0 ? "+" : "") + percent + "%";
    }

    private static String round(double value) {
        return String.valueOf(Math.round(value));
    }
}
