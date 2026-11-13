//! Astucy Client native Rust suite for Horizon 26.3 (1:1 port of Java `com.astucy.client`).

use super::{Game, Screen};
use horizon_client::{
    hud::{HudBatch, rgba},
    menu_style::hud_colour as hex,
    ui_theme::{Face, Ui, draw_astucy_logo, draw_astucy_logo_travel},
};
use serde::{Deserialize, Serialize};
use std::{
    collections::{HashMap, VecDeque},
    time::Instant,
};
use winit::keyboard::KeyCode;

pub const SWATCHES: [u32; 12] = [
    0xECEFF3, 0xC9CFD6, 0x878E96, 0x12161B,
    0xFF5D5D, 0xFFB454, 0xFFE066, 0x6FE3A2,
    0x5AD6E8, 0x6BA8FF, 0xB48FFF, 0xFF8FD6,
];

#[derive(Clone, Copy, PartialEq, Eq, Debug, Serialize, Deserialize)]
pub enum ModCat {
    Info,
    Combat,
    Visuals,
    Utility,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct AstucyMod {
    pub id: &'static str,
    pub name: &'static str,
    pub desc: &'static str,
    pub cat: ModCat,
    pub is_new: bool,
    pub has_hud: bool,
    pub enabled: bool,
    pub anchor: [f32; 2],
    pub scale: f32,
    pub bg: bool,
    pub bg_opacity: f32,
    pub bg_rgb: u32,
    pub fg_rgb: u32,
    pub shadow: bool,
    pub show_prefix: bool,
    pub level: f32,
}

impl AstucyMod {
    const fn new(
        id: &'static str,
        name: &'static str,
        desc: &'static str,
        cat: ModCat,
        has_hud: bool,
        is_new: bool,
        anchor: [f32; 2],
    ) -> Self {
        Self {
            id,
            name,
            desc,
            cat,
            is_new,
            has_hud,
            enabled: false,
            anchor,
            scale: 1.0,
            bg: true,
            bg_opacity: 0.60,
            bg_rgb: 0x000000,
            fg_rgb: 0xECEFF3,
            shadow: false,
            show_prefix: true,
            level: 0.40,
        }
    }

    pub fn reset_style(&mut self) {
        self.scale = 1.0;
        self.bg = true;
        self.bg_opacity = 0.60;
        self.bg_rgb = 0x000000;
        self.fg_rgb = 0xECEFF3;
        self.shadow = false;
        self.show_prefix = true;
        self.level = 0.40;
    }
}

#[derive(Clone, Debug, Serialize, Deserialize)]
struct SavedModState {
    id: String,
    enabled: bool,
    anchor: [f32; 2],
    scale: f32,
    #[serde(default = "default_true")]
    bg: bool,
    bg_opacity: f32,
    #[serde(default)]
    bg_rgb: u32,
    #[serde(default = "default_fg")]
    fg_rgb: u32,
    #[serde(default)]
    shadow: bool,
    show_prefix: bool,
    level: f32,
}

fn default_true() -> bool { true }
fn default_fg() -> u32 { 0xECEFF3 }
fn default_crosshair_pixels() -> [u32; 17] {
    let mut g = [0u32; 17];
    for i in 2..=14 {
        g[i] |= 1 << 8;
        g[8] |= 1 << i;
    }
    g
}

fn crosshair_preset(kind: u8) -> [u32; 17] {
    let mut g = [0u32; 17];
    match kind {
        0 => return default_crosshair_pixels(), // Cross
        1 => g[8] = 1 << 8,                     // Dot
        2 => {
            // Plus + 1px gap + center dot
            for i in [2, 3, 4, 5, 8, 11, 12, 13, 14] {
                g[i] |= 1 << 8;
                g[8] |= 1 << i;
            }
        }
        3 => {
            // Diagonal X
            for d in 2..=5_usize {
                g[8 - d] |= (1 << (8 - d)) | (1 << (8 + d));
                g[8 + d] |= (1 << (8 - d)) | (1 << (8 + d));
            }
            g[8] |= 1 << 8;
        }
        _ => {} // Clear
    }
    g
}

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
struct SavedConfig {
    version: u8,
    active_profile: usize,
    profiles: [Vec<SavedModState>; 3],
    #[serde(default = "default_crosshair_pixels")]
    crosshair_pixels: [u32; 17],
    #[serde(default = "default_true")]
    crosshair_invert: bool,
    #[serde(default)]
    light_mode: bool,
}

pub(super) struct AstucyState {
    pub initialized: bool,
    pub active_profile: usize,
    pub profiles: [Vec<AstucyMod>; 3],
    pub mods: Vec<AstucyMod>,
    pub in_menu: bool,
    pub menu_opened_at: Instant,
    pub cat_opened_at: Instant,
    pub world_joined_at: Option<Instant>,
    pub was_in_world: bool,
    pub tab: u8,
    pub cat_filter: u8,
    pub search: String,
    pub search_focused: bool,
    pub scroll: f32,
    pub scroll_target: f32,
    pub editing_mod: Option<usize>,
    pub hud_boxes: Vec<(usize, [f32; 4])>,
    pub selected_hud: Option<usize>,
    pub dragging_hud: Option<(usize, f32, f32)>,
    pub fps_history: VecDeque<u32>,
    pub last_fps_sample: Instant,
    pub high_speed_factor: f32,
    pub move_rush_factor: f32,
    pub last_yaw: f32,
    pub last_pitch: f32,
    pub last_frame_at: Option<Instant>,
    pub last_ui_at: Option<Instant>,
    pub zoom_factor: f32,
    pub saved_flash_at: Option<Instant>,
    pub anims: HashMap<&'static str, f32>,
    pub card_hov: [f32; 48],
    pub card_on: [f32; 48],
    pub card_flash: [Option<Instant>; 48],
    pub key_anim: [f32; 7],
    pub crosshair_pixels: [u32; 17],
    pub crosshair_invert: bool,
    pub crosshair_paint: Option<bool>,
    pub light_mode: bool,
}

impl Default for AstucyState {
    fn default() -> Self {
        let default_mods = build_default_modules();
        let mut card_on = [0.0; 48];
        for (i, m) in default_mods.iter().enumerate().take(48) {
            card_on[i] = if m.enabled { 1.0 } else { 0.0 };
        }
        let now = Instant::now();
        Self {
            initialized: false,
            active_profile: 0,
            profiles: [default_mods.clone(), default_mods.clone(), default_mods.clone()],
            mods: default_mods,
            in_menu: true,
            menu_opened_at: now,
            cat_opened_at: now,
            world_joined_at: None,
            was_in_world: false,
            tab: 0,
            cat_filter: 0,
            search: String::new(),
            search_focused: false,
            scroll: 0.0,
            scroll_target: 0.0,
            editing_mod: None,
            hud_boxes: Vec::new(),
            selected_hud: None,
            dragging_hud: None,
            fps_history: VecDeque::with_capacity(96),
            last_fps_sample: now,
            high_speed_factor: 0.0,
            move_rush_factor: 0.0,
            last_yaw: f32::NAN,
            last_pitch: f32::NAN,
            last_frame_at: None,
            last_ui_at: None,
            zoom_factor: 1.0,
            saved_flash_at: None,
            anims: HashMap::new(),
            card_hov: [0.0; 48],
            card_on,
            card_flash: [None; 48],
            key_anim: [0.0; 7],
            crosshair_pixels: default_crosshair_pixels(),
            crosshair_invert: true,
            crosshair_paint: None,
            light_mode: false,
        }
    }
}

/// Exact 41 modules & default positions/states from `com.astucy.client.module.Modules.registerAll()`.
fn build_default_modules() -> Vec<AstucyMod> {
    use ModCat::*;
    let mut list = vec![
        // Informação (HUD)
        AstucyMod::new("fps", "FPS", "Frames per second", Info, true, false, [0.012, 0.020]),
        AstucyMod::new("ping", "Ping", "Server latency", Info, true, false, [0.012, 0.060]),
        AstucyMod::new("server", "Server", "Server IP address", Info, true, false, [0.012, 0.098]),
        AstucyMod::new("memory", "Memory", "Game RAM usage", Info, true, false, [0.012, 0.136]),
        AstucyMod::new("speed", "Speed Display", "Blocks per second", Info, true, false, [0.012, 0.174]),
        AstucyMod::new("direcao", "Direction", "Facing (N/E/S/W)", Info, true, false, [0.500, 0.020]),
        AstucyMod::new("sessao", "Session", "Session playtime", Info, true, false, [0.012, 0.250]),
        AstucyMod::new("hora", "Time", "Server world time", Info, true, false, [0.845, 0.135]),
        AstucyMod::new("bioma", "Biome", "Current biome", Info, true, false, [0.845, 0.173]),
        AstucyMod::new("coords", "Coords", "X, Y, Z + facing", Info, true, false, [0.845, 0.020]),
        AstucyMod::new("efeitos", "Effects", "Active potions", Info, true, false, [0.845, 0.211]),
        // Combate (HUD)
        AstucyMod::new("cps", "CPS Display", "Clicks per second", Combat, true, false, [0.012, 0.880]),
        AstucyMod::new("keystrokes", "Keystrokes", "WASD + mouse keys", Combat, true, false, [0.012, 0.620]),
        AstucyMod::new("reach", "Reach Display", "Target distance", Combat, true, false, [0.845, 0.880]),
        AstucyMod::new("totem", "Totems", "Totem counter", Combat, true, false, [0.845, 0.940]),
        AstucyMod::new("armor", "Armor", "Armor durability", Combat, true, false, [0.845, 0.835]),
        // Visão (toggle)
        AstucyMod::new("fullbright", "Fullbright", "Max brightness", Visuals, false, false, [0.5, 0.5]),
        AstucyMod::new("zoom", "Zoom", "Smooth hold zoom", Visuals, false, true, [0.5, 0.5]),
        AstucyMod::new("hurtcam", "No Hurt Cam", "No damage shake", Visuals, false, true, [0.5, 0.5]),
        // Utilidade
        AstucyMod::new("sprint", "Auto Sprint", "Always sprint", Utility, true, false, [0.012, 0.300]),
        AstucyMod::new("sneak", "Toggle Sneak", "Tap sneak toggle", Utility, false, true, [0.5, 0.5]),
        AstucyMod::new("chat", "Chat", "History & /chs", Utility, false, false, [0.5, 0.5]),
        // Informação / Combate extra
        AstucyMod::new("itens", "Held Item", "Held item count", Info, true, true, [0.012, 0.338]),
        AstucyMod::new("health", "Health", "HP + absorption", Combat, true, true, [0.012, 0.414]),
        AstucyMod::new("hunger", "Hunger", "Food + saturation", Combat, true, true, [0.012, 0.452]),
        AstucyMod::new("combo", "Combo Counter", "Consecutive hits", Combat, true, true, [0.012, 0.490]),
        AstucyMod::new("healthtag", "Player Health", "Overhead HP tag", Combat, false, true, [0.5, 0.5]),
        AstucyMod::new("pack", "Pack", "Active pack name", Info, true, true, [0.012, 0.376]),
        AstucyMod::new("target", "Target HUD", "Aimed target HUD", Combat, true, true, [0.845, 0.280]),
        AstucyMod::new("armordur", "Armor Durability", "Piece durability", Combat, true, true, [0.845, 0.340]),
        AstucyMod::new("daycounter", "Day Counter", "World day number", Info, true, true, [0.012, 0.528]),
        AstucyMod::new("itemcount", "Item Counter", "Stack & durability", Info, true, true, [0.012, 0.566]),
        // Visuals (comportamento & efeitos)
        AstucyMod::new("viewmodel", "Viewmodel", "Hand pose & scale", Visuals, false, true, [0.5, 0.5]),
        AstucyMod::new("blur", "Motion Blur", "Camera rotation blur", Visuals, false, true, [0.5, 0.5]),
        AstucyMod::new("menublur", "Menu Glass FX", "Glass & spotlight", Visuals, false, true, [0.5, 0.5]),
        AstucyMod::new("crosshair", "Crosshair", "Custom crosshair", Visuals, false, true, [0.5, 0.5]),
        // Addons opcionais (Launcher / Rust)
        AstucyMod::new("modmenu", "Mod Menu", "In-game mods & config", Utility, false, true, [0.5, 0.5]),
        AstucyMod::new("mousetweaks", "Mouse Tweaks", "Fast drag & wheel move", Utility, false, true, [0.5, 0.5]),
        AstucyMod::new("itemrespawn", "Custom Item Respawn", "Fast item & totem swap", Combat, false, true, [0.5, 0.5]),
        // Extras de HUD
        AstucyMod::new("watermark", "Watermark", "Client watermark", Info, true, true, [0.500, 0.955]),
        AstucyMod::new("fpsgraph", "FPS Graph", "FPS sparkline", Info, true, true, [0.845, 0.400]),
        AstucyMod::new("realtime", "Real Time", "Local time clock", Info, true, true, [0.845, 0.560]),
    ];

    // Preset padrão: apenas Auto Sprint, FPS, Ping e CPS ativos
    for id in ["sprint", "fps", "ping", "cps"] {
        if let Some(m) = list.iter_mut().find(|m| m.id == id) {
            m.enabled = true;
        }
    }
    list
}

impl AstucyState {
    pub fn find(&self, id: &str) -> Option<&AstucyMod> {
        self.mods.iter().find(|m| m.id == id)
    }

    pub fn is_on(&self, id: &str) -> bool {
        self.find(id).is_some_and(|m| m.enabled)
    }

    /// Exponential easing channel matching `RShiftScreen.java:1616` (`cur += (target - cur) * (1 - exp(-speed * dt))`).
    pub fn anim(&mut self, key: &'static str, target: f32, speed: f32, dt: f32) -> f32 {
        let cur = *self.anims.entry(key).or_insert(target);
        let k = 1.0 - (-speed * dt.clamp(0.001, 0.08)).exp();
        let next = cur + (target - cur) * k;
        let snapped = if (next - target).abs() < 0.002 { target } else { next };
        self.anims.insert(key, snapped);
        snapped
    }

    fn export_profile(mods: &[AstucyMod]) -> Vec<SavedModState> {
        mods.iter()
            .map(|m| SavedModState {
                id: m.id.to_owned(),
                enabled: m.enabled,
                anchor: m.anchor,
                scale: m.scale,
                bg: m.bg,
                bg_opacity: m.bg_opacity,
                bg_rgb: m.bg_rgb,
                fg_rgb: m.fg_rgb,
                shadow: m.shadow,
                show_prefix: m.show_prefix,
                level: m.level,
            })
            .collect()
    }

    fn apply_saved(target: &mut [AstucyMod], saved: &[SavedModState]) {
        for s in saved {
            if let Some(m) = target.iter_mut().find(|m| m.id == s.id) {
                m.enabled = s.enabled;
                m.anchor = [s.anchor[0].clamp(0.0, 0.98), s.anchor[1].clamp(0.0, 0.98)];
                m.scale = s.scale.clamp(0.6, 2.0);
                m.bg = s.bg;
                m.bg_opacity = s.bg_opacity.clamp(0.0, 1.0);
                m.bg_rgb = s.bg_rgb & 0xFFFFFF;
                m.fg_rgb = if s.fg_rgb == 0 { 0xECEFF3 } else { s.fg_rgb & 0xFFFFFF };
                m.shadow = s.shadow;
                m.show_prefix = s.show_prefix;
                m.level = s.level.clamp(0.05, 1.0);
            }
        }
    }
}

/// Clean, sharp vector icon renderer (48x48 design canvas).
fn draw_skin_icon(ui: &mut Ui<'_>, id: &str, x: f32, y: f32, size: f32, col: u32) {
    let s = size / 48.0;
    let k_to_gui = ui.density / ui.batch.scale;
    let bx = |ux: f32, uy: f32, uw: f32, uh: f32, ur: f32| -> (f32, f32, f32, f32, f32) {
        (x + ux * s, y + uy * s, (uw * s).max(1.3), (uh * s).max(1.3), ur * s)
    };
    let cap = |ui: &mut Ui<'_>, ux: f32, uy: f32, uw: f32, uh: f32, ur: f32| {
        let (rx, ry, rw, rh, rr) = bx(ux, uy, uw, uh, ur);
        ui.rounded(rx, ry, rw, rh, rr, col);
    };
    let rbox = |ui: &mut Ui<'_>, ux: f32, uy: f32, uw: f32, uh: f32, ur: f32| {
        let (rx, ry, rw, rh, rr) = bx(ux, uy, uw, uh, ur);
        ui.border(rx, ry, rw, rh, rr, col);
    };
    let dot = |ui: &mut Ui<'_>, cx: f32, cy: f32, r: f32| {
        cap(ui, cx - r, cy - r, r * 2.0, r * 2.0, r);
    };
    let tri = |ui: &mut Ui<'_>, p0: [f32; 2], p1: [f32; 2], p2: [f32; 2]| {
        let g = |p: [f32; 2]| [(x + p[0] * s) * k_to_gui, (y + p[1] * s) * k_to_gui];
        ui.batch.fill_triangle(ui.atlas, [g(p0), g(p1), g(p2)], col);
    };
    match id {
        "fps" | "fpsgraph" => {
            cap(ui, 8.0, 26.0, 7.0, 13.0, 2.0);
            cap(ui, 20.5, 19.0, 7.0, 20.0, 2.0);
            cap(ui, 33.0, 10.0, 7.0, 29.0, 2.0);
        }
        "ping" => {
            cap(ui, 10.0, 29.0, 6.0, 10.0, 2.0);
            cap(ui, 21.0, 21.0, 6.0, 18.0, 2.0);
            cap(ui, 32.0, 12.0, 6.0, 27.0, 2.0);
        }
        "server" => {
            rbox(ui, 7.0, 10.0, 34.0, 12.0, 4.0);
            rbox(ui, 7.0, 26.0, 34.0, 12.0, 4.0);
            dot(ui, 14.0, 16.0, 2.6);
            dot(ui, 14.0, 32.0, 2.6);
        }
        "memory" => {
            rbox(ui, 11.0, 11.0, 26.0, 26.0, 5.0);
            cap(ui, 18.0, 18.0, 12.0, 12.0, 2.5);
        }
        "speed" | "sprint" => {
            tri(ui, [10.0, 13.0], [23.0, 24.0], [10.0, 35.0]);
            tri(ui, [24.0, 13.0], [37.0, 24.0], [24.0, 35.0]);
        }
        "coords" => {
            rbox(ui, 12.0, 8.0, 24.0, 24.0, 12.0);
            dot(ui, 24.0, 20.0, 4.2);
            tri(ui, [15.0, 26.0], [33.0, 26.0], [24.0, 41.0]);
        }
        "reach" => {
            cap(ui, 8.0, 22.0, 26.0, 4.0, 2.0);
            tri(ui, [30.0, 15.0], [41.0, 24.0], [30.0, 33.0]);
        }
        "crosshair" => {
            cap(ui, 22.0, 6.0, 4.0, 11.0, 2.0);
            cap(ui, 22.0, 31.0, 4.0, 11.0, 2.0);
            cap(ui, 6.0, 22.0, 11.0, 4.0, 2.0);
            cap(ui, 31.0, 22.0, 11.0, 4.0, 2.0);
            dot(ui, 24.0, 24.0, 2.8);
        }
        "cps" => {
            rbox(ui, 13.0, 7.0, 22.0, 34.0, 10.0);
            cap(ui, 22.2, 12.0, 3.6, 9.0, 1.8);
        }
        "keystrokes" => {
            cap(ui, 18.5, 9.0, 11.0, 11.0, 3.0);
            cap(ui, 6.0, 25.0, 11.0, 11.0, 3.0);
            cap(ui, 18.5, 25.0, 11.0, 11.0, 3.0);
            cap(ui, 31.0, 25.0, 11.0, 11.0, 3.0);
        }
        "totem" | "itemrespawn" => {
            dot(ui, 24.0, 13.0, 5.0);
            cap(ui, 10.0, 20.0, 28.0, 5.5, 2.5);
            cap(ui, 18.0, 20.0, 12.0, 19.0, 4.0);
        }
        "mousetweaks" => {
            rbox(ui, 13.0, 7.0, 22.0, 34.0, 10.0);
            cap(ui, 22.2, 12.0, 3.6, 9.0, 1.8);
            tri(ui, [34.0, 18.0], [42.0, 24.0], [34.0, 30.0]);
        }
        "modmenu" => {
            cap(ui, 10.0, 12.0, 28.0, 5.0, 2.5);
            cap(ui, 10.0, 21.5, 28.0, 5.0, 2.5);
            cap(ui, 10.0, 31.0, 28.0, 5.0, 2.5);
        }
        "moon" => {
            dot(ui, 24.0, 11.5, 4.2);
            dot(ui, 17.5, 16.5, 5.2);
            dot(ui, 15.5, 24.0, 5.5);
            dot(ui, 17.5, 31.5, 5.2);
            dot(ui, 24.0, 36.5, 4.2);
            dot(ui, 31.5, 35.5, 3.0);
            dot(ui, 31.5, 12.5, 3.0);
        }
        "healthtag" => {
            cap(ui, 21.0, 10.0, 6.0, 28.0, 2.5);
            cap(ui, 10.0, 21.0, 28.0, 6.0, 2.5);
        }
        "health" => {
            dot(ui, 17.0, 18.0, 7.5);
            dot(ui, 31.0, 18.0, 7.5);
            tri(ui, [10.0, 21.0], [38.0, 21.0], [24.0, 39.0]);
        }
        "hunger" => {
            dot(ui, 18.0, 28.0, 9.0);
            cap(ui, 22.0, 15.0, 14.0, 5.5, 2.5);
        }
        "combo" => {
            tri(ui, [26.0, 7.0], [13.0, 25.0], [25.0, 25.0]);
            tri(ui, [23.0, 23.0], [35.0, 23.0], [22.0, 41.0]);
        }
        "armor" | "armordur" => {
            rbox(ui, 11.0, 8.0, 26.0, 24.0, 6.0);
            tri(ui, [12.0, 28.0], [36.0, 28.0], [24.0, 40.0]);
        }
        "hora" | "realtime" => {
            rbox(ui, 8.0, 8.0, 32.0, 32.0, 16.0);
            cap(ui, 22.2, 13.0, 3.6, 12.5, 1.8);
            cap(ui, 22.2, 22.5, 10.5, 3.6, 1.8);
        }
        "bioma" => {
            tri(ui, [8.0, 36.0], [22.0, 12.0], [34.0, 36.0]);
            tri(ui, [22.0, 36.0], [32.0, 19.0], [41.0, 36.0]);
        }
        "direcao" => {
            tri(ui, [24.0, 8.0], [16.0, 24.0], [32.0, 24.0]);
            tri(ui, [16.0, 24.0], [32.0, 24.0], [24.0, 40.0]);
        }
        "target" => {
            rbox(ui, 8.0, 8.0, 32.0, 32.0, 16.0);
            dot(ui, 24.0, 24.0, 5.5);
        }
        "sessao" => {
            rbox(ui, 8.0, 8.0, 32.0, 32.0, 16.0);
            tri(ui, [19.0, 15.0], [33.0, 24.0], [19.0, 33.0]);
        }
        "efeitos" => {
            cap(ui, 19.0, 7.0, 10.0, 6.0, 2.0);
            rbox(ui, 12.0, 14.0, 24.0, 26.0, 8.0);
            cap(ui, 15.0, 26.0, 18.0, 10.0, 4.0);
        }
        "fullbright" | "daycounter" => {
            dot(ui, 24.0, 24.0, 8.5);
            cap(ui, 22.0, 5.0, 4.0, 7.0, 2.0);
            cap(ui, 22.0, 36.0, 4.0, 7.0, 2.0);
            cap(ui, 5.0, 22.0, 7.0, 4.0, 2.0);
            cap(ui, 36.0, 22.0, 7.0, 4.0, 2.0);
        }
        "zoom" | "search" => {
            rbox(ui, 8.0, 8.0, 23.0, 23.0, 11.5);
            tri(ui, [27.0, 30.0], [30.0, 27.0], [40.0, 37.0]);
            tri(ui, [27.0, 30.0], [40.0, 37.0], [37.0, 40.0]);
        }
        "options" => {
            cap(ui, 8.0, 12.5, 32.0, 3.2, 1.5);
            cap(ui, 8.0, 22.5, 32.0, 3.2, 1.5);
            cap(ui, 8.0, 32.5, 32.0, 3.2, 1.5);
            dot(ui, 16.0, 14.1, 4.5);
            dot(ui, 31.0, 24.1, 4.5);
            dot(ui, 21.0, 34.1, 4.5);
        }
        "watermark" => {
            rbox(ui, 7.0, 14.0, 34.0, 20.0, 10.0);
            dot(ui, 16.0, 24.0, 4.2);
        }
        "pack" | "itemcount" | "itens" => {
            cap(ui, 10.0, 11.0, 28.0, 6.5, 3.0);
            cap(ui, 10.0, 21.0, 28.0, 6.5, 3.0);
            cap(ui, 10.0, 31.0, 28.0, 6.5, 3.0);
        }
        _ => {
            rbox(ui, 10.0, 10.0, 28.0, 28.0, 7.0);
            dot(ui, 24.0, 24.0, 4.5);
        }
    }
}

/// Draws a `Ui.toggle` pill switch with smooth knob slide (`on_t: 0.0..1.0`) matching `Ui.java:231-249`.
fn draw_toggle(ui: &mut Ui<'_>, x: f32, y: f32, w: f32, h: f32, on_t: f32, hov: bool) {
    let t = on_t.clamp(0.0, 1.0);
    let r = (h * 0.5).max(2.0);
    let bg_r = (6.0 + (34.0 - 6.0) * t).round() as u8;
    let bg_g = (8.0 + (197.0 - 8.0) * t).round() as u8;
    let bg_b = (12.0 + (94.0 - 12.0) * t).round() as u8;
    ui.rounded(x, y, w, h, r, rgba(bg_r, bg_g, bg_b, 255));
    if t < 0.92 {
        ui.border(x, y, w, h, r, hex(0xFFFFFF, ((1.0 - t) * 44.0).round() as u8));
    }
    let knob = h - 4.0;
    let kx = x + 2.0 + (w - 4.0 - knob) * t;
    let base_rgb = if hov { [148.0, 163.0, 184.0] } else { [100.0, 116.0, 139.0] };
    let kr = (base_rgb[0] + (255.0 - base_rgb[0]) * t).round() as u8;
    let kg = (base_rgb[1] + (255.0 - base_rgb[1]) * t).round() as u8;
    let kb = (base_rgb[2] + (255.0 - base_rgb[2]) * t).round() as u8;
    ui.rounded(kx, y + 2.0, knob, knob, knob * 0.5, rgba(kr, kg, kb, 255));
}

/// Draws a `Ui.sliderV3` bar matching `Ui.java:260-284`.
fn draw_slider(ui: &mut Ui<'_>, x: f32, y: f32, w: f32, frac: f32, hot: bool) {
    let f = frac.clamp(0.0, 1.0);
    let ty = y + 5.0;
    ui.rounded(x, ty, w, 4.0, 2.0, hex(0xFFFFFF, 38));
    let fill_w = (w * f).max(4.0);
    ui.rounded(
        x,
        ty,
        fill_w,
        4.0,
        2.0,
        if hot { hex(0x5CE69B, 255) } else { hex(0xE2E8F0, 255) },
    );
    let kc = x + w * f;
    ui.rounded(kc - 4.5, ty - 2.5, 9.0, 9.0, 4.5, hex(0xFFFFFF, 255));
}

fn prefix_of(id: &str, name: &'static str) -> &'static str {
    match id {
        "fps" => "FPS",
        "ping" => "Ping",
        "server" => "Server",
        "memory" => "RAM",
        "speed" => "Speed",
        "cps" => "CPS",
        "coords" => "Coords",
        "reach" => "Reach",
        "totem" => "Totems",
        "armor" => "Armor",
        "hora" => "Time",
        "bioma" => "Biome",
        "direcao" => "Direction",
        "sessao" => "Session",
        "sprint" => "Sprint",
        "itens" => "Item",
        "pack" => "Pack",
        "efeitos" => "Effects",
        "health" => "HP",
        "hunger" => "Food",
        "combo" => "Combo",
        "target" => "Target",
        "armordur" => "Durability",
        "daycounter" => "Day",
        "itemcount" => "Item",
        "realtime" => "Time",
        "watermark" => "Astucy",
        "fpsgraph" => "FPS",
        _ => name,
    }
}

impl Game<'_> {
    fn astucy_config_path() -> std::path::PathBuf {
        Self::game_dir().join("astucy-rust.json")
    }

    pub(super) fn ensure_astucy_loaded(&mut self) {
        if self.pvp.astucy.initialized {
            return;
        }
        self.pvp.astucy.initialized = true;
        let path = Self::astucy_config_path();
        if let Ok(text) = std::fs::read_to_string(&path)
            && let Ok(cfg) = serde_json::from_str::<SavedConfig>(&text)
            && cfg.version == 2
        {
            for (i, prof) in cfg.profiles.iter().enumerate() {
                if !prof.is_empty() {
                    AstucyState::apply_saved(&mut self.pvp.astucy.profiles[i], prof);
                }
            }
            let idx = cfg.active_profile.min(2);
            self.pvp.astucy.active_profile = idx;
            self.pvp.astucy.mods = self.pvp.astucy.profiles[idx].clone();
            self.pvp.astucy.crosshair_pixels = cfg.crosshair_pixels;
            self.pvp.astucy.crosshair_invert = cfg.crosshair_invert;
            self.pvp.astucy.light_mode = cfg.light_mode;
        }
        self.sync_astucy_to_horizon();
    }

    pub(super) fn save_astucy_config(&mut self) {
        let idx = self.pvp.astucy.active_profile.min(2);
        self.pvp.astucy.profiles[idx] = self.pvp.astucy.mods.clone();
        let cfg = SavedConfig {
            version: 2,
            active_profile: idx,
            profiles: [
                AstucyState::export_profile(&self.pvp.astucy.profiles[0]),
                AstucyState::export_profile(&self.pvp.astucy.profiles[1]),
                AstucyState::export_profile(&self.pvp.astucy.profiles[2]),
            ],
            crosshair_pixels: self.pvp.astucy.crosshair_pixels,
            crosshair_invert: self.pvp.astucy.crosshair_invert,
            light_mode: self.pvp.astucy.light_mode,
        };
        if let Ok(json) = serde_json::to_string_pretty(&cfg) {
            let _ = std::fs::write(Self::astucy_config_path(), json);
        }
        self.sync_astucy_to_horizon();
    }

    pub(super) fn sync_astucy_to_horizon(&mut self) {
        let crosshair = self.pvp.astucy.is_on("crosshair");
        let chat = self.pvp.astucy.is_on("chat");
        let mouse_tweaks = self.pvp.astucy.is_on("mousetweaks");
        let fullbright = self.pvp.astucy.is_on("fullbright");
        let auto_sprint = self.pvp.astucy.is_on("sprint");
        let toggle_sneak = self.pvp.astucy.is_on("sneak");
        let viewmodel = self.pvp.astucy.find("viewmodel").cloned();

        self.settings.extras.pvp.crosshair.custom = crosshair;
        self.settings.extras.pvp.chat.timestamps = chat;
        self.settings.extras.pvp.chat.compact = chat;
        self.settings.extras.mouse_tweaks = mouse_tweaks;
        self.settings.extras.mouse_tweaks_wheel = mouse_tweaks;
        self.settings.extras.fullbright = fullbright;
        self.settings.toggle_sprint = auto_sprint;
        self.settings.toggle_crouch = toggle_sneak;
        self.hold_fullbright();

        if let Some(vm) = viewmodel {
            let pct = if vm.enabled {
                (60.0 + vm.level * 60.0).round() as u8
            } else {
                100
            };
            self.settings.extras.pvp.tweaks.shield_size = pct.clamp(50, 120);
            self.settings.extras.pvp.tweaks.totem_size = pct.clamp(50, 120);
        }
        let hud = &mut self.settings.extras.pvp.hud;
        hud.fps = false;
        hud.ping = false;
        hud.coords = false;
        hud.server = false;
        hud.speed = false;
        hud.cps = false;
        hud.reach = false;
        hud.combo = false;
        hud.clock = false;
    }

    pub(super) fn open_astucy_menu(&mut self, in_menu: bool) {
        self.ensure_astucy_loaded();
        if self.player.is_none() || self.title.is_some() {
            return;
        }
        self.keys.clear();
        let now = Instant::now();
        self.pvp.astucy.in_menu = in_menu;
        self.pvp.astucy.menu_opened_at = now;
        self.pvp.astucy.cat_opened_at = now;
        self.pvp.astucy.dragging_hud = None;
        self.pvp.astucy.search_focused = false;
        self.screen = Screen::HudEditor;
        self.capture(false);
    }

    pub(super) fn close_astucy_screen(&mut self) {
        if self.screen == Screen::HudEditor && !self.pvp.astucy.in_menu {
            let now = Instant::now();
            self.pvp.astucy.in_menu = true;
            self.pvp.astucy.dragging_hud = None;
            self.pvp.astucy.menu_opened_at = now;
            self.pvp.astucy.cat_opened_at = now;
            self.save_astucy_config();
            return;
        }
        if self.pvp.astucy.editing_mod.is_some() {
            self.pvp.astucy.editing_mod = None;
            self.pvp.astucy.cat_opened_at = Instant::now();
            self.save_astucy_config();
            return;
        }
        self.pvp.astucy.dragging_hud = None;
        self.pvp.astucy.search_focused = false;
        self.save_astucy_config();
        self.screen = Screen::Playing;
        self.capture(true);
    }

    pub(super) fn astucy_blur_radius(&self) -> Option<f32> {
        if self.title.is_some() || self.player.is_none() {
            return None;
        }
        // Only blur behind the R-SHIFT Menu (`in_menu = true`). Never blur in the HUD Editor (`!in_menu`),
        // otherwise `batch.blur_at` would blur the HUD chips themselves!
        if self.screen == Screen::HudEditor && self.pvp.astucy.in_menu {
            if self.pvp.astucy.is_on("menublur") {
                let elapsed = self.pvp.astucy.menu_opened_at.elapsed().as_secs_f32();
                let t = (elapsed / 0.22).clamp(0.0, 1.0);
                let ease = 1.0 - (1.0 - t).powi(3);
                return Some((10.0 * ease).max(1.0));
            }
            return None;
        }
        if self.screen == Screen::Playing
            && let Some(blur_mod) = self.pvp.astucy.find("blur")
            && blur_mod.enabled
        {
            let r = self.pvp.astucy.high_speed_factor * (1.2 + blur_mod.level * 5.2);
            if r >= 1.05 {
                return Some(r.clamp(1.0, 8.0));
            }
        }
        None
    }

    pub(super) fn astucy_zoom_factor(&self) -> Option<(f32, f64)> {
        let f = self.pvp.astucy.zoom_factor;
        if f > 1.01 {
            Some((f, 1.0 / f64::from(f)))
        } else {
            None
        }
    }

    pub(super) fn update_astucy_frame(&mut self) {
        self.ensure_astucy_loaded();
        let in_world = self.player.is_some() && self.title.is_none();
        if in_world && !self.pvp.astucy.was_in_world {
            self.pvp.astucy.world_joined_at = Some(Instant::now());
        }
        self.pvp.astucy.was_in_world = in_world;

        if !in_world {
            self.pvp.astucy.high_speed_factor = 0.0;
            self.pvp.astucy.move_rush_factor = 0.0;
            self.pvp.astucy.zoom_factor = 1.0;
            self.pvp.astucy.last_yaw = f32::NAN;
            self.pvp.astucy.last_frame_at = None;
            return;
        }

        let now = Instant::now();
        let dt = self
            .pvp
            .astucy
            .last_frame_at
            .map_or(0.016, |prev| now.duration_since(prev).as_secs_f32().clamp(0.001, 0.1));
        self.pvp.astucy.last_frame_at = Some(now);

        if now.duration_since(self.pvp.astucy.last_fps_sample).as_millis() >= 45 {
            self.pvp.astucy.last_fps_sample = now;
            let fps_val = self.fps.round().max(1.0) as u32;
            self.pvp.astucy.fps_history.push_back(fps_val);
            while self.pvp.astucy.fps_history.len() > 96 {
                self.pvp.astucy.fps_history.pop_front();
            }
        }

        let (yaw, pitch) = (self.yaw, self.pitch);
        let mut ang_speed = 0.0_f32;
        if !self.pvp.astucy.last_yaw.is_nan() {
            let mut dy = yaw - self.pvp.astucy.last_yaw;
            while dy > 180.0 { dy -= 360.0; }
            while dy < -180.0 { dy += 360.0; }
            let dp = pitch - self.pvp.astucy.last_pitch;
            ang_speed = dy.hypot(dp) / dt;
        }
        self.pvp.astucy.last_yaw = yaw;
        self.pvp.astucy.last_pitch = pitch;

        let (bps, _vert_bps) = self.player.as_ref().map_or((0.0, 0.0), |p| {
            ((p.velocity.x.hypot(p.velocity.z) * 20.0) as f32, (p.velocity.y.abs() * 20.0) as f32)
        });
        let move_target = ((bps - 4.2) / 7.5).clamp(0.0, 1.0);
        let cam_target = ((ang_speed - 75.0) / 480.0).clamp(0.0, 1.0);
        let k = 1.0 - (-dt * 14.0).exp();
        self.pvp.astucy.high_speed_factor += (cam_target - self.pvp.astucy.high_speed_factor) * k;
        self.pvp.astucy.move_rush_factor +=
            (move_target - self.pvp.astucy.move_rush_factor) * (1.0 - (-dt * 10.0).exp());

        let zoom_target = if self.screen == Screen::Playing
            && let Some(zm) = self.pvp.astucy.find("zoom")
            && zm.enabled
            && self.physical_keys.contains(&KeyCode::KeyC)
        {
            1.0 + zm.level * 8.0
        } else {
            1.0
        };
        let zk = 1.0 - (-dt * 14.0).exp();
        self.pvp.astucy.zoom_factor += (zoom_target - self.pvp.astucy.zoom_factor) * zk;
        if (self.pvp.astucy.zoom_factor - zoom_target).abs() < 0.003 {
            self.pvp.astucy.zoom_factor = zoom_target;
        }

        if self.screen == Screen::Playing && self.pvp.astucy.is_on("sprint") {
            self.settings.toggle_sprint = true;
            if !self.toggles.0[1].is_down {
                self.toggles.0[1].set_down(true, true);
                self.sync_toggle(1);
            }
        }
    }

    /// Renders in-game Astucy HUD matching `Modules.java` 1:1 (clean translucent pills, no green borders/stripes,
    /// boss-bar compass for `direcao`, 3x3 WASD+LMB/RMB/SPACE boxes for `keystrokes`, conditional `armor`/`efeitos`).
    pub(super) fn draw_astucy_hud(&mut self, batch: &mut HudBatch) {
        self.update_astucy_frame();
        if self.player.is_none() || self.title.is_some() {
            return;
        }
        // Hide live HUD behind the R-SHIFT menu (just like Java)
        if self.screen == Screen::HudEditor && self.pvp.astucy.in_menu {
            return;
        }
        let editing_hud = self.screen == Screen::HudEditor && !self.pvp.astucy.in_menu;
        let (sw, sh) = (batch.width, batch.height);

        // Gather live telemetry
        let now_secs = self.started.elapsed().as_secs_f64();
        let fps_num = self.fps.round().max(1.0) as u32;
        let ping_ms = self.own_latency().unwrap_or(0);
        let server_str = self
            .server_address()
            .map(horizon_client::net::shown_address)
            .unwrap_or_else(|| "Singleplayer".to_owned());
        let (px_pos, py_pos, pz_pos, hp, abs, food, sat, bps) =
            self.player.as_ref().map_or((0, 0, 0, 20.0, 0.0, 20, 5.0, 0.0), |p| {
                (
                    p.position.x.floor() as i32,
                    p.position.y.floor() as i32,
                    p.position.z.floor() as i32,
                    p.health.health(),
                    p.health.absorption(),
                    p.health.food.food,
                    p.health.food.saturation,
                    (p.velocity.x.hypot(p.velocity.z) * 20.0) as f32,
                )
            });
        let yaw = self.yaw;
        let facing_letter = match ((yaw / 90.0).round() as i32).rem_euclid(4) {
            0 => "S",
            1 => "W",
            2 => "N",
            _ => "E",
        };
        let cps_l = self.pvp.clicks[0].per_second(now_secs);
        let cps_r = self.pvp.clicks[1].per_second(now_secs);
        let reach_val = self
            .pvp
            .reach
            .hundredths(now_secs)
            .map(|n| format!("{:.2} b", n as f32 / 100.0))
            .unwrap_or_else(|| "0.00 b".to_owned());
        let combo_val = format!("{}x", self.pvp.combo.count(now_secs));
        let session_secs = self.started.elapsed().as_secs();
        let session_str = format!("{:02}:{:02}", session_secs / 60, session_secs % 60);
        let day_ticks = self.world.day_time.rem_euclid(24000);
        let game_hour = ((day_ticks / 1000 + 6) % 24) as u32;
        let game_min = ((day_ticks % 1000) * 60 / 1000) as u32;
        let time_str = format!("{game_hour:02}:{game_min:02}");
        let day_num = (self.world.day_time.max(0) / 24000) + 1;
        let real_time_str = {
            use chrono::Timelike;
            let t = chrono::Local::now();
            format!("{:02}:{:02}:{:02}", t.hour(), t.minute(), t.second())
        };
        let held_str = self
            .inventory
            .get(self.selected)
            .and_then(|s| s.as_ref())
            .map(|s| {
                let short = s.item.strip_prefix("minecraft:").unwrap_or(&s.item);
                format!("{} x{}", short.replace('_', " "), s.count)
            })
            .unwrap_or_else(|| "—".to_owned());
        let totem_count: u32 = self
            .inventory
            .iter()
            .chain(std::iter::once(&self.offhand))
            .filter_map(|s| s.as_ref())
            .filter(|s| s.item.ends_with("totem_of_undying"))
            .map(|s| s.count)
            .sum();

        // Collect real armor pieces (or preview in HUD Editor): (slot_tag, dur_text, pct, rgb)
        let slot_tags = ["B", "L", "C", "H"];
        let mut armor_rows: Vec<(&'static str, String, f32, u32)> = Vec::new();
        for (si, slot) in self.armor.iter().enumerate().rev() {
            if let Some(piece) = slot {
                let max = self.items().get(&piece.item).map_or(0, |i| i.max_damage);
                if max > 0 {
                    let rem = max.saturating_sub(piece.damage);
                    let pct = (rem as f32 / max as f32).clamp(0.0, 1.0);
                    let rgb = if pct > 0.60 {
                        0x6FE3A2
                    } else if pct > 0.35 {
                        0xFFE066
                    } else if pct > 0.15 {
                        0xFFB454
                    } else {
                        0xFF5D5D
                    };
                    let tag = slot_tags.get(si).copied().unwrap_or("A");
                    armor_rows.push((tag, format!("{rem}"), pct, rgb));
                }
            }
        }
        if armor_rows.is_empty() && editing_hud {
            armor_rows.extend([
                ("H", "363".to_owned(), 1.0, 0x6FE3A2),
                ("C", "528".to_owned(), 0.92, 0x6FE3A2),
                ("L", "495".to_owned(), 0.85, 0x6FE3A2),
                ("B", "429".to_owned(), 0.78, 0x6FE3A2),
            ]);
        }

        // Collect real active potion effects (or preview in HUD Editor)
        let mut effect_rows: Vec<(String, String)> = self
            .potions
            .effects
            .list
            .iter()
            .map(|e| {
                let raw = e.id.strip_prefix("minecraft:").unwrap_or(&e.id);
                let title: String = raw
                    .split('_')
                    .filter(|w| !w.is_empty())
                    .map(|w| {
                        let mut c = w.chars();
                        match c.next() {
                            Some(first) => format!("{}{}", first.to_ascii_uppercase(), c.as_str()),
                            None => String::new(),
                        }
                    })
                    .collect::<Vec<_>>()
                    .join(" ");
                let roman = match e.amplifier {
                    0 => String::new(),
                    1 => " II".to_owned(),
                    2 => " III".to_owned(),
                    3 => " IV".to_owned(),
                    4 => " V".to_owned(),
                    n => format!(" {}", n + 1),
                };
                let secs = (e.duration.max(0) / 20) as u32;
                (format!("{title}{roman}"), format!("{:01}:{:02}", secs / 60, secs % 60))
            })
            .collect();
        if effect_rows.is_empty() && editing_hud {
            effect_rows.push(("Speed II".to_owned(), "1:07".to_owned()));
            effect_rows.push(("Strength".to_owned(), "0:45".to_owned()));
        }

        let key_w = self.mapped_key_down("key.forward");
        let key_a = self.mapped_key_down("key.left");
        let key_s = self.mapped_key_down("key.back");
        let key_d = self.mapped_key_down("key.right");
        let key_space = self.mapped_key_down("key.jump");
        let key_lmb = cps_l > 0;
        let key_rmb = cps_r > 0;
        let keys_down = [key_w, key_a, key_s, key_d, key_lmb, key_rmb, key_space];
        for (ki, &down) in keys_down.iter().enumerate() {
            let target = if down { 1.0 } else { 0.0 };
            self.pvp.astucy.key_anim[ki] += (target - self.pvp.astucy.key_anim[ki]) * 0.34;
        }
        let key_anim = self.pvp.astucy.key_anim;
        let has_target = self.crosshair_living() == Some(true) || editing_hud;

        let mut new_boxes = Vec::new();
        let mut wm_logo: Option<(f32, f32, f32)> = None;
        {
            let mut ui = Ui::new(&self.gui.atlas, batch, self.cursor);
            let (uw, uh) = (ui.w, ui.h);
            let k_to_gui = ui.density / ui.batch.scale;

            for (idx, m) in self.pvp.astucy.mods.iter().enumerate() {
                if !m.has_hud || !m.enabled {
                    continue;
                }
                let sc = m.scale.clamp(0.6, 2.0);
                let bg_alpha = if m.bg { (m.bg_opacity * 255.0).round() as u8 } else { 0 };
                let bg_col = hex(m.bg_rgb, bg_alpha);
                let fg_col = hex(m.fg_rgb, 255);
                let pre_col = hex(m.fg_rgb, 184); // 72% alpha for prefix (`Modules.java:1088`)

                // 1. `direcao`: Boss-bar horizontal compass (`Modules.java:847-900`)
                if m.id == "direcao" {
                    let cw = 340.0 * sc;
                    let ch = 32.0 * sc;
                    let cx = (m.anchor[0] * uw - cw * 0.5).clamp(2.0, (uw - cw - 2.0).max(2.0));
                    let cy = (m.anchor[1] * uh).clamp(2.0, (uh - ch - 2.0).max(2.0));
                    if m.bg {
                        ui.rounded(cx, cy, cw, ch, 7.5 * sc, bg_col);
                    }
                    let center_x = cx + cw * 0.5;
                    let span = cw * 0.5 - 16.0 * sc;
                    let exact_bearing = (yaw + 180.0).rem_euclid(360.0);

                    for b_tick in (0..360).step_by(15) {
                        if b_tick % 45 == 0 {
                            continue;
                        }
                        let mut delta = (b_tick as f32 - exact_bearing + 180.0).rem_euclid(360.0) - 180.0;
                        if delta < -180.0 { delta += 360.0; }
                        if delta.abs() <= 88.0 {
                            let lx = center_x + (delta / 90.0) * span;
                            let fade = 1.0 - delta.abs() / 90.0;
                            ui.rounded(lx, cy + ch - 7.0 * sc, 1.4, 5.0 * sc, 0.0, hex(0xFFFFFF, (112.0 * fade) as u8));
                        }
                    }
                    let labels = ["N", "NE", "E", "SE", "S", "SW", "W", "NW"];
                    for (i, &lbl) in labels.iter().enumerate() {
                        let mut delta = (i as f32 * 45.0 - exact_bearing + 180.0).rem_euclid(360.0) - 180.0;
                        if delta < -180.0 { delta += 360.0; }
                        if delta.abs() <= 88.0 {
                            let lx = center_x + (delta / 90.0) * span;
                            let fade = 1.0 - delta.abs() / 90.0;
                            let a = (60.0 + 195.0 * fade).min(255.0) as u8;
                            ui.text_centered(
                                if i % 2 == 0 { Face::SemiBold } else { Face::Regular },
                                16.0 * sc,
                                lx,
                                cy + 4.0 * sc,
                                hex(m.fg_rgb, a),
                                lbl,
                            );
                        }
                    }
                    ui.rounded(center_x - 1.5 * sc, cy, 3.0 * sc, 5.0 * sc, 1.0, hex(0x33C46E, 255));
                    ui.rounded(center_x, cy + 5.0 * sc, 1.0, ch - 5.0 * sc, 0.0, hex(m.fg_rgb, 85));
                    new_boxes.push((idx, [cx * k_to_gui, cy * k_to_gui, cw * k_to_gui, ch * k_to_gui]));
                    continue;
                }

                // 2. `keystrokes`: 3x3 WASD + LMB/RMB + SPACE with smooth press animation (`Modules.java:1152-1239`)
                if m.id == "keystrokes" {
                    let s = 32.0 * sc;
                    let gap = 4.0 * sc;
                    let step = s + gap;
                    let cols_w = s * 3.0 + gap * 2.0;
                    let space_h = (20.0 * sc).max(s * 0.64);
                    let total_h = step * 3.0 + space_h;
                    let kx = (m.anchor[0] * uw).clamp(2.0, (uw - cols_w - 2.0).max(2.0));
                    let ky = (m.anchor[1] * uh).clamp(2.0, (uh - total_h - 2.0).max(2.0));

                    let draw_key = |ui: &mut Ui<'_>, rx: f32, ry: f32, rw: f32, rh: f32, lbl: &str, kt: f32| {
                        let t = kt.clamp(0.0, 1.0);
                        let inset = 1.4 * sc * t;
                        let bx = rx + inset;
                        let by = ry + inset;
                        let bw = (rw - inset * 2.0).max(4.0);
                        let bh = (rh - inset * 2.0).max(4.0);
                        if m.bg || t > 0.02 {
                            let r0 = ((m.bg_rgb >> 16) & 0xFF) as f32;
                            let g0 = ((m.bg_rgb >> 8) & 0xFF) as f32;
                            let b0 = (m.bg_rgb & 0xFF) as f32;
                            let r1 = ((m.fg_rgb >> 16) & 0xFF) as f32;
                            let g1 = ((m.fg_rgb >> 8) & 0xFF) as f32;
                            let b1 = (m.fg_rgb & 0xFF) as f32;
                            let ka = (f32::from(bg_alpha) + (235.0 - f32::from(bg_alpha)) * t).round() as u8;
                            let kbg = rgba(
                                (r0 + (r1 - r0) * t).round() as u8,
                                (g0 + (g1 - g0) * t).round() as u8,
                                (b0 + (b1 - b0) * t).round() as u8,
                                ka,
                            );
                            ui.rounded(bx, by, bw, bh, 7.5 * sc, kbg);
                        }
                        let kfg = if t > 0.55 { hex(0x101318, 255) } else { fg_col };
                        if lbl == "SPACE" {
                            let bar_w = bw * (0.38 + 0.16 * t);
                            ui.rounded(bx + (bw - bar_w) * 0.5, by + bh * 0.5 - 1.4 * sc, bar_w, 2.8 * sc, 1.4 * sc, kfg);
                        } else {
                            ui.text_centered(Face::Medium, 15.5 * sc, bx + bw * 0.5, by + (bh - 21.5 * sc) * 0.5, kfg, lbl);
                        }
                    };
                    draw_key(&mut ui, kx + step, ky, s, s, "W", key_anim[0]);
                    draw_key(&mut ui, kx, ky + step, s, s, "A", key_anim[1]);
                    draw_key(&mut ui, kx + step, ky + step, s, s, "S", key_anim[2]);
                    draw_key(&mut ui, kx + step * 2.0, ky + step, s, s, "D", key_anim[3]);
                    let half = (cols_w - gap) * 0.5;
                    draw_key(&mut ui, kx, ky + step * 2.0, half, s, "LMB", key_anim[4]);
                    draw_key(&mut ui, kx + half + gap, ky + step * 2.0, cols_w - half - gap, s, "RMB", key_anim[5]);
                    draw_key(&mut ui, kx, ky + step * 3.0, cols_w, space_h, "SPACE", key_anim[6]);

                    new_boxes.push((idx, [kx * k_to_gui, ky * k_to_gui, cols_w * k_to_gui, total_h * k_to_gui]));
                    continue;
                }

                // 3. `armor`: only drawn when armor equipped or in HUD editor preview
                if m.id == "armor" {
                    if armor_rows.is_empty() {
                        continue;
                    }
                    let cw = 96.0 * sc;
                    let row_h = 24.0 * sc;
                    let ch = armor_rows.len() as f32 * row_h + 8.0 * sc;
                    let ax = (m.anchor[0] * uw).clamp(2.0, (uw - cw - 2.0).max(2.0));
                    let ay = (m.anchor[1] * uh).clamp(2.0, (uh - ch - 2.0).max(2.0));
                    if m.bg {
                        ui.rounded(ax, ay, cw, ch, 7.5 * sc, bg_col);
                    }
                    for (ri, (tag, txt, pct, rgb)) in armor_rows.iter().enumerate() {
                        let ry = ay + 4.0 * sc + ri as f32 * row_h;
                        ui.rounded(ax + 7.0 * sc, ry + 3.0 * sc, 17.0 * sc, 17.0 * sc, 4.0 * sc, hex(*rgb, 36));
                        ui.text_centered(Face::SemiBold, 11.5 * sc, ax + 15.5 * sc, ry + 4.5 * sc, hex(*rgb, 245), tag);
                        ui.text(Face::Medium, 15.0 * sc, ax + 29.0 * sc, ry + 2.0 * sc, hex(*rgb, 255), txt);
                        let bar_w = cw - 36.0 * sc;
                        ui.rounded(ax + 29.0 * sc, ry + 19.0 * sc, bar_w, 2.2 * sc, 1.0, hex(0xFFFFFF, 32));
                        ui.rounded(ax + 29.0 * sc, ry + 19.0 * sc, bar_w * *pct, 2.2 * sc, 1.0, hex(*rgb, 220));
                    }
                    new_boxes.push((idx, [ax * k_to_gui, ay * k_to_gui, cw * k_to_gui, ch * k_to_gui]));
                    continue;
                }

                // 4. `efeitos`: only drawn when potions active or in HUD editor preview
                if m.id == "efeitos" {
                    if effect_rows.is_empty() {
                        continue;
                    }
                    let mut max_w = 132.0_f32;
                    for (name, time) in &effect_rows {
                        let w = ui.measure(Face::Medium, 15.0 * sc, name)
                            + ui.measure(Face::Regular, 14.0 * sc, time)
                            + 36.0 * sc;
                        max_w = max_w.max(w);
                    }
                    let row_h = 24.0 * sc;
                    let ch = effect_rows.len() as f32 * row_h + 8.0 * sc;
                    let ex = (m.anchor[0] * uw).clamp(2.0, (uw - max_w - 2.0).max(2.0));
                    let ey = (m.anchor[1] * uh).clamp(2.0, (uh - ch - 2.0).max(2.0));
                    if m.bg {
                        ui.rounded(ex, ey, max_w, ch, 7.5 * sc, bg_col);
                    }
                    for (ri, (name, time)) in effect_rows.iter().enumerate() {
                        let ry = ey + 4.0 * sc + ri as f32 * row_h;
                        ui.rounded(ex + 9.0 * sc, ry + 8.5 * sc, 6.0 * sc, 6.0 * sc, 3.0 * sc, hex(0x22C55E, 230));
                        ui.text(Face::Medium, 15.0 * sc, ex + 20.0 * sc, ry + 2.5 * sc, fg_col, name);
                        let tw = ui.measure(Face::Regular, 14.0 * sc, time);
                        ui.text(Face::Regular, 14.0 * sc, ex + max_w - 9.0 * sc - tw, ry + 3.0 * sc, pre_col, time);
                    }
                    new_boxes.push((idx, [ex * k_to_gui, ey * k_to_gui, max_w * k_to_gui, ch * k_to_gui]));
                    continue;
                }

                // 5. `target`: Target HUD card (`Modules.java:680-757`)
                if m.id == "target" {
                    if !has_target {
                        continue;
                    }
                    let tw = 206.0 * sc;
                    let th = 56.0 * sc;
                    let tx = (m.anchor[0] * uw).clamp(2.0, (uw - tw - 2.0).max(2.0));
                    let ty = (m.anchor[1] * uh).clamp(2.0, (uh - th - 2.0).max(2.0));
                    if m.bg {
                        ui.rounded(tx, ty, tw, th, 7.5 * sc, bg_col);
                    }
                    ui.rounded(tx + 8.0 * sc, ty + 8.0 * sc, 40.0 * sc, 40.0 * sc, 7.5 * sc, hex(m.fg_rgb, 52));
                    ui.text_centered(Face::SemiBold, 17.5 * sc, tx + 28.0 * sc, ty + 16.0 * sc, fg_col, "T");
                    ui.text(Face::SemiBold, 16.0 * sc, tx + 56.0 * sc, ty + 7.0 * sc, fg_col, "Target");
                    ui.text(Face::Regular, 13.5 * sc, tx + 56.0 * sc, ty + 24.0 * sc, hex(0x9AA2AB, 210), &format!("20.0 HP · {reach_val}"));
                    let bx = tx + 56.0 * sc;
                    let bw = tw - 66.0 * sc;
                    ui.rounded(bx, ty + 42.0 * sc, bw, 6.5 * sc, 3.2 * sc, hex(0xFFFFFF, 68));
                    ui.rounded(bx, ty + 42.0 * sc, bw * 0.92, 6.5 * sc, 3.2 * sc, hex(0x38C975, 255));
                    new_boxes.push((idx, [tx * k_to_gui, ty * k_to_gui, tw * k_to_gui, th * k_to_gui]));
                    continue;
                }

                // 6. `fpsgraph`: sparkline (`Modules.java:791-830`)
                if m.id == "fpsgraph" {
                    let gw = 166.0 * sc;
                    let gh = 58.0 * sc;
                    let gx = (m.anchor[0] * uw).clamp(2.0, (uw - gw - 2.0).max(2.0));
                    let gy = (m.anchor[1] * uh).clamp(2.0, (uh - gh - 2.0).max(2.0));
                    if m.bg {
                        ui.rounded(gx, gy, gw, gh, 7.5 * sc, bg_col);
                    }
                    ui.text(Face::Medium, 15.5 * sc, gx + 8.0 * sc, gy + 4.5 * sc, fg_col, &format!("{fps_num}"));
                    let lbl_w = ui.measure(Face::Medium, 14.0 * sc, "FPS");
                    ui.text(Face::Medium, 14.0 * sc, gx + gw - 8.0 * sc - lbl_w, gy + 4.5 * sc, hex(0x9AA2AB, 120), "FPS");
                    let bar_area_w = gw - 16.0 * sc;
                    let bar_area_h = gh - 28.0 * sc;
                    let bot_y = gy + gh - 6.0 * sc;
                    ui.rounded(gx + 8.0 * sc, bot_y, bar_area_w, 1.0, 0.0, hex(0xFFFFFF, 34));
                    let max_fps = self.pvp.astucy.fps_history.iter().copied().max().unwrap_or(60).max(60) as f32;
                    let count = self.pvp.astucy.fps_history.len().min(64);
                    let bw = (bar_area_w / 64.0).max(1.0);
                    for (k, &v) in self.pvp.astucy.fps_history.iter().rev().take(count).rev().enumerate() {
                        let bh = ((v as f32 / max_fps) * bar_area_h).max(1.0);
                        let rgb = if v >= 60 { 0x4FD07A } else if v >= 30 { 0xE8C15C } else { 0xE25661 };
                        let bx = gx + 8.0 * sc + bar_area_w - (count - k) as f32 * bw;
                        ui.rounded(bx, bot_y - bh, bw, bh, 0.0, hex(rgb, 210));
                    }
                    new_boxes.push((idx, [gx * k_to_gui, gy * k_to_gui, gw * k_to_gui, gh * k_to_gui]));
                    continue;
                }

                // 7. `watermark`: spinning mini Iris logo + ASTUCY + 0.1 (`Modules.java:767-788`)
                if m.id == "watermark" {
                    let fsz = 17.0 * sc;
                    let mw = ui.measure(Face::SemiBold, fsz, "ASTUCY");
                    let vw = ui.measure(Face::Regular, fsz, "0.1");
                    let cw = 26.0 * sc + mw + 7.0 * sc + vw + 11.0 * sc;
                    let ch = 30.0 * sc;
                    let wx = (m.anchor[0] * uw).clamp(2.0, (uw - cw - 2.0).max(2.0));
                    let wy = (m.anchor[1] * uh).clamp(2.0, (uh - ch - 2.0).max(2.0));
                    if m.bg {
                        ui.rounded(wx, wy, cw, ch, 7.5 * sc, bg_col);
                    }
                    let ty = wy + (ch - fsz * 1.42) * 0.5;
                    ui.text(Face::SemiBold, fsz, wx + 24.0 * sc, ty, fg_col, "ASTUCY");
                    ui.text(Face::Regular, fsz, wx + 24.0 * sc + mw + 6.0 * sc, ty, hex(m.fg_rgb, 142), "0.1");
                    wm_logo = Some(((wx + 13.0 * sc) * k_to_gui, (wy + ch * 0.5) * k_to_gui, 14.5 * sc * k_to_gui));
                    new_boxes.push((idx, [wx * k_to_gui, wy * k_to_gui, cw * k_to_gui, ch * k_to_gui]));
                    continue;
                }

                // Standard clean text HUD chip (`Modules.java:1072-1096`)
                let raw: String = match m.id {
                    "fps" => format!("{fps_num}"),
                    "ping" => format!("{ping_ms} ms"),
                    "server" => server_str.clone(),
                    "memory" => "28%".to_owned(),
                    "speed" => format!("{bps:.2} bps"),
                    "sessao" => session_str.clone(),
                    "hora" => time_str.clone(),
                    "bioma" => format!("{:?}", self.dim),
                    "coords" => format!("{px_pos}  {py_pos}  {pz_pos}  {facing_letter}"),
                    "cps" => format!("{cps_l} | {cps_r}"),
                    "reach" => reach_val.clone(),
                    "totem" => format!("{totem_count}"),
                    "sprint" => "ON".to_owned(),
                    "itens" | "itemcount" => held_str.clone(),
                    "pack" => "Astucy".to_owned(),
                    "health" => if abs > 0.05 { format!("{hp:.1} +{}", abs.round() as i32) } else { format!("{hp:.1}") },
                    "hunger" => format!("{food}  {sat:.1}"),
                    "combo" => combo_val.clone(),
                    "armordur" => armor_rows.first().map(|(_, t, _, _)| t.clone()).unwrap_or_else(|| "100%".to_owned()),
                    "daycounter" => format!("{day_num}"),
                    "realtime" => real_time_str.clone(),
                    _ => continue,
                };

                let fsz = 18.0 * sc;
                let pre_str = if m.show_prefix {
                    format!("{}: ", prefix_of(m.id, m.name))
                } else {
                    String::new()
                };
                let pre_w = if m.show_prefix { ui.measure(Face::Medium, fsz, &pre_str) } else { 0.0 };
                let val_w = ui.measure(Face::Medium, fsz, &raw);
                let chip_w = pre_w + val_w + 22.0 * sc;
                let chip_h = 30.0 * sc;

                let cx = (m.anchor[0] * uw).clamp(2.0, (uw - chip_w - 2.0).max(2.0));
                let cy = (m.anchor[1] * uh).clamp(2.0, (uh - chip_h - 2.0).max(2.0));

                if m.bg {
                    ui.rounded(cx, cy, chip_w, chip_h, 7.5 * sc, bg_col);
                }
                let ty = cy + (chip_h - fsz * 1.42) * 0.5;
                if m.show_prefix {
                    ui.text(Face::Medium, fsz, cx + 11.0 * sc, ty, pre_col, &pre_str);
                }
                ui.text(Face::Medium, fsz, cx + 11.0 * sc + pre_w, ty, fg_col, &raw);

                new_boxes.push((idx, [cx * k_to_gui, cy * k_to_gui, chip_w * k_to_gui, chip_h * k_to_gui]));
            }
            ui.finish();
        }
        if let Some((lx, ly, lsz)) = wm_logo {
            draw_astucy_logo(&self.gui.atlas, batch, lx, ly, lsz, now_secs as f32 * 0.85, 0.95, true);
        }
        self.pvp.astucy.hud_boxes = new_boxes;

        // World-Join Intro Banner (`Modules.java:503-577`)
        if !editing_hud
            && let Some(joined_at) = self.pvp.astucy.world_joined_at
        {
            let elapsed_ms = joined_at.elapsed().as_secs_f32() * 1000.0;
            if elapsed_ms <= 3600.0 {
                let in_raw = (elapsed_ms / 620.0).clamp(0.0, 1.0);
                let out_raw = if elapsed_ms > 3050.0 { ((elapsed_ms - 3050.0) / 550.0).clamp(0.0, 1.0) } else { 0.0 };
                let out_ease = out_raw * out_raw * (3.0 - 2.0 * out_raw);
                let vis = (1.0 - out_ease).clamp(0.0, 1.0);
                if vis > 0.01 {
                    let pill_reveal = ((in_raw - 0.28) / 0.72).clamp(0.0, 1.0);
                    let pill_ease = 1.0 - (1.0 - pill_reveal).powi(3);
                    let alpha = pill_ease * vis;

                    let (end_x, end_y, end_sz) = {
                        let mut ui = Ui::new(&self.gui.atlas, batch, self.cursor);
                        let bw = 296.0_f32;
                        let bh = 32.0_f32;
                        let bx = (ui.w - bw) * 0.5;
                        let by = 16.0 - out_ease * 32.0;
                        if alpha > 0.02 {
                            let cur_w = (bw * (0.35 + 0.65 * pill_ease)).max(44.0);
                            let cur_x = (ui.w - cur_w) * 0.5;
                            ui.rounded(cur_x, by, cur_w, bh, 10.0, hex(0x080B11, (238.0 * alpha) as u8));
                            ui.border(cur_x, by, cur_w, bh, 10.0, hex(0x22C55E, (95.0 * alpha) as u8));
                            if pill_ease > 0.55 {
                                let ta = (((pill_ease - 0.55) / 0.45).clamp(0.0, 1.0) * vis * 255.0) as u8;
                                ui.text(Face::SemiBold, 13.5, bx + 36.0, by + 6.5, hex(0xF8FAFC, ta), "A S T U C Y");
                                ui.rounded(bx + 124.0, by + 8.0, 1.0, bh - 16.0, 0.0, hex(0x22C55E, ta / 2));
                                ui.text(Face::Regular, 12.5, bx + 134.0, by + 7.5, hex(0x94A3B8, ta), "Press R-SHIFT for menu");
                            }
                        }
                        let k = ui.density / ui.batch.scale;
                        let res = ((bx + 18.0) * k, (by + bh * 0.5) * k, 18.0 * vis * k);
                        ui.finish();
                        res
                    };
                    draw_astucy_logo_travel(
                        &self.gui.atlas,
                        batch,
                        sw * 0.5,
                        sh * 0.36,
                        end_x,
                        end_y,
                        end_sz,
                        elapsed_ms,
                        620.0,
                        vis,
                    );
                }
            }
        }
    }

    /// Renders either the 1:1 Astucy R-SHIFT Menu (`in_menu = true`) or the 1:1 HUD Editor (`in_menu = false`).
    pub(super) fn draw_astucy_screen(&mut self, batch: &mut HudBatch) {
        self.ensure_astucy_loaded();
        if !self.pvp.astucy.in_menu {
            self.draw_astucy_hud_editor(batch);
            return;
        }

        let now = Instant::now();
        let dt = self
            .pvp
            .astucy
            .last_ui_at
            .map_or(0.016, |prev| now.duration_since(prev).as_secs_f32().clamp(0.001, 0.08));
        self.pvp.astucy.last_ui_at = Some(now);

        let elapsed_ms = self.pvp.astucy.menu_opened_at.elapsed().as_secs_f32() * 1000.0;
        let open_t = (elapsed_ms / 240.0).clamp(0.0, 1.0);
        let open_ease = 1.0 - (1.0 - open_t).powf(3.5);
        let cat_ms = self.pvp.astucy.cat_opened_at.elapsed().as_secs_f32() * 1000.0;
        let now_secs = self.started.elapsed().as_secs_f64() as f32;
        let fps_num = self.fps.round().max(1.0) as u32;
        let player_name = self.options.player_name();
        let active_count = self.pvp.astucy.mods.iter().filter(|m| m.enabled).count();

        // Scrim over blurred world (`RShiftScreen.java:1483-1484`)
        batch.fill(
            &self.gui.atlas,
            0.0,
            0.0,
            batch.width,
            batch.height,
            rgba(3, 5, 8, (108.0 * open_ease).round() as u8),
        );

        // Subtle drifting ambient emerald particles behind the R-SHIFT panel
        for i in 0..18 {
            let fi = i as f32;
            let px_p = ((fi * 0.417 + now_secs * (0.015 + (i % 3) as f32 * 0.005)).fract()) * batch.width;
            let py_p = ((fi * 0.731 - now_secs * (0.020 + (i % 4) as f32 * 0.004)).rem_euclid(1.0)) * batch.height;
            let pa = ((42.0 + 48.0 * (now_secs * 2.2 + fi).sin().abs()) * open_ease) as u8;
            let psz = if i % 3 == 0 { 2.0 } else { 1.3 };
            batch.fill(&self.gui.atlas, px_p, py_p, psz, psz, hex(0x4ADE80, pa));
        }

        // Pre-step per-card toggle animations
        let k_on = 1.0 - (-dt * 18.0).exp();
        for (i, m) in self.pvp.astucy.mods.iter().enumerate().take(48) {
            let target = if m.enabled { 1.0 } else { 0.0 };
            let next = self.pvp.astucy.card_on[i] + (target - self.pvp.astucy.card_on[i]) * k_on;
            self.pvp.astucy.card_on[i] = if (next - target).abs() < 0.003 { target } else { next };
        }

        let (header_logo, flight_start, grid_wm, tel_logo) = {
            let mut ui = Ui::new(&self.gui.atlas, batch, self.cursor);
            let (uw, uh) = (ui.w, ui.h);
            let (mx, my) = ui.cursor;
            let k_to_gui = ui.density / ui.batch.scale;
            let pw = (uw * 0.86).clamp(660.0, 900.0).round();
            let ph = (uh * 0.84).clamp(420.0, 560.0).round();
            let px = ((uw - pw) * 0.5).round();
            let py = ((uh - ph) * 0.5 + (1.0 - open_ease) * 14.0).round();
            let top_h = 44.0;
            let side_w = (pw / 5.8).clamp(138.0, 168.0).round();

            let lm = self.pvp.astucy.light_mode;
            let p_panel = if lm { 0xF8FAFC } else { 0x070A0F };
            let p_side = if lm { 0xE2E8F0 } else { 0x020306 };
            let p_chip = if lm { 0xE2E8F0 } else { 0x10141D };
            let p_text = if lm { 0x0F172A } else { 0xFFFFFF };
            let p_muted = if lm { 0x475569 } else { 0x8A94A6 };
            let p_border = if lm { 0x0F172A } else { 0xFFFFFF };

            // Main glass panel
            ui.rounded(px - 4.0, py + 3.0, pw + 8.0, ph + 8.0, 16.0, hex(0x000000, (52.0 * open_ease) as u8));
            ui.rounded(px, py, pw, ph, 13.0, hex(p_panel, (246.0 * open_ease) as u8));
            ui.border(px, py, pw, ph, 13.0, hex(p_border, (if lm { 36.0 } else { 28.0 } * open_ease) as u8));

            // Subtle header line + recessed sidebar
            ui.rounded(px + 8.0, py + top_h, pw - 16.0, 1.0, 0.0, hex(p_border, if lm { 24 } else { 18 }));
            ui.rounded(px + 8.0, py + top_h + 6.0, side_w - 6.0, ph - top_h - 14.0, 10.0, hex(p_side, if lm { 170 } else { 124 }));

            // Spaced "ASTUCY" wordmark next to the spinning/traveling official logo
            ui.text(Face::SemiBold, 15.5, px + 38.0, py + 11.0, hex(p_text, 255), "A S T U C Y");

            // Header Tabs: MODULES | MODS | SETTINGS with smooth sliding emerald underline
            let tabs = [("MODULES", 74.0_f32), ("MODS", 54.0), ("SETTINGS", 72.0)];
            let mut tx = px + side_w + 16.0;
            let mut target_tab_x = tx;
            let mut target_tab_w = 74.0_f32;
            for (ti, &(tname, tw)) in tabs.iter().enumerate() {
                let active = self.pvp.astucy.tab == ti as u8;
                if active {
                    target_tab_x = tx;
                    target_tab_w = tw;
                }
                let hov = mx >= tx && mx < tx + tw && my >= py + 6.0 && my < py + top_h - 6.0;
                if hov && !active {
                    ui.rounded(tx, py + 7.0, tw, top_h - 14.0, 6.0, hex(p_border, 22));
                }
                ui.text_centered(
                    if active { Face::SemiBold } else { Face::Medium },
                    12.5,
                    tx + tw * 0.5,
                    py + 13.0,
                    if active || hov { hex(p_text, 255) } else { hex(p_muted, 255) },
                    tname,
                );
                tx += tw + 6.0;
            }
            let anim_tab_x = self.pvp.astucy.anim("tab_x", target_tab_x, 20.0, dt);
            let anim_tab_w = self.pvp.astucy.anim("tab_w", target_tab_w, 20.0, dt);
            ui.rounded(anim_tab_x + 8.0, py + top_h - 2.5, (anim_tab_w - 16.0).max(8.0), 2.5, 1.2, hex(0x22C55E, 255));

            // Top-right Close [X] button (`22x22` at `px + pw - 32`)
            let cbx = px + pw - 32.0;
            let cby = py + (top_h - 22.0) * 0.5;
            let chov = mx >= cbx && mx < cbx + 22.0 && my >= cby && my < cby + 22.0;
            let c_t = self.pvp.astucy.anim("close_hov", if chov { 1.0 } else { 0.0 }, 18.0, dt);
            ui.rounded(cbx, cby, 22.0, 22.0, 6.5, if chov { hex(0xE25661, (40.0 + 36.0 * c_t) as u8) } else { hex(p_chip, 255) });
            ui.border(cbx, cby, 22.0, 22.0, 6.5, if chov { hex(0xE25661, 155) } else { hex(p_border, 26) });
            ui.text_centered(Face::Medium, 12.0, cbx + 11.0, cby + 2.5, if chov { hex(0xFFFFFF, 255) } else { hex(p_muted, 255) }, "X");

            // Account chip next to Close button
            let uw_chip = (ui.measure(Face::Medium, 12.0, &player_name) + 42.0).clamp(96.0, 180.0);
            let ux_chip = cbx - uw_chip - 8.0;
            ui.rounded(ux_chip, cby, uw_chip, 22.0, 6.5, hex(p_chip, 255));
            ui.border(ux_chip, cby, uw_chip, 22.0, 6.5, hex(p_border, 22));
            ui.rounded(ux_chip + 4.5, cby + 3.5, 15.0, 15.0, 4.5, hex(0x22C55E, 255));
            let initial: String = player_name.chars().take(1).collect();
            ui.text_centered(Face::SemiBold, 10.5, ux_chip + 12.0, cby + 3.5, hex(0x04140A, 255), &initial);
            ui.text(Face::Medium, 12.0, ux_chip + 24.0, cby + 2.5, hex(p_text, 255), &player_name);
            ui.text(Face::Regular, 10.5, ux_chip + uw_chip - 12.0, cby + 3.5, hex(p_muted, 255), "▾");

            // Dark / Light mode toggle pill next to Account chip
            let tw_btn = 58.0_f32;
            let tx_btn = ux_chip - tw_btn - 6.0;
            let thov = mx >= tx_btn && mx < tx_btn + tw_btn && my >= cby && my < cby + 22.0;
            ui.rounded(tx_btn, cby, tw_btn, 22.0, 6.5, if thov { hex(0x22C55E, 42) } else { hex(p_chip, 255) });
            ui.border(tx_btn, cby, tw_btn, 22.0, 6.5, if thov { hex(0x22C55E, 155) } else { hex(p_border, 22) });
            draw_skin_icon(
                &mut ui,
                if lm { "fullbright" } else { "moon" },
                tx_btn + 5.0,
                cby + 5.0,
                12.0,
                if lm { hex(0xF59E0B, 255) } else { hex(0x94A3B8, 255) },
            );
            ui.text(Face::Medium, 10.5, tx_btn + 20.0, cby + 4.0, hex(p_text, 255), if lm { "LIGHT" } else { "DARK" });

            // ---- Left Sidebar (`drawSidebar`, `RShiftScreen.java:1630-1700`) ----
            let sy0 = py + top_h + 26.0;
            let bw_prof = side_w - 22.0;
            ui.text(Face::Medium, 10.5, px + 14.0, sy0 - 17.0, hex(0x64748B, 255), "PROFILES");
            let target_prof_y = sy0 + self.pvp.astucy.active_profile.min(2) as f32 * 26.0;
            let anim_prof_y = self.pvp.astucy.anim("prof_y", target_prof_y, 20.0, dt);
            ui.rounded(px + 11.0, anim_prof_y, bw_prof, 22.0, 6.0, hex(0x151C27, 255));
            ui.rounded(px + 13.5, anim_prof_y + 5.0, 2.5, 12.0, 1.2, hex(0x22C55E, 255));

            for i in 0..3 {
                let ry = sy0 + i as f32 * 26.0;
                let current = self.pvp.astucy.active_profile == i;
                let hov = mx >= px + 11.0 && mx < px + 11.0 + bw_prof && my >= ry && my < ry + 22.0;
                if hov && !current {
                    ui.rounded(px + 11.0, ry, bw_prof, 22.0, 6.0, hex(0xFFFFFF, 20));
                }
                ui.text(
                    if current { Face::SemiBold } else { Face::Medium },
                    12.0,
                    px + 21.0,
                    ry + 2.5,
                    if current { hex(0xFFFFFF, 255) } else if hov { hex(0xE2E8F0, 255) } else { hex(0x8A94A6, 255) },
                    &format!("Profile {}", i + 1),
                );
            }

            let by_prof = sy0 + 3.0 * 26.0 + 10.0;
            let sh = mx >= px + 11.0 && mx < px + 11.0 + bw_prof && my >= by_prof && my < by_prof + 25.0;
            let hh = mx >= px + 11.0 && mx < px + 11.0 + bw_prof && my >= by_prof + 31.0 && my < by_prof + 56.0;
            let just_saved = self.pvp.astucy.saved_flash_at.is_some_and(|t| t.elapsed().as_millis() < 1200);

            ui.rounded(
                px + 11.0,
                by_prof,
                bw_prof,
                25.0,
                6.5,
                if just_saved {
                    hex(0x22C55E, 52)
                } else if sh {
                    hex(0x1A2230, 255)
                } else {
                    hex(0x121721, 255)
                },
            );
            ui.border(px + 11.0, by_prof, bw_prof, 25.0, 6.5, if just_saved { hex(0x22C55E, 150) } else { hex(0xFFFFFF, 24) });
            ui.text_centered(
                Face::Medium,
                11.5,
                px + 11.0 + bw_prof * 0.5,
                by_prof + 4.0,
                if just_saved { hex(0x4ADE80, 255) } else if sh { hex(0xFFFFFF, 255) } else { hex(0xCBD5E1, 255) },
                if just_saved { "SAVED ✓" } else { "SAVE PROFILE" },
            );

            // Solid emerald EDIT HUD button (`RShiftScreen.java:1671-1677`)
            let hh_t = self.pvp.astucy.anim("edit_hud_hov", if hh { 1.0 } else { 0.0 }, 18.0, dt);
            ui.rounded(
                px + 11.0,
                by_prof + 31.0 - hh_t * 1.0,
                bw_prof,
                25.0,
                6.5,
                if hh { hex(0x34D399, 255) } else { hex(0x22C55E, 255) },
            );
            ui.text_centered(
                Face::SemiBold,
                11.5,
                px + 11.0 + bw_prof * 0.5,
                by_prof + 35.0 - hh_t * 1.0,
                hex(0x04140A, 255),
                "EDIT HUD",
            );

            // Bottom sidebar telemetry card (`RShiftScreen.java:1681-1699`)
            let ty_tel = py + ph - 50.0;
            ui.rounded(px + 11.0, ty_tel, bw_prof, 38.0, 7.0, hex(0x070A0F, 255));
            ui.border(px + 11.0, ty_tel, bw_prof, 38.0, 7.0, hex(0xFFFFFF, 20));
            let pulse_a = (180.0 + 75.0 * (now_secs * 3.2).sin()).round() as u8;
            ui.rounded(px + 18.0, ty_tel + 9.0, 6.0, 6.0, 3.0, hex(0x22C55E, pulse_a));
            ui.text(Face::Medium, 11.5, px + 28.0, ty_tel + 4.0, hex(0xE2E8F0, 255), &format!("{active_count} active"));
            ui.text(Face::Regular, 10.5, px + 18.0, ty_tel + 20.0, hex(0x64748B, 255), &format!("{fps_num} FPS  ·  v0.1"));
            let tel_logo = (
                (px + 11.0 + bw_prof - 14.0) * k_to_gui,
                (ty_tel + 12.5) * k_to_gui,
                14.0 * k_to_gui,
            );

            // ---- Main Content Area (`cx`, `cy`, `cw`, `ch`) ----
            let cx = px + side_w + 12.0;
            let cy = py + top_h + 12.0;
            let cw = pw - side_w - 22.0;
            let ch = ph - top_h - 22.0;

            if self.pvp.astucy.tab == 1 {
                // MODS tab (`RShiftScreen.java:2154-2178`)
                let loaded_mods = [
                    ("Astucy Client", "0.1.0"),
                    ("Horizon Rust Engine", "26.3"),
                    ("wgpu Renderer (Sodium-class)", "26.3"),
                    ("WGSL Shader Pipeline (Iris-class)", "26.3"),
                    ("Astucy Menu Glass & Blur FX", "0.1.0"),
                    ("Astucy Native PvP Telemetry", "0.1.0"),
                ];
                for (i, &(mname, mver)) in loaded_mods.iter().enumerate() {
                    let delay = i as f32 * 24.0;
                    let rt = ((cat_ms - delay) / 220.0).clamp(0.0, 1.0);
                    let re = 1.0 - (1.0 - rt).powi(3);
                    let ry = cy + i as f32 * 32.0 + (1.0 - re) * 10.0;
                    let hov = mx >= cx && mx < cx + cw && my >= ry && my < ry + 26.0;
                    ui.rounded(cx, ry, cw, 26.0, 7.0, if hov { hex(0xFFFFFF, (32.0 * re) as u8) } else { hex(0xFFFFFF, (14.0 * re) as u8) });
                    if hov {
                        ui.rounded(cx + 2.0, ry + 5.0, 2.5, 16.0, 1.2, hex(0x22C55E, 255));
                    }
                    ui.text(Face::Medium, 12.5, cx + 12.0, ry + 4.0, hex(0xFFFFFF, (255.0 * re) as u8), mname);
                    let vw = ui.measure(Face::Regular, 11.5, mver);
                    ui.text(Face::Regular, 11.5, cx + cw - vw - 12.0, ry + 4.5, hex(0x6FE3A2, (235.0 * re) as u8), mver);
                }
                ui.text(Face::Regular, 11.0, cx + 8.0, cy + ch - 18.0, hex(0x8A94A6, 200), "6 mods loaded natively in Rust");
            } else if self.pvp.astucy.tab == 2 {
                // SETTINGS tab (`ClientSettingsScreen.java`)
                ui.text(Face::SemiBold, 11.5, cx + 6.0, cy, hex(0x6FE3A2, 255), "CLIENT & ENGINE SETTINGS");
                let rows = [
                    ("Edit HUD Layout", "Move, scale and restyle every chip", "EDIT HUD"),
                    ("Horizon PvP & Video Settings", "FOV, Render Distance, Shaders, 1.8 Combat", "OPEN"),
                    ("Reset HUD Positions", "Restore factory layout for active profile", "RESET"),
                    ("Restore Default PvP Preset", "Enable the 16 default Astucy modules", "APPLY"),
                ];
                for (i, &(lbl, tip, btn)) in rows.iter().enumerate() {
                    let delay = i as f32 * 28.0;
                    let rt = ((cat_ms - delay) / 220.0).clamp(0.0, 1.0);
                    let re = 1.0 - (1.0 - rt).powi(3);
                    let ry = cy + 24.0 + i as f32 * 40.0 + (1.0 - re) * 10.0;
                    let hov = mx >= cx && mx < cx + cw && my >= ry && my < ry + 34.0;
                    if hov {
                        ui.rounded(cx, ry, cw, 34.0, 7.0, hex(0xFFFFFF, 22));
                        ui.rounded(cx + 2.0, ry + 6.0, 2.5, 22.0, 1.2, hex(0x38C975, 255));
                    }
                    ui.text(Face::Medium, 13.0, cx + 14.0, ry + 3.0, hex(0xFFFFFF, (255.0 * re) as u8), lbl);
                    ui.text(Face::Regular, 11.0, cx + 14.0, ry + 18.0, hex(0x8A94A6, (220.0 * re) as u8), tip);
                    let bx = cx + cw - 122.0;
                    ui.rounded(bx, ry + 5.0, 112.0, 24.0, 6.5, if hov { hex(0x38C975, 58) } else { hex(0xFFFFFF, 24) });
                    ui.border(bx, ry + 5.0, 112.0, 24.0, 6.5, if hov { hex(0x38C975, 155) } else { hex(0xFFFFFF, 34) });
                    ui.text_centered(Face::Medium, 11.5, bx + 56.0, ry + 8.5, hex(0xFFFFFF, 255), btn);
                }
            } else if let Some(edit_idx) = self.pvp.astucy.editing_mod
                && let Some(em) = self.pvp.astucy.mods.get(edit_idx).cloned()
            {
                // Full Module Options View (`RShiftScreen.java:1894-2034`)
                ui.rounded(cx - 4.0, cy - 4.0, cw + 8.0, ch + 8.0, 10.0, hex(0x000000, 60));
                draw_skin_icon(&mut ui, em.id, cx + 4.0, cy + 2.0, 20.0, hex(0x4ADE80, 255));
                ui.text(Face::SemiBold, 14.0, cx + 30.0, cy - 1.0, hex(0xFFFFFF, 255), em.name);
                ui.text(Face::Regular, 11.5, cx + 30.0, cy + 16.0, hex(0x8A94A6, 255), em.desc);

                // Live HUD chip preview at top-right (`RShiftScreen.java:1909-1913`)
                if em.has_hud {
                    let pre = if em.show_prefix { format!("{}: ", prefix_of(em.id, em.name)) } else { String::new() };
                    let sample = format!("{pre}144");
                    let pw_chip = ui.measure(Face::Medium, 13.0, &sample) + 18.0;
                    let px_chip = cx + cw - pw_chip - 8.0;
                    if em.bg {
                        ui.rounded(px_chip, cy + 3.0, pw_chip, 24.0, 6.5, hex(em.bg_rgb, (em.bg_opacity * 255.0) as u8));
                    }
                    ui.text(Face::Medium, 13.0, px_chip + 9.0, cy + 5.5, hex(em.fg_rgb, 255), &sample);
                }

                if em.id == "crosshair" {
                    // Dedicated 17x17 Pixel Crosshair Painter
                    let cell = 11.0_f32;
                    let grid_px = 17.0 * cell;
                    let gx0 = cx + 8.0;
                    let gy0 = cy + 44.0;
                    ui.rounded(gx0 - 4.0, gy0 - 4.0, grid_px + 8.0, grid_px + 8.0, 8.0, hex(0x04060A, 255));
                    ui.border(gx0 - 4.0, gy0 - 4.0, grid_px + 8.0, grid_px + 8.0, 8.0, hex(0xFFFFFF, 28));

                    let pix_col = if self.pvp.astucy.crosshair_invert {
                        hex(0xFFFFFF, 255)
                    } else {
                        hex(em.fg_rgb, 255)
                    };
                    for py_i in 0..17_usize {
                        let row_bits = self.pvp.astucy.crosshair_pixels[py_i];
                        for px_i in 0..17_usize {
                            let x_c = gx0 + px_i as f32 * cell;
                            let y_c = gy0 + py_i as f32 * cell;
                            let is_center = px_i == 8 || py_i == 8;
                            let hovered = mx >= x_c && mx < x_c + cell && my >= y_c && my < y_c + cell;
                            let on = (row_bits >> px_i) & 1 != 0;
                            let bg_cell = if on {
                                pix_col
                            } else if hovered {
                                hex(0x22C55E, 90)
                            } else if px_i == 8 && py_i == 8 {
                                hex(0x22C55E, 36)
                            } else if is_center {
                                hex(0xFFFFFF, 18)
                            } else if (px_i + py_i) % 2 == 0 {
                                hex(0x0E131B, 255)
                            } else {
                                hex(0x0A0E15, 255)
                            };
                            ui.rounded(x_c + 0.5, y_c + 0.5, cell - 1.0, cell - 1.0, 1.5, bg_cell);
                        }
                    }
                    ui.text(Face::Regular, 10.0, gx0, gy0 + grid_px + 7.0, hex(0x64748B, 255), "Left-click/drag: Paint  ·  Right-click/drag: Erase");

                    // Right-side controls & 1:1 preview
                    let rx = gx0 + grid_px + 20.0;
                    let rw = (cx + cw - rx - 8.0).max(160.0);
                    ui.text(Face::Medium, 11.5, rx, gy0 - 2.0, hex(0x94A3B8, 255), "LIVE PREVIEW (1:1)");
                    let prev_y = gy0 + 16.0;
                    ui.rounded(rx, prev_y, 58.0, 58.0, 7.0, hex(0x0B0F17, 255));
                    ui.rounded(rx + 29.0, prev_y, 29.0, 58.0, 0.0, hex(0xCBD5E1, 40));
                    ui.border(rx, prev_y, 58.0, 58.0, 7.0, hex(0xFFFFFF, 28));
                    let pcx = rx + 29.0;
                    let pcy = prev_y + 29.0;
                    for py_i in 0..17_usize {
                        let row_bits = self.pvp.astucy.crosshair_pixels[py_i];
                        for px_i in 0..17_usize {
                            if (row_bits >> px_i) & 1 != 0 {
                                let ox = px_i as f32 - 8.0;
                                let oy = py_i as f32 - 8.0;
                                if em.shadow {
                                    ui.rounded(pcx + ox - 0.5, pcy + oy - 0.5, 2.5, 2.5, 0.0, hex(0x000000, 220));
                                }
                                ui.rounded(pcx + ox, pcy + oy, 1.5, 1.5, 0.0, pix_col);
                            }
                        }
                    }

                    // Presets
                    ui.text(Face::Medium, 11.5, rx, prev_y + 70.0, hex(0x94A3B8, 255), "PRESETS");
                    let presets = ["Cross", "Dot", "Gap", "X", "Clear"];
                    let pr_w = ((rw - 16.0) / 5.0).floor();
                    for (pi, &pname) in presets.iter().enumerate() {
                        let bx_p = rx + pi as f32 * (pr_w + 4.0);
                        let by_p = prev_y + 88.0;
                        let phov = mx >= bx_p && mx < bx_p + pr_w && my >= by_p && my < by_p + 22.0;
                        ui.rounded(bx_p, by_p, pr_w, 22.0, 5.5, hex(0xFFFFFF, if phov { 44 } else { 22 }));
                        ui.text_centered(Face::Medium, 10.5, bx_p + pr_w * 0.5, by_p + 4.0, hex(0xFFFFFF, 255), pname);
                    }

                    // Invert & Outline toggles
                    let inv_y = prev_y + 122.0;
                    let inv_t = self.pvp.astucy.anim("ch_inv", if self.pvp.astucy.crosshair_invert { 1.0 } else { 0.0 }, 20.0, dt);
                    let inv_hov = mx >= rx && mx < rx + rw && my >= inv_y && my < inv_y + 24.0;
                    ui.text(Face::Medium, 12.0, rx, inv_y + 3.0, hex(0xE2E8F0, 240), "Invert Color");
                    draw_toggle(&mut ui, rx + rw - 40.0, inv_y + 3.0, 38.0, 18.0, inv_t, inv_hov);

                    let out_y = inv_y + 30.0;
                    let out_t = self.pvp.astucy.anim("ch_out", if em.shadow { 1.0 } else { 0.0 }, 20.0, dt);
                    let out_hov = mx >= rx && mx < rx + rw && my >= out_y && my < out_y + 24.0;
                    ui.text(Face::Medium, 12.0, rx, out_y + 3.0, hex(0xE2E8F0, 240), "Outline");
                    draw_toggle(&mut ui, rx + rw - 40.0, out_y + 3.0, 38.0, 18.0, out_t, out_hov);

                    // Color Swatches
                    let sw_y = out_y + 34.0;
                    ui.text(Face::Medium, 11.5, rx, sw_y, hex(0x94A3B8, 255), "COLOR");
                    for (si, &scol) in SWATCHES.iter().enumerate() {
                        let wx = rx + si as f32 * 20.0;
                        let wy = sw_y + 18.0;
                        ui.rounded(wx, wy, 15.0, 15.0, 4.0, hex(scol, 255));
                        if !self.pvp.astucy.crosshair_invert && em.fg_rgb == scol {
                            ui.border(wx - 1.5, wy - 1.5, 18.0, 18.0, 5.0, hex(0xFFFFFF, 255));
                        }
                    }
                } else {
                let sx = cx + (cw * 0.44).round();
                let sw_sl = (cw - (sx - cx) - 68.0).clamp(110.0, 220.0);
                let row_y0 = cy + 40.0;
                let row_h = 28.0;

                let opt_labels = [
                    "Scale",
                    "Background",
                    "Opacity",
                    "Background color",
                    "Text color",
                    "Shadow",
                    "Prefix",
                    "Position",
                    "Level / Intensity",
                ];
                let bg_on_t = self.pvp.astucy.anim("opt_bg", if em.bg { 1.0 } else { 0.0 }, 20.0, dt);
                let sh_on_t = self.pvp.astucy.anim("opt_sh", if em.shadow { 1.0 } else { 0.0 }, 20.0, dt);
                let pr_on_t = self.pvp.astucy.anim("opt_pr", if em.show_prefix { 1.0 } else { 0.0 }, 20.0, dt);

                for (ri, &rlbl) in opt_labels.iter().enumerate() {
                    let ry = row_y0 + ri as f32 * row_h;
                    let rhov = mx >= cx && mx < cx + cw && my >= ry - 2.0 && my < ry + row_h - 2.0;
                    if rhov {
                        ui.rounded(cx - 2.0, ry - 2.0, cw + 4.0, row_h - 2.0, 6.0, hex(0xFFFFFF, 20));
                        ui.rounded(cx - 2.0, ry + 2.0, 2.5, row_h - 8.0, 1.2, hex(0x6FE3A2, 255));
                    }
                    ui.text(Face::Medium, 12.0, cx + 8.0, ry + 2.5, if rhov { hex(0xFFFFFF, 255) } else { hex(0xE2E8F0, 220) }, rlbl);
                    match ri {
                        0 => {
                            draw_slider(&mut ui, sx, ry + 4.0, sw_sl, (em.scale - 0.6) / 1.4, rhov);
                            ui.text(Face::Medium, 11.5, sx + sw_sl + 12.0, ry + 2.5, hex(0xFFFFFF, 255), &format!("{:.2}x", em.scale));
                        }
                        1 => draw_toggle(&mut ui, sx, ry + 2.5, 38.0, 18.0, bg_on_t, rhov),
                        2 => {
                            draw_slider(&mut ui, sx, ry + 4.0, sw_sl, em.bg_opacity, rhov);
                            ui.text(Face::Medium, 11.5, sx + sw_sl + 12.0, ry + 2.5, hex(0xFFFFFF, 255), &format!("{:.0}%", em.bg_opacity * 100.0));
                        }
                        3 => {
                            for (si, &scol) in SWATCHES.iter().enumerate() {
                                let wx = sx + si as f32 * 18.0;
                                ui.rounded(wx, ry + 3.0, 14.0, 14.0, 4.0, hex(scol, 255));
                                if em.bg_rgb == scol {
                                    ui.border(wx - 1.5, ry + 1.5, 17.0, 17.0, 5.0, hex(0xFFFFFF, 255));
                                }
                            }
                        }
                        4 => {
                            for (si, &scol) in SWATCHES.iter().enumerate() {
                                let wx = sx + si as f32 * 18.0;
                                ui.rounded(wx, ry + 3.0, 14.0, 14.0, 4.0, hex(scol, 255));
                                if em.fg_rgb == scol {
                                    ui.border(wx - 1.5, ry + 1.5, 17.0, 17.0, 5.0, hex(0xFFFFFF, 255));
                                }
                            }
                        }
                        5 => draw_toggle(&mut ui, sx, ry + 2.5, 38.0, 18.0, sh_on_t, rhov),
                        6 => draw_toggle(&mut ui, sx, ry + 2.5, 38.0, 18.0, pr_on_t, rhov),
                        7 => {
                            ui.rounded(sx, ry + 1.0, 132.0, 21.0, 6.0, hex(0x3E7BFA, 255));
                            ui.text_centered(Face::SemiBold, 11.0, sx + 66.0, ry + 3.5, hex(0xFFFFFF, 255), "EDIT LAYOUT");
                        }
                        _ => {
                            draw_slider(&mut ui, sx, ry + 4.0, sw_sl, em.level, rhov);
                            ui.text(Face::Medium, 11.5, sx + sw_sl + 12.0, ry + 2.5, hex(0xFFFFFF, 255), &format!("{:.0}%", em.level * 100.0));
                        }
                    }
                }
                }

                let by_opt = cy + ch - 26.0;
                let h_rst = mx >= cx && mx < cx + 138.0 && my >= by_opt && my < by_opt + 23.0;
                let h_bck = mx >= cx + 148.0 && mx < cx + 286.0 && my >= by_opt && my < by_opt + 23.0;
                ui.rounded(cx, by_opt, 138.0, 23.0, 6.0, hex(0xFFFFFF, if h_rst { 56 } else { 32 }));
                ui.text_centered(Face::Medium, 11.5, cx + 69.0, by_opt + 3.5, hex(0xFFFFFF, 255), "RESET STYLE");
                ui.rounded(cx + 148.0, by_opt, 138.0, 23.0, 6.0, hex(0xFFFFFF, if h_bck { 56 } else { 32 }));
                ui.text_centered(Face::Medium, 11.5, cx + 217.0, by_opt + 3.5, hex(0xFFFFFF, 255), "BACK");
            } else {
                // 2-Column Module Card Grid drawn FIRST so top/bottom masks clip cleanly on scroll
                let grid_y = cy + 31.0;
                let grid_bottom = cy + ch;
                let card_w = ((cw - 10.0 - 10.0) * 0.5).floor();
                let card_h = 46.0;
                let row_step = card_h + 9.0;

                let query = self.pvp.astucy.search.trim().to_ascii_lowercase();
                let cat_filter = self.pvp.astucy.cat_filter;
                let filtered: Vec<usize> = self
                    .pvp
                    .astucy
                    .mods
                    .iter()
                    .enumerate()
                    .filter(|(_, m)| {
                        let cat_ok = match cat_filter {
                            0 => true,
                            1 => m.is_new,
                            2 => m.cat == ModCat::Combat,
                            3 => m.cat == ModCat::Info,
                            4 => m.cat == ModCat::Visuals,
                            5 => m.cat == ModCat::Utility,
                            _ => true,
                        };
                        let search_ok = query.is_empty()
                            || m.name.to_ascii_lowercase().contains(&query)
                            || m.desc.to_ascii_lowercase().contains(&query)
                            || m.id.to_ascii_lowercase().contains(&query);
                        cat_ok && search_ok
                    })
                    .map(|(i, _)| i)
                    .collect();

                let total_rows = filtered.len().div_ceil(2);
                let content_h = total_rows as f32 * row_step;
                let view_h = (grid_bottom - grid_y).max(1.0);
                let max_scroll = (content_h - view_h).max(0.0);
                self.pvp.astucy.scroll_target = self.pvp.astucy.scroll_target.clamp(0.0, max_scroll);
                let sc_k = 1.0 - (-dt * 18.0).exp();
                self.pvp.astucy.scroll += (self.pvp.astucy.scroll_target - self.pvp.astucy.scroll) * sc_k;
                if (self.pvp.astucy.scroll - self.pvp.astucy.scroll_target).abs() < 0.2 {
                    self.pvp.astucy.scroll = self.pvp.astucy.scroll_target;
                }
                let scroll = self.pvp.astucy.scroll.clamp(0.0, max_scroll);

                for (slot, &mod_idx) in filtered.iter().enumerate() {
                    let col = slot % 2;
                    let row = slot / 2;
                    let base_gy = grid_y + row as f32 * row_step - scroll;
                    if base_gy + card_h <= grid_y || base_gy + card_h > grid_bottom + 2.0 {
                        continue;
                    }

                    // Staggered cascade entrance (`RShiftScreen.java:1800-1806`)
                    let delay_ms = (row.min(8) * 20 + col * 10) as f32;
                    let card_t = ((cat_ms - delay_ms) / 210.0).clamp(0.0, 1.0);
                    let card_ease = 1.0 - (1.0 - card_t).powi(3);
                    if card_ease <= 0.02 {
                        continue;
                    }

                    let gx = cx + col as f32 * (card_w + 10.0);
                    let hov = mx >= gx && mx < gx + card_w && my >= base_gy && my < base_gy + card_h;
                    let hov_k = 1.0 - (-dt * 18.0).exp();
                    if mod_idx < 48 {
                        let h_target = if hov { 1.0 } else { 0.0 };
                        self.pvp.astucy.card_hov[mod_idx] += (h_target - self.pvp.astucy.card_hov[mod_idx]) * hov_k;
                    }
                    let h_t = if mod_idx < 48 { self.pvp.astucy.card_hov[mod_idx] } else { 0.0 };
                    let on_t = if mod_idx < 48 { self.pvp.astucy.card_on[mod_idx] } else { 0.0 };
                    let gy = base_gy + (1.0 - card_ease) * 10.0 - h_t * 1.4;

                    let m = &self.pvp.astucy.mods[mod_idx];

                    // Smoothly blended card surface (Dark / Light)
                    let fill_col = match (lm, m.enabled, hov) {
                        (true, true, true) => hex(0xDCFCE7, (255.0 * card_ease) as u8),
                        (true, true, false) => hex(0xECFDF5, (255.0 * card_ease) as u8),
                        (true, false, true) => hex(0xFFFFFF, (255.0 * card_ease) as u8),
                        (true, false, false) => hex(0xF1F5F9, (255.0 * card_ease) as u8),
                        (false, true, true) => hex(0x132C22, (255.0 * card_ease) as u8),
                        (false, true, false) => hex(0x0E221A, (255.0 * card_ease) as u8),
                        (false, false, true) => hex(0x151B27, (255.0 * card_ease) as u8),
                        (false, false, false) => hex(0x0E121A, (255.0 * card_ease) as u8),
                    };
                    ui.rounded(gx, gy, card_w, card_h, 8.0, fill_col);
                    if m.enabled || h_t > 0.05 || lm {
                        let brd_col = if m.enabled {
                            hex(0x22C55E, ((if lm { 120.0 } else { 58.0 } + 48.0 * h_t) * card_ease) as u8)
                        } else {
                            hex(p_border, ((if lm { 22.0 } else { 0.0 } + 26.0 * h_t) * card_ease) as u8)
                        };
                        ui.border(gx, gy, card_w, card_h, 8.0, brd_col);
                    }

                    // Click pulse wave (`card_flash`, `RShiftScreen.java:1888-1891`)
                    if mod_idx < 48
                        && let Some(fl_at) = self.pvp.astucy.card_flash[mod_idx]
                    {
                        let fl_ms = fl_at.elapsed().as_secs_f32() * 1000.0;
                        if fl_ms < 320.0 {
                            let ft = (fl_ms / 320.0).clamp(0.0, 1.0);
                            let fw = card_w * (0.25 + 0.75 * (1.0 - (1.0 - ft).powi(3)));
                            let fa = ((1.0 - ft) * 44.0) as u8;
                            ui.rounded(gx + (card_w - fw) * 0.5, gy, fw, card_h, 8.0, hex(0x22C55E, fa));
                        }
                    }

                    // Left vertical emerald indicator with smooth height (`RShiftScreen.java:1823-1827`)
                    if on_t > 0.02 {
                        let bar_h = (card_h - 16.0) * on_t;
                        ui.rounded(gx + 2.5, gy + (card_h - bar_h) * 0.5, 2.5, bar_h, 1.2, hex(0x22C55E, (255.0 * on_t * card_ease) as u8));
                    }

                    // Recessed 28x28 icon well + per-module vector icon (`RShiftScreen.java:1833-1836`)
                    let badge_x = gx + 10.0;
                    let badge_y = gy + 9.0;
                    ui.rounded(
                        badge_x,
                        badge_y,
                        28.0,
                        28.0,
                        6.5,
                        if m.enabled {
                            hex(0x22C55E, (42.0 * card_ease) as u8)
                        } else if lm {
                            hex(0xE2E8F0, (255.0 * card_ease) as u8)
                        } else if hov {
                            hex(0x0B0E16, (255.0 * card_ease) as u8)
                        } else {
                            hex(0x07090E, (255.0 * card_ease) as u8)
                        },
                    );
                    draw_skin_icon(
                        &mut ui,
                        m.id,
                        badge_x + 6.0,
                        badge_y + 6.0,
                        16.0,
                        if m.enabled {
                            hex(if lm { 0x16A34A } else { 0x4ADE80 }, (255.0 * card_ease) as u8)
                        } else if hov {
                            hex(if lm { 0x0F172A } else { 0xE2E8F0 }, (255.0 * card_ease) as u8)
                        } else {
                            hex(if lm { 0x475569 } else { 0x94A3B8 }, (255.0 * card_ease) as u8)
                        },
                    );

                    // Title + Description
                    let text_x = badge_x + 38.0;
                    ui.text(
                        Face::Medium,
                        13.0,
                        text_x,
                        gy + 6.5,
                        if lm { hex(0x0F172A, (255.0 * card_ease) as u8) } else if m.enabled || hov { hex(0xFFFFFF, (255.0 * card_ease) as u8) } else { hex(0xF1F5F9, (255.0 * card_ease) as u8) },
                        m.name,
                    );
                    ui.text(
                        Face::Regular,
                        11.0,
                        text_x,
                        gy + 23.5,
                        if m.enabled {
                            hex(if lm { 0x15803D } else { 0x6EE7B7 }, (225.0 * card_ease) as u8)
                        } else if hov {
                            hex(if lm { 0x334155 } else { 0x94A3B8 }, (255.0 * card_ease) as u8)
                        } else {
                            hex(0x64748B, (255.0 * card_ease) as u8)
                        },
                        m.desc,
                    );

                    // Options button with 3-slider-bars icon (`RShiftScreen.java:1859-1867`)
                    let opt_x = gx + card_w - 66.0;
                    let opt_y = gy + 12.0;
                    let opt_hov = mx >= opt_x - 2.0 && mx < opt_x + 24.0 && my >= gy + 4.0 && my < gy + card_h - 4.0;
                    if opt_hov {
                        ui.rounded(opt_x, opt_y, 22.0, 22.0, 6.0, hex(0xFFFFFF, 34));
                    }
                    draw_skin_icon(
                        &mut ui,
                        "options",
                        opt_x + 4.5,
                        opt_y + 4.5,
                        13.0,
                        if opt_hov { hex(0xFFFFFF, 255) } else { hex(0x64748B, (255.0 * card_ease) as u8) },
                    );

                    // Smoothly animated pill toggle switch (`30x16` at `gx + card_w - 38`, `RShiftScreen.java:1870-1885`)
                    draw_toggle(&mut ui, gx + card_w - 38.0, gy + 15.0, 30.0, 16.0, on_t, hov);
                }

                // Discrete scrollbar (`Ui.scrollbar`, `RShiftScreen.java:1783-1789`)
                if max_scroll > 1.0 {
                    let sb_cx = cx + cw - 3.5;
                    ui.rounded(sb_cx - 1.5, grid_y, 3.5, view_h, 1.8, hex(0xFFFFFF, 20));
                    let thumb_h = ((view_h / content_h) * view_h).clamp(28.0, view_h);
                    let thumb_y = grid_y + (scroll / max_scroll) * (view_h - thumb_h);
                    ui.rounded(sb_cx - 2.0, thumb_y, 4.5, thumb_h, 2.2, hex(0x22C55E, 185));
                }

                // Top & bottom clip masks so scrolled cards slide cleanly under Category Bar & Search
                ui.rounded(cx - 4.0, py + top_h + 1.0, cw + 8.0, grid_y - (py + top_h + 1.0), 0.0, hex(p_panel, (248.0 * open_ease) as u8));
                ui.rounded(cx - 4.0, grid_bottom, cw + 8.0, (py + ph - 1.0 - grid_bottom).max(0.0), 0.0, hex(p_panel, (248.0 * open_ease) as u8));
                ui.border(px, py, pw, ph, 13.0, hex(p_border, (if lm { 36.0 } else { 28.0 } * open_ease) as u8));

                // Category pills with smooth sliding emerald pill + inline Search
                let cats = [
                    ("ALL", 0_u8),
                    ("NEW", 1),
                    ("COMBAT", 2),
                    ("INFO", 3),
                    ("VISUALS", 4),
                    ("UTILITY", 5),
                ];
                let mut fx = cx;
                let mut target_pill_x = cx;
                let mut target_pill_w = 42.0_f32;
                let mut pill_layouts: [([f32; 2], &'static str, u8); 6] = [([0.0, 0.0], "", 0); 6];
                for (ci, &(cname, cid)) in cats.iter().enumerate() {
                    let pw_pill = ui.measure(Face::Medium, 11.5, cname) + 18.0;
                    if self.pvp.astucy.cat_filter == cid {
                        target_pill_x = fx;
                        target_pill_w = pw_pill;
                    }
                    pill_layouts[ci] = ([fx, pw_pill], cname, cid);
                    fx += pw_pill + 5.0;
                }
                let anim_pill_x = self.pvp.astucy.anim("pill_x", target_pill_x, 22.0, dt);
                let anim_pill_w = self.pvp.astucy.anim("pill_w", target_pill_w, 22.0, dt);
                ui.rounded(anim_pill_x, cy, anim_pill_w, 23.0, 6.5, hex(0x22C55E, 255));

                for &([px_p, pw_p], cname, cid) in &pill_layouts {
                    let active = self.pvp.astucy.cat_filter == cid;
                    let hov = mx >= px_p && mx < px_p + pw_p && my >= cy && my < cy + 23.0;
                    if hov && !active {
                        ui.rounded(px_p, cy, pw_p, 23.0, 6.5, hex(0xFFFFFF, 22));
                    }
                    ui.text_centered(
                        if active { Face::SemiBold } else { Face::Medium },
                        11.5,
                        px_p + pw_p * 0.5,
                        cy + 3.5,
                        if active { hex(0x04140A, 255) } else if hov { hex(0xFFFFFF, 255) } else { hex(0x8A94A6, 255) },
                        cname,
                    );
                }

                // Inline Search box on the same row
                let sb_w = (cx + cw - 10.0 - fx - 6.0).clamp(118.0, 176.0);
                let sb_x = cx + cw - 10.0 - sb_w;
                let sb_hov = mx >= sb_x && mx < sb_x + sb_w && my >= cy && my < cy + 23.0;
                let s_foc = self.pvp.astucy.search_focused;
                ui.rounded(sb_x, cy, sb_w, 23.0, 6.5, if lm { hex(0xFFFFFF, 255) } else if s_foc { hex(0x0A0E16, 255) } else { hex(0x05070B, 255) });
                ui.border(sb_x, cy, sb_w, 23.0, 6.5, if s_foc { hex(0x22C55E, 195) } else if sb_hov { hex(p_border, 52) } else { hex(p_border, 28) });
                draw_skin_icon(&mut ui, "search", sb_x + 6.0, cy + 5.5, 12.0, if s_foc { hex(0x22C55E, 255) } else { hex(0x64748B, 255) });
                let caret_on = s_foc && ((now_secs * 2.0) as i32 % 2 == 0);
                if self.pvp.astucy.search.is_empty() {
                    if caret_on {
                        ui.rounded(sb_x + 22.0, cy + 5.0, 1.5, 13.0, 0.5, hex(0x22C55E, 255));
                    }
                    ui.text(Face::Regular, 11.5, sb_x + 25.0, cy + 3.5, hex(0x64748B, 210), "Search...");
                } else {
                    ui.text(Face::Regular, 11.5, sb_x + 22.0, cy + 3.5, hex(p_text, 255), &self.pvp.astucy.search);
                    if caret_on {
                        let tw = ui.measure(Face::Regular, 11.5, &self.pvp.astucy.search);
                        ui.rounded((sb_x + 23.0 + tw).min(sb_x + sb_w - 20.0), cy + 5.0, 1.5, 13.0, 0.5, hex(0x22C55E, 255));
                    }
                    let clr_x = sb_x + sb_w - 17.0;
                    let clr_hov = mx >= clr_x && mx < sb_x + sb_w && my >= cy && my < cy + 23.0;
                    ui.text_centered(Face::Medium, 11.0, clr_x + 7.0, cy + 3.5, if clr_hov { hex(0xFFFFFF, 255) } else { hex(0x64748B, 255) }, "x");
                }
            }

            let header_logo = ((px + 22.0) * k_to_gui, (py + top_h * 0.5) * k_to_gui, 20.0 * k_to_gui);
            let flight_start = ((px + pw * 0.36) * k_to_gui, (py + top_h + 36.0) * k_to_gui);
            let grid_wm = (
                (cx + (cw - 10.0) * 0.5) * k_to_gui,
                (cy + 31.0 + (ch - 31.0) * 0.5) * k_to_gui,
                144.0 * k_to_gui,
            );
            ui.finish();
            (header_logo, flight_start, grid_wm, tel_logo)
        };

        // Subtle official logo watermark inside the module grid
        if self.pvp.astucy.tab == 0 && self.pvp.astucy.editing_mod.is_none() {
            draw_astucy_logo(
                &self.gui.atlas,
                batch,
                grid_wm.0,
                grid_wm.1,
                grid_wm.2,
                0.0,
                0.038 * open_ease,
                false,
            );
        }

        // Spinning mini Astucy Iris logo in the bottom-left telemetry card
        draw_astucy_logo(
            &self.gui.atlas,
            batch,
            tel_logo.0,
            tel_logo.1,
            tel_logo.2,
            -now_secs * 0.9,
            0.85 * open_ease,
            true,
        );

        // Flying + spinning Astucy Iris Logo in the top-left header (`RShiftScreen.java:1516-1526`)
        draw_astucy_logo_travel(
            &self.gui.atlas,
            batch,
            flight_start.0,
            flight_start.1,
            header_logo.0,
            header_logo.1,
            header_logo.2,
            elapsed_ms,
            520.0,
            open_ease,
        );
    }

    /// 1:1 Astucy HUD Editor (`HudEditorScreen.java`): crisp unblurred HUD chips, center snap guides when dragging,
    /// selected chip frame + resize handles + scale badge, 18x18 options button next to hovered/selected chip,
    /// and a clean `BACK TO MENU` button (`156x24`) at the bottom center.
    fn draw_astucy_hud_editor(&mut self, batch: &mut HudBatch) {
        let (cx, cy) = self.cursor;
        let held = self.pvp.astucy.dragging_hud.map(|(i, _, _)| i);
        let hovered = held.or_else(|| {
            self.pvp
                .astucy
                .hud_boxes
                .iter()
                .rev()
                .find(|(_, [x, y, w, h])| cx >= *x && cx <= x + w && cy >= *y && cy <= y + h)
                .map(|(i, _)| *i)
        });
        if held.is_some() {
            self.pvp.astucy.selected_hud = held;
        }

        let mut ui = Ui::new(&self.gui.atlas, batch, self.cursor);
        let (uw, uh) = (ui.w, ui.h);
        let (mx, my) = ui.cursor;
        let k_from_gui = ui.batch.scale / ui.density;

        // Center alignment guides while dragging (`HudEditorScreen.java:462-471`)
        if let Some((drag_idx, _, _)) = self.pvp.astucy.dragging_hud
            && let Some(&(_, [bx, by, bw, bh])) = self.pvp.astucy.hud_boxes.iter().find(|(i, _)| *i == drag_idx)
        {
            let ccx = (bx + bw * 0.5) * k_from_gui;
            let ccy = (by + bh * 0.5) * k_from_gui;
            ui.rounded(uw * 0.5, 0.0, 1.0, uh, 0.0, hex(0x6FE3A2, 52));
            ui.rounded(0.0, uh * 0.5, uw, 1.0, 0.0, hex(0x6FE3A2, 52));
            ui.rounded(ccx, 0.0, 1.0, uh, 0.0, hex(0x6FE3A2, 136));
            ui.rounded(0.0, ccy, uw, 1.0, 0.0, hex(0x6FE3A2, 136));
        }

        // Options button (`18x18`) next to hovered or selected chip (`HudEditorScreen.java:477-487`)
        let gear_target = hovered.or(self.pvp.astucy.selected_hud);
        if let Some(g_idx) = gear_target
            && let Some(&(_, [bx, by, bw, _])) = self.pvp.astucy.hud_boxes.iter().find(|(i, _)| *i == g_idx)
        {
            let gx = (bx + bw) * k_from_gui + 5.0;
            let gy = by * k_from_gui;
            let ghov = mx >= gx && mx < gx + 18.0 && my >= gy && my < gy + 18.0;
            ui.rounded(gx, gy, 18.0, 18.0, 5.0, if ghov { hex(0x131A22, 238) } else { hex(0x0C1117, 214) });
            ui.border(gx, gy, 18.0, 18.0, 5.0, hex(0xFFFFFF, if ghov { 112 } else { 42 }));
            draw_skin_icon(&mut ui, "options", gx + 3.0, gy + 3.0, 12.0, hex(0xFFFFFF, if ghov { 255 } else { 190 }));
        }

        // Selected chip emerald frame + top/bottom resize handles + scale badge (`HudEditorScreen.java:489-505`)
        if let Some(s_idx) = self.pvp.astucy.selected_hud.or(hovered)
            && let Some(&(_, [bx, by, bw, bh])) = self.pvp.astucy.hud_boxes.iter().find(|(i, _)| *i == s_idx)
            && let Some(m) = self.pvp.astucy.mods.get(s_idx)
        {
            let rx = bx * k_from_gui;
            let ry = by * k_from_gui;
            let rw = bw * k_from_gui;
            let rh = bh * k_from_gui;
            ui.border(rx - 2.0, ry - 2.0, rw + 4.0, rh + 4.0, 7.0, hex(0x38C975, 216));
            let hw = (rw - 12.0).clamp(24.0, 44.0);
            let hcx = rx + rw * 0.5 - hw * 0.5;
            ui.rounded(hcx, ry - 7.0, hw, 5.0, 2.5, hex(0x0C1117, 224));
            ui.rounded(hcx, ry + rh + 2.0, hw, 5.0, 2.5, hex(0x0C1117, 224));

            let badge = format!("{}  {:.0}%", m.name, m.scale * 100.0);
            let badge_w = ui.measure(Face::Medium, 11.5, &badge) + 12.0;
            let badge_y = (ry - 22.0).max(4.0);
            ui.rounded(rx - 2.0, badge_y, badge_w, 18.0, 5.5, hex(0x0A0D12, 220));
            ui.text(Face::Medium, 11.5, rx + 4.0, badge_y + 1.5, hex(0x33C46E, 255), &badge);
        }

        // Bottom footer `BACK TO MENU` button (`156x24` at `uw / 2 - 78, uh - 32`, `HudEditorScreen.java:307-312`)
        let bx_exit = uw * 0.5 - 78.0;
        let by_exit = uh - 32.0;
        let hov_exit = mx >= bx_exit && mx < bx_exit + 156.0 && my >= by_exit && my < by_exit + 24.0;
        ui.rounded(
            bx_exit,
            by_exit,
            156.0,
            24.0,
            7.0,
            if hov_exit { hex(0x191E25, 220) } else { hex(0x191E25, 175) },
        );
        ui.border(bx_exit, by_exit, 156.0, 24.0, 7.0, hex(0xFFFFFF, if hov_exit { 78 } else { 36 }));
        ui.text_centered(Face::Medium, 12.0, uw * 0.5, by_exit + 3.5, hex(0xFFFFFF, 255), "BACK TO MENU");
        ui.finish();
    }

    pub(super) fn astucy_mouse_press(&mut self, right: bool) {
        let size = self
            .renderer
            .as_ref()
            .map_or([1280, 720], |r| [r.config.width, r.config.height]);
        let mut dummy = HudBatch::new(size);
        let ui = Ui::new(&self.gui.atlas, &mut dummy, self.cursor);
        let (uw, uh) = (ui.w, ui.h);
        let (mx, my) = ui.cursor;
        let k_from_gui = ui.batch.scale / ui.density;

        if !self.pvp.astucy.in_menu {
            // 1. Bottom `BACK TO MENU` button (`HudEditorScreen.java:182-188`)
            let bx_exit = uw * 0.5 - 78.0;
            let by_exit = uh - 32.0;
            if !right && mx >= bx_exit && mx < bx_exit + 156.0 && my >= by_exit && my < by_exit + 24.0 {
                self.audio.play_button_click();
                self.close_astucy_screen();
                return;
            }

            // 2. Options gear button next to any chip (`HudEditorScreen.java:213-223`)
            for &(idx, [bx, by, bw, _]) in &self.pvp.astucy.hud_boxes {
                let gx = (bx + bw) * k_from_gui + 5.0;
                let gy = by * k_from_gui;
                if mx >= gx - 2.0 && mx < gx + 20.0 && my >= gy - 2.0 && my < gy + 20.0 {
                    self.audio.play_button_click();
                    self.pvp.astucy.editing_mod = Some(idx);
                    self.pvp.astucy.in_menu = true;
                    self.pvp.astucy.tab = 0;
                    self.save_astucy_config();
                    return;
                }
            }

            // 3. Select / drag HUD chip (or right-click to open options)
            let (cx, cy) = self.cursor;
            if let Some(&(idx, [x, y, _, _])) = self
                .pvp
                .astucy
                .hud_boxes
                .iter()
                .rev()
                .find(|(_, [x, y, w, h])| cx >= *x && cx <= x + w && cy >= *y && cy <= y + h)
            {
                self.pvp.astucy.selected_hud = Some(idx);
                if right {
                    self.audio.play_button_click();
                    self.pvp.astucy.editing_mod = Some(idx);
                    self.pvp.astucy.in_menu = true;
                    self.pvp.astucy.tab = 0;
                } else {
                    self.pvp.astucy.dragging_hud = Some((idx, cx - x, cy - y));
                }
            } else {
                self.pvp.astucy.selected_hud = None;
            }
            return;
        }

        // ---- R-SHIFT Menu Clicks ----
        let pw = (uw * 0.86).clamp(660.0, 900.0).round();
        let ph = (uh * 0.84).clamp(420.0, 560.0).round();
        let px = ((uw - pw) * 0.5).round();
        let py = ((uh - ph) * 0.5).round();
        let top_h = 44.0;
        let side_w = (pw / 5.8).clamp(138.0, 168.0).round();

        if mx < px || mx > px + pw || my < py || my > py + ph {
            self.close_astucy_screen();
            return;
        }

        // Close [X] button
        let cbx = px + pw - 32.0;
        let cby = py + (top_h - 22.0) * 0.5;
        if mx >= cbx && mx < cbx + 22.0 && my >= cby && my < cby + 22.0 {
            self.audio.play_button_click();
            self.pvp.astucy.editing_mod = None;
            self.close_astucy_screen();
            return;
        }

        // Dark / Light mode toggle pill next to Account chip
        let player_name = self.options.player_name();
        let uw_chip = (ui.measure(Face::Medium, 12.0, &player_name) + 42.0).clamp(96.0, 180.0);
        let ux_chip = cbx - uw_chip - 8.0;
        let tw_btn = 58.0_f32;
        let tx_btn = ux_chip - tw_btn - 6.0;
        if mx >= tx_btn && mx < tx_btn + tw_btn && my >= cby && my < cby + 22.0 {
            self.audio.play_button_click();
            self.pvp.astucy.light_mode = !self.pvp.astucy.light_mode;
            self.save_astucy_config();
            return;
        }

        // Header Tabs
        let tabs = [("MODULES", 74.0_f32), ("MODS", 54.0), ("SETTINGS", 72.0)];
        let mut tx = px + side_w + 16.0;
        for (ti, &(_, tw)) in tabs.iter().enumerate() {
            if mx >= tx && mx < tx + tw && my >= py + 4.0 && my < py + top_h {
                self.pvp.astucy.tab = ti as u8;
                self.pvp.astucy.editing_mod = None;
                self.pvp.astucy.cat_opened_at = Instant::now();
                self.audio.play_button_click();
                return;
            }
            tx += tw + 6.0;
        }

        // Left Sidebar: Profiles, Save Profile, Edit HUD
        let sy0 = py + top_h + 26.0;
        let bw_prof = side_w - 22.0;
        for i in 0..3 {
            let ry = sy0 + i as f32 * 26.0;
            if mx >= px + 11.0 && mx < px + 11.0 + bw_prof && my >= ry && my < ry + 22.0 {
                let cur = self.pvp.astucy.active_profile.min(2);
                self.pvp.astucy.profiles[cur] = self.pvp.astucy.mods.clone();
                self.pvp.astucy.active_profile = i;
                self.pvp.astucy.mods = self.pvp.astucy.profiles[i].clone();
                self.pvp.astucy.cat_opened_at = Instant::now();
                self.audio.play_button_click();
                self.save_astucy_config();
                return;
            }
        }
        let by_prof = sy0 + 3.0 * 26.0 + 10.0;
        if mx >= px + 11.0 && mx < px + 11.0 + bw_prof && my >= by_prof && my < by_prof + 25.0 {
            self.pvp.astucy.saved_flash_at = Some(Instant::now());
            self.audio.play_button_click();
            self.save_astucy_config();
            return;
        }
        if mx >= px + 11.0 && mx < px + 11.0 + bw_prof && my >= by_prof + 31.0 && my < by_prof + 56.0 {
            self.pvp.astucy.in_menu = false;
            self.audio.play_button_click();
            return;
        }

        let cx = px + side_w + 12.0;
        let cy = py + top_h + 12.0;
        let cw = pw - side_w - 22.0;
        let ch = ph - top_h - 22.0;

        if self.pvp.astucy.tab == 2 {
            for i in 0..4 {
                let ry = cy + 24.0 + i as f32 * 40.0;
                if mx >= cx && mx < cx + cw && my >= ry && my < ry + 34.0 {
                    self.audio.play_button_click();
                    match i {
                        0 => self.pvp.astucy.in_menu = false,
                        1 => self.open_options_at(false, horizon_client::options_ui::Page::Pvp),
                        2 => {
                            let defaults = build_default_modules();
                            for (m, d) in self.pvp.astucy.mods.iter_mut().zip(&defaults) {
                                m.anchor = d.anchor;
                                m.scale = 1.0;
                            }
                            self.save_astucy_config();
                        }
                        _ => {
                            let defaults = build_default_modules();
                            for (m, d) in self.pvp.astucy.mods.iter_mut().zip(&defaults) {
                                m.enabled = d.enabled;
                            }
                            self.save_astucy_config();
                        }
                    }
                    return;
                }
            }
            return;
        }
        if self.pvp.astucy.tab != 0 {
            return;
        }

        // Full Module Options View clicks
        if let Some(edit_idx) = self.pvp.astucy.editing_mod {
            let is_crosshair = self.pvp.astucy.mods.get(edit_idx).is_some_and(|m| m.id == "crosshair");
            if is_crosshair {
                let cell = 11.0_f32;
                let grid_px = 17.0 * cell;
                let gx0 = cx + 8.0;
                let gy0 = cy + 44.0;
                if mx >= gx0 && mx < gx0 + grid_px && my >= gy0 && my < gy0 + grid_px {
                    let px_i = ((mx - gx0) / cell).floor() as usize;
                    let py_i = ((my - gy0) / cell).floor() as usize;
                    if px_i < 17 && py_i < 17 {
                        let paint = !right;
                        self.pvp.astucy.crosshair_paint = Some(paint);
                        if paint {
                            self.pvp.astucy.crosshair_pixels[py_i] |= 1 << px_i;
                        } else {
                            self.pvp.astucy.crosshair_pixels[py_i] &= !(1 << px_i);
                        }
                        self.save_astucy_config();
                    }
                    return;
                }
                let rx = gx0 + grid_px + 20.0;
                let rw = (cx + cw - rx - 8.0).max(160.0);
                let prev_y = gy0 + 16.0;
                let pr_w = ((rw - 16.0) / 5.0).floor();
                let by_p = prev_y + 88.0;
                if my >= by_p && my < by_p + 22.0 {
                    for pi in 0..5_usize {
                        let bx_p = rx + pi as f32 * (pr_w + 4.0);
                        if mx >= bx_p && mx < bx_p + pr_w {
                            self.audio.play_button_click();
                            self.pvp.astucy.crosshair_pixels = crosshair_preset(pi as u8);
                            self.save_astucy_config();
                            return;
                        }
                    }
                }
                let inv_y = prev_y + 122.0;
                if mx >= rx && mx < rx + rw && my >= inv_y && my < inv_y + 24.0 {
                    self.audio.play_button_click();
                    self.pvp.astucy.crosshair_invert = !self.pvp.astucy.crosshair_invert;
                    self.save_astucy_config();
                    return;
                }
                let out_y = inv_y + 30.0;
                if mx >= rx && mx < rx + rw && my >= out_y && my < out_y + 24.0 {
                    self.audio.play_button_click();
                    if let Some(m) = self.pvp.astucy.mods.get_mut(edit_idx) {
                        m.shadow = !m.shadow;
                    }
                    self.save_astucy_config();
                    return;
                }
                let sw_y = out_y + 52.0;
                if mx >= rx && mx < rx + (SWATCHES.len() as f32) * 20.0 && my >= sw_y && my < sw_y + 18.0 {
                    let si = (((mx - rx) / 20.0).floor() as usize).min(SWATCHES.len() - 1);
                    self.audio.play_button_click();
                    self.pvp.astucy.crosshair_invert = false;
                    if let Some(m) = self.pvp.astucy.mods.get_mut(edit_idx) {
                        m.fg_rgb = SWATCHES[si];
                    }
                    self.save_astucy_config();
                    return;
                }
                let by_opt = cy + ch - 26.0;
                if mx >= cx && mx < cx + 138.0 && my >= by_opt && my < by_opt + 23.0 {
                    self.audio.play_button_click();
                    self.pvp.astucy.crosshair_pixels = default_crosshair_pixels();
                    self.pvp.astucy.crosshair_invert = true;
                    if let Some(m) = self.pvp.astucy.mods.get_mut(edit_idx) {
                        m.reset_style();
                    }
                    self.save_astucy_config();
                    return;
                }
                if mx >= cx + 148.0 && mx < cx + 286.0 && my >= by_opt && my < by_opt + 23.0 {
                    self.audio.play_button_click();
                    self.pvp.astucy.editing_mod = None;
                    self.pvp.astucy.cat_opened_at = Instant::now();
                    return;
                }
                return;
            }
            let sx = cx + (cw * 0.44).round();
            let sw_sl = (cw - (sx - cx) - 68.0).clamp(110.0, 220.0);
            let row_y0 = cy + 40.0;
            let row_h = 28.0;

            for ri in 0..9 {
                let ry = row_y0 + ri as f32 * row_h;
                if my >= ry - 2.0 && my < ry + row_h - 2.0 && mx >= cx && mx < cx + cw {
                    self.audio.play_button_click();
                    if let Some(m) = self.pvp.astucy.mods.get_mut(edit_idx) {
                        let frac = ((mx - sx) / sw_sl).clamp(0.0, 1.0);
                        match ri {
                            0 if mx >= sx - 6.0 => m.scale = (0.6 + frac * 1.4).clamp(0.6, 2.0),
                            1 => m.bg = !m.bg,
                            2 if mx >= sx - 6.0 => m.bg_opacity = frac,
                            3 if mx >= sx => {
                                let si = (((mx - sx) / 18.0).floor() as usize).min(SWATCHES.len() - 1);
                                m.bg_rgb = SWATCHES[si];
                            }
                            4 if mx >= sx => {
                                let si = (((mx - sx) / 18.0).floor() as usize).min(SWATCHES.len() - 1);
                                m.fg_rgb = SWATCHES[si];
                            }
                            5 => m.shadow = !m.shadow,
                            6 => m.show_prefix = !m.show_prefix,
                            7 => {
                                self.pvp.astucy.in_menu = false;
                                self.pvp.astucy.selected_hud = Some(edit_idx);
                                self.save_astucy_config();
                                return;
                            }
                            8 if mx >= sx - 6.0 => m.level = frac.clamp(0.05, 1.0),
                            _ => {}
                        }
                    }
                    self.save_astucy_config();
                    return;
                }
            }

            let by_opt = cy + ch - 26.0;
            if mx >= cx && mx < cx + 138.0 && my >= by_opt && my < by_opt + 23.0 {
                self.audio.play_button_click();
                if let Some(m) = self.pvp.astucy.mods.get_mut(edit_idx) {
                    m.reset_style();
                }
                self.save_astucy_config();
                return;
            }
            if mx >= cx + 148.0 && mx < cx + 286.0 && my >= by_opt && my < by_opt + 23.0 {
                self.audio.play_button_click();
                self.pvp.astucy.editing_mod = None;
                self.pvp.astucy.cat_opened_at = Instant::now();
                return;
            }
            return;
        }

        // Category pills & inline Search clicks
        let cats = [
            ("ALL", 0_u8),
            ("NEW", 1),
            ("COMBAT", 2),
            ("INFO", 3),
            ("VISUALS", 4),
            ("UTILITY", 5),
        ];
        let mut fx = cx;
        for &(cname, cid) in &cats {
            let pw_pill = ui.measure(Face::Medium, 11.5, cname) + 18.0;
            if mx >= fx && mx < fx + pw_pill && my >= cy && my < cy + 23.0 {
                self.pvp.astucy.cat_filter = cid;
                self.pvp.astucy.scroll = 0.0;
                self.pvp.astucy.scroll_target = 0.0;
                self.pvp.astucy.cat_opened_at = Instant::now();
                self.audio.play_button_click();
                return;
            }
            fx += pw_pill + 5.0;
        }

        let sb_w = (cx + cw - 10.0 - fx - 6.0).clamp(118.0, 176.0);
        let sb_x = cx + cw - 10.0 - sb_w;
        self.pvp.astucy.search_focused = mx >= sb_x && mx < sb_x + sb_w && my >= cy && my < cy + 23.0;
        if self.pvp.astucy.search_focused && (right || (!self.pvp.astucy.search.is_empty() && mx >= sb_x + sb_w - 18.0)) {
            self.pvp.astucy.search.clear();
            self.pvp.astucy.scroll = 0.0;
            self.pvp.astucy.scroll_target = 0.0;
            return;
        }

        // Module Card Grid clicks
        let grid_y = cy + 31.0;
        let grid_bottom = cy + ch;
        if my < grid_y || my > grid_bottom {
            return;
        }
        let card_w = ((cw - 10.0 - 10.0) * 0.5).floor();
        let card_h = 46.0;
        let row_step = card_h + 9.0;
        let scroll = self.pvp.astucy.scroll;

        let query = self.pvp.astucy.search.trim().to_ascii_lowercase();
        let cat_filter = self.pvp.astucy.cat_filter;
        let filtered: Vec<usize> = self
            .pvp
            .astucy
            .mods
            .iter()
            .enumerate()
            .filter(|(_, m)| {
                let cat_ok = match cat_filter {
                    0 => true,
                    1 => m.is_new,
                    2 => m.cat == ModCat::Combat,
                    3 => m.cat == ModCat::Info,
                    4 => m.cat == ModCat::Visuals,
                    5 => m.cat == ModCat::Utility,
                    _ => true,
                };
                let search_ok = query.is_empty()
                    || m.name.to_ascii_lowercase().contains(&query)
                    || m.desc.to_ascii_lowercase().contains(&query)
                    || m.id.to_ascii_lowercase().contains(&query);
                cat_ok && search_ok
            })
            .map(|(i, _)| i)
            .collect();

        for (slot, &mod_idx) in filtered.iter().enumerate() {
            let col = slot % 2;
            let row = slot / 2;
            let gx = cx + col as f32 * (card_w + 10.0);
            let gy = grid_y + row as f32 * row_step - scroll;
            if mx >= gx && mx < gx + card_w && my >= gy && my < gy + card_h {
                let opt_x = gx + card_w - 66.0;
                let clicked_opt = right || (mx >= opt_x - 2.0 && mx < opt_x + 24.0);
                self.audio.play_button_click();
                if clicked_opt {
                    self.pvp.astucy.editing_mod = Some(mod_idx);
                } else if let Some(m) = self.pvp.astucy.mods.get_mut(mod_idx) {
                    m.enabled = !m.enabled;
                    if mod_idx < 48 {
                        self.pvp.astucy.card_flash[mod_idx] = Some(Instant::now());
                    }
                    self.save_astucy_config();
                }
                return;
            }
        }
    }

    pub(super) fn astucy_mouse_drag(&mut self) {
        if self.pvp.astucy.in_menu {
            if let Some(paint) = self.pvp.astucy.crosshair_paint {
                let size = self
                    .renderer
                    .as_ref()
                    .map_or([1280, 720], |r| [r.config.width, r.config.height]);
                let mut dummy = HudBatch::new(size);
                let ui = Ui::new(&self.gui.atlas, &mut dummy, self.cursor);
                let (uw, uh) = (ui.w, ui.h);
                let (mx, my) = ui.cursor;
                let pw = (uw * 0.86).clamp(660.0, 900.0).round();
                let ph = (uh * 0.84).clamp(420.0, 560.0).round();
                let px = ((uw - pw) * 0.5).round();
                let py = ((uh - ph) * 0.5).round();
                let side_w = (pw / 5.8).clamp(138.0, 168.0).round();
                let cx = px + side_w + 12.0;
                let cy = py + 44.0 + 12.0;
                let cell = 11.0_f32;
                let grid_px = 17.0 * cell;
                let gx0 = cx + 8.0;
                let gy0 = cy + 44.0;
                if mx >= gx0 && mx < gx0 + grid_px && my >= gy0 && my < gy0 + grid_px {
                    let px_i = ((mx - gx0) / cell).floor() as usize;
                    let py_i = ((my - gy0) / cell).floor() as usize;
                    if px_i < 17 && py_i < 17 {
                        if paint {
                            self.pvp.astucy.crosshair_pixels[py_i] |= 1 << px_i;
                        } else {
                            self.pvp.astucy.crosshair_pixels[py_i] &= !(1 << px_i);
                        }
                    }
                }
            }
            return;
        }
        let Some((idx, dx, dy)) = self.pvp.astucy.dragging_hud else {
            return;
        };
        let Some(&(_, [_, _, bw, bh])) = self.pvp.astucy.hud_boxes.iter().find(|(i, _)| *i == idx)
        else {
            return;
        };
        let (sw, sh) = self.gui_size();
        let is_compass = self.pvp.astucy.mods.get(idx).is_some_and(|m| m.id == "direcao");
        let mut nx = if is_compass {
            ((self.cursor.0 - dx + bw * 0.5) / sw.max(1.0)).clamp(0.05, 0.95)
        } else {
            ((self.cursor.0 - dx) / sw.max(1.0)).clamp(0.002, 0.98)
        };
        let mut ny = ((self.cursor.1 - dy) / sh.max(1.0)).clamp(0.002, 0.98);

        // Center snap (`HudEditorScreen.java:88-99`)
        let center_px_x = if is_compass { nx * sw } else { nx * sw + bw * 0.5 };
        let center_px_y = ny * sh + bh * 0.5;
        if (center_px_x - sw * 0.5).abs() <= 4.0 {
            nx = if is_compass { 0.5 } else { ((sw * 0.5 - bw * 0.5) / sw).clamp(0.0, 0.98) };
        }
        if (center_px_y - sh * 0.5).abs() <= 4.0 {
            ny = ((sh * 0.5 - bh * 0.5) / sh).clamp(0.0, 0.98);
        }
        if let Some(m) = self.pvp.astucy.mods.get_mut(idx) {
            m.anchor = [nx, ny];
        }
    }

    pub(super) fn astucy_mouse_release(&mut self) {
        if self.pvp.astucy.crosshair_paint.take().is_some() {
            self.save_astucy_config();
        }
        if self.pvp.astucy.dragging_hud.take().is_some() {
            self.save_astucy_config();
        }
    }

    pub(super) fn astucy_mouse_scroll(&mut self, d: f64) {
        if self.pvp.astucy.in_menu {
            self.pvp.astucy.scroll_target = (self.pvp.astucy.scroll_target - (d as f32) * 38.0).max(0.0);
            return;
        }
        let (cx, cy) = self.cursor;
        let target = self
            .pvp
            .astucy
            .hud_boxes
            .iter()
            .rev()
            .find(|(_, [x, y, w, h])| cx >= *x && cx <= x + w && cy >= *y && cy <= y + h)
            .map(|(i, _)| *i)
            .or(self.pvp.astucy.selected_hud);
        if let Some(idx) = target
            && let Some(m) = self.pvp.astucy.mods.get_mut(idx)
        {
            self.pvp.astucy.selected_hud = Some(idx);
            m.scale = ((m.scale * (1.0 + (d as f32).signum() * 0.05) * 100.0).round() / 100.0).clamp(0.6, 2.0);
            self.save_astucy_config();
        }
    }

    pub(super) fn astucy_key_input(&mut self, key: KeyCode, text: Option<&str>) -> bool {
        if self.screen != Screen::HudEditor {
            return false;
        }
        if key == KeyCode::ShiftRight {
            self.close_astucy_screen();
            return true;
        }
        if !self.pvp.astucy.in_menu {
            return false;
        }
        if self.pvp.astucy.search_focused {
            let instant_cat = Instant::now() - std::time::Duration::from_secs(1);
            match key {
                KeyCode::Backspace => {
                    self.pvp.astucy.search.pop();
                    self.pvp.astucy.scroll = 0.0;
                    self.pvp.astucy.scroll_target = 0.0;
                    self.pvp.astucy.cat_opened_at = instant_cat;
                    return true;
                }
                KeyCode::Enter | KeyCode::NumpadEnter => {
                    self.pvp.astucy.search_focused = false;
                    return true;
                }
                _ => {
                    if let Some(t) = text {
                        for ch in t.chars() {
                            if !ch.is_control() && self.pvp.astucy.search.len() < 24 {
                                self.pvp.astucy.search.push(ch);
                                self.pvp.astucy.scroll = 0.0;
                                self.pvp.astucy.scroll_target = 0.0;
                                self.pvp.astucy.cat_opened_at = instant_cat;
                            }
                        }
                        return true;
                    } else if key == KeyCode::Space && self.pvp.astucy.search.len() < 24 {
                        self.pvp.astucy.search.push(' ');
                        self.pvp.astucy.scroll = 0.0;
                        self.pvp.astucy.scroll_target = 0.0;
                        self.pvp.astucy.cat_opened_at = instant_cat;
                        return true;
                    }
                }
            }
        }
        false
    }
}
