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
package org.spongepowered.common.mixin.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.command.brigadier.SpongeCommandDispatcher;

@Mixin(Commands.class)
public abstract class CommandsMixin {

    // We augment the CommandDispatcher with our own methods using a wrapper, so we need to make sure it's replaced here.
    @Redirect(method = "<init>", at = @At(
            value = "NEW",
            args = "class=com/mojang/brigadier/CommandDispatcher"
    ))
    private CommandDispatcher<CommandSource> impl$useVanillaRegistrarAsDispatcher() {
        return new SpongeCommandDispatcher();
    }

    // We redirect to our own command manager, which might return to the dispatcher.
    @Redirect(method = "handleCommand", at = @At(value = "INVOKE",
            target = "Lcom/mojang/brigadier/CommandDispatcher;execute(Lcom/mojang/brigadier/StringReader;Ljava/lang/Object;)I"))
    private int impl$redirectExecuteCall(CommandDispatcher<?> commandDispatcher, StringReader input, Object sourceSentToDispatcher,
            CommandSource source, String commandToSend) throws net.minecraft.command.CommandException {
        return SpongeImpl.getCommandManager().process(source, commandToSend);
    }
/* TODO: This may very well not be needed, but keeping it on hand just in case for the moment

    @Shadow
    protected abstract void commandSourceNodesToSuggestionNodes(CommandNode<CommandSource> rootCommandSource,
            CommandNode<ISuggestionProvider> rootSuggestion, CommandSource source,
            Map<CommandNode<CommandSource>, CommandNode<ISuggestionProvider>> commandNodeToSuggestionNode);

    @Redirect(method = "send", at =
        @At(
                value = "INVOKE",
                target = "Lnet/minecraft/command/Commands;commandSourceNodesToSuggestionNodes"
                + "(Lcom/mojang/brigadier/tree/CommandNode;Lcom/mojang/brigadier/tree/CommandNode;Lnet/minecraft/command/CommandSource;Ljava/util/Map;)V"))
    private void impl$addSuggestionsToCommandList(
            Commands commands,
            CommandNode<CommandSource> rootCommandSource,
            CommandNode<ISuggestionProvider> rootSuggestion,
            CommandSource source,
            Map<CommandNode<CommandSource>, CommandNode<ISuggestionProvider>> commandNodeToSuggestionNode) {

        // We start by letting the Vanilla code do its thing...
        this.commandSourceNodesToSuggestionNodes(rootCommandSource, rootSuggestion, source, commandNodeToSuggestionNode);

        CommandCause cause = CommandCause.of(Sponge.getCauseStackManager().getCurrentCause());

        // Now we take our command manager. Anything that is a Brigadier backed manager is easy...
        SpongeImpl.getRegistry().getCatalogRegistry().getAllOf(CommandRegistrar.class)
                .filter(x -> !(x instanceof VanillaCommandRegistrar))
                .forEach(x -> {
                    if (x instanceof BrigadierBackedCommandRegistrar) {
                        // use the node to throw it something similar to the Vanilla spec...
                        Map<CommandNode<CommandCause>, CommandNode<ISuggestionProvider>> map = new HashMap<>();
                        this.impl$createSuggestionNodes(
                                ((BrigadierBackedCommandRegistrar) x).getCommandNode(),
                                rootSuggestion,
                                cause,
                                map);
                    } else {
                        // get the command trees
                        RootCommandTreeBuilder treeBuilder = new RootCommandTreeBuilder();
                        x.completeCommandTree(cause, treeBuilder);

                        // TODO: create a Brig tree and merge
                    }
                });

        // Finally, add our aliases to the tree using redirects.
        SpongeCommandManager commandManager = SpongeImpl.getCommandManager();
        commandManager.getMappings().forEach((primary, mapping) -> {
            CommandNode<ISuggestionProvider> targetRedirect = rootSuggestion.getChild(primary);
            mapping.getAllAliases().forEach(alias -> {
                if (!alias.equals(primary)) {
                    rootSuggestion.addChild(
                            LiteralArgumentBuilder.<ISuggestionProvider>literal(alias).redirect(targetRedirect).build()
                    );
                }
            });
        });
    }
*/
}
