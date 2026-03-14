package appeng.server.subcommands;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

import appeng.idle.upgrade.IdleUpgradePurchaseService;

class IdleCurrencyCommandTest {
    private static final ResourceLocation UPGRADE_ID = ResourceLocation.fromNamespaceAndPath("ae2", "matter_mk1");

    @Test
    void buildsSuccessPurchaseMessage() {
        var message = IdleCurrencyCommand.buildPurchaseMessage(
                "alex",
                UPGRADE_ID,
                IdleUpgradePurchaseService.PurchaseResult.SUCCESS);

        assertThat(message).isEqualTo("Purchased ae2:matter_mk1 for alex.");
    }

    @Test
    void buildsUnknownUpgradeMessage() {
        var message = IdleCurrencyCommand.buildPurchaseMessage(
                "alex",
                UPGRADE_ID,
                IdleUpgradePurchaseService.PurchaseResult.UNKNOWN_UPGRADE);

        assertThat(message).isEqualTo("Purchase rejected for alex: unknown upgrade ae2:matter_mk1.");
    }

    @Test
    void buildsMaxLevelMessage() {
        var message = IdleCurrencyCommand.buildPurchaseMessage(
                "alex",
                UPGRADE_ID,
                IdleUpgradePurchaseService.PurchaseResult.MAX_LEVEL);

        assertThat(message).isEqualTo("Purchase rejected for alex: upgrade already at max level (ae2:matter_mk1).");
    }

    @Test
    void buildsInsufficientFundsMessage() {
        var message = IdleCurrencyCommand.buildPurchaseMessage(
                "alex",
                UPGRADE_ID,
                IdleUpgradePurchaseService.PurchaseResult.INSUFFICIENT_FUNDS);

        assertThat(message).isEqualTo("Purchase rejected for alex: insufficient idle currency for ae2:matter_mk1.");
    }
}
