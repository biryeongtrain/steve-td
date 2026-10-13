package kim.biryeong.semiontd.tower.hero;

import static kim.biryeong.semiontd.tower.hero.HeroCompanionAbilityDefaults.*;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.api.SemionTdApi;
import kim.biryeong.semiontd.api.area.AreaEffectOutcome;
import kim.biryeong.semiontd.api.area.AreaTowerTarget;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.TowerAreaEffectRequest;
import kim.biryeong.semiontd.api.area.TowerAreaTargetMode;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.vfx.TowerVfxService;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.area.AreaEffectIds;
import net.minecraft.resources.Identifier;

final class HeroCompanionSupportController {
    private static final Identifier KNIGHT_GUARD = Identifier.fromNamespaceAndPath("semion-td", "hero_party_knight_guard");
    private static final Identifier BARD_AURA = Identifier.fromNamespaceAndPath("semion-td", "hero_party_bard_aura");
    private static final Identifier BARD_ENCORE = Identifier.fromNamespaceAndPath("semion-td", "hero_party_bard_encore");
    private final HeroCompanionTower companion;
    private int supportCooldown;
    private int supportPulseCount;

    HeroCompanionSupportController(HeroCompanionTower companion) {
        this.companion = companion;
    }

    void resetRound() {
        supportPulseCount = 0;
    }

    void copyFrom(HeroCompanionSupportController source) {
        supportCooldown = source.supportCooldown;
        supportPulseCount = source.supportPulseCount;
    }

    int cooldownTicks() {
        return supportCooldown;
    }

    int pulseCount() {
        return supportPulseCount;
    }

    void tick(PlayerLane lane) {
        HeroCompanionRole role = companion.role().orElse(null);
        if (role != HeroCompanionRole.KNIGHT
                && role != HeroCompanionRole.PRIEST
                && role != HeroCompanionRole.BARD) {
            return;
        }
        if (supportCooldown > 0) {
            supportCooldown--;
            return;
        }
        if (role == HeroCompanionRole.KNIGHT) {
            applyKnightGuard(lane);
            supportCooldown = 20;
        } else if (role == HeroCompanionRole.PRIEST) {
            healParty(lane);
            supportCooldown = Math.max(1, HeroPartyBalance.towerInt(companion.type().id(), "healIntervalTicks", PRIEST_INTERVAL[companion.index()]));
        } else {
            applyBardAura(lane);
            supportPulseCount++;
            int encoreEvery = HeroPartyBalance.towerInt(companion.type().id(), "encoreEveryPulses", BARD_ENCORE_EVERY[companion.index()]);
            if (encoreEvery > 0 && supportPulseCount % encoreEvery == 0) {
                applyBardEncore(lane);
            }
            supportCooldown = 20;
        }
    }

    private void healParty(PlayerLane lane) {
        List<Tower> wounded = lowestWoundedTargets(lane.towers(), companion.ownerPlayer());
        if (wounded.isEmpty()) {
            return;
        }
        double heal = companion.value("healAmount", PRIEST_HEAL[companion.index()]);
        double reduction = companion.value("healGuardReduction", PRIEST_GUARD[companion.index()]);
        int reductionTicks = HeroPartyBalance.towerInt(
                companion.type().id(), "healGuardDurationTicks", PRIEST_GUARD_TICKS[companion.index()]
        );
        recordPriestHealing(lane, healTower(lane, wounded.get(0), heal, reduction, reductionTicks));
        double secondRatio = companion.value("secondTargetRatio", PRIEST_SECOND[companion.index()]);
        if (wounded.size() > 1 && secondRatio > 0.0) {
            recordPriestHealing(lane, healTower(
                    lane, wounded.get(1), heal * secondRatio, reduction, reductionTicks
            ));
        }
    }

    static List<Tower> lowestWoundedTargets(List<Tower> towers, UUID owner) {
        Tower first = null;
        Tower second = null;
        double firstRatio = 0.0;
        double secondRatio = 0.0;
        for (Tower tower : towers) {
            if (!tower.ownerPlayer().equals(owner) || !HeroPartyTowers.isHeroPartyTower(tower.type())
                    || !(tower.health() > 0.0 && tower.health() < tower.currentMaxHealth())) {
                continue;
            }
            double ratio = tower.health() / Math.max(1.0, tower.currentMaxHealth());
            if (first == null || Double.compare(ratio, firstRatio) < 0) {
                second = first;
                secondRatio = firstRatio;
                first = tower;
                firstRatio = ratio;
            } else if (second == null || Double.compare(ratio, secondRatio) < 0) {
                second = tower;
                secondRatio = ratio;
            }
        }
        return first == null ? List.of() : second == null ? List.of(first) : List.of(first, second);
    }

    private double healTower(PlayerLane lane, Tower target, double amount, double reduction, int reductionTicks) {
        SemionTowerEntity entity = companion.towerEntity(lane, target);
        if (entity != null) {
            double healed = companion.healPartyMember(entity, amount);
            if (healed > 0.0 && reduction > 0.0 && reductionTicks > 0) {
                entity.applyTimedEffect(TimedEffectType.TOWER_DAMAGE_REDUCTION, reduction, reductionTicks);
                SemionTowerEntity source = companion.towerEntity(lane, companion);
                if (source != null) {
                    TowerVfxService.showAreaEffect(
                            source,
                            AreaEffectIds.tower(companion, "priest_guard"),
                            AreaVfxStyles.BUFF,
                            entity.position(),
                            0.8,
                            List.of(entity.position()),
                            1,
                            1,
                            0
                    );
                }
            }
            return healed;
        }
        double previous = target.health();
        target.syncHealth(Math.min(target.currentMaxHealth(), target.health()
                + amount * HeroPartyBalance.partyHealingMultiplier(companion.state().adventurePoints())));
        return Math.max(0.0, target.health() - previous);
    }

    private void recordPriestHealing(PlayerLane lane, double healed) {
        SemionTowerEntity source = companion.towerEntity(lane, companion);
        companion.state().recordSpecial(HeroQuestKind.PRIEST_HEALING, null, healed, companion.onlineOwner(source));
    }

    private void applyKnightGuard(PlayerLane lane) {
        double radius = companion.value("guardRadius", KNIGHT_GUARD_RADIUS[companion.index()]);
        double reduction = companion.value("guardDamageReduction", KNIGHT_GUARD_REDUCTION[companion.index()]);
        int ticks = HeroPartyBalance.towerInt(companion.type().id(), "guardDurationTicks", KNIGHT_GUARD_TICKS[companion.index()]);
        SemionTowerEntity source = companion.towerEntity(lane, companion);
        if (source == null || radius <= 0.0 || reduction <= 0.0 || ticks <= 0) {
            return;
        }
        TowerAreaEffectRequest request = TowerAreaEffectRequest.aroundTower(
                AreaEffectIds.tower(companion, "guard_formation"),
                source,
                radius,
                TowerAreaTargetMode.REGISTERED,
                AreaVfxSpec.onChange(AreaVfxStyles.BUFF)
        ).withFilter(target -> target.tower().ownerPlayer().equals(companion.ownerPlayer())
                && HeroPartyTowers.isHeroPartyTower(target.tower().type()));
        SemionTdApi.areaEffects().applyToTowers(request, target -> {
            HeroCompanionTower provider = strongestProvider(lane, target.tower(), HeroCompanionRole.KNIGHT, false);
            if (provider != companion) {
                return AreaEffectOutcome.UNCHANGED;
            }
            SemionTowerEntity entity = target.entity().orElse(null);
            if (entity == null) {
                return AreaEffectOutcome.UNCHANGED;
            }
            return entity.refreshTimedEffect(TimedEffectType.TOWER_DAMAGE_REDUCTION, KNIGHT_GUARD, reduction, ticks)
                    ? AreaEffectOutcome.APPLIED
                    : AreaEffectOutcome.UNCHANGED;
        });
    }

    private void applyBardAura(PlayerLane lane) {
        double radius = companion.value("auraRadius", BARD_RADIUS[companion.index()]);
        SemionTowerEntity source = companion.towerEntity(lane, companion);
        if (source == null || radius <= 0.0) {
            return;
        }
        TowerAreaEffectRequest request = new TowerAreaEffectRequest(
                AreaEffectIds.tower(companion, "battle_song"),
                source,
                source.position(),
                radius,
                TowerAreaTargetMode.REGISTERED,
                true,
                target -> target.tower().ownerPlayer().equals(companion.ownerPlayer())
                        && HeroPartyTowers.isHeroPartyTower(target.tower().type()),
                AreaVfxSpec.onChange(AreaVfxStyles.BUFF)
        );
        SemionTdApi.areaEffects().applyToTowers(request, target -> applyStrongestBardAura(lane, target));
    }

    private AreaEffectOutcome applyStrongestBardAura(PlayerLane lane, AreaTowerTarget target) {
        HeroCompanionTower provider = strongestProvider(lane, target.tower(), HeroCompanionRole.BARD, true);
        SemionTowerEntity entity = target.entity().orElse(null);
        if (provider == null || entity == null || provider != companion) {
            return AreaEffectOutcome.UNCHANGED;
        }
        int providerIndex = provider.index();
        double speed = provider.value("attackSpeedBonus", BARD_SPEED[providerIndex]);
        double damage = provider.value("damageBonus", BARD_DAMAGE[providerIndex]);
        boolean changed = entity.refreshTimedEffect(
                TimedEffectType.TOWER_ATTACK_SPEED_BONUS, BARD_AURA, speed, 40
        );
        if (damage > 0.0) {
            changed |= entity.refreshTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS, BARD_AURA, damage, 40);
        }
        companion.state().recordSpecial(HeroQuestKind.BARD_AURA_SUPPORT, null, 1.0, companion.onlineOwner(entity));
        return changed ? AreaEffectOutcome.APPLIED : AreaEffectOutcome.UNCHANGED;
    }

    private void applyBardEncore(PlayerLane lane) {
        double radius = companion.value("auraRadius", BARD_RADIUS[companion.index()]);
        double damage = companion.value("encoreDamageBonus", BARD_ENCORE_BONUS[companion.index()]);
        double speed = companion.value("encoreAttackSpeedBonus", BARD_ENCORE_BONUS[companion.index()]);
        int ticks = HeroPartyBalance.towerInt(companion.type().id(), "encoreDurationTicks", BARD_ENCORE_TICKS[companion.index()]);
        SemionTowerEntity source = companion.towerEntity(lane, companion);
        if (source == null || radius <= 0.0 || damage <= 0.0 || speed <= 0.0 || ticks <= 0) {
            return;
        }
        TowerAreaEffectRequest request = new TowerAreaEffectRequest(
                AreaEffectIds.tower(companion, "encore"),
                source,
                source.position(),
                radius,
                TowerAreaTargetMode.REGISTERED,
                true,
                target -> target.tower().ownerPlayer().equals(companion.ownerPlayer())
                        && HeroPartyTowers.isHeroPartyTower(target.tower().type()),
                AreaVfxSpec.onTrigger(AreaVfxStyles.PULSE)
        );
        SemionTdApi.areaEffects().applyToTowers(request, target -> {
            HeroCompanionTower provider = strongestProvider(lane, target.tower(), HeroCompanionRole.BARD, true);
            SemionTowerEntity entity = target.entity().orElse(null);
            if (provider != companion || entity == null) {
                return AreaEffectOutcome.UNCHANGED;
            }
            boolean changed = entity.refreshTimedEffect(
                    TimedEffectType.TOWER_ATTACK_SPEED_BONUS, BARD_ENCORE, speed, ticks
            );
            changed |= entity.refreshTimedEffect(TimedEffectType.TOWER_DAMAGE_BONUS, BARD_ENCORE, damage, ticks);
            return changed ? AreaEffectOutcome.APPLIED : AreaEffectOutcome.UNCHANGED;
        });
    }

    private HeroCompanionTower strongestProvider(
            PlayerLane lane,
            Tower target,
            HeroCompanionRole providerRole,
            boolean includeSelf
    ) {
        return lane.towers().stream()
                .filter(HeroCompanionTower.class::isInstance)
                .map(HeroCompanionTower.class::cast)
                .filter(provider -> provider.ownerPlayer().equals(companion.ownerPlayer()))
                .filter(provider -> provider.role().orElse(null) == providerRole)
                .filter(provider -> includeSelf || provider != target)
                .filter(provider -> covers(provider, target, providerRole))
                .max(Comparator.comparingInt(HeroCompanionTower::tier))
                .orElse(null);
    }

    private static boolean covers(HeroCompanionTower companion, Tower target, HeroCompanionRole providerRole) {
        double radius = providerRole == HeroCompanionRole.KNIGHT
                ? companion.value("guardRadius", KNIGHT_GUARD_RADIUS[companion.index()])
                : companion.value("auraRadius", BARD_RADIUS[companion.index()]);
        return radius > 0.0 && gridDistanceSqr(companion, target) <= radius * radius;
    }

    private static double gridDistanceSqr(HeroCompanionTower companion, Tower tower) {
        double dx = tower.position().x() - companion.position().x();
        double dy = tower.position().y() - companion.position().y();
        double dz = tower.position().z() - companion.position().z();
        return dx * dx + dy * dy + dz * dz;
    }
}
