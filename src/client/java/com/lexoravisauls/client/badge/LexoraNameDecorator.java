package com.lexoravisauls.client.badge;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class LexoraNameDecorator {
    private static final Identifier BADGE_FONT =
            Identifier.of("lexoravisauls", "lexora_badge");

    private static final String BADGE_CHAR = "\uE001";

    private static final boolean TEST_MODE = false;

    private LexoraNameDecorator() {
    }

    public static Text decorate(Text original) {
        if (original == null) {
            return Text.empty();
        }

        String originalString = original.getString();

        if (originalString.startsWith("◆") || originalString.startsWith(BADGE_CHAR)) {
            return original;
        }

        MutableText badge;

        if (TEST_MODE) {
            badge = Text.literal("◆")
                    .setStyle(Style.EMPTY.withColor(0x55AAFF));
        } else {
            badge = Text.literal(BADGE_CHAR)
                    .setStyle(Style.EMPTY.withFont(BADGE_FONT).withColor(0xFFFFFF));
        }

        return Text.empty()
                .append(badge)
                .append(Text.literal(" "))
                .append(original.copy());
    }
}