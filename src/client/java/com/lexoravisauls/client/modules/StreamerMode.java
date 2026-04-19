package com.lexoravisauls.client.modules;

import com.lexoravisauls.client.gui.LexoraGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class StreamerMode {

    public static String filterText(String text) {
        if (text == null || text.isEmpty()) return text;
        if (!LexoraGui.moduleStates.getOrDefault("Streamer Mode", false)) return text;
        if (!LexoraGui.moduleStates.getOrDefault("Hide Name", true)) return text;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getSession() == null) return text;

        String myName = mc.getSession().getUsername();
        if (myName == null || myName.isEmpty()) return text;

        return text.replace(myName, "Protected");
    }

    public static Text filterText(Text text) {
        if (text == null) return null;

        String original = text.getString();
        String filtered = filterText(original);

        if (filtered.equals(original)) {
            return text;
        }

        return Text.literal(filtered).setStyle(text.getStyle());
    }
}