package kim.biryeong.semiontd.tower.adversary;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.config.WaveMonsterEntry;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterDataKey;
import kim.biryeong.semiontd.entity.monster.MonsterDimensions;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * A placed rival is a normal slot-consuming tower during preparation and a tagged
 * hostile monster during a wave.  The logical tower remains in the lane while its
 * visual entity is hidden, which preserves its slot, upgrade identity, and score
 * ledger across round resets.
 */
public final class AdversaryRivalTower extends EntityBackedTower implements RivalProgressSource {
    private static final TowerDataKey<UUID> RIVAL_ID = TowerDataKey.of(id("rival/id"), UUID.class);
    private static final TowerDataKey<Integer> CONTRIBUTED_SCORE = TowerDataKey.of(
            id("rival/contributed_score"),
            Integer.class
    );

    private static final MonsterDataKey<UUID> PROXY_OWNER = MonsterDataKey.of(
            id("rival_proxy/owner"),
            UUID.class
    );
    private static final MonsterDataKey<UUID> PROXY_RIVAL_ID = MonsterDataKey.of(
            id("rival_proxy/id"),
            UUID.class
    );
    private static final MonsterDataKey<RivalKind> PROXY_KIND = MonsterDataKey.of(
            id("rival_proxy/kind"),
            RivalKind.class
    );
    private static final MonsterDataKey<Boolean> PROXY_ENHANCED = MonsterDataKey.of(
            id("rival_proxy/enhanced"),
            Boolean.class
    );
    private static final MonsterDataKey<Boolean> PROXY_SCORE_CREDITED = MonsterDataKey.of(
            id("rival_proxy/score_credited"),
            Boolean.class
    );

    private final RivalKind kind;
    private boolean convertedForWave;

    public AdversaryRivalTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition position
    ) {
        this(type, ownerPlayer, teamId, laneId, position, position);
    }

    public AdversaryRivalTower(
            TowerType type,
            UUID ownerPlayer,
            TeamId teamId,
            int laneId,
            GridPosition originalPosition,
            GridPosition currentPosition
    ) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
        this.kind = AdversaryTowers.rivalKind(type)
                .orElseThrow(() -> new IllegalArgumentException("Not an adversary rival tower: " + type.id()));
        setData(RIVAL_ID, UUID.randomUUID());
        setData(CONTRIBUTED_SCORE, 0);
    }

    @Override
    public UUID rivalId() {
        return getDataOrDefault(RIVAL_ID, ownerPlayer());
    }

    @Override
    public RivalKind rivalKind() {
        return kind;
    }

    @Override
    public int contributedScore() {
        return Math.max(0, getDataOrDefault(CONTRIBUTED_SCORE, 0));
    }

    public boolean enhanced() {
        return AdversaryTowers.isEnhancedRival(type());
    }

    public boolean convertedForWave() {
        return convertedForWave;
    }

    @Override
    public long sellRefundAmount() {
        return 0L;
    }

    @Override
    public List<String> runtimeDetailLines() {
        return List.of(
                "<red>" + kind.displayName() + " 숙적</red>" + (enhanced() ? " <gold>(강화)</gold>" : ""),
                "<yellow>처치 점수</yellow> " + kind.scorePerKill(enhanced())
                        + "점 / 누적 기여 " + contributedScore() + "점",
                "<red>판매하면 이 숙적이 쌓은 점수가 사라지며 환불은 없습니다.</red>"
        );
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        super.onPlaced(lane);
        AdversaryProgressStates.reconcileLane(ownerPlayer(), lane);
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        removeLeftoverProxy(lane);
        convertedForWave = false;
        super.onRemoved(lane);
        AdversaryProgressStates.reconcileLane(ownerPlayer(), lane);
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        if (lane == null || lane.arenaWorld() == null || convertedForWave || health() <= 0.0) {
            return;
        }

        Monster proxy = createProxy(currentRound);
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, lane.arenaWorld());
        entity.configureFrom(proxy, lane.laneLayout());
        Vec3 spawn = new Vec3(position().x() + 0.5, position().y() + 1.0, position().z() + 0.5);
        entity.setPos(spawn.x, spawn.y, spawn.z);

        // Keep the tower visible if spawning fails; transformation is transactional.
        if (!lane.arenaWorld().addFreshEntity(entity)) {
            return;
        }

        proxy.markMinecraftEntitySpawned(entity.getId(), spawn.x, spawn.y, spawn.z);
        lane.activeMonsters().add(proxy);
        convertedForWave = true;
        // Intentionally call the superclass implementation: a wave conversion is not
        // a sale and therefore must not reconcile the tower out of the score ledger.
        super.onRemoved(lane);
        // A converted rival must count as absent from the defense without broadcasting
        // a fake tower-death event.  Consuming the per-round notification flag here makes
        // PlayerLane's destroyed aggregation work while suppressing nearby-death fanout.
        notifyDeath(lane);
    }

    @Override
    public void onDeath(PlayerLane lane) {
        // Wave conversion is not a real tower death.  See onWaveStarted.
    }

    /** Records one kill after the fox-owned kill boundary verifies this exact proxy. */
    boolean creditFoxKill(PlayerLane lane, Monster monster) {
        if (!isProxyOf(monster, rivalId())
                || !isOwnedRival(monster, ownerPlayer())
                || kindOf(monster).filter(kind::equals).isEmpty()
                || isEnhancedProxy(monster) != enhanced()
                || monster.getData(PROXY_SCORE_CREDITED).orElse(false)) {
            return false;
        }
        int next = Math.addExact(contributedScore(), kind.scorePerKill(enhanced()));
        monster.setData(PROXY_SCORE_CREDITED, true);
        setData(CONTRIBUTED_SCORE, next);
        AdversaryProgressStates.noteScoringKind(ownerPlayer(), kind);
        if (lane != null) {
            AdversaryProgressStates.reconcileLane(ownerPlayer(), lane);
        }
        return true;
    }

    @Override
    public boolean isDestroyed(PlayerLane lane) {
        return convertedForWave || super.isDestroyed(lane);
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        removeLeftoverProxy(lane);
        convertedForWave = false;
        super.resetForRound(lane);
        AdversaryProgressStates.reconcileLane(ownerPlayer(), lane);
    }

    @Override
    public void moveToFinalDefense(PlayerLane lane, GridPosition position) {
        // A transformed rival has no tower entity to move.  The core allocator still
        // reserves its slot, matching the documented v1 final-defense limitation.
        if (!convertedForWave) {
            super.moveToFinalDefense(lane, position);
        }
    }

    /** Returns whether the monster is a special rival owned by the supplied player. */
    public static boolean isOwnedRival(Monster monster, UUID ownerPlayer) {
        return monster != null
                && ownerPlayer != null
                && monster.getData(PROXY_OWNER).filter(ownerPlayer::equals).isPresent()
                && monster.getData(PROXY_RIVAL_ID).isPresent()
                && monster.getData(PROXY_KIND).isPresent();
    }

    /** Returns the special rival kind, excluding ordinary wave mobs with the same skin. */
    public static Optional<RivalKind> kindOf(Monster monster) {
        return monster == null ? Optional.empty() : monster.getData(PROXY_KIND);
    }

    public static Optional<UUID> logicalRivalIdOf(Monster monster) {
        return monster == null ? Optional.empty() : monster.getData(PROXY_RIVAL_ID);
    }

    public static boolean isEnhancedProxy(Monster monster) {
        return monster != null && monster.getData(PROXY_ENHANCED).orElse(false);
    }

    Monster createProxy(int currentRound) {
        boolean enhanced = enhanced();
        WaveMonsterEntry entry = new WaveMonsterEntry(
                SemionTd.MOD_ID + ":adversary_rival_" + kind.name().toLowerCase(java.util.Locale.ROOT),
                kind.maxHealth(currentRound, enhanced),
                kind.armor(currentRound, enhanced),
                kind.damage(currentRound, enhanced),
                kind.attackKind(),
                kind.entityTypeId(),
                null,
                MonsterDimensions.DEFAULT,
                0L,
                1,
                100.0,
                WaveMonsterEntry.DEFAULT_MOVEMENT_SPEED_MULTIPLIER,
                kind.range(enhanced),
                kind.attackIntervalTicks(enhanced)
        );
        Monster proxy = Monster.fromWaveEntry(entry, teamId(), laneId());
        proxy.setData(PROXY_OWNER, ownerPlayer());
        proxy.setData(PROXY_RIVAL_ID, rivalId());
        proxy.setData(PROXY_KIND, kind);
        proxy.setData(PROXY_ENHANCED, enhanced);
        proxy.setData(PROXY_SCORE_CREDITED, false);
        return proxy;
    }

    private void removeLeftoverProxy(PlayerLane lane) {
        if (lane == null) {
            return;
        }
        Iterator<Monster> iterator = lane.activeMonsters().iterator();
        while (iterator.hasNext()) {
            Monster monster = iterator.next();
            if (!isProxyOf(monster, rivalId())) {
                continue;
            }
            if (lane.arenaWorld() != null && monster.hasMinecraftEntity()) {
                var entity = lane.arenaWorld().getEntity(monster.minecraftEntityId());
                if (entity != null) {
                    entity.discard();
                }
            }
            monster.markRemoved();
            iterator.remove();
        }
    }

    private static boolean isProxyOf(Monster monster, UUID rivalId) {
        return monster != null
                && rivalId != null
                && monster.getData(PROXY_RIVAL_ID).filter(rivalId::equals).isPresent();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, "adversary/" + path);
    }
}
