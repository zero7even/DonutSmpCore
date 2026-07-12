package com.bx.ultimateDonutSmp.listeners;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.utils.PlayerContext;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class PlayerContextListener implements Listener {

    private final UltimateDonutSmp plugin;

    public PlayerContextListener(UltimateDonutSmp plugin) {
        this.plugin = plugin;
    }

    // Setters at LOWEST (runs first)
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandLowest(PlayerCommandPreprocessEvent event) {
        PlayerContext.set(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClickLowest(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            PlayerContext.set(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpenLowest(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            PlayerContext.set(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractLowest(PlayerInteractEvent event) {
        PlayerContext.set(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChatLowest(AsyncPlayerChatEvent event) {
        PlayerContext.set(event.getPlayer());
    }

    // Clearers at MONITOR (runs last)
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommandMonitor(PlayerCommandPreprocessEvent event) {
        PlayerContext.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClickMonitor(InventoryClickEvent event) {
        PlayerContext.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpenMonitor(InventoryOpenEvent event) {
        PlayerContext.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteractMonitor(PlayerInteractEvent event) {
        PlayerContext.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChatMonitor(AsyncPlayerChatEvent event) {
        PlayerContext.clear();
    }
}
