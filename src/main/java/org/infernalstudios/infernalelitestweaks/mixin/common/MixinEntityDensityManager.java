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

package org.infernalstudios.infernalelitestweaks.mixin.common;

import org.infernalstudios.infernalelitestweaks.util.LevelGetter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.entity.EntityClassification;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.spawner.WorldEntitySpawner;

@Mixin(WorldEntitySpawner.EntityDensityManager.class)
public class MixinEntityDensityManager implements LevelGetter {
  @Shadow
  @Final
  private int spawnableChunkCount;

  @Shadow
  @Final
  private Object2IntMap<EntityClassification> unmodifiableMobCategoryCounts;

  public ServerWorld level;

  @Override
  public void setLevel(World world) {
    this.level = (ServerWorld) world;
  }

  @Override
  public World getLevel() {
    return this.level;
  }

  private DimensionType overworldDimensionType;

  @Inject(method = "canSpawnForCategory", at = @At("HEAD"), cancellable = true)
  private void modifySpawnCapByCategory(EntityClassification entityClassification, CallbackInfoReturnable<Boolean> cir) {
    if (!this.level.isClientSide()) {
      if (overworldDimensionType == null) {
        this.overworldDimensionType = this.level.getServer().getLevel(World.OVERWORLD).dimensionType();
      }
      if (this.level != null && this.level.dimensionType() == this.overworldDimensionType) {
        double multiplier = 5;
        int i = (int) (entityClassification.getMaxInstancesPerChunk() * (this.spawnableChunkCount * multiplier) / WorldEntitySpawnerAccess.getMagicNumber());
        cir.setReturnValue(this.unmodifiableMobCategoryCounts.getInt(entityClassification) < i);
      }
    }
  }
}
