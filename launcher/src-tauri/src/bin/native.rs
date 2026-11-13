//! Astucy Launcher — UI nativa (egui/eframe). Sem WebView2, sem servidor local.
//!
//! Compila junto do backend (engine/auth) e chama as funções direto: nada de
//! IPC, nada de navegador. Um único .exe de ~8 MB, abre em milissegundos.

// Sem janela de console em build de release (console fica útil só em debug,
// para logs no terminal). Isso evita o cmd aberto junto do launcher.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use eframe::egui::{
    self, Align, Align2, Color32, FontFamily, FontId, Frame, Layout, Margin, Response, RichText,
    Rounding, ScrollArea, Sense, Stroke, Vec2,
};
use launcher_lib::{auth, engine, profile};
use serde_json::Value;
use std::path::PathBuf;
use std::sync::mpsc::{channel, Receiver, Sender};
use std::time::{Duration, SystemTime, UNIX_EPOCH};

const DM_REG: &[u8] = include_bytes!("../../resources/dmsans-regular.ttf");
const DM_MED: &[u8] = include_bytes!("../../resources/dmsans-medium.ttf");
const DM_BOLD: &[u8] = include_bytes!("../../resources/dmsans-bold.ttf");

/* ==================================================================
 * paleta
 * ================================================================== */

#[derive(Clone, Copy)]
struct Pal {
    bg: Color32,
    surface: Color32,
    surface2: Color32,
    line: Color32,
    text: Color32,
    name: Color32,
    dim: Color32,
    faint: Color32,
    accent: Color32,
    accent_ink: Color32,
    accent_soft: Color32,
    shadow: Color32,
}

fn palette(light: bool) -> Pal {
    if light {
        Pal {
            bg: Color32::from_rgb(241, 242, 245),
            surface: Color32::from_rgb(255, 255, 255),
            surface2: Color32::from_rgb(246, 247, 249),
            line: Color32::from_rgba_unmultiplied(11, 15, 21, 10),
            text: Color32::from_rgb(11, 15, 21),
            name: Color32::from_rgb(11, 15, 21),
            dim: Color32::from_rgb(94, 104, 117),
            faint: Color32::from_rgb(138, 148, 161),
            accent: Color32::from_rgb(22, 138, 76),
            accent_ink: Color32::from_rgb(255, 255, 255),
            accent_soft: Color32::from_rgba_unmultiplied(22, 138, 76, 26),
            shadow: Color32::from_rgba_unmultiplied(9, 13, 18, 24),
        }
    } else {
        Pal {
            bg: Color32::from_rgb(8, 10, 13),
            surface: Color32::from_rgb(15, 19, 24),
            surface2: Color32::from_rgb(21, 26, 33),
            line: Color32::from_rgba_unmultiplied(255, 255, 255, 8),
            text: Color32::from_rgb(242, 245, 249),
            name: Color32::from_rgb(242, 245, 249),
            dim: Color32::from_rgb(152, 163, 177),
            faint: Color32::from_rgb(107, 118, 132),
            accent: Color32::from_rgb(70, 200, 119),
            accent_ink: Color32::from_rgb(4, 21, 11),
            accent_soft: Color32::from_rgba_unmultiplied(70, 200, 119, 30),
            shadow: Color32::from_rgba_unmultiplied(0, 0, 0, 120),
        }
    }
}

/* ==================================================================
 * dados
 * ================================================================== */

#[derive(Clone)]
struct Installed {
    name: String,
    modified: u64,
}

#[derive(Clone)]
struct ModFile {
    name: String,
    enabled: bool,
    bytes: u64,
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum Pane {
    Home,
    Versions,
    Mods,
    Settings,
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum Filter {
    All,
    Client,
    Legacy,
    Installed,
}

enum Msg {
    Catalog(Result<Vec<engine::CatalogEntry>, String>),
    Accounts(Vec<auth::AccountView>),
    Skin(String, Vec<u8>),
    Icon(String, Vec<u8>),
    InstalledDone(Result<String, String>),
    Launched(Result<u32, String>),
    LoginCode(Result<Value, String>),
    LoginDone(Result<(), String>),
    Modrinth(Result<Vec<profile::SearchHit>, String>),
    ModInstalled(Result<String, String>),
}

struct Login {
    open: bool,
    busy: bool,
    code: String,
    uri: String,
    device: String,
    status: String,
}

/* ==================================================================
 * app
 * ================================================================== */

struct App {
    pane: Pane,
    light: bool,
    pal: Pal,

    tx: Sender<Msg>,
    rx: Receiver<Msg>,

    accounts: Vec<auth::AccountView>,
    account_idx: usize,
    installed: Vec<Installed>,
    version: String,
    nick: String,

    catalog: Vec<engine::CatalogEntry>,
    catalog_busy: bool,
    catalog_loaded: bool,
    query: String,
    filter: Filter,
    open_groups: std::collections::HashSet<String>,
    install_id: Option<String>,

    mods: Vec<ModFile>,

    theme: String,
    res_w: u32,
    res_h: u32,
    client_id: String,

    mr_query: String,
    mr_hits: Option<Vec<profile::SearchHit>>,
    mr_busy: bool,
    mr_err: Option<String>,
    installing: Option<String>,
    confirm_remove: Option<(usize, f64)>,

    ram: u32,
    jvm: String,
    close_on_play: bool,
    launching: bool,
    close_at: Option<f64>,
    stage: String,
    autostart: bool,
    pane_at: f64,

    login: Login,
    toast: Option<(String, bool, f64)>,
    settings_path: PathBuf,
    sys: Option<Value>,

    skins: std::collections::HashMap<String, egui::TextureHandle>,
    skin_pending: std::collections::HashSet<String>,
    icons: std::collections::HashMap<String, egui::TextureHandle>,
    icon_pending: std::collections::HashSet<String>,
    nick_ready: bool,
    account_open: bool,
    boot_at: f64,
}

impl App {
    fn new(cc: &eframe::CreationContext<'_>) -> Self {
        install_fonts(&cc.egui_ctx);
        let (tx, rx) = channel();
        let settings_path = settings_file();
        let saved = read_settings(&settings_path);
        let theme = std::env::var("ASTUCY_THEME")
            .ok()
            .or_else(|| {
                saved
                    .get("settings.theme")
                    .and_then(|v| v.as_str())
                    .map(|s| s.to_string())
            })
            .unwrap_or_else(|| "dark".to_string());
        let light = match theme.as_str() {
            "light" => true,
            "dark" => false,
            _ => system_is_light(),
        };
        let pal = palette(light);
        apply_visuals(&cc.egui_ctx, pal);

        let accounts = auth::auth_accounts();
        let account_idx = accounts.iter().position(|a| a.default).unwrap_or(0);

        let mut app = Self {
            pane: match std::env::var("ASTUCY_PANE").as_deref() {
                Ok("versions") => Pane::Versions,
                Ok("mods") => Pane::Mods,
                Ok("settings") => Pane::Settings,
                _ => Pane::Home,
            },
            light,
            pal,
            tx: tx.clone(),
            rx,
            accounts,
            account_idx,
            installed: Vec::new(),
            version: saved
                .get("settings.version")
                .and_then(|v| v.as_str())
                .unwrap_or("")
                .to_string(),
            nick: saved
                .get("settings.nick")
                .and_then(|v| v.as_str())
                .unwrap_or("")
                .to_string(),
            catalog: Vec::new(),
            catalog_busy: false,
            catalog_loaded: false,
            query: String::new(),
            filter: Filter::All,
            open_groups: Default::default(),
            install_id: None,
            mods: Vec::new(),
            theme: theme.clone(),
            res_w: saved
                .get("settings.res_w")
                .and_then(|v| v.as_u64())
                .unwrap_or(1280) as u32,
            res_h: saved
                .get("settings.res_h")
                .and_then(|v| v.as_u64())
                .unwrap_or(720) as u32,
            client_id: auth::auth_client_id().unwrap_or_default(),
            mr_query: String::new(),
            mr_hits: None,
            mr_busy: false,
            mr_err: None,
            installing: None,
            confirm_remove: None,
            ram: saved
                .get("settings.ram")
                .and_then(|v| v.as_u64())
                .unwrap_or(2560) as u32,
            jvm: saved
                .get("settings.jvm")
                .and_then(|v| v.as_str())
                .unwrap_or("-XX:+UseG1GC -XX:MaxGCPauseMillis=60 -XX:+DisableExplicitGC")
                .to_string(),
            close_on_play: saved
                .get("settings.close_on_play")
                .and_then(|v| v.as_bool())
                .unwrap_or(true),
            launching: false,
            close_at: None,
            stage: String::new(),
            autostart: false,
            pane_at: 0.0,
            skins: Default::default(),
            skin_pending: Default::default(),
            icons: Default::default(),
            icon_pending: Default::default(),
            nick_ready: false,
            account_open: false,
            boot_at: now(),
            login: Login {
                open: false,
                busy: false,
                code: String::new(),
                uri: "https://microsoft.com/link".into(),
                device: String::new(),
                status: String::new(),
            },
            toast: None,
            settings_path,
            sys: None,
        };

        let _ = profile::sync_core_mod();
        std::thread::spawn(|| {
            let _ = engine::check_github_updates();
        });
        app.refresh_installed();
        app.refresh_mods();
        app.spawn_catalog(false);
        app.sys = engine::system_info().into();

        if std::env::args().any(|a| a == "--play" || a == "-p") {
            app.autostart = true;
        }

        // `--switch-account <uuid>`: o mod abre o launcher pra trocar de conta
        let args: Vec<String> = std::env::args().collect();
        let switch = args
            .iter()
            .position(|a| a == "--switch-account")
            .and_then(|i| args.get(i + 1))
            .cloned()
            .or_else(|| {
                args.iter()
                    .find_map(|a| a.strip_prefix("--switch-account=").map(str::to_string))
            });
        if let Some(uuid) = switch {
            if auth::auth_set_default(uuid.trim().to_string()).is_ok() {
                app.accounts = auth::auth_accounts();
                app.account_idx = app
                    .accounts
                    .iter()
                    .position(|a| a.default)
                    .unwrap_or(0);
                app.autostart = true;
            }
        }
        app
    }

    /* ---------- persistência ---------- */

    fn save(&self) {
        let v = serde_json::json!({
            "settings.version": self.version,
            "settings.nick": self.nick,
            "settings.ram": self.ram,
            "settings.jvm": self.jvm,
            "settings.close_on_play": self.close_on_play,
            "settings.theme": self.theme,
            "settings.res_w": self.res_w,
            "settings.res_h": self.res_h,
        });
        let _ = std::fs::create_dir_all(self.settings_path.parent().unwrap_or(&self.settings_path));
        let _ = std::fs::write(
            &self.settings_path,
            serde_json::to_string_pretty(&v).unwrap_or_default(),
        );
    }

    /* ---------- skins ---------- */

    /// Baixa/carrega em cache a cabeça de um identificador (uuid ou nick).
    /// Retorna Some na primeira vez que existir textura pronta.
    fn skin(
        &mut self,
        ctx: &egui::Context,
        ident: &str,
        url: Option<String>,
    ) -> Option<egui::TextureHandle> {
        if ident.is_empty() {
            return None;
        }
        if let Some(t) = self.skins.get(ident) {
            return Some(t.clone());
        }
        // cache em disco
        let path = skins_dir().join(format!("{}.png", skin_ident_ident(ident)));
        if let Ok(png) = std::fs::read(&path) {
            if self.load_skin_tex(ctx, ident, png).is_some() {
                return self.skins.get(ident).cloned();
            }
            let _ = std::fs::remove_file(&path);
        }
        // baixa 1x por sessão
        if let Some(url) = url {
            if self.skin_pending.insert(ident.to_string()) {
                let tx = self.tx.clone();
                let id2 = ident.to_string();
                std::thread::spawn(move || {
                    let client = reqwest::blocking::Client::builder()
                        .timeout(Duration::from_secs(8))
                        .build();
                    let png = client
                        .ok()
                        .and_then(|c| c.get(url).send().ok())
                        .and_then(|r| r.error_for_status().ok())
                        .and_then(|r| r.bytes().ok())
                        .map(|b| b.to_vec());
                    if let Some(png) = png {
                        let _ = tx.send(Msg::Skin(id2, png));
                    }
                });
            }
        }
        None
    }

    fn load_skin_tex(
        &mut self,
        ctx: &egui::Context,
        ident: &str,
        png: Vec<u8>,
    ) -> Option<egui::TextureHandle> {
        let img = decode_png(&png)?;
        let tex = ctx.load_texture(
            format!("skin/{ident}"),
            img,
            egui::TextureOptions::NEAREST,
        );
        self.skins.insert(ident.to_string(), tex.clone());
        Some(tex)
    }

    /// O avatar "padrão": uuid da conta Microsoft ou nick offline.
    fn head_target(&self) -> (String, Option<String>) {
        if let Some(a) = self.default_account() {
            return (a.uuid.clone(), Some(head_url(&a.uuid)));
        }
        let nick = self.nick.trim().to_string();
        if valid_nick(&nick) {
            (nick.clone(), Some(head_url(&nick)))
        } else {
            (String::new(), None)
        }
    }

    fn default_head(&mut self, ctx: &egui::Context) -> Option<egui::TextureHandle> {
        let (ident, url) = self.head_target();
        self.skin(ctx, &ident, url)
    }

    /* ---------- ícones de mods (Modrinth) ---------- */

    /// Textura do ícone (baixa 1x por sessão; cacheia em disco).
    fn icon(&mut self, ctx: &egui::Context, url: Option<String>) -> Option<egui::TextureHandle> {
        let url = url.filter(|u| !u.trim().is_empty())?;
        if let Some(t) = self.icons.get(&url) {
            return Some(t.clone());
        }
        let fpath = skins_dir()
            .join("icons")
            .join(format!("{:016x}.bin", fnv_hash(&url)));
        if let Ok(bytes) = std::fs::read(&fpath) {
            if let Some(img) = decode_any(&bytes) {
                let tex = ctx.load_texture(format!("icon/{:016x}", fnv_hash(&url)), img, egui::TextureOptions::LINEAR);
                self.icons.insert(url.clone(), tex.clone());
                return Some(tex);
            }
            let _ = std::fs::remove_file(&fpath);
        }
        if self.icon_pending.insert(url.clone()) {
            let tx = self.tx.clone();
            let u2 = url.clone();
            std::thread::spawn(move || {
                let client = reqwest::blocking::Client::builder()
                    .timeout(Duration::from_secs(8))
                    .build();
                let bytes = client
                    .ok()
                    .and_then(|c| c.get(u2.clone()).send().ok())
                    .and_then(|r| r.error_for_status().ok())
                    .and_then(|r| r.bytes().ok())
                    .map(|b| b.to_vec());
                if let Some(bytes) = bytes {
                    let _ = tx.send(Msg::Icon(u2, bytes));
                }
            });
        }
        None
    }

    fn toast(&mut self, msg: impl Into<String>, error: bool) {
        self.toast = Some((msg.into(), error, now()));
    }

    /* ---------- dados ---------- */

    fn spawn_catalog(&mut self, force: bool) {
        if self.catalog_busy {
            return;
        }
        self.catalog_busy = true;
        let tx = self.tx.clone();
        std::thread::spawn(move || {
            let _ = tx.send(Msg::Catalog(engine::catalog(force)));
        });
    }

    fn refresh_installed(&mut self) {
        self.installed = scan_installed();
        if self.version.is_empty() {
            if let Some(v) = self
                .installed
                .iter()
                .find(|v| v.name.split('.').next().and_then(|s| s.parse::<u32>().ok()).is_some_and(|m| m >= 26))
                .or_else(|| self.installed.first())
            {
                self.version = v.name.clone();
            }
        }
    }

    fn refresh_mods(&mut self) {
        self.mods = scan_mods(&self.version);
    }

    fn default_account(&self) -> Option<&auth::AccountView> {
        self.accounts.get(self.account_idx)
    }

    /* ---------- ações ---------- */

    fn install(&mut self, id: String) {
        if self.install_id.is_some() {
            return;
        }
        self.install_id = Some(id.clone());
        let tx = self.tx.clone();
        std::thread::spawn(move || {
            let r = engine::ensure_version(None, &id).map(|_| id);
            let _ = tx.send(Msg::InstalledDone(r));
        });
    }

    fn launch(&mut self) {
        if self.launching || self.version.is_empty() {
            return;
        }
        let acc = self.default_account().cloned();
        let nick = acc
            .as_ref()
            .map(|a| a.name.clone())
            .unwrap_or_else(|| self.nick.clone());
        if nick.trim().is_empty() {
            self.toast("Escolha uma conta ou digite um nick.", true);
            return;
        }
        self.launching = true;
        self.close_at = None;
        let version = self.version.clone();
        let uuid = acc.map(|a| a.uuid);
        let tx = self.tx.clone();
        std::thread::spawn(move || {
            let r = engine::launch(None, &nick, &version, uuid.as_deref());
            let _ = tx.send(Msg::Launched(r));
        });
    }

    fn begin_login(&mut self) {
        if self.login.busy {
            return;
        }
        self.login.busy = true;
        self.login.status = "Pedindo código à Microsoft…".into();
        let tx = self.tx.clone();
        std::thread::spawn(move || {
            let r = tauri::async_runtime::block_on(auth::auth_begin());
            let _ = tx.send(Msg::LoginCode(r));
        });
    }

    fn poll_login(&mut self) {
        if self.login.device.is_empty() {
            return;
        }
        let device = self.login.device.clone();
        let interval = 3u64;
        let tx = self.tx.clone();
        self.login.status = "Aguardando você entrar no navegador…".into();
        std::thread::spawn(move || loop {
            std::thread::sleep(Duration::from_secs(interval));
            let r = tauri::async_runtime::block_on(auth::auth_poll(device.clone()));
            match r {
                Ok(v) => {
                    let status = v.get("status").and_then(|s| s.as_str()).unwrap_or("");
                    if status == "done" {
                        let _ = tx.send(Msg::LoginDone(Ok(())));
                        break;
                    }
                }
                Err(e) => {
                    let _ = tx.send(Msg::LoginDone(Err(e)));
                    break;
                }
            }
        });
    }

    /* ---------- mensagens ---------- */

    fn splash(&mut self, ctx: &egui::Context) {
        let age = now() - self.boot_at;
        const SHOW: f64 = 1.55;
        const FADE: f64 = 0.40;
        if age >= SHOW + FADE {
            return;
        }
        ctx.request_repaint();
        let p = self.pal;
        egui::Area::new(egui::Id::new("boot"))
            .order(egui::Order::Foreground)
            .fixed_pos(egui::pos2(0.0, 0.0))
            .show(ctx, |ui| {
                let vp = ui.input(|i| i.screen_rect());
                let in_a = ((age / 0.22) as f32).clamp(0.0, 1.0);
                let out_a = (((age - SHOW) / FADE) as f32).clamp(0.0, 1.0);
                let alpha = in_a * (1.0 - ease_out_cubic(out_a));
                let e = ease_out_cubic(((age / 0.55) as f32).min(1.0));
                let rot = -1.35 * (1.0 - e);
                let rise = (1.0 - e) * 12.0;
                let b = 0.5 + 0.5 * (age as f32 * 1.8).sin();
                let gp = ui.painter().with_clip_rect(vp);
                gp.rect_filled(vp, 0.0, p.bg);
                let light = p.bg.r() > 128;
                let a = p.accent.to_array();
                radial(
                    &gp,
                    vp.center(),
                    260.0 + 26.0 * b,
                    Color32::from_rgba_unmultiplied(
                        a[0],
                        a[1],
                        a[2],
                        ((if light { 9.0 } else { 20.0 }) * (0.75 + 0.25 * b) * alpha) as u8,
                    ),
                );
                ui.set_opacity(alpha);
                let painter = ui.painter().with_clip_rect(vp);
                let lock_w = ctx.fonts(|f| {
                    let font = FontId::new(30.0, FontFamily::Name("dmb".into()));
                    let mut x = 0.0f32;
                    for ch in "ASTUCY".chars() {
                        let zg = f.layout_no_wrap(ch.to_string(), font.clone(), p.text);
                        x += zg.size().x + 30.0 * 0.035;
                    }
                    x - 30.0 * 0.035
                });
                let total = 58.0 + 16.0 + lock_w;
                let c = vp.center() - Vec2::new(0.0, 10.0 + rise);
                let iris_c = egui::pos2(c.x - total / 2.0 + 29.0, c.y);
                let irect = egui::Rect::from_center_size(iris_c, Vec2::splat(58.0 * (0.9 + 0.1 * e)));
                paint_iris(&painter, irect, p.text, rot);
                let wm_left = irect.right() + 16.0;
                wordmark(&painter, wm_left, c.y, 30.0, p.text);
                painter.text(
                    egui::pos2(c.x, c.y + 36.0),
                    Align2::CENTER_CENTER,
                    "v0.1.0",
                    FontId::new(10.0, FontFamily::Name("dm".into())),
                    p.faint,
                );
                let bar_r = egui::Rect::from_min_size(
                    egui::pos2(c.x - 80.0, c.y + 56.0),
                    Vec2::new(160.0, 4.0),
                );
                painter.rect_filled(bar_r, Rounding::same(999.0), p.surface2);
                let seg = 44.0f32;
                let t = (age as f32 * 1.4) % 1.0;
                let lo = bar_r.left() + (bar_r.width() + seg) * t - seg;
                let fill = egui::Rect::from_min_max(
                    egui::pos2(lo.max(bar_r.left()), bar_r.top()),
                    egui::pos2((lo + seg).min(bar_r.right()), bar_r.bottom()),
                );
                if fill.width() > 1.0 {
                    let ga = ((160.0 + 60.0 * b) * alpha) as u8;
                    painter.rect_filled(
                        fill,
                        Rounding::same(999.0),
                        Color32::from_rgba_unmultiplied(a[0], a[1], a[2], ga),
                    );
                }
                let dots = (age as f32 * 2.0).floor() as i32 % 3 + 1;
                painter.text(
                    egui::pos2(c.x, c.y + 74.0),
                    Align2::CENTER_CENTER,
                    format!("carregando{}", ".".repeat((dots + 1) as usize)),
                    FontId::new(10.0, FontFamily::Name("dm".into())),
                    p.faint,
                );
            });
    }

    fn drain(&mut self, ctx: &egui::Context) {
        while let Ok(m) = self.rx.try_recv() {
            match m {
                Msg::Catalog(r) => {
                    self.catalog_busy = false;
                    self.catalog_loaded = true;
                    match r {
                        Ok(list) => {
                            self.catalog = list;
                            let fam = self
                                .catalog
                                .iter()
                                .find(|v| v.id == self.version)
                                .map(|v| family_of(&v.id))
                                .unwrap_or_else(|| {
                                    self.catalog.first().map(|v| family_of(&v.id)).unwrap_or_default()
                                });
                            if !fam.is_empty() {
                                self.open_groups.insert(fam);
                            }
                        }
                        Err(e) => self.toast(e, true),
                    }
                }
                Msg::Accounts(list) => {
                    self.accounts = list;
                    self.account_idx = self
                        .accounts
                        .iter()
                        .position(|a| a.default)
                        .unwrap_or(0);
                }
                Msg::Skin(ident, png) => {
                    // decodifica, guarda textura e salva pro cache em disco
                    if let Some(tex) = self.load_skin_tex(ctx, &ident, png.clone()) {
                        let _ = tex;
                    }
                    let dir = skins_dir();
                    let _ = std::fs::create_dir_all(&dir);
                    let _ = std::fs::write(dir.join(format!("{}.png", skin_ident_ident(&ident))), &png);
                    ctx.request_repaint();
                }
                Msg::Icon(url, bytes) => {
                    if let Some(img) = decode_any(&bytes) {
                        let tex = ctx.load_texture(
                            format!("icon/{}", url),
                            img,
                            egui::TextureOptions::LINEAR,
                        );
                        self.icons.insert(url.clone(), tex);
                    }
                    let dir = skins_dir().join("icons");
                    let _ = std::fs::create_dir_all(&dir);
                    let _ = std::fs::write(dir.join(format!("{:016x}.bin", fnv_hash(&url))), &bytes);
                    ctx.request_repaint();
                }
                Msg::Modrinth(r) => {
                    self.mr_busy = false;
                    match r {
                        Ok(hits) => {
                            self.mr_err = None;
                            self.mr_hits = Some(hits);
                        }
                        Err(e) => self.mr_err = Some(e),
                    }
                }
                Msg::ModInstalled(r) => {
                    self.installing = None;
                    match r {
                        Ok(detail) => {
                            self.toast(format!("{detail} instalado."), false);
                            self.refresh_mods();
                        }
                        Err(e) => self.toast(e, true),
                    }
                }
                Msg::InstalledDone(r) => {
                    self.install_id = None;
                    match r {
                        Ok(id) => {
                            self.refresh_installed();
                            self.version = id.clone();
                            self.refresh_mods();
                            self.save();
                            self.toast(format!("{id} pronta."), false);
                        }
                        Err(e) => self.toast(e, true),
                    }
                }
                Msg::Launched(r) => {
                    self.launching = false;
                    match r {
                        Ok(_) => {
                            let is_client = self
                                .version
                                .split('.')
                                .next()
                                .and_then(|s| s.parse::<u32>().ok())
                                .unwrap_or(0)
                                >= 26;
                            if self.close_on_play {
                                self.close_at = Some(now() + 1.6);
                            } else if is_client {
                                self.toast("Astucy Client aberto. Bom jogo!", false);
                            } else {
                                self.toast(
                                    format!(
                                        "{} aberto (Vanilla — o mod do client roda na 26.x).",
                                        self.version
                                    ),
                                    false,
                                );
                            }
                        }
                        Err(e) => self.toast(e, true),
                    }
                }
                Msg::LoginCode(r) => match r {
                    Ok(v) => {
                        self.login.code = v
                            .get("user_code")
                            .and_then(|s| s.as_str())
                            .unwrap_or("")
                            .into();
                        self.login.uri = v
                            .get("verification_uri")
                            .and_then(|s| s.as_str())
                            .unwrap_or("https://microsoft.com/link")
                            .into();
                        self.login.device = v
                            .get("device_code")
                            .and_then(|s| s.as_str())
                            .unwrap_or("")
                            .into();
                        self.login.status = "Digite o código no site que abrir.".into();
                        self.poll_login();
                    }
                    Err(e) => {
                        self.login.busy = false;
                        self.login.status = e;
                    }
                },
                Msg::LoginDone(r) => {
                    self.login.busy = false;
                    match r {
                        Ok(()) => {
                            self.login.open = false;
                            self.login.status.clear();
                            let _ = self.tx.send(Msg::Accounts(auth::auth_accounts()));
                            self.toast("Conta conectada.", false);
                        }
                        Err(e) => self.login.status = e,
                    }
                }
            }
        }
        if self.launching {
            self.stage = engine::last_stage();
            ctx.request_repaint_after(Duration::from_millis(120));
        }
        if self.catalog_busy || self.install_id.is_some() || self.login.busy {
            ctx.request_repaint_after(Duration::from_millis(200));
        }
        if self.pane == Pane::Home && !self.launching {
            // mantém o glow do hero respirando
            ctx.request_repaint_after(Duration::from_millis(120));
        }
        if let Some(at) = self.close_at {
            if now() >= at {
                ctx.send_viewport_cmd(egui::ViewportCommand::Close);
            } else {
                ctx.request_repaint_after(Duration::from_millis(150));
            }
        }
        if let Some((_, _, at)) = self.toast {
            let age = now() - at;
            if age > 4.0 {
                self.toast = None;
            } else if age < 0.5 || (4.0 - age) < 0.7 {
                ctx.request_repaint();
            } else {
                ctx.request_repaint_after(Duration::from_millis(200));
            }
        }
    }

    /* ---------- UI ---------- */

    fn titlebar(&mut self, ctx: &egui::Context) {
        let p = self.pal;
        egui::TopBottomPanel::top("bar")
            .exact_height(44.0)
            .frame(Frame::none().fill(p.bg))
            .show(ctx, |ui| {
                let rect = ui.max_rect();
                let resp = ui.interact(rect, ui.id().with("drag"), Sense::click_and_drag());
                if resp.drag_started() {
                    ui.ctx().send_viewport_cmd(egui::ViewportCommand::StartDrag);
                }
                if resp.double_clicked() {
                    let max = ui.ctx().input(|i| i.viewport().maximized.unwrap_or(false));
                    ui.ctx()
                        .send_viewport_cmd(egui::ViewportCommand::Maximized(!max));
                }
                ui.horizontal_centered(|ui| {
                    ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                        ui.add_space(10.0);
                        if window_button(ui, 'x', p).clicked() {
                            ui.ctx().send_viewport_cmd(egui::ViewportCommand::Close);
                        }
                        if window_button(ui, 'o', p).clicked() {
                            let max = ui
                                .ctx()
                                .input(|i| i.viewport().maximized.unwrap_or(false));
                            ui.ctx()
                                .send_viewport_cmd(egui::ViewportCommand::Maximized(!max));
                        }
                        if window_button(ui, '-', p).clicked() {
                            ui.ctx()
                                .send_viewport_cmd(egui::ViewportCommand::Minimized(true));
                        }
                        ui.add_space(12.0);
                        let prev_light = self.light;
                        theme_pill(ui, &mut self.light, &mut self.pal);
                        if self.light != prev_light {
                            let choice = if self.light { "light" } else { "dark" };
                            self.apply_theme(choice);
                        }
                    });
                });
            });
    }

    fn sidebar(&mut self, ctx: &egui::Context) {
        let p = self.pal;
        egui::SidePanel::left("nav")
            .exact_width(212.0)
            .resizable(false)
            .frame(
                Frame::none().fill(p.bg).inner_margin(Margin {
                    left: 12.0,
                    right: 12.0,
                    top: 8.0,
                    bottom: 12.0,
                }),
            )
            .show(ctx, |ui| {
                let (brand, _) = ui.allocate_exact_size(
                    Vec2::new(ui.available_width(), 36.0),
                    Sense::hover(),
                );
                paint_iris(
                    ui.painter(),
                    egui::Rect::from_center_size(
                        egui::pos2(brand.left() + 12.0, brand.top() + 14.0),
                        Vec2::splat(24.0),
                    ),
                    p.text,
                    0.0,
                );
                let wm = wordmark(ui.painter(), brand.left() + 33.0, brand.top() + 14.0, 15.0, p.text);
                ui.painter().text(
                    egui::pos2(brand.left() + 33.0 + wm + 6.0, brand.top() + 15.0),
                    Align2::LEFT_CENTER,
                    format!("v{}", env!("CARGO_PKG_VERSION")),
                    FontId::new(8.5, FontFamily::Name("dm".into())),
                    p.faint,
                );
                ui.painter().text(
                    egui::pos2(brand.left() + 33.0, brand.top() + 28.0),
                    Align2::LEFT_CENTER,
                    "launcher",
                    FontId::new(9.0, FontFamily::Name("dm".into())),
                    p.faint,
                );

                ui.add_space(18.0);
                text(ui, "MENU", 9.0, p.faint, true);
                ui.add_space(7.0);
                for (pane, label, icon) in [
                    (Pane::Home, "In\u{00ed}cio", 0u8),
                    (Pane::Versions, "Vers\u{00f5}es", 1),
                    (Pane::Mods, "Mods", 2),
                    (Pane::Settings, "Ajustes", 3),
                ] {
                    if nav_item(ui, icon, label, self.pane == pane, p).clicked() {
                        if self.pane != pane {
                            self.pane = pane;
                            self.pane_at = now();
                        }
                    }
                    ui.add_space(2.0);
                }

                let atex = self.default_head(ui.ctx());
                let mut clicked_acc = false;
                ui.with_layout(Layout::bottom_up(Align::LEFT), |ui| {
                    ui.add_space(2.0);
                    text(ui, "Astucy", 9.0, p.faint, false);
                    ui.add_space(10.0);
                    ram_meter(ui, self.sys.as_ref(), p);
                    ui.add_space(8.0);
                    if account_card(ui, self.default_account(), atex.as_ref(), p).clicked() {
                        clicked_acc = true;
                    }
                });
                if clicked_acc {
                    if self.pane != Pane::Home {
                        self.pane = Pane::Home;
                        self.pane_at = now();
                    }
                    self.account_open = !self.account_open;
                }
            });
    }

    fn home(&mut self, ui: &mut egui::Ui) {
        let p = self.pal;
        let acc = self.default_account().cloned();
        let signed = acc.is_some();
        let name = acc
            .as_ref()
            .map(|a| a.name.clone())
            .filter(|n| !n.is_empty())
            .unwrap_or_else(|| {
                if self.nick.is_empty() {
                    "Jogador".to_string()
                } else {
                    self.nick.clone()
                }
            });
        let mode = if signed { "Conta Microsoft" } else { "Offline" };

        ui.horizontal(|ui| {
            let (r, _) = ui.allocate_exact_size(Vec2::splat(44.0), Sense::hover());
            let htex = self.default_head(ui.ctx());
            paint_avatar(ui, r, &name, htex.as_ref(), p);
            ui.add_space(12.0);
            ui.vertical(|ui| {
                text(ui, "Bem-vindo de volta", 10.5, p.faint, false);
                ui.add_space(2.0);
                text(ui, &name, 22.0, p.text, true);
            });
            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                pill_dot(ui, mode, signed, p);
                ui.add_space(8.0);
                chip(ui, &self.version, p);
            });
        });

        ui.add_space(16.0);

        let full = ui.available_width();
        let gap = 14.0;
        let right_w = (full * 0.30).clamp(280.0, 360.0);
        let left_w = (full - right_w - gap).max(430.0);

        ui.horizontal_top(|ui| {
            ui.spacing_mut().item_spacing.x = gap;
            ui.allocate_ui_with_layout(
                Vec2::new(left_w, 0.0),
                Layout::top_down(Align::Min),
                |ui| self.hero(ui, left_w),
            );
            ui.allocate_ui_with_layout(
                Vec2::new(right_w, 0.0),
                Layout::top_down(Align::Min),
                |ui| {
                    self.system_card(ui);
                    ui.add_space(12.0);
                    self.shortcuts_card(ui);
                },
            );
        });

        ui.add_space(14.0);
        self.stat_strip(ui);
    }

    fn hero(&mut self, ui: &mut egui::Ui, width: f32) {
        let p = self.pal;
        let signed = self.default_account().is_some();
        let name = self
            .default_account()
            .map(|a| a.name.clone())
            .unwrap_or_else(|| {
                if self.nick.is_empty() {
                    "Jogador".to_string()
                } else {
                    self.nick.clone()
                }
            });
        let (rect, _) = ui.allocate_exact_size(Vec2::new(width, 362.0), Sense::hover());
        paint_hero(ui, rect, p);
        ui.scope_builder(
            egui::UiBuilder::new().max_rect(rect.shrink2(Vec2::new(26.0, 22.0))),
            |ui| {
                ui.horizontal(|ui| {
                    let badge_txt = if self.version.contains("Rust") {
                        "ASTUCY · MOTOR RUST 26.3"
                    } else {
                        "MINECRAFT: JAVA EDITION"
                    };
                    pill_outline(ui, badge_txt, p);
                    ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                        chip(ui, &self.version, p);
                    });
                });

                ui.add_space(22.0);
                let (logo, _) = ui.allocate_exact_size(Vec2::splat(44.0), Sense::hover());
                let iris_rot = (now() as f32) * if self.launching { 2.2 } else { 0.16 };
                paint_iris(ui.painter(), logo, p.text, iris_rot);
                wordmark(
                    ui.painter(),
                    logo.right() + 14.0,
                    logo.center().y,
                    40.0,
                    p.text,
                );

                ui.add_space(16.0);
                ui.horizontal(|ui| {
                    let (d, _) = ui.allocate_exact_size(Vec2::splat(10.0), Sense::hover());
                    ui.painter().circle_filled(d.center(), 3.4, p.accent);
                    ui.add_space(2.0);
                    let sub = if signed {
                        format!("conta Microsoft \u{b7} {name}")
                    } else if self.nick.is_empty() {
                        "modo offline".to_string()
                    } else {
                        format!("modo offline \u{b7} {}", self.nick)
                    };
                    text(ui, &sub, 11.0, p.dim, false);
                });

                ui.add_space(16.0);
                self.identity_bar(ui, 408.0);

                ui.add_space(16.0);
                ui.horizontal(|ui| {
                    let busy = self.launching;
                    let label = if busy { "Abrindo\u{2026}" } else { "Jogar" };
                    let sub = if busy {
                        if self.stage.is_empty() {
                            "preparando".to_string()
                        } else {
                            self.stage.clone()
                        }
                    } else if signed || !self.nick.is_empty() {
                        "pronto pra abrir".to_string()
                    } else {
                        "escolha um nick".to_string()
                    };
                    if play_button(ui, label, &sub, busy, p).clicked() {
                        self.launch();
                    }
                    ui.add_space(14.0);
                    ui.vertical(|ui| {
                        text(ui, "VERS\u{c3}O", 9.0, p.faint, true);
                        ui.add_space(4.0);
                        if let Some(pick) = version_picker(ui, &self.version, &self.installed, p) {
                            self.version = pick;
                            self.refresh_mods();
                            self.save();
                        }
                    });
                });

                ui.add_space(12.0);
                let note = if signed {
                    "A sessão renova sozinha antes de abrir o jogo.".to_string()
                } else if self.nick.is_empty() {
                    "Sem conta Microsoft: escolha um nick no seletor acima pra jogar offline."
                        .to_string()
                } else {
                    format!(
                        "Offline como {}. Entre com a Microsoft quando quiser skins.",
                        self.nick
                    )
                };
                text(ui, &note, 10.5, p.faint, false);
            },
        );
    }

    fn identity_bar(&mut self, ui: &mut egui::Ui, width: f32) {
        let p = self.pal;
        let acc = self.default_account().cloned();
        let name = acc
            .as_ref()
            .map(|a| a.name.clone())
            .unwrap_or_else(|| {
                if self.nick.is_empty() {
                    "Jogador".to_string()
                } else {
                    self.nick.clone()
                }
            });
        let sub = if acc.is_some() {
            "conta Microsoft"
        } else {
            "modo offline"
        };

        let (rect, resp) = ui.allocate_exact_size(Vec2::new(width, 60.0), Sense::click());
        let hot = resp.hovered();
        let hd = ui
            .ctx()
            .animate_bool_with_time(ui.id().with("ident"), hot, 0.16);
        ui.painter()
            .rect_filled(rect, Rounding::same(12.0), mixc(p.surface2, lighten(p.surface2, 6), hd));
        ui.painter().rect_stroke(
            rect,
            Rounding::same(12.0),
            Stroke::new(1.0_f32, mixc(p.line, p.dim, hd)),
        );
        let atex = self.default_head(ui.ctx());
        let av = egui::Rect::from_center_size(
            rect.left_center() + Vec2::new(32.0, 0.0),
            Vec2::splat(36.0),
        );
        paint_avatar(ui, av, &name, atex.as_ref(), p);
        ui.painter().text(
            rect.left_center() + Vec2::new(58.0, -9.0),
            Align2::LEFT_CENTER,
            &name,
            FontId::new(13.0, FontFamily::Name("dmb".into())),
            p.text,
        );
        ui.painter().text(
            rect.left_center() + Vec2::new(58.0, 9.0),
            Align2::LEFT_CENTER,
            sub,
            FontId::new(10.0, FontFamily::Name("dm".into())),
            p.dim,
        );
        let ch = rect.right_center() - Vec2::new(20.0, 0.0);
        ui.painter()
            .line_segment([ch + Vec2::new(-4.5, -2.5), ch], Stroke::new(1.4_f32, p.dim));
        ui.painter()
            .line_segment([ch, ch + Vec2::new(4.5, -2.5)], Stroke::new(1.4_f32, p.dim));

        let id = egui::Id::new("identity-pop");
        if resp.clicked() {
            self.account_open = !self.account_open;
        }
        if self.account_open {
            let viewport = ui.input(|i| i.screen_rect());
            // camada atrás da popup que fecha ao clicar fora (escudo)
            egui::Area::new(id.with("strut"))
                .order(egui::Order::Middle)
                .fixed_pos(egui::pos2(0.0, 0.0))
                .show(ui.ctx(), |ui| {
                    let (_, r) = ui.allocate_exact_size(
                        Vec2::new(viewport.width() + 8.0, viewport.height() + 8.0),
                        Sense::click(),
                    );
                    if r.clicked() {
                        self.account_open = false;
                        ui.ctx().request_repaint();
                    }
                });
            // popup propriamente dita
            let px = (rect.left())
                .min(viewport.right() - 344.0)
                .max(8.0);
            let py = (rect.bottom() + 10.0)
                .min(viewport.bottom() - 300.0)
                .max(8.0);
            egui::Area::new(id)
                .order(egui::Order::Foreground)
                .fixed_pos(egui::pos2(px, py))
                .show(ui.ctx(), |ui| {
                    ui.set_width(324.0);
                    Frame::none()
                        .fill(p.surface)
                        .rounding(Rounding::same(14.0))
                        .stroke(Stroke::new(1.0_f32, p.line))
                        .shadow(egui::epaint::Shadow {
                            offset: egui::vec2(0.0, 10.0),
                            blur: 26.0,
                            spread: 0.0,
                            color: Color32::from_black_alpha(70),
                        })
                        .inner_margin(Margin::same(14.0))
                        .show(ui, |ui| {
                            ui.set_max_width(296.0);
                            // ---------------- contas Microsoft ----------------
                            if !self.accounts.is_empty() {
                                text(ui, "CONTAS", 9.0, p.faint, true);
                                ui.add_space(6.0);
                                for a in self.accounts.clone() {
                                    let cur = a.default;
                                    let stex =
                                        self.skin(ui.ctx(), &a.uuid, Some(head_url(&a.uuid)));
                                    let (rect2, rrow) = ui.allocate_exact_size(
                                        Vec2::new(296.0, 34.0),
                                        Sense::click(),
                                    );
                                    let hl = ui.ctx().animate_bool_with_time(
                                        ui.id().with(("accrow", &a.uuid)),
                                        rrow.hovered(),
                                        0.14,
                                    );
                                    let on = if cur { 1.0f32 } else { hl };
                                    if on > 0.02 {
                                        ui.painter().rect_filled(
                                            rect2,
                                            Rounding::same(9.0),
                                            mixc(p.surface2, lighten(p.surface2, 7), on),
                                        );
                                    }
                                    let avr = egui::Rect::from_center_size(
                                        egui::pos2(rect2.left() + 17.0, rect2.center().y),
                                        Vec2::splat(22.0),
                                    );
                                    paint_avatar(ui, avr, &a.name, stex.as_ref(), p);
                                    ui.painter().text(
                                        egui::pos2(rect2.left() + 36.0, rect2.center().y - 6.0),
                                        Align2::LEFT_CENTER,
                                        &a.name,
                                        FontId::new(12.0, FontFamily::Name("dmb".into())),
                                        mixc(p.dim, p.text, on),
                                    );
                                    ui.painter().text(
                                        egui::pos2(rect2.left() + 36.0, rect2.center().y + 6.5),
                                        Align2::LEFT_CENTER,
                                        "Microsoft",
                                        FontId::new(9.0, FontFamily::Name("dm".into())),
                                        p.faint,
                                    );
                                    if cur {
                                        ui.painter().circle_filled(
                                            egui::pos2(rect2.right() - 12.0, rect2.center().y),
                                            3.0,
                                            p.accent,
                                        );
                                    }
                                    if rrow.clicked() {
                                        let _ = auth::auth_set_default(a.uuid.clone());
                                        let _ =
                                            self.tx.send(Msg::Accounts(auth::auth_accounts()));
                                        self.account_open = false;
                                    }
                                }
                                ui.add_space(8.0);
                                hairline(ui, p);
                                ui.add_space(8.0);
                            }
                            // ---------------- nick offline (conta pirata) ----------------
                            text(ui, "NICK OFFLINE", 9.0, p.faint, true);
                            ui.add_space(5.0);
                            ui.horizontal(|ui| {
                                let r = ui.add(
                                    egui::TextEdit::singleline(&mut self.nick)
                                        .desired_width(234.0)
                                        .hint_text("Steve"),
                                );
                                if r.changed() {
                                    self.nick_ready = false;
                                }
                                let enter = ui.input(|i| i.key_pressed(egui::Key::Enter))
                                    && r.has_focus();
                                if r.lost_focus() || enter {
                                    self.nick_ready = true;
                                    self.save();
                                }
                                let (pvbox, _) = ui.allocate_exact_size(
                                    Vec2::splat(24.0),
                                    Sense::hover(),
                                );
                                let nv = self.nick.trim().to_string();
                                let stex = if self.nick_ready && valid_nick(&nv) {
                                    self.skin(ui.ctx(), &nv, Some(head_url(&nv)))
                                } else {
                                    None
                                };
                                paint_avatar(ui, pvbox, &nv, stex.as_ref(), p);
                            });
                            if self.nick_ready
                                && !self.nick.trim().is_empty()
                                && !valid_nick(self.nick.trim())
                            {
                                text(
                                    ui,
                                    "3 a 16 caracteres: letras, n\u{00fa}meros e _",
                                    9.5,
                                    Color32::from_rgb(226, 86, 97),
                                    false,
                                );
                                ui.add_space(6.0);
                            }
                            ui.add_space(8.0);
                            ui.horizontal(|ui| {
                                if accent_button(ui, "Jogar offline", p).clicked() {
                                    let nv = self.nick.trim().to_string();
                                    if valid_nick(&nv) {
                                        self.nick = nv.clone();
                                        self.nick_ready = true;
                                        self.save();
                                        self.toast(format!("Modo offline como {nv}"), false);
                                        self.account_open = false;
                                    } else {
                                        self.nick_ready = true;
                                    }
                                }
                                ui.add_space(4.0);
                                if chip_button(ui, "Entrar com Microsoft", p).clicked() {
                                    self.login.open = true;
                                    self.account_open = false;
                                    if self.default_account().is_none() {
                                        self.begin_login();
                                    }
                                }
                            });
                        });
                });
            ui.ctx().request_repaint();
        }
    }

    fn system_card(&mut self, ui: &mut egui::Ui) {
        let p = self.pal;
        let total = self
            .sys
            .as_ref()
            .and_then(|s| s.get("total_mb"))
            .and_then(|v| v.as_u64())
            .unwrap_or(0);
        let avail = self
            .sys
            .as_ref()
            .and_then(|s| s.get("avail_mb"))
            .and_then(|v| v.as_u64())
            .unwrap_or(0);
        let used = total.saturating_sub(avail);
        let frac = if total > 0 {
            used as f32 / total as f32
        } else {
            0.0
        };
        Frame::none()
            .fill(p.surface)
            .rounding(Rounding::same(16.0))
            .stroke(Stroke::new(1.0_f32, p.line))
            .inner_margin(Margin::symmetric(16.0, 14.0))
            .show(ui, |ui| {
                ui.set_min_width(ui.available_width());
                ui.horizontal(|ui| {
                    text(ui, "Sistema", 13.0, p.name, true);
                    ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                        text(
                            ui,
                            &format!("{:.0}% em uso", frac * 100.0),
                            10.0,
                            p.faint,
                            false,
                        );
                    });
                });
                ui.add_space(10.0);
                let (bar, _) = ui.allocate_exact_size(
                    Vec2::new(ui.available_width(), 6.0),
                    Sense::hover(),
                );
                ui.painter()
                    .rect_filled(bar, Rounding::same(3.0), p.surface2);
                let fam = ui
                    .ctx()
                    .animate_value_with_time(egui::Id::new("sysbar"), frac, 0.45);
                let mut fill = bar;
                fill.set_width(bar.width() * fam);
                let col = if frac > 0.85 {
                    Color32::from_rgb(226, 86, 97)
                } else {
                    p.accent
                };
                ui.painter().rect_filled(fill, Rounding::same(3.0), col);
                ui.add_space(8.0);
                ui.horizontal(|ui| {
                    text(
                        ui,
                        &format!("{:.1} GB de {:.1} GB", used as f32 / 1024.0, total as f32 / 1024.0),
                        10.0,
                        p.faint,
                        false,
                    );
                    ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                        text(
                            ui,
                            &format!("{:.1} GB livres", avail as f32 / 1024.0),
                            10.0,
                            p.dim,
                            true,
                        );
                    });
                });
                ui.add_space(10.0);
                hairline(ui, p);
                ui.add_space(10.0);
                ui.horizontal(|ui| {
                    text(ui, "Java disponível", 10.5, p.dim, false);
                    ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                        let mut majors: Vec<u64> = self
                            .sys
                            .as_ref()
                            .and_then(|s| s.get("java"))
                            .and_then(|j| j.as_array())
                            .map(|j| {
                                j.iter()
                                    .filter_map(|x| x.get("major").and_then(|m| m.as_u64()))
                                    .collect()
                            })
                            .unwrap_or_default();
                        majors.sort_unstable();
                        majors.dedup();
                        for m in majors.iter().rev().take(4) {
                            chip(ui, &format!("{m}"), p);
                            ui.add_space(4.0);
                        }
                    });
                });
            });
    }

    fn shortcuts_card(&mut self, ui: &mut egui::Ui) {
        let p = self.pal;
        Frame::none()
            .fill(p.surface)
            .rounding(Rounding::same(16.0))
            .stroke(Stroke::new(1.0_f32, p.line))
            .inner_margin(Margin::symmetric(16.0, 12.0))
            .show(ui, |ui| {
                ui.set_min_width(ui.available_width());
                text(ui, "Atalhos", 13.0, p.name, true);
                ui.add_space(6.0);
                for (kind, title, sub) in [
                    (0u8, "Pasta do jogo", ".minecraft"),
                    (1, "Logs", "última execução"),
                    (2, "Mods", "perfil Astucy"),
                ] {
                    let (rect, resp) = ui.allocate_exact_size(
                        Vec2::new(ui.available_width(), 34.0),
                        Sense::click(),
                    );
                    let hl = ui
                        .ctx()
                        .animate_bool_with_time(ui.id().with(("sc", title)), resp.hovered(), 0.15);
                    if hl > 0.02 {
                        ui.painter().rect_filled(
                            rect,
                            Rounding::same(9.0),
                            mixc(p.surface, p.surface2, hl),
                        );
                    }
                    let irect = egui::Rect::from_center_size(
                        rect.left_center() + Vec2::new(14.0, 0.0),
                        Vec2::splat(15.0),
                    );
                    quick_icon(ui.painter(), irect, kind, mixc(p.dim, p.text, hl));
                    ui.painter().text(
                        rect.left_center() + Vec2::new(30.0, -6.0),
                        Align2::LEFT_CENTER,
                        title,
                        FontId::new(11.5, FontFamily::Name("dmb".into())),
                        p.name,
                    );
                    ui.painter().text(
                        rect.left_center() + Vec2::new(30.0, 7.0),
                        Align2::LEFT_CENTER,
                        sub,
                        FontId::new(9.5, FontFamily::Name("dm".into())),
                        p.faint,
                    );
                    ui.painter().text(
                        rect.right_center() - Vec2::new(10.0, 0.0),
                        Align2::RIGHT_CENTER,
                        "abrir",
                        FontId::new(9.5, FontFamily::Name("dm".into())),
                        p.faint,
                    );
                    if resp.clicked() {
                        let _ = engine::open_folder(match kind {
                            0 => "minecraft",
                            1 => "logs",
                            _ => "mods",
                        });
                    }
                }
            });
    }

    fn stat_strip(&mut self, ui: &mut egui::Ui) {
        let p = self.pal;
        let mods_on = self.mods.iter().filter(|m| m.enabled).count();
        let java = self
            .sys
            .as_ref()
            .and_then(|s| s.get("java"))
            .and_then(|j| j.as_array())
            .and_then(|j| j.last())
            .and_then(|j| j.get("major"))
            .and_then(|m| m.as_u64())
            .unwrap_or(0);
        Frame::none()
            .fill(p.surface)
            .rounding(Rounding::same(16.0))
            .stroke(Stroke::new(1.0_f32, p.line))
            .inner_margin(Margin::symmetric(20.0, 14.0))
            .show(ui, |ui| {
                ui.set_min_width(ui.available_width());
                ui.columns(4, |cols| {
                    stat(&mut cols[0], "VERSÃO", &self.version, p);
                    stat(&mut cols[1], "MODS ATIVOS", &format!("{mods_on}"), p);
                    stat(&mut cols[2], "MEMÓRIA", &format!("{} MB", self.ram), p);
                    stat(
                        &mut cols[3],
                        "JAVA",
                        &if java > 0 {
                            format!("Java {java}")
                        } else {
                            "—".into()
                        },
                        p,
                    );
                });
            });
    }

    fn versions(&mut self, ui: &mut egui::Ui) {
        let p = self.pal;
        ui.horizontal(|ui| {
            text(ui, "Versões", 24.0, p.text, true);
            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                if pill_button(ui, "Atualizar", false, p).clicked() {
                    self.catalog_loaded = false;
                    self.spawn_catalog(true);
                }
            });
        });
        ui.add_space(4.0);
        text(
            ui,
            "Catálogo oficial, de 1.7.10 até a mais recente, agrupado por família.",
            11.5,
            p.dim,
            false,
        );
        ui.add_space(14.0);

        ui.horizontal(|ui| {
            let search = egui::TextEdit::singleline(&mut self.query)
                .hint_text("Filtrar — 1.21, 1.7.10…")
                .desired_width(300.0);
            ui.add(search);
            ui.add_space(10.0);
            for (f, label) in [
                (Filter::All, "Todas"),
                (Filter::Client, "26.x"),
                (Filter::Legacy, "1.x"),
                (Filter::Installed, "Instaladas"),
            ] {
                if pill_button(ui, label, self.filter == f, p).clicked() {
                    self.filter = f;
                }
            }
        });
        ui.add_space(14.0);

        if !self.catalog_loaded {
            text(ui, "Lendo o catálogo da Mojang…", 12.0, p.faint, false);
            return;
        }

        let q = self.query.trim().to_lowercase();
        let mut groups: Vec<(String, Vec<engine::CatalogEntry>)> = Vec::new();
        for v in &self.catalog {
            if !q.is_empty() && !v.id.to_lowercase().contains(&q) {
                continue;
            }
            let ok = match self.filter {
                Filter::All => true,
                Filter::Client => v.client,
                Filter::Legacy => !v.client,
                Filter::Installed => v.installed,
            };
            if !ok {
                continue;
            }
            let fam = family_of(&v.id);
            match groups.iter_mut().find(|(k, _)| *k == fam) {
                Some((_, list)) => list.push(v.clone()),
                None => groups.push((fam, vec![v.clone()])),
            }
        }

        let mut toggle: Option<String> = None;
        let mut action: Option<CardAction> = None;
        let version = self.version.clone();
        let install_id = self.install_id.clone();

        ScrollArea::vertical()
            .auto_shrink([false, false])
            .show(ui, |ui| {
                for (fam, list) in &groups {
                    let open = !q.is_empty() || self.open_groups.contains(fam);
                    let installed = list.iter().filter(|v| v.installed).count();
                    if group_header(ui, fam, list.len(), installed, &list[0].id, open, p).clicked() {
                        toggle = Some(fam.clone());
                    }
                    if open {
                        ui.add_space(8.0);
                        let card_w = ((ui.available_width() - 10.0) / 2.0).max(260.0);
                        let mut i = 0;
                        while i < list.len() {
                            ui.horizontal(|ui| {
                                ui.spacing_mut().item_spacing.x = 10.0;
                                for k in 0..2 {
                                    if i + k < list.len() {
                                        let v = &list[i + k];
                                        ui.allocate_ui_with_layout(
                                            Vec2::new(card_w, 64.0),
                                            Layout::top_down(Align::Min),
                                            |ui| {
                                                if let Some(a) = version_card(
                                                    ui,
                                                    v,
                                                    &version,
                                                    install_id.as_deref(),
                                                    p,
                                                ) {
                                                    action = Some(a);
                                                }
                                            },
                                        );
                                    } else {
                                        ui.allocate_space(Vec2::new(card_w, 64.0));
                                    }
                                }
                            });
                            i += 2;
                        }
                        ui.add_space(4.0);
                    }
                    ui.add_space(6.0);
                }
                if groups.is_empty() {
                    text(ui, "Nada aqui — ajuste o filtro.", 12.0, p.faint, false);
                }
            });

        if let Some(fam) = toggle {
            if !self.open_groups.remove(&fam) {
                self.open_groups.insert(fam);
            }
        }
        match action {
            Some(CardAction::Install(id)) => self.install(id),
            Some(CardAction::Use(id)) => {
                self.version = id;
                self.refresh_mods();
                self.save();
            }
            None => {}
        }
    }

    fn mods(&mut self, ui: &mut egui::Ui) {
        let p = self.pal;
        let is_rust = self.version.contains("Rust");
        let ver = if self.version.is_empty() {
            "—".to_string()
        } else {
            self.version.clone()
        };
        ui.horizontal(|ui| {
            ui.vertical(|ui| {
                text(ui, "Mods", 26.0, p.text, true);
                ui.add_space(3.0);
                let sub = if is_rust {
                    "26.3 (Rust) \u{2014} motor nativo em Rust. Mods .jar de Fabric não rodam aqui; ative os addons opcionais abaixo.".to_string()
                } else {
                    format!(
                        "Fabric {ver} \u{2014} baixa direto do Modrinth, já com as dependências obrigatórias."
                    )
                };
                text(ui, &sub, 11.5, p.dim, false);
            });
            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                if chip_button(ui, "Abrir pasta", p).clicked() {
                    if let Ok(dir) = profile::mods_dir_for(&self.version) {
                        let _ = std::process::Command::new("explorer").arg(dir).spawn();
                    } else {
                        let _ = engine::open_folder("mods");
                    }
                }
            });
        });
        ui.add_space(14.0);

        if is_rust {
            const RUST_ADDONS: &[(&str, &str, &str, bool)] = &[
                ("modmenu", "Mod Menu", "Lista de módulos e configs no menu R-SHIFT", false),
                ("mousetweaks", "Mouse Tweaks", "Arrastar itens rápido e scroll no inventário/baús", false),
                ("itemrespawn", "Custom Item Respawn", "Troca rápida e respawn customizado de itens/totem", false),
                ("healthtag", "Player Health (Corações)", "Mostra corações de vida em cima da cabeça dos players (ajustável no R-SHIFT)", false),
                ("blur", "Motion Blur", "Desfoque suave ao girar a câmera", false),
                ("fullbright", "Fullbright", "Brilho máximo em cavernas e à noite", false),
                ("zoom", "Zoom (C)", "Zoom suave segurando a tecla C", false),
                ("crosshair", "Custom Crosshair", "Mira pixelada customizável no R-SHIFT", false),
                ("viewmodel", "Viewmodel", "Escala e posição da mão/escudo/totem", false),
                ("target", "Target HUD", "HUD com vida e info do alvo na mira", false),
            ];
            ScrollArea::vertical().auto_shrink([false, false]).show(ui, |ui| {
                text(ui, "Addons opcionais do Astucy (Rust)", 15.0, p.text, true);
                ui.add_space(4.0);
                text(
                    ui,
                    "Sincronizado com astucy-rust.json — também pode ser ajustado dentro do jogo com R-SHIFT.",
                    10.5,
                    p.faint,
                    false,
                );
                ui.add_space(10.0);
                for &(id, title, desc, def_on) in RUST_ADDONS {
                    let on = profile::rust_addon_enabled(id, def_on);
                    Frame::none()
                        .fill(p.surface)
                        .rounding(Rounding::same(12.0))
                        .stroke(Stroke::new(1.0_f32, p.line))
                        .inner_margin(Margin::symmetric(16.0, 10.0))
                        .show(ui, |ui| {
                            ui.set_min_width(ui.available_width());
                            ui.horizontal(|ui| {
                                ui.vertical(|ui| {
                                    ui.horizontal(|ui| {
                                        text(ui, title, 12.5, p.name, true);
                                        ui.add_space(6.0);
                                        chip(ui, "rust", p);
                                    });
                                    text(ui, desc, 10.5, p.faint, false);
                                });
                                ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                                    if switch(ui, on, p).clicked() {
                                        if let Err(e) = profile::set_rust_addon_enabled(id, !on) {
                                            self.toast(e, true);
                                        }
                                    }
                                });
                            });
                        });
                    ui.add_space(8.0);
                }
            });
            return;
        }

        ui.horizontal(|ui| {
            let w = (ui.available_width() - 112.0).max(220.0);
            let r = ui.add_sized(
                [w, 38.0],
                egui::TextEdit::singleline(&mut self.mr_query)
                    .hint_text("Buscar no Modrinth \u{2014} sodium, zoom, motion blur\u{2026}"),
            );
            ui.add_space(8.0);
            let go = ui.add_sized(
                [100.0, 38.0],
                egui::Button::new(
                    RichText::new(if self.mr_busy { "Buscando\u{2026}" } else { "Buscar" })
                        .size(12.0)
                        .color(p.accent_ink)
                        .font(FontId::new(12.0, FontFamily::Name("dmb".into()))),
                )
                .fill(if self.mr_busy { p.surface2 } else { p.accent })
                .rounding(Rounding::same(10.0))
                .stroke(Stroke::NONE),
            );
            let enter = r.lost_focus() && ui.input(|i| i.key_pressed(egui::Key::Enter));
            if (go.clicked() || enter) && !self.mr_busy {
                self.search_modrinth();
            }
        });
        ui.add_space(12.0);

        ScrollArea::vertical().auto_shrink([false, false]).show(ui, |ui| {
            third_party_disclaimer(ui, p);
            ui.add_space(10.0);
            if let Some(err) = self.mr_err.clone() {
                text(ui, &err, 11.0, Color32::from_rgb(226, 86, 97), false);
                ui.add_space(10.0);
            }
            if let Some(hits) = self.mr_hits.clone() {
                if hits.is_empty() && !self.mr_busy {
                    text(ui, "Nada encontrado pra essa versão.", 11.5, p.faint, false);
                    ui.add_space(10.0);
                }
                for h in hits {
                    let installed = self.mods.iter().any(|m| {
                        let name = m.name.to_lowercase();
                        let slug = h.slug.to_lowercase();
                        !slug.is_empty() && name.contains(&slug)
                    });
                    let itex = self.icon(ui.ctx(), Some(h.icon.clone()));
                    if let Some(slug) =
                        mod_result(ui, &h, self.installing.as_deref(), installed, itex.as_ref(), p)
                    {
                        self.install_mod(slug);
                    }
                    ui.add_space(8.0);
                }
            }

            ui.add_space(8.0);
            ui.horizontal(|ui| {
                text(ui, "Instalados", 15.0, p.text, true);
                ui.add_space(8.0);
                chip(ui, &format!("{}", self.mods.len()), p);
                ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                    if chip_button(ui, "Atualizar", p).clicked() {
                        self.refresh_mods();
                    }
                });
            });
            ui.add_space(4.0);
            text(
                ui,
                "Desativar renomeia o .jar. Remover apaga o arquivo do perfil.",
                10.5,
                p.faint,
                false,
            );
            ui.add_space(10.0);

            let mut toggle: Option<usize> = None;
            let mut remove: Option<usize> = None;
            for (i, m) in self.mods.iter().enumerate() {
                Frame::none()
                    .fill(p.surface)
                    .rounding(Rounding::same(12.0))
                    .stroke(Stroke::new(1.0_f32, p.line))
                    .inner_margin(Margin::symmetric(16.0, 9.0))
                    .show(ui, |ui| {
                        ui.set_min_width(ui.available_width());
                        ui.horizontal(|ui| {
                            ui.vertical(|ui| {
                                ui.horizontal(|ui| {
                                    text(ui, &m.name, 12.5, p.name, true);
                                    let lower = m.name.to_ascii_lowercase();
                                    let is_core = [
                                        "astucyc",
                                        "fabric-api",
                                        "sodium",
                                        "lithium",
                                        "iris",
                                        "immediatelyfast",
                                        "entityculling",
                                        "ferritecore",
                                        "modmenu",
                                    ]
                                    .iter()
                                    .any(|k| lower.contains(k));
                                    if is_core {
                                        ui.add_space(6.0);
                                        chip(ui, "core", p);
                                    }
                                });
                                text(
                                    ui,
                                    &format!(
                                        "{:.1} MB \u{b7} {}",
                                        m.bytes as f64 / 1_048_576.0,
                                        if m.enabled { "ativo" } else { "desativado" }
                                    ),
                                    10.0,
                                    p.faint,
                                    false,
                                );
                            });
                            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                                if switch(ui, m.enabled, p).clicked() {
                                    toggle = Some(i);
                                }
                                ui.add_space(10.0);
                                let armed =
                                    matches!(self.confirm_remove, Some((j, t)) if j == i && now() - t < 4.0);
                                let label = if armed { "confirmar" } else { "remover" };
                                let col = if armed {
                                    Color32::from_rgb(226, 86, 97)
                                } else {
                                    p.dim
                                };
                                if ui
                                    .add(
                                        egui::Button::new(
                                            RichText::new(label)
                                                .size(10.0)
                                                .color(col)
                                                .font(FontId::new(10.0, FontFamily::Name("dm".into()))),
                                        )
                                        .fill(Color32::TRANSPARENT)
                                        .stroke(Stroke::NONE)
                                        .rounding(Rounding::same(8.0)),
                                    )
                                    .clicked()
                                {
                                    remove = Some(i);
                                }
                            });
                        });
                    });
                ui.add_space(6.0);
            }
            if self.mods.is_empty() {
                text(ui, "Nenhum mod na pasta do perfil.", 11.5, p.faint, false);
            }
            if let Some(i) = toggle {
                self.toggle_mod(i);
            }
            if let Some(i) = remove {
                let armed = matches!(self.confirm_remove, Some((j, t)) if j == i && now() - t < 4.0);
                if armed {
                    self.remove_mod(i);
                } else {
                    self.confirm_remove = Some((i, now()));
                }
            }
        });
    }

    fn search_modrinth(&mut self) {
        if self.mr_busy {
            return;
        }
        if self.version.trim().is_empty() {
            self.toast("Escolha uma versão antes de buscar.", true);
            return;
        }
        self.mr_busy = true;
        self.mr_err = None;
        let tx = self.tx.clone();
        let q = self.mr_query.clone();
        let v = self.version.clone();
        std::thread::spawn(move || {
            let r = profile::modrinth_search_sync(&q, &v, 0, 20).map(|page| page.hits);
            let _ = tx.send(Msg::Modrinth(r));
        });
    }

    fn install_mod(&mut self, slug: String) {
        if self.installing.is_some() {
            return;
        }
        self.installing = Some(slug.clone());
        let tx = self.tx.clone();
        let v = self.version.clone();
        std::thread::spawn(move || {
            let r = match profile::install_mods_sync(&v, &[slug]) {
                Ok(list) => {
                    if let Some(ok) = list.iter().find(|r| r.ok) {
                        Ok(ok.detail.clone())
                    } else if let Some(bad) = list.first() {
                        Err(bad.detail.clone())
                    } else {
                        Err("nada foi baixado".to_string())
                    }
                }
                Err(e) => Err(e),
            };
            let _ = tx.send(Msg::ModInstalled(r));
        });
    }

    fn remove_mod(&mut self, i: usize) {
        let Some(m) = self.mods.get(i).cloned() else {
            self.confirm_remove = None;
            return;
        };
        let dir = profile::mods_dir_for(&self.version).unwrap_or_else(|_| mods_dir());
        let path = dir.join(&m.name);
        match std::fs::remove_file(&path) {
            Ok(()) => {
                self.toast(format!("{} removido.", m.name), false);
                self.refresh_mods();
            }
            Err(e) => self.toast(format!("não deu pra remover: {e}"), true),
        }
        self.confirm_remove = None;
    }

    fn apply_theme(&mut self, choice: &str) {
        self.theme = choice.to_string();
        let light = match choice {
            "light" => true,
            "dark" => false,
            _ => system_is_light(),
        };
        self.light = light;
        self.pal = palette(light);
        self.save();
    }

    fn toggle_mod(&mut self, i: usize) {
        let Some(m) = self.mods.get(i).cloned() else {
            return;
        };
        let dir = profile::mods_dir_for(&self.version).unwrap_or_else(|_| mods_dir());
        let from = dir.join(&m.name);
        let to = if m.enabled {
            dir.join(format!("{}.disabled", m.name))
        } else {
            dir.join(m.name.trim_end_matches(".disabled"))
        };
        match std::fs::rename(&from, &to) {
            Ok(()) => self.refresh_mods(),
            Err(e) => self.toast(format!("Não deu para mudar {e}"), true),
        }
    }

    fn settings(&mut self, ui: &mut egui::Ui) {
        let p = self.pal;
        text(ui, "Ajustes", 26.0, p.text, true);
        ui.add_space(3.0);
        text(
            ui,
            "Salvos automaticamente e aplicados na próxima vez que você abrir o jogo.",
            11.5,
            p.dim,
            false,
        );
        ui.add_space(18.0);

        let full = ui.available_width();
        let gap = 14.0;
        let col = ((full - gap) / 2.0).max(320.0);
        let mut theme_pick: Option<String> = None;
        let mut restore = false;
        let mut save_client = false;

        ui.horizontal_top(|ui| {
            ui.spacing_mut().item_spacing.x = gap;

            ui.allocate_ui_with_layout(
                Vec2::new(col, 0.0),
                Layout::top_down(Align::Min),
                |ui| {
                    settings_card(ui, p, "Memória do jogo", |ui| {
                        ui.horizontal(|ui| {
                            text(ui, "Heap reservado", 12.0, p.name, true);
                            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                                badge(ui, &format!("{} MB", self.ram), p);
                            });
                        });
                        ui.add_space(8.0);
                        if ram_slider(ui, &mut self.ram, p) {
                            self.save();
                        }
                        ui.add_space(6.0);
                        ui.horizontal(|ui| {
                            text(ui, "1 GB", 9.0, p.faint, false);
                            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                                text(ui, "8 GB", 9.0, p.faint, false);
                            });
                        });
                        ui.add_space(6.0);
                        text(
                            ui,
                            "O heap cresce sob demanda: abre rápido e ocupa menos RAM no menu.",
                            10.5,
                            p.faint,
                            false,
                        );
                    });

                    settings_card(ui, p, "Resolução da janela", |ui| {
                        ui.horizontal(|ui| {
                            for (w, h) in [(1280u32, 720u32), (1600, 900), (1920, 1080)] {
                                if chip_button(ui, &format!("{w} × {h}"), p).clicked() {
                                    self.res_w = w;
                                    self.res_h = h;
                                    self.save();
                                }
                                ui.add_space(6.0);
                            }
                        });
                        ui.add_space(10.0);
                        ui.horizontal(|ui| {
                            ui.add(
                                egui::DragValue::new(&mut self.res_w)
                                    .range(640..=7680)
                                    .speed(4.0)
                                    .suffix(" px"),
                            );
                            ui.add_space(6.0);
                            text(ui, "×", 11.0, p.faint, false);
                            ui.add_space(6.0);
                            ui.add(
                                egui::DragValue::new(&mut self.res_h)
                                    .range(480..=4320)
                                    .speed(4.0)
                                    .suffix(" px"),
                            );
                            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                                if chip_button(ui, "Salvar", p).clicked() {
                                    self.save();
                                    self.toast("Resolução salva.", false);
                                }
                            });
                        });
                        ui.add_space(6.0);
                        text(
                            ui,
                            "Aplicada na janela do jogo quando o launcher abrir ele.",
                            10.5,
                            p.faint,
                            false,
                        );
                    });

                    settings_card(ui, p, "Flags de JVM", |ui| {
                        let r = ui.add(
                            egui::TextEdit::multiline(&mut self.jvm)
                                .desired_rows(3)
                                .desired_width(f32::INFINITY)
                                .hint_text("-XX:+UseG1GC ..."),
                        );
                        if r.lost_focus() {
                            self.save();
                        }
                        ui.add_space(6.0);
                        text(
                            ui,
                            "Aplicadas depois das flags do Astucy — o que você escrever aqui vence.",
                            10.5,
                            p.faint,
                            false,
                        );
                    });
                },
            );

            ui.allocate_ui_with_layout(
                Vec2::new(col, 0.0),
                Layout::top_down(Align::Min),
                |ui| {
                    settings_card(ui, p, "Aparência", |ui| {
                        ui.horizontal(|ui| {
                            ui.spacing_mut().item_spacing.x = 8.0;
                            let w = ((ui.available_width() - 16.0) / 3.0).max(80.0);
                            for (id, label) in [
                                ("light", "Claro"),
                                ("dark", "Escuro"),
                                ("system", "Sistema"),
                            ] {
                                if theme_card(ui, w, label, id, self.theme == id, p).clicked() {
                                    theme_pick = Some(id.to_string());
                                }
                            }
                        });
                    });

                    settings_card(ui, p, "Ao iniciar o jogo", |ui| {
                        ui.horizontal(|ui| {
                            ui.vertical(|ui| {
                                text(ui, "Fechar o launcher ao jogar", 12.0, p.name, true);
                                text(
                                    ui,
                                    "Libera RAM e GPU para o Minecraft. Reabra pelo atalho.",
                                    10.5,
                                    p.faint,
                                    false,
                                );
                            });
                            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                                if switch(ui, self.close_on_play, p).clicked() {
                                    self.close_on_play = !self.close_on_play;
                                    self.save();
                                }
                            });
                        });
                    });

                    settings_card(ui, p, "Pastas", |ui| {
                        ui.horizontal_wrapped(|ui| {
                            ui.spacing_mut().item_spacing = Vec2::new(8.0, 8.0);
                            for (kind, label) in [
                                ("minecraft", "Pasta do jogo"),
                                ("logs", "Logs"),
                                ("mods", "Mods"),
                                ("versions", "Versões"),
                            ] {
                                if chip_button(ui, label, p).clicked() {
                                    let _ = engine::open_folder(kind);
                                }
                            }
                        });
                    });

                    settings_card(ui, p, "Client ID do Azure", |ui| {
                        let r = ui.add(
                            egui::TextEdit::singleline(&mut self.client_id)
                                .desired_width(f32::INFINITY)
                                .hint_text("00000000-0000-0000-0000-000000000000"),
                        );
                        if r.lost_focus() {
                            save_client = true;
                        }
                        ui.add_space(8.0);
                        ui.horizontal(|ui| {
                            if chip_button(ui, "Salvar client ID", p).clicked() {
                                save_client = true;
                            }
                            ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                                if danger_button(ui, "Restaurar padrão", p).clicked() {
                                    restore = true;
                                }
                            });
                        });
                        ui.add_space(6.0);
                        text(
                            ui,
                            "O Astucy já vem com o app dele — preencha só pra usar o seu.",
                            10.5,
                            p.faint,
                            false,
                        );
                    });
                },
            );
        });

        if let Some(t) = theme_pick {
            self.apply_theme(&t);
        }
        if save_client {
            match auth::auth_set_client_id(self.client_id.clone()) {
                Ok(()) => self.toast("Client ID salvo.", false),
                Err(e) => self.toast(e, true),
            }
        }
        if restore {
            self.ram = 2560;
            self.res_w = 1280;
            self.res_h = 720;
            self.jvm = "-XX:+UseG1GC -XX:MaxGCPauseMillis=60 -XX:+DisableExplicitGC".into();
            self.close_on_play = true;
            self.apply_theme("system");
            self.save();
            self.toast("Ajustes restaurados.", false);
        }
    }

    fn login_modal(&mut self, ctx: &egui::Context) {
        if !self.login.open {
            return;
        }
        let p = self.pal;
        let mut close = false;
        egui::Window::new("login")
            .title_bar(false)
            .collapsible(false)
            .resizable(false)
            .anchor(Align2::CENTER_CENTER, [0.0, 0.0])
            .min_width(400.0)
            .frame(
                Frame::none()
                    .fill(p.surface)
                    .rounding(Rounding::same(16.0))
                    .stroke(Stroke::new(1.0_f32, p.line))
                    .inner_margin(Margin::same(20.0)),
            )
            .show(ctx, |ui| {
                ui.horizontal(|ui| {
                    text(ui, "Conta Microsoft", 14.5, p.text, true);
                    ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                        if window_button(ui, 'x', p).clicked() {
                            close = true;
                        }
                    });
                });
                ui.add_space(10.0);
                hairline(ui, p);
                ui.add_space(12.0);

                let acc = self.default_account().cloned();
                if let Some(a) = acc {
                    text(ui, "Conta conectada", 10.5, p.faint, true);
                    ui.add_space(4.0);
                    text(ui, &a.name, 18.0, p.text, true);
                    ui.add_space(14.0);
                    ui.horizontal(|ui| {
                        if chip_button(ui, "Entrar com outra", p).clicked() {
                            self.login.code.clear();
                            self.begin_login();
                        }
                        if chip_button(ui, "Sair desta conta", p).clicked() {
                            let _ = auth::auth_remove(a.uuid.clone());
                            let _ = self.tx.send(Msg::Accounts(auth::auth_accounts()));
                            self.close_login(ui.ctx());
                        }
                    });
                    ui.add_space(10.0);
                    text(
                        ui,
                        "A sessão renova sozinha antes de abrir o jogo.",
                        10.5,
                        p.faint,
                        false,
                    );
                    return;
                }

                if self.login.code.is_empty() {
                    text(ui, "Entrar sem senha, pelo site da Microsoft.", 11.5, p.dim, false);
                    ui.add_space(12.0);
                    if !self.login.busy {
                        if accent_button(ui, "Gerar código", p).clicked() {
                            self.begin_login();
                        }
                    } else {
                        text(ui, "Gerando código…", 11.5, p.faint, false);
                    }
                    return;
                }

                text(ui, "Digite este código em", 11.0, p.dim, false);
                ui.add_space(3.0);
                text(ui, &self.login.uri, 12.5, p.accent, false);
                ui.add_space(12.0);
                Frame::none()
                    .fill(p.surface2)
                    .rounding(Rounding::same(12.0))
                    .inner_margin(Margin::symmetric(18.0, 12.0))
                    .show(ui, |ui| {
                        ui.set_min_width(ui.available_width());
                        ui.with_layout(Layout::top_down(Align::Center), |ui| {
                            text(ui, &self.login.code, 30.0, p.text, true);
                        });
                    });
                ui.add_space(14.0);
                ui.horizontal(|ui| {
                    if accent_button(ui, "Abrir navegador", p).clicked() {
                        let _ = std::process::Command::new("cmd")
                            .args(["/C", "start", "", &self.login.uri])
                            .spawn();
                    }
                    if chip_button(ui, "Cancelar", p).clicked() {
                        close = true;
                    }
                });
                ui.add_space(10.0);
                text(ui, &self.login.status, 10.5, p.faint, false);
            });
        if close {
            self.close_login(ctx);
        }
    }

    fn close_login(&mut self, ctx: &egui::Context) {
        self.login.open = false;
        self.login.busy = false;
        self.login.code.clear();
        self.login.device.clear();
        ctx.request_repaint();
    }
}

impl eframe::App for App {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        self.drain(ctx);
        if self.autostart {
            self.autostart = false;
            self.launch();
        }
        apply_visuals(ctx, self.pal);
        self.splash(ctx);
        

        self.titlebar(ctx);
        self.sidebar(ctx);

        egui::CentralPanel::default()
            .frame(
                Frame::none()
                    .fill(self.pal.bg)
                    .inner_margin(Margin {
                        left: 26.0,
                        right: 26.0,
                        top: 22.0,
                        bottom: 18.0,
                    }),
            )
            .show(ctx, |ui| {
                // transição de pane: fade + deslizar pra cima
                let t = ((now() - self.pane_at) / 0.30 as f64) as f32;
                let t = t.clamp(0.0, 1.0);
                if t < 1.0 {
                    ui.ctx().request_repaint();
                }
                let e = ease_out_cubic(t);
                Frame::none()
                    .outer_margin(Margin {
                        top: 18.0 * (1.0 - e),
                        ..Default::default()
                    })
                    .show(ui, |ui| {
                        ui.set_opacity(e);
                        match self.pane {
                            Pane::Home => {
                                ScrollArea::vertical()
                                    .auto_shrink([false, false])
                                    .show(ui, |ui| self.home(ui));
                            }
                            Pane::Versions => self.versions(ui),
                            Pane::Mods => self.mods(ui),
                            Pane::Settings => {
                                ScrollArea::vertical()
                                    .auto_shrink([false, false])
                                    .show(ui, |ui| self.settings(ui));
                            }
                        }
                    });
            });

        self.login_modal(ctx);
        self.toast_layer(ctx);
    }
}

/* ==================================================================
 * widgets
 * ================================================================== */

fn text(ui: &mut egui::Ui, s: &str, size: f32, color: Color32, bold: bool) {
    let family = if bold { "dmb" } else { "dm" };
    ui.label(
        RichText::new(s)
            .size(size)
            .color(color)
            .font(FontId::new(size, FontFamily::Name(family.into()))),
    );
}

/* ==================================================================
 * skins (cabeças de player)
 * ================================================================== */

fn skins_dir() -> PathBuf {
    std::env::var("APPDATA")
        .map(|a| PathBuf::from(a).join("com.astucy.launcher").join("skins"))
        .unwrap_or_else(|_| PathBuf::from("skins"))
}

fn skin_ident_ident(k: &str) -> String {
    k.chars()
        .filter(|c| c.is_ascii_alphanumeric() || *c == '-' || *c == '_')
        .take(64)
        .collect()
}

fn valid_nick(n: &str) -> bool {
    let n = n.trim();
    (3..=16).contains(&n.chars().count())
        && n.chars().all(|c| c.is_ascii_alphanumeric() || c == '_')
}

fn head_url(ident: &str) -> String {
    format!("https://mc-heads.net/avatar/{}/64", ident)
}

/// Decodifica um PNG (usado nas skins) para ColorImage em RGBA.
fn decode_png(data: &[u8]) -> Option<egui::ColorImage> {
    let mut dec = png::Decoder::new(std::io::Cursor::new(data));
    dec.set_transformations(png::Transformations::EXPAND | png::Transformations::STRIP_16);
    let mut r = dec.read_info().ok()?;
    let mut buf = vec![0u8; r.output_buffer_size()];
    let info = r.next_frame(&mut buf).ok()?;
    let (w, h) = (info.width as usize, info.height as usize);
    if w == 0 || h == 0 || w > 512 || h > 512 {
        return None;
    }
    let n = w * h * 4;
    let mut rgba = vec![0u8; n];
    let src = &buf[..buf.len().min(n * 2)];
    match info.color_type {
        png::ColorType::Rgba => rgba = src[..n.min(src.len())].to_vec(),
        png::ColorType::Rgb => {
            for i in 0..(w * h) {
                let si = i * 3;
                let di = i * 4;
                if si + 3 > src.len() {
                    break;
                }
                rgba[di..di + 3].copy_from_slice(&src[si..si + 3]);
                rgba[di + 3] = 255;
            }
        }
        png::ColorType::Grayscale => {
            for i in 0..(w * h) {
                if i >= src.len() {
                    break;
                }
                let g = src[i];
                let di = i * 4;
                rgba[di] = g;
                rgba[di + 1] = g;
                rgba[di + 2] = g;
                rgba[di + 3] = 255;
            }
        }
        png::ColorType::GrayscaleAlpha => {
            for i in 0..(w * h) {
                let si = i * 2;
                if si + 2 > src.len() {
                    break;
                }
                let g = src[si];
                rgba[i * 4] = g;
                rgba[i * 4 + 1] = g;
                rgba[i * 4 + 2] = g;
                rgba[i * 4 + 3] = src[si + 1];
            }
        }
        png::ColorType::Indexed => return None,
    }
    Some(egui::ColorImage::from_rgba_unmultiplied([w, h], &rgba))
}

fn fnv_hash(s: &str) -> u64 {
    s.bytes().fold(0xcbf29ce484222325u64, |a, b| (a ^ b as u64).wrapping_mul(0x100000001b3))
}

/// Decodifica um WebP (ícones do Modrinth) para ColorImage RGBA.
fn decode_webp(data: &[u8]) -> Option<egui::ColorImage> {
    let mut d = image_webp::WebPDecoder::new(std::io::Cursor::new(data)).ok()?;
    let (w, h) = d.dimensions();
    if w == 0 || h == 0 || w > 512 || h > 512 {
        return None;
    }
    let mut buf = vec![0u8; 4 * w as usize * h as usize];
    d.read_image(&mut buf).ok()?;
    Some(egui::ColorImage::from_rgba_unmultiplied(
        [w as usize, h as usize],
        &buf,
    ))
}

/// Detecta o formato pelo magic bytes e decodifica.
fn decode_any(data: &[u8]) -> Option<egui::ColorImage> {
    if data.len() > 8 && &data[0..4] == b"RIFF" && &data[8..12] == b"WEBP" {
        decode_webp(data)
    } else {
        decode_png(data)
    }
}

/// Interpolação ease-out cúbica.
fn ease_out_cubic(x: f32) -> f32 {
    1.0 - (1.0 - x).powi(3)
}

/// Mistura duas cores (linear em RGB).
fn mixc(a: Color32, b: Color32, t: f32) -> Color32 {
    let t = t.clamp(0.0, 1.0);
    let ch = |x: u8, y: u8| (x as f32 + (y as f32 - x as f32) * t) as u8;
    Color32::from_rgba_unmultiplied(ch(a.r(), b.r()), ch(a.g(), b.g()), ch(a.b(), b.b()), ch(
        a.a(),
        b.a(),
    ))
}

fn nav_item(ui: &mut egui::Ui, icon: u8, label: &str, on: bool, p: Pal) -> Response {
    let (rect, resp) =
        ui.allocate_exact_size(Vec2::new(ui.available_width(), 38.0), Sense::click());
    let id = ui.id().with(("nav", label));
    let hot = ui.ctx().animate_bool_with_time(id, resp.hovered(), 0.14);
    let sel = ui.ctx().animate_bool_with_time(id.with("sel"), on, 0.22);
    let a = hot.max(sel);
    if a > 0.02 {
        let fill = if on {
            mixc(p.bg, p.surface, a)
        } else {
            mixc(p.bg, p.surface2, hot)
        };
        ui.painter().rect_filled(rect, Rounding::same(11.0), fill);
    }
    if sel > 0.02 {
        ui.painter().rect_stroke(
            rect,
            Rounding::same(11.0),
            Stroke::new(1.0_f32, mixc(p.bg, p.line, sel)),
        );
        let bar_h = 14.0 * sel;
        let bar = egui::Rect::from_center_size(
            egui::pos2(rect.left() + 4.0, rect.center().y),
            Vec2::new(2.5, bar_h),
        );
        ui.painter().rect_filled(bar, Rounding::same(2.0), p.accent);
    }
    let col = mixc(p.dim, p.text, a);
    let irect = egui::Rect::from_center_size(
        rect.left_center() + Vec2::new(24.0, 0.0),
        Vec2::splat(15.0),
    );
    nav_icon(ui.painter(), irect, icon, mixc(col, p.accent, sel));
    let fam = if on { "dmb" } else { "dm" };
    ui.painter().text(
        rect.left_center() + Vec2::new(42.0, 0.0),
        Align2::LEFT_CENTER,
        label,
        FontId::new(12.5, FontFamily::Name(fam.into())),
        col,
    );
    if sel > 0.04 {
        ui.painter().circle_filled(
            rect.right_center() - Vec2::new(12.0, 0.0),
            2.5 * sel,
            p.accent,
        );
    }
    resp
}

/// Aviso de conteúdo de terceiros (isenções de responsabilidade sobre os mods do Modrinth).
fn third_party_disclaimer(ui: &mut egui::Ui, p: Pal) {
    let amber = Color32::from_rgb(232, 160, 64);
    Frame::none()
        .fill(Color32::from_rgba_unmultiplied(232, 160, 64, 18))
        .rounding(Rounding::same(12.0))
        .stroke(Stroke::new(1.0_f32, Color32::from_rgba_unmultiplied(232, 160, 64, 110)))
        .inner_margin(Margin::same(12.0))
        .show(ui, |ui| {
            ui.set_min_width(ui.available_width());
            ui.horizontal(|ui| {
                let (tri, _) = ui.allocate_exact_size(Vec2::splat(16.0), Sense::hover());
                let c = tri.center();
                let r = tri.width() * 0.5;
                ui.painter().add(egui::Shape::convex_polygon(
                    vec![
                        egui::pos2(c.x, c.y - r),
                        egui::pos2(c.x + r * 0.92, c.y + r * 0.78),
                        egui::pos2(c.x - r * 0.92, c.y + r * 0.78),
                    ],
                    amber,
                    Stroke::NONE,
                ));
                ui.painter().text(
                    egui::pos2(c.x, c.y + 2.0),
                    Align2::CENTER_CENTER,
                    "!",
                    FontId::new(9.5, FontFamily::Name("dmb".into())),
                    Color32::from_rgb(38, 28, 12),
                );
                text(
                    ui,
                    "Avisos e isenção de responsabilidade",
                    11.5,
                    mixc(p.text, amber, 0.55),
                    true,
                );
            });
            ui.add_space(6.0);
            text(
                ui,
                "Os mods listados vêm do Modrinth, hospedados e publicados por terceiros. O Astucy apenas instala o que você escolher — não revisa, publica ou modera esses arquivos. Instale somente projetos de autores em que você confia. Não somos afiliados ao Modrinth, Mojang, Microsoft ou aos autores dos mods.",
                10.5,
                p.dim,
                false,
            );
        });
}

fn nav_icon(painter: &egui::Painter, r: egui::Rect, kind: u8, col: Color32) {
    let at = |x: f32, y: f32| r.min + Vec2::new(r.width() * x, r.height() * y);
    let st = Stroke::new(1.4_f32, col);
    match kind {
        // casa
        0 => {
            let icon = 0;
            let _ = icon;
            painter.add(egui::Shape::convex_polygon(
                vec![at(0.5, 0.02), at(0.02, 0.46), at(0.5, 0.34), at(0.98, 0.46)],
                Color32::TRANSPARENT,
                st,
            ));
            painter.rect_stroke(
                egui::Rect::from_min_max(at(0.16, 0.5), at(0.84, 0.98)),
                Rounding::same(2.0),
                st,
            );
        }
        // versões: 2x2 blocos
        1 => {
            let a = egui::Rect::from_min_max(at(0.02, 0.06), at(0.46, 0.46));
            let b = egui::Rect::from_min_max(at(0.54, 0.06), at(0.98, 0.46));
            let c = egui::Rect::from_min_max(at(0.02, 0.54), at(0.46, 0.94));
            let d = egui::Rect::from_min_max(at(0.54, 0.54), at(0.98, 0.94));
            painter.rect_stroke(a, Rounding::same(2.5), st);
            painter.rect_stroke(b, Rounding::same(2.5), st);
            painter.rect_stroke(c, Rounding::same(2.5), st);
            painter.rect_filled(d, Rounding::same(2.5), col);
        }
        // mods: quadrado com +
        2 => {
            painter.rect_stroke(
                egui::Rect::from_min_max(at(0.06, 0.06), at(0.94, 0.94)),
                Rounding::same(3.5),
                st,
            );
            painter.line_segment([at(0.5, 0.3), at(0.5, 0.7)], st);
            painter.line_segment([at(0.3, 0.5), at(0.7, 0.5)], st);
        }
        // ajustes: sliders
        _ => {
            for (y, kx) in [(0.22, 0.66), (0.5, 0.34), (0.78, 0.6)] {
                painter.line_segment([at(0.02, y), at(0.98, y)], st);
                painter.circle_filled(at(kx, y), 2.4, col);
            }
        }
    }
}

fn pill_button(ui: &mut egui::Ui, label: &str, on: bool, p: Pal) -> Response {
    let font = FontId::new(11.5, FontFamily::Name("dm".into()));
    let gsize = ui.ctx().fonts(|f| {
        f.layout_no_wrap(label.to_owned(), font.clone(), p.text).size()
    });
    let (rect, resp) = ui
        .allocate_exact_size(Vec2::new(gsize.x + 24.0, 28.0), Sense::click());
    let h = ui
        .ctx()
        .animate_bool_with_time(ui.id().with(("pc", label)), resp.hovered(), 0.13);
    let base = if on { p.surface2 } else { Color32::TRANSPARENT };
    if h > 0.02 || on {
        ui.painter()
            .rect_filled(rect, Rounding::same(9.0), mixc(base, lighten(p.surface2, 7), h));
    }
    if on || h > 0.02 {
        ui.painter().rect_stroke(
            rect,
            Rounding::same(9.0),
            Stroke::new(1.0_f32, mixc(p.bg, p.line, if on { 1.0 } else { h })),
        );
    }
    ui.painter().text(
        rect.center(),
        Align2::CENTER_CENTER,
        label,
        font,
        mixc(p.dim, p.text, if on { 1.0 } else { h }),
    );
    resp
}

fn theme_card(ui: &mut egui::Ui, width: f32, label: &str, id: &str, active: bool, p: Pal) -> Response {
    let (rect, resp) = ui.allocate_exact_size(Vec2::new(width, 66.0), Sense::click());
    let bg = if active { p.surface2 } else { p.surface };
    ui.painter().rect_filled(rect, Rounding::same(10.0), bg);
    ui.painter().rect_stroke(
        rect,
        Rounding::same(10.0),
        Stroke::new(if active { 1.5f32 } else { 1.0f32 }, if active { p.accent } else { p.line }),
    );
    let prev = egui::Rect::from_min_size(
        rect.min + Vec2::new(8.0, 8.0),
        Vec2::new(width - 16.0, 30.0),
    );
    ui.painter()
        .rect_filled(prev, Rounding::same(6.0), Color32::from_rgb(240, 242, 238));
    match id {
        "light" => {}
        "dark" => {
            ui.painter()
                .rect_filled(prev, Rounding::same(6.0), Color32::from_rgb(14, 17, 20));
        }
        _ => {
            let half = egui::Rect::from_min_max(prev.min, egui::pos2(prev.center().x, prev.max.y));
            ui.painter()
                .rect_filled(half, Rounding::same(6.0), Color32::from_rgb(240, 242, 238));
            let right = egui::Rect::from_min_max(egui::pos2(prev.center().x, prev.min.y), prev.max);
            ui.painter()
                .rect_filled(right, Rounding::same(6.0), Color32::from_rgb(14, 17, 20));
        }
    }
    ui.painter().text(
        egui::pos2(rect.center().x, rect.bottom() - 14.0),
        Align2::CENTER_CENTER,
        label,
        FontId::new(10.5, FontFamily::Name("dm".into())),
        if active { p.text } else { p.dim },
    );
    resp
}

fn danger_button(ui: &mut egui::Ui, label: &str, _p: Pal) -> Response {
    let red = Color32::from_rgb(226, 86, 97);
    let font = FontId::new(11.5, FontFamily::Name("dm".into()));
    let gsize = ui.ctx().fonts(|f| {
        f.layout_no_wrap(label.to_owned(), font.clone(), red).size()
    });
    let (rect, resp) = ui
        .allocate_exact_size(Vec2::new(gsize.x + 24.0, 30.0), Sense::click());
    let h = ui
        .ctx()
        .animate_bool_with_time(ui.id().with(("dg", label)), resp.hovered(), 0.13);
    if h > 0.02 {
        ui.painter().rect_filled(
            rect,
            Rounding::same(9.0),
            Color32::from_rgba_unmultiplied(226, 86, 97, (46.0 * h) as u8),
        );
    }
    ui.painter().rect_stroke(
        rect,
        Rounding::same(9.0),
        Stroke::new(
            1.0_f32,
            Color32::from_rgba_unmultiplied(226, 86, 97, (150.0 + 70.0 * h) as u8),
        ),
    );
    ui.painter()
        .text(rect.center(), Align2::CENTER_CENTER, label, font, red);
    resp
}

fn ram_slider(ui: &mut egui::Ui, mb: &mut u32, p: Pal) -> bool {
    let w = ui.available_width().max(160.0);
    let (rect, resp) = ui.allocate_exact_size(Vec2::new(w, 22.0), Sense::click_and_drag());
    let track = egui::Rect::from_center_size(rect.center(), Vec2::new(w, 6.0));
    ui.painter()
        .rect_filled(track, Rounding::same(3.0), p.surface2);
    let min = 1024.0f32;
    let max = 8192.0f32;
    let mut frac = ((*mb as f32 - min) / (max - min)).clamp(0.0, 1.0);
    let mut changed = false;
    if resp.dragged() || resp.clicked() {
        if let Some(pos) = resp.interact_pointer_pos() {
            let f = ((pos.x - track.left()) / track.width()).clamp(0.0, 1.0);
            let v = ((min + f * (max - min)) / 256.0).round() as u32 * 256;
            let v = v.clamp(1024, 8192);
            if v != *mb {
                *mb = v;
                changed = true;
            }
            frac = ((*mb as f32 - min) / (max - min)).clamp(0.0, 1.0);
        }
    }
    let mut fill = track;
    fill.set_width(track.width() * frac);
    ui.painter()
        .rect_filled(fill, Rounding::same(3.0), p.accent);
    let knob = egui::pos2(track.left() + track.width() * frac, track.center().y);
    let hot = resp.hovered() || resp.dragged();
    let r = if hot { 8.5 } else { 7.5 };
    ui.painter().circle_filled(knob, r, Color32::WHITE);
    ui.painter()
        .circle_stroke(knob, r, Stroke::new(2.0_f32, p.accent));
    changed
}

fn chip_button(ui: &mut egui::Ui, label: &str, p: Pal) -> Response {
    let font = FontId::new(11.5, FontFamily::Name("dm".into()));
    let gsize = ui
        .ctx()
        .fonts(|f| f.layout_no_wrap(label.to_owned(), font.clone(), p.text).size());
    let (rect, resp) = ui.allocate_exact_size(
        Vec2::new(gsize.x + 22.0, 30.0f32.max(gsize.y + 13.0)),
        Sense::click(),
    );
    let h = ui
        .ctx()
        .animate_bool_with_time(ui.id().with(("cb", label)), resp.hovered(), 0.13);
    ui.painter().rect_filled(
        rect,
        Rounding::same(9.0),
        mixc(p.surface2, lighten(p.surface2, 7), h),
    );
    ui.painter().rect_stroke(
        rect,
        Rounding::same(9.0),
        Stroke::new(1.0_f32, mixc(p.line, lighten(p.line, 14), h)),
    );
    ui.painter().text(
        rect.center(),
        Align2::CENTER_CENTER,
        label,
        font,
        mixc(p.text, lighten(p.text, 10), h),
    );
    resp
}

fn accent_button(ui: &mut egui::Ui, label: &str, p: Pal) -> Response {
    let font = FontId::new(12.5, FontFamily::Name("dmb".into()));
    let gsize = ui
        .ctx()
        .fonts(|f| f.layout_no_wrap(label.to_owned(), font.clone(), p.accent_ink).size());
    let (rect, resp) = ui.allocate_exact_size(Vec2::new(gsize.x + 30.0, 34.0), Sense::click());
    let h = ui
        .ctx()
        .animate_bool_with_time(ui.id().with(("ab", label)), resp.hovered(), 0.13);
    if h > 0.03 {
        ui.painter().rect_filled(
            rect.translate(Vec2::new(0.0, 2.5)),
            Rounding::same(10.0),
            Color32::from_rgba_unmultiplied(0, 0, 0, (34.0 * h) as u8),
        );
    }
    let body = rect.translate(Vec2::new(0.0, -2.5 * h));
    ui.painter().rect_filled(
        body,
        Rounding::same(10.0),
        mixc(p.accent, lighten(p.accent, 14), h),
    );
    ui.painter()
        .text(body.center(), Align2::CENTER_CENTER, label, font, p.accent_ink);
    resp
}

fn switch(ui: &mut egui::Ui, on: bool, p: Pal) -> Response {
    let (rect, resp) = ui.allocate_exact_size(Vec2::new(36.0, 20.0), Sense::click());
    let t = ui
        .ctx()
        .animate_bool_with_time(ui.id().with("sw"), on, 0.15);
    ui.painter().rect_filled(
        rect,
        Rounding::same(rect.height() / 2.0),
        mixc(p.surface2, p.accent, t),
    );
    let cx = rect.left() + 10.0 + (rect.width() - 20.0) * t;
    ui.painter().circle_filled(
        egui::pos2(cx, rect.center().y),
        6.8 + 0.5 * t,
        mixc(p.dim, Color32::WHITE, t),
    );
    resp
}

fn badge(ui: &mut egui::Ui, label: &str, p: Pal) {
    Frame::none()
        .fill(p.surface2)
        .stroke(Stroke::new(1.0_f32, p.line))
        .rounding(Rounding::same(10.0))
        .inner_margin(Margin::symmetric(8.0, 3.0))
        .show(ui, |ui| {
            text(ui, label, 9.5, p.faint, true);
        });
}

fn window_button(ui: &mut egui::Ui, glyph: char, p: Pal) -> Response {
    let (rect, resp) = ui.allocate_exact_size(Vec2::new(30.0, 26.0), Sense::click());
    let h = ui
        .ctx()
        .animate_bool_with_time(ui.id().with(glyph), resp.hovered(), 0.12);
    if h > 0.02 {
        ui.painter()
            .rect_filled(rect, Rounding::same(8.0), mixc(p.bg, p.surface2, h));
    }
    let c = rect.center();
    let col = mixc(p.dim, p.text, h);
    match glyph {
        'x' => {
            let s = 4.0;
            ui.painter().line_segment(
                [c + Vec2::new(-s, -s), c + Vec2::new(s, s)],
                Stroke::new(1.4_f32, col),
            );
            ui.painter().line_segment(
                [c + Vec2::new(-s, s), c + Vec2::new(s, -s)],
                Stroke::new(1.4_f32, col),
            );
        }
        'o' => {
            ui.painter().rect_stroke(
                egui::Rect::from_center_size(c, Vec2::splat(8.0)),
                Rounding::same(2.0),
                Stroke::new(1.3_f32, col),
            );
        }
        _ => {
            ui.painter().line_segment(
                [c + Vec2::new(-4.5, 0.0), c + Vec2::new(4.5, 0.0)],
                Stroke::new(1.4_f32, col),
            );
        }
    }
    resp
}

fn theme_pill(ui: &mut egui::Ui, light: &mut bool, pal: &mut Pal) {
    let p = *pal;
    let (rect, resp) = ui.allocate_exact_size(Vec2::new(74.0, 28.0), Sense::click());
    ui.painter()
        .rect_filled(rect, Rounding::same(14.0), p.surface2);
    let half = rect.width() / 2.0;
    let tt = ui
        .ctx()
        .animate_bool_with_time(ui.id().with("theme"), *light, 0.20);
    let knob = {
        let kw = half - 4.0;
        let kx = rect.min.x + 2.0 + tt * (rect.width() - kw - 4.0);
        egui::Rect::from_min_size(
            egui::pos2(kx, rect.min.y + 2.0),
            Vec2::new(kw, rect.height() - 4.0),
        )
    };
    ui.painter().rect_filled(knob, Rounding::same(999.0), p.surface);
    let (a, b) = (rect.center().x - half / 2.0, rect.center().x + half / 2.0);
    // lua = círculo com um recorte; sol = círculo com raios
    let moon_col = if *light { p.faint } else { p.text };
    let mc = egui::pos2(a, rect.center().y);
    ui.painter().circle_filled(mc, 4.2, moon_col);
    ui.painter()
        .circle_filled(mc + Vec2::new(2.0, -1.6), 3.4, p.surface2);

    let sun_col = if *light { p.text } else { p.faint };
    let sc = egui::pos2(b, rect.center().y);
    ui.painter().circle_filled(sc, 2.6, sun_col);
    for i in 0..4 {
        let ang = i as f32 * std::f32::consts::FRAC_PI_4 * 1.0;
        let d = Vec2::new(ang.cos(), ang.sin()) * 6.0;
        ui.painter().line_segment(
            [sc + d * 0.72, sc + d],
            Stroke::new(1.2_f32, sun_col),
        );
        let a2 = ang + std::f32::consts::FRAC_PI_2;
        let d2 = Vec2::new(a2.cos(), a2.sin()) * 6.0;
        ui.painter().line_segment(
            [sc + d2 * 0.72, sc + d2],
            Stroke::new(1.2_f32, sun_col),
        );
    }
    if resp.clicked() {
        *light = !*light;
        *pal = palette(*light);
    }
}

fn settings_card(ui: &mut egui::Ui, p: Pal, title: &str, add: impl FnOnce(&mut egui::Ui)) {
    Frame::none()
        .fill(p.surface)
        .rounding(Rounding::same(16.0))
        .stroke(Stroke::new(1.0_f32, p.line))
        .inner_margin(Margin::symmetric(18.0, 16.0))
        .show(ui, |ui| {
            ui.set_min_width(ui.available_width());
            text(ui, title, 13.0, p.name, true);
            ui.add_space(12.0);
            add(ui);
        });
    ui.add_space(12.0);
}

fn mod_result(
    ui: &mut egui::Ui,
    h: &profile::SearchHit,
    installing: Option<&str>,
    installed: bool,
    icon: Option<&egui::TextureHandle>,
    p: Pal,
) -> Option<String> {
    let mut out = None;
    Frame::none()
        .fill(p.surface)
        .rounding(Rounding::same(14.0))
        .stroke(Stroke::new(1.0_f32, p.line))
        .inner_margin(Margin::symmetric(16.0, 12.0))
        .show(ui, |ui| {
            ui.set_min_width(ui.available_width());
            ui.horizontal(|ui| {
                let (r, _) = ui.allocate_exact_size(Vec2::splat(38.0), Sense::hover());
                let seed = h.slug.bytes().fold(7u32, |a, b| a.wrapping_mul(31).wrapping_add(b as u32));
                let (cr, cg, cb) = hsl((seed % 360) as f32, 0.52, 0.42);
                ui.painter()
                    .rect_filled(r, Rounding::same(10.0), Color32::from_rgb(cr, cg, cb));
                let letter = h
                    .title
                    .chars()
                    .next()
                    .map(|c| c.to_uppercase().to_string())
                    .unwrap_or_else(|| "?".into());
                ui.painter().text(
                    r.center(),
                    Align2::CENTER_CENTER,
                    letter,
                    FontId::new(17.0, FontFamily::Name("dmb".into())),
                    Color32::WHITE,
                );
                if let Some(t) = icon {
                    // ícone real do projeto por cima do tile com a inicial
                    ui.painter().rect_filled(r, Rounding::same(10.0), p.surface);
                    ui.put(
                        r,
                        egui::Image::new(t).fit_to_exact_size(r.size()).rounding(Rounding::same(10.0)),
                    );
                }
                ui.add_space(12.0);
                ui.vertical(|ui| {
                    text(ui, &h.title, 13.5, p.name, true);
                    text(
                        ui,
                        &format!(
                            "{} \u{b7} {} downloads",
                            if h.author.is_empty() { "Modrinth" } else { &h.author },
                            pretty_count(h.downloads)
                        ),
                        9.5,
                        p.faint,
                        false,
                    );
                });
                ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                    if installing == Some(h.slug.as_str()) {
                        text(ui, "instalando\u{2026}", 10.0, p.faint, false);
                    } else if installed {
                        text(ui, "instalado", 10.0, p.accent, true);
                    } else if chip_button(ui, "Instalar", p).clicked() {
                        out = Some(h.slug.clone());
                    }
                });
            });
            ui.add_space(8.0);
            ui.horizontal(|ui| {
                ui.add_space(50.0);
                ui.vertical(|ui| {
                    text(ui, &h.description, 10.5, p.dim, false);
                });
            });
        });
    out
}

fn pretty_count(n: u64) -> String {
    if n >= 1_000_000 {
        format!("{:.1} mi", n as f64 / 1_000_000.0)
    } else if n >= 1_000 {
        format!("{:.0} mil", n as f64 / 1_000.0)
    } else {
        format!("{n}")
    }
}

fn chip(ui: &mut egui::Ui, label: &str, p: Pal) -> Response {
    let font = FontId::new(9.5, FontFamily::Name("dm".into()));
    let galley = ui.painter().layout_no_wrap(label.to_string(), font, p.faint);
    let (rect, resp) =
        ui.allocate_exact_size(Vec2::new(galley.size().x + 18.0, 22.0), Sense::hover());
    ui.painter().rect_filled(rect, Rounding::same(11.0), p.surface2);
    ui.painter()
        .rect_stroke(rect, Rounding::same(11.0), Stroke::new(1.0_f32, p.line));
    let pos = rect.center() - galley.size() / 2.0;
    ui.painter().galley(pos, galley, p.faint);
    resp
}

fn hairline(ui: &mut egui::Ui, p: Pal) {
    let (r, _) = ui.allocate_exact_size(Vec2::new(ui.available_width(), 1.0), Sense::hover());
    ui.painter()
        .hline(r.x_range(), r.center().y, Stroke::new(1.0_f32, p.line));
}

fn version_picker(
    ui: &mut egui::Ui,
    current: &str,
    installed: &[Installed],
    p: Pal,
) -> Option<String> {
    let (rect, resp) = ui.allocate_exact_size(Vec2::new(176.0, 46.0), Sense::click());
    let hot = resp.hovered();
    ui.painter()
        .rect_filled(rect, Rounding::same(11.0), p.surface2);
    ui.painter().rect_stroke(
        rect,
        Rounding::same(11.0),
        Stroke::new(1.0_f32, if hot { p.dim } else { p.line }),
    );
    ui.painter().text(
        rect.left_center() + Vec2::new(15.0, 0.0),
        Align2::LEFT_CENTER,
        current,
        FontId::new(12.0, FontFamily::Name("dm".into())),
        p.name,
    );
    let c = rect.right_center() - Vec2::new(17.0, 0.0);
    let cc = if hot { p.text } else { p.dim };
    ui.painter()
        .line_segment([c + Vec2::new(-4.0, -2.5), c], Stroke::new(1.4_f32, cc));
    ui.painter()
        .line_segment([c, c + Vec2::new(4.0, -2.5)], Stroke::new(1.4_f32, cc));

    let id = egui::Id::new("version-pop");
    let mut picked = None;
    if resp.clicked() {
        ui.memory_mut(|m| m.toggle_popup(id));
    }
    if ui.memory(|m| m.is_popup_open(id)) {
        egui::popup_below_widget(
            ui,
            id,
            &resp,
            egui::PopupCloseBehavior::CloseOnClick,
            |ui| {
                ui.set_min_width(150.0);
                if installed.is_empty() {
                    text(ui, "nada instalado", 11.0, p.faint, false);
                }
                for v in installed {
                    if ui
                        .selectable_label(
                            v.name == current,
                            RichText::new(&v.name)
                                .size(12.0)
                                .color(if v.name == current { p.text } else { p.dim })
                                .font(FontId::new(12.0, FontFamily::Name("dm".into()))),
                        )
                        .clicked()
                    {
                        picked = Some(v.name.clone());
                    }
                }
            },
        );
    }
    picked
}

fn pill_outline(ui: &mut egui::Ui, label: &str, p: Pal) -> Response {
    let font = FontId::new(9.0, FontFamily::Name("dmb".into()));
    let galley = ui.painter().layout_no_wrap(label.to_string(), font, p.dim);
    let (rect, resp) =
        ui.allocate_exact_size(Vec2::new(galley.size().x + 24.0, 26.0), Sense::hover());
    ui.painter()
        .rect_stroke(rect, Rounding::same(13.0), Stroke::new(1.0_f32, p.line));
    let pos = rect.center() - galley.size() / 2.0;
    ui.painter().galley(pos, galley, p.dim);
    resp
}

fn pill_dot(ui: &mut egui::Ui, label: &str, on: bool, p: Pal) -> Response {
    let font = FontId::new(9.5, FontFamily::Name("dm".into()));
    let galley = ui.painter().layout_no_wrap(label.to_string(), font, p.faint);
    let (rect, resp) =
        ui.allocate_exact_size(Vec2::new(galley.size().x + 34.0, 26.0), Sense::hover());
    ui.painter()
        .rect_filled(rect, Rounding::same(13.0), p.surface);
    ui.painter()
        .rect_stroke(rect, Rounding::same(13.0), Stroke::new(1.0_f32, p.line));
    ui.painter().circle_filled(
        rect.left_center() + Vec2::new(12.0, 0.0),
        3.4,
        if on { p.accent } else { p.dim },
    );
    let pos = egui::pos2(rect.left() + 21.0, rect.center().y) - Vec2::new(0.0, galley.size().y / 2.0);
    ui.painter().galley(pos, galley, p.dim);
    resp
}

fn lighten(c: Color32, by: u8) -> Color32 {
    Color32::from_rgb(
        c.r().saturating_add(by),
        c.g().saturating_add(by),
        c.b().saturating_add(by),
    )
}

fn play_button(ui: &mut egui::Ui, label: &str, sub: &str, busy: bool, p: Pal) -> Response {
    let (rect, resp) = ui.allocate_exact_size(Vec2::new(252.0, 62.0), Sense::click());
    let id = ui.id().with("play");
    let h = ui
        .ctx()
        .animate_bool_with_time(id, resp.hovered() && !busy, 0.16);
    if h > 0.02 {
        let a = p.accent.to_array();
        ui.painter().rect_filled(
            rect.expand(2.0 * h).translate(Vec2::new(0.0, 3.0)),
            Rounding::same(16.0),
            Color32::from_rgba_unmultiplied(a[0], a[1], a[2], (34.0 * h) as u8),
        );
    }
    let body = if busy {
        rect
    } else {
        rect.translate(Vec2::new(0.0, -3.0 * h))
    };
    let fill = if busy {
        p.surface2
    } else {
        mixc(p.accent, lighten(p.accent, 14), h)
    };
    ui.painter().rect_filled(body, Rounding::same(14.0), fill);
    if !busy && resp.hovered() {
        let sweep = ui.ctx().animate_value_with_time(
            id.with("sweep"),
            if resp.hovered() { 1.0 } else { 0.0 },
            0.60,
        );
        let sa = (sweep * (1.0 - sweep) * 4.0).clamp(0.0, 1.0);
        if sa > 0.02 {
            let x = body.left() - 120.0 + sweep * (body.width() + 240.0);
            let cp = ui.painter().with_clip_rect(body);
            let wa = Color32::from_rgba_unmultiplied(255, 255, 255, (92.0 * sa) as u8);
            let top = body.top() + 6.0;
            let bot = body.bottom() - 6.0;
            cp.add(egui::Shape::convex_polygon(
                vec![
                    egui::pos2(x, top),
                    egui::pos2(x + 34.0, top),
                    egui::pos2(x + 96.0, bot),
                    egui::pos2(x + 62.0, bot),
                ],
                wa,
                Stroke::NONE,
            ));
        }
    }
    if busy {
        // pulso de progresso na base
        let t = (now() * 1.4) % 1.0;
        let cp = ui.painter().with_clip_rect(body);
        let bx = body.left() + t as f32 * body.width();
        cp.line_segment(
            [
                egui::pos2((bx - 18.0).max(body.left() + 14.0), body.bottom() - 2.0),
                egui::pos2(bx, body.bottom() - 2.0),
            ],
            Stroke::new(2.0_f32, Color32::from_rgba_unmultiplied(
                p.line.r(),
                p.line.g(),
                p.line.b(),
                170,
            )),
        );
    }
    let ink = if busy { p.dim } else { p.accent_ink };
    if !busy {
        let tc = egui::pos2(body.right() - 24.0, body.center().y);
        ui.painter().add(egui::Shape::convex_polygon(
            vec![
                tc + Vec2::new(-4.0, -6.0),
                tc + Vec2::new(6.0, 0.0),
                tc + Vec2::new(-4.0, 6.0),
            ],
            ink,
            Stroke::NONE,
        ));
    }
    ui.painter().text(
        egui::pos2(body.left() + 20.0, body.center().y - 8.0),
        Align2::LEFT_CENTER,
        label,
        FontId::new(16.0, FontFamily::Name("dmb".into())),
        ink,
    );
    ui.painter().text(
        egui::pos2(body.left() + 20.0, body.center().y + 10.0),
        Align2::LEFT_CENTER,
        sub,
        FontId::new(9.5, FontFamily::Name("dm".into())),
        if busy {
            p.faint
        } else {
            Color32::from_rgba_unmultiplied(
                p.accent_ink.r(),
                p.accent_ink.g(),
                p.accent_ink.b(),
                165,
            )
        },
    );
    resp
}

fn quick_icon(painter: &egui::Painter, r: egui::Rect, kind: u8, col: Color32) {
    let at = |x: f32, y: f32| r.min + Vec2::new(r.width() * x, r.height() * y);
    let st = Stroke::new(1.4_f32, col);
    match kind {
        // pasta
        0 => {
            painter.add(egui::Shape::convex_polygon(
                vec![
                    at(0.04, 0.28),
                    at(0.38, 0.28),
                    at(0.48, 0.42),
                    at(0.96, 0.42),
                    at(0.96, 0.88),
                    at(0.04, 0.88),
                ],
                Color32::TRANSPARENT,
                st,
            ));
        }
        // documento
        1 => {
            painter.rect_stroke(
                egui::Rect::from_min_max(at(0.16, 0.06), at(0.84, 0.94)),
                Rounding::same(1.5),
                st,
            );
            painter.line_segment([at(0.32, 0.4), at(0.68, 0.4)], st);
            painter.line_segment([at(0.32, 0.58), at(0.68, 0.58)], st);
        }
        // mods
        _ => {
            painter.rect_stroke(
                egui::Rect::from_min_max(at(0.08, 0.08), at(0.92, 0.92)),
                Rounding::same(3.0),
                st,
            );
            painter.line_segment([at(0.5, 0.3), at(0.5, 0.7)], st);
            painter.line_segment([at(0.3, 0.5), at(0.7, 0.5)], st);
        }
    }
}

fn stat(ui: &mut egui::Ui, label: &str, value: &str, p: Pal) {
    text(ui, label, 9.5, p.faint, true);
    ui.add_space(3.0);
    text(ui, value, 15.0, p.text, true);
}

fn account_card(
    ui: &mut egui::Ui,
    acc: Option<&auth::AccountView>,
    skin: Option<&egui::TextureHandle>,
    p: Pal,
) -> Response {
    let (rect, resp) = ui.allocate_exact_size(Vec2::new(ui.available_width(), 48.0), Sense::click());
    let hl = ui
        .ctx()
        .animate_bool_with_time(ui.id().with("sidebar-acc"), resp.hovered(), 0.14);
    ui.painter().rect_filled(
        rect,
        Rounding::same(14.0),
        mixc(p.surface, p.surface2, hl),
    );
    ui.painter().rect_stroke(
        rect,
        Rounding::same(14.0),
        Stroke::new(1.0_f32, mixc(p.line, p.dim, hl * 0.6)),
    );
    let av = egui::Rect::from_center_size(
        rect.left_center() + Vec2::new(24.0, 0.0),
        Vec2::splat(28.0),
    );
    paint_avatar(
        ui,
        av,
        acc.map(|a| a.name.as_str()).unwrap_or("?"),
        skin,
        p,
    );
    ui.painter().text(
        rect.left_center() + Vec2::new(46.0, -6.0),
        Align2::LEFT_CENTER,
        acc.map(|a| a.name.as_str()).unwrap_or("offline"),
        FontId::new(12.0, FontFamily::Name("dmb".into())),
        p.name,
    );
    ui.painter().text(
        rect.left_center() + Vec2::new(46.0, 7.0),
        Align2::LEFT_CENTER,
        if acc.is_some() { "conta original" } else { "sem login" },
        FontId::new(9.5, FontFamily::Name("dm".into())),
        p.faint,
    );
    resp
}

fn ram_meter(ui: &mut egui::Ui, sys: Option<&Value>, p: Pal) {
    let total = sys
        .and_then(|s| s.get("total_mb"))
        .and_then(|v| v.as_u64())
        .unwrap_or(1);
    let avail = sys
        .and_then(|s| s.get("avail_mb"))
        .and_then(|v| v.as_u64())
        .unwrap_or(0);
    let frac = 1.0 - avail.min(total) as f32 / total as f32;
    ui.horizontal(|ui| {
        text(ui, "Memória livre", 10.0, p.faint, false);
        ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
            text(ui, &format!("{:.1} GB", avail as f32 / 1024.0), 10.0, p.dim, true);
        });
    });
    let (rect, _) = ui.allocate_exact_size(Vec2::new(ui.available_width(), 4.0), Sense::hover());
    ui.painter().rect_filled(rect, Rounding::same(2.0), p.surface2);
    let free = ui
        .ctx()
        .animate_value_with_time(egui::Id::new("rambar"), 1.0 - frac, 0.45);
    let mut fill = rect;
    fill.set_width(rect.width() * free);
    ui.painter().rect_filled(fill, Rounding::same(2.0), p.accent);
}

fn group_header(
    ui: &mut egui::Ui,
    fam: &str,
    count: usize,
    installed: usize,
    newest: &str,
    open: bool,
    p: Pal,
) -> Response {
    Frame::none()
        .fill(if open { p.surface2 } else { p.surface })
        .rounding(Rounding::same(14.0))
        .stroke(Stroke::new(1.0_f32, p.line))
        .inner_margin(Margin::symmetric(10.0, 9.0))
        .show(ui, |ui| {
            let resp = ui
                .horizontal(|ui| {
                    let (r, _) = ui.allocate_exact_size(Vec2::new(84.0, 50.0), Sense::hover());
                    paint_cover(ui, r, fam, family_hue(fam));
                    ui.add_space(10.0);
                    ui.vertical(|ui| {
                        ui.horizontal(|ui| {
                            text(ui, fam, 14.0, p.text, true);
                            if fam.ends_with(".x") {
                                badge(ui, "client", p);
                            }
                            text(ui, &format!("{count} versões"), 10.0, p.faint, false);
                        });
                        ui.add_space(2.0);
                        text(
                            ui,
                            &format!(
                                "mais recente {newest}{}",
                                if installed > 0 {
                                    format!(" · {installed} instalada(s)")
                                } else {
                                    String::new()
                                }
                            ),
                            10.0,
                            p.faint,
                            false,
                        );
                    });
                    ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                        let (cr, _) = ui.allocate_exact_size(Vec2::splat(16.0), Sense::hover());
                        let c = cr.center();
                        let pts = if open {
                            vec![
                                c + Vec2::new(-4.5, 2.0),
                                c + Vec2::new(4.5, 2.0),
                                c + Vec2::new(0.0, -3.0),
                            ]
                        } else {
                            vec![
                                c + Vec2::new(-4.5, -2.0),
                                c + Vec2::new(4.5, -2.0),
                                c + Vec2::new(0.0, 3.0),
                            ]
                        };
                        ui.painter()
                            .add(egui::Shape::convex_polygon(pts, p.dim, Stroke::NONE));
                    });
                })
                .response;
            resp.interact(Sense::click())
        })
        .inner
}

enum CardAction {
    Install(String),
    Use(String),
}

fn version_card(
    ui: &mut egui::Ui,
    v: &engine::CatalogEntry,
    selected: &str,
    installing: Option<&str>,
    p: Pal,
) -> Option<CardAction> {
    let is_sel = v.id == selected;
    let busy = installing == Some(v.id.as_str());
    let mut action = None;
    Frame::none()
        .fill(if is_sel { p.accent_soft } else { p.surface })
        .rounding(Rounding::same(12.0))
        .stroke(Stroke::new(1.0_f32, if is_sel { p.accent } else { p.line }))
        .inner_margin(Margin::symmetric(14.0, 11.0))
        .show(ui, |ui| {
            ui.set_min_width(ui.available_width());
            ui.horizontal(|ui| {
                text(ui, &v.id, 13.5, p.text, true);
                if v.client {
                    badge(ui, "Client", p);
                }
                ui.with_layout(Layout::right_to_left(Align::Center), |ui| {
                    if busy {
                        text(ui, "baixando…", 10.5, p.faint, false);
                    } else if v.installed {
                        let label = if is_sel { "Em uso" } else { "Usar" };
                        let r = if is_sel {
                            pill_button(ui, label, true, p)
                        } else {
                            accent_button(ui, label, p)
                        };
                        if r.clicked() && !is_sel {
                            action = Some(CardAction::Use(v.id.clone()));
                        }
                    } else if accent_button(ui, "Baixar", p).clicked() {
                        action = Some(CardAction::Install(v.id.clone()));
                    }
                });
            });
            ui.add_space(3.0);
            text(
                ui,
                &format!("{} · release", v.released.get(0..10).unwrap_or("")),
                10.0,
                p.faint,
                false,
            );
        });
    action
}

/* ==================================================================
 * pintura
 * ================================================================== */

fn paint_iris(painter: &egui::Painter, rect: egui::Rect, color: Color32, rot: f32) {
    // geometria do astucy-symbol.svg: 3 lâminas entre r=96 (fora) e r=46 (dentro)
    let radius = rect.width() / 2.0;
    let c = rect.center();
    for (a, b) in [(258.0f32, 162.0f32), (138.0, 42.0), (18.0, -18.0)] {
        let mut pts: Vec<egui::Pos2> = Vec::with_capacity(80);
        let steps = 40;
        for i in 0..=steps {
            let t = (a + (b - a) * i as f32 / steps as f32).to_radians() + rot;
            pts.push(c + Vec2::new(t.cos(), t.sin()) * radius);
        }
        for i in 0..=steps {
            let t = (b + (a - b) * i as f32 / steps as f32).to_radians() + rot;
            pts.push(c + Vec2::new(t.cos(), t.sin()) * (radius * 0.4792));
        }
        painter.add(egui::Shape::convex_polygon(pts, color, Stroke::NONE));
    }
}

/// Wordmark "ASTUCY" com tracking, devolve a largura usada.
fn wordmark(painter: &egui::Painter, left: f32, center_y: f32, size: f32, color: Color32) -> f32 {
    let font = FontId::new(size, FontFamily::Name("dmb".into()));
    let mut x = left;
    for ch in "ASTUCY".chars() {
        let g = painter.layout_no_wrap(ch.to_string(), font.clone(), color);
        let y = center_y - g.size().y / 2.0;
        painter.galley(egui::pos2(x, y), g.clone(), color);
        x += g.size().x + size * 0.035;
    }
    x - left
}

fn paint_avatar(
    ui: &mut egui::Ui,
    rect: egui::Rect,
    name: &str,
    skin: Option<&egui::TextureHandle>,
    p: Pal,
) {
    ui.painter().rect_filled(
        rect,
        Rounding::same(rect.width() * 0.3),
        p.surface2,
    );
    ui.painter().rect_stroke(
        rect,
        Rounding::same(rect.width() * 0.3),
        Stroke::new(1.0_f32, p.line),
    );
    if let Some(tex) = skin {
        ui.put(
            rect,
            egui::Image::new(tex)
                .fit_to_exact_size(rect.size())
                .rounding(Rounding::same(rect.width() * 0.3)),
        );
        return;
    }
    let initial = name.chars().next().unwrap_or('?').to_uppercase().to_string();
    ui.painter().text(
        rect.center(),
        Align2::CENTER_CENTER,
        initial,
        FontId::new(rect.width() * 0.42, FontFamily::Name("dmb".into())),
        p.dim,
    );
}

fn paint_hero(ui: &mut egui::Ui, rect: egui::Rect, p: Pal) {
    let painter = ui.painter().with_clip_rect(rect.intersect(ui.clip_rect()));
    painter.rect_filled(rect, Rounding::same(16.0), p.surface);
    let light = p.bg.r() > 128;
    let a = p.accent.to_array();
    // respiração suave dos brilhos (só repinta pois o Herder pulse já mantém o Home vivo)
    let b = 0.5 + 0.5 * (now() as f32 * 0.9).sin();
    let b = ease_out_cubic(b);
    radial(
        &painter,
        egui::pos2(rect.right() - 110.0, rect.top() + 40.0),
        300.0 + 16.0 * b,
        Color32::from_rgba_unmultiplied(
            a[0],
            a[1],
            a[2],
            ((if light { 12.0 } else { 22.0 }) * (0.70 + 0.30 * b)) as u8,
        ),
    );
    radial(
        &painter,
        egui::pos2(rect.right() + 40.0, rect.bottom() + 10.0),
        220.0 * (1.0 + 0.04 * b),
        Color32::from_rgba_unmultiplied(
            a[0],
            a[1],
            a[2],
            ((if light { 6.0 } else { 12.0 }) * (0.72 + 0.28 * b)) as u8,
        ),
    );
    painter.rect_stroke(rect, Rounding::same(16.0), Stroke::new(1.0_f32, p.line));
}

/// Círculo com gradiente radial (centro colorido → borda transparente).
fn radial(painter: &egui::Painter, center: egui::Pos2, radius: f32, color: Color32) {
    let steps = 112usize;
    let mut mesh = egui::Mesh::default();
    mesh.vertices.push(egui::epaint::Vertex {
        pos: center,
        uv: egui::epaint::WHITE_UV,
        color,
    });
    for i in 0..=steps {
        let a = i as f32 / steps as f32 * std::f32::consts::TAU;
        mesh.vertices.push(egui::epaint::Vertex {
            pos: center + Vec2::new(a.cos(), a.sin()) * radius,
            uv: egui::epaint::WHITE_UV,
            color: Color32::TRANSPARENT,
        });
    }
    for i in 1..=steps {
        let next = if i == steps { 1 } else { i + 1 };
        mesh.indices
            .extend([0u32, i as u32, next as u32]);
    }
    painter.add(egui::Shape::mesh(mesh));
}

fn paint_cover(ui: &mut egui::Ui, rect: egui::Rect, label: &str, hue: f32) {
    let (r, g, b) = hsl(hue, 0.62, 0.44);
    let (r2, g2, b2) = hsl(hue + 40.0, 0.58, 0.24);
    let painter = ui.painter().with_clip_rect(rect);
    painter.rect_filled(rect, Rounding::same(10.0), Color32::from_rgb(r2, g2, b2));
    painter.circle_filled(
        egui::pos2(rect.right() - 4.0, rect.top() + 8.0),
        rect.height() * 1.05,
        Color32::from_rgb(r, g, b),
    );
    painter.text(
        rect.center() - Vec2::new(0.0, 1.0),
        Align2::CENTER_CENTER,
        label,
        FontId::new(17.0, FontFamily::Name("dmb".into())),
        Color32::WHITE,
    );
}

fn hsl(h: f32, s: f32, l: f32) -> (u8, u8, u8) {
    let c = (1.0 - (2.0 * l - 1.0).abs()) * s;
    let hp = (h.rem_euclid(360.0)) / 60.0;
    let x = c * (1.0 - (hp % 2.0 - 1.0).abs());
    let (r1, g1, b1) = match hp as u32 {
        0 => (c, x, 0.0),
        1 => (x, c, 0.0),
        2 => (0.0, c, x),
        3 => (0.0, x, c),
        4 => (x, 0.0, c),
        _ => (c, 0.0, x),
    };
    let m = l - c / 2.0;
    (
        ((r1 + m) * 255.0) as u8,
        ((g1 + m) * 255.0) as u8,
        ((b1 + m) * 255.0) as u8,
    )
}

/* ==================================================================
 * utilidades
 * ================================================================== */

fn now() -> f64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs_f64())
        .unwrap_or(0.0)
}

fn mc_root() -> PathBuf {
    std::env::var("APPDATA")
        .map(|a| PathBuf::from(a).join(".minecraft"))
        .unwrap_or_else(|_| PathBuf::from(".minecraft"))
}

fn mods_dir() -> PathBuf {
    mc_root().join("astucy-profile").join("mods")
}

fn system_is_light() -> bool {
    let out = std::process::Command::new("reg")
        .args([
            "query",
            r"HKCU\Software\Microsoft\Windows\CurrentVersion\Themes\Personalize",
            "/v",
            "AppsUseLightTheme",
        ])
        .output();
    match out {
        Ok(o) => String::from_utf8_lossy(&o.stdout).contains("0x1"),
        Err(_) => false,
    }
}

fn settings_file() -> PathBuf {
    std::env::var("APPDATA")
        .map(|a| PathBuf::from(a).join("com.astucy.launcher").join("settings.json"))
        .unwrap_or_else(|_| PathBuf::from("settings.json"))
}

fn read_settings(path: &PathBuf) -> Value {
    std::fs::read_to_string(path)
        .ok()
        .and_then(|s| serde_json::from_str(&s).ok())
        .unwrap_or(Value::Null)
}

fn scan_installed() -> Vec<Installed> {
    let mut out = Vec::new();
    if engine::horizon_available() {
        out.push(Installed {
            name: "26.3 (Rust)".to_string(),
            modified: u64::MAX,
        });
    }
    if let Ok(entries) = std::fs::read_dir(mc_root().join("versions")) {
        for e in entries.flatten() {
            if !e.path().is_dir() {
                continue;
            }
            let name = e.file_name().to_string_lossy().to_string();
            if name.starts_with("astucy-fabric-") || name.starts_with("fabric-loader-") {
                continue;
            }
            let modified = e
                .metadata()
                .and_then(|m| m.modified())
                .ok()
                .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
                .map(|d| d.as_secs())
                .unwrap_or(0);
            out.push(Installed { name, modified });
        }
    }
    out.sort_by_key(|v| std::cmp::Reverse(v.modified));
    out
}

fn scan_mods(version: &str) -> Vec<ModFile> {
    let mut out = Vec::new();
    let dir = profile::mods_dir_for(version).unwrap_or_else(|_| mods_dir());
    if let Ok(entries) = std::fs::read_dir(dir) {
        for e in entries.flatten() {
            let file = e.file_name().to_string_lossy().to_string();
            let enabled = file.ends_with(".jar");
            let disabled = file.ends_with(".jar.disabled");
            if !enabled && !disabled {
                continue;
            }
            let bytes = e.metadata().map(|m| m.len()).unwrap_or(0);
            out.push(ModFile { name: file, enabled, bytes });
        }
    }
    out.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
    out
}

fn family_of(id: &str) -> String {
    let mut parts = id.split('.');
    let major: u32 = parts.next().and_then(|s| s.parse().ok()).unwrap_or(0);
    if major >= 26 {
        return format!("{major}.x");
    }
    let minor = parts.next().unwrap_or("0");
    format!("1.{minor}")
}

fn family_hue(fam: &str) -> f32 {
    if fam.ends_with(".x") {
        return 150.0;
    }
    let minor: f32 = fam.split('.').nth(1).and_then(|s| s.parse().ok()).unwrap_or(0.0);
    (184.0 + (21.0 - minor) * 27.0 + 720.0) % 360.0
}

fn install_fonts(ctx: &egui::Context) {
    let mut fonts = egui::FontDefinitions::default();
    fonts.font_data.insert("dm".into(), egui::FontData::from_static(DM_REG));
    fonts.font_data.insert("dmm".into(), egui::FontData::from_static(DM_MED));
    fonts.font_data.insert("dmb".into(), egui::FontData::from_static(DM_BOLD));
    fonts
        .families
        .insert(FontFamily::Name("dm".into()), vec!["dm".into(), "dmm".into()]);
    fonts
        .families
        .insert(FontFamily::Name("dmb".into()), vec!["dmb".into()]);
    // fallback do egui (emoji/símbolos) depois do DM Sans
    let fallback = fonts
        .families
        .get(&FontFamily::Proportional)
        .cloned()
        .unwrap_or_default();
    for fam in ["dm", "dmb"] {
        if let Some(list) = fonts.families.get_mut(&FontFamily::Name(fam.into())) {
            list.extend(fallback.iter().cloned());
        }
    }
    if let Some(list) = fonts.families.get_mut(&FontFamily::Proportional) {
        list.insert(0, "dm".into());
    }
    ctx.set_fonts(fonts);
}

fn apply_visuals(ctx: &egui::Context, p: Pal) {
    let mut v = if p.bg == palette(false).bg {
        egui::Visuals::dark()
    } else {
        egui::Visuals::light()
    };
    v.panel_fill = p.bg;
    v.window_fill = p.surface;
    v.extreme_bg_color = p.surface2;
    v.faint_bg_color = p.surface2;
    v.override_text_color = Some(p.text);
    // sem contorno em texto/widgets: o visual é por cartão, não por caixinha
    v.widgets.noninteractive.bg_stroke = Stroke::NONE;
    v.widgets.inactive.bg_stroke = Stroke::NONE;
    v.widgets.hovered.bg_stroke = Stroke::NONE;
    v.widgets.active.bg_stroke = Stroke::NONE;
    v.widgets.inactive.bg_fill = p.surface2;
    v.widgets.inactive.weak_bg_fill = p.surface2;
    v.widgets.inactive.fg_stroke = Stroke::new(1.0_f32, p.dim);
    v.widgets.hovered.bg_fill = p.surface2;
    v.widgets.active.bg_fill = p.surface2;
    v.slider_trailing_fill = true;
    v.selection.bg_fill = p.accent_soft;
    v.selection.stroke = Stroke::new(1.0_f32, p.accent);
    v.widgets.noninteractive.rounding = Rounding::same(10.0);
    v.widgets.inactive.rounding = Rounding::same(10.0);
    v.widgets.hovered.rounding = Rounding::same(10.0);
    v.widgets.active.rounding = Rounding::same(10.0);
    v.widgets.hovered.bg_fill = p.surface2;
    v.widgets.hovered.weak_bg_fill = p.surface2;
    let sh = egui::epaint::Shadow {
        offset: egui::vec2(0.0, 10.0),
        blur: 26.0,
        spread: 0.0,
        color: Color32::from_black_alpha(70),
    };
    v.popup_shadow = sh;
    v.window_shadow = sh;
    v.window_rounding = Rounding::same(14.0);
    v.menu_rounding = Rounding::same(10.0);
    // texto padrão de TODOS os widgets em DM Sans com métricas consistentes
    // (TextEdit usaria Monospace/Proggy por padrão; Button/Body em 14px estouram)
    ctx.style_mut(|style| {
        use egui::TextStyle::*;
        let f = |sz: f32| FontId::new(sz, FontFamily::Name("dm".into()));
        let fb = |sz: f32| FontId::new(sz, FontFamily::Name("dmb".into()));
        style.text_styles = [
            (Heading, fb(24.0)),
            (Body, f(12.0)),
            (Button, f(12.0)),
            (Small, f(9.5)),
            (Monospace, f(12.0)),
        ]
        .into();
    });
    v.window_shadow = egui::epaint::Shadow {
        offset: egui::vec2(0.0, 10.0),
        blur: 30.0,
        spread: 0.0,
        color: p.shadow,
    };
    ctx.set_visuals(v);
}

/* ==================================================================
 * toast
 * ================================================================== */

impl App {
    fn toast_layer(&mut self, ctx: &egui::Context) {
        let Some((msg, error, at)) = self.toast.clone() else {
            return;
        };
        let age = now() - at;
        let e_in = ease_out_cubic(((age / 0.34) as f32).clamp(0.0, 1.0));
        let e_out = (((4.0 - age) / 0.55) as f32).clamp(0.0, 1.0);
        let vis = e_in * e_out;
        if vis <= 0.02 {
            return;
        }
        let p = self.pal;
        egui::Area::new(egui::Id::new("toast"))
            .anchor(Align2::CENTER_BOTTOM, [0.0, -24.0 + (1.0 - e_in) * 34.0])
            .show(ctx, |ui| {
                ui.set_opacity(vis);
                let dot_col = if error {
                    Color32::from_rgb(232, 92, 102)
                } else {
                    p.accent
                };
                Frame::none()
                    .fill(if error {
                        Color32::from_rgb(48, 22, 26)
                    } else {
                        p.surface2
                    })
                    .rounding(Rounding::same(12.0))
                    .stroke(Stroke::new(
                        1.0_f32,
                        if error {
                            Color32::from_rgba_unmultiplied(232, 92, 102, 110)
                        } else {
                            p.line
                        },
                    ))
                    .shadow(egui::epaint::Shadow {
                        offset: egui::vec2(0.0, 6.0),
                        blur: 18.0,
                        spread: 0.0,
                        color: p.shadow,
                    })
                    .inner_margin(Margin::symmetric(14.0, 9.0))
                    .show(ui, |ui| {
                        ui.horizontal(|ui| {
                            let (dr, _) = ui.allocate_exact_size(egui::vec2(7.0, 7.0), Sense::hover());
                            ui.painter().circle_filled(dr.center(), 3.2, dot_col);
                            ui.add_space(6.0);
                            text(
                                ui,
                                &msg,
                                11.5,
                                if error { Color32::WHITE } else { p.text },
                                false,
                            );
                        });
                    });
            });
    }
}

/* ==================================================================
 * main
 * ================================================================== */

fn main() -> eframe::Result<()> {
    let options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default()
            .with_inner_size([1216.0, 718.0])
            .with_min_inner_size([980.0, 620.0])
            .with_decorations(false)
            .with_title("Astucy Launcher")
            .with_icon(app_icon()),
        centered: true,
        ..Default::default()
    };
    eframe::run_native(
        "Astucy Launcher",
        options,
        Box::new(|cc| Ok(Box::new(App::new(cc)))),
    )
}

/// Icone da marca nas janelas do Windows (também taskbar). Asset do kit de
/// branding decodificado em tempo de compilação — nada external em runtime.
fn app_icon() -> egui::IconData {
    let bytes = include_bytes!("../../../../branding/kit/astucy-app-icon-512.png");
    match decode_png(bytes) {
        Some(img) => {
            let (w, h) = (img.size[0] as u32, img.size[1] as u32);
            let mut rgba = Vec::with_capacity(img.pixels.len() * 4);
            for c in &img.pixels {
                let [r, g, b, a] = c.to_array();
                rgba.extend_from_slice(&[r, g, b, a]);
            }
            egui::IconData { width: w, height: h, rgba }
        }
        None => egui::IconData::default(),
    }
}
