package com.bx.ultimateDonutSmp.listeners;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.utils.ColorUtils;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;

public class SpawnerBlockListener implements Listener {

    private final UltimateDonutSmp plugin;

    public SpawnerBlockListener(UltimateDonutSmp plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getSpawnerManager().isEnabled()) {
            return;
        }
        ItemStack item = event.getItemInHand();
        if (!plugin.getSpawnerManager().isSpawnerItem(item)) {
            return;
        }

        var result = plugin.getSpawnerManager().placeSpawner(event.getPlayer(), event.getBlockPlaced(), item);
        if (!result.success()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ColorUtils.toComponent(result.message()));
            return;
        }

        if (event.getPlayer().isSneaking() && event.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE) {
            int expectedAmount = event.getItemInHand().getAmount() - 1;
            org.bukkit.inventory.EquipmentSlot slot = event.getHand();
            plugin.getSpigotScheduler().runEntity(event.getPlayer(), () -> {
                org.bukkit.entity.Player p = event.getPlayer();
                org.bukkit.inventory.ItemStack current = slot == org.bukkit.inventory.EquipmentSlot.HAND ? p.getInventory().getItemInMainHand() : p.getInventory().getItemInOffHand();
                if (current != null && current.getAmount() == expectedAmount && plugin.getSpawnerManager().isSpawnerItem(current)) {
                    if (slot == org.bukkit.inventory.EquipmentSlot.HAND) {
                        p.getInventory().setItemInMainHand(null);
                    } else {
                        p.getInventory().setItemInOffHand(null);
                    }
                    p.updateInventory();
                }
            });
        }

        event.getPlayer().sendMessage(ColorUtils.toComponent(result.message()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getSpawnerManager().isEnabled()) {
            return;
        }
        Block block = event.getBlock();
        if (plugin.getSpawnStashManager() != null && plugin.getSpawnStashManager().isActiveBlock(block)) {
            return;
        }
        if (plugin.getSpawnerManager().getSpawner(block) == null) {
            return;
        }

        Player player = event.getPlayer();
        var result = plugin.getSpawnerManager().breakSpawner(player, block);
        if (!result.success()) {
            event.setCancelled(true);
            player.sendMessage(ColorUtils.toComponent(result.message()));
            return;
        }

        event.setDropItems(false);
        event.setExpToDrop(0);
        player.sendMessage(ColorUtils.toComponent(result.message()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        if (!plugin.getSpawnerManager().isEnabled()) {
            return;
        }
        if (plugin.getSpawnerManager().getSpawner(event.getSpawner().getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!plugin.getSpawnerManager().isEnabled()) {
            return;
        }
        filterManagedSpawners(event.blockList().iterator());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!plugin.getSpawnerManager().isEnabled()) {
            return;
        }
        filterManagedSpawners(event.blockList().iterator());
    }

    private void filterManagedSpawners(Iterator<Block> iterator) {
        while (iterator.hasNext()) {
            Block block = iterator.next();
            if (plugin.getSpawnStashManager() != null && plugin.getSpawnStashManager().isActiveBlock(block)) {
                continue;
            }
            if (block.getType() == Material.SPAWNER && plugin.getSpawnerManager().getSpawner(block) != null) {
                iterator.remove();
            }
        }
    }
}
