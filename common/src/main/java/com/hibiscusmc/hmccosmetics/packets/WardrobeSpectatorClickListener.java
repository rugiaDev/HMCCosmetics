package com.hibiscusmc.hmccosmetics.packets;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.hibiscusmc.hmccosmetics.gui.Menu;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.CosmeticUsers;
import com.hibiscusmc.hmccosmetics.user.manager.UserWardrobeManager;
import org.bukkit.entity.Player;

/**
 * giraffe: 옷장 안에서 좌클릭하면 메뉴를 연다 (26.x 손님용).
 * <p>
 * 옷장에 들어가면 손님 화면만 관전 모드가 된다. 26.x 손님은 관전 모드에서 좌클릭하면
 * 팔 휘두르기 신호를 보내지 않고 관전 동작 신호(바라본 대상 · 없으면 빈 값)를 보낸다.
 * 그래서 팔 휘두르기로 메뉴를 여는 원래 길({@link CosmeticPacketInterface#readPlayerArm})이 닿지 않는다.
 * 이 신호를 받아 같은 메뉴를 연다. 서버 쪽 사람은 관전 모드가 아니라 원래도 무시되는 신호라 막아 둔다.
 */
public class WardrobeSpectatorClickListener extends PacketListenerAbstract {

    /** packetevents 가 켜져 있을 때만 부른다 (없으면 이 클래스를 읽지 않음). */
    public static void register() {
        PacketEvents.getAPI().getEventManager().registerListener(new WardrobeSpectatorClickListener());
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.SPECTATE_ENTITY
                && event.getPacketType() != PacketType.Play.Client.SPECTATE) return;

        Player player = event.getPlayer();
        if (player == null) return;
        CosmeticUser user = CosmeticUsers.getUser(player);
        if (user == null) return;
        UserWardrobeManager wardrobeManager = user.getWardrobeManager();   // 나가는 중 null 이 될 수 있어 한 번만 읽음
        if (wardrobeManager == null) return;
        event.setCancelled(true);
        if (wardrobeManager.getWardrobeStatus() != UserWardrobeManager.WardrobeStatus.RUNNING) return;

        Menu menu = wardrobeManager.getLastOpenMenu();
        if (menu == null) return;
        menu.openMenu(user);   // 메뉴 열기는 안에서 메인 스레드로 넘긴다
    }
}
