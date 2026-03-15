package appeng.server.subcommands;

import static net.minecraft.commands.Commands.argument;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import appeng.idle.reward.natural.NaturalLogTracker;
import appeng.server.ISubCommand;

/**
 * Dev-only helper for QA to inspect idle natural-log provenance at a target block.
 */
public class IdleLogProvenanceCommand implements ISubCommand {
    @Override
    public void addArguments(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(argument("pos", BlockPosArgument.blockPos())
                .executes(ctx -> {
                    inspect(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "pos"));
                    return 1;
                }));
    }

    @Override
    public void call(MinecraftServer srv, CommandContext<CommandSourceStack> ctx, CommandSourceStack sender) {
        try {
            ServerPlayer player = sender.getPlayerOrException();
            inspect(ctx, player.blockPosition());
        } catch (CommandSyntaxException e) {
            sender.sendFailure(Component.literal("Usage: /ae2 idlelogprovenance <x y z>"));
        }
    }

    private static void inspect(CommandContext<CommandSourceStack> ctx, BlockPos pos) {
        var source = ctx.getSource();
        var level = source.getLevel();
        var state = level.getBlockState(pos);
        var provenance = NaturalLogTracker.getProvenanceForDebug(level, pos, state);
        var natural = NaturalLogTracker.isNaturallyGeneratedLog(level, pos, state);

        source.sendSuccess(() -> Component.literal("Idle log provenance at " + pos + ": block="
                + state.getBlock().builtInRegistryHolder().key().location() + ", provenance=" + provenance
                + ", naturalEligible=" + natural), false);
    }
}
