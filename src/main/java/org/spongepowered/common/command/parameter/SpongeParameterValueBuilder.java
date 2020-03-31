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
package org.spongepowered.common.command.parameter;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.command.parameter.managed.ValueCompleter;
import org.spongepowered.api.command.parameter.managed.ValueParser;
import org.spongepowered.api.command.parameter.managed.ValueUsage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class SpongeParameterValueBuilder<T> implements Parameter.Value.Builder<T> {

    private static final ValueCompleter EMPTY_COMPLETER = context -> ImmutableList.of();

    private final TypeToken<T> typeToken;
    private final List<ValueParser<? extends T>> parsers = new ArrayList<>();
    private Parameter.@Nullable Key<T> key;
    @Nullable private ValueCompleter completer;
    @Nullable private ValueUsage usage;
    @Nullable private Predicate<CommandCause> executionRequirements;
    @Nullable private Function<CommandCause, T> defaultValueFunction;
    private boolean consumesAll;
    private boolean isOptional;

    public SpongeParameterValueBuilder(TypeToken<T> token) {
        this.typeToken = token;
    }

    @Override
    public Parameter.Value.Builder<T> setKey(String key) {
        return setKey(new SpongeParameterKey<T>(key, this.typeToken));
    }

    @Override public Parameter.Value.Builder<T> setKey(Parameter.Key<T> key) {
        Objects.requireNonNull(key, "The key cannot be null");
        this.key = key;
        return this;
    }

    @Override
    public Parameter.Value.Builder<T> parser(ValueParser<? extends T> parser) {
        this.parsers.add(Objects.requireNonNull(parser, "The ValueParser may not be null"));
        return this;
    }

    @Override
    public Parameter.Value.Builder<T> setSuggestions(@Nullable ValueCompleter completer) {
        this.completer = completer;
        return this;
    }

    @Override
    public Parameter.Value.Builder<T> setUsage(@Nullable ValueUsage usage) {
        this.usage = usage;
        return this;
    }

    @Override
    public Parameter.Value.Builder<T> setRequiredPermission(@Nullable String permission) {
        if (permission == null) {
            return setUsage(null);
        } else {
            return setRequirements(commandCause -> commandCause.getSubject().hasPermission(permission));
        }
    }

    @Override public Parameter.Value.Builder<T> setRequirements(@Nullable Predicate<CommandCause> executionRequirements) {
        this.executionRequirements = executionRequirements;
        return this;
    }

    @Override
    public Parameter.Value.Builder<T> consumeAllRemaining() {
        this.consumesAll = true;
        return this;
    }

    @Override
    public Parameter.Value.Builder<T> optional() {
        this.isOptional = true;
        return this;
    }

    @Override
    public Parameter.Value.Builder<T> orDefault(Supplier<T> defaultValueSupplier) {
        return orDefault((cause) -> defaultValueSupplier.get());
    }

    @Override
    public Parameter.Value.Builder<T> orDefault(@Nullable Function<CommandCause, T> defaultValueFunction) {
        this.defaultValueFunction = defaultValueFunction;
        return this;
    }

    @Override
    public SpongeParameterValue<T> build() throws IllegalStateException {
        Preconditions.checkState(this.key != null, "The command key may not be null");
        Preconditions.checkState(!this.parsers.isEmpty(), "There must be parsers");
        ImmutableList.Builder<ValueParser<? extends T>> parsersBuilder = ImmutableList.builder();
        parsersBuilder.addAll(this.parsers);
        if (this.defaultValueFunction != null) {
            parsersBuilder.add((key, reader, context) -> Optional.of(this.defaultValueFunction.apply(context)));
        }

        ValueCompleter completer;
        if (this.completer != null) {
            completer = this.completer;
        } else {
            ImmutableList.Builder<ValueCompleter> completersBuilder = ImmutableList.builder();
            for (ValueParser<? extends T> parser : this.parsers) {
                if (parser instanceof ValueCompleter) {
                    completersBuilder.add((ValueCompleter) parser);
                }
            }

            final ImmutableList<ValueCompleter> completers = completersBuilder.build();
            if (completers.isEmpty()) {
                completer = EMPTY_COMPLETER;
            } else {
                completer = (context) -> {
                    ImmutableList.Builder<String> builder = ImmutableList.builder();
                    for (ValueCompleter valueCompleter : completers) {
                        builder.addAll(valueCompleter.complete(context));
                    }

                    return builder.build();
                };
            }
        }

        return new SpongeParameterValue<>(
                parsersBuilder.build(),
                completer,
                this.usage,
                this.executionRequirements == null ? commandCause -> true : this.executionRequirements,
                this.key,
                this.isOptional,
                this.consumesAll
        );
    }

    @Override
    public Parameter.Value.Builder<T> reset() {
        this.key = null;
        this.parsers.clear();
        this.completer = null;
        this.usage = null;
        this.defaultValueFunction = null;
        this.isOptional = false;
        this.consumesAll = false;
        return this;
    }

}
