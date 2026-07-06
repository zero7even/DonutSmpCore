package com.bx.ultimateDonutSmp.tasks;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;

public final class AuctionOrderBotTask implements Runnable {

    private final UltimateDonutSmp plugin;

    private AuctionOrderBotTask(UltimateDonutSmp plugin) {
        this.plugin = plugin;
    }

    public static void start(UltimateDonutSmp plugin) {
        // Tick every 20 seconds
        long periodTicks = 400L; 
        plugin.getSpigotScheduler().runAsyncTimer(new AuctionOrderBotTask(plugin), periodTicks, periodTicks);
    }

    @Override
    public void run() {
        if (plugin.getAuctionOrderBotManager() != null) {
            plugin.getAuctionOrderBotManager().tick();
        }
    }
}
