package com.lexoravisauls.client.modules.weather.winter;

import com.lexoravisauls.client.utils.LexoraShaders;
import com.lexoravisauls.client.utils.ShaderUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.render.VertexFormat.DrawMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.Heightmap;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public final class WinterSnowfall {
    private static final Identifier SOFT_FLAKE = Identifier.of("lexoravisauls", "textures/particles/snow_soft.png");
    private static final Identifier DETAILED_FLAKE = Identifier.of("lexoravisauls", "textures/particles/snowflake.png");
    private static final Identifier SNOW_COVER = Identifier.of("lexoravisauls", "textures/particles/snow_cover.png");

    private static final int MAX_FLAKES = 4200;
    private static final int MAX_GROUND_QUADS = 120000;
    private static final float GRID_REFRESH_SECONDS = 3.0f;
    private static final int GRID_MARGIN = 3;
    private static final int MAX_COLUMN_SCAN = 48;
    private static final int FLOATS_PER_QUAD = 28; // 4 вершины * (x, y, z, u, v, shade, alpha)
    private static final float EDGE_EPS = 0.005f;
    private static final float LIP_DEPTH = 0.050f;
    private static final float SIDE_V = 0.18f;

    public static final class Config {
        public float radius = 32.0f;
        public float height = 24.0f;
        public float density = 1.2f;
        public float flakeSize = 0.45f; // Зафиксирован на деликатном реалистичном размере
        public float fallSpeed = 1.0f;
        public float wind = 1.0f;
        public float opacity = 1.0f;
        public float blizzard = 0.0f;
        public boolean skyOnly = true;
        public boolean groundSnow = true;
        public boolean hangingSnow = true;
        public boolean footprints = true;
        public float footprintDuration = 20.0f;
        public boolean snowfall = true;
        public boolean wallSnow = true;
        public boolean wallFrost = false;
        public float groundRadius = 40.0f;
        public float groundDepth = 0.09f;
        public int color = 0xFFEAF4FF;
    }

    private static final WinterSnowfall INSTANCE = new WinterSnowfall();

    public static WinterSnowfall getInstance() {
        return INSTANCE;
    }

    private final List<Flake> flakes = new ArrayList<>(MAX_FLAKES);
    private final Random random = new Random();
    private final BlockPos.Mutable scratchPos = new BlockPos.Mutable();
    private final BlockPos.Mutable edgePos = new BlockPos.Mutable();
    private final BlockPos.Mutable seamCheckPos = new BlockPos.Mutable();
    private final BlockPos.Mutable wallPos = new BlockPos.Mutable();
    private final BlockPos.Mutable airPos = new BlockPos.Mutable();
    private final BlockPos.Mutable belowPos = new BlockPos.Mutable();
    private final BlockPos.Mutable belowAirPos = new BlockPos.Mutable();
    private final Matrix4f sprite = new Matrix4f();
    private final Quaternionf inverseCamera = new Quaternionf();
    private final Vector3f scratchVelocity = new Vector3f();
    private final Vector3f camRight = new Vector3f();
    private final Vector3f camUp = new Vector3f();

    private float[] surfaceBase = new float[0];
    private boolean[] surfaceFull = new boolean[0];
    private float[] surfaceLight = new float[0];
    private int[] surfaceY = new int[0];
    private VoxelShape[] surfaceShapes = new VoxelShape[0];
    private float[] mesh = new float[FLOATS_PER_QUAD * 4096];
    private int meshQuads;
    private float builtDepth = -1.0f;
    private int resolvedY;
    private VoxelShape currentShape;
    private int currentWorldX;
    private int currentWorldZ;
    private final float[] segStart = new float[16];
    private final float[] segEnd = new float[16];
    private final float[] segStartNext = new float[16];
    private final float[] segEndNext = new float[16];
    private VoxelShape resolvedShape;
    private int gridRadius = -1;
    private int gridOriginX = Integer.MIN_VALUE;
    private int gridOriginY = Integer.MIN_VALUE;
    private int gridOriginZ = Integer.MIN_VALUE;
    private float gridAge = Float.MAX_VALUE;
    private long lastFrameNanos;
    private float clock;

    private WinterSnowfall() {
    }

    public void reset() {
        this.flakes.clear();
        this.lastFrameNanos = 0L;
        this.clock = 0.0f;
        this.gridRadius = -1;
        this.gridOriginX = Integer.MIN_VALUE;
        this.gridOriginY = Integer.MIN_VALUE;
        this.gridOriginZ = Integer.MIN_VALUE;
        this.gridAge = Float.MAX_VALUE;
        this.meshQuads = 0;
        this.surfaceShapes = new VoxelShape[0];
        this.surfaceBase = new float[0];
    }

    public void cleanupBuffers() {
    }

    public void render(MatrixStack matrices, Camera camera, Matrix4f projectionMatrix, Config config) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null || camera == null) {
            reset();
            return;
        }

        long now = System.nanoTime();
        if (this.lastFrameNanos == 0L) this.lastFrameNanos = now;
        float dt = MathHelper.clamp((float) ((now - this.lastFrameNanos) / 1.0E9d), 0.0f, 0.05f);
        this.lastFrameNanos = now;
        this.clock += dt;

        Vec3d cam = camera.getPos();
        float ambient = ambientLight(mc, cam);

        if (config.groundSnow || config.wallSnow || config.wallFrost) {
            updateGrid(mc.world, cam, config, dt);
            drawGround(matrices, camera, cam, config, ambient);
        }

        if (config.footprints) {
            WinterFootprints.getInstance().render(matrices, camera, projectionMatrix, config, ambient);
        }

        if (config.snowfall) {
            renderSnowfall(matrices, camera, config, dt);
        }
    }

    /**
     * Высокопроизводительный 3D снегопад:
     * - Каждая снежинка существует в реальном 3D мире на своих координатах (f.x, f.y, f.z).
     * - Биллборд ориентируется в пространстве камеры без привязки к экрану: при вращении головой снежинки
     *   остаются неподвижно парить в воздухе мира.
     * - Рендерится через нативный конвейер Minecraft (POSITION_TEX_COLOR + BufferRenderer),
     *   полностью совместимый с Sodium и Iris.
     * - Автоматически скрывается за блоками и стенами мира благодаря тесту глубины (depth test).
     */
    private void renderSnowfall(MatrixStack matrices, Camera camera, Config config, float dt) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        Vec3d cam = camera.getPos();
        updateFlakes(mc.world, cam, config, dt);
        if (this.flakes.isEmpty()) return;

        Matrix4f mat = matrices.peek().getPositionMatrix();

        float spawnR = MathHelper.clamp(config.radius, 16.0f, 40.0f);
        float maxDistSq = spawnR * spawnR;
        float fadeStart = spawnR * 0.72f;
        float fadeRange = spawnR - fadeStart + 0.1f;

        float cr = ((config.color >> 16) & 0xFF) / 255.0f;
        float cg = ((config.color >> 8) & 0xFF) / 255.0f;
        float cb = (config.color & 0xFF) / 255.0f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, SOFT_FLAKE);
        BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        // Коэффициенты матрицы поворота камеры для прямого преобразования в пространство камеры (view space)
        float m00 = mat.m00(), m10 = mat.m10(), m20 = mat.m20();
        float m01 = mat.m01(), m11 = mat.m11(), m21 = mat.m21();
        float m02 = mat.m02(), m12 = mat.m12(), m22 = mat.m22();

        int quads = 0;
        int flakeCount = this.flakes.size();
        for (int i = 0; i < flakeCount; i++) {
            Flake f = this.flakes.get(i);

            // Вектор от камеры к снежинке в мировом пространстве
            float dx = (float) (f.x - cam.x);
            float dy = (float) (f.y - cam.y);
            float dz = (float) (f.z - cam.z);

            // Прямая проекция в пространство вида камеры (eye/view space):
            // Камера всегда смотрит вдоль оси -Z. Впереди камеры cz строго отрицательный (cz < -0.15f).
            // Это гарантирует 100% симметрию во всех направлениях (North, South, East, West).
            float cz = m02 * dx + m12 * dy + m22 * dz;
            if (cz >= -0.15f) continue; // Позади камеры или в плоскости ближнего отсечения

            float cx = m00 * dx + m10 * dy + m20 * dz;
            float cy = m01 * dx + m11 * dy + m21 * dz;

            float distSq = cx * cx + cy * cy + cz * cz;
            if (distSq > maxDistSq || distSq < 0.04f) continue;

            float dist = (float) Math.sqrt(distSq);
            float distFade = dist <= fadeStart ? 1.0f : 1.0f - (dist - fadeStart) / fadeRange;
            float depth = -cz;
            float nearFade = depth < 1.2f ? MathHelper.clamp((depth - 0.15f) / 1.05f, 0.0f, 1.0f) : 1.0f;
            float alpha = MathHelper.clamp(f.baseAlpha * config.opacity * nearFade * distFade, 0.0f, 1.0f);
            if (alpha <= 0.01f) continue;

            // Быстрые табличные синус и косинус Minecraft для вращения снежинки
            float cosA = MathHelper.cos(f.angle) * f.size;
            float sinA = MathHelper.sin(f.angle) * f.size;

            float u0 = -cosA + sinA; float v0 = -sinA - cosA;
            float u1 =  cosA + sinA; float v1 =  sinA - cosA;
            float u2 =  cosA - sinA; float v2 =  sinA + cosA;
            float u3 = -cosA - sinA; float v3 = -sinA + cosA;

            // Строгое ограничение яркости и каналов цветов во избежание переполнения байта
            float br = MathHelper.clamp(f.brightness, 0.0f, 1.0f);
            float r = MathHelper.clamp(cr * br, 0.0f, 1.0f);
            float g = MathHelper.clamp(cg * br, 0.0f, 1.0f);
            float b = MathHelper.clamp(cb * br, 0.0f, 1.0f);

            // Прямая запись вершин в пространство камеры без лишних матричных умножений и аллокаций
            buffer.vertex(cx + u0, cy + v0, cz).texture(0.0f, 0.0f).color(r, g, b, alpha);
            buffer.vertex(cx + u1, cy + v1, cz).texture(1.0f, 0.0f).color(r, g, b, alpha);
            buffer.vertex(cx + u2, cy + v2, cz).texture(1.0f, 1.0f).color(r, g, b, alpha);
            buffer.vertex(cx + u3, cy + v3, cz).texture(0.0f, 1.0f).color(r, g, b, alpha);

            quads++;
        }

        BuiltBuffer built = buffer.endNullable();
        if (built != null && quads > 0) {
            BufferRenderer.drawWithGlobalProgram(built);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
    }

    private void updateFlakes(ClientWorld level, Vec3d cam, Config config, float dt) {
        int targetCount = MathHelper.clamp((int) (2200 * config.density), 600, MAX_FLAKES);
        while (this.flakes.size() < targetCount) {
            Flake f = new Flake();
            initFlake(f, level, cam, config, true);
            this.flakes.add(f);
        }
        while (this.flakes.size() > targetCount) {
            this.flakes.remove(this.flakes.size() - 1);
        }

        float windAngle = this.clock * 0.08f;
        float windSpeed = config.wind * (1.2f + config.blizzard * 4.0f);
        float windX = MathHelper.cos(windAngle) * windSpeed;
        float windZ = MathHelper.sin(windAngle) * windSpeed;
        float fallSpeed = config.fallSpeed * (1.6f + config.blizzard * 3.2f);
        float spawnR = MathHelper.clamp(config.radius, 16.0f, 40.0f);
        double maxDistSq = (spawnR + 4.0f) * (spawnR + 4.0f);
        double minY = cam.y - 12.0;

        int size = this.flakes.size();
        for (int i = 0; i < size; i++) {
            Flake f = this.flakes.get(i);
            f.angle += f.spin * dt;
            float sway = MathHelper.sin(this.clock * f.swayFrequency + f.phase) * f.swayAmplitude;

            f.x += (f.vx + windX + sway) * dt;
            f.y -= (fallSpeed * f.terminal) * dt;
            f.z += (f.vz + windZ + sway * 0.7f) * dt;

            // Круговой контроль дистанции вокруг игрока: плавный респавн сверху
            double dx = f.x - cam.x;
            double dz = f.z - cam.z;
            double distSq = dx * dx + dz * dz;

            if (distSq > maxDistSq) {
                initFlake(f, level, cam, config, false);
            } else if (f.y < minY || (config.skyOnly && f.groundY > f.y + 0.1)) {
                initFlake(f, level, cam, config, false);
            }
        }
    }

    private void initFlake(Flake f, ClientWorld level, Vec3d cam, Config config, boolean initialScatter) {
        float spawnR = MathHelper.clamp(config.radius, 16.0f, 40.0f);
        double angle = this.random.nextDouble() * (Math.PI * 2.0);
        double dist = Math.sqrt(this.random.nextDouble()) * spawnR;
        f.x = cam.x + Math.cos(angle) * dist;
        f.z = cam.z + Math.sin(angle) * dist;

        if (initialScatter) {
            f.y = cam.y - 6.0 + random(0.0f, 24.0f);
        } else {
            f.y = cam.y + 12.0 + random(0.0f, 8.0f);
        }

        f.vx = random(-0.2f, 0.2f);
        f.vz = random(-0.2f, 0.2f);
        f.terminal = 0.85f + this.random.nextFloat() * 0.45f;

        float baseSize = 0.085f * (config.flakeSize / 0.45f);
        f.size = baseSize * (0.60f + this.random.nextFloat() * 0.85f);
        f.baseAlpha = 0.75f + this.random.nextFloat() * 0.25f;
        f.brightness = 0.85f + this.random.nextFloat() * 0.15f; // Строго не выше 1.0f

        f.swayFrequency = 1.2f + this.random.nextFloat() * 1.5f;
        f.swayAmplitude = 0.25f + this.random.nextFloat() * 0.45f;
        f.phase = this.random.nextFloat() * 6.2831855f;
        f.spin = (this.random.nextFloat() - 0.5f) * 2.5f;
        f.angle = this.random.nextFloat() * 6.2831855f;

        f.groundY = config.skyOnly ? flakeGround(level, (int) Math.floor(f.x), (int) Math.floor(f.z)) : Double.NEGATIVE_INFINITY;
    }

    private void updateGrid(ClientWorld level, Vec3d cam, Config config, float dt) {
        int radius = MathHelper.clamp((int) Math.ceil(config.groundRadius), 4, 48) + GRID_MARGIN;
        int camX = MathHelper.floor(cam.x);
        int camZ = MathHelper.floor(cam.z);
        this.gridAge += dt;
        boolean moved = Math.abs(camX - this.gridOriginX) >= GRID_MARGIN
                || Math.abs(camZ - this.gridOriginZ) >= GRID_MARGIN
                || Math.abs(MathHelper.floor(cam.y) - this.gridOriginY) > 24;
        boolean resized = radius != this.gridRadius || config.groundDepth != this.builtDepth;
        if (!moved && !resized && this.gridAge < GRID_REFRESH_SECONDS) return;

        int side = radius * 2 + 1;
        int cells = side * side;
        if (this.surfaceBase.length != cells) {
            this.surfaceBase = new float[cells];
            this.surfaceFull = new boolean[cells];
            this.surfaceLight = new float[cells];
            this.surfaceY = new int[cells];
            this.surfaceShapes = new VoxelShape[cells];
        }
        this.gridRadius = radius;
        this.gridOriginX = camX;
        this.gridOriginZ = camZ;
        this.gridOriginY = MathHelper.floor(cam.y);
        this.gridAge = 0.0f;
        this.builtDepth = config.groundDepth;

        for (int gz = 0; gz < side; gz++) {
            int worldZ = camZ - radius + gz;
            for (int gx = 0; gx < side; gx++) {
                int worldX = camX - radius + gx;
                int index = gz * side + gx;
                this.surfaceBase[index] = Float.NaN;
                this.surfaceFull[index] = false;
                this.surfaceShapes[index] = null;
                if (!resolveSurface(level, worldX, worldZ)) continue;
                VoxelShape shape = this.resolvedShape;
                int y = this.resolvedY;
                this.surfaceY[index] = y;
                this.surfaceShapes[index] = shape;
                this.surfaceBase[index] = (float) (y + shape.getMax(Direction.Axis.Y));
                this.surfaceFull[index] = coversWholeTop(shape);
                this.surfaceLight[index] = columnLight(level, worldX, y + 1, worldZ);
            }
        }
        buildMesh(level, cam, config);
    }

    private boolean resolveSurface(ClientWorld level, int x, int z) {
        try {
            if (!level.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) return false;
            int top = level.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
            int minY = level.getBottomY();
            if (top <= minY) return false;
            int bottom = Math.max(minY, top - MAX_COLUMN_SCAN);
            for (int y = top - 1; y >= bottom; y--) {
                this.scratchPos.set(x, y, z);
                BlockState state = level.getBlockState(this.scratchPos);
                if (state.isAir()) continue;
                if (!state.getFluidState().isEmpty()) return false;
                if (isInvisible(state)) continue;
                VoxelShape shape = state.getOutlineShape(level, this.scratchPos);
                if (shape.isEmpty()) continue;
                boolean snowLayer = state.isOf(Blocks.SNOW);
                if (!snowLayer && state.getCollisionShape(level, this.scratchPos).isEmpty()) continue;
                this.resolvedY = y;
                this.resolvedShape = shape;
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isInvisible(BlockState state) {
        if (state.isOf(Blocks.BARRIER) || state.isOf(Blocks.LIGHT) || state.isOf(Blocks.STRUCTURE_VOID)) return true;
        return state.getRenderType() == BlockRenderType.INVISIBLE && !state.hasBlockEntity();
    }

    private static boolean coversWholeTop(VoxelShape shape) {
        double top = shape.getMax(Direction.Axis.Y);
        for (Box box : shape.getBoundingBoxes()) {
            if (box.maxY >= top - 1.0E-3 && box.minX <= 1.0E-3 && box.minZ <= 1.0E-3
                    && box.maxX >= 1.0 - 1.0E-3 && box.maxZ >= 1.0 - 1.0E-3) return true;
        }
        return false;
    }

    private float columnLight(ClientWorld level, int x, int y, int z) {
        try {
            this.scratchPos.set(x, y, z);
            float light = level.getLightLevel(this.scratchPos) / 15.0f;
            return 0.40f + MathHelper.clamp(light, 0.0f, 1.0f) * 0.60f;
        } catch (Throwable ignored) {
            return 1.0f;
        }
    }

    private void buildMesh(ClientWorld level, Vec3d cam, Config config) {
        this.meshQuads = 0;
        int side = this.gridRadius * 2 + 1;
        if (config.groundSnow) {
            for (int gz = 0; gz < side; gz++) {
                for (int gx = 0; gx < side; gx++) {
                    int index = gz * side + gx;
                    VoxelShape shape = this.surfaceShapes[index];
                    if (shape == null) continue;
                    int worldX = this.gridOriginX - this.gridRadius + gx;
                    int worldZ = this.gridOriginZ - this.gridRadius + gz;
                    int blockY = this.surfaceY[index];
                    float light = this.surfaceLight[index];
                    for (Box box : shape.getBoundingBoxes()) {
                        double w = box.maxX - box.minX;
                        double d = box.maxZ - box.minZ;
                        if (w < 0.06 || d < 0.06) continue;
                        appendBox(level, worldX, blockY, worldZ, gx, gz, box, light, config);
                    }
                }
            }
        }
        if (config.wallFrost || config.wallSnow) {
            buildWallSurfaces(level, cam, config);
        }
    }

    private void appendBox(ClientWorld level, int worldX, int blockY, int worldZ, int gx, int gz,
                          Box box, float light, Config config) {
        float top = (float) (blockY + box.maxY);

        float lx0 = (float) (worldX + box.minX - this.gridOriginX);
        float lx1 = (float) (worldX + box.maxX - this.gridOriginX);
        float lz0 = (float) (worldZ + box.minZ - this.gridOriginZ);
        float lz1 = (float) (worldZ + box.maxZ - this.gridOriginZ);
        float oy = this.gridOriginY;

        float u0 = (float) box.minX, u1 = (float) box.maxX;
        float v0 = (float) box.minZ, v1 = (float) box.maxZ;

        // Снежный покров наверху блока
        float snowY = (top + 0.003f) - oy;

        pushQuad7(lx0, snowY, lz0, u0, v0, light, 1.0f,
                 lx0, snowY, lz1, u0, v1, light, 1.0f,
                 lx1, snowY, lz1, u1, v1, light, 1.0f,
                 lx1, snowY, lz0, u1, v0, light, 1.0f);

        // 3D свисающий снег на открытых краях (обрывы, ступени, крыши, выступы)
        if (config.hangingSnow && box.maxX - box.minX >= 0.18 && box.maxZ - box.minZ >= 0.18) {
            emitHangingSnow(level, worldX, blockY, worldZ, gx, gz, box, light, lx0, lx1, lz0, lz1, snowY);
        }
    }

    /**
     * Проверка, открыто ли ребро блока наружу (соседний блок пустой, вода или находится ниже).
     */
    private boolean isEdgeOpen(ClientWorld level, int worldX, int blockY, int worldZ, int gx, int gz,
                               Box box, double boxMaxY, int dir) {
        // 1. Внутреннее ребро составной формы (ступени лестниц, полублоки)
        boolean isInternal = switch (dir) {
            case 0 -> box.minX > 0.03;
            case 1 -> box.maxX < 0.97;
            case 2 -> box.minZ > 0.03;
            default -> box.maxZ < 0.97;
        };
        if (isInternal) {
            return true;
        }

        int nx = worldX, nz = worldZ;
        int ngx = gx, ngz = gz;
        switch (dir) {
            case 0 -> { nx--; ngx--; }
            case 1 -> { nx++; ngx++; }
            case 2 -> { nz--; ngz--; }
            default -> { nz++; ngz++; }
        }

        double myTop = blockY + boxMaxY;

        // 2. Блок прямо над соседней гранью (на высоте blockY + 1)
        // Если там есть твердый полноразмерный блок — значит стена уходит вверх, ребро закрыто
        this.edgePos.set(nx, blockY + 1, nz);
        BlockState aboveNeighbor = level.getBlockState(this.edgePos);
        if (!aboveNeighbor.isAir() && aboveNeighbor.getFluidState().isEmpty()) {
            if (!aboveNeighbor.getCollisionShape(level, this.edgePos).isEmpty()) {
                return false;
            }
        }

        // 3. Быстрая проверка по предварительно рассчитанной сетке высот поверхности surfaceBase
        int side = this.gridRadius * 2 + 1;
        if (ngx >= 0 && ngx < side && ngz >= 0 && ngz < side) {
            int nIndex = ngz * side + ngx;
            VoxelShape nShape = this.surfaceShapes[nIndex];
            if (nShape == null) return true; // Вода или пустота
            float nBase = this.surfaceBase[nIndex];
            if (Float.isNaN(nBase)) return true;
            if (nBase < myTop - 0.05f) return true; // Сосед ниже — обрыв/ступенька!
            if (nBase >= myTop + 0.05f) return false; // Сосед выше — стена!
            return !this.surfaceFull[nIndex]; // На той же высоте — если у соседа не полная поверхность (напр. ступенька)
        }

        // 4. За пределами сетки — прямая проверка мира
        this.edgePos.set(nx, blockY, nz);
        BlockState neighbor = level.getBlockState(this.edgePos);
        if (neighbor.isAir() || !neighbor.getFluidState().isEmpty() || isInvisible(neighbor)) {
            return true;
        }
        VoxelShape shape = neighbor.getCollisionShape(level, this.edgePos);
        if (shape.isEmpty()) {
            return true; // Трава, цветы, факелы и т.д. не являются препятствием
        }
        double neighborMaxY = shape.getMax(Direction.Axis.Y);
        return neighborMaxY < boxMaxY - 0.05;
    }

    private float getDropDepth(int gx, int gz, int dir, double myTop) {
        int side = this.gridRadius * 2 + 1;
        int ngx = gx, ngz = gz;
        switch (dir) {
            case 0 -> ngx--;
            case 1 -> ngx++;
            case 2 -> ngz--;
            default -> ngz++;
        }
        if (ngx >= 0 && ngx < side && ngz >= 0 && ngz < side) {
            float nBase = this.surfaceBase[ngz * side + ngx];
            if (!Float.isNaN(nBase)) {
                return MathHelper.clamp((float) (myTop - nBase), 0.06f, 1.5f);
            }
        }
        return 1.0f;
    }

    /**
     * Создание объемного 3D свисающего снега (карниза со снежными язычками) на обрывах и ступенях блоков.
     */
    private void emitHangingSnow(ClientWorld level, int worldX, int blockY, int worldZ, int gx, int gz,
                                 Box box, float light, float lx0, float lx1, float lz0, float lz1, float snowY) {
        double boxMaxY = box.maxY;
        double myTop = blockY + boxMaxY;

        for (int dir = 0; dir < 4; dir++) {
            if (!isEdgeOpen(level, worldX, blockY, worldZ, gx, gz, box, boxMaxY, dir)) continue;

            float drop = getDropDepth(gx, gz, dir, myTop);
            float baseH = drop >= 0.75f ? 0.22f : Math.min(0.22f, drop * 0.85f);
            float baseP = 0.065f;
            float lip = 0.020f;

            final int SEGS = 5;

            if (dir == 0) { // West (-X) край на lx0 вдоль оси Z
                float px = lx0;
                float len = lz1 - lz0;
                for (int s = 0; s < SEGS; s++) {
                    float t0 = (float) s / SEGS;
                    float t1 = (float) (s + 1) / SEGS;

                    float z0 = lz0 + t0 * len;
                    float z1 = lz0 + t1 * len;

                    float wZ0 = (float) (worldZ + box.minZ + t0 * (box.maxZ - box.minZ));
                    float wZ1 = (float) (worldZ + box.minZ + t1 * (box.maxZ - box.minZ));

                    float wave0 = (float) (0.55 * Math.sin(wZ0 * 4.2) + 0.35 * Math.sin(wZ0 * 8.7 + worldX * 2.3) + 0.20 * Math.cos(wZ0 * 12.0));
                    float wave1 = (float) (0.55 * Math.sin(wZ1 * 4.2) + 0.35 * Math.sin(wZ1 * 8.7 + worldX * 2.3) + 0.20 * Math.cos(wZ1 * 12.0));

                    float h0 = baseH + wave0 * 0.055f;
                    float h1 = baseH + wave1 * 0.055f;
                    float p0 = baseP + wave0 * 0.018f;
                    float p1 = baseP + wave1 * 0.018f;

                    float edgeFade0 = 1.0f;
                    if (t0 < 0.08f && !isEdgeOpen(level, worldX, blockY, worldZ, gx, gz, box, boxMaxY, 2)) edgeFade0 = Math.min(edgeFade0, t0 / 0.08f);
                    if (t0 > 0.92f && !isEdgeOpen(level, worldX, blockY, worldZ, gx, gz, box, boxMaxY, 3)) edgeFade0 = Math.min(edgeFade0, (1.0f - t0) / 0.08f);

                    float edgeFade1 = 1.0f;
                    if (t1 < 0.08f && !isEdgeOpen(level, worldX, blockY, worldZ, gx, gz, box, boxMaxY, 2)) edgeFade1 = Math.min(edgeFade1, t1 / 0.08f);
                    if (t1 > 0.92f && !isEdgeOpen(level, worldX, blockY, worldZ, gx, gz, box, boxMaxY, 3)) edgeFade1 = Math.min(edgeFade1, (1.0f - t1) / 0.08f);

                    h0 = Math.max(0.04f, h0 * edgeFade0);
                    p0 = Math.max(0.015f, p0 * edgeFade0);
                    h1 = Math.max(0.04f, h1 * edgeFade1);
                    p1 = Math.max(0.015f, p1 * edgeFade1);

                    float roll0 = 0.030f * edgeFade0;
                    float roll1 = 0.030f * edgeFade1;

                    // 1. Верхний перегиб (валик через край, нормаль -X, +Y)
                    pushQuad7(px + roll0, snowY, z0, t0, 0.0f, light * 1.0f, 1.0f,
                             px - p0, snowY - lip, z0, t0, 0.25f, light * 1.0f, 1.0f,
                             px - p1, snowY - lip, z1, t1, 0.25f, light * 1.0f, 1.0f,
                             px + roll1, snowY, z1, t1, 0.0f, light * 1.0f, 1.0f);

                    // 2. Свисающий лоб (дроп вниз по стене со снежными язычками, нормаль -X)
                    pushQuad7(px - p0, snowY - lip, z0, t0, 0.25f, light * 0.88f, 1.0f,
                             px - p0 * 0.75f, snowY - h0, z0, t0, 0.75f, light * 0.88f, 1.0f,
                             px - p1 * 0.75f, snowY - h1, z1, t1, 0.75f, light * 0.88f, 1.0f,
                             px - p1, snowY - lip, z1, t1, 0.25f, light * 0.88f, 1.0f);

                    // 3. Нижний подгиб к стене блока (нормаль -X, -Y)
                    pushQuad7(px - p0 * 0.75f, snowY - h0, z0, t0, 0.75f, light * 0.65f, 1.0f,
                             px, snowY - h0 - 0.015f, z0, t0, 1.0f, light * 0.65f, 1.0f,
                             px, snowY - h1 - 0.015f, z1, t1, 1.0f, light * 0.65f, 1.0f,
                             px - p1 * 0.75f, snowY - h1, z1, t1, 0.75f, light * 0.65f, 1.0f);
                }

            } else if (dir == 1) { // East (+X) край на lx1 вдоль оси Z
                float px = lx1;
                float len = lz1 - lz0;
                for (int s = 0; s < SEGS; s++) {
                    float t0 = (float) s / SEGS;
                    float t1 = (float) (s + 1) / SEGS;

                    float z0 = lz0 + t0 * len;
                    float z1 = lz0 + t1 * len;

                    float wZ0 = (float) (worldZ + box.minZ + t0 * (box.maxZ - box.minZ));
                    float wZ1 = (float) (worldZ + box.minZ + t1 * (box.maxZ - box.minZ));

                    float wave0 = (float) (0.55 * Math.sin(wZ0 * 4.2) + 0.35 * Math.sin(wZ0 * 8.7 + worldX * 2.3) + 0.20 * Math.cos(wZ0 * 12.0));
                    float wave1 = (float) (0.55 * Math.sin(wZ1 * 4.2) + 0.35 * Math.sin(wZ1 * 8.7 + worldX * 2.3) + 0.20 * Math.cos(wZ1 * 12.0));

                    float h0 = baseH + wave0 * 0.055f;
                    float h1 = baseH + wave1 * 0.055f;
                    float p0 = baseP + wave0 * 0.018f;
                    float p1 = baseP + wave1 * 0.018f;

                    float edgeFade0 = 1.0f;
                    if (t0 < 0.08f && !isEdgeOpen(level, worldX, blockY, worldZ, gx, gz, box, boxMaxY, 2)) edgeFade0 = Math.min(edgeFade0, t0 / 0.08f);
                    if (t0 > 0.92f && !isEdgeOpen(level, worldX, blockY, worldZ, gx, gz, box, boxMaxY, 3)) edgeFade0 = Math.min(edgeFade0, (1.0f - t0) / 0.08f);

                    float edgeFade1 = 1.0f;
                    if (t1 < 0.08f && !isEdgeOpen(level, worldX, blockY, worldZ, gx, gz, box, boxMaxY, 2)) edgeFade1 = Math.min(edgeFade1, t1 / 0.08f);
                    if (t1 > 0.92f && !isEdgeOpen(level, worldX, blockY, worldZ, gx, gz, box, boxMaxY, 3)) edgeFade1 = Math.min(edgeFade1, (1.0f - t1) / 0.08f);

                    h0 = Math.max(0.04f, h0 * edgeFade0);
                    p0 = Math.max(0.015f, p0 * edgeFade0);
                    h1 = Math.max(0.04f, h1 * edgeFade1);
                    p1 = Math.max(0.015f, p1 * edgeFade1);

                    float roll0 = 0.030f * edgeFade0;
                    float roll1 = 0.030f * edgeFade1;

                    // 1. Верхний перегиб (нормаль +X, +Y)
                    pushQuad7(px - roll1, snowY, z1, t1, 0.0f, light * 1.0f, 1.0f,
                             px + p1, snowY - lip, z1, t1, 0.25f, light * 1.0f, 1.0f,
                             px + p0, snowY - lip, z0, t0, 0.25f, light * 1.0f, 1.0f,
                             px - roll0, snowY, z0, t0, 0.0f, light * 1.0f, 1.0f);

                    // 2. Свисающий лоб (нормаль +X)
                    pushQuad7(px + p1, snowY - lip, z1, t1, 0.25f, light * 0.88f, 1.0f,
                             px + p1 * 0.75f, snowY - h1, z1, t1, 0.75f, light * 0.88f, 1.0f,
                             px + p0 * 0.75f, snowY - h0, z0, t0, 0.75f, light * 0.88f, 1.0f,
                             px + p0, snowY - lip, z0, t0, 0.25f, light * 0.88f, 1.0f);

                    // 3. Нижний подгиб (нормаль +X, -Y)
                    pushQuad7(px + p1 * 0.75f, snowY - h1, z1, t1, 0.75f, light * 0.65f, 1.0f,
                             px, snowY - h1 - 0.015f, z1, t1, 1.0f, light * 0.65f, 1.0f,
                             px, snowY - h0 - 0.015f, z0, t0, 1.0f, light * 0.65f, 1.0f,
                             px + p0 * 0.75f, snowY - h0, z0, t0, 0.75f, light * 0.65f, 1.0f);
                }

            } else if (dir == 2) { // North (-Z) край на lz0 вдоль оси X
                float pz = lz0;
                float len = lx1 - lx0;
                for (int s = 0; s < SEGS; s++) {
                    float t0 = (float) s / SEGS;
                    float t1 = (float) (s + 1) / SEGS;

                    float x0 = lx0 + t0 * len;
                    float x1 = lx0 + t1 * len;

                    float wX0 = (float) (worldX + box.minX + t0 * (box.maxX - box.minX));
                    float wX1 = (float) (worldX + box.minX + t1 * (box.maxX - box.minX));

                    float wave0 = (float) (0.55 * Math.sin(wX0 * 4.2) + 0.35 * Math.sin(wX0 * 8.7 + worldZ * 2.3) + 0.20 * Math.cos(wX0 * 12.0));
                    float wave1 = (float) (0.55 * Math.sin(wX1 * 4.2) + 0.35 * Math.sin(wX1 * 8.7 + worldZ * 2.3) + 0.20 * Math.cos(wX1 * 12.0));

                    float h0 = baseH + wave0 * 0.055f;
                    float h1 = baseH + wave1 * 0.055f;
                    float p0 = baseP + wave0 * 0.018f;
                    float p1 = baseP + wave1 * 0.018f;

                    float edgeFade0 = 1.0f;
                    if (t0 < 0.08f && !isEdgeOpen(level, worldX, blockY, worldZ, gx, gz, box, boxMaxY, 0)) edgeFade0 = Math.min(edgeFade0, t0 / 0.08f);
                    if (t0 > 0.92f && !isEdgeOpen(level, worldX, blockY, worldZ, gx, gz, box, boxMaxY, 1)) edgeFade0 = Math.min(edgeFade0, (1.0f - t0) / 0.08f);

                    float edgeFade1 = 1.0f;
                    if (t1 < 0.08f && !isEdgeOpen(level, worldX, blockY, worldZ, gx, gz, box, boxMaxY, 0)) edgeFade1 = Math.min(edgeFade1, t1 / 0.08f);
                    if (t1 > 0.92f && !isEdgeOpen(level, worldX, blockY, worldZ, gx, gz, box, boxMaxY, 1)) edgeFade1 = Math.min(edgeFade1, (1.0f - t1) / 0.08f);

                    h0 = Math.max(0.04f, h0 * edgeFade0);
                    p0 = Math.max(0.015f, p0 * edgeFade0);
                    h1 = Math.max(0.04f, h1 * edgeFade1);
                    p1 = Math.max(0.015f, p1 * edgeFade1);

                    float roll0 = 0.030f * edgeFade0;
                    float roll1 = 0.030f * edgeFade1;

                    // 1. Верхний перегиб (нормаль -Z, +Y)
                    pushQuad7(x1, snowY, pz + roll1, t1, 0.0f, light * 1.0f, 1.0f,
                             x1, snowY - lip, pz - p1, t1, 0.25f, light * 1.0f, 1.0f,
                             x0, snowY - lip, pz - p0, t0, 0.25f, light * 1.0f, 1.0f,
                             x0, snowY, pz + roll0, t0, 0.0f, light * 1.0f, 1.0f);

                    // 2. Свисающий лоб (нормаль -Z)
                    pushQuad7(x1, snowY - lip, pz - p1, t1, 0.25f, light * 0.88f, 1.0f,
                             x1, snowY - h1, pz - p1 * 0.75f, t1, 0.75f, light * 0.88f, 1.0f,
                             x0, snowY - h0, pz - p0 * 0.75f, t0, 0.75f, light * 0.88f, 1.0f,
                             x0, snowY - lip, pz - p0, t0, 0.25f, light * 0.88f, 1.0f);

                    // 3. Нижний подгиб (нормаль -Z, -Y)
                    pushQuad7(x1, snowY - h1, pz - p1 * 0.75f, t1, 0.75f, light * 0.65f, 1.0f,
                             x1, snowY - h1 - 0.015f, pz, t1, 1.0f, light * 0.65f, 1.0f,
                             x0, snowY - h0 - 0.015f, pz, t0, 1.0f, light * 0.65f, 1.0f,
                             x0, snowY - h0, pz - p0 * 0.75f, t0, 0.75f, light * 0.65f, 1.0f);
                }

            } else { // South (+Z) край на lz1 вдоль оси X
                float pz = lz1;
                float len = lx1 - lx0;
                for (int s = 0; s < SEGS; s++) {
                    float t0 = (float) s / SEGS;
                    float t1 = (float) (s + 1) / SEGS;

                    float x0 = lx0 + t0 * len;
                    float x1 = lx0 + t1 * len;

                    float wX0 = (float) (worldX + box.minX + t0 * (box.maxX - box.minX));
                    float wX1 = (float) (worldX + box.minX + t1 * (box.maxX - box.minX));

                    float wave0 = (float) (0.55 * Math.sin(wX0 * 4.2) + 0.35 * Math.sin(wX0 * 8.7 + worldZ * 2.3) + 0.20 * Math.cos(wX0 * 12.0));
                    float wave1 = (float) (0.55 * Math.sin(wX1 * 4.2) + 0.35 * Math.sin(wX1 * 8.7 + worldZ * 2.3) + 0.20 * Math.cos(wX1 * 12.0));

                    float h0 = baseH + wave0 * 0.055f;
                    float h1 = baseH + wave1 * 0.055f;
                    float p0 = baseP + wave0 * 0.018f;
                    float p1 = baseP + wave1 * 0.018f;

                    float edgeFade0 = 1.0f;
                    if (t0 < 0.08f && !isEdgeOpen(level, worldX, blockY, worldZ, gx, gz, box, boxMaxY, 0)) edgeFade0 = Math.min(edgeFade0, t0 / 0.08f);
                    if (t0 > 0.92f && !isEdgeOpen(level, worldX, blockY, worldZ, gx, gz, box, boxMaxY, 1)) edgeFade0 = Math.min(edgeFade0, (1.0f - t0) / 0.08f);

                    float edgeFade1 = 1.0f;
                    if (t1 < 0.08f && !isEdgeOpen(level, worldX, blockY, worldZ, gx, gz, box, boxMaxY, 0)) edgeFade1 = Math.min(edgeFade1, t1 / 0.08f);
                    if (t1 > 0.92f && !isEdgeOpen(level, worldX, blockY, worldZ, gx, gz, box, boxMaxY, 1)) edgeFade1 = Math.min(edgeFade1, (1.0f - t1) / 0.08f);

                    h0 = Math.max(0.04f, h0 * edgeFade0);
                    p0 = Math.max(0.015f, p0 * edgeFade0);
                    h1 = Math.max(0.04f, h1 * edgeFade1);
                    p1 = Math.max(0.015f, p1 * edgeFade1);

                    float roll0 = 0.030f * edgeFade0;
                    float roll1 = 0.030f * edgeFade1;

                    // 1. Верхний перегиб (нормаль +Z, +Y)
                    pushQuad7(x0, snowY, pz - roll0, t0, 0.0f, light * 1.0f, 1.0f,
                             x0, snowY - lip, pz + p0, t0, 0.25f, light * 1.0f, 1.0f,
                             x1, snowY - lip, pz + p1, t1, 0.25f, light * 1.0f, 1.0f,
                             x1, snowY, pz - roll1, t1, 0.0f, light * 1.0f, 1.0f);

                    // 2. Свисающий лоб (нормаль +Z)
                    pushQuad7(x0, snowY - lip, pz + p0, t0, 0.25f, light * 0.88f, 1.0f,
                             x0, snowY - h0, pz + p0 * 0.75f, t0, 0.75f, light * 0.88f, 1.0f,
                             x1, snowY - h1, pz + p1 * 0.75f, t1, 0.75f, light * 0.88f, 1.0f,
                             x1, snowY - lip, pz + p1, t1, 0.25f, light * 0.88f, 1.0f);

                    // 3. Нижний подгиб (нормаль +Z, -Y)
                    pushQuad7(x0, snowY - h0, pz + p0 * 0.75f, t0, 0.75f, light * 0.65f, 1.0f,
                             x0, snowY - h0 - 0.015f, pz, t0, 1.0f, light * 0.65f, 1.0f,
                             x1, snowY - h1 - 0.015f, pz, t1, 1.0f, light * 0.65f, 1.0f,
                             x1, snowY - h1, pz + p1 * 0.75f, t1, 0.75f, light * 0.65f, 1.0f);
                }
            }
        }
    }

    /**
     * Строгая валидация строительного материала стены:
     * - Снег спавнится ТОЛЬКО на полных, видимых, непрозрачных блоках (камень, дерево, кирпич и т.д.).
     * - Категорически исключены: барьеры (Blocks.BARRIER), свет, структуры, стекло, лед, листья, невидимые блоки,
     *   полублоки, лестницы и контейнеры.
     */
    private static boolean isValidWallMaterial(ClientWorld level, BlockPos pos, BlockState state) {
        if (state == null || state.isAir()) return false;
        if (state.isOf(Blocks.BARRIER) || state.isOf(Blocks.STRUCTURE_VOID) || state.isOf(Blocks.LIGHT)) return false;
        if (state.getRenderType() == BlockRenderType.INVISIBLE) return false;
        if (!state.getFluidState().isEmpty()) return false;
        if (state.hasBlockEntity()) return false;

        // Не спавнить на стекле, льде и листве
        if (state.isOf(Blocks.GLASS) || state.isOf(Blocks.TINTED_GLASS) || state.isIn(BlockTags.IMPERMEABLE)) return false;
        if (state.isOf(Blocks.ICE) || state.isOf(Blocks.PACKED_ICE) || state.isOf(Blocks.BLUE_ICE)) return false;
        if (state.isIn(BlockTags.LEAVES)) return false;

        // Только полные непрозрачные кубы (дерево, камень, булыжник, кирпичи, терракота, бетон и т.д.)
        if (!state.isOpaqueFullCube()) return false;

        VoxelShape shape = state.getOutlineShape(level, pos);
        return !shape.isEmpty() && coversWholeTop(shape);
    }

    /**
     * Сканирование реальных вертикальных стен вокруг игрока:
     * 1. Наложение полупрозрачного инеевого оттенка (изморозь на стенах).
     * 2. Размещение 3D скоплений/валиков/палочек снега на горизонтальных швах между блоками.
     */
    private void buildWallSurfaces(ClientWorld level, Vec3d cam, Config config) {
        int camX = MathHelper.floor(cam.x);
        int camY = MathHelper.floor(cam.y);
        int camZ = MathHelper.floor(cam.z);

        MinecraftClient mc = MinecraftClient.getInstance();
        int chunkDist = (mc.options != null && mc.options.getViewDistance() != null)
                ? mc.options.getViewDistance().getValue() : 8;
        // Радиус сканирования стен зависит от дальности прорисовки чанков (16..48 блоков)
        int wallRadius = MathHelper.clamp(chunkDist * 3 + 6, 16, 48);
        int rSq = wallRadius * wallRadius;
        int yMin = Math.max(level.getBottomY(), camY - 8);
        int yMax = Math.min(level.getBottomY() + level.getHeight() - 1, camY + 10);

        for (int dz = -wallRadius; dz <= wallRadius; dz++) {
            int wz = camZ + dz;
            for (int dx = -wallRadius; dx <= wallRadius; dx++) {
                if (dx * dx + dz * dz > rSq) continue;
                int wx = camX + dx;
                if (!level.getChunkManager().isChunkLoaded(wx >> 4, wz >> 4)) continue;

                for (int y = yMin; y <= yMax; y++) {
                    this.wallPos.set(wx, y, wz);
                    BlockState state = level.getBlockState(this.wallPos);
                    if (!isValidWallMaterial(level, this.wallPos, state)) continue;

                    float lx = (float) (wx - this.gridOriginX);
                    float ly = (float) (y - this.gridOriginY);
                    float lz = (float) (wz - this.gridOriginZ);

                    // Проверяем 4 горизонтальные грани блока:
                    // 0: West (-X), 1: East (+X), 2: North (-Z), 3: South (+Z)
                    for (int dir = 0; dir < 4; dir++) {
                        int nx = wx, ny = y, nz = wz;
                        switch (dir) {
                            case 0 -> nx--;
                            case 1 -> nx++;
                            case 2 -> nz--;
                            default -> nz++;
                        }
                        this.airPos.set(nx, ny, nz);
                        BlockState airState = level.getBlockState(this.airPos);
                        if (!airState.isAir() && airState.isOpaqueFullCube()) continue;
                        if (airState.isOf(Blocks.BARRIER) || airState.isOf(Blocks.STRUCTURE_VOID) || airState.isOf(Blocks.LIGHT)) continue;

                        float light = columnLight(level, nx, ny, nz);

                        // 1. Деликатный иней, который окрашивает стены в морозный зимний цвет (без квадратных швов!)
                        if (config.wallFrost) {
                            emitWallFrostFace(dir, wx, y, wz, lx, ly, lz, light);
                        }

                        // 2. Снег в стыках между блоками на стене (объемные кучки/палочки снега)
                        // Спавнится только на стыке двух полных видимых блоков стены (камень, дерево и т.д.)
                        // И только редко, далеко друг от друга по всему миру!
                        if (config.wallSnow) {
                            this.belowPos.set(wx, y - 1, wz);
                            BlockState belowState = level.getBlockState(this.belowPos);
                            if (isValidWallMaterial(level, this.belowPos, belowState)) {
                                this.belowAirPos.set(nx, y - 1, nz);
                                BlockState belowAirState = level.getBlockState(this.belowAirPos);
                                if ((belowAirState.isAir() || !belowAirState.isOpaqueFullCube())
                                        && !belowAirState.isOf(Blocks.BARRIER)
                                        && !belowAirState.isOf(Blocks.STRUCTURE_VOID)
                                        && !belowAirState.isOf(Blocks.LIGHT)) {
                                    if (shouldPlaceSeamSnow(dir, wx, y, wz)) {
                                        emitWallSeamSnowStick(level, dir, wx, y, wz, lx, ly, lz, light);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Деликатное инеевое тонирование вертикальной грани стены:
     * - Окрашивает стены в морозный зимний оттенок.
     * - UV привязаны к мировым координатам, чтобы текстура шла непрерывно
     *   без квадратных границ и видимых рамок блоков.
     */
    private void emitWallFrostFace(int dir, int wx, int y, int wz, float lx, float ly, float lz, float light) {
        float alpha = 0.11f;
        float shade = light * 0.95f;

        float u0 = (dir == 0 || dir == 1 ? wz : wx) * 0.25f;
        float u1 = u0 + 0.25f;
        float v0 = y * 0.25f;
        float v1 = v0 + 0.25f;

        switch (dir) {
            case 0 -> { // West (-X)
                float px = lx - 0.0015f;
                pushQuad7(px, ly, lz, u0, v0, shade, alpha,
                         px, ly + 1.0f, lz, u0, v1, shade * 1.04f, alpha,
                         px, ly + 1.0f, lz + 1.0f, u1, v1, shade * 1.04f, alpha,
                         px, ly, lz + 1.0f, u1, v0, shade, alpha);
            }
            case 1 -> { // East (+X)
                float px = lx + 1.0f + 0.0015f;
                pushQuad7(px, ly, lz + 1.0f, u1, v0, shade, alpha,
                         px, ly + 1.0f, lz + 1.0f, u1, v1, shade * 1.04f, alpha,
                         px, ly + 1.0f, lz, u0, v1, shade * 1.04f, alpha,
                         px, ly, lz, u0, v0, shade, alpha);
            }
            case 2 -> { // North (-Z)
                float pz = lz - 0.0015f;
                pushQuad7(lx + 1.0f, ly, pz, u1, v0, shade, alpha,
                         lx + 1.0f, ly + 1.0f, pz, u1, v1, shade * 1.04f, alpha,
                         lx, ly + 1.0f, pz, u0, v1, shade * 1.04f, alpha,
                         lx, ly, pz, u0, v0, shade, alpha);
            }
            default -> { // South (+Z)
                float pz = lz + 1.0f + 0.0015f;
                pushQuad7(lx, ly, pz, u0, v0, shade, alpha,
                         lx, ly + 1.0f, pz, u0, v1, shade * 1.04f, alpha,
                         lx + 1.0f, ly + 1.0f, pz, u1, v1, shade * 1.04f, alpha,
                         lx + 1.0f, ly, pz, u1, v0, shade, alpha);
            }
        }
    }

    /**
     * Снег в стыках стен спавнится РЕДКО и ДАЛЕКО друг от друга:
     * - Пространство разбито на макро-сегменты по 9 блоков.
     * - Лишь ~18% сегментов получают право на снег (82% стен остаются абсолютно чистыми).
     * - В выбранном сегменте снег занимает компактный очаг всего в 1-2 блока.
     * - Между любыми двумя очагами снега гарантирован большой промежуток (не менее 7-8 блоков).
     * - Хэш привязан к мировым координатам, детерминирован и не мерцает при движении.
     */
    /**
     * Снег в стыках стен спавнится вокруг игрока на разных стенах, но редко:
     * - Пространство разбито на секции по 5 блоков.
     * - ~38% секций получают снег, 62% остаются чистыми.
     * - В выбранной секции снег занимает аккуратный очаг в 1-2 блока.
     * - Хэш учитывает ось вдоль шва, координату перпендикулярной стены, высоту Y и направление dir,
     *   поэтому снег естественным образом появляется на всех стенах вокруг игрока (Север, Юг, Восток, Запад),
     *   а не в одной точке.
     */
    private boolean shouldPlaceSeamSnow(int dir, int wx, int y, int wz) {
        int u = (dir == 0 || dir == 1) ? wz : wx;
        int perp = (dir == 0 || dir == 1) ? wx : wz;
        int cluster = Math.floorDiv(u, 5);
        int seed = (cluster * 73856093) ^ (Math.floorDiv(y, 3) * 19349663) ^ (dir * 83492791);
        float hCluster = hash(seed, perp * 50331653);

        // 62% стен чистые
        if (hCluster > 0.38f) return false;

        // Стартовая позиция очага в секции (1, 2 или 3)
        int patchStart = 1 + ((int) (hCluster * 31.0f)) % 3;
        int posInCluster = Math.floorMod(u, 5);

        // Длина очага: 1 или 2 блока
        boolean twoBlocks = (((int) (hCluster * 1000.0f)) % 3) != 0;
        if (posInCluster == patchStart) return true;
        if (twoBlocks && posInCluster == patchStart + 1) return true;

        return false;
    }

    /**
     * Проверка наличия стыка стены на соседнем блоке (слева/справа вдоль шва).
     */
    private boolean hasSeam(ClientWorld level, int dir, int x, int y, int z) {
        this.seamCheckPos.set(x, y, z);
        BlockState s = level.getBlockState(this.seamCheckPos);
        if (!isValidWallMaterial(level, this.seamCheckPos, s)) return false;

        this.seamCheckPos.set(x, y - 1, z);
        BlockState below = level.getBlockState(this.seamCheckPos);
        if (!isValidWallMaterial(level, this.seamCheckPos, below)) return false;

        int nx = x, nz = z;
        switch (dir) {
            case 0 -> nx--;
            case 1 -> nx++;
            case 2 -> nz--;
            default -> nz++;
        }
        this.seamCheckPos.set(nx, y, nz);
        BlockState front = level.getBlockState(this.seamCheckPos);
        if (!front.isAir() && front.isOpaqueFullCube()) return false;
        if (front.isOf(Blocks.BARRIER) || front.isOf(Blocks.STRUCTURE_VOID) || front.isOf(Blocks.LIGHT)) return false;

        this.seamCheckPos.set(nx, y - 1, nz);
        BlockState belowFront = level.getBlockState(this.seamCheckPos);
        if (!belowFront.isAir() && belowFront.isOpaqueFullCube()) return false;
        if (belowFront.isOf(Blocks.BARRIER) || belowFront.isOf(Blocks.STRUCTURE_VOID) || belowFront.isOf(Blocks.LIGHT)) return false;

        return true;
    }

    /**
     * Органический, скругленный 3D валик/подушка снега в горизонтальном шве между 2 блоками стены:
     * - Высота уменьшена в 2 раза: деликатная, естественная снежная кромка в шве.
     * - Если блоки стены идут подряд слева и справа, сугробы непрерывно соединяются без зазоров.
     * - Каждый блок стены получает свой уникальный стиль сугроба (подушка, карниз, ветровой надув, двойная волна).
     * - Нормали вершин и глубина строго ориентированы наружу, исключая просвечивание низа через верх при виде сверху.
     */
    private void emitWallSeamSnowStick(ClientWorld level, int dir, int wx, int y, int wz,
                                      float lx, float ly, float lz, float light) {
        // Проверяем соседей слева и справа вдоль шва
        int leftX = wx, leftZ = wz;
        int rightX = wx, rightZ = wz;
        if (dir == 0 || dir == 1) { // шов идет вдоль оси Z
            leftZ = wz - 1;
            rightZ = wz + 1;
        } else { // шов идет вдоль оси X
            leftX = wx - 1;
            rightX = wx + 1;
        }

        boolean hasLeft = hasSeam(level, dir, leftX, y, leftZ) && shouldPlaceSeamSnow(dir, leftX, y, leftZ);
        boolean hasRight = hasSeam(level, dir, rightX, y, rightZ) && shouldPlaceSeamSnow(dir, rightX, y, rightZ);

        // Граничные параметры стыков (уменьшены ровно в 2 раза)
        float edgeP0 = hasLeft ? 0.040f : 0.012f;
        float edgeTopH0 = hasLeft ? 0.025f : 0.008f;
        float edgeBotH0 = hasLeft ? 0.020f : 0.008f;
        if (hasLeft) {
            int bX = wx;
            int bZ = wz;
            float hL = hash(bX * 83 + dir * 17, y * 47 + bZ * 101);
            edgeP0 += hL * 0.015f;
            edgeTopH0 += hL * 0.010f;
            edgeBotH0 += hL * 0.008f;
        }

        float edgeP1 = hasRight ? 0.040f : 0.012f;
        float edgeTopH1 = hasRight ? 0.025f : 0.008f;
        float edgeBotH1 = hasRight ? 0.020f : 0.008f;
        if (hasRight) {
            int bX = (dir == 0 || dir == 1) ? wx : wx + 1;
            int bZ = (dir == 0 || dir == 1) ? wz + 1 : wz;
            float hR = hash(bX * 83 + dir * 17, y * 47 + bZ * 101);
            edgeP1 += hR * 0.015f;
            edgeTopH1 += hR * 0.010f;
            edgeBotH1 += hR * 0.008f;
        }

        // Уникальный стиль сугроба для каждого блока (деликатная высота в 2 раза меньше)
        float hBlock = hash(wx * 41 + dir * 19, y * 53 + wz * 79);
        int style = ((int) (hBlock * 100.0f)) % 4;

        float maxMoundP;
        float maxMoundTop;
        float maxMoundBot;
        switch (style) {
            case 0 -> { // Пышная мягкая подушка (Fluffy Cushion)
                maxMoundP = 0.055f + hBlock * 0.018f;
                maxMoundTop = 0.035f + hBlock * 0.012f;
                maxMoundBot = 0.022f + hBlock * 0.010f;
            }
            case 1 -> { // Нависший карниз с провисанием вниз (Drooping Cornice)
                maxMoundP = 0.045f + hBlock * 0.012f;
                maxMoundTop = 0.024f + hBlock * 0.008f;
                maxMoundBot = 0.042f + hBlock * 0.015f;
            }
            case 2 -> { // Ветровой надув со смещенным пиком (Wind Drift)
                maxMoundP = 0.052f + hBlock * 0.015f;
                maxMoundTop = 0.032f + hBlock * 0.010f;
                maxMoundBot = 0.025f + hBlock * 0.010f;
            }
            default -> { // Двугорбый волнистый рельеф (Double Crest)
                maxMoundP = 0.050f + hBlock * 0.012f;
                maxMoundTop = 0.030f + hBlock * 0.010f;
                maxMoundBot = 0.028f + hBlock * 0.010f;
            }
        }

        float lightTop = light * 1.0f;
        float lightFront = light * 0.88f;
        float lightBot = (style == 1) ? light * 0.58f : light * 0.68f;

        float alongStart = hasLeft ? 0.0f : 0.02f;
        float alongEnd = hasRight ? 1.0f : 0.98f;

        final int SEGS = 5;
        for (int s = 0; s < SEGS; s++) {
            float t0 = (float) s / SEGS;
            float t1 = (float) (s + 1) / SEGS;

            float shape0 = evaluateMoundShape(style, t0, hBlock);
            float shape1 = evaluateMoundShape(style, t1, hBlock);

            float along0 = alongStart + t0 * (alongEnd - alongStart);
            float along1 = alongStart + t1 * (alongEnd - alongStart);

            float p0 = (1.0f - t0) * edgeP0 + t0 * edgeP1 + maxMoundP * shape0;
            float p1 = (1.0f - t1) * edgeP0 + t1 * edgeP1 + maxMoundP * shape1;

            float th0 = (1.0f - t0) * edgeTopH0 + t0 * edgeTopH1 + maxMoundTop * shape0;
            float th1 = (1.0f - t1) * edgeTopH0 + t1 * edgeTopH1 + maxMoundTop * shape1;

            float bh0 = (1.0f - t0) * edgeBotH0 + t0 * edgeBotH1 + maxMoundBot * shape0;
            float bh1 = (1.0f - t1) * edgeBotH0 + t1 * edgeBotH1 + maxMoundBot * shape1;

            float u0 = t0;
            float u1 = t1;

            if (dir == 0) { // West (-X)
                float px = lx - 0.002f;
                float xA0 = px, yA0 = ly + th0, z0 = lz + along0;
                float xB0 = px - p0, yB0 = ly + th0 * 0.75f;
                float xC0 = px - p0 * 0.75f, yC0 = ly - bh0 * 0.40f;
                float xD0 = px, yD0 = ly - bh0;

                float xA1 = px, yA1 = ly + th1, z1 = lz + along1;
                float xB1 = px - p1, yB1 = ly + th1 * 0.75f;
                float xC1 = px - p1 * 0.75f, yC1 = ly - bh1 * 0.40f;
                float xD1 = px, yD1 = ly - bh1;

                // Верхняя грань (нормаль +Y, -X): A0 -> B0 -> B1 -> A1
                pushQuad7(xA0, yA0, z0, u0, 0.0f, lightTop, 1.0f,
                         xB0, yB0, z0, u0, 0.35f, lightTop, 1.0f,
                         xB1, yB1, z1, u1, 0.35f, lightTop, 1.0f,
                         xA1, yA1, z1, u1, 0.0f, lightTop, 1.0f);

                // Передняя грань (нормаль -X): B0 -> C0 -> C1 -> B1
                pushQuad7(xB0, yB0, z0, u0, 0.35f, lightFront, 1.0f,
                         xC0, yC0, z0, u0, 0.70f, lightFront, 1.0f,
                         xC1, yC1, z1, u1, 0.70f, lightFront, 1.0f,
                         xB1, yB1, z1, u1, 0.35f, lightFront, 1.0f);

                // Нижняя грань (нормаль -Y, -X): C0 -> D0 -> D1 -> C1
                pushQuad7(xC0, yC0, z0, u0, 0.70f, lightBot, 1.0f,
                         xD0, yD0, z0, u0, 1.0f, lightBot, 1.0f,
                         xD1, yD1, z1, u1, 1.0f, lightBot, 1.0f,
                         xC1, yC1, z1, u1, 0.70f, lightBot, 1.0f);

                if (s == 0 && !hasLeft) {
                    pushQuad7(xA0, yA0, z0, 0.0f, 0.0f, lightFront, 1.0f,
                             xD0, yD0, z0, 0.0f, 1.0f, lightFront, 1.0f,
                             xC0, yC0, z0, 0.5f, 1.0f, lightFront, 1.0f,
                             xB0, yB0, z0, 0.5f, 0.0f, lightFront, 1.0f);
                }
                if (s == SEGS - 1 && !hasRight) {
                    pushQuad7(xA1, yA1, z1, 0.0f, 0.0f, lightFront, 1.0f,
                             xB1, yB1, z1, 0.5f, 0.0f, lightFront, 1.0f,
                             xC1, yC1, z1, 0.5f, 1.0f, lightFront, 1.0f,
                             xD1, yD1, z1, 0.0f, 1.0f, lightFront, 1.0f);
                }

            } else if (dir == 1) { // East (+X)
                float px = lx + 1.0f + 0.002f;
                float xA0 = px, yA0 = ly + th0, z0 = lz + along0;
                float xB0 = px + p0, yB0 = ly + th0 * 0.75f;
                float xC0 = px + p0 * 0.75f, yC0 = ly - bh0 * 0.40f;
                float xD0 = px, yD0 = ly - bh0;

                float xA1 = px, yA1 = ly + th1, z1 = lz + along1;
                float xB1 = px + p1, yB1 = ly + th1 * 0.75f;
                float xC1 = px + p1 * 0.75f, yC1 = ly - bh1 * 0.40f;
                float xD1 = px, yD1 = ly - bh1;

                // Верхняя грань (нормаль +Y, +X): A1 -> B1 -> B0 -> A0
                pushQuad7(xA1, yA1, z1, u1, 0.0f, lightTop, 1.0f,
                         xB1, yB1, z1, u1, 0.35f, lightTop, 1.0f,
                         xB0, yB0, z0, u0, 0.35f, lightTop, 1.0f,
                         xA0, yA0, z0, u0, 0.0f, lightTop, 1.0f);

                // Передняя грань (нормаль +X): B1 -> C1 -> C0 -> B0
                pushQuad7(xB1, yB1, z1, u1, 0.35f, lightFront, 1.0f,
                         xC1, yC1, z1, u1, 0.70f, lightFront, 1.0f,
                         xC0, yC0, z0, u0, 0.70f, lightFront, 1.0f,
                         xB0, yB0, z0, u0, 0.35f, lightFront, 1.0f);

                // Нижняя грань (нормаль -Y, +X): C1 -> D1 -> D0 -> C0
                pushQuad7(xC1, yC1, z1, u1, 0.70f, lightBot, 1.0f,
                         xD1, yD1, z1, u1, 1.0f, lightBot, 1.0f,
                         xD0, yD0, z0, u0, 1.0f, lightBot, 1.0f,
                         xC0, yC0, z0, u0, 0.70f, lightBot, 1.0f);

                if (s == 0 && !hasLeft) {
                    pushQuad7(xA0, yA0, z0, 0.0f, 0.0f, lightFront, 1.0f,
                             xB0, yB0, z0, 0.5f, 0.0f, lightFront, 1.0f,
                             xC0, yC0, z0, 0.5f, 1.0f, lightFront, 1.0f,
                             xD0, yD0, z0, 0.0f, 1.0f, lightFront, 1.0f);
                }
                if (s == SEGS - 1 && !hasRight) {
                    pushQuad7(xA1, yA1, z1, 0.0f, 0.0f, lightFront, 1.0f,
                             xD1, yD1, z1, 0.0f, 1.0f, lightFront, 1.0f,
                             xC1, yC1, z1, 0.5f, 1.0f, lightFront, 1.0f,
                             xB1, yB1, z1, 0.5f, 0.0f, lightFront, 1.0f);
                }

            } else if (dir == 2) { // North (-Z)
                float pz = lz - 0.002f;
                float x0 = lx + along0, yA0 = ly + th0;
                float yB0 = ly + th0 * 0.75f, zB0 = pz - p0;
                float yC0 = ly - bh0 * 0.40f, zC0 = pz - p0 * 0.75f;
                float yD0 = ly - bh0;

                float x1 = lx + along1, yA1 = ly + th1;
                float yB1 = ly + th1 * 0.75f, zB1 = pz - p1;
                float yC1 = ly - bh1 * 0.40f, zC1 = pz - p1 * 0.75f;
                float yD1 = ly - bh1;

                // Верхняя грань (нормаль +Y, -Z): A1 -> B1 -> B0 -> A0
                pushQuad7(x1, yA1, pz, u1, 0.0f, lightTop, 1.0f,
                         x1, yB1, zB1, u1, 0.35f, lightTop, 1.0f,
                         x0, yB0, zB0, u0, 0.35f, lightTop, 1.0f,
                         x0, yA0, pz, u0, 0.0f, lightTop, 1.0f);

                // Передняя грань (нормаль -Z): B1 -> C1 -> C0 -> B0
                pushQuad7(x1, yB1, zB1, u1, 0.35f, lightFront, 1.0f,
                         x1, yC1, zC1, u1, 0.70f, lightFront, 1.0f,
                         x0, yC0, zC0, u0, 0.70f, lightFront, 1.0f,
                         x0, yB0, zB0, u0, 0.35f, lightFront, 1.0f);

                // Нижняя грань (нормаль -Y, -Z): C1 -> D1 -> D0 -> C0
                pushQuad7(x1, yC1, zC1, u1, 0.70f, lightBot, 1.0f,
                         x1, yD1, pz, u1, 1.0f, lightBot, 1.0f,
                         x0, yD0, pz, u0, 1.0f, lightBot, 1.0f,
                         x0, yC0, zC0, u0, 0.70f, lightBot, 1.0f);

                if (s == 0 && !hasLeft) {
                    pushQuad7(x0, yA0, pz, 0.0f, 0.0f, lightFront, 1.0f,
                             x0, yD0, pz, 0.0f, 1.0f, lightFront, 1.0f,
                             x0, yC0, zC0, 0.5f, 1.0f, lightFront, 1.0f,
                             x0, yB0, zB0, 0.5f, 0.0f, lightFront, 1.0f);
                }
                if (s == SEGS - 1 && !hasRight) {
                    pushQuad7(x1, yA1, pz, 0.0f, 0.0f, lightFront, 1.0f,
                             x1, yB1, zB1, 0.5f, 0.0f, lightFront, 1.0f,
                             x1, yC1, zC1, 0.5f, 1.0f, lightFront, 1.0f,
                             x1, yD1, pz, 0.0f, 1.0f, lightFront, 1.0f);
                }

            } else { // South (+Z)
                float pz = lz + 1.0f + 0.002f;
                float x0 = lx + along0, yA0 = ly + th0;
                float yB0 = ly + th0 * 0.75f, zB0 = pz + p0;
                float yC0 = ly - bh0 * 0.40f, zC0 = pz + p0 * 0.75f;
                float yD0 = ly - bh0;

                float x1 = lx + along1, yA1 = ly + th1;
                float yB1 = ly + th1 * 0.75f, zB1 = pz + p1;
                float yC1 = ly - bh1 * 0.40f, zC1 = pz + p1 * 0.75f;
                float yD1 = ly - bh1;

                // Верхняя грань (нормаль +Y, +Z): A0 -> B0 -> B1 -> A1
                pushQuad7(x0, yA0, pz, u0, 0.0f, lightTop, 1.0f,
                         x0, yB0, zB0, u0, 0.35f, lightTop, 1.0f,
                         x1, yB1, zB1, u1, 0.35f, lightTop, 1.0f,
                         x1, yA1, pz, u1, 0.0f, lightTop, 1.0f);

                // Передняя грань (нормаль +Z): B0 -> C0 -> C1 -> B1
                pushQuad7(x0, yB0, zB0, u0, 0.35f, lightFront, 1.0f,
                         x0, yC0, zC0, u0, 0.70f, lightFront, 1.0f,
                         x1, yC1, zC1, u1, 0.70f, lightFront, 1.0f,
                         x1, yB1, zB1, u1, 0.35f, lightFront, 1.0f);

                // Нижняя грань (нормаль -Y, +Z): C0 -> D0 -> D1 -> C1
                pushQuad7(x0, yC0, zC0, u0, 0.70f, lightBot, 1.0f,
                         x0, yD0, pz, u0, 1.0f, lightBot, 1.0f,
                         x1, yD1, pz, u1, 1.0f, lightBot, 1.0f,
                         x1, yC1, zC1, u1, 0.70f, lightBot, 1.0f);

                if (s == 0 && !hasLeft) {
                    pushQuad7(x0, yA0, pz, 0.0f, 0.0f, lightFront, 1.0f,
                             x0, yB0, zB0, 0.5f, 0.0f, lightFront, 1.0f,
                             x0, yC0, zC0, 0.5f, 1.0f, lightFront, 1.0f,
                             x0, yD0, pz, 0.0f, 1.0f, lightFront, 1.0f);
                }
                if (s == SEGS - 1 && !hasRight) {
                    pushQuad7(x1, yA1, pz, 0.0f, 0.0f, lightFront, 1.0f,
                             x1, yD1, pz, 0.0f, 1.0f, lightFront, 1.0f,
                             x1, yC1, zC1, 0.5f, 1.0f, lightFront, 1.0f,
                             x1, yB1, zB1, 0.5f, 0.0f, lightFront, 1.0f);
                }
            }
        }
    }

    private static float evaluateMoundShape(int style, float t, float h) {
        if (t <= 0.0f || t >= 1.0f) return 0.0f;
        float baseSin = (float) Math.sin(t * Math.PI);
        return switch (style) {
            case 0 -> (float) Math.pow(baseSin, 0.65); // Округлая пышная подушка
            case 1 -> (float) (Math.pow(baseSin, 0.80) * (0.85 + 0.15 * Math.sin(t * 3.0 * Math.PI))); // Карниз с провисанием
            case 2 -> { // Ветровой надув (асимметричный)
                float skew = (h > 0.5f) ? (t < 0.35f ? t / 0.35f : (1.0f - t) / 0.65f)
                                        : (t < 0.65f ? t / 0.65f : (1.0f - t) / 0.35f);
                yield (float) Math.pow(MathHelper.clamp(skew, 0.0f, 1.0f), 0.75) * baseSin;
            }
            default -> (float) (0.65 * Math.pow(baseSin, 0.7) + 0.35 * Math.pow(Math.sin(t * 2.0 * Math.PI), 2.0) * baseSin); // Двугорбый
        };
    }

    private void pushQuad7(float x0, float y0, float z0, float u0, float v0, float s0, float a0,
                           float x1, float y1, float z1, float u1, float v1, float s1, float a1,
                           float x2, float y2, float z2, float u2, float v2, float s2, float a2,
                           float x3, float y3, float z3, float u3, float v3, float s3, float a3) {
        if (this.meshQuads >= MAX_GROUND_QUADS) return;
        int need = (this.meshQuads + 1) * FLOATS_PER_QUAD;
        if (this.mesh.length < need) {
            this.mesh = Arrays.copyOf(this.mesh, Math.max(need, this.mesh.length * 2));
        }
        int o = this.meshQuads * FLOATS_PER_QUAD;
        float[] m = this.mesh;
        m[o] = x0; m[o + 1] = y0; m[o + 2] = z0; m[o + 3] = u0; m[o + 4] = v0; m[o + 5] = s0; m[o + 6] = a0;
        m[o + 7] = x1; m[o + 8] = y1; m[o + 9] = z1; m[o + 10] = u1; m[o + 11] = v1; m[o + 12] = s1; m[o + 13] = a1;
        m[o + 14] = x2; m[o + 15] = y2; m[o + 16] = z2; m[o + 17] = u2; m[o + 18] = v2; m[o + 19] = s2; m[o + 20] = a2;
        m[o + 21] = x3; m[o + 22] = y3; m[o + 23] = z3; m[o + 24] = u3; m[o + 25] = v3; m[o + 26] = s3; m[o + 27] = a3;
        this.meshQuads++;
    }

    private void drawGround(MatrixStack matrices, Camera camera, Vec3d cam, Config config, float ambient) {
        if (this.meshQuads <= 0) return;
        float radius = Math.min(config.groundRadius, this.gridRadius - GRID_MARGIN);
        float fadeStart = radius * 0.72f;
        float fadeRange = Math.max(1.0f, radius - fadeStart);
        float sun = MathHelper.clamp(0.55f + ambient * 0.45f, 0.4f, 1.0f);
        float cr = ((config.color >> 16) & 0xFF) / 255.0f;
        float cg = ((config.color >> 8) & 0xFF) / 255.0f;
        float cb = (config.color & 0xFF) / 255.0f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, SNOW_COVER);
        BufferBuilder buffer = Tessellator.getInstance().begin(DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        Matrix4f mat = matrices.peek().getPositionMatrix();
        float ox = (float) (this.gridOriginX - cam.x);
        float oy = (float) (this.gridOriginY - cam.y);
        float oz = (float) (this.gridOriginZ - cam.z);

        int quads = 0;
        float[] m = this.mesh;
        for (int q = 0; q < this.meshQuads; q++) {
            int o = q * FLOATS_PER_QUAD;
            float cx = (m[o] + m[o + 14]) * 0.5f + ox;
            float cz = (m[o + 2] + m[o + 16]) * 0.5f + oz;
            float flat = (float) Math.sqrt(cx * cx + cz * cz);
            if (flat > radius) continue;
            float fade = flat <= fadeStart ? 1.0f : 1.0f - (flat - fadeStart) / fadeRange;
            float baseAlpha = MathHelper.clamp(config.opacity * fade, 0.0f, 1.0f);
            if (baseAlpha <= 0.005f) continue;
            for (int v = 0; v < 4; v++) {
                int p = o + v * 7;
                float shade = MathHelper.clamp(m[p + 5] * sun, 0.0f, 1.0f);
                float r = MathHelper.clamp(cr * shade * 0.97f, 0.0f, 1.0f);
                float g = MathHelper.clamp(cg * (0.03f + shade * 0.97f), 0.0f, 1.0f);
                float b = MathHelper.clamp(cb * (0.16f + shade * 0.86f), 0.0f, 1.0f);
                float finalAlpha = MathHelper.clamp(baseAlpha * m[p + 6], 0.0f, 1.0f);
                buffer.vertex(mat, m[p] + ox, m[p + 1] + oy, m[p + 2] + oz)
                        .texture(m[p + 3], m[p + 4]).color(r, g, b, finalAlpha);
            }
            quads++;
        }
        BuiltBuffer built = buffer.endNullable();
        if (built != null && quads > 0) {
            BufferRenderer.drawWithGlobalProgram(built);
        }

        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
    }

    private double flakeGround(ClientWorld level, int x, int z) {
        try {
            return level.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
        } catch (Throwable ignored) {
            return Double.NEGATIVE_INFINITY;
        }
    }

    private float ambientLight(MinecraftClient mc, Vec3d cam) {
        try {
            int light = mc.world.getLightLevel(BlockPos.ofFloored(cam));
            return MathHelper.clamp(light / 15.0f, 0.25f, 1.0f);
        } catch (Throwable ignored) {
            return 1.0f;
        }
    }

    private float random(float min, float max) {
        return min + this.random.nextFloat() * (max - min);
    }

    private static float hash(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0f;
    }

    private static final class Flake {
        double x, y, z;
        double vx, vy, vz;
        double terminal = 1.0;
        double groundY = Double.NEGATIVE_INFINITY;
        float size = 0.08f;
        float baseAlpha = 1.0f;
        float brightness = 1.0f;
        float swayFrequency = 1.0f;
        float swayAmplitude = 0.4f;
        float phase;
        float spin;
        float angle;
    }
}
