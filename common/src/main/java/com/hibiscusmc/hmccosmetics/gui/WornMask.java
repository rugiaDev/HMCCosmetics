package com.hibiscusmc.hmccosmetics.gui;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.UseCooldownComponent;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * giraffe: 치장 메뉴에서 지금 착용 중인 치장 칸에 회색 덮개 (10-23 사용자 — GCore 쿨타임 덮개 방식 그대로).
 *
 * <p>메뉴 아이콘에 치장마다 전용 쿨다운 묶음(hmccosmetics:worn_&lt;치장&gt;)을 붙이고, 착용 중이면 그 묶음에 아주 긴 쿨다운을 걸어
 * 게임이 아이콘 위에 쿨타임 덮개를 그리게 한다. 실제로 쓰는 아이템과 묶음이 달라 다른 쿨다운에 영향이 없다.
 * 메뉴를 닫으면 걸어 둔 쿨다운을 모두 푼다.</p>
 */
public final class WornMask {

    /** 덮개가 줄어들지 않게 아주 길게 (약 14시간) */
    private static final int MASK_TICKS = 1_000_000;
    /** 사람 → (묶음 → 그 묶음이 붙은 아이콘) — 풀 때 같은 아이콘으로 쿨다운을 0 으로 */
    private static final Map<UUID, Map<NamespacedKey, ItemStack>> MASKED = new ConcurrentHashMap<>();

    private WornMask() {
    }

    /** 아이콘에 그 치장 전용 쿨다운 묶음을 붙이고, 착용 중이면 덮개 · 아니면 덮개 해제 */
    public static ItemStack apply(Player viewer, ItemStack itemStack, String cosmeticId, boolean worn) {
        if (viewer == null || itemStack == null || itemStack.getType().isAir()) return itemStack;
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) return itemStack;
        NamespacedKey group = new NamespacedKey(HMCCosmeticsPlugin.getInstance(), "worn_" + safe(cosmeticId));
        UseCooldownComponent cooldown = meta.getUseCooldown();
        cooldown.setCooldownGroup(group);
        if (cooldown.getCooldownSeconds() <= 0f) cooldown.setCooldownSeconds(0.05f);
        meta.setUseCooldown(cooldown);
        itemStack.setItemMeta(meta);

        Map<NamespacedKey, ItemStack> masked = MASKED.computeIfAbsent(viewer.getUniqueId(), ignored -> new ConcurrentHashMap<>());
        if (worn) {
            if (masked.put(group, itemStack.clone()) == null || viewer.getCooldown(itemStack) <= 0) viewer.setCooldown(itemStack, MASK_TICKS);
        } else {
            ItemStack previous = masked.remove(group);
            if (previous != null) viewer.setCooldown(previous, 0);
        }
        return itemStack;
    }

    /** 메뉴를 닫을 때 — 걸어 둔 덮개를 모두 푼다 */
    public static void clear(Player viewer) {
        if (viewer == null) return;
        Map<NamespacedKey, ItemStack> masked = MASKED.remove(viewer.getUniqueId());
        if (masked == null) return;
        for (ItemStack icon : masked.values()) viewer.setCooldown(icon, 0);
    }

    private static String safe(String id) {
        String text = id == null ? "none" : id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
        return text.length() > 60 ? text.substring(0, 60) : text;
    }
}
