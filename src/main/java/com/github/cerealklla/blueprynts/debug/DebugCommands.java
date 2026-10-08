package com.github.cerealklla.blueprynts.debug;

import java.util.List;
import java.util.Optional;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import com.github.cerealklla.blueprynts.blueprint.BlueprintCell;
import com.github.cerealklla.blueprynts.blueprint.BlueprintRecord;
import com.github.cerealklla.blueprynts.blueprint.BlueprintStorage;
import com.github.cerealklla.blueprynts.blueprint.SequenceComputer;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * DEBUG ONLY -- deliberately no permission gate, same "remove or gate more strictly before any real
 * release" precedent as Lyfe's/Yconomics' own {@code debug.DebugCommands}.
 *
 * <p>Every Blueprint saved before {@link BlueprintCell#sequence()} existed (the 15 "Ancestors"
 * reference Blueprints plus any other pre-2026-09-29 save) has every cell at the {@code -1}
 * backfill sentinel, which sorts identically for all cells and so falls back to on-disk order --
 * for these, that happens to read as "front to back, one whole column at a time" rather than
 * "ground floor up," since the original save loop filled one column's entire vertical extent
 * before moving to the next (a real playtest report, 2026-09-29: "the blueprints are building from
 * front to back, not ground up"). {@code /blueprynts resequence <name>}/{@code resequence_all}
 * re-run {@link SequenceComputer#assign} against an already-saved Blueprint's existing cells and
 * re-save it under the same name -- a one-off migration, not something a player ever needs to run.
 */
public final class DebugCommands {

    private DebugCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("blueprynts")
                .requires(source -> true) // deliberately no permission gate -- see class doc
                .then(Commands.literal("resequence")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> resequenceOne(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("resequence_all")
                        .executes(ctx -> resequenceAll(ctx.getSource()))));
    }

    private static int resequenceOne(CommandSourceStack source, String name) {
        Optional<BlueprintRecord> found = BlueprintStorage.get().load(name);
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("No Blueprint named '" + name + "'."));
            return 0;
        }
        resequence(found.get());
        source.sendSuccess(() -> Component.literal("Resequenced '" + name + "'."), false);
        return 1;
    }

    private static int resequenceAll(CommandSourceStack source) {
        int count = 0;
        for (String name : BlueprintStorage.get().listNames()) {
            Optional<BlueprintRecord> found = BlueprintStorage.get().load(name);
            if (found.isPresent()) {
                resequence(found.get());
                count++;
            }
        }
        int total = count;
        source.sendSuccess(() -> Component.literal("Resequenced " + total + " Blueprint(s)."), false);
        return count;
    }

    private static void resequence(BlueprintRecord record) {
        List<BlueprintCell> sequenced = SequenceComputer.assign(record.cells());
        BlueprintRecord updated = new BlueprintRecord(record.templateVersion(), record.name(), record.author(), record.status(),
                record.reviews(), record.blueprintTypeId(), record.tier(), record.relativeColumns(), record.height(), record.depth(),
                sequenced, record.facing(), record.sizeClass());
        BlueprintStorage.get().save(updated);
    }
}
