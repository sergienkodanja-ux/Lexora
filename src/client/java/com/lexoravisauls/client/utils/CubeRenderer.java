package com.lexoravisauls.client.utils;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public class CubeRenderer {
    private static int vaoId = -1;
    private static int vboId = -1;

    private static final float[] BASE_CUBE = new float[] {
            // North
            0,0,0, 0,1, 0,0,0,  0,1,0, 0,0, 0,1,0,  1,1,0, 1,0, 1,1,0,
            1,1,0, 1,0, 1,1,0,  1,0,0, 1,1, 1,0,0,  0,0,0, 0,1, 0,0,0,

            // South
            0,0,1, 0,1, 0,0,1,  1,0,1, 1,1, 1,0,1,  1,1,1, 1,0, 1,1,1,
            1,1,1, 1,0, 1,1,1,  0,1,1, 0,0, 0,1,1,  0,0,1, 0,1, 0,0,1,

            // West
            0,0,0, 0,1, 0,0,0,  0,0,1, 1,1, 0,0,1,  0,1,1, 1,0, 0,1,1,
            0,1,1, 1,0, 0,1,1,  0,1,0, 0,0, 0,1,0,  0,0,0, 0,1, 0,0,0,

            // East
            1,0,0, 0,1, 1,0,0,  1,1,0, 0,0, 1,1,0,  1,1,1, 1,0, 1,1,1,
            1,1,1, 1,0, 1,1,1,  1,0,1, 1,1, 1,0,1,  1,0,0, 0,1, 1,0,0,

            // Bottom
            0,0,0, 0,1, 0,0,0,  1,0,0, 1,1, 1,0,0,  1,0,1, 1,0, 1,0,1,
            1,0,1, 1,0, 1,0,1,  0,0,1, 0,0, 0,0,1,  0,0,0, 0,1, 0,0,0,

            // Top
            0,1,0, 0,1, 0,1,0,  0,1,1, 0,0, 0,1,1,  1,1,1, 1,0, 1,1,1,
            1,1,1, 1,0, 1,1,1,  1,1,0, 1,1, 1,1,0,  0,1,0, 0,1, 0,1,0
    };

    public static void drawBox() {
        if (vaoId == -1) {
            vaoId = GL30.glGenVertexArrays();
            vboId = GL15.glGenBuffers();
        }

        GL30.glBindVertexArray(vaoId);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, BASE_CUBE, GL15.GL_DYNAMIC_DRAW);

        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int posLoc = GL20.glGetAttribLocation(program, "Position");
        int uvLoc = GL20.glGetAttribLocation(program, "UV0");
        int localLoc = GL20.glGetAttribLocation(program, "LocalPos");

        if (posLoc != -1) {
            GL20.glVertexAttribPointer(posLoc, 3, GL11.GL_FLOAT, false, 8 * 4, 0L);
            GL20.glEnableVertexAttribArray(posLoc);
        }

        if (uvLoc != -1) {
            GL20.glVertexAttribPointer(uvLoc, 2, GL11.GL_FLOAT, false, 8 * 4, 3L * 4L);
            GL20.glEnableVertexAttribArray(uvLoc);
        }

        if (localLoc != -1) {
            GL20.glVertexAttribPointer(localLoc, 3, GL11.GL_FLOAT, false, 8 * 4, 5L * 4L);
            GL20.glEnableVertexAttribArray(localLoc);
        }

        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 36);

        if (posLoc != -1) {
            GL20.glDisableVertexAttribArray(posLoc);
        }
        if (uvLoc != -1) {
            GL20.glDisableVertexAttribArray(uvLoc);
        }
        if (localLoc != -1) {
            GL20.glDisableVertexAttribArray(localLoc);
        }

        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }
}