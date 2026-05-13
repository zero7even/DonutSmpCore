package com.bx.ultimateDonutSmp.utils;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public final class ItemSerializationUtils {

    private ItemSerializationUtils() {
    }

    public static String serialize(ItemStack item) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeObject(item);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    public static ItemStack deserialize(String encoded) throws IOException, ClassNotFoundException {
        byte[] bytes = Base64.getDecoder().decode(encoded);
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object value = input.readObject();
            return value instanceof ItemStack item ? item : null;
        }
    }
}
