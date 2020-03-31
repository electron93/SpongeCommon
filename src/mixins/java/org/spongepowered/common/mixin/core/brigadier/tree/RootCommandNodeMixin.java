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
package org.spongepowered.common.mixin.core.brigadier.tree;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.common.accessor.brigadier.tree.CommandNodeAccessor;
import org.spongepowered.common.bridge.brigadier.tree.RootCommandNodeBridge;

@Mixin(value = RootCommandNode.class, remap = false)
public abstract class RootCommandNodeMixin<S> extends CommandNodeMixin<S> implements RootCommandNodeBridge<S> {

    @Override
    public void bridge$removeNode(CommandNode<S> nodeToRemove) {
        CommandNodeAccessor<S> accessor = (CommandNodeAccessor<S>) this;
        if (accessor.accessor$getChildren().containsValue(nodeToRemove)) {
            // It's backed by the map, so it'll remove the corresponding key.
            accessor.accessor$getChildren().values().remove(nodeToRemove);
            if (nodeToRemove instanceof LiteralCommandNode) {
                accessor.accessor$getLiterals().values().remove(nodeToRemove);
            } else if (nodeToRemove instanceof ArgumentCommandNode) {
                accessor.accessor$getArguments().values().remove(nodeToRemove);
            }
        }
    }

}
