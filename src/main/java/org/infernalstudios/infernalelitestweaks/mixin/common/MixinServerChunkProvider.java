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

import javax.annotation.Nullable;

import org.infernalstudios.infernalelitestweaks.util.LevelGetter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.spawner.WorldEntitySpawner;

@Mixin(ServerChunkProvider.class)
public class MixinServerChunkProvider {
  @Shadow
  @Nullable
  private WorldEntitySpawner.EntityDensityManager lastSpawnState;

  @Shadow
  @Final
  public ServerWorld level;

  @Inject(method = "tickChunks", at = @At(value = "INVOKE", target = "Lnet/minecraft/profiler/IProfiler;pop()V", ordinal = 0))
  private void setDensityManagerLevel(CallbackInfo ci) {
    ((LevelGetter) this.lastSpawnState).setLevel(this.level);
  }
}

