package kim.biryeong.semiontd.tower.queen;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaTargetMode;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.visual.TowerEquipmentVisual;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.EntityBackedTower;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.TowerType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

public final class QueenCardTower extends ProductionTower {
    private static final TowerDataKey<String> SUIT = TowerDataKey.of(id("queen_card_suit"), String.class);
    private static final TowerDataKey<Integer> RANK = TowerDataKey.of(id("queen_card_rank"), Integer.class);
    private transient PlayerLane lane;
    private PokerHand pokerHand = PokerHand.HIGH_CARD;
    private double pokerBonus;
    private long lastCombatTick = Long.MIN_VALUE;
    private int heartHealCooldown;
    private boolean waveActive;
    private transient ArmorStand equipmentVisual;

    public QueenCardTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId,
                          GridPosition originalPosition, GridPosition currentPosition) {
        super(type, ownerPlayer, teamId, laneId, originalPosition, currentPosition);
    }

    public Optional<QueenCard> card() {
        try {
            return getData(SUIT).map(QueenCard.Suit::valueOf)
                    .flatMap(suit -> getData(RANK).map(rank -> new QueenCard(suit, rank)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public void onPlaced(PlayerLane lane) {
        this.lane = lane;
        if (card().isEmpty()) {
            assignCard(QueenStates.state(ownerPlayer()).drawNextCard());
        }
        super.onPlaced(lane);
        syncEquipmentVisual();
    }

    void assignCard(QueenCard value) {
        if (value == null) return;
        setData(SUIT, value.suit().name());
        setData(RANK, value.rank());
        syncMaxHealth(desiredMaxHealth(), true);
        if (lane != null) onStateChanged(lane);
    }

    @Override
    protected void configureEntityAfterSpawn(SemionTowerEntity entity, PlayerLane lane) {
        applyAppearance(entity);
    }

    @Override
    public void onStateChanged(PlayerLane lane) {
        super.onStateChanged(lane);
        this.lane = lane;
        entity(lane).ifPresent(entity -> {
            applyAppearance(entity);
            equipmentVisual = TowerEquipmentVisual.sync(equipmentVisual, entity);
        });
    }

    @Override
    public void onRemoved(PlayerLane lane) {
        TowerEquipmentVisual.remove(equipmentVisual);
        equipmentVisual = null;
        super.onRemoved(lane);
    }

    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        this.lane = lane;
        waveActive = true;
        heartHealCooldown = QueenBalance.heartHealIntervalTicks();
        QueenPoker.snapshot(lane, ownerPlayer());
    }

    @Override
    public void resetForRound(PlayerLane lane) {
        waveActive = false;
        heartHealCooldown = 0;
        pokerHand = PokerHand.HIGH_CARD;
        pokerBonus = 0.0;
        syncMaxHealth(desiredMaxHealth(), false);
        super.resetForRound(lane);
    }

    @Override
    public void tick(PlayerLane lane) {
        this.lane = lane;
        super.tick(lane);
        syncEquipmentVisual();
        if (!waveActive || isDestroyed(lane) || card().map(QueenCard::suit).orElse(null) != QueenCard.Suit.HEART) return;
        if (heartHealCooldown > 0) {heartHealCooldown--; return;}
        if (healFamily(lane)) heartHealCooldown = QueenBalance.heartHealIntervalTicks();
    }

    void applyPokerSnapshot(PokerHand hand) {
        pokerHand = hand == null ? PokerHand.HIGH_CARD : hand;
        pokerBonus = QueenBalance.handBonus(pokerHand);
        syncMaxHealth(desiredMaxHealth(), true);
        if (lane != null) onStateChanged(lane);
    }

    boolean recentlyActive(long gameTime) {
        return gameTime - lastCombatTick <= QueenBalance.giantAccelerationMemoryTicks();
    }

    @Override public double effectBaseMaxHealth() {return desiredMaxHealth();}
    @Override protected void refreshMaxHealthAfterTypeChange(PlayerLane lane) {syncMaxHealth(desiredMaxHealth(), false);}
    @Override public double adjustAttackRange(double baseRange) {return card().map(value -> QueenBalance.cardRange(value.suit())).orElse(baseRange);}
    @Override public int adjustAttackInterval(int baseIntervalTicks) {
        int interval = card().map(value -> QueenBalance.cardInterval(value.suit())).orElse(baseIntervalTicks);
        return Math.max(1, (int) Math.ceil(interval / (1.0 + pokerBonus)));
    }
    @Override public int aggroPriority() {return card().map(value -> QueenBalance.cardAggro(value.suit())).orElse(super.aggroPriority());}

    @Override
    public double modifyIncomingDamage(SemionTowerEntity source, DamageSource damageSource, double damage) {
        return card().map(QueenCard::suit).orElse(null) == QueenCard.Suit.CLUB
                ? damage * (1.0 - QueenBalance.clubDamageReduction()) : damage;
    }

    @Override
    public void onAttackResolved(SemionTowerEntity source, SemionMonsterEntity target, double attempted,
                                 double outgoing, double dealt, boolean killed) {
        if (target == null || !target.isAlive()) return;
        lastCombatTick = source.level().getGameTime();
        double points = QueenBalance.cardShrinkPoints() * (1.0 + pokerBonus);
        QueenShrink.apply(target, points);
        shrinkNearbyTargets(source, target, points);
    }

    @Override
    public void onDamaged(SemionTowerEntity source, DamageSource damageSource, double amount,
                          double previousHealth, double currentHealth) {
        if (currentHealth < previousHealth) lastCombatTick = source.level().getGameTime();
    }

    @Override
    public void onDeath(PlayerLane lane) {
        entity(lane).ifPresent(source -> {
            MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTower(
                    id("queen_card_death"), source, QueenBalance.cardDeathRadius(),
                    AreaVfxSpec.onTrigger(AreaVfxStyles.DEBUFF));
            SemionTdApi.areaEffects().applyToMonsters(request, target ->
                    QueenShrink.apply(target, QueenBalance.cardDeathShrinkPoints() * (1.0 + pokerBonus))
                            ? AreaEffectOutcome.APPLIED : AreaEffectOutcome.UNCHANGED);
        });
    }

    @Override
    public List<String> runtimeDetailLines() {
        QueenCard value = card().orElse(null);
        if (value == null) return List.of("카드: 추첨 대기");
        return List.of(
                "카드: " + value.label() + " (" + value.suit().displayName() + ")",
                "현재 족보: " + pokerHand.displayName(),
                "축소 위력: " + oneDecimal(QueenBalance.cardShrinkPoints() * (1.0 + pokerBonus))
                        + "점 (점당 " + percentInteger(1.0 - QueenBalance.shrinkFactorPerPoint()) + " 감소)",
                "능력치 하한: 원본의 " + percentInteger(QueenBalance.minimumStatScale()),
                "외형 하한: 원본의 " + percentInteger(QueenBalance.minimumVisualScale()),
                "족보 보너스: " + percentInteger(pokerBonus)
        );
    }

    private double desiredMaxHealth() {
        return card().map(value -> QueenBalance.cardMaxHealth(value.suit()) * (1.0 + pokerBonus)).orElse(type().maxHealth());
    }

    private void shrinkNearbyTargets(SemionTowerEntity source, SemionMonsterEntity primary, double points) {
        if (lane == null) return;
        boolean spade = card().map(QueenCard::suit).orElse(null) == QueenCard.Suit.SPADE;
        double radius = spade ? QueenBalance.spadeRadius() : QueenBalance.cardSplashRadius();
        int extraTargets = spade ? QueenBalance.spadeExtraTargets() : QueenBalance.cardSplashExtraTargets();
        Set<UUID> allowed = new HashSet<>();
        lane.activeMonsters().stream()
                .filter(monster -> monster.isAlive() && monster.minecraftEntityId() >= 0)
                .map(monster -> lane.arenaWorld().getEntity(monster.minecraftEntityId()))
                .filter(SemionMonsterEntity.class::isInstance).map(SemionMonsterEntity.class::cast)
                .filter(target -> target != primary && target.position().distanceToSqr(primary.position()) <= radius * radius)
                .sorted(Comparator.comparingDouble(target -> target.distanceToSqr(primary)))
                .limit(extraTargets).forEach(target -> allowed.add(target.getUUID()));
        if (allowed.isEmpty()) return;
        MonsterAreaEffectRequest request = MonsterAreaEffectRequest.aroundTarget(
                id(spade ? "queen_spade_shrink" : "queen_card_splash_shrink"), source, primary, radius,
                AreaVfxSpec.onChange(AreaVfxStyles.DEBUFF))
                .withFilter(target -> allowed.contains(target.getUUID()));
        SemionTdApi.areaEffects().applyToMonsters(request, target ->
                QueenShrink.apply(target, points) ? AreaEffectOutcome.APPLIED : AreaEffectOutcome.UNCHANGED);
    }

    private boolean healFamily(PlayerLane lane) {
        Optional<SemionTowerEntity> source = entity(lane);
        if (source.isEmpty()) return false;
        TowerAreaEffectRequest request = new TowerAreaEffectRequest(
                id("queen_heart_heal"), source.get(), source.get().position(), QueenBalance.heartHealRadius(),
                TowerAreaTargetMode.REGISTERED, true,
                target -> QueenTowers.isQueenTower(target.tower().type()), AreaVfxSpec.onChange(AreaVfxStyles.BUFF));
        double amount = QueenBalance.heartHealAmount() * (1.0 + pokerBonus);
        return SemionTdApi.areaEffects().applyToTowers(request, target -> heal(target.tower(), lane, amount)
                ? AreaEffectOutcome.APPLIED : AreaEffectOutcome.UNCHANGED).appliedCount() > 0;
    }

    private static boolean heal(Tower tower, PlayerLane lane, double amount) {
        if (tower.health() <= 0.0 || tower.health() >= tower.currentMaxHealth()) return false;
        if (tower instanceof EntityBackedTower backed && backed.entityId().isPresent()
                && lane.arenaWorld().getEntity(backed.entityId().getAsInt()) instanceof SemionTowerEntity entity) {
            return entity.receiveHealing(amount);
        }
        double before = tower.health();
        tower.syncHealth(before + amount);
        return tower.health() > before;
    }

    private Optional<SemionTowerEntity> entity(PlayerLane lane) {
        if (lane == null || entityId().isEmpty()) return Optional.empty();
        return Optional.ofNullable(lane.arenaWorld().getEntity(entityId().getAsInt()))
                .filter(SemionTowerEntity.class::isInstance).map(SemionTowerEntity.class::cast);
    }

    private void applyAppearance(SemionTowerEntity entity) {
        QueenCard value = card().orElse(null);
        if (value == null) return;
        entity.setCustomName(Component.literal(value.label() + " 카드병정"));
        entity.setCustomNameVisible(true);
        ItemStack item = switch (value.suit()) {
            case HEART -> new ItemStack(Items.RED_DYE);
            case DIAMOND -> new ItemStack(Items.DIAMOND);
            case CLUB -> new ItemStack(Items.OAK_SAPLING);
            case SPADE -> new ItemStack(Items.IRON_SHOVEL);
        };
        int armorColor = switch (value.suit()) {
            case HEART -> 0xB02E26;
            case DIAMOND -> 0x3AB3DA;
            case CLUB -> 0x5E7C16;
            case SPADE -> 0x1D1D21;
        };
        ItemStack chestplate = new ItemStack(Items.LEATHER_CHESTPLATE);
        chestplate.set(DataComponents.DYED_COLOR, new DyedItemColor(armorColor));
        entity.setItemSlot(EquipmentSlot.MAINHAND, item);
        entity.setItemSlot(EquipmentSlot.CHEST, chestplate);
    }

    private void syncEquipmentVisual() {
        equipmentVisual = TowerEquipmentVisual.sync(equipmentVisual, entity(lane).orElse(null));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SemionTd.MOD_ID, path);
    }
}
