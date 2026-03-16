package appeng.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import appeng.client.idle.combat.IdlePunchAnimationComponent;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    @Shadow
    @Final
    private EntityRenderDispatcher entityRenderDispatcher;

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void ae2$renderIdlePunchArm(
            AbstractClientPlayer player,
            float partialTick,
            float pitch,
            InteractionHand hand,
            float swingProgress,
            ItemStack stack,
            float equipProgress,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int combinedLight,
            CallbackInfo ci) {
        if (player.isScoping() || player.isInvisible() || !stack.isEmpty()) {
            return;
        }

        if (!IdlePunchAnimationComponent.shouldRenderForHand(player, hand)) {
            return;
        }

        var gameTime = player.level().getGameTime();
        var duration = IdlePunchAnimationComponent.getSwingDurationTicks();
        if (duration <= 0) {
            return;
        }

        var elapsed = (gameTime - IdlePunchAnimationComponent.getSwingStartTick()) + partialTick;
        var idleSwingProgress = Mth.clamp((float) elapsed / duration, 0.0F, 1.0F);

        var mainHand = hand == InteractionHand.MAIN_HAND;
        var humanoidArm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();

        poseStack.pushPose();
        renderPlayerArmPose(poseStack, buffer, combinedLight, equipProgress, idleSwingProgress, humanoidArm, player);
        poseStack.popPose();
        ci.cancel();
    }

    private void renderPlayerArmPose(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int combinedLight,
            float equipProgress,
            float swingProgress,
            HumanoidArm humanoidArm,
            AbstractClientPlayer player) {
        var rightArm = humanoidArm != HumanoidArm.LEFT;
        var direction = rightArm ? 1.0F : -1.0F;
        var sqrtSwing = Mth.sqrt(swingProgress);
        var swingX = -0.3F * Mth.sin(sqrtSwing * (float) Math.PI);
        var swingY = 0.4F * Mth.sin(sqrtSwing * (float) (Math.PI * 2));
        var swingZ = -0.4F * Mth.sin(swingProgress * (float) Math.PI);
        poseStack.translate(direction * (swingX + 0.64000005F), swingY + -0.6F + equipProgress * -0.6F,
                swingZ + -0.71999997F);
        poseStack.mulPose(Axis.YP.rotationDegrees(direction * 45.0F));
        var f5 = Mth.sin(swingProgress * swingProgress * (float) Math.PI);
        var f6 = Mth.sin(sqrtSwing * (float) Math.PI);
        poseStack.mulPose(Axis.YP.rotationDegrees(direction * f6 * 70.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(direction * f5 * -20.0F));
        poseStack.translate(direction * -1.0F, 3.6F, 3.5F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(direction * 120.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(200.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(direction * -135.0F));
        poseStack.translate(direction * 5.6F, 0.0F, 0.0F);

        var playerRenderer = (PlayerRenderer) this.entityRenderDispatcher.getRenderer(player);
        if (rightArm) {
            playerRenderer.renderRightHand(poseStack, buffer, combinedLight, player);
        } else {
            playerRenderer.renderLeftHand(poseStack, buffer, combinedLight, player);
        }
    }
}
