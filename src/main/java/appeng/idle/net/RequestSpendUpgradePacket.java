package appeng.idle.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import appeng.core.network.CustomAppEngPayload;
import appeng.core.network.ServerboundPacket;
import appeng.idle.currency.CurrencyId;
import appeng.idle.upgrade.IdleUpgradePurchaseService;

/**
 * Client request to spend idle currency for an upgrade. Server performs all validation and mutations.
 */
public record RequestSpendUpgradePacket(ResourceLocation upgradeId, CurrencyId currencyId,
        long amount) implements ServerboundPacket {

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestSpendUpgradePacket> STREAM_CODEC = StreamCodec
            .ofMember(RequestSpendUpgradePacket::write, RequestSpendUpgradePacket::decode);

    public static final Type<RequestSpendUpgradePacket> TYPE = CustomAppEngPayload.createType("request_spend_upgrade");

    @Override
    public Type<RequestSpendUpgradePacket> type() {
        return TYPE;
    }

    public static RequestSpendUpgradePacket decode(RegistryFriendlyByteBuf data) {
        return new RequestSpendUpgradePacket(
                data.readResourceLocation(),
                new CurrencyId(data.readResourceLocation()),
                data.readVarLong());
    }

    public void write(RegistryFriendlyByteBuf data) {
        data.writeResourceLocation(upgradeId);
        data.writeResourceLocation(currencyId.id());
        data.writeVarLong(amount);
    }

    @Override
    public void handleOnServer(ServerPlayer player) {
        // Currency and amount are still decoded for backward compatibility with older clients,
        // but server-authoritative pricing comes from the upgrade definition.
        var result = IdleUpgradePurchaseService.tryPurchase(player, upgradeId);
        if (result != IdleUpgradePurchaseService.PurchaseResult.SUCCESS) {
            IdleCurrencySyncService.sendSnapshot(player);
        }
    }
}
