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

    @Test
    void buildsSetupgradeSuccessMessage() {
        var message = IdleCurrencyCommand.buildSetupgradeSuccessMessage("alex", UPGRADE_ID, 3);

        assertThat(message).isEqualTo("Set ae2:matter_mk1 for alex to level 3.");
    }

    @Test
    void buildsSetupgradeFailureMessage() {
        var message = IdleCurrencyCommand.buildSetupgradeFailureMessage(
                "alex",
                UPGRADE_ID,
                "level 9 exceeds max level 5");

        assertThat(message)
                .isEqualTo("Set-upgrade rejected for alex: level 9 exceeds max level 5 (ae2:matter_mk1).");
    }

    @Test
    void validatesSetupgradeRequestWithinKnownBounds() {
        var validation = IdleCurrencyCommand.validateSetupgradeRequest(appeng.idle.upgrade.IdleUpgrades.TIMBER_1.id(),
                5);

        assertThat(validation).isNull();
    }

    @Test
    void rejectsSetupgradeRequestAboveKnownBounds() {
        var validation = IdleCurrencyCommand.validateSetupgradeRequest(appeng.idle.upgrade.IdleUpgrades.TIMBER_1.id(),
                6);

        assertThat(validation).isEqualTo("level 6 exceeds max level 5");
    }

    @Test
    void allowsSetupgradeRequestForUnknownUpgrade() {
        var validation = IdleCurrencyCommand.validateSetupgradeRequest(UPGRADE_ID, 999);

        assertThat(validation).isNull();
    }

}
