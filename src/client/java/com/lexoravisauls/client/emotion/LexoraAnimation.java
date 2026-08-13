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
    public float returnTime = 0f;
    public Map<String, BoneAnimation> bones = new HashMap<>();

    private static final float WAIST_Y = 12.0f;
    private static final float LOOP_BLEND_TIME = 0.20f; // 200мс кросс-фейд при перезапуске зацикленного танца

    public void apply(BipedEntityModel<?> model, float timeInSeconds, float weight) {
        float blendWeight = 1.0f;

        // Плавный переход на стыке кругов танца
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

        // 2. ГОЛОВА (HEAD) - Наследует вращение груди
        applyChild(model.head, bodyRot, bodyT, headT, new Vector3f(0f, -WAIST_Y, 0f), finalWeight);

        // 3. ПРАВАЯ РУКА (RIGHT ARM)
        applyChild(model.rightArm, bodyRot, bodyT, rArmT, new Vector3f(-5f, 2f - WAIST_Y, 0f), finalWeight);

        // 4. ЛЕВАЯ РУКА (LEFT ARM)
        applyChild(model.leftArm, bodyRot, bodyT, lArmT, new Vector3f(5f, 2f - WAIST_Y, 0f), finalWeight);

        // 5. ПРАВАЯ НОГА (RIGHT LEG) - Ноги стоят ровно при поклонах
        blendPart(model.rightLeg, rLegT.rotX, rLegT.rotY, rLegT.rotZ,
                -1.9f + bodyT.posX + rLegT.posX, WAIST_Y + bodyT.posY + rLegT.posY, 0.0f + bodyT.posZ + rLegT.posZ, finalWeight);

        // 6. ЛЕВАЯ НОГА (LEFT LEG) - Ноги стоят ровно при поклонах
        blendPart(model.leftLeg, lLegT.rotX, lLegT.rotY, lLegT.rotZ,
                1.9f + bodyT.posX + lLegT.posX, WAIST_Y + bodyT.posY + lLegT.posY, 0.0f + bodyT.posZ + lLegT.posZ, finalWeight);
    }

    private void applyChild(ModelPart part, Quaternionf bodyRot, BoneTransform bodyT, BoneTransform childT, Vector3f offsetFromWaist, float weight) {
        float targetPitch = bodyT.rotX + childT.rotX;
        float targetYaw   = bodyT.rotY + childT.rotY;
        float targetRoll  = bodyT.rotZ + childT.rotZ;

        Vector3f rotatedOffset = new Vector3f(offsetFromWaist);
        bodyRot.transform(rotatedOffset);

        float targetPivotX = rotatedOffset.x + bodyT.posX + childT.posX;
        float targetPivotY = WAIST_Y + rotatedOffset.y + bodyT.posY + childT.posY;
        float targetPivotZ = rotatedOffset.z + bodyT.posZ + childT.posZ;

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

        return new Vec3f(
                lerp(prev.x, next.x, delta),
                lerp(prev.y, next.y, delta),
                lerp(prev.z, next.z, delta)
        );
    }

    private float lerp(float start, float end, float delta) {
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

        public Keyframe(float time, float x, float y, float z) {
            this.time = time;
            this.x = x;
            this.y = y;
            this.z = z;
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