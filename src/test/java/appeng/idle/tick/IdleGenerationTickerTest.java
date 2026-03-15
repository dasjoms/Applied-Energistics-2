package appeng.idle.tick;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import appeng.core.AEConfig;
import appeng.idle.currency.CurrencyDefinition;
import appeng.idle.currency.CurrencyId;
import appeng.idle.currency.IdleCurrencies;
import appeng.idle.currency.IdleCurrencyManager;
import appeng.idle.player.PlayerIdleData;
import appeng.idle.player.PlayerIdleDataManager;

class IdleGenerationTickerTest {
    @Test
    void currenciesToGenerateUsesRegisteredCurrenciesNotPlayerBalances() throws Exception {
        var managerInstanceField = IdleCurrencyManager.class.getDeclaredField("INSTANCE");
        managerInstanceField.setAccessible(true);
        var managerInstance = managerInstanceField.get(null);

        var currenciesField = IdleCurrencyManager.class.getDeclaredField("currencies");
        currenciesField.setAccessible(true);

        @SuppressWarnings("unchecked")
        var originalCurrencies = (Map<CurrencyId, CurrencyDefinition>) currenciesField.get(managerInstance);

        var extraId = new CurrencyId(ResourceLocation.fromNamespaceAndPath("ae2", "extra_test_currency"));
        var extraDefinition = new CurrencyDefinition(
                extraId,
                "gui.ae2.idle.currency.extra_test_currency",
                ResourceLocation.fromNamespaceAndPath("ae2", "certus_quartz_crystal"),
                1,
                true,
                null);

        var modified = new LinkedHashMap<>(originalCurrencies);
        modified.put(extraId, extraDefinition);

        try {
            currenciesField.set(managerInstance, Map.copyOf(modified));

            var currenciesToGenerate = IdleGenerationTicker.currenciesToGenerate();
            assertThat(currenciesToGenerate)
                    .contains(IdleCurrencies.IDLE, extraId);
        } finally {
            currenciesField.set(managerInstance, originalCurrencies);
        }
    }

    @Test
    void authoritativeBalancesAccrueOnlyOnGenerationIntervalBoundaries() {
        var server = mock(MinecraftServer.class);
        var playerList = mock(PlayerList.class);
        var player = mock(ServerPlayer.class);
        when(server.getPlayerList()).thenReturn(playerList);
        when(playerList.getPlayers()).thenReturn(java.util.List.of(player));
        when(player.getServer()).thenReturn(server);

        var data = new PlayerIdleData(Map.of(IdleCurrencies.IDLE, 0L), 0L,
                PlayerIdleData.CURRENT_DATA_VERSION, Map.of(), true);
        data.setGenerationProgressTicks(IdleCurrencies.IDLE, 19L);

        var config = mock(AEConfig.class);

        try (MockedStatic<AEConfig> aeConfig = mockStatic(AEConfig.class);
                MockedStatic<PlayerIdleDataManager> dataManager = mockStatic(PlayerIdleDataManager.class)) {
            aeConfig.when(AEConfig::instance).thenReturn(config);
            when(config.getIdleGenerationIntervalTicks()).thenReturn(20);

            dataManager.when(() -> PlayerIdleDataManager.isPassiveGenerationEnabled(player)).thenReturn(true);
            dataManager.when(() -> PlayerIdleDataManager.get(player)).thenReturn(data);
            runServerTick(2, server);
            dataManager.verify(() -> PlayerIdleDataManager.addGeneratedBalances(eq(player), any(), any()), times(0));

            runServerTick(20, server);

            dataManager.verify(() -> PlayerIdleDataManager.save(player, data), times(1));
            dataManager.verify(() -> PlayerIdleDataManager.get(player), times(1));
            dataManager.verify(() -> PlayerIdleDataManager.isPassiveGenerationEnabled(player), times(1));
        }

        assertThat(data.getBalance(IdleCurrencies.IDLE)).isZero();
        assertThat(data.getOnlineProgressBaselineTick()).isEqualTo(20L);
    }

    private static void runServerTick(int tickCount, MinecraftServer server) {
        var event = mock(ServerTickEvent.Post.class);
        when(event.getServer()).thenReturn(server);
        when(server.getTickCount()).thenReturn(tickCount);
        IdleGenerationTicker.onServerTickEnd(event);
    }
}
