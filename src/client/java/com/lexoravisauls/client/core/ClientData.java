package com.lexoravisauls.client.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClientData {

    public static final Map<String, Boolean> moduleStates = new HashMap<>();
    public static final Map<String, Float> numSettings = new HashMap<>();
    public static final Map<String, String> modeSettings = new HashMap<>();
    public static final Map<String, float[]> colorSettings = new HashMap<>();
    public static final Map<String, Integer> moduleBinds = new HashMap<>();

    public static final List<String> savedConfigs = new ArrayList<>();
    public static final List<String> savedThemes = new ArrayList<>();

    private ClientData() {
    }
}