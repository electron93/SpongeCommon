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
package org.spongepowered.common.mixin.core.server.management;

import com.flowpowered.math.vector.Vector3d;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockCommandBlock;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockStructure;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemDoor;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.network.play.server.SPacketCloseWindow;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.ILockableContainer;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.cause.EventContextKeys;
import org.spongepowered.api.util.Tristate;
import org.spongepowered.api.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.event.SpongeCommonEventFactory;
import org.spongepowered.common.interfaces.IMixinContainer;
import org.spongepowered.common.interfaces.server.management.IMixinPlayerInteractionManager;
import org.spongepowered.common.interfaces.world.IMixinWorld;
import org.spongepowered.common.item.inventory.util.ItemStackUtil;
import org.spongepowered.common.registry.provider.DirectionFacingProvider;

@Mixin(value = PlayerInteractionManager.class)
public abstract class MixinPlayerInteractionManager implements IMixinPlayerInteractionManager {

    @Shadow public EntityPlayerMP player;
    @Shadow public net.minecraft.world.World world;
    @Shadow private GameType gameType;

    @Shadow public abstract boolean isCreative();

    /**
     * @author gabizou - September 5th, 2018
     * @reason Due to the way that buckets and the like can be handled
     * on the client, often times we need to cancel the item stack usage
     * due to server side cancellation logic that may not exist on the client.
     * Therefor, the cancellation of possible block changes doesn't take
     * effect, and therefor requires telling the client to set back the item
     * in hand.
     *
     * @param actionResult The action result returned from useItemRightClick, which if
     *  the result is FAIL, then we should be setting the item in hand back.
     * @param player The player
     * @param worldIn The world
     * @param stack The stack
     * @param hand The hand
     * @return The result, but we will inject a "send inventory to player packet" fi it was failed
     */
    @Redirect(
        method = "processRightClick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/ActionResult;getType()Lnet/minecraft/util/EnumActionResult;",
            ordinal = 0 // We need to target only the first getType, since
                      // there's technically two getTypes that are being caught by the slice

        ),
        slice = @Slice(
            from = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/item/ItemStack;getMaxItemUseDuration()I",
                ordinal = 0 // This targets the first max use duration in the massive if statement.
            ),
            to = @At(
                value = "FIELD",
                target = "Lnet/minecraft/util/EnumActionResult;FAIL:Lnet/minecraft/util/EnumActionResult;"
            )
        )
    )
    private EnumActionResult spongeGetResultCheckForFailure(ActionResult<ItemStack> actionResult, EntityPlayer player, net.minecraft.world.World worldIn, ItemStack stack, EnumHand hand) {
        // Sanity checks on the world being used (hey, i don't know the rules about clients...
        // and if the world is in fact a responsible server world.
        final EnumActionResult result = actionResult.getType();
        if (!(worldIn instanceof IMixinWorld) || ((IMixinWorld) worldIn).isFake()) {
            return result;
        }

        // Otherwise, let's find out if it's a failed result
        if (result == EnumActionResult.FAIL && player instanceof EntityPlayerMP) {
            // Then, go ahead and tell the client about the change.
            // A few comments about this:
            // window id of -2 sets the player's inventory slot instead of the "held cursor"
            // Then, we need to get the slot index for the held item, which is always
            // playerMP.inventory.currentItem
            final EntityPlayerMP playerMP = (EntityPlayerMP) player;
            final SPacketSetSlot packetToSend;
            if (hand == EnumHand.MAIN_HAND) {
                // And here, my friends, is why the offhand slot is so stupid....
                packetToSend = new SPacketSetSlot(-2, player.inventory.currentItem, actionResult.getResult());
            } else {
                // This is the type of stupidity that comes from finding out that offhand slots
                // are always the last remaining slot index remaining of the player's overall inventory.
                // And this has to be done to avoid duplications by inadvertently setting the main hand
                // item.
                final int offhandSlotIndex = player.inventory.getSizeInventory() - 1;
                packetToSend = new SPacketSetSlot(-2, offhandSlotIndex, actionResult.getResult());
            }
            // And finally, set the packet.
            playerMP.connection.sendPacket(packetToSend);
            // this is a full stop re-sync to the client, code above might not actually matter.
            playerMP.sendContainerToPlayer(player.inventoryContainer);
        }
        return result;
    }

    /**
     * @author Aaron1011
     * @author gabizou - May 28th, 2016 - Rewritten for 1.9.4
     *
     * @reason Fire interact block event.
     */
    @Overwrite
    public EnumActionResult processRightClickBlock(EntityPlayer player, net.minecraft.world.World worldIn, ItemStack stack, EnumHand hand, BlockPos
            pos, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (this.gameType == GameType.SPECTATOR) {
            TileEntity tileentity = worldIn.getTileEntity(pos);

            if (tileentity instanceof ILockableContainer) {
                Block block = worldIn.getBlockState(pos).getBlock();
                ILockableContainer ilockablecontainer = (ILockableContainer) tileentity;

                if (ilockablecontainer instanceof TileEntityChest && block instanceof BlockChest) {
                    ilockablecontainer = ((BlockChest) block).getLockableContainer(worldIn, pos);
                }

                if (ilockablecontainer != null) {
                    // TODO - fire event
                    player.displayGUIChest(ilockablecontainer);
                    return EnumActionResult.SUCCESS;
                }
            } else if (tileentity instanceof IInventory) {
                // TODO - fire event
                player.displayGUIChest((IInventory) tileentity);
                return EnumActionResult.SUCCESS;
            }

            return EnumActionResult.PASS;

        } // else { // Sponge - Remove unecessary else
        // Sponge Start - Create an interact block event before something happens.
        final ItemStack oldStack = stack.copy();
        final Vector3d hitVec = new Vector3d(pos.getX() + hitX, pos.getY() + hitY, pos.getZ() + hitZ);
        final BlockSnapshot currentSnapshot = ((World) worldIn).createSnapshot(pos.getX(), pos.getY(), pos.getZ());
        Sponge.getCauseStackManager().addContext(EventContextKeys.USED_ITEM, ItemStackUtil.snapshotOf(oldStack));
        final boolean interactItemCancelled = SpongeCommonEventFactory.callInteractItemEventSecondary(player, oldStack, hand, hitVec, currentSnapshot).isCancelled();
        final InteractBlockEvent.Secondary event = SpongeCommonEventFactory.createInteractBlockEventSecondary(player, oldStack,
                hitVec, currentSnapshot, DirectionFacingProvider.getInstance().getKey(facing).get(), hand);
        if (interactItemCancelled) {
            event.setUseItemResult(Tristate.FALSE);
        }
        SpongeImpl.postEvent(event);
        if (!ItemStack.areItemStacksEqual(oldStack, this.player.getHeldItem(hand))) {
            SpongeCommonEventFactory.playerInteractItemChanged = true;
        }
        SpongeCommonEventFactory.lastInteractItemOnBlockCancelled = event.getUseItemResult() == Tristate.UNDEFINED ? false : !event.getUseItemResult().asBoolean();

        if (event.isCancelled()) {
            final IBlockState state = (IBlockState) currentSnapshot.getState();

            if (state.getBlock() == Blocks.COMMAND_BLOCK) {
                // CommandBlock GUI opens solely on the client, we need to force it close on cancellation
                this.player.connection.sendPacket(new SPacketCloseWindow(0));

            } else if (state.getProperties().containsKey(BlockDoor.HALF)) {
                // Stopping a door from opening while `g the top part will allow the door to open, we need to update the
                // client to resolve this
                if (state.getValue(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.LOWER) {
                    this.player.connection.sendPacket(new SPacketBlockChange(worldIn, pos.up()));
                } else {
                    this.player.connection.sendPacket(new SPacketBlockChange(worldIn, pos.down()));
                }

            } else if (!oldStack.isEmpty()) {
                // Stopping the placement of a door or double plant causes artifacts (ghosts) on the top-side of the block. We need to remove it
                final Item item = oldStack.getItem();
                if (item instanceof ItemDoor || (item instanceof ItemBlock && ((ItemBlock) item).getBlock().equals(Blocks.DOUBLE_PLANT))) {
                    this.player.connection.sendPacket(new SPacketBlockChange(worldIn, pos.up(2)));
                }
            }

            SpongeCommonEventFactory.interactBlockEventCancelled = true;
            return EnumActionResult.FAIL;
        }
        // Sponge End

        if (!player.isSneaking() || player.getHeldItemMainhand().isEmpty() && player.getHeldItemOffhand().isEmpty()) {
            // Sponge start - check event useBlockResult, and revert the client if it's FALSE.
            // Also, store the result instead of returning immediately
            if (event.getUseBlockResult() != Tristate.FALSE) {
                IBlockState iblockstate = (IBlockState) currentSnapshot.getState();
                Container lastOpenContainer = player.openContainer;

                EnumActionResult result = iblockstate.getBlock().onBlockActivated(worldIn, pos, iblockstate, player, hand, facing, hitX, hitY, hitZ)
                         ? EnumActionResult.SUCCESS
                         : EnumActionResult.PASS;
                // if itemstack changed, avoid restore
                if (!ItemStack.areItemStacksEqual(oldStack, this.player.getHeldItem(hand))) {
                    SpongeCommonEventFactory.playerInteractItemChanged = true;
                }

                result = this.handleOpenEvent(lastOpenContainer, this.player, currentSnapshot, result);

                if (result != EnumActionResult.PASS) {

                    return result;
                }
            } else {
                // Need to send a block change to the client, because otherwise, they are not
                // going to be told about the block change.
                this.player.connection.sendPacket(new SPacketBlockChange(this.world, pos));
                // Since the event was explicitly set to fail, we need to respect it and treat it as if
                // it wasn't cancelled, but perform no further processing.
                return EnumActionResult.FAIL;
            }
            // Sponge End
        }

        if (stack.isEmpty()) {
            return EnumActionResult.PASS;
        } else if (player.getCooldownTracker().hasCooldown(stack.getItem())) {
            return EnumActionResult.PASS;
        } else if (stack.getItem() instanceof ItemBlock) {
            Block block = ((ItemBlock)stack.getItem()).getBlock();

            if ((block instanceof BlockCommandBlock || block instanceof BlockStructure) && !player.canUseCommandBlock())
            {
                return EnumActionResult.FAIL;
            }
        } // else if (this.isCreative()) { // Sponge - Rewrite this to handle an isCreative check after the result, since we have a copied stack at the top of this method.
        //    int j = stack.getMetadata();
        //    int i = stack.stackSize;
        //    EnumActionResult enumactionresult = stack.onItemUse(player, worldIn, pos, hand, facing, offsetX, offsetY, offsetZ);
        //    stack.setItemDamage(j);
        //    stack.stackSize = i;
        //    return enumactionresult;
        // } else {
        //    return stack.onItemUse(player, worldIn, pos, hand, facing, offsetX, offsetY, offsetZ);
        // }
        // } // Sponge - Remove unecessary else bracket
        // Sponge Start - complete the method with the micro change of resetting item damage and quantity from the copied stack.
        EnumActionResult result = EnumActionResult.PASS;
        if (event.getUseItemResult() != Tristate.FALSE) {
            result = stack.onItemUse(player, worldIn, pos, hand, facing, hitX, hitY, hitZ);
            if (this.isCreative()) {
                stack.setItemDamage(oldStack.getItemDamage());
                stack.setCount(oldStack.getCount());
            }
        }

        if (!ItemStack.areItemStacksEqual(player.getHeldItem(hand), oldStack) || result != EnumActionResult.SUCCESS) {
            player.openContainer.detectAndSendChanges();
        }

        return result;
        // Sponge end
        // } // Sponge - Remove unecessary else bracket
    }

    @Override
    public EnumActionResult handleOpenEvent(Container lastOpenContainer, EntityPlayerMP player, BlockSnapshot blockSnapshot, EnumActionResult result) {
        if (lastOpenContainer != player.openContainer) {
            try (CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
                frame.pushCause(player);
                frame.addContext(EventContextKeys.BLOCK_HIT, blockSnapshot);
                ((IMixinContainer) player.openContainer).setOpenLocation(blockSnapshot.getLocation().orElse(null));
                if (!SpongeCommonEventFactory.callInteractInventoryOpenEvent(player)) {
                    result = EnumActionResult.FAIL;
                    SpongeCommonEventFactory.interactBlockEventCancelled = true;
                }
            }
        }
        return result;
    }
}
