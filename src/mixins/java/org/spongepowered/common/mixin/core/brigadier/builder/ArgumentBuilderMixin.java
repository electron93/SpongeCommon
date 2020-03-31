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
package org.spongepowered.common.mixin.core.brigadier.builder;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.common.bridge.brigadier.builder.ArgumentBuilderBridge;

import java.util.function.Predicate;

@Mixin(value = ArgumentBuilder.class, remap = false)
public abstract class ArgumentBuilderMixin<S, T extends ArgumentBuilder<S, T>> implements ArgumentBuilderBridge<S, T> {

    @Shadow @Final private RootCommandNode<S> arguments;
    @Shadow private boolean forks;
    @Shadow private Predicate<S> requirement;
    @Shadow private Command<S> command;
    @Shadow private RedirectModifier<S> modifier;
    @Shadow private CommandNode<S> target;

    @Shadow
    public abstract T shadow$then(CommandNode<S> argument);

    @SuppressWarnings("unchecked")
    public T bridge$cloneFrom(ArgumentBuilder<S, T> builder) {
        for (CommandNode<S> commandNode : this.arguments.getChildren()) {
            this.shadow$then(commandNode);
        }

        this.requirement = builder.getRequirement();
        this.forks = builder.isFork();
        this.command = builder.getCommand();
        this.modifier = builder.getRedirectModifier();
        this.target = builder.getRedirect();
        return (T) (Object) this;
    }

    @Override
    public RootCommandNode<S> bridge$getRootCommandNode() {
        return this.arguments;
    }

}
