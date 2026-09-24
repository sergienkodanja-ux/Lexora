package com.lexoravisauls.client.events;

import com.lexoravisauls.client.gui.LexoraGui;
import com.lexoravisauls.client.utils.AttackManager;
import com.lexoravisauls.client.utils.CrystalRenderer;
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
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TargetESPRenderer {

    private static final Identifier RHOMBUS_TEX = Identifier.of("lexoravisauls", "textures/gui/rhombus.png");
    private static final Identifier ROUND_RHOMBUS_TEX = Identifier.of("lexoravisauls", "textures/gui/round_rhombus.png");
    private static final Identifier BLOOM_TEX = Identifier.of("lexoravisauls", "textures/gui/bloom.png");

    private static final Identifier SKULL_0_TEX = Identifier.of("lexoravisauls", "textures/gui/skull_0.png");
    private static final Identifier SKULL_1_TEX = Identifier.of("lexoravisauls", "textures/gui/skull_1.png");
    private static final Identifier SKULL_2_TEX = Identifier.of("lexoravisauls", "textures/gui/skull_2.png");
    private static final Map<UUID, Float> animProgress = new HashMap<>();
    private static final Map<UUID, Float> hurtAnimProgress = new HashMap<>();

    private static UUID lastTargetId = null;
    private static long lastTargetLostTime = 0L;

    private static class CoolSoulState {
        final Vec3d[] pos = new Vec3d[3];
        final Vec3d[] vel = new Vec3d[3];
        final List<Vec3d>[] trails = new List[3];
        long lastUpdate = 0;

        CoolSoulState() {
            for (int i = 0; i < 3; i++) {
                vel[i] = Vec3d.ZERO;
                trails[i] = new ArrayList<>();
            }
        }

        void reset() {
            for (int i = 0; i < 3; i++) {
                pos[i] = null;
                vel[i] = Vec3d.ZERO;
                trails[i].clear();
            }
            lastUpdate = 0;
        }
    }

    private static class Bolt {
        final List<Vec3d> points;
        final List<List<Vec3d>> branches;
        final long spawn;
        final long life;
        final float phase;

        Bolt(List<Vec3d> points, List<List<Vec3d>> branches, long life, float phase) {
            this.points = points;
            this.branches = branches;
            this.spawn = System.currentTimeMillis();
            this.life = life;
            this.phase = phase;
        }
    }

    private static final Map<UUID, CoolSoulState> coolSoulStates = new HashMap<>();
    private static final Map<UUID, List<Bolt>> targetBolts = new HashMap<>();
    private static final Map<UUID, Long> lastBoltSpawnMap = new HashMap<>();

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
            if (e instanceof LivingEntity living && e != mc.player) {
                currentTarget = living;
                lastTargetId = living.getUuid();
                lastTargetLostTime = System.currentTimeMillis();
            }
        }

        Vec3d camPos = camera.getPos();
        float time = mc.player.age + tickDelta;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || entity == mc.player) continue;
            if (living.isInvisible()) continue;

            UUID id = living.getUuid();
            boolean isTarget = living == currentTarget;
            boolean isRecent = currentTarget == null
                    && id.equals(lastTargetId)
                    && (System.currentTimeMillis() - lastTargetLostTime) <= 1000L;

            float current = animProgress.getOrDefault(id, 0.0f);
            float step = 0.04f;
            current = (isTarget || isRecent)
                    ? Math.min(1.0f, current + step)
                    : Math.max(0.0f, current - step);
            animProgress.put(id, current);

            if (current <= 0.0f) {
                animProgress.remove(id);
                hurtAnimProgress.remove(id);
                coolSoulStates.remove(id);
                targetBolts.remove(id);
                lastBoltSpawnMap.remove(id);
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

            float alphaMod = ease(current);
            int themeColor = LexoraGui.getThemeColor(0);
            int baseColor = blendColors(themeColor | 0xFF000000, 0xFFFF0000, hurt);

            double x = MathHelper.lerp(tickDelta, living.prevX, living.getX()) - camPos.x;
            double y = MathHelper.lerp(tickDelta, living.prevY, living.getY()) - camPos.y;
            double z = MathHelper.lerp(tickDelta, living.prevZ, living.getZ()) - camPos.z;

            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                    GlStateManager.SrcFactor.SRC_ALPHA,
                    GlStateManager.DstFactor.ONE,
                    GlStateManager.SrcFactor.ZERO,
                    GlStateManager.DstFactor.ONE
            );
            if (style.equals("Spirits")) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();

            if (style.contains("Rhombus")) {
                Identifier tex = style.equals("Rhombus") ? RHOMBUS_TEX : ROUND_RHOMBUS_TEX;
                renderRhombus(matrices, camera, x, y, z, living, tex, baseColor, alphaMod, time, speedSet, hurt);
            } else if (style.equals("Spirits") || style.equals("Крутые Души")) {
                renderCoolSouls(matrices, camera, tickDelta, camPos, living, baseColor, alphaMod, speedSet);
            } else if (style.equals("Lightning") || style.equals("Молнии")) {
                renderLightning(matrices, camera, tickDelta, camPos, living, baseColor, alphaMod, hurt);
            } else if (style.equals("Crystals") || style.equals("Кристаллы")) {
                renderCrystals(matrices, camera, tickDelta, camPos, living, baseColor, alphaMod, speedSet, hurt);
            } else if (style.equals("Circle")) {
                renderJello(matrices, camera, tickDelta, camPos, living, baseColor, alphaMod, speedSet, hurt);
            } else if (style.equals("Skull")) {
                renderSkull(matrices, camera, tickDelta, camPos, living, alphaMod, speedSet, hurt);
            }

            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.blendFunc(
                    GlStateManager.SrcFactor.SRC_ALPHA,
                    GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA
            );
            RenderSystem.enableCull();
        }
    }

    private static void renderRhombus(MatrixStack matrices,
                                      Camera camera,
                                      double x,
                                      double y,
                                      double z,
                                      LivingEntity living,
                                      Identifier tex,
                                      int baseColor,
                                      float alphaMod,
                                      float time,
                                      float speedSet,
                                      float hurt) {
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
        drawTexturedQuad(matrices, tex, baseColor, alphaMod, 1.0f);
        matrices.pop();
    }

    private static void renderCoolSouls(MatrixStack matrices,
                                        Camera camera,
                                        float tickDelta,
                                        Vec3d cameraPos,
                                        LivingEntity renderTarget,
                                        int baseColor,
                                        float alphaProgress,
                                        float speedSet) {
        if (alphaProgress <= 0.001f) {
            CoolSoulState state = coolSoulStates.get(renderTarget.getUuid());
            if (state != null) state.reset();
            return;
        }

        CoolSoulState state = coolSoulStates.computeIfAbsent(renderTarget.getUuid(), k -> new CoolSoulState());

        long now = System.currentTimeMillis();
        if (state.lastUpdate == 0) state.lastUpdate = now;
        float dt = (now - state.lastUpdate) / 1000f;
        state.lastUpdate = now;
        if (dt > 0.1f) dt = 0.1f;
        if (dt <= 0) dt = 0.001f;

        Vec3d feetPos = new Vec3d(
                MathHelper.lerp(tickDelta, renderTarget.prevX, renderTarget.getX()),
                MathHelper.lerp(tickDelta, renderTarget.prevY, renderTarget.getY()),
                MathHelper.lerp(tickDelta, renderTarget.prevZ, renderTarget.getZ())
        );
        float entityHeight = renderTarget.getHeight();

        float speed = Math.max(0.1f, speedSet);
        float radiusMultiplier = LexoraGui.numSettings.getOrDefault("Cool Soul Radius", 1.15f);
        int trailLength = Math.max(5, Math.round(LexoraGui.numSettings.getOrDefault("Cool Soul Trail Length", 25.0f)));
        float glowWidth = LexoraGui.numSettings.getOrDefault("Cool Soul Glow Width", 0.7f);

        float baseRadius = renderTarget.getWidth() * radiusMultiplier;

        // Непрерывный 4-фазный цикл высоты с нулевой производной на границах
        float moveUpDuration = 1200f / speed;
        float topSpinDuration = 800f / speed;
        float moveDownDuration = 1200f / speed;
        float bottomSpinDuration = 800f / speed;
        float totalCycleDuration = moveUpDuration + topSpinDuration + moveDownDuration + bottomSpinDuration;

        float cycleTime = (now % (long) totalCycleDuration);

        double heightProgress = 0.0;
        if (cycleTime < moveUpDuration) {
            float progress = cycleTime / moveUpDuration;
            heightProgress = (1.0 - Math.cos(Math.PI * progress)) * 0.5;
        } else if (cycleTime < moveUpDuration + topSpinDuration) {
            heightProgress = 1.0;
        } else if (cycleTime < moveUpDuration + topSpinDuration + moveDownDuration) {
            float progress = (cycleTime - moveUpDuration - topSpinDuration) / moveDownDuration;
            heightProgress = (1.0 + Math.cos(Math.PI * progress)) * 0.5;
        } else {
            heightProgress = 0.0;
        }

        float pitch = camera.getPitch();
        float yaw = camera.getYaw();
        double time = (now % 100000L) / 1000.0;

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, BLOOM_TEX);

        // Непрерывное и монотонное вращение вокруг цели без угловых скачков
        double baseOrbit = (now / 1000.0) * 180.0 * speed;

        for (int j = 0; j < 3; j++) {
            double totalAngle = j * 120.0 + baseOrbit;
            double radians = Math.toRadians(totalAngle);

            float dynR = baseRadius * (0.95f + 0.12f * (float) Math.sin(time * 0.8 + j));
            double tx = Math.cos(radians) * dynR;
            double tz = Math.sin(radians) * dynR;
            double ty = heightProgress * entityHeight;

            Vec3d targetWorldPos = feetPos.add(tx, ty, tz);

            if (state.pos[j] == null || state.pos[j].distanceTo(targetWorldPos) > 10.0) {
                state.pos[j] = targetWorldPos;
                state.vel[j] = Vec3d.ZERO;
            }

            Vec3d diff = targetWorldPos.subtract(state.pos[j]);
            double smoothFactor = 1.0 - Math.exp(-14.0 * dt);
            state.pos[j] = state.pos[j].add(diff.multiply(smoothFactor));

            List<Vec3d> trail = state.trails[j];
            if (trail.isEmpty() || trail.get(0).distanceTo(state.pos[j]) > 0.015) {
                trail.add(0, state.pos[j]);
                while (trail.size() > trailLength) {
                    trail.remove(trail.size() - 1);
                }
            }

            // Рендер trail частиц
            int trailSize = trail.size();
            for (int i = 0; i < trailSize; i++) {
                Vec3d p = trail.get(i);
                float offset = MathHelper.clamp(1.0f - (float) i / Math.max(1, trailLength), 0.0f, 1.0f);
                float opacity = (float) Math.pow(offset, 1.5) * 0.85f * alphaProgress;
                if (opacity <= 0.01f) continue;

                int trailColor = withAlpha(baseColor, (int) (opacity * 255));
                float scale = Math.max(0.28f * offset, 0.15f) * 0.65f * glowWidth;

                matrices.push();
                matrices.translate(p.x - cameraPos.x, p.y - cameraPos.y, p.z - cameraPos.z);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));

                Matrix4f mat = matrices.peek().getPositionMatrix();
                BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

                float r = ((trailColor >> 16) & 0xFF) / 255f;
                float g = ((trailColor >> 8) & 0xFF) / 255f;
                float b = (trailColor & 0xFF) / 255f;
                float a = ((trailColor >> 24) & 0xFF) / 255f;

                buffer.vertex(mat, -scale, scale, 0).texture(0f, 1f).color(r, g, b, a);
                buffer.vertex(mat, scale, scale, 0).texture(1f, 1f).color(r, g, b, a);
                buffer.vertex(mat, scale, -scale, 0).texture(1f, 0f).color(r, g, b, a);
                buffer.vertex(mat, -scale, -scale, 0).texture(0f, 0f).color(r, g, b, a);

                BufferRenderer.drawWithGlobalProgram(buffer.end());
                matrices.pop();
            }
        }
    }

    private static double easeInOutSine(double x) {
        return -(Math.cos(Math.PI * x) - 1.0) / 2.0;
    }

    private static void renderLightning(MatrixStack matrices,
                                        Camera camera,
                                        float tickDelta,
                                        Vec3d camPos,
                                        LivingEntity target,
                                        int baseColor,
                                        float alphaMod,
                                        float hurt) {
        UUID id = target.getUuid();
        long now = System.currentTimeMillis();

        float countSetting = LexoraGui.numSettings.getOrDefault("Lightning Count", 3.0f);
        float widthSetting = LexoraGui.numSettings.getOrDefault("Lightning Width", 1.0f);

        int maxBolts = Math.max(4, Math.round(countSetting) * 2);
        List<Bolt> bolts = targetBolts.computeIfAbsent(id, k -> new ArrayList<>());
        long lastSpawn = lastBoltSpawnMap.getOrDefault(id, 0L);

        if (target.isAlive() && now - lastSpawn > 70 && bolts.size() < maxBolts) {
            bolts.add(spawnBolt(target));
            lastBoltSpawnMap.put(id, now);
        }

        bolts.removeIf(b -> now - b.spawn > b.life);
        if (bolts.isEmpty()) return;

        Vec3d basePos = new Vec3d(
                MathHelper.lerp(tickDelta, target.prevX, target.getX()),
                MathHelper.lerp(tickDelta, target.prevY, target.getY()),
                MathHelper.lerp(tickDelta, target.prevZ, target.getZ())
        );

        int redColor = 0xFFFF3C3C;
        int glowColor = blendColors(baseColor, redColor, hurt);
        int coreColor = blendColors(0xFFFFFFFF, redColor, hurt);

        float widthScale = widthSetting;

        // Render pass 1: Outer wide glow ribbon
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        boolean hasOuter = false;
        BufferBuilder outerBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (Bolt bolt : bolts) {
            float alpha = boltAlpha(bolt, now) * alphaMod;
            if (alpha <= 0.02f) continue;
            int outerA = (int) (alpha * 34);
            if (outerA > 0 && appendRibbon(outerBuffer, matrices, basePos, camPos, bolt.points, 0.045f * widthScale, withAlpha(glowColor, outerA))) {
                hasOuter = true;
            }
            for (List<Vec3d> branch : bolt.branches) {
                if (outerA > 0 && appendRibbon(outerBuffer, matrices, basePos, camPos, branch, 0.03f * widthScale, withAlpha(glowColor, (int) (outerA * 0.8f)))) {
                    hasOuter = true;
                }
            }
        }
        if (hasOuter) BufferRenderer.drawWithGlobalProgram(outerBuffer.end());

        // Render pass 2: Mid glow ribbon
        boolean hasGlow = false;
        BufferBuilder glowBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (Bolt bolt : bolts) {
            float alpha = boltAlpha(bolt, now) * alphaMod;
            if (alpha <= 0.02f) continue;
            int glowA = (int) (alpha * 80);
            if (glowA > 0 && appendRibbon(glowBuffer, matrices, basePos, camPos, bolt.points, 0.018f * widthScale, withAlpha(glowColor, glowA))) {
                hasGlow = true;
            }
            for (List<Vec3d> branch : bolt.branches) {
                if (glowA > 0 && appendRibbon(glowBuffer, matrices, basePos, camPos, branch, 0.012f * widthScale, withAlpha(glowColor, (int) (glowA * 0.75f)))) {
                    hasGlow = true;
                }
            }
        }
        if (hasGlow) BufferRenderer.drawWithGlobalProgram(glowBuffer.end());

        // Render pass 3: Core hot arc
        boolean hasCore = false;
        BufferBuilder coreBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (Bolt bolt : bolts) {
            float alpha = boltAlpha(bolt, now) * alphaMod;
            if (alpha <= 0.02f) continue;
            int coreA = (int) (alpha * 225);
            if (coreA > 0 && appendRibbon(coreBuffer, matrices, basePos, camPos, bolt.points, 0.006f * widthScale, withAlpha(coreColor, coreA))) {
                hasCore = true;
            }
            for (List<Vec3d> branch : bolt.branches) {
                if (coreA > 0 && appendRibbon(coreBuffer, matrices, basePos, camPos, branch, 0.004f * widthScale, withAlpha(coreColor, (int) (coreA * 0.85f)))) {
                    hasCore = true;
                }
            }
        }
        if (hasCore) BufferRenderer.drawWithGlobalProgram(coreBuffer.end());

        // Render pass 4: Bloom billboard nodes
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, BLOOM_TEX);
        for (Bolt bolt : bolts) {
            float alpha = boltAlpha(bolt, now) * alphaMod;
            if (alpha <= 0.05f) continue;
            int bloomA = (int) (alpha * 60);
            if (bloomA <= 0) continue;
            int bColor = withAlpha(glowColor, bloomA);
            for (int i = 0; i < bolt.points.size(); i += 2) {
                Vec3d p = basePos.add(bolt.points.get(i));
                drawBillboard(matrices, camera, camPos, p, BLOOM_TEX, bColor, bloomA / 255f, 0.2f * widthScale);
            }
        }
    }

    private static float boltAlpha(Bolt bolt, long now) {
        float life = MathHelper.clamp((now - bolt.spawn) / (float) bolt.life, 0.0f, 1.0f);
        float fade = life < 0.18f ? life / 0.18f : 1.0f - (life - 0.18f) / 0.82f;
        float flicker = 0.78f + 0.22f * (float) Math.sin(now * 0.045f + bolt.phase);
        return MathHelper.clamp(fade, 0.0f, 1.0f) * flicker;
    }

    private static Bolt spawnBolt(LivingEntity target) {
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        float radius = target.getWidth() * 0.5f;
        float height = target.getHeight();

        double startAngle = random.nextDouble() * Math.PI * 2.0;
        double startY = 0.15 + random.nextDouble() * Math.max(0.2, height - 0.4);
        Vec3d start = new Vec3d(
                Math.cos(startAngle) * radius * 0.7,
                startY,
                Math.sin(startAngle) * radius * 0.7
        );

        double theta = random.nextDouble() * Math.PI * 2.0;
        double phi = Math.toRadians((random.nextDouble() - 0.5) * 150.0);
        Vec3d dir = new Vec3d(
                Math.cos(phi) * Math.cos(theta),
                Math.sin(phi) * 0.8 + 0.25,
                Math.cos(phi) * Math.sin(theta)
        ).normalize();

        double length = 0.35 + random.nextDouble() * 0.45;
        Vec3d end = start.add(dir.multiply(length));

        List<Vec3d> points = new ArrayList<>(List.of(start, end));
        points = displacePoints(points, 0.09, 3);

        List<List<Vec3d>> branches = new ArrayList<>();
        int branchCount = 1 + random.nextInt(3);
        for (int i = 0; i < branchCount; i++) {
            int anchorIndex = 1 + random.nextInt(Math.max(1, points.size() - 2));
            Vec3d anchor = points.get(anchorIndex);
            Vec3d branchDir = randomPerpendicular(dir);
            if (random.nextBoolean()) {
                branchDir = branchDir.multiply(-1.0);
            }
            double branchLength = 0.12 + random.nextDouble() * 0.18;
            Vec3d branchEnd = anchor.add(branchDir.multiply(branchLength));
            List<Vec3d> branchPoints = new ArrayList<>(List.of(anchor, branchEnd));
            branches.add(displacePoints(branchPoints, 0.05, 2));
        }

        return new Bolt(points, branches, 140L + random.nextLong(160L), random.nextFloat() * 6.28f);
    }

    private static List<Vec3d> displacePoints(List<Vec3d> input, double amount, int passes) {
        List<Vec3d> points = new ArrayList<>(input);
        double amt = amount;
        for (int pass = 0; pass < passes; pass++) {
            List<Vec3d> next = new ArrayList<>(points.size() * 2);
            for (int i = 0; i < points.size() - 1; i++) {
                Vec3d a = points.get(i);
                Vec3d b = points.get(i + 1);
                next.add(a);
                Vec3d dir = b.subtract(a);
                if (dir.lengthSquared() < 1.0E-7) {
                    next.add(a.add(b).multiply(0.5));
                    continue;
                }
                Vec3d perp = randomPerpendicular(dir.normalize());
                double offset = (java.util.concurrent.ThreadLocalRandom.current().nextDouble() - 0.5) * 2.0 * amt;
                next.add(a.add(b).multiply(0.5).add(perp.multiply(offset)));
            }
            next.add(points.get(points.size() - 1));
            points = next;
            amt *= 0.5;
        }
        return points;
    }

    private static Vec3d randomPerpendicular(Vec3d dir) {
        Vec3d arbitrary = Math.abs(dir.y) < 0.9 ? new Vec3d(0, 1, 0) : new Vec3d(1, 0, 0);
        Vec3d perp1 = dir.crossProduct(arbitrary).normalize();
        Vec3d perp2 = dir.crossProduct(perp1).normalize();
        double angle = java.util.concurrent.ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0;
        return perp1.multiply(Math.cos(angle)).add(perp2.multiply(Math.sin(angle)));
    }

    private static boolean appendRibbon(BufferBuilder buffer,
                                        MatrixStack matrices,
                                        Vec3d base,
                                        Vec3d cam,
                                        List<Vec3d> points,
                                        float width,
                                        int color) {
        int a = (color >> 24) & 0xFF;
        if (a <= 0 || points.size() < 2) return false;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float half = width * 0.5f;
        int appended = 0;

        float rf = r / 255f, gf = g / 255f, bf = b / 255f, af = a / 255f;

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3d p1 = base.add(points.get(i));
            Vec3d p2 = base.add(points.get(i + 1));
            Vec3d segDir = p2.subtract(p1);
            if (segDir.lengthSquared() < 1.0E-8) continue;
            Vec3d viewDir = p1.add(p2).multiply(0.5).subtract(cam);
            if (viewDir.lengthSquared() < 1.0E-8) continue;
            Vec3d side = segDir.crossProduct(viewDir);
            double sideLen = side.length();
            if (sideLen < 1.0E-8 || !Double.isFinite(sideLen)) continue;
            side = side.multiply(1.0 / sideLen);

            float x1 = (float) (p1.x - cam.x);
            float y1 = (float) (p1.y - cam.y);
            float z1 = (float) (p1.z - cam.z);
            float x2 = (float) (p2.x - cam.x);
            float y2 = (float) (p2.y - cam.y);
            float z2 = (float) (p2.z - cam.z);
            float sx = (float) side.x * half;
            float sy = (float) side.y * half;
            float sz = (float) side.z * half;

            buffer.vertex(matrix, x1 - sx, y1 - sy, z1 - sz).color(rf, gf, bf, af);
            buffer.vertex(matrix, x1 + sx, y1 + sy, z1 + sz).color(rf, gf, bf, af);
            buffer.vertex(matrix, x2 + sx, y2 + sy, z2 + sz).color(rf, gf, bf, af);
            buffer.vertex(matrix, x2 - sx, y2 - sy, z2 - sz).color(rf, gf, bf, af);
            appended++;
        }
        return appended > 0;
    }

    private static void renderCrystals(MatrixStack matrices,
                                       Camera camera,
                                       float tickDelta,
                                       Vec3d camPos,
                                       LivingEntity target,
                                       int baseColor,
                                       float alphaMod,
                                       float speedSet,
                                       float hurt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null && mc.player != null) {
            boolean occluded = mc.world.raycast(new RaycastContext(
                    camera.getPos(),
                    target.getEyePos(),
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    mc.player
            )).getType() != HitResult.Type.MISS;

            if (occluded) {
                RenderSystem.disableDepthTest();
            } else {
                RenderSystem.enableDepthTest();
            }
        }

        double tx = MathHelper.lerp(tickDelta, target.prevX, target.getX());
        double ty = MathHelper.lerp(tickDelta, target.prevY, target.getY());
        double tz = MathHelper.lerp(tickDelta, target.prevZ, target.getZ());

        double renderX = tx - camPos.x;
        double renderY = ty - camPos.y;
        double renderZ = tz - camPos.z;

        float width = target.getWidth() * 1.5f;
        float height = target.getHeight();

        int crystalCount = MathHelper.clamp(
                LexoraGui.numSettings.getOrDefault("Crystal Count", 18.0f).intValue(),
                8,
                30
        );

        float crystalScaleSetting = MathHelper.clamp(
                LexoraGui.numSettings.getOrDefault("Crystal Size", 1.0f),
                0.1f,
                3.0f
        );

        float size = 0.1f * crystalScaleSetting;
        float bigSize = 1.0f * crystalScaleSetting;
        float val = 1.2f - 0.5f * alphaMod;

        float moving = (MinecraftClient.getInstance().player.age + tickDelta) * 35.0f * Math.max(0.1f, speedSet);

        int crystalAlpha = MathHelper.clamp((int) (255.0f * alphaMod), 0, 255);
        int bloomAlpha = MathHelper.clamp((int) (255.0f * alphaMod * 0.2f), 0, 255);

        int crystalColor = (crystalAlpha << 24) | (baseColor & 0x00FFFFFF);
        int bloomColor = (bloomAlpha << 24) | (baseColor & 0x00FFFFFF);

        matrices.push();
        matrices.translate(renderX, renderY, renderZ);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        float step = 360.0f / crystalCount;

        // Pass 1: 3D Shaded Crystals (Rockstar CrystalRenderer)
        BufferBuilder builder = CrystalRenderer.createBuffer();
        for (int c = 0; c < crystalCount; c++) {
            float i = c * step;
            float rad = (float) Math.toRadians(i + moving * 0.3f);
            float sin = (float) (MathHelper.sin(rad) * width * val);
            float cos = (float) (MathHelper.cos(rad) * width * val);
            float yPos = 0.1f + height * Math.abs((float) Math.sin(i));

            matrices.push();
            matrices.translate(sin, yPos, cos);

            // Vector from crystal to target center (0, height / 2, 0)
            Vector3f directionToTarget = new Vector3f(-sin, (height * 0.5f) - yPos, -cos).normalize();
            Vector3f initialDirection = new Vector3f(0.0f, 1.0f, 0.0f);
            Quaternionf rotation = new Quaternionf().rotationTo(initialDirection, directionToTarget);
            matrices.multiply(rotation);

            CrystalRenderer.render(matrices, builder, 0.0f, 0.0f, 0.0f, size, crystalColor);
            matrices.pop();
        }
        BufferRenderer.drawWithGlobalProgram(builder.end());

        // Pass 2: Bloom Glow Billboard on each crystal (Rockstar drawImage bloom)
        RenderSystem.setShaderTexture(0, BLOOM_TEX);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        BufferBuilder bloomBuffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_TEXTURE_COLOR
        );

        for (int c = 0; c < crystalCount; c++) {
            float i = c * step;
            float rad = (float) Math.toRadians(i + moving * 0.3f);
            float sin = (float) (MathHelper.sin(rad) * width * val);
            float cos = (float) (MathHelper.cos(rad) * width * val);
            float yPos = 0.1f + height * Math.abs((float) Math.sin(i));

            matrices.push();
            matrices.translate(sin, yPos, cos);
            matrices.multiply(camera.getRotation());

            CrystalRenderer.drawBloomQuad(matrices, bloomBuffer, -bigSize / 2.0f, -bigSize / 2.0f, 0.0f, bigSize, bigSize, bloomColor);
            matrices.pop();
        }
        BufferRenderer.drawWithGlobalProgram(bloomBuffer.end());

        matrices.pop();

        RenderSystem.depthMask(true);
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.disableDepthTest();
    }

    private static void renderJello(MatrixStack matrices,
                                    Camera camera,
                                    float tickDelta,
                                    Vec3d camPos,
                                    LivingEntity target,
                                    int baseColor,
                                    float alphaMod,
                                    float speedSet,
                                    float hurt) {
        double x = MathHelper.lerp(tickDelta, target.prevX, target.getX()) - camPos.x;
        double y = MathHelper.lerp(tickDelta, target.prevY, target.getY()) - camPos.y;
        double z = MathHelper.lerp(tickDelta, target.prevZ, target.getZ()) - camPos.z;

        float entityWidth = target.getWidth() * 1.65f;
        float entityHeight = target.getHeight() - 0.15f;

        // было jelloMoving += 4f за кадр (зависело от fps), теперь от игрового времени + ESP Speed
        float jelloMoving = (MinecraftClient.getInstance().player.age + tickDelta) * 4.0f * Math.max(0.1f, speedSet);
        float scale = Math.max(0.5f, 0.7f - 0.2f * alphaMod);

        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, BLOOM_TEX);

        matrices.push();
        matrices.translate(x, y, z);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        for (int i = 0; i < 360; i += 2) {
            double rad = Math.toRadians(i + jelloMoving);
            float xOffset = (float) (Math.cos(rad) * entityWidth * scale);
            float zOffset = (float) (Math.sin(rad) * entityWidth * scale);

            float sizeBase = 0.2f;

            for (int j = 0; j < 15; ++j) {
                float yOffsetLayer = entityHeight / 1.7f + (entityHeight / 2.0f) * (float) Math.cos(Math.toRadians(jelloMoving / 1.5f + j * 2.0f));

                matrices.push();
                matrices.translate(xOffset, yOffsetLayer, zOffset);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

                MatrixStack.Entry entry = matrices.peek();
                int finalAlpha = (int) (255 * alphaMod * ((float) j / 15.0f) * 0.05f);

                buffer.vertex(entry.getPositionMatrix(), -sizeBase / 2.0f, -sizeBase / 2.0f, 0).texture(0, 0).color(r, g, b, finalAlpha);
                buffer.vertex(entry.getPositionMatrix(), sizeBase / 2.0f, -sizeBase / 2.0f, 0).texture(1, 0).color(r, g, b, finalAlpha);
                buffer.vertex(entry.getPositionMatrix(), sizeBase / 2.0f, sizeBase / 2.0f, 0).texture(1, 1).color(r, g, b, finalAlpha);
                buffer.vertex(entry.getPositionMatrix(), -sizeBase / 2.0f, sizeBase / 2.0f, 0).texture(0, 1).color(r, g, b, finalAlpha);
                buffer.vertex(entry.getPositionMatrix(), -sizeBase / 2.0f, -sizeBase / 2.0f, 0).texture(0, 0).color(r, g, b, finalAlpha);
                buffer.vertex(entry.getPositionMatrix(), sizeBase / 2.0f, -sizeBase / 2.0f, 0).texture(1, 0).color(r, g, b, finalAlpha);
                matrices.pop();
            }
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();

        matrices.push();
        matrices.translate(x, y, z);
        RenderSystem.setShaderTexture(0, BLOOM_TEX);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        for (int i = 0; i < 360; i += 2) {
            double rad = Math.toRadians(i + jelloMoving);
            float xOffset = (float) (Math.cos(rad) * entityWidth * scale);
            float zOffset = (float) (Math.sin(rad) * entityWidth * scale);
            float yOffset = entityHeight / 1.75f + (entityHeight / 2.0f) * (float) Math.cos(Math.toRadians(jelloMoving / 1.5f + 30.0f));

            float sizeLarge = 0.2f;

            matrices.push();
            matrices.translate(xOffset, yOffset, zOffset);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

            MatrixStack.Entry entry = matrices.peek();
            int finalAlpha = (int) (255 * alphaMod * 0.2f);

            buffer.vertex(entry.getPositionMatrix(), -sizeLarge / 2.0f, -sizeLarge / 2.0f, 0).texture(0, 0).color(r, g, b, finalAlpha);
            buffer.vertex(entry.getPositionMatrix(), sizeLarge / 2.0f, -sizeLarge / 2.0f, 0).texture(1, 0).color(r, g, b, finalAlpha);
            buffer.vertex(entry.getPositionMatrix(), sizeLarge / 2.0f, sizeLarge / 2.0f, 0).texture(1, 1).color(r, g, b, finalAlpha);
            buffer.vertex(entry.getPositionMatrix(), -sizeLarge / 2.0f, sizeLarge / 2.0f, 0).texture(0, 1).color(r, g, b, finalAlpha);

            matrices.pop();
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();
    }

    private static void renderSkull(MatrixStack matrices,
                                    Camera camera,
                                    float tickDelta,
                                    Vec3d camPos,
                                    LivingEntity target,
                                    float alphaMod,
                                    float speedSet,
                                    float hurt) {
        double x = MathHelper.lerp(tickDelta, target.prevX, target.getX()) - camPos.x;
        double y = MathHelper.lerp(tickDelta, target.prevY, target.getY()) - camPos.y;
        double z = MathHelper.lerp(tickDelta, target.prevZ, target.getZ()) - camPos.z;

        float pulse = 1.0f + 0.12f * MathHelper.sin(
                (MinecraftClient.getInstance().player.age + tickDelta) * 0.15f * Math.max(0.1f, speedSet)
        );

        float skullSize = MathHelper.clamp(
                LexoraGui.numSettings.getOrDefault("Skull Size", 1.0f),
                0.5f,
                3.0f
        );

        float finalSize = skullSize * pulse;
        Identifier skullTex = getSkullTexture(target);

        matrices.push();
        matrices.translate(x, y + target.getHeight() / 2.0f, z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrices.scale(finalSize, finalSize, finalSize);

        int color = 0xFFFFFFFF;
        if (hurt > 0.0f) {
            color = blendColors(0xFFFFFFFF, 0xFFFF4040, hurt);
        }

        color = withAlpha(color, MathHelper.clamp((int) (alphaMod * 255.0f), 0, 255));
        drawTexturedQuad(matrices, skullTex, color, alphaMod, 1.0f);

        matrices.pop();
    }

    private static Identifier getSkullTexture(LivingEntity target) {
        float maxHp = Math.max(1.0f, target.getMaxHealth());
        float hpPercent = target.getHealth() / maxHp;

        if (hpPercent > 0.5f) {
            return SKULL_0_TEX;
        } else if (hpPercent > 0.25f) {
            return SKULL_1_TEX;
        } else {
            return SKULL_2_TEX;
        }
    }

    private static void drawQuad(MatrixStack matrices, Identifier texture, int color, float size) {
        drawTexturedQuad(matrices, texture, color, ((color >> 24) & 0xFF) / 255.0f, size);
    }

    private static void drawTexturedQuad(MatrixStack matrices, Identifier texture, int color, float alpha, float size) {
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, texture);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_TEXTURE_COLOR
        );

        float half = size * 0.5f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = MathHelper.clamp(alpha, 0.0f, 1.0f);

        buffer.vertex(matrix, -half, -half, 0.0f).texture(0.0f, 1.0f).color(r, g, b, a);
        buffer.vertex(matrix,  half, -half, 0.0f).texture(1.0f, 1.0f).color(r, g, b, a);
        buffer.vertex(matrix,  half,  half, 0.0f).texture(1.0f, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, -half,  half, 0.0f).texture(0.0f, 0.0f).color(r, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static void drawBillboard(MatrixStack matrices,
                                      Camera camera,
                                      Vec3d camPos,
                                      Vec3d worldPos,
                                      Identifier texture,
                                      int color,
                                      float alpha,
                                      float size) {
        matrices.push();
        matrices.translate(worldPos.x - camPos.x, worldPos.y - camPos.y, worldPos.z - camPos.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

        drawTexturedQuad(
                matrices,
                texture,
                withAlpha(color, MathHelper.clamp((int) (alpha * 255.0f), 0, 255)),
                alpha,
                size
        );

        matrices.pop();
    }


    private static int withAlpha(int color, int alpha) {
        alpha = MathHelper.clamp(alpha, 0, 255);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static float ease(float x) {
        return x < 0.5f
                ? 2.0f * x * x
                : -1.0f + (4.0f - 2.0f * x) * x;
    }

    private static int mixToWhite(int color, float factor) {
        factor = MathHelper.clamp(factor, 0.0f, 1.0f);

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        r = (int) (r + (255 - r) * factor);
        g = (int) (g + (255 - g) * factor);
        b = (int) (b + (255 - b) * factor);

        return (r << 16) | (g << 8) | b;
    }

    private static int blendColors(int from, int to, float progress) {
        progress = MathHelper.clamp(progress, 0.0f, 1.0f);

        int a1 = (from >> 24) & 0xFF;
        int r1 = (from >> 16) & 0xFF;
        int g1 = (from >> 8) & 0xFF;
        int b1 = from & 0xFF;

        int a2 = (to >> 24) & 0xFF;
        int r2 = (to >> 16) & 0xFF;
        int g2 = (to >> 8) & 0xFF;
        int b2 = to & 0xFF;

        int a = (int) (a1 + (a2 - a1) * progress);
        int r = (int) (r1 + (r2 - r1) * progress);
        int g = (int) (g1 + (g2 - g1) * progress);
        int b = (int) (b1 + (b2 - b1) * progress);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}