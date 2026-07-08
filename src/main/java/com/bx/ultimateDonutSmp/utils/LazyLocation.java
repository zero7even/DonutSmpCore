package com.bx.ultimateDonutSmp.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class LazyLocation extends Location {

    private final String worldName;

    public LazyLocation(String worldName, double x, double y, double z, float yaw, float pitch) {
        super(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
        this.worldName = worldName;
    }

    @Override
    public World getWorld() {
        World w = super.getWorld();
        if (w == null && worldName != null) {
            w = Bukkit.getWorld(worldName);
            if (w != null) {
                setWorld(w);
            }
        }
        return w;
    }

    public String getWorldName() {
        return worldName;
    }

    @Override
    public LazyLocation clone() {
        return (LazyLocation) super.clone();
    }
}
