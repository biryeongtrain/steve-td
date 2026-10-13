package kim.biryeong.semiontd.tower.demonlord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerPlacementPositions;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.body.BodyTowers;
import kim.biryeong.semiontd.tower.end.EndTower;
import kim.biryeong.semiontd.tower.engineer.EngineerTowers;
import kim.biryeong.semiontd.tower.frost.FrostTowers;
import kim.biryeong.semiontd.tower.futureagency.FutureAgencyTowers;
import kim.biryeong.semiontd.tower.hero.HeroPartyTower;
import kim.biryeong.semiontd.tower.mage.MageTowers;
import kim.biryeong.semiontd.tower.succubus.SuccubusTowers;
import kim.biryeong.semiontd.tower.warlock.WarlockTowers;
import kim.biryeong.semiontd.vfx.DisplayShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

/** 8·9번 슬롯 패시브의 실제 효과. */
public final class DemonLordPassives {
    private DemonLordPassives() {
    }

    // ------------------------------------------------------------ 흡혈 참격

    /**
     * 마검 평타가 맞은 적 주변까지 베고, 이번 한 번에 준 피해의 일부만큼 회복합니다. 회복은 한 번에 최대 체력의
     * {@code lifeStealCap}까지로 묶어, 떼로 몰린 적을 한 번 베어 체력이 다 차지 않게 합니다.
     */
    static void bloodCleave(ServerPlayer player, PlayerLane lane, DemonLordState state, DemonLordSkillTower altar,
            SemionMonsterEntity target, double swingDamage, double dealtToTarget) {
        DemonLordPassive passive = DemonLordPassive.BLOOD_CLEAVE;
        double radius = passive.ability("cleaveRadius", 2.5) * state.skillRangeMultiplier();
        double cleave = swingDamage * passive.ability("cleaveRatio", 0.6);
        double dealt = Math.max(0.0, dealtToTarget);
        Vec3 centre = target.position();
        AABB box = new AABB(centre, centre).inflate(radius, 2.0, radius);
        for (SemionMonsterEntity other : lane.arenaWorld().getEntitiesOfClass(SemionMonsterEntity.class, box, entity ->
                entity != target && entity.isAlive() && entity.runtimeMonster() != null
                        && state.canFight(entity.runtimeMonster())
                        && entity.position().distanceToSqr(centre) <= radius * radius)) {
            dealt += DemonLordService.dealDamage(player, lane, altar, other, cleave, DamageType.PHYSICAL).dealtDamage();
        }
        double heal = Math.min(dealt * passive.ability("lifeStealRatio", 0.15),
                state.maxHealth() * passive.ability("lifeStealCap", 0.04));
        if (heal > 0.0) {
            state.heal(heal);
        }
        DemonLordVfx.play(lane, DemonLordDisplayVfx.bloodCleave(radius, DemonLordVfx.seed(lane)), centre);
    }

    // ------------------------------------------------------------ 검기

    /** 검기가 시전 후 움직이기 시작하는 틱. 연출의 첫 이동 키프레임과 맞춥니다. */
    static final int BLADE_WAVE_DELAY = 3;

    /**
     * 날아가는 검기 하나. 연출은 쏠 때 끝점까지 한 번에 짜 두고, 피해는 매 틱 연출과 같은 속도로 한 구간씩 전진하며
     * 그 구간에 걸친 적에게 줍니다. 한 적은 한 번만 맞습니다.
     */
    public static final class BladeWave {
        final Vec3 origin;
        final Vec3 direction;
        final double speed;
        final double length;
        final double damage;
        final double hitRadius;
        final long startTick;
        final DemonLordSkillTower altar;
        final Set<Integer> hit = new HashSet<>();
        double travelled;
        boolean firstHit;

        BladeWave(Vec3 origin, Vec3 direction, double speed, double length, double damage, double hitRadius,
                long startTick, DemonLordSkillTower altar) {
            this.origin = origin;
            this.direction = direction;
            this.speed = speed;
            this.length = length;
            this.damage = damage;
            this.hitRadius = hitRadius;
            this.startTick = startTick;
            this.altar = altar;
        }

        public double length() {
            return length;
        }
    }

    /**
     * 좌클릭으로 검기를 쏩니다. 평타 간격이 다 차지 않았으면 쏘지 않습니다(연타로 약한 검기를 뿌리지 않게).
     *
     * @return 쐈으면 {@code true}
     */
    static boolean fireBladeWave(ServerPlayer player, PlayerLane lane, DemonLordState state, DemonLordSkillTower altar,
            long gameTime, int intervalTicks) {
        if (state.bladeChargeScale(gameTime, intervalTicks) < 1.0) {
            return false;
        }
        state.recordBladeAttack(gameTime);
        DemonLordPassive passive = DemonLordPassive.BLADE_WAVE;
        double range = Math.max(1.0, passive.ability("range", 14.0));
        double speed = Math.max(0.2, passive.ability("speed", 1.4));
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 origin = player.getEyePosition().subtract(0.0, 0.4, 0.0);
        HitResult clip = player.level().clip(new ClipContext(origin, origin.add(direction.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double length = clip.getType() == HitResult.Type.MISS ? range : Math.max(0.5, clip.getLocation().distanceTo(origin));
        double damage = state.bladeDamage() * passive.ability("damageRatio", 0.6);
        state.bladeWaves().add(new BladeWave(origin, direction, speed, length, damage,
                Math.max(0.1, passive.ability("hitRadius", 1.1)), gameTime + BLADE_WAVE_DELAY, altar));
        float yaw = DisplayShapes.yawOf(direction.x, direction.z);
        DemonLordVfx.play(lane, DemonLordDisplayVfx.bladeWave(yaw, player.getXRot(), length, speed, DemonLordVfx.seed(lane)), origin);
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.8f);
        }
        return true;
    }

    /** 날아가는 검기를 한 틱 전진시킵니다. */
    static void tickBladeWaves(ServerPlayer player, PlayerLane lane, DemonLordState state, long gameTime) {
        List<BladeWave> waves = state.bladeWaves();
        if (waves.isEmpty() || lane.arenaWorld() == null) {
            return;
        }
        for (Iterator<BladeWave> iterator = waves.iterator(); iterator.hasNext(); ) {
            BladeWave wave = iterator.next();
            if (gameTime <= wave.startTick) {
                continue;
            }
            double from = wave.travelled;
            double to = Math.min(wave.length, from + wave.speed);
            wave.travelled = to;
            advance(player, lane, state, wave, wave.origin.add(wave.direction.scale(from)), wave.origin.add(wave.direction.scale(to)));
            if (to >= wave.length) {
                iterator.remove();
            }
        }
    }

    private static void advance(ServerPlayer player, PlayerLane lane, DemonLordState state, BladeWave wave, Vec3 from, Vec3 to) {
        AABB sweep = new AABB(from, to).inflate(wave.hitRadius + 1.0);
        List<SemionMonsterEntity> struck = new ArrayList<>(lane.arenaWorld().getEntitiesOfClass(SemionMonsterEntity.class, sweep,
                entity -> entity.isAlive() && entity.runtimeMonster() != null && !wave.hit.contains(entity.getId())
                        && state.canFight(entity.runtimeMonster())
                        && crosses(entity.getBoundingBox().inflate(wave.hitRadius), from, to)));
        struck.sort(Comparator.comparingDouble(entity -> entity.position().distanceToSqr(wave.origin)));
        for (SemionMonsterEntity target : struck) {
            wave.hit.add(target.getId());
            double dealt = DemonLordService.dealDamage(player, lane, wave.altar, target, wave.damage, DamageType.PHYSICAL).dealtDamage();
            if (wave.firstHit) {
                continue;
            }
            // 폭발(흡혈 참격)과 마무리 동작은 처음 맞은 적에게서만 터집니다.
            wave.firstHit = true;
            if (state.loadout().hasPassive(DemonLordPassive.BLOOD_CLEAVE)) {
                bloodCleave(player, lane, state, wave.altar, target, wave.damage, dealt);
            }
            DemonLordService.applyFinisher(player, lane, state, wave.altar, target, dealt);
        }
    }

    /** 선분이 상자를 지나가는지(시작점이 상자 안이어도 참). */
    static boolean crosses(AABB box, Vec3 from, Vec3 to) {
        return box.contains(from) || box.clip(from, to).isPresent();
    }

    // ------------------------------------------------------------ 침공군 호위

    /**
     * 인컴 타워가 보낼 유닛을 적 레인 대신 무작위 아군 라인의 빈자리에 호위로 세웁니다.
     *
     * @return 세웠으면 {@code true}. 어느 아군 라인에도 빈자리가 없으면 {@code false}이고, 그때는 유닛을 보내지 않습니다.
     */
    public static boolean deployInvasionGuard(PlayerLane ownLane, TowerType incomeType, Monster unit, int round, Random random) {
        List<PlayerLane> lanes = new ArrayList<>(ownLane.teamLanes().stream()
                .filter(lane -> lane.arenaWorld() != null && lane.laneLayout() != null).toList());
        Collections.shuffle(lanes, random);
        DemonLordPassive passive = DemonLordPassive.INVASION_GUARD;
        TowerType type = InvasionGuardTower.type(incomeType, unit,
                passive.ability("healthRatio", 1.0), passive.ability("damageRatio", 1.0));
        for (PlayerLane lane : lanes) {
            List<GridPosition> free = freePositions(lane);
            if (free.isEmpty()) {
                continue;
            }
            GridPosition at = free.get(random.nextInt(free.size()));
            InvasionGuardTower guard = new InvasionGuardTower(type, ownLane.ownerPlayer(), ownLane.teamId(), lane.laneId(), at);
            lane.addTower(guard);
            // 웨이브 시작 처리가 이미 지나간 뒤에 들어오므로 시작 표시를 직접 해 줍니다.
            guard.markWaveStarted(round);
            guard.onWaveStarted(lane, round);
            DemonLordVfx.play(lane, DemonLordDisplayVfx.echo(DemonLordVfx.seed(lane)), new Vec3(at.x() + 0.5, at.y() + 1.0, at.z() + 0.5));
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------ 군단의 잔영

    /**
     * 웨이브가 시작될 때 아군 라인의 타워를 무작위로 몇 개 복제해 마왕 라인의 빈자리에 세웁니다.
     *
     * <p>복제본은 임시 복제본이라 타워 수를 차지하지 않고 라운드가 끝나면 레인이 치웁니다. 원본 주인의 타워로
     * 만들어, 원본 빌더의 상태(마나·군대 등)를 읽는 타워도 제 주인의 값을 씁니다.
     *
     * @return 세운 복제본 수
     */
    static int summonLegion(PlayerLane lane, int round) {
        int copies = (int) Math.max(0.0, DemonLordPassive.LEGION_ECHO.ability("copies", 5.0));
        if (copies <= 0 || lane.arenaWorld() == null) {
            return 0;
        }
        List<Tower> pool = new ArrayList<>();
        for (PlayerLane ally : lane.teamLanes()) {
            if (ally == lane) {
                continue;
            }
            for (Tower tower : ally.towers()) {
                if (copyable(tower)) {
                    pool.add(tower);
                }
            }
        }
        if (pool.isEmpty()) {
            return 0;
        }
        Random random = new Random(lane.arenaWorld().getGameTime() ^ (lane.ownerPlayer() == null ? 0 : lane.ownerPlayer().hashCode()));
        Collections.shuffle(pool, random);
        List<GridPosition> free = freePositions(lane);
        Collections.shuffle(free, random);
        int placed = 0;
        for (Tower original : pool) {
            if (placed >= copies || placed >= free.size()) {
                break;
            }
            Optional<ProductionTowerCatalog.CatalogEntry> entry = ProductionTowerCatalog.find(original.type().id());
            if (entry.isEmpty()) {
                continue;
            }
            GridPosition at = free.get(placed);
            Tower copy = entry.get().create(original.ownerPlayer(), original.teamId(), lane.laneId(), at);
            copy.markTemporaryCopy(original.logicalId());
            lane.addTower(copy);
            // 웨이브 시작 처리가 이미 지나간 뒤에 들어오므로 시작 표시를 직접 해 줍니다.
            copy.markWaveStarted(round);
            copy.onWaveStarted(lane, round);
            DemonLordVfx.play(lane, DemonLordDisplayVfx.echo(DemonLordVfx.seed(lane) + placed),
                    new Vec3(at.x() + 0.5, at.y() + 1.0, at.z() + 0.5));
            placed++;
        }
        return placed;
    }

    /**
     * 복제할 수 있는 타워인지.
     *
     * <p>싸우는 타워만 복제합니다. 플레이어마다 하나만 둘 수 있는 핵심 타워(흑마법사 본체, 엔더 드래곤, 심장,
     * 골렘, 지부장, 마법핵, 서큐버스, 냉각 장치)와 파티 단위로 움직이는 용사 파티, 증강 타워, 임시 복제본은 뺍니다.
     */
    public static boolean copyable(Tower tower) {
        if (tower == null || tower.isTemporaryCopy() || tower.isAugmentTower() || tower.health() <= 0.0) {
            return false;
        }
        TowerType type = tower.type();
        if (type.damage() <= 0.0 || !tower.countsForLaneDefense() || !tower.targetableByMonsters()) {
            return false;
        }
        if (tower instanceof HeroPartyTower || tower instanceof EndTower || tower instanceof DemonLordFiend
                || tower instanceof kim.biryeong.semiontd.tower.plant.GardenerTower) {
            return false;
        }
        if (WarlockTowers.isWarlockCore(type) || BodyTowers.isHeart(type) || EngineerTowers.isGolem(type)
                || FutureAgencyTowers.isLeader(type) || MageTowers.isCore(type) || SuccubusTowers.isSuccubus(type)
                || FrostTowers.isEmissionCoolingDevice(type) || FrostTowers.isEruptionCoolingDevice(type)) {
            return false;
        }
        return ProductionTowerCatalog.find(type.id()).isPresent();
    }

    static List<GridPosition> freePositions(PlayerLane lane) {
        BlockBounds bounds = lane.laneLayout().laneArea();
        Set<Long> occupiedColumns = new HashSet<>();
        for (Tower tower : lane.towers()) {
            GridPosition position = tower.position();
            occupiedColumns.add(columnKey(position.x(), position.z()));
        }
        List<GridPosition> free = new ArrayList<>();
        for (int x = bounds.min().getX(); x <= bounds.max().getX(); x++) {
            for (int z = bounds.min().getZ(); z <= bounds.max().getZ(); z++) {
                BlockPos candidate = new BlockPos(x, bounds.max().getY(), z);
                Optional<BlockPos> floor = TowerPlacementPositions.resolve(lane, candidate);
                if (floor.isEmpty()) {
                    continue;
                }
                GridPosition grid = GridPosition.from(floor.get());
                if (!occupiedColumns.contains(columnKey(grid.x(), grid.z()))) {
                    free.add(grid);
                }
            }
        }
        return free;
    }

    static long columnKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }
}
