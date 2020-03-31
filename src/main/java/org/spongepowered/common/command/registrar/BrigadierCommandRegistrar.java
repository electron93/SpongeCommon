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

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.command.CommandSource;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.api.CatalogKey;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.exception.CommandException;
import org.spongepowered.api.command.manager.CommandMapping;
import org.spongepowered.api.command.registrar.CommandRegistrar;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.Tuple;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.bridge.brigadier.builder.ArgumentBuilderBridge;
import org.spongepowered.common.bridge.brigadier.tree.RootCommandNodeBridge;
import org.spongepowered.common.command.brigadier.SpongeCommandDispatcher;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * This is used for vanilla commands and mods, but can also be used by
 * frameworks that simply output Brig nodes (or just use Brig on its own!)
 *
 * <p>Such command registrations should simply use the
 * {@link #register(PluginContainer, LiteralArgumentBuilder, String...)}
 * method.</p>
 */
public class BrigadierCommandRegistrar implements CommandRegistrar {

    public static final BrigadierCommandRegistrar INSTANCE = new BrigadierCommandRegistrar();
    public static final CatalogKey CATALOG_KEY = CatalogKey.builder().namespace(SpongeImpl.getMinecraftPlugin()).value("brigadier").build();

    private BrigadierCommandRegistrar() {}

    // For mods and others that use this. We get the plugin container from the CauseStack
    // TODO: Make sure this is valid. For Forge, I suspect we'll have done this in a context of some sort.
    public LiteralCommandNode<CommandSource> register(final LiteralArgumentBuilder<CommandSource> command) {
        // Get the plugin container
        PluginContainer container = Sponge.getCauseStackManager().getCurrentCause().first(PluginContainer.class)
                .orElseThrow(() -> new IllegalStateException("Cannot register command without knowing its origin."));

        return registerInternal(this, container, command, new String[0], true).getSecond();
    }

    /**
     * Entry point for brigadier based commands that are Sponge aware, such that
     * they will not have any other permission checks imposed upon them.
     *
     * @param container The {@link PluginContainer} of the registering plugin
     * @param command The {@link LiteralArgumentBuilder} to register
     * @param secondaryAliases Any aliases should be registered (they will be registered as a redirection)
     * @return The built {@link LiteralCommandNode}.
     */
    public Tuple<CommandMapping, LiteralCommandNode<CommandSource>> register(
            final PluginContainer container,
            final LiteralArgumentBuilder<CommandSource> command,
            final String... secondaryAliases) {
        return registerInternal(this, container, command, secondaryAliases,false);
    }

    Tuple<CommandMapping, LiteralCommandNode<CommandSource>> registerInternal(
            final CommandRegistrar registrar,
            final PluginContainer container,
            final String[] secondaryAliases,
            final LiteralArgumentBuilder<CommandSource> command) {
        return registerInternal(registrar, container, command, secondaryAliases, false);
    }

    @SuppressWarnings("unchecked")
    private Tuple<CommandMapping, LiteralCommandNode<CommandSource>> registerInternal(
            final CommandRegistrar registrar,
            final PluginContainer container,
            final LiteralArgumentBuilder<CommandSource> command,
            final String[] secondaryAliases,
            boolean updateRequirement) {

        // Get the builder and the first literal.
        String requestedAlias = command.getLiteral();

        // This will throw an error if there is an issue.
        CommandMapping mapping = SpongeImpl.getCommandManager().registerAlias(
                        registrar,
                        container,
                        command,
                        secondaryAliases
                );

        final LiteralArgumentBuilder<CommandSource> literalToRegister;
        if (mapping.getPrimaryAlias().equals(requestedAlias)) {
            literalToRegister = command;
        } else {
            // We need to alter the primary alias.
            literalToRegister = LiteralArgumentBuilder.<CommandSource>literal(mapping.getPrimaryAlias());
            ((ArgumentBuilderBridge<CommandSource, LiteralArgumentBuilder<CommandSource>>) literalToRegister).bridge$cloneFrom(command);
        }

        // Let the registration happen.
        if (updateRequirement) {
            // If the requirement should be updated, register with the permission <modid>.command.<permission>
            final String permission = String.format("%s.command.%s", container.getId(), requestedAlias.toLowerCase());
            literalToRegister.requires(command.getRequirement().and(commandSource -> ((CommandCause) commandSource).getSubject().hasPermission(permission)));
        }

        LiteralCommandNode<CommandSource> builtNode =
                ((SpongeCommandDispatcher) SpongeImpl.getServer().getCommandManager().getDispatcher()).registerInternal(literalToRegister);

        // Redirect aliases
        for (String alias : mapping.getAllAliases()) {
            if (!alias.equals(literalToRegister.getLiteral())) {
                SpongeImpl.getServer().getCommandManager().getDispatcher()
                        .register(LiteralArgumentBuilder.<CommandSource>literal(alias).requires(builtNode.getRequirement()).redirect(builtNode));
            }
        }

        return Tuple.of(mapping, builtNode);
    }

    @Override
    @NonNull
    public CommandResult process(@NonNull final CommandCause cause, @NonNull final String command, @NonNull final String arguments) throws CommandException {
        try {
            int result = SpongeImpl.getServer().getCommandManager().getDispatcher().execute(command + " " + arguments, (CommandSource) cause);
            return CommandResult.builder().setResult(result).build();
        } catch (CommandSyntaxException e) {
            throw new CommandException(Text.of(e.getMessage()), e);
        }
    }

    @Override
    @NonNull
    public List<String> suggestions(@NonNull final CommandCause cause, @NonNull final String command, @NonNull final String arguments) {
        CommandDispatcher<CommandSource> dispatcher = SpongeImpl.getServer().getCommandManager().getDispatcher();
        CompletableFuture<Suggestions> suggestionsCompletableFuture =
                dispatcher.getCompletionSuggestions(dispatcher.parse(command + " " + arguments, (CommandSource) cause));
        // TODO: Fix so that we keep suggestions in the Mojang format?
        return suggestionsCompletableFuture.join().getList().stream().map(Suggestion::getText).collect(Collectors.toList());
    }

    @Override
    @NonNull
    public Optional<Text> help(@NonNull final CommandCause cause, @NonNull final String command) {
        CommandDispatcher<CommandSource> dispatcher = SpongeImpl.getServer().getCommandManager().getDispatcher();
        CommandNode<CommandSource> node = dispatcher.findNode(Collections.singletonList(command));
        if (node != null) {
            return Optional.of(Text.of(dispatcher.getSmartUsage(node, (CommandSource) cause)));
        }

        return Optional.empty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void unregister(@NonNull final CommandMapping mapping) {
        if (!Sponge.getCommandManager().isRegistered(mapping)) {
            ((RootCommandNodeBridge<CommandSource>) getDispatcher().getRoot())
                    .bridge$removeNode(getDispatcher().getRoot().getChild(mapping.getPrimaryAlias()));
        }
    }

    @Override
    @NonNull
    public CatalogKey getKey() {
        return CATALOG_KEY;
    }

    private CommandDispatcher<CommandSource> getDispatcher() {
        return SpongeImpl.getServer().getCommandManager().getDispatcher();
    }

}
