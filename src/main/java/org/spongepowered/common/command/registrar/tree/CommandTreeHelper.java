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

import com.google.common.base.Preconditions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import org.spongepowered.api.CatalogKey;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.registrar.tree.ClientCompletionKey;
import org.spongepowered.api.command.registrar.tree.CommandTreeBuilder;
import org.spongepowered.common.util.Constants;

import java.util.Collection;

public class CommandTreeHelper {

    public static void fromJson(CommandTreeBuilder<?> rootCommandTreeBuilder, JsonObject json) {
        // This must be a root node, if not, bail.
        Preconditions.checkArgument(Constants.Command.ROOT.equals(json.get(Constants.Command.TYPE).getAsString()),
                "Root object is not a root argument type!");
        parseChildren(rootCommandTreeBuilder, json);
    }

    private static void parseChildren(CommandTreeBuilder<?> treeBuilder, JsonObject json) {
        JsonObject childrenObject = json.getAsJsonObject(Constants.Command.CHILDREN);
        if (childrenObject != null) {
            childrenObject.entrySet().forEach(entry -> {
                JsonObject child = entry.getValue().getAsJsonObject();
                if (child.get(Constants.Command.TYPE).getAsString().equals(Constants.Command.LITERAL)) {
                    treeBuilder.child(entry.getKey(), x -> parseCommon(x, child));
                } else {
                    CatalogKey parser = CatalogKey.resolve(child.get(Constants.Command.PARSER).getAsString());
                    ClientCompletionKey<?> key = Sponge.getRegistry().getCatalogRegistry()
                            .get(ClientCompletionKey.class, parser)
                            .orElseThrow(() -> new IllegalStateException("The argument key " + parser.getFormatted() + " could not be found."));
                    treeBuilder.child(entry.getKey(), key, x -> parseArgument(x, child));
                }
            });
        }
    }

    private static void parseArgument(CommandTreeBuilder<?> commandTreeBuilder, JsonObject child) {
        // properties
        JsonElement element = child.get(Constants.Command.PROPERTIES);
        if (element != null) {
            JsonObject object = element.getAsJsonObject();
            object.entrySet().forEach(entry -> {
                JsonElement val = entry.getValue();
                if (!val.isJsonNull() && val.isJsonPrimitive()) {
                    JsonPrimitive primitive = val.getAsJsonPrimitive();
                    if (primitive.isBoolean()) {
                        commandTreeBuilder.property(entry.getKey(), primitive.getAsBoolean());
                    } else if (primitive.isNumber()) {
                        if (primitive.getAsString().contains(".")) {
                            commandTreeBuilder.property(entry.getKey(), primitive.getAsDouble());
                        } else {
                            commandTreeBuilder.property(entry.getKey(), primitive.getAsLong());
                        }
                    } else {
                        commandTreeBuilder.property(entry.getKey(), primitive.getAsString());
                    }
                }
            });
        }
        parseCommon(commandTreeBuilder, child);
    }

    private static void parseCommon(CommandTreeBuilder<?> commandTreeBuilder, JsonObject child) {
        // if "executable" exists, it will be executable;
        if (child.get(Constants.Command.EXECUTABLE) != null) {
            commandTreeBuilder.executable();
        }

        JsonElement element = child.get(Constants.Command.REDIRECT);
        if (element != null) {
            element.getAsJsonArray().forEach(x -> commandTreeBuilder.redirect(x.getAsString()));
        }

        parseChildren(commandTreeBuilder, child);
    }

}
