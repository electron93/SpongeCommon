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

import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.network.PacketBuffer;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.command.registrar.tree.ClientCompletionKey;
import org.spongepowered.api.command.registrar.tree.CommandTreeBuilder;

public class EntityCommandTreeBuilder
        extends AmountCommandTreeBuilder<CommandTreeBuilder.EntitySelection> implements CommandTreeBuilder.EntitySelection {

    private static final EntityArgument.Serializer ENTITY_ARGUMENT_SERIALIZER = new EntityArgument.Serializer();
    private static final String TYPE_KEY = "type";

    private static final String TYPE_ENTITIES = "entities";
    private static final String TYPE_PLAYER_ONLY = "players";

    public EntityCommandTreeBuilder(@Nullable ClientCompletionKey<EntitySelection> parameterType) {
        super(parameterType);
        this.addProperty(TYPE_KEY, TYPE_ENTITIES);
    }

    @Override
    public EntitySelection playersOnly() {
        return this.addProperty(TYPE_KEY, TYPE_PLAYER_ONLY);
    }

    public boolean isPlayersOnly() {
        return TYPE_PLAYER_ONLY.equals(this.getProperty(TYPE_KEY));
    }

    @Override
    public void applyProperties(PacketBuffer packetBuffer) {
        EntityArgument argument;
        if (isPlayersOnly()) {
            argument = isSingleTarget() ? EntityArgument.player() : EntityArgument.players();
        } else {
            argument = isSingleTarget() ? EntityArgument.entity() : EntityArgument.entities();
        }

        ENTITY_ARGUMENT_SERIALIZER.write(argument, packetBuffer);
    }

}
