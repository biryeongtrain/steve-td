package kim.biryeong.semiontd.tower.army;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Runtime tower for the 군대 family.
 *
 * <p>Service is accumulated rather than derived from {@code currentRound - placedRound}, because the
 * 조교 and 초소장 change how fast it accrues. It is carried across upgrades in
 * {@link #copyRuntimeStateFrom(Tower)} so buying a higher tier never resets a tower's rank — tier is
 * bought with minerals, rank is earned with time, and the two must not leak into each other.
 */
public class ArmyTower extends ProductionTower {
    /** Single source key so the summed command bonus stays capped in one place. */
    private static final ResourceLocation COMMAND_SOURCE =
            ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "army_command");

    private int service;
    private boolean dischargePending;
    private ArmyRank appliedRank;
    private long lastCommandTick = Long.MIN_VALUE;

    /**
     * Kept so {@link #sellRefundAmount()} can still see the lane.
     *
     * <p>That override receives no arguments, and the discharge payout depends on which 보급관 are
     * currently standing on the lane, so the reference has to be captured while ticking.
     */
    private PlayerLane currentLane;

    public ArmyTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public ArmyTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    public int service() {
        return service;
    }

    public ArmyRank rank() {
        return ArmyRank.of(service);
    }

    /** Whether this tower's damage is scaled by rank. Only the 전투 line is. */
    public boolean ranks() {
        return ArmyTowers.ranks(type());
    }

    /** Whether this tower is close enough to discharge that the player must be warned. */
    public boolean dischargeImminent() {
        return ranks() && ArmyRank.wavesUntilDischarge(service) <= ArmyRank.DISCHARGE_NOTICE_WAVES;
    }

    @Override
    protected void copyRuntimeStateFrom(Tower previousTower) {
        super.copyRuntimeStateFrom(previousTower);
        if (previousTower instanceof ArmyTower army) {
            service = army.service;
            appliedRank = army.appliedRank;
        }
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        super.onPlaced(lane);
        currentLane = lane;
    }

    @Override
    protected void configureEntityAfterSpawn(SemionTowerEntity entity, PlayerLane lane) {
        super.configureEntityAfterSpawn(entity, lane);
        currentLane = lane;
        appliedRank = null;
        applyAppearance(entity);
    }

    private Optional<SemionTowerEntity> entity(PlayerLane lane) {
        if (lane == null || lane.arenaWorld() == null || entityId().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(lane.arenaWorld().getEntity(entityId().getAsInt()))
                .filter(SemionTowerEntity.class::isInstance)
                .map(SemionTowerEntity.class::cast);
    }

    // ------------------------------------------------------------------ service

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        super.onWaveStarted(lane, currentRound);
        currentLane = lane;
        if (!ranks()) {
            return;
        }
        service = Math.max(0, service + serviceGainThisWave(lane));
        if (service >= ArmyRank.DISCHARGE_SERVICE) {
            dischargePending = true;
        }
        entity(lane).ifPresent(this::applyAppearance);
    }

    /**
     * How much service this tower accrues this wave.
     *
     * <p>One by default, shifted by every support tower whose radius covers this one. 조교 pushes it
     * up, 초소장 pushes it down, and a lane holding both cancels out — which is intended, since the
     * two are the same dial turned in opposite directions.
     *
     * <p>Floored at zero rather than one: a player who commits hard to 초소장 should be able to hold
     * a roster in place, and the medal economy already punishes never rotating.
     */
    private int serviceGainThisWave(PlayerLane lane) {
        double gain = 1.0;
        if (lane == null) {
            return (int) Math.round(gain);
        }
        for (Tower tower : lane.towers()) {
            if (tower == this || !ownerPlayer().equals(tower.ownerPlayer())) {
                continue;
            }
            double bonus = ArmyBalance.serviceRateBonus(tower.type().id());
            if (bonus == 0.0) {
                continue;
            }
            double radius = ArmyBalance.serviceRateRadius(tower.type().id());
            if (radius > 0.0 && withinRadius(tower, radius)) {
                gain += bonus;
            }
        }
        return (int) Math.round(Math.max(0.0, gain));
    }

    private boolean withinRadius(Tower other, double radius) {
        GridPosition here = position();
        GridPosition there = other.position();
        if (here == null || there == null) {
            return false;
        }
        double dx = here.x() - there.x();
        double dz = here.z() - there.z();
        return dx * dx + dz * dz <= radius * radius;
    }

    // ------------------------------------------------------------------ rank scaling

    @Override
    public double modifyAttackDamage(SemionTowerEntity towerEntity, SemionMonsterEntity target, double damageAmount) {
        double damage = super.modifyAttackDamage(towerEntity, target, damageAmount);
        if (!ranks()) {
            return damage;
        }
        damage *= rank().attackMultiplier();
        return damage * (1.0 + ArmyStates.medalBonus(ownerPlayer()));
    }

    /**
     * The guard route sheds part of every hit.
     *
     * <p>Routed through {@code modifyIncomingDamage} rather than a timed effect because it is an
     * always-on property, matching how the ocean and 람쥐썬더 families apply their own reductions.
     */
    @Override
    public double modifyIncomingDamage(SemionTowerEntity towerEntity, DamageSource damageSource, double damageAmount) {
        double damage = super.modifyIncomingDamage(towerEntity, damageSource, damageAmount);
        double reduction = ArmyBalance.damageReduction(type().id());
        return reduction <= 0.0 ? damage : damage * (1.0 - reduction);
    }

    // ------------------------------------------------------------------ command

    @Override
    public void tick(PlayerLane lane) {
        super.tick(lane);
        if (lane == null || lane.arenaWorld() == null) {
            return;
        }
        currentLane = lane;
        long now = lane.arenaWorld().getGameTime();
        if (now - lastCommandTick < ArmyBalance.COMMAND_SCAN_INTERVAL_TICKS) {
            return;
        }
        lastCommandTick = now;
        refreshCommand(lane);
    }

    /**
     * Pulls the command bonus this tower is receiving from nearby seniors.
     *
     * <p>Written as a pull rather than a push so the cap has exactly one place to live. Sourced
     * timed effects sum across sources, so letting every senior write its own entry would make
     * {@link ArmyBalance#MAX_COMMAND_BONUS} unenforceable without bookkeeping here anyway.
     *
     * <p>Only strictly higher ranks count. Two 병장 standing together buff nobody, which is what
     * forces a mixed roster instead of a wall of top-rank towers.
     */
    private void refreshCommand(PlayerLane lane) {
        if (!ranks()) {
            return;
        }
        ArmyRank myRank = rank();
        double radius = ArmyBalance.commandRadius();
        double damageBonus = 0.0;
        double speedBonus = 0.0;

        for (Tower tower : lane.towers()) {
            if (tower == this || !ownerPlayer().equals(tower.ownerPlayer())) {
                continue;
            }
            if (!(tower instanceof ArmyTower senior) || !senior.ranks()) {
                continue;
            }
            ArmyRank seniorRank = senior.rank();
            if (!seniorRank.isSuperiorTo(myRank) || !withinRadius(tower, radius)) {
                continue;
            }
            damageBonus += seniorRank.damageBuff();
            speedBonus += seniorRank.attackSpeedBuff();
        }

        double cap = ArmyBalance.maxCommandBonus();
        double damage = Math.min(damageBonus, cap);
        double speed = Math.min(speedBonus, cap);
        int duration = ArmyBalance.COMMAND_SCAN_INTERVAL_TICKS * 3;

        entity(lane).ifPresent(entity -> {
            entity.refreshTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS, COMMAND_SOURCE, damage, duration);
            entity.refreshTimedEffect(TimedEffectType.TOWER_ATTACK_SPEED_BONUS, COMMAND_SOURCE, speed, duration);
        });
    }

    // ------------------------------------------------------------------ artillery

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
        if (towerEntity == null || target == null || !ArmyTowers.isArtillery(type())) {
            return;
        }
        double ratio = ArmyBalance.splashDamageRatio(type().id());
        double radius = ArmyBalance.splashRadius(type().id());
        if (ratio <= 0.0 || radius <= 0.0 || resolvedOutgoingDamage <= 0.0) {
            return;
        }
        double splash = resolvedOutgoingDamage * ratio;
        MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                AreaEffectIds.tower(this, "barrage"),
                towerEntity,
                target.position(),
                radius,
                Set.of(target.getUUID()),
                null,
                AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH)
        );
        TowerAreaDamage.apply(this, towerEntity, request, monster -> splash, true);
    }

    // ------------------------------------------------------------------ discharge

    /**
     * Discharge pays more than a sale and leaves a medal behind.
     *
     * <p>Kept under the purchase price on purpose: a refund above cost would turn the rotation into
     * a mineral printer. The permanent medal is the real reward, and it is capped so a player cannot
     * farm the cycle forever.
     */
    @Override
    public long sellRefundAmount() {
        if (!ranks() || service < ArmyRank.DISCHARGE_SERVICE) {
            return super.sellRefundAmount();
        }
        double ratio = ArmyBalance.dischargeRefundRatio() * (1.0 + supportBonus(currentLane, true));
        return Math.max(super.sellRefundAmount(), Math.round(paidMineralCost() * ratio));
    }

    @Override
    public void onSold(PlayerLane lane) {
        super.onSold(lane);
        awardMedalIfDischarged(lane);
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        super.onRemoved(lane);
        awardMedalIfDischarged(lane);
    }

    private void awardMedalIfDischarged(PlayerLane lane) {
        if (!ranks() || service < ArmyRank.DISCHARGE_SERVICE) {
            return;
        }
        ArmyStates.awardMedal(ownerPlayer(), 1.0 + supportBonus(lane, false));
    }

    /** Aggregate of every 보급관 bonus currently on the lane. */
    private double supportBonus(PlayerLane lane, boolean refund) {
        if (lane == null) {
            return 0.0;
        }
        double total = 0.0;
        for (Tower tower : lane.towers()) {
            if (!ownerPlayer().equals(tower.ownerPlayer())) {
                continue;
            }
            total += refund
                    ? ArmyBalance.dischargeRefundBonus(tower.type().id())
                    : ArmyBalance.medalValueBonus(tower.type().id());
        }
        return total;
    }

    public boolean dischargePending() {
        return dischargePending;
    }

    // ------------------------------------------------------------------ appearance

    /**
     * Rank as a helmet.
     *
     * <p>{@code EntityVisualProperties} has no equipment lever, so the runtime tower reaches the
     * spawned entity directly — the same route the 붉은 여왕 card soldiers take for their suit item.
     * Leather to diamond is a progression Minecraft players already read without a legend.
     */
    private void applyAppearance(SemionTowerEntity entity) {
        if (entity == null || !ranks()) {
            return;
        }
        ArmyRank current = rank();
        if (current == appliedRank) {
            return;
        }
        appliedRank = current;
        entity.setItemSlot(EquipmentSlot.HEAD, new ItemStack(helmetFor(current)));
        entity.setCustomName(Component.literal(type().displayName() + " [" + current.displayName() + "]"));
        entity.setCustomNameVisible(true);
    }

    private static Item helmetFor(ArmyRank rank) {
        return switch (rank) {
            case PRIVATE -> Items.LEATHER_HELMET;
            case CORPORAL -> Items.CHAINMAIL_HELMET;
            case SERGEANT -> Items.IRON_HELMET;
            case STAFF_SERGEANT -> Items.DIAMOND_HELMET;
        };
    }

    // ------------------------------------------------------------------ dialog

    @Override
    public List<String> runtimeDetailLines() {
        ArrayList<String> lines = new ArrayList<>();
        if (!ranks()) {
            lines.add("계급 없음 · 짬의 영향을 받지 않습니다");
            addSupportLines(lines);
            return lines;
        }

        ArmyRank current = rank();
        lines.add("계급 " + current.displayName() + " (짬 " + service + ")");
        lines.add("공격력 " + percentInteger(current.attackMultiplier()) + " · 후임 버프 +"
                + percentInteger(current.damageBuff()));

        int untilPromotion = ArmyRank.wavesUntilPromotion(service);
        int untilDischarge = ArmyRank.wavesUntilDischarge(service);
        if (untilDischarge <= ArmyRank.DISCHARGE_NOTICE_WAVES) {
            lines.add("<red>전역까지 " + untilDischarge + "웨이브</red>");
        } else if (untilPromotion > 0) {
            lines.add("다음 진급까지 " + untilPromotion + "웨이브 · 전역까지 " + untilDischarge + "웨이브");
        } else {
            lines.add("전역까지 " + untilDischarge + "웨이브");
        }

        double medal = ArmyStates.medalBonus(ownerPlayer());
        if (medal > 0.0) {
            lines.add("훈장 " + ArmyStates.medalCount(ownerPlayer()) + "개 · 공격력 +" + percentInteger(medal));
        }
        return lines;
    }

    private void addSupportLines(ArrayList<String> lines) {
        double bonus = ArmyBalance.serviceRateBonus(type().id());
        if (bonus != 0.0) {
            String sign = bonus > 0 ? "+" : "";
            lines.add("주변 아군 짬 " + sign + Math.round(bonus) + "/웨이브");
        }
    }
}
