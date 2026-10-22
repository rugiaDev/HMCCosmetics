package com.hibiscusmc.hmccosmetics.cosmetic;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.List;

@Getter
public class CosmeticSlot {
    private static final ConcurrentHashMap<String, CosmeticSlot> REGISTRY = new ConcurrentHashMap<>();

    public static final CosmeticSlot HELMET = register("HELMET");
    public static final CosmeticSlot CHESTPLATE = register("CHESTPLATE");
    public static final CosmeticSlot LEGGINGS = register("LEGGINGS");
    public static final CosmeticSlot BOOTS = register("BOOTS");
    public static final CosmeticSlot MAINHAND = register("MAINHAND");
    public static final CosmeticSlot OFFHAND = register("OFFHAND");
    public static final CosmeticSlot BACKPACK = register("BACKPACK");
    /** giraffe: 바디 치장 칸 수 상한 — config.yml cosmetic-settings.giraffe-body-slots 는 1 ~ 이 값 */
    public static final int MAX_BACKPACK_SLOTS = 9;
    /**
     * giraffe: 등 치장(바디 치장)이 들어갈 수 있는 칸 (앞에서부터 채운다). BACKPACK · BACKPACK_2 · BACKPACK_3 …
     * 칸 수는 설정을 읽을 때 {@link #setBackpackSlotCount} 로 정한다 (기본 3).
     */
    public static volatile List<CosmeticSlot> BACKPACK_SLOTS = List.of(BACKPACK);

    /** giraffe: 등 치장 칸인가 */
    public static boolean isBackpack(@Nullable CosmeticSlot slot) {
        return slot != null && BACKPACK_SLOTS.contains(slot);
    }

    /** giraffe: 바디 치장 칸 수를 정한다 (1 ~ {@link #MAX_BACKPACK_SLOTS}). 칸 BACKPACK_2 … 를 필요한 만큼 등록한다 */
    public static void setBackpackSlotCount(int count) {
        int clamped = Math.max(1, Math.min(MAX_BACKPACK_SLOTS, count));
        List<CosmeticSlot> slots = new ArrayList<>();
        slots.add(BACKPACK);
        for (int index = 2; index <= clamped; index++) {
            slots.add(REGISTRY.computeIfAbsent("BACKPACK_" + index, CosmeticSlot::new));
        }
        BACKPACK_SLOTS = List.copyOf(slots);
    }
    public static final CosmeticSlot BALLOON = register("BALLOON");
    /**
     * giraffe: 풍선이 들어갈 수 있는 칸 (앞에서부터 채운다). BALLOON · BALLOON_2 · BALLOON_3 …
     * 칸 수는 설정을 읽을 때 {@link #setBalloonSlotCount} 로 정한다 (기본 3).
     */
    public static volatile List<CosmeticSlot> BALLOON_SLOTS = List.of(BALLOON);

    /** giraffe: 풍선 칸인가 */
    public static boolean isBalloon(@Nullable CosmeticSlot slot) {
        return slot != null && BALLOON_SLOTS.contains(slot);
    }

    /** giraffe: 풍선 칸 수를 정한다 (1 ~ {@link #MAX_BACKPACK_SLOTS}) */
    public static void setBalloonSlotCount(int count) {
        int clamped = Math.max(1, Math.min(MAX_BACKPACK_SLOTS, count));
        List<CosmeticSlot> slots = new ArrayList<>();
        slots.add(BALLOON);
        for (int index = 2; index <= clamped; index++) {
            slots.add(REGISTRY.computeIfAbsent("BALLOON_" + index, CosmeticSlot::new));
        }
        BALLOON_SLOTS = List.copyOf(slots);
    }

    /** giraffe: 이 치장 종류가 여러 칸에 들어갈 수 있으면 그 칸 목록 (등 치장 · 풍선), 아니면 null */
    public static @Nullable List<CosmeticSlot> multiSlots(@Nullable CosmeticSlot baseSlot) {
        if (baseSlot == BACKPACK) return BACKPACK_SLOTS;
        if (baseSlot == BALLOON) return BALLOON_SLOTS;
        return null;
    }

    private final String name;

    private CosmeticSlot(@NotNull String name) {
        this.name = name;
    }

    /**
     * Registers a new slot with the given name. If a slot with the given name already exists, it will be returned.
     * @param name The name of the slot (This will automatically be converted into all UPPERCASE)
     * @return The slot that was registered or already exists.
     * @throws IllegalArgumentException if a cosmetic slot by that name has already been registered
     */
    @NotNull
    public static CosmeticSlot register(@NotNull String name) {
        final String upperName = name.toUpperCase();
        if(REGISTRY.containsKey(upperName)) {
            throw new IllegalArgumentException("A cosmetic slot with name '" + name + "' is already registered.");
        }

        final CosmeticSlot slot = new CosmeticSlot(upperName);
        REGISTRY.put(upperName, slot);
        return slot;
    }

    /**
     * @return An unmodifiable map of all the slots that have been registered.
     */
    @NotNull
    public static Map<String, CosmeticSlot> values() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    /**
     * Gets the slot with the given name.
     * @param name The name of the slot to get. This is automatically converted to all UPPERCASE.
     * @return The slot with the given name, or null if it does not exist.
     */
    @Nullable
    public static CosmeticSlot valueOf(@NotNull String name) {
        final String upperName = name.toUpperCase();
        return REGISTRY.get(upperName);
    }

    /**
     * Checks if the registry contains a slot with the given name.
     * @param name The name of the slot to check for. This is automatically converted to all UPPERCASE.
     * @return True if the slot exists, false otherwise.
     */
    public static boolean contains(@NotNull String name) {
        final String upperName = name.toUpperCase();
        return REGISTRY.containsKey(upperName);
    }

    @Override
    public String toString() {
        return name;
    }
}
