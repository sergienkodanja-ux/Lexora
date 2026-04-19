package com.lexoravisauls.client.events;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.utils.AttackManager;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TargetESPRenderer {

    private static final Identifier RHOMBUS_TEX = Identifier.of("lexoravisauls", "textures/gui/rhombus.png");
    private static final Identifier ROUND_RHOMBUS_TEX = Identifier.of("lexoravisauls", "textures/gui/round_rhombus.png");
    private static final Identifier BLOOM_TEX = Identifier.of("lexoravisauls", "textures/gui/bloom.png");

    private static final Map<UUID, Float> animProgress = new HashMap<>();
    private static final Map<UUID, Float> hurtAnimProgress = new HashMap<>();

    private static UUID lastTargetId = null;
    private static long lastTargetLostTime = 0;

    private static final List<CrystalData> crystalList = new ArrayList<>();

    static class CrystalData {
        final float relativeY;
        final float radius;
        final float sizeMult;
        final Vec3d positionOffset;
        final Vec3d rotation;
        final float rotationSpeed;

        CrystalData(float relativeY, float radius, float sizeMult, Vec3d positionOffset, Vec3d rotation) {
            this.relativeY = relativeY;
            this.radius = radius;
            this.sizeMult = sizeMult;
            this.positionOffset = positionOffset;
            this.rotation = rotation;
            this.rotationSpeed = 0.5f + (float)(Math.random() * 1.5f);
        }
    }

    public static void render(MatrixStack matrices, Camera camera, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        if (!LexoraGui.moduleStates.getOrDefault("Target ESP", false)) return;

        String style = LexoraGui.modeSettings.getOrDefault("Target ESP Mode", "Spirits");
        float speedSet = LexoraGui.numSettings.getOrDefault("ESP Speed", 1.0f);
        boolean redOnDamage = LexoraGui.moduleStates.getOrDefault("Red On Damage", true);
        boolean onlyOnCrit = LexoraGui.moduleStates.getOrDefault("Only On Crit", false);

        LivingEntity currentTarget = null;
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            Entity e = ((EntityHitResult) mc.crosshairTarget).getEntity();
            if (e instanceof LivingEntity && e != mc.player) {
                currentTarget = (LivingEntity) e;
                lastTargetId = currentTarget.getUuid();
                lastTargetLostTime = System.currentTimeMillis();
            }
        }

        Vec3d camPos = camera.getPos();
        float time = mc.player.age + tickDelta;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || entity == mc.player) continue;
            if (living.isInvisible()) continue;

            UUID id = living.getUuid();
            boolean isTarget = (living == currentTarget);
            boolean isRecent = (currentTarget == null && id.equals(lastTargetId) && (System.currentTimeMillis() - lastTargetLostTime) <= 1000);

            float step = 0.04f;
            float current = animProgress.getOrDefault(id, 0.0f);
            current = (isTarget || isRecent) ? Math.min(1.0f, current + step) : Math.max(0.0f, current - step);
            animProgress.put(id, current);

            if (current <= 0) {
                animProgress.remove(id);
                hurtAnimProgress.remove(id);
                continue;
            }

            float hurt = hurtAnimProgress.getOrDefault(id, 0.0f);
            boolean takingDamage = living.hurtTime > 0;

            if (redOnDamage && takingDamage) {
                if (!onlyOnCrit || AttackManager.isRecentCrit(living) || hurt > 0.2f) {
                    hurt = Math.min(1.0f, hurt + 0.35f);
                } else {
                    hurt = Math.max(0.0f, hurt - 0.05f);
                }
            } else {
                hurt = Math.max(0.0f, hurt - 0.05f);
            }
            hurtAnimProgress.put(id, hurt);

            float alphaMod = (current < 0.5f) ? 2 * current * current : -1 + (4 - 2 * current) * current;

            double x = MathHelper.lerp(tickDelta, living.prevX, living.getX()) - camPos.x;
            double y = MathHelper.lerp(tickDelta, living.prevY, living.getY()) - camPos.y;
            double z = MathHelper.lerp(tickDelta, living.prevZ, living.getZ()) - camPos.z;

            int themeColor = LexoraGui.getThemeColor(0);
            int baseColor = blendColors(themeColor | 0xFF000000, 0xFFFF0000, hurt);

            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE, GlStateManager.SrcFactor.ZERO, GlStateManager.DstFactor.ONE);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();

            if (style.contains("Rhombus")) {
                Identifier tex = style.equals("Rhombus") ? RHOMBUS_TEX : ROUND_RHOMBUS_TEX;
                RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
                matrices.push();
                matrices.translate(x, y + living.getHeight() / 2.0f, z);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

                float t = time * 0.025f * speedSet;
                float swingRot = MathHelper.sin(t) * 1080.0f;

                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(swingRot));

                float targetSize = LexoraGui.numSettings.getOrDefault("Rhombus Size", 1.0f) * 1.3f;
                float finalScale = Math.max(0.1f, (targetSize + ((1.0f - alphaMod) * 3.0f)) - (hurt * 0.35f));
                matrices.scale(finalScale, finalScale, finalScale);

                drawTex(matrices, tex, baseColor, alphaMod);
                matrices.pop();

            } else if (style.equals("Spirits")) {
                renderSpiritsMath(matrices, living, camera, tickDelta, camPos, alphaMod, baseColor, speedSet);
            } else if (style.equals("Crystals")) {
                renderCrystals(matrices, living, camera, tickDelta, camPos, alphaMod, baseColor, speedSet, hurt);
            }

            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.enableCull();
        }
    }

    private static void renderCrystals(MatrixStack ms, LivingEntity target, Camera camera, float tickDelta, Vec3d camPos, float anim, int baseColor, float speedSet, float hurt) {
        if (crystalList.isEmpty()) createCrystals();

        double tX = MathHelper.lerp(tickDelta, target.prevX, target.getX()) - camPos.x;
        double tY = MathHelper.lerp(tickDelta, target.prevY, target.getY()) - camPos.y;
        double tZ = MathHelper.lerp(tickDelta, target.prevZ, target.getZ()) - camPos.z;

        float timeSec = (System.currentTimeMillis() % 3600000) / 1000.0f;
        float globalRotation = timeSec * 36.0f * speedSet;

        ms.push();
        ms.translate(tX, tY + target.getHeight() / 2.0f, tZ);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(globalRotation));

        float baseSize = 0.08f;
        float targetHeight = target.getHeight();

        for (CrystalData crystal : crystalList) {
            ms.push();
            float realYOffset = (crystal.relativeY * targetHeight) - (targetHeight / 2.0f);
            ms.translate(crystal.positionOffset.x, realYOffset, crystal.positionOffset.z);

            float pulsation = (1.0f + (float) (Math.sin(System.currentTimeMillis() / 500.0) * 0.05f) - (hurt * 0.15f)) * crystal.sizeMult;
            ms.scale(pulsation, pulsation, pulsation);

            float selfRotation = timeSec * 100.0f * crystal.rotationSpeed;
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) crystal.rotation.x));
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) crystal.rotation.y + selfRotation));
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) crystal.rotation.z));

            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
            drawCrystalShape(ms, baseColor, 0.3f, true, anim, baseSize);

            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            drawCrystalShape(ms, baseColor, 0.6f, true, anim, baseSize);

            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
            RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
            RenderSystem.setShaderTexture(0, BLOOM_TEX);

            int bloomAlpha = (int) (0.5f * 255 * anim);
            float bloomSize = baseSize * 10.0f;
            float pitch = camera.getPitch();
            float yaw = camera.getYaw();

            for (int i = 0; i < 3; i++) {
                ms.push();
                ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((360.0f / 3) * i));
                ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
                ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));

                Matrix4f matrix = ms.peek().getPositionMatrix();
                BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                float aF = bloomAlpha / 255f;
                float rF = ((baseColor >> 16) & 0xFF) / 255f;
                float gF = ((baseColor >> 8) & 0xFF) / 255f;
                float bF = (baseColor & 0xFF) / 255f;

                buffer.vertex(matrix, -bloomSize / 2, -bloomSize / 2, 0).texture(0, 1).color(rF, gF, bF, aF);
                buffer.vertex(matrix, bloomSize / 2, -bloomSize / 2, 0).texture(1, 1).color(rF, gF, bF, aF);
                buffer.vertex(matrix, bloomSize / 2, bloomSize / 2, 0).texture(1, 0).color(rF, gF, bF, aF);
                buffer.vertex(matrix, -bloomSize / 2, bloomSize / 2, 0).texture(0, 0).color(rF, gF, bF, aF);
                BufferRenderer.drawWithGlobalProgram(buffer.end());
                ms.pop();
            }
            ms.pop();
        }
        ms.pop();
    }

    private static void createCrystals() {
        crystalList.clear();
        generateRing(0.5f, 0.85f, 1.0f, 8);
        generateRing(0.85f, 0.5f, 0.6f, 5);
        generateRing(0.15f, 0.5f, 0.6f, 5);
    }

    private static void generateRing(float relY, float radius, float sizeMult, int count) {
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI * i) / count;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Vec3d randomRotation = new Vec3d(Math.random() * 360, Math.random() * 360, Math.random() * 360);
            crystalList.add(new CrystalData(relY, radius, sizeMult, new Vec3d(x, 0, z), randomRotation));
        }
    }

    private static void drawCrystalShape(MatrixStack ms, int baseColor, float alphaMod, boolean filled, float anim, float size) {
        BufferBuilder buffer = Tessellator.getInstance().begin(
                filled ? VertexFormat.DrawMode.TRIANGLES : VertexFormat.DrawMode.DEBUG_LINES,
                VertexFormats.POSITION_COLOR
        );

        float h_prism = size * 1.2f;
        float h_pyramid = size * 2.0f;
        int numSides = 6;

        List<Vec3d> topV = new ArrayList<>();
        List<Vec3d> botV = new ArrayList<>();

        for (int i = 0; i < numSides; i++) {
            float angle = (float) (2 * Math.PI * i / numSides);
            float x = (float) (size * Math.cos(angle));
            float z = (float) (size * Math.sin(angle));
            topV.add(new Vec3d(x, h_prism / 2, z));
            botV.add(new Vec3d(x, -h_prism / 2, z));
        }

        Vec3d vTop = new Vec3d(0, h_prism / 2 + h_pyramid, 0);
        Vec3d vBottom = new Vec3d(0, -h_prism / 2 - h_pyramid, 0);

        float aF = (alphaMod * anim);
        float rF = ((baseColor >> 16) & 0xFF) / 255f;
        float gF = ((baseColor >> 8) & 0xFF) / 255f;
        float bF = (baseColor & 0xFF) / 255f;

        for (int i = 0; i < numSides; i++) {
            Vec3d v1 = botV.get(i);
            Vec3d v2 = botV.get((i + 1) % numSides);
            Vec3d v3 = topV.get((i + 1) % numSides);
            Vec3d v4 = topV.get(i);
            if (filled) {
                drawTriangle(ms, buffer, v1, v2, v3, rF, gF, bF, aF);
                drawTriangle(ms, buffer, v1, v3, v4, rF, gF, bF, aF);
            }
            if (filled) drawTriangle(ms, buffer, vTop, topV.get(i), topV.get((i + 1) % numSides), rF, gF, bF, aF);
            if (filled) drawTriangle(ms, buffer, vBottom, botV.get((i + 1) % numSides), botV.get(i), rF, gF, bF, aF);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static void drawTriangle(MatrixStack ms, BufferBuilder bb, Vec3d v1, Vec3d v2, Vec3d v3, float r, float g, float b, float a) {
        Matrix4f matrix = ms.peek().getPositionMatrix();
        bb.vertex(matrix, (float)v1.x, (float)v1.y, (float)v1.z).color(r, g, b, a);
        bb.vertex(matrix, (float)v2.x, (float)v2.y, (float)v2.z).color(r, g, b, a);
        bb.vertex(matrix, (float)v3.x, (float)v3.y, (float)v3.z).color(r, g, b, a);
    }

    private static void renderSpiritsMath(MatrixStack matrices, LivingEntity target, Camera camera, float tickDelta, Vec3d camPos, float anim, int baseColor, float speedSet) {
        double tX = MathHelper.lerp(tickDelta, target.prevX, target.getX()) - camPos.x;
        double tY = MathHelper.lerp(tickDelta, target.prevY, target.getY()) - camPos.y + target.getHeight() / 2.0D;
        double tZ = MathHelper.lerp(tickDelta, target.prevZ, target.getZ()) - camPos.z;

        int ghostCount = Math.round(LexoraGui.numSettings.getOrDefault("Spirits Count", 3.0f));
        int maxTrailSize = Math.round(LexoraGui.numSettings.getOrDefault("Trail Length", 15.0f));

        float timeSec = (System.currentTimeMillis() % 3600000) / 1000.0f;
        float timeParam = timeSec * speedSet * 3.0f;

        float radius = 1.0f;
        float verticalAmp = 0.5f;

        float baseSize = 0.25f;

        RenderSystem.setShaderTexture(0, BLOOM_TEX);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        for (int i = 0; i < ghostCount; i++) {
            float offsetAngle = i * ((float) Math.PI * 2f / ghostCount);

            for (int t = maxTrailSize; t >= 0; t--) {
                float trailDelay = t * 0.05f;
                float pastTime = timeParam - trailDelay;

                double orbitX = Math.sin(pastTime + offsetAngle) * radius * Math.cos(pastTime * 0.2);
                double orbitZ = Math.cos(pastTime + offsetAngle) * radius;
                double waveY = Math.sin(pastTime * 1.5 + offsetAngle) * verticalAmp;

                Vec3d pos = new Vec3d(tX + orbitX, tY + waveY, tZ + orbitZ);

                float trailFactor = 1.0f - ((float) t / Math.max(1, maxTrailSize));
                renderGhostPartMath(builder, matrices, camera, pos, trailFactor, anim, baseSize, baseColor);
            }
        }
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    private static void renderGhostPartMath(BufferBuilder builder, MatrixStack ms, Camera camera, Vec3d pos, float trailFactor, float anim, float baseSize, int color) {
        float size = baseSize * (trailFactor * trailFactor);

        float aF = (trailFactor * anim * 0.8f);
        float rF = ((color >> 16) & 0xFF) / 255f;
        float gF = ((color >> 8) & 0xFF) / 255f;
        float bF = (color & 0xFF) / 255f;

        ms.push();
        ms.translate(pos.x, pos.y, pos.z);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

        Matrix4f mat = ms.peek().getPositionMatrix();
        builder.vertex(mat, -size, -size, 0).texture(0, 1).color(rF, gF, bF, aF);
        builder.vertex(mat, size, -size, 0).texture(1, 1).color(rF, gF, bF, aF);
        builder.vertex(mat, size, size, 0).texture(1, 0).color(rF, gF, bF, aF);
        builder.vertex(mat, -size, size, 0).texture(0, 0).color(rF, gF, bF, aF);
        ms.pop();
    }

    private static int blendColors(int c1, int c2, float r) {
        int a1 = (c1 >> 24) & 0xFF; int r1 = (c1 >> 16) & 0xFF; int g1 = (c1 >> 8) & 0xFF; int b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF; int r2 = (c2 >> 16) & 0xFF; int g2 = (c2 >> 8) & 0xFF; int b2 = c2 & 0xFF;
        return ((int)(a1 + (a2 - a1) * r) << 24) | ((int)(r1 + (r2 - r1) * r) << 16) | ((int)(g1 + (g2 - g1) * r) << 8) | (int)(b1 + (b2 - b1) * r);
    }

    private static void drawTex(MatrixStack ms, Identifier tex, int color, float a) {
        RenderSystem.setShaderTexture(0, tex);
        Matrix4f mat = ms.peek().getPositionMatrix();
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        float aF = a;
        float rF = ((color >> 16) & 0xFF) / 255.0F;
        float gF = ((color >> 8) & 0xFF) / 255.0F;
        float bF = (color & 0xFF) / 255.0F;

        bb.vertex(mat, -0.5f, -0.5f, 0).texture(0, 1).color(rF, gF, bF, aF);
        bb.vertex(mat, 0.5f, -0.5f, 0).texture(1, 1).color(rF, gF, bF, aF);
        bb.vertex(mat, 0.5f, 0.5f, 0).texture(1, 0).color(rF, gF, bF, aF);
        bb.vertex(mat, -0.5f, 0.5f, 0).texture(0, 0).color(rF, gF, bF, aF);
        BufferRenderer.drawWithGlobalProgram(bb.end());
    }
}