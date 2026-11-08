package com.hibiscusmc.hmccosmetics.gui.type.types;

import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticHolder;
import com.hibiscusmc.hmccosmetics.gui.OwnedView;
import com.hibiscusmc.hmccosmetics.gui.action.Actions;
import com.hibiscusmc.hmccosmetics.gui.type.Type;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import me.lojosho.hibiscuscommons.config.serializer.ItemSerializer;
import me.lojosho.shaded.configurate.ConfigurationNode;
import me.lojosho.shaded.configurate.serialize.SerializationException;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * giraffe (11-08 디스코드 S149): '보유한 치장만 보기' 켜고 끄기 단추 — type: owned_toggle.
 *
 * <p>클릭하면 그 사람의 보기 방식을 바꾸고(메뉴는 클릭 뒤 다시 그려진다) actions.any 를 실행한다.
 * 켜져 있으면 설정의 on-item 을, 꺼져 있으면 item 을 보인다.
 */
public class TypeOwnedToggle extends Type {

    public TypeOwnedToggle() {
        super("owned_toggle");
    }

    @Override
    public void run(Player viewer, CosmeticHolder cosmeticHolder, @NotNull ConfigurationNode config, ClickType clickType) {
        OwnedView.toggle(viewer);
        try {
            List<String> actions = new ArrayList<>();
            ConfigurationNode any = config.node("actions", "any");
            if (!any.virtual()) actions.addAll(any.getList(String.class));
            Actions.runActions(viewer, cosmeticHolder, actions);
        } catch (SerializationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run(CosmeticUser user, ConfigurationNode config, ClickType clickType) {
        Player player = user.getPlayer();
        if (player == null) return;
        run(player, user, config, clickType);
    }

    @Override
    public ItemStack setItem(CosmeticUser user, ConfigurationNode config, ItemStack itemStack, int slot) {
        return setItem(user.getPlayer(), user, config, itemStack, slot);
    }

    @Override
    public ItemStack setItem(Player viewer, CosmeticHolder cosmeticHolder, ConfigurationNode config, @NotNull ItemStack itemStack, int slot) {
        ItemStack shown = itemStack;
        if (OwnedView.isOn(viewer) && !config.node("on-item").virtual()) {
            try {
                ItemStack onItem = ItemSerializer.INSTANCE.deserialize(ItemStack.class, config.node("on-item"));
                if (onItem != null) shown = onItem;
            } catch (SerializationException ignored) {
                // on-item 이 잘못되면 기본 그림
            }
        }
        if (shown.hasItemMeta()) shown.setItemMeta(processItemMeta(viewer, shown.getItemMeta()));
        return shown;
    }
}
