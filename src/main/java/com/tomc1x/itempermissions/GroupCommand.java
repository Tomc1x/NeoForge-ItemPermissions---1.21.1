package com.tomc1x.itempermissions;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public class GroupCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("itemp")
                .then(Commands.literal("group")
                        // CREATE GROUP
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .then(Commands.argument("color", StringArgumentType.string())
                                                .executes(ctx -> createGroup(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "name"),
                                                        StringArgumentType.getString(ctx, "color")
                                                ))
                                        ))
                        )

                        // SET DEFAULT GROUP
                        .then(Commands.literal("setdefault")
                                .then(Commands.argument("group", StringArgumentType.string())
                                        .executes(ctx -> setDefaultGroup(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "group")
                                        ))
                                )
                        )

                        // PLAYER MANAGEMENT
                        .then(Commands.literal("addplayer")
                                .then(Commands.argument("group", StringArgumentType.string())
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(ctx -> modifyPlayerGroup(
                                                ctx.getSource(),
                                                GameProfileArgument.getGameProfiles(ctx, "player"),
                                                StringArgumentType.getString(ctx, "group"),
                                                true
                                        ))
                                ))
                        )
                        .then(Commands.literal("removeplayer")
                                .then(Commands.argument("group", StringArgumentType.string())
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(ctx -> modifyPlayerGroup(
                                                ctx.getSource(),
                                                GameProfileArgument.getGameProfiles(ctx, "player"),
                                                StringArgumentType.getString(ctx, "group"),
                                                false
                                        ))
                                ))
                        )

                        // LIST GROUPS/PLAYERS
                        .then(Commands.literal("list")
                                .executes(ctx -> listAllGroups(ctx.getSource()))
                                .then(Commands.argument("group", StringArgumentType.string())
                                        .executes(ctx -> listGroupMembers(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "group")
                                        ))
                                )
                        )
                ));

    }

    private static int createGroup(CommandSourceStack source, String name, String color) {
        if (!color.matches("^[a-fA-F0-9]{6}$")) {
            source.sendFailure(Component.literal("Couleur HEX invalide (format: 6 caractères 0-9/a-f)"));
            return 0;
        }

        boolean success = GroupManager.get().createGroup(name, color);
        if (success) {
            source.sendSuccess(() ->
                    Component.literal("Groupe '§" + color + name + "§r' créé avec la couleur #" + color), false);
            return 1;
        } else {
            source.sendFailure(Component.literal("Le groupe existe déjà"));
            return 0;
        }
    }

    private static int setDefaultGroup(CommandSourceStack source, String groupName) {
        GroupManager manager = GroupManager.get();

        if (!manager.groupExists(groupName)) {
            source.sendFailure(Component.literal("Le groupe n'existe pas"));
            return 0;
        }

        manager.setDefaultGroup(groupName);
        source.sendSuccess(() ->
                Component.literal("Groupe par défaut défini sur '" + groupName + "'"), false);

        return 1;
    }

//    private static int managePermission(CommandSourceStack source, String groupName,
//                                        String listType, String actionType,
//                                        ResourceLocation itemId, boolean add) {
//        if (!listType.equalsIgnoreCase("whitelist") && !listType.equalsIgnoreCase("blacklist")) {
//            source.sendFailure(Component.literal("Type de liste invalide (whitelist/blacklist)"));
//            return 0;
//        }
//
//        if (!actionType.equalsIgnoreCase("craft") &&
//                !actionType.equalsIgnoreCase("place") &&
//                !actionType.equalsIgnoreCase("break")) {
//            source.sendFailure(Component.literal("Type d'action invalide (craft/place/break)"));
//            return 0;
//        }
//
//        try {
//            ResourceLocation res = itemId;
//            GroupManager manager = GroupManager.get();
//
//            if (add) {
//                manager.addPermission(groupName, listType.toLowerCase(), actionType.toLowerCase(), res);
//                source.sendSuccess(() ->
//                        Component.literal("Permission ajoutée: " +
//                                groupName + " peut " + actionType + " " + itemId + " (" + listType + ")"), false);
//            } else {
//                manager.removePermission(groupName, listType.toLowerCase(), actionType.toLowerCase(), res);
//                source.sendSuccess(() ->
//                        Component.literal("Permission retirée: " +
//                                groupName + " ne peut plus " + actionType + " " + itemId + " (" + listType + ")"), false);
//            }
//            return 1;
//        } catch (Exception e) {
//            source.sendFailure(Component.literal("ID d'item invalide"));
//            return 0;
//        }
//    }

    private static int modifyPlayerGroup(CommandSourceStack source,
                                         Collection<GameProfile> profiles,
                                         String groupName, boolean add) {
        GroupManager manager = GroupManager.get();

        if (!manager.groupExists(groupName)) {
            source.sendFailure(Component.literal("Le groupe n'existe pas"));
            return 0;
        }

        for (GameProfile profile : profiles) {
            UUID uuid = profile.getId();
            if (add) {
                manager.addPlayerToGroup(uuid, groupName);
            } else {
                manager.removePlayerFromGroup(uuid, groupName);
            }
        }

        String action = add ? "ajouté(s) à" : "retiré(s) de";
        source.sendSuccess(() ->
                Component.literal("Joueur(s) " + action + " le groupe '" + groupName + "'"), false);
        return 1;
    }

    private static int listAllGroups(CommandSourceStack source) {
        Collection<Group> groups = GroupManager.get().getAllGroups();
        if (groups.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Aucun groupe créé"), false);
            return 1;
        }

        StringBuilder sb = new StringBuilder("Groupes disponibles:\n");
        for (Group group : groups) {
            sb.append(" - §").append(group.getColorCode()).append(group.getName())
                    .append("§r (Membres: ").append(GroupManager.get().getPlayersInGroup(group.getName()).size())
                    .append(group.isDefault() ? ", DEFAULT" : "").append(")\n");
        }

        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int listGroupMembers(CommandSourceStack source, String groupName) {
        GroupManager manager = GroupManager.get();
        if (!manager.groupExists(groupName)) {
            source.sendFailure(Component.literal("Le groupe n'existe pas"));
            return 0;
        }

        // Debug
        System.out.println("[Debug] Liste des membres pour " + groupName);
        System.out.println("Depuis Group: " + manager.getGroup(groupName).getPlayers());
        System.out.println("Depuis Manager: " + manager.getPlayersInGroup(groupName));

        Set<UUID> members = manager.getPlayersInGroup(groupName);
        if (members.isEmpty()) {
            source.sendSuccess(() ->
                    Component.literal("Le groupe '" + groupName + "' n'a aucun membre"), false);
            return 1;
        }

        StringBuilder sb = new StringBuilder("Membres du groupe '").append(groupName).append("':\n");
        for (UUID uuid : members) {
            ServerPlayer player = source.getServer().getPlayerList().getPlayer(uuid);
            sb.append(" - ").append(player != null ? player.getScoreboardName() : "Offline(" + uuid + ")").append("\n");
        }

        source.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }
}