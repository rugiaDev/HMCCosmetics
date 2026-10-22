package com.hibiscusmc.hmccosmetics.user.manager;

import com.hibiscusmc.hmccosmetics.util.packets.HMCCPacketManager;
import lombok.Getter;
import me.lojosho.hibiscuscommons.nms.NMSHandlers;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class UserBalloonPufferfish extends UserEntity {

    @Getter
    private final int pufferFishEntityId;
    @Getter
    private final UUID uuid;
    /**
     * giraffe: 줄 고정점 — 손님 화면에만 있는 보이지 않는 표식 거치대. 사람 위에 태운 것처럼 보내 몸과 같은 틱에 같이 움직인다.
     * 서버에 진짜 엔티티로 태웠더니 사람이 탑승자를 가진 것이 되어 다른 월드로 가는 순간이동이 전부 실패했다(10-23 마을 이동 실패)
     */
    @Getter
    private final int leashAnchorEntityId;
    private final UUID leashAnchorUuid;
    private boolean destroyed = false;

    public UserBalloonPufferfish(UUID owner, int pufferFishEntityId, int leashAnchorEntityId, UUID uuid) {
        super(owner);
        this.pufferFishEntityId = pufferFishEntityId;
        this.leashAnchorEntityId = leashAnchorEntityId;
        this.leashAnchorUuid = UUID.randomUUID();
        setIds(List.of(pufferFishEntityId, leashAnchorEntityId));   // giraffe: 보는 사람이 멀어지면 고정점도 같이 지운다
        this.uuid = uuid;
    }

    public void hidePufferfish() {
        HMCCPacketManager.sendEntityDestroyPacket(getIds(), getViewers());
        getViewers().clear();
    }

    public void spawnPufferfish(Location location, List<Player> sendTo) {
        HMCCPacketManager.spawnInvisibleEntity(pufferFishEntityId, EntityType.PUFFERFISH, location, uuid, sendTo);
        NMSHandlers.getHandler().getPacketSender().sendBundle(HMCCPacketManager.getInvisibleArmorStand(leashAnchorEntityId, location, leashAnchorUuid), sendTo);   // giraffe: 줄 고정점
    }

    public void destroyPufferfish() {
        HMCCPacketManager.sendEntityDestroyPacket(getIds(), getViewers());
        getViewers().clear();
        destroyed = true;
    }

    public void destroyPufferfish(List<Player> viewers) {
        HMCCPacketManager.sendEntityDestroyPacket(getIds(), viewers);
        //getViewers().clear();
        destroyed = true;
    }

    @Override
    public List<Player> refreshViewers(Location location) {
        if (destroyed) return List.of(); //Prevents refreshing a destroyed entity
        return super.refreshViewers(location);
    }
}
