package kim.biryeong.semiontd.tower.magicschool;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.BlockDisplayElement;
import java.util.List;
import java.util.Optional;
import kim.biryeong.semiontd.api.area.AreaVfxSpec;
import kim.biryeong.semiontd.api.area.AreaVfxStyles;
import kim.biryeong.semiontd.api.area.MonsterAreaEffectRequest;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterDimensions;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.area.TowerAreaDamage;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class MagicSchoolBombBarrelEntity extends SemionMonsterEntity {
    private ElementHolder barrelVisual;
    private boolean exploded;

    MagicSchoolBombBarrelEntity(PlayerLane lane, Vec3 position) {
        super(SemionEntityTypes.MONSTER, lane.arenaWorld());
        var monster = new Monster("magic_school_bomb_barrel", lane.teamId(), lane.laneId(), Optional.empty(), Optional.empty(),
                1, 0, 0, AttackKind.MELEE, "minecraft:silverfish", null, DamageType.PHYSICAL, 0,
                MonsterDimensions.of(1, 1), null, List.of(), 0);
        monster.setDisplayName("폭탄통");
        configureFrom(monster, lane.laneLayout());
        setPos(position);
        setNoAi(true);
        setNoGravity(true);
        setInvisible(true);
    }

    void attachBarrelVisual() {
        var block = new BlockDisplayElement(Blocks.TNT.defaultBlockState());
        block.setTranslation(new Vector3f(-.5f, 0, -.5f));
        barrelVisual = new ElementHolder();
        barrelVisual.addElement(block);
        EntityAttachment.ofTicking(barrelVisual, this);
    }

    boolean hasBarrelVisual() { return barrelVisual != null; }

    @Override
    public AppliedDamageResult applySemionDamageResult(DamageSource source, double amount, DamageType type) {
        var result = super.applySemionDamageResult(source, amount, type);
        if (result.killed()) explode(source);
        return result;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        explode(source);
    }

    private void explode(DamageSource damageSource) {
        if (exploded) return;
        exploded = true;
        if (!(damageSource.getEntity() instanceof SemionTowerEntity source) || source.runtimeTower() == null) return;
        var tower = source.runtimeTower();
        double damage = tower.resolveBasicAttackOutgoingDamage(source, null, source.attackDamageAmount(null))
                * MagicSchoolCurriculum.value("explosiveBarrelDamageRatio", .30);
        var request = MonsterAreaEffectRequest.aroundTarget(MagicSchoolSpellCombat.id("explosive_barrel"), source, this,
                MagicSchoolCurriculum.value("explosiveBarrelRadius", 2), AreaVfxSpec.onChange(AreaVfxStyles.CORPSE_EXPLOSION))
                .withFilter(target -> target.runtimeMonster().targetTeam() == source.teamId());
        TowerAreaDamage.applyResolved(tower, source, request, target -> damage, true,
                (target, dealt, killed) -> {}, DamageType.MAGIC);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (barrelVisual != null) {
            barrelVisual.destroy();
            barrelVisual = null;
        }
        super.remove(reason);
    }
}
