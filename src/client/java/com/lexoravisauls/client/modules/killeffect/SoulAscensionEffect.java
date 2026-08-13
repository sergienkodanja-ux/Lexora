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
 * gently, fading out. Bare — no armor, no held items, no nametag. Renders
 * through walls/terrain, matching the other two effects.
 * <p>
 * Every frame: re-clears equipment, re-asserts the hidden-nametag team
 * membership, and re-captures the render state fresh from OUR OWN throwaway
 * ghost entity — redundant on purpose, see git history for why.
 * <p>
 * Transparency: LivingEntityRenderer#getRenderLayer takes a `translucent`
 * boolean that's the real, official switch between the normal and
 * translucent render layer (see RenderLayerGhostMixin) — forced to true only
 * for the exact duration of this render() call via KillEffectGhostState.
 */
public class SoulAscensionEffect implements KillEffect<SoulAscensionEffect.State> {

    private static final String HIDDEN_NAMETAG_TEAM = "lexora_hide_nametag";
    private static int nextFakeId = -200000;

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

        RenderSystem.disableDepthTest(); // render through walls/terrain, matching the other two effects
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);

        EntityRenderDispatcher dispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
        EntityRenderer renderer = dispatcher.getRenderer(state.ghost);

        KillEffectGhostState.RENDERING = true;
        try {
            renderFresh(renderer, state.ghost, matrices, consumers);
            // Force this geometry to actually draw NOW instead of sitting
            // queued in the shared buffer until something else (like a
            // second ghost) happens to trigger a flush later. That deferred-
            // flush behavior is exactly what made a ghost only "become"
            // transparent once a second one spawned — its translucent-layer
            // geometry was queued correctly but never actually drawn until
            // something else forced the buffer to flush.
            if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
                immediate.draw();
            }
        } finally {
            KillEffectGhostState.RENDERING = false;
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        matrices.pop();
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
