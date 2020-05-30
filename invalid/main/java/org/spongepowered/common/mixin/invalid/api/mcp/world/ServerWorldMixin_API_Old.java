/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.mixin.invalid.api.mcp.world;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.play.server.SChunkDataPacket;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Teleporter;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.server.ServerWorld;
import org.spongepowered.api.world.ChunkRegenerateFlag;
import org.spongepowered.api.world.teleport.PortalAgent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.common.bridge.server.management.PlayerChunkMapEntryBridge;
import org.spongepowered.common.bridge.world.chunk.AbstractChunkProviderBridge;
import org.spongepowered.common.bridge.world.chunk.ChunkBridge;
import org.spongepowered.common.bridge.world.chunk.ServerChunkProviderBridge;
import org.spongepowered.common.event.tracking.PhaseContext;
import org.spongepowered.common.event.tracking.PhaseTracker;
import org.spongepowered.common.event.tracking.phase.generation.GenerationPhase;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin_API_Old extends WorldMixin_API {

    @Shadow @Final @Mutable private Teleporter worldTeleporter;

    @SuppressWarnings("deprecation")
    @Override
    public Optional<org.spongepowered.api.world.chunk.Chunk> regenerateChunk(final int cx, final int cy, final int cz, final ChunkRegenerateFlag flag) {
        final List<ServerPlayerEntity> playerList = new ArrayList<>();
        final List<net.minecraft.entity.Entity> entityList = new ArrayList<>();
        org.spongepowered.api.world.chunk.Chunk spongeChunk;
        try (final PhaseContext<?> context = GenerationPhase.State.CHUNK_REGENERATING_LOAD_EXISTING.createPhaseContext(PhaseTracker.SERVER)
            .world((net.minecraft.world.World)(Object) this)) {
            context.buildAndSwitch();
            spongeChunk = this.loadChunk(cx, cy, cz, false).orElse(null);
        }

        if (spongeChunk == null) {
            if (!flag.create()) {
                return Optional.empty();
            }
            // This should generate a chunk so there won't be a need to regenerate one
            return this.loadChunk(cx, cy, cz, true);
        }

        final net.minecraft.world.chunk.Chunk chunk = (net.minecraft.world.chunk.Chunk) spongeChunk;
        final boolean keepEntities = flag.entities();
        try (final PhaseContext<?> context = GenerationPhase.State.CHUNK_REGENERATING.createPhaseContext(PhaseTracker.SERVER)
            .chunk(chunk)) {
            context.buildAndSwitch();
            // If we reached this point, an existing chunk was found so we need to regen
            for (final ClassInheritanceMultiMap<net.minecraft.entity.Entity> multiEntityList : chunk.getEntityLists()) {
                for (final net.minecraft.entity.Entity entity : multiEntityList) {
                    if (entity instanceof ServerPlayerEntity) {
                        playerList.add((ServerPlayerEntity) entity);
                        entityList.add(entity);
                    } else if (keepEntities) {
                        entityList.add(entity);
                    }
                }
            }

            for (final net.minecraft.entity.Entity entity : entityList) {
                chunk.removeEntity(entity);
            }

            final ServerChunkProvider chunkProviderServer = (ServerChunkProvider) chunk.getWorld().getChunkProvider();
            ((ServerChunkProviderBridge) chunkProviderServer).bridge$unloadChunkAndSave(chunk);
            // TODO - Move to accessor with Mixin 0.8
            final net.minecraft.world.chunk.Chunk newChunk = ((ServerChunkProviderBridge) chunkProviderServer).accessor$getChunkGenerator().generateChunk(cx, cz);
            final PlayerChunkMapEntry playerChunk = ((ServerWorld) chunk.getWorld()).getPlayerChunkMap().getEntry(cx, cz);
            if (playerChunk != null) {
                ((PlayerChunkMapEntryBridge) playerChunk).bridge$setChunk(newChunk);
            }

            if (newChunk != null) {
                final ServerWorld world = (ServerWorld) newChunk.getWorld();
                ((ServerChunkProviderBridge) world.getChunkProvider()).accessor$getLoadedChunks().put(ChunkPos.asLong(cx, cz), newChunk);
                newChunk.onLoad();
                ((ChunkBridge) newChunk).accessor$populate(((ServerChunkProviderBridge) world.getChunkProvider()).accessor$getChunkGenerator());
                for (final net.minecraft.entity.Entity entity: entityList) {
                    newChunk.addEntity(entity);
                }

                if (((AbstractChunkProviderBridge) chunkProviderServer).bridge$getLoadedChunkWithoutMarkingActive(cx, cz) == null) {
                    return Optional.of((org.spongepowered.api.world.chunk.Chunk) newChunk);
                }

                final PlayerChunkMapEntry playerChunkMapEntry = ((ServerWorld) newChunk.getWorld()).getPlayerChunkMap().getEntry(cx, cz);
                if (playerChunkMapEntry != null) {
                    final List<ServerPlayerEntity> chunkPlayers = ((PlayerChunkMapEntryBridge) playerChunkMapEntry).accessor$getPlayers();
                    // We deliberately send two packets, to avoid sending a 'fullChunk' packet
                    // (a changedSectionFilter of 65535). fullChunk packets cause the client to
                    // completely overwrite its current chunk with a new chunk instance. This causes
                    // weird issues, such as making any entities in that chunk invisible (until they leave it
                    // for a new chunk)
                    // - Aaron1011
                    for (final ServerPlayerEntity playerMP: chunkPlayers) {
                        playerMP.connection.sendPacket(new SChunkDataPacket(newChunk, 65534));
                        playerMP.connection.sendPacket(new SChunkDataPacket(newChunk, 1));
                    }
                }
            }

            return Optional.of((org.spongepowered.api.world.chunk.Chunk) newChunk);
        }
    }

    @Override
    public PortalAgent getPortalAgent() {
        return (PortalAgent) this.worldTeleporter;
    }

}
