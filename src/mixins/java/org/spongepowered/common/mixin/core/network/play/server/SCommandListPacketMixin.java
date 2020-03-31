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
package org.spongepowered.common.mixin.core.network.play.server;

import net.minecraft.command.arguments.SuggestionProviders;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SCommandListPacket;
import net.minecraft.util.ResourceLocation;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.command.registrar.tree.ClientCompletionKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.common.bridge.network.play.server.SCommandListPacketBridge;
import org.spongepowered.common.command.registrar.tree.AbstractCommandTreeBuilder;
import org.spongepowered.common.command.registrar.tree.ArgumentCommandTreeBuilder;
import org.spongepowered.common.command.registrar.tree.RootCommandTreeBuilder;

import java.util.Map;

@Mixin(SCommandListPacket.class)
public abstract class SCommandListPacketMixin implements SCommandListPacketBridge {

    @Nullable private RootCommandTreeBuilder impl$commandTreeBuilder;

    @Override
    public void bridge$addRootCommandTreeBuilder(RootCommandTreeBuilder rootCommandTreeBuilder) {
        if (this.impl$commandTreeBuilder == null) {
            this.impl$commandTreeBuilder = new RootCommandTreeBuilder();
        }

        this.impl$commandTreeBuilder.addChildren(rootCommandTreeBuilder.getChildren());
    }

    // This adds our own commands in that don't make use of Brigadier.
    private void impl$writeCommandNode(
            PacketBuffer buf,
            @Nullable String key,
            AbstractCommandTreeBuilder<?> node,
            Map<AbstractCommandTreeBuilder<?>, Integer> nodeIds) {
        buf.writeByte(node.getFlags());
        buf.writeVarInt(node.getChildren().size());

        for(AbstractCommandTreeBuilder<?> treeNode : node.getChildren().values()) {
            buf.writeVarInt(nodeIds.get(treeNode));
        }

        // Redirects are going to be a nightmare.
        // TODO: Node counting
        if (node.getRedirect() != null) {
            buf.writeVarInt(nodeIds.get(node.getRedirect()));
        }

        if (key != null) {
            buf.writeString(key);
            if (node instanceof ArgumentCommandTreeBuilder) {
                ArgumentCommandTreeBuilder<?> treeBuilder = (ArgumentCommandTreeBuilder<?>) node;
                ClientCompletionKey<?> clientCompletionKey = treeBuilder.getClientCompletionKey();
                buf.writeResourceLocation((ResourceLocation) (Object) clientCompletionKey.getKey());
                treeBuilder.applyProperties(buf);
                if (treeBuilder.isCustomSuggestions()) {
                    buf.writeResourceLocation(SuggestionProviders.getId(SuggestionProviders.ASK_SERVER));
                }
            }
        }

    }

}
