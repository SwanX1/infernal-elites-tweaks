/*
 * Copyright 2021 Infernal Studios
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.infernalstudios.infernalelitestweaks;

import static org.infernalstudios.infernalelitestweaks.InfernalElitesTweaks.LOGGER;
import static org.infernalstudios.infernalelitestweaks.InfernalElitesTweaks.MOD_ID;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.infernalstudios.infernalelitestweaks.util.IETUtil;
import org.infernalstudios.infernalelitestweaks.mixin.common.CreeperEntityAccess;
import org.infernalstudios.infernalexp.init.IEBiomes;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityClassification;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.monster.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.stats.Stats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.MobSpawnInfo;
import net.minecraft.world.biome.MobSpawnInfo.Spawners;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.BiomeLoadingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

public final class IETEvents {
  @SubscribeEvent
  public static void playerLoggedInEvent(PlayerEvent.PlayerLoggedInEvent event) {
    PlayerEntity player = event.getPlayer();
    if (!player.level.isClientSide()) {
      ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
      if (serverPlayer.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_ONE_MINUTE)) == 0) {
        respawnInNether(serverPlayer);
      }
    }
  }

  @SubscribeEvent
  public static void playerRespawnEvent(PlayerEvent.PlayerRespawnEvent event) {
    PlayerEntity player = event.getPlayer();
    if (!player.level.isClientSide()) {
      ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
      BlockPos respawnPos = serverPlayer.getRespawnPosition();
      if (respawnPos == null) {
        respawnInNether(serverPlayer);
      }
    }
  }

  private static void respawnInNether(ServerPlayerEntity player) {
    if (!player.level.isClientSide()) {
      MinecraftServer minecraftserver = player.level.getServer();
      ServerWorld overworld = minecraftserver.getLevel(World.OVERWORLD);
      ServerWorld nether = minecraftserver.getLevel(World.NETHER);

      if (overworld != null && nether != null && minecraftserver.isNetherEnabled() && !player.isPassenger()) {
        BlockPos spawnPos;

        double posX = player.getX();
        double posZ = player.getZ();
        double scale = DimensionType.getTeleportationScale(overworld.dimensionType(), nether.dimensionType());
        posX /= scale;
        posZ /= scale;

        double d0 = Math.min(-2.9999872E7D, nether.getWorldBorder().getMinX() + 16.0D);
        double d1 = Math.min(-2.9999872E7D, nether.getWorldBorder().getMinZ() + 16.0D);
        double d2 = Math.min(2.9999872E7D, nether.getWorldBorder().getMaxX() - 16.0D);
        double d3 = Math.min(2.9999872E7D, nether.getWorldBorder().getMaxZ() - 16.0D);
        posX = MathHelper.clamp(posX, d0, d2);
        posZ = MathHelper.clamp(posZ, d1, d3);
        spawnPos = new BlockPos(posX, player.getY(), posZ);

        Biome crimsonForest = ForgeRegistries.BIOMES.getValue(new ResourceLocation("minecraft", "crimson_forest"));
        Biome warpedForest = ForgeRegistries.BIOMES.getValue(new ResourceLocation("minecraft", "warped_forest"));
        Biome glowstoneCanyon = IEBiomes.GLOWSTONE_CANYON.get();

        List<Biome> spawnableBiomes = new ArrayList<>();

        if (crimsonForest != null) {
          spawnableBiomes.add(crimsonForest);
        } else {
          LOGGER.info("Couldn't add crimsonForest to spawnableBiomes, it is null.");
        }

        if (warpedForest != null) {
          spawnableBiomes.add(warpedForest);
        } else {
          LOGGER.info("Couldn't add warpedForest to spawnableBiomes, it is null.");
        }

        if (glowstoneCanyon != null) {
          spawnableBiomes.add(glowstoneCanyon);
        } else {
          LOGGER.info("Couldn't add glowstoneCanyon to spawnableBiomes, it is null.");
        }

        // Find a safe spawn position
        spawnPos = IETUtil.getSpawnableBiomesPosition(nether, spawnableBiomes, spawnPos, 9999).immutable();

        if (spawnPos != null) {
          // Make sure there's a solid block under player.
          Block blockBelow = nether.getBlockState(spawnPos.below()).getBlock();
          if (blockBelow instanceof FallingBlock || blockBelow.equals(Blocks.LAVA)) {
            nether.setBlock(spawnPos.below(), Blocks.NETHERRACK.defaultBlockState(), 2);
          }
          // Teleport to safe spawn position
          player.teleportTo(nether, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), player.getViewYRot(0.0f), player.getViewXRot(0.0f));
        } else {
          LOGGER.info("Couldn't spawn player {} in the nether, spawnPos is null", player.getScoreboardName());
        }

        player.level.getProfiler().endTick();
      } else {
        if (overworld == null) {
          LOGGER.info("Couldn't spawn player {} in the nether, overworld == null", player.getScoreboardName());
        }
        if (nether == null) {
          LOGGER.info("Couldn't spawn player {} in the nether, nether == null", player.getScoreboardName());
        }
        if (player.isPassenger()) {
          LOGGER.info("Couldn't spawn player {} in the nether, player.isPassenger() == true", player.getScoreboardName());
        }
      }
    }
  }

  private static Map<EntityType<?>, List<Supplier<EffectInstance>>> MOB_EFFECT_LIST = new HashMap<>();
  
  static {
    for (EntityType<?> entityType : Arrays.asList(
      EntityType.CREEPER,
      EntityType.ZOMBIE, 
      EntityType.SKELETON,
      EntityType.SPIDER,
      EntityType.CAVE_SPIDER,
      EntityType.ZOMBIE_VILLAGER,
      EntityType.HUSK,
      ForgeRegistries.ENTITIES.getValue(new ResourceLocation("desolation", "blackened")),
      ForgeRegistries.ENTITIES.getValue(new ResourceLocation("eidolon", "zombie_brute")),
      ForgeRegistries.ENTITIES.getValue(new ResourceLocation("eidolon", "wraith"))
    )) {
      MOB_EFFECT_LIST.put(
        entityType,
        Arrays.asList(
          () -> new EffectInstance(Effects.MOVEMENT_SPEED, 1, Integer.MAX_VALUE, true, true),
          () -> new EffectInstance(Effects.DAMAGE_BOOST, 1, Integer.MAX_VALUE, true, true),
          () -> new EffectInstance(Effects.DAMAGE_RESISTANCE, 1, Integer.MAX_VALUE, true, true)
        )
      );
    }
  }

  @SubscribeEvent
  public void onEntityJoin(EntityJoinWorldEvent event) {
    World world = event.getWorld();
    if (!world.isClientSide()) {
      Entity e = event.getEntity();
      if (e instanceof CreeperEntity) {
        CreeperEntity entity = (CreeperEntity) e;
        Set<String> tags = entity.getTags();

        if (!tags.contains(MOD_ID + ".checkedForChargedEntity")) {
          entity.addTag(MOD_ID + ".checkedForChargedEntity");
          double num = Math.random();
          // 20% chance
          if (num < 0.2) {
            entity.getEntityData().set(CreeperEntityAccess.getDataIsPowered(), true);
          }
        }
      }

      if (e instanceof LivingEntity) {
        LivingEntity entity = (LivingEntity) e;
        if (MOB_EFFECT_LIST.containsKey(e.getType())) {
          for (Supplier<EffectInstance> effectInstanceProvider : MOB_EFFECT_LIST.get(entity.getType())) {
            EffectInstance effectInstance = effectInstanceProvider.get();
            entity.removeEffect(effectInstance.getEffect());
            entity.addEffect(effectInstance);
          }
        }
      }
    }
  }

  @SubscribeEvent
  public void onBiomeLoad(BiomeLoadingEvent event) {
    if (new ResourceLocation("desolation", "charred_forest").equals(event.getName())) {
      List<Spawners> mobSpawners = event.getSpawns().getSpawner(EntityClassification.MONSTER);
      for (Map.Entry<EntityType<?>, ?> entry : MOB_EFFECT_LIST.entrySet()) {
        if (entry.getKey() != null) {
          mobSpawners.add(new MobSpawnInfo.Spawners(entry.getKey(), 1, 1, 1));
        }
      }

      // Adding more blackened spawners, so that other mobs don't outweigh them.
      mobSpawners.add(
        new MobSpawnInfo.Spawners(
          ForgeRegistries.ENTITIES.getValue(new ResourceLocation("desolation", "blackened")),
          MOB_EFFECT_LIST.size(),
          1,
          1
        )
      );
    }
  }
  
  @SubscribeEvent
  public void onPlayerDeath(LivingDeathEvent event) {
    Entity entity = event.getEntity();
    World world = entity.getCommandSenderWorld();
    if (!world.isClientSide()) {
      if (entity instanceof PlayerEntity) {
        PlayerEntity player = (PlayerEntity) entity;
        
        if (player.getMainHandItem().getItem().equals(Items.TOTEM_OF_UNDYING) || player.getOffhandItem().getItem().equals(Items.TOTEM_OF_UNDYING)) {
          return;
        }

        ItemStack totemStack = null;
        for (ItemStack itemStack : player.getAllSlots()) {
          if (itemStack.getItem().equals(Items.TOTEM_OF_UNDYING)) {
            totemStack = itemStack;
            break;
          }
        }

        if (totemStack != null) {
          event.setCanceled(true);
          if (player instanceof ServerPlayerEntity) {
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
            serverPlayer.awardStat(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING));
            CriteriaTriggers.USED_TOTEM.trigger(serverPlayer, totemStack);
          }
  
          player.setHealth(1.0F);
          player.removeAllEffects();
          player.addEffect(new EffectInstance(Effects.REGENERATION, 900, 1));
          player.addEffect(new EffectInstance(Effects.ABSORPTION, 100, 1));
          world.broadcastEntityEvent(player, (byte) 35);
          totemStack.shrink(1);
        }
      }
    }
  }
}
