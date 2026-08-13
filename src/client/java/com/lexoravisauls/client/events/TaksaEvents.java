package com.lexoravisauls.client.events;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.utils.models.DogBrain;
import com.lexoravisauls.client.utils.models.DogModel;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

/**
 * Хук питомца "Taksa" — тик + рендер.
 *
 * ВАЖНО: у друга это был Module с @ModuleRegister и своей шиной событий
 * (TickEvent/Render3DEvent). Я не видел вашу реальную систему модулей —
 * только ModernSettingsRegistry с настройками, поэтому:
 *
 *  1) тик и рендер зарегистрированы напрямую через Fabric API
 *     (ClientTickEvents.END_CLIENT_TICK / WorldRenderEvents.AFTER_ENTITIES),
 *     без завязки на чужой базовый класс Module — гарантированно скомпилится;
 *  2) вкл/выкл проверяется через LexoraGui.moduleStates.getOrDefault("Taksa", false),
 *     по аналогии с тем, как читаются булевые тумблеры в ModernSettingsRegistry.
 *
 * Если модули у вас на самом деле — отдельные классы с onEnable()/onDisable()
 * (как у друга), а не флаг в мапе, пришли пример любого своего простого модуля
 * (лучше с тиком+рендером) — перепишу 1-в-1 под вашу архитектуру.
 */
@Environment(EnvType.CLIENT)
public class TaksaEvents {

   private static final DogBrain brain = new DogBrain();
   private static final DogModel model = new DogModel();

   public static void register() {
      ClientTickEvents.END_CLIENT_TICK.register(TaksaEvents::onTick);
      WorldRenderEvents.AFTER_ENTITIES.register(TaksaEvents::onRender);
   }

   private static boolean isEnabled() {
      return LexoraGui.moduleStates.getOrDefault("Taksa", false);
   }

   private static void onTick(MinecraftClient mc) {
      if (!isEnabled() || mc.player == null) {
         return;
      }

      brain.setEntity(mc.player);
      brain.onUpdate();

      boolean attackReactionOn = LexoraGui.moduleStates.getOrDefault("Taksa Attack Reaction", true);
      if (attackReactionOn && mc.options.attackKey.isPressed() && mc.targetedEntity instanceof LivingEntity living
            && living.isAlive() && living != mc.player) {
         brain.setAttackTarget(living);
      }
   }

   private static void onRender(WorldRenderContext context) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (!isEnabled() || mc.player == null || mc.world == null) {
         return;
      }

      float tickDelta = mc.getRenderTickCounter().getTickDelta(false);
      Vec3d dogWorldPos = brain.getPos(tickDelta);
      Camera camera = mc.gameRenderer.getCamera();
      Vec3d cam = camera.getPos();
      MatrixStack ms = context.matrixStack();
      ms.push();
      ms.translate(dogWorldPos.x - cam.x, dogWorldPos.y - cam.y, dogWorldPos.z - cam.z);
      float age = mc.player.age + tickDelta;
      model.setRotationAngles(age, brain);
      BlockPos lightPos = BlockPos.ofFloored(dogWorldPos);
      int light = LightmapTextureManager.pack(
            mc.world.getLightLevel(LightType.BLOCK, lightPos),
            mc.world.getLightLevel(LightType.SKY, lightPos)
      );
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.enableDepthTest();
      RenderSystem.disableCull();
      VertexConsumerProvider.Immediate vcp = mc.getBufferBuilders().getEntityVertexConsumers();
      model.render(ms, vcp, brain, light);
      vcp.draw();
      RenderSystem.enableCull();
      RenderSystem.disableBlend();
      ms.pop();
   }
}
