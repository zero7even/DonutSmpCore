package com.bx.ultimateDonutSmp.utils;

import org.bukkit.entity.Player;

public class PlayerContext {

    private static final ThreadLocal<Player> currentTargetPlayer = new ThreadLocal<>();

    public static void set(Player player) {
        currentTargetPlayer.set(player);
    }

    public static Player get() {
        return currentTargetPlayer.get();
    }

    public static void clear() {
        currentTargetPlayer.remove();
    }
}
