package com.hibiscusmc.hmccosmetics.cosmetic;

import com.google.common.collect.ImmutableCollection;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Represents any object that can wear/hold cosmetics,
 * users, for example.
 */
public interface CosmeticHolder {
    @Nullable Cosmetic getCosmetic(@NotNull CosmeticSlot slot);

    @NotNull ImmutableCollection<Cosmetic> getCosmetics();

    void addCosmetic(@NotNull Cosmetic cosmetic, @Nullable Color color);

    default void addCosmetic(@NotNull Cosmetic cosmetic) {
        addCosmetic(cosmetic, null);
    }

    default void removeCosmetics() {
        // Small optimization could be made, but Concurrent modification prevents us from both getting and removing
        for (CosmeticSlot slot : CosmeticSlot.values().values()) {
            removeCosmeticSlot(slot);
        }
    }

    void removeCosmeticSlot(@NotNull CosmeticSlot slot);

    default void removeCosmeticSlot(@NotNull Cosmetic cosmetic) {
        CosmeticSlot slot = slotOf(cosmetic);
        removeCosmeticSlot(slot != null ? slot : cosmetic.getSlot());
    }

    /**
     * giraffe: 이 치장이 실제로 들어 있는 칸. 등 치장은 BACKPACK · BACKPACK_2 · BACKPACK_3 중 어디에 있을 수 있다.
     * 착용 중이 아니면 null.
     */
    default @Nullable CosmeticSlot slotOf(@NotNull Cosmetic cosmetic) {
        if (CosmeticSlot.isBackpack(cosmetic.getSlot())) {
            for (CosmeticSlot slot : CosmeticSlot.BACKPACK_SLOTS) {
                Cosmetic existing = getCosmetic(slot);
                if (existing != null && Objects.equals(existing.getId(), cosmetic.getId())) return slot;
            }
            return null;
        }
        Cosmetic existing = getCosmetic(cosmetic.getSlot());
        if (existing != null && Objects.equals(existing.getId(), cosmetic.getId())) return cosmetic.getSlot();
        return null;
    }

    default boolean hasCosmeticInSlot(@NotNull CosmeticSlot slot) {
        return getCosmetic(slot) != null;
    }

    default boolean hasCosmeticInSlot(@NotNull Cosmetic cosmetic) {
        return slotOf(cosmetic) != null;
    }

    default boolean canEquipCosmetic(@NotNull Cosmetic cosmetic) {
        return canEquipCosmetic(cosmetic, false);
    }

    boolean canEquipCosmetic(@NotNull Cosmetic cosmetic, boolean ignoreWardrobe);

    boolean updateCosmetic(@NotNull CosmeticSlot slot);

    boolean updateMovementCosmetic(@NotNull CosmeticSlot slot, final Location from, final Location to);

    /**
     * Just for backwards compatibility, ensures that the given viewer and the given cosmetic holder
     * represent the same user.
     *
     * @param viewer the viewer
     * @param cosmeticHolder the cosmetic holder
     */
    @ApiStatus.Internal
    static @NotNull CosmeticUser ensureSingleCosmeticUser(@NotNull Player viewer, @NotNull CosmeticHolder cosmeticHolder) {
        if (!(cosmeticHolder instanceof CosmeticUser user) || !user.getUniqueId().equals(viewer.getUniqueId())) {
            throw new IllegalStateException("This method does not support having a different viewer than" +
                    " the same cosmetic holder. Needs to be updated to support the overload that takes a viewer and" +
                    " a cosmetic holder instead of a single CosmeticUser.");
        }
        return user;
    }
}
