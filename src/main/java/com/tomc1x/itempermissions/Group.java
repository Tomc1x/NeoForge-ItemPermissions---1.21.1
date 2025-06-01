package com.tomc1x.itempermissions;

import net.minecraft.resources.ResourceLocation;
import java.util.*;

public class Group {
    private final String name;
    private String colorCode;
    private boolean isDefault;
    private final Set<UUID> players;
    private final Map<String, Set<ResourceLocation>> whitelist;
    private final Map<String, Set<ResourceLocation>> blacklist;

    public Group(String name, String colorCode) {
        this.name = name;
        this.colorCode = colorCode;
        this.isDefault = false;
        this.players = new HashSet<>();
        this.whitelist = new HashMap<>();
        this.blacklist = new HashMap<>();
        initializePermissionMaps();
    }

    private void initializePermissionMaps() {
        // Initialise les maps pour craft/place/break
        String[] actions = {"craft", "place", "break"};
        for (String action : actions) {
            whitelist.put(action, new HashSet<>());
            blacklist.put(action, new HashSet<>());
        }
    }

    // --- Gestion des joueurs ---
    public boolean addPlayer(UUID playerId) {
        return players.add(playerId);
    }

    public boolean removePlayer(UUID playerId) {
        return players.remove(playerId);
    }

    public boolean hasPlayer(UUID playerId) {
        return players.contains(playerId);
    }

    // --- Gestion des permissions ---
    public void addToWhitelist(String actionType, ResourceLocation itemId) {
        if (whitelist.containsKey(actionType)) {
            whitelist.get(actionType).add(itemId);
        }
    }

    public void removeFromWhitelist(String actionType, ResourceLocation itemId) {
        if (whitelist.containsKey(actionType)) {
            whitelist.get(actionType).remove(itemId);
        }
    }

    public void addToBlacklist(String actionType, ResourceLocation itemId) {
        if (blacklist.containsKey(actionType)) {
            blacklist.get(actionType).add(itemId);
        }
    }

    public void removeFromBlacklist(String actionType, ResourceLocation itemId) {
        if (blacklist.containsKey(actionType)) {
            blacklist.get(actionType).remove(itemId);
        }
    }

    // --- Getters ---
    public String getName() {
        return name;
    }

    public String getColorCode() {
        return colorCode;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public Set<UUID> getPlayers() {
        return Collections.unmodifiableSet(players);
    }

    public Set<ResourceLocation> getWhitelist(String actionType) {
        return Collections.unmodifiableSet(whitelist.getOrDefault(actionType, new HashSet<>()));
    }

    public Set<ResourceLocation> getBlacklist(String actionType) {
        return Collections.unmodifiableSet(blacklist.getOrDefault(actionType, new HashSet<>()));
    }

    public Map<String, Set<ResourceLocation>> getWhitelist() {
        return Collections.unmodifiableMap(whitelist);
    }

    public Map<String, Set<ResourceLocation>> getBlacklist() {
        return Collections.unmodifiableMap(blacklist);
    }

    // --- Setters ---
    public void setColorCode(String colorCode) {
        if (colorCode.matches("^[a-fA-F0-9]{6}$")) {
            this.colorCode = colorCode;
        }
    }

    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    // --- Méthodes de vérification ---
    public boolean canPerformAction(String actionType, ResourceLocation itemId) {
        // Whitelist a priorité sur blacklist
        if (whitelist.getOrDefault(actionType, Set.of()).contains(itemId)) {
            return true;
        }
        return !blacklist.getOrDefault(actionType, Set.of()).contains(itemId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Group group = (Group) o;
        return name.equals(group.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

}