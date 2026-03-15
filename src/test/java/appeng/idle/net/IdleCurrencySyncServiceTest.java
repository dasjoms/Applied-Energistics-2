package appeng.idle.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.core.AEConfig;
import appeng.core.definitions.AEItems;
import appeng.idle.currency.CurrencyDefinition;
import appeng.idle.currency.CurrencyId;
import appeng.idle.currency.IdleCurrencyManager;
import appeng.idle.player.PlayerIdleData;
import appeng.idle.player.PlayerIdleDataManager;

class IdleCurrencySyncServiceTest {
    private static final CurrencyId HUD_TEST_CURRENCY = new CurrencyId(
            ResourceLocation.fromNamespaceAndPath("ae2", "hud_sync_test_currency"));

    @Test
    void projectedDisplayProgressAdvancesFromPersistedBaseline() {
        var projected = IdleCurrencySyncService.projectDisplayProgressTicks(15L, 100L, 7L);

        assertThat(projected).isEqualTo(22L);
    }

    @Test
    void projectedDisplayProgressWrapsAtTicksPerUnitBoundary() {
        var projected = IdleCurrencySyncService.projectDisplayProgressTicks(18L, 20L, 5L);

        assertThat(projected).isEqualTo(19L);
    }

    @Test
    void projectedDisplayProgressClampsNegativeBaselineAndElapsed() {
        var projected = IdleCurrencySyncService.projectDisplayProgressTicks(-2L, 20L, -4L);

        assertThat(projected).isZero();
    }

    @Test
    void projectedDisplayProgressReturnsZeroWhenTicksPerUnitIsNonPositive() {
        assertThat(IdleCurrencySyncService.projectDisplayProgressTicks(10L, 0L, 5L)).isZero();
        assertThat(IdleCurrencySyncService.projectDisplayProgressTicks(10L, -1L, 5L)).isZero();
    }

    @Test
    void sendSnapshotDoesNotSendHudPacketWhenVisorIsAbsent() {
        var player = mock(ServerPlayer.class);
        when(player.getItemBySlot(EquipmentSlot.HEAD)).thenReturn(ItemStack.EMPTY);

        var data = new PlayerIdleData(Map.of(), 0L, PlayerIdleData.CURRENT_DATA_VERSION, Map.of(), true);

        try (MockedStatic<PlayerIdleDataManager> dataManager = mockStatic(PlayerIdleDataManager.class);
                MockedStatic<PacketDistributor> packets = mockStatic(PacketDistributor.class)) {
            dataManager.when(() -> PlayerIdleDataManager.get(player)).thenReturn(data);
            dataManager.when(() -> PlayerIdleDataManager.isPassiveGenerationEnabled(player)).thenReturn(false);

            IdleCurrencySyncService.sendSnapshot(player);

            packets.verify(() -> PacketDistributor.sendToPlayer(eq(player),
                    argThat(packet -> packet instanceof IdleCurrencySnapshotPacket)), times(1));
            packets.verify(() -> PacketDistributor.sendToPlayer(eq(player),
                    argThat(packet -> packet instanceof IdleCurrencyHudSnapshotPacket)), never());
        }
    }

    @Test
    void sendSnapshotSendsHudPacketWhenVisorIsPresent() {
        var player = mock(ServerPlayer.class);
        when(player.getItemBySlot(EquipmentSlot.HEAD)).thenReturn(AEItems.IDLE_VISOR.stack());

        var data = new PlayerIdleData(Map.of(), 0L, PlayerIdleData.CURRENT_DATA_VERSION, Map.of(), true);

        try (MockedStatic<PlayerIdleDataManager> dataManager = mockStatic(PlayerIdleDataManager.class);
                MockedStatic<PacketDistributor> packets = mockStatic(PacketDistributor.class)) {
            dataManager.when(() -> PlayerIdleDataManager.get(player)).thenReturn(data);
            dataManager.when(() -> PlayerIdleDataManager.isPassiveGenerationEnabled(player)).thenReturn(false);

            IdleCurrencySyncService.sendSnapshot(player);

            packets.verify(() -> PacketDistributor.sendToPlayer(eq(player),
                    argThat(packet -> packet instanceof IdleCurrencySnapshotPacket)), times(1));
            packets.verify(() -> PacketDistributor.sendToPlayer(eq(player),
                    argThat(packet -> packet instanceof IdleCurrencyHudSnapshotPacket)), times(1));
        }
    }

    @Test
    void hudHeartbeatUsesConfiguredIntervalAndOnlyTargetsVisorWearers() throws Exception {
        withInjectedCurrency(new CurrencyDefinition(
                HUD_TEST_CURRENCY,
                "gui.ae2.idle.currency.hud_sync_test_currency",
                "gui.ae2.idle.currency.hud_sync_test_currency",
                ResourceLocation.fromNamespaceAndPath("ae2", "certus_quartz_crystal"),
                20,
                true,
                null), () -> {
                    var server = mock(MinecraftServer.class);
                    var playerList = mock(PlayerList.class);
                    when(server.getPlayerList()).thenReturn(playerList);

                    var visorPlayer = mock(ServerPlayer.class);
                    when(visorPlayer.getItemBySlot(EquipmentSlot.HEAD)).thenReturn(AEItems.IDLE_VISOR.stack());
                    when(visorPlayer.getServer()).thenReturn(server);

                    var nonVisorPlayer = mock(ServerPlayer.class);
                    when(nonVisorPlayer.getItemBySlot(EquipmentSlot.HEAD)).thenReturn(ItemStack.EMPTY);
                    when(nonVisorPlayer.getServer()).thenReturn(server);

                    when(playerList.getPlayers()).thenReturn(java.util.List.of(visorPlayer, nonVisorPlayer));

                    var data = new PlayerIdleData(Map.of(HUD_TEST_CURRENCY, 5L), 0L,
                            PlayerIdleData.CURRENT_DATA_VERSION, Map.of(), true);
                    data.setOnlineProgressBaselineTick(0L);

                    var config = mock(AEConfig.class);

                    try (MockedStatic<AEConfig> aeConfig = mockStatic(AEConfig.class);
                            MockedStatic<PlayerIdleDataManager> dataManager = mockStatic(PlayerIdleDataManager.class);
                            MockedStatic<PacketDistributor> packets = mockStatic(PacketDistributor.class)) {
                        aeConfig.when(AEConfig::instance).thenReturn(config);
                        when(config.getIdleHudSyncIntervalTicks()).thenReturn(2);
                        when(config.getIdleGenerationIntervalTicks()).thenReturn(20);
                        dataManager.when(() -> PlayerIdleDataManager.get(any())).thenReturn(data);
                        dataManager.when(() -> PlayerIdleDataManager.isPassiveGenerationEnabled(any()))
                                .thenReturn(true);

                        runServerTick(1, server);
                        runServerTick(2, server);
                        runServerTick(3, server);
                        runServerTick(4, server);
                        runServerTick(5, server);
                        runServerTick(6, server);

                        packets.verify(() -> PacketDistributor.sendToPlayer(eq(visorPlayer),
                                argThat(packet -> packet instanceof IdleCurrencyHudSnapshotPacket)), times(3));
                        packets.verify(() -> PacketDistributor.sendToPlayer(eq(nonVisorPlayer),
                                argThat(packet -> packet instanceof IdleCurrencyHudSnapshotPacket)), times(0));
                    }
                });
    }

    @Test
    void successiveTwoTickHudSnapshotsAdvanceProgressAndEtaWithoutAccrual() throws Exception {
        withInjectedCurrency(new CurrencyDefinition(
                HUD_TEST_CURRENCY,
                "gui.ae2.idle.currency.hud_sync_test_currency",
                "gui.ae2.idle.currency.hud_sync_test_currency",
                ResourceLocation.fromNamespaceAndPath("ae2", "certus_quartz_crystal"),
                25,
                true,
                null), () -> {
                    var server = mock(MinecraftServer.class);
                    var playerList = mock(PlayerList.class);
                    when(server.getPlayerList()).thenReturn(playerList);

                    var visorPlayer = mock(ServerPlayer.class);
                    when(visorPlayer.getItemBySlot(EquipmentSlot.HEAD)).thenReturn(AEItems.IDLE_VISOR.stack());
                    when(visorPlayer.getServer()).thenReturn(server);
                    when(playerList.getPlayers()).thenReturn(java.util.List.of(visorPlayer));

                    var data = new PlayerIdleData(Map.of(HUD_TEST_CURRENCY, 9L), 0L,
                            PlayerIdleData.CURRENT_DATA_VERSION, Map.of(), true);
                    data.setGenerationProgressTicks(HUD_TEST_CURRENCY, 1L);
                    data.setOnlineProgressBaselineTick(0L);

                    var config = mock(AEConfig.class);

                    try (MockedStatic<AEConfig> aeConfig = mockStatic(AEConfig.class);
                            MockedStatic<PlayerIdleDataManager> dataManager = mockStatic(PlayerIdleDataManager.class);
                            MockedStatic<PacketDistributor> packets = mockStatic(PacketDistributor.class)) {
                        aeConfig.when(AEConfig::instance).thenReturn(config);
                        when(config.getIdleHudSyncIntervalTicks()).thenReturn(2);
                        when(config.getIdleGenerationIntervalTicks()).thenReturn(20);
                        dataManager.when(() -> PlayerIdleDataManager.get(visorPlayer)).thenReturn(data);
                        dataManager.when(() -> PlayerIdleDataManager.isPassiveGenerationEnabled(visorPlayer))
                                .thenReturn(true);

                        var snapshots = new java.util.ArrayList<IdleCurrencyHudSnapshotPacket>();
                        packets.when(() -> PacketDistributor.sendToPlayer(eq(visorPlayer), any()))
                                .thenAnswer(invocation -> {
                                    var payload = invocation.getArgument(1);
                                    if (payload instanceof IdleCurrencyHudSnapshotPacket packet) {
                                        snapshots.add(packet);
                                    }
                                    return null;
                                });

                        runServerTick(2, server);
                        runServerTick(24, server);

                        assertThat(snapshots).hasSize(2);
                        var first = snapshots.get(0).values().get(HUD_TEST_CURRENCY);
                        var second = snapshots.get(1).values().get(HUD_TEST_CURRENCY);
                        assertThat(second.progressTicks()).isGreaterThan(first.progressTicks());
                        assertThat(second.secondsToNext()).isNotEqualTo(first.secondsToNext());
                        assertThat(data.getBalance(HUD_TEST_CURRENCY)).isEqualTo(9L);
                    }
                });
    }

    @Test
    void unchangedHudHeartbeatSnapshotIsSkippedUntilValueChanges() throws Exception {
        withInjectedCurrency(new CurrencyDefinition(
                HUD_TEST_CURRENCY,
                "gui.ae2.idle.currency.hud_sync_test_currency",
                "gui.ae2.idle.currency.hud_sync_test_currency",
                ResourceLocation.fromNamespaceAndPath("ae2", "certus_quartz_crystal"),
                20,
                true,
                null), () -> {
                    var player = mock(ServerPlayer.class);
                    when(player.getUUID()).thenReturn(UUID.randomUUID());
                    when(player.getServer()).thenReturn(mock(MinecraftServer.class));

                    var data = new PlayerIdleData(Map.of(HUD_TEST_CURRENCY, 5L), 0L,
                            PlayerIdleData.CURRENT_DATA_VERSION, Map.of(), true);
                    data.setOnlineProgressBaselineTick(0L);

                    try (MockedStatic<PlayerIdleDataManager> dataManager = mockStatic(PlayerIdleDataManager.class);
                            MockedStatic<PacketDistributor> packets = mockStatic(PacketDistributor.class)) {
                        dataManager.when(() -> PlayerIdleDataManager.get(player)).thenReturn(data);
                        dataManager.when(() -> PlayerIdleDataManager.isPassiveGenerationEnabled(player))
                                .thenReturn(false);

                        IdleCurrencySyncService.sendHudSnapshot(player);
                        IdleCurrencySyncService.sendHudSnapshot(player);

                        data.setBalance(HUD_TEST_CURRENCY, 6L);
                        IdleCurrencySyncService.sendHudSnapshot(player);

                        packets.verify(() -> PacketDistributor.sendToPlayer(eq(player),
                                argThat(packet -> packet instanceof IdleCurrencyHudSnapshotPacket)), times(2));
                    }
                });
    }

    @Test
    void sendEmptyHudSnapshotClearsHudCache() throws Exception {
        withInjectedCurrency(new CurrencyDefinition(
                HUD_TEST_CURRENCY,
                "gui.ae2.idle.currency.hud_sync_test_currency",
                "gui.ae2.idle.currency.hud_sync_test_currency",
                ResourceLocation.fromNamespaceAndPath("ae2", "certus_quartz_crystal"),
                20,
                true,
                null), () -> {
                    var player = mock(ServerPlayer.class);
                    when(player.getUUID()).thenReturn(UUID.randomUUID());
                    when(player.getServer()).thenReturn(mock(MinecraftServer.class));

                    var data = new PlayerIdleData(Map.of(HUD_TEST_CURRENCY, 5L), 0L,
                            PlayerIdleData.CURRENT_DATA_VERSION, Map.of(), true);
                    data.setOnlineProgressBaselineTick(0L);

                    try (MockedStatic<PlayerIdleDataManager> dataManager = mockStatic(PlayerIdleDataManager.class);
                            MockedStatic<PacketDistributor> packets = mockStatic(PacketDistributor.class)) {
                        dataManager.when(() -> PlayerIdleDataManager.get(player)).thenReturn(data);
                        dataManager.when(() -> PlayerIdleDataManager.isPassiveGenerationEnabled(player))
                                .thenReturn(false);

                        IdleCurrencySyncService.sendHudSnapshot(player);
                        IdleCurrencySyncService.sendEmptyHudSnapshot(player);
                        IdleCurrencySyncService.sendHudSnapshot(player);

                        packets.verify(() -> PacketDistributor.sendToPlayer(eq(player),
                                argThat(packet -> packet instanceof IdleCurrencyHudSnapshotPacket)), times(3));
                    }
                });
    }

    @Test
    void playerLogoutClearsHudCache() throws Exception {
        withInjectedCurrency(new CurrencyDefinition(
                HUD_TEST_CURRENCY,
                "gui.ae2.idle.currency.hud_sync_test_currency",
                "gui.ae2.idle.currency.hud_sync_test_currency",
                ResourceLocation.fromNamespaceAndPath("ae2", "certus_quartz_crystal"),
                20,
                true,
                null), () -> {
                    var player = mock(ServerPlayer.class);
                    when(player.getUUID()).thenReturn(UUID.randomUUID());
                    when(player.getServer()).thenReturn(mock(MinecraftServer.class));

                    var data = new PlayerIdleData(Map.of(HUD_TEST_CURRENCY, 5L), 0L,
                            PlayerIdleData.CURRENT_DATA_VERSION, Map.of(), true);
                    data.setOnlineProgressBaselineTick(0L);

                    var logoutEvent = mock(PlayerLoggedOutEvent.class);
                    when(logoutEvent.getEntity()).thenReturn(player);

                    try (MockedStatic<PlayerIdleDataManager> dataManager = mockStatic(PlayerIdleDataManager.class);
                            MockedStatic<PacketDistributor> packets = mockStatic(PacketDistributor.class)) {
                        dataManager.when(() -> PlayerIdleDataManager.get(player)).thenReturn(data);
                        dataManager.when(() -> PlayerIdleDataManager.isPassiveGenerationEnabled(player))
                                .thenReturn(false);

                        IdleCurrencySyncService.sendHudSnapshot(player);
                        IdleCurrencySyncService.handlePlayerLoggedOut(logoutEvent);
                        IdleCurrencySyncService.sendHudSnapshot(player);

                        packets.verify(() -> PacketDistributor.sendToPlayer(eq(player),
                                argThat(packet -> packet instanceof IdleCurrencyHudSnapshotPacket)), times(2));
                    }
                });
    }

    private static void runServerTick(int tickCount, MinecraftServer server) {
        var event = mock(ServerTickEvent.Post.class);
        when(event.getServer()).thenReturn(server);
        when(server.getTickCount()).thenReturn(tickCount);
        IdleCurrencySyncService.handleServerTickEnd(event);
    }

    private static void withInjectedCurrency(CurrencyDefinition definition, ThrowingRunnable assertion)
            throws Exception {
        var managerInstanceField = IdleCurrencyManager.class.getDeclaredField("INSTANCE");
        managerInstanceField.setAccessible(true);
        var managerInstance = managerInstanceField.get(null);

        var currenciesField = IdleCurrencyManager.class.getDeclaredField("currencies");
        currenciesField.setAccessible(true);

        @SuppressWarnings("unchecked")
        var originalCurrencies = (Map<CurrencyId, CurrencyDefinition>) currenciesField.get(managerInstance);

        var modified = new LinkedHashMap<>(originalCurrencies);
        modified.put(definition.id(), definition);

        try {
            currenciesField.set(managerInstance, Map.copyOf(modified));
            assertion.run();
        } finally {
            currenciesField.set(managerInstance, originalCurrencies);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
