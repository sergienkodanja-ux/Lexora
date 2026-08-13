package com.lexoravisauls.client.utils.models;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public class DogBrain {
   private final MinecraftClient mc = MinecraftClient.getInstance();

   private Vec3d prevPos;
   private Vec3d pos;
   private Vec3d motion = Vec3d.ZERO;
   private Vec3d smoothVel = Vec3d.ZERO;
   private float direction = (float)(Math.random() * 360.0);
   private float smoothBody;
   private float smoothHeadYaw;
   private float smoothHeadPitch;
   private boolean lay;
   private long lastMoveTime = System.currentTimeMillis();
   private AttackPhase attackPhase = AttackPhase.RUNNING_FORWARD;
   private Vec3d attackStrikeTarget = null;
   private boolean didJumpThisPhase = false;
   private double leashVelY = 0.0;
   private int attackTargetId = -1;
   public float prevLimbSwingAmount;
   public float limbSwingAmount;
   public float limbSwing;
   private ClientPlayerEntity entity;
   private LivingEntity attackTarget;

   public void setEntity(ClientPlayerEntity entity) {
      this.entity = entity;
   }

   public void setAttackTarget(LivingEntity target) {
      if (target == null) {
         this.attackTarget = null;
         this.attackTargetId = -1;
         this.attackStrikeTarget = null;
         this.attackPhase = AttackPhase.RUNNING_FORWARD;
         this.didJumpThisPhase = false;
      } else {
         int newId = target.getId();
         if (newId != this.attackTargetId) {
            this.attackTarget = target;
            this.attackTargetId = newId;
            this.attackPhase = AttackPhase.RUNNING_FORWARD;
            this.attackStrikeTarget = null;
            this.didJumpThisPhase = false;
         }
      }
   }

   public LivingEntity getAttackTarget() {
      return this.attackTarget;
   }

   public void onUpdate() {
      if (this.entity != null && mc.world != null) {
         Vec3d playerPos = this.entity.getPos();
         if (this.pos == null) {
            this.pos = playerPos;
            this.prevPos = this.pos;
            this.motion = Vec3d.ZERO;
            this.smoothVel = Vec3d.ZERO;
         } else {
            this.prevPos = this.pos;
            double distToPlayer = this.pos.distanceTo(playerPos);
            if (distToPlayer > 20.0) {
               float angle = (float)Math.toRadians(this.entity.getYaw() + 140.0F);
               this.pos = new Vec3d(playerPos.x + Math.cos(angle) * 1.5, playerPos.y, playerPos.z + Math.sin(angle) * 1.5);
               this.prevPos = this.pos;
               this.motion = Vec3d.ZERO;
               this.smoothVel = Vec3d.ZERO;
            } else {
               boolean onGround = this.isSolidBox(this.pos.x, this.pos.y - 0.1, this.pos.z, 0.25);
               boolean playerHighAbove = !this.entity.isOnGround() && playerPos.y - this.pos.y > 1.5;
               if (!onGround && !playerHighAbove) {
                  this.motion = this.motion.add(0.0, -0.078, 0.0);
               } else if (onGround && this.motion.y < 0.0) {
                  this.motion = new Vec3d(this.motion.x, 0.0, this.motion.z);
               }

               if (this.attackTarget != null) {
                  boolean dead = !this.attackTarget.isAlive() || this.attackTarget.isRemoved();
                  boolean tooFar = playerPos.distanceTo(this.attackTarget.getPos()) > 5.0;
                  boolean dogFar = this.pos.distanceTo(this.attackTarget.getPos()) > 14.0;
                  if (dead || tooFar || dogFar) {
                     this.attackTarget = null;
                     this.attackTargetId = -1;
                     this.attackStrikeTarget = null;
                     this.attackPhase = AttackPhase.RUNNING_FORWARD;
                     this.didJumpThisPhase = false;
                  }
               }

               if (this.attackTarget != null) {
                  this.handleAttackMode(onGround);
               } else if (playerHighAbove) {
                  this.handleFollowFlying(playerPos);
               } else {
                  this.handleFollowPlayer(playerPos, distToPlayer);
               }

               if (!playerHighAbove) {
                  this.motion = new Vec3d(this.motion.x * 0.88, this.motion.y, this.motion.z * 0.88);
                  this.pos = this.moveWithCollision(this.pos, this.motion);
               }

               this.handleRotation();
               this.limbTick();
               if (this.motion.horizontalLengthSquared() > 4.0E-4 || this.attackTarget != null) {
                  this.lastMoveTime = System.currentTimeMillis();
               }

               this.lay = System.currentTimeMillis() - this.lastMoveTime > 2500L;
            }
         }
      }
   }

   private void handleAttackMode(boolean onGround) {
      Vec3d targetPos = this.attackTarget.getPos();
      if (this.attackStrikeTarget == null) {
         Vec3d dir = targetPos.subtract(this.pos).normalize();
         if (this.attackPhase == AttackPhase.RUNNING_FORWARD) {
            this.attackStrikeTarget = targetPos.add(dir.multiply(0.5));
         } else {
            this.attackStrikeTarget = targetPos.subtract(dir.multiply(0.5));
         }
      }

      if (!this.didJumpThisPhase && onGround) {
         this.motion = new Vec3d(this.motion.x, 0.22, this.motion.z);
         this.didJumpThisPhase = true;
      }

      Vec3d toStrike = this.attackStrikeTarget.subtract(this.pos);
      double dist = toStrike.horizontalLength();
      if (dist < 0.2) {
         this.attackPhase = this.attackPhase == AttackPhase.RUNNING_FORWARD ? AttackPhase.RUNNING_BACK : AttackPhase.RUNNING_FORWARD;
         this.didJumpThisPhase = false;
         this.attackStrikeTarget = null;
      } else {
         Vec3d wantedVel = toStrike.normalize().multiply(0.46);
         this.smoothVel = lerpVec(this.smoothVel, wantedVel, 0.2);
         this.motion = new Vec3d(this.smoothVel.x, this.motion.y, this.smoothVel.z);
      }
   }

   private void handleFollowPlayer(Vec3d playerPos, double distToPlayer) {
      this.attackPhase = AttackPhase.RUNNING_FORWARD;
      Vec3d wantedVel;
      if (distToPlayer > 2.5) {
         double speed = distToPlayer > 7.0 ? 0.46 : 0.34;
         Vec3d dir = playerPos.subtract(this.pos).normalize();
         wantedVel = new Vec3d(dir.x * speed, 0.0, dir.z * speed);
      } else if (distToPlayer < 0.6) {
         double xM = -Math.sin(Math.toRadians(this.direction)) * 0.04;
         double zM = Math.cos(Math.toRadians(this.direction)) * 0.04;
         wantedVel = new Vec3d(xM, 0.0, zM);
      } else {
         wantedVel = Vec3d.ZERO;
         this.direction += 0.5F;
      }

      this.smoothVel = lerpVec(this.smoothVel, wantedVel, 0.16);
      this.motion = new Vec3d(this.smoothVel.x, this.motion.y, this.smoothVel.z);
   }

   private void handleFollowFlying(Vec3d playerPos) {
      Vec3d playerMotion = this.entity.getVelocity();
      Vec3d behindOffset = playerMotion.horizontalLength() > 0.02 ? playerMotion.normalize().multiply(-1.2) : Vec3d.ZERO;
      Vec3d anchor = new Vec3d(playerPos.x + behindOffset.x, playerPos.y - 1.6, playerPos.z + behindOffset.z);
      double dist = this.pos.distanceTo(anchor);
      if (dist > 6.0) {
         this.pos = lerpVec(this.pos, anchor, 0.6);
         this.leashVelY = 0.0;
         this.motion = Vec3d.ZERO;
         this.smoothVel = Vec3d.ZERO;
      } else {
         double hDist = Math.sqrt(
            (this.pos.x - anchor.x) * (this.pos.x - anchor.x)
               + (this.pos.z - anchor.z) * (this.pos.z - anchor.z)
         );
         double tH = hDist < 1.5 ? 0.06 : MathHelper.clamp(hDist * 0.045, 0.06, 0.18);
         double nx = this.pos.x + (anchor.x - this.pos.x) * tH;
         double nz = this.pos.z + (anchor.z - this.pos.z) * tH;
         double dy = anchor.y - this.pos.y;
         this.leashVelY -= 0.018;
         this.leashVelY += dy * 0.055;
         this.leashVelY *= 0.8;
         double ny = this.pos.y + this.leashVelY;
         this.pos = new Vec3d(nx, ny, nz);
         this.motion = Vec3d.ZERO;
         this.smoothVel = Vec3d.ZERO;
      }
   }

   private static Vec3d lerpVec(Vec3d from, Vec3d to, double t) {
      return new Vec3d(
         from.x + (to.x - from.x) * t,
         from.y + (to.y - from.y) * t,
         from.z + (to.z - from.z) * t
      );
   }

   private void handleRotation() {
      if (this.attackTarget != null) {
         Vec3d targetPos = this.attackTarget.getPos();
         Vec3d toTarget = targetPos.subtract(this.pos);
         if (toTarget.horizontalLength() > 0.01) {
            float targetBody = (float)Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
            this.smoothBody = this.smoothBody + wrapDegrees(targetBody - this.smoothBody) * 0.35F;
         }
      } else if (this.motion.x != 0.0 || this.motion.z != 0.0) {
         float targetBody = (float)Math.toDegrees(Math.atan2(-this.motion.x, this.motion.z));
         this.smoothBody = this.smoothBody + wrapDegrees(targetBody - this.smoothBody) * 0.15F;
      }

      Vec3d lookAt;
      if (mc.options != null && mc.options.getPerspective().isFirstPerson()) {
         lookAt = mc.player != null ? mc.player.getEyePos() : mc.gameRenderer.getCamera().getPos();
      } else {
         lookAt = mc.gameRenderer.getCamera().getPos();
      }

      Vec3d headPos = this.pos.add(0.0, 0.55, 0.0);
      double dx = lookAt.x - headPos.x;
      double dy = lookAt.y - headPos.y;
      double dz = lookAt.z - headPos.z;
      float worldYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
      float targetHeadPitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
      float targetHeadYaw = wrapDegrees(this.smoothBody - worldYaw);
      float yawLimit = this.lay ? 90.0F : 70.0F;
      targetHeadYaw = MathHelper.clamp(targetHeadYaw, -yawLimit, yawLimit);
      targetHeadPitch = MathHelper.clamp(targetHeadPitch, -30.0F, 30.0F);
      this.smoothHeadYaw = this.smoothHeadYaw + (targetHeadYaw - this.smoothHeadYaw) * 0.18F;
      this.smoothHeadPitch = this.smoothHeadPitch + (targetHeadPitch - this.smoothHeadPitch) * 0.18F;
   }

   private void limbTick() {
      this.prevLimbSwingAmount = this.limbSwingAmount;
      float f = (float)this.motion.horizontalLength() * 4.0F;
      if (f > 1.0F) {
         f = 1.0F;
      }

      this.limbSwingAmount = this.limbSwingAmount + (f - this.limbSwingAmount) * 0.3F;
      this.limbSwing = this.limbSwing + this.limbSwingAmount;
   }

   private Vec3d moveWithCollision(Vec3d current, Vec3d vel) {
      double W = 0.3;
      double H = 0.5;
      double nx = current.x;
      double ny = current.y;
      double nz = current.z;
      double newY = ny + vel.y;
      if (vel.y < 0.0) {
         double groundY = this.findGroundBelow(nx, ny, nz, 0.5);
         if (groundY != -999.0 && newY < groundY) {
            newY = groundY;
            this.motion = new Vec3d(this.motion.x, 0.0, this.motion.z);
         }
      }

      ny = newY;
      double newX = nx + vel.x;
      if (this.isSolidBox(newX, ny + 0.01, nz, 0.3)) {
         double stepY = ny + 1.01;
         if (!this.isSolidBox(newX, stepY + 0.01, nz, 0.3) && !this.isSolidBox(newX, stepY + 0.5 - 0.01, nz, 0.3)) {
            this.motion = new Vec3d(this.motion.x, Math.max(this.motion.y, 0.18), this.motion.z);
         } else {
            newX = nx;
            this.motion = new Vec3d(0.0, this.motion.y, this.motion.z);
         }
      }

      nx = newX;
      double newZ = nz + vel.z;
      if (this.isSolidBox(nx, ny + 0.01, newZ, 0.3)) {
         double stepY = ny + 1.01;
         if (!this.isSolidBox(nx, stepY + 0.01, newZ, 0.3) && !this.isSolidBox(nx, stepY + 0.5 - 0.01, newZ, 0.3)) {
            this.motion = new Vec3d(this.motion.x, Math.max(this.motion.y, 0.18), this.motion.z);
         } else {
            newZ = nz;
            this.motion = new Vec3d(this.motion.x, this.motion.y, 0.0);
         }
      }

      nz = newZ;
      double groundSnap = this.findGroundBelow(nx, ny + 0.3, nz, 0.5);
      if (groundSnap != -999.0 && ny < groundSnap) {
         ny += Math.min(groundSnap - ny, 0.15);
         if (this.motion.y < 0.0) {
            this.motion = new Vec3d(this.motion.x, 0.0, this.motion.z);
         }
      }

      return new Vec3d(nx, ny, nz);
   }

   private double findGroundBelow(double x, double y, double z, double height) {
      if (mc.world == null) {
         return -999.0;
      }

      for (double dy = 0.0; dy <= 4.0; dy += 0.5) {
         double checkY = y - dy;
         BlockPos bp = BlockPos.ofFloored(x, checkY - 0.1, z);
         if (!mc.world.getBlockState(bp).getCollisionShape(mc.world, bp).isEmpty()) {
            return bp.getY() + 1.0;
         }
      }

      return -999.0;
   }

   private boolean isSolidBox(double x, double y, double z, double w) {
      if (mc.world == null) {
         return false;
      }

      double[] offsets = new double[]{-w, w};

      for (double dx : offsets) {
         for (double dz : offsets) {
            BlockPos bp = BlockPos.ofFloored(x + dx, y, z + dz);
            if (!mc.world.getBlockState(bp).getCollisionShape(mc.world, bp).isEmpty()) {
               return true;
            }
         }
      }

      return false;
   }

   private static float wrapDegrees(float deg) {
      deg %= 360.0F;
      if (deg >= 180.0F) {
         deg -= 360.0F;
      }

      if (deg < -180.0F) {
         deg += 360.0F;
      }

      return deg;
   }

   public Vec3d getPos(float tickDelta) {
      if (this.prevPos != null && this.pos != null) {
         return new Vec3d(
            MathHelper.lerp(tickDelta, this.prevPos.x, this.pos.x),
            MathHelper.lerp(tickDelta, this.prevPos.y, this.pos.y),
            MathHelper.lerp(tickDelta, this.prevPos.z, this.pos.z)
         );
      } else {
         return this.pos != null ? this.pos : Vec3d.ZERO;
      }
   }

   public Vec3d getPos() {
      return this.pos != null ? this.pos : Vec3d.ZERO;
   }

   public float getBody() {
      return this.smoothBody;
   }

   public float getYaw() {
      return this.smoothHeadYaw;
   }

   public float getPitch() {
      return this.smoothHeadPitch;
   }

   public boolean isLay() {
      return this.lay;
   }

   @Environment(EnvType.CLIENT)
   enum AttackPhase {
      RUNNING_FORWARD,
      RUNNING_BACK;
   }
}
