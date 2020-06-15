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
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.IPacket;
import net.minecraft.network.play.server.SChangeBlockPacket;
import net.minecraft.network.play.server.SPlaySoundPacket;
import net.minecraft.network.play.server.SStopSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.GameType;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.LightType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.AbstractChunkProvider;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.dimension.Dimension;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.storage.WorldInfo;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.Server;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.data.Keys;
import org.spongepowered.api.effect.particle.ParticleEffect;
import org.spongepowered.api.effect.sound.SoundType;
import org.spongepowered.api.effect.sound.music.MusicDisc;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.service.context.Context;
import org.spongepowered.api.text.BookView;
import org.spongepowered.api.text.title.Title;
import org.spongepowered.api.util.TemporalUnits;
import org.spongepowered.api.world.BlockChangeFlag;
import org.spongepowered.api.world.BlockChangeFlags;
import org.spongepowered.api.world.BoundedWorldView;
import org.spongepowered.api.world.HeightTypes;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.chunk.Chunk;
import org.spongepowered.api.world.volume.archetype.ArchetypeVolume;
import org.spongepowered.api.world.volume.block.ImmutableBlockVolume;
import org.spongepowered.api.world.volume.block.UnmodifiableBlockVolume;
import org.spongepowered.api.world.volume.entity.ImmutableEntityVolume;
import org.spongepowered.api.world.volume.entity.UnmodifiableEntityVolume;
import org.spongepowered.api.world.weather.Weather;
import org.spongepowered.api.world.weather.Weathers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.accessor.network.play.server.SChangeBlockPacketAccessor;
import org.spongepowered.common.accessor.world.server.ChunkManagerAccessor;
import org.spongepowered.common.block.SpongeBlockSnapshotBuilder;
import org.spongepowered.common.bridge.world.ServerWorldBridge;
import org.spongepowered.common.bridge.world.chunk.ChunkBridge;
import org.spongepowered.common.effect.record.SpongeRecordType;
import org.spongepowered.common.event.tracking.TrackingUtil;
import org.spongepowered.common.util.BookFaker;
import org.spongepowered.common.util.Constants;
import org.spongepowered.common.util.VecHelper;
import org.spongepowered.common.world.storage.SpongeChunkLayout;
import org.spongepowered.math.vector.Vector3d;
import org.spongepowered.math.vector.Vector3i;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Mixin(net.minecraft.world.World.class)
public abstract class WorldMixin_API<W extends World<W>> implements IWorldMixin_API<W>, World<W>, IEnvironmentBlockReaderMixin_API, AutoCloseable {

    @Shadow public @Final Random rand;
    @Shadow protected @Final WorldInfo worldInfo;

    // Shadowed methods and fields for reference. All should be prefixed with 'shadow$' to avoid confusion

    @Shadow public abstract Biome shadow$getBiome(BlockPos p_180494_1_);
    @Shadow public abstract @javax.annotation.Nullable MinecraftServer shadow$getServer();
    @Shadow public abstract net.minecraft.world.chunk.Chunk shadow$getChunkAt(BlockPos p_175726_1_);
    @Shadow public abstract IChunk shadow$getChunk(int p_217353_1_, int p_217353_2_, ChunkStatus p_217353_3_, boolean p_217353_4_);
    @Shadow public abstract boolean shadow$setBlockState(BlockPos p_180501_1_, BlockState p_180501_2_, int p_180501_3_);
    @Shadow public abstract boolean shadow$removeBlock(BlockPos p_217377_1_, boolean p_217377_2_);
    @Shadow public abstract boolean shadow$destroyBlock(BlockPos p_175655_1_, boolean p_175655_2_);
    @Shadow public abstract int shadow$getHeight(Heightmap.Type p_201676_1_, int p_201676_2_, int p_201676_3_);
    @Shadow public abstract int shadow$getLightFor(LightType p_175642_1_, BlockPos p_175642_2_);
    @Shadow public abstract BlockState shadow$getBlockState(BlockPos p_180495_1_);
    @Shadow public abstract void shadow$playSound(@javax.annotation.Nullable PlayerEntity p_184148_1_, double p_184148_2_, double p_184148_4_, double p_184148_6_, SoundEvent p_184148_8_, SoundCategory p_184148_9_, float p_184148_10_, float p_184148_11_);
    @Shadow public abstract @javax.annotation.Nullable TileEntity shadow$getTileEntity(BlockPos p_175625_1_);
    @Shadow public abstract List<Entity> shadow$getEntitiesInAABBexcluding(@javax.annotation.Nullable Entity p_175674_1_, AxisAlignedBB p_175674_2_, @javax.annotation.Nullable Predicate<? super Entity> p_175674_3_);
    @Shadow public abstract <T extends Entity> List<T> shadow$getEntitiesWithinAABB(Class<? extends T> p_175647_1_, AxisAlignedBB p_175647_2_, @javax.annotation.Nullable Predicate<? super T> p_175647_3_);
    @Shadow public abstract int shadow$getSeaLevel();
    @Shadow public abstract long shadow$getSeed();
    @Shadow public abstract AbstractChunkProvider shadow$getChunkProvider();
    @Shadow public abstract WorldInfo shadow$getWorldInfo();
    @Shadow public abstract boolean shadow$isThundering();
    @Shadow public abstract boolean shadow$isRaining();
    @Shadow public abstract DifficultyInstance shadow$getDifficultyForLocation(BlockPos p_175649_1_);
    @Shadow public abstract int shadow$getSkylightSubtracted();
    @Shadow public abstract WorldBorder shadow$getWorldBorder();
    @Shadow public abstract Dimension shadow$getDimension();
    @Shadow public abstract Random shadow$getRandom();
    @Shadow public abstract boolean shadow$hasBlockState(BlockPos p_217375_1_, Predicate<BlockState> p_217375_2_);
    @Shadow public abstract BlockPos shadow$getHeight(Heightmap.Type p_205770_1_, BlockPos p_205770_2_);

    // World

    @Override
    public Server getServer() {
        return null; // TODO API Optional or remove from non ServerWorld?
    }

    @Override
    public boolean isLoaded() {
        return SpongeImpl.getWorldManager().getWorld(this.shadow$getDimension().getType()) == (Object) this;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Optional<? extends Player> getClosestPlayer(int x, int y, int z, double distance, Predicate<? super Player> predicate) {
        final PlayerEntity player = this.shadow$getClosestPlayer(x, y, z, distance, (Predicate) predicate);
        return Optional.ofNullable((Player) player);
    }

    @Override
    public BlockSnapshot createSnapshot(int x, int y, int z) {
        if (!this.containsBlock(x, y, z)) {
            return BlockSnapshot.empty();
        }

        if (!this.isChunkLoaded(x, y, z, false)) { // TODO bitshift in old impl?
            return BlockSnapshot.empty();
        }
        final BlockPos pos = new BlockPos(x, y, z);
        final SpongeBlockSnapshotBuilder builder = SpongeBlockSnapshotBuilder.pooled();
        builder.worldId(this.getProperties().getUniqueId())
                .position(new Vector3i(x, y, z));
        final net.minecraft.world.chunk.Chunk chunk = this.shadow$getChunkAt(pos);
        final net.minecraft.block.BlockState state = chunk.getBlockState(pos);
        builder.blockState(state);
        final net.minecraft.tileentity.TileEntity tile = chunk.getTileEntity(pos, net.minecraft.world.chunk.Chunk.CreateEntityType.CHECK);
        if (tile != null) {
            TrackingUtil.addTileEntityToBuilder(tile, builder);
        }
        ((ChunkBridge) chunk).bridge$getBlockOwnerUUID(pos).ifPresent(builder::creator);
        ((ChunkBridge) chunk).bridge$getBlockNotifierUUID(pos).ifPresent(builder::notifier);

        builder.flag(BlockChangeFlags.NONE);
        return builder.build();
    }

    @Override
    public boolean restoreSnapshot(BlockSnapshot snapshot, boolean force, BlockChangeFlag flag) {
        return snapshot.restore(force, flag);
    }

    @Override
    public boolean restoreSnapshot(int x, int y, int z, BlockSnapshot snapshot, boolean force, BlockChangeFlag flag) {
        return snapshot.withLocation(Location.of(this, x, y, z)).restore(force, flag);
    }

    @Override
    public Chunk getChunk(int cx, int cy, int cz) {
        return (Chunk) IWorldMixin_API.super.getChunk(cx, cy, cz);
    }

    @Override
    public Optional<Chunk> loadChunk(int cx, int cy, int cz, boolean shouldGenerate) {
        if (!SpongeChunkLayout.instance.isValidChunk(cx, cy, cz)) {
            return Optional.empty();
        }
        final AbstractChunkProvider chunkProvider = this.shadow$getChunkProvider();
        // If we aren't generating, return the chunk
        if (!shouldGenerate) {
            // TODO correct ChunkStatus?
            return Optional.ofNullable((Chunk) chunkProvider.getChunk(cx, cz, ChunkStatus.EMPTY, true));
        }
        // TODO correct ChunkStatus?
        return Optional.ofNullable((Chunk) chunkProvider.getChunk(cx, cz, ChunkStatus.FULL, true));
    }

    @Override
    public Iterable<Chunk> getLoadedChunks() {
        final AbstractChunkProvider chunkProvider = this.shadow$getChunkProvider();
        if (chunkProvider instanceof ServerChunkProvider) {
            final ChunkManagerAccessor chunkManager = (ChunkManagerAccessor) ((ServerChunkProvider) chunkProvider).chunkManager;
            final List<Chunk> chunks = new ArrayList<>();
            chunkManager.accessor$getLoadedChunksIterable().forEach(holder -> chunks.add((Chunk) holder.func_219298_c()));
            return chunks;
        }
        return Collections.emptyList();
    }

    // LocationCreator

    @Override
    public Location getLocation(Vector3i position) {
        return Location.of(this, position);
    }

    @Override
    public Location getLocation(Vector3d position) {
        return Location.of(this, position);
    }

    // ReadableBlockVolume

    @Override
    public UnmodifiableBlockVolume<?> asUnmodifiableBlockVolume() {
    }

    @Override
    public ImmutableBlockVolume asImmutableBlockVolume() {
    }

    @Override
    public int getHighestYAt(int x, int z) {
        return this.getHeight(HeightTypes.WORLD_SURFACE.get(), x, z);
    }

    // Volume

    @Override
    public Vector3i getBlockMin() {
        return Constants.World.BLOCK_MIN;
    }

    @Override
    public Vector3i getBlockMax() {
        return Constants.World.BIOME_MAX;
    }

    @Override
    public Vector3i getBlockSize() {
        return Constants.World.BLOCK_SIZE;
    }

    @Override
    public BoundedWorldView<W> getView(Vector3i newMin, Vector3i newMax) {
    }

    // ReadableEntityVolume

    @Override
    public UnmodifiableEntityVolume<?> asUnmodifiableEntityVolume() {
    }

    @Override
    public ImmutableEntityVolume asImmutableEntityVolume() {
    }

    @Override
    public Collection<? extends Player> getPlayers() {
        return IWorldMixin_API.super.getPlayers();
    }

    // WeatherUniverse

    @Override
    public Weather getWeather() {
        if (this.shadow$isThundering()) {
            return Weathers.THUNDER_STORM.get();
        }
        if (this.shadow$isRaining()) {
            return Weathers.RAIN.get();
        }
        return Weathers.CLEAR.get();
    }

    @Override
    public Duration getRemainingWeatherDuration() {
        return Duration.of(this.impl$getDurationInTicks(), TemporalUnits.MINECRAFT_TICKS);
    }

    private long impl$getDurationInTicks() {
        if (this.shadow$isThundering()) {
            return this.worldInfo.getThunderTime();
        }
        if (this.shadow$isRaining()) {
            return this.worldInfo.getRainTime();
        }
        if (this.worldInfo.getClearWeatherTime() > 0) {
            return this.worldInfo.getClearWeatherTime();
        }
        return Math.min(this.worldInfo.getThunderTime(), this.worldInfo.getRainTime());
    }

    @Override
    public Duration getRunningWeatherDuration() {
        return Duration.of(this.worldInfo.getGameTime() - ((ServerWorldBridge) this).bridge$getWeatherStartTime(), TemporalUnits.MINECRAFT_TICKS);
    }

    @Override
    public void setWeather(Weather weather) {
        this.impl$setWeather(weather, (300 + this.rand.nextInt(600)) * 20);
    }

    @Override
    public void setWeather(Weather weather, Duration duration) {
        ((ServerWorldBridge) this).bridge$setPreviousWeather(this.getWeather());
        int ticks = (int) (duration.toMillis() / TemporalUnits.MINECRAFT_TICKS.getDuration().toMillis());
        this.impl$setWeather(weather, ticks);
    }

    public void impl$setWeather(Weather weather, int ticks) {
        if (weather == Weathers.CLEAR.get()) {
            this.worldInfo.setClearWeatherTime(ticks);
            this.worldInfo.setRainTime(0);
            this.worldInfo.setThunderTime(0);
            this.worldInfo.setRaining(false);
            this.worldInfo.setThundering(false);
        } else if (weather == Weathers.RAIN.get()) {
            this.worldInfo.setClearWeatherTime(0);
            this.worldInfo.setRainTime(ticks);
            this.worldInfo.setThunderTime(ticks);
            this.worldInfo.setRaining(true);
            this.worldInfo.setThundering(false);
        } else if (weather == Weathers.THUNDER_STORM.get()) {
            this.worldInfo.setClearWeatherTime(0);
            this.worldInfo.setRainTime(ticks);
            this.worldInfo.setThunderTime(ticks);
            this.worldInfo.setRaining(true);
            this.worldInfo.setThundering(true);
        }
    }

    // ContextSource

    private Context impl$worldContext;

    @Override
    public Context getContext() {
        if (this.impl$worldContext == null) {
            WorldInfo worldInfo = this.shadow$getWorldInfo();
            if (worldInfo == null) {
                // We still have to consider some mods are making dummy worlds that
                // override getWorldInfo with a null, or submit a null value.
                worldInfo = new WorldInfo(new WorldSettings(0, GameType.NOT_SET, false, false, WorldType.DEFAULT), "sponge$dummy_World");
            }
            this.impl$worldContext = new Context(Context.WORLD_KEY, worldInfo.getWorldName());
        }
        return this.impl$worldContext;
    }

    // TrackedVolume

    @Override
    public Optional<UUID> getCreator(int x, int y, int z) {
        return this.get(x, y, z, Keys.CREATOR);
    }

    @Override
    public Optional<UUID> getNotifier(int x, int y, int z) {
        return this.get(x, y, z, Keys.NOTIFIER);
    }

    @Override
    public void setCreator(int x, int y, int z, @Nullable UUID uuid) {
        this.offer(x, y, z, Keys.CREATOR, uuid);
    }

    @Override
    public void setNotifier(int x, int y, int z, @Nullable UUID uuid) {
        this.offer(x, y, z, Keys.NOTIFIER, uuid);
    }

    // Viewer

    @Override
    public void spawnParticles(ParticleEffect particleEffect, Vector3d position, int radius) {
        Preconditions.checkNotNull(particleEffect, "The particle effect cannot be null!");
        Preconditions.checkNotNull(position, "The position cannot be null");
        Preconditions.checkArgument(radius > 0, "The radius has to be greater then zero!");

        final List<IPacket<?>> packets = SpongeParticleHelper.toPackets((SpongeParticleEffect) particleEffect, position);

        if (!packets.isEmpty()) {
            final PlayerList playerList = this.shadow$getServer().getPlayerList();

            final double x = position.getX();
            final double y = position.getY();
            final double z = position.getZ();

            for (final IPacket<?> packet : packets) {
                playerList.sendToAllNearExcept(null, x, y, z, radius, this.shadow$getDimension().getType(), packet);
            }
        }
    }

    @Override
    public void playSound(SoundType sound, org.spongepowered.api.effect.sound.SoundCategory category, Vector3d position, double volume, double pitch, double minVolume) {
        // Check if the event is registered (ie has an integer ID)
        final ResourceLocation soundKey = (ResourceLocation) (Object) sound.getKey();
        final Optional<SoundEvent> event = Registry.SOUND_EVENT.getValue(soundKey);
        final SoundCategory soundCategory = (SoundCategory) (Object) category;
        final float soundVolume = (float) Math.max(minVolume, volume);
        if (event.isPresent()) {
            this.shadow$playSound(null, position.getX(), position.getY(), position.getZ(), event.get(), soundCategory, soundVolume, (float) pitch);
        } else {
            // Otherwise send it as a custom sound
            final double radius = volume > 1.0F ? (16.0F * volume) : 16.0D;
            final SPlaySoundPacket packet = new SPlaySoundPacket(soundKey, soundCategory, VecHelper.toVec3d(position), soundVolume, (float) pitch);
            this.shadow$getServer().getPlayerList().sendToAllNearExcept(null, position.getX(), position.getY(), position.getZ(), radius,
                    this.shadow$getDimension().getType(), packet);
        }
    }

    private void apiImpl$stopSounds(@javax.annotation.Nullable final SoundType sound, @javax.annotation.Nullable final org.spongepowered.api.effect.sound.SoundCategory category) {
        this.shadow$getServer().getPlayerList().sendPacketToAllPlayersInDimension(new SStopSoundPacket((ResourceLocation) (Object) sound.getKey(),
                (net.minecraft.util.SoundCategory) (Object) category), this.shadow$getDimension().getType());
    }

    @Override
    public void stopSounds() {
        this.apiImpl$stopSounds(null, null);
    }

    @Override
    public void stopSounds(SoundType sound) {
        this.apiImpl$stopSounds(checkNotNull(sound, "sound"), null);
    }

    @Override
    public void stopSoundTypes(Supplier<? extends SoundType> sound) {
        this.stopSounds(sound.get());
    }

    @Override
    public void stopSounds(org.spongepowered.api.effect.sound.SoundCategory category) {
        this.apiImpl$stopSounds(null, checkNotNull(category, "category"));
    }

    @Override
    public void stopSoundCategoriess(Supplier<? extends org.spongepowered.api.effect.sound.SoundCategory> category) {
        this.stopSounds(category.get());
    }

    @Override
    public void stopSounds(SoundType sound, org.spongepowered.api.effect.sound.SoundCategory category) {
        this.apiImpl$stopSounds(checkNotNull(sound, "sound"), checkNotNull(category, "category"));
    }

    @Override
    public void stopSounds(Supplier<? extends SoundType> sound, Supplier<? extends org.spongepowered.api.effect.sound.SoundCategory> category) {
        this.stopSounds(sound.get(), category.get());
    }

    private void api$playRecord(final Vector3i position, @javax.annotation.Nullable final MusicDisc recordType) {
        this.shadow$getServer().getPlayerList().sendPacketToAllPlayersInDimension(
                SpongeRecordType.createPacket(position, recordType), this.shadow$getDimension().getType());
    }

    @Override
    public void playMusicDisc(Vector3i position, MusicDisc musicDiscType) {
        this.api$playRecord(position, Preconditions.checkNotNull(musicDiscType, "recordType"));
    }

    @Override
    public void playMusicDisc(Vector3i position, Supplier<? extends MusicDisc> musicDiscType) {
        this.playMusicDisc(position, musicDiscType.get());
    }

    @Override
    public void stopMusicDisc(Vector3i position) {
        this.api$playRecord(position, null);
    }

    @Override
    public void sendTitle(Title title) {
        checkNotNull(title, "title");

        for (Player player : getPlayers()) {
            player.sendTitle(title);
        }
    }

    @Override
    public void sendBookView(BookView bookView) {
        checkNotNull(bookView, "bookview");

        BookFaker.fakeBookView(bookView, this.getPlayers());
    }

    @Override
    public void sendBlockChange(int x, int y, int z, org.spongepowered.api.block.BlockState state) {
        checkNotNull(state, "state");
        SChangeBlockPacket packet = new SChangeBlockPacket();
        ((SChangeBlockPacketAccessor) packet).accessor$setPos(new BlockPos(x, y, z));
        ((SChangeBlockPacketAccessor) packet).accessor$setState((BlockState) state);

        this.shadow$getPlayers().stream()
                .filter(ServerPlayerEntity.class::isInstance)
                .map(ServerPlayerEntity.class::cast)
                .forEach(p -> p.connection.sendPacket(packet));
    }

    @Override
    public void resetBlockChange(int x, int y, int z) {
        SChangeBlockPacket packet = new SChangeBlockPacket((IWorldReader) this, new BlockPos(x, y, z));

        this.shadow$getPlayers().stream()
                .filter(ServerPlayerEntity.class::isInstance)
                .map(ServerPlayerEntity.class::cast)
                .forEach(p -> p.connection.sendPacket(packet));
    }

    // ArchetypeVolumeCreator

    @Override
    public ArchetypeVolume createArchetypeVolume(Vector3i min, Vector3i max, Vector3i origin) {
    }
}
