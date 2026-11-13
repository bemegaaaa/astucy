use serde::Serialize;
use std::path::{Path, PathBuf};

/// Perfil do Astucy (mesmo que o motor de launch usa).
fn profile_root() -> Result<PathBuf, String> {
    let appdata = std::env::var("APPDATA").map_err(|_| "APPDATA não definido".to_string())?;
    Ok(Path::new(&appdata).join(".minecraft").join("astucy-profile"))
}

pub(crate) fn mods_dir() -> Result<PathBuf, String> {
    let d = profile_root()?.join("mods");
    std::fs::create_dir_all(&d).map_err(|e| format!("pasta de mods: {e}"))?;
    Ok(d)
}

pub fn mods_dir_for(version: &str) -> Result<PathBuf, String> {
    let base = mods_dir()?;
    let ver = version.trim();
    if ver.is_empty() {
        return Ok(base);
    }
    let safe: String = ver
        .chars()
        .map(|c| if c.is_ascii_alphanumeric() || matches!(c, '.' | '-' | '_') { c } else { '_' })
        .collect();
    let d = base.join(&safe);
    let newly_created = !d.exists();
    std::fs::create_dir_all(&d).map_err(|e| format!("pasta de mods: {e}"))?;
    // Migra .jars antigos da raiz apenas para a pasta 26.2 na primeira vez.
    if newly_created && safe == "26.2" && let Ok(entries) = std::fs::read_dir(&base) {
        for e in entries.flatten() {
            let p = e.path();
            if p.is_file() {
                let name = e.file_name().to_string_lossy().to_string();
                let lower = name.to_ascii_lowercase();
                if lower.ends_with(".jar") || lower.ends_with(".jar.disabled") {
                    let _ = std::fs::rename(&p, d.join(&name));
                }
            }
        }
    }
    Ok(d)
}

pub fn rust_addon_enabled(id: &str, default_on: bool) -> bool {
    let Ok(root) = profile_root() else { return default_on; };
    let data = read_json(&root.join("astucy-rust.json"));
    let idx = data.get("active_profile").and_then(|v| v.as_u64()).unwrap_or(0) as usize;
    data.get("profiles")
        .and_then(|p| p.get(idx))
        .and_then(|arr| arr.as_array())
        .and_then(|arr| arr.iter().find(|m| m.get("id").and_then(|v| v.as_str()) == Some(id)))
        .and_then(|m| m.get("enabled").and_then(|v| v.as_bool()))
        .unwrap_or(default_on)
}

pub fn set_rust_addon_enabled(id: &str, on: bool) -> Result<(), String> {
    let root = profile_root()?;
    std::fs::create_dir_all(&root).map_err(|e| format!("perfil: {e}"))?;
    let path = root.join("astucy-rust.json");
    let mut data = read_json(&path);
    if !data.is_object() || data.get("version").and_then(|v| v.as_u64()) != Some(2) {
        data = serde_json::json!({
            "version": 2,
            "active_profile": 0,
            "profiles": [[], [], []],
            "crosshair_invert": true,
            "light_mode": false
        });
    }
    let idx = data.get("active_profile").and_then(|v| v.as_u64()).unwrap_or(0).min(2) as usize;
    let profiles = data
        .as_object_mut()
        .and_then(|o| o.get_mut("profiles"))
        .and_then(|p| p.as_array_mut())
        .ok_or_else(|| "astucy-rust.json inválido".to_string())?;
    while profiles.len() < 3 {
        profiles.push(serde_json::json!([]));
    }
    if !profiles[idx].is_array() {
        profiles[idx] = serde_json::json!([]);
    }
    let arr = profiles[idx].as_array_mut().unwrap();
    if let Some(entry) = arr.iter_mut().find(|m| m.get("id").and_then(|v| v.as_str()) == Some(id)) {
        if let Some(obj) = entry.as_object_mut() {
            obj.insert("enabled".into(), serde_json::Value::Bool(on));
        }
    } else {
        arr.push(serde_json::json!({
            "id": id,
            "enabled": on,
            "anchor": [0.5, 0.5],
            "scale": 1.0,
            "bg": true,
            "bg_opacity": 0.45,
            "bg_rgb": 0,
            "fg_rgb": 15527923,
            "shadow": false,
            "show_prefix": true,
            "level": 0.5
        }));
    }
    let text = serde_json::to_string_pretty(&data).map_err(|e| format!("serializar: {e}"))?;
    std::fs::write(&path, text).map_err(|e| format!("gravar: {e}"))
}

fn config_file() -> Result<PathBuf, String> {
    let d = profile_root()?.join("config");
    std::fs::create_dir_all(&d).map_err(|e| format!("pasta de config: {e}"))?;
    Ok(d.join("astucyc.json"))
}

/* ==================================================================
 * auto-update do módulo do próprio Astucy
 * ================================================================== */

const BUNDLED_CORE_MOD: &[u8] =
    include_bytes!("../../../mod/astucyc/build/libs/astucyc-0.1.0.jar");

/// Localiza `mod/astucyc/build/libs/astucyc-0.1.0.jar` no workspace se existir.
fn find_workspace_core_mod() -> Option<PathBuf> {
    let rel = Path::new("mod")
        .join("astucyc")
        .join("build")
        .join("libs")
        .join("astucyc-0.1.0.jar");
    for start in [
        std::env::current_exe().ok(),
        std::env::current_dir().ok(),
    ]
    .into_iter()
    .flatten()
    {
        let mut cur: Option<&Path> = Some(start.as_path());
        while let Some(dir) = cur {
            let candidate = dir.join(&rel);
            if candidate.is_file() {
                return Some(candidate);
            }
            cur = dir.parent();
        }
    }
    None
}

/// Mantém o `astucyc-0.1.0.jar` do perfil sincronizado com a build local/embutida
/// mais recente (sem sobrescrever com release antiga do GitHub).
pub fn sync_core_mod() -> Result<(), String> {
    const CORE_NAME: &str = "astucyc-0.1.0.jar";

    let dir = mods_dir()?;
    let dest = dir.join(CORE_NAME);

    let fresh_bytes: Option<Vec<u8>> = find_workspace_core_mod()
        .and_then(|p| std::fs::read(p).ok())
        .or_else(|| {
            if !BUNDLED_CORE_MOD.is_empty() {
                Some(BUNDLED_CORE_MOD.to_vec())
            } else {
                None
            }
        });

    if let Some(bytes) = fresh_bytes {
        let same = std::fs::read(&dest)
            .map(|existing| existing == bytes)
            .unwrap_or(false);
        if same {
            return Ok(());
        }
        let tmp = dir.join(".astucyc-update.jar");
        std::fs::write(&tmp, &bytes).map_err(|e| format!("gravar mod: {e}"))?;
        if std::fs::rename(&tmp, &dest).is_err() {
            let _ = std::fs::remove_file(&dest);
            std::fs::rename(&tmp, &dest).map_err(|e| format!("instalar mod: {e}"))?;
        }
        return Ok(());
    }

    Ok(())
}

fn read_json(path: &Path) -> serde_json::Value {
    std::fs::read_to_string(path)
        .ok()
        .and_then(|s| serde_json::from_str::<serde_json::Value>(&s).ok())
        .filter(|v| v.is_object())
        .unwrap_or_else(|| serde_json::json!({}))
}

/* ==================================================================
 * mods instalados no perfil
 * ================================================================== */

#[derive(Serialize)]
pub struct ModEntry {
    file: String,
    name: String,
    enabled: bool,
    size: u64,
    /// mods do próprio Astucy (astucyc / fabric-api) — não podem ser desligados
    core: bool,
    /// slug do Modrinth quando o launcher instalou (usado para marcar "instalado")
    slug: Option<String>,
}

/// slug do Modrinth -> arquivo instalado (gravado a cada instalação).
fn mod_map_path() -> Result<PathBuf, String> {
    Ok(profile_root()?.join("astucy-mods.json"))
}

fn load_mod_map() -> std::collections::HashMap<String, String> {
    let Ok(path) = mod_map_path() else {
        return Default::default();
    };
    let Ok(text) = std::fs::read_to_string(path) else {
        return Default::default();
    };
    serde_json::from_str(&text).unwrap_or_default()
}

fn save_mod_map(map: &std::collections::HashMap<String, String>) {
    if let Ok(path) = mod_map_path() {
        if let Ok(text) = serde_json::to_string_pretty(map) {
            let _ = std::fs::write(path, text);
        }
    }
}

#[tauri::command]
pub fn list_mods() -> Result<Vec<ModEntry>, String> {
    let dir = mods_dir()?;
    let map = load_mod_map();
    let mut out = Vec::new();
    let entries = std::fs::read_dir(&dir).map_err(|e| format!("ler mods: {e}"))?;
    for entry in entries.flatten() {
        let path = entry.path();
        if !path.is_file() {
            continue;
        }
        let file = entry.file_name().to_string_lossy().to_string();
        let lower = file.to_lowercase();
        let (name, enabled) = if lower.ends_with(".jar.disabled") {
            (file[..file.len() - ".disabled".len()].to_string(), false)
        } else if lower.ends_with(".jar") {
            (file.clone(), true)
        } else {
            continue;
        };
        let core = name.to_lowercase().starts_with("astucyc") || name.to_lowercase().starts_with("fabric-api");
        let size = entry.metadata().map(|m| m.len()).unwrap_or(0);
        let slug = map
            .iter()
            .find(|(_, v)| v.as_str() == name)
            .map(|(k, _)| k.clone());
        out.push(ModEntry { file, name, enabled, size, core, slug });
    }
    out.sort_by_key(|m| (m.name.to_lowercase(), m.name.clone()));
    Ok(out)
}

/// Liga/desliga um mod renomeando `.jar` ↔ `.jar.disabled` (nada é apagado).
#[tauri::command]
pub fn set_mod_enabled(file: String, enabled: bool) -> Result<(), String> {
    let lower = file.to_lowercase();
    if lower.starts_with("astucyc") || lower.starts_with("fabric-api") {
        return Err("esse é o núcleo do cliente (astucyc + Fabric API)".into());
    }
    let dir = mods_dir()?;
    let from = dir.join(&file);
    if !from.is_file() {
        return Err(format!("não achei {file}"));
    }
    let to = if enabled {
        if !lower.ends_with(".jar.disabled") {
            return Ok(());
        }
        dir.join(&file[..file.len() - ".disabled".len()])
    } else {
        if !lower.ends_with(".jar") {
            return Ok(());
        }
        dir.join(format!("{file}.disabled"))
    };
    if to.exists() {
        return Err(format!("já existe {}", to.file_name().unwrap_or_default().to_string_lossy()));
    }
    std::fs::rename(&from, &to).map_err(|e| format!("renomear mod: {e}"))
}

#[tauri::command]
pub fn open_mods_folder() -> Result<String, String> {
    let dir = mods_dir()?;
    #[cfg(target_os = "windows")]
    {
        std::process::Command::new("explorer")
            .arg(&dir)
            .spawn()
            .map_err(|e| format!("abrir explorer: {e}"))?;
    }
    Ok(dir.to_string_lossy().to_string())
}

/* ==================================================================
 * módulos do cliente (astucyc.json)
 * ================================================================== */

/// Ids reais do mod (Modules.java). Só esses podem ser alternados.
const CLIENT_MODULES: &[&str] = &[
    "fps", "ping", "server", "memory", "speed", "direcao", "sessao", "hora", "bioma", "coords",
    "efeitos", "cps", "keystrokes", "reach", "totem", "armor", "fullbright", "zoom", "hurtcam",
    "sprint", "sneak", "itens", "pack", "health", "hunger", "combo", "healthtag",
    "target", "armordur", "daycounter", "itemcount", "viewmodel", "crosshair",
    "blur", "speedfx", "ambience", "menublur", "chat", "watermark", "fpsgraph", "realtime",
];

#[derive(Serialize)]
pub struct ClientModule {
    id: String,
    on: bool,
}

#[tauri::command]
pub fn list_client_modules() -> Result<Vec<ClientModule>, String> {
    let data = read_json(&config_file()?);
    // lista fixa + qualquer módulo que o jogo já tenha salvo
    let mut ids: Vec<String> = CLIENT_MODULES.iter().map(|s| s.to_string()).collect();
    if let Some(obj) = data.as_object() {
        for key in obj.keys() {
            if key != "_versao" && !ids.iter().any(|i| i == key) {
                ids.push(key.clone());
            }
        }
    }
    Ok(ids
        .iter()
        .map(|id| ClientModule {
            id: id.clone(),
            on: data
                .get(id)
                .and_then(|m| m.get("enabled"))
                .and_then(|v| v.as_bool())
                .unwrap_or(false),
        })
        .collect())
}

/// Alterna um módulo mantendo todo o estilo já salvo (só mexe na chave "enabled").
#[tauri::command]
pub fn set_client_module(id: String, on: bool) -> Result<(), String> {
    if id.is_empty()
        || id == "_versao"
        || id.len() > 32
        || !id.chars().all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '_')
    {
        return Err("módulo inválido".into());
    }
    let path = config_file()?;
    let mut data = read_json(&path);
    if !data.is_object() {
        data = serde_json::json!({});
    }
    let obj = data.as_object_mut().ok_or("config inválida")?;
    let entry = obj.entry(id).or_insert_with(|| serde_json::json!({}));
    if !entry.is_object() {
        *entry = serde_json::json!({});
    }
    entry
        .as_object_mut()
        .ok_or("config inválida")?
        .insert("enabled".into(), serde_json::Value::Bool(on));
    let text = serde_json::to_string_pretty(&data).map_err(|e| format!("json: {e}"))?;
    std::fs::write(&path, text).map_err(|e| format!("gravar config: {e}"))
}

/* ==================================================================
 * Modrinth: instalar (com dependências obrigatórias) + buscar
 * ================================================================== */

#[derive(Serialize, Clone)]
pub struct InstallResult {
    pub slug: String,
    pub ok: bool,
    pub detail: String,
}

#[derive(Serialize, Clone)]
pub struct SearchHit {
    pub slug: String,
    pub title: String,
    pub description: String,
    pub downloads: u64,
    pub icon: String,
    pub author: String,
}

#[derive(Serialize)]
pub struct SearchPage {
    pub hits: Vec<SearchHit>,
    pub total: u64,
    pub offset: u64,
    pub limit: u64,
}

fn http_client() -> Result<reqwest::blocking::Client, String> {
    reqwest::blocking::Client::builder()
        .user_agent("AstucyLauncher/0.1")
        .timeout(std::time::Duration::from_secs(90))
        .build()
        .map_err(|e| format!("http: {e}"))
}

fn url_encode(s: &str) -> String {
    let mut out = String::with_capacity(s.len() * 3);
    for b in s.bytes() {
        match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => out.push(b as char),
            _ => out.push_str(&format!("%{b:02X}")),
        }
    }
    out
}

fn modrinth_versions(client: &reqwest::blocking::Client, id: &str, version: &str) -> Result<serde_json::Value, String> {
    let url = format!(
        "https://api.modrinth.com/v2/project/{id}/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22{version}%22%5D"
    );
    let text = client
        .get(&url)
        .send()
        .and_then(|r| r.error_for_status())
        .and_then(|r| r.text())
        .map_err(|e| format!("consulta: {e}"))?;
    serde_json::from_str(&text).map_err(|e| format!("json: {e}"))
}

/// Instala um projeto + dependências obrigatórias (até 3 níveis).
fn install_project(
    client: &reqwest::blocking::Client,
    dir: &Path,
    id: &str,
    version: &str,
    depth: u8,
    seen: &mut std::collections::HashSet<String>,
    done_files: &mut std::collections::HashSet<String>,
    out: &mut Vec<InstallResult>,
) {
    let key = id.to_string();
    if depth > 3 || !seen.insert(key) {
        return;
    }

    let versions = match modrinth_versions(client, id, version) {
        Ok(v) => v,
        Err(e) => {
            out.push(InstallResult { slug: id.to_string(), ok: false, detail: e });
            return;
        }
    };
    let empty = Vec::new();
    let list = versions.as_array().unwrap_or(&empty);
    let picked = match list
        .iter()
        .find(|v| v.get("version_type").and_then(|t| t.as_str()) == Some("release"))
        .or_else(|| list.first())
    {
        Some(v) => v,
        None => {
            out.push(InstallResult {
                slug: id.to_string(),
                ok: false,
                detail: format!("sem build para {version}"),
            });
            return;
        }
    };

    // dependências obrigatórias primeiro (ex.: YACL do motion blur)
    if let Some(deps) = picked.get("dependencies").and_then(|d| d.as_array()) {
        for dep in deps {
            if dep.get("dependency_type").and_then(|t| t.as_str()) != Some("required") {
                continue;
            }
            if let Some(pid) = dep.get("project_id").and_then(|p| p.as_str()) {
                install_project(client, dir, pid, version, depth + 1, seen, done_files, out);
            }
        }
    }

    let files = picked.get("files").and_then(|f| f.as_array()).cloned().unwrap_or_default();
    let chosen = files
        .iter()
        .find(|f| f.get("primary").and_then(|p| p.as_bool()).unwrap_or(false))
        .or_else(|| files.first());
    let (fname, furl) = match chosen {
        Some(f) => (
            f.get("filename").and_then(|s| s.as_str()).unwrap_or("").to_string(),
            f.get("url").and_then(|s| s.as_str()).unwrap_or("").to_string(),
        ),
        None => (String::new(), String::new()),
    };
    if fname.is_empty() || furl.is_empty() {
        out.push(InstallResult {
            slug: id.to_string(),
            ok: false,
            detail: format!("sem arquivo para {version}"),
        });
        return;
    }
    if !done_files.insert(fname.clone()) {
        return;
    }

    match client.get(&furl).send().and_then(|r| r.error_for_status()).and_then(|r| r.bytes()) {
        Ok(bytes) => match std::fs::write(dir.join(&fname), &bytes) {
            Ok(()) => out.push(InstallResult { slug: id.to_string(), ok: true, detail: fname }),
            Err(e) => out.push(InstallResult { slug: id.to_string(), ok: false, detail: format!("gravar: {e}") }),
        },
        Err(e) => out.push(InstallResult { slug: id.to_string(), ok: false, detail: format!("download: {e}") }),
    }
}

/// Instala no thread atual (usado pelo launcher nativo).
pub fn install_mods_sync(version: &str, slugs: &[String]) -> Result<Vec<InstallResult>, String> {
    if version.trim().is_empty() {
        return Err("escolha uma versão antes de instalar".into());
    }
    let client = http_client()?;
    let dir = mods_dir_for(version)?;
    let mut out = Vec::new();
    let mut seen = std::collections::HashSet::new();
    let mut done_files = std::collections::HashSet::new();
    for slug in slugs {
        install_project(&client, &dir, slug, version, 0, &mut seen, &mut done_files, &mut out);
    }
    // guarda slug -> arquivo para a busca marcar o que já está instalado
    let mut map = load_mod_map();
    for r in &out {
        if r.ok && !r.detail.is_empty() {
            map.insert(r.slug.clone(), r.detail.clone());
        }
    }
    save_mod_map(&map);
    Ok(out)
}

#[tauri::command(async)]
pub async fn install_mods(version: String, slugs: Vec<String>) -> Result<Vec<InstallResult>, String> {
    tauri::async_runtime::spawn_blocking(move || install_mods_sync(&version, &slugs))
        .await
        .map_err(|e| format!("thread: {e}"))?
}

/// Busca no Modrinth (só Fabric + a versão selecionada), paginada.
#[tauri::command(async)]
/// Busca síncrona (usada pelo launcher nativo).
pub fn modrinth_search_sync(
    query: &str,
    version: &str,
    offset: u64,
    limit: u64,
) -> Result<SearchPage, String> {
    let client = http_client()?;
    let per_page = limit.clamp(5, 50);
    let start = offset;
    let facets = format!("[[\"categories:fabric\"], [\"versions:{version}\"], [\"project_type:mod\"]]");
    let url = format!(
        "https://api.modrinth.com/v2/search?query={}&limit={}&offset={}&index=follows&facets={}",
        url_encode(query),
        per_page,
        start,
        url_encode(&facets)
    );
    let text = client
        .get(&url)
        .send()
        .and_then(|r| r.error_for_status())
        .and_then(|r| r.text())
        .map_err(|e| format!("consulta: {e}"))?;
    let value: serde_json::Value = serde_json::from_str(&text).map_err(|e| format!("json: {e}"))?;
    let empty = Vec::new();
    let hits = value.get("hits").and_then(|h| h.as_array()).unwrap_or(&empty);
    let total = value.get("total_hits").and_then(|t| t.as_u64()).unwrap_or(0);
    let list: Vec<SearchHit> = hits
        .iter()
        .map(|h| SearchHit {
            slug: h.get("slug").and_then(|s| s.as_str()).unwrap_or("").to_string(),
            title: h.get("title").and_then(|s| s.as_str()).unwrap_or("").to_string(),
            description: h.get("description").and_then(|s| s.as_str()).unwrap_or("").to_string(),
            downloads: h.get("downloads").and_then(|d| d.as_u64()).unwrap_or(0),
            icon: h.get("icon_url").and_then(|s| s.as_str()).unwrap_or("").to_string(),
            author: h.get("author").and_then(|s| s.as_str()).unwrap_or("").to_string(),
        })
        .collect();
    Ok(SearchPage { hits: list, total, offset: start, limit: per_page })
}

#[tauri::command(async)]
pub async fn modrinth_search(
    query: String,
    version: String,
    offset: Option<u64>,
    limit: Option<u64>,
) -> Result<SearchPage, String> {
    tauri::async_runtime::spawn_blocking(move || {
        modrinth_search_sync(&query, &version, offset.unwrap_or(0), limit.unwrap_or(20))
    })
    .await
    .map_err(|e| format!("thread: {e}"))?
}
