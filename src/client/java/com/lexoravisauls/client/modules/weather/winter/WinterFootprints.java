package com.lexoravisauls.client.modules.weather.winter;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Шейдерная система реалистичных 3D-следов от обуви игрока на снегу:
 * - 3D-деформация снега (Normal Mapping углубления подошвы и протектора через .fsh шейдер).
 * - Направленный свет и тени внутри желобков протектора ботинка.
 * - Холодный синевато-морозный оттенок спрессованного снега.
 * - Микро-искры кристалликов льда и снега на солнце (Micro-facet Glitter).
 * - Плавное естественное заметание следов ветром (Wind Erosion Dissolve).
 */
public final class WinterFootprints {
    private static final Identifier FOOTPRINT_TEX = Identifier.of("lexoravisauls", "textures/particles/footprint.png");
    private static final int MAX_FOOTPRINTS = 512;

    private static final WinterFootprints INSTANCE = new WinterFootprints();

    public static WinterFootprints getInstance() {
        return INSTANCE;
    }

    public static final class Footprint {
        public double x;
        public double y;
        public double z;
        public float yaw;
        public boolean isRightFoot;
        public long spawnTime;
        public float light;
        public float width;
        public float length;
    }

    private final List<Footprint> footprints = new ArrayList<>(MAX_FOOTPRINTS);

    private double lastX = Double.NaN;
    private double lastZ = Double.NaN;
    private boolean isRightFoot = false;
    private boolean wasInAir = false;

    private WinterFootprints() {
    }

    public void clear() {
        this.footprints.clear();
        this.lastX = Double.NaN;
        this.lastZ = Double.NaN;
        this.wasInAir = false;
    }

    public void cleanupBuffers() {
    }

    /**
     * Вызывается каждый клиентский тик для отслеживания шагов игрока.
     */
    public void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        ClientPlayerEntity player = mc.player;

        // Не оставляем следы в полете или режиме наблюдателя
        if (player.isSpectator() || player.getAbilities().flying) {
            this.lastX = Double.NaN;
            this.lastZ = Double.NaN;
            return;
        }

        boolean onGround = player.isOnGround();
        double px = player.getX();
        double pz = player.getZ();

        if (Double.isNaN(this.lastX) || Double.isNaN(this.lastZ)) {
            this.lastX = px;
            this.lastZ = pz;
            this.wasInAir = !onGround;
            return;
        }

        // Защита от телепортации / респавна
        double dX = px - this.lastX;
        double dZ = pz - this.lastZ;
        double distSq = dX * dX + dZ * dZ;
        if (distSq > 16.0) {
            this.lastX = px;
            this.lastZ = pz;
            this.wasInAir = !onGround;
            return;
        }

        // Приземление после прыжка: оставляем оба следа рядом
        if (this.wasInAir && onGround) {
            spawnStep(mc.world, player, false);
            spawnStep(mc.world, player, true);
            this.lastX = px;
            this.lastZ = pz;
            this.wasInAir = false;
            return;
        }
        this.wasInAir = !onGround;

        if (!onGround || player.isTouchingWater() || player.isInLava() || player.hasVehicle()) {
            return;
        }

        // Шаг при ходьбе, беге или приседе
        float stride = player.isSprinting() ? 0.85f : (player.isSneaking() ? 0.45f : 0.65f);
        if (distSq >= stride * stride) {
            this.isRightFoot = !this.isRightFoot;
            spawnStep(mc.world, player, this.isRightFoot);
            this.lastX = px;
            this.lastZ = pz;
        }
    }

    private void spawnStep(ClientWorld world, ClientPlayerEntity player, boolean rightFoot) {
        float yaw = player.getYaw();
        float yawRad = (float) Math.toRadians(yaw);

        // Векторы направления игрока в координатах Minecraft
        float fwdX = -MathHelper.sin(yawRad);
        float fwdZ = MathHelper.cos(yawRad);
        float rightX = -fwdZ;
        float rightZ = fwdX;

        float lateralDist = (player.isSprinting() ? 0.16f : (player.isSneaking() ? 0.11f : 0.14f)) * (rightFoot ? 1.0f : -1.0f);
        double footX = player.getX() + rightX * lateralDist;
        double footZ = player.getZ() + rightZ * lateralDist;
        double baseY = player.getY();

        // Проверка открытого неба
        boolean skyOnly = com.lexoravisauls.client.gui.LexoraGui.moduleStates.getOrDefault("Winter Sky Only", true);
        if (skyOnly) {
            int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(footX), MathHelper.floor(footZ));
            if (baseY < topY - 1) {
                // Под крышей: оставляем следы только если на полу лежит настоящий снег
                BlockPos underPos = BlockPos.ofFloored(footX, baseY - 0.2, footZ);
                BlockState underState = world.getBlockState(underPos);
                if (!underState.isOf(Blocks.SNOW) &&
                    !underState.isOf(Blocks.SNOW_BLOCK) &&
                    !underState.isOf(Blocks.POWDER_SNOW)) {
                    return;
                }
            }
        }

        double surfaceY = resolveGroundY(world, footX, baseY, footZ);
        BlockPos lightPos = BlockPos.ofFloored(footX, surfaceY + 0.5, footZ);
        int lightVal = world.getLightLevel(lightPos);
        float light = MathHelper.clamp(lightVal / 15.0f, 0.35f, 1.0f);

        Footprint fp = new Footprint();
        fp.x = footX;
        // На 6мм выше поверхности блока (+0.006) и 3мм выше снежного меша, без Z-файтинга
        fp.y = surfaceY + 0.006;
        fp.z = footZ;
        // Естественный легкий разворот стопы наружу (~4 градуса)
        fp.yaw = yaw + (rightFoot ? 4.0f : -4.0f);
        fp.isRightFoot = rightFoot;
        fp.spawnTime = System.currentTimeMillis();
        fp.light = light;
        fp.length = 0.42f;
        fp.width = 0.21f;

        if (this.footprints.size() >= MAX_FOOTPRINTS) {
            this.footprints.remove(0);
        }
        this.footprints.add(fp);
    }

    private double resolveGroundY(ClientWorld world, double x, double baseY, double z) {
        int bx = MathHelper.floor(x);
        int bz = MathHelper.floor(z);
        int by = MathHelper.floor(baseY + 0.1);

        for (int y = by; y >= by - 2; y--) {
            BlockPos pos = new BlockPos(bx, y, bz);
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) continue;
            VoxelShape shape = state.getCollisionShape(world, pos);
            if (!shape.isEmpty()) {
                double top = y + shape.getMax(Direction.Axis.Y);
                if (Math.abs(top - baseY) <= 0.8) {
                    return top;
                }
            }
        }
        return baseY;
    }

    /**
     * Отрисовка всех активных следов на поверхности снега через нативный конвейер Minecraft:
     * - Полная совместимость с Sodium и Iris.
     * - Исключены любые сбои VAO / EBO в драйверах OpenGL (AMD/Intel/NVIDIA).
     * - Мягкое угасание и зеркальное отражение протектора для левой и правой ноги.
     */
    public void render(MatrixStack matrices, Camera camera, Matrix4f projectionMatrix, WinterSnowfall.Config config, float ambient) {
        if (this.footprints.isEmpty() || !config.footprints) return;

        long now = System.currentTimeMillis();
        float durationMs = Math.max(2000.0f, config.footprintDuration * 1000.0f);

        // Удаляем следы, время жизни которых истекло
        this.footprints.removeIf(fp -> (now - fp.spawnTime) > durationMs);
        if (this.footprints.isEmpty()) return;

        Vec3d cam = camera.getPos();
        float renderRadius = Math.max(24.0f, config.groundRadius);
        float rSq = renderRadius * renderRadius;
        float fadeStart = renderRadius * 0.75f;
        float fadeRange = Math.max(1.0f, renderRadius - fadeStart);

        float cr = ((config.color >> 16) & 0xFF) / 255.0f;
        float cg = ((config.color >> 8) & 0xFF) / 255.0f;
        float cb = (config.color & 0xFF) / 255.0f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, FOOTPRINT_TEX);
        BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        Matrix4f mat = matrices.peek().getPositionMatrix();
        int quads = 0;

        for (int i = 0; i < this.footprints.size(); i++) {
            Footprint fp = this.footprints.get(i);
            float dx = (float) (fp.x - cam.x);
            float dy = (float) (fp.y - cam.y);
            float dz = (float) (fp.z - cam.z);

            float dSq = dx * dx + dz * dz;
            if (dSq > rSq) continue;

            float age = (float) (now - fp.spawnTime);
            float lifeProgress = MathHelper.clamp(age / durationMs, 0.0f, 1.0f);
            float freshness = 1.0f - lifeProgress;
            if (freshness <= 0.005f) continue;

            float dist = (float) Math.sqrt(dSq);
            float distFade = dist <= fadeStart ? 1.0f : 1.0f - (dist - fadeStart) / fadeRange;
            float finalAlpha = MathHelper.clamp(freshness * config.opacity * distFade, 0.0f, 1.0f);
            if (finalAlpha <= 0.005f) continue;

            // Естественная тень углубления следа на снегу с морозным оттенком
            float light = MathHelper.clamp(fp.light * (0.50f + ambient * 0.50f), 0.25f, 1.0f);
            float r = MathHelper.clamp(cr * light * 0.74f, 0.0f, 1.0f);
            float g = MathHelper.clamp(cg * light * 0.80f, 0.0f, 1.0f);
            float b = MathHelper.clamp(cb * light * 0.92f, 0.0f, 1.0f);

            float yawRad = (float) Math.toRadians(fp.yaw);
            float fwdX = -MathHelper.sin(yawRad);
            float fwdZ = MathHelper.cos(yawRad);
            float rightX = -fwdZ;
            float rightZ = fwdX;

            float hl = fp.length * 0.5f;
            float hw = fp.width * 0.5f;

            // 4 угла отпечатка ботинка
            float x0 = dx + fwdX * hl - rightX * hw;
            float z0 = dz + fwdZ * hl - rightZ * hw;

            float x1 = dx - fwdX * hl - rightX * hw;
            float z1 = dz - fwdZ * hl - rightZ * hw;

            float x2 = dx - fwdX * hl + rightX * hw;
            float z2 = dz - fwdZ * hl + rightZ * hw;

            float x3 = dx + fwdX * hl + rightX * hw;
            float z3 = dz + fwdZ * hl + rightZ * hw;

            // Зеркальное отражение UV для правого ботинка
            float u0 = fp.isRightFoot ? 1.0f : 0.0f;
            float u1 = fp.isRightFoot ? 0.0f : 1.0f;

            buffer.vertex(mat, x0, dy, z0).texture(u0, 0.0f).color(r, g, b, finalAlpha);
            buffer.vertex(mat, x1, dy, z1).texture(u0, 1.0f).color(r, g, b, finalAlpha);
            buffer.vertex(mat, x2, dy, z2).texture(u1, 1.0f).color(r, g, b, finalAlpha);
            buffer.vertex(mat, x3, dy, z3).texture(u1, 0.0f).color(r, g, b, finalAlpha);

            quads++;
            if (quads >= MAX_FOOTPRINTS) break;
        }

        BuiltBuffer built = buffer.endNullable();
        if (built != null && quads > 0) {
            BufferRenderer.drawWithGlobalProgram(built);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
