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
package org.spongepowered.common.command.registrar;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import org.spongepowered.api.CatalogKey;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.command.exception.CommandException;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.command.brigadier.context.SpongeCommandContext;
import org.spongepowered.common.command.exception.SpongeCommandSyntaxException;

import java.util.concurrent.CompletableFuture;

/**
 * For use with {@link org.spongepowered.api.command.Command}
 */
public class SpongeRawCommandRegistrar extends SpongeCommandRegistrar<Command> {

    private static final String PARAMETER_NAME = "parameters";
    public static final CatalogKey CATALOG_KEY = CatalogKey.builder().namespace(SpongeImpl.getSpongePlugin()).value("raw").build();
    public static final SpongeRawCommandRegistrar INSTANCE = new SpongeRawCommandRegistrar(CATALOG_KEY);

    private SpongeRawCommandRegistrar(CatalogKey catalogKey) {
        super(catalogKey);
    }

    @Override
    LiteralArgumentBuilder<CommandSource> createNode(final String primaryAlias, final Command command) {
        final Executor executor = new Executor(command);
        return LiteralArgumentBuilder.<CommandSource>literal(primaryAlias)
                .requires(x -> command.canExecute((CommandCause) x))
                .executes(executor)
                .then(
                        RequiredArgumentBuilder
                                .<CommandSource, String>argument(PARAMETER_NAME, new RawString(command))
                                .executes(executor)
                                .build()
                );
    }

    private static class Executor implements com.mojang.brigadier.Command<CommandSource> {

        private final Command command;

        private Executor(Command command) {
            this.command = command;
        }

        @Override
        public int run(final CommandContext<CommandSource> context) throws CommandSyntaxException {
            String argument;
            try {
                argument = context.getArgument(PARAMETER_NAME, String.class);
            } catch (IllegalArgumentException e) {
                // doesn't exist, no input
                argument = "";
            }

            try {
                return command.process((CommandCause) context.getSource(), argument).getResult();
            } catch (CommandException e) {
                throw new SpongeCommandSyntaxException(e, (SpongeCommandContext) context);
            }
        }
    }

    private static class RawString implements ArgumentType<String> {

        private final Command command;

        private RawString(final Command command) {
            this.command = command;
        }

        @Override
        public String parse(final StringReader reader) {
            return reader.getRemaining();
        }

        @Override
        public <S> CompletableFuture<Suggestions> listSuggestions(final CommandContext<S> context, final SuggestionsBuilder builder) {
            final String[] input = context.getInput().split(" ", 2);
            final String arg;
            SuggestionsBuilder offsetBuilder = builder;
            if (input.length == 2) {
                arg = input[1];
                offsetBuilder =  builder.createOffset(context.getInput().lastIndexOf(" "));
            } else {
                arg = "";
            }
            try {
                for (String completion : this.command.getSuggestions(((CommandCause) context.getSource()), arg)) {
                    offsetBuilder.suggest(completion);
                }

                return offsetBuilder.buildFuture();
            } catch (CommandException e) {
                return Suggestions.empty();
            }
        }
    }

}
