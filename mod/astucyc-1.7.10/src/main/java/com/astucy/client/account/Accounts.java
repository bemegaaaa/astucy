package com.astucy.client.account;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Session;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Accounts {
    public static final class Entry {
        public final String username;
        public final String uuid;
        public final boolean active;

        public Entry(String username, String uuid, boolean active) {
            this.username = username;
            this.uuid = uuid;
            this.active = active;
        }
    }

    private static final Gson GSON = new Gson();
    private static List<Entry> cached = Collections.emptyList();
    private static long lastLoadMs = 0L;

    public static String activeName() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Session s = mc != null ? mc.getSession() : null;
        return s != null && s.getUsername() != null ? s.getUsername() : "Player";
    }

    public static String activeUuid() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Session s = mc != null ? mc.getSession() : null;
        return s != null && s.getUuid() != null ? s.getUuid() : "";
    }

    public static List<Entry> list() {
        long now = System.currentTimeMillis();
        if (now - lastLoadMs < 5000L && !cached.isEmpty()) {
            return cached;
        }
        lastLoadMs = now;
        List<Entry> out = new ArrayList<Entry>();
        String curName = activeName();
        String curUuid = activeUuid();
        out.add(new Entry(curName, curUuid, true));

        try {
            File runDir = MinecraftClient.getInstance().runDirectory;
            File f = new File(runDir, "astucy-accounts.json");
            if (f.isFile()) {
                BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
                try {
                    JsonElement root = GSON.fromJson(r, JsonElement.class);
                    if (root != null && root.isJsonArray()) {
                        JsonArray arr = root.getAsJsonArray();
                        for (int i = 0; i < arr.size(); i++) {
                            JsonElement el = arr.get(i);
                            if (el.isJsonObject()) {
                                JsonObject obj = el.getAsJsonObject();
                                String u = obj.has("username") ? obj.get("username").getAsString() : "";
                                String id = obj.has("uuid") ? obj.get("uuid").getAsString() : "";
                                if (!u.isEmpty() && !u.equalsIgnoreCase(curName)) {
                                    out.add(new Entry(u, id, false));
                                }
                            }
                        }
                    }
                } finally {
                    r.close();
                }
            }
        } catch (Throwable ignored) {
        }
        cached = out;
        return out;
    }
}
