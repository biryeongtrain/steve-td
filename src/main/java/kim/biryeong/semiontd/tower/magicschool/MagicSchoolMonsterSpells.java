package kim.biryeong.semiontd.tower.magicschool;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.entity.tower.vfx.MagicSchoolSpellVfx;

public final class MagicSchoolMonsterSpells {
    private final SemionMonsterEntity target;
    private int stunHits;
    private int vulnerabilityStacks;
    private boolean addedGlow;
    private final Map<MagicSchoolWizardTower, Crucio> curses = new IdentityHashMap<>();
    private MagicSchoolWizardTower controller;
    private SemionTowerEntity controlSource;
    private long nextCurseVisualTick;
    private long nextControlVisualTick;

    public MagicSchoolMonsterSpells(SemionMonsterEntity target) { this.target = target; }

    public void stupefy() {
        if (++stunHits >= MagicSchoolSpell.STUPEFY.ticks("hitsToStun")) {
            stunHits = 0;
            target.applyTimedEffect(TimedEffectType.MONSTER_STUN, 1, MagicSchoolSpell.STUPEFY.ticks("stunTicks"));
        }
    }

    public void lumos() {
        addedGlow |= !target.isCurrentlyGlowing();
        target.setPersistentEffect(TimedEffectType.MONSTER_LUMOS, MagicSchoolSpellCombat.id("lumos"), MagicSchoolSpell.LUMOS.value("magicVulnerability"));
        target.setGlowingTag(true);
    }

    private void addVulnerability() {
        var spell = MagicSchoolSpell.CRUCIO;
        vulnerabilityStacks = Math.min(spell.ticks("maxStacks"), vulnerabilityStacks + 1);
        target.setPersistentEffect(TimedEffectType.MONSTER_CRUCIO_VULNERABILITY, MagicSchoolSpellCombat.id("crucio"),
                vulnerabilityStacks * spell.value("vulnerabilityPerStack"));
    }

    public void crucio(MagicSchoolWizardTower wizard, SemionTowerEntity source) {
        if (!target.isAlive() || target.isRemoved()) return;
        addVulnerability();
        var spell = MagicSchoolSpell.CRUCIO;
        var previous = curses.get(wizard);
        double damage = wizard.resolveBasicAttackOutgoingDamage(source, target, source.attackDamageAmount(target)) * spell.value("dotMultiplier");
        curses.put(wizard, new Crucio(source, damage, spell.ticks("dotTicks"),
                previous == null ? spell.ticks("dotIntervalTicks") : previous.untilHit));
    }

    public void tick() {
        if (!target.isAlive() || target.isRemoved()) { curses.clear(); return; }
        long now = target.level().getGameTime();
        if (!curses.isEmpty() && now >= nextCurseVisualTick) {
            TowerVfxService.showMagicSchoolVisual(curses.values().iterator().next().source,
                    MagicSchoolSpell.CRUCIO, MagicSchoolSpellVfx.Kind.DOT,
                    target.position().add(0, target.getBbHeight() * .55, 0), 0);
            nextCurseVisualTick = now + 5;
        }
        if (controlled() && now >= nextControlVisualTick) {
            TowerVfxService.showMagicSchoolVisual(controlSource, MagicSchoolSpell.IMPERIO,
                    MagicSchoolSpellVfx.Kind.CONTROL, target.position().add(0, target.getBbHeight() + .15, 0), 0);
            nextControlVisualTick = now + 5;
        }
        var iterator = curses.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var curse = entry.getValue();
            curse.remaining--;
            if (--curse.untilHit <= 0) {
                var result = entry.getKey().damageResolvedTargetResult(curse.source, target, curse.damage, DamageType.MAGIC);
                if (result.dealtDamage() > 0) addVulnerability();
                if (result.killed()) entry.getKey().onKill(curse.source, target, result.dealtDamage());
                curse.untilHit = MagicSchoolSpell.CRUCIO.ticks("dotIntervalTicks");
            }
            if (curse.remaining <= 0 || !target.isAlive() || target.isRemoved()) iterator.remove();
        }
        if (!controlled()) { controller = null; controlSource = null; }
    }

    public void clearRound() {
        stunHits = 0;
        vulnerabilityStacks = 0;
        nextCurseVisualTick = 0;
        nextControlVisualTick = 0;
        curses.clear();
        controller = null;
        controlSource = null;
        for (var effect : java.util.List.of(TimedEffectType.MONSTER_LUMOS, TimedEffectType.MONSTER_CRUCIO_VULNERABILITY,
                TimedEffectType.MONSTER_IMPERIO, TimedEffectType.MONSTER_DISARM, TimedEffectType.MONSTER_HEAL_REDUCTION)) {
            target.removeTimedEffect(effect);
        }
        if (addedGlow) target.setGlowingTag(false);
        addedGlow = false;
    }

    public void imperio(MagicSchoolWizardTower wizard, SemionTowerEntity source) {
        controller = wizard;
        controlSource = source;
        target.applyTimedEffect(TimedEffectType.MONSTER_IMPERIO, 1, MagicSchoolSpell.IMPERIO.ticks("controlTicks"));
    }

    public boolean controlled() {
        return controller != null && controlSource != null && target.activeTimedEffectMagnitude(TimedEffectType.MONSTER_IMPERIO) > 0;
    }

    public SemionMonsterEntity controlTarget() {
        if (!controlled()) return null;
        SemionMonsterEntity[] found = {null};
        var request = new MonsterAreaEffectRequest(MagicSchoolSpellCombat.id("imperio_target"), controlSource,
                target.position(), target.defenseTargetSearchRange(), Set.of(target.getUUID()), null, AreaVfxSpec.none()).nearestTargets(1);
        SemionTdApi.areaEffects().applyToMonsters(request, other -> {
            found[0] = other;
            return AreaEffectOutcome.UNCHANGED;
        });
        return found[0] == null ? target : found[0];
    }

    public void attackControlled(SemionMonsterEntity victim) {
        if (!controlled() || victim == null) return;
        var result = controller.damageResolvedTargetResult(controlSource, victim, target.attackDamageAmount(), DamageType.PHYSICAL);
        if (result.killed()) controller.onKill(controlSource, victim, result.dealtDamage());
    }

    private static final class Crucio {
        final SemionTowerEntity source;
        final double damage;
        int remaining;
        int untilHit;
        Crucio(SemionTowerEntity source, double damage, int remaining, int untilHit) {
            this.source = source;
            this.damage = damage;
            this.remaining = remaining;
            this.untilHit = untilHit;
        }
    }
}
