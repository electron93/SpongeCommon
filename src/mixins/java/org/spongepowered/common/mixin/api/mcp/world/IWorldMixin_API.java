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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.effect.LightningBoltEntity;
import net.minecraft.entity.item.ArmorStandEntity;
import net.minecraft.entity.item.EnderPearlEntity;
import net.minecraft.entity.item.FallingBlockEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.item.PaintingEntity;
import net.minecraft.entity.item.PaintingType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.particles.IParticleData;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.ITickList;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.chunk.AbstractChunkProvider;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.storage.WorldInfo;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.data.Keys;
import org.spongepowered.api.data.persistence.DataContainer;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityType;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.projectile.EnderPearl;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.entity.SpawnEntityEvent;
import org.spongepowered.api.fluid.FluidType;
import org.spongepowered.api.projectile.source.ProjectileSource;
import org.spongepowered.api.scheduler.ScheduledUpdateList;
import org.spongepowered.api.util.AABB;
import org.spongepowered.api.world.BlockChangeFlag;
import org.spongepowered.api.world.BoundedWorldView;
import org.spongepowered.api.world.HeightType;
import org.spongepowered.api.world.ProtoWorld;
import org.spongepowered.api.world.biome.BiomeType;
import org.spongepowered.api.world.chunk.ProtoChunk;
import org.spongepowered.api.world.gen.TerrainGenerator;
import org.spongepowered.api.world.storage.WorldProperties;
import org.spongepowered.api.world.volume.entity.ImmutableEntityVolume;
import org.spongepowered.api.world.volume.entity.UnmodifiableEntityVolume;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.common.accessor.entity.LivingEntityAccessor;
import org.spongepowered.common.accessor.entity.MobEntityAccessor;
import org.spongepowered.common.entity.EntityUtil;
import org.spongepowered.common.entity.projectile.UnknownProjectileSource;
import org.spongepowered.common.event.tracking.IPhaseState;
import org.spongepowered.common.event.tracking.PhaseTracker;
import org.spongepowered.common.event.tracking.phase.plugin.BasicPluginContext;
import org.spongepowered.common.event.tracking.phase.plugin.PluginPhase;
import org.spongepowered.common.mixin.api.mcp.entity.EntityTypeMixin_API;
import org.spongepowered.common.util.NonNullArrayList;
import org.spongepowered.math.vector.Vector3d;
import org.spongepowered.math.vector.Vector3i;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.Nullable;

@Mixin(IWorld.class)
public interface IWorldMixin_API<T extends ProtoWorld<T>> extends IEntityReaderMixin_API, IWorldReaderMixin_API<BoundedWorldView<T>>, IWorldGenerationReaderMixin_API, ProtoWorld<T> {
    @Shadow long shadow$getSeed();
    @Shadow float shadow$getCurrentMoonPhaseFactor();
    @Shadow float shadow$getCelestialAngle(float p_72826_1_);
    @Shadow ITickList<Block> shadow$getPendingBlockTicks();
    @Shadow ITickList<Fluid> shadow$getPendingFluidTicks();
    @Shadow net.minecraft.world.World shadow$getWorld();
    @Shadow WorldInfo shadow$getWorldInfo();
    @Shadow DifficultyInstance shadow$getDifficultyForLocation(BlockPos p_175649_1_);
    @Shadow Difficulty shadow$getDifficulty();
    @Shadow AbstractChunkProvider shadow$getChunkProvider();
    @Shadow boolean shadow$chunkExists(int p_217354_1_, int p_217354_2_);
    @Shadow Random shadow$getRandom();
    @Shadow void shadow$notifyNeighbors(BlockPos p_195592_1_, Block p_195592_2_);
    @Shadow void shadow$playSound(@Nullable PlayerEntity p_184133_1_, BlockPos p_184133_2_, SoundEvent p_184133_3_, net.minecraft.util.SoundCategory p_184133_4_, float p_184133_5_, float p_184133_6_);
    @Shadow void shadow$addParticle(IParticleData p_195594_1_, double p_195594_2_, double p_195594_4_, double p_195594_6_, double p_195594_8_, double p_195594_10_, double p_195594_12_);
    @Shadow void shadow$playEvent(@Nullable PlayerEntity p_217378_1_, int p_217378_2_, BlockPos p_217378_3_, int p_217378_4_);
    @Shadow void shadow$playEvent(int p_217379_1_, BlockPos p_217379_2_, int p_217379_3_);
    @Shadow Stream<VoxelShape> shadow$getEmptyCollisionShapes(@Nullable net.minecraft.entity.Entity p_223439_1_, AxisAlignedBB p_223439_2_, Set<net.minecraft.entity.Entity> p_223439_3_) ;
    @Shadow boolean shadow$checkNoEntityCollision(@Nullable net.minecraft.entity.Entity p_195585_1_, VoxelShape p_195585_2_);

    // MutableBiomeVolume

    @Override
    default boolean setBiome(final int x, final int y, final int z, final BiomeType biome) {
        final IChunk iChunk = this.shadow$getChunk(x >> 4, z >> 4, ChunkStatus.BIOMES, true);
        if (iChunk == null) {
            return false;
        }
        return ((ProtoChunk) iChunk).setBiome(x, y, z, biome);
    }

    // Volume

    @Override
    default Vector3i getBlockMin() {
        throw new UnsupportedOperationException("Unfortunately, you've found an extended class of IWorld that isn't part of Sponge API: " + this.getClass());
    }

    @Override
    default Vector3i getBlockMax() {
        throw new UnsupportedOperationException("Unfortunately, you've found an extended class of IWorld that isn't part of Sponge API: " + this.getClass());
    }

    @Override
    default Vector3i getBlockSize() {
        throw new UnsupportedOperationException("Unfortunately, you've found an extended class of IWorld that isn't part of Sponge API: " + this.getClass());
    }

    @Override
    default boolean containsBlock(final int x, final int y, final int z) {
        return this.shadow$chunkExists(x >> 4, z >> 4);
    }

    @Override
    default boolean isAreaAvailable(final int x, final int y, final int z) {
        return this.shadow$chunkExists(x >> 4, z >> 4);
    }

    @Override
    default BoundedWorldView<T> getView(final Vector3i newMin, final Vector3i newMax) {
        throw new UnsupportedOperationException("Unfortunately, you've found an extended class of IWorld that isn't part of Sponge API: " + this.getClass());
    }

    // ReadableEntityVolume

    @Override
    default UnmodifiableEntityVolume<?> asUnmodifiableEntityVolume() {
        throw new UnsupportedOperationException("Unfortunately, you've found an extended class of IWorld that isn't part of Sponge API: " + this.getClass());
    }

    @Override
    default ImmutableEntityVolume asImmutableEntityVolume() {
        throw new UnsupportedOperationException("Unfortunately, you've found an extended class of IWorld that isn't part of Sponge API: " + this.getClass());
    }

    @Override
    default Optional<Entity> getEntity(final UUID uuid) {
        return Optional.empty();
    }

    @Override
    default Collection<? extends Player> getPlayers() {
        return IEntityReaderMixin_API.super.getPlayers();
    }

    @Override
    default Collection<? extends Entity> getEntities(final AABB box, final Predicate<? super Entity> filter) {
        return IEntityReaderMixin_API.super.getEntities(box, filter);
    }

    @Override
    default <E extends Entity> Collection<? extends E> getEntities(final Class<? extends E> entityClass, final AABB box,
                                                                   @Nullable final Predicate<? super E> predicate) {
        return IEntityReaderMixin_API.super.getEntities(entityClass, box, predicate);
    }

    // RandomProvider


    @Override
    default Random getRandom() {
        return this.shadow$getRandom();
    }

    @Override
    default long getSeed() {
        return this.shadow$getSeed();
    }

    @Override
    default TerrainGenerator<?> getTerrainGenerator() {
        return (TerrainGenerator<?>) this.shadow$getChunkProvider().getChunkGenerator();
    }

    @Override
    default WorldProperties getProperties() {
        return (WorldProperties) this.shadow$getWorldInfo();
    }

    @Override
    default boolean setBlock(final Vector3i position, final BlockState block) {
        throw new UnsupportedOperationException("Unfortunately, you've found an extended class of IWorld that isn't part of Sponge API: " + this.getClass());
    }

    @Override
    default boolean setBlock(final Vector3i position, final BlockState state, final BlockChangeFlag flag) {
        throw new UnsupportedOperationException("Unfortunately, you've found an extended class of IWorld that isn't part of Sponge API: " + this.getClass());
    }

    // MutableEntityVolume

    @Override
    default Entity createEntity(final EntityType<?> type, final Vector3d position) throws IllegalArgumentException, IllegalStateException {
        return this.impl$createEntity(type, position, false);
    }

    @Override
    default Entity createEntityNaturally(final EntityType<?> type, final Vector3d position) throws IllegalArgumentException, IllegalStateException {
        return this.impl$createEntity(type, position, true);
    }

    @Override
    default Optional<Entity> createEntity(final DataContainer entityContainer) {
    }

    @Override
    default Optional<Entity> createEntity(final DataContainer entityContainer, final Vector3d position) {
    }

    default Entity impl$createEntity(EntityType<?> type, Vector3d position, boolean naturally) throws IllegalArgumentException, IllegalStateException {
        checkNotNull(type, "The entity type cannot be null!");
        checkNotNull(position, "The position cannot be null!");

        if (type == net.minecraft.entity.EntityType.PLAYER) {
            // Unable to construct these
            throw new IllegalArgumentException("Cannot construct " + type.getKey() + " please look to using entity types correctly!");
        }

        net.minecraft.entity.Entity entity = null;
        final double x = position.getX();
        final double y = position.getY();
        final double z = position.getZ();
        // Not all entities have a single World parameter as their constructor
        if (type == net.minecraft.entity.EntityType.LIGHTNING_BOLT) {
            entity = new LightningBoltEntity((World) this, x, y, z, false);
        }
        // TODO - archetypes should solve the problem of calling the correct constructor
        if (type == net.minecraft.entity.EntityType.ENDER_PEARL) {
            ArmorStandEntity tempEntity = new ArmorStandEntity((World) this, x, y, z);
            tempEntity.posY -= tempEntity.getEyeHeight();
            entity = new EnderPearlEntity((World) this, tempEntity);
            ((EnderPearl) entity).offer(Keys.SHOOTER, UnknownProjectileSource.UNKNOWN);
        }
        // Some entities need to have non-null fields (and the easiest way to
        // set them is to use the more specialised constructor).
        if (type == net.minecraft.entity.EntityType.FALLING_BLOCK) {
            entity = new FallingBlockEntity((World) this, x, y, z, Blocks.SAND.getDefaultState());
        }
        if (type == net.minecraft.entity.EntityType.ITEM) {
            entity = new ItemEntity((World) this, x, y, z, new ItemStack(Blocks.STONE));
        }

        if (entity == null) {
            try {
                entity = ((net.minecraft.entity.EntityType) type).create((World) this);
                entity.setPosition(x, y, z);
            } catch (Exception e) {
                throw new RuntimeException("There was an issue attempting to construct " + type.getKey(), e);
            }
        }

        // TODO - replace this with an actual check
        /*
        if (entity instanceof EntityHanging) {
            if (((EntityHanging) entity).facingDirection == null) {
                // TODO Some sort of detection of a valid direction?
                // i.e scan immediate blocks for something to attach onto.
                ((EntityHanging) entity).facingDirection = EnumFacing.NORTH;
            }
            if (!((EntityHanging) entity).onValidSurface()) {
                return Optional.empty();
            }
        }*/

        if (naturally && entity instanceof MobEntity) {
            // Adding the default equipment
            final DifficultyInstance difficulty = this.shadow$getDifficultyForLocation(new BlockPos(x, y, z));
            ((MobEntityAccessor)entity).accessor$setEquipmentBasedOnDifficulty(difficulty);
        }

        if (entity instanceof PaintingEntity) {
            // This is default when art is null when reading from NBT, could
            // choose a random art instead?
            ((PaintingEntity) entity).art = PaintingType.KEBAB;
        }

        return (Entity) entity;
    }

    @Override
    default Collection<Entity> spawnEntities(final Iterable<? extends Entity> entities) {
        final List<Entity> entitiesToSpawn = new NonNullArrayList<>();
        entities.forEach(entitiesToSpawn::add);
        final SpawnEntityEvent.Custom event = SpongeEventFactory
                .createSpawnEntityEventCustom(Sponge.getCauseStackManager().getCurrentCause(), entitiesToSpawn);
        if (Sponge.getEventManager().post(event)) {
            return ImmutableList.of();
        }
        for (final Entity entity : event.getEntities()) {
            EntityUtil.processEntitySpawn(entity, Optional::empty);
        }

        final ImmutableList.Builder<Entity> builder = ImmutableList.builder();
        for (final Entity entity : event.getEntities()) {
            builder.add(entity);
        }
        return builder.build();
    }

    @Override
    default boolean spawnEntity(Entity entity) {
        return IWorldWriterMixin_API.super.spawnEntity(entity);

        Preconditions.checkNotNull(entity, "The entity cannot be null!");
        if (PhaseTracker.isEntitySpawnInvalid(entity)) {
            return true;
        }
        final PhaseTracker phaseTracker = PhaseTracker.getInstance();
        final IPhaseState<?> state = phaseTracker.getCurrentState();
        if (!state.alreadyCapturingEntitySpawns()) {
            try (final BasicPluginContext context = PluginPhase.State.CUSTOM_SPAWN.createPhaseContext(PhaseTracker.SERVER)) {
                context.buildAndSwitch();
                phaseTracker.spawnEntityWithCause(this, entity);
                return true;
            }
        }
        return phaseTracker.spawnEntityWithCause(this, entity);
    }

    // HeightAwareVolume

    @Override
    default int getHeight(final HeightType type, final int x, final int z) {
        return IWorldReaderMixin_API.super.getHeight(type, x, z);
    }

    // UpdateableVolume

    @Override
    default ScheduledUpdateList<BlockType> getScheduledBlockUpdates() {
    }

    @Override
    default ScheduledUpdateList<FluidType> getScheduledFluidUpdates() {
    }

}
