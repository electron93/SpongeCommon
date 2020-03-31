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

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.command.CommandSource;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.command.CommandExecutor;
import org.spongepowered.api.command.parameter.Parameter;
import org.spongepowered.common.command.brigadier.tree.SpongeCommandExecutorWrapper;
import org.spongepowered.common.command.parameter.multi.SpongeMultiParameter;

import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class SpongeParameterTranslator {

    @NonNull
    public Set<LiteralCommandNode<CommandSource>> convertSpongeParameters(
            @NonNull final String primaryAlias,
            @NonNull final Set<String> secondaryAliases,
            @NonNull final Predicate<CommandCause> requirement,
            @NonNull final List<Parameter> parameter,
            @NonNull final CommandExecutor executor) {

        final SpongeCommandExecutorWrapper executorWrapper = new SpongeCommandExecutorWrapper(executor);
        final Predicate<CommandSource> requirementsPredicate = new RequirementsPredicate(requirement);
        final LiteralArgumentBuilder<CommandSource> primary =
                LiteralArgumentBuilder.<CommandSource>literal(primaryAlias).requires(requirementsPredicate);
        final ListIterator<Parameter> parameterListIterator = parameter.listIterator();

        // If we have no parameters, or they are all optional, all literals will get an executor.
        final boolean isOptional = parameterListIterator.hasNext() || createNode(parameterListIterator.next(),
                parameterListIterator,
                executorWrapper,
                primary::then);
        if (isOptional) {
            primary.executes(executorWrapper);
        }

        final LiteralCommandNode<CommandSource> primaryNode = primary.build();
        final Set<LiteralCommandNode<CommandSource>> res = new HashSet<>();
        res.add(primaryNode);
        secondaryAliases.stream()
                .map(x -> LiteralArgumentBuilder.<CommandSource>literal(x)
                        .requires(requirementsPredicate)
                        .redirect(primaryNode)
                        .build())
                .forEach(res::add);
        return res;
    }

    private boolean createNode(
            @NonNull final Parameter currentParameter,
            @NonNull final ListIterator<Parameter> parameters,
            @NonNull final SpongeCommandExecutorWrapper executorWrapper,
            @NonNull final Consumer<CommandNode<CommandSource>> nodeConsumer) {

        boolean isTermination = currentParameter.isTerminal();

        // Process the next element if it exists
        ArgumentBuilder<CommandSource, ?> currentNode = createNode(currentParameter);
        if (parameters.hasNext()) {
            isTermination = createNode(parameters.next(), parameters, executorWrapper, currentNode::then);
        }

        if (isTermination) {
            currentNode.executes(executorWrapper);
        }

        // Apply the node to the parent if required.
        CommandNode<CommandSource> builtNode = currentNode.build();
        nodeConsumer.accept(builtNode);

        // see if we can spin this out a bit better.
        if (currentParameter instanceof Parameter.Subcommand) {
            Set<String> aliases = ((Parameter.Subcommand) currentParameter).getAliases();
            for (String alias : aliases) {
                if (!alias.equalsIgnoreCase(builtNode.getName())) {
                    nodeConsumer.accept(
                            LiteralArgumentBuilder.<CommandSource>literal(alias)
                                    .redirect(builtNode)
                                    .requires(builtNode.getRequirement())
                                    .executes(builtNode.getCommand())
                                    .build());
                }
            }
        }

        // Return true if all arguments are optional and so the preceding parameter should be treated as a termination,
        // false otherwise.
        return isTermination;
    }

    private void firstOf(@NonNull final List<Parameter> parameter, @NonNull final Consumer<CommandNode<CommandSource>> nodeConsumer) {

    }

    @NonNull
    private ArgumentBuilder<CommandSource, ?> createNode(@NonNull final Parameter parameter) {
        // If the parameter is a sequence parameter, we recurse into here with a separate list iterator
        // If the parameter is a first of, then we go to firstOf
        // Else, create node and move on.
        if (parameter instanceof Parameter.Subcommand) {
            return subcommandNode((Parameter.Subcommand) parameter);
        } else if (parameter instanceof SpongeMultiParameter) {
            return ((SpongeMultiParameter) parameter).createNode(this);
        }

        // TODO: Obviously this is wrong
        return null;
    }

    @NonNull
    private LiteralArgumentBuilder<CommandSource> subcommandNode(final Parameter.@NonNull Subcommand subcommand) {
        RequirementsPredicate predicate = new RequirementsPredicate(x -> subcommand.getCommand().canExecute(x));
        // convertSpongeParameters(subcommand.getAliases(), ImmutableList.of(), predicate, subcommand.getCommand().getParameters)
        // TODO
        return null;
    }

    static final class RequirementsPredicate implements Predicate<CommandSource> {

        private final Predicate<CommandCause> requirement;

        RequirementsPredicate(final Predicate<CommandCause> requirement) {
            this.requirement = requirement;
        }

        @Override
        public boolean test(CommandSource commandSource) {
            return this.requirement.test((CommandCause) commandSource);
        }
    }

}
