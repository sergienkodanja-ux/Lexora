package com.lexoravisauls.client.mixin;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class MixinClientWorld {

    @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true)
    private void onGetRainGradient(float delta, CallbackInfoReturnable<Float> cir) {
        boolean worldCustomizer = ClientData.moduleStates.getOrDefault("World Customizer",
                LexoraGui.moduleStates.getOrDefault("World Customizer", false));
        if (worldCustomizer) {
            String weather = ClientData.modeSettings.getOrDefault("Weather Mode",
                    LexoraGui.modeSettings.getOrDefault("Weather Mode", "Clear"));
            if (weather.equals("Clear")) cir.setReturnValue(0f);
            else if (weather.equals("Rain") || weather.equals("Thunder")) cir.setReturnValue(1f);
        }
    }

    @Inject(method = "getThunderGradient", at = @At("HEAD"), cancellable = true)
    private void onGetThunderGradient(float delta, CallbackInfoReturnable<Float> cir) {
        boolean worldCustomizer = ClientData.moduleStates.getOrDefault("World Customizer",
                LexoraGui.moduleStates.getOrDefault("World Customizer", false));
        if (worldCustomizer) {
            String weather = ClientData.modeSettings.getOrDefault("Weather Mode",
                    LexoraGui.modeSettings.getOrDefault("Weather Mode", "Clear"));
            if (weather.equals("Thunder")) cir.setReturnValue(1f);
            else cir.setReturnValue(0f);
        }
    }
}
