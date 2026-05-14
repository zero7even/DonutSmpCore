package com.bx.ultimateDonutSmp.commands;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.managers.FeatureManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class FeatureCommandExecutor implements CommandExecutor {

    private final UltimateDonutSmp plugin;
    private final CommandExecutor delegate;
    private final FeatureManager.Feature[] requiredFeatures;

    public FeatureCommandExecutor(
            UltimateDonutSmp plugin,
            CommandExecutor delegate,
            FeatureManager.Feature... requiredFeatures
    ) {
        this.plugin = plugin;
        this.delegate = delegate;
        this.requiredFeatures = requiredFeatures == null ? new FeatureManager.Feature[0] : requiredFeatures;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        for (FeatureManager.Feature feature : requiredFeatures) {
            if (feature != null && !plugin.getFeatureManager().isEnabled(feature)) {
                plugin.getFeatureManager().sendDisabledMessage(sender, feature, label);
                return true;
            }
        }
        return delegate.onCommand(sender, command, label, args);
    }
}
