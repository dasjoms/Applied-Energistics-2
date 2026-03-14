package appeng.idle.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import appeng.core.network.CustomAppEngPayload;
import appeng.core.network.ServerboundPacket;
import appeng.idle.player.PlayerIdleDataManager;
import appeng.idle.upgrade.CurrencyTransactionService;
import appeng.idle.upgrade.IdleUpgrades;
import appeng.idle.upgrade.SpendReason;

/**
 * Client request to spend idle currency for an upgrade. Server performs all validation and mutations.
 */
public record RequestSpendUpgradePacket(ResourceLocation upgradeId) implements ServerboundPacket {

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestSpendUpgradePacket> STREAM_CODEC = StreamCodec
            .ofMember(RequestSpendUpgradePacket::write, RequestSpendUpgradePacket::decode);

    public static final Type<RequestSpendUpgradePacket> TYPE = CustomAppEngPayload.createType("request_spend_upgrade");

    @Override
    public Type<RequestSpendUpgradePacket> type() {
        return TYPE;
    }

    public static RequestSpendUpgradePacket decode(RegistryFriendlyByteBuf data) {
        return new RequestSpendUpgradePacket(data.readResourceLocation());
    }

    public void write(RegistryFriendlyByteBuf data) {
        data.writeResourceLocation(upgradeId);
    }

    @Override
    public void handleOnServer(ServerPlayer player) {
        var definition = IdleUpgrades.get(upgradeId);
        if (definition == null) {
            IdleCurrencySyncService.sendSnapshot(player);
            return;
        }

        var data = PlayerIdleDataManager.get(player);
        var currentLevel = data.ownedUpgradeLevelsView().getOrDefault(upgradeId, 0);
        if (currentLevel >= definition.maxLevel()) {
            IdleCurrencySyncService.sendSnapshot(player);
            return;
        }

        var spent = CurrencyTransactionService.trySpend(data, definition.costPerLevel(), SpendReason.UPGRADE_PURCHASE);
        if (!spent) {
            IdleCurrencySyncService.sendSnapshot(player);
            return;
        }

        PlayerIdleDataManager.save(player, data);
        PlayerIdleDataManager.setUpgradeLevel(player, upgradeId, currentLevel + 1);
    }
}
