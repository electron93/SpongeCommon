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

import com.mojang.brigadier.ImmutableStringReader;
import com.mojang.brigadier.StringReader;
import org.spongepowered.api.command.exception.ArgumentParseException;
import org.spongepowered.api.command.parameter.ArgumentReader;
import org.spongepowered.api.text.Text;

public class SpongeImmutableArgumentReader implements ArgumentReader.Immutable, ImmutableStringReader {

    private final String input;
    private final int cursor;
    private final int length;
    private final int remaining;

    SpongeImmutableArgumentReader(String input, int cursor) {
        this.input = input;
        this.cursor = cursor;
        this.length = this.input.length();
        this.remaining = this.length - cursor;
    }

    @Override
    public String getString() {
        return this.input;
    }

    @Override
    public String getInput() {
        return this.input;
    }

    @Override
    public int getRemainingLength() {
        return this.remaining;
    }

    @Override
    public int getTotalLength() {
        return this.length;
    }

    @Override
    public int getCursor() {
        return this.cursor;
    }

    @Override
    public String getRead() {
        return this.input.substring(0, this.cursor);
    }

    @Override
    public String getRemaining() {
        return this.input.substring(this.cursor);
    }

    @Override
    public boolean canRead(int length) {
        return this.cursor + length <= this.length;
    }

    @Override
    public boolean canRead() {
        return canRead(1);
    }

    @Override
    public ArgumentParseException createException(Text errorMessage) {
        return new ArgumentParseException(errorMessage, getInput(), getCursor());
    }

    @Override
    public char peek() {
        return peek(0);
    }

    @Override
    public char peek(int offset) {
        return this.input.charAt(this.cursor + offset);
    }

    @Override
    public ArgumentReader.Mutable getMutable() {
        StringReader reader = new StringReader(this.input);
        reader.setCursor(this.cursor);
        return (ArgumentReader.Mutable) reader;
    }
}
