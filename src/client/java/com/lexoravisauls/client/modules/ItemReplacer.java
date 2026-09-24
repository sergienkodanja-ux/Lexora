package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.core.ClientData;
import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;

import java.util.List;

public class ItemReplacer {
    public static ItemStack getRenderStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof SwordItem)) {
            return stack;
        }

        boolean enabled = ClientData.moduleStates.getOrDefault("Custom Swords",
                LexoraGui.moduleStates.getOrDefault("Custom Swords", false));
        if (!enabled) {
            return stack;
        }

        String model = ClientData.modeSettings.getOrDefault("Custom Swords Model",
                ClientData.modeSettings.getOrDefault("Custom Swords:Model",
                        LexoraGui.modeSettings.getOrDefault("Custom Swords Model",
                                LexoraGui.modeSettings.getOrDefault("Custom Swords:Model", "Katana"))));
        if (model == null || model.isEmpty() || model.equalsIgnoreCase("Default") || model.equalsIgnoreCase("None")) {
            return stack;
        }

        ItemStack copy = stack.copy();
        copy.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(
                List.of(),
                List.of(),
                List.of(model),
                List.of()
        ));
        return copy;
    }
}
