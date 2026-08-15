package kim.biryeong.semiontd.tower.queen;

import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;

final class QueenEquipmentVisual {
    private QueenEquipmentVisual() {}

    static ArmorStand sync(ArmorStand visual, SemionTowerEntity source) {
        if (source == null || !source.isAlive() || !(source.level() instanceof ServerLevel level)) {
            remove(visual);
            return null;
        }
        if (visual == null || visual.isRemoved()) {
            visual = new ArmorStand(level, source.getX(), source.getY(), source.getZ());
            visual.setInvisible(true);
            visual.setInvulnerable(true);
            visual.setNoGravity(true);
            visual.setSilent(true);
            visual.setShowArms(true);
            visual.setNoBasePlate(true);
            visual.getEntityData().set(ArmorStand.DATA_CLIENT_FLAGS,
                    (byte) (visual.getEntityData().get(ArmorStand.DATA_CLIENT_FLAGS) | ArmorStand.CLIENT_FLAG_MARKER));
            if (!level.addFreshEntity(visual)) return null;
        }
        visual.setItemSlot(EquipmentSlot.HEAD, source.getItemBySlot(EquipmentSlot.HEAD).copy());
        visual.setItemSlot(EquipmentSlot.CHEST, source.getItemBySlot(EquipmentSlot.CHEST).copy());
        visual.setItemSlot(EquipmentSlot.MAINHAND, source.getItemBySlot(EquipmentSlot.MAINHAND).copy());
        visual.teleportTo(source.getX(), source.getY(), source.getZ());
        visual.setYRot(source.getYRot());
        visual.setYBodyRot(source.getYRot());
        visual.setYHeadRot(source.getYHeadRot());
        return visual;
    }

    static void remove(ArmorStand visual) {
        if (visual != null && !visual.isRemoved()) visual.discard();
    }
}
