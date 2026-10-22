package com.hibiscusmc.hmccosmetics.cosmetic.types;

import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.behavior.CosmeticMovementBehavior;
import com.hibiscusmc.hmccosmetics.cosmetic.behavior.CosmeticUpdateBehavior;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.manager.UserBalloonManager;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import com.hibiscusmc.hmccosmetics.util.packets.HMCCPacketManager;
import lombok.Getter;
import me.lojosho.shaded.configurate.ConfigurationNode;
import me.lojosho.shaded.configurate.serialize.SerializationException;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CosmeticBalloonType extends Cosmetic implements CosmeticUpdateBehavior, CosmeticMovementBehavior {

    @Getter
    private final String modelName;
    @Getter
    private List<String> dyeableParts;
    @Getter
    private final boolean showLead;
    @Getter
    private Vector balloonOffset;

    public CosmeticBalloonType(String id, ConfigurationNode config) {
        super(id, config);

        String modelId = config.node("model").getString();
        showLead = config.node("show-lead").getBoolean(Settings.isBalloonDefaultShowLead());

        ConfigurationNode balloonOffsetNode = config.node("balloon-offset");
        if (balloonOffsetNode.virtual())
            balloonOffset = Settings.getBalloonOffset();
        else
            balloonOffset = Settings.loadVector(balloonOffsetNode);

        try {
            if (!config.node("dyeable-parts").virtual()) {
                dyeableParts = config.node("dyeable-parts").getList(String.class);
            }
        } catch (SerializationException e) {
            // Seriously?
            throw new RuntimeException(e);
        }
        if (modelId != null) modelId = modelId.toLowerCase(); // ME only accepts lowercase
        this.modelName = modelId;
    }

    @Override
    public void dispatchUpdate(@NotNull CosmeticUser user) {
        Entity entity = Bukkit.getEntity(user.getUniqueId());
        UserBalloonManager userBalloonManager = user.getBalloonManager(user.slotOf(this));   // giraffe: 칸마다 담당

        if (entity == null || userBalloonManager == null) return;
        if (user.isInWardrobe()) return;

        if (!userBalloonManager.getModelEntity().isValid()) {
            user.respawnBalloon();
            return;
        }

        Location newLocation = entity.getLocation();
        newLocation = newLocation.clone().add(user.balloonOffset(this, user.slotOf(this)));   // giraffe: 칸마다 자리
        if (Settings.isBalloonHeadForward()) newLocation.setPitch(0);

        if (!user.isHidden() && showLead) {
            List<Player> sendTo = userBalloonManager.getPufferfish().refreshViewers(newLocation);
            // giraffe: 바디 치장이 없으면 탑승 패킷을 주기적으로 보내는 곳이 없다 — 다른 플러그인이 사람 탑승자를 바꿔 손님 화면에서
            // 줄 고정점이 내려앉아도 1초 안에 다시 태운다 (바디 치장이 있으면 그쪽이 고정점까지 한 목록으로 보낸다)
            if (Settings.isBackpackForceRidingEnabled() && !user.hasBackpackCosmetic()) {
                userBalloonManager.mountLeashAnchor(user, new ArrayList<>(userBalloonManager.getPufferfish().getViewers()));
            }
            if (sendTo.isEmpty()) return;
            userBalloonManager.getPufferfish().spawnPufferfish(newLocation, sendTo);
            userBalloonManager.mountLeashAnchor(user, sendTo);   // giraffe: 줄 고정점을 새로 보는 사람 화면에서 사람 위에 태운다
            HMCCPacketManager.sendLeashPacket(userBalloonManager.getPufferfishBalloonId(), userBalloonManager.getLeashHolderId(entity), sendTo);
        }
    }

    @Override
    public void dispatchMove(@NotNull CosmeticUser user, @NotNull Location from, @NotNull Location to) {
        Entity entity = Bukkit.getEntity(user.getUniqueId());
        UserBalloonManager userBalloonManager = user.getBalloonManager(user.slotOf(this));   // giraffe: 칸마다 담당

        if (entity == null || userBalloonManager == null) return;
        if (user.isInWardrobe()) return;

        if (!userBalloonManager.getModelEntity().isValid()) {
            return;
        }

        Location newLocation = entity.getLocation();
        Location currentLocation = userBalloonManager.getLocation();
        newLocation = newLocation.clone().add(user.balloonOffset(this, user.slotOf(this)));   // giraffe: 칸마다 자리
        if (Settings.isBalloonHeadForward()) newLocation.setPitch(0);

        // giraffe: 풍선을 받은 사람에게만 (주변 검색은 1초마다 dispatchUpdate 가 한다 — 움직일 때마다 찾지 않는다)
        List<Player> viewers = new ArrayList<>(userBalloonManager.getPufferfish().getViewers());

        if (entity.getLocation().getWorld() != userBalloonManager.getLocation().getWorld()) {
            userBalloonManager.getModelEntity().teleport(newLocation);
            HMCCPacketManager.sendTeleportPacket(userBalloonManager.getPufferfishBalloonId(), newLocation, false, viewers);
            return;
        }

        //Vector velocity = newLocation.toVector().subtract(currentLocation.toVector());
        userBalloonManager.setLocation(newLocation);

        MessagesUtil.sendDebugMessages("Balloon Cosmetic Update for " + user.getEntity().getName());
        MessagesUtil.sendDebugMessages("Ballon previous location is " + currentLocation);
        MessagesUtil.sendDebugMessages("Balloon location set to " + newLocation);

        HMCCPacketManager.sendTeleportPacket(userBalloonManager.getPufferfishBalloonId(), newLocation, false, viewers);
        // giraffe: 줄은 사람 위에 태운 고정점에 묶여 있어 움직일 때마다 다시 묶지 않는다 (새로 보는 사람은 dispatchUpdate 가 묶음)
    }

    public boolean isDyeablePart(String name) {
        // If player does not define parts, dye whole model
        if (dyeableParts == null) return true;
        if (dyeableParts.isEmpty()) return true;
        return dyeableParts.contains(name);
    }
}
