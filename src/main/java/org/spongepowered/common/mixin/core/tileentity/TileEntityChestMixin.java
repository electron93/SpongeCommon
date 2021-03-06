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
package org.spongepowered.common.mixin.core.tileentity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.SoundCategory;
import org.spongepowered.api.util.annotation.NonnullByDefault;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.common.item.inventory.adapter.InventoryAdapter;
import org.spongepowered.common.item.inventory.lens.Fabric;
import org.spongepowered.common.item.inventory.lens.SlotProvider;
import org.spongepowered.common.item.inventory.lens.impl.ReusableLens;
import org.spongepowered.common.item.inventory.lens.impl.minecraft.SingleGridLens;
import org.spongepowered.common.item.inventory.lens.impl.collections.SlotCollection;

@SuppressWarnings("rawtypes")
@NonnullByDefault
@Mixin(TileEntityChest.class)
public abstract class TileEntityChestMixin extends TileEntityLockableLootMixin {

    @Shadow public float lidAngle;
    @Shadow public int numPlayersUsing;
    @Shadow public TileEntityChest adjacentChestZNeg;
    @Shadow public TileEntityChest adjacentChestXPos;
    @Shadow public TileEntityChest adjacentChestXNeg;
    @Shadow public TileEntityChest adjacentChestZPos;

    @Shadow public abstract void checkForAdjacentChests();
    @Shadow public abstract int getSizeInventory();

    @Override
    public ReusableLens<?> bridge$generateReusableLens(final Fabric fabric, final InventoryAdapter adapter) {
        return ReusableLens.getLens(SingleGridLens.class, this, this::impl$generateSlotProvider, this::impl$generateRootLens);
    }

    private SlotProvider impl$generateSlotProvider() {
        return new SlotCollection.Builder().add(this.getSizeInventory()).build();
    }

    @SuppressWarnings("unchecked")
    private SingleGridLens impl$generateRootLens(final SlotProvider slots) {
        return new SingleGridLens(0, 9, this.getSizeInventory() / 9, (Class)TileEntityChest.class, slots);
    }

    /**
     * @author bloodmc - July 21st, 2016
     *
     * @reason Overwritten in case chests ever attempt to tick
     */
    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void impl$DisableTickingChestsOnServer(final CallbackInfo ci) {
        if (this.world == null || !this.world.isRemote) {
            // chests should never tick on server
            ci.cancel();
        }
    }

    @Inject(method = "openInventory",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;addBlockEvent(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/Block;II)V"),
        cancellable = true)
    private void impl$Moved(final EntityPlayer player, final CallbackInfo ci) {
        // Moved out of tick loop
        if (this.world == null) {
            ci.cancel();
            return;
        }
        if (this.world.isRemote) {
            return;
        }

        this.checkForAdjacentChests();
        if (this.numPlayersUsing > 0 && this.lidAngle == 0.0F && this.adjacentChestZNeg == null && this.adjacentChestXNeg == null) {
            this.lidAngle = 0.7F;
            double posX = this.pos.getX() + 0.5D;
            final double posY = this.pos.getY() + 0.5D;
            double posZ = this.pos.getZ() + 0.5D;

            if (this.adjacentChestXPos != null) {
                posX += 0.5D;
            }

            if (this.adjacentChestZPos != null) {
                posZ += 0.5D;
            }

            this.world.playSound(null, posX, posY, posZ, SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.5F, this.world.rand.nextFloat() * 0.1F + 0.9F);
        }
    }

    @Inject(
        method = "closeInventory",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/World;addBlockEvent(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/Block;II)V"),
        cancellable = true)
    private void impl$MovedSoundOutofTickLoop(final EntityPlayer player, final CallbackInfo ci) {
        // Moved out of tick loop
        if (this.world == null) {
            ci.cancel();
            return;
        }
        if (this.world.isRemote) {
            return;
        }

        if (this.numPlayersUsing == 0 && this.lidAngle > 0.0F || this.numPlayersUsing > 0 && this.lidAngle < 1.0F) {
            final float f = 0.1F;

            if (this.numPlayersUsing > 0) {
                this.lidAngle += f;
            } else {
                this.lidAngle = 0.0f;
            }

            double posX = this.pos.getX() + 0.5D;
            final double posY = this.pos.getY() + 0.5D;
            double posZ = this.pos.getZ() + 0.5D;

            if (this.adjacentChestXPos != null) {
                posX += 0.5D;
            }

            if (this.adjacentChestZPos != null) {
                posZ += 0.5D;
            }

            this.world.playSound(null, posX, posY, posZ, SoundEvents.BLOCK_CHEST_CLOSE, SoundCategory.BLOCKS, 0.5F, this.world.rand.nextFloat() * 0.1F + 0.9F);
        }
    }

}

