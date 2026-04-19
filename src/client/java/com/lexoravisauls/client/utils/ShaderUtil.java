package com.lexoravisauls.client.utils;

import org.apache.commons.io.IOUtils;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ShaderUtil {
    private final int programID;
    private boolean deleted = false;

    public ShaderUtil(String vertexPath, String fragmentPath) {
        int vertexShader = loadShader(vertexPath, GL20.GL_VERTEX_SHADER);
        int fragmentShader = loadShader(fragmentPath, GL20.GL_FRAGMENT_SHADER);

        if (vertexShader == 0 || fragmentShader == 0) {
            this.programID = 0;
            return;
        }

        int program = GL20.glCreateProgram();
        if (program == 0) {
            GL20.glDeleteShader(vertexShader);
            GL20.glDeleteShader(fragmentShader);
            this.programID = 0;
            return;
        }

        GL20.glAttachShader(program, vertexShader);
        GL20.glAttachShader(program, fragmentShader);

        GL20.glBindAttribLocation(program, 0, "Position");
        GL20.glBindAttribLocation(program, 1, "UV0");

        GL20.glLinkProgram(program);

        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            System.err.println("[Lexora] Program link failed: " + vertexPath + " / " + fragmentPath);
            System.err.println(GL20.glGetProgramInfoLog(program));

            GL20.glDetachShader(program, vertexShader);
            GL20.glDetachShader(program, fragmentShader);
            GL20.glDeleteShader(vertexShader);
            GL20.glDeleteShader(fragmentShader);
            GL20.glDeleteProgram(program);

            this.programID = 0;
            return;
        }

        GL20.glDetachShader(program, vertexShader);
        GL20.glDetachShader(program, fragmentShader);
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);

        this.programID = program;
    }

    public void bind() {
        if (programID > 0 && !deleted) {
            GL20.glUseProgram(programID);
        }
    }

    public void unbind() {
        GL20.glUseProgram(0);
    }

    public boolean isValid() {
        return programID > 0 && !deleted;
    }

    public int getProgramID() {
        return programID;
    }

    public void delete() {
        if (!deleted && programID > 0) {
            GL20.glDeleteProgram(programID);
            deleted = true;
        }
    }

    public int getUniform(String name) {
        if (programID <= 0 || deleted) return -1;
        return GL20.glGetUniformLocation(programID, name);
    }

    public void setUniform1f(String name, float value) {
        int loc = getUniform(name);
        if (loc != -1) GL20.glUniform1f(loc, value);
    }

    public void setUniform4f(String name, float f0, float f1, float f2, float f3) {
        int loc = getUniform(name);
        if (loc != -1) GL20.glUniform4f(loc, f0, f1, f2, f3);
    }

    public void setUniform3f(String name, float f0, float f1, float f2) {
        int loc = getUniform(name);
        if (loc != -1) GL20.glUniform3f(loc, f0, f1, f2);
    }

    public void setUniformMatrix4f(String name, Matrix4f matrix) {
        int loc = getUniform(name);
        if (loc != -1) {
            float[] array = new float[16];
            matrix.get(array);
            GL20.glUniformMatrix4fv(loc, false, array);
        }
    }

    public void setUniform1i(String name, int value) {
        int loc = getUniform(name);
        if (loc != -1) GL20.glUniform1i(loc, value);
    }

    public void setUniform2f(String name, float f0, float f1) {
        int loc = getUniform(name);
        if (loc != -1) GL20.glUniform2f(loc, f0, f1);
    }

    private int loadShader(String path, int type) {
        String fullPath = "/assets/lexoravisauls/shaders/core/" + path;

        try (InputStream stream = ShaderUtil.class.getResourceAsStream(fullPath)) {
            if (stream == null) {
                System.err.println("[Lexora] Shader file not found: " + fullPath);
                return 0;
            }

            String source = IOUtils.toString(stream, StandardCharsets.UTF_8);

            int shaderID = GL20.glCreateShader(type);
            if (shaderID == 0) {
                System.err.println("[Lexora] glCreateShader failed for: " + path);
                return 0;
            }

            GL20.glShaderSource(shaderID, source);
            GL20.glCompileShader(shaderID);

            if (GL20.glGetShaderi(shaderID, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                System.err.println("[Lexora] Shader compile failed: " + path);
                System.err.println(GL20.glGetShaderInfoLog(shaderID));
                GL20.glDeleteShader(shaderID);
                return 0;
            }

            return shaderID;
        } catch (Exception e) {
            System.err.println("[Lexora] Failed to load shader: " + path);
            e.printStackTrace();
            return 0;
        }
    }
}