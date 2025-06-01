package com.tomc1x.itempermissions;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

public class GroupManager {
    private static final GroupManager INSTANCE = new GroupManager();
    private final Map<String, Group> groups = new HashMap<>();
    public final Map<UUID, Set<String>> playerGroups = new HashMap<>();
    private String defaultGroupName = "default";

    private GroupManager() {}

    public static GroupManager get() {
        return INSTANCE;
    }

    public void loadFromFile() {
        System.out.println("[ItemPermissions] Chargement des groupes...");
        List<Group> loaded = GroupStorage.loadGroups();
        groups.clear();
        loaded.forEach(group -> {
            groups.put(group.getName(), group);
            System.out.println("[ItemPermissions] Groupe chargé: " + group.getName() +
                    " (Joueurs: " + group.getPlayers().size() +
                    ", Whitelist: " + group.getWhitelist().values().stream().mapToInt(Set::size).sum() +
                    ", Blacklist: " + group.getBlacklist().values().stream().mapToInt(Set::size).sum() + ")");
            if (group.isDefault()) {
                defaultGroupName = group.getName();
            }
        });
        if (defaultGroupName == null) {
            System.err.println("[ItemPermissions] Aucun groupe par défaut trouvé !");
        }
    }

    public boolean createGroup(String name, String color) {
        if (groups.containsKey(name)) return false;
        groups.put(name, new Group(name, color));
        GroupStorage.saveGroups(groups.values());
        return true;
    }

//    // Gestion des permissions
//    public void addPermission(String groupName, String listType, String actionType, ResourceLocation itemId) {
//        Group group = groups.get(groupName);
//        if (group != null) {
//            group.addPermission(itemId, listType, actionType);
//            GroupStorage.saveGroups(groups.values());
//        }
//    }
//
//    public void removePermission(String groupName, String listType, String actionType, ResourceLocation itemId) {
//        Group group = groups.get(groupName);
//        if (group != null) {
//            group.removePermission(itemId, listType, actionType);
//            GroupStorage.saveGroups(groups.values());
//        }
//    }

    // Vérification des permissions
    public boolean hasPermission(UUID playerId, ResourceLocation itemId, String actionType) {
        Set<String> groupNames = getGroupsForPlayer(playerId);
        boolean isAllowed = true;

        // D'abord vérifier les whitelists
        for (String groupName : groupNames) {
            Group group = groups.get(groupName);
            if (group == null) continue;

            Set<ResourceLocation> whitelist = group.getWhitelist(actionType);
            if (whitelist.contains(itemId)) {
                return true;
            }
        }

        // Ensuite vérifier les blacklists
        for (String groupName : groupNames) {
            Group group = groups.get(groupName);
            if (group == null) continue;

            Set<ResourceLocation> blacklist = group.getBlacklist(actionType);
            if (blacklist.contains(itemId)) {
                isAllowed = false;
            }
        }

        return isAllowed;
    }

    // Gestion du groupe par défaut
    public void setDefaultGroup(String groupName) {
        // Retirer le statut par défaut de tous les groupes
        groups.values().forEach(g -> g.setDefault(false));

        // Définir le nouveau groupe par défaut
        Group group = groups.get(groupName);
        if (group != null) {
            group.setDefault(true);
            defaultGroupName = groupName;
            GroupStorage.saveGroups(groups.values());
        }
    }

    public void checkPlayerDefaultGroup(ServerPlayer player) {
        UUID playerId = player.getUUID();

        // Si le joueur n'a aucun groupe
        if (playerGroups.getOrDefault(playerId, Set.of()).isEmpty()) {
            // Ajouter au groupe par défaut
            if (defaultGroupName != null) {
                addPlayerToGroup(playerId, defaultGroupName);
                player.sendSystemMessage(Component.literal(
                        "§aVous avez été ajouté au groupe par défaut: " + defaultGroupName));
            }
        }
    }


    public boolean canCraft(UUID playerId, ResourceLocation itemId) {
        return checkPermission(playerId, itemId, "craft");
    }

    public boolean canPlace(UUID playerId, ResourceLocation blockId) {
        return checkPermission(playerId, blockId, "place");
    }

    public boolean canBreak(UUID playerId, ResourceLocation blockId) {
        return checkPermission(playerId, blockId, "break");
    }

    private boolean checkPermission(UUID playerId, ResourceLocation resourceId, String actionType) {
        Set<String> groupNames = getGroupsForPlayer(playerId);
        boolean isWhitelisted = false;
        boolean isBlacklisted = false;

        // Vérification des permissions
        for (String groupName : groupNames) {
            Group group = groups.get(groupName);
            if (group == null) continue;

            // Whitelist prioritaire
            if (group.getWhitelist(actionType).contains(resourceId)) {
                isWhitelisted = true;
            }
            if (group.getBlacklist(actionType).contains(resourceId)) {
                isBlacklisted = true;
            }
        }

        return isWhitelisted || !isBlacklisted;
    }

    public Group getDefaultGroup() {
        return groups.get(defaultGroupName);
    }

    // Méthodes utilitaires
    public Set<String> getGroupsForPlayer(UUID playerId) {
        Set<String> result = new HashSet<>(playerGroups.getOrDefault(playerId, new HashSet<>()));
        if (defaultGroupName != null) {
            result.add(defaultGroupName);
        }
        return result;
    }

    public boolean groupExists(String groupName) {
        return groups.containsKey(groupName);
    }

    public Collection<Group> getAllGroups() {
        return groups.values();
    }

    public Set<UUID> getPlayersInGroup(String groupName) {
        Set<UUID> result = new HashSet<>();
        for (Map.Entry<UUID, Set<String>> entry : playerGroups.entrySet()) {
            if (entry.getValue().contains(groupName)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public int getGroupMemberCount(String groupName) {
        return getPlayersInGroup(groupName).size();
    }

    // Méthodes pour les commandes
    public void removeGroup(String groupName) {
        groups.remove(groupName);
        playerGroups.values().forEach(groups -> groups.remove(groupName));
        GroupStorage.saveGroups(groups.values());
    }

    public void clearGroups() {
        groups.clear();
        playerGroups.clear();
        GroupStorage.saveGroups(groups.values());
    }

    public void addPlayerToGroup(UUID playerId, String groupName) {
        Group group = groups.get(groupName);
        if (group == null) return;

        group.addPlayer(playerId);
        playerGroups.computeIfAbsent(playerId, k -> new HashSet<>()).add(groupName);
        GroupStorage.saveGroups(groups.values());
    }

    public void removePlayerFromGroup(UUID playerId, String groupName) {
        Set<String> playerGroupSet = playerGroups.get(playerId);
        if (playerGroupSet != null) {
            playerGroupSet.remove(groupName);
            if (playerGroupSet.isEmpty()) {
                playerGroups.remove(playerId);
            }
        }

        // Retire le joueur du groupe
        Group group = groups.get(groupName);
        if (group != null) {
            group.removePlayer(playerId);
        }

        // Sauvegarde immédiate
        GroupStorage.saveGroups(groups.values());
    }

    public Group getGroup(String groupName) {
        return groups.get(groupName);
    }
}