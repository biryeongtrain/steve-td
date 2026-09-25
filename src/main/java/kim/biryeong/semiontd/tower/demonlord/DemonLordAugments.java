package kim.biryeong.semiontd.tower.demonlord;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentCombat;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.PlayerLane;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

/** Combat-local skill hit history. Replays contain damage only, never another paid cast. */
final class DemonLordAugments {
    static final String SILVER = "job_demon_lord_towers_s";
    static final String PHASE = "job_demon_lord_towers_g1";
    static final String COMBO = "job_demon_lord_towers_g2";
    static final String THRONES = "job_demon_lord_towers_p";
    private final Map<DemonLordSkill, Spell> recent = new EnumMap<>(DemonLordSkill.class);
    private final List<ArmorStand> visuals = new ArrayList<>();
    private List<Hit> currentHits;
    private DemonLordSkillTower currentAltar;
    private long finisherUntil = Long.MIN_VALUE;
    private long phaseUntil = Long.MIN_VALUE;
    private long comboUntil = Long.MIN_VALUE;
    private long comboReady = Long.MIN_VALUE;
    private long thronesReady = Long.MIN_VALUE;
    private long visualsUntil;
    private boolean phaseUsed;
    private boolean replaying;
    private boolean cooldownsDirty;
    private TargetedProgress targeted = new TargetedProgress(0, 0, 0, 0, 0, 0, false);

    // Kept separately from spell state: death/reset must not discard a pending heat settlement.
    record TargetedProgress(int heat, int mastery, int round, int settledRound,
                            double openingHealth, double enemyDamage, boolean overheated) {}

    TargetedProgress targetedProgress() {return targeted;}
    void restoreTargetedProgress(TargetedProgress progress) {targeted = progress;}

    void beginTargeted(AugmentSnapshot snapshot, int round, double openingHealth) {
        if (round <= targeted.settledRound() || round == targeted.round()) return;
        targeted = new TargetedProgress(targeted.heat(), targeted.mastery(), round, targeted.settledRound(),
                openingHealth, 0, snapshot.has("overheat_core")
                && targeted.heat() < snapshot.parameter("overheat_core", "maxStacks", 5));
    }

    void recordEnemyDamage(double amount) {
        if (targeted.round() <= targeted.settledRound() || amount <= 0 || !Double.isFinite(amount)) return;
        targeted = new TargetedProgress(targeted.heat(), targeted.mastery(), targeted.round(), targeted.settledRound(),
                targeted.openingHealth(), targeted.enemyDamage() + amount, targeted.overheated());
    }

    void settleTargeted(AugmentSnapshot snapshot, int round, boolean survived) {
        if (targeted.round() != round || targeted.settledRound() >= round) return;
        int mastery = targeted.mastery();
        if (snapshot.has("battlefield_mastery") && survived && targeted.openingHealth() > 0
                && targeted.enemyDamage() + 1e-9 >= targeted.openingHealth()
                * snapshot.parameter("battlefield_mastery", "damageThreshold", .40)) {
            mastery = Math.min((int) snapshot.parameter("battlefield_mastery", "maxStacks", 4), mastery + 1);
        }
        int heat = targeted.overheated()
                ? Math.min((int) snapshot.parameter("overheat_core", "maxStacks", 5), targeted.heat() + 1) : targeted.heat();
        targeted = new TargetedProgress(heat, mastery, round, round, targeted.openingHealth(), targeted.enemyDamage(), false);
    }

    double maxHealthBonus(AugmentSnapshot snapshot) {
        return masteryBonus(snapshot) + (snapshot.has("one_man_show")
                ? snapshot.parameter("one_man_show", "maxHealthBonus", .30) : 0);
    }

    private double masteryBonus(AugmentSnapshot snapshot) {
        return snapshot.has("battlefield_mastery")
                ? targeted.mastery() * snapshot.parameter("battlefield_mastery", "bonusPerStack", .04) : 0;
    }

    private static double tacticalBonus(AugmentSnapshot snapshot, String mode) {
        double bonus = 0;
        for (int tier = 1; tier <= 3; tier++) {
            String id = "tactical_designation_" + tier;
            if (snapshot.has(id) && mode.equals(snapshot.choice(id).mode())) {
                bonus += snapshot.parameter(id, mode.equals("ASSAULT") ? "damageBonus" : "damageReduction", 0);
            }
        }
        return bonus;
    }

    double damageReduction(AugmentSnapshot snapshot) {
        return Math.min(.60, tacticalBonus(snapshot, "COVER"));
    }

    record Hit(UUID target, double amount, DamageType type) {}
    record Spell(DemonLordSkillTower altar, long tick, List<Hit> hits) {}

    void reset() {
        recent.clear();
        currentHits = null;
        currentAltar = null;
        finisherUntil = phaseUntil = comboUntil = comboReady = thronesReady = Long.MIN_VALUE;
        phaseUsed = false;
        replaying = false;
        cooldownsDirty = false;
        clearVisuals();
    }

    boolean activatePhase(DemonLordState state, AugmentSnapshot snapshot, long now) {
        if (!AugmentCombat.allowsTriggers() || phaseUsed || !snapshot.has(PHASE)
                || state.health() > state.maxHealth() * snapshot.parameter(PHASE, "healthThreshold", 0.3)) {
            return false;
        }
        phaseUsed = true;
        state.heal(state.maxHealth() * snapshot.parameter(PHASE, "healRatio", 0.5));
        state.resetSkillCooldowns();
        cooldownsDirty = true;
        phaseUntil = now + (int) snapshot.parameter(PHASE, "durationTicks", 200);
        return true;
    }

    double damageMultiplier(AugmentSnapshot snapshot, long now) {
        double bonus = tacticalBonus(snapshot, "ASSAULT") + masteryBonus(snapshot)
                - targeted.heat() * snapshot.parameter("overheat_core", "penaltyPerStack", .06);
        if (targeted.overheated()) bonus += snapshot.parameter("overheat_core", "damageBonus", .40);
        if (snapshot.has("one_man_show")) bonus += snapshot.parameter("one_man_show", "damageBonus", 1.0);
        double multiplier = Math.max(0, 1.0 + bonus)
                * (now < phaseUntil ? 1.0 + snapshot.parameter(PHASE, "damageBonus", 0.8) : 1.0);
        if ((currentAltar != null || replaying) && now < comboUntil) {
            multiplier *= 1.0 + snapshot.parameter(COMBO, "damageBonus", 0.6);
        }
        return multiplier;
    }

    double consumeFinisher(AugmentSnapshot snapshot, long now) {
        if (!AugmentCombat.allowsTriggers() || !snapshot.has(SILVER) || now > finisherUntil) {return 0.0;}
        finisherUntil = Long.MIN_VALUE;
        return snapshot.parameter(SILVER, "damageRatio", 2.0);
    }

    void beginSpell(DemonLordSkillTower altar) {
        currentAltar = altar;
        currentHits = new ArrayList<>();
    }

    void recordHit(SemionMonsterEntity target, double amount, DamageType type) {
        if (AugmentCombat.allowsTriggers() && currentAltar != null && currentHits != null) {
            currentHits.add(new Hit(target.getUUID(), amount, type));
        }
    }

    void finishSpell(ServerPlayer player, PlayerLane lane, DemonLordState state, long now) {
        DemonLordSkillTower altar = currentAltar;
        List<Hit> hits = currentHits;
        currentAltar = null;
        currentHits = null;
        if (altar == null || hits == null || hits.isEmpty() || !AugmentCombat.allowsTriggers()) {return;}
        AugmentSnapshot snapshot = lane.augmentSnapshot();
        List<Spell> echoes = recordSpell(state, snapshot, new Spell(altar, now, List.copyOf(hits)), now);
        for (int index = 0; index < echoes.size(); index++) {
            Spell spell = echoes.get(index);
            showEcho(player, lane, index == 0 ? -1.0 : 1.0, now);
            DemonLordVfx.show(spell.altar(), lane, player.position(), 2.0,
                    DemonLordVfx.styleFor(spell.altar().skill()));
            double ratio = snapshot.parameter(THRONES, "damageRatio", 1.5);
            replaying = true;
            try {
                AugmentCombat.runWithoutTriggers(() -> {
                    for (Hit hit : spell.hits()) {
                        if (lane.arenaWorld().getEntity(hit.target()) instanceof SemionMonsterEntity target
                                && target.isAlive()) {
                            DemonLordService.dealDamage(player, lane, spell.altar(), target, hit.amount() * ratio, hit.type());
                        }
                    }
                });
            } finally {
                replaying = false;
            }
        }
        if (consumeCooldownChanges()) {DemonLordService.syncSkillCooldowns(player, state, now);}
    }

    List<Spell> recordSpell(DemonLordState state, AugmentSnapshot snapshot, Spell spell, long now) {
        if (!AugmentCombat.allowsTriggers() || spell.altar().skill() == DemonLordSkill.DEMON_BARRIER
                || spell.hits().isEmpty()) {return List.of();}
        recent.put(spell.altar().skill(), spell);
        if (snapshot.has(SILVER)) {
            finisherUntil = now + (int) snapshot.parameter(SILVER, "windowTicks", 40);
        }
        List<Spell> combo = recent.values().stream()
                .filter(value -> now - value.tick() <= snapshot.parameter(COMBO, "windowTicks", 160)).toList();
        if (snapshot.has(COMBO) && now >= comboReady
                && combo.size() >= snapshot.parameter(COMBO, "distinctSkills", 3)) {
            state.reduceAttackSkillCooldowns((int) snapshot.parameter(COMBO, "cooldownReductionTicks", 80));
            cooldownsDirty = true;
            comboUntil = now + (int) snapshot.parameter(COMBO, "durationTicks", 120);
            comboReady = now + (int) snapshot.parameter(COMBO, "cooldownTicks", 160);
        }
        List<Spell> echoes = recent.values().stream()
                .filter(value -> now - value.tick() <= snapshot.parameter(THRONES, "windowTicks", 160))
                .sorted(java.util.Comparator.comparingLong(Spell::tick).reversed()).limit(2).toList();
        if (!snapshot.has(THRONES) || now < thronesReady || echoes.size() < 2) {return List.of();}
        thronesReady = now + (int) snapshot.parameter(THRONES, "cooldownTicks", 200);
        return echoes;
    }

    private void showEcho(ServerPlayer player, PlayerLane lane, double side, long now) {
        ArmorStand visual = new ArmorStand(lane.arenaWorld(), player.getX() + side, player.getY(), player.getZ());
        visual.setInvisible(true);
        visual.setInvulnerable(true);
        visual.setNoGravity(true);
        visual.setSilent(true);
        visual.setShowArms(true);
        visual.setNoBasePlate(true);
        visual.setYRot(player.getYRot());
        visual.addTag(SemionEntityTypes.RUNTIME_NO_SAVE_TAG);
        visual.getEntityData().set(ArmorStand.DATA_CLIENT_FLAGS,
                (byte) (visual.getEntityData().get(ArmorStand.DATA_CLIENT_FLAGS) | ArmorStand.CLIENT_FLAG_MARKER));
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
            visual.setItemSlot(slot, player.getItemBySlot(slot).copy());
        }
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponents.PROFILE, new ResolvableProfile(player.getGameProfile()));
        visual.setItemSlot(EquipmentSlot.HEAD, head);
        if (lane.arenaWorld().addFreshEntity(visual)) {visuals.add(visual);}
        visualsUntil = now + 10;
    }

    void tickVisuals(long now) {
        if (now >= visualsUntil) {clearVisuals();}
    }

    boolean consumeCooldownChanges() {
        boolean dirty = cooldownsDirty;
        cooldownsDirty = false;
        return dirty;
    }

    void clearVisuals() {
        visuals.forEach(ArmorStand::discard);
        visuals.clear();
    }
}
