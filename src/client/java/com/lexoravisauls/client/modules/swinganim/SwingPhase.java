package com.lexoravisauls.client.modules.swinganim;

public class SwingPhase {
    public float anchorX = 0.0f;
    public float anchorY = 0.0f;
    public float anchorZ = 0.0f;
    public float moveX = 0.0f;
    public float moveY = 0.0f;
    public float moveZ = 0.0f;
    public float rotateX = 0.0f;
    public float rotateY = 0.0f;
    public float rotateZ = 0.0f;

    public SwingPhase() {}

    public SwingPhase(float anchorX, float anchorY, float anchorZ,
                      float moveX, float moveY, float moveZ,
                      float rotateX, float rotateY, float rotateZ) {
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        this.moveX = moveX;
        this.moveY = moveY;
        this.moveZ = moveZ;
        this.rotateX = rotateX;
        this.rotateY = rotateY;
        this.rotateZ = rotateZ;
    }

    public SwingTransformations toTransformations() {
        return new SwingTransformations(anchorX, anchorY, anchorZ, moveX, moveY, moveZ, rotateX, rotateY, rotateZ);
    }

    public void setFrom(SwingTransformations t) {
        if (t == null) return;
        this.anchorX = t.anchorX();
        this.anchorY = t.anchorY();
        this.anchorZ = t.anchorZ();
        this.moveX = t.moveX();
        this.moveY = t.moveY();
        this.moveZ = t.moveZ();
        this.rotateX = t.rotateX();
        this.rotateY = t.rotateY();
        this.rotateZ = t.rotateZ();
    }

    public void copyFrom(SwingPhase other) {
        if (other == null) return;
        this.anchorX = other.anchorX;
        this.anchorY = other.anchorY;
        this.anchorZ = other.anchorZ;
        this.moveX = other.moveX;
        this.moveY = other.moveY;
        this.moveZ = other.moveZ;
        this.rotateX = other.rotateX;
        this.rotateY = other.rotateY;
        this.rotateZ = other.rotateZ;
    }

    public void setAnchor(float x, float y, float z) {
        this.anchorX = x;
        this.anchorY = y;
        this.anchorZ = z;
    }

    public void setMove(float x, float y, float z) {
        this.moveX = x;
        this.moveY = y;
        this.moveZ = z;
    }

    public void setRotate(float x, float y, float z) {
        this.rotateX = x;
        this.rotateY = y;
        this.rotateZ = z;
    }
}
