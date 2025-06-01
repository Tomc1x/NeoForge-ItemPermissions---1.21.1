package com.tomc1x.itempermissions;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(ItemPermissions.MOD_ID)
public class ItemPermissions
{

    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "tomc1x_itempermissions";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    //Constructor
    public ItemPermissions()
    {
        // Enregistrement des événements
        NeoForge.EVENT_BUS.register(this);

        // Ajout des joueurs au groupe par défaut
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);

        // Chargement des groupes
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);

        // Enregistrement des commandes
        NeoForge.EVENT_BUS.addListener(this::registerCommands);

        // Enregistrement des événements de permission
        NeoForge.EVENT_BUS.addListener(this::onPlayerCraft);
        NeoForge.EVENT_BUS.addListener(this::onBlockPlace);
        NeoForge.EVENT_BUS.addListener(this::onBlockBreak);

    }

    private void registerCommands(RegisterCommandsEvent event) {
        GroupCommand.register(event.getDispatcher());
    }

    private void onServerStarted(ServerStartedEvent event) {
        GroupManager.get().loadFromFile();
        System.out.println("[ItemPermissions] Groupes chargés : " +
                GroupManager.get().getAllGroups().size());
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            GroupManager.get().checkPlayerDefaultGroup(player);
        }
    }

    @SubscribeEvent
    public void onPlayerCraft(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(event.getCrafting().getItem());
        if (!GroupManager.get().canCraft(player.getUUID(), itemId)) {
            // 1. Supprimer l'item crafté du résultat
            event.getCrafting().setCount(0); // Réduire à 0 le nombre d'items

            // 2. Rendre les ingrédients
            CraftingContainer container = (CraftingContainer) event.getInventory();
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty()) {
                    player.getInventory().add(stack.copy());
                }
            }

            // 3. Vider la grille de craft
            container.clearContent();

            // 4. Envoyer le message d'erreur
            player.sendSystemMessage(Component.literal("§cVous ne pouvez pas crafter cet objet"));

            // 5. Synchroniser l'inventaire
            player.containerMenu.broadcastChanges();
        }
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock());
            if (!GroupManager.get().canPlace(player.getUUID(), blockId)) {
                event.setCanceled(true);

//                // Rendre le bloc au joueur
//                ItemStack itemStack = new ItemStack(event.getPlacedBlock().getBlock());
//                if (!player.getInventory().add(itemStack)) {
//                    player.drop(itemStack, false);
//                }

                player.sendSystemMessage(Component.literal("§cVous ne pouvez pas placer ce bloc"));
            }
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer serverPlayer) {
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock());

            if (!GroupManager.get().canBreak(serverPlayer.getUUID(), blockId)) {
                event.setCanceled(true);

                // Restaurer le bloc après un tick
                serverPlayer.server.execute(() -> {
                    event.getLevel().setBlock(event.getPos(), event.getState(), 3);
                });

                serverPlayer.sendSystemMessage(Component.literal("§cVous ne pouvez pas casser ce bloc"));
            }
        }
    }


    private void commonSetup(final FMLCommonSetupEvent event)
    {

    }


    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {

    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {

        }
    }



}
