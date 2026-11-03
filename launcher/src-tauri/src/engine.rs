// Engine de launch do Astucy — protocolo: docs/minecraft-launch-protocol.md.
// Regras de ouro seguidas:
//  - `arguments.jvm` do JSON da versão LITERALMENTE (nada hardcodado de layout)
//  - natives 26.x: jars no classpath, SEM extração de DLL; só criar os dirs vazios
//  - classpath explícito, SEM wildcard
//  - argv com token é redigido em QUALQUER log/evento
//  - modo offline: nick + uuid offline (v3 md5 "OfflinePlayer:<nick>"), accessToken "0"

use serde_json::{json, Value};
use std::collections::HashMap;
use std::os::windows::process::CommandExt; // creation_flags (silencioso, sem console)
use std::path::{Path, PathBuf};
use tauri::{AppHandle, Emitter};

const MANIFEST_URL: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
const USER_AGENT: &str = "AstucyLauncher/0.1 (privado)";

/// Jogo aberto por este launcher (para trocar de conta sem dois processos
/// disputando a mesma pasta .minecraft).
static GAME: std::sync::Mutex<Option<std::process::Child>> = std::sync::Mutex::new(None);

/// Espera o jogo anterior fechar antes de abrir outro (troca de conta no
/// menu do R-SHIFT). No uso normal não existe jogo rodando: retorna na hora.
fn wait_previous_game() {
    let previous = GAME.lock().ok().and_then(|mut g| g.take());
    let Some(mut child) = previous else {
        return;
    };
    for _ in 0..160 {
        match child.try_wait() {
            Ok(Some(_)) => break,
            Ok(None) => std::thread::sleep(std::time::Duration::from_millis(250)),
            Err(_) => break,
        }
    }
}

/* ================================================================
 * estruturas de progresso
 * ================================================================ */

fn stage(app: Option<&AppHandle>, stage: &str, index: usize, total: usize, detail: &str) {
    if let Ok(mut s) = LAST_STAGE.lock() {
        *s = format!("{stage}: {detail}");
    }
    if let Some(app) = app {
        let _ = app.emit(
            "launch://stage",
            json!({ "stage": stage, "index": index, "total": total, "detail": detail }),
        );
    }
}

/// Última etapa do launch (a UI nativa lê isso em vez de eventos).
static LAST_STAGE: std::sync::Mutex<String> = std::sync::Mutex::new(String::new());

/// Etapa atual do motor, para a UI nativa mostrar progresso.
pub fn last_stage() -> String {
    LAST_STAGE.lock().map(|s| s.clone()).unwrap_or_default()
}

fn fail(app: Option<&AppHandle>, msg: &str) -> String {
    if let Some(app) = app {
        let _ = app.emit("launch://error", json!({ "message": msg }));
    }
    msg.to_string()
}

/* ================================================================
 * caminhos
 * ================================================================ */
struct Paths {
    mc: PathBuf,    // %APPDATA%\.minecraft
    meta: PathBuf,  // %LOCALAPPDATA%\astucy\meta
    natives: PathBuf,
}

// ---------- tema visual (resource pack do Astucy) ----------
// As fontes vivem no jar do mod. O pack existe só para garantir a entrada em
// resourcePacks; nada de fonte aqui — assim ele nunca sobrescreve o jar.

const THEME_PACK_META: &str = r#"{"pack":{"description":"Astucy UI (SIL OFL)","min_format":[88,0],"max_format":[88,0]}}"#;

/// Instala o pack de tema do Astucy no gameDir (resource pack real,
/// sempre acima do vanilla pela ordem do options.txt). Não toca em nada
/// além do próprio pack e da lista `resourcePacks` do options.txt.
fn ensure_theme_pack(gamedir: &Path) -> Result<(), String> {
    let pack = gamedir.join("resourcepacks").join("astucy-theme");
    let mk = |d: &Path| std::fs::create_dir_all(d).map_err(|e| format!("pack theme: {e}"));
    mk(&pack)?;

    // versões antigas do tema carregavam fontes Poppins/Inter (e sobrescreviam
    // o jar). Remove a pasta inteira: as fontes agora vêm só do mod.
    let assets = pack.join("assets");
    if assets.exists() {
        let _ = std::fs::remove_dir_all(&assets);
    }

    std::fs::write(pack.join("pack.mcmeta"), THEME_PACK_META.as_bytes())
        .map_err(|e| format!("pack {pack:?}: {e}"))?;

    // options.txt: garantir "file/astucy-theme" em resourcePacks
    let opt_path = gamedir.join("options.txt");
    let raw = std::fs::read_to_string(&opt_path).unwrap_or_default();
    let mut found = false;
    let mut out_lines: Vec<String> = Vec::new();
    for line in raw.lines() {
        if line.starts_with("resourcePacks:") {
            found = true;
            let patched = ensure_pack_entry(line, "file/astucy-theme")?;
            out_lines.push(patched);
        } else {
            out_lines.push(line.to_string());
        }
    }
    if !found {
        out_lines.push("resourcePacks:[\"vanilla\",\"file/astucy-theme\"]".to_string());
    }
    let mut body = out_lines.join("\n");
    if !raw.is_empty() && raw.ends_with('\n') {
        body.push('\n');
    }
    std::fs::write(&opt_path, body).map_err(|e| format!("options.txt: {e}"))?;
    Ok(())
}

fn ensure_pack_entry(line: &str, pack: &str) -> Result<String, String> {
    let open = match line.find('[') { Some(i) => i, None => return Ok(line.to_string()) };
    let close = match line.rfind(']') { Some(i) => i, None => return Ok(line.to_string()) };
    let inside = &line[open + 1..close];
    let mut items: Vec<String> = inside
        .split(',')
        .map(|s| s.trim().trim_matches('"').to_string())
        .filter(|s| !s.is_empty())
        .filter(|s| !s.contains("astucy-theme"))
        .collect();
    // vanilla sempre primeiro; o tema logo depois (vence providers de fonte)
    if !items.iter().any(|s| s == "vanilla") {
        items.insert(0, "vanilla".to_string());
    }
    if !items.iter().any(|s| s.contains("astucy-theme")) {
        let pos = if items.len() > 1 { 1 } else { items.len() };
        items.insert(pos, pack.to_string());
    }
    let joined = items
        .iter()
        .map(|s| format!("\"{s}\""))
        .collect::<Vec<_>>()
        .join(",");
    Ok(format!("resourcePacks:[{joined}]"))
}

fn paths(version: &str) -> Result<Paths, String> {
    let appdata = std::env::var("APPDATA").map_err(|_| "APPDATA não definido")?;
    let local = std::env::var("LOCALAPPDATA").map_err(|_| "LOCALAPPDATA não definido")?;
    let mc = Path::new(&appdata).join(".minecraft");
    let meta = Path::new(&local).join("astucy").join("meta");
    let natives = Path::new(&local).join("astucy").join("natives").join(version);
    for d in [&meta, &mc.join("libraries"), &mc.join("assets"), &mc.join("versions"),
              &natives.join("java"), &natives.join("jna"), &natives.join("lwjgl"), &natives.join("netty")] {
        std::fs::create_dir_all(d).map_err(|e| format!("criar dir {d:?}: {e}"))?;
    }
    Ok(Paths { mc, meta, natives })
}

fn meta_dir() -> PathBuf {
    let d = std::env::var("LOCALAPPDATA")
        .map(|l| Path::new(&l).join("astucy").join("meta"))
        .unwrap_or_else(|_| PathBuf::from("astucy-meta"));
    let _ = std::fs::create_dir_all(&d);
    d
}

fn mc_root() -> PathBuf {
    std::env::var("APPDATA")
        .map(|a| Path::new(&a).join(".minecraft"))
        .unwrap_or_else(|_| PathBuf::from(".minecraft"))
}

/* ================================================================
 * catálogo de versões (Mojang) + download sob demanda
 * ================================================================ */

#[derive(serde::Serialize, Clone)]
pub struct CatalogEntry {
    pub id: String,
    pub kind: String,
    pub released: String,
    pub installed: bool,
    /// roda com o client Astucy (Fabric 26.x)
    pub client: bool,
}

/// 26.x em diante (esquema novo ano.major) roda com o client Astucy.
/// Snapshots ("26w13a") não entram — não têm perfil Fabric pronto.
fn is_client_version(id: &str) -> bool {
    id.split('.')
        .next()
        .and_then(|s| s.parse::<u32>().ok())
        .map(|m| m >= 26)
        .unwrap_or(false)
}

/// Catálogo começa em 1.7.10 (ainda roda com Java 8/LWJGL 2) e só inclui
/// lançamentos estáveis — nada de beta/alpha/snapshot.
fn supported_release(id: &str) -> bool {
    let mut parts = id.split('.');
    let major = match parts.next().and_then(|s| s.parse::<u32>().ok()) {
        Some(v) => v,
        None => return false,
    };
    if major >= 26 {
        return true;
    }
    if major != 1 {
        return false;
    }
    let minor = parts.next().and_then(|s| s.parse::<u32>().ok()).unwrap_or(0);
    if minor != 7 {
        return minor > 7;
    }
    let patch = parts.next().and_then(|s| s.parse::<u32>().ok()).unwrap_or(0);
    patch >= 10
}

/// Lista completa da Mojang (release/snapshot/alpha/beta) - independe do
/// que já existe na máquina. `installed` só marca o que já foi baixado.
pub fn catalog(force: bool) -> Result<Vec<CatalogEntry>, String> {
    let meta = meta_dir();
    std::fs::create_dir_all(&meta).ok();
    let cache = meta.join("version_manifest_v2.json");
    if force {
        let _ = std::fs::remove_file(&cache);
    }
    let manifest = fetch_json(MANIFEST_URL, Some(&cache))?;
    let Some(list) = manifest.get("versions").and_then(|v| v.as_array()) else {
        return Err("manifest sem lista de versões".into());
    };
    let versions_root = mc_root().join("versions");
    let mut out = Vec::with_capacity(list.len());
    for v in list {
        let Some(id) = v.get("id").and_then(|s| s.as_str()) else {
            continue;
        };
        // só versões estáveis e a partir de 1.7.10 (nada de snapshot/beta/alpha)
        if v.get("type").and_then(|s| s.as_str()) != Some("release") {
            continue;
        }
        if !supported_release(id) {
            continue;
        }
        let dir = versions_root.join(id);
        out.push(CatalogEntry {
            id: id.to_string(),
            kind: v
                .get("type")
                .and_then(|s| s.as_str())
                .unwrap_or("release")
                .to_string(),
            released: v
                .get("releaseTime")
                .and_then(|s| s.as_str())
                .unwrap_or("")
                .to_string(),
            installed: dir.join(format!("{id}.json")).exists()
                && dir.join(format!("{id}.jar")).exists(),
            client: is_client_version(id),
        });
    }
    Ok(out)
}

/// Baixa (se faltar) o JSON da versão e o jar do cliente direto da Mojang.
/// Bibliotecas e assets continuam sendo verificados no launch, como antes.
pub fn ensure_version(app: Option<&AppHandle>, id: &str) -> Result<(), String> {
    let dir = mc_root().join("versions").join(id);
    let json_path = dir.join(format!("{id}.json"));
    let jar_path = dir.join(format!("{id}.jar"));

    let manifest = fetch_json(MANIFEST_URL, Some(&meta_dir().join("version_manifest_v2.json")))?;
    let entry = manifest
        .get("versions")
        .and_then(|v| v.as_array())
        .and_then(|list| {
            list.iter()
                .find(|v| v.get("id").and_then(|s| s.as_str()) == Some(id))
        })
        .cloned()
        .ok_or_else(|| format!("A versão {id} não existe no catálogo da Mojang"))?;

    if !json_path.exists() {
        let url = entry
            .get("url")
            .and_then(|s| s.as_str())
            .ok_or("manifest sem url da versão")?;
        let sha1 = entry.get("sha1").and_then(|s| s.as_str());
        if let Some(a) = app {
            stage(Some(a), "versão", 0, 2, &format!("baixando {id}.json"));
        }
        std::fs::create_dir_all(&dir).map_err(|e| format!("dir {}: {e}", dir.display()))?;
        download_to(url, &json_path, sha1)?;
    }

    let raw = std::fs::read_to_string(&json_path).map_err(|e| format!("json {id}: {e}"))?;
    let v: Value = serde_json::from_str(&raw).map_err(|e| format!("json {id}: {e}"))?;

    if !jar_path.exists() {
        if let Some(client) = v.get("downloads").and_then(|d| d.get("client")) {
            let url = client.get("url").and_then(|s| s.as_str()).unwrap_or("");
            let sha1 = client.get("sha1").and_then(|s| s.as_str());
            if !url.is_empty() {
                if let Some(a) = app {
                    stage(Some(a), "versão", 1, 2, &format!("baixando {id}.jar"));
                }
                download_to(url, &jar_path, sha1)?;
            }
        }
    }
    Ok(())
}

/* ================================================================
 * http + cache em disco
 * ================================================================ */

fn http() -> Result<reqwest::blocking::Client, String> {
    reqwest::blocking::Client::builder()
        .user_agent(USER_AGENT)
        .build()
        .map_err(|e| format!("http client: {e}"))
}

fn fetch_json(url: &str, cache: Option<&Path>) -> Result<Value, String> {
    if let Some(c) = cache {
        if let Ok(raw) = std::fs::read_to_string(c) {
            if let Ok(v) = serde_json::from_str(&raw) {
                return Ok(v);
            }
        }
    }
    let body = http()?
        .get(url)
        .send()
        .and_then(|r| r.error_for_status())
        .map_err(|e| format!("GET {url}: {e}"))?
        .text()
        .map_err(|e| format!("corpo {url}: {e}"))?;
    if let Some(c) = cache {
        let _ = std::fs::write(c, &body);
    }
    serde_json::from_str(&body).map_err(|e| format!("json {url}: {e}"))
}

/// Diz ao Windows para rodar esse Java na GPU dedicada (a mesma preferência
/// que o painel de gráficos do Windows grava). Sem isso o notebook costuma
/// abrir o jogo na iGPU e o FPS despenca.
fn expose_gpu(java: &Path) {
    let exe = java.to_string_lossy().to_string();
    let _ = std::process::Command::new("reg")
        .args([
            "add",
            r"HKCU\Software\Microsoft\DirectX\UserGpuPreferences",
            "/v",
            &exe,
            "/t",
            "REG_SZ",
            "/d",
            "GpuPreference=2;",
            "/f",
        ])
        .creation_flags(CREATE_NO_WINDOW)
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null())
        .status();
}

fn download_to(url: &str, dest: &Path, sha1: Option<&str>) -> Result<(), String> {
    if dest.exists() {
        if let Some(want) = sha1 {
            // cache por (tamanho, mtime): evita re-hashear ~200 jars a cada launch
            if verified_hit(dest, want) {
                return Ok(());
            }
            if let Ok(bytes) = std::fs::read(dest) {
                if sha1_hex(&bytes) == want {
                    verified_put(dest, want);
                    return Ok(());
                }
            }
        } else {
            return Ok(());
        }
    }
    let bytes = http()?
        .get(url)
        .send()
        .and_then(|r| r.error_for_status())
        .map_err(|e| format!("GET {url}: {e}"))?
        .bytes()
        .map_err(|e| format!("bytes {url}: {e}"))?;
    if let Some(want) = sha1 {
        let got = sha1_hex(&bytes);
        if got != want {
            return Err(format!("sha1 divergente em {url}: {got} != {want}"));
        }
    }
    if let Some(parent) = dest.parent() {
        std::fs::create_dir_all(parent).map_err(|e| format!("dir {parent:?}: {e}"))?;
    }
    std::fs::write(dest, &bytes).map_err(|e| format!("gravar {dest:?}: {e}"))?;
    if let Some(want) = sha1 {
        verified_put(dest, want);
    }
    Ok(())
}

/* ------------------------------------------------------------------ */
/* cache de integridade (tamanho + mtime → não re-hasheia todo launch) */
/* ------------------------------------------------------------------ */

#[derive(serde::Serialize, serde::Deserialize, Clone)]
struct Verified {
    size: u64,
    mtime: u64,
    sha1: String,
}

static VERIFIED: std::sync::Mutex<Option<HashMap<String, Verified>>> = std::sync::Mutex::new(None);

fn verified_path() -> Option<PathBuf> {
    let appdata = std::env::var("APPDATA").ok()?;
    Some(
        Path::new(&appdata)
            .join("com.astucy.launcher")
            .join("verified.json"),
    )
}

fn with_verified<R>(f: impl FnOnce(&mut HashMap<String, Verified>) -> R) -> R {
    let mut guard = VERIFIED.lock().unwrap_or_else(|e| e.into_inner());
    if guard.is_none() {
        let map = verified_path()
            .and_then(|p| std::fs::read_to_string(p).ok())
            .and_then(|t| serde_json::from_str(&t).ok())
            .unwrap_or_default();
        *guard = Some(map);
    }
    f(guard.as_mut().unwrap())
}

fn meta_key(path: &Path) -> Option<(u64, u64)> {
    let m = std::fs::metadata(path).ok()?;
    let mtime = m
        .modified()
        .ok()?
        .duration_since(std::time::UNIX_EPOCH)
        .ok()?
        .as_secs();
    Some((m.len(), mtime))
}

fn verified_hit(path: &Path, sha1: &str) -> bool {
    let Some((size, mtime)) = meta_key(path) else {
        return false;
    };
    let key = path.to_string_lossy().to_string();
    with_verified(|m| {
        m.get(&key)
            .map(|v| v.size == size && v.mtime == mtime && v.sha1 == sha1)
            .unwrap_or(false)
    })
}

fn verified_put(path: &Path, sha1: &str) {
    if let Some((size, mtime)) = meta_key(path) {
        let key = path.to_string_lossy().to_string();
        with_verified(|m| {
            m.insert(
                key,
                Verified {
                    size,
                    mtime,
                    sha1: sha1.to_string(),
                },
            );
        });
    }
}

/// Grava o cache uma vez por launch (não a cada arquivo).
fn verified_flush() {
    let map = with_verified(|m| m.clone());
    if let Some(p) = verified_path() {
        if let Some(dir) = p.parent() {
            let _ = std::fs::create_dir_all(dir);
        }
        if let Ok(text) = serde_json::to_string(&map) {
            let _ = std::fs::write(p, text);
        }
    }
}

fn sha1_hex(bytes: &[u8]) -> String {
    use sha1::{Digest, Sha1};
    let mut h = Sha1::new();
    h.update(bytes);
    h.finalize().iter().map(|b| format!("{b:02x}")).collect()
}

/* ================================================================
 * resolução da versão (herança fabric → vanilla)
 * ================================================================ */

fn version_json(mc: &Path, name: &str) -> Result<Value, String> {
    let path = mc.join("versions").join(name).join(format!("{name}.json"));
    let raw = std::fs::read_to_string(&path).map_err(|e| format!("versão {name}: {e}"))?;
    let mut leaf: Value = serde_json::from_str(&raw).map_err(|e| format!("json {name}: {e}"))?;

    // merge da cadeia inheritsFrom (fabric herda do vanilla)
    let mut merged = leaf.take();
    let mut guard = 0;
    while let Some(parent_id) = merged.get("inheritsFrom").and_then(|v| v.as_str()) {
        guard += 1;
        if guard > 5 {
            return Err("cadeia inheritsFrom cíclica".into());
        }
        let parent_path = mc
            .join("versions")
            .join(parent_id)
            .join(format!("{parent_id}.json"));
        let praw = std::fs::read_to_string(&parent_path)
            .map_err(|e| format!("versão base {parent_id}: {e}"))?;
        let parent: Value = serde_json::from_str(&praw).map_err(|e| e.to_string())?;

        // filho tem precedência; bibliotecas = pai + filho (ordem MultiMC)
        let mut libs = vec![];
        if let Some(l) = parent.get("libraries").and_then(|v| v.as_array()) {
            libs.extend(l.iter().cloned());
        }
        if let Some(l) = merged.get("libraries").and_then(|v| v.as_array()) {
            libs.extend(l.iter().cloned());
        }
        let mut m = parent;
        merge_missing(&mut m, &merged);
        m["libraries"] = Value::Array(libs);
        if m.get("jar").is_none() {
            m["jar"] = json!(parent_id);
        }
        merged = m;
    }
    Ok(merged)
}

/// sobrepõe os campos do filho sobre o pai (mainClass, assetIndex…).
/// `arguments` é especial: CONCATENA (pai primeiro, filho acrescenta) —
/// substituir perderia os args vanilla (jvm flags + game args) e quebrava o JVM.
fn merge_missing(parent: &mut Value, child: &Value) {
    if let (Some(pm), Some(cm)) = (parent.as_object_mut(), child.as_object()) {
        for (k, v) in cm {
            if k == "libraries" || k == "inheritsFrom" {
                continue;
            }
            if k == "arguments" {
                let mut merged_args = pm.get("arguments").cloned().unwrap_or_else(|| json!({}));
                if let (Some(ma), Some(ca)) = (merged_args.as_object_mut(), v.as_object()) {
                    for key in ["game", "jvm"] {
                        let mut list: Vec<Value> = ma
                            .get(key)
                            .and_then(|x| x.as_array())
                            .cloned()
                            .unwrap_or_default();
                        if let Some(child_list) = ca.get(key).and_then(|x| x.as_array()) {
                            list.extend(child_list.iter().cloned());
                        }
                        if !list.is_empty() {
                            ma.insert(key.to_string(), Value::Array(list));
                        }
                    }
                }
                pm.insert(k.clone(), merged_args);
                continue;
            }
            if !pm.contains_key(k) || k == "mainClass" || k == "assetIndex" {
                pm.insert(k.clone(), v.clone());
            }
        }
    }
}

/* ================================================================
 * regras de bibliotecas/argumentos
 * ================================================================ */

fn rules_allow(rules: Option<&Value>) -> bool {
    let Some(rules) = rules.and_then(|v| v.as_array()) else {
        return true; // sem regras = permitido
    };
    if rules.is_empty() {
        return true;
    }
    let mut allowed = false;
    for rule in rules {
        let action = rule.get("action").and_then(|v| v.as_str()).unwrap_or("allow");
        let os_ok = rule
            .get("os")
            .and_then(|o| o.get("name"))
            .and_then(|v| v.as_str())
            .map(|n| n == "windows")
            .unwrap_or(true);
        // features: pedimos nenhuma feature extra; regra com feature exigida não casa
        let feat_ok = rule
            .get("features")
            .and_then(|f| f.as_object())
            .map(|f| f.is_empty())
            .unwrap_or(true);
        if os_ok && feat_ok {
            allowed = action == "allow";
        }
    }
    allowed
}

/* ================================================================
 * bibliotecas → classpath (natives 26.x ficam NO classpath, sem extrair)
 * ================================================================ */

/// name maven ("org.ow2.asm:asm:9.10.1") → caminho relativo no repositório.
fn maven_rel(gav: &str) -> Option<String> {
    let parts: Vec<&str> = gav.split(':').collect();
    if parts.len() != 3 {
        return None;
    }
    let (g, a, v) = (parts[0], parts[1], parts[2]);
    Some(format!("{}/{a}/{v}/{a}-{v}.jar", g.replace('.', "/")))
}

fn resolve_libraries(v: &Value, libs_dir: &Path, natives_dir: &Path) -> Result<Vec<PathBuf>, String> {
    let mut cp = Vec::new();
    let mut seen = std::collections::HashSet::new();
    let Some(libs) = v.get("libraries").and_then(|l| l.as_array()) else {
        return Ok(cp);
    };
    for lib in libs {
        if !rules_allow(lib.get("rules")) {
            continue;
        }
        // versões antigas (≤ 1.12): DLLs em jars separados, precisam ser
        // extraídas — o LWJGL 2 não abre jar, procura o arquivo solto.
        if let Some(cls) = lib
            .get("natives")
            .and_then(|n| n.get("windows"))
            .and_then(|s| s.as_str())
        {
            let cls = cls.replace(
                "${arch}",
                if cfg!(target_pointer_width = "64") { "64" } else { "32" },
            );
            if let Some(art) = lib
                .get("downloads")
                .and_then(|d| d.get("classifiers"))
                .and_then(|c| c.get(&cls))
            {
                let url = art.get("url").and_then(|u| u.as_str()).unwrap_or("");
                let sha1 = art.get("sha1").and_then(|s| s.as_str());
                let rel = art.get("path").and_then(|p| p.as_str()).unwrap_or("");
                if !url.is_empty() && !rel.is_empty() {
                    let jar = libs_dir.join(rel);
                    download_to(url, &jar, sha1)?;
                    unzip_natives(&jar, natives_dir)?;
                }
            }
        }
        let Some(artifact) = lib
            .get("downloads")
            .and_then(|d| d.get("artifact"))
        else {
            // tem bloco `downloads` mas sem artifact = biblioteca só de
            // natives (já extraída acima): não entra no classpath
            if lib.get("downloads").is_some() {
                continue;
            }
            // formato Fabric (name + url de maven): g:a:v → caminho maven
            let Some(name) = lib.get("name").and_then(|n| n.as_str()) else {
                continue;
            };
            let Some(rel) = maven_rel(name) else {
                continue;
            };
            if !seen.insert(rel.clone()) {
                continue;
            }
            let dest = libs_dir.join(&rel);
            if !dest.exists() {
                let base = lib
                    .get("url")
                    .and_then(|u| u.as_str())
                    .unwrap_or("https://maven.fabricmc.net/");
                let sha1 = lib.get("sha1").and_then(|s| s.as_str());
                download_to(&format!("{}{}", base, rel), &dest, sha1)?;
            }
            cp.push(dest);
            continue;
        };
        let Some(rel) = artifact.get("path").and_then(|p| p.as_str()) else {
            continue;
        };
        if !seen.insert(rel.to_string()) {
            continue;
        }
        let dest = libs_dir.join(rel);
        let url = artifact.get("url").and_then(|u| u.as_str()).unwrap_or("");
        let sha1 = artifact.get("sha1").and_then(|s| s.as_str());
        if !url.is_empty() {
            download_to(url, &dest, sha1)?;
        }
        cp.push(dest);
    }
    Ok(cp)
}

/// Extrai só as bibliotecas nativas (.dll) de um jar para a pasta do LWJGL.
fn unzip_natives(jar: &Path, dest: &Path) -> Result<(), String> {
    use std::io::Read;
    let file = std::fs::File::open(jar).map_err(|e| format!("abrir {}: {e}", jar.display()))?;
    let mut zip = zip::ZipArchive::new(file)
        .map_err(|e| format!("zip {}: {e}", jar.display()))?;
    std::fs::create_dir_all(dest).ok();
    for i in 0..zip.len() {
        let mut entry = zip.by_index(i).map_err(|e| e.to_string())?;
        if entry.is_dir() {
            continue;
        }
        let name = entry.name().to_string();
        if name.starts_with("META-INF/")
            || name.ends_with(".class")
            || name.ends_with(".java")
            || name.ends_with(".sha1")
        {
            continue;
        }
        let Some(base) = Path::new(&name)
            .file_name()
            .and_then(|n| n.to_str())
            .map(|s| s.to_string())
        else {
            continue;
        };
        let out = dest.join(&base);
        if out.exists() {
            continue;
        }
        let mut buf = Vec::new();
        entry
            .read_to_end(&mut buf)
            .map_err(|e| format!("{name}: {e}"))?;
        std::fs::write(&out, buf).map_err(|e| format!("gravar {}: {e}", out.display()))?;
    }
    Ok(())
}

fn ensure_assets(app: Option<&AppHandle>, v: &Value, mc: &Path) -> Result<PathBuf, String> {
    let idx = v
        .get("assetIndex")
        .ok_or("assetIndex ausente no JSON da versão")?;
    let id = idx.get("id").and_then(|s| s.as_str()).unwrap_or("legacy");
    let url = idx.get("url").and_then(|s| s.as_str()).unwrap_or("");
    let sha1 = idx.get("sha1").and_then(|s| s.as_str());

    let indexes = mc.join("assets").join("indexes");
    let index_path = indexes.join(format!("{id}.json"));
    if !url.is_empty() {
        download_to(url, &index_path, sha1)?;
    }

    let raw = std::fs::read_to_string(&index_path).map_err(|e| format!("index {id}: {e}"))?;
    let index: Value = serde_json::from_str(&raw).map_err(|e| e.to_string())?;
    let objects = index
        .get("objects")
        .and_then(|o| o.as_object())
        .ok_or("objects ausente no asset index")?;

    let total = objects.len();
    let objects_dir = mc.join("assets").join("objects");
    let mut done = 0usize;
    let mut errors = 0usize;
    for (_name, meta) in objects {
        let hash = meta.get("hash").and_then(|h| h.as_str()).unwrap_or("");
        if hash.len() < 2 {
            continue;
        }
        let dest = objects_dir.join(&hash[0..2]).join(hash);
        if dest.exists() {
            done += 1;
            continue;
        }
        let url = format!("https://resources.download.minecraft.net/{}/{}", &hash[0..2], hash);
        match download_to(&url, &dest, Some(hash)) {
            Ok(()) => done += 1,
            Err(e) => {
                errors += 1;
                if errors < 4 {
                    eprintln!("[astucy] asset falhou: {e}");
                }
            }
        }
        if done % 200 == 0 {
            stage(app, "assets", done, total, "baixando objetos");
        }
    }
    if errors > 0 {
        return Err(format!("{errors} assets com falha (rede?). Repita o launch."));
    }
    Ok(mc.join("assets"))
}

/* ================================================================
 * java
 * ================================================================ */

/// Espaço onde javaw.exe roda sem abrir console (GUI sem terminal).
const CREATE_NO_WINDOW: u32 = 0x0800_0000;

/// Versão major do Java (parse de `java -version`): "1.8.0" → 8, "21.0.3" → 21.
fn java_major(path: &Path) -> Option<u32> {
    // javaw.exe não escreve no console — sondar o java.exe irmão
    let probe = if path.file_name().map(|n| n.to_string_lossy().eq_ignore_ascii_case("javaw.exe")).unwrap_or(false) {
        path.with_file_name("java.exe")
    } else {
        path.to_path_buf()
    };
    let out = std::process::Command::new(&probe)
        .arg("-version")
        .creation_flags(CREATE_NO_WINDOW)
        .output()
        .ok()?;
    let text = format!(
        "{}{}",
        String::from_utf8_lossy(&out.stdout),
        String::from_utf8_lossy(&out.stderr)
    );
    let start = text.find("version \"")? + 9;
    let end = text[start..].find('"')? + start;
    let ver = &text[start..end];
    let mut parts = ver.split('.');
    let first: u32 = parts.next()?.parse().ok()?;
    if first == 1 {
        parts.next()?.parse().ok()
    } else {
        Some(first)
    }
}

/// Ajustes do usuário lidos do settings.json do launcher (plugin store).
/// Chaves: "settings.ram" (MB), "settings.res_w", "settings.res_h", "settings.jvm".
/// Defaults conservadores quando ausentes (RAM 2560, resolução 1280×720).
#[derive(Default, Clone)]
pub struct UserSettings {
    pub ram: u32,
    pub res_w: u32,
    pub res_h: u32,
    jvm: Option<String>,
}

fn load_user_settings() -> UserSettings {
    let mut s = UserSettings {
        ram: 2560,
        res_w: 1280,
        res_h: 720,
        jvm: None,
    };
    let Ok(base) = std::env::var("APPDATA") else {
        return s;
    };
    let path = std::path::Path::new(&base)
        .join("com.astucy.launcher")
        .join("settings.json");
    let Ok(text) = std::fs::read_to_string(path) else {
        return s;
    };
    let Ok(d) = serde_json::from_str::<serde_json::Value>(&text) else {
        return s;
    };
    if let Some(v) = d.get("settings.ram").and_then(|x| x.as_u64()) {
        if (512..=65536).contains(&v) {
            s.ram = v as u32;
        }
    }
    if let Some(v) = d.get("settings.res_w").and_then(|x| x.as_u64()) {
        if (640..=7680).contains(&v) {
            s.res_w = v as u32;
        }
    }
    if let Some(v) = d.get("settings.res_h").and_then(|x| x.as_u64()) {
        if (480..=4320).contains(&v) {
            s.res_h = v as u32;
        }
    }
    if let Some(j) = d.get("settings.jvm").and_then(|x| x.as_str()) {
        let clean = j.trim().to_string();
        if !clean.is_empty() {
            s.jvm = Some(clean);
        }
    }
    s
}

/// Escolhe o melhor Java: major >= 21 exigido (MC 26.x / Fabric), preferindo o mais novo.
fn java_candidates() -> Vec<(PathBuf, u32)> {
    if let Some(cached) = JAVA_CACHE.lock().ok().and_then(|g| g.clone()) {
        return cached;
    }
    let out = java_candidates_scan();
    if let Ok(mut g) = JAVA_CACHE.lock() {
        *g = Some(out.clone());
    }
    out
}

/// Um `java -version` por candidato custa caro (~1 s cada). O resultado fica
/// em cache: a UI pergunta a cada 5 s e não pode varrer processos toda vez.
static JAVA_CACHE: std::sync::Mutex<Option<Vec<(PathBuf, u32)>>> =
    std::sync::Mutex::new(None);

fn java_candidates_scan() -> Vec<(PathBuf, u32)> {
    let mut candidates: Vec<PathBuf> = Vec::new();
    if let Ok(home) = std::env::var("JAVA_HOME") {
        candidates.push(Path::new(&home).join("bin").join("javaw.exe"));
        candidates.push(Path::new(&home).join("bin").join("java.exe"));
    }
    for root_var in ["PROGRAMFILES", "PROGRAMFILES(X86)", "LOCALAPPDATA"] {
        if let Ok(root) = std::env::var(root_var) {
            for sub in ["Java", "Eclipse Adoptium", "Microsoft", "Zulu", "Programs"] {
                let dir = Path::new(&root).join(sub);
                if let Ok(entries) = std::fs::read_dir(&dir) {
                    for e in entries.flatten() {
                        candidates.push(e.path().join("bin").join("javaw.exe"));
                        candidates.push(e.path().join("bin").join("java.exe"));
                    }
                }
            }
        }
    }
    // runtime do Minecraft Launcher (MSI): Program Files (x86)\Minecraft Launcher\runtime\*
    if let Ok(x86) = std::env::var("PROGRAMFILES(X86)") {
        let dir = Path::new(&x86).join("Minecraft Launcher").join("runtime");
        if let Ok(entries) = std::fs::read_dir(&dir) {
            for e in entries.flatten() {
                candidates.push(e.path().join("bin").join("javaw.exe"));
                candidates.push(e.path().join("bin").join("java.exe"));
            }
        }
    }
    // runtime do Store launcher: %LOCALAPPDATA%\Packages\Microsoft.4297127D64EC6_*\LocalCache\Local\runtime
    if let Ok(local) = std::env::var("LOCALAPPDATA") {
        let pkgs = Path::new(&local).join("Packages");
        if let Ok(entries) = std::fs::read_dir(&pkgs) {
            for e in entries
                .flatten()
                .filter(|x| x.file_name().to_string_lossy().starts_with("Microsoft.4297127D64EC6"))
            {
                let rt = e.path().join("LocalCache").join("Local").join("runtime");
                if let Ok(sites) = std::fs::read_dir(&rt) {
                    for s in sites.flatten() {
                        candidates.push(s.path().join("bin").join("javaw.exe"));
                        candidates.push(s.path().join("bin").join("java.exe"));
                    }
                }
            }
        }
    }
    if let Ok(out) = std::process::Command::new("where")
        .arg("javaw")
        .creation_flags(CREATE_NO_WINDOW)
        .output()
    {
        if out.status.success() {
            for line in String::from_utf8_lossy(&out.stdout).lines() {
                candidates.push(PathBuf::from(line.trim()));
            }
        }
    }

    let mut out: Vec<(PathBuf, u32)> = Vec::new();
    for c in candidates {
        if !c.exists() {
            continue;
        }
        if let Some(major) = java_major(&c) {
            if !out.iter().any(|(p, _)| *p == c) {
                out.push((c, major));
            }
        }
    }
    out.sort_by_key(|(_, m)| *m);
    out
}

/// Escolhe o Java certo para a versão: Java 8 exato nas antigas (17+ quebra o
/// LWJGL 2) e o mais novo disponível (≥ mínimo) nas modernas.
fn pick_java(need: u32) -> Result<(PathBuf, u32), String> {
    let all = java_candidates();
    if all.is_empty() {
        return Err(format!(
            "Nenhum Java encontrado. Instale o Temurin {need} (https://adoptium.net) e tente de novo."
        ));
    }
    if need <= 8 {
        if let Some(c) = all.iter().find(|(_, m)| *m == 8) {
            return Ok(c.clone());
        }
        if let Some(c) = all.first() {
            return Ok(c.clone());
        }
    }
    if let Some(c) = all.iter().rev().find(|(_, m)| *m >= need) {
        return Ok(c.clone());
    }
    Err(format!(
        "Esta versão pede Java {need}+ e o mais novo aqui é Java {}. Instale o Temurin {need}.",
        all.last().map(|(_, m)| *m).unwrap_or(0)
    ))
}

/* ================================================================
 * tokens + montagem de argv
 * ================================================================ */

fn interpolate(s: &str, map: &HashMap<String, String>) -> String {
    let mut out = s.to_string();
    for (k, v) in map {
        out = out.replace(&format!("${{{k}}}"), v);
    }
    out
}

/// CDS dinâmico: (arquivo de classes, precisa regravar?).
///
/// A primeira abertura do jogo termina gravando `astucy-classes.jsa`; as
/// seguintes mapeiam esse arquivo (as classes já vêm prontas) em vez de
/// parsear ~4 mil classes do zero. Se algum jar do classpath ficou mais novo
/// que o arquivo (versão nova, mod atualizado), regrava.
fn cds_plan(mc: &Path, cp: &str) -> (PathBuf, bool) {
    let archive = mc.join("astucy-classes.jsa");
    let newest = cp
        .split(';')
        .filter_map(|e| std::fs::metadata(e).ok())
        .filter_map(|m| m.modified().ok())
        .filter_map(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
        .map(|d| d.as_secs())
        .max()
        .unwrap_or(0);
    let ok = std::fs::metadata(&archive)
        .ok()
        .filter(|m| m.len() > 1_000_000)
        .and_then(|m| m.modified().ok())
        .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
        .map(|d| d.as_secs() >= newest)
        .unwrap_or(false);
    (archive, !ok)
}

fn arg_entries(list: Option<&Value>, map: &HashMap<String, String>) -> Vec<String> {    let mut out = Vec::new();
    let Some(Value::Array(items)) = list else {
        return out;
    };
    for item in items {
        match item {
            Value::String(s) => {
                let v = interpolate(s, map);
                if !v.trim().is_empty() {
                    out.push(v);
                }
            }
            Value::Array(parts) => {
                // [ {rules…}, "valor", "valor"… ]
                let Some((rules, values)) = parts.split_first() else {
                    continue;
                };
                if rules_allow(Some(rules)) {
                    for v in values {
                        if let Some(s) = v.as_str() {
                            let v = interpolate(s, map);
                            if !v.trim().is_empty() {
                                out.push(v);
                            }
                        }
                    }
                }
            }
            _ => {}
        }
    }
    out
}

/// Remove o 2º+, 3º… par `flag valor` do argv (mantém o 1º).
fn dedupe_value_flags(argv: &mut Vec<String>, keys: &[&str]) {
    for key in keys {
        let mut seen = 0usize;
        let mut i = 0usize;
        while i < argv.len() {
            if argv[i] == *key {
                seen += 1;
                if seen == 1 {
                    i += 2;
                    continue;
                }
                let end = (i + 1).min(argv.len() - 1);
                argv.drain(i..=end);
                continue;
            }
            i += 1;
        }
    }
}

/// Journal de falhas de launch (a UI pode fechar antes de mostrar o toast).
fn journal_launch_error(version: &str, msg: &str) {
    let dir = std::env::var("LOCALAPPDATA")
        .map(|l| Path::new(&l).join("astucy").join("logs"))
        .unwrap_or_else(|_| PathBuf::from("."));
    let _ = std::fs::create_dir_all(&dir);
    let line = format!(
        "[{}] {version}: {}\n",
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0),
        msg.replace('\n', " | ")
    );
    let _ = std::fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(dir.join("launch-errors.log"))
        .and_then(|mut f| std::io::Write::write_all(&mut f, line.as_bytes()));
}

/// Redige valores sensíveis (token, uuid, nick) de um argv para log/evento.
fn redact(args: &[String], secrets: &[&str]) -> Vec<String> {
    args.iter()
        .map(|a| {
            let mut s = a.clone();
            for sec in secrets {
                if !sec.is_empty() {
                    s = s.replace(sec, "***");
                }
            }
            s
        })
        .collect()
}

/// UUID offline clássico: md5("OfflinePlayer:<nick>") com bits de versão 3.
fn offline_uuid(nick: &str) -> String {
    use md5::{Digest, Md5};
    let mut h = Md5::new();
    h.update(format!("OfflinePlayer:{nick}").as_bytes());
    let mut b = h.finalize();
    b[6] = (b[6] & 0x0f) | 0x30; // versão 3
    b[8] = (b[8] & 0x3f) | 0x80; // variante RFC4122
    let hex: String = b.iter().map(|x| format!("{x:02x}")).collect();
    format!(
        "{}-{}-{}-{}-{}",
        &hex[0..8],
        &hex[8..12],
        &hex[12..16],
        &hex[16..20],
        &hex[20..32]
    )
}

/* ================================================================
 * launch principal
 * ================================================================ */

/// Pede ao Fabric meta o loader mais novo para esta versão do jogo e devolve
/// o profile json pronto (mainClass + libraries + arguments).
fn fabric_profile(version: &str) -> Result<(String, serde_json::Value), String> {
    let client = reqwest::blocking::Client::builder()
        .user_agent("AstucyLauncher/0.1")
        .timeout(std::time::Duration::from_secs(20))
        .build()
        .map_err(|e| format!("http: {e}"))?;

    let list: serde_json::Value = client
        .get(format!("https://meta.fabricmc.net/v2/versions/loader/{version}"))
        .send()
        .and_then(|r| r.error_for_status())
        .and_then(|r| r.json())
        .map_err(|e| format!("fabric meta: {e}"))?;

    let loader = list
        .as_array()
        .and_then(|a| a.first())
        .and_then(|v| v.get("loader"))
        .and_then(|l| l.get("version"))
        .and_then(|s| s.as_str())
        .ok_or("Fabric meta não devolveu loader")?
        .to_string();

    let prof: serde_json::Value = client
        .get(format!(
            "https://meta.fabricmc.net/v2/versions/loader/{version}/{loader}/profile/json"
        ))
        .send()
        .and_then(|r| r.error_for_status())
        .and_then(|r| r.json())
        .map_err(|e| format!("fabric profile: {e}"))?;

    Ok((loader, prof))
}

/// "Sempre Fabric": devolve o id de um perfil Fabric para a versão escolhida.
/// 1) versão já é Fabric → tal como está;
/// 2) perfil astucy-fabric-<v> já criado antes → reuso;
/// 3) senão, cria a partir do template fabric-loader-* instalado na máquina
///    (asm/mixin/loader são versão-independentes; jar do cliente e assets
///    vêm do inheritsFrom). Versões desofuscadas (26.x) só — as antigas
///    exigiriam intermediary próprio.
fn ensure_fabric_profile(app: Option<&AppHandle>, version: &str) -> Result<String, String> {
    let appdata = std::env::var("APPDATA").map_err(|_| "APPDATA não definido")?;
    let versions = Path::new(&appdata).join(".minecraft").join("versions");

    let is_fabric = |id: &str| -> bool {
        std::fs::read_to_string(versions.join(id).join(format!("{id}.json")))
            .ok()
            .and_then(|raw| serde_json::from_str::<serde_json::Value>(&raw).ok())
            .and_then(|j| {
                j.get("mainClass")
                    .and_then(|m| m.as_str())
                    .map(|m| m.contains("Knot"))
            })
            .unwrap_or(false)
    };

    if is_fabric(version) {
        return Ok(version.to_string());
    }

    // 26.x = desofuscado; 1.x/antigas ficam tal como escolhido (sem pack,
    // pois exigiriam intermediary próprio por versão).
    let unobfuscated = is_client_version(version);
    if !unobfuscated {
        return Ok(version.to_string());
    }

    let profile = format!("astucy-fabric-{version}");
    let prof_path = versions.join(&profile).join(format!("{profile}.json"));

    // Loader mais novo do Fabric para esta versão do jogo. O template local
    // pode estar velho (0.19.x) e aí mods recentes recusam carregar
    // ("requires Fabric Loader 0.20.1 or later").
    if let Ok((loader_ver, mut prof)) = fabric_profile(version) {
        let already = std::fs::read_to_string(&prof_path)
            .ok()
            .and_then(|raw| serde_json::from_str::<serde_json::Value>(&raw).ok())
            .and_then(|j| j.get("_astucyLoader").and_then(|v| v.as_str()).map(|s| s == loader_ver))
            .unwrap_or(false);
        if !already {
            prof["id"] = serde_json::Value::String(profile.clone());
            prof["inheritsFrom"] = serde_json::Value::String(version.to_string());
            prof["_astucyLoader"] = serde_json::Value::String(loader_ver.clone());
            let dir = versions.join(&profile);
            std::fs::create_dir_all(&dir).map_err(|e| format!("dir {profile}: {e}"))?;
            std::fs::write(
                &prof_path,
                serde_json::to_string_pretty(&prof).unwrap_or_default(),
            )
            .map_err(|e| format!("gravar {profile}: {e}"))?;
            stage(app, "versão", 0, 3, &format!("{version} + Fabric {loader_ver}"));
        } else {
            stage(app, "versão", 0, 3, &format!("{version} + Fabric {loader_ver}"));
        }
        return Ok(profile);
    }

    // offline: reusa o que já existe
    if is_fabric(&profile) {
        stage(app, "versão", 0, 3, &format!("{version} + Fabric (perfil pronto)"));
        return Ok(profile);
    }

    // offline e sem perfil: copia o template local
    let mut template: Option<String> = None;
    if let Ok(entries) = std::fs::read_dir(&versions) {
        for e in entries.flatten() {
            let name = e.file_name().to_string_lossy().to_string();
            if name.starts_with("fabric-loader-") && is_fabric(&name) {
                template = Some(name);
                break;
            }
        }
    }
    let template = template.ok_or_else(|| {
        "Sem internet para baixar o Fabric Loader e sem nenhum instalado na máquina. Conecte e tente de novo."
            .to_string()
    })?;

    let raw = std::fs::read_to_string(
        versions.join(&template).join(format!("{template}.json")),
    )
    .map_err(|e| format!("template {template}: {e}"))?;
    let mut prof: serde_json::Value = serde_json::from_str(&raw)
        .map_err(|e| format!("template {template}: {e}"))?;
    prof["id"] = serde_json::Value::String(profile.clone());
    prof["inheritsFrom"] = serde_json::Value::String(version.to_string());

    let dir = versions.join(&profile);
    std::fs::create_dir_all(&dir).map_err(|e| format!("dir {profile}: {e}"))?;
    std::fs::write(
        dir.join(format!("{profile}.json")),
        serde_json::to_string_pretty(&prof).unwrap_or_default(),
    )
    .map_err(|e| format!("gravar {profile}: {e}"))?;

    stage(app, "versão", 0, 3, &format!("{version} + Fabric ({template})"));
    Ok(profile)
}

/// wrapper de diagnóstico: qualquer falha (antes ou depois do spawn)
/// fica no disco — a UI pode fechar antes de exibir o toast.
pub fn launch(
    app: Option<&AppHandle>,
    nick: &str,
    version: &str,
    account: Option<&str>,
) -> Result<u32, String> {
    match launch_inner(app, nick, version, account) {
        Ok(pid) => Ok(pid),
        Err(e) => {
            journal_launch_error(version, &e);
            Err(e)
        }
    }
}

fn launch_inner(
    app: Option<&AppHandle>,
    nick: &str,
    version: &str,
    account: Option<&str>,
) -> Result<u32, String> {
    let base_version = version.to_string();
    // Conta Microsoft (quando houver): o token é resolvido aqui no backend,
    // nunca no webview. Se a sessão expirou, o refresh acontece agora.
    let session = match account.filter(|a| !a.trim().is_empty()) {
        Some(uuid) => Some(crate::auth::session_for(uuid.trim()).map_err(|e| fail(app, &e))?),
        None => None,
    };
    let nick = match &session {
        Some(s) => s.name.as_str(),
        None => nick.trim(),
    };
    if nick.is_empty() {
        return Err(fail(app, "Escolha um nick para jogar offline"));
    }
    if !nick.chars().all(|c| c.is_ascii_alphanumeric() || c == '_') {
        return Err(fail(app, "Nick só pode ter letras, números e _"));
    }

    // A versão pode não existir nesta máquina: baixa o JSON + jar da Mojang.
    ensure_version(app, version)?;

    // SEMPRE FABRIC (26.x): injeta o loader + pack do Astucy antes de qualquer coisa.
    let version = ensure_fabric_profile(app, version)?;

    // módulo astucyc no perfil = mesma build da release (auto-update silencioso;
    // se a rede falhar, segue a partida com o jar que já está no perfil)
    let _ = crate::profile::sync_core_mod();
    let p = paths(&version)?;

    stage(app, "versão", 0, 3, &version);
    let v = version_json(&p.mc, &version)?;

    // Java certo por versão: 8 nas antigas (17+ quebra o LWJGL 2), 17/21+ nas
    // modernas — o próprio JSON da versão diz o mínimo (`javaVersion`).
    let need = v
        .get("javaVersion")
        .and_then(|j| j.get("majorVersion"))
        .and_then(|m| m.as_u64())
        .map(|m| m as u32)
        .unwrap_or(8);
    let (java, java_ver) = pick_java(need)?;
    stage(app, "java", 3, 3, &format!("Java {java_ver} — {}", java.display()));
    expose_gpu(&java);

    // manifest geral (mantém o launcher sabido das versões; cache 24h via disco)
    let _ = fetch_json(MANIFEST_URL, Some(&p.meta.join("version_manifest_v2.json")));

    stage(app, "bibliotecas", 1, 3, "verificando por sha1");
    let mut classpath = resolve_libraries(&v, &p.mc.join("libraries"), &p.natives)?;

    stage(app, "assets", 2, 3, "verificando por sha1");
    let assets_root = ensure_assets(app, &v, &p.mc)?;

    // jar do cliente vai primeiro no classpadrão (estilo vanilla)
    let jar_id = v
        .get("jar")
        .and_then(|s| s.as_str())
        .or_else(|| v.get("id").and_then(|s| s.as_str()))
        .unwrap_or(&version);
    let client_jar = p
        .mc
        .join("versions")
        .join(jar_id)
        .join(format!("{jar_id}.jar"));
    if !client_jar.exists() {
        return Err(fail(
            app,
            &format!("Jar do cliente não encontrado: {jar_id}. Instale essa versão pelo launcher oficial ou pelo Astucy (em breve)."),
        ));
    }
    classpath.insert(0, client_jar);

    let cp_str = classpath
        .iter()
        .map(|s| s.to_string_lossy().to_string())
        .collect::<Vec<_>>()
        .join(";"); // Windows: sem wildcard, lista explícita

    // ---------- tokens ----------
    let (uuid, user_type, access_token, xuid) = match &session {
        Some(s) => (
            s.uuid.clone(),
            "msa".to_string(),
            s.access_token.clone(),
            s.xuid.clone(),
        ),
        None => (
            offline_uuid(nick),
            "legacy".to_string(),
            "0".to_string(),
            String::new(),
        ),
    };
    // cópia usada só para redigir o log/evento de launch — o token em si
    // continua só no argv do processo Java (nunca vai para arquivo)
    let redact_token = access_token.clone();
    let mut map: HashMap<String, String> = HashMap::new();
    map.insert("auth_player_name".into(), nick.into());
    map.insert("auth_uuid".into(), uuid.clone());
    map.insert("auth_access_token".into(), access_token);
    map.insert("user_type".into(), user_type);
    map.insert("user_properties".into(), "{}".into());
    map.insert("auth_xuid".into(), xuid);
    map.insert(
        "clientid".into(),
        crate::auth::client_id().unwrap_or_default(),
    );
    map.insert("version_name".into(), version.clone());
    map.insert("version_type".into(),
        v.get("type").and_then(|s| s.as_str()).unwrap_or("release").into());
    // Pasta do jogo:
    //  - 26.x (client)  → astucy-profile (mods + tema)
    //  - legado ≤1.12   → pasta própria (o options.txt antigo destruiria o
    //                     formato novo do 26.x se dividisse o .minecraft)
    //  - resto          → .minecraft normal
    let legacy = v.get("arguments").is_none() && v.get("minecraftArguments").is_some();
    let knot = v
        .get("mainClass")
        .and_then(|s| s.as_str())
        .map(|m| m.contains("Knot"))
        .unwrap_or(false);
    let game_dir = if knot {
        p.mc.join("astucy-profile")
    } else if legacy {
        p.mc.join("astucy-versions").join(&base_version)
    } else {
        p.mc.clone()
    };
    let _ = std::fs::create_dir_all(&game_dir);
    map.insert("game_directory".into(), game_dir.to_string_lossy().into());
    map.insert("assets_root".into(), assets_root.to_string_lossy().into());
    map.insert("assets_index_name".into(),
        v.get("assetIndex").and_then(|i| i.get("id")).and_then(|s| s.as_str()).unwrap_or("legacy").into());
    map.insert("natives_directory".into(), p.natives.to_string_lossy().into());
    map.insert("classpath".into(), cp_str.clone());
    map.insert("classpath_separator".into(), ";".into());
    map.insert("library_directory".into(), p.mc.join("libraries").to_string_lossy().into());
    map.insert("launcher_name".into(), "astucy".into());
    map.insert("launcher_version".into(), env!("CARGO_PKG_VERSION").into());
    map.insert("version_name".into(), version.clone());

    let args = v.get("arguments");
    let mut argv = arg_entries(args.and_then(|a| a.get("jvm")), &map);

    // Ajustes do usuário (settings.json do launcher) — JVM primeiro, vence
    let user = load_user_settings();
    {
        let mut pre: Vec<String> = Vec::new();
        // Xms baixo = o heap cresce sob demanda: abre mais rápido e ocupa bem
        // menos RAM enquanto está no menu. Xmx continua o limite escolhido.
        let xms = (user.ram / 3).max(512).min(user.ram);
        pre.push(format!("-Xms{}m", xms));
        pre.push(format!("-Xmx{}m", user.ram));
        // G1 ajustado (base Aikar): menos pausa de GC = frametime mais estável.
        // Vem ANTES do jvm do usuário, então o usuário ainda pode sobrescrever.
        // Java 8 (versões antigas) não conhece várias dessas flags — usa o
        // conjunto mínimo para não recusar o boot.
        let flags: &[&str] = if java_ver >= 13 {
            &[
                "-XX:+UseG1GC",
                "-XX:+ParallelRefProcEnabled",
                // alvo de pausa curto: menos engasgo por frame. O G1 escolhe um
                // young gen menor e coleta mais vezes (cada pausa fica curta).
                "-XX:MaxGCPauseMillis=120",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+DisableExplicitGC",
                // deduplica strings iguais (chat, nomes de asset) → menos heap
                "-XX:+UseStringDeduplication",
                "-XX:G1NewSizePercent=20",
                "-XX:G1MaxNewSizePercent=40",
                "-XX:G1HeapRegionSize=8M",
                "-XX:G1ReservePercent=20",
                "-XX:G1HeapWastePercent=5",
                "-XX:G1MixedGCCountTarget=4",
                "-XX:InitiatingHeapOccupancyPercent=15",
                "-XX:G1MixedGCLiveThresholdPercent=90",
                "-XX:G1RSetUpdatingPauseTimePercent=5",
                "-XX:SurvivorRatio=32",
                "-XX:MaxTenuringThreshold=1",
                "-XX:+PerfDisableSharedMem",
            ]
        } else {
            &[
                "-XX:+UseG1GC",
                "-XX:MaxGCPauseMillis=120",
                "-XX:+DisableExplicitGC",
                "-XX:+UseStringDeduplication",
            ]
        };
        for flag in flags {
            pre.push(flag.to_string());
        }

        // ---- CDS dinâmico ------------------------------------------------
        // A primeira abertura termina gravando um arquivo com as classes que
        // o jogo já carregou; as seguintes sobem lendo esse arquivo em vez de
        // parsear ~4 mil classes de novo. Se um jar mudou, regrava.
        // (só existe do Java 13 pra cima)
        if java_ver >= 13 {
            let (cds, regen) = cds_plan(&p.mc, &cp_str);
            if regen {
                pre.push(format!("-XX:ArchiveClassesAtExit={}", cds.to_string_lossy()));
            } else {
                pre.push(format!("-XX:SharedArchiveFile={}", cds.to_string_lossy()));
                pre.push("-Xshare:auto".into());
            }
        }

        // versões legadas (1.7.10) não trazem -Djava.library.path no JSON —
        // o LWJGL 2 procura as DLLs soltas na pasta de natives
        if v.get("arguments").is_none() && v.get("minecraftArguments").is_some() {
            pre.push(format!("-Djava.library.path={}", p.natives.display()));
        }
        if let Some(jvm) = &user.jvm {
            pre.extend(jvm.split_whitespace().map(str::to_string));
        }
        argv.splice(0..0, pre);
    }

    // -cp: só acrescenta se o JSON da versão não trouxe o próprio (evita duplicar)
    if !argv.iter().any(|a| a == "-cp" || a == "-classpath") {
        argv.push("-cp".into());
        argv.push(cp_str.clone());
    }

    let main_class = v
        .get("mainClass")
        .and_then(|s| s.as_str())
        .ok_or_else(|| fail(app, "mainClass ausente (ex.: KnotClient do Fabric)"))?;
    argv.push(main_class.into());
    argv.extend(arg_entries(args.and_then(|a| a.get("game")), &map));

    // Ajustes de resolução (vencem só quando o JSON não trouxe as próprias)
    if !argv.iter().any(|a| a == "--width" || a == "--height") {
        argv.push("--width".into());
        argv.push(user.res_w.to_string());
        argv.push("--height".into());
        argv.push(user.res_h.to_string());
    }

    // Perfil Fabric: pasta própria com mods + tema visual
    if knot {
        let profile = p.mc.join("astucy-profile");
        let _ = std::fs::create_dir_all(profile.join("mods"));
        if let Err(e) = ensure_theme_pack(&profile) {
            eprintln!("[astucy] tema: {e}");
        }
    }

    // JSONs antigos podem não trazer --gameDir; garante a pasta escolhida
    if !argv.iter().any(|a| a == "--gameDir") {
        argv.push("--gameDir".into());
        argv.push(game_dir.to_string_lossy().into());
    }

    // fallback vanilla: versões antigas sem arguments.game
    if args.is_none() {
        if let Some(legacy) = v.get("minecraftArguments").and_then(|s| s.as_str()) {
            for t in legacy.split_whitespace() {
                argv.push(interpolate(t, &map));
            }
        }
    }

    // pares duplicados recusam o boot em versões antigas (jopt-simple):
    // o minecraftArguments legado já contém --gameDir/--username/…, e nós
    // empurramos o nosso por garantia — deixa só o primeiro par de cada.
    dedupe_value_flags(
        &mut argv,
        &[
            "--gameDir",
            "--username",
            "--version",
            "--assetsDir",
            "--assetIndex",
            "--uuid",
            "--accessToken",
            "--userProperties",
            "--userType",
            "--clientId",
            "--width",
            "--height",
        ],
    );

    // ---------- spawn ----------
    let display_argv = redact(&argv, &[&uuid, nick, redact_token.as_str(), "0"]);
    if let Some(app) = app {
        let _ = app.emit(
            "launch://argv",
            json!({ "java": java.display().to_string(), "args": display_argv }),
        );
    }

    // log de diagnóstico: argv redigido + stdout/stderr do JVM capturados
    let log_dir = p.meta.parent().map(|d| d.join("logs")).unwrap_or_else(|| p.meta.clone());
    std::fs::create_dir_all(&log_dir).ok();
    let log_path = log_dir.join(format!(
        "launch-{version}-{}.log",
        std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0)
    ));
    let log_text = format!(
        "java: {} (major {})\nversion: {}\nargv:\n  {}\n--- stdout/stderr ---\n",
        java.display(),
        java_ver,
        version,
        display_argv.join("\n  ")
    );
    let _ = std::fs::write(&log_path, &log_text);

    let log_file = std::fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(&log_path);
    let (out, err) = match &log_file {
        Ok(f) => (
            std::process::Stdio::from(f.try_clone().unwrap()),
            std::process::Stdio::from(f.try_clone().unwrap()),
        ),
        Err(_) => (std::process::Stdio::null(), std::process::Stdio::null()),
    };

    let launcher_exe = std::env::current_exe().unwrap_or_default();
    // troca de conta: o jogo anterior precisa fechar antes (mesma .minecraft)
    wait_previous_game();
    let mut child = std::process::Command::new(&java)
        .args(&argv)
        .current_dir(&p.mc)
        // o mod usa isso pra saber qual launcher abrir ao trocar de conta
        .env("ASTUCY_LAUNCHER", &launcher_exe)
        .creation_flags(CREATE_NO_WINDOW)
        .stdin(std::process::Stdio::null())
        .stdout(out)
        .stderr(err)
        .spawn()
        .map_err(|e| fail(app, &format!("spawn javaw: {e}")))?;

    let pid = child.id();
    // O JVM pode morrer na largada (RAM curta, Java errado, jar faltando).
    // Confere antes de dizer "jogo aberto" — e antes do launcher se fechar.
    std::thread::sleep(std::time::Duration::from_millis(900));
    if let Ok(Some(status)) = child.try_wait() {
        return Err(fail(
            app,
            &format!(
                "O jogo fechou logo ao abrir ({status}). Detalhes em {}",
                log_path.display()
            ),
        ));
    }
    if let Ok(mut g) = GAME.lock() {
        *g = Some(child);
    }
    verified_flush();
    if let Some(app) = app {
        let _ = app.emit(
            "launch://done",
            json!({ "pid": pid, "version": version, "nick": nick, "log": log_path.display().to_string() }),
        );
    }
    Ok(pid)
}

/* ================================================================
 * sistema + pastas (dados reais para a home do launcher)
 * ================================================================ */

#[cfg(windows)]
fn memory_mb() -> (u64, u64) {
    use windows_sys::Win32::System::SystemInformation::{
        GlobalMemoryStatusEx, MEMORYSTATUSEX,
    };
    unsafe {
        let mut st: MEMORYSTATUSEX = std::mem::zeroed();
        st.dwLength = std::mem::size_of::<MEMORYSTATUSEX>() as u32;
        if GlobalMemoryStatusEx(&mut st) != 0 {
            return (
                st.ullTotalPhys / (1024 * 1024),
                st.ullAvailPhys / (1024 * 1024),
            );
        }
    }
    (0, 0)
}

#[cfg(not(windows))]
fn memory_mb() -> (u64, u64) {
    (0, 0)
}

/// RAM total/livre + todos os Java achados (para o painel Sistema).
pub fn system_info() -> Value {
    let (total, avail) = memory_mb();
    let java: Vec<Value> = java_candidates()
        .into_iter()
        .map(|(p, m)| json!({ "major": m, "path": p.display().to_string() }))
        .collect();
    json!({
        "total_mb": total,
        "avail_mb": avail,
        "java": java,
        "game_dir": mc_root().display().to_string(),
    })
}

pub fn open_folder(kind: &str) -> Result<(), String> {
    let mc = mc_root();
    let path = match kind {
        "logs" => mc.join("logs"),
        "mods" => mc.join("astucy-profile").join("mods"),
        "versions" => mc.join("versions"),
        "profile" => mc.join("astucy-profile"),
        _ => mc.clone(),
    };
    std::fs::create_dir_all(&path).ok();
    std::process::Command::new("explorer")
        .arg(&path)
        .creation_flags(CREATE_NO_WINDOW)
        .spawn()
        .map(|_| ())
        .map_err(|e| format!("abrir {}: {e}", path.display()))
}
