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
package org.spongepowered.common.mixin.api.mcp.world;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.network.play.server.SChunkDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.ServerTickList;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.raid.Raid;
import net.minecraft.world.raid.RaidManager;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.SaveHandler;
import net.minecraft.world.storage.SessionLockException;
import org.apache.logging.log4j.Level;
import org.spongepowered.api.Server;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.world.ExplosionEvent;
import org.spongepowered.api.fluid.FluidType;
import org.spongepowered.api.scheduler.ScheduledUpdateList;
import org.spongepowered.api.world.ChunkRegenerateFlag;
import org.spongepowered.api.world.storage.WorldStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.util.PrettyPrinter;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.accessor.world.raid.RaidManagerAccessor;
import org.spongepowered.common.accessor.world.storage.SaveHandlerAccessor;
import org.spongepowered.common.bridge.world.chunk.ServerChunkProviderBridge;
import org.spongepowered.common.event.tracking.PhaseContext;
import org.spongepowered.common.event.tracking.PhaseTracker;
import org.spongepowered.common.event.tracking.phase.general.GeneralPhase;
import org.spongepowered.common.event.tracking.phase.generation.GenerationPhase;
import org.spongepowered.common.util.VecHelper;
import org.spongepowered.common.world.NoOpChunkStatusListener;
import org.spongepowered.math.vector.Vector3i;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin_API extends WorldMixin_API<org.spongepowered.api.world.server.ServerWorld> implements org.spongepowered.api.world.server.ServerWorld {

    @Shadow public abstract void shadow$save(@Nullable IProgressUpdate p_217445_1_, boolean p_217445_2_, boolean p_217445_3_) throws SessionLockException;
    @Shadow public abstract boolean shadow$addEntity(Entity p_217376_1_);
    @Shadow public abstract void shadow$onChunkUnloading(Chunk p_217466_1_);
    @Shadow public abstract void shadow$playSound(@Nullable PlayerEntity p_184148_1_, double p_184148_2_, double p_184148_4_, double p_184148_6_, SoundEvent p_184148_8_, SoundCategory p_184148_9_, float p_184148_10_, float p_184148_11_);
    @Shadow public abstract ServerChunkProvider shadow$getChunkProvider();
    @Nonnull @Shadow public abstract MinecraftServer shadow$getServer();
    @Nullable @Shadow public abstract Entity shadow$getEntityByUuid(UUID p_217461_1_);
    @Shadow public abstract SaveHandler shadow$getSaveHandler();
    @Shadow public abstract List<ServerPlayerEntity> shadow$getPlayers();
    @Shadow public abstract RaidManager shadow$getRaids();
    @Nullable @Shadow public abstract Raid shadow$findRaid(BlockPos p_217475_1_);

    @Shadow @Final private ServerTickList<Block> pendingBlockTicks;
    @Shadow @Final private ServerTickList<Fluid> pendingFluidTicks;

    // ServerWorld

    @Override
    public Server getServer() {
        return (Server) this.shadow$getServer();
    }

    @Override
    public Optional<org.spongepowered.api.world.chunk.Chunk> regenerateChunk(int cx, int cy, int cz, ChunkRegenerateFlag flag) {
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
        try (final PhaseContext<?> context = GenerationPhase.State.CHUNK_REGENERATING.createPhaseContext(PhaseTracker.SERVER).chunk(chunk)) {
            context.buildAndSwitch();
            // If we reached this point, an existing chunk was found so we need to regen
            for (final ClassInheritanceMultiMap<Entity> multiEntityList : chunk.getEntityLists()) {
                for (final net.minecraft.entity.Entity entity : multiEntityList) {
                    if (!keepEntities && !(entity instanceof ServerPlayerEntity)) {
                        entity.remove();
                    }
                }
            }

            final ServerChunkProvider chunkProviderServer = (ServerChunkProvider) chunk.getWorld().getChunkProvider();
            ((ServerChunkProviderBridge) chunkProviderServer).bridge$unloadChunkAndSave(chunk);

            File saveFolder = Files.createTempDir();
            // register this just in case something goes wrong
            // normally it should be deleted at the end of this method
            saveFolder.deleteOnExit();
            try {
                ServerWorld originalWorld = (ServerWorld) (Object) this;

                MinecraftServer server = originalWorld.getServer();
                SaveHandler saveHandler = new SaveHandler(saveFolder, originalWorld.getSaveHandler().getWorldDirectory().getName(), server, server.getDataFixer());
                try (World freshWorld = new ServerWorld(server, server.getBackgroundExecutor(), saveHandler, originalWorld.getWorldInfo(),
                        originalWorld.dimension.getType(), originalWorld.getProfiler(), new NoOpChunkStatusListener())) {

                    // Also generate chunks around this one
                    for (int z = cz - 1; z <= cz + 1; z++) {
                        for (int x = cx - 1; x <= cx + 1; x++) {
                            freshWorld.getChunk(x, z);
                        }
                    }

                    Vector3i blockMin = spongeChunk.getBlockMin();
                    Vector3i blockMax = spongeChunk.getBlockMax();

                    for (int z = blockMin.getZ(); z <= blockMax.getZ(); z++) {
                        for (int y = blockMin.getY(); y <= blockMax.getY(); y++) {
                            for (int x = blockMin.getX(); x <= blockMax.getX(); x++) {
                                final BlockPos pos = new BlockPos(x, y, z);
                                chunk.setBlockState(pos, freshWorld.getBlockState(pos), false);
                                // TODO performance? will this send client updates?
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } finally {
                saveFolder.delete();
            }

            chunkProviderServer.chunkManager.getTrackingPlayers(new ChunkPos(cx, cz), false).forEach(player -> {
                // We deliberately send two packets, to avoid sending a 'fullChunk' packet
                // (a changedSectionFilter of 65535). fullChunk packets cause the client to
                // completely overwrite its current chunk with a new chunk instance. This causes
                // weird issues, such as making any entities in that chunk invisible (until they leave it
                // for a new chunk)
                // - Aaron1011
                player.connection.sendPacket(new SChunkDataPacket(chunk, 65534));
                player.connection.sendPacket(new SChunkDataPacket(chunk, 1));
            });

            return Optional.of((org.spongepowered.api.world.chunk.Chunk) chunk);
        }
    }

    @Override
    public Path getDirectory() {
        final File worldDirectory = this.shadow$getSaveHandler().getWorldDirectory();
        if (worldDirectory == null) {
            new PrettyPrinter(60).add("A Server World has a null save directory!").centre().hr()
                    .add("%s : %s", "World Name", ((SaveHandlerAccessor) this.shadow$getSaveHandler()).accessor$getName())
                    .add("%s : %s", "Dimension", this.getProperties().getDimensionType())
                    .add("Please report this to sponge developers so they may potentially fix this")
                    .trace(System.err, SpongeImpl.getLogger(), Level.ERROR);
            return null;
        }
        return worldDirectory.toPath();
    }

    @Override
    public WorldStorage getWorldStorage() {
        return (WorldStorage) this.shadow$getChunkProvider();
    }

    @Override
    public boolean save() throws IOException {
        try {
            this.shadow$save((IProgressUpdate) null, false, false);
        } catch (SessionLockException e) {
            throw new IOException(e);
        }
        return true;
    }

    @Override
    public boolean unloadChunk(org.spongepowered.api.world.chunk.Chunk chunk) {
        this.shadow$onChunkUnloading((Chunk) chunk);
        return true;
    }

    // TODO move to bridge?
    private boolean impl$processingExplosion;
    @Override
    public void triggerExplosion(org.spongepowered.api.world.explosion.Explosion explosion) {
        checkNotNull(explosion, "explosion");
        // Sponge start
        this.impl$processingExplosion = true;
        // Set up the pre event
        final ExplosionEvent.Pre
                event =
                SpongeEventFactory.createExplosionEventPre(Sponge.getCauseStackManager().getCurrentCause(),
                        explosion, this);
        if (SpongeImpl.postEvent(event)) {
            this.impl$processingExplosion = false;
            return;
        }
        explosion = event.getExplosion();
        final Explosion mcExplosion;
        try {
            // Since we already have the API created implementation Explosion, let's use it.
            mcExplosion = (Explosion) explosion;
        } catch (Exception e) {
            new PrettyPrinter(60).add("Explosion not compatible with this implementation").centre().hr()
                    .add("An explosion that was expected to be used for this implementation does not")
                    .add("originate from this implementation.")
                    .add(e)
                    .trace();
            return;
        }

        try (final PhaseContext<?> ignored = GeneralPhase.State.EXPLOSION.createPhaseContext(PhaseTracker.SERVER)
                .explosion((Explosion) explosion)
                .source(explosion.getSourceExplosive().isPresent() ? explosion.getSourceExplosive() : this)) {
            ignored.buildAndSwitch();
            final boolean damagesTerrain = explosion.shouldBreakBlocks();
            // Sponge End

            mcExplosion.doExplosionA();
            mcExplosion.doExplosionB(false);

            if (!damagesTerrain) {
                mcExplosion.clearAffectedBlockPositions();
            }

            // Sponge Start - end processing
            this.impl$processingExplosion = false;
        }
        // Sponge End
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<ServerPlayer> getPlayers() {
        return ImmutableList.copyOf((Collection<ServerPlayer>) (Collection<?>) this.shadow$getPlayers());
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Collection<org.spongepowered.api.raid.Raid> getRaids() {
        final RaidManagerAccessor raidManager = (RaidManagerAccessor) this.shadow$getRaids();
        return (Collection<org.spongepowered.api.raid.Raid>) (Collection) raidManager.accessor$getById().values();
    }

    @Override
    public Optional<org.spongepowered.api.raid.Raid> getRaidAt(Vector3i blockPosition) {
        org.spongepowered.api.raid.Raid raid = (org.spongepowered.api.raid.Raid) this.shadow$findRaid(VecHelper.toBlockPos(blockPosition));
        return Optional.ofNullable(raid);
    }

    // ReadableEntityVolume

    @Override
    public Optional<org.spongepowered.api.entity.Entity> getEntity(UUID uuid) {
        return Optional.ofNullable((org.spongepowered.api.entity.Entity) this.shadow$getEntityByUuid(uuid));
    }

    // UpdateableVolume

    @Override
    @SuppressWarnings("unchecked")
    public ScheduledUpdateList<BlockType> getScheduledBlockUpdates() {
        return (ScheduledUpdateList<BlockType>) this.pendingBlockTicks;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ScheduledUpdateList<FluidType> getScheduledFluidUpdates() {
        return (ScheduledUpdateList<FluidType>) this.pendingFluidTicks;
    }

}
