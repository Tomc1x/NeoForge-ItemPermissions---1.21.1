package com.tomc1x.itempermissions;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class GroupStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE_PATH = Path.of("config", "itempermissions", "groups.json");

    public static void saveGroups(Collection<Group> groups) {
        try {
            Files.createDirectories(FILE_PATH.getParent());

            JsonObject root = new JsonObject();
            JsonArray groupsArray = new JsonArray();


            for (Group group : groups) {
                JsonObject groupObj = new JsonObject();
                groupObj.addProperty("name", group.getName());
                groupObj.addProperty("color", group.getColorCode());
                groupObj.addProperty("isDefault", group.isDefault());

                // Players
                JsonArray playersArray = new JsonArray();
                group.getPlayers().forEach(uuid -> {
                    if (uuid != null) {
                        playersArray.add(uuid.toString());
                    }
                });
                groupObj.add("players", playersArray);

                // Permissions
                JsonObject permissionsObj = new JsonObject();
                permissionsObj.add("whitelist", createPermissionObject(group.getWhitelist()));
                permissionsObj.add("blacklist", createPermissionObject(group.getBlacklist()));
                groupObj.add("permissions", permissionsObj);

                groupsArray.add(groupObj);
            }

            root.add("groups", groupsArray);

            try (Writer writer = Files.newBufferedWriter(FILE_PATH)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            System.err.println("Failed to save groups: " + e.getMessage());
        }
    }

    private static JsonObject createPermissionObject(Map<String, Set<ResourceLocation>> permissions) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, Set<ResourceLocation>> entry : permissions.entrySet()) {
            JsonArray items = new JsonArray();
            entry.getValue().forEach(item -> items.add(item.toString()));
            obj.add(entry.getKey(), items);
        }
        return obj;
    }

    public static List<Group> loadGroups() {
        if (!Files.exists(FILE_PATH)) {
            System.out.println("[ItemPermissions] Aucun fichier de groupes trouvé, création d'un nouveau...");
            return createDefaultGroups();
        }

        try (Reader reader = Files.newBufferedReader(FILE_PATH)) {
            JsonObject root;
            try {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (JsonSyntaxException e) {
                System.err.println("[ItemPermissions] Fichier JSON corrompu : " + e.getMessage());
                return createDefaultGroups();
            }

            if (!root.has("groups")) {
                System.err.println("[ItemPermissions] Format de fichier invalide : clé 'groups' manquante");
                return createDefaultGroups();
            }

            JsonArray groupsArray = root.getAsJsonArray("groups");
            List<Group> groups = new ArrayList<>();

            for (JsonElement element : groupsArray) {
                try {
                    JsonObject groupObj = element.getAsJsonObject();
                    Group group = new Group(
                            groupObj.get("name").getAsString(),
                            groupObj.get("color").getAsString()
                    );

                    if (groupObj.has("isDefault")) {
                        group.setDefault(groupObj.get("isDefault").getAsBoolean());
                    }

                    // Chargement des joueurs
                    if (groupObj.has("players")) {
                        JsonArray playersArray = groupObj.getAsJsonArray("players");
                        playersArray.forEach(el -> {
                            try {
                                UUID playerId = UUID.fromString(el.getAsString());
                                group.addPlayer(playerId);
                                // Mettre à jour la map inverse playerGroups
                                GroupManager.get().playerGroups
                                        .computeIfAbsent(playerId, k -> new HashSet<>())
                                        .add(group.getName());
                            } catch (IllegalArgumentException e) {
                                System.err.println("[ItemPermissions] UUID invalide: " + el.getAsString());
                            }
                        });
                    }

                    // Chargement des permissions
                    if (groupObj.has("permissions")) {
                        JsonObject permissionsObj = groupObj.getAsJsonObject("permissions");
                        loadPermissionList(group, permissionsObj, "whitelist");
                        loadPermissionList(group, permissionsObj, "blacklist");
                    }

                    groups.add(group);
                } catch (Exception e) {
                    System.err.println("[ItemPermissions] Erreur lors du chargement d'un groupe : " + e.getMessage());
                }
            }

            long defaultCount = groups.stream().filter(Group::isDefault).count();
            if (defaultCount > 1) {
                System.err.println("[ItemPermissions] Correction: plusieurs groupes par défaut trouvés");
                groups.forEach(g -> g.setDefault(false));
                if (!groups.isEmpty()) {
                    groups.get(0).setDefault(true);
                }
            }

            return groups;

        } catch (Exception e) {
            System.err.println("[ItemPermissions] Échec du chargement des groupes : " + e.getMessage());
            return createDefaultGroups();
        }


    }

    private static List<Group> createDefaultGroups() {
        List<Group> groups = new ArrayList<>();
        Group defaultGroup = new Group("default", "FFFFFF");
        defaultGroup.setDefault(true);
        groups.add(defaultGroup);
        saveGroups(groups); // Sauvegarde les groupes par défaut
        return groups;
    }

    private static void loadPermissionList(Group group, JsonObject permissionsObj, String listType) {
        if (!permissionsObj.has(listType)) return;

        JsonObject permissions = permissionsObj.getAsJsonObject(listType);
        permissions.entrySet().forEach(entry -> {
            String action = entry.getKey();
            JsonArray itemsArray = entry.getValue().getAsJsonArray();

            itemsArray.forEach(item -> {
                try {
                    ResourceLocation itemId = ResourceLocation.parse(item.getAsString());
                    if (listType.equals("whitelist")) {
                        group.addToWhitelist(action, itemId);
                    } else {
                        group.addToBlacklist(action, itemId);
                    }
                } catch (Exception e) {
                    System.err.println("[ItemPermissions] ID d'item invalide: " + item.getAsString());
                }
            });
        });
    }
}