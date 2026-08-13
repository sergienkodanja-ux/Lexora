package com.lexoravisauls.client.gui.modern;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.CompiledShader;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MirageGlassShader {
    public static ShaderProgram program;

    public static void register() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> loadShader(client));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (program != null) {
                program.close();
                program = null;
            }
        });
    }

    private static void loadShader(MinecraftClient client) {
        try {
            ResourceManager rm = client.getResourceManager();

            String vertSrc = readResource(rm, Identifier.of("lexoravisauls", "shaders/core/mirage_glass_vertex.vsh"));
            String fragSrc = readResource(rm, Identifier.of("lexoravisauls", "shaders/core/mirage_glass_fragment.fsh"));

            CompiledShader vert = CompiledShader.compile(
                    Identifier.of("lexoravisauls", "mirage_glass_vertex"),
                    CompiledShader.Type.VERTEX,
                    vertSrc
            );

            CompiledShader frag = CompiledShader.compile(
                    Identifier.of("lexoravisauls", "mirage_glass_fragment"),
                    CompiledShader.Type.FRAGMENT,
                    fragSrc
            );

            program = ShaderProgram.create(vert, frag, VertexFormats.POSITION);

            // Регистрируем uniforms из json вручную, добавив u_alpha
            program.set(
                    java.util.List.of(
                            new net.minecraft.client.gl.ShaderProgramDefinition.Uniform("ModelViewMat", "matrix4x4", 16, java.util.List.of(1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f)),
                            new net.minecraft.client.gl.ShaderProgramDefinition.Uniform("ProjMat",      "matrix4x4", 16, java.util.List.of(1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f)),
                            new net.minecraft.client.gl.ShaderProgramDefinition.Uniform("u_rect",   "float", 4, java.util.List.of(0f,0f,0f,0f)),
                            new net.minecraft.client.gl.ShaderProgramDefinition.Uniform("u_radius", "float", 1, java.util.List.of(0f)),
                            new net.minecraft.client.gl.ShaderProgramDefinition.Uniform("u_blur",   "float", 1, java.util.List.of(15f)),
                            new net.minecraft.client.gl.ShaderProgramDefinition.Uniform("u_tint",   "float", 4, java.util.List.of(1f,1f,1f,1f)),
                            new net.minecraft.client.gl.ShaderProgramDefinition.Uniform("u_alpha",  "float", 1, java.util.List.of(1f)) // <--- ФИКС ЗДЕСЬ
                    ),
                    java.util.List.of(
                            new net.minecraft.client.gl.ShaderProgramDefinition.Sampler("Sampler0")
                    )
            );

        } catch (Exception e) {
            System.err.println("[MirageGlass] Шейдер не загрузился: " + e.getMessage());
            e.printStackTrace();
            program = null;
        }
    }

    private static String readResource(ResourceManager rm, Identifier id) throws Exception {
        Resource resource = rm.getResourceOrThrow(id);
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}