package com.bx.ultimateDonutSmp.api;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.managers.LeaderboardManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EconomyRankExpansion extends PlaceholderExpansion {

    private final UltimateDonutSmp plugin;

    public EconomyRankExpansion(UltimateDonutSmp plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "economyrank";
    }

    @Override
    public @NotNull String getAuthor() {
        return "UltimateDonutSmp";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || params.isBlank()) {
            return "0";
        }

        String typeKey = params;
        if (typeKey.startsWith("_")) {
            typeKey = typeKey.substring(1);
        }

        LeaderboardManager.LeaderboardType type = plugin.getLeaderboardManager().parseType(typeKey).orElse(null);
        if (type == null) {
            return "0";
        }

        LeaderboardManager.LeaderboardEntry entry = plugin.getLeaderboardManager().getPlayerEntry(player.getUniqueId(), type);
        return entry == null ? "0" : String.valueOf(entry.position());
    }
}
