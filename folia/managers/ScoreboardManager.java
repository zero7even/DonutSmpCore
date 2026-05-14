package com.bx.ultimateDonutSmp.managers;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.models.PlayerData;
import com.bx.ultimateDonutSmp.utils.ColorUtils;
import com.bx.ultimateDonutSmp.utils.PacketSidebarRenderer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ScoreboardManager {

    private static final int MAX_LINES = 15;

    private final UltimateDonutSmp plugin;
    private final PacketSidebarRenderer sidebarRenderer;
    private int titleIndex = 0;

    public ScoreboardManager(UltimateDonutSmp plugin) {
        this.plugin = plugin;
        this.sidebarRenderer = new PacketSidebarRenderer(plugin);
    }

    public boolean isEnabled() {
        return plugin.getFeatureManager().isEnabled(FeatureManager.Feature.SCOREBOARD)
                && plugin.getConfigManager().getScoreboard().getBoolean("SCOREBOARD.ENABLED", true);
    }

    public void applyVisibility(Player player) {
        if (!isEnabled() || !isVisibleFor(player)) {
            hidePlayer(player);
            return;
        }

        update(player);
    }

    public void setupPlayer(Player player) {
        update(player);
    }

    public void removePlayer(UUID uuid) {
        sidebarRenderer.remove(uuid);
    }

    public void update(Player player) {
        if (!isEnabled() || !isVisibleFor(player)) {
            hidePlayer(player);
            return;
        }

        sidebarRenderer.show(player, currentTitle(player), visibleLines(player));
    }

    private String currentTitle(Player player) {
        List<String> titles = plugin.getConfigManager().getScoreboard()
                .getStringList("SCOREBOARD.TITLE");
        if (titles.isEmpty()) {
            return ColorUtils.colorize("EconomySMP", player);
        }

        return ColorUtils.colorize(titles.get(titleIndex % titles.size()), player);
    }

    private List<String> visibleLines(Player player) {
        List<String> configuredLines = getLines(player);
        List<String> visible = new ArrayList<>();
        for (String line : configuredLines) {
            if (visible.size() >= MAX_LINES) {
                break;
            }
            visible.add(ColorUtils.colorize(line, player));
        }
        return visible;
    }

    private List<String> getLines(Player player) {
        FileConfiguration scoreboard = plugin.getConfigManager().getScoreboard();
        List<String> lines = new ArrayList<>();
        String teamLine = scoreboard.getString("SCOREBOARD.TEAM");
        String boosterLine = scoreboard.getString("SCOREBOARD.SHARD-BOOSTER");
        String shardCuboidLine = scoreboard.getString("SCOREBOARD.SHARD-CUBOID");
        boolean hasBooster = plugin.getShardManager().hasBooster(player.getUniqueId());
        boolean showShardCuboid = plugin.getShardManager().shouldShowShardCuboidLine(player.getUniqueId());

        for (String line : scoreboard.getStringList("SCOREBOARD.LINES")) {
            String resolved = resolveConfiguredLine(
                    line,
                    teamLine,
                    boosterLine,
                    shardCuboidLine,
                    hasBooster,
                    showShardCuboid
            );
            if (resolved != null) {
                lines.add(resolved);
            }
        }

        return lines;
    }

    private String resolveConfiguredLine(
            String line,
            String teamLine,
            String boosterLine,
            String shardCuboidLine,
            boolean hasBooster,
            boolean showShardCuboid
    ) {
        if (line == null) {
            return null;
        }

        String trimmed = line.trim();
        if ("{team}".equalsIgnoreCase(trimmed)) {
            return teamLine;
        }
        if ("{shard_booster}".equalsIgnoreCase(trimmed)) {
            return hasBooster ? boosterLine : null;
        }
        if ("{shard_cuboid}".equalsIgnoreCase(trimmed)) {
            return showShardCuboid ? shardCuboidLine : null;
        }
        return line;
    }

    public void updateAll() {
        List<String> titles = plugin.getConfigManager().getScoreboard()
                .getStringList("SCOREBOARD.TITLE");
        if (!titles.isEmpty()) {
            titleIndex = (titleIndex + 1) % titles.size();
        }

        plugin.getFoliaScheduler().forEachOnlinePlayer(this::update);
    }

    private boolean isVisibleFor(Player player) {
        PlayerData data = plugin.getPlayerDataManager().get(player);
        return data == null || data.isScoreboardVisible();
    }

    private void hidePlayer(Player player) {
        sidebarRenderer.hide(player);
    }
}
