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
package org.spongepowered.common.command.brigadier.context;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.command.CommandSource;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.common.accessor.brigadier.context.CommandContextBuilderAccessor;
import org.spongepowered.common.bridge.brigadier.CommandContextBuilderBridge;
import org.spongepowered.common.command.parameter.SpongeParameterKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

public class SpongeCommandContextBuilder extends CommandContextBuilder<CommandSource>
        implements org.spongepowered.api.command.parameter.CommandContext.Builder {

    // The Sponge command system allows for multiple arguments to be stored under the same key.
    private final HashMap<Parameter.Key<?>, Collection<?>> arguments = new HashMap<>();

    public SpongeCommandContextBuilder(
            CommandDispatcher<CommandSource> dispatcher,
            CommandSource source,
            CommandNode<CommandSource> root,
            int start) {
        super(dispatcher, source, root, start);
    }

    @SuppressWarnings("unchecked")
    public SpongeCommandContextBuilder(CommandContextBuilder<CommandSource> original) {
        super(original.getDispatcher(), original.getSource(), original.getRootNode(), original.getRange().getStart());
        CommandContextBuilderBridge<CommandSource> copyMixinCommandContextBuilder = (CommandContextBuilderBridge<CommandSource>) this;
        CommandContextBuilderAccessor<CommandSource> accessorCommandContextBuilder = (CommandContextBuilderAccessor<CommandSource>) original;
        CommandContextBuilderAccessor<CommandSource> copyAccessorCommandContextBuilder = (CommandContextBuilderAccessor<CommandSource>) this;
        this.withChild(original.getChild());
        this.withCommand(original.getCommand());
        copyMixinCommandContextBuilder.bridge$putArguments(original.getArguments());
        copyAccessorCommandContextBuilder.accessor$setModifier(accessorCommandContextBuilder.accessor$getModifier());
        copyAccessorCommandContextBuilder.accessor$setForks(accessorCommandContextBuilder.accessor$isForks());
        copyAccessorCommandContextBuilder.accessor$setStringRange(this.getRange());
    }

    @Override
    public SpongeCommandContextBuilder withArgument(String name, ParsedArgument<CommandSource, ?> argument) {
        // Generic wildcards begone!
        return withArgumentInternal(name, argument);
    }

    private <T> SpongeCommandContextBuilder withArgumentInternal(String name, ParsedArgument<CommandSource, T> argument) {
        Parameter.Key<T> objectKey = new SpongeParameterKey<T>(name, TypeToken.of((Class<T>) argument.getResult().getClass()));
        addToArgumentMap(objectKey, argument.getResult());
        super.withArgument(name, argument); // for getArguments and any mods that use this.
        return this;
    }

    @Override
    public SpongeCommandContextBuilder withSource(CommandSource source) {
        // Update the cause to include the command source into the context.
        super.withSource(source);
        return this;
    }

    public SpongeCommandContextBuilder copy() {
        final SpongeCommandContextBuilder copy = new SpongeCommandContextBuilder(this);
        copy.arguments.putAll(this.arguments);
        return copy;
    }

    @Override
    @NonNull
    public Cause getCause() {
        return ((CommandCause) getSource()).getCause();
    }

    @Override
    public boolean hasAny(Parameter.Key<?> key) {
        return this.arguments.containsKey(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getOne(Parameter.Key<T> key) {
        SpongeParameterKey<T> spongeParameterKey = SpongeParameterKey.getSpongeKey(key);
        Collection<?> collection = getFrom(spongeParameterKey);
        if (collection.size() > 1) {
            throw new IllegalArgumentException("More than one entry was found for " + spongeParameterKey.toString());
        }

        return Optional.ofNullable((T) collection.iterator().next());
    }

    @Override
    @NonNull
    @SuppressWarnings("unchecked")
    public <T> T requireOne(Parameter.@NonNull Key<T> key) throws NoSuchElementException, IllegalArgumentException {
        SpongeParameterKey<T> spongeParameterKey = SpongeParameterKey.getSpongeKey(key);
        Collection<?> collection = getFrom(spongeParameterKey);
        if (collection.size() > 1) {
            throw new IllegalArgumentException("More than one entry was found for " + spongeParameterKey.toString());
        } else if (collection.isEmpty()) {
            throw new NoSuchElementException("No entry was found for " + spongeParameterKey.toString());
        }

        return (T) collection.iterator().next();
    }

    @Override
    @NonNull
    @SuppressWarnings("unchecked")
    public <T> Collection<? extends T> getAll(Parameter.@NonNull Key<T> key) {
        return (Collection<? extends T>) getFrom(SpongeParameterKey.getSpongeKey(key));
    }

    private Collection<?> getFrom(SpongeParameterKey<?> key) {
        Collection<?> collection = this.arguments.get(key);
        if (collection == null) {
            return ImmutableSet.of();
        }

        return collection;
    }

    @Override
    public <T> void putEntry(Parameter.@NonNull Key<T> key, @NonNull T object) {
        addToArgumentMap(SpongeParameterKey.getSpongeKey(key), object);
    }

    @Override
    public State getState() {
        return null;
    }

    @Override
    public void setState(@NonNull State state) {

    }

    @Override
    @SuppressWarnings("unchecked")
    public SpongeCommandContext build(@NonNull final String input) {
        final CommandContextBuilder<CommandSource> child = getChild();
        // TODO: this might not be needed for the derived class, come back when mixins are working
        final CommandContextBuilderAccessor<CommandSource> mixinCommandContextBuilder = (CommandContextBuilderAccessor<CommandSource>) this;
        return new SpongeCommandContext(
                getSource(),
                input,
                getArguments(),
                ImmutableMap.copyOf(this.arguments),
                getCommand(),
                getNodes(),
                getRange(),
                child == null ? null : child.build(input),
                mixinCommandContextBuilder.accessor$getModifier(),
                mixinCommandContextBuilder.accessor$isForks());
    }

    @Override
    @NonNull
    public Builder reset() {
        return this;
    }

    @SuppressWarnings("unchecked")
    private <T> void addToArgumentMap(Parameter.Key<T> key, T value) {
        ((List<T>) this.arguments.computeIfAbsent(key, k -> new ArrayList<>())).add(value);
    }

}
