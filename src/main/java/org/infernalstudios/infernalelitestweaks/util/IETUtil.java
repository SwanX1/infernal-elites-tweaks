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

package org.infernalstudios.infernalelitestweaks.util;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.server.ServerWorld;

public final class IETUtil {
  private IETUtil() {} // Uninstantiable

  @Nullable
  public static BlockPos getSpawnableBiomesPosition(ServerWorld world, List<Biome> biomes, BlockPos nearpos, int radius) {
    List<BlockPos> spawnableBiomePositions = new ArrayList<BlockPos>(biomes.size());
    for (Biome biome : biomes) {
      spawnableBiomePositions.add(getSpawnableBiomePosition(world, biome, nearpos, radius));
    }

    BlockPos nearestBlockPos = null;
    for (BlockPos pos : spawnableBiomePositions) {
      if (pos != null) {
        if (nearestBlockPos == null) {
          nearestBlockPos = pos;
        } else if (nearestBlockPos.distSqr(new Vector3i(nearpos.getX(), nearpos.getY(), nearpos.getZ())) > pos.distSqr(new Vector3i(nearpos.getX(), nearpos.getY(), nearpos.getZ()))) {
          nearestBlockPos = pos;
        }
      }
    }

    return nearestBlockPos;
  }

  @Nullable
  public static BlockPos getSpawnableBiomePosition(ServerWorld world, Biome biome, BlockPos nearpos, int radius) {
    BlockPos pos = world.findNearestBiome(biome, nearpos, radius, 1);
    if (pos == null) {
      return null;
    }
    return pos == null ? null : IETUtil.getSafePosition(world, pos);
  }

  /**
   * Finds a safe* position in given world around a coordinate.
   * * - Not in block and not in lava. Can be over lava though.
   */
  public static BlockPos getSafePosition(ServerWorld world, BlockPos pos) {
    Direction direction = Direction.get(Direction.AxisDirection.POSITIVE, Direction.Axis.X);
    double shortestDistToOpen = -1.0D; // The shortest distance^2 found to an "open" area where a portal will fit
    double shortestDistToFit = -1.0D; // The shortest distance^2 found to ANY area where a portal will fit
    BlockPos safePos = null;
    BlockPos tempPos = null;
    WorldBorder worldborder = world.getWorldBorder();
    int dimHeight = world.getHeight(); // Logical height of the world
    Mutable mutablePos = pos.mutable();

    // For each BlockPos in a 16x16 area to the Southeast?
    for (Mutable currentPos : BlockPos.spiralAround(pos, 16, Direction.EAST, Direction.SOUTH)) {

      int minDimHeight = Math.min(dimHeight - 1,
          world.getHeight(Heightmap.Type.MOTION_BLOCKING, currentPos.getX(), currentPos.getZ()));

      if (worldborder.isWithinBounds(currentPos) && worldborder.isWithinBounds(currentPos.move(direction, 1))) {
        currentPos.move(direction.getOpposite(), 1);

        for (int height = minDimHeight; height >= 0; height--) {

          currentPos.setY(height);
          if (world.isEmptyBlock(currentPos)) {

            int currentHeight = height;

            // Find the height where there isn't air
            while (height > 0 && world.isEmptyBlock(currentPos.move(Direction.DOWN))) {
              height--;
            }

            // If height is low-enough from dimension height to place a portal
            if (height + 4 <= dimHeight - 1) {

              int deltaHeight = currentHeight - height;

              if (deltaHeight <= 0 || deltaHeight >= 3) {
                currentPos.setY(height);

                // If currentPos is valid for placement
                if (IETUtil.checkRegionForPlacement(world, currentPos, mutablePos, direction, 0)) {

                  // Calculate distance^2 to current position
                  double currentDist = pos.distSqr(currentPos);

                  // If positions next to current position are valid for placement and currentPos
                  // is closer than the next-closest open position, then...
                  // this area is likely an "open" area and now the closest to the original
                  // position we were looking at
                  if (IETUtil.checkRegionForPlacement(world, currentPos, mutablePos, direction, -1) &&
                      IETUtil.checkRegionForPlacement(world, currentPos, mutablePos, direction, 1) &&
                      (shortestDistToOpen == -1.0D || shortestDistToOpen > currentDist)) {
                    shortestDistToOpen = currentDist;
                    safePos = currentPos.immutable();
                  }

                  // If no "open" area has been found, then...
                  // this is the closest place to the original position where it can fit
                  if (shortestDistToOpen == -1.0D && (shortestDistToFit == -1.0D || shortestDistToFit > currentDist)) {
                    shortestDistToFit = currentDist;
                    tempPos = currentPos.immutable();
                  }
                }
              }
            }
          }
        }
      }
    }

    // If no "open" area was found, use the closest area where a portal will fit
    if (shortestDistToOpen == -1.0D && shortestDistToFit != -1.0D) {
      safePos = tempPos;
      shortestDistToOpen = shortestDistToFit;
    }

    // If the portal will fit nowhere, return the original position (clamped to
    // between y 70 and dimension height - 10) as the best candidate, unless it's
    // not in the world border
    if (shortestDistToOpen == -1.0D) {
      safePos = (new BlockPos(pos.getX(), MathHelper.clamp(pos.getY(), 70, dimHeight - 10), pos.getZ())).immutable();
      // If the original position is not in the world border, return that there is NO
      // safe position for a portal
      if (!worldborder.isWithinBounds(safePos)) {
        return null;
      }
    }

    return safePos;
  }

  /**
   * Checks whether the area around the provided position is large enough to
   * teleport a player
   */
  private static boolean checkRegionForPlacement(ServerWorld world, BlockPos originalPos, Mutable offsetPos, Direction directionIn, int offsetScale) {
    Direction direction = directionIn.getClockWise();

    for (int x = -1; x < 1; ++x) {
      for (int y = -1; y < 2; ++y) {
        offsetPos.setWithOffset(
          originalPos,
          directionIn.getStepX() * x + direction.getStepX() * offsetScale,
          y,
          directionIn.getStepZ() * x + direction.getStepZ() * offsetScale
        );

        // If no solid ground beneath offsetPos, return false
        if (y < 0 && !world.getBlockState(offsetPos).getMaterial().isSolid()) {
          return false;
        }

        // If no air within region, return false
        if (y >= 0 && !world.isEmptyBlock(offsetPos)) {
          return false;
        }
      }
    }

    return true;
  }
}
