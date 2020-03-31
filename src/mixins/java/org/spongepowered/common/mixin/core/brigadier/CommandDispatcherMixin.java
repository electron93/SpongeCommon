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
package org.spongepowered.common.mixin.core.brigadier;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.common.command.brigadier.SpongeStringReader;
import org.spongepowered.common.command.brigadier.context.SpongeCommandContextBuilder;

/*
 * A note about why this mixin does so many type checks.
 *
 * We're attempting to keep the standard logic as much as possible if the
 * Brigadier system is used for other command trees outside the main Minecraft
 * dispatcher. Because our objects are tailored to the CommandSource object, if
 * others don't use that, they'd be in for a rough time! So, we make sure that
 * if the method didn't start with our objects, then it doesn't continue with
 * them.
 *
 * We could have just copied the methods into our own dispatcher, but that would
 * have resulted in a lot of duplicated code. Most of these edits will go into
 * private methods, methods that we would otherwise override in derived types.
 */
@Mixin(CommandDispatcher.class)
public abstract class CommandDispatcherMixin<S> {

    @Shadow
    private ParseResults<S> shadow$parseNodes(
            final CommandNode<S> node,
            final StringReader originalReader,
            final CommandContextBuilder<S> contextSoFar) {
        throw new AssertionError("This shouldn't happen.");
    }

    @Redirect(
            method = "parseNodes",
            remap = false,
            slice = @Slice(
                    from = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/context/CommandContextBuilder;copy()"
                            + "Lcom/mojang/brigadier/context/CommandContextBuilder;"),
                    to = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/StringReader;<init>(Lcom/mojang/brigadier/StringReader;)V")
            ),
            at = @At(value = "NEW", target = "com/mojang/brigadier/StringReader"))
    private StringReader impl$copyStringReaderDuringParseNodes(final StringReader originalReader) {
        if (originalReader instanceof SpongeStringReader) {
            return new SpongeStringReader(originalReader, null); // the latter will get added later.
        }

        return new StringReader(originalReader);
    }

    @Redirect(
            method = "parseNodes",
            remap = false,
            slice = @Slice(
                    from = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/StringReader;<init>(Lcom/mojang/brigadier/StringReader;)V"),
                    to = @At(value = "FIELD", target = "Lcom/mojang/brigadier/exceptions/CommandSyntaxException;"
                            + "BUILT_IN_EXCEPTIONS:Lcom/mojang/brigadier/exceptions/BuiltInExceptionProvider;", opcode = Opcodes.GETFIELD)
            ),
            at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/tree/CommandNode;"
                    + "parse(Lcom/mojang/brigadier/StringReader;Lcom/mojang/brigadier/context/CommandContextBuilder;)V"))
    private void impl$onChildParse(CommandNode<S> commandNode, StringReader reader, CommandContextBuilder<S> contextBuilder)
            throws CommandSyntaxException {
        // this is that latter referred to above
        if (reader instanceof SpongeStringReader && contextBuilder instanceof SpongeCommandContextBuilder) {
            ((SpongeStringReader) reader).reapplyContextBuilder((SpongeCommandContextBuilder) contextBuilder);
        }

        commandNode.parse(reader, contextBuilder);
    }

    @SuppressWarnings("unchecked")
    @Redirect(
            method = "parseNodes",
            remap = false,
            slice = @Slice(
                    from = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/StringReader;skip()V"),
                    to = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/context/CommandContextBuilder;"
                            + "<init>(Lcom/mojang/brigadier/CommandDispatcher;Ljava/lang/Object;Lcom/mojang/brigadier/tree/CommandNode;I)V")
            ),
            at = @At(value = "NEW", target = "com/mojang/brigadier/context/CommandContextBuilder"))
    private CommandContextBuilder<S> impl$createCommandContextBuilderUponRedirect(
            final CommandDispatcher<S> dispatcher,
            final S source,
            final CommandNode<S> rootNode,
            final int start,
            final CommandNode<S> node,
            final StringReader originalReader,
            final CommandContextBuilder<S> contextSoFar) {
        if (contextSoFar instanceof SpongeCommandContextBuilder) {
            return (CommandContextBuilder<S>) new SpongeCommandContextBuilder((SpongeCommandContextBuilder) contextSoFar);
        }

        return new CommandContextBuilder<>(dispatcher, source, rootNode, start);
    }

    @Redirect(
            method = "parseNodes",
            remap = false,
            slice = @Slice(
                    from = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/context/CommandContextBuilder;"
                            + "<init>(Lcom/mojang/brigadier/CommandDispatcher;Ljava/lang/Object;Lcom/mojang/brigadier/tree/CommandNode;I)V"),
                    to = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/context/CommandContextBuilder;"
                            + "withChild(Lcom/mojang/brigadier/context/CommandContextBuilder;)Lcom/mojang/brigadier/context/CommandContextBuilder;")
            ),
            at = @At(value = "INVOKE", target = "Lcom/mojang/brigadier/CommandDispatcher;parseNodes(Lcom/mojang/brigadier/tree/CommandNode;"
                    + "Lcom/mojang/brigadier/StringReader;Lcom/mojang/brigadier/context/CommandContextBuilder;)Lcom/mojang/brigadier/ParseResults;"))
    private ParseResults<S> impl$parseNodesOnRedirect(CommandDispatcher<S> commandDispatcher, CommandNode<S> node,
            StringReader originalReader, CommandContextBuilder<S> contextSoFar) {

        // Because we "hide" our context in the StringReader for our nodes, we need to create a new reader and use
        // that in the redirect. We then need to sync the cursor position back.
        if (originalReader instanceof SpongeStringReader && contextSoFar instanceof SpongeCommandContextBuilder) {
            final SpongeStringReader currentReader = (SpongeStringReader) originalReader;
            final ParseResults<S> results = shadow$parseNodes(node,
                    new SpongeStringReader(originalReader, (SpongeCommandContextBuilder) contextSoFar),
                    contextSoFar);
            currentReader.setCursor(results.getReader().getCursor());
            return results;
        }

        return shadow$parseNodes(node, originalReader, contextSoFar);
    }

}
