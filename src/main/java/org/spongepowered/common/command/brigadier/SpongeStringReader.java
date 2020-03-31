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

import static org.spongepowered.common.util.SpongeCommonTranslationHelper.t;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.command.exception.ArgumentParseException;
import org.spongepowered.api.command.parameter.ArgumentReader;
import org.spongepowered.api.text.Text;
import org.spongepowered.common.command.brigadier.context.SpongeCommandContextBuilder;

// ArgumentReader.Mutable specifies a non null getRead() method, StringReader suggests its
// nullable - but it isn't. So we just need to suppress the warning.
@SuppressWarnings("NullableProblems")
public class SpongeStringReader extends StringReader implements ArgumentReader.Mutable {

    private static final char SYNTAX_QUOTE = '"';

    @Nullable private SpongeCommandContextBuilder commandContextBuilder;

    public SpongeStringReader(String string, @Nullable SpongeCommandContextBuilder commandContextBuilder) {
        super(string);
        this.commandContextBuilder = commandContextBuilder;
    }

    public SpongeStringReader(StringReader other, @Nullable SpongeCommandContextBuilder commandContextBuilder) {
        super(other);
        this.commandContextBuilder = commandContextBuilder;
    }

    // Used because we do a redirect to redirect the creation of a string reader,
    // only to then need to apply a new context (can't get LVT during redirect).
    public void reapplyContextBuilder(SpongeCommandContextBuilder contextBuilder) {
        if (this.commandContextBuilder == null) {
            this.commandContextBuilder = contextBuilder;
        }
    }

    // Sorry Mojang...
    public SpongeCommandContextBuilder getCommandContextBuilder() {
        return this.commandContextBuilder;
    }

    @Override
    public String getInput() {
        return getString();
    }

    @Override
    public char parseChar() {
        return read();
    }

    @Override
    public int parseInt() throws ArgumentParseException {
        try {
            return readInt();
        } catch (CommandSyntaxException e) {
            throw new ArgumentParseException(t("Could not parse an integer"), e, getString(), getCursor());
        }
    }

    @Override
    public double parseDouble() throws ArgumentParseException {
        try {
            return readDouble();
        } catch (CommandSyntaxException e) {
            throw new ArgumentParseException(t("Could not parse a double"), e, getString(), getCursor());
        }
    }

    @Override
    public float parseFloat() throws ArgumentParseException {
        try {
            return readFloat();
        } catch (CommandSyntaxException e) {
            throw new ArgumentParseException(t("Could not parse a float"), e, getString(), getCursor());
        }
    }

    @Override
    public String parseUnquotedString() {
        final int start = getCursor();
        while (canRead() && !Character.isWhitespace(peek())) {
            skip();
        }
        return getString().substring(start, getCursor());
    }

    @Override
    public String parseString() throws ArgumentParseException {
        try {
            if (canRead() && peek() == SYNTAX_QUOTE) {
                return readQuotedString();
            } else {
                return readUnquotedString();
            }
        } catch (CommandSyntaxException e) {
            throw new ArgumentParseException(t("Could not parse string"), e, getString(), getCursor());
        }
    }

    @Override
    public boolean parseBoolean() throws ArgumentParseException {
        try {
            return readBoolean();
        } catch (CommandSyntaxException e) {
            throw new ArgumentParseException(t("Could not parse a boolean"), e, getString(), getCursor());
        }
    }

    @Override
    public SpongeImmutableArgumentReader getImmutable() {
        return new SpongeImmutableArgumentReader(getString(), getCursor());
    }

    @Override
    public void setState(ArgumentReader state) throws IllegalArgumentException {
        if (state.getInput().equals(getString())) {
            setCursor(state.getCursor());
        } else {
            throw new IllegalArgumentException("The provided ArgumentReader does not match this ArgumentReader");
        }
    }

    @Override
    public ArgumentParseException createException(Text errorMessage) {
        return new ArgumentParseException(errorMessage, getInput(), getCursor());
    }

}
