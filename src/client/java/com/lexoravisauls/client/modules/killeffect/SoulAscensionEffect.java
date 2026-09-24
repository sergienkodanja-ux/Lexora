package com.lexoravisauls.client.modules.killeffect;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.Random;
import java.util.UUID;

/**
 * A frozen, motionless copy of the victim's own skin appears half-transparent
 * over the death spot, standing level, and drifts upward while spinning
 * gently, fading out. Bare — no armor, no held items, no nametag.
 * <p>
 * Uses an ISOLATED BufferAllocator / VertexConsumerProvider.Immediate so that
 * Minecraft's global entity consumer is NEVER flushed prematurely or drawn with
 * alpha < 1, which previously caused dropped items and surrounding entities
 * to become transparent.
 */
public class SoulAscensionEffect implements KillEffect<SoulAscensionEffect.State> {

    private static final String HIDDEN_NAMETAG_TEAM = "lexora_hide_nametag";
    private static int nextFakeId = -200000;

    private static final BufferAllocator GHOST_ALLOCATOR = new BufferAllocator(262144);
    private static final VertexConsumerProvider.Immediate GHOST_CONSUMERS = VertexConsumerProvider.immediate(GHOST_ALLOCATOR);

    @Override
    public State onStart(LivingEntity victim, double x, double y, double z, Random random) {
        float durationSeconds = KillEffectSettings.slider("Ascension Duration", 3.0f);
        float height = KillEffectSettings.slider("Ascension Height", 2.6f);
        float spinSpeed = KillEffectSettings.slider("Ascension Spin Speed", 0.0f);
        int durationTicks = Math.max(1, Math.round(durationSeconds * 20.0f));

        int fakeId = nextFakeId--;
        String fakeName = "~gh" + Math.abs(fakeId);

        GameProfile ghostProfile = new GameProfile(UUID.randomUUID(), fakeName);
        if (victim instanceof AbstractClientPlayerEntity clientPlayer) {
            ghostProfile.getProperties().putAll(clientPlayer.getGameProfile().getProperties());
        }

        OtherClientPlayerEntity ghost = new OtherClientPlayerEntity((ClientWorld) victim.getWorld(), ghostProfile);
        ghost.setId(fakeId);
        ghost.refreshPositionAndAngles(victim.getX(), victim.getY(), victim.getZ(), victim.getYaw(), 0.0f);
        ghost.bodyYaw = victim.bodyYaw;
        ghost.headYaw = victim.headYaw;

        return new State(ghost, fakeName, x, y, z, random.nextFloat() * 360.0f,
                durationTicks, height, spinSpeed);
    }

    @Override
    public void render(State state, MatrixStack matrices, Camera camera, VertexConsumerProvider consumers,
                        Vec3d camPos, float progress, float ageWithDelta, Random random) {
        double eased = progress * progress * (3.0 - 2.0 * progress); // smoothstep rise
        double riseY = eased * state.riseHeight;

        float fadeIn = MathHelper.clamp(progress / 0.12f, 0.0f, 1.0f);
        float fadeOut = 1.0f - MathHelper.clamp((progress - 0.6f) / 0.4f, 0.0f, 1.0f);
        float alpha = 0.55f * fadeIn * fadeOut; // 0.55 cap = "half-transparent" even at peak

        if (alpha <= 0.01f) {
            return;
        }

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            state.ghost.equipStack(slot, ItemStack.EMPTY);
        }
        hideNameTag(state.ghost, state.fakeName);

        float angle = state.baseAngle + ageWithDelta * state.spinSpeed;

        matrices.push();
        matrices.translate(state.x - camPos.x, state.y + riseY - camPos.y, state.z - camPos.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);

        EntityRenderDispatcher dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
        EntityRenderer renderer = dispatcher.getRenderer(state.ghost);

        KillEffectGhostState.RENDERING = true;
        try {
            renderFresh(renderer, state.ghost, matrices, GHOST_CONSUMERS);
            GHOST_CONSUMERS.draw();
        } finally {
            KillEffectGhostState.RENDERING = false;
            RenderSystem.depthMask(true);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();
        }

        matrices.pop();

        // 1. Custom GLSL Ethereal Soul Outline Shader (Subtle silhouette contour, 2x lighter)
        if (alpha > 0.02f) {
            KillEffectShaders.renderSpiritAura(matrices, camera,
                    state.x - camPos.x, state.y + riseY + 1.0 - camPos.y, state.z - camPos.z,
                    1.9f, 2.4f,
                    ageWithDelta * 0.05f, progress, alpha,
                    0.25f, 0.85f, 1.0f, 0.65f, 0.35f, 1.0f);

            KillEffectDraw.beginGlow(true);

            // 2. Solid 3D Nimb with mass (exact form from NimbRenderer, static, no animation)
            double haloY = state.y + riseY + 2.05 - camPos.y;
            KillEffectDraw.drawSolidNimb(matrices,
                    state.x - camPos.x, haloY, state.z - camPos.z,
                    0.38f, 0.028f, 0.022f,
                    1.0f, 0.88f, 0.28f, alpha * 0.95f);

            // 3. Ground departure ripple ring at the spot of death
            float groundWave = (progress * 1.5f) % 1.0f;
            float groundR = groundWave * 1.5f;
            float groundA = (1.0f - groundWave) * alpha * 0.5f;
            KillEffectDraw.drawRing(matrices,
                    state.x - camPos.x, state.y - camPos.y + 0.02, state.z - camPos.z,
                    groundR, 0.04f,
                    0.0f, 0.0f, 0.0f,
                    0.25f, 0.8f, 1.0f, groundA, 36);

            KillEffectDraw.endGlow();
        }
    }

    private static void hideNameTag(LivingEntity ghost, String fakeName) {
        Scoreboard scoreboard = ghost.getWorld().getScoreboard();
        Team team = scoreboard.getTeam(HIDDEN_NAMETAG_TEAM);
        if (team == null) {
            team = scoreboard.addTeam(HIDDEN_NAMETAG_TEAM);
            team.setNameTagVisibilityRule(AbstractTeam.VisibilityRule.NEVER);
        }
        scoreboard.addScoreHolderToTeam(fakeName, team);
    }

    @Override
    public int getDurationTicks(State state) {
        return state.durationTicks;
    }

    @SuppressWarnings("unchecked")
    private static <T extends LivingEntity, S extends EntityRenderState> void renderFresh(
            EntityRenderer renderer, LivingEntity ghost, MatrixStack matrices, VertexConsumerProvider consumers) {
        EntityRenderer<T, S> typed = (EntityRenderer<T, S>) renderer;
        T typedGhost = (T) ghost;
        S renderState = typed.getAndUpdateRenderState(typedGhost, 1.0f);
        typed.render(renderState, matrices, consumers, LightmapTextureManager.pack(15, 15));
    }

    static final class State {
        final OtherClientPlayerEntity ghost;
        final String fakeName;
        final double x, y, z;
        final float baseAngle;
        final int durationTicks;
        final double riseHeight;
        final float spinSpeed;

        State(OtherClientPlayerEntity ghost, String fakeName, double x, double y, double z, float baseAngle,
              int durationTicks, double riseHeight, float spinSpeed) {
            this.ghost = ghost;
            this.fakeName = fakeName;
            this.x = x;
            this.y = y;
            this.z = z;
            this.baseAngle = baseAngle;
            this.durationTicks = durationTicks;
            this.riseHeight = riseHeight;
            this.spinSpeed = spinSpeed;
        }
    }
}
