package com.hibiscusmc.hmccosmetics.user.manager;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import com.hibiscusmc.hmccosmetics.util.packets.HMCCPacketManager;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.*;

public class UserEntity {

    @Getter
    private UUID owner;
    @Getter
    private List<Player> viewers = new ArrayList<>();
    @Getter @Setter
    private Long viewerLastUpdate = 0L;
    @Getter @Setter
    private Long lastPositionUpdate = 0L;
    @Getter @Setter
    private List<Integer> ids = new ArrayList<>();
    @Getter
    private Location location;

    public UserEntity(UUID owner) {
        this.owner = owner;
    }

    public List<Player> refreshViewers() {
        return refreshViewers(location);
    }

    public List<Player> refreshViewers(Location location) {
        //Prevents mass refreshes
        //if(System.currentTimeMillis() - viewerLastUpdate <= 3000) {
        //    return List.of();
        //}

        Entity ownerPlayer = Bukkit.getEntity(owner);
        if (ownerPlayer == null) {
            MessagesUtil.sendDebugMessages("Owner is null (refreshViewers), returning empty list");
            return List.of();
        }

        final List<Player> players = HMCCosmeticsPlugin.getInstance()
            .getPlayerSearchManager()
            .getPlayersInRange(location, Settings.getViewDistance());

        final ArrayList<UUID> newPlayerIds = new ArrayList<>();
        final ArrayList<UUID> removePlayerIds = new ArrayList<>();

        // Go through all nearby players, check if they are new to the viewers list.
        for (Player player : players) {
            // giraffe: 주인을 못 보는 사람(로비 서로 숨김 · 투명 관리자 등)에게는 등 치장 · 풍선도 보내지 않는다 —
            // 원본은 옷장 안에 있는 사람만 걸러서, 로비에서 숨긴 사람의 풍선 · 가방만 허공에 떠 보였다
            // (치장 정보를 아직 못 읽은 사람도 걸러야 해서 CosmeticUser 유무와 상관없이 본다)
            if(
                !owner.equals(player.getUniqueId())
                && !player.canSee(ownerPlayer)
            ) {
                removePlayerIds.add(player.getUniqueId());
                continue;
            }

            if (!viewers.contains(player)) {
                viewers.add(player);
                newPlayerIds.add(player.getUniqueId());
            }
        }
        // Basically, if they are not nearby, they are still in the viewers and we need to kick em to the curb
        for (Player viewerPlayer : viewers) {
            if (!players.contains(viewerPlayer)) {
                removePlayerIds.add(viewerPlayer.getUniqueId());
            }
        }

        // If there are players for removal, send the packets to them
        if (!removePlayerIds.isEmpty()) {
            final List<Player> removePlayers = removePlayerIds.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .toList();

            HMCCPacketManager.sendEntityDestroyPacket(ids, removePlayers);
            viewers.removeAll(removePlayers);
        }

        this.setViewerLastUpdate(System.currentTimeMillis());
        return newPlayerIds.stream()
            .map(Bukkit::getPlayer)
            .filter(Objects::nonNull)
            .toList();
    }

    public void teleport(Location location) {
        if (this.getLocation() != null && this.getLocation().getWorld() == location.getWorld()) {
            // Was thinking about using schedulers to just send the packet later... but that would be a lot of tasks and
            // would probably cause more lag. Furthermore, the server "ticks" the cosmetics every second by defualt. So it's fine like this.
            //if (System.currentTimeMillis() - getLastPositionUpdate() <= Settings.getPacketEntityTeleportCooldown()) return;
        }
        this.location = location;
        for (Integer entity : ids) {
            HMCCPacketManager.sendTeleportPacket(entity, location, false, getViewers());
        }
        setLastPositionUpdate(System.currentTimeMillis());
    }

    public void setRotation(int yaw) {
        setRotation(yaw, false);
    }

    public void setRotation(int yaw, boolean additonalPacket) {
        location.setYaw(yaw);
        for (Integer entity : ids) {
            // First person backpacks need both packets to rotate properly, otherwise they look off
            // Regular backpacks just need the look packet
            if (additonalPacket) HMCCPacketManager.sendRotationPacket(entity, yaw, false, getViewers());
            HMCCPacketManager.sendRotateHeadPacket(entity, location, getViewers());
        }
    }
}
