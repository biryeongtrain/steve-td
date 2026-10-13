package kim.biryeong.semiontd.tower.magicschool;

import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.SummonConfig;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.KillSourceKind;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterDataKey;
import kim.biryeong.semiontd.entity.monster.MonsterDimensions;
import kim.biryeong.semiontd.entity.monster.MonsterState;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.summon.BasicIncomeSummon;
import kim.biryeong.semiontd.summon.SummonBalancePolicy;
import kim.biryeong.semiontd.summon.SummonRegistry;
import net.minecraft.resources.Identifier;

public final class MagicSchoolDeathEaters {
    public static final String ID = "magic_school_death_eater";
    private static final MonsterDataKey<Encounter> ENCOUNTER = MonsterDataKey.of(
            Identifier.fromNamespaceAndPath("semion-td", "magic_school_death_eater"), Encounter.class);

    private record Encounter(UUID owner, int round, boolean rewarded) { }

    private MagicSchoolDeathEaters() {
    }

    public static void onWaveStarted(PlayerLane lane, int round) {
        if (MagicSchoolCurriculum.beginDeathEaterWave(lane.ownerPlayer(), round)) {
            lane.enqueueSummonedMonster(create(lane, round));
        }
    }

    static Monster create(PlayerLane lane, int round) {
        var magmaCube = SummonRegistry.find("magma_cube").orElseGet(() ->
                new BasicIncomeSummon(SummonConfig.defaultConfig().summons().get("magma_cube")));
        var monster = new Monster(ID, lane.teamId(), lane.laneId(), Optional.empty(), Optional.empty(),
                magmaCube.maxHealth() * SummonBalancePolicy.summonHealthMultiplier(round), magmaCube.armor(),
                magmaCube.attackDamage() * SummonBalancePolicy.summonAttackDamageMultiplier(round), magmaCube.attackKind(),
                "minecraft:vex", null, DamageType.MAGIC, magmaCube.resistance(),
                MonsterDimensions.of(0.4, 0.8), magmaCube.tier(), magmaCube.roles(), 0);
        monster.setDisplayName("죽음을 먹는 자");
        monster.setData(ENCOUNTER, new Encounter(lane.ownerPlayer(), round, false));
        return monster;
    }

    public static void onMonsterDeath(PlayerLane lane, Monster monster) {
        Encounter encounter = monster.getData(ENCOUNTER).orElse(null);
        if (encounter == null || encounter.rewarded() || !encounter.owner().equals(lane.ownerPlayer())
                || monster.state() != MonsterState.DEAD
                || !MagicSchoolCurriculum.purchased(encounter.owner(), MagicSchoolCurriculum.Upgrade.DEATH_EATER)
                || (monster.lastHitSourceKind() != KillSourceKind.TOWER && monster.lastHitSourceKind() != KillSourceKind.DEFENDER)) return;
        monster.setData(ENCOUNTER, new Encounter(encounter.owner(), encounter.round(), true));
        double amount = encounter.round() * TowerBalanceRuntime.ability(MagicSchoolTowers.CONFIG_ID, "deathEaterProficiencyPerRound", 2);
        for (var tower : lane.towers()) {
            if (tower instanceof MagicSchoolWizardTower wizard && encounter.owner().equals(wizard.ownerPlayer())
                    && !wizard.isTemporaryCopy() && wizard.health() > 0
                    && wizard.runtimeEntity(lane).filter(entity -> entity.isAlive() && !entity.isRemoved()).isPresent()) {
                wizard.gainProficiency(amount, lane);
            }
        }
    }
}
