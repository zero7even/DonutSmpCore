package com.bx.ultimateDonutSmp.utils;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

public final class TablistComponentUpdater {

    private static final String[] PLAYER_INFO_UPDATE_PACKET_CLASS_NAMES = {
            "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket",
            "net.minecraft.network.protocol.game.PacketPlayOutPlayerInfo"
    };

    private final UltimateDonutSmp plugin;
    private boolean warned;
    private boolean disabled;

    public TablistComponentUpdater(UltimateDonutSmp plugin) {
        this.plugin = plugin;
    }

    public boolean updateName(Player target, String componentJson) {
        if (disabled || target == null || !target.isOnline()
                || componentJson == null || componentJson.isBlank()) {
            return false;
        }

        try {
            Object handle = invokeNoArg(target, "getHandle");
            Object component = parseNativeComponent(componentJson);
            setTabListDisplayName(handle, component);
            Object packet = createDisplayNamePacket(handle);
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer != null && viewer.isOnline()) {
                    sendPacket(viewer, packet);
                }
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            disableWithWarning(exception);
            return false;
        }
    }

    private Object parseNativeComponent(String json) throws ReflectiveOperationException {
        Object component = parseWithCraftChatMessage(json);
        if (component != null) {
            return component;
        }
        return parseWithMinecraftSerializer(json);
    }

    private Object parseWithCraftChatMessage(String json) throws ReflectiveOperationException {
        Class<?> type;
        try {
            type = Class.forName("org.bukkit.craftbukkit.util.CraftChatMessage",
                    false, plugin.getServer().getClass().getClassLoader());
        } catch (ClassNotFoundException exception) {
            return null;
        }

        for (String name : List.of("fromJSON", "fromJson", "fromJSONOrNull", "fromJsonOrNull")) {
            for (Method method : type.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers())
                        || !method.getName().equals(name)
                        || method.getParameterCount() != 1
                        || method.getParameterTypes()[0] != String.class) {
                    continue;
                }
                method.setAccessible(true);
                Object value = method.invoke(null, json);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private Object parseWithMinecraftSerializer(String json) throws ReflectiveOperationException {
        ClassLoader loader = plugin.getServer().getClass().getClassLoader();
        Class<?> componentType = Class.forName("net.minecraft.network.chat.Component", false, loader);
        Class<?> serializerType = Class.forName("net.minecraft.network.chat.Component$Serializer", false, loader);

        for (Method method : serializerType.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())
                    || method.getParameterCount() != 1
                    || method.getParameterTypes()[0] != String.class
                    || !componentType.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            String name = method.getName().toLowerCase(Locale.ROOT);
            if (!name.contains("json") && !name.equals("a")) {
                continue;
            }
            method.setAccessible(true);
            Object value = method.invoke(null, json);
            if (value != null) {
                return value;
            }
        }

        throw new NoSuchMethodException("Component JSON parser");
    }

    private void setTabListDisplayName(Object handle, Object component) throws ReflectiveOperationException {
        Method method = findDisplayNameSetter(handle.getClass(), component);
        if (method != null) {
            method.setAccessible(true);
            method.invoke(handle, component);
            return;
        }

        Field field = findDisplayNameField(handle, component);
        if (field != null) {
            field.setAccessible(true);
            field.set(handle, component);
            return;
        }

        throw new NoSuchFieldException("ServerPlayer tablist display name");
    }

    private Method findDisplayNameSetter(Class<?> type, Object component) {
        Method fallback = null;
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getParameterCount() != 1
                        || !isVoidLike(method.getReturnType())
                        || !method.getParameterTypes()[0].isAssignableFrom(component.getClass())) {
                    continue;
                }
                String name = method.getName().toLowerCase(Locale.ROOT);
                if (name.contains("tab") && name.contains("list") && name.contains("name")) {
                    return method;
                }
                if ((name.contains("display") && name.contains("name"))
                        || (name.contains("list") && name.contains("name"))) {
                    fallback = method;
                }
            }
        }
        return fallback;
    }

    private Field findDisplayNameField(Object handle, Object component) {
        Field fallback = null;
        Field nullableFallback = null;
        Field componentFallback = null;
        Class<?> type = handle.getClass();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isFinal(field.getModifiers())
                        || !field.getType().isAssignableFrom(component.getClass())) {
                    continue;
                }
                if (componentFallback == null) {
                    componentFallback = field;
                }
                String name = field.getName().toLowerCase(Locale.ROOT);
                if (name.contains("tab") && name.contains("list") && name.contains("name")) {
                    return field;
                }
                if ((name.contains("display") && name.contains("name"))
                        || (name.contains("list") && name.contains("name"))) {
                    fallback = field;
                    continue;
                }
                if (nullableFallback == null && isNullField(handle, field)) {
                    nullableFallback = field;
                }
            }
        }
        if (fallback != null) {
            return fallback;
        }
        return nullableFallback != null ? nullableFallback : componentFallback;
    }

    private boolean isNullField(Object handle, Field field) {
        try {
            field.setAccessible(true);
            return field.get(handle) == null;
        } catch (RuntimeException exception) {
            return false;
        } catch (IllegalAccessException exception) {
            return false;
        }
    }

    private Object createDisplayNamePacket(Object handle) throws ReflectiveOperationException {
        Class<?> packetClass = getFirstAvailableClass(PLAYER_INFO_UPDATE_PACKET_CLASS_NAMES);
        Object action = findAction(packetClass, "UPDATE_DISPLAY_NAME", "UPDATE_DISPLAY");
        if (action == null) {
            throw new NoSuchFieldException("UPDATE_DISPLAY_NAME action");
        }
        return instantiateActionPacket(packetClass, action, handle);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object instantiateActionPacket(Class<?> packetClass, Object action, Object handle)
            throws ReflectiveOperationException {
        EnumSet actionSet = null;
        if (action instanceof Enum enumAction) {
            actionSet = EnumSet.noneOf(enumAction.getDeclaringClass());
            actionSet.add(enumAction);
        }
        List<Object> handles = List.of(handle);

        for (Constructor<?> constructor : packetClass.getDeclaredConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length != 2) {
                continue;
            }
            constructor.setAccessible(true);

            if (actionSet != null
                    && EnumSet.class.isAssignableFrom(parameters[0])
                    && (Collection.class.isAssignableFrom(parameters[1])
                    || Iterable.class.isAssignableFrom(parameters[1]))) {
                return constructor.newInstance(actionSet, handles);
            }
            if (actionSet != null
                    && EnumSet.class.isAssignableFrom(parameters[1])
                    && (Collection.class.isAssignableFrom(parameters[0])
                    || Iterable.class.isAssignableFrom(parameters[0]))) {
                return constructor.newInstance(handles, actionSet);
            }
            if (parameters[0].isAssignableFrom(action.getClass()) && parameters[1].isArray()) {
                Object array = Array.newInstance(parameters[1].getComponentType(), 1);
                Array.set(array, 0, handle);
                return constructor.newInstance(action, array);
            }
            if (parameters[1].isAssignableFrom(action.getClass()) && parameters[0].isArray()) {
                Object array = Array.newInstance(parameters[0].getComponentType(), 1);
                Array.set(array, 0, handle);
                return constructor.newInstance(array, action);
            }
            if (parameters[0].isAssignableFrom(action.getClass())
                    && (Collection.class.isAssignableFrom(parameters[1])
                    || Iterable.class.isAssignableFrom(parameters[1]))) {
                return constructor.newInstance(action, handles);
            }
            if (parameters[1].isAssignableFrom(action.getClass())
                    && (Collection.class.isAssignableFrom(parameters[0])
                    || Iterable.class.isAssignableFrom(parameters[0]))) {
                return constructor.newInstance(handles, action);
            }
        }

        throw new NoSuchMethodException(packetClass.getName() + "(UPDATE_DISPLAY_NAME,ServerPlayer)");
    }

    private Object findAction(Class<?> packetClass, String... names) {
        for (Class<?> nested : packetClass.getDeclaredClasses()) {
            if (!nested.isEnum()) {
                continue;
            }
            Object[] constants = nested.getEnumConstants();
            if (constants == null) {
                continue;
            }
            for (String name : names) {
                for (Object constant : constants) {
                    if (((Enum<?>) constant).name().equalsIgnoreCase(name)) {
                        return constant;
                    }
                }
            }
        }
        return null;
    }

    private void sendPacket(Player player, Object packet) throws ReflectiveOperationException {
        Object handle = invokeNoArg(player, "getHandle");
        PacketSender sender = findPacketSender(handle, packet);
        if (sender == null) {
            throw new NoSuchMethodException("packet sender");
        }
        sender.send(packet);
    }

    private PacketSender findPacketSender(Object root, Object packet) throws ReflectiveOperationException {
        PacketSender sender = findSenderOn(root, packet);
        if (sender != null) {
            return sender;
        }

        for (Class<?> current = root.getClass(); current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(root);
                if (value == null) {
                    continue;
                }
                sender = findSenderOn(value, packet);
                if (sender != null) {
                    return sender;
                }
            }
        }

        return null;
    }

    private PacketSender findSenderOn(Object target, Object packet) {
        PacketSender fallback = null;
        for (Class<?> current = target.getClass(); current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length == 1 && acceptsPacket(parameters[0], packet)) {
                    PacketSender sender = new PacketSender(target, method, false);
                    if (isPreferredSendMethod(method)) {
                        return sender;
                    }
                    fallback = sender;
                }
                if (parameters.length == 2 && acceptsPacket(parameters[0], packet)) {
                    PacketSender sender = new PacketSender(target, method, true);
                    if (isPreferredSendMethod(method)) {
                        return sender;
                    }
                    fallback = sender;
                }
            }
        }
        return fallback;
    }

    private boolean acceptsPacket(Class<?> parameterType, Object packet) {
        return parameterType.isAssignableFrom(packet.getClass())
                || "net.minecraft.network.protocol.Packet".equals(parameterType.getName())
                || "Packet".equals(parameterType.getSimpleName());
    }

    private boolean isPreferredSendMethod(Method method) {
        String name = method.getName().toLowerCase(Locale.ROOT);
        return "send".equals(name) || "sendpacket".equals(name) || "a".equals(name);
    }

    private Object invokeNoArg(Object target, String name) throws ReflectiveOperationException {
        Method method = findNoArgMethod(target.getClass(), name);
        if (method == null) {
            throw new NoSuchMethodException(name);
        }
        method.setAccessible(true);
        return method.invoke(target);
    }

    private Method findNoArgMethod(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getParameterCount() == 0 && method.getName().equals(name)) {
                    return method;
                }
            }
        }
        return null;
    }

    private Class<?> getFirstAvailableClass(String[] classNames) throws ClassNotFoundException {
        ClassLoader loader = plugin.getServer().getClass().getClassLoader();
        for (String className : classNames) {
            try {
                return Class.forName(className, false, loader);
            } catch (ClassNotFoundException ignored) {
            }
        }
        throw new ClassNotFoundException(String.join(", ", classNames));
    }

    private boolean isVoidLike(Class<?> type) {
        return type == Void.TYPE || type == Void.class;
    }

    private void disableWithWarning(Exception exception) {
        disabled = true;
        if (warned) {
            return;
        }
        warned = true;
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        plugin.getLogger().warning("[Tablist] Unable to send Adventure tablist name components on this Spigot build: "
                + cause.getClass().getSimpleName() + ": " + cause.getMessage());
    }

    private static final class PacketSender {

        private final Object target;
        private final Method method;
        private final boolean trailingNull;

        private PacketSender(Object target, Method method, boolean trailingNull) {
            this.target = target;
            this.method = method;
            this.trailingNull = trailingNull;
        }

        private void send(Object packet) throws ReflectiveOperationException {
            method.setAccessible(true);
            if (trailingNull) {
                method.invoke(target, packet, null);
            } else {
                method.invoke(target, packet);
            }
        }
    }
}
