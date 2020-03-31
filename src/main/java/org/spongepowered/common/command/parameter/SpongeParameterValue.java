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

import static org.spongepowered.common.util.SpongeCommonTranslationHelper.t;

import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.command.exception.ArgumentParseException;
import org.spongepowered.api.command.parameter.ArgumentReader;
import org.spongepowered.api.command.parameter.CommandContext;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.api.command.parameter.managed.ValueCompleter;
import org.spongepowered.api.command.parameter.managed.ValueParser;
import org.spongepowered.api.command.parameter.managed.ValueUsage;
import org.spongepowered.api.text.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SpongeParameterValue<T> implements Parameter.Value<T> {

    private final static Text NEW_LINE = Text.of("\n");
    private final static Text GENERIC_EXCEPTION_ERROR = t("Could not parse element");

    private final ImmutableList<ValueParser<? extends T>> parsers;
    private final ValueCompleter completer;
    private final Predicate<CommandCause> requirement;
    @Nullable private final ValueUsage usage;
    private final Key<T> key;
    private final boolean isOptional;
    private final boolean consumeAll;

    public SpongeParameterValue(
            ImmutableList<ValueParser<? extends T>> parsers,
            ValueCompleter completer,
            @Nullable ValueUsage usage,
            Predicate<CommandCause> requirement,
            Key<T> key,
            boolean isOptional,
            boolean consumeAll) {
        this.parsers = parsers;
        this.completer = completer;
        this.requirement = requirement;
        this.usage = usage;
        this.key = key;
        this.isOptional = isOptional;
        this.consumeAll = consumeAll;
    }

    @Override
    public void parse(ArgumentReader.Mutable args, CommandContext.Builder context) throws ArgumentParseException {
        ArgumentReader.Immutable readerState = args.getImmutable();
        CommandContext.Builder.State contextState = context.getState();

        try {
            do {
                args.skipWhitespace();
                parseInternal(args, context);
            } while (this.consumeAll && args.canRead()); // executes more than once for "consumeAll"
        } catch (ArgumentParseException apex) {
            args.setState(readerState);
            context.setState(contextState);
            if (!this.isOptional) {
                return; // Optional
            }

            throw apex;
        }

    }

    private void parseInternal(
            ArgumentReader.Mutable args,
            CommandContext.Builder context) throws ArgumentParseException {

        List<ArgumentParseException> currentExceptions = null;
        ArgumentReader.Immutable state = args.getImmutable();
        CommandContext.Builder.State contextState = context.getState();
        for (ValueParser<? extends T> parser : this.parsers) {
            try {
                parser.getValue(this.key, args, context).ifPresent(t -> context.putEntry(this.key, t));
                return; // something parsed, so we exit.
            } catch (ArgumentParseException ex) {
                if (currentExceptions == null) {
                    currentExceptions = new ArrayList<>();
                }

                currentExceptions.add(ex);
                args.setState(state);
                context.setState(contextState);
            }
        }

        // If we get this far, we failed to parse, return the exceptions
        if (currentExceptions == null) {
            throw new ArgumentParseException(GENERIC_EXCEPTION_ERROR, args.getInput(), args.getCursor());
        } else if (currentExceptions.size() == 1) {
            throw currentExceptions.get(0);
        } else {
            List<Text> errors = currentExceptions.stream().map(ArgumentParseException::getSuperText).collect(Collectors.toList());
            throw new ArgumentParseException(Text.joinWith(NEW_LINE, errors), args.getInput(), args.getCursor());
        }

    }

    @Override
    public Key<T> getKey() {
        return this.key;
    }

    @Override
    public List<String> complete(ArgumentReader.Immutable reader, CommandContext context) throws ArgumentParseException {
        return this.completer.complete(context);
    }

    @Override
    public Text getUsage(CommandCause cause) {
        if (this.usage != null) {
            return this.usage.getUsage(cause, Text.of(this.key.key()));
        }

        Text usage = Text.of(this.key.key());
        if (this.isOptional) {
            return Text.of("[", usage, "]");
        }

        return usage;
    }

    @Override
    public Collection<ValueParser<? extends T>> getParsers() {
        return this.parsers;
    }

    @Override
    public ValueCompleter getCompleter() {
        return this.completer;
    }

    @Override
    public Predicate<CommandCause> getRequirement() {
        return this.requirement;
    }

    @Override
    public boolean isTerminal() {
        return this.isOptional;
    }

}
