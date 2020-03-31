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
package org.spongepowered.common.command.registrar.tree;

import net.minecraft.network.PacketBuffer;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.command.registrar.tree.ClientCompletionKey;
import org.spongepowered.api.command.registrar.tree.CommandTreeBuilder;

public class AmountCommandTreeBuilder<T extends CommandTreeBuilder<T>>
        extends ArgumentCommandTreeBuilder<T> implements CommandTreeBuilder.AmountBase<T> {

    private static final String AMOUNT_KEY = "amount";

    private static final String AMOUNT_SINGLE = "single";
    private static final String AMOUNT_MULTIPLE = "multiple";

    public AmountCommandTreeBuilder(ClientCompletionKey<T> parameterType) {
        super(parameterType);
        this.addProperty(AMOUNT_KEY, AMOUNT_MULTIPLE);
    }

    @Override
    public T single() {
        return this.addProperty(AMOUNT_KEY, AMOUNT_SINGLE);
    }

    public boolean isSingleTarget() {
        return AMOUNT_SINGLE.equals(this.getProperty(AMOUNT_KEY));
    }

    @Override
    public void applyProperties(PacketBuffer packetBuffer) {
        // If allowing multiple, it's 1, else 0
        if (isSingleTarget()) {
            packetBuffer.writeByte(0);
        } else {
            packetBuffer.writeByte(1);
        }
    }
}
