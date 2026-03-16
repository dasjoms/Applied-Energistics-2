package appeng.hooks;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.core.definitions.AEItems;
import appeng.core.network.serverbound.IdlePunchRequestPacket;
import appeng.idle.net.IdleCurrencyClientCache;

/**
 * Intercepts attack input for idle unarmed punching and redirects it to a server-authoritative packet.
 */
@OnlyIn(Dist.CLIENT)
public final class IdlePunchAttackHook {
    private static final double TARGET_PICK_RANGE = 5.0D;

    private IdlePunchAttackHook() {
    }

    public static void install() {
        NeoForge.EVENT_BUS.addListener(IdlePunchAttackHook::onInteractionKeyMappingTriggered);
    }

    private static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }

        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || !shouldTakeOverAttackInput(player)) {
            return;
        }

        if (!(minecraft.hitResult instanceof EntityHitResult entityHitResult)) {
            return;
        }

        var target = entityHitResult.getEntity();
        if (!target.isAlive() || player.distanceToSqr(target) > TARGET_PICK_RANGE * TARGET_PICK_RANGE) {
            return;
        }

        if (!IdleCurrencyClientCache.isIdlePunchEligible()) {
            return;
        }

        event.setCanceled(true);
        PacketDistributor.sendToServer(new IdlePunchRequestPacket(target.getId()));
    }

    private static boolean shouldTakeOverAttackInput(Player player) {
        return AEItems.IDLE_VISOR.is(player.getItemBySlot(EquipmentSlot.HEAD))
                && player.getMainHandItem().isEmpty()
                && player.getOffhandItem().isEmpty();
    }
}
