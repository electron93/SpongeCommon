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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.command.CommandSource;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.command.registrar.tree.ClientCompletionKey;
import org.spongepowered.api.command.registrar.tree.CommandTreeBuilder;
import org.spongepowered.common.util.Constants;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class AbstractCommandTreeBuilder<T extends CommandTreeBuilder<T>, O extends CommandNode<CommandSource>>
        implements CommandTreeBuilder<T> {

    @Nullable private Map<String, Object> properties = null;
    @Nullable private String redirect = null;
    @Nullable private Map<String, AbstractCommandTreeBuilder<?, ?>> children = null;
    private boolean executable = false;
    private boolean customSuggestions = false;

    public ImmutableMap<String, AbstractCommandTreeBuilder<?, ?>> getChildren() {
        if (this.children == null) {
            return ImmutableMap.of();
        }
        return ImmutableMap.copyOf(this.children);
    }

    final void addChildrenInternal(Map<String, AbstractCommandTreeBuilder<?, ?>> children) {
        if (this.children == null) {
            this.children = new HashMap<>();
        }

        this.children.putAll(children);
    }

    @Override
    public T child(String key, Consumer<Basic> childNode) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(childNode);

        return childInternal(key, LiteralCommandTreeBuilder::new, childNode);
    }

    @Override
    public <S extends CommandTreeBuilder<S>> T child(String key, ClientCompletionKey<S> completionKey, Consumer<S> childNode) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(completionKey);
        Objects.requireNonNull(childNode);
        return childInternal(key, completionKey::createCommandTreeBuilder, childNode);
    }

    private <S extends CommandTreeBuilder<S>> T childInternal(
            String key,
            Supplier<S> builderSupplier,
            Consumer<S> childNode) {
        Preconditions.checkState(this.redirect == null, "There must be no redirect if using children nodes");
        checkKey(key);
        if (this.children == null) {
            this.children = new HashMap<>();
        }

        S childTreeBuilder = builderSupplier.get();
        childNode.accept(childTreeBuilder);
        this.children.put(key.toLowerCase(), (AbstractCommandTreeBuilder<?, ?>) childTreeBuilder);
        return getThis();
    }

    @Override
    public T redirect(String to) {
        Preconditions.checkNotNull(to);
        Preconditions.checkState(this.children == null, "There must be no children if using a redirect");
        this.redirect = to.toLowerCase();
        return getThis();
    }

    @Override
    public T executable() {
        this.executable = true;
        return getThis();
    }

    @Override
    public T customSuggestions() {
        this.customSuggestions = true;
        return getThis();
    }

    @Override
    public T property(String key, String value) {
        return addProperty(key, value);
    }

    @Override
    public T property(String key, long value) {
        return addProperty(key, value);
    }

    @Override
    public T property(String key, double value) {
        return addProperty(key, value);
    }

    @Override
    public T property(String key, boolean value) {
        return addProperty(key, value);
    }

    @SuppressWarnings("unchecked")
    protected T getThis() {
        return (T) this;
    }

    public JsonObject toJson(JsonObject object, boolean withChildren) {
        setType(object);
        if (this.executable) {
            object.addProperty("executable", true);
        }

        if (withChildren && this.children != null) {
            // create children
            JsonObject childrenObject = new JsonObject();
            for (Map.Entry<String, AbstractCommandTreeBuilder<?, ?>> element : this.children.entrySet()) {
                childrenObject.add(element.getKey(), element.getValue().toJson(new JsonObject(), true));
            }
            object.add("children", childrenObject);
        }

        if (this.redirect != null) {
            JsonArray redirectObject = new JsonArray();
            redirectObject.add(this.redirect);
        }

        if (this.properties != null) {
            JsonObject propertiesObject = null;
            for (Map.Entry<String, Object> property : this.properties.entrySet()) {
                if (property.getValue() != null) {
                    if (propertiesObject == null) {
                        propertiesObject = new JsonObject();
                    }

                    if (property.getValue() instanceof Number) {
                        propertiesObject.addProperty(property.getKey(), (Number) property.getValue());
                    } else if (property.getValue() instanceof Boolean) {
                        propertiesObject.addProperty(property.getKey(), (boolean) property.getValue());
                    } else {
                        propertiesObject.addProperty(property.getKey(), String.valueOf(property.getValue()));
                    }
                }
            }

            if (propertiesObject != null) {
                object.add("properties", propertiesObject);
            }
        }

        return object;
    }


    abstract void setType(JsonObject object);

    private void checkKey(String key) {
        if (this.children != null && this.children.containsKey(key.toLowerCase())) {
            throw new IllegalArgumentException("Key " + key + " is already set.");
        }
    }

    protected T addProperty(String key, Object value) {
        Objects.requireNonNull(key, "Property key must not be null");
        Objects.requireNonNull(value, "Property value must not be null");
        if (this.properties == null) {
            this.properties = new HashMap<>();
        }

        this.properties.put(key, value);
        return getThis();
    }

    protected T removeProperty(String key) {
        if (this.properties != null) {
            this.properties.remove(key.toLowerCase());
        }

        return getThis();
    }

    protected boolean hasProperty(String property) {
        return this.properties != null && this.properties.containsKey(property);
    }

    public boolean isCustomSuggestions() {
        return this.customSuggestions;
    }

    protected abstract byte getNodeMask();

    public byte getFlags() {
        byte flags = getNodeMask();
        if (this.executable) {
            flags |= Constants.Command.EXECUTABLE_BIT;
        }
        return flags;
    }

    @Nullable
    protected Object getProperty(String key) {
        if (this.properties == null) {
            return null;
        }

        return this.properties.get(key);
    }

    protected abstract O createArgumentTree(String nodeKey, Command<CommandSource> command);

    protected final void addChildNodesToTree(ArgumentBuilder<CommandSource, ?> builder, Command<CommandSource> command) {
        this.getChildren().forEach((key, value) -> builder.then(value.createArgumentTree(key, command)));
    }

}
