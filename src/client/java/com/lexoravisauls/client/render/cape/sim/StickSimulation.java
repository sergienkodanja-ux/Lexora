package com.lexoravisauls.client.render.cape.sim;

import net.minecraft.util.math.MathHelper;
import java.util.ArrayList;
import java.util.List;

public class StickSimulation {
    public List<Point> points      = new ArrayList<>();
    public List<Stick> sticks      = new ArrayList<>();
    public Vector3 gravityDirection = new Vector3(0.0f, -1.0f, 0.0f);
    public float gravity            = 25.0f;
    public int numIterations        = 4;
    private final float maxBend     = 20.0f;
    private boolean sneaking        = false;

    // Внешние силы на каждую точку (заполняются ветром каждый тик)
    // Индекс совпадает с индексом points
    public float[] windForceX = new float[0];
    public float[] windForceY = new float[0];
    public float[] windForceZ = new float[0];

    public boolean init(int partCount) {
        if (this.points.size() != partCount) {
            this.points.clear();
            this.sticks.clear();
            for (int i = 0; i < partCount; i++) {
                Point point = new Point();
                point.position.y    = -i;
                point.position.x    = -i;
                point.locked        = (i == 0);
                this.points.add(point);
                if (i > 0) {
                    this.sticks.add(new Stick(this.points.get(i - 1), point, 1.0f));
                }
            }
            // Инициализируем массивы сил
            windForceX = new float[partCount];
            windForceY = new float[partCount];
            windForceZ = new float[partCount];
            return true;
        }
        // Если размер не изменился но массивы сил не инициализированы
        if (windForceX.length != partCount) {
            windForceX = new float[partCount];
            windForceY = new float[partCount];
            windForceZ = new float[partCount];
        }
        return false;
    }

    public void simulate() {
        applyGravity();
        preventClipping();
        applyMotion();
        preventHardBends();
        limitLength();
    }

    private void applyGravity() {
        float deltaTime = 0.05f;
        Vector3 down = this.gravityDirection.clone().mul(this.gravity * deltaTime);
        Vector3 tmp  = new Vector3(0, 0, 0);

        for (int i = 0; i < this.points.size(); i++) {
            Point p = this.points.get(i);
            if (!p.locked) {
                tmp.copy(p.position);

                // Гравитация
                p.position.add(down.clone());

                // Внешняя сила ветра применяется здесь — вместе с гравитацией,
                // до всех constraint-решателей. Это значит она пройдёт через
                // всю физику верёвки и создаст настоящую волну.
                if (i < windForceX.length) {
                    p.position.x += windForceX[i];
                    p.position.y += windForceY[i];
                    p.position.z += windForceZ[i];
                }

                p.prevPosition.copy(tmp);
            }
        }
    }

    private void applyMotion() {
        for (int i = 0; i < this.numIterations; i++) {
            for (int x = this.sticks.size() - 1; x >= 0; x--) {
                Stick stick = this.sticks.get(x);
                Vector3 centre = stick.pointA.position.clone()
                        .add(stick.pointB.position).div(2.0f);
                Vector3 dir = stick.pointA.position.clone()
                        .subtract(stick.pointB.position).normalize();
                if (!stick.pointA.locked) {
                    stick.pointA.position = centre.clone()
                            .add(dir.clone().mul(stick.length / 2.0f));
                }
                if (!stick.pointB.locked) {
                    stick.pointB.position = centre.clone()
                            .subtract(dir.clone().mul(stick.length / 2.0f));
                }
            }
        }
    }

    private void limitLength() {
        for (int x = 0; x < this.sticks.size(); x++) {
            Stick stick = this.sticks.get(x);
            Vector3 dir = stick.pointA.position.clone()
                    .subtract(stick.pointB.position).normalize();
            if (!stick.pointB.locked) {
                stick.pointB.position = stick.pointA.position.clone()
                        .subtract(dir.mul(stick.length));
            }
        }
    }

    private void preventSelfClipping() {
        boolean clipped;
        int runs = 0;
        do {
            clipped = false;
            for (int a = 0; a < this.points.size(); a++) {
                for (int b = a + 1; b < this.points.size(); b++) {
                    Point pA = this.points.get(a);
                    Point pB = this.points.get(b);
                    Vector3 dir = pA.position.clone().subtract(pB.position);
                    if (dir.sqrMagnitude() < 0.99f) {
                        clipped = true;
                        runs++;
                        dir.normalize();
                        Vector3 centre = pA.position.clone()
                                .add(pB.position).div(2.0f);
                        if (!pA.locked) pA.position = centre.clone()
                                .add(dir.clone().mul(0.5f));
                        if (!pB.locked) pB.position = centre.clone()
                                .subtract(dir.clone().mul(0.5f));
                    }
                }
            }
        } while (clipped && runs < 32);
    }

    private void preventHardBends() {
        for (int i = 1; i < this.points.size() - 2; i++) {
            double angle = getAngle(
                    this.points.get(i).position,
                    this.points.get(i - 1).position,
                    this.points.get(i + 1).position);
            if (angle < -this.maxBend) {
                this.points.get(i + 1).position = getReplacement(
                        this.points.get(i).position,
                        this.points.get(i - 1).position,
                        -this.maxBend * 2.0f);
            }
            if (angle > this.maxBend) {
                this.points.get(i + 1).position = getReplacement(
                        this.points.get(i).position,
                        this.points.get(i - 1).position,
                        this.maxBend * 2.0f);
            }
        }
    }

    private void preventClipping() {
        Point basePoint = this.points.get(0);
        for (int i = 1; i < this.points.size(); i++) {
            Point p = this.points.get(i);
            if (p.position.x - basePoint.position.x > 0.0f) {
                p.position.x = basePoint.position.x;
            }
            float maxZ = i / (float) this.points.size()
                    * (i / (float) this.points.size()) * 5.0f;
            float z = basePoint.position.z - p.position.z;
            if (z >  maxZ) p.position.z = basePoint.position.z - maxZ;
            if (z < -maxZ) p.position.z = basePoint.position.z + maxZ;
        }
    }

    private Vector3 getReplacement(Vector3 middle, Vector3 prev, double target) {
        Vector3 dir = middle.clone().subtract(prev);
        dir.rotateDegrees((float) target).add(middle);
        return dir;
    }

    private double getAngle(Vector3 a, Vector3 b, Vector3 c) {
        float abx = b.x - a.x, aby = b.y - a.y;
        float cbx = b.x - c.x, cby = b.y - c.y;
        float dot   = abx * cbx + aby * cby;
        float cross = abx * cby - aby * cbx;
        double alpha = MathHelper.atan2(cross, dot);
        return alpha * 180.0 / Math.PI;
    }

    public void setGravityDirection(Vector3 dir) { this.gravityDirection = dir; }
    public void setGravity(float gravity)         { this.gravity = gravity; }
    public float getGravity()                     { return this.gravity; }
    public boolean isSneaking()                   { return this.sneaking; }
    public void setSneaking(boolean sneaking)     { this.sneaking = sneaking; }
    public boolean empty()                        { return this.sticks.isEmpty(); }

    public void applyMovement(Vector3 movement) {
        this.points.get(0).prevPosition.copy(this.points.get(0).position);
        this.points.get(0).position.add(movement);
    }

    public List<Point> getPoints() { return this.points; }

    public static class Point {
        public Vector3 position     = new Vector3(0, 0, 0);
        public Vector3 prevPosition = new Vector3(0, 0, 0);
        public boolean locked       = false;

        public float getLerpX(float delta) {
            return MathHelper.lerp(delta, prevPosition.x, position.x);
        }
        public float getLerpY(float delta) {
            return MathHelper.lerp(delta, prevPosition.y, position.y);
        }
        public float getLerpZ(float delta) {
            return MathHelper.lerp(delta, prevPosition.z, position.z);
        }
        public Vector3 getLerpedPos(float delta) {
            return new Vector3(getLerpX(delta), getLerpY(delta), getLerpZ(delta));
        }
    }

    public static class Stick {
        public Point pointA, pointB;
        public float length;
        public Stick(Point a, Point b, float length) {
            this.pointA = a; this.pointB = b; this.length = length;
        }
    }
}