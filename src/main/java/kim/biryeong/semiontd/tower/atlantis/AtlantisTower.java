package kim.biryeong.semiontd.tower.atlantis;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Runtime tower for the Atlantis family.
 *
 * <p>Keeps the shared production basic-attack behaviour and layers three responsibilities on top:
 * turtles keep the player's pressure zones in sync and periodically refresh them, dolphins apply
 * pressure stacks and detonate them, and every tower participates in the shared zone bookkeeping.
 */
public class AtlantisTower extends ProductionTower {
    private long lastZoneScanTick = Long.MIN_VALUE;
    private long lastZoneVfxTick = Long.MIN_VALUE;
    private long lastPressureTick = Long.MIN_VALUE;
    private PlayerLane currentLane;
    private long nextTsunamiTick = Long.MAX_VALUE;

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        super.onWaveStarted(lane, currentRound);
        currentLane = lane;
        nextTsunamiTick = lane.arenaWorld().getGameTime() + (long) parameter("s", "intervalTicks", 120);
    }

    public AtlantisTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
    }

    public AtlantisTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    public AtlantisRole role() {
        return AtlantisTowers.roleOf(type());
    }

    public int tier() {
        return AtlantisTowers.tier(type());
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        super.onPlaced(lane);
        currentLane = lane;
        AtlantisStates.rebuild(ownerPlayer(), lane);
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        super.onRemoved(lane);
        AtlantisStates.rebuild(ownerPlayer(), lane);
    }

    @Override
    public void onSold(PlayerLane lane) {
        super.onSold(lane);
        AtlantisStates.rebuild(ownerPlayer(), lane);
    }

    /**
     * Final defense relocates every tower to slots near the boss, so a turtle's position stops
     * predicting where monsters will be. Redeploy so the same number of zones covers the approach
     * to the final defense line instead of the ground ahead of the moved tower.
     */
    @Override
    public void moveToFinalDefense(PlayerLane lane, GridPosition position) {
        super.moveToFinalDefense(lane, position);
        currentLane = lane;
        AtlantisStates.rebuild(ownerPlayer(), lane);
    }

    /**
     * The round reset clears {@code deployedAtFinalDefense} and returns towers to their original
     * positions, so the zones have to come back to the normal forward layout with them.
     */
    @Override
    public void resetForRound(PlayerLane lane) {
        super.resetForRound(lane);
        currentLane = lane;
        AtlantisStates.rebuild(ownerPlayer(), lane);
        nextTsunamiTick = Long.MAX_VALUE;
    }

    @Override
    public void tick(PlayerLane lane) {
        super.tick(lane);
        currentLane = lane;
        if (lane == null || lane.arenaWorld() == null) {
            return;
        }
        long now = lane.arenaWorld().getGameTime();
        if (role() == AtlantisRole.DOLPHIN) {
            releaseLapsedPressure(lane, now);
            return;
        }
        if (role() != AtlantisRole.TURTLE) {
            return;
        }
        if (has("g2")) releaseLapsedPressure(lane, now);
        if (has("s") && AugmentCombat.allowsTriggers() && !isDestroyed(lane) && now >= nextTsunamiTick) {
            nextTsunamiTick = now + (long) parameter("s", "intervalTicks", 120);
            pullZoneEdge(lane);
        }
        int interval = AtlantisBalance.zoneScanIntervalTicks();
        if (lastZoneScanTick != Long.MIN_VALUE && now - lastZoneScanTick < interval) {
            return;
        }
        lastZoneScanTick = now;
        refreshOwnedZones(lane);
    }

    /**
     * Re-applies the zone effect to everything standing in the zones this turtle deployed.
     *
     * <p>The shared area-effect API is re-triggered on an interval instead of holding a persistent
     * field, matching how the rest of the mod expresses continuous effects. The timed effect is
     * applied unsourced, so overlapping zones do not stack: the strongest magnitude wins.
     */
    private void refreshOwnedZones(PlayerLane lane) {
        SemionTowerEntity towerEntity = towerEntity(lane);
        if (towerEntity == null) {
            return;
        }
        List<PressureZone> owned = AtlantisStates.zones(ownerPlayer()).stream()
                .filter(zone -> zone.ownerPosition().equals(originalPosition()))
                .toList();
        if (owned.isEmpty()) {
            return;
        }
        long now = lane.arenaWorld().getGameTime();
        boolean showZoneVfx = lastZoneVfxTick == Long.MIN_VALUE
                || now - lastZoneVfxTick >= AtlantisBalance.zoneVfxIntervalTicks();
        if (showZoneVfx) {
            lastZoneVfxTick = now;
        }
        AreaVfxSpec zoneVfx = showZoneVfx
                ? AreaVfxSpec.onTrigger(AtlantisVfx.PRESSURE_ZONE)
                : AreaVfxSpec.none();
        int duration = AtlantisBalance.stackDurationTicks();
        for (int index = 0; index < owned.size(); index++) {
            PressureZone zone = owned.get(index);
            // Each zone carries its own source id, so effects are tracked per zone instead of
            // collapsing to a single strongest value: a zone that disappears takes exactly its own
            // contribution with it, and a weaker zone can still refresh what it owns.
            ResourceLocation zoneId = AreaEffectIds.tower(this, "pressure_zone/" + index);
            MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                    zoneId,
                    towerEntity,
                    zone.center(),
                    zone.radius(),
                    Set.of(),
                    null,
                    zoneVfx
            );
            SemionTdApi.areaEffects().applyToMonsters(request, target -> {
                UUID id = target.getUUID();
                AtlantisPressure.markZoneState(ownerPlayer(), id, true);
                double slow = AtlantisPressure.slowMagnitude(AtlantisPressure.stacks(ownerPlayer(), id));
                if (slow <= 0.0) {
                    return AreaEffectOutcome.UNCHANGED;
                }
                // Sourced magnitudes sum, so overlapping zones genuinely stack. Movement is
                // consumed as (1 - reduction) though, so an unbounded sum would pin the wave in
                // place: cap each zone's share so the total lands on maxSlow and no further.
                int overlap = AtlantisStates.overlapCount(ownerPlayer(), target.position());
                double share = Math.min(slow, AtlantisBalance.maxSlow() / overlap);
                target.applyTimedEffect(
                        TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, zoneId, share, duration);
                return AreaEffectOutcome.APPLIED;
            });
            protectAlliesInside(lane, zone, zoneId, duration);
        }
    }

    /**
     * Grants the zone's damage reduction to friendly towers standing inside it.
     *
     * <p>Sourced per zone like the monster-side slow, and split the same way so stacked zones do
     * not push a tower towards immunity. This is the ally half of the zone that the turtle tooltip
     * advertises.
     */
    private void protectAlliesInside(PlayerLane lane, PressureZone zone, ResourceLocation zoneId, int duration) {
        double reduction = zone.allyDamageReduction();
        if (reduction <= 0.0) {
            return;
        }
        for (Tower ally : lane.towers()) {
            if (!ownerPlayer().equals(ally.ownerPlayer())) {
                continue;
            }
            SemionTowerEntity allyEntity = towerEntityOf(ally, lane);
            if (allyEntity == null || !zone.contains(allyEntity.position())) {
                continue;
            }
            int overlap = AtlantisStates.overlapCount(ownerPlayer(), allyEntity.position());
            double share = Math.min(reduction, AtlantisBalance.maxZoneAllyDamageReduction() / overlap);
            allyEntity.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_REDUCTION, zoneId, share, duration);
        }
    }

    /**
     * Axolotl support pulse. Runs on the shared cooldown-driven execute path rather than a private
     * timer so the ability interval stays configurable and observable like every other family.
     *
     * <p>Abilities accumulate with tier: T1 regenerates, T2 adds attack speed, T3 additionally feeds
     * the dolphin stack/waterPressure bonuses read by {@link #supportBonus(String)}.
     */
    @Override
    protected boolean execute(PlayerLane lane) {
        if (role() != AtlantisRole.AXOLOTL || lane == null) {
            return super.execute(lane);
        }
        double radius = abilityDouble("supportRadius", 0.0);
        if (radius <= 0.0) {
            return false;
        }
        double regen = abilityDouble("regenAmount", 0.0);
        double attackSpeed = abilityDouble("attackSpeedBonus", 0.0);
        if (regen <= 0.0 && attackSpeed <= 0.0) {
            return false;
        }
        int duration = AtlantisBalance.stackDurationTicks();
        boolean acted = false;
        for (var tower : lane.towers()) {
            if (tower == this || !ownerPlayer().equals(tower.ownerPlayer())) {
                continue;
            }
            if (!withinGridRadius(tower.position(), radius)) {
                continue;
            }
            SemionTowerEntity target = towerEntityOf(tower, lane);
            if (target == null) {
                continue;
            }
            if (regen > 0.0 && tower.health() < tower.currentMaxHealth() && healTarget(target, regen)) {
                target.playHealingAnimation();
                acted = true;
            }
            if (attackSpeed > 0.0) {
                target.applyTimedEffect(TimedEffectType.TOWER_ATTACK_SPEED_BONUS, attackSpeed, duration);
                acted = true;
            }
        }
        return acted;
    }

    @Override
    protected int cooldownTicksAfterExecute(PlayerLane lane) {
        if (role() == AtlantisRole.AXOLOTL) {
            return Math.max(1, TowerBalanceRuntime.abilityTicks(type().id(), "supportIntervalTicks", 40));
        }
        return super.cooldownTicksAfterExecute(lane);
    }

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
        if (role() != AtlantisRole.DOLPHIN || target == null) {
            return;
        }
        UUID monsterId = target.getUUID();
        if (dealtDamage > 0.0 && has("g1") && AugmentCombat.allowsTriggers()) {
            spreadBasicPressure(towerEntity, target);
        }
        if (killedTarget) {
            burst(towerEntity, monsterId, target.position(), pressureChain());
            return;
        }
        if (dealtDamage <= 0.0) {
            // Blocked or fully mitigated hits must not build pressure.
            return;
        }
        // A monster standing in a high pressure zone accumulates faster: this is what makes the
        // turtle's zones feed the dolphin's damage rather than only slowing the wave.
        boolean insideZone = AtlantisStates.strongestZoneAt(ownerPlayer(), target.position()) != null;
        int base = Math.max(1, abilityInt("stackPerHit", 1) + axolotlStackBonus());
        int amount = insideZone
                ? Math.max(base, (int) Math.round(base * AtlantisBalance.zoneStackMultiplier()))
                : base;
        int ceiling = AtlantisBalance.maxPressureStacks() + conduitStackBonus();
        int stacks = AtlantisPressure.addStacks(
                monsterId,
                ownerPlayer(),
                originalPosition(),
                amount,
                type().damage(),
                ceiling,
                AtlantisBalance.stackDurationTicks()
        );
        AtlantisPressure.markZoneState(ownerPlayer(), monsterId, insideZone);
        if (stacks >= ceiling) {
            // Reaching the ceiling releases immediately. Without this the duration refresh on every
            // hit means a dolphin attacking one target keeps pushing expiry out of reach, and the
            // pressure never converts into damage.
            burst(towerEntity, monsterId, target.position(), pressureChain());
        }
    }

    @Override
    public void onNearbyMonsterDeath(PlayerLane lane, Monster monster, Vec3 deathPosition) {
        if ((role() != AtlantisRole.DOLPHIN && !(role() == AtlantisRole.TURTLE && has("g2"))) || lane == null || monster == null
                || deathPosition == null || !monster.hasMinecraftEntity()) {
            return;
        }
        if (lane.arenaWorld().getEntity(monster.minecraftEntityId()) instanceof SemionMonsterEntity entity) {
            burst(towerEntity(lane), entity.getUUID(), deathPosition, pressureChain());
        }
    }

    /**
     * Releases pressure that has lapsed: expired by duration, or carried out of every zone.
     *
     * <p>Runs on the owning dolphin so the burst is attributed to the tower whose damage sized it.
     * Monsters that vanished without dying (despawn, reaching the goal) drop their entry here
     * rather than accumulating in the map.
     */
    private void releaseLapsedPressure(PlayerLane lane, long now) {
        int elapsed = lastPressureTick == Long.MIN_VALUE ? 1 : (int) (now - lastPressureTick);
        if (elapsed <= 0) {
            return;
        }
        lastPressureTick = now;
        List<UUID> carried = AtlantisPressure.monstersFrom(ownerPlayer(), originalPosition());
        if (carried.isEmpty()) {
            return;
        }
        SemionTowerEntity towerEntity = towerEntity(lane);
        if (towerEntity == null) {
            return;
        }
        for (UUID monsterId : carried) {
            boolean expired = AtlantisPressure.tickExpired(ownerPlayer(), monsterId, elapsed);
            if (!(lane.arenaWorld().getEntity(monsterId) instanceof SemionMonsterEntity monster)
                    || monster.isRemoved()) {
                AtlantisPressure.remove(ownerPlayer(), monsterId);
                continue;
            }
            if (!monster.isAlive()) {
                // PlayerLane sends the authoritative death notification later in this tick.
                continue;
            }
            boolean insideZone = AtlantisStates.strongestZoneAt(ownerPlayer(), monster.position()) != null;
            boolean leftZone = AtlantisPressure.insideZone(ownerPlayer(), monsterId) && !insideZone;
            AtlantisPressure.markZoneState(ownerPlayer(), monsterId, insideZone);
            if (expired || leftZone) {
                burst(towerEntity, monsterId, monster.position(), pressureChain());
            }
        }
    }

    /**
     * Releases a monster's stored pressure as an area burst. Damage is routed through
     * {@link TowerAreaDamage} so attribution, statistics and kill hooks behave exactly like a
     * direct tower attack, and kills inside the burst continue the chain.
     */
    private void burst(SemionTowerEntity towerEntity, UUID originId, Vec3 center, AtlantisPressure.Chain chain) {
        burst(towerEntity, originId, center, chain, false);
    }

    private void burst(SemionTowerEntity towerEntity, UUID originId, Vec3 center, AtlantisPressure.Chain chain,
                       boolean generatedByAugment) {
        if (originId == null || center == null) {
            return;
        }
        GridPosition sourcePosition = AtlantisPressure.sourceTower(ownerPlayer(), originId);
        if (sourcePosition == null) {
            return;
        }
        if (!sourcePosition.equals(originalPosition())) {
            AtlantisTower source = sourcePressureTower(sourcePosition);
            if (source == null) {
                AtlantisPressure.remove(ownerPlayer(), originId);
            } else {
                source.burst(source.towerEntity(source.currentLane), originId, center, chain, generatedByAugment);
            }
            return;
        }
        if (towerEntity == null || !chain.canBurst(originId)) {
            return;
        }
        int previousStacks = AtlantisPressure.stacks(ownerPlayer(), originId);
        double cap = has("g1") ? parameter("g1", "damageCap", 4) : AtlantisBalance.waterPressureDamageCap();
        double damage = AtlantisPressure.consumeForBurst(ownerPlayer(), originId, waterPressureRatioBonus(), cap);
        if (damage <= 0.0) {
            return;
        }
        chain.enter();
        try {
            if (has("p") && (AugmentCombat.allowsTriggers() || generatedByAugment)) {
                transferPressure(towerEntity, originId, center, previousStacks, chain);
            }
            MonsterAreaEffectRequest request = new MonsterAreaEffectRequest(
                    AreaEffectIds.tower(this, "water_pressure"),
                    towerEntity,
                    center,
                    AtlantisBalance.waterPressureRadius(),
                    Set.of(),
                    null,
                    AreaVfxSpec.onTrigger(AtlantisVfx.WATER_PRESSURE)
            );
            Runnable damageAction = () -> TowerAreaDamage.apply(
                    this,
                    towerEntity,
                    request,
                    ignored -> damage,
                    true,
                    (victim, dealt, killed) -> {
                        if (killed) {
                            burst(towerEntity, victim.getUUID(), victim.position(), chain, generatedByAugment);
                        }
                    },
                    DamageType.MAGIC
            );
            if (generatedByAugment) AugmentCombat.runWithoutTriggers(damageAction);
            else damageAction.run();
        } finally {
            chain.exit();
        }
    }

    private AtlantisPressure.Chain pressureChain() {
        return AtlantisPressure.Chain.currentOrNew(ownerPlayer(), has("p") ? (int) parameter("p", "chainTargets", 12) : 0);
    }

    private void spreadBasicPressure(SemionTowerEntity source, SemionMonsterEntity primary) {
        double radius = parameter("g1", "radius", 3);
        int count = (int) parameter("g1", "targets", 2);
        for (SemionMonsterEntity target : nearby(source, primary.position(), radius, Set.of(primary.getUUID()), count)) {
            addPressure(target, (int) parameter("g1", "stacks", 1), pressureChain(), false);
        }
    }

    private void transferPressure(SemionTowerEntity source, UUID origin, Vec3 center, int stacks,
                                  AtlantisPressure.Chain chain) {
        int transferred = (int) Math.floor(stacks * parameter("p", "transferRatio", 0.5));
        if (transferred <= 0) return;
        for (SemionMonsterEntity target : nearby(source, center, parameter("p", "radius", 3),
                chain.burstIds(), (int) parameter("p", "targets", 2))) {
            if (!chain.alreadyBurst(target.getUUID())) addPressure(target, transferred, chain, true);
        }
    }

    private void addPressure(SemionMonsterEntity target, int amount, AtlantisPressure.Chain chain, boolean requireZone) {
        int ceiling = AtlantisBalance.maxPressureStacks() + conduitStackBonus();
        int stacks = AtlantisPressure.addStacks(target.getUUID(), ownerPlayer(), originalPosition(), amount,
                type().damage(), ceiling, AtlantisBalance.stackDurationTicks());
        boolean inside = AtlantisStates.strongestZoneAt(ownerPlayer(), target.position()) != null;
        AtlantisPressure.markZoneState(ownerPlayer(), target.getUUID(), inside);
        if (stacks >= ceiling && (!requireZone || inside)) {
            burst(towerEntity(currentLane), target.getUUID(), target.position(), chain, true);
        }
    }

    private List<SemionMonsterEntity> nearby(SemionTowerEntity source, Vec3 center, double radius,
                                            Set<UUID> excluded, int count) {
        List<SemionMonsterEntity> candidates = new ArrayList<>();
        var request = new MonsterAreaEffectRequest(AreaEffectIds.tower(this, "augment_pressure"), source,
                center, radius, excluded, null, AreaVfxSpec.none());
        SemionTdApi.areaEffects().applyToMonsters(request, target -> {
            candidates.add(target);
            return AreaEffectOutcome.UNCHANGED;
        });
        return candidates.stream().sorted(Comparator.comparingDouble(target -> target.position().distanceToSqr(center)))
                .limit(Math.max(0, count)).toList();
    }

    private void pullZoneEdge(PlayerLane lane) {
        SemionTowerEntity source = towerEntity(lane);
        if (source == null) return;
        SemionMonsterEntity edge = null;
        PressureZone chosen = null;
        double furthest = -1;
        for (PressureZone zone : ownedZones()) {
            for (SemionMonsterEntity candidate : nearby(source, zone.center(), zone.radius(), Set.of(), Integer.MAX_VALUE)) {
                double distance = candidate.position().distanceToSqr(zone.center());
                if (distance > furthest) {furthest = distance; edge = candidate; chosen = zone;}
            }
        }
        if (edge == null || chosen == null) return;
        Vec3 delta = chosen.center().subtract(edge.position());
        Vec3 destination = edge.position().add(delta.normalize().scale(Math.min(delta.length(), parameter("s", "distance", 2))));
        edge.getNavigation().stop();
        if (edge.runtimeMonster() != null) {
            PlayerLane pathLane = edge.runtimeMonster().inFinalDefenseCombat() ? lane.finalDefensePathLane() : lane;
            edge.runtimeMonster().syncLaneProgress(pathLane.laneLayout().progressAt(destination));
        }
        edge.teleportTo(destination.x, destination.y, destination.z);
    }

    @Override
    public void onDeath(PlayerLane lane) {
        currentLane = lane;
        if (role() == AtlantisRole.TURTLE && has("g2") && AugmentCombat.allowsTriggers()) {
            SemionTowerEntity source = towerEntity(lane);
            Set<UUID> affected = new HashSet<>();
            AtlantisPressure.Chain chain = pressureChain();
            int limit = (int) parameter("g2", "targets", 12);
            for (PressureZone zone : ownedZones()) {
                for (SemionMonsterEntity target : nearby(source, zone.center(), zone.radius(), affected, limit - affected.size())) {
                    affected.add(target.getUUID());
                    AtlantisTower dolphin = lane.towers().stream().filter(AtlantisTower.class::isInstance)
                            .map(AtlantisTower.class::cast).filter(tower -> tower.role() == AtlantisRole.DOLPHIN
                                    && ownerPlayer().equals(tower.ownerPlayer()) && !tower.isDestroyed(lane))
                            .max(Comparator.comparingDouble(tower -> tower.type().damage())).orElse(this);
                    int ceiling = AtlantisBalance.maxPressureStacks() + dolphin.conduitStackBonus();
                    dolphin.addPressure(target, ceiling, chain, false);
                }
            }
        }
        super.onDeath(lane);
        AtlantisStates.rebuildAfterDeath(ownerPlayer(), lane, this);
    }

    private List<PressureZone> ownedZones() {
        return AtlantisStates.zones(ownerPlayer()).stream()
                .filter(zone -> zone.ownerPosition().equals(originalPosition())).toList();
    }

    private boolean has(String suffix) {return augmentSnapshot().has("job_atlantis_towers_" + suffix);}
    private double parameter(String suffix, String key, double fallback) {
        return augmentSnapshot().parameter("job_atlantis_towers_" + suffix, key, fallback);
    }

    private AtlantisTower sourcePressureTower(GridPosition sourcePosition) {
        if (currentLane == null) {
            return null;
        }
        for (Tower tower : currentLane.towers()) {
            if (tower instanceof AtlantisTower atlantis
                    && (atlantis.role() == AtlantisRole.DOLPHIN || atlantis.role() == AtlantisRole.TURTLE)
                    && ownerPlayer().equals(atlantis.ownerPlayer())
                    && sourcePosition.equals(atlantis.originalPosition())) {
                return atlantis;
            }
        }
        return null;
    }

    private double waterPressureRatioBonus() {
        return Math.max(0.0, abilityDouble("waterPressureRatioBonus", 0.0))
                + supportBonus("waterPressureRatioBonus");
    }

    private int conduitStackBonus() {
        return (int) Math.round(supportBonus("maxStackBonus"));
    }

    private int axolotlStackBonus() {
        return (int) Math.round(supportBonus("stackBonus"));
    }

    /**
     * Sums a support ability contributed by nearby conduit and axolotl towers owned by the same
     * player. Support towers publish their radius through {@code amplifyRadius}/{@code supportRadius}.
     */
    private double supportBonus(String key) {
        PlayerLane lane = currentLane;
        if (lane == null) {
            return 0.0;
        }
        double total = 0.0;
        for (var tower : lane.towers()) {
            if (!(tower instanceof AtlantisTower support) || support == this) {
                continue;
            }
            if (!ownerPlayer().equals(support.ownerPlayer())) {
                continue;
            }
            AtlantisRole supportRole = support.role();
            if (supportRole != AtlantisRole.CONDUIT && supportRole != AtlantisRole.AXOLOTL) {
                continue;
            }
            double value = TowerBalanceRuntime.ability(support.type().id(), key, 0.0);
            if (value <= 0.0) {
                continue;
            }
            double radius = supportRole == AtlantisRole.CONDUIT
                    ? TowerBalanceRuntime.ability(support.type().id(), "amplifyRadius", 0.0)
                    : TowerBalanceRuntime.ability(support.type().id(), "supportRadius", 0.0);
            if (radius > 0.0 && withinGridRadius(support.position(), radius)) {
                total += value;
            }
        }
        return total;
    }

    private boolean withinGridRadius(GridPosition other, double radius) {
        GridPosition self = position();
        if (self == null || other == null) {
            return false;
        }
        double dx = self.x() - other.x();
        double dz = self.z() - other.z();
        return dx * dx + dz * dz <= radius * radius;
    }

    private static SemionTowerEntity towerEntityOf(Tower tower, PlayerLane lane) {
        if (!(tower instanceof EntityBackedTower backed) || lane == null || lane.arenaWorld() == null) {
            return null;
        }
        if (backed.entityId().isEmpty()) {
            return null;
        }
        return lane.arenaWorld().getEntity(backed.entityId().getAsInt()) instanceof SemionTowerEntity towerEntity
                ? towerEntity
                : null;
    }

    private SemionTowerEntity towerEntity(PlayerLane lane) {
        PlayerLane resolved = lane == null ? currentLane : lane;
        if (resolved == null || resolved.arenaWorld() == null || entityId().isEmpty()) {
            return null;
        }
        return resolved.arenaWorld().getEntity(entityId().getAsInt()) instanceof SemionTowerEntity towerEntity
                ? towerEntity
                : null;
    }

    private double abilityDouble(String key, double fallback) {
        return TowerBalanceRuntime.ability(type().id(), key, fallback);
    }

    private int abilityInt(String key, int fallback) {
        return TowerBalanceRuntime.abilityInt(type().id(), key, fallback);
    }
}
