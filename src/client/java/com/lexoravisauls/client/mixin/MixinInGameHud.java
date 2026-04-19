package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.gui.LexoraGui;
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

    // ТОТ САМЫЙ ИДЕАЛЬНЫЙ ХОТБАР
    @ModifyArg(
            method = "renderHotbar",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIII)V", ordinal = 1),
            index = 2
    )
    private int modifySelectionX(int x) {
        if (!LexoraGui.moduleStates.getOrDefault("Animations", true) || !LexoraGui.moduleStates.getOrDefault("Anim Hotbar", true)) return x;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return x;

        float targetSlot = mc.player.getInventory().selectedSlot;
        if (animHotbarSlot == -1) animHotbarSlot = targetSlot;

        animHotbarSlot += (targetSlot - animHotbarSlot) * 0.2f;

        int middle = mc.getWindow().getScaledWidth() / 2;
        return middle - 91 - 1 + (int) (animHotbarSlot * 20);
    }

    // ЛОГИКА АНИМАЦИИ ТАБА ПРИ ЗАКРЫТИИ
    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderMain(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!LexoraGui.moduleStates.getOrDefault("Animations", true) || !LexoraGui.moduleStates.getOrDefault("Anim Tab", true)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        boolean pressed = mc.options.playerListKey.isPressed();

        TabAnimState.targetY = pressed ? 0f : -150f;
        TabAnimState.animY += (TabAnimState.targetY - TabAnimState.animY) * (pressed ? 0.15f : 0.25f);

        if (!pressed && TabAnimState.animY > -149f && mc.world != null) {
            Scoreboard scoreboard = mc.world.getScoreboard();
            this.playerListHud.render(context, context.getScaledWindowWidth(), scoreboard, scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST));
        }
    }

    // 🔥 НОВОЕ: ОТРИСОВКА БИНДОВ FAST SWAP НА ХОТБАРЕ (Без фона + Шрифт sfui)
    @Inject(method = "renderHotbar", at = @At("RETURN"))
    private void onRenderHotbarBinds(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;

        // Проверяем, включен ли Fast Swap и опция отображения биндов
        if (player == null || !LexoraGui.moduleStates.getOrDefault("Fast Swap", false)) return;
        if (!LexoraGui.moduleStates.getOrDefault("Show Hotbar Binds", true)) return;

        int scaledWidth = mc.getWindow().getScaledWidth();
        int scaledHeight = mc.getWindow().getScaledHeight();

        int startX = scaledWidth / 2 - 91; // Начало хотбара
        int y = scaledHeight - 22;         // Высота хотбара

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();

            // Проходим по всем зарегистрированным предметам
            for (Map.Entry<String, Item> entry : FastSwap.SWAP_ITEMS.entrySet()) {
                if (entry.getValue() == item) {
                    // Ищем, забинден ли этот предмет
                    int key = LexoraGui.numSettings.getOrDefault("Bind_" + entry.getKey(), -1f).intValue();
                    if (key != -1) {
                        String keyName = getKeyName(key);

                        int slotX = startX + i * 20 + 2;
                        int slotY = y - 10; // Поднимаем текст чуть выше иконки предмета

                        // 🔥 ФИКС: Используем кастомный шрифт sfui
                        net.minecraft.text.Text bindText = net.minecraft.text.Text.literal(keyName)
                                .setStyle(net.minecraft.text.Style.EMPTY.withFont(net.minecraft.util.Identifier.of("lexoravisauls", "sfui")));

                        // Рисуем текст с тенью (true в конце), чтобы было видно без фона!
                        context.drawText(mc.textRenderer, bindText, slotX + 1, slotY, LexoraGui.getGuiThemeColor(), true);
                    }
                }
            }
        }
    }

    private String getKeyName(int key) {
        if (key < 0) {
            return "M" + Math.abs(key); // Мышь
        }
        return InputUtil.fromKeyCode(key, -1).getLocalizedText().getString().toUpperCase(); // Клавиатура
    }

    // ==========================================
    // 🔥 NO RENDER: УБИРАЕМ ПОРТАЛ И ТЫКВУ 🔥
    // ==========================================

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderPortal(DrawContext context, float f, CallbackInfo ci) {
        if (LexoraGui.moduleStates.getOrDefault("No Render", false) && LexoraGui.moduleStates.getOrDefault("No Portal", true)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderSpyglass(DrawContext context, float scale, CallbackInfo ci) {
        // Убираем рамку подзорной трубы и тыкву (в новых версиях они рисуются похоже)
        if (LexoraGui.moduleStates.getOrDefault("No Render", false) && LexoraGui.moduleStates.getOrDefault("No Pumpkin", true)) {
            ci.cancel();
        }
    }
}