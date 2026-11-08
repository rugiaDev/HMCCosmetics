package com.hibiscusmc.hmccosmetics.gui;

import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetics;
import com.hibiscusmc.hmccosmetics.gui.type.types.TypeCosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticHolder;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import me.lojosho.shaded.configurate.ConfigurationNode;
import me.lojosho.shaded.configurate.serialize.SerializationException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * giraffe (11-08 디스코드 S149): 치장 메뉴 '보유한 치장만 보기'.
 *
 * <p>메뉴 설정에 {@code owned-view: { group: hat, page: 1, grid: [19, 20, ...] }} 가 있으면 그 메뉴는 같은 group 의 메뉴들과 한 묶음이다.
 * 켜 둔 사람에게는 grid 칸에 그 묶음의 치장 중 착용할 수 있는(보유한) 것만 앞에서부터 채워 보인다 — page 2 는 그다음 칸 수만큼.
 * 끄면 원래 칸 그대로. 켜고 끄기는 메뉴 단추(type: owned_toggle)로, 서버가 켜져 있는 동안만 기억한다.
 */
public final class OwnedView {

    private static final Set<UUID> ENABLED = ConcurrentHashMap.newKeySet();

    private OwnedView() {
    }

    public static boolean isOn(Player player) {
        return player != null && ENABLED.contains(player.getUniqueId());
    }

    public static void toggle(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        if (!ENABLED.remove(uuid)) ENABLED.add(uuid);
    }

    /** 이 메뉴를 보유한 것만으로 그릴 때 칸 → 치장 단추(빈 칸은 null). 꺼져 있거나 묶음 메뉴가 아니면 null */
    @Nullable
    static Map<Integer, MenuItem> plan(Menu menu, Player viewer, CosmeticHolder holder) {
        if (!isOn(viewer)) return null;
        ConfigurationNode view = menu.getConfig().node("owned-view");
        if (view.virtual()) return null;
        String group = view.node("group").getString("");
        int page = Math.max(1, view.node("page").getInt(1));
        List<Integer> grid = grid(view);
        if (group.isEmpty() || grid.isEmpty()) return null;

        List<MenuItem> owned = new ArrayList<>();
        for (MenuItem item : groupItems(group)) {
            Cosmetic cosmetic = Cosmetics.getCosmetic(item.itemConfig().node("cosmetic").getString(""));
            if (cosmetic != null && holder.canEquipCosmetic(cosmetic, true)) owned.add(item);
        }
        Map<Integer, MenuItem> result = new HashMap<>();
        int start = (page - 1) * grid.size();
        for (int index = 0; index < grid.size(); index++) {
            int ownedIndex = start + index;
            result.put(grid.get(index), ownedIndex < owned.size() ? owned.get(ownedIndex) : null);
        }
        return result;
    }

    /* 같은 묶음 메뉴들의 치장 단추 — 쪽 순서 · 칸 순서대로 (메뉴 설정 순서 그대로라 다시 읽으면 바로 반영) */
    private static List<MenuItem> groupItems(String group) {
        record Placed(int page, int order, MenuItem item) {
        }
        List<Placed> placed = new ArrayList<>();
        for (Menu menu : Menus.values()) {
            ConfigurationNode view = menu.getConfig().node("owned-view");
            if (view.virtual() || !group.equals(view.node("group").getString(""))) continue;
            int page = view.node("page").getInt(1);
            List<Integer> grid = grid(view);
            for (Map.Entry<Integer, List<MenuItem>> entry : menu.getItemsBySlot().entrySet()) {
                int order = grid.indexOf(entry.getKey());
                if (order < 0 || entry.getValue().isEmpty()) continue;
                MenuItem item = entry.getValue().getFirst();
                if (item.type() instanceof TypeCosmetic) placed.add(new Placed(page, order, item));
            }
        }
        placed.sort(Comparator.comparingInt(Placed::page).thenComparingInt(Placed::order));
        List<MenuItem> result = new ArrayList<>();
        for (Placed entry : placed) result.add(entry.item());
        return result;
    }

    private static List<Integer> grid(ConfigurationNode view) {
        try {
            List<Integer> grid = view.node("grid").getList(Integer.class);
            return grid == null ? List.of() : grid;
        } catch (SerializationException e) {
            return List.of();
        }
    }
}
