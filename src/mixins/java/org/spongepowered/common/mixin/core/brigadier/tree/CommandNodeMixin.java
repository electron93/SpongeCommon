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

import com.google.common.base.Preconditions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.common.bridge.brigadier.tree.CommandNodeBridge;
import org.spongepowered.common.accessor.brigadier.tree.CommandNodeAccessor;

import java.util.Arrays;

@Mixin(value = CommandNode.class, remap = false)
public abstract class CommandNodeMixin<S> implements CommandNodeBridge<S> {

    private String impl$stringRedirect;
    private CommandNode<S> impl$lazyRedirect;

    @Override
    public void bridge$provideRedirectString(final String redirectString) {
        Preconditions.checkArgument(
                ((CommandNodeAccessor<S>) this).accessor$getChildren().isEmpty(), "No children allowed if providing a redirect.");
        this.impl$stringRedirect = redirectString;
    }

    @Override
    public void bridge$resolveRedirect(final CommandDispatcher<S> dispatcher) {
        if (this.impl$stringRedirect != null) {
            this.impl$lazyRedirect = dispatcher.findNode(Arrays.asList(this.impl$stringRedirect.split(" ")));
            if (this.impl$lazyRedirect == null) {
                throw new IllegalStateException(
                        "Redirect on " + String.join(" ", dispatcher.getPath((CommandNode<S>) (Object) this)) + " is invalid");
            }
        }
    }

    @Redirect(method = "getRedirect",
            at = @At(
                    value = "FIELD",
                    opcode = Opcodes.GETFIELD,
                    target = "Lcom/mojang/brigadier/tree/CommandNode;redirect:Lcom/mojang/brigadier/tree/CommandNode;"))
    private CommandNode<S> impl$getLazyRedirect(final CommandNode<S> commandNode) {
        if (commandNode == null && this.impl$lazyRedirect != null) {
            return this.impl$lazyRedirect;
        }

        return commandNode;
    }

}
