package com.lexoravisauls.client.events;

import com.lexoravisauls.client.gui.HudManager;
import com.lexoravisauls.client.gui.LexoraGui;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class TargetHudRenderer {

    public static final int WIDTH = 160;
    public static final int HEIGHT = 50;

    private static final Identifier WHODARK_TEX = Identifier.of("lexoravisauls", "textures/gui/whodark.png");
    private static final Identifier TARGET_ICON = Identifier.of("lexoravisauls", "textures/gui/target_icon.png");
    private static final Identifier STAR_TEX = Identifier.of("lexoravisauls", "textures/particle/star1.png");
    private static final Identifier CUSTOM_FONT = Identifier.of("lexoravisauls", "sfui");

    private static LivingEntity target = null;
    private static long lastHitTime = 0;
    private static float animAlpha = 0f;
    private static float animScale = 0.8f;
    private static float healthAnim = 0f;
    private static float absorbAnim = 0f;

    private static LivingEntity lastDisplayTarget = null;
    private static final Map<EquipmentSlot, Float> slotAlphas = new HashMap<>();
    private static final Map<EquipmentSlot, Float> slotXs = new HashMap<>();
    private static final Map<EquipmentSlot, ItemStack> slotStacks = new HashMap<>();

    private static final Random RANDOM = new Random();
    private static final List<StarParticle> starParticles = new ArrayList<>();
    private static int lastHurtTrackedTargetId = Integer.MIN_VALUE;
    private static int lastObservedHurtTime = 0;
    private static long lastStarUpdateMs = 0L;

    private static class StarParticle {
        float x;
        float y;
        float vx;
        float vy;
        float size;
        float life;
        float maxLife;
        float angle;
        float spin;

        StarParticle(float x, float y, float vx, float vy, float size, float life, float angle, float spin) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.size = size;
            this.life = life;
            this.maxLife = life;
            this.angle = angle;
            this.spin = spin;
        }
    }

    public static void render(DrawContext context, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.options.hudHidden || !LexoraGui.moduleStates.getOrDefault("Target HUD", true)) return;

        int x = HudManager.targetX == -1 ? 150 : HudManager.targetX;
        int y = HudManager.targetY == -1 ? 150 : HudManager.targetY;

        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult) mc.crosshairTarget).getEntity();
            if (entity instanceof LivingEntity && entity != mc.player) {
                target = (LivingEntity) entity;
                lastHitTime = System.currentTimeMillis();
            }
        }

        boolean showPreview = mc.currentScreen instanceof LexoraGui || mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen;
        boolean isValid = target != null && target.isAlive() && !target.isInvisible() && target.distanceTo(mc.player) < 30 && (System.currentTimeMillis() - lastHitTime < 100);
        boolean shouldShow = isValid || showPreview;

        float smoothSpeed = 0.15f;
        animAlpha += ((shouldShow ? 1.0f : 0.0f) - animAlpha) * smoothSpeed;

        float menuScale = LexoraGui.numSettings.getOrDefault("Target HUD Scale", 1.0f);
        float targetAnimScale = shouldShow ? menuScale : (menuScale - 0.2f);
        animScale += (targetAnimScale - animScale) * smoothSpeed;

        if (animAlpha < 0.02f) return;

        int textAlpha = (int) (animAlpha * 255);
        float hurtPercent = 0.0f;
        if (isValid && target != null) hurtPercent = target.hurtTime / 10.0f;

        RenderSystem.enableDepthTest();

        MatrixStack ms = context.getMatrices();
        ms.push();
        ms.translate(0, 0, -150);

        float centerX = x + WIDTH / 2f;
        float centerY = y + HEIGHT / 2f;
        ms.push();
        ms.translate(centerX, centerY, 0);
        ms.scale(animScale, animScale, 1.0f);
        ms.translate(-centerX, -centerY, 0);

        boolean isSolid = LexoraGui.moduleStates.getOrDefault("Target HUD Solid", false);
        boolean showDamageTint = LexoraGui.moduleStates.getOrDefault("Damage Tint", true);

        int baseBgAlpha = isSolid ? 255 : 200;
        int bgAlpha = (int)(animAlpha * baseBgAlpha);
        int bgColor = (bgAlpha << 24) | 0x101015;

        drawSmoothRect(context, x, y, WIDTH, HEIGHT, bgColor);

        ms.push();
        ms.translate(0, 0, 10);

        String name = "No Target";
        float healthPct = 1.0f, absorbPct = 0.0f, displayHp = 20.0f;
        Identifier faceTex = WHODARK_TEX;
        boolean isPlayer = false;

        LivingEntity displayEntity = null;

        if (isValid && target != null) {
            displayEntity = target;

            name = target.getName().getString();
            float hp = target.getHealth();
            float maxHp = target.getMaxHealth();
            float absorb = target.getAbsorptionAmount();
            displayHp = hp + absorb;
            healthPct = MathHelper.clamp(hp / maxHp, 0, 1);
            absorbPct = MathHelper.clamp(absorb / maxHp, 0, 1);

            if (target instanceof AbstractClientPlayerEntity player) {
                faceTex = player.getSkinTextures().texture();
                isPlayer = true;
            }
        } else if (showPreview && mc.player != null) {
            displayEntity = mc.player;

            name = mc.player.getName().getString();
            float hp = mc.player.getHealth();
            float maxHp = mc.player.getMaxHealth();
            float absorb = mc.player.getAbsorptionAmount();
            displayHp = hp + absorb;
            healthPct = MathHelper.clamp(hp / maxHp, 0, 1);
            absorbPct = MathHelper.clamp(absorb / maxHp, 0, 1);
            faceTex = mc.player.getSkinTextures().texture();
            isPlayer = true;
        }

        boolean streamerMode = LexoraGui.moduleStates.getOrDefault("Streamer Mode", false);
        boolean hideName = streamerMode && LexoraGui.moduleStates.getOrDefault("Hide Name", true);

        if (hideName && mc.getSession() != null) {
            String myName = mc.getSession().getUsername();
            if (name.contains(myName)) name = name.replace(myName, "Protected");
        }

        healthAnim += (healthPct - healthAnim) * 0.2f;
        absorbAnim += (absorbPct - absorbAnim) * 0.2f;

        int faceSize = 36;
        int faceX = x + 7;
        int faceY = y + 7;
        int baseColor = (textAlpha << 24) | 0xFFFFFF;

        if (isPlayer) {
            drawTexQuad(context, faceTex, faceX, faceY, faceSize, faceSize, 8 / 64f, 8 / 64f, 16 / 64f, 16 / 64f, baseColor);
            drawTexQuad(context, faceTex, faceX, faceY, faceSize, faceSize, 40 / 64f, 8 / 64f, 48 / 64f, 16 / 64f, baseColor);

            if (showDamageTint && hurtPercent > 0.05f) {
                int hurtAlpha = (int) (hurtPercent * 120 * animAlpha);
                int hurtColor = (hurtAlpha << 24) | 0xFF0000;
                drawTexQuad(context, faceTex, faceX, faceY, faceSize, faceSize, 8 / 64f, 8 / 64f, 16 / 64f, 16 / 64f, hurtColor);
                drawTexQuad(context, faceTex, faceX, faceY, faceSize, faceSize, 40 / 64f, 8 / 64f, 48 / 64f, 16 / 64f, hurtColor);
            }
        } else {
            drawTexQuad(context, faceTex, faceX, faceY, faceSize, faceSize, 0, 0, 1, 1, baseColor);
            if (showDamageTint && hurtPercent > 0.05f) {
                int hurtAlpha = (int) (hurtPercent * 120 * animAlpha);
                int hurtColor = (hurtAlpha << 24) | 0xFF0000;
                drawTexQuad(context, faceTex, faceX, faceY, faceSize, faceSize, 0, 0, 1, 1, hurtColor);
            }
        }

        updateHitStars(displayEntity, faceX, faceY, faceSize, showDamageTint);
        renderHitStars(context, textAlpha);

        drawTexQuad(context, TARGET_ICON, x + 4, y + 4, 12, 12, 0, 0, 1, 1, baseColor);

        long time = System.currentTimeMillis();
        float wave = (float) (Math.sin(time / 400.0) * 0.5 + 0.5);
        int colorVal = (int) (150 + 80 * wave);
        int shimmerRGB = (colorVal << 16) | (colorVal << 8) | colorVal;
        int shimmerColorWithAlpha = (textAlpha << 24) | shimmerRGB;

        TextRenderer tr = mc.textRenderer;
        int textStartX = faceX + faceSize + 8;

        context.drawText(tr, Text.literal(name).setStyle(Style.EMPTY.withFont(CUSTOM_FONT)), textStartX, y + 8, shimmerColorWithAlpha, false);

        String hpString = String.format("%.1f", displayHp).replace(",", ".");
        Text hpLabel = Text.literal("HP: ").setStyle(Style.EMPTY.withFont(CUSTOM_FONT));
        Text hpValue = Text.literal(hpString).setStyle(Style.EMPTY.withFont(CUSTOM_FONT));

        int hpTextColor = 0xFF44FF44;
        if (healthPct <= 0.6f) hpTextColor = 0xFFFFFF00;
        if (healthPct <= 0.3f) hpTextColor = 0xFFFF4444;
        hpTextColor = (textAlpha << 24) | (hpTextColor & 0xFFFFFF);

        context.drawText(tr, hpLabel, textStartX, y + 20, shimmerColorWithAlpha, false);
        context.drawText(tr, hpValue, textStartX + tr.getWidth(hpLabel), y + 20, hpTextColor, false);

        int barX = textStartX;
        int barY = y + 33;
        int barW = WIDTH - faceSize - 22;
        int barH = 6;
        context.fill(barX, barY, barX + barW, barY + barH, (textAlpha << 24) | 0x222222);
        context.fill(barX, barY, barX + (int)(barW * healthAnim), barY + barH, hpTextColor);
        if (absorbAnim > 0.01f) {
            context.fill(barX, barY, barX + (int)(barW * absorbAnim), barY + barH, (textAlpha << 24) | 0xFFCC00);
        }

        if (displayEntity != null) {
            if (displayEntity != lastDisplayTarget) {
                slotAlphas.clear();
                slotXs.clear();
                slotStacks.clear();
                lastDisplayTarget = displayEntity;
            }

            EquipmentSlot[] slots = {
                    EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND
            };

            int boxSize = 14;
            int gap = 3;

            float totalActiveW = 0;
            for (EquipmentSlot slot : slots) {
                ItemStack stack = displayEntity.getEquippedStack(slot);
                boolean hasItem = stack != null && !stack.isEmpty();

                if (hasItem) slotStacks.put(slot, stack.copy());

                float targetScaleAlpha = hasItem ? 1.0f : 0.0f;
                float currentScaleAlpha = slotAlphas.getOrDefault(slot, 0.0f);

                currentScaleAlpha += (targetScaleAlpha - currentScaleAlpha) * 0.15f;
                slotAlphas.put(slot, currentScaleAlpha);

                totalActiveW += (boxSize + gap) * currentScaleAlpha;
            }
            if (totalActiveW > 0) totalActiveW -= gap;

            float startEqX = x + (WIDTH - totalActiveW) / 2f;
            float currentRunX = startEqX;
            int eqY = y + HEIGHT + 5;

            for (EquipmentSlot slot : slots) {
                float alphaScale = slotAlphas.getOrDefault(slot, 0.0f);
                if (alphaScale < 0.01f) continue;

                float targetXForSlot = currentRunX;
                float currentAnimX = slotXs.getOrDefault(slot, targetXForSlot);

                currentAnimX += (targetXForSlot - currentAnimX) * 0.2f;
                slotXs.put(slot, currentAnimX);

                currentRunX += (boxSize + gap) * alphaScale;

                float slotCenterX = currentAnimX + boxSize / 2f;
                float slotCenterY = eqY + boxSize / 2f;

                ms.push();
                ms.translate(slotCenterX, slotCenterY, 0);
                ms.scale(alphaScale, alphaScale, 1.0f);

                drawSmoothRect(context, (int)(-boxSize / 2f), (int)(-boxSize / 2f), boxSize, boxSize, bgColor);

                ItemStack stackToDraw = slotStacks.get(slot);
                if (stackToDraw != null && !stackToDraw.isEmpty()) {
                    ms.push();
                    float itemScale = 0.65f;
                    ms.scale(itemScale, itemScale, 1.0f);

                    context.drawItem(stackToDraw, -8, -8);

                    if (stackToDraw.getCount() > 1) {
                        ms.translate(0, 0, 200);
                        String countStr = String.valueOf(stackToDraw.getCount());
                        float scaleCount = 0.8f;
                        ms.scale(scaleCount, scaleCount, 1.0f);
                        int strW = tr.getWidth(countStr);
                        context.drawText(
                                tr,
                                Text.literal(countStr).setStyle(Style.EMPTY.withFont(CUSTOM_FONT)),
                                (int)(8 / scaleCount - strW),
                                (int)(8 / scaleCount - 7),
                                0xFFFFFFFF,
                                true
                        );
                    }
                    ms.pop();
                }
                ms.pop();
            }
        } else {
            lastDisplayTarget = null;
        }

        context.draw();
        ms.pop();
        ms.pop();
        ms.pop();

        RenderSystem.enableDepthTest();
    }

    private static void updateHitStars(LivingEntity entity, float faceX, float faceY, int faceSize, boolean damageTintEnabled) {
        long now = System.currentTimeMillis();
        if (lastStarUpdateMs == 0L) lastStarUpdateMs = now;

        float dt = Math.min(3.0f, (now - lastStarUpdateMs) / 16.6667f);
        lastStarUpdateMs = now;

        for (int i = starParticles.size() - 1; i >= 0; i--) {
            StarParticle p = starParticles.get(i);
            p.x += p.vx * dt;
            p.y += p.vy * dt;

            p.vx *= Math.pow(0.965f, dt);
            p.vy = (float)(p.vy * Math.pow(0.975f, dt) - 0.0065f * dt);

            p.angle += p.spin * dt;
            p.spin *= Math.pow(0.992f, dt);

            p.life -= dt * 0.78f;

            if (p.life <= 0.0f) {
                starParticles.remove(i);
            }
        }

        int entityId = entity != null ? entity.getId() : Integer.MIN_VALUE;
        int hurtTimeNow = entity != null ? entity.hurtTime : 0;

        if (entityId != lastHurtTrackedTargetId) {
            lastHurtTrackedTargetId = entityId;
            lastObservedHurtTime = hurtTimeNow;
            return;
        }

        if (damageTintEnabled && entity != null && hurtTimeNow > 0 && hurtTimeNow > lastObservedHurtTime) {
            spawnHitStars(faceX, faceY, faceSize, 8 + RANDOM.nextInt(4));
        }

        lastObservedHurtTime = hurtTimeNow;
    }

    private static void spawnHitStars(float faceX, float faceY, int faceSize, int count) {
        float centerX = faceX + faceSize / 2.0f;
        float centerY = faceY + faceSize / 2.0f;

        for (int i = 0; i < count; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0;
            float spawnRadius = 7.0f + RANDOM.nextFloat() * 9.0f;

            float px = centerX + (float)Math.cos(angle) * spawnRadius;
            float py = centerY + (float)Math.sin(angle) * spawnRadius;

            float speed = 0.75f + RANDOM.nextFloat() * 1.15f;
            float vx = (float)Math.cos(angle) * speed;
            float vy = (float)Math.sin(angle) * speed - 0.12f - RANDOM.nextFloat() * 0.12f;

            float size = 5.6f + RANDOM.nextFloat() * 4.2f;
            float life = 18.0f + RANDOM.nextFloat() * 10.0f;

            float startAngle = RANDOM.nextFloat() * ((float)Math.PI * 2.0f);
            float spin = (RANDOM.nextFloat() * 0.24f + 0.08f) * (RANDOM.nextBoolean() ? 1.0f : -1.0f);

            starParticles.add(new StarParticle(px, py, vx, vy, size, life, startAngle, spin));
        }
    }

    private static void renderHitStars(DrawContext context, int baseAlpha) {
        for (StarParticle p : starParticles) {
            float lifeFactor = MathHelper.clamp(p.life / p.maxLife, 0.0f, 1.0f);
            float fade = (float)Math.pow(lifeFactor, 0.7f);

            int alpha = (int)(baseAlpha * fade * 1.18f);
            alpha = Math.min(alpha, 255);

            if (alpha <= 4) continue;

            int color = (alpha << 24) | (255 << 16) | (245 << 8) | 170;

            drawRotatedTexQuad(
                    context,
                    STAR_TEX,
                    p.x - p.size / 2.0f,
                    p.y - p.size / 2.0f,
                    p.size,
                    p.size,
                    0.0f, 0.0f, 1.0f, 1.0f,
                    color,
                    p.angle
            );
        }
    }

    public static void drawSmoothRect(DrawContext context, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;

        float radius = Math.min(6.0f, Math.min(width / 2.0f, height / 2.0f));
        if (width < 12 || height < 12) {
            radius = Math.min(width, height) / 2.0f;
        }

        RoundedRectShader.draw(context, x, y, width, height, radius, color);
    }

    public static void drawTexQuad(DrawContext context, Identifier texture, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int color) {
        if (((color >> 24) & 0xFF) <= 5) return;
        context.draw();

        float a = ((color >> 24) & 0xFF) / 255.0F;
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        AbstractTexture tex = MinecraftClient.getInstance().getTextureManager().getTexture(texture);
        if (tex != null) {
            tex.bindTexture();
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        }

        RenderSystem.setShader(net.minecraft.client.gl.ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();

        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, x, y, 0.0F).texture(u0, v0).color(r, g, b, a);
        buffer.vertex(matrix, x, y + h, 0.0F).texture(u0, v1).color(r, g, b, a);
        buffer.vertex(matrix, x + w, y + h, 0.0F).texture(u1, v1).color(r, g, b, a);
        buffer.vertex(matrix, x + w, y, 0.0F).texture(u1, v0).color(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void drawRotatedTexQuad(DrawContext context, Identifier texture, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int color, float angle) {
        context.getMatrices().push();
        context.getMatrices().translate(x + w / 2.0f, y + h / 2.0f, 0.0f);
        context.getMatrices().multiply(new Quaternionf().rotateZ(angle));
        context.getMatrices().translate(-w / 2.0f, -h / 2.0f, 0.0f);
        drawTexQuad(context, texture, 0.0f, 0.0f, w, h, u0, v0, u1, v1, color);
        context.getMatrices().pop();
    }
}