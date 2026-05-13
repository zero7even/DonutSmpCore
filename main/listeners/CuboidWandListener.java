package com.bx.ultimateDonutSmp.listeners;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.utils.ColorUtils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class CuboidWandListener implements Listener {

    private static final String WAND_NAME = "&6Cuboid Wand";

    private final UltimateDonutSmp plugin;

    public CuboidWandListener(UltimateDonutSmp plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("ultimatedonutsmp.admin.cuboid")) {
            return;
        }
        if (!isCuboidWand(event.getItem())) {
            return;
        }

        event.setCancelled(true);

        if (action == Action.LEFT_CLICK_BLOCK) {
            plugin.getCuboidManager().setPos1(player.getUniqueId(), clicked.getLocation());
            player.sendMessage(ColorUtils.toComponent("&aFirst position set at &f" + formatLoc(clicked)));
            handleSelectionCompleted(player);
            return;
        }

        plugin.getCuboidManager().setPos2(player.getUniqueId(), clicked.getLocation());
        player.sendMessage(ColorUtils.toComponent("&aSecond position set at &f" + formatLoc(clicked)));
        handleSelectionCompleted(player);
    }

    public static boolean isCuboidWand(ItemStack item) {
        if (item == null || item.getType() != Material.GOLDEN_SHOVEL) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }

        String displayName = meta.getDisplayName();
        return displayName.contains("Cuboid Wand");
    }

    public static String getWandName() {
        return WAND_NAME;
    }

    private void handleSelectionCompleted(Player player) {
        if (!plugin.getCuboidManager().hasFullSelection(player.getUniqueId())) {
            return;
        }

        removeWandFromInventory(player);
        player.sendMessage(ColorUtils.toComponent("&aSelection complete. &7Cuboid Wand removed from your inventory."));

        TextComponent guideMessage = ColorUtils.toBaseComponent("&eStep 3/3 &7Type ");
        TextComponent commandPart = ColorUtils.toBaseComponent("&f/cuboid create <name>");
        commandPart.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/cuboid create "));
        commandPart.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                ColorUtils.toBaseComponents("&7Click to autofill the create command.")
        ));
        guideMessage.addExtra(commandPart);
        player.spigot().sendMessage(guideMessage);
    }

    private void removeWandFromInventory(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (isCuboidWand(item)) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    private String formatLoc(Block block) {
        return "X:" + block.getX() + " Y:" + block.getY() + " Z:" + block.getZ();
    }
}
