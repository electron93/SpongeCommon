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
package org.spongepowered.common.command.brigadier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.command.CommandSource;
import org.spongepowered.common.accessor.brigadier.CommandDispatcherAccessor;
import org.spongepowered.common.command.brigadier.context.SpongeCommandContextBuilder;
import org.spongepowered.common.command.registrar.BrigadierCommandRegistrar;

// This is a wrapper as we're going to trust the mods are not going to try to hijack this.
// If they did, their commands would not get called.
// This is so we can call super.register()
//
// If mods do the wrong thing, we'll mixin.
public class SpongeCommandDispatcher extends CommandDispatcher<CommandSource> {

    @Override
    public LiteralCommandNode<CommandSource> register(LiteralArgumentBuilder<CommandSource> command) {
        return BrigadierCommandRegistrar.INSTANCE.register(command);
    }

    // Yup. It is what it is.
    public LiteralCommandNode<CommandSource> registerInternal(LiteralArgumentBuilder<CommandSource> command) {
        return super.register(command);
    }

    @Override
    public ParseResults<CommandSource> parse(String command, CommandSource source) {
        SpongeCommandContextBuilder builder = new SpongeCommandContextBuilder(this, source, this.getRoot(), 0);
        SpongeStringReader reader = new SpongeStringReader(command, builder);
        return this.parse(reader);
    }

    @Override
    public ParseResults<CommandSource> parse(StringReader command, CommandSource source) {
        SpongeCommandContextBuilder builder = new SpongeCommandContextBuilder(this, source, this.getRoot(), command.getCursor());
        SpongeStringReader reader = new SpongeStringReader(command, builder);
        return this.parse(reader);
    }

    // This is simply to avoid object creation - a second string reader.
    @Override
    public int execute(String input, CommandSource source) throws CommandSyntaxException {
        return this.execute(parse(input, source));
    }

    @SuppressWarnings("unchecked")
    private ParseResults<CommandSource> parse(final SpongeStringReader reader) {
        return ((CommandDispatcherAccessor<CommandSource>) this).accessor$parseNodes(this.getRoot(), reader, reader.getCommandContextBuilder());
    }

}
