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
package org.spongepowered.common.mixin.core.brigadier.context;

import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.tree.CommandNode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.common.bridge.brigadier.CommandContextBuilderBridge;

import java.util.Map;

import javax.annotation.Nullable;

@Mixin(value = CommandContextBuilder.class, remap = false)
public abstract class CommandContextBuilderMixin<S> implements CommandContextBuilderBridge<S> {

    @Shadow @Final private Map<String, ParsedArgument<S, ?>> shadow$arguments;
    @Shadow private StringRange shadow$range;
    @Shadow @Nullable private RedirectModifier<S> shadow$modifier = null;
    @Shadow private boolean shadow$forks;

    @Override
    public RedirectModifier<S> bridge$getRedirectModifier() {
        return this.shadow$modifier;
    }

    @Override
    public boolean bridge$isForks() {
        return this.shadow$forks;
    }

    @Override
    public void bridge$setRedirectModifier(@Nullable RedirectModifier<S> redirectModifier) {
        this.shadow$modifier = redirectModifier;
    }

    @Override
    public void bridge$setFork(boolean fork) {
        this.shadow$forks = fork;
    }

    @Override
    public void bridge$setStringRange(StringRange range) {
        this.shadow$range = range;
    }

    @Override
    public void bridge$putArguments(Map<String, ParsedArgument<S, ?>> arguments) {
        this.shadow$arguments.putAll(arguments);
    }
}
