package com.hibiscusmc.hmccosmetics.hooks.placeholders;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetics;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.CosmeticUsers;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import com.hibiscusmc.hmccosmetics.util.TranslationUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A hook that integrates the plugin {@link me.clip.placeholderapi.PlaceholderAPIPlugin PlaceholderAPIPlugin}
 */
public class HMCPlaceholderExpansion extends PlaceholderExpansion {

    public HMCPlaceholderExpansion() {

    }

    @Override
    @NotNull
    public String getIdentifier() {
        return "HMCCosmetics";
    }

    @Override
    @NotNull
    public String getAuthor() {
        return "HibiscusMC";
    }

    @Override
    @NotNull
    @SuppressWarnings("deprecation")
    public String getVersion() {
        return HMCCosmeticsPlugin.getInstance().getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(@NotNull OfflinePlayer player, @NotNull String params) {
        if (!player.isOnline()) return TranslationUtil.getTranslation("user-cosmetic", "offline");
        CosmeticUser user = CosmeticUsers.getUser(player.getUniqueId());
        if (user == null) return TranslationUtil.getTranslation("user-cosmetic", "invalid-user");

        List<String> placeholderArgs = Arrays.asList(params.split("_", 3));

        switch (placeholderArgs.get(0).toLowerCase()) {
            case "using":
                if (placeholderArgs.size() >= 2) {
                    Cosmetic cosmetic = Cosmetics.getCosmetic(placeholderArgs.get(1));
                    if (cosmetic == null) return "INVALID_COSMETIC";
                    // giraffe: 등 치장은 칸 3개 중 어디든
                    return TranslationUtil.getTranslation("using-cosmetic", String.valueOf(user.hasCosmeticInSlot(cosmetic)));
                }
            case "current":
                if (placeholderArgs.size() >= 2) {
                    CosmeticSlot slot = CosmeticSlot.valueOf(placeholderArgs.get(1).toUpperCase());
                    if (slot == null) return null;
                    Cosmetic cosmetic = user.getCosmetic(slot);
                    if (cosmetic == null) return TranslationUtil.getTranslation("current-cosmetic", "no-cosmetic");
                    if (placeholderArgs.size() == 2) return TranslationUtil.getTranslation("current-cosmetic", cosmetic.getId());

                    String output;
                    switch (placeholderArgs.get(2).toLowerCase()) {
                        case "material" -> output = getMaterialName(cosmetic);
                        case "custommodeldata" -> output = getModelData(cosmetic);
                        case "itemmodel" -> output = getItemModel(cosmetic);
                        case "itemname" -> output = getItemName(cosmetic);
                        case "name" -> output = getDisplayName(cosmetic);
                        case "lore" -> output = getItemLore(cosmetic);
                        case "permission" -> output = cosmetic.getPermission();
                        default -> output = cosmetic.getId();
                    }
                    if (output == null) output = "none";
                    return TranslationUtil.getTranslation("current-cosmetic", output);
                }
            case "unlocked":
                if (placeholderArgs.size() >= 2) {
                    StringBuilder lookupStringBuilder = new StringBuilder();
                    for (int i = 1; i < placeholderArgs.size(); i++) {
                        String append = placeholderArgs.get(i);
                        if (lookupStringBuilder.isEmpty()) lookupStringBuilder.append(append);
                        else lookupStringBuilder.append("_").append(append);
                    }

                    Cosmetic cosmetic = Cosmetics.getCosmetic(lookupStringBuilder.toString());
                    if (cosmetic == null) return "INVALID_COSMETIC";
                    return TranslationUtil.getTranslation("unlocked-cosmetic", String.valueOf(user.canEquipCosmetic(cosmetic, true)));
                }
            case "equipped":
                if (placeholderArgs.size() >= 2) {
                    StringBuilder lookupStringBuilder = new StringBuilder();
                    for (int i = 1; i < placeholderArgs.size(); i++) {
                        String append = placeholderArgs.get(i);
                        if (lookupStringBuilder.isEmpty()) lookupStringBuilder.append(append);
                        else lookupStringBuilder.append("_").append(append);
                    }
                    String lookupString = lookupStringBuilder.toString();
                    String rawSlot = lookupString.toUpperCase();
                    //if (CosmeticSlot.contains(rawSlot)) {
                    CosmeticSlot slot = CosmeticSlot.valueOf(rawSlot);
                    if (slot != null) {
                        return TranslationUtil.getTranslation("equipped-cosmetic", String.valueOf(user.getCosmetic(slot) != null));
                    }

                    MessagesUtil.sendDebugMessages(lookupString);

                    Cosmetic cosmetic = Cosmetics.getCosmetic(lookupString);
                    if (cosmetic == null) {
                        return "INVALID_COSMETIC";
                    }
                    // giraffe: 등 치장은 칸 3개 중 어디든
                    return TranslationUtil.getTranslation("equipped-cosmetic", String.valueOf(user.hasCosmeticInSlot(cosmetic)));
                }
            // %hmccosmetics_amount_balloon_unlocked%
            case "amount":
                if (placeholderArgs.size() >= 2) {
                    String args1 = placeholderArgs.get(1).toUpperCase(); // changes offhand to OFFHAND

                    if (!CosmeticSlot.contains(args1)) return null;

                    CosmeticSlot slot = CosmeticSlot.valueOf(args1);
                    int amount = 0;
                    boolean checkUnlocked = false;
                    if (placeholderArgs.size() >= 3) if (placeholderArgs.get(2).equalsIgnoreCase("unlocked")) checkUnlocked = true;

                    for (Cosmetic cosmetic : Cosmetics.values()) {
                        if (cosmetic.getSlot() != slot) continue;
                        if (checkUnlocked && !user.canEquipCosmetic(cosmetic)) continue;
                        amount += 1;
                    }
                    return TranslationUtil.getTranslation("amount-cosmetic", String.valueOf(amount));

                } else {
                    return TranslationUtil.getTranslation("amount-cosmetic", String.valueOf(Cosmetics.values().size()));
                }
            case "hidden":
                return TranslationUtil.getTranslation("hidden", String.valueOf(user.isHidden()));
            case "wardrobe-enabled":
                return TranslationUtil.getTranslation("in-wardrobe", String.valueOf(user.isInWardrobe()));
        }
        return null;
    }

    /**
     * Gets the name of the cosmetic item {@link org.bukkit.Material Material}
     * @param cosmetic The cosmetic to get its {@link org.bukkit.Material Material}s name
     * @return The name of the cosmetic item {@link org.bukkit.Material Material}
     * @deprecated As of release 2.2.5+, use {@link #getMaterialName(Cosmetic)} instead
     */
    @Deprecated
    @Nullable
    public String getMaterial(@NotNull Cosmetic cosmetic) {
        ItemStack item = cosmetic.getItem();
        if (item == null) return null;
        return item.getType().toString();
    }

    /**
     * Gets the name of the cosmetic item {@link org.bukkit.Material Material}
     * @param cosmetic The cosmetic to get its {@link org.bukkit.Material Material}s name
     * @return The name of the cosmetic item {@link org.bukkit.Material Material}
     * @since 2.2.5
     */
    @Nullable
    public String getMaterialName(@NotNull Cosmetic cosmetic) {
        ItemStack item = cosmetic.getItem();
        if (item == null) return null;
        return item.getType().toString();
    }

    /**
     * Gets the cosmetic items custom model data
     * @param cosmetic The cosmetic to get its custom model data
     * @return The cosmetic items custom model data
     */
    @Nullable
    public String getModelData(@NotNull Cosmetic cosmetic) {
        ItemStack item = cosmetic.getItem();
        if (item == null) return null;
        if (!item.hasItemMeta()) return null;
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) return null;
        return String.valueOf(itemMeta.getCustomModelData());
    }

    /**
     * Gets the cosmetic items item model
     * @param cosmetic The cosmetic to get its item model
     * @return The cosmetic items item model
     */
    @Nullable
    public String getItemModel(@NotNull Cosmetic cosmetic) {
        try {
            ItemStack item = cosmetic.getItem();
            if (item == null) return null;
            if (!item.hasItemMeta()) return null;
            ItemMeta itemMeta = item.getItemMeta();
            if (itemMeta == null || !itemMeta.hasItemModel() || itemMeta.getItemModel() == null) return null;
            return itemMeta.getItemModel().asString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets the cosmetic items item name
     * @param cosmetic The cosmetic to get its items item name
     * @return The cosmetic items item name
     */
    @SuppressWarnings("deprecation")
    @Nullable
    public String getItemName(@NotNull Cosmetic cosmetic) {
        try {
            ItemStack item = cosmetic.getItem();
            if (item == null) return null;
            if (!item.hasItemMeta()) return null;
            ItemMeta itemMeta = item.getItemMeta();
            if (itemMeta == null || !itemMeta.hasItemName()) return null;
            return itemMeta.getDisplayName();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets the cosmetic items display name
     * @param cosmetic The cosmetic to get its items display name
     * @return The cosmetic items display name
     */
    @SuppressWarnings("deprecation")
    @Nullable
    public String getDisplayName(@NotNull Cosmetic cosmetic) {
        ItemStack item = cosmetic.getItem();
        if (item == null) return null;
        if (!item.hasItemMeta()) return null;
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) return null;
        if (!itemMeta.hasDisplayName()) return null;
        return itemMeta.getDisplayName();
    }

    /**
     * Gets the cosmetic items lore
     * @param cosmetic The cosmetic to get its items lore
     * @return The cosmetic items lore
     */
    @SuppressWarnings("deprecation")
    @Nullable
    public String getItemLore(@NotNull Cosmetic cosmetic) {
        ItemStack item = cosmetic.getItem();
        if (item == null) return null;
        if (item.hasItemMeta()) {
            ItemMeta itemMeta = item.getItemMeta();
            if (itemMeta == null) return null;
            return String.valueOf(itemMeta.getLore());
        }
        return null;
    }
}
