# Tauri 2 — APIs verificadas (Fase 0, descoberta)

Fontes oficiais lidas: `v2.tauri.app` (create-project, prerequisites, updater, sidecar, shell,
resources, window-customization, store, stronghold, global-shortcut, single-instance, system-tray,
reference/config), `docs.rs` (tauri-plugin-shell 2.5.0), `crates.io` API.
Status: **verificado** salvo itens marcados `UNVERIFIED`.

## 1. Criação do projeto

- Wizard oficial: `cargo install create-tauri-app` → `cargo create-tauri-app`.
- Template **Rust (cargo)** existe; para UI: templates `Vanilla`, `Yew`, `Leptos`, `Sycamore`.
- **Não existe UI em widgets nativos Rust sob Tauri.** O Tauri sempre renderiza via WebView
  (wry/WebView2 no Windows). UI também disponível como HTML/CSS estático servido pelo Tauri
  (`build.frontendDist` apontando para uma pasta; dev server embutido com hot-reload).
- Decisão Astucy: template **vanilla-ts (Vite)** + lib `motion` para animações (sem framework pesado).

## 2. Spwanning de processo externo (javaw.exe)

Duas rotas, ambas válidas (verificadas em docs.rs `tauri-plugin-shell` v2.5.0):

**A. Plugin shell** (`app.shell().command(...)`):
```rust
Command::args(...) / .arg(...) / .env(k,v) / .env_clear() / .current_dir(path)
Command::set_raw_out(bool) / .set_process_group(bool)   // Windows: job object (mata a árvore)
Command::spawn() -> (Receiver<CommandEvent>, CommandChild)
CommandEvent::{Stdout(Vec<u8>), Stderr(..), Error(..), Terminated(TerminatedPayload)}
// TerminatedPayload { code: Option<i32>, signal: Option<i32> }  <- código de saída está AQUI no spawn
CommandChild.write(bytes)  // stdin
```
Permissões: lado JS precisa de capability (`shell:allow-spawn` com `sidecar: false` e args);
**chamadas Rust-side não exigem capability** (frame dos docs; tratar como verificado no comportamento dos docs).

**B. `std::process::Command` puro** para spawn destacado (jogo sobrevive ao launcher):
```rust
std::process::Command::new("javaw.exe").args([...]).env("K","V").spawn()
    .map(|child| { std::mem::forget(child); })
```
- Não existe `spawn_detached` no plugin shell. Flags Win32 via `std::os::windows::process::CommandExt::creation_flags`
  — combinações exatas (CREATE_NO_WINDOW/BREAKAWAY) = `UNVERIFIED`, checar docs da Microsoft na implementação.
- Receita stdout→UI: `tauri::async_runtime::spawn` + `rx.recv()` + `app.emit(...)` por linha.

## 3. Updater (`tauri-plugin-updater` v2) — chaves exatas

```json
"bundle": { "createUpdaterArtifacts": true },
"plugins": { "updater": {
  "pubkey": "<CONTEÚDO da chave pública minisign, não caminho>",
  "endpoints": ["https://...");
  "windows": { "installMode": "passive" }
} }
```
- Assinatura: `npm run tauri signer generate -- -w ~/.tauri/astucy.key` (`TAURI_SIGNING_PRIVATE_KEY` via env;
  `.env` NÃO funciona no build).
- Host: JSON estático com `platforms["windows-x86_64"] = {url, signature}` ou servidor dinâmico
  (HTTP 204 = sem update; 200 + `{version, url, signature, notes, pub_date}`).
- `tauri-action` (GitHub Action) gera `latest.json` automaticamente.
- Windows: app é reiniciado/instalado pelo instalador (limitação do instalador).

## 4. Janela (config exato, `WindowConfig`)

```json
{ "label":"main", "width":800, "height":600, "minWidth":1000, "minHeight":640,
  "decorations": false,          // titlebar custom (tutorial oficial)
  "transparent": true,
  "noRedirectionBitmap": true,   // evita flash branco em janela transparente (Windows)
  "shadow": true,                // Win11: cantos arredondados de graça
  "windowEffects": { "effects": ["mica"|"acrylic"|"blur"], "color": {...} } }
```
- Raio de canto custom no Windows: **não existe chave** — é Win11 nativo (`shadow:true`) + CSS.
- Titlebar: `data-tauri-drag-region` + permissões `core:window:allow-close/minimize/toggle-maximize/start-dragging`.
- Tamanhos em **pixels lógicos** (DPI-aware).

## 5. Bundling (JRE, assets)

- `bundle.resources`: globs → `$RESOURCE/`; resolver com
  `app.path().resolve("jre/bin/javaw.exe", BaseDirectory::Resource)`.
- `bundle.externalBin`: sidecars com sufixo `-<triple>` (p.ex. `tool-x86_64-pc-windows-msvc.exe`).
- JRE (árvore de arquivos) → **`bundle.resources` da pasta `jre/`** + `std::process::Command`.
- `externalBin` exige plugin shell; Flutter-estilo `app.tree` = `UNVERIFIED` (não usar).

## 6. Settings + segredos

- Settings: `tauri-plugin-store` v2 (`store:default` concede todos os comandos; `autoSave` debounce).
- Segredos: **não existe plugin oficial de DPAPI**; `win-dpapi`/`dpapi` NÃO EXISTEM no crates.io (verificado).
  Opções: `tauri-plugin-stronghold` (vault com senha argon2) ou **crate `keyring` v4.2.0**
  (Windows Credential Manager; assinatura exata = `UNVERIFIED`, checar antes da P4).

## 7. Residente

- Tray: feature `tray-icon` do core (`tauri = { version="2", features=["tray-icon"] }`).
- `tauri-plugin-single-instance`: **registrar como PRIMEIRO plugin** (antes de todos).
- `tauri-plugin-global-shortcut`: 5 permissões default **negadas** — opt-in nas capabilities.

## 8. Build/toolchain

- Windows 7+; WebView2 Evergreen (install mode configurável, traversal: `UNVERIFIED`多项).
- Toolchain: MSVC Build Tools, Rust stable, tauri-cli, VBSCRIPT opcional só para MSI.
- `build.windows.staticVCRuntime` default `true`. Comandos: `cargo tauri dev` / `cargo tauri build`.
- Plugins exigem **Rust 1.90+**.

## Anti-padrões (NÃO fazer)

1. Não inventar `spawn_detached` ou API de DPAPI oficial do Tauri (não existe).
2. Não usar `.env` para a chave de update do Tauri (só env var real).
3. Não esquecer `noRedirectionBitmap: true` em janela transparente (flash branco).
4. Não registrar single-instance depois de outros plugins (é a primeira).
5. Não assumir permissão shell no JS sem capability (só Rust-side é livre).
6. Não inferir `TerminatedPayload.code` como "exit no evento Error" — código de saída vive em `Terminated`.
