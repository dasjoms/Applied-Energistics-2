package appeng.mixins;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;

import appeng.hooks.IdlePunchAttackHook;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Shadow
    @Nullable
    public LocalPlayer player;

    @Shadow
    @Nullable
    public HitResult hitResult;

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void ae2$suppressVanillaMainHandSwingDuringIdlePunchTakeover(CallbackInfoReturnable<Boolean> cir) {
        var localPlayer = this.player;
        if (localPlayer == null || !IdlePunchAttackHook.shouldSuppressVanillaAttackSwing(localPlayer, this.hitResult)) {
            return;
        }

        localPlayer.swinging = false;
        localPlayer.swingTime = 0;
        cir.setReturnValue(false);
    }
}
