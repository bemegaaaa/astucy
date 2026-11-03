use tauri::{
    menu::{Menu, MenuItem},
    tray::TrayIconBuilder,
    Emitter, Manager,
};

pub mod auth;
pub mod engine;
pub mod profile;

/// Pedido de troca de conta vindo de dentro do jogo
/// (`astucy launcher --switch-account <uuid>`). O mod abre o launcher assim
/// quando o jogador escolhe outra conta no menu do R-SHIFT.
static SWITCH: std::sync::Mutex<Option<String>> = std::sync::Mutex::new(None);

fn switch_arg(args: &[String]) -> Option<String> {
    let mut it = args.iter();
    while let Some(a) = it.next() {
        if a == "--switch-account" {
            return it
                .next()
                .map(|v| v.trim().to_string())
                .filter(|v| !v.is_empty());
        }
        if let Some(v) = a.strip_prefix("--switch-account=") {
            let v = v.trim();
            if !v.is_empty() {
                return Some(v.to_string());
            }
        }
    }
    None
}

/// O frontend chama isso ao abrir: se o launcher foi iniciado pelo jogo para
/// trocar de conta, devolve o uuid e já dispara o launch.
#[tauri::command]
fn take_switch_request() -> Option<String> {
    SWITCH.lock().ok().and_then(|mut g| g.take())
}

/// Launch (F3): nick offline OU conta Microsoft → baixa/verifica e abre o jogo.
/// ASYNC + spawn_blocking: downloads bloqueantes NÃO podem travar a thread da UI
/// (era isso que deixava a janela "não respondendo").
#[tauri::command(async)]
async fn launch_game(
    app: tauri::AppHandle,
    nick: String,
    version: String,
    account: Option<String>,
) -> Result<u32, String> {
    tauri::async_runtime::spawn_blocking(move || {
        engine::launch(Some(&app), &nick, &version, account.as_deref())
    })
    .await
    .map_err(|e| format!("thread do launch: {e}"))?
}

#[derive(serde::Serialize)]
struct InstalledVersion {
    name: String,
    kind: String,
    last_modified: u64,
}

/// Varredura real de %APPDATA%\.minecraft\versions (somente leitura).
#[tauri::command]
fn list_versions() -> Vec<InstalledVersion> {
    let mut out = Vec::new();
    if let Ok(appdata) = std::env::var("APPDATA") {
        let root = std::path::Path::new(&appdata)
            .join(".minecraft")
            .join("versions");
        if let Ok(entries) = std::fs::read_dir(&root) {
            for entry in entries.flatten() {
                if !entry.path().is_dir() {
                    continue;
                }
                let name = entry.file_name().to_string_lossy().to_string();
                // perfis internos criados pelo motor ("sempre Fabric") não são
                // versões escolhíveis — o Astucy injeta o loader sozinho.
                if name.starts_with("astucy-fabric-") || name.starts_with("fabric-loader-") {
                    continue;
                }
                let kind = if name
                    .split('.')
                    .all(|p| !p.is_empty() && p.chars().all(|c| c.is_ascii_digit()))
                {
                    "release"
                } else if name.starts_with('b') {
                    "old_beta"
                } else if name.starts_with('a') {
                    "old_alpha"
                } else if name.contains('w') {
                    "snapshot"
                } else {
                    "outro"
                };
                let last_modified = entry
                    .metadata()
                    .and_then(|m| m.modified())
                    .ok()
                    .and_then(|t| {
                        t.duration_since(std::time::UNIX_EPOCH)
                            .ok()
                            .map(|d| d.as_secs())
                    })
                    .unwrap_or(0);
                out.push(InstalledVersion { name, kind: kind.to_string(), last_modified });
            }
        }
    }
    out.sort_by_key(|v| std::cmp::Reverse(v.last_modified));
    out
}

#[tauri::command]
fn app_info() -> serde_json::Value {
    serde_json::json!({
        "name": "Astucy Launcher",
        "version": env!("CARGO_PKG_VERSION"),
    })
}

/// Catálogo completo da Mojang (independe do que existe na máquina).
#[tauri::command(async)]
async fn version_catalog(force: bool) -> Result<Vec<engine::CatalogEntry>, String> {
    tauri::async_runtime::spawn_blocking(move || engine::catalog(force))
        .await
        .map_err(|e| format!("thread do catálogo: {e}"))?
}

/// Baixa JSON + jar do cliente da versão escolhida.
#[tauri::command(async)]
async fn install_version(app: tauri::AppHandle, id: String) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || engine::ensure_version(Some(&app), &id))
        .await
        .map_err(|e| format!("thread do download: {e}"))?
}

#[tauri::command(async)]
async fn system_info() -> Result<serde_json::Value, String> {
    tauri::async_runtime::spawn_blocking(engine::system_info)
        .await
        .map_err(|e| format!("thread do sistema: {e}"))
}

#[tauri::command]
fn open_folder(kind: String) -> Result<(), String> {
    engine::open_folder(&kind)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    // iniciado pelo jogo para trocar de conta? guarda o pedido
    let argv: Vec<String> = std::env::args().collect();
    if let Some(uuid) = switch_arg(&argv) {
        if let Ok(mut g) = SWITCH.lock() {
            *g = Some(uuid);
        }
    }

    tauri::Builder::default()
        // single-instance DEVE ser o primeiro plugin (docs Tauri)
        .plugin(tauri_plugin_single_instance::init(|app, args, _cwd| {
            if let Some(win) = app.get_webview_window("main") {
                let _ = win.set_focus();
            }
            // o jogo abriu o launcher com --switch-account: repassa pro frontend
            if let Some(uuid) = switch_arg(&args) {
                if let Ok(mut g) = SWITCH.lock() {
                    *g = Some(uuid.clone());
                }
                let _ = app.emit("switch-account", serde_json::json!({ "uuid": uuid }));
            }
        }))
        .plugin(tauri_plugin_store::Builder::new().build())
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![
            app_info,
            list_versions,
            version_catalog,
            install_version,
            system_info,
            open_folder,
            launch_game,
            profile::list_mods,
            profile::set_mod_enabled,
            profile::open_mods_folder,
            profile::list_client_modules,
            profile::set_client_module,
            profile::install_mods,
            profile::modrinth_search,
            auth::auth_client_id,
            auth::auth_set_client_id,
            auth::auth_begin,
            auth::auth_poll,
            auth::auth_browser_open,
            auth::auth_browser_finish,
            auth::auth_browser_close,
            auth::auth_accounts,
            auth::auth_set_default,
            auth::auth_remove,
            take_switch_request
        ])
        .setup(|app| {
            let open_i = MenuItem::with_id(app, "open", "Abrir Astucy", true, None::<&str>)?;
            let quit_i = MenuItem::with_id(app, "quit", "Sair", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&open_i, &quit_i])?;

            let _tray = TrayIconBuilder::new()
                .icon(app.default_window_icon().unwrap().clone())
                .menu(&menu)
                .show_menu_on_left_click(false)
                .on_menu_event(|app, event| match event.id().as_ref() {
                    "open" => {
                        if let Some(win) = app.get_webview_window("main") {
                            let _ = win.show();
                            let _ = win.set_focus();
                        }
                    }
                    "quit" => {
                        app.exit(0);
                    }
                    _ => {}
                })
                .build(app)?;

            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("erro ao rodar o Astucy Launcher");
}
