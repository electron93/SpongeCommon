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
package org.spongepowered.common.command.manager;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.inject.Singleton;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.command.CommandSource;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.exception.CommandException;
import org.spongepowered.api.command.manager.CommandFailedRegistrationException;
import org.spongepowered.api.command.manager.CommandManager;
import org.spongepowered.api.command.manager.CommandMapping;
import org.spongepowered.api.command.registrar.CommandRegistrar;
import org.spongepowered.api.command.registrar.tree.CommandTreeBuilder;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.api.event.cause.EventContextKeys;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.service.permission.Subject;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.channel.MessageChannel;
import org.spongepowered.api.text.channel.MessageReceiver;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.command.brigadier.SpongeCommandDispatcher;
import org.spongepowered.common.command.registrar.SpongeManagedCommandRegistrar;
import org.spongepowered.common.command.registrar.SpongeRawCommandRegistrar;
import org.spongepowered.common.command.registrar.tree.RootCommandTreeBuilder;
import org.spongepowered.common.text.SpongeTexts;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Singleton
public class SpongeCommandManager implements CommandManager {

    private final Map<String, SpongeCommandMapping> commandMappings = new HashMap<>();
    private final Multimap<SpongeCommandMapping, String> inverseCommandMappings = HashMultimap.create();
    private final Multimap<PluginContainer, SpongeCommandMapping> pluginToCommandMap = HashMultimap.create();

    @NonNull
    public CommandMapping registerAlias(
            @NonNull final CommandRegistrar registrar,
            @NonNull final PluginContainer container,
            @NonNull final LiteralArgumentBuilder<CommandSource> rootArgument,
            @NonNull final String @NonNull... secondaryAliases)
            throws CommandFailedRegistrationException {
        final String requestedPrimaryAlias = rootArgument.getLiteral();

        // Get the mapping, if any.
        return this.registerAliasInternal(
                registrar,
                container,
                requestedPrimaryAlias,
                secondaryAliases
        );
    }

    @Override
    @NonNull
    @SuppressWarnings("unchecked")
    public CommandMapping registerAlias(
            @NonNull final CommandRegistrar registrar,
            @NonNull final PluginContainer container,
            final CommandTreeBuilder.@NonNull Basic parameterTree,
            @NonNull final Predicate<CommandCause> requirement,
            @NonNull final String primaryAlias,
            @NonNull final String @NonNull ... secondaryAliases)
            throws CommandFailedRegistrationException {
        final CommandMapping mapping = registerAliasInternal(registrar, container, primaryAlias, secondaryAliases);

        // In general, this won't be executed as we will intercept it before this point. However,
        // this is as a just in case - a mod redirect or something.
        com.mojang.brigadier.Command<CommandSource> command = context -> {
            org.spongepowered.api.command.parameter.CommandContext spongeContext =
                    (org.spongepowered.api.command.parameter.CommandContext) context;
            String[] command1 = context.getInput().split(" ", 2);
            try {
                return registrar.process(spongeContext, command1[0], command1.length == 2 ? command1[1] : "").getResult();
            } catch (CommandException e) {
                throw new SimpleCommandExceptionType(SpongeTexts.toComponent(e.getText())).create();
            }
        };

        Collection<CommandNode<CommandSource>> commandSourceRootCommandNode = ((RootCommandTreeBuilder) parameterTree)
                .createArgumentTree(command);

        // From the primary alias...
        LiteralArgumentBuilder<CommandSource> node = LiteralArgumentBuilder.literal(mapping.getPrimaryAlias());

        // CommandSource == CommandCause, so this will be fine.
        node.requires((Predicate<CommandSource>) (Object) requirement).executes(command);
        for (CommandNode<CommandSource> commandNode : commandSourceRootCommandNode) {
            node.then(commandNode);
        }

        LiteralCommandNode<CommandSource> commandToAppend =
                ((SpongeCommandDispatcher) SpongeImpl.getServer().getCommandManager().getDispatcher()).registerInternal(node);
        for (String secondaryAlias : mapping.getAllAliases()) {
            if (!secondaryAlias.equals(mapping.getPrimaryAlias())) {
                ((SpongeCommandDispatcher) SpongeImpl.getServer().getCommandManager().getDispatcher())
                        .registerInternal(LiteralArgumentBuilder.<CommandSource>literal(secondaryAlias).redirect(commandToAppend));
            }
        }

        return mapping;
    }

    @NonNull
    private CommandMapping registerAliasInternal(
            @NonNull final CommandRegistrar registrar,
            @NonNull final PluginContainer container,
            @NonNull final String primaryAlias,
            @NonNull final String @NonNull... secondaryAliases)
            throws CommandFailedRegistrationException {
        // Check it's been registered:
        if (primaryAlias.contains(" ") || Arrays.stream(secondaryAliases).anyMatch(x -> x.contains(" "))) {
                throw new CommandFailedRegistrationException("Aliases may not contain spaces.");
        }

        // We have a Sponge command, so let's start by checking to see what
        // we're going to register.
        String primaryAliasLowercase = primaryAlias.toLowerCase(Locale.ENGLISH);
        String namespacedAlias = container.getId() + ":" + primaryAlias.toLowerCase(Locale.ENGLISH);
        if (this.commandMappings.containsKey(namespacedAlias)) {
            // It's registered.
            throw new CommandFailedRegistrationException(
                    "The command alias " + primaryAlias + " has already been registered for this plugin");
        }

        Set<String> aliases = new HashSet<>();
        aliases.add(primaryAliasLowercase);
        aliases.add(namespacedAlias);
        for (String secondaryAlias : secondaryAliases) {
            aliases.add(secondaryAlias.toLowerCase(Locale.ENGLISH));
        }

        // Okay, what can we register?
        aliases.removeIf(this.commandMappings::containsKey);

        // We need to consider the configuration file - if there is an entry in there
        // then remove an alias if the command is not entitled to use it.
        SpongeImpl.getGlobalConfigAdapter().getConfig()
                .getCommands()
                .getAliases()
                .entrySet()
                .stream()
                .filter(x -> !x.getValue().equalsIgnoreCase(container.getId()))
                .filter(x -> aliases.contains(x.getKey()))
                .forEach(x -> aliases.remove(x.getKey()));

        if (aliases.isEmpty()) {
            // If the mapping is empty, throw an exception. Shouldn't happen, but you never know.
            throw new CommandFailedRegistrationException("No aliases could be registered for the supplied command.");
        }

        // Create the mapping
        final SpongeCommandMapping mapping = new SpongeCommandMapping(
                primaryAlias,
                aliases,
                container,
                registrar
        );

        this.pluginToCommandMap.put(container, mapping);
        aliases.forEach(key -> {
            this.commandMappings.put(key, mapping);
            this.inverseCommandMappings.put(mapping, key);
        });
        return mapping;
    }

    // Sponge command
    @Override
    @NonNull
    public CommandMapping register(
            @NonNull final PluginContainer container,
            @NonNull final Command command,
            @NonNull final String primaryAlias,
            @NonNull final String @NonNull... secondaryAliases) throws CommandFailedRegistrationException {
        final CommandMapping mapping;
        if (command instanceof Command.Parameterized) {
            // send it to the Sponge Managed registrar
            mapping = SpongeManagedCommandRegistrar.INSTANCE.register(container, (Command.Parameterized) command, primaryAlias, secondaryAliases);
        } else {
            // send it to the Sponge Managed registrar
            mapping = SpongeRawCommandRegistrar.INSTANCE.register(container, command, primaryAlias, secondaryAliases);
        }

        return mapping;
    }

    @Override
    @NonNull
    public Optional<CommandMapping> unregister(@NonNull final CommandMapping mapping) {
        if (!(mapping instanceof SpongeCommandMapping)) {
            throw new IllegalArgumentException("Mapping is not of type SpongeCommandMapping!");
        }

        final SpongeCommandMapping spongeCommandMapping = (SpongeCommandMapping) mapping;

        // Cannot unregister Sponge or Minecraft commands
        if (isMinecraftOrSpongePluginContainer(mapping.getPlugin())) {
            return Optional.empty();
        }

        final Collection<String> aliases = this.inverseCommandMappings.get(spongeCommandMapping);
        if (mapping.getAllAliases().containsAll(aliases)) {
            // Okay - the mapping checks out.
            this.inverseCommandMappings.removeAll(spongeCommandMapping);
            aliases.forEach(this.commandMappings::remove);
            this.pluginToCommandMap.remove(spongeCommandMapping.getPlugin(), spongeCommandMapping);

            // notify the registrar, which will do what it needs to do
            spongeCommandMapping.getRegistrar().unregister(mapping);
            return Optional.of(spongeCommandMapping);
        }

        return Optional.empty();
    }

    @Override
    @NonNull
    public Collection<CommandMapping> unregisterAll(@NonNull PluginContainer container) {
        final Collection<SpongeCommandMapping> mappingsToRemove = this.pluginToCommandMap.get(container);
        final ImmutableList.Builder<CommandMapping> commandMappingBuilder = ImmutableList.builder();
        for (final CommandMapping toRemove : mappingsToRemove) {
            unregister(toRemove).ifPresent(commandMappingBuilder::add);
        }

        return commandMappingBuilder.build();
    }

    @Override
    @NonNull
    public Collection<PluginContainer> getPlugins() {
        return ImmutableSet.copyOf(this.pluginToCommandMap.keySet());
    }

    @Override
    public boolean isRegistered(@NonNull final CommandMapping mapping) {
        Preconditions.checkArgument(mapping instanceof SpongeCommandMapping, "Mapping is not of type SpongeCommandMapping!");
        return this.inverseCommandMappings.containsKey(mapping);
    }

    public int process(@NonNull final CommandSource source, @NonNull final String commandToSend) throws net.minecraft.command.CommandException {
        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.pushCause(source);
            return process(commandToSend).getResult();
        } catch (CommandException ex) {
            final Throwable cause = ex.getCause();
            if (cause instanceof net.minecraft.command.CommandException) {
                throw (net.minecraft.command.CommandException) cause;
            }

            source.sendErrorMessage(SpongeTexts.toComponent(ex.getText()));
            return 0;
        }
    }

    @Override
    @NonNull
    public CommandResult process(@NonNull final String arguments) throws CommandException {
        try (CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.addContext(EventContextKeys.COMMAND_STRING.get(), arguments);
            final String[] splitArg = arguments.split(" ", 2);
            final String command = splitArg[0];
            final String args = splitArg.length == 2 ? splitArg[1] : "";
            final SpongeCommandMapping mapping = this.commandMappings.get(command.toLowerCase());
            if (mapping == null) {
                // no command.
                throw new CommandException(Text.of(TextColors.RED, "Unknown command. Type /help for a list of commands."));
            }
            return mapping.getRegistrar().process(CommandCause.of(frame.getCurrentCause()), mapping.getPrimaryAlias(), args);
        }
    }

    @Override
    @NonNull
    public <T extends Subject & MessageReceiver> CommandResult process(
            @NonNull T subjectReceiver,
            @NonNull String arguments) throws CommandException {
        try (CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.addContext(EventContextKeys.SUBJECT.get(), subjectReceiver);
            frame.addContext(EventContextKeys.MESSAGE_CHANNEL.get(), MessageChannel.to(subjectReceiver));
            return process(arguments);
        }
    }

    @Override
    @NonNull
    public CommandResult process(
            @NonNull final Subject subject,
            @NonNull final MessageChannel receiver,
            @NonNull final String arguments) throws CommandException {
        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.addContext(EventContextKeys.SUBJECT.get(), subject);
            frame.addContext(EventContextKeys.MESSAGE_CHANNEL.get(), receiver);
            return process(arguments);
        }
    }

    @Override
    @NonNull
    public List<String> suggest(@NonNull String arguments) {
        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.addContext(EventContextKeys.COMMAND_STRING.get(), arguments);
            final String[] splitArg = arguments.split(" ", 2);
            final String command = splitArg[0].toLowerCase();

            if (splitArg.length == 2) {
                // we have a subcommand, suggest on that if it exists, else
                // return nothing
                final SpongeCommandMapping mapping = this.commandMappings.get(command);
                if (mapping == null) {
                    return Collections.emptyList();
                }

                return mapping.getRegistrar().suggestions(
                        CommandCause.of(frame.getCurrentCause()), mapping.getPrimaryAlias(), splitArg[1]);
            }

            return this.commandMappings.keySet()
                    .stream()
                    .filter(x -> x.startsWith(command))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    @NonNull
    public <T extends Subject & MessageReceiver> List<String> suggest(
            @NonNull T subjectReceiver,
            @NonNull String arguments) {
        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.addContext(EventContextKeys.SUBJECT.get(), subjectReceiver);
            frame.addContext(EventContextKeys.MESSAGE_CHANNEL.get(), MessageChannel.to(subjectReceiver));
            return suggest(arguments);
        }
    }

    @Override
    @NonNull
    public List<String> suggest(
            @NonNull final Subject subject,
            @NonNull final MessageChannel receiver,
            @NonNull final String arguments) {
        try (final CauseStackManager.StackFrame frame = Sponge.getCauseStackManager().pushCauseFrame()) {
            frame.addContext(EventContextKeys.SUBJECT.get(), subject);
            frame.addContext(EventContextKeys.MESSAGE_CHANNEL.get(), receiver);
            return suggest(arguments);
        }
    }

    public Map<String, SpongeCommandMapping> getMappings() {
        return this.commandMappings;
    }

    private boolean isMinecraftOrSpongePluginContainer(final PluginContainer pluginContainer) {
        return !(SpongeImpl.getMinecraftPlugin() == pluginContainer || SpongeImpl.getSpongePlugin() == pluginContainer);
    }

}
