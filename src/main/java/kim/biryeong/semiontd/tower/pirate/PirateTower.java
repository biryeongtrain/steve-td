package kim.biryeong.semiontd.tower.pirate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaTargetMode;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.effect.TimedEffectType;
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
import kim.biryeong.semiontd.tower.hero.FakePlayerTowerVisuals;

public final class PirateTower extends ProductionTower {
    private static final net.minecraft.resources.ResourceLocation SOUL_REAVER_HASTE = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("semion-td", "pirate/soul_reaver_haste");
    private static final net.minecraft.resources.ResourceLocation ANCHOR_GUARD = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("semion-td", "pirate/anchor_guard");
    private static final net.minecraft.resources.ResourceLocation DECKHAND_OPENING_DAMAGE = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("semion-td", "pirate/deckhand_opening_damage");
    private boolean firstIronAttack = true;
    private int parrotOpeningAttacks;
    private boolean chestReady;
    private int chestRoundsReduced;
    private double anchorBonus;
    public PirateTower(TowerType type, UUID owner, TeamId team, int lane, GridPosition position) { super(type, owner, team, lane, position); }
    public PirateTower(TowerType type, UUID owner, TeamId team, int lane, GridPosition original, GridPosition current) { super(type, owner, team, lane, original, current); }
    @Override protected void configureEntityAfterSpawn(SemionTowerEntity entity, PlayerLane lane) { super.configureEntityAfterSpawn(entity, lane); if (PirateTowers.isPlayerVisual(type())) { entity.setInvisible(true); entity.setCustomNameVisible(false); FakePlayerTowerVisuals.attach(entity, this); } }
    @Override public void tick(PlayerLane lane) { super.tick(lane); if (PirateTowers.isPlayerVisual(type())) FakePlayerTowerVisuals.tick(this); if (chestReady && PirateTowers.isChest(type())) cashOutChest(lane); }
    @Override public void onRemoved(PlayerLane lane) { if (PirateTowers.isPlayerVisual(type())) FakePlayerTowerVisuals.remove(this); super.onRemoved(lane); }
    @Override protected void copyRuntimeStateFrom(Tower previousTower) { super.copyRuntimeStateFrom(previousTower); if (previousTower instanceof PirateTower pirateTower) { chestReady = pirateTower.chestReady; chestRoundsReduced = pirateTower.chestRoundsReduced; anchorBonus = pirateTower.anchorBonus; parrotOpeningAttacks = pirateTower.parrotOpeningAttacks; } }
    @Override public long sellRefundAmount() { return Math.round(paidMineralCost() * .5); }
    @Override public void onWaveStarted(PlayerLane lane, int round) { super.onWaveStarted(lane, round); firstIronAttack = true; if (PirateTowers.isParrot(type())) { parrotOpeningAttacks = abilityInt("openingAttackCount", 3); runtimeEntity(lane).ifPresent(SemionTowerEntity::refreshCombatStats); } if (PirateTowers.isDeckhand(type())) runtimeEntity(lane).ifPresent(entity -> entity.refreshTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS, DECKHAND_OPENING_DAMAGE, ability("openingDamageBonus", .5), abilityTicks("openingDamageTicks", 120))); if (PirateTowers.isAnchor(type())) applyAnchorGuard(lane); refreshEconomyStats(lane); }
    public void onRoundEnded(PlayerLane lane, int round) { if (!PirateTowers.isChest(type()) || chestReady || chestRoundProgress(round) < abilityInt("rounds", 5)) return; chestReady = true; cashOutChest(lane); }
    @Override public void onDeath(PlayerLane lane) { if (PirateTowers.isPlayerVisual(type())) FakePlayerTowerVisuals.remove(this); if (PirateTowers.matches(type(), PirateTowers.SOUL_REAVER_DECKHAND)) applySoulReaverHaste(lane); super.onDeath(lane); }
    @Override public void onSold(PlayerLane lane) { if (PirateTowers.isChest(type())) applyChestSaleBonus(lane); }
    @Override public double effectBaseMaxHealth() { return super.effectBaseMaxHealth() + economyHealthBonus(); }
    @Override public double permanentMaxHealthBonus() { double bonus = super.permanentMaxHealthBonus(); return PirateTowers.isSwordsman(type()) ? bonus * ability("positiveBuffMultiplier", PirateTowers.matches(type(), PirateTowers.IRON_SWORDSMAN) ? 2.0 : 1.5) : bonus; }
    @Override public double permanentFlatDamageBonus() { double bonus = super.permanentFlatDamageBonus(); return PirateTowers.isSwordsman(type()) ? bonus * ability("positiveBuffMultiplier", PirateTowers.matches(type(), PirateTowers.IRON_SWORDSMAN) ? 2.0 : 1.5) : bonus; }
    @Override public double adjustIncomingTimedEffectMagnitude(TimedEffectType type, double magnitude) { return magnitude <= 0 || !PirateTowers.isSwordsman(type()) ? magnitude : magnitude * ability("positiveBuffMultiplier", PirateTowers.matches(type(), PirateTowers.IRON_SWORDSMAN) ? 2.0 : 1.5); }
    @Override public double modifyAttackDamage(SemionTowerEntity source, SemionMonsterEntity target, double damage) { double result = damage + economyDamageBonus(); if (PirateTowers.isParrot(type())) { var player = PirateStates.player(ownerPlayer()); if (player != null) result *= 1 + Math.min(ability("damageBonusCap", 5), player.economy().diamond() / 100 * ability("damagePerHundredDiamond", PirateTowers.matches(type(), PirateTowers.PARROT) ? .02 : PirateTowers.matches(type(), PirateTowers.ONE_EYED_PARROT) ? .035 : .05)); if (parrotOpeningAttacks > 0) result *= ability("openingDamageMultiplier", 2.5); } if (PirateTowers.matches(type(), PirateTowers.IRON_SWORDSMAN) && firstIronAttack) result *= ability("firstAttackMultiplier", 2); if (PirateTowers.matches(type(), PirateTowers.GUIDE) && currentMaxHealth() >= ability("lostHealthThreshold", 1200) && target != null && target.runtimeMonster() != null) result += Math.max(0, target.runtimeMonster().maxHealth() - target.runtimeMonster().health()) * ability("lostHealthRatio", .035); return result; }
    @Override public int adjustAttackInterval(int base) { if (!PirateTowers.matches(type(), PirateTowers.CAPABLANCA)) return base; var player = PirateStates.player(ownerPlayer()); return player == null ? base : Math.max(1, (int) Math.round(base / (1 + Math.min(ability("speedBonusCap", 1), player.economy().emerald() / 100 * ability("speedPerHundredEmerald", .05))))); }
    @Override public double adjustAttackRange(double base) { return PirateTowers.isParrot(type()) && parrotOpeningAttacks > 0 ? base + ability("openingRangeBonus", 10) : base; }
    @Override public void onAttackResolved(SemionTowerEntity source, SemionMonsterEntity target, double attempted, double outgoing, double dealt, boolean killed) { super.onAttackResolved(source, target, attempted, outgoing, dealt, killed); if (PirateTowers.isParrot(type()) && parrotOpeningAttacks > 0) { parrotOpeningAttacks--; if (parrotOpeningAttacks == 0 && source != null) source.refreshCombatStats(); } if (PirateTowers.matches(type(), PirateTowers.IRON_SWORDSMAN) && firstIronAttack) { firstIronAttack = false; target.applyTimedEffect(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION, ability("firstAttackSlow", .80), abilityTicks("firstAttackSlowTicks", 40)); } double ratio = PirateTowers.matches(type(), PirateTowers.LOOKOUT_DECKHAND) ? ability("splashRatio", .5) : PirateTowers.matches(type(), PirateTowers.SOUL_REAVER_DECKHAND) ? ability("splashRatio", .6) : PirateTowers.matches(type(), PirateTowers.FIRST_NAVIGATOR) ? ability("splashRatio", .8) : 0; if (ratio > 0 && target != null && target.isAlive()) TowerAreaDamage.applyResolved(this, source, MonsterAreaEffectRequest.aroundTarget(AreaEffectIds.tower(this, "splash"), source, target, ability("splashRadius", PirateTowers.matches(type(), PirateTowers.FIRST_NAVIGATOR) ? 2 : .5), AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH)), ignored -> outgoing * ratio, true, (ignored, areaDamage, dead) -> { }); }
    @Override public void onKill(SemionTowerEntity source, SemionMonsterEntity target, double damage) { super.onKill(source, target, damage); if (!PirateTowers.isDeckhand(type())) return; var player = PirateStates.player(ownerPlayer()); if (player == null) return; int min = abilityInt("incomeMin", PirateTowers.matches(type(), PirateTowers.DECKHAND) ? 1 : PirateTowers.matches(type(), PirateTowers.LOOKOUT_DECKHAND) ? 2 : 3); int max = Math.max(min, abilityInt("incomeMax", PirateTowers.matches(type(), PirateTowers.DECKHAND) ? 2 : PirateTowers.matches(type(), PirateTowers.LOOKOUT_DECKHAND) ? 3 : 5)); player.economy().addDiamond(min + java.util.concurrent.ThreadLocalRandom.current().nextInt(max - min + 1)); PirateStates.grantFerrymanIncome(player); }
    @Override public List<String> runtimeDetailLines() { List<String> lines = new ArrayList<>(); if (PirateTowers.isAdmiral(type())) lines.add("제독 누적 소비: " + PirateStates.admiralProgress(ownerPlayer()) + "/" + abilityInt(PirateTowers.ADMIRAL, "spendThreshold", 200)); if (PirateTowers.isParrot(type()) && parrotOpeningAttacks > 0) lines.add("앵무 개시 강화: 남은 공격 " + parrotOpeningAttacks + "회"); if (PirateTowers.isHelmsman(type())) lines.add("이번 라운드 소비: 다이아 " + PirateStates.diamondSpent(ownerPlayer()) + ", 에메랄드 " + PirateStates.emeraldSpent(ownerPlayer())); if (PirateTowers.isChest(type())) { int required = abilityInt("rounds", 5); String shortened = chestRoundsReduced > 0 ? " (뱃노래 단축 +" + chestRoundsReduced + ")" : ""; lines.add(chestReady ? "보물상자: 회수 대기" : "보물상자 경과: " + Math.min(required, chestRoundProgress(currentRound())) + "/" + required + " 라운드" + shortened); } if (PirateTowers.isAnchor(type())) lines.add("닻 피해 감소 보너스: " + Math.round(anchorBonus * 1000) / 10.0 + "%"); return List.copyOf(lines); }
    public void refreshEconomyStats(PlayerLane lane) { syncMaxHealth(effectBaseMaxHealth(), true); onStateChanged(lane); }
    public static void triggerAdmiralEffects(PlayerLane lane, UUID owner) {
        if (lane == null || owner == null) return;
        lane.towers().stream().filter(PirateTower.class::isInstance).map(PirateTower.class::cast)
                .filter(tower -> owner.equals(tower.ownerPlayer()) && PirateTowers.isAdmiral(tower.type()))
                .forEach(tower -> tower.triggerAdmiral(lane));
    }
    public static void notifyTowerSold(PlayerLane lane, Tower sold) {
        if (lane == null || sold == null) return;
        List<PirateTower> pirateTowers = List.copyOf(lane.towers()).stream().filter(PirateTower.class::isInstance).map(PirateTower.class::cast).toList();
        pirateTowers.stream()
                .filter(tower -> PirateTowers.isAnchor(tower.type()) && tower.teamId() == sold.teamId() && distanceSquared(tower, sold) <= tower.ability("radius", 2) * tower.ability("radius", 2))
                .forEach(tower -> tower.anchorBonus = Math.min(tower.ability("saleBonusCap", .10), tower.anchorBonus + tower.ability("saleBonus", .001)));
        if (sold instanceof PirateTower singer && PirateTowers.isBoatSinger(singer.type())) {
            int reduction = singer.abilityInt("chestRoundReduction", PirateTowers.matches(singer.type(), PirateTowers.SWEET_BOAT_SINGER) ? 2 : 1);
            pirateTowers.stream()
                    .filter(tower -> PirateTowers.isChest(tower.type()) && singer.ownerPlayer().equals(tower.ownerPlayer()))
                    .forEach(chest -> {
                        chest.runtimeEntity(lane).ifPresent(PirateTowerVfx::showChestAcceleration);
                        chest.advanceChestTimer(lane, reduction);
                    });
        }
    }
    private void triggerAdmiral(PlayerLane lane) {
        List<Integer> effects = new ArrayList<>(List.of(0, 1, 2));
        Collections.shuffle(effects);
        int count = abilityInt("effectCount", PirateTowers.matches(type(), PirateTowers.GOLDEN_ADMIRAL) ? 2 : 1);
        for (int index = 0; index < count; index++) applyAdmiralEffect(lane, effects.get(index));
    }
    private void applyAdmiralEffect(PlayerLane lane, int effect) {
        var player = PirateStates.player(ownerPlayer());
        if (effect == 0 && player != null) {
            player.economy().addDiamond(Math.round(java.util.concurrent.ThreadLocalRandom.current().nextBoolean()
                    ? ability(PirateTowers.ADMIRAL, "paybackLow", 50)
                    : ability(PirateTowers.ADMIRAL, "paybackHigh", 75)));
            PirateStates.grantFerrymanIncome(player);
            runtimeEntity(lane).ifPresent(PirateTowerVfx::showAdmiralDiamondGain);
            return;
        }
        List<Tower> targets = lane.towers().stream()
                .filter(tower -> ownerPlayer().equals(tower.ownerPlayer()))
                .toList();
        if (effect == 1) {
            double bonus = java.util.concurrent.ThreadLocalRandom.current().nextBoolean()
                    ? ability(PirateTowers.ADMIRAL, "maxHealthBonusLow", 2)
                    : ability(PirateTowers.ADMIRAL, "maxHealthBonusHigh", 4);
            targets.forEach(target -> {
                target.addPermanentMaxHealthBonus(bonus, lane);
                showAdmiralStatGain(lane, target);
            });
            return;
        }
        double bonus = ability(PirateTowers.ADMIRAL, "damageBonus", .5);
        targets.forEach(target -> {
            target.addPermanentFlatDamageBonus(bonus, lane);
            showAdmiralStatGain(lane, target);
        });
    }
    private void applySoulReaverHaste(PlayerLane lane) {
        runtimeEntity(lane).ifPresent(source -> SemionTdApi.areaEffects().applyToTowers(
                TowerAreaEffectRequest.aroundTower(SOUL_REAVER_HASTE, source, ability("deathHasteRadius", 5), TowerAreaTargetMode.REGISTERED, AreaVfxSpec.onTrigger(AreaVfxStyles.PULSE))
                        .withFilter(target -> target.tower().teamId() == teamId()),
                target -> {
                    SemionTowerEntity entity = target.entity().orElse(null);
                    return entity != null && entity.applyTimedEffect(TimedEffectType.TOWER_ATTACK_SPEED_BONUS, SOUL_REAVER_HASTE, ability("deathHaste", .40), abilityTicks("deathHasteTicks", 60))
                            ? AreaEffectOutcome.APPLIED : AreaEffectOutcome.UNCHANGED;
                }));
    }
    private void applyAnchorGuard(PlayerLane lane) {
        runtimeEntity(lane).ifPresent(source -> {
            double base = ability("damageReduction", PirateTowers.matches(type(), PirateTowers.DROPPED_ANCHOR) ? .01 : PirateTowers.matches(type(), PirateTowers.DEEP_ANCHOR) ? .02 : .05);
            int ticks = abilityTicks("durationTicks", PirateTowers.matches(type(), PirateTowers.DROPPED_ANCHOR) ? 80 : PirateTowers.matches(type(), PirateTowers.DEEP_ANCHOR) ? 140 : 240);
            SemionTdApi.areaEffects().applyToTowers(TowerAreaEffectRequest.aroundTower(ANCHOR_GUARD, source, ability("radius", 2), TowerAreaTargetMode.REGISTERED, AreaVfxSpec.onTrigger(AreaVfxStyles.PULSE))
                    .withFilter(target -> target.tower().teamId() == teamId() && isSelectedAnchorFor(lane, target.tower())), target -> {
                SemionTowerEntity entity = target.entity().orElse(null);
                if (entity == null) return AreaEffectOutcome.UNCHANGED;
                boolean applied = entity.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_REDUCTION, anchorGuardSource(), base + anchorBonus, ticks);
                if (applied) PirateTowerVfx.showAnchorGuard(entity);
                return applied ? AreaEffectOutcome.APPLIED : AreaEffectOutcome.UNCHANGED;
            });
        });
    }
    private void cashOutChest(PlayerLane lane) {
        chestReady = false;
        var player = PirateStates.player(ownerPlayer());
        if (player != null) {
            long reward = abilityInt("cashout", PirateTowers.matches(type(), PirateTowers.SHABBY_CHEST) ? 90 : PirateTowers.matches(type(), PirateTowers.EMPIRE_CHEST) ? 200 : PirateTowers.matches(type(), PirateTowers.DEEP_CHEST) ? 250 : 600);
            player.economy().addDiamond(reward);
        }
        if (lane.removeTower(this)) {
            clearPermanentStatBonuses(lane);
            if (player != null) {
                player.economy().addDiamond(sellRefundAmount());
                PirateStates.grantFerrymanIncome(player);
            }
            notifyTowerSold(lane, this);
            onSold(lane);
        }
    }
    private void applyChestSaleBonus(PlayerLane lane) {
        List<Tower> ownedTowers = lane.towers().stream()
                .filter(tower -> tower != this && ownerPlayer().equals(tower.ownerPlayer()))
                .toList();
        if (ownedTowers.isEmpty()) return;
        if (PirateTowers.matches(type(), PirateTowers.EMPIRE_CHEST)) {
            ownedTowers.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(ownedTowers.size()))
                    .addPermanentFlatDamageBonus(ability("saleDamageBonus", 1), lane);
            return;
        }
        if (PirateTowers.matches(type(), PirateTowers.FANTASY_CHEST)) ownedTowers.forEach(tower -> tower.addPermanentMaxHealthBonus(ability("saleHealthBonus", 10), lane));
        else ownedTowers.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(ownedTowers.size()))
                .addPermanentMaxHealthBonus(ability("saleHealthBonus", PirateTowers.matches(type(), PirateTowers.SHABBY_CHEST) ? 5 : 10), lane);
    }
    private static double distanceSquared(Tower first, Tower second) { double x = first.position().x() - second.position().x(), y = first.position().y() - second.position().y(), z = first.position().z() - second.position().z(); return x * x + y * y + z * z; }
    private static void showAdmiralStatGain(PlayerLane lane, Tower tower) { if (tower instanceof EntityBackedTower entityBackedTower) entityBackedTower.runtimeEntity(lane).ifPresent(PirateTowerVfx::showAdmiralStatGain); }
    private int chestRoundProgress(int round) { return Math.max(0, round - placedRound()) + chestRoundsReduced; }
    private void advanceChestTimer(PlayerLane lane, int rounds) { if (!PirateTowers.isChest(type()) || rounds <= 0 || chestReady) return; chestRoundsReduced += rounds; if (chestRoundProgress(currentRound()) >= abilityInt("rounds", 5)) { chestReady = true; cashOutChest(lane); } }
    private double economyHealthBonus() { long spent = PirateTowers.matches(type(), PirateTowers.GUIDE) ? PirateStates.emeraldSpent(ownerPlayer()) : PirateStates.diamondSpent(ownerPlayer()); if (!PirateTowers.isHelmsman(type())) return 0; return Math.floor(spent / ability("spendStep", 100)) * ability("healthPerStep", PirateTowers.matches(type(), PirateTowers.HELMSMAN) ? 2 : PirateTowers.matches(type(), PirateTowers.GUIDE) ? 3 : PirateTowers.matches(type(), PirateTowers.NAVIGATOR) ? 3 : 5); }
    private double economyDamageBonus() { if (!PirateTowers.isHelmsman(type()) || PirateTowers.matches(type(), PirateTowers.GUIDE)) return 0; return Math.floor(PirateStates.diamondSpent(ownerPlayer()) / ability("spendStep", PirateTowers.matches(type(), PirateTowers.NAVIGATOR) ? 75 : PirateTowers.matches(type(), PirateTowers.FIRST_NAVIGATOR) ? 50 : 100)) * ability("damagePerStep", .5); }
    private boolean isSelectedAnchorFor(PlayerLane lane, Tower recipient) { return lane.towers().stream().filter(PirateTower.class::isInstance).map(PirateTower.class::cast).filter(anchor -> PirateTowers.isAnchor(anchor.type()) && anchor.teamId() == teamId() && distanceSquared(anchor, recipient) <= anchor.ability("radius", 2) * anchor.ability("radius", 2)).sorted((left, right) -> Double.compare(right.ability("damageReduction", 0) + right.anchorBonus, left.ability("damageReduction", 0) + left.anchorBonus)).limit(abilityInt("maxStacks", 2)).anyMatch(anchor -> anchor == this); }
    private net.minecraft.resources.ResourceLocation anchorGuardSource() { GridPosition origin = originalPosition(); return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("semion-td", "pirate/anchor/" + ownerPlayer() + "/" + origin.x() + "_" + origin.y() + "_" + origin.z()); }
    private double ability(String key, double fallback) { return TowerBalanceRuntime.ability(type().id(), key, fallback); }
    private int abilityInt(String key, int fallback) { return TowerBalanceRuntime.abilityInt(type().id(), key, fallback); }
    private int abilityTicks(String key, int fallback) { return TowerBalanceRuntime.abilityTicks(type().id(), key, fallback); }
    private static double ability(TowerType type, String key, double fallback) { return TowerBalanceRuntime.ability(type.id(), key, fallback); }
    private static int abilityInt(TowerType type, String key, int fallback) { return TowerBalanceRuntime.abilityInt(type.id(), key, fallback); }
}
