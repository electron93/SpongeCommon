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
package org.spongepowered.common.mixin.core.world.chunk.storage;

import com.flowpowered.math.vector.Vector3d;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;
import net.minecraft.world.chunk.storage.RegionFileCache;
import net.minecraft.world.storage.ThreadedFileIOBase;
import org.apache.logging.log4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.EntityType;
import org.spongepowered.api.entity.Transform;
import org.spongepowered.api.event.CauseStackManager.StackFrame;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.EventContext;
import org.spongepowered.api.event.cause.EventContextKeys;
import org.spongepowered.api.event.cause.entity.spawn.SpawnTypes;
import org.spongepowered.api.event.entity.ConstructEntityEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.SpongeImplHooks;
import org.spongepowered.common.bridge.world.WorldBridge;
import org.spongepowered.common.bridge.world.chunk.ChunkBridge;
import org.spongepowered.common.bridge.world.chunk.storage.AnvilChunkLoaderBridge;
import org.spongepowered.common.entity.PlayerTracker;
import org.spongepowered.common.event.ShouldFire;
import org.spongepowered.common.registry.type.entity.EntityTypeRegistryModule;
import org.spongepowered.common.util.Constants;
import org.spongepowered.common.util.QueuedChunk;
import org.spongepowered.common.util.VecHelper;
import org.spongepowered.common.util.WorldChunkPos;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

@Mixin(AnvilChunkLoader.class)
public abstract class AnvilChunkLoaderMixin implements AnvilChunkLoaderBridge {

    private ConcurrentLinkedQueue<QueuedChunk> impl$queue = new ConcurrentLinkedQueue<>();
    private final Object impl$lock = new Object();

    @Shadow @Final private static Logger LOGGER;
    @Shadow @Final private Map<ChunkPos, NBTTagCompound> chunksToSave;
    @Shadow @Final private File chunkSaveLocation;
    @Shadow private boolean flushing;

    @Shadow private void writeChunkData(final ChunkPos pos, final NBTTagCompound compound) { } // Shadow

    @Inject(method = "writeChunkToNBT", at = @At(value = "RETURN"))
    private void impl$writeSpongeOwnerNotifierPosTable(final net.minecraft.world.chunk.Chunk chunkIn, final World worldIn,
        final NBTTagCompound compound, final CallbackInfo ci) {
        final ChunkBridge chunk = (ChunkBridge) chunkIn;

        // Add tracked block positions
        if (chunk.bridge$getTrackedShortPlayerPositions().size() > 0 || chunk.bridge$getTrackedIntPlayerPositions().size() > 0) {
            final NBTTagCompound trackedNbt = new NBTTagCompound();
            final NBTTagList positions = new NBTTagList();
            trackedNbt.setTag(Constants.Sponge.SPONGE_BLOCK_POS_TABLE, positions);
            compound.setTag(Constants.Sponge.SPONGE_DATA, trackedNbt);

            for (final Map.Entry<Short, PlayerTracker> mapEntry : chunk.bridge$getTrackedShortPlayerPositions().entrySet()) {
                final Short pos = mapEntry.getKey();
                final int ownerUniqueIdIndex = mapEntry.getValue().ownerIndex;
                final int notifierUniqueIdIndex = mapEntry.getValue().notifierIndex;
                final NBTTagCompound valueNbt = new NBTTagCompound();
                valueNbt.setInteger("owner", ownerUniqueIdIndex);
                valueNbt.setInteger("notifier", notifierUniqueIdIndex);
                valueNbt.setShort("pos", pos);
                positions.appendTag(valueNbt);
            }

            for (final Map.Entry<Integer, PlayerTracker> mapEntry : chunk.bridge$getTrackedIntPlayerPositions().entrySet()) {
                final Integer pos = mapEntry.getKey();
                final int ownerUniqueIdIndex = mapEntry.getValue().ownerIndex;
                final int notifierUniqueIdIndex = mapEntry.getValue().notifierIndex;
                final NBTTagCompound valueNbt = new NBTTagCompound();
                valueNbt.setInteger("owner", ownerUniqueIdIndex);
                valueNbt.setInteger("notifier", notifierUniqueIdIndex);
                valueNbt.setInteger("ipos", pos);
                positions.appendTag(valueNbt);
            }
        }
    }

    @Inject(method = "readChunkFromNBT", at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/NBTTagCompound;getIntArray(Ljava/lang/String;)[I", shift = At.Shift.BEFORE), locals = LocalCapture.CAPTURE_FAILHARD)
    private void onReadChunkFromNBT(final World worldIn, final NBTTagCompound compound, final CallbackInfoReturnable<net.minecraft.world.chunk.Chunk> ci, final int chunkX,
      final int chunkZ, final net.minecraft.world.chunk.Chunk chunkIn) {
        if (compound.hasKey(Constants.Sponge.SPONGE_DATA)) {
            final Map<Integer, PlayerTracker> trackedIntPlayerPositions = new HashMap<>();
            final Map<Short, PlayerTracker> trackedShortPlayerPositions = new HashMap<>();
            final NBTTagList positions = compound.getCompoundTag(Constants.Sponge.SPONGE_DATA).getTagList(Constants.Sponge.SPONGE_BLOCK_POS_TABLE, 10);
            final ChunkBridge chunk = (ChunkBridge) chunkIn;
            for (int i = 0; i < positions.tagCount(); i++) {
                final NBTTagCompound valueNbt = positions.getCompoundTagAt(i);
                final boolean isShortPos = valueNbt.hasKey("pos");
                final PlayerTracker tracker = new PlayerTracker();
                if (valueNbt.hasKey("owner")) {
                    tracker.ownerIndex = valueNbt.getInteger("owner");
                } else if (valueNbt.hasKey("uuid")) { // Migrate old data, remove in future
                    tracker.ownerIndex = valueNbt.getInteger("uuid");
                }
                if (valueNbt.hasKey("notifier")) {
                    tracker.notifierIndex = valueNbt.getInteger("notifier");
                }

                if (tracker.notifierIndex != -1 || tracker.ownerIndex != -1) {
                    if (isShortPos) {
                        trackedShortPlayerPositions.put(valueNbt.getShort("pos"), tracker);
                    } else {
                        trackedIntPlayerPositions.put(valueNbt.getInteger("ipos"), tracker);
                    }
                }
            }
            chunk.bridge$setTrackedIntPlayerPositions(trackedIntPlayerPositions);
            chunk.bridge$setTrackedShortPlayerPositions(trackedShortPlayerPositions);
        }
    }

    /**
     * @author gabizou - January 30th, 2016
     *
     *         Attempts to redirect EntityList spawning an entity. Forge
     *         rewrites this method to handle it in a different method, so this
     *         will not actually inject in SpongeForge.
     */
    @Redirect(method = "readChunkEntity",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/storage/AnvilChunkLoader;createEntityFromNBT(Lnet/minecraft/nbt/NBTTagCompound;Lnet/minecraft/world/World;)Lnet/minecraft/entity/Entity;"),
        require = 0,
        expect = 0)
    private static Entity impl$createEntityFromCompound(final NBTTagCompound compound, final World world) {
        if ("Minecart".equals(compound.getString(Constants.Entity.ENTITY_TYPE_ID))) {
            compound.setString(Constants.Entity.ENTITY_TYPE_ID,
                    EntityMinecart.Type.values()[compound.getInteger(Constants.Entity.Minecart.MINECART_TYPE)].getName());
            compound.removeTag(Constants.Entity.Minecart.MINECART_TYPE);
        }
        final Class<? extends Entity> entityClass = SpongeImplHooks.getEntityClass(new ResourceLocation(compound.getString(Constants.Entity.ENTITY_TYPE_ID)));
        if (entityClass == null) {
            return null;
        }
        final EntityType type = EntityTypeRegistryModule.getInstance().getForClass(entityClass);
        if (type == null) {
            return null;
        }
        final NBTTagList positionList = compound.getTagList(Constants.Entity.ENTITY_POSITION, Constants.NBT.TAG_DOUBLE);
        final NBTTagList rotationList = compound.getTagList(Constants.Entity.ENTITY_ROTATION, Constants.NBT.TAG_FLOAT);
        final Vector3d position = new Vector3d(positionList.getDoubleAt(0), positionList.getDoubleAt(1), positionList.getDoubleAt(2));
        final Vector3d rotation = new Vector3d(rotationList.getFloatAt(0), rotationList.getFloatAt(1), 0);
        final Transform<org.spongepowered.api.world.World> transform = new Transform<>((org.spongepowered.api.world.World) world, position, rotation);
        try (final StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.addContext(EventContextKeys.SPAWN_TYPE, SpawnTypes.CHUNK_LOAD);
            final ConstructEntityEvent.Pre event = SpongeEventFactory.createConstructEntityEventPre(frame.getCurrentCause(), type, transform);
            SpongeImpl.postEvent(event);
            if (event.isCancelled()) {
                return null;
            }
            return EntityList.createEntityFromNBT(compound, world);
        }
    }

    @Override
    public boolean bridge$chunkExists(final World world, final int x, final int z) {
        final ChunkPos chunkcoordintpair = new ChunkPos(x, z);

        // Sponge start - Chunk queue improvements
        // if (this.field_193415_c.contains(chunkcoordintpair)) {
        //     for (ChunkPos pendingChunkCoord : this.chunksToSave.keySet()) {
        //         if (pendingChunkCoord.equals(chunkcoordintpair)) {
        //             return true;
        //         }
        //     }
        // }
        if (this.chunksToSave.containsKey(chunkcoordintpair)) {
            return true;
        }
        // Sponge end

        return RegionFileCache.getChunkInputStream(this.chunkSaveLocation, x, z) != null;
    }

    /**
     * @author aikar - February 19th, 2017
     * @reason Chunk queue improvements.
     *
     * @param pos The chunk position to queue
     * @param compound The NBTTagCompound containing chunk data
     */
    @Overwrite
    protected void addChunkToPending(final ChunkPos pos, final NBTTagCompound compound) {
        synchronized (this.impl$lock) {
            this.chunksToSave.put(pos, compound);
        }
        this.impl$queue.add(new QueuedChunk(pos, compound));

        ThreadedFileIOBase.getThreadedIOInstance().queueIO((AnvilChunkLoader) (Object) this);
    }

    /**
     * @author aikar - February 19th, 2017
     * @reason Refactor entire method for chunk queue improvements.
     * @return Whether write was successful
     */
    @Overwrite
    public boolean writeNextIO() {
        final QueuedChunk chunk = this.impl$queue.poll();
        if (chunk == null) {
            if (this.flushing) {
                LOGGER.info("ThreadedAnvilChunkStorage ({}): All chunks are saved", new Object[] {this.chunkSaveLocation.getName()});
            }

            return false;
        } else {
            final ChunkPos chunkpos = chunk.coords;
            boolean lvt_3_1_;

            try {
                // this.field_193415_c.add(chunkpos);
                final NBTTagCompound nbttagcompound = chunk.compound;

                if (nbttagcompound != null) {
                    int attempts = 0;
                    Exception laste = null;
                    while (attempts++ < 5) {
                        try {
                            this.writeChunkData(chunkpos, nbttagcompound);
                            laste = null;
                            break;
                        } catch (final Exception exception) {
                            // LOGGER.error((String)"Failed to save chunk",
                            // (Throwable)exception);
                            laste = exception;
                        }
                        try {
                            Thread.sleep(10);
                        } catch (final InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    if (laste != null) {
                        laste.printStackTrace();
                    }
                }

                synchronized (this.impl$lock) {
                    if (this.chunksToSave.get(chunkpos) == nbttagcompound) {
                        this.chunksToSave.remove(chunkpos);
                    }
                }
                // Sponge - This will not equal if a newer version is still
                // pending
                lvt_3_1_ = true;
            } finally {
                // this.field_193415_c.remove(chunkpos);
            }

            return lvt_3_1_;
        }
    }

    @Override
    public Path bridge$getWorldDir() {
        return this.chunkSaveLocation.toPath();
    }

    @Redirect(method = "saveChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/storage/AnvilChunkLoader;addChunkToPending"
            + "(Lnet/minecraft/util/math/ChunkPos;Lnet/minecraft/nbt/NBTTagCompound;)V"))
    private void impl$useWorldChunkPosWhenQueuing(final AnvilChunkLoader anvilChunkLoader, final ChunkPos pos, final NBTTagCompound compound,
            final World worldIn, final Chunk chunkIn) {
        this.addChunkToPending(new WorldChunkPos(((org.spongepowered.api.world.World) worldIn).getUniqueId(), pos.x, pos.z), compound);
    }

    @Inject(method = "writeChunkData", at = @At("RETURN"))
    private void impl$callSaveChunkEventPost(final ChunkPos pos, final NBTTagCompound compound, final CallbackInfo ci) {
        if (ShouldFire.SAVE_CHUNK_EVENT_POST) {
            final Cause cause = Cause.of(EventContext.empty(), Collections.singleton(SpongeImpl.getServer()));
            final Optional<UUID> worldUUID;
            if (pos instanceof WorldChunkPos) {
                worldUUID = Optional.of(((WorldChunkPos) pos).getWorldUUID());
            } else {
                worldUUID = Optional.empty();
            }
            SpongeImpl.postEvent(SpongeEventFactory.createSaveChunkEventPost(cause, VecHelper.toVec3i(pos), worldUUID));
        }
    }
}
