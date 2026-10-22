package com.hibiscusmc.hmccosmetics.cosmetic.types;

import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.behavior.CosmeticMovementBehavior;
import com.hibiscusmc.hmccosmetics.cosmetic.behavior.CosmeticUpdateBehavior;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.manager.UserBackpackManager;
import com.hibiscusmc.hmccosmetics.user.manager.UserEntity;
import com.hibiscusmc.hmccosmetics.util.packets.HMCCPacketManager;
import lombok.Getter;
import me.lojosho.hibiscuscommons.nms.NMSHandlers;
import me.lojosho.hibiscuscommons.nms.NMSPacketBuilder;
import me.lojosho.hibiscuscommons.packets.wrapper.PacketWrapper;
import me.lojosho.shaded.configurate.ConfigurationNode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
public class CosmeticBackpackType extends Cosmetic implements CosmeticUpdateBehavior, CosmeticMovementBehavior {
    private int height = -1;
    private ItemStack firstPersonBackpack;

    public CosmeticBackpackType(String id, ConfigurationNode config) {
        super(id, config);

        if (!config.node("firstperson-item").virtual()) {
            this.firstPersonBackpack = generateItemStack(config.node("firstperson-item"));
            this.height = config.node("height").getInt(5);
        }
    }

    @Override
    public void dispatchUpdate(@NotNull CosmeticUser user) {
        if (user.isInWardrobe()) return;

        Entity entity = user.getEntity();
        if(entity == null) {
            return;
        }

        Location entityLocation = entity.getLocation();
        Location loc = entityLocation.clone().add(0, 2, 0);

        UserBackpackManager backpackManager = user.getUserBackpackManager(user.slotOf(this));   // giraffe: 칸마다 담당
        if(backpackManager == null) return;

        UserEntity entityManager = backpackManager.getEntityManager();
        if(entityManager == null) return;

        entityManager.teleport(loc);
        entityManager.setRotation((int) loc.getYaw(), isFirstPersonCompadible());

        int firstArmorStandId = backpackManager.getFirstArmorStandId();

        List<Player> newViewers = entityManager.refreshViewers(loc);
        NMSPacketBuilder packetBuilder = NMSHandlers.getHandler().getPacketBuilder();

        final ArrayList<PacketWrapper> newViewerBundle = new ArrayList<>();

        if(!newViewers.isEmpty()) {
            newViewerBundle.addAll(HMCCPacketManager.getInvisibleArmorStand(firstArmorStandId, entityLocation, UUID.randomUUID()));
            newViewerBundle.add(packetBuilder.buildEntityEquipmentSlotUpdatePacket(firstArmorStandId, Map.of(EquipmentSlot.HEAD, user.getUserCosmeticItem(this, getItem()))));

            if (user.getPlayer() != null) {
                AttributeInstance scaleAttribute = user.getPlayer().getAttribute(Attribute.SCALE);
                if (scaleAttribute != null) {
                    newViewerBundle.add(packetBuilder.buildEntityAttributePacket(firstArmorStandId, Attribute.SCALE, scaleAttribute.getValue()));
                    /*
                    ArrayList<Integer> particleCloud = backpackManager.getAreaEffectEntityId();
                    for (int i : particleCloud) {
                        wrapper.add(packetBuilder.buildEntityAttributePacket(i, Attribute.SCALE, scaleAttribute.getValue()));
                    }
                     */
                }
            }
        }

        // If true, it will send the riding packet to all players. If false, it will send the riding packet only to new players
        int[] existingPassengers = entity.getPassengers().stream()
                .mapToInt(Entity::getEntityId)
                .toArray();
        boolean hasExistingPassengers = existingPassengers.length > 0;

        // giraffe: 진짜 탑승자는 사람 위에 그대로 두고, 등 치장 거치대 전부를 한 목록으로 태운다 (칸마다 보내면 서로 지운다)
        int[] allPassengers = user.getBackpackPassengerIds(false);
        if (Settings.isBackpackForceRidingEnabled()) {
            HMCCPacketManager.sendRidingPacket(entity.getEntityId(), allPassengers, entityManager.getViewers());
        } else {
            newViewerBundle.add(packetBuilder.buildEntityMountPacket(entity.getEntityId(), allPassengers));
        }

        if (isFirstPersonCompadible() && !user.isInWardrobe() && user.getPlayer() != null) {
            final ArrayList<PacketWrapper> ownerBundle = new ArrayList<>();

            ArrayList<Integer> particleCloud = backpackManager.getAreaEffectEntityId();
            for (int i = 1; i < particleCloud.size(); i++) {
                ownerBundle.add(packetBuilder.buildEntityMountPacket(particleCloud.get(i - 1), new int[]{particleCloud.get(i)}));
            }
            ownerBundle.add(packetBuilder.buildEntityMountPacket(particleCloud.getLast(), new int[]{firstArmorStandId}));
            ownerBundle.add(packetBuilder.buildEntityMountPacket(entity.getEntityId(), user.getBackpackPassengerIds(true)));   // giraffe
            if (!user.isHidden()) {
                ownerBundle.add(packetBuilder.buildEntityEquipmentSlotUpdatePacket(firstArmorStandId, Map.of(EquipmentSlot.HEAD, user.getUserCosmeticItem(this, firstPersonBackpack))));
            }

            NMSHandlers.getHandler().getPacketSender().sendBundle(ownerBundle, user.getPlayer());
        }

        NMSHandlers.getHandler().getPacketSender().sendBundle(newViewerBundle, newViewers);
        backpackManager.showBackpack();
    }

    @Override
    public void dispatchMove(@NotNull CosmeticUser user, @NotNull Location from, @NotNull Location to) {
        @SuppressWarnings("DuplicatedCode") // thanks.
        Entity entity = user.getEntity();
        if(entity == null) {
            return;
        }

        Location entityLocation = entity.getLocation();
        Location loc = entityLocation.clone().add(0, 2, 0);

        UserBackpackManager backpackManager = user.getUserBackpackManager(user.slotOf(this));   // giraffe: 칸마다 담당
        if(backpackManager == null) return;

        UserEntity entityManager = backpackManager.getEntityManager();
        if(entityManager == null) return;

        entityManager.teleport(loc);
        entityManager.setRotation((int) loc.getYaw(), isFirstPersonCompadible());
    }

    public boolean isFirstPersonCompadible() {
        return firstPersonBackpack != null;
    }

}
