package com.lexoravisauls.client.emotion;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LexoraAnimation {
    public float length;
    public boolean isLoop = false;
    public boolean isEmotecraft = false;
    public float returnTime = 0f;
    public Map<String, BoneAnimation> bones = new HashMap<>();

    private static final float WAIST_Y = 12.0f;
    private static final float LOOP_BLEND_TIME = 0.20f;

    public void apply(BipedEntityModel<?> model, float timeInSeconds, float weight) {
        float blendWeight = 1.0f;

        if (isLoop && length > 0f) {
            if (timeInSeconds >= length) {
                float duration = length - returnTime;
                if (duration > 0f) {
                    float cycleTime = returnTime + ((timeInSeconds - returnTime) % duration);
                    float timeInCycle = (timeInSeconds - returnTime) % duration;
                    if (timeInCycle < LOOP_BLEND_TIME) {
                        blendWeight = timeInCycle / LOOP_BLEND_TIME;
                    }
                    timeInSeconds = cycleTime;
                } else {
                    timeInSeconds = timeInSeconds % length;
                }
            }
        }

        float finalWeight = weight * blendWeight;

        BoneTransform bodyT = getTransform(bones.get("body"), timeInSeconds);
        BoneTransform headT = getTransform(bones.get("head"), timeInSeconds);
        BoneTransform rArmT = getTransform(bones.get("right_arm"), timeInSeconds);
        BoneTransform lArmT = getTransform(bones.get("left_arm"), timeInSeconds);
        BoneTransform rLegT = getTransform(bones.get("right_leg"), timeInSeconds);
        BoneTransform lLegT = getTransform(bones.get("left_leg"), timeInSeconds);

        Quaternionf bodyRot = new Quaternionf().rotateZYX(bodyT.rotZ, bodyT.rotY, bodyT.rotX);

        Vector3f waistOffset = new Vector3f(0f, -WAIST_Y, 0f);
        bodyRot.transform(waistOffset);

        float targetBodyX = waistOffset.x + bodyT.posX;
        float targetBodyY = WAIST_Y + waistOffset.y + bodyT.posY;
        float targetBodyZ = waistOffset.z + bodyT.posZ;

        // 1. ТОРС (BODY)
        blendPart(model.body, bodyT.rotX, bodyT.rotY, bodyT.rotZ, targetBodyX, targetBodyY, targetBodyZ, finalWeight);

        // 2. ГОЛОВА (HEAD)
        applyChild(model.head, bodyRot, bodyT, headT, new Vector3f(0f, -WAIST_Y, 0f), finalWeight, isEmotecraft);

        // 3. ПРАВАЯ РУКА (RIGHT ARM)
        applyChild(model.rightArm, bodyRot, bodyT, rArmT, new Vector3f(-5f, 2f - WAIST_Y, 0f), finalWeight, isEmotecraft);

        // 4. ЛЕВАЯ РУКА (LEFT ARM)
        applyChild(model.leftArm, bodyRot, bodyT, lArmT, new Vector3f(5f, 2f - WAIST_Y, 0f), finalWeight, isEmotecraft);

        // 5. НОГИ (Поворачиваются вместе с телом при разворотах танца)
        if (isEmotecraft) {
            // В Emotecraft ноги прикреплены к телу и крутятся при разворотах
            applyLeg(model.rightLeg, bodyRot, bodyT, rLegT, new Vector3f(-1.9f, 0f, 0f), finalWeight);
            applyLeg(model.leftLeg,  bodyRot, bodyT, lLegT, new Vector3f(1.9f, 0f, 0f), finalWeight);
        } else {
            // В Bedrock (поклоны) ноги остаются на месте
            blendPart(model.rightLeg, rLegT.rotX, rLegT.rotY, rLegT.rotZ,
                    -1.9f + bodyT.posX + rLegT.posX, WAIST_Y + bodyT.posY + rLegT.posY, bodyT.posZ + rLegT.posZ, finalWeight);
            blendPart(model.leftLeg, lLegT.rotX, lLegT.rotY, lLegT.rotZ,
                    1.9f + bodyT.posX + lLegT.posX, WAIST_Y + bodyT.posY + lLegT.posY, bodyT.posZ + lLegT.posZ, finalWeight);
        }
    }

    private void applyChild(ModelPart part, Quaternionf bodyRot, BoneTransform bodyT, BoneTransform childT, Vector3f offsetFromWaist, float weight, boolean isAbsolute) {
        float targetPitch = bodyT.rotX + childT.rotX;
        float targetYaw   = bodyT.rotY + childT.rotY;
        float targetRoll  = bodyT.rotZ + childT.rotZ;

        Vector3f rotatedOffset = new Vector3f(offsetFromWaist);
        bodyRot.transform(rotatedOffset);

        float targetPivotX;
        float targetPivotY;
        float targetPivotZ;

        if (isAbsolute) {
            // Emotecraft: смещение ребенка не суммируется с телом повторно
            targetPivotX = rotatedOffset.x + childT.posX;
            targetPivotY = WAIST_Y + rotatedOffset.y + childT.posY;
            targetPivotZ = rotatedOffset.z + childT.posZ;
        } else {
            // Bedrock: иерархическое смещение
            targetPivotX = rotatedOffset.x + bodyT.posX + childT.posX;
            targetPivotY = WAIST_Y + rotatedOffset.y + bodyT.posY + childT.posY;
            targetPivotZ = rotatedOffset.z + bodyT.posZ + childT.posZ;
        }

        blendPart(part, targetPitch, targetYaw, targetRoll, targetPivotX, targetPivotY, targetPivotZ, weight);
    }

    private void applyLeg(ModelPart part, Quaternionf bodyRot, BoneTransform bodyT, BoneTransform legT, Vector3f hipOffset, float weight) {
        float targetPitch = legT.rotX;
        float targetYaw   = bodyT.rotY + legT.rotY;
        float targetRoll  = legT.rotZ;

        Vector3f rotatedHip = new Vector3f(hipOffset);
        bodyRot.transform(rotatedHip);

        float targetPivotX = rotatedHip.x + legT.posX;
        float targetPivotY = WAIST_Y + rotatedHip.y + legT.posY;
        float targetPivotZ = rotatedHip.z + legT.posZ;

        blendPart(part, targetPitch, targetYaw, targetRoll, targetPivotX, targetPivotY, targetPivotZ, weight);
    }

    private void blendPart(ModelPart part, float targetPitch, float targetYaw, float targetRoll, float targetX, float targetY, float targetZ, float weight) {
        if (weight >= 1.0f) {
            part.pitch  = targetPitch;
            part.yaw    = targetYaw;
            part.roll   = targetRoll;
            part.pivotX = targetX;
            part.pivotY = targetY;
            part.pivotZ = targetZ;
        } else {
            part.pitch  = lerp(part.pitch,  targetPitch, weight);
            part.yaw    = lerp(part.yaw,    targetYaw,   weight);
            part.roll   = lerp(part.roll,   targetRoll,  weight);
            part.pivotX = lerp(part.pivotX, targetX,     weight);
            part.pivotY = lerp(part.pivotY, targetY,     weight);
            part.pivotZ = lerp(part.pivotZ, targetZ,     weight);
        }
    }

    private BoneTransform getTransform(BoneAnimation anim, float time) {
        BoneTransform t = new BoneTransform();
        if (anim == null) return t;

        if (!anim.rotationKeyframes.isEmpty()) {
            Vec3f rot = interpolate(anim.rotationKeyframes, time);
            t.rotX = (float) Math.toRadians(rot.x);
            t.rotY = (float) Math.toRadians(rot.y);
            t.rotZ = (float) Math.toRadians(rot.z);
        }

        if (!anim.positionKeyframes.isEmpty()) {
            Vec3f pos = interpolate(anim.positionKeyframes, time);
            t.posX = pos.x;
            t.posY = pos.y;
            t.posZ = pos.z;
        }

        return t;
    }

    private Vec3f interpolate(List<Keyframe> keyframes, float time) {
        if (keyframes.isEmpty()) return new Vec3f(0, 0, 0);

        if (time <= keyframes.get(0).time) {
            Keyframe k = keyframes.get(0);
            return new Vec3f(k.x, k.y, k.z);
        }
        if (time >= keyframes.get(keyframes.size() - 1).time) {
            Keyframe k = keyframes.get(keyframes.size() - 1);
            return new Vec3f(k.x, k.y, k.z);
        }

        Keyframe prev = keyframes.get(0);
        Keyframe next = keyframes.get(keyframes.size() - 1);

        for (int i = 0; i < keyframes.size() - 1; i++) {
            if (time >= keyframes.get(i).time && time < keyframes.get(i + 1).time) {
                prev = keyframes.get(i);
                next = keyframes.get(i + 1);
                break;
            }
        }

        float delta = 0f;
        if (next.time > prev.time) {
            delta = (time - prev.time) / (next.time - prev.time);
        }

        float easedDelta = applyEasing(prev.easing, delta);

        return new Vec3f(
                lerp(prev.x, next.x, easedDelta),
                lerp(prev.y, next.y, easedDelta),
                lerp(prev.z, next.z, easedDelta)
        );
    }

    public static float applyEasing(String easing, float t) {
        if (easing == null) return t;
        String e = easing.toUpperCase();

        if (e.contains("EASEINOUTQUAD")) {
            return t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;
        } else if (e.contains("EASEINQUAD")) {
            return t * t;
        } else if (e.contains("EASEOUTQUAD")) {
            return 1f - (1f - t) * (1f - t);
        } else if (e.contains("EASEINOUTSINE") || e.contains("EASEINOUTSINT")) {
            return -(float) (Math.cos(Math.PI * t) - 1.0) / 2.0f;
        }

        return t; // LINEAR
    }

    private static float lerp(float start, float end, float delta) {
        return start + (end - start) * delta;
    }

    public static class BoneTransform {
        public float rotX, rotY, rotZ;
        public float posX, posY, posZ;
    }

    public static class BoneAnimation {
        public List<Keyframe> rotationKeyframes = new ArrayList<>();
        public List<Keyframe> positionKeyframes = new ArrayList<>();
    }

    public static class Keyframe {
        public float time;
        public float x, y, z;
        public String easing = "LINEAR";

        public Keyframe(float time, float x, float y, float z, String easing) {
            this.time = time;
            this.x = x;
            this.y = y;
            this.z = z;
            this.easing = easing != null ? easing : "LINEAR";
        }
    }

    public static class Vec3f {
        public float x, y, z;

        public Vec3f(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}