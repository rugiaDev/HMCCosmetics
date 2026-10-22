package com.hibiscusmc.hmccosmetics.user;

import org.bukkit.entity.LivingEntity;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.api.events.*;
import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.config.WardrobeSettings;
import com.hibiscusmc.hmccosmetics.config.section.Wardrobe;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticHolder;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.cosmetic.behavior.CosmeticMovementBehavior;
import com.hibiscusmc.hmccosmetics.cosmetic.behavior.CosmeticUpdateBehavior;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticArmorType;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticBackpackType;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticBalloonType;
import com.hibiscusmc.hmccosmetics.database.Database;
import com.hibiscusmc.hmccosmetics.database.UserData;
import com.hibiscusmc.hmccosmetics.gui.Menus;
import com.hibiscusmc.hmccosmetics.user.manager.UserBackpackManager;
import com.hibiscusmc.hmccosmetics.user.manager.UserBalloonManager;
import com.hibiscusmc.hmccosmetics.user.manager.UserWardrobeManager;
import com.hibiscusmc.hmccosmetics.util.HMCCInventoryUtils;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import com.hibiscusmc.hmccosmetics.util.packets.HMCCPacketManager;
import lombok.Getter;
import lombok.Setter;
import me.lojosho.hibiscuscommons.hooks.Hooks;
import me.lojosho.hibiscuscommons.nms.NMSHandlers;
import me.lojosho.hibiscuscommons.util.InventoryUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.logging.Level;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.util.Vector;

public class CosmeticUser implements CosmeticHolder {
    @Getter
    private final UUID uniqueId;
    private int taskId = -1;
    /** giraffe: 바꿀 때 저장 — DB 에서 다 읽은 뒤부터만 (읽는 중의 착용은 저장하지 않는다) */
    private boolean saveOnChange = false;
    /** giraffe: 연타하면 마지막 상태만 저장하도록 미뤄 둔 저장 */
    private BukkitTask pendingSave;
    /** giraffe: 마지막으로 바꾼 뒤 이만큼(틱) 조용하면 저장 */
    private static final long SAVE_DEBOUNCE_TICKS = 10L;
    private final HashMap<CosmeticSlot, Cosmetic> playerCosmetics = new HashMap<>();
    private UserWardrobeManager userWardrobeManager;
    // giraffe: 풍선 칸마다 그리기 담당 하나
    private final LinkedHashMap<CosmeticSlot, UserBalloonManager> balloonManagers = new LinkedHashMap<>();
    @Getter @Nullable
    // giraffe: 등 치장 칸마다 그리기 담당 하나 (원래는 사람마다 하나라 등 치장을 하나만 그렸다)
    private final LinkedHashMap<CosmeticSlot, UserBackpackManager> backpackManagers = new LinkedHashMap<>();

    // Cosmetic Settings/Toggles
    private final ArrayList<HiddenReason> hiddenReason = new ArrayList<>();
    private final HashMap<CosmeticSlot, Color> colors = new HashMap<>();

    @Getter @Setter
    @ApiStatus.Internal
    private boolean hidingBackpackPose = false; // This is so dumb, but I need to know if a user has their backpack hidden because of their pose. See #214. Probably move over to a better system in the future.

    /**
     * Use {@link #CosmeticUser(UUID)} instead and use {@link #initialize(UserData)} to populate the user with data.
     * @param uuid
     * @param data
     */
    @Deprecated(forRemoval = true, since = "2.7.5")
    public CosmeticUser(UUID uuid, UserData data) {
        this(uuid);
        initialize(data);
    }

    public CosmeticUser(@NotNull UUID uuid) {
        this.uniqueId = uuid;
    }

    /**
     * Initialize the {@link CosmeticUser}.
     * @param userData the associated {@link UserData}
     * @return the {@link CosmeticUser}
     * @apiNote Initialize is called after {@link CosmeticUserProvider#createCosmeticUser(UUID)} so it is possible to
     * populate an extending version of {@link CosmeticUser} with data then override this method to apply your
     * own state.
     */
    public CosmeticUser initialize(final @Nullable UserData userData) {
        if(userData != null) {
            // CosmeticSlot -> Entry<Cosmetic, Integer>
            for(final Map.Entry<CosmeticSlot, Map.Entry<Cosmetic, Integer>> entry : userData.getCosmetics().entrySet()) {
                final Cosmetic cosmetic = entry.getValue().getKey();
                final Integer colorRGBInt = entry.getValue().getValue();

                if (!this.canApplyCosmetic(cosmetic)) {
                    MessagesUtil.sendDebugMessages("Cannot apply cosmetic[id=" + cosmetic.getId() + "]");
                    continue;
                }

                Color color = null;
                if (colorRGBInt != -1) color = Color.fromRGB(colorRGBInt); // -1 is defined as no color; anything else is a color

                this.addCosmetic(cosmetic, color);
            }
            this.applyHiddenState(userData.getHiddenReasons());
        }
        this.saveOnChange = true;   // giraffe

        return this;
    }

    /**
     * giraffe: 치장을 착용 · 해제 · 염색할 때 저장 (10-23 — 퇴장 때만 저장하면 채널을 옮기기 직전에 바꾼 치장이 다음 채널에서 옛것으로 보였다).
     * 0.5초 안에 또 바뀌면 다시 미뤄 마지막 상태만 저장한다. 저장 자체는 비동기 (Database.save)
     */
    private void scheduleSave() {
        if (!saveOnChange || getPlayer() == null) return;
        HMCCosmeticsPlugin plugin = HMCCosmeticsPlugin.getInstance();
        if (plugin.isDisabled()) return;
        if (pendingSave != null) pendingSave.cancel();
        pendingSave = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingSave = null;
            if (CosmeticUsers.getUser(getUniqueId()) == this) Database.save(this);   // 이미 나갔으면 퇴장 저장이 했다
        }, SAVE_DEBOUNCE_TICKS);
    }

    /**
     * This method is only called from {@link #initialize(UserData)} and can't be called directly.
     * This is used to help hooking plugins apply custom logic to the user.
     */
    protected boolean applyCosmetic(@NotNull Cosmetic cosmetic, @Nullable Color color) {
        this.addCosmetic(cosmetic, color);
        return true;
    }

    /**
     * This method is only called from {@link #initialize(UserData)} and can't be called directly.
     * This is used to help hooking plugins apply custom logic to the user.
     */
    protected boolean canApplyCosmetic(@NotNull Cosmetic cosmetic) {
        return canEquipCosmetic(cosmetic, false);
    }

    /**
     * This method is only called from {@link #initialize(UserData)} and can't be called directly.
     * This is used to help hooking plugins apply custom logic to the user.
     */
    protected void applyHiddenState(@NotNull List<HiddenReason> hiddenReasons) {
        if(!hiddenReason.isEmpty()) {
            for(final HiddenReason reason : this.hiddenReason) {
                this.silentlyAddHideFlag(reason);
            }
            return;
        }

        Player bukkitPlayer = getPlayer();
        if (bukkitPlayer != null && Settings.isDisabledGamemodesEnabled() && Settings.getDisabledGamemodes().contains(bukkitPlayer.getGameMode().toString())) {
            MessagesUtil.sendDebugMessages("Hiding cosmetics due to gamemode");
            hideCosmetics(HiddenReason.GAMEMODE);
        } else if (this.isHidden(HiddenReason.GAMEMODE)) {
            MessagesUtil.sendDebugMessages("Showing cosmetics for gamemode");
            showCosmetics(HiddenReason.GAMEMODE);
        }

        if (bukkitPlayer != null && Settings.getDisabledWorlds().contains(bukkitPlayer.getLocation().getWorld().getName())) {
            MessagesUtil.sendDebugMessages("Hiding Cosmetics due to world");
            hideCosmetics(CosmeticUser.HiddenReason.WORLD);
        } else if (this.isHidden(HiddenReason.WORLD)) {
            MessagesUtil.sendDebugMessages("Showing Cosmetics due to world");
            showCosmetics(HiddenReason.WORLD);
        }

        if (bukkitPlayer != null && bukkitPlayer.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            hideCosmetics(HiddenReason.POTION);
        }

        if (Settings.isAllPlayersHidden()) {
            hideCosmetics(HiddenReason.DISABLED);
        }

        for (final HiddenReason reason : hiddenReasons) {
            this.silentlyAddHideFlag(reason);
        }
    }

    /**
     * Start ticking against the {@link CosmeticUser}.
     * @implNote The tick-rate is determined by the tick period specified in the configuration, if it is less-than or equal to 0
     * there will be no {@link BukkitTask} created, and the {@link CosmeticUser#taskId} will be -1
     */
    public final void startTicking() {
        int tickPeriod = Settings.getTickPeriod();
        if(tickPeriod <= 0) {
            MessagesUtil.sendDebugMessages("CosmeticUser tick is disabled.");
            return;
        }

        final BukkitTask task = Bukkit.getScheduler().runTaskTimer(HMCCosmeticsPlugin.getInstance(), this::tick, 0, tickPeriod);
        this.taskId = task.getTaskId();
    }

    /**
     * Dispatch an operation to happen against this {@link CosmeticUser}
     * at a pre-determined tick-rate.
     * The tick-rate is determined by the tick period specified in the configuration.
     */
    protected void tick() {
        MessagesUtil.sendDebugMessages("Tick[uuid=" + uniqueId + "]", Level.INFO);

        if (Hooks.isInvisible(uniqueId)) {
            this.hideCosmetics(HiddenReason.VANISH);
        } else {
            this.showCosmetics(HiddenReason.VANISH);
        }

        this.updateCosmetic();

        if(isHidden() && !playerCosmetics.isEmpty()) {
            MessagesUtil.sendActionBar(getPlayer(), "hidden-cosmetics");
        }
    }

    public void destroy() {
        if (pendingSave != null) {   // giraffe: 퇴장 저장이 바로 앞에서 했다
            pendingSave.cancel();
            pendingSave = null;
        }
        saveOnChange = false;
        if(this.taskId != -1) { // ensure we're actually ticking this user.
            Bukkit.getScheduler().cancelTask(taskId);
        }

        despawnBackpack();
        despawnBalloon();
    }

    @Override
    public @Nullable Cosmetic getCosmetic(@NotNull CosmeticSlot slot) {
        return playerCosmetics.getOrDefault(slot, null);
    }

    @Override
    public @NotNull ImmutableCollection<Cosmetic> getCosmetics() {
        return ImmutableList.copyOf(playerCosmetics.values());
    }

    @Override
    public void addCosmetic(@NotNull Cosmetic cosmetic, @Nullable Color color) {
        // API
        PlayerCosmeticEquipEvent event = new PlayerCosmeticEquipEvent(this, cosmetic);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        cosmetic = event.getCosmetic();
        // Internal
        // giraffe: 등 치장은 이미 착용 중인 칸 → 빈 칸(1 → 2 → 3) → 다 차 있으면 1번 칸을 바꾼다
        final CosmeticSlot targetSlot = CosmeticSlot.multiSlots(cosmetic.getSlot()) != null ? pickMultiSlot(cosmetic) : cosmetic.getSlot();
        if (playerCosmetics.containsKey(targetSlot)) {
            removeCosmeticSlot(targetSlot);
        }

        playerCosmetics.put(targetSlot, cosmetic);
        if (color != null) colors.put(targetSlot, color);
        MessagesUtil.sendDebugMessages("addPlayerCosmetic[id=" + cosmetic.getId() + ",slot=" + targetSlot + "]");
        if (!isHidden()) {
            if (cosmetic instanceof CosmeticBackpackType backpackType) {
                spawnBackpack(backpackType, targetSlot);
                MessagesUtil.sendDebugMessages("addPlayerCosmetic[spawnBackpack,id=" + cosmetic.getId() + "]");
            }
            if (cosmetic instanceof CosmeticBalloonType balloonType) {
                spawnBalloon(balloonType, targetSlot);
            }
        }
        scheduleSave();   // giraffe
        // API
        PlayerCosmeticPostEquipEvent postEquipEvent = new PlayerCosmeticPostEquipEvent(this, cosmetic);
        Bukkit.getPluginManager().callEvent(postEquipEvent);
    }

    /**
     * @deprecated Use {@link #addCosmetic(Cosmetic)} instead
     */
    @Deprecated(since = "2.7.7", forRemoval = true)
    public void addPlayerCosmetic(@NotNull Cosmetic cosmetic) {
        addCosmetic(cosmetic);
    }

    /**
     * @deprecated Use {@link #addCosmetic(Cosmetic, Color)} instead
     */
    @Deprecated(since = "2.7.7", forRemoval = true)
    public void addPlayerCosmetic(@NotNull Cosmetic cosmetic, @Nullable Color color) {
        addCosmetic(cosmetic, color);
    }

    @Override
    public void removeCosmeticSlot(@NotNull CosmeticSlot slot) {
        // API
        PlayerCosmeticRemoveEvent event = new PlayerCosmeticRemoveEvent(this, getCosmetic(slot));
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        // Internal
        if (CosmeticSlot.isBackpack(slot)) {
            despawnBackpack(slot);
        }
        if (CosmeticSlot.isBalloon(slot)) {
            despawnBalloon(slot);
        }
        colors.remove(slot);
        playerCosmetics.remove(slot);
        removeArmor(slot);
        scheduleSave();   // giraffe
    }

    @Override
    public boolean hasCosmeticInSlot(@NotNull CosmeticSlot slot) {
        return playerCosmetics.containsKey(slot);
    }

    public @NotNull Set<CosmeticSlot> getSlotsWithCosmetics() {
        return Set.copyOf(playerCosmetics.keySet());
    }

    @Override
    public boolean updateCosmetic(@NotNull CosmeticSlot slot) {
        final Cosmetic cosmetic = playerCosmetics.get(slot);
        if(cosmetic == null) {
            return false;
        }

        if(!(cosmetic instanceof CosmeticUpdateBehavior behavior)) {
            MessagesUtil.sendDebugMessages("Attempted to update cosmetic that does not implement CosmeticUpdateBehavior");
            return false;
        }

        behavior.dispatchUpdate(this);
        return true;
    }

    @Override
    public boolean updateMovementCosmetic(@NotNull CosmeticSlot slot, @NotNull final Location from, @NotNull final Location to) {
        final Cosmetic cosmetic = playerCosmetics.get(slot);
        if(cosmetic == null) {
            return false;
        }

        if(!(cosmetic instanceof CosmeticMovementBehavior behavior)) {
            MessagesUtil.sendDebugMessages("Attempted to update cosmetic that does not implement CosmeticMovementBehavior");
            return false;
        }

        behavior.dispatchMove(this, from, to);
        return true;
    }

    public boolean updateCosmetic(@NotNull final Cosmetic cosmetic) {
        return updateCosmetic(cosmetic.getSlot());
    }

    public void updateCosmetic() {
        MessagesUtil.sendDebugMessages("updateCosmetic (All) - start");
        final HashMap<EquipmentSlot, ItemStack> items = new HashMap<>();

        for(final Cosmetic cosmetic : playerCosmetics.values()) {
            if(!(cosmetic instanceof CosmeticUpdateBehavior behavior)) {
                continue;
            }

            // defers item updates to end of operation
            if(cosmetic instanceof CosmeticArmorType armorType) {
                if (isInWardrobe()) return;
                if (!(getEntity() instanceof HumanEntity humanEntity)) return;

                boolean requireEmpty = Settings.getSlotOption(armorType.getEquipSlot()).isRequireEmpty();
                boolean isAir = humanEntity.getInventory().getItem(armorType.getEquipSlot()).getType().isAir();
                MessagesUtil.sendDebugMessages("updateCosmetic (All) - " + armorType.getId() + " - " + requireEmpty + " - " + isAir);
                if (requireEmpty && !isAir) continue;

                items.put(HMCCInventoryUtils.getEquipmentSlot(armorType.getSlot()), armorType.getItem(this));
            } else {
                behavior.dispatchUpdate(this);
            }
        }

        final Entity entity = this.getEntity();
        if(!items.isEmpty() && entity != null) {
            NMSHandlers.getHandler().getPacketBuilder().buildEntityEquipmentSlotUpdatePacket(entity.getEntityId(), items).sendPacket(HMCCPacketManager.getViewers(entity.getLocation()));
            MessagesUtil.sendDebugMessages("updateCosmetic (All) - end - " + items.size());
        }
    }

    public ItemStack getUserCosmeticItem(@NotNull CosmeticSlot slot) {
        Cosmetic cosmetic = getCosmetic(slot);
        if (cosmetic == null) return new ItemStack(Material.AIR);
        return getUserCosmeticItem(cosmetic);
    }

    public ItemStack getUserCosmeticItem(@NotNull Cosmetic cosmetic) {
        ItemStack item = null;
        if (!hiddenReason.isEmpty()) {
            if (cosmetic instanceof CosmeticBackpackType || cosmetic instanceof CosmeticBalloonType) return new ItemStack(Material.AIR);
            return getPlayer().getInventory().getItem(HMCCInventoryUtils.getEquipmentSlot(cosmetic.getSlot()));
        }
        if (cosmetic instanceof CosmeticArmorType armorType) {
            item = armorType.getItem(this, cosmetic.getItem());
        }
        if (cosmetic instanceof CosmeticBackpackType) {
            item = cosmetic.getItem();
        }
        if (cosmetic instanceof CosmeticBalloonType) {
            if (cosmetic.getItem() == null) {
                item = new ItemStack(Material.LEATHER_HORSE_ARMOR);
            } else {
                item = cosmetic.getItem();
            }
        }
        return getUserCosmeticItem(cosmetic, item);
    }

    @SuppressWarnings("deprecation")
    public @NotNull ItemStack getUserCosmeticItem(@NotNull Cosmetic cosmetic, @Nullable ItemStack item) {
        if (item == null) {
            //MessagesUtil.sendDebugMessages("GetUserCosemticUser Item is null");
            return new ItemStack(Material.AIR);
        }
        if (item.hasItemMeta()) {
            ItemMeta itemMeta = item.getItemMeta();

            if (item.getType() == Material.PLAYER_HEAD) {
                SkullMeta skullMeta = (SkullMeta) itemMeta;
                if (skullMeta.getPersistentDataContainer().has(InventoryUtils.getSkullOwner(), PersistentDataType.STRING)) {
                    String owner = skullMeta.getPersistentDataContainer().get(InventoryUtils.getSkullOwner(), PersistentDataType.STRING);

                    owner = Hooks.processPlaceholders(getPlayer(), owner);

                    skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
                    //skullMeta.getPersistentDataContainer().remove(InventoryUtils.getSkullOwner()); // Don't really need this?
                }
                if (skullMeta.getPersistentDataContainer().has(InventoryUtils.getSkullTexture(), PersistentDataType.STRING)) {
                    String texture = skullMeta.getPersistentDataContainer().get(InventoryUtils.getSkullTexture(), PersistentDataType.STRING);

                    texture = Hooks.processPlaceholders(getPlayer(), texture);

                    Bukkit.getUnsafe().modifyItemStack(item, "{SkullOwner:{Id:[I;0,0,0,0],Properties:{textures:[{Value:\""
                            + texture + "\"}]}}}");
                    //skullMeta.getPersistentDataContainer().remove(InventoryUtils.getSkullTexture()); // Don't really need this?
                }

                itemMeta = skullMeta;
            }

            if (Settings.isItemProcessingDisplayName()) {
                if (itemMeta.hasDisplayName()) {
                    String displayName = itemMeta.getDisplayName();
                    itemMeta.setDisplayName(Hooks.processPlaceholders(getPlayer(), displayName));
                }
            }
            if (Settings.isItemProcessingLore()) {
                List<String> processedLore = new ArrayList<>();
                if (itemMeta.hasLore()) {
                    for (String loreLine : itemMeta.getLore()) {
                        processedLore.add(Hooks.processPlaceholders(getPlayer(), loreLine));
                    }
                }
                itemMeta.setLore(processedLore);
            }


            itemMeta.getPersistentDataContainer().set(HMCCInventoryUtils.getCosmeticKey(), PersistentDataType.STRING, cosmetic.getId());
            itemMeta.getPersistentDataContainer().set(InventoryUtils.getOwnerKey(), PersistentDataType.STRING, getEntity().getUniqueId().toString());

            item.setItemMeta(itemMeta);

            CosmeticSlot colorSlot = slotOf(cosmetic) != null ? slotOf(cosmetic) : cosmetic.getSlot();   // giraffe: 등 치장 2 · 3 칸
            if (colors.containsKey(colorSlot)) {
                Color color = colors.get(colorSlot);
                item = NMSHandlers.getHandler().getUtilHandler().setColor(item, color);
            }
        }
        return item;
    }

    public @Nullable UserBalloonManager getBalloonManager() {
        return balloonManagers.isEmpty() ? null : balloonManagers.values().iterator().next();   // giraffe: 첫 칸 (옛 API)
    }

    /** giraffe: 그 칸의 풍선 담당 */
    public @Nullable UserBalloonManager getBalloonManager(@Nullable CosmeticSlot slot) {
        return slot == null ? null : balloonManagers.get(slot);
    }

    /** giraffe: 풍선이 하나라도 있나 */
    public boolean hasBalloonCosmetic() {
        for (CosmeticSlot slot : CosmeticSlot.BALLOON_SLOTS) if (playerCosmetics.containsKey(slot)) return true;
        return false;
    }

    /**
     * giraffe: 그 칸 풍선을 띄우는 자리 (사람 발 기준 세상 좌표 차이) — 늘 몸 뒤 (10-23 사용자: 줄이 몸 앞을 가로질러 거슬림).
     * 몸 방향(body yaw) 기준 뒤로 0.9, 높이는 치장 설정(balloon-offset y, 기본 3). 칸이 여럿이면 좌우로 벌리고 칸마다 0.25 씩 낮춘다
     * (2칸 = 오른쪽 뒤 · 왼쪽 뒤 대칭).
     */
    public @NotNull Vector balloonOffset(@NotNull CosmeticBalloonType type, @Nullable CosmeticSlot slot) {
        Vector base = type.getBalloonOffset();
        int index = slot == null ? 0 : Math.max(0, CosmeticSlot.BALLOON_SLOTS.indexOf(slot));
        int count = CosmeticSlot.BALLOON_SLOTS.size();
        double side = count <= 1 ? 0.45 : 0.45 * (1.0 - 2.0 * index / (count - 1));
        return behindBody(side, base.getY() - 0.25 * index, BALLOON_BACK);
    }

    /** giraffe: 풍선이 몸 뒤로 떨어진 거리 */
    private static final double BALLOON_BACK = 0.9;

    /**
     * giraffe: 몸 방향 기준 (오른쪽 side, 위 up, 뒤 back) → 세상 좌표 차이.
     * 마인크래프트 방향: 앞 = (-sin yaw, 0, cos yaw), 오른쪽 = (-cos yaw, 0, -sin yaw)
     */
    public @NotNull Vector behindBody(double side, double up, double back) {
        Entity entity = getEntity();
        float yaw = entity instanceof LivingEntity living ? living.getBodyYaw() : (entity == null ? 0f : entity.getLocation().getYaw());
        double radians = Math.toRadians(yaw);
        double forwardX = -Math.sin(radians);
        double forwardZ = Math.cos(radians);
        double rightX = -Math.cos(radians);
        double rightZ = -Math.sin(radians);
        return new Vector(rightX * side - forwardX * back, up, rightZ * side - forwardZ * back);
    }

    public @Nullable UserWardrobeManager getWardrobeManager() {
        return userWardrobeManager;
    }

    /**
     * Use {@link #enterWardrobe(Wardrobe, boolean)} instead.
     * @param ignoreDistance
     * @param wardrobe
     */
    @Deprecated(forRemoval = true, since = "2.7.5")
    public void enterWardrobe(boolean ignoreDistance, @NotNull Wardrobe wardrobe) {
        enterWardrobe(wardrobe, ignoreDistance);
    }

    /**
     * This method is used to enter a wardrobe. You can listen to the {@link PlayerWardrobeEnterEvent} to cancel the event or modify any data.
     * @param wardrobe The wardrobe to enter. Use {@link WardrobeSettings#getWardrobe(String)} to get pre-existing wardrobe or use your own by {@link Wardrobe}.
     * @param ignoreDistance If true, the player can enter the wardrobe from any distance. If false, the player must be within the distance set in the wardrobe (If wardrobe has a distance of 0 or lower, the player can enter from any distance).
     */
    public void enterWardrobe(@NotNull Wardrobe wardrobe, boolean ignoreDistance) {
        if (wardrobe.hasPermission() && !getPlayer().hasPermission(wardrobe.getPermission())) {
            MessagesUtil.sendMessage(getPlayer(), "no-permission");
            return;
        }
        if (!wardrobe.canEnter(this) && !ignoreDistance) {
            MessagesUtil.sendMessage(getPlayer(), "not-near-wardrobe");
            return;
        }
        if (!wardrobe.getLocation().hasAllLocations()) {
            MessagesUtil.sendMessage(getPlayer(), "wardrobe-not-setup");
            return;
        }
        PlayerWardrobeEnterEvent event = new PlayerWardrobeEnterEvent(this, wardrobe);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        wardrobe = event.getWardrobe();

        if (userWardrobeManager == null) {
            userWardrobeManager = new UserWardrobeManager(this, wardrobe);
            userWardrobeManager.start();
        }
    }

    /**
     * Use {@link #leaveWardrobe(boolean)} instead.
     */
    @Deprecated(forRemoval = true, since = "2.7.5")
    public void leaveWardrobe() {
        leaveWardrobe(false);
    }

    /**
     * Causes the player to leave the wardrobe. If a player is not in the wardrobe, this will do nothing, use (@{@link #isInWardrobe()} to check if they are).
     * @param ejected If true, the player was ejected from the wardrobe (Skips transition). If false, the player left the wardrobe normally.
     */
    public void leaveWardrobe(boolean ejected) {
        UserWardrobeManager userWardrobe = getWardrobeManager();
        if (userWardrobe == null) return;

        if (userWardrobe.getWardrobeStatus() != UserWardrobeManager.WardrobeStatus.RUNNING) return;
        PlayerWardrobeLeaveEvent event = new PlayerWardrobeLeaveEvent(this, userWardrobe);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        MessagesUtil.sendDebugMessages("Leaving Wardrobe");

        userWardrobe.setWardrobeStatus(UserWardrobeManager.WardrobeStatus.STOPPING);
        userWardrobe.setLastOpenMenu(Menus.getDefaultMenu());

        if (WardrobeSettings.isEnabledTransition() && !ejected) {
            MessagesUtil.sendTitle(
                    getPlayer(),
                    WardrobeSettings.getTransitionText(),
                    WardrobeSettings.getTransitionFadeIn(),
                    WardrobeSettings.getTransitionStay(),
                    WardrobeSettings.getTransitionFadeOut()
            );
            Bukkit.getScheduler().runTaskLater(HMCCosmeticsPlugin.getInstance(), () -> {
                userWardrobeManager.end();
                userWardrobeManager = null;
            }, WardrobeSettings.getTransitionDelay());
        } else {
            userWardrobeManager.end();
            userWardrobeManager = null;
        }
    }

    /**
     * This checks if the player is in a wardrobe. If they are, it will return true, else false. See {@link #getWardrobeManager()} to get the wardrobe manager.
     * @return If the player is in a wardrobe.
     */
    public boolean isInWardrobe() {
        return userWardrobeManager != null;
    }

    public void spawnBackpack(CosmeticBackpackType cosmeticBackpackType) {
        CosmeticSlot slot = slotOf(cosmeticBackpackType);
        spawnBackpack(cosmeticBackpackType, slot != null ? slot : CosmeticSlot.BACKPACK);
    }

    /** giraffe: 그 칸의 등 치장을 그린다 */
    public void spawnBackpack(CosmeticBackpackType cosmeticBackpackType, CosmeticSlot slot) {
        if (backpackManagers.containsKey(slot)) return;
        if (getEntity() == null) return;   // giraffe: 나간 사람 (10-23 NPE)
        UserBackpackManager manager = new UserBackpackManager(this, slot);
        backpackManagers.put(slot, manager);
        manager.spawnBackpack(cosmeticBackpackType);
    }

    public void despawnBackpack() {
        for (CosmeticSlot slot : List.copyOf(backpackManagers.keySet())) despawnBackpack(slot);
    }

    /** giraffe: 그 칸의 등 치장만 치운다 */
    public void despawnBackpack(CosmeticSlot slot) {
        UserBackpackManager manager = backpackManagers.remove(slot);
        if (manager == null) return;
        manager.despawnBackpack();
    }

    public boolean isBackpackSpawned() {
        return !backpackManagers.isEmpty();
    }

    /** giraffe: 첫 번째로 그려진 등 치장 담당 (옛 API — 칸 하나일 때와 같게) */
    public @Nullable UserBackpackManager getUserBackpackManager() {
        return backpackManagers.isEmpty() ? null : backpackManagers.values().iterator().next();
    }

    /** giraffe: 그 칸의 등 치장 담당 */
    public @Nullable UserBackpackManager getUserBackpackManager(@Nullable CosmeticSlot slot) {
        return slot == null ? null : backpackManagers.get(slot);
    }

    /** giraffe: 지금 그려진 등 치장 담당 전부 (칸 순서) */
    public @NotNull Collection<UserBackpackManager> getUserBackpackManagers() {
        return List.copyOf(backpackManagers.values());
    }

    /** giraffe: 등 치장이 하나라도 있나 */
    public boolean hasBackpackCosmetic() {
        for (CosmeticSlot slot : CosmeticSlot.BACKPACK_SLOTS) if (playerCosmetics.containsKey(slot)) return true;
        return false;
    }

    /** giraffe: 칸 → 치장 (저장용 — 같은 종류 칸이 여러 개라 치장의 기본 칸으로는 가를 수 없다) */
    public @NotNull Map<CosmeticSlot, Cosmetic> getCosmeticsBySlot() {
        return Map.copyOf(playerCosmetics);
    }

    /** giraffe: 여러 칸 치장(등 치장 · 풍선)을 넣을 칸 — 이미 착용 중이면 그 칸, 아니면 빈 칸, 다 차 있으면 1번 칸 */
    public @NotNull CosmeticSlot pickMultiSlot(@NotNull Cosmetic cosmetic) {
        CosmeticSlot existing = slotOf(cosmetic);
        if (existing != null) return existing;
        List<CosmeticSlot> slots = CosmeticSlot.multiSlots(cosmetic.getSlot());
        if (slots == null) return cosmetic.getSlot();
        for (CosmeticSlot slot : slots) {
            if (!playerCosmetics.containsKey(slot)) return slot;
        }
        return slots.get(0);
    }

    /** giraffe: 옛 이름 — 등 치장 */
    public @NotNull CosmeticSlot pickBackpackSlot(@NotNull Cosmetic cosmetic) {
        return pickMultiSlot(cosmetic);
    }

    /** giraffe: 여러 칸 치장(등 치장 · 풍선)의 칸이 다 찼나 (이 치장을 이미 착용 중이면 false) */
    public boolean isMultiSlotsFull(@NotNull Cosmetic cosmetic) {
        if (slotOf(cosmetic) != null) return false;
        List<CosmeticSlot> slots = CosmeticSlot.multiSlots(cosmetic.getSlot());
        if (slots == null) return false;
        for (CosmeticSlot slot : slots) {
            if (!playerCosmetics.containsKey(slot)) return false;
        }
        return true;
    }

    /** giraffe: 옛 이름 — 등 치장 */
    public boolean isBackpackSlotsFull(@NotNull Cosmetic cosmetic) {
        return isMultiSlotsFull(cosmetic);
    }

    /**
     * giraffe: 사람 위에 태울 엔티티 목록 — 등 치장 거치대 전부(칸 순서) 다음에 진짜 탑승자.
     * 원래는 등 치장 하나의 거치대만 보내서, 칸마다 보내면 서로 지웠다.
     *
     * @param owner true 면 본인 화면용 — 1인칭 모델이 있는 등 치장은 거치대 대신 구름 사다리 첫 칸
     */
    public int[] getBackpackPassengerIds(boolean owner) {
        List<Integer> ids = new ArrayList<>();
        for (Map.Entry<CosmeticSlot, UserBackpackManager> entry : backpackManagers.entrySet()) {
            UserBackpackManager manager = entry.getValue();
            Cosmetic cosmetic = playerCosmetics.get(entry.getKey());
            boolean firstPerson = owner && cosmetic instanceof CosmeticBackpackType type && type.isFirstPersonCompadible()
                && manager.getAreaEffectEntityId() != null && !manager.getAreaEffectEntityId().isEmpty();
            ids.add(firstPerson ? manager.getAreaEffectEntityId().get(0) : manager.getFirstArmorStandId());
        }
        // giraffe: 풍선 줄 고정점도 사람 위에 태운 것으로 보낸다 (손님 화면에만 있는 거치대)
        for (UserBalloonManager balloonManager : balloonManagers.values()) {
            ids.add(balloonManager.getPufferfish().getLeashAnchorEntityId());
        }
        Entity entity = getEntity();
        if (entity != null) {
            for (Entity passenger : entity.getPassengers()) ids.add(passenger.getEntityId());
        }
        return ids.stream().mapToInt(Integer::intValue).toArray();
    }

    public boolean isBalloonSpawned() {
        return !balloonManagers.isEmpty();
    }

    public void spawnBalloon(CosmeticBalloonType cosmeticBalloonType) {
        CosmeticSlot slot = slotOf(cosmeticBalloonType);
        spawnBalloon(cosmeticBalloonType, slot != null ? slot : CosmeticSlot.BALLOON);
    }

    /** giraffe: 그 칸의 풍선을 띄운다 — 자리는 {@link #balloonOffset} */
    public void spawnBalloon(CosmeticBalloonType cosmeticBalloonType, CosmeticSlot slot) {
        if (balloonManagers.containsKey(slot)) return;

        Entity entity = getEntity();
        if (entity == null) return;   // giraffe: 나간 사람

        UserBalloonManager userBalloonManager1 = new UserBalloonManager(this, entity.getLocation());
        userBalloonManager1.getModelEntity().teleport(entity.getLocation().add(balloonOffset(cosmeticBalloonType, slot)));

        userBalloonManager1.spawnModel(cosmeticBalloonType, getCosmeticColor(slot));
        userBalloonManager1.addPlayerToModel(this, cosmeticBalloonType, getCosmeticColor(slot));

        balloonManagers.put(slot, userBalloonManager1);
    }

    public void despawnBalloon() {
        for (CosmeticSlot slot : List.copyOf(balloonManagers.keySet())) despawnBalloon(slot);
    }

    /** giraffe: 그 칸의 풍선만 치운다 */
    public void despawnBalloon(CosmeticSlot slot) {
        UserBalloonManager manager = balloonManagers.remove(slot);
        if (manager == null) return;
        manager.remove();
    }

    public void respawnBackpack() {
        if (!hasBackpackCosmetic()) return;
        despawnBackpack();
        if (!hiddenReason.isEmpty()) return;
        if (getEntity() == null) return;   // giraffe: 1틱 뒤 다시 그리기 사이에 나가면 사람이 없다(10-23 NPE)
        for (CosmeticSlot slot : CosmeticSlot.BACKPACK_SLOTS) {
            if (getCosmetic(slot) instanceof CosmeticBackpackType backpackType) spawnBackpack(backpackType, slot);
        }
        MessagesUtil.sendDebugMessages("Respawned Backpack for " + getEntity().getName());
    }

    public void respawnBalloon() {
        if (!hasBalloonCosmetic()) return;
        despawnBalloon();
        if (!hiddenReason.isEmpty()) return;
        if (getEntity() == null) return;   // giraffe: 나간 사람
        for (CosmeticSlot slot : CosmeticSlot.BALLOON_SLOTS) {   // giraffe: 칸마다
            if (getCosmetic(slot) instanceof CosmeticBalloonType balloonType) spawnBalloon(balloonType, slot);
        }
        MessagesUtil.sendDebugMessages("Respawned Balloon for " + getEntity().getName());
    }

    public void removeArmor(CosmeticSlot slot) {
        EquipmentSlot equipmentSlot = HMCCInventoryUtils.getEquipmentSlot(slot);
        if (equipmentSlot == null) return;
        if (getPlayer() != null) {
            HMCCPacketManager.equipmentSlotUpdate(getEntity().getEntityId(), equipmentSlot, getPlayer().getInventory().getItem(equipmentSlot), HMCCPacketManager.getViewers(getEntity().getLocation()));
        } else {
            HMCCPacketManager.equipmentSlotUpdate(getEntity().getEntityId(), this, slot, HMCCPacketManager.getViewers(getEntity().getLocation()));
        }
    }

    /**
     * This returns the player associated with the user. Some users may not have a player attached, ie, they are npcs
     * wearing cosmetics through an addon. If you need to get locations, use getEntity instead.
     * @return Player
     */
    @Nullable
    public Player getPlayer() {
        return Bukkit.getPlayer(uniqueId);
    }

    /**
     * This gets the entity associated with the user.
     * @return Entity
     */
    public Entity getEntity() {
        return getPlayer();
    }

    public Color getCosmeticColor(CosmeticSlot slot) {
        return colors.get(slot);
    }

    public List<CosmeticSlot> getDyeableSlots() {
        ArrayList<CosmeticSlot> dyeableSlots = new ArrayList<>();

        for (Cosmetic cosmetic : playerCosmetics.values()) {
            if (cosmetic.isDyeable()) dyeableSlots.add(cosmetic.getSlot());
        }

        return dyeableSlots;
    }

    @Override
    public boolean canEquipCosmetic(@NotNull Cosmetic cosmetic, boolean ignoreWardrobe) {
        if (!cosmetic.requiresPermission()) return true;
        if (isInWardrobe() && !ignoreWardrobe) {
            if (WardrobeSettings.isTryCosmeticsInWardrobe() && userWardrobeManager.getWardrobeStatus().equals(UserWardrobeManager.WardrobeStatus.RUNNING)) return true;
        }
        final Player player = getPlayer();
        if (player != null) return player.hasPermission(cosmetic.getPermission());
        // This sucks, but basically if we can find a player, use that. If not, try to find the entity. If it can't find the entity, just return false.
        final Entity entity = getEntity();
        if (entity != null) return entity.hasPermission(cosmetic.getPermission());
        return false;
    }

    public void hidePlayer() {
        Player player = getPlayer();
        if (player == null) return;
        for (final Player p : Bukkit.getOnlinePlayers()) {
            p.hidePlayer(HMCCosmeticsPlugin.getInstance(), player);
            player.hidePlayer(HMCCosmeticsPlugin.getInstance(), p);
        }
    }

    public void showPlayer() {
        Player player = getPlayer();
        if (player == null) return;
        for (final Player p : Bukkit.getOnlinePlayers()) {
            p.showPlayer(HMCCosmeticsPlugin.getInstance(), player);
            player.showPlayer(HMCCosmeticsPlugin.getInstance(), p);
        }
    }

    public void hideCosmetics(@NotNull HiddenReason reason) {
        if (hiddenReason.contains(reason)) return;

        PlayerCosmeticHideEvent event = new PlayerCosmeticHideEvent(this, reason);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        hiddenReason.add(reason);
        if (hasBalloonCosmetic()) {
            despawnBalloon();
            //getBalloonManager().removePlayerFromModel(getPlayer());
            //getBalloonManager().sendRemoveLeashPacket();
        }
        if (hasBackpackCosmetic()) {
            despawnBackpack();
        }
        updateCosmetic();
        MessagesUtil.sendDebugMessages("HideCosmetics");
    }

    /**
     * This is used to silently add a hidden flag to the user. This will not trigger any events or checks, nor do anything else
     * @param reason
     */
    public void silentlyAddHideFlag(@NotNull HiddenReason reason) {
        if (!hiddenReason.contains(reason)) hiddenReason.add(reason);
    }

    public void showCosmetics(@NotNull HiddenReason reason) {
        if (hiddenReason.isEmpty()) return;
        if (!hiddenReason.contains(reason)) return;

        PlayerCosmeticShowEvent event = new PlayerCosmeticShowEvent(this, reason);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        hiddenReason.remove(reason);
        if (isHidden()) return;
        if (hasBalloonCosmetic()) {
            if (!isBalloonSpawned()) respawnBalloon();
            List<Player> viewer = HMCCPacketManager.getViewers(getEntity().getLocation());
            for (CosmeticSlot slot : CosmeticSlot.BALLOON_SLOTS) {   // giraffe: 칸마다
                UserBalloonManager manager = balloonManagers.get(slot);
                if (manager == null || !(getCosmetic(slot) instanceof CosmeticBalloonType balloonType)) continue;
                manager.addPlayerToModel(this, balloonType);
                HMCCPacketManager.sendLeashPacket(manager.getPufferfishBalloonId(), manager.getLeashHolderId(getPlayer()), viewer);   // giraffe: 줄은 등 뒤 고정점
            }
        }
        if (hasBackpackCosmetic()) {
            if (!isBackpackSpawned()) respawnBackpack();
            for (CosmeticSlot slot : CosmeticSlot.BACKPACK_SLOTS) {
                UserBackpackManager manager = backpackManagers.get(slot);
                if (manager != null && getCosmetic(slot) instanceof CosmeticBackpackType backpackType) {
                    manager.setItem(getUserCosmeticItem(backpackType));
                }
            }
        }
        updateCosmetic();
        MessagesUtil.sendDebugMessages("ShowCosmetics");
    }

    public boolean isHidden() {
        return !hiddenReason.isEmpty();
    }

    public boolean isHidden(HiddenReason reason) {
        return hiddenReason.contains(reason);
    }

    public @NotNull List<HiddenReason> getHiddenReasons() {
        return hiddenReason;
    }

    public void clearHiddenReasons() {
        hiddenReason.clear();
    }

    public enum HiddenReason {
        NONE,
        WORLDGUARD,
        PLUGIN,
        VANISH,
        POTION,
        ACTION,
        COMMAND,
        EMOTE,
        GAMEMODE,
        WORLD,
        DISABLED
    }
}
