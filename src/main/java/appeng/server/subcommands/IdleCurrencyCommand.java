package appeng.server.subcommands;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import appeng.core.AEConfig;
import appeng.idle.currency.CurrencyAmount;
import appeng.idle.currency.CurrencyId;
import appeng.idle.player.PlayerIdleData;
import appeng.idle.player.PlayerIdleDataManager;
import appeng.idle.upgrade.IdleUpgradePurchaseService;
import appeng.idle.upgrade.IdleUpgrades;
import appeng.server.ISubCommand;

/**
 * Admin/debug tooling for idle-currency verification without a dedicated UI.
 */
public class IdleCurrencyCommand implements ISubCommand {
    @Override
    public void addArguments(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.requires(source -> source.hasPermission(2));

        builder.then(literal("balance")
                .then(argument("player", EntityArgument.player())
                        .executes(ctx -> {
                            var player = EntityArgument.getPlayer(ctx, "player");
                            showAllBalances(ctx.getSource(), player);
                            return 1;
                        })
                        .then(argument("currency", ResourceLocationArgument.id())
                                .executes(ctx -> {
                                    var player = EntityArgument.getPlayer(ctx, "player");
                                    var currency = new CurrencyId(ResourceLocationArgument.getId(ctx, "currency"));
                                    showBalance(ctx.getSource(), player, currency);
                                    return 1;
                                }))));

        builder.then(literal("grant")
                .then(argument("player", EntityArgument.player())
                        .then(argument("currency", ResourceLocationArgument.id())
                                .then(argument("amount", LongArgumentType.longArg(0L))
                                        .executes(ctx -> {
                                            grantCurrency(ctx);
                                            return 1;
                                        })))));

        builder.then(literal("purchase")
                .then(argument("player", EntityArgument.player())
                        .then(argument("upgrade", ResourceLocationArgument.id())
                                .executes(ctx -> {
                                    attemptPurchase(ctx);
                                    return 1;
                                }))));

        builder.then(literal("setupgrade")
                .then(argument("player", EntityArgument.player())
                        .then(argument("upgrade", ResourceLocationArgument.id())
                                .then(argument("level", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            setUpgradeLevel(ctx, ResourceLocationArgument.getId(ctx, "upgrade"));
                                            return 1;
                                        })))));

        builder.then(literal("settimber")
                .then(argument("player", EntityArgument.player())
                        .then(argument("level", IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    setUpgradeLevel(ctx, IdleUpgrades.TIMBER_1.id());
                                    return 1;
                                }))));

        builder.then(literal("setlastseen")
                .then(argument("player", EntityArgument.player())
                        .then(argument("epochSeconds", LongArgumentType.longArg(0L))
                                .executes(ctx -> {
                                    setLastSeen(ctx);
                                    return 1;
                                }))));

        builder.then(literal("catchup")
                .then(argument("player", EntityArgument.player())
                        .then(argument("elapsedSeconds", LongArgumentType.longArg(0L))
                                .executes(ctx -> {
                                    simulateCatchup(ctx);
                                    return 1;
                                }))));
    }

    @Override
    public void call(MinecraftServer srv, CommandContext<CommandSourceStack> ctx, CommandSourceStack sender) {
        sender.sendSuccess(
                () -> Component
                        .literal(
                                "Usage: /ae2 idlecurrency <balance|grant|purchase|setupgrade|settimber|setlastseen|catchup> ..."),
                false);
    }

    private static void showAllBalances(CommandSourceStack sender, ServerPlayer player) {
        var data = PlayerIdleDataManager.get(player);
        if (data.balancesView().isEmpty()) {
            sender.sendSuccess(
                    () -> Component.literal(player.getGameProfile().getName() + " has no idle currency balances."),
                    false);
            return;
        }

        data.balancesView().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().id().toString()))
                .forEach(entry -> sender.sendSuccess(
                        () -> Component.literal(player.getGameProfile().getName() + " " + entry.getKey().id()
                                + " = " + entry.getValue()),
                        false));
    }

    private static void showBalance(CommandSourceStack sender, ServerPlayer player, CurrencyId currencyId) {
        var data = PlayerIdleDataManager.get(player);
        var amount = data.getBalance(currencyId);
        sender.sendSuccess(
                () -> Component.literal(player.getGameProfile().getName() + " " + currencyId.id() + " = " + amount),
                false);
    }

    private static void grantCurrency(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = EntityArgument.getPlayer(ctx, "player");
        var currencyId = new CurrencyId(ResourceLocationArgument.getId(ctx, "currency"));
        var amount = LongArgumentType.getLong(ctx, "amount");

        var updated = PlayerIdleDataManager.addBalance(player, currencyId, new CurrencyAmount(amount));
        ctx.getSource().sendSuccess(
                () -> Component.literal("Granted " + amount + " " + currencyId.id() + " to "
                        + player.getGameProfile().getName() + ". New balance: " + updated),
                true);
    }

    private static void attemptPurchase(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = EntityArgument.getPlayer(ctx, "player");
        var upgradeId = ResourceLocationArgument.getId(ctx, "upgrade");
        var result = IdleUpgradePurchaseService.tryPurchase(player, upgradeId);

        ctx.getSource().sendSuccess(
                () -> Component.literal(buildPurchaseMessage(player.getGameProfile().getName(), upgradeId, result)),
                true);
    }

    static String buildPurchaseMessage(String playerName, ResourceLocation upgradeId,
            IdleUpgradePurchaseService.PurchaseResult result) {
        return switch (result) {
            case SUCCESS -> "Purchased " + upgradeId + " for " + playerName + ".";
            case UNKNOWN_UPGRADE -> "Purchase rejected for " + playerName + ": unknown upgrade " + upgradeId + ".";
            case MAX_LEVEL -> "Purchase rejected for " + playerName + ": upgrade already at max level (" + upgradeId
                    + ").";
            case INSUFFICIENT_FUNDS -> "Purchase rejected for " + playerName + ": insufficient idle currency for "
                    + upgradeId + ".";
        };
    }

    private static void setUpgradeLevel(CommandContext<CommandSourceStack> ctx, ResourceLocation upgradeId)
            throws CommandSyntaxException {
        var player = EntityArgument.getPlayer(ctx, "player");
        var level = IntegerArgumentType.getInteger(ctx, "level");

        var validation = validateSetupgradeRequest(upgradeId, level);
        if (validation != null) {
            ctx.getSource().sendFailure(Component.literal(buildSetupgradeFailureMessage(
                    player.getGameProfile().getName(),
                    upgradeId,
                    validation)));
            return;
        }

        PlayerIdleDataManager.setUpgradeLevel(player, upgradeId, level);
        ctx.getSource().sendSuccess(
                () -> Component
                        .literal(buildSetupgradeSuccessMessage(player.getGameProfile().getName(), upgradeId, level)),
                true);
    }

    static String buildSetupgradeSuccessMessage(String playerName, ResourceLocation upgradeId, int level) {
        return "Set " + upgradeId + " for " + playerName + " to level " + level + ".";
    }

    static String buildSetupgradeFailureMessage(String playerName, ResourceLocation upgradeId, String reason) {
        return "Set-upgrade rejected for " + playerName + ": " + reason + " (" + upgradeId + ").";
    }

    static String validateSetupgradeRequest(ResourceLocation upgradeId, int level) {
        var definition = IdleUpgrades.get(upgradeId);
        if (definition != null && level > definition.maxLevel()) {
            return "level " + level + " exceeds max level " + definition.maxLevel();
        }
        return null;
    }

    private static void setLastSeen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = EntityArgument.getPlayer(ctx, "player");
        var epochSeconds = LongArgumentType.getLong(ctx, "epochSeconds");
        PlayerIdleDataManager.setLastSeenEpochSeconds(player, epochSeconds);

        ctx.getSource().sendSuccess(
                () -> Component.literal("Set lastSeenEpochSeconds for " + player.getGameProfile().getName() + " to "
                        + epochSeconds + "."),
                true);
    }

    private static void simulateCatchup(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        var player = EntityArgument.getPlayer(ctx, "player");
        var requestedElapsed = LongArgumentType.getLong(ctx, "elapsedSeconds");
        var offlineCap = AEConfig.instance().getIdleOfflineMaxSeconds();
        var appliedElapsed = Math.min(requestedElapsed, offlineCap);

        var before = PlayerIdleDataManager.get(player);
        var beforeBalances = before.balancesView();

        PlayerIdleDataManager.simulateOfflineCatchup(player, requestedElapsed);

        var after = PlayerIdleDataManager.get(player);
        var delta = sumBalances(after) - sumBalances(beforeBalances);

        ctx.getSource().sendSuccess(
                () -> Component.literal("Simulated offline catch-up for " + player.getGameProfile().getName()
                        + ": requested=" + requestedElapsed + "s, applied=" + appliedElapsed + "s, totalDelta="
                        + delta + ", lastSeen=" + Instant.now().getEpochSecond()),
                true);
    }

    private static long sumBalances(PlayerIdleData data) {
        return sumBalances(data.balancesView());
    }

    private static long sumBalances(Map<CurrencyId, Long> balances) {
        var total = 0L;
        for (var balance : balances.values()) {
            total += balance;
        }
        return total;
    }
}
