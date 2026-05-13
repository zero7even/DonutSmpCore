package com.bx.ultimateDonutSmp.managers;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.utils.ColorUtils;
import com.bx.ultimateDonutSmp.utils.TablistComponentUpdater;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TablistManager {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final GsonComponentSerializer GSON_COMPONENT_SERIALIZER = GsonComponentSerializer.gson();
    private static final Pattern HEAD_TAG_PATTERN = Pattern.compile("(?i)<head:[^>\\r\\n]*>");
    private static final Pattern GRADIENT_TAG_PATTERN = Pattern.compile(
            "<#([A-Fa-f0-9]{6})>(.*?)</#([A-Fa-f0-9]{6})>",
            Pattern.DOTALL
    );
    private static final Pattern LEGACY_HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern LEGACY_CODE_PATTERN = Pattern.compile("[&\\u00A7]([0-9A-FK-ORa-fk-or])");
    private static final List<Long> DEFAULT_SKIN_HEAD_REFRESH_DELAYS = List.of(5L, 40L, 100L);
    private static final long SKIN_HEAD_TEXTURE_REFRESH_INTERVAL_MS = 60_000L;
    private static final long SKIN_HEAD_TEXTURE_RETRY_INTERVAL_MS = 15_000L;

    private final UltimateDonutSmp plugin;
    private final TablistComponentUpdater componentUpdater;
    private final Set<UUID> refreshedSkinHeads = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingSkinHeadTextureRefreshes = ConcurrentHashMap.newKeySet();
    private final Map<UUID, SkinTexture> skinHeadTextures = new ConcurrentHashMap<>();
    private final Map<UUID, Long> skinHeadTextureRefreshTimes = new ConcurrentHashMap<>();

    public TablistManager(UltimateDonutSmp plugin) {
        this.plugin = plugin;
        this.componentUpdater = new TablistComponentUpdater(plugin);
    }

    public boolean isEnabled() {
        return config().getBoolean("TABLIST.ENABLED", true);
    }

    public void update(Player player) {
        if (!isEnabled()) {
            player.setPlayerListHeaderFooter("", "");
            return;
        }

        String headerText = applyInternalPlaceholders(getMultilineText("TABLIST.HEADER"), player);
        String footerText = applyInternalPlaceholders(getMultilineText("TABLIST.FOOTER"), player);

        player.setPlayerListHeaderFooter(parseTabText(headerText, player), parseTabText(footerText, player));
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    public void updateNamesAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateTablistName(player);
        }
    }

    public void updateTablistName(Player player) {
        if (!isEnabled()) {
            player.setPlayerListName(player.getName());
            return;
        }

        String nameFormat = resolveNameFormat(player);
        refreshSkinHeadTextureIfNeeded(player, nameFormat, false);
        String componentJson = parseTabComponentJson(nameFormat, player);
        if (componentJson != null && componentUpdater.updateName(player, componentJson)) {
            return;
        }

        player.setPlayerListName(parseTabText(nameFormat, player));
    }

    public void refreshSkinHeads(Player player) {
        if (player == null || !player.isOnline() || !isEnabled()
                || !config().getBoolean("TABLIST.REFRESH-SKIN-HEADS", true)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        if (!refreshedSkinHeads.add(playerId)) {
            return;
        }

        for (long delayTicks : getSkinHeadRefreshDelays()) {
            plugin.getSpigotScheduler().runEntityLater(player, () -> {
                Player online = Bukkit.getPlayer(playerId);
                if (online != null && online.isOnline()) {
                    refreshSkinHeadTextureIfNeeded(online, resolveNameFormat(online), true);
                    updateTablistName(online);
                } else {
                    refreshedSkinHeads.remove(playerId);
                }
            }, delayTicks);
        }
    }

    public void removePlayer(UUID playerId) {
        refreshedSkinHeads.remove(playerId);
        pendingSkinHeadTextureRefreshes.remove(playerId);
        skinHeadTextures.remove(playerId);
        skinHeadTextureRefreshTimes.remove(playerId);
    }

    private String resolveNameFormat(Player player) {
        String rawTeamName = plugin.getTeamManager().getTeamName(player);
        boolean showTeam = config().getBoolean("TABLIST.SHOW-TEAM-NAME", true);
        String teamName = showTeam ? rawTeamName : null;
        String prefix = resolvePrefix(player);
        String teamSuffix = "";
        String iconHeadSkin = config().getString("TABLIST.ICON-HEAD-SKIN", "<head:%player_name%>");
        String iconMedia = config().getString("TABLIST.ICON-MEDIA", "");
        String mediaBadge = resolveMediaBadge(player, iconMedia == null ? "" : iconMedia);
        String nickname = resolveNickname(player);

        if (showTeam && teamName != null && !teamName.isBlank()) {
            teamSuffix = " &7[&b" + teamName.toUpperCase() + "&7]";
        }

        String configuredFormat = config().getString(
                "TABLIST.NAME-FORMAT",
                "%prefix%%player%%team_suffix%"
        );
        configuredFormat = normalizeTeamFormat(configuredFormat, showTeam);
        if (showTeam && !containsTeamPlaceholder(configuredFormat)) {
            configuredFormat = configuredFormat + "%team_suffix%";
        }

        return applyInternalPlaceholders(configuredFormat, player)
                .replace("%prefix%", prefix)
                .replace("%player%", player.getName())
                .replace("%nick%", nickname)
                .replace("<player>", player.getName())
                .replace("<nick>", nickname)
                .replace("<icon_head_skin>", iconHeadSkin == null ? "" : iconHeadSkin)
                .replace("<icon_media>", iconMedia == null ? "" : iconMedia)
                .replace("%media_badge%", mediaBadge)
                .replace("<media_badge>", mediaBadge)
                .replace("<icon_media_plus>", mediaBadge)
                .replace("%team%", teamName == null ? "" : teamName)
                .replace("%team_name%", teamName == null ? "" : teamName.toUpperCase())
                .replace("%team_suffix%", teamSuffix)
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private String normalizeTeamFormat(String text, boolean showTeam) {
        if (text == null || text.isBlank() || showTeam) {
            return text;
        }

        return text
                .replace(" &8[&d%team_name%&8]", "")
                .replace(" &8[&b%team_name%&8]", "")
                .replace(" &7[&b%team_name%&7]", "")
                .replace(" [&d%team_name%]", "")
                .replace(" [&b%team_name%]", "")
                .replace("%team_suffix%", "")
                .replace("%team_name%", "")
                .replace("%team%", "");
    }

    private boolean containsTeamPlaceholder(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        return text.contains("%team_suffix%")
                || text.contains("%team_name%")
                || text.contains("%team%");
    }

    private String resolvePrefix(Player player) {
        if (!ColorUtils.hasPAPI()) {
            return "";
        }

        try {
            String prefix = me.clip.placeholderapi.PlaceholderAPI
                    .setPlaceholders(player, "%luckperms_prefix%");
            if (prefix == null || prefix.isBlank() || prefix.startsWith("%")) {
                return "";
            }
            return prefix;
        } catch (Exception ignored) {
            return "";
        }
    }

    private String getMultilineText(String path) {
        if (config().isList(path)) {
            List<String> lines = config().getStringList(path);
            return String.join("\n", lines);
        }

        return config().getString(path, "");
    }

    private String resolveMediaBadge(Player player, String iconMedia) {
        String badgeFormat = config().getString("TABLIST.MEDIA-BADGE-FORMAT", "<icon_media>&#37BFF9+");
        String permission = config().getString("TABLIST.MEDIA-BADGE-PERMISSION", "");

        if (badgeFormat == null || badgeFormat.isBlank() || iconMedia.isBlank()) {
            return "";
        }

        if (permission != null && !permission.isBlank() && !player.hasPermission(permission)) {
            return "";
        }

        return badgeFormat.replace("<icon_media>", iconMedia);
    }

    private String applyInternalPlaceholders(String text, Player player) {
        if (text == null || text.isBlank()) {
            return "";
        }

        return text
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%max_players%", String.valueOf(Bukkit.getMaxPlayers()))
                .replace("%player_name%", player.getName())
                .replace("%player%", player.getName())
                .replace("<player>", player.getName())
                .replace("%nick%", resolveNickname(player))
                .replace("<nick>", resolveNickname(player));
    }

    private String resolveNickname(Player player) {
        if (!ColorUtils.hasPAPI()) {
            return player.getName();
        }

        try {
            String nickname = me.clip.placeholderapi.PlaceholderAPI
                    .setPlaceholders(player, "%nickplus_nick%");
            if (nickname == null || nickname.isBlank() || nickname.startsWith("%")) {
                return player.getName();
            }
            return nickname;
        } catch (Exception ignored) {
            return player.getName();
        }
    }

    private String parseTabText(String text, Player player) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String resolved = text;
        if (ColorUtils.hasPAPI()) {
            try {
                resolved = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, resolved);
            } catch (Exception ignored) {
            }
        }

        resolved = applyInternalPlaceholders(resolved, player);
        return ColorUtils.colorize(stripUnsupportedHeadTags(resolved));
    }

    private String parseTabComponentJson(String text, Player player) {
        if (text == null || text.isBlank()) {
            return null;
        }

        String resolved = text;
        if (ColorUtils.hasPAPI()) {
            try {
                resolved = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, resolved);
            } catch (Exception ignored) {
            }
        }

        resolved = applyInternalPlaceholders(resolved, player);
        resolved = convertLegacyAndGradientToMiniMessage(resolved);

        try {
            Component component = MINI_MESSAGE.deserialize(resolved, headTagResolver(player));
            return GSON_COMPONENT_SERIALIZER.serialize(component);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private TagResolver headTagResolver(Player player) {
        return TagResolver.resolver("head", (arguments, context) -> {
            String source = arguments.hasNext() ? arguments.pop().value() : player.getName();
            if (source == null || source.isBlank()) {
                source = player.getName();
            }

            PlayerHeadObjectContents.Builder builder = ObjectContents.playerHead()
                    .name(source)
                    .hat(true);
            UUID id = parseUuid(source);
            if (id != null) {
                builder.id(id);
            }

            SkinTexture skinTexture = skinHeadTextures.get(player.getUniqueId());
            if (skinTexture != null && skinTexture.isValid() && isSelfHeadSource(source, player)) {
                builder.profileProperty(skinTexture.toProfileProperty());
            }

            return Tag.inserting(Component.object(builder.build()));
        });
    }

    private boolean isSelfHeadSource(String source, Player player) {
        if (source == null || source.isBlank()) {
            return true;
        }

        if (source.equalsIgnoreCase(player.getName())) {
            return true;
        }

        UUID sourceId = parseUuid(source);
        return sourceId != null && sourceId.equals(player.getUniqueId());
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void refreshSkinHeadTextureIfNeeded(Player player, String text, boolean force) {
        if (player == null || !player.isOnline() || text == null || !HEAD_TAG_PATTERN.matcher(text).find()) {
            return;
        }

        if (!config().getBoolean("TABLIST.REFRESH-SKIN-HEADS", true)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastRefresh = skinHeadTextureRefreshTimes.get(playerId);
        boolean hasTexture = skinHeadTextures.containsKey(playerId);
        long interval = hasTexture ? SKIN_HEAD_TEXTURE_REFRESH_INTERVAL_MS : SKIN_HEAD_TEXTURE_RETRY_INTERVAL_MS;
        if (!force && lastRefresh != null && now - lastRefresh < interval) {
            return;
        }

        if (!pendingSkinHeadTextureRefreshes.add(playerId)) {
            return;
        }

        SkinTexture profileTexture = resolveGameProfileTexture(player);
        if (profileTexture != null && profileTexture.isValid()) {
            skinHeadTextureRefreshTimes.put(playerId, now);
            skinHeadTextures.put(playerId, profileTexture);
            pendingSkinHeadTextureRefreshes.remove(playerId);
            return;
        }

        String playerName = player.getName();
        plugin.getSpigotScheduler().runAsync(() -> {
            SkinTexture skinTexture = null;
            try {
                skinTexture = resolveSkinsRestorerTexture(playerId, playerName);
            } catch (RuntimeException | LinkageError ignored) {
            }

            SkinTexture resolvedTexture = skinTexture;
            plugin.getSpigotScheduler().runGlobal(() -> finishSkinHeadTextureRefresh(playerId, resolvedTexture));
        });
    }

    private void finishSkinHeadTextureRefresh(UUID playerId, SkinTexture skinTexture) {
        try {
            skinHeadTextureRefreshTimes.put(playerId, System.currentTimeMillis());

            Player online = Bukkit.getPlayer(playerId);
            if (online == null || !online.isOnline()) {
                return;
            }

            if (skinTexture != null && skinTexture.isValid()) {
                SkinTexture previous = skinHeadTextures.put(playerId, skinTexture);
                if (!skinTexture.equals(previous)) {
                    updateTablistName(online);
                }
            }
        } finally {
            pendingSkinHeadTextureRefreshes.remove(playerId);
        }
    }

    private SkinTexture resolveSkinsRestorerTexture(UUID playerId, String playerName) {
        if (Bukkit.isPrimaryThread()) {
            return null;
        }

        try {
            Class<?> providerClass = Class.forName("net.skinsrestorer.api.SkinsRestorerProvider");
            Object skinsRestorer = invokeStaticNoArg(providerClass, "get");
            if (skinsRestorer == null) {
                return null;
            }

            Object playerStorage = invokeNoArg(skinsRestorer, "getPlayerStorage", "playerStorage");
            SkinTexture texture = resolveSkinsRestorerStoredTexture(skinsRestorer, playerStorage, playerId);
            if (texture != null && texture.isValid()) {
                return texture;
            }

            return resolveSkinsRestorerPlayerStorageTexture(playerStorage, playerId, playerName);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private SkinTexture resolveSkinsRestorerStoredTexture(Object skinsRestorer, Object playerStorage, UUID playerId)
            throws ReflectiveOperationException {
        if (skinsRestorer == null || playerStorage == null) {
            return null;
        }

        Object skinId = unwrapOptional(invokeCompatible(playerStorage, "getSkinIdOfPlayer", playerId));
        if (skinId == null) {
            skinId = unwrapOptional(invokeCompatible(playerStorage, "getSkinIdForPlayer", playerId));
        }
        if (skinId == null) {
            return null;
        }

        Object skinStorage = invokeNoArg(skinsRestorer, "getSkinStorage", "skinStorage");
        if (skinStorage == null) {
            return null;
        }

        SkinTexture texture = extractSkinTexture(unwrapOptional(invokeCompatible(skinStorage, "findSkinData", skinId)));
        if (texture != null && texture.isValid()) {
            return texture;
        }

        texture = extractSkinTexture(unwrapOptional(invokeCompatible(skinStorage, "getSkinDataByIdentifier", skinId)));
        return texture != null && texture.isValid() ? texture : null;
    }

    private SkinTexture resolveSkinsRestorerPlayerStorageTexture(Object playerStorage, UUID playerId, String playerName)
            throws ReflectiveOperationException {
        if (playerStorage == null) {
            return null;
        }

        // This can contact Mojang through SkinsRestorer, so callers must keep it off the server thread.
        List<Object[]> attempts = List.of(
                new Object[]{playerId, playerName, true},
                new Object[]{playerId, playerName},
                new Object[]{playerId, true},
                new Object[]{playerId},
                new Object[]{playerName, true},
                new Object[]{playerName}
        );
        for (Object[] args : attempts) {
            Object result = unwrapOptional(invokeCompatible(playerStorage, "getSkinForPlayer", args));
            SkinTexture texture = extractSkinTexture(result);
            if (texture != null && texture.isValid()) {
                return texture;
            }
        }

        return null;
    }

    private SkinTexture resolveGameProfileTexture(Player player) {
        try {
            Object profile = resolveGameProfile(player);
            if (profile == null) {
                return null;
            }

            Object propertyMap = invokeNoArg(profile, "getProperties", "properties");
            if (propertyMap == null) {
                return null;
            }

            Object textures = unwrapOptional(invokeCompatible(propertyMap, "get", "textures"));
            SkinTexture texture = extractFirstTexture(textures);
            if (texture != null && texture.isValid()) {
                return texture;
            }

            Object values = unwrapOptional(invokeNoArg(propertyMap, "values"));
            return extractFirstTexture(values);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private Object resolveGameProfile(Player player) throws ReflectiveOperationException {
        Object profile = invokeNoArg(player, "getProfile", "getGameProfile");
        if (profile != null) {
            return profile;
        }

        Object handle = invokeNoArg(player, "getHandle");
        if (handle != null) {
            profile = invokeNoArg(handle, "getGameProfile", "getProfile");
            if (profile != null) {
                return profile;
            }

            profile = findNamedField(handle, "gameProfile", "profile");
            if (profile != null) {
                return profile;
            }
        }

        return findNamedField(player, "gameProfile", "profile");
    }

    private SkinTexture extractFirstTexture(Object textures) throws ReflectiveOperationException {
        if (textures instanceof Iterable<?> iterable) {
            for (Object property : iterable) {
                SkinTexture texture = extractSkinTexture(property);
                if (texture != null && texture.isValid()) {
                    return texture;
                }
            }
        }

        return extractSkinTexture(textures);
    }

    private SkinTexture extractSkinTexture(Object source) throws ReflectiveOperationException {
        source = unwrapOptional(source);
        if (source == null) {
            return null;
        }

        String propertyName = readStringNoArg(source, "getName", "name");
        String value = readStringNoArg(source, "getValue", "value", "getTexture", "texture");
        String signature = readStringNoArg(source, "getSignature", "signature");
        if (value != null && !value.isBlank()
                && (propertyName == null || propertyName.equalsIgnoreCase("textures"))) {
            return new SkinTexture(value, signature);
        }

        for (String methodName : List.of("getProperty", "property", "getSkinProperty", "skinProperty")) {
            SkinTexture nested = extractSkinTexture(unwrapOptional(invokeNoArg(source, methodName)));
            if (nested != null && nested.isValid()) {
                return nested;
            }
        }

        return null;
    }

    private Object invokeStaticNoArg(Class<?> type, String methodName) throws ReflectiveOperationException {
        Method method = type.getMethod(methodName);
        method.setAccessible(true);
        return method.invoke(null);
    }

    private Object invokeNoArg(Object target, String... methodNames) throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }

        for (String methodName : methodNames) {
            Method method = findNoArgMethod(target.getClass(), methodName);
            if (method != null) {
                method.setAccessible(true);
                return method.invoke(target);
            }
        }
        return null;
    }

    private Method findNoArgMethod(Class<?> type, String methodName) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == 0) {
                    return method;
                }
            }
        }

        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 0) {
                return method;
            }
        }
        return null;
    }

    private Object invokeCompatible(Object target, String methodName, Object... args) throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }

        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
                continue;
            }

            if (canAccept(method.getParameterTypes(), args)) {
                method.setAccessible(true);
                return method.invoke(target, args);
            }
        }

        for (Class<?> current = target.getClass(); current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
                    continue;
                }

                if (canAccept(method.getParameterTypes(), args)) {
                    method.setAccessible(true);
                    return method.invoke(target, args);
                }
            }
        }

        return null;
    }

    private boolean canAccept(Class<?>[] parameterTypes, Object[] args) {
        for (int index = 0; index < parameterTypes.length; index++) {
            Object arg = args[index];
            if (arg == null) {
                continue;
            }

            Class<?> parameterType = wrapPrimitive(parameterTypes[index]);
            if (!parameterType.isAssignableFrom(arg.getClass())) {
                return false;
            }
        }
        return true;
    }

    private Class<?> wrapPrimitive(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }

        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private Object findNamedField(Object target, String... names) throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }

        Set<String> wanted = new LinkedHashSet<>();
        for (String name : names) {
            wanted.add(name.toLowerCase(Locale.ROOT));
        }

        for (Class<?> current = target.getClass(); current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!wanted.contains(field.getName().toLowerCase(Locale.ROOT))) {
                    continue;
                }

                field.setAccessible(true);
                Object value = field.get(target);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private String readStringNoArg(Object target, String... methodNames) throws ReflectiveOperationException {
        Object value = invokeNoArg(target, methodNames);
        value = unwrapOptional(value);
        return value instanceof String string ? string : null;
    }

    private Object unwrapOptional(Object value) {
        if (value instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        return value;
    }

    private String stripUnsupportedHeadTags(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        return HEAD_TAG_PATTERN.matcher(text).replaceAll("");
    }

    private List<Long> getSkinHeadRefreshDelays() {
        List<Long> delays = plugin.getConfigManager().getConfig()
                .getLongList("TABLIST.SKIN-HEAD-REFRESH-DELAYS");
        if (delays.isEmpty()) {
            return DEFAULT_SKIN_HEAD_REFRESH_DELAYS;
        }

        ArrayList<Long> sanitized = new ArrayList<>();
        sanitized.addAll(DEFAULT_SKIN_HEAD_REFRESH_DELAYS);
        for (long delay : delays) {
            if (delay >= 1L) {
                sanitized.add(delay);
            }
        }
        return sanitized.stream().distinct().sorted().toList();
    }

    private String convertLegacyAndGradientToMiniMessage(String text) {
        Matcher gradientMatcher = GRADIENT_TAG_PATTERN.matcher(text);
        StringBuffer gradientBuffer = new StringBuffer();
        while (gradientMatcher.find()) {
            String replacement = "<gradient:#" + gradientMatcher.group(1) + ":#" + gradientMatcher.group(3) + ">"
                    + gradientMatcher.group(2)
                    + "</gradient>";
            gradientMatcher.appendReplacement(gradientBuffer, Matcher.quoteReplacement(replacement));
        }
        gradientMatcher.appendTail(gradientBuffer);

        Matcher hexMatcher = LEGACY_HEX_PATTERN.matcher(gradientBuffer.toString());
        StringBuffer hexBuffer = new StringBuffer();
        while (hexMatcher.find()) {
            hexMatcher.appendReplacement(hexBuffer, Matcher.quoteReplacement("<#" + hexMatcher.group(1) + ">"));
        }
        hexMatcher.appendTail(hexBuffer);

        Matcher legacyMatcher = LEGACY_CODE_PATTERN.matcher(hexBuffer.toString());
        StringBuffer legacyBuffer = new StringBuffer();
        while (legacyMatcher.find()) {
            legacyMatcher.appendReplacement(
                    legacyBuffer,
                    Matcher.quoteReplacement(legacyCodeToMiniMessage(legacyMatcher.group(1).charAt(0)))
            );
        }
        legacyMatcher.appendTail(legacyBuffer);

        return legacyBuffer.toString();
    }

    private String legacyCodeToMiniMessage(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'k' -> "<obfuscated>";
            case 'l' -> "<bold>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underlined>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> String.valueOf(code);
        };
    }

    private FileConfiguration config() {
        return plugin.getConfigManager().getConfig();
    }

    private record SkinTexture(String value, String signature) {
        private boolean isValid() {
            return value != null && !value.isBlank();
        }

        private PlayerHeadObjectContents.ProfileProperty toProfileProperty() {
            if (signature == null || signature.isBlank()) {
                return PlayerHeadObjectContents.property("textures", value);
            }
            return PlayerHeadObjectContents.property("textures", value, signature);
        }
    }
}
