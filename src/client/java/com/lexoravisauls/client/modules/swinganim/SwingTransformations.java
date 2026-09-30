package com.lexoravisauls.client.modules.swinganim;

public record SwingTransformations(
        float anchorX, float anchorY, float anchorZ,
        float moveX, float moveY, float moveZ,
        float rotateX, float rotateY, float rotateZ
) {
    public static final SwingTransformations ZERO = new SwingTransformations(0, 0, 0, 0, 0, 0, 0, 0, 0);
}
