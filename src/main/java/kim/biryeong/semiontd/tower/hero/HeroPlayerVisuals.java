package kim.biryeong.semiontd.tower.hero;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.mixin.accessor.PlayerInfoUpdatePacketAccessor;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

public final class HeroPlayerVisuals {
    private static final double TRACKING_DISTANCE_SQR = 128.0 * 128.0;
    private static final Map<HeroPartyTower, Visual> VISUALS = new IdentityHashMap<>();

    private HeroPlayerVisuals() {
    }

    public static synchronized void attach(SemionTowerEntity anchor, HeroPartyTower tower) {
        if (anchor == null || tower == null || !(anchor.level() instanceof ServerLevel level)) {
            return;
        }
        remove(tower);
        ServerPlayer fakePlayer = new ServerPlayer(
                level.getServer(),
                level,
                profile(level, tower),
                clientInformation()
        );
        fakePlayer.snapTo(anchor.getX(), anchor.getY(), anchor.getZ(), fakePlayer.getYRot(), fakePlayer.getXRot());
        equip(fakePlayer, tower);
        Visual visual = new Visual(anchor, tower, fakePlayer);
        VISUALS.put(tower, visual);
        visual.tick(true);
    }

    public static synchronized void tick(HeroPartyTower tower) {
        Visual visual = VISUALS.get(tower);
        if (visual != null) {
            visual.tick(false);
        }
    }

    public static synchronized void refresh(HeroPartyTower tower) {
        Visual visual = VISUALS.get(tower);
        if (visual == null) {
            return;
        }
        equip(visual.fakePlayer, tower);
        visual.refreshEquipment();
        visual.tick(true);
    }

    public static synchronized void refreshOwner(UUID ownerId) {
        if (ownerId == null) {
            return;
        }
        VISUALS.forEach((tower, visual) -> {
            if (ownerId.equals(tower.ownerPlayer())) {
                equip(visual.fakePlayer, tower);
                visual.refreshEquipment();
            }
        });
    }

    public static synchronized void refreshSkin(UUID ownerId, HeroCompanionRole role) {
        if (ownerId == null || role == null) {
            return;
        }
        List<Visual> matching = VISUALS.entrySet().stream()
                .filter(entry -> ownerId.equals(entry.getKey().ownerPlayer()))
                .filter(entry -> HeroPartyTowers.role(entry.getKey().type()).filter(role::equals).isPresent())
                .map(Map.Entry::getValue)
                .toList();
        for (Visual visual : matching) {
            VISUALS.remove(visual.tower);
            visual.remove();
            attach(visual.anchor, visual.tower);
        }
    }

    public static synchronized void playAttack(HeroPartyTower tower) {
        Visual visual = VISUALS.get(tower);
        if (visual == null) {
            return;
        }
        ClientboundAnimatePacket packet = new ClientboundAnimatePacket(
                visual.fakePlayer,
                ClientboundAnimatePacket.SWING_MAIN_HAND
        );
        visual.viewers().forEach(viewer -> viewer.connection.send(packet));
    }

    public static synchronized void remove(HeroPartyTower tower) {
        Visual visual = VISUALS.remove(tower);
        if (visual != null) {
            visual.remove();
        }
    }

    static synchronized Set<UUID> activeProfileIdsForTesting() {
        return VISUALS.values().stream()
                .map(visual -> visual.fakePlayer.getUUID())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static GameProfile profile(ServerLevel level, HeroPartyTower tower) {
        if (HeroPartyTowers.isHero(tower.type())) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(tower.ownerPlayer());
            if (owner != null) {
                UUID visualId = UUID.nameUUIDFromBytes(
                        ("semion-td:hero:" + tower.ownerPlayer()).getBytes(StandardCharsets.UTF_8)
                );
                GameProfile profile = new GameProfile(visualId, profileName("Hero", visualId));
                profile.getProperties().putAll(owner.getGameProfile().getProperties());
                return profile;
            }
        }
        HeroCompanionRole role = HeroPartyTowers.role(tower.type()).orElse(null);
        if (role != null) {
            return HeroCompanionSkins.profile(tower.ownerPlayer(), role);
        }
        UUID uuid = UUID.nameUUIDFromBytes("semion-td:hero-party:unknown".getBytes(StandardCharsets.UTF_8));
        return new GameProfile(uuid, profileName("Hero", uuid));
    }

    static UUID companionProfileId(HeroCompanionRole role) {
        return HeroCompanionSkins.roleDefaultProfileId(role);
    }

    private static String profileName(String prefix, UUID uuid) {
        String suffix = uuid.toString().replace("-", "").substring(0, 6);
        String name = prefix + suffix;
        return name.length() <= 16 ? name : name.substring(0, 16);
    }

    private static ClientInformation clientInformation() {
        return new ClientInformation(
                "ko_kr",
                10,
                ChatVisiblity.FULL,
                true,
                0x7F,
                HumanoidArm.RIGHT,
                false,
                false,
                net.minecraft.server.level.ParticleStatus.ALL
        );
    }

    private static void equip(ServerPlayer player, HeroPartyTower tower) {
        ItemStack mainHand = mainHand(tower);
        player.setItemSlot(EquipmentSlot.MAINHAND, mainHand);
        int armor = displayedArmorLevel(tower);
        player.setItemSlot(EquipmentSlot.HEAD, armorStack(armor, EquipmentSlot.HEAD));
        player.setItemSlot(EquipmentSlot.CHEST, armorStack(armor, EquipmentSlot.CHEST));
        player.setItemSlot(EquipmentSlot.LEGS, armorStack(armor, EquipmentSlot.LEGS));
        player.setItemSlot(EquipmentSlot.FEET, armorStack(armor, EquipmentSlot.FEET));
    }

    static int displayedArmorLevel(HeroPartyTower tower) {
        if (!(tower instanceof HeroTower)) {
            return 0;
        }
        HeroPartyState state = HeroPartyStates.state(tower.ownerPlayer());
        return state.armorVisible() ? state.armorLevel() : 0;
    }

    private static ItemStack mainHand(HeroPartyTower tower) {
        if (tower instanceof HeroTower) {
            return HeroPartyStates.state(tower.ownerPlayer()).equippedWeapon().item().getDefaultInstance();
        }
        return HeroPartyTowers.role(tower.type()).map(role -> switch (role) {
            case KNIGHT -> Items.IRON_SWORD.getDefaultInstance();
            case ARCHER -> Items.BOW.getDefaultInstance();
            case MAGE -> Items.BLAZE_ROD.getDefaultInstance();
            case PRIEST -> Items.ENCHANTED_BOOK.getDefaultInstance();
            case ROGUE -> Items.IRON_SWORD.getDefaultInstance();
            case BARD -> Items.NOTE_BLOCK.getDefaultInstance();
        }).orElse(ItemStack.EMPTY);
    }

    private static ItemStack armorStack(int level, EquipmentSlot slot) {
        if (level <= 0) {
            return ItemStack.EMPTY;
        }
        int tier = Math.max(1, Math.min(5, level));
        return switch (slot) {
            case HEAD -> switch (tier) {
                case 1 -> Items.LEATHER_HELMET.getDefaultInstance();
                case 2 -> Items.CHAINMAIL_HELMET.getDefaultInstance();
                case 3 -> Items.IRON_HELMET.getDefaultInstance();
                case 4 -> Items.DIAMOND_HELMET.getDefaultInstance();
                default -> Items.NETHERITE_HELMET.getDefaultInstance();
            };
            case CHEST -> switch (tier) {
                case 1 -> Items.LEATHER_CHESTPLATE.getDefaultInstance();
                case 2 -> Items.CHAINMAIL_CHESTPLATE.getDefaultInstance();
                case 3 -> Items.IRON_CHESTPLATE.getDefaultInstance();
                case 4 -> Items.DIAMOND_CHESTPLATE.getDefaultInstance();
                default -> Items.NETHERITE_CHESTPLATE.getDefaultInstance();
            };
            case LEGS -> switch (tier) {
                case 1 -> Items.LEATHER_LEGGINGS.getDefaultInstance();
                case 2 -> Items.CHAINMAIL_LEGGINGS.getDefaultInstance();
                case 3 -> Items.IRON_LEGGINGS.getDefaultInstance();
                case 4 -> Items.DIAMOND_LEGGINGS.getDefaultInstance();
                default -> Items.NETHERITE_LEGGINGS.getDefaultInstance();
            };
            case FEET -> switch (tier) {
                case 1 -> Items.LEATHER_BOOTS.getDefaultInstance();
                case 2 -> Items.CHAINMAIL_BOOTS.getDefaultInstance();
                case 3 -> Items.IRON_BOOTS.getDefaultInstance();
                case 4 -> Items.DIAMOND_BOOTS.getDefaultInstance();
                default -> Items.NETHERITE_BOOTS.getDefaultInstance();
            };
            default -> ItemStack.EMPTY;
        };
    }

    private static List<Pair<EquipmentSlot, ItemStack>> equipment(ServerPlayer fakePlayer) {
        ArrayList<Pair<EquipmentSlot, ItemStack>> equipment = new ArrayList<>();
        for (EquipmentSlot slot : List.of(
                EquipmentSlot.MAINHAND,
                EquipmentSlot.OFFHAND,
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        )) {
            equipment.add(Pair.of(slot, fakePlayer.getItemBySlot(slot).copy()));
        }
        return List.copyOf(equipment);
    }

    private static final class Visual {
        private final SemionTowerEntity anchor;
        private final HeroPartyTower tower;
        private final ServerPlayer fakePlayer;
        private final Set<UUID> trackingViewers = new java.util.HashSet<>();
        private int ticks;
        private double lastX = Double.NaN;
        private double lastY = Double.NaN;
        private double lastZ = Double.NaN;

        private Visual(SemionTowerEntity anchor, HeroPartyTower tower, ServerPlayer fakePlayer) {
            this.anchor = anchor;
            this.tower = tower;
            this.fakePlayer = fakePlayer;
        }

        private void tick(boolean forceMove) {
            if (!(anchor.level() instanceof ServerLevel level) || anchor.isRemoved()) {
                return;
            }
            ticks++;
            Set<UUID> visible = new java.util.HashSet<>();
            for (ServerPlayer viewer : level.players()) {
                if (viewer.distanceToSqr(anchor) > TRACKING_DISTANCE_SQR) {
                    continue;
                }
                visible.add(viewer.getUUID());
                if (trackingViewers.add(viewer.getUUID())) {
                    spawn(viewer);
                }
            }
            for (UUID viewerId : Set.copyOf(trackingViewers)) {
                if (!visible.contains(viewerId)) {
                    ServerPlayer viewer = level.getServer().getPlayerList().getPlayer(viewerId);
                    if (viewer != null) {
                        viewer.connection.send(new ClientboundRemoveEntitiesPacket(fakePlayer.getId()));
                        viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(fakePlayer.getUUID())));
                    }
                    trackingViewers.remove(viewerId);
                }
            }

            double x = anchor.getX();
            double y = anchor.getY();
            double z = anchor.getZ();
            if (forceMove || ticks % 2 == 0 && (x != lastX || y != lastY || z != lastZ)) {
                fakePlayer.snapTo(x, y, z, fakePlayer.getYRot(), fakePlayer.getXRot());
                ClientboundTeleportEntityPacket packet = ClientboundTeleportEntityPacket.teleport(
                        fakePlayer.getId(),
                        PositionMoveRotation.of(fakePlayer),
                        Set.of(),
                        true
                );
                viewers().forEach(viewer -> viewer.connection.send(packet));
                lastX = x;
                lastY = y;
                lastZ = z;
            }
        }

        private List<ServerPlayer> viewers() {
            if (!(anchor.level() instanceof ServerLevel level)) {
                return List.of();
            }
            return trackingViewers.stream()
                    .map(id -> level.getServer().getPlayerList().getPlayer(id))
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }

        private void spawn(ServerPlayer viewer) {
            ClientboundPlayerInfoUpdatePacket playerInfo = new ClientboundPlayerInfoUpdatePacket(
                    EnumSet.of(
                            ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                            ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED
                    ),
                    List.of(viewer)
            );
            ((PlayerInfoUpdatePacketAccessor) (Object) playerInfo).semiontd$setEntries(List.of(
                    new ClientboundPlayerInfoUpdatePacket.Entry(
                            fakePlayer.getUUID(),
                            fakePlayer.getGameProfile(),
                            false,
                            0,
                            GameType.ADVENTURE,
                            null,
                            true,
                            0,
                            null
                    )
            ));
            viewer.connection.send(playerInfo);
            viewer.connection.send(new ClientboundAddEntityPacket(fakePlayer, 0, fakePlayer.blockPosition()));
            List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> data = fakePlayer.getEntityData().getNonDefaultValues();
            if (data != null && !data.isEmpty()) {
                viewer.connection.send(new ClientboundSetEntityDataPacket(fakePlayer.getId(), data));
            }
            viewer.connection.send(new ClientboundSetEquipmentPacket(fakePlayer.getId(), equipment(fakePlayer)));
        }

        private void refreshEquipment() {
            ClientboundSetEquipmentPacket packet = new ClientboundSetEquipmentPacket(fakePlayer.getId(), equipment(fakePlayer));
            viewers().forEach(viewer -> viewer.connection.send(packet));
        }

        private void remove() {
            ClientboundRemoveEntitiesPacket removeEntity = new ClientboundRemoveEntitiesPacket(fakePlayer.getId());
            ClientboundPlayerInfoRemovePacket removeInfo = new ClientboundPlayerInfoRemovePacket(List.of(fakePlayer.getUUID()));
            viewers().forEach(viewer -> {
                viewer.connection.send(removeEntity);
                viewer.connection.send(removeInfo);
            });
            trackingViewers.clear();
        }
    }
}
