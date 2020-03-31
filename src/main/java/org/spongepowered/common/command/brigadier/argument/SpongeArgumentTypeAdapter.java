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

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import org.spongepowered.api.CatalogKey;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.command.exception.ArgumentParseException;
import org.spongepowered.api.command.parameter.ArgumentReader;
import org.spongepowered.api.command.parameter.CommandContext;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.command.parameter.managed.ValueParameter;
import org.spongepowered.api.command.parameter.managed.standard.CatalogedValueParameter;
import org.spongepowered.api.text.Text;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Adapts underlying {@link ArgumentType}s as {@link ValueParameter}s
 *
 * <p>For use with standard {@link ArgumentType}s</p>
 *
 * @param <T> The type of parameter
 */
public class SpongeArgumentTypeAdapter<T> implements ArgumentType<T>, CatalogedValueParameter<T> {

    private final CatalogKey key;
    private final ArgumentType<T> type;

    public SpongeArgumentTypeAdapter(CatalogKey key, ArgumentType<T> type) {
        this.key = key;
        this.type = type;
    }

    public ArgumentType<T> getUnderlyingType() {
        return this.type;
    }

    @Override
    public T parse(final StringReader reader) throws CommandSyntaxException {
        return this.type.parse(reader);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(final com.mojang.brigadier.context.CommandContext<S> context,
            SuggestionsBuilder builder) {
        return this.type.listSuggestions(context, builder);
    }

    @Override
    public Collection<String> getExamples() {
        return this.type.getExamples();
    }

    @Override
    public Text getUsage(final CommandCause cause, final Text key) {
        return key;
    }

    @Override
    public List<String> complete(final CommandContext context) {
        CompletableFuture<Suggestions> c =
                this.listSuggestions((com.mojang.brigadier.context.CommandContext) context, new SuggestionsBuilder("", 0));
        try {
            return c.get().getList().stream().map(Suggestion::getText).collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            return ImmutableList.of();
        }
    }

    @Override
    public Optional<? extends T> getValue(final Parameter.Key<? super T> parameterKey, final ArgumentReader.Mutable reader, final CommandContext.Builder context)
            throws ArgumentParseException {
        try {
            return Optional.of(parse((StringReader) reader));
        } catch (CommandSyntaxException e) {
            throw new ArgumentParseException(Text.of(e.getMessage()), e, e.getInput(), e.getCursor());
        }
    }

    @Override
    public CatalogKey getKey() {
        return this.key;
    }
}
