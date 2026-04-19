package com.lexoravisauls.client.utils;

import org.lwjgl.opengl.GL11;

public class HandShaderStencil {

    private HandShaderStencil() {
    }

    public static boolean ensureStencilAttachment() {
        // Больше НЕ пытаемся attach'ить свой stencil renderbuffer к framebuffer игры.
        // Это и было самым опасным местом на NVIDIA.
        // Используем только уже существующий stencil, если он есть.
        return GL11.glGetInteger(GL11.GL_STENCIL_BITS) > 0;
    }

    public static void detach() {
        // Ничего не делаем специально.
    }

    public static void cleanup() {
        // Ничего не делаем специально.
    }
}