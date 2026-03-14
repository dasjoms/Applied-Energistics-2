package appeng.idle.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import appeng.core.network.CustomAppEngPayload;
import appeng.core.network.ServerboundPacket;
import appeng.idle.currency.CurrencyAmount;
import appeng.idle.currency.CurrencyId;
import appeng.idle.player.PlayerIdleDataManager;

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
        if (amount <= 0L) {
            return;
        }

        var spent = PlayerIdleDataManager.trySpend(player, currencyId, new CurrencyAmount(amount));
        if (!spent) {
            IdleCurrencySyncService.sendSnapshot(player);
            return;
        }

        var data = PlayerIdleDataManager.get(player);
        var currentLevel = data.ownedUpgradeLevelsView().getOrDefault(upgradeId, 0);
        PlayerIdleDataManager.setUpgradeLevel(player, upgradeId, currentLevel + 1);

    }
}
