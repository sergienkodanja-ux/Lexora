package com.lexoravisauls.client.events;

import com.lexoravisauls.client.core.ClientData;

public class CustomCrosshairData {
    public static final int GRID_SIZE = 21;
    public static final boolean[][] MATRIX = new boolean[GRID_SIZE][GRID_SIZE];
    private static boolean loaded = false;

    static {
        loadDefault();
    }

    public static void loadDefault() {
        clear();
        int c = GRID_SIZE / 2; // 10
        // Classic crisp crosshair with 2px gap in center
        for (int i = 2; i <= 6; i++) {
            MATRIX[c - i][c] = true; // Top
            MATRIX[c + i][c] = true; // Bottom
            MATRIX[c][c - i] = true; // Left
            MATRIX[c][c + i] = true; // Right
        }
    }

    public static void loadDot() {
        clear();
        int c = GRID_SIZE / 2;
        MATRIX[c][c] = true;
        MATRIX[c - 1][c] = true;
        MATRIX[c + 1][c] = true;
        MATRIX[c][c - 1] = true;
        MATRIX[c][c + 1] = true;
    }

    public static void loadCorners() {
        clear();
        int c = GRID_SIZE / 2;
        int d = 4;
        // 4 corner brackets
        // Top-left
        MATRIX[c - d][c - d] = true;
        MATRIX[c - d + 1][c - d] = true;
        MATRIX[c - d][c - d + 1] = true;
        // Top-right
        MATRIX[c - d][c + d] = true;
        MATRIX[c - d + 1][c + d] = true;
        MATRIX[c - d][c + d - 1] = true;
        // Bottom-left
        MATRIX[c + d][c - d] = true;
        MATRIX[c + d - 1][c - d] = true;
        MATRIX[c + d][c - d + 1] = true;
        // Bottom-right
        MATRIX[c + d][c + d] = true;
        MATRIX[c + d - 1][c + d] = true;
        MATRIX[c + d][c + d - 1] = true;
        // Center dot
        MATRIX[c][c] = true;
    }

    public static void clear() {
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                MATRIX[y][x] = false;
            }
        }
    }

    public static void invert() {
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                MATRIX[y][x] = !MATRIX[y][x];
            }
        }
        save();
    }

    public static void toggle(int x, int y) {
        if (x >= 0 && x < GRID_SIZE && y >= 0 && y < GRID_SIZE) {
            MATRIX[y][x] = !MATRIX[y][x];
            save();
        }
    }

    public static void set(int x, int y, boolean val) {
        if (x >= 0 && x < GRID_SIZE && y >= 0 && y < GRID_SIZE) {
            if (MATRIX[y][x] != val) {
                MATRIX[y][x] = val;
                save();
            }
        }
    }

    public static void save() {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                sb.append(MATRIX[y][x] ? '1' : '0');
            }
        }
        ClientData.modeSettings.put("CrosshairGrid", sb.toString());
    }

    public static void load() {
        String data = ClientData.modeSettings.getOrDefault("CrosshairGrid", "");
        if (data.length() == GRID_SIZE * GRID_SIZE) {
            int idx = 0;
            for (int y = 0; y < GRID_SIZE; y++) {
                for (int x = 0; x < GRID_SIZE; x++) {
                    MATRIX[y][x] = data.charAt(idx++) == '1';
                }
            }
            loaded = true;
        } else if (!loaded) {
            loadDefault();
            save();
            loaded = true;
        }
    }
}
