package com.astucy.client.account;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Contas do Astucy Launcher, vistas de dentro do jogo.
 *
 * Só leitura: o arquivo {@code accounts.json} do launcher tem os tokens
 * cifrados com DPAPI, e aqui a gente lê apenas uuid / nome / origem. Trocar de
 * conta não move token nenhum — quem faz o login é o launcher, que reinicia o
 * jogo com a conta escolhida.
 */
public final class Accounts {

    private Accounts() {
    }

    /** Uma conta listada no launcher (sem token). */
    public record Acc(String uuid, String name, String source) {
    }

    private static Path storePath() {
        String appdata = System.getenv("APPDATA");
        if (appdata == null || appdata.isBlank()) {
            return null;
        }
        return Paths.get(appdata, "com.astucy.launcher", "accounts.json");
    }

    /** Contas salvas no launcher (ordem do arquivo). */
    public static List<Acc> list() {
        List<Acc> out = new ArrayList<>();
        Path p = storePath();
        if (p == null || !Files.isRegularFile(p)) {
            return out;
        }
        try {
            JsonObject root = new Gson().fromJson(Files.readString(p), JsonObject.class);
            if (root == null) {
                return out;
            }
            JsonArray arr = root.getAsJsonArray("accounts");
            if (arr == null) {
                return out;
            }
            for (var el : arr) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject a = el.getAsJsonObject();
                String uuid = str(a, "uuid");
                String name = str(a, "name");
                if (uuid.isEmpty() || name.isEmpty()) {
                    continue;
                }
                out.add(new Acc(norm(uuid), name, str(a, "source")));
            }
        } catch (Exception ignored) {
            // arquivo em escrita/rotacionado: simplesmente não lista agora
        }
        return out;
    }

    /** UUID da conta que está rodando agora (sem hífens). */
    public static String currentUuid() {
        try {
            return norm(Minecraft.getInstance().getUser().getProfileId().toString());
        } catch (Throwable t) {
            return "";
        }
    }

    public static String currentName() {
        try {
            String n = Minecraft.getInstance().getUser().getName();
            return n == null || n.isBlank() ? "Player" : n;
        } catch (Throwable t) {
            return "Player";
        }
    }

    /**
     * Caminho do launcher: vem no ambiente que o launcher monta ao abrir o
     * jogo ({@code ASTUCY_LAUNCHER}); se não vier, tenta o processo pai.
     */
    public static String launcherExe() {
        String env = System.getenv("ASTUCY_LAUNCHER");
        if (env != null && !env.isBlank()) {
            File f = new File(env);
            if (f.isFile()) {
                return f.getAbsolutePath();
            }
        }
        try {
            var parent = ProcessHandle.current().parent();
            if (parent.isPresent()) {
                var cmd = parent.get().info().command();
                if (cmd.isPresent() && cmd.get().toLowerCase(Locale.ROOT).endsWith("launcher.exe")) {
                    return cmd.get();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static boolean canSwitch() {
        return launcherExe() != null;
    }

    /** Abre o launcher já apontando para outra conta. Devolve erro ou "". */
    public static String switchTo(String uuid) {
        String exe = launcherExe();
        if (exe == null) {
            return "Não achei o Astucy Launcher. Abra ele e troque a conta por lá.";
        }
        try {
            new ProcessBuilder(exe, "--switch-account", norm(uuid))
                    .directory(new File(exe).getParentFile())
                    .start();
            return "";
        } catch (Exception e) {
            return "Não consegui abrir o launcher.";
        }
    }

    /** Abre o launcher normal (para adicionar conta / gerenciar). */
    public static String openLauncher() {
        String exe = launcherExe();
        if (exe == null) {
            return "Não achei o Astucy Launcher.";
        }
        try {
            new ProcessBuilder(exe).directory(new File(exe).getParentFile()).start();
            return "";
        } catch (Exception e) {
            return "Não consegui abrir o launcher.";
        }
    }

    public static String norm(String s) {
        return s == null ? "" : s.replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static String str(JsonObject o, String key) {
        try {
            return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
        } catch (Exception e) {
            return "";
        }
    }
}
