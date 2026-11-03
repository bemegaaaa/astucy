//! Conta Microsoft (OAuth2 device code) → Xbox Live → XSTS → Minecraft Services.
//!
//! Regra de ouro: o token nunca sai do backend. O webview só vê uuid/nome/skin.
//! O refresh token é gravado cifrado com DPAPI (amarrado ao usuário do Windows).

use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::path::PathBuf;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

// `consumers`: é o único tenant que aceita o escopo XboxLive.signin
// (doc do Minecraft auth). `/common` só serve pra conta corporativa, que falha
// depois no Xbox Live.
const DEVICECODE_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
const TOKEN_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const XBL_URL: &str = "https://user.auth.xboxlive.com/user/authenticate";
const XSTS_URL: &str = "https://xsts.auth.xboxlive.com/xsts/authorize";
const MC_LOGIN_URL: &str = "https://api.minecraftservices.com/authentication/login_with_xbox";
const MC_ENTITLEMENTS_URL: &str = "https://api.minecraftservices.com/entitlements/mcstore";
const MC_PROFILE_URL: &str = "https://api.minecraftservices.com/minecraft/profile";
const SCOPE: &str = "XboxLive.signin offline_access";

/* ------------------------------------------------------------------ */
/* login pela janela do navegador (página "Sign in to Minecraft")      */
/* ------------------------------------------------------------------ */
// É o fluxo que a própria página de login do Minecraft usa. O client id é
// público (aparece na URL do login) — não é segredo e não dá acesso a nada.
const MSA_CLIENT: &str = "00000000402b5328";
const LEGACY_AUTH: &str = "https://login.live.com/oauth20_authorize.srf";
const LEGACY_TOKEN: &str = "https://login.live.com/oauth20_token.srf";
const LEGACY_REDIRECT: &str = "https://login.live.com/oauth20_desktop.srf";
const LEGACY_SCOPE: &str = "service::user.auth.xboxlive.com::MBI_SSL";

/// Resultado do login feito na janela (Some = já voltou da Microsoft).
static BROWSER: std::sync::Mutex<Option<Result<String, String>>> = std::sync::Mutex::new(None);

/* ------------------------------------------------------------------ */
/* armazenamento                                                       */
/* ------------------------------------------------------------------ */

#[derive(Serialize, Deserialize, Clone, Default)]
pub struct Account {
    pub uuid: String,
    pub name: String,
    #[serde(default)]
    pub xuid: String,
    #[serde(default)]
    pub skin: Option<String>,
    #[serde(default)]
    pub cape: Option<String>,
    /// refresh token do Microsoft, cifrado (DPAPI, hex)
    #[serde(default)]
    pub ms_refresh: String,
    /// token do Minecraft em cache (para não refazer a cadeia toda)
    #[serde(default)]
    pub mc_token: String,
    #[serde(default)]
    pub mc_expires: u64,
    /// "browser" = login feito na janela da Microsoft (refresh no live.com)
    #[serde(default)]
    pub via: String,
    #[serde(default)]
    pub added: u64,
}

#[derive(Serialize, Deserialize, Default)]
struct Store {
    #[serde(default)]
    default: String,
    #[serde(default)]
    accounts: Vec<Account>,
}

/// O que o frontend pode ver — nunca contém token.
#[derive(Serialize, Clone)]
pub struct AccountView {
    pub uuid: String,
    pub name: String,
    pub xuid: String,
    pub skin: Option<String>,
    pub cape: Option<String>,
    pub default: bool,
    /// quando o token em cache expira (unix); 0 = desconhecido
    pub expires: u64,
}

impl From<&Account> for AccountView {
    fn from(a: &Account) -> Self {
        Self {
            uuid: a.uuid.clone(),
            name: a.name.clone(),
            xuid: a.xuid.clone(),
            skin: a.skin.clone(),
            cape: a.cape.clone(),
            default: false,
            expires: a.mc_expires,
        }
    }
}

/// Sessão pronta para o launch (uso interno do motor).
pub struct Session {
    pub uuid: String,
    pub name: String,
    pub access_token: String,
    pub xuid: String,
}

fn base_dir() -> Result<PathBuf, String> {
    let appdata = std::env::var("APPDATA").map_err(|_| "APPDATA não definido".to_string())?;
    let dir = std::path::Path::new(&appdata).join("com.astucy.launcher");
    std::fs::create_dir_all(&dir).map_err(|e| format!("criar pasta do launcher: {e}"))?;
    Ok(dir)
}

fn accounts_path() -> Result<PathBuf, String> {
    Ok(base_dir()?.join("accounts.json"))
}

fn auth_path() -> Result<PathBuf, String> {
    Ok(base_dir()?.join("auth.json"))
}

fn load_store() -> Store {
    let Ok(path) = accounts_path() else {
        return Store::default();
    };
    let Ok(text) = std::fs::read_to_string(path) else {
        return Store::default();
    };
    serde_json::from_str(&text).unwrap_or_default()
}

fn save_store(store: &Store) -> Result<(), String> {
    let path = accounts_path()?;
    let text = serde_json::to_string_pretty(store).map_err(|e| e.to_string())?;
    write_atomic(&path, &text).map_err(|e| format!("salvar contas: {e}"))
}

/// Grava em .tmp e renomeia: nunca deixa o arquivo pela metade se o processo
/// morrer no meio (isso apagava todas as contas).
fn write_atomic(path: &std::path::Path, text: &str) -> std::io::Result<()> {
    let tmp = path.with_extension("tmp");
    std::fs::write(&tmp, text)?;
    match std::fs::rename(&tmp, path) {
        Ok(()) => Ok(()),
        Err(e) => {
            let _ = std::fs::remove_file(&tmp);
            Err(e)
        }
    }
}

fn now() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

/* ------------------------------------------------------------------ */
/* client id (Azure; campo opcional em Ajustes)                       */
/* ------------------------------------------------------------------ */

/// Client ID público do Astucy (device code / public client — não é segredo).
/// Basta colar aqui o Application (client) ID do app registrado no Azure e
/// TODOS os usuários passam a logar sem configurar nada.
/// Pode ser trocado sem rebuild pela aba Conta → campo avançado.
const DEFAULT_CLIENT_ID: &str = "";

pub fn client_id() -> Option<String> {
    if let Some(id) = stored_client_id() {
        return Some(id);
    }
    let baked = DEFAULT_CLIENT_ID.trim();
    if baked.is_empty() {
        None
    } else {
        Some(baked.to_string())
    }
}

fn stored_client_id() -> Option<String> {
    let path = auth_path().ok()?;
    let text = std::fs::read_to_string(path).ok()?;
    let v: Value = serde_json::from_str(&text).ok()?;
    let raw = v.get("clientId").and_then(|x| x.as_str())?;
    let clean = raw.trim();
    if clean.is_empty() {
        None
    } else {
        Some(clean.to_string())
    }
}

fn need_client_id() -> Result<String, String> {
    client_id().ok_or_else(|| {
        "Falta o client ID do Azure. Registre um app em portal.azure.com (public client flows = Yes) e cole o ID em Ajustes.".to_string()
    })
}

#[tauri::command]
pub fn auth_client_id() -> Option<String> {
    client_id()
}

#[tauri::command]
pub fn auth_set_client_id(id: String) -> Result<(), String> {
    let path = auth_path()?;
    let mut v: Value = std::fs::read_to_string(&path)
        .ok()
        .and_then(|t| serde_json::from_str(&t).ok())
        .unwrap_or_else(|| json!({}));
    if !v.is_object() {
        v = json!({});
    }
    let clean = id.trim();
    if !clean.is_empty() && !is_guid(clean) {
        return Err("Client ID inválido: o Azure usa o formato 8-4-4-4-12 (ex.: 11111111-2222-3333-4444-555555555555).".to_string());
    }
    if clean.is_empty() {
        v.as_object_mut().map(|m| m.remove("clientId"));
    } else {
        v["clientId"] = json!(clean);
    }
    let text = serde_json::to_string_pretty(&v).map_err(|e| e.to_string())?;
    write_atomic(&path, &text).map_err(|e| format!("salvar client id: {e}"))
}

/// Application (client) ID é sempre um GUID.
fn is_guid(s: &str) -> bool {
    s.len() == 36
        && s.chars().enumerate().all(|(i, c)| {
            if matches!(i, 8 | 13 | 18 | 23) {
                c == '-'
            } else {
                c.is_ascii_hexdigit()
            }
        })
}

/* ------------------------------------------------------------------ */
/* DPAPI (refresh token cifrado)                                       */
/* ------------------------------------------------------------------ */

#[cfg(windows)]
fn dpapi(data: &[u8], protect: bool) -> Option<Vec<u8>> {
    use windows_sys::Win32::Foundation::LocalFree;
    use windows_sys::Win32::Security::Cryptography::{
        CryptProtectData, CryptUnprotectData, CRYPT_INTEGER_BLOB,
    };

    let mut input = CRYPT_INTEGER_BLOB {
        cbData: data.len() as u32,
        pbData: data.as_ptr() as *mut u8,
    };
    let mut output = CRYPT_INTEGER_BLOB {
        cbData: 0,
        pbData: std::ptr::null_mut(),
    };
    let ok = unsafe {
        if protect {
            CryptProtectData(
                &mut input,
                std::ptr::null(),
                std::ptr::null(),
                std::ptr::null(),
                std::ptr::null(),
                0,
                &mut output,
            )
        } else {
            CryptUnprotectData(
                &mut input,
                std::ptr::null_mut(),
                std::ptr::null(),
                std::ptr::null(),
                std::ptr::null(),
                0,
                &mut output,
            )
        }
    };
    if ok == 0 || output.pbData.is_null() {
        return None;
    }
    let out = unsafe { std::slice::from_raw_parts(output.pbData, output.cbData as usize).to_vec() };
    unsafe {
        LocalFree(output.pbData as *mut _);
    }
    Some(out)
}

#[cfg(not(windows))]
fn dpapi(_data: &[u8], _protect: bool) -> Option<Vec<u8>> {
    None
}

fn hex_encode(bytes: &[u8]) -> String {
    let mut s = String::with_capacity(bytes.len() * 2);
    for b in bytes {
        s.push_str(&format!("{b:02x}"));
    }
    s
}

fn hex_decode(text: &str) -> Option<Vec<u8>> {
    if text.len() % 2 != 0 {
        return None;
    }
    let mut out = Vec::with_capacity(text.len() / 2);
    let bytes = text.as_bytes();
    let mut i = 0;
    while i < bytes.len() {
        let hi = (bytes[i] as char).to_digit(16)?;
        let lo = (bytes[i + 1] as char).to_digit(16)?;
        out.push((hi * 16 + lo) as u8);
        i += 2;
    }
    Some(out)
}

fn seal(secret: &str) -> String {
    if secret.is_empty() {
        return String::new();
    }
    match dpapi(secret.as_bytes(), true) {
        Some(b) => format!("dpapi:{}", hex_encode(&b)),
        // fail-closed: sem DPAPI o token NAO vai para o disco em claro.
        None => String::new(),
    }
}

fn unseal(stored: &str) -> Option<String> {
    if stored.is_empty() {
        return None;
    }
    if let Some(hex) = stored.strip_prefix("dpapi:") {
        let raw = hex_decode(hex)?;
        let plain = dpapi(&raw, false)?;
        return String::from_utf8(plain).ok();
    }
    if let Some(plain) = stored.strip_prefix("plain:") {
        return Some(plain.to_string());
    }
    Some(stored.to_string())
}

/* ------------------------------------------------------------------ */
/* HTTP                                                                */
/* ------------------------------------------------------------------ */

fn http() -> Result<reqwest::blocking::Client, String> {
    reqwest::blocking::Client::builder()
        .timeout(Duration::from_secs(30))
        .user_agent("AstucyLauncher/0.1")
        .build()
        .map_err(|e| format!("http: {e}"))
}

fn post_form(url: &str, form: &[(&str, &str)]) -> Result<Value, String> {
    let res = http()?
        .post(url)
        .form(form)
        .send()
        .map_err(|e| format!("rede ({url}): {e}"))?;
    let status = res.status();
    let text = res.text().unwrap_or_default();
    let v: Value = serde_json::from_str(&text).unwrap_or_else(|_| json!({ "raw": text }));
    if !status.is_success() && v.get("error").is_none() {
        return Err(format!("HTTP {status} em {url}"));
    }
    Ok(v)
}

fn post_json(url: &str, body: &Value) -> Result<(u16, Value), String> {
    let res = http()?
        .post(url)
        .json(body)
        .send()
        .map_err(|e| format!("rede ({url}): {e}"))?;
    let status = res.status().as_u16();
    let text = res.text().unwrap_or_default();
    let v: Value = serde_json::from_str(&text).unwrap_or_else(|_| json!({ "raw": text }));
    Ok((status, v))
}

fn get_json(url: &str, bearer: &str) -> Result<(u16, Value), String> {
    let res = http()?
        .get(url)
        .bearer_auth(bearer)
        .send()
        .map_err(|e| format!("rede ({url}): {e}"))?;
    let status = res.status().as_u16();
    let text = res.text().unwrap_or_default();
    let v: Value = serde_json::from_str(&text).unwrap_or_else(|_| json!({ "raw": text }));
    Ok((status, v))
}

/* ------------------------------------------------------------------ */
/* cadeia Xbox → Minecraft                                             */
/* ------------------------------------------------------------------ */

struct McSession {
    uuid: String,
    name: String,
    token: String,
    expires_at: u64,
    xuid: String,
    skin: Option<String>,
    cape: Option<String>,
}

fn xsts_message(code: i64) -> &'static str {
    match code {
        2148916233 => "Essa conta Microsoft não tem conta Xbox. Crie uma em xbox.com e tente de novo.",
        2148916235 => "Conta Xbox bloqueada na sua região.",
        2148916236 | 2148916237 => "Conta Xbox precisa de verificação de adulto (xbox.com).",
        2148916238 => "Conta infantil: o responsável precisa liberar o acesso ao Minecraft.",
        2148916227 => "Conta Xbox suspensa (banida).",
        _ => "O Xbox recusou o login dessa conta.",
    }
}

fn ms_to_mc(ms_access: &str) -> Result<McSession, String> {
    ms_to_mc_ticket(&format!("d={ms_access}"))
}

/// `ticket`: RpsTicket já no formato do Xbox (`d=<token>` no fluxo novo;
/// token cru quando vem do live.com, que já começa com "EwA").
fn ms_to_mc_ticket(ticket: &str) -> Result<McSession, String> {
    // 1) Xbox Live
    let (status, xbl) = post_json(
        XBL_URL,
        &json!({
            "Properties": {
                "AuthMethod": "RPS",
                "SiteName": "user.auth.xboxlive.com",
                "RpsTicket": ticket
            },
            "RelyingParty": "http://auth.xboxlive.com",
            "TokenType": "JWT"
        }),
    )?;
    if status != 200 {
        return Err(format!("Xbox Live recusou o token Microsoft (HTTP {status})."));
    }
    let xbl_token = xbl
        .get("Token")
        .and_then(|t| t.as_str())
        .ok_or("Xbox Live não devolveu token")?
        .to_string();

    // 2) XSTS
    let (status, xsts) = post_json(
        XSTS_URL,
        &json!({
            "Properties": {
                "SandboxId": "RETAIL",
                "UserTokens": [xbl_token]
            },
            "RelyingParty": "rp://api.minecraftservices.com/",
            "TokenType": "JWT"
        }),
    )?;
    if status != 200 {
        let code = xsts.get("XErr").and_then(|c| c.as_i64()).unwrap_or(0);
        return Err(xsts_message(code).to_string());
    }
    let xsts_token = xsts
        .get("Token")
        .and_then(|t| t.as_str())
        .ok_or("XSTS não devolveu token")?
        .to_string();
    let claims = xsts
        .get("DisplayClaims")
        .and_then(|d| d.get("xui"))
        .and_then(|x| x.get(0));
    let uhs = claims
        .and_then(|c| c.get("uhs"))
        .and_then(|u| u.as_str())
        .unwrap_or_default()
        .to_string();
    let xuid = claims
        .and_then(|c| c.get("xid"))
        .and_then(|u| u.as_str())
        .unwrap_or_default()
        .to_string();

    // 3) Minecraft
    let (status, mc) = post_json(
        MC_LOGIN_URL,
        &json!({ "identityToken": format!("XBL3.0 x={uhs};{xsts_token}") }),
    )?;
    if status != 200 {
        return Err("O Minecraft recusou o login do Xbox.".to_string());
    }
    let token = mc
        .get("access_token")
        .and_then(|t| t.as_str())
        .ok_or("Minecraft não devolveu access_token")?
        .to_string();
    let expires_in = mc.get("expires_in").and_then(|e| e.as_u64()).unwrap_or(86400);

    // 4) possui o jogo?
    let (status, ent) = get_json(MC_ENTITLEMENTS_URL, &token)?;
    if status == 401 {
        return Err("Token do Minecraft inválido (401). Tente entrar de novo.".to_string());
    }
    let owns = ent
        .get("items")
        .and_then(|i| i.as_array())
        .map(|items| {
            items.iter().any(|it| {
                it.get("name")
                    .and_then(|n| n.as_str())
                    .map(|n| n == "product_minecraft" || n == "game_minecraft")
                    .unwrap_or(false)
            })
        })
        .unwrap_or(false);
    if !owns {
        return Err("Essa conta não tem Minecraft: Java Edition comprado.".to_string());
    }

    // 5) perfil + skin
    let (status, profile) = get_json(MC_PROFILE_URL, &token)?;
    if status != 200 {
        return Err(format!("Não consegui ler o perfil do Minecraft (HTTP {status})."));
    }
    let uuid = profile
        .get("id")
        .and_then(|s| s.as_str())
        .ok_or("perfil sem id")?
        .to_string();
    let name = profile
        .get("name")
        .and_then(|s| s.as_str())
        .ok_or("perfil sem nome")?
        .to_string();
    let skin = profile
        .get("skins")
        .and_then(|s| s.as_array())
        .and_then(|a| a.first())
        .and_then(|s| s.get("url"))
        .and_then(|u| u.as_str())
        .map(str::to_string);
    let cape = profile
        .get("capes")
        .and_then(|c| c.as_array())
        .and_then(|a| a.first())
        .and_then(|c| c.get("url"))
        .and_then(|u| u.as_str())
        .map(str::to_string);

    Ok(McSession {
        uuid,
        name,
        token,
        expires_at: now() + expires_in.saturating_sub(120),
        xuid,
        skin,
        cape,
    })
}

fn upsert(session: &McSession, refresh: &str) -> Result<AccountView, String> {
    upsert_src(session, refresh, "")
}

fn upsert_src(session: &McSession, refresh: &str, via: &str) -> Result<AccountView, String> {
    // fail-closed: se o DPAPI nao proteger, o login aborta em vez de
    // gravar refresh token / access token em texto puro no disco.
    let ms_refresh = seal(refresh);
    if !refresh.is_empty() && ms_refresh.is_empty() {
        return Err("O Windows não deixou cifrar o token (DPAPI). Login cancelado por segurança.".into());
    }
    let mc_token = seal(&session.token);
    if !session.token.is_empty() && mc_token.is_empty() {
        return Err("O Windows não deixou cifrar o token (DPAPI). Login cancelado por segurança.".into());
    }
    let mut store = load_store();
    let account = Account {
        uuid: session.uuid.clone(),
        name: session.name.clone(),
        xuid: session.xuid.clone(),
        skin: session.skin.clone(),
        cape: session.cape.clone(),
        ms_refresh,
        mc_token,
        mc_expires: session.expires_at,
        via: via.to_string(),
        added: now(),
    };
    if let Some(existing) = store.accounts.iter_mut().find(|a| a.uuid == account.uuid) {
        let added = existing.added;
        *existing = account;
        existing.added = added;
    } else {
        store.accounts.push(account);
    }
    if store.default.is_empty() || !store.accounts.iter().any(|a| a.uuid == store.default) {
        store.default = session.uuid.clone();
    }
    save_store(&store)?;
    let mut view = AccountView {
        uuid: session.uuid.clone(),
        name: session.name.clone(),
        xuid: session.xuid.clone(),
        skin: session.skin.clone(),
        cape: session.cape.clone(),
        default: store.default == session.uuid,
        expires: session.expires_at,
    };
    view.default = store.default == view.uuid;
    Ok(view)
}

/* ------------------------------------------------------------------ */
/* comandos                                                            */
/* ------------------------------------------------------------------ */

/// Passo 1: pede o código de 8 dígitos e a URL que o usuário abre no navegador.
#[tauri::command(async)]
pub async fn auth_begin() -> Result<Value, String> {
    tauri::async_runtime::spawn_blocking(|| -> Result<Value, String> {
        let client = need_client_id()?;
        let v = post_form(
            DEVICECODE_URL,
            &[("client_id", client.as_str()), ("scope", SCOPE)],
        )?;
        if let Some(err) = v.get("error").and_then(|e| e.as_str()) {
            return Err(format!("Microsoft recusou o pedido: {err}"));
        }
        Ok(json!({
            "device_code": v.get("device_code").and_then(|x| x.as_str()).unwrap_or_default(),
            "user_code": v.get("user_code").and_then(|x| x.as_str()).unwrap_or_default(),
            "verification_uri": v.get("verification_uri").and_then(|x| x.as_str()).unwrap_or("https://microsoft.com/link"),
            "interval": v.get("interval").and_then(|x| x.as_u64()).unwrap_or(5),
            "expires_in": v.get("expires_in").and_then(|x| x.as_u64()).unwrap_or(900),
            "message": v.get("message").and_then(|x| x.as_str()).unwrap_or_default(),
        }))
    })
    .await
    .map_err(|e| format!("thread: {e}"))?
}

/// Passo 2: consulta o token. Retorna status = pending | done.
#[tauri::command(async)]
pub async fn auth_poll(device_code: String) -> Result<Value, String> {
    tauri::async_runtime::spawn_blocking(move || -> Result<Value, String> {
        let client = need_client_id()?;
        let v = post_form(
            TOKEN_URL,
            &[
                ("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
                ("client_id", client.as_str()),
                ("device_code", device_code.as_str()),
            ],
        )?;
        if let Some(err) = v.get("error").and_then(|e| e.as_str()) {
            return match err {
                "authorization_pending" | "slow_down" => Ok(json!({ "status": "pending" })),
                "expired_token" => Err("O código expirou. Gere outro.".to_string()),
                "authorization_declined" => Err("Login cancelado no navegador.".to_string()),
                other => Err(format!("Microsoft: {other}")),
            };
        }
        let access = v
            .get("access_token")
            .and_then(|x| x.as_str())
            .ok_or("Microsoft não devolveu access_token")?;
        let refresh = v
            .get("refresh_token")
            .and_then(|x| x.as_str())
            .ok_or("Microsoft não devolveu refresh_token")?;
        let session = ms_to_mc(access)?;
        let view = upsert(&session, refresh)?;
        Ok(json!({ "status": "done", "account": view }))
    })
    .await
    .map_err(|e| format!("thread: {e}"))?
}

/// Lista de contas (sem tokens).
#[tauri::command]
pub fn auth_accounts() -> Vec<AccountView> {
    let store = load_store();
    store
        .accounts
        .iter()
        .map(|a| {
            let mut v = AccountView::from(a);
            v.default = store.default == a.uuid;
            v
        })
        .collect()
}

#[tauri::command]
pub fn auth_set_default(uuid: String) -> Result<(), String> {
    let mut store = load_store();
    if !store.accounts.iter().any(|a| a.uuid == uuid) {
        return Err("Conta não encontrada.".to_string());
    }
    store.default = uuid;
    save_store(&store)
}

#[tauri::command]
pub fn auth_remove(uuid: String) -> Result<(), String> {
    let mut store = load_store();
    store.accounts.retain(|a| a.uuid != uuid);
    if store.default == uuid {
        store.default = store.accounts.first().map(|a| a.uuid.clone()).unwrap_or_default();
    }
    save_store(&store)
}


/// Renova (se preciso) e devolve um token válido — chamado pelo motor antes do launch.
pub fn session_for(uuid: &str) -> Result<Session, String> {
    let store = load_store();
    let account = store
        .accounts
        .iter()
        .find(|a| a.uuid == uuid)
        .ok_or("Conta não encontrada no launcher.")?
        .clone();

    // token do Minecraft ainda vale? usa direto (evita bater na Microsoft)
    let mc_token = unseal(&account.mc_token).unwrap_or_default();
    if !mc_token.is_empty() && account.mc_expires > now() {
        return Ok(Session {
            uuid: account.uuid,
            name: account.name,
            access_token: mc_token,
            xuid: account.xuid,
        });
    }

    // login feito na janela da Microsoft: renova no live.com (mesmo client)
    if account.via == "browser" {
        let refresh = unseal(&account.ms_refresh)
            .ok_or("Sessão expirada: entre de novo com a Microsoft.")?;
        let v = post_form(
            LEGACY_TOKEN,
            &[
                ("client_id", MSA_CLIENT),
                ("grant_type", "refresh_token"),
                ("refresh_token", refresh.as_str()),
                ("scope", LEGACY_SCOPE),
            ],
        )?;
        if let Some(err) = v.get("error").and_then(|e| e.as_str()) {
            return Err(if err == "invalid_grant" {
                "Sessão expirou. Entre de novo com a Microsoft.".to_string()
            } else {
                format!("Microsoft: {err}")
            });
        }
        let access = v
            .get("access_token")
            .and_then(|x| x.as_str())
            .ok_or("refresh sem access_token")?;
        let new_refresh = v
            .get("refresh_token")
            .and_then(|x| x.as_str())
            .unwrap_or(refresh.as_str())
            .to_string();
        let ticket = if access.starts_with("EwA") {
            access.to_string()
        } else {
            format!("d={access}")
        };
        let session = ms_to_mc_ticket(&ticket)?;
        upsert_src(&session, &new_refresh, "browser")?;
        return Ok(Session {
            uuid: session.uuid,
            name: session.name,
            access_token: session.token,
            xuid: session.xuid,
        });
    }

    let refresh = unseal(&account.ms_refresh).ok_or("Sessão expirada: entre de novo com a Microsoft.")?;
    let client = need_client_id()?;
    let v = post_form(
        TOKEN_URL,
        &[
            ("grant_type", "refresh_token"),
            ("client_id", client.as_str()),
            ("refresh_token", refresh.as_str()),
            ("scope", SCOPE),
        ],
    )?;
    if let Some(err) = v.get("error").and_then(|e| e.as_str()) {
        return Err(match err {
            "invalid_grant" => "Sessão expirou. Entre de novo com a Microsoft.".to_string(),
            other => format!("Microsoft: {other}"),
        });
    }
    let ms_access = v
        .get("access_token")
        .and_then(|x| x.as_str())
        .ok_or("refresh sem access_token")?;
    let new_refresh = v
        .get("refresh_token")
        .and_then(|x| x.as_str())
        .unwrap_or(refresh.as_str());
    let session = ms_to_mc(ms_access)?;
    upsert(&session, new_refresh)?;
    Ok(Session {
        uuid: session.uuid,
        name: session.name,
        access_token: session.token,
        xuid: session.xuid,
    })
}

/* ------------------------------------------------------------------ */
/* login na janela da Microsoft (página "Sign in to Minecraft")        */
/* ------------------------------------------------------------------ */

fn url_decode(s: &str) -> String {
    let bytes = s.as_bytes();
    let mut out = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        match bytes[i] {
            b'%' if i + 2 < bytes.len() => {
                let hex = std::str::from_utf8(&bytes[i + 1..i + 3]).unwrap_or("");
                match u8::from_str_radix(hex, 16) {
                    Ok(v) => {
                        out.push(v);
                        i += 3;
                    }
                    Err(_) => {
                        out.push(bytes[i]);
                        i += 1;
                    }
                }
            }
            b'+' => {
                out.push(b' ');
                i += 1;
            }
            c => {
                out.push(c);
                i += 1;
            }
        }
    }
    String::from_utf8_lossy(&out).to_string()
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

/// Valor de um parâmetro da query string (?a=b&c=d).
fn url_param(url: &str, name: &str) -> Option<String> {
    let query = url.split_once('?')?.1;
    for pair in query.split('&') {
        let (k, v) = pair.split_once('=').unwrap_or((pair, ""));
        if k == name {
            return Some(url_decode(v));
        }
    }
    None
}

/// Abre a janela da Microsoft. Quando a URL de retorno chega com o `code`,
/// guarda o código, avisa o frontend e para a navegação.
#[tauri::command(async)]
pub async fn auth_browser_open(app: tauri::AppHandle) -> Result<(), String> {
    use tauri::{Emitter, Manager};

    if let Some(w) = app.get_webview_window("login") {
        let _ = w.close();
    }
    if let Ok(mut g) = BROWSER.lock() {
        *g = None;
    }

    let url = format!(
        "{}?client_id={}&scope={}&response_type=code&redirect_uri={}",
        LEGACY_AUTH,
        MSA_CLIENT,
        url_encode(LEGACY_SCOPE),
        url_encode(LEGACY_REDIRECT)
    );
    let parsed = tauri::Url::parse(&url).map_err(|e| format!("url de login: {e}"))?;

    let handle = app.clone();
    tauri::WebviewWindowBuilder::new(&app, "login", tauri::WebviewUrl::External(parsed))
        .title("Entrar com Microsoft")
        .inner_size(470.0, 720.0)
        .min_inner_size(360.0, 520.0)
        .center()
        .on_navigation(move |u| {
            let s = u.as_str();
            if s.starts_with(LEGACY_REDIRECT) {
                let code = url_param(s, "code");
                let err = url_param(s, "error_description").or_else(|| url_param(s, "error"));
                let res = match code {
                    Some(c) if !c.is_empty() => Ok(c),
                    _ => Err(err.unwrap_or_else(|| "Login cancelado.".to_string())),
                };
                let done = res.is_ok();
                if let Ok(mut g) = BROWSER.lock() {
                    *g = Some(res);
                }
                let _ = handle.emit("auth://browser", json!({ "done": done }));
                return false;
            }
            true
        })
        .build()
        .map_err(|e| format!("abrir janela de login: {e}"))?;
    Ok(())
}

/// Troca o código pelo token do Minecraft e salva a conta.
#[tauri::command(async)]
pub async fn auth_browser_finish(app: tauri::AppHandle) -> Result<AccountView, String> {
    use tauri::Manager;
    if let Some(w) = app.get_webview_window("login") {
        let _ = w.close();
    }
    tauri::async_runtime::spawn_blocking(move || -> Result<AccountView, String> {
        let res = BROWSER
            .lock()
            .ok()
            .and_then(|mut g| g.take())
            .ok_or("Nenhum login em andamento.")?;
        let code = res?;
        let v = post_form(
            LEGACY_TOKEN,
            &[
                ("client_id", MSA_CLIENT),
                ("grant_type", "authorization_code"),
                ("code", code.as_str()),
                ("redirect_uri", LEGACY_REDIRECT),
                ("scope", LEGACY_SCOPE),
            ],
        )?;
        if let Some(err) = v.get("error").and_then(|e| e.as_str()) {
            return Err(format!("Microsoft recusou o login: {err}"));
        }
        let access = v
            .get("access_token")
            .and_then(|x| x.as_str())
            .ok_or("Microsoft não devolveu access_token")?
            .to_string();
        let refresh = v
            .get("refresh_token")
            .and_then(|x| x.as_str())
            .unwrap_or_default()
            .to_string();
        let ticket = if access.starts_with("EwA") {
            access
        } else {
            format!("d={access}")
        };
        let session = ms_to_mc_ticket(&ticket)?;
        upsert_src(&session, &refresh, "browser")
    })
    .await
    .map_err(|e| format!("thread: {e}"))?
}

#[tauri::command]
pub fn auth_browser_close(app: tauri::AppHandle) {
    use tauri::Manager;
    if let Ok(mut g) = BROWSER.lock() {
        *g = None;
    }
    if let Some(w) = app.get_webview_window("login") {
        let _ = w.close();
    }
}
