package com.lexoravisauls.client.motionblur;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MotionReBlur {
    public static final String MOD_ID = "motionreblur";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * Вызывается из инициализации основного мода (например, из твоего
     * ClientModInitializer), чтобы поднять шейдер Motion Blur.
     * Настройки для GUI регистрируются отдельно, в ModernSettingsRegistry.
     */
    public static void init() {
        LOGGER.info("Motion ReBlur initialized!");
        MotionBlurModule.getInstance();
    }
}