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
package org.spongepowered.common.command.brigadier.argument;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.command.exception.ArgumentParseException;
import org.spongepowered.api.command.parameter.ArgumentReader;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.command.parameter.managed.ValueCompleter;
import org.spongepowered.api.command.parameter.managed.ValueParser;
import org.spongepowered.api.text.Text;
import org.spongepowered.common.command.brigadier.SpongeStringReader;
import org.spongepowered.common.command.brigadier.context.SpongeCommandContext;
import org.spongepowered.common.command.brigadier.context.SpongeCommandContextBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SpongeParameterArgumentType<T> implements ArgumentType<T>, SuggestionProvider<CommandSource> {

    private static final BiMap<ValueCompleter, String> VALUE_COMPLETER_ID_MAP = HashBiMap.create();

    private static final Pattern INTEGER_PATTERN = Pattern.compile("\\d+");

    private final Parameter.Key<T> key;
    private final Collection<ValueParser<? extends T>> parsers;
    private final ValueCompleter completer;

    public SpongeParameterArgumentType(Parameter.Key<T> key, Collection<ValueParser<? extends T>> parsers, ValueCompleter completer) {
        this.key = key;
        this.parsers = parsers;
        this.completer = completer;
    }

    @Override
    @Nullable
    public T parse(StringReader reader) throws CommandSyntaxException {
        // So, we hid the context in our string reader...
        Optional<? extends T> value;
        SpongeStringReader stringReader = ((SpongeStringReader) reader);
        SpongeCommandContextBuilder builder = stringReader.getCommandContextBuilder();
        List<ArgumentParseException> exceptions = null;
        ArgumentReader.Immutable state = stringReader.getImmutable();
        SpongeCommandContextBuilder.State contextState = builder.getState();
        for (ValueParser<? extends T> parser : this.parsers) {
            try {
                value = parser.getValue(this.key, (ArgumentReader.Mutable) stringReader, builder);
                if (value.isPresent()) {
                    return value.get();
                }
            } catch (ArgumentParseException e) {
                if (exceptions == null) {
                    exceptions = new ArrayList<>();
                }
                exceptions.add(e);
            }

            // reset the state as it did not go through.
            stringReader.setState(state);
            builder.setState(contextState);
        }

        if (exceptions != null) {
            // TODO: Check this works with Text objects.
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherParseException()
                    .createWithContext(reader,
                            Text.joinWith(Text.newLine(), exceptions.stream().map(ArgumentParseException::getSuperText).collect(Collectors.toList())));
        }

        // TODO: Check this - don't want Brig to blow up. If that happens, mandate everything returns an object.
        return null;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        for (String s : this.completer.complete((SpongeCommandContext) context)) {
            if (INTEGER_PATTERN.matcher(s).matches()) {
                try {
                    builder.suggest(Integer.parseInt(s));
                } catch (NumberFormatException ex) {
                    builder.suggest(s);
                }
            } else {
                builder.suggest(s);
            }
        }
        return builder.buildFuture();
    }

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSource> context, SuggestionsBuilder builder) {
        return listSuggestions(context, builder);
    }
}
