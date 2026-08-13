package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.gui.modern.ScreenCaptureManager;
import com.lexoravisauls.client.modules.FastSwap;
import com.lexoravisauls.client.utils.TabAnimState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(InGameHud.class)
public class MixinInGameHud {

    @Shadow @Final private PlayerListHud playerListHud;
    private float animHotbarSlot = -1;

    // =====================================================================
    //  ФИКС БЛЮРА: ГЛОБАЛЬНЫЙ ЗАХВАТ ЭКРАНА ДО ОТРИСОВКИ ИНТЕРФЕЙСА
    //  Это навсегда убирает баг с клонированием текста и иконок в блюре!
    // =====================================================================
    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderCapture(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        try {
            ScreenCaptureManager.captureScreen();
        } catch (Throwable ignored) {}
    }

    @ModifyArg(
            method = "renderHotbar",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIII)V", ordinal = 1),
            index = 2
    )
    private int modifySelectionX(int x) {
        if (!LexoraGui.moduleStates.getOrDefault("Animations", true)
                || !LexoraGui.moduleStates.getOrDefault("Anim Hotbar", true)) return x;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return x;

        float targetSlot = mc.player.getInventory().selectedSlot;
        if (animHotbarSlot == -1) animHotbarSlot = targetSlot;
        animHotbarSlot += (targetSlot - animHotbarSlot) * 0.2f;

        int middle = mc.getWindow().getScaledWidth() / 2;
        return middle - 91 - 1 + (int) (animHotbarSlot * 20);
    }

    // =====================================================================
    //  ФИКС: updateScale перенесён в HEAD — scaleY обновляется ДО того,
    //  как InGameHud внутри вызовет PlayerListHud.render().
    //  Раньше было в RETURN → первый фрейм таб рисовался без масштаба
    //  (scaleY ещё 0), что давало 1 кадр полного таба без анимации.
    // =====================================================================
    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderMainHead(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!LexoraGui.moduleStates.getOrDefault("Animations", true)
                || !LexoraGui.moduleStates.getOrDefault("Anim Tab", true)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        boolean pressed = mc.options.playerListKey.isPressed();

        // animY для DynamicIsland — обновляем здесь же
        TabAnimState.targetY = pressed ? 0f : -150f;
        TabAnimState.animY += (TabAnimState.targetY - TabAnimState.animY)
                * (pressed ? 0.15f : 0.25f);

        // updateScale ДО рендера — теперь PlayerListHud.render() увидит
        // правильный scaleY с самого первого нажатия TAB
        TabAnimState.updateScale(pressed);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderMainReturn(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!LexoraGui.moduleStates.getOrDefault("Animations", true)
                || !LexoraGui.moduleStates.getOrDefault("Anim Tab", true)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        boolean pressed = mc.options.playerListKey.isPressed();

        // Дорисовываем таб при закрытии пока scaleY > 0
        if (!pressed && TabAnimState.scaleY > 0.01f && mc.world != null) {
            Scoreboard scoreboard = mc.world.getScoreboard();
            this.playerListHud.render(
                    context,
                    context.getScaledWindowWidth(),
                    scoreboard,
                    scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST)
            );
        }
    }

    @Inject(method = "renderHotbar", at = @At("RETURN"))
    private void onRenderHotbarBinds(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;

        if (player == null || !LexoraGui.moduleStates.getOrDefault("Fast Swap", false)) return;
        if (!LexoraGui.moduleStates.getOrDefault("Show Hotbar Binds", true)) return;

        int scaledWidth = mc.getWindow().getScaledWidth();
        int scaledHeight = mc.getWindow().getScaledHeight();
        int startX = scaledWidth / 2 - 91;
        int y = scaledHeight - 22;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();

            for (Map.Entry<String, Item> entry : FastSwap.SWAP_ITEMS.entrySet()) {
                if (entry.getValue() == item) {
                    int key = LexoraGui.numSettings.getOrDefault("Bind_" + entry.getKey(), -1f).intValue();
                    if (key != -1) {
                        String keyName = getKeyName(key);
                        int slotX = startX + i * 20 + 2;
                        int slotY = y - 10;

                        net.minecraft.text.Text bindText = net.minecraft.text.Text.literal(keyName)
                                .setStyle(net.minecraft.text.Style.EMPTY.withFont(
                                        net.minecraft.util.Identifier.of("lexoravisauls", "sfui")));

                        context.drawText(mc.textRenderer, bindText, slotX + 1, slotY,
                                LexoraGui.getGuiThemeColor(), true);
                    }
                }
            }
        }
    }

    private String getKeyName(int key) {
        if (key < 0) return "M" + Math.abs(key);
        return InputUtil.fromKeyCode(key, -1).getLocalizedText().getString().toUpperCase();
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderPortal(DrawContext context, float f, CallbackInfo ci) {
        if (LexoraGui.moduleStates.getOrDefault("No Render", false)
                && LexoraGui.moduleStates.getOrDefault("No Portal", true)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderSpyglass(DrawContext context, float scale, CallbackInfo ci) {
        if (LexoraGui.moduleStates.getOrDefault("No Render", false)
                && LexoraGui.moduleStates.getOrDefault("No Pumpkin", true)) {
            ci.cancel();
        }
    }
}