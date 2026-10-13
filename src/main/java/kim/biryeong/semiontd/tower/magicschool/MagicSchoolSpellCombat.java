package kim.biryeong.semiontd.tower.magicschool;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.*;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.entity.tower.vfx.MagicSchoolSpellVfx;
import kim.biryeong.semiontd.tower.TowerDataKey;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.resources.Identifier;

public final class MagicSchoolSpellCombat {
    private static final Identifier SELF_PROTECTION = id("protego_self");
    private static final TowerDataKey<Long> EPISKEY_READY = TowerDataKey.of(id("episkey_ready"), Long.class);

    private MagicSchoolSpellCombat() {}
    static Identifier id(String name) { return Identifier.fromNamespaceAndPath(SemionTd.MOD_ID, "magic_school_" + name); }

    public static double maximumHealth(SemionMonsterEntity target) {
        return target.runtimeMonster() == null ? target.getMaxHealth() : target.runtimeMonster().maxHealth();
    }

    public static int targetPriority(MagicSchoolWizardTower wizard, SemionMonsterEntity target) {
        if (target.schoolSpells().controlled()) return 2;
        boolean lit = target.activeTimedEffectMagnitude(TimedEffectType.MONSTER_LUMOS) > 0;
        return wizard.selectedSpell().spreadsLumos() ? (lit ? 1 : 0) : (lit ? 0 : 1);
    }

    public static double protection(SemionTowerEntity target) {
        double aura = target.activeMultiplicativeEffectMagnitude(TimedEffectType.TOWER_PROTEGO_MAXIMA_AURA,
                MagicSchoolSpell.PROTEGO_MAXIMA.ticks("maxAuraStacks"));
        double self = target.activeMultiplicativeEffectMagnitude(TimedEffectType.TOWER_PROTEGO, 1);
        return 1.0 - (1.0 - self) * (1.0 - aura);
    }

    public static void applySelfProtection(MagicSchoolWizardTower wizard, SemionTowerEntity source) {
        var spell = wizard.selectedSpell();
        if (spell != MagicSchoolSpell.PROTEGO && spell != MagicSchoolSpell.PROTEGO_MAXIMA) return;
        source.setPersistentEffect(TimedEffectType.TOWER_PROTEGO, SELF_PROTECTION,
                Math.max(source.activeEffectMagnitude(TimedEffectType.TOWER_PROTEGO), spell.value("damageReduction")));
    }

    public static void onWaveStarted(MagicSchoolWizardTower wizard, SemionTowerEntity source) {
        applySelfProtection(wizard, source);
        if (wizard.selectedSpell() == MagicSchoolSpell.PROTEGO_MAXIMA) {
            Identifier contribution = id("protego_" + wizard.logicalId());
            var spell = MagicSchoolSpell.PROTEGO_MAXIMA;
            SemionTdApi.areaEffects().applyToTowers(allies(source, spell), target -> {
                target.entity().orElseThrow().setPersistentEffect(TimedEffectType.TOWER_PROTEGO_MAXIMA_AURA,
                        contribution, spell.value("auraReduction"));
                return AreaEffectOutcome.APPLIED;
            });
        }
        if (wizard.selectedSpell() == MagicSchoolSpell.RENNERVATE) rennervate(source);
    }

    public static void clearRound(SemionTowerEntity entity) {
        entity.removeTimedEffect(TimedEffectType.TOWER_PROTEGO);
        entity.removeTimedEffect(TimedEffectType.TOWER_PROTEGO_MAXIMA_AURA);
        entity.removeTimedEffect(TimedEffectType.TOWER_RENNERVATE_DAMAGE_BONUS);
        if (entity.runtimeTower() != null) entity.runtimeTower().setData(EPISKEY_READY, 0L);
    }

    private static TowerAreaEffectRequest allies(SemionTowerEntity source, MagicSchoolSpell spell) {
        return new TowerAreaEffectRequest(id(spell.id()), source, source.position(), spell.value("radius"),
                TowerAreaTargetMode.ENTITIES, true, null, AreaVfxSpec.onChange(AreaVfxStyles.BUFF)).forAlliedTeam();
    }

    public static void rennervate(SemionTowerEntity source) {
        var spell = MagicSchoolSpell.RENNERVATE;
        SemionTdApi.areaEffects().applyToTowers(allies(source, spell), target -> {
            var entity = target.entity().orElseThrow();
            entity.cleanseDebuffs();
            entity.applyTimedEffect(TimedEffectType.TOWER_RENNERVATE_DAMAGE_BONUS, spell.value("damageBonus"), spell.ticks("buffTicks"));
            return AreaEffectOutcome.APPLIED;
        });
    }

    public static void leviosa(SemionTowerEntity source) {
        if (!(source.runtimeTower() instanceof MagicSchoolWizardTower wizard)) return;
        var spell = MagicSchoolSpell.WINGARDIUM_LEVIOSA;
        var request = MonsterAreaEffectRequest.aroundTower(id(spell.id()), source, spell.value("radius"),
                AreaVfxSpec.onTrigger(AreaVfxStyles.DEBUFF))
                .withFilter(target -> target.runtimeMonster().targetTeam() == source.teamId());
        TowerAreaDamage.applyResolved(wizard, source, request,
                target -> wizard.resolveBasicAttackOutgoingDamage(source, target, source.attackDamageAmount(target))
                        * spell.value("liftDamageMultiplier"), true, (target, dealt, killed) -> {
            if (killed) return;
            target.setDeltaMovement(0, Math.max(target.getDeltaMovement().y, spell.value("liftPower")), 0);
            target.syncVelocity = true;
            target.getNavigation().stop();
            target.applyTimedEffect(TimedEffectType.MONSTER_STUN, 1, spell.ticks("stunTicks"));
        }, DamageType.MAGIC);
    }

    public static void onHit(MagicSchoolWizardTower wizard, SemionTowerEntity source, SemionMonsterEntity target,
            boolean firstAttack, int attackCount) {
        var spell = wizard.selectedSpell();
        switch (spell) {
            case EXPELLIARMUS -> {
                if (firstAttack) target.applyTimedEffect(TimedEffectType.MONSTER_DISARM, 1, spell.ticks("disarmTicks"));
            }
            case STUPEFY -> target.schoolSpells().stupefy();
            case EXPULSO, BOMBARDA -> secondary(wizard, source, target, spell, Integer.MAX_VALUE, false);
            case EPISKEY -> heal(source);
            case SECTUMSEMPRA -> {
                wound(source, target);
                secondary(wizard, source, target, spell, 1, true);
            }
            case EXPECTO_PATRONUM -> {
                int count = 1 + attackCount / spell.ticks("attacksPerExtraTarget");
                int hits = secondary(wizard, source, target, spell, count, false);
                for (int i = hits; i < count && target.isAlive() && !target.isRemoved(); i++) {
                    var result = wizard.damageResolvedTargetResult(source, target, secondaryDamage(wizard, source, target, spell), DamageType.MAGIC);
                    if (result.killed()) wizard.onKill(source, target, result.dealtDamage());
                }
            }
            case LUMOS, LUMOS_MAXIMA -> {
                target.schoolSpells().lumos();
                SemionTdApi.areaEffects().applyToMonsters(around(source, target, spell, Integer.MAX_VALUE), other -> {
                    other.schoolSpells().lumos();
                    return AreaEffectOutcome.APPLIED;
                });
            }
            case CRUCIO -> target.schoolSpells().crucio(wizard, source);
            case IMPERIO -> {
                if (target.isAlive()) target.schoolSpells().imperio(wizard, source);
            }
            default -> {}
        }
    }

    private static MonsterAreaEffectRequest around(SemionTowerEntity source, SemionMonsterEntity target, MagicSchoolSpell spell, int limit) {
        return MonsterAreaEffectRequest.aroundTarget(id(spell.id()), source, target, spell.value("radius"),
                AreaVfxSpec.onTrigger(AreaVfxStyles.SPLASH)).nearestTargets(limit);
    }

    private static double secondaryDamage(MagicSchoolWizardTower wizard, SemionTowerEntity source,
            SemionMonsterEntity target, MagicSchoolSpell spell) {
        return wizard.resolveBasicAttackOutgoingDamage(source, target, source.attackDamageAmount(target)) * spell.value("secondaryMultiplier");
    }

    private static int secondary(MagicSchoolWizardTower wizard, SemionTowerEntity source, SemionMonsterEntity target,
            MagicSchoolSpell spell, int limit, boolean wounds) {
        var result = TowerAreaDamage.applyResolved(wizard, source, around(source, target, spell, limit),
                other -> secondaryDamage(wizard, source, other, spell), true,
                (other, dealt, killed) -> { if (wounds && dealt > 0) wound(source, other); }, DamageType.MAGIC);
        return result.candidateCount();
    }

    private static void wound(SemionTowerEntity source, SemionMonsterEntity target) {
        var spell = MagicSchoolSpell.SECTUMSEMPRA;
        TowerVfxService.showMagicSchoolVisual(source, spell, MagicSchoolSpellVfx.Kind.WOUND,
                target.position().add(0, target.getBbHeight() * .55, 0), 0);
        target.applyTimedEffect(TimedEffectType.MONSTER_HEAL_REDUCTION, spell.value("healingReduction"), spell.ticks("healingReductionTicks"));
    }

    static void transfer(MagicSchoolWizardTower wizard, SemionTowerEntity source, SemionMonsterEntity target, double dealt) {
        var lane = wizard.attachedLane();
        if (lane == null) return;

        var box = lane.laneLayout().defenseSearchBox(target.position(), source.attackRange(), 8);
        double radius = Math.sqrt(Math.pow(Math.max(Math.abs(box.minX - target.getX()), Math.abs(box.maxX - target.getX())), 2)
                + Math.pow(Math.max(Math.abs(box.minY - target.getY()), Math.abs(box.maxY - target.getY())), 2)
                + Math.pow(Math.max(Math.abs(box.minZ - target.getZ()), Math.abs(box.maxZ - target.getZ())), 2));
        var request = MonsterAreaEffectRequest.aroundTarget(id("spell_transfer"), source, target, radius, AreaVfxSpec.none())
                .withFilter(other -> other.runtimeMonster().targetTeam() == source.teamId()
                        && other.activeTimedEffectMagnitude(TimedEffectType.MONSTER_LUMOS) > 0)
                .nearestTargets(MagicSchoolCurriculum.integer("spellTransferMaxTargets", 5));
        TowerAreaDamage.applyResolved(wizard, source, request,
                other -> dealt * MagicSchoolCurriculum.value("spellTransferDamageRatio", .10), true,
                (other, damage, killed) -> {}, DamageType.MAGIC);
    }

    private static void heal(SemionTowerEntity source) {
        var spell = MagicSchoolSpell.EPISKEY;
        long now = source.level().getGameTime();
        List<SemionTowerEntity> candidates = new ArrayList<>();
        SemionTdApi.areaEffects().applyToTowers(allies(source, spell).withFilter(target ->
                target.tower() instanceof MagicSchoolWizardTower && target.entity().isPresent()
                        && target.entity().get().canReceiveHealing()
                        && target.tower().getDataOrDefault(EPISKEY_READY, 0L) <= now), target -> {
            candidates.add(target.entity().orElseThrow());
            return AreaEffectOutcome.UNCHANGED;
        });
        candidates.stream().min(Comparator.comparingDouble((SemionTowerEntity candidate) ->
                candidate.runtimeTower().health() / candidate.runtimeTower().currentMaxHealth())
                .thenComparingInt(SemionTowerEntity::getId)).ifPresent(recipient -> {
            if (source.healTarget(recipient, source.attackDamageAmount(null) * spell.value("healingMultiplier"))) {
                recipient.runtimeTower().setData(EPISKEY_READY, now + spell.ticks("recipientCooldownTicks"));
                source.playHealingAnimation();
                TowerVfxService.showMagicSchoolVisual(source, spell, MagicSchoolSpellVfx.Kind.HEAL,
                        recipient.position().add(0, recipient.getBbHeight() + .3, 0), 0);
            }
        });
    }
}
