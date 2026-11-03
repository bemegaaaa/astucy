import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { load, type Store } from "@tauri-apps/plugin-store";
import { openUrl } from "@tauri-apps/plugin-opener";

import "@fontsource/dm-sans/400.css";
import "@fontsource/dm-sans/500.css";
import "@fontsource/dm-sans/700.css";
import lockupRaw from "./assets/astucy-lockup.svg?raw";
import symbolUrl from "./assets/astucy-symbol.svg?url";

import "./styles.css";

/* =====================================================================
 * ambiente
 * ===================================================================== */

const isTauri =
  typeof (window as unknown as { __TAURI_INTERNALS__?: unknown })
    .__TAURI_INTERNALS__ !== "undefined";
const win = isTauri ? getCurrentWindow() : null;

function qs<T extends Element>(sel: string): T | null {
  return document.querySelector<T>(sel);
}

function wait(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, ms));
}

/** Texto vindo do Modrinth/arquivos nunca entra como HTML. */
function esc(s: string): string {
  return s.replace(/[&<>"']/g, (c) => {
    switch (c) {
      case "&":
        return "&amp;";
      case "<":
        return "&lt;";
      case ">":
        return "&gt;";
      case '"':
        return "&quot;";
      default:
        return "&#39;";
    }
  });
}

/** invoke com um caminho de demonstração para preview no navegador. */
async function cmd<T>(name: string, args?: Record<string, unknown>): Promise<T> {
  if (isTauri) return invoke<T>(name, args);
  return demo<T>(name, args);
}

/* =====================================================================
 * preview (só no navegador — o app real usa o backend Rust)
 * ===================================================================== */

type DemoCatalog = {
  id: string;
  kind: string;
  released: string;
  installed: boolean;
  client: boolean;
};

function demoCatalog(): DemoCatalog[] {
  const releases = [
    "26.2", "26.1", "1.21.8", "1.21.7", "1.21.5", "1.21.4", "1.21.1",
    "1.20.6", "1.20.4", "1.20.1", "1.19.4", "1.18.2", "1.16.5", "1.12.2",
    "1.8.9", "1.7.10",
  ];
  const installed = new Set(["26.2", "1.21.4", "1.8.9"]);
  return releases.map((id, i) => ({
    id,
    kind: "release",
    released: new Date(2026, 0, 20 - i * 40).toISOString(),
    installed: installed.has(id),
    client: id.startsWith("2"),
  }));
}

function demo<T>(name: string, _args?: Record<string, unknown>): Promise<T> {
  const ok = (v: unknown) => Promise.resolve(v as T);
  switch (name) {
    case "app_info":
      return ok({ name: "Astucy Launcher", version: "0.1.0" });
    case "version_catalog":
      return ok(demoCatalog());
    case "list_versions":
      return ok(
        demoCatalog()
          .filter((v) => v.installed)
          .map((v) => ({
            name: v.id,
            kind: v.kind,
            last_modified: Math.floor(Date.now() / 1000) - 86400,
          })),
      );
    case "system_info":
      return ok({
        total_mb: 7500,
        avail_mb: 3120,
        java: [
          { major: 8, path: "C:\\Program Files\\Java\\jre1.8.0_501\\bin\\javaw.exe" },
          { major: 21, path: "C:\\Program Files\\Eclipse Adoptium\\jdk-21\\bin\\javaw.exe" },
          { major: 25, path: "C:\\Program Files\\Eclipse Adoptium\\jdk-25\\bin\\javaw.exe" },
        ],
        game_dir: "C:\\Users\\PlayerX\\AppData\\Roaming\\.minecraft",
      });
    case "auth_accounts":
      return ok([
        {
          uuid: "00000000000000000000000000000001",
          name: "PlayerX",
          xuid: "",
          skin: null,
          cape: null,
          default: true,
          expires: Date.now() + 86400000,
        },
      ]);
    case "list_mods":
      return ok([
        { file: "sodium.jar", name: "Sodium", enabled: true, size: 1_200_000, core: false, slug: "sodium" },
        { file: "lithium.jar", name: "Lithium", enabled: true, size: 640_000, core: false, slug: "lithium" },
        { file: "iris.jar", name: "Iris", enabled: true, size: 2_400_000, core: false, slug: "iris" },
        { file: "zoomify.jar", name: "Zoomify", enabled: false, size: 120_000, core: false, slug: "zoomify" },
      ]);
    case "modrinth_search":
      return ok({
        hits: [
          {
            slug: "sodium",
            title: "Sodium",
            description: "Renderização moderna para Minecraft — mais FPS e frametime estável.",
            downloads: 42_000_000,
            icon: "",
            author: "CaffeineMC",
          },
          {
            slug: "entityculling",
            title: "Entity Culling",
            description: "Não renderiza entidades escondidas atrás de blocos.",
            downloads: 18_300_000,
            icon: "",
            author: "tr7zw",
          },
          {
            slug: "immediatelyfast",
            title: "ImmediatelyFast",
            description: "Acelera texto, HUD e entidades com buffering em lote.",
            downloads: 9_100_000,
            icon: "",
            author: "RaphiMC",
          },
          {
            slug: "zoomify",
            title: "Zoomify",
            description: "Zoom suave com tecla configurável.",
            downloads: 3_200_000,
            icon: "",
            author: "pumbas",
          },
        ],
        total: 4,
        offset: 0,
        limit: 20,
      });
    case "take_switch_request":
      return ok(null);
    case "auth_client_id":
      return ok("");
    case "install_mods":
      return ok([]);
    case "launch_game":
    case "install_version":
    case "auth_set_default":
    case "auth_remove":
    case "set_mod_enabled":
    case "open_folder":
      return ok(undefined);
    default:
      return ok(undefined);
  }
}

/* =====================================================================
 * tema (claro / escuro / sistema)
 * ===================================================================== */

type ThemeChoice = "light" | "dark" | "system";
let themeChoice: ThemeChoice = "system";

const systemDark = window.matchMedia("(prefers-color-scheme: dark)");

function applyTheme(choice: ThemeChoice, persist = true): void {
  themeChoice = choice;
  const dark = choice === "dark" || (choice === "system" && systemDark.matches);
  const root = document.documentElement;
  root.dataset.theme = dark ? "dark" : "light";
  root.dataset.themeChoice = choice;
  if (persist) {
    try {
      localStorage.setItem("astucy.theme", choice);
    } catch {
      /* sem storage */
    }
  }

  const index = choice === "light" ? 0 : choice === "dark" ? 1 : 2;
  const sw = qs<HTMLElement>("#theme-switch");
  if (sw) sw.style.setProperty("--i", String(index));
  sw?.querySelectorAll<HTMLElement>("button").forEach((b) => {
    b.classList.toggle("is-on", b.dataset.themeSet === choice);
  });
  qs("#theme-cards")
    ?.querySelectorAll<HTMLElement>("[data-theme-choice]")
    .forEach((c) => c.classList.toggle("is-on", c.dataset.themeChoice === choice));
}

function initTheme(): void {
  let saved: string = "system";
  try {
    saved = localStorage.getItem("astucy.theme") ?? "system";
  } catch {
    /* ignora */
  }
  applyTheme(
    saved === "light" || saved === "dark" || saved === "system" ? saved : "system",
    false,
  );

  qs("#theme-switch")?.addEventListener("click", (e) => {
    const btn = (e.target as HTMLElement).closest<HTMLElement>("[data-theme-set]");
    if (!btn?.dataset.themeSet) return;
    applyTheme(btn.dataset.themeSet as ThemeChoice);
  });

  qs("#theme-cards")?.addEventListener("click", (e) => {
    const card = (e.target as HTMLElement).closest<HTMLElement>("[data-theme-choice]");
    if (!card?.dataset.themeChoice) return;
    applyTheme(card.dataset.themeChoice as ThemeChoice);
  });

  systemDark.addEventListener("change", () => {
    if (themeChoice === "system") applyTheme("system", false);
  });
}

/* =====================================================================
 * toasts
 * ===================================================================== */

function toast(message: string, isError = false): void {
  const root = qs("#toasts");
  if (!root) return;
  const el = document.createElement("div");
  el.className = "toast" + (isError ? " is-error" : "");
  el.textContent = message;
  root.appendChild(el);
  window.setTimeout(() => {
    el.classList.add("is-out");
    window.setTimeout(() => el.remove(), 280);
  }, 3600);
}

/* =====================================================================
 * settings persistentes
 * ===================================================================== */

type Settings = {
  ram: number;
  resW: number;
  resH: number;
  jvm: string;
  version: string;
  nick: string;
  closeOnPlay: boolean;
};

const DEFAULTS: Settings = {
  ram: 2560,
  resW: 1280,
  resH: 720,
  jvm: "-XX:+UseG1GC -XX:MaxGCPauseMillis=60 -XX:+DisableExplicitGC",
  version: "",
  nick: "",
  closeOnPlay: true,
};

let store: Store | null = null;

async function loadSettings(): Promise<Settings> {
  return {
    ram: (await store?.get<number>("settings.ram")) ?? DEFAULTS.ram,
    resW: (await store?.get<number>("settings.res_w")) ?? DEFAULTS.resW,
    resH: (await store?.get<number>("settings.res_h")) ?? DEFAULTS.resH,
    jvm: (await store?.get<string>("settings.jvm")) ?? DEFAULTS.jvm,
    version: (await store?.get<string>("settings.version")) ?? DEFAULTS.version,
    nick: (await store?.get<string>("settings.nick")) ?? DEFAULTS.nick,
    closeOnPlay:
      (await store?.get<boolean>("settings.close_on_play")) ?? DEFAULTS.closeOnPlay,
  };
}

/* =====================================================================
 * sistema (RAM + Javas instalados)
 * ===================================================================== */

type SystemInfo = {
  total_mb: number;
  avail_mb: number;
  java: { major: number; path: string }[];
  game_dir: string;
};

let sysInfo: SystemInfo | null = null;

function maxRamMb(): number {
  const total = sysInfo?.total_mb ?? 8192;
  // deixa pelo menos 2 GB para o Windows + navegador
  const top = Math.min(8192, Math.max(2048, Math.floor((total - 1800) / 256) * 256));
  return top;
}

function renderSystem(): void {
  if (!sysInfo) return;
  const total = sysInfo.total_mb;
  const avail = sysInfo.avail_mb;
  const usedPct = total > 0 ? Math.min(100, Math.round(((total - avail) / total) * 100)) : 0;

  const gb = (mb: number) => (mb / 1024).toFixed(1).replace(".", ",");

  const fill = qs<HTMLElement>("#sys-ram-fill");
  if (fill) {
    fill.style.width = `${usedPct}%`;
    fill.classList.toggle("is-tight", usedPct >= 85);
  }
  const sideFill = qs<HTMLElement>("#side-ram-fill");
  if (sideFill) {
    sideFill.style.width = `${usedPct}%`;
    sideFill.classList.toggle("is-tight", usedPct >= 85);
  }

  const label = qs("#sys-ram-label");
  if (label) label.textContent = `${usedPct}% em uso`;
  const text = qs("#sys-ram-text");
  if (text) text.textContent = `${gb(total - avail)} GB usados`;
  const totalEl = qs("#sys-ram-total");
  if (totalEl) totalEl.textContent = `de ${gb(total)} GB`;
  const sideVal = qs("#side-ram-val");
  if (sideVal) sideVal.textContent = `${gb(avail)} GB livre`;
  const ramHint = qs("#ram-hint");
  if (ramHint) ramHint.textContent = `${gb(total)} GB no PC`;

  // Javas: um chip por major, o maior em destaque
  const javaRoot = qs("#sys-java");
  if (javaRoot) {
    const majors = [...new Set(sysInfo.java.map((j) => j.major))].sort((a, b) => a - b);
    const top = majors[majors.length - 1];
    javaRoot.innerHTML = majors.length
      ? majors
          .map(
            (m) =>
              `<span class="chip${m === top ? " is-on" : ""}" title="Java ${m}">${m}</span>`,
          )
          .join("")
      : '<span class="chip">—</span>';
    const statJava = qs("#stat-java");
    if (statJava) statJava.textContent = majors.length ? `Java ${top}` : "—";
  }

  // slider de RAM acompanha a máquina
  const ram = qs<HTMLInputElement>("#ram-range");
  if (ram) {
    const top = maxRamMb();
    ram.max = String(top);
    const maxLabel = qs("#ram-max");
    if (maxLabel) maxLabel.textContent = `${Math.round(top / 1024)} GB`;
    if (Number(ram.value) > top) {
      ram.value = String(top);
      qs("#ram-val")!.textContent = `${top} MB`;
      void store?.set("settings.ram", top);
    }
    paintRange(ram);
  }
}

function paintRange(el: HTMLInputElement): void {
  const min = Number(el.min || 0);
  const max = Number(el.max || 100);
  const val = Number(el.value);
  const pct = max > min ? ((val - min) / (max - min)) * 100 : 0;
  el.style.setProperty("--fill", `${pct}%`);
}

async function refreshSystem(): Promise<void> {
  try {
    sysInfo = await cmd<SystemInfo>("system_info");
    renderSystem();
    updateStats();
  } catch {
    /* sem info do sistema */
  }
}

/* =====================================================================
 * mods: Modrinth + instalados no perfil
 * ===================================================================== */

type ModEntry = {
  file: string;
  name: string;
  enabled: boolean;
  size: number;
  core: boolean;
  slug: string | null;
};
type SearchHit = {
  slug: string;
  title: string;
  description: string;
  downloads: number;
  icon: string;
  author: string;
};
type InstallResult = { slug: string; ok: boolean; detail: string };
type SearchPage = { hits: SearchHit[]; total: number; offset: number; limit: number };

let modsCache: ModEntry[] = [];
let mrOffset = 0;
let mrTotal = 0;
let mrLimit = 20;

function humanSize(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${bytes} B`;
}

function fmtDownloads(n: number): string {
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)}M downloads`;
  if (n >= 1_000) return `${Math.round(n / 1000)}k downloads`;
  return `${n} downloads`;
}

function isInstalledSlug(slug: string): boolean {
  const norm = slug.toLowerCase().replace(/[^a-z0-9]/g, "");
  return modsCache.some((m) => {
    if (m.slug === slug) return true;
    const file = m.name.toLowerCase().replace(/[^a-z0-9]/g, "");
    return norm.length >= 4 && file.includes(norm);
  });
}

async function renderInstalledMods(): Promise<void> {
  const root = qs("#installed-mods");
  if (!root) return;
  try {
    modsCache = await cmd<ModEntry[]>("list_mods");
  } catch (err) {
    root.innerHTML = `<p class="set-note">Falha ao listar: ${esc(String(err))}</p>`;
    return;
  }
  const user = modsCache.filter((m) => !m.core);
  const count = qs("#mods-count");
  if (count) count.textContent = String(user.length);

  root.innerHTML = "";
  if (user.length === 0) {
    root.innerHTML = `<p class="set-note">Nenhum mod extra — o Astucy já vem pronto. Use a busca acima para adicionar.</p>`;
  } else {
    for (const m of user) {
      const row = document.createElement("label");
      row.className = "mod-row" + (m.enabled ? "" : " is-off");
      row.innerHTML = `
        <span class="mod-row-text">
          <span class="mod-row-name">${esc(m.name)}</span>
          <span class="mod-row-meta">${humanSize(m.size)}</span>
        </span>
        <input class="mod-toggle" type="checkbox" ${m.enabled ? "checked" : ""} />
        <span class="switch" aria-hidden="true"></span>`;
      const input = row.querySelector<HTMLInputElement>(".mod-toggle")!;
      input.dataset.file = m.file;
      input.addEventListener("change", () => {
        void (async () => {
          try {
            await cmd("set_mod_enabled", { file: m.file, enabled: input.checked });
            row.classList.toggle("is-off", !input.checked);
            updateStats();
          } catch (err) {
            input.checked = !input.checked;
            toast(String(err), true);
          }
        })();
      });
      root.appendChild(row);
    }
  }
  updateStats();
  document.querySelectorAll<HTMLElement>(".mr-card").forEach((card) => {
    const slug = card.dataset.slug ?? "";
    if (!slug) return;
    const installed = isInstalledSlug(slug);
    card.classList.toggle("is-installed", installed);
    const btn = card.querySelector<HTMLButtonElement>("button.mr-install");
    if (installed && btn) {
      const okk = document.createElement("span");
      okk.className = "mr-ok";
      okk.textContent = "✓ Instalado";
      btn.replaceWith(okk);
    }
  });
}

function mrCard(hit: SearchHit): HTMLElement {
  const installed = isInstalledSlug(hit.slug);
  const card = document.createElement("article");
  card.className = "mr-card" + (installed ? " is-installed" : "");
  card.dataset.slug = hit.slug;

  const icon = document.createElement("span");
  icon.className = "mr-icon";
  if (hit.icon) {
    const img = document.createElement("img");
    img.src = hit.icon;
    img.alt = "";
    img.loading = "lazy";
    icon.appendChild(img);
  } else {
    icon.textContent = (hit.title || "?").slice(0, 1).toUpperCase();
  }

  const body = document.createElement("div");
  body.className = "mr-body";
  body.innerHTML = `
    <span class="mr-title">${esc(hit.title)}</span>
    <span class="mr-desc">${esc(hit.description)}</span>
    <span class="mr-meta">${fmtDownloads(hit.downloads)}${hit.author ? ` · ${esc(hit.author)}` : ""}</span>`;

  const action = document.createElement("div");
  action.className = "mr-action";
  if (installed) {
    const okk = document.createElement("span");
    okk.className = "mr-ok";
    okk.textContent = "✓ Instalado";
    action.appendChild(okk);
  } else {
    const btn = document.createElement("button");
    btn.className = "btn-primary btn-mini mr-install";
    btn.textContent = "Instalar";
    btn.addEventListener("click", () => void installFromCard(hit, btn, card));
    action.appendChild(btn);
  }

  card.append(icon, body, action);
  return card;
}

async function installFromCard(
  hit: SearchHit,
  btn: HTMLButtonElement,
  card: HTMLElement,
): Promise<void> {
  const version = await currentVersion();
  if (!version) {
    toast("Escolha uma versão na aba Versões primeiro.", true);
    return;
  }
  btn.disabled = true;
  btn.textContent = "Baixando…";
  try {
    const res = await cmd<InstallResult[]>("install_mods", { version, slugs: [hit.slug] });
    const main = res.find((r) => r.slug === hit.slug) ?? res[0];
    const deps = res.filter((r) => r.slug !== hit.slug && r.ok).length;
    if (main?.ok) {
      const okk = document.createElement("span");
      okk.className = "mr-ok";
      okk.textContent = deps > 0 ? `✓ Instalado (+${deps})` : "✓ Instalado";
      btn.replaceWith(okk);
      card.classList.add("is-installed");
      toast(`${hit.title} instalado.`);
      await renderInstalledMods();
    } else {
      btn.disabled = false;
      btn.textContent = main?.detail ? `Falhou: ${main.detail}` : "Falhou";
    }
  } catch (err) {
    btn.disabled = false;
    btn.textContent = "Erro";
    toast(String(err), true);
  }
}

async function searchModrinth(offset = 0): Promise<void> {
  const input = qs<HTMLInputElement>("#mr-query");
  const root = qs("#mr-results");
  if (!input || !root) return;

  const version = await currentVersion();
  if (!version) {
    root.innerHTML = `<p class="list-note">Escolha uma versão na aba Versões primeiro.</p>`;
    return;
  }
  mrOffset = Math.max(0, offset);
  root.innerHTML = `<p class="list-note">Buscando no Modrinth…</p>`;
  try {
    const page = await cmd<SearchPage>("modrinth_search", {
      query: input.value.trim(),
      version,
      offset: mrOffset,
      limit: mrLimit,
    });
    mrTotal = page.total;
    if (page.limit > 0) mrLimit = page.limit;

    root.innerHTML = "";
    if (page.hits.length === 0) {
      root.innerHTML = `<p class="list-note">Nada encontrado para Fabric ${esc(version)}.</p>`;
      return;
    }
    for (const hit of page.hits) root.appendChild(mrCard(hit));
    root.appendChild(mrPager());
  } catch (err) {
    root.innerHTML = `<p class="list-note">${esc(String(err))}</p>`;
  }
}

function mrPager(): HTMLElement {
  const pages = Math.max(1, Math.ceil(mrTotal / Math.max(1, mrLimit)));
  const current = Math.floor(mrOffset / Math.max(1, mrLimit)) + 1;

  const wrap = document.createElement("div");
  wrap.className = "mr-pager";

  const prev = document.createElement("button");
  prev.className = "btn-ghost btn-mini";
  prev.textContent = "← Anterior";
  prev.disabled = current <= 1;
  prev.addEventListener("click", () => void searchModrinth(mrOffset - mrLimit));

  const label = document.createElement("span");
  label.className = "mr-page";
  label.textContent = `Página ${current} de ${pages} · ${mrTotal.toLocaleString("pt-BR")} mods`;

  const next = document.createElement("button");
  next.className = "btn-ghost btn-mini";
  next.textContent = "Próxima →";
  next.disabled = current >= pages;
  next.addEventListener("click", () => void searchModrinth(mrOffset + mrLimit));

  wrap.append(prev, label, next);
  return wrap;
}

function initModsPane(): void {
  qs("#mr-search")?.addEventListener("click", () => void searchModrinth());
  qs("#mr-query")?.addEventListener("keydown", (e) => {
    if ((e as KeyboardEvent).key === "Enter") void searchModrinth();
  });
  qs("#btn-open-mods")?.addEventListener("click", () => void openFolder("mods"));
  qs("#btn-refresh-mods")?.addEventListener("click", () => void renderInstalledMods());
  void renderInstalledMods();
}

/* =====================================================================
 * versões: catálogo da Mojang + download
 * ===================================================================== */

type InstalledVersion = { name: string; kind: string; last_modified: number };
type CatalogEntry = {
  id: string;
  kind: string;
  released: string;
  installed: boolean;
  client: boolean;
};

let installedVersions: InstalledVersion[] = [];
let catalog: CatalogEntry[] = [];
let verFilter: "all" | "client" | "legacy" | "installed" = "all";
let verQuery = "";
let catalogLoaded = false;
let installing: string | null = null;

function fmtDate(iso: string): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleDateString("pt-BR");
}

async function currentVersion(): Promise<string> {
  const s = await loadSettings();
  return s.version || installedVersions[0]?.name || "";
}

function fillVersionSelect(selected: string): void {
  const sel = qs<HTMLSelectElement>("#version-select");
  if (!sel) return;

  const names = installedVersions.map((v) => v.name);
  if (selected && !names.includes(selected)) names.unshift(selected);
  sel.innerHTML = "";
  if (names.length === 0) {
    const o = document.createElement("option");
    o.value = "";
    o.textContent = "nenhuma instalada";
    sel.appendChild(o);
    sel.value = "";
    return;
  }
  for (const n of names) {
    const o = document.createElement("option");
    o.value = n;
    o.textContent = n;
    sel.appendChild(o);
  }
  sel.value = selected || names[0];
}

async function loadInstalledVersions(): Promise<void> {
  try {
    installedVersions = await cmd<InstalledVersion[]>("list_versions");
  } catch {
    installedVersions = [];
  }
  // sem versão salva: prefere a mais nova do client (26.x) — não a que o
  // Windows tocou por último
  const fallback =
    installedVersions.find((v) => /^(2[6-9]|[3-9]\d)\./.test(v.name)) ??
    installedVersions[0];
  const selected = await currentVersion();
  if (!selected && fallback) {
    await store?.set("settings.version", fallback.name);
  }
  fillVersionSelect(selected || fallback?.name || "");
  updateHeroVersion(selected || fallback?.name || "");
}

async function loadCatalog(force = false): Promise<void> {
  const grid = qs("#versions-grid");
  if (!grid) return;
  if (!catalogLoaded) grid.innerHTML = `<p class="list-note">Lendo o catálogo da Mojang…</p>`;
  try {
    catalog = await cmd<CatalogEntry[]>("version_catalog", { force });
    catalogLoaded = true;
    renderVersions();
  } catch (err) {
    grid.innerHTML = `<div class="note-card"><span class="note-title">Não deu para ler o catálogo</span><span class="note-desc">${esc(
      String(err),
    )}</span></div>`;
  }
}

function versionMatches(v: CatalogEntry): boolean {
  if (verQuery && !v.id.toLowerCase().includes(verQuery)) return false;
  switch (verFilter) {
    case "client":
      return v.client;
    case "legacy":
      return !v.client;
    case "installed":
      return v.installed;
    default:
      return true;
  }
}

/* ---- famílias: 26.x, 1.21, 1.20, … 1.7 ---- */

type VerFamily = { key: string; hue: number; entries: CatalogEntry[] };

const openFamilies = new Set<string>();
let familiesSeeded = false;

function familyOf(id: string): string {
  const m = /^(\d+)(?:\.(\d+))?/.exec(id);
  if (!m) return "outras";
  const major = Number(m[1]);
  // 26.x é uma família só; o resto agrupa por minor: 1.21, 1.20, ...
  if (major >= 26) return `${major}.x`;
  return `1.${m[2] ?? "0"}`;
}

/** Cor da capa: 26.x verde Astucy, 1.x passeia pelo espectro conforme envelhece. */
function familyHue(key: string): number {
  if (key.endsWith(".x")) return 150;
  const minor = Number(key.split(".")[1] ?? "0");
  return (184 + (21 - minor) * 27 + 720) % 360;
}

function groupFamilies(entries: CatalogEntry[]): VerFamily[] {
  const order: string[] = [];
  const map = new Map<string, CatalogEntry[]>();
  for (const v of entries) {
    const key = familyOf(v.id);
    let bucket = map.get(key);
    if (!bucket) {
      bucket = [];
      map.set(key, bucket);
      order.push(key);
    }
    bucket.push(v);
  }
  return order.map((key) => ({ key, hue: familyHue(key), entries: map.get(key)! }));
}

function versionCard(v: CatalogEntry, selected: string): HTMLElement {
  const card = document.createElement("article");
  const isSel = v.id === selected;
  card.className =
    "version-card" +
    (isSel ? " is-selected" : "") +
    (installing === v.id ? " is-busy" : "");
  card.dataset.id = v.id;

  const badge = v.client
    ? `<span class="vc-badge is-client">Astucy</span>`
    : `<span class="vc-badge">release</span>`;

  const state = v.installed
    ? `<span class="vc-state is-ok">
         <svg viewBox="0 0 16 16" aria-hidden="true"><path d="M3.2 8.4l3 3L12.8 5" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/></svg>
         Instalado
       </span>`
    : `<span class="vc-state">${humanVersionNote(v)}</span>`;

  const action =
    installing === v.id
      ? `<span class="vc-state">baixando…</span>`
      : v.installed
        ? `<button class="vc-btn${isSel ? "" : " is-accent"}" data-act="use">${isSel ? "Em uso" : "Usar"}</button>`
        : `<button class="vc-btn is-accent" data-act="install">Baixar</button>`;

  card.innerHTML = `
    <div class="vc-top">
      <span class="vc-name">${esc(v.id)}</span>
      ${badge}
    </div>
    <div class="vc-meta">
      <span>${fmtDate(v.released)}</span>
      <span class="sep"></span>
      <span>release</span>
    </div>
    ${installing === v.id ? `<div class="vc-progress"><span class="bar"><span></span></span><span data-stage>preparando</span></div>` : ""}
    <div class="vc-actions">${state}${action}</div>`;

  card.addEventListener("click", (e) => {
    const act = (e.target as HTMLElement).closest<HTMLElement>("[data-act]");
    if (act?.dataset.act === "install") {
      void installVersion(v.id);
      return;
    }
    if (!v.installed) {
      toast("Baixe a versão antes de usar.", true);
      return;
    }
    if (v.id === selected) return;
    void selectVersion(v.id);
  });

  return card;
}

async function renderVersions(): Promise<void> {
  const grid = qs("#versions-grid");
  const foot = qs("#versions-foot");
  if (!grid) return;

  const selected = await currentVersion();
  const all = catalog.filter(versionMatches);
  const families = groupFamilies(all);

  grid.innerHTML = "";
  if (families.length === 0) {
    grid.innerHTML = catalogLoaded
      ? `<div class="note-card"><span class="note-title">Nada aqui</span><span class="note-desc">Ajuste o filtro ou busque outro número de versão.</span></div>`
      : `<p class="list-note">Lendo o catálogo da Mojang…</p>`;
    if (foot) foot.textContent = "";
    return;
  }

  if (!familiesSeeded && catalogLoaded) {
    const selFam = all.find((v) => v.id === selected);
    openFamilies.add(selFam ? familyOf(selFam.id) : families[0].key);
    familiesSeeded = true;
  }

  for (const fam of families) {
    const open = !!verQuery || openFamilies.has(fam.key);
    const installedCount = fam.entries.filter((v) => v.installed).length;

    const section = document.createElement("section");
    section.className = "ver-group" + (open ? " is-open" : "");
    section.dataset.family = fam.key;
    section.style.setProperty("--h", String(fam.hue));

    const head = document.createElement("button");
    head.type = "button";
    head.className = "vg-head";
    head.setAttribute("aria-expanded", open ? "true" : "false");
    head.innerHTML = `
      <span class="vg-art" aria-hidden="true"><span class="vg-art-key">${esc(fam.key)}</span></span>
      <span class="vg-text">
        <span class="vg-title">${esc(fam.key)}${
          fam.key.endsWith(".x") ? `<span class="vg-tag">client</span>` : ""
        }<em>${fam.entries.length} ${fam.entries.length === 1 ? "versão" : "versões"}</em></span>
        <span class="vg-sub">mais recente ${esc(fam.entries[0].id)}${
          installedCount
            ? ` · ${installedCount} instalada${installedCount > 1 ? "s" : ""}`
            : ""
        }</span>
      </span>
      <span class="vg-chev" aria-hidden="true">
        <svg viewBox="0 0 20 20"><path d="M5.5 8l4.5 4.5L14.5 8" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
      </span>`;

    const body = document.createElement("div");
    body.className = "vg-body";
    const inner = document.createElement("div");
    inner.className = "versions-grid";
    for (const v of fam.entries) inner.appendChild(versionCard(v, selected));
    body.appendChild(inner);

    head.addEventListener("click", () => {
      const nowOpen = section.classList.toggle("is-open");
      head.setAttribute("aria-expanded", nowOpen ? "true" : "false");
      if (nowOpen) openFamilies.add(fam.key);
      else openFamilies.delete(fam.key);
    });

    section.appendChild(head);
    section.appendChild(body);
    grid.appendChild(section);
  }

  if (foot) {
    const n = all.length;
    foot.innerHTML = `<span>${n.toLocaleString("pt-BR")} ${
      n === 1 ? "versão" : "versões"
    } em ${families.length} ${families.length === 1 ? "família" : "famílias"}</span><span>${
      catalog.filter((v) => v.installed).length
    } instaladas</span>`;
  }
}

function humanVersionNote(v: CatalogEntry): string {
  if (v.kind === "old_beta") return "beta antiga";
  if (v.kind === "old_alpha") return "alpha antiga";
  return "pronta para baixar";
}

async function selectVersion(id: string): Promise<void> {
  await store?.set("settings.version", id);
  fillVersionSelect(id);
  updateHeroVersion(id);
  await renderVersions();
  updateStats();
  toast(`Versão ${id} selecionada.`);
}

async function installVersion(id: string): Promise<void> {
  if (installing) return;
  installing = id;
  await renderVersions();
  try {
    await cmd("install_version", { id });
    const entry = catalog.find((v) => v.id === id);
    if (entry) entry.installed = true;
    installing = null;
    await loadInstalledVersions();
    await selectVersion(id);
    toast(`${id} baixada e pronta.`);
  } catch (err) {
    installing = null;
    await renderVersions();
    toast(String(err), true);
  }
}

function initVersionsPane(): void {
  const search = qs<HTMLInputElement>("#ver-search");
  search?.addEventListener("input", () => {
    verQuery = search.value.trim().toLowerCase();
    void renderVersions();
  });

  qs("#ver-filter")?.addEventListener("click", (e) => {
    const btn = (e.target as HTMLElement).closest<HTMLElement>("[data-filter]");
    if (!btn?.dataset.filter) return;
    verFilter = btn.dataset.filter as typeof verFilter;
    qs("#ver-filter")
      ?.querySelectorAll<HTMLElement>("button")
      .forEach((b) => {
        const on = b === btn;
        b.classList.toggle("is-on", on);
        b.setAttribute("aria-selected", on ? "true" : "false");
      });
    void renderVersions();
  });

  qs("#btn-refresh-catalog")?.addEventListener("click", () => {
    void (async () => {
      const btn = qs<HTMLButtonElement>("#btn-refresh-catalog");
      if (btn) btn.disabled = true;
      await loadCatalog(true);
      if (btn) btn.disabled = false;
      toast("Catálogo atualizado.");
    })();
  });
}

function updateHeroVersion(name: string): void {
  const hv = qs("#hero-tag");
  const pill = qs("#pill-version");
  if (hv) hv.textContent = name || "—";
  if (pill) pill.textContent = name ? `${name}` : "Sem versão";
  const mv = qs("#mods-version");
  if (mv && name) mv.textContent = name;
  updateStats();
}

/* =====================================================================
 * stats
 * ===================================================================== */

function updateStats(): void {
  const v = qs("#stat-version");
  const m = qs("#stat-mods");
  const r = qs("#stat-ram");
  const j = qs("#stat-java");

  if (v) v.textContent = qs("#hero-tag")?.textContent ?? "—";
  if (m) m.textContent = String(modsCache.filter((x) => !x.core).length);
  if (r) r.textContent = `${qs<HTMLInputElement>("#ram-range")?.value ?? DEFAULTS.ram} MB`;
  if (j && sysInfo) {
    const majors = [...new Set(sysInfo.java.map((x) => x.major))].sort((a, b) => a - b);
    j.textContent = majors.length ? `Java ${majors[majors.length - 1]}` : "—";
  }
}

/* =====================================================================
 * launch
 * ===================================================================== */

function setStatus(msg: string, isError = false): void {
  const el = qs("#hero-status");
  if (!el) return;
  el.textContent = msg;
  el.classList.toggle("is-error", isError);
}

function refreshPlayButton(): void {
  const acc = defaultAccount();
  const nick = (qs<HTMLInputElement>("#nick-input")?.value ?? "").trim();
  const btn = qs<HTMLButtonElement>("#btn-play");
  const sub = qs("#play-sub");
  if (!btn || !sub) return;
  const hasVersion = Boolean(qs<HTMLSelectElement>("#version-select")?.value);
  const ok = (acc !== null || nick.length > 0) && hasVersion && !btn.dataset.busy;
  btn.disabled = !ok;
  if (btn.dataset.busy) {
    sub.textContent = "abrindo o jogo…";
  } else if (!hasVersion) {
    sub.textContent = "baixe uma versão";
  } else if (acc) {
    sub.textContent = `${acc.name} · conta original`;
  } else {
    sub.textContent = nick ? `${nick} · offline` : "escolha um nick";
  }
  updateStats();
}

/** Abre o jogo com a conta/nick atual (usado pelo botão e pela troca no jogo). */
async function startLaunch(): Promise<void> {
  const acc = defaultAccount();
  const nick = acc ? acc.name : (qs<HTMLInputElement>("#nick-input")?.value ?? "").trim();
  const version = await currentVersion();
  if ((!nick && !acc) || !version) {
    setStatus("Escolha um nick e uma versão.", true);
    return;
  }
  const btn = qs<HTMLButtonElement>("#btn-play")!;
  btn.dataset.busy = "1";
  refreshPlayButton();
  setStatus(acc ? `Entrando como ${acc.name}…` : "Iniciando launch…");
  try {
    await cmd("launch_game", { nick, version, account: acc?.uuid ?? null });
    // o client é o jogo (Java); a janela do launcher é WebView2 e custa RAM.
    // Fechando ao jogar, o Minecraft fica com a máquina inteira.
    const cfg = await loadSettings();
    if (isTauri && cfg.closeOnPlay) {
      setStatus("Jogo aberto. Fechando o launcher para liberar RAM…");
      window.setTimeout(() => {
        void win?.close().catch(() => {});
      }, 1800);
    } else {
      setStatus("Jogo aberto. Bom jogo!");
      delete btn.dataset.busy;
      refreshPlayButton();
    }
  } catch (err) {
    setStatus(String(err), true);
    toast(String(err), true);
    delete btn.dataset.busy;
    refreshPlayButton();
  }
}

async function handleSwitch(uuid: string): Promise<void> {
  try {
    await cmd("auth_set_default", { uuid });
    await refreshAccounts();
    await startLaunch();
  } catch (err) {
    setStatus(String(err), true);
  }
}

async function wireAccountSwitch(): Promise<void> {
  if (!isTauri) return;
  const pending = await invoke<string | null>("take_switch_request").catch(() => null);
  if (pending) void handleSwitch(pending);
  await listen<{ uuid: string }>("switch-account", (e) => {
    const uuid = e.payload?.uuid;
    if (uuid) void handleSwitch(uuid);
  });
}

async function setupLaunch(): Promise<void> {
  const nickInput = qs<HTMLInputElement>("#nick-input");
  const s = await loadSettings();
  if (nickInput) {
    nickInput.value = s.nick;
    nickInput.addEventListener("input", () => {
      refreshPlayButton();
      updateIdentity();
    });
    nickInput.addEventListener("change", () => {
      void store?.set("settings.nick", nickInput.value.trim());
      updateIdentity();
    });
  }

  qs("#version-select")?.addEventListener("change", (e) => {
    const id = (e.target as HTMLSelectElement).value;
    if (id) void selectVersion(id);
  });

  refreshPlayButton();
  qs("#btn-play")?.addEventListener("click", () => void startLaunch());

  if (!isTauri) return;

  await listen<{ stage: string; index: number; total: number; detail: string }>(
    "launch://stage",
    (e) => {
      const { stage, index, total, detail } = e.payload;
      setStatus(`${stage} ${index}/${total} — ${detail}`);
      if (installing) {
        const stageEl = document.querySelector<HTMLElement>(
          `[data-id="${installing}"] [data-stage]`,
        );
        if (stageEl) stageEl.textContent = stage;
      }
    },
  );
  await listen<{ message: string }>("launch://error", (e) => {
    setStatus(e.payload.message, true);
    toast(e.payload.message, true);
    delete qs<HTMLButtonElement>("#btn-play")!.dataset.busy;
    refreshPlayButton();
  });
  await listen<{ pid: number; version: string }>("launch://done", (e) => {
    setStatus(`Jogo aberto (${e.payload.version}, pid ${e.payload.pid}). Bom jogo!`);
    toast(`Minecraft ${e.payload.version} aberto. Bom jogo!`);
    delete qs<HTMLButtonElement>("#btn-play")!.dataset.busy;
    refreshPlayButton();
  });
}

/* =====================================================================
 * navegação
 * ===================================================================== */

const PANES = ["home", "versions", "mods", "settings"] as const;

function setupNav(): void {
  const nav = qs("#nav")!;

  nav.addEventListener("click", (e) => {
    const btn = (e.target as HTMLElement).closest<HTMLElement>(".nav-item");
    if (!btn) return;
    const target = btn.dataset.pane as (typeof PANES)[number];
    nav.querySelectorAll(".nav-item").forEach((b) => b.classList.remove("is-current"));
    btn.classList.add("is-current");
    for (const p of PANES) {
      const el = qs<HTMLElement>(`#${p}`);
      if (!el) continue;
      el.hidden = p !== target;
      if (!el.hidden) {
        el.style.animation = "none";
        void el.offsetWidth;
        el.style.animation = "";
      }
    }
    if (target === "versions") void loadCatalog();
    if (target === "mods") {
      void renderInstalledMods();
      if (!qs("#mr-results")?.children.length) void searchModrinth();
    }
    if (target === "settings") void refreshSystem();
  });

  // cartão de conta da lateral leva para o início
  qs("#side-account")?.addEventListener("click", () => {
    qs<HTMLElement>('.nav-item[data-pane="home"]')?.click();
  });
}

async function loadVersionBadge(): Promise<void> {
  try {
    const info = await cmd<{ version: string }>("app_info");
    const badge = qs("#app-version");
    if (badge) badge.textContent = `v${info.version}`;
  } catch {
    /* badge estático já no HTML */
  }
}

/* =====================================================================
 * splash
 * ===================================================================== */

const RING_LEN = 647;
const STEPS = Array.from(document.querySelectorAll<HTMLLIElement>("#splash-steps li"));

function setRing(done: number): void {
  const ring = qs<SVGCircleElement>("#ring-fill");
  if (ring) ring.style.strokeDashoffset = String(RING_LEN * (1 - done / STEPS.length));
}

async function playSplash(): Promise<void> {
  for (const li of STEPS) {
    const dot = document.createElement("span");
    dot.className = "step-dot";
    li.prepend(dot);
  }
  const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (reduced) {
    STEPS.forEach((s) => s.classList.add("is-done"));
    setRing(STEPS.length);
    await wait(300);
    return;
  }
  await wait(220);
  for (let i = 0; i < STEPS.length; i++) {
    STEPS[i]!.classList.add("is-active");
    await wait(520);
    STEPS[i]!.classList.remove("is-active");
    STEPS[i]!.classList.add("is-done");
    setRing(i + 1);
    await wait(90);
  }
}

/* =====================================================================
 * identidade: conta Microsoft OU nick offline
 * ===================================================================== */

type AccountView = {
  uuid: string;
  name: string;
  xuid: string;
  skin: string | null;
  cape: string | null;
  default: boolean;
  expires: number;
};

let accounts: AccountView[] = [];
let signinTimer: number | null = null;

function defaultAccount(): AccountView | null {
  return accounts.find((a) => a.default) ?? accounts[0] ?? null;
}

function headUrl(uuid: string): string {
  return `https://mc-heads.net/avatar/${uuid}/48`;
}

function offlineNick(): string {
  return (qs<HTMLInputElement>("#nick-input")?.value ?? "").trim();
}

function activeName(): string {
  const acc = defaultAccount();
  return acc ? acc.name : offlineNick() || "Jogador";
}

function updateIdentity(): void {
  const acc = defaultAccount();
  const name = activeName();

  const greet = qs("#greet-name");
  if (greet) greet.textContent = name;
  const greetHi = qs("#greet-hi");
  if (greetHi) greetHi.textContent = greeting();

  const iname = qs("#identity-name");
  if (iname) iname.textContent = name;
  const isub = qs("#identity-sub");
  if (isub) isub.textContent = acc ? "conta Microsoft" : "modo offline";

  const img = qs<HTMLImageElement>("#identity-img");
  if (img) img.src = acc ? headUrl(acc.uuid) : symbolUrl;

  const gimg = qs<HTMLImageElement>("#greet-avatar img");
  if (gimg) gimg.src = acc ? headUrl(acc.uuid) : symbolUrl;

  const sideImg = qs<HTMLImageElement>("#side-avatar-img");
  if (sideImg) sideImg.src = acc ? headUrl(acc.uuid) : symbolUrl;
  const sideName = qs("#side-name");
  if (sideName) sideName.textContent = name;
  const sideMode = qs("#side-mode");
  if (sideMode) sideMode.textContent = acc ? "conta original" : "offline";
  const sideDot = qs("#side-dot");
  if (sideDot) sideDot.classList.toggle("is-live", acc !== null);

  const pill = qs("#pill-mode");
  if (pill) pill.textContent = acc ? "Conta original" : "Offline";
  pill?.classList.toggle("pill-live", acc !== null);

  const hm = qs("#hero-mode");
  if (hm) hm.textContent = acc ? "conta original" : "modo offline";

  const note = qs("#hero-status");
  if (note) {
    note.textContent = acc
      ? `Entrando com ${acc.name}. A sessão renova sozinha, sem pedir senha de novo.`
      : "Sem conta Microsoft — você joga offline com o nick escolhido no seletor acima.";
  }

  renderIdentityList();
  refreshPlayButton();
}

function greeting(): string {
  const h = new Date().getHours();
  if (h < 6) return "Boa madrugada";
  if (h < 12) return "Bom dia";
  if (h < 19) return "Boa tarde";
  return "Boa noite";
}

function renderIdentityList(): void {
  const root = qs("#identity-list");
  if (!root) return;
  root.innerHTML = "";

  if (accounts.length === 0) {
    root.innerHTML = `<p class="identity-empty">Nenhuma conta conectada.</p>`;
    return;
  }
  for (const a of accounts) {
    const row = document.createElement("div");
    row.className = "identity-row" + (a.default ? " is-current" : "");
    row.innerHTML = `
      <span class="identity-row-avatar"><img src="${headUrl(a.uuid)}" alt="" /></span>
      <span class="identity-row-name">${esc(a.name)}</span>
      <button class="identity-row-x" title="Remover conta" data-remove="${a.uuid}">×</button>`;
    row.addEventListener("click", (e) => {
      if ((e.target as HTMLElement).dataset.remove) return;
      void useAccount(a.uuid);
    });
    row.querySelector<HTMLButtonElement>("[data-remove]")?.addEventListener("click", (e) => {
      e.stopPropagation();
      void removeAccount(a.uuid);
    });
    root.appendChild(row);
  }
}

async function refreshAccounts(): Promise<void> {
  try {
    accounts = await cmd<AccountView[]>("auth_accounts");
  } catch {
    accounts = [];
  }
  updateIdentity();
}

async function useAccount(uuid: string): Promise<void> {
  if (!uuid) return;
  await cmd("auth_set_default", { uuid });
  await refreshAccounts();
  closeIdentityMenu();
  toast("Conta trocada.");
}

async function removeAccount(uuid: string): Promise<void> {
  if (!uuid) return;
  await cmd("auth_remove", { uuid });
  await refreshAccounts();
}

/* ------------------------------------------------------------------ */
/* janela de login                                                     */
/* ------------------------------------------------------------------ */

function openLogin(): void {
  const modal = qs<HTMLElement>("#login-modal");
  if (!modal) return;
  closeIdentityMenu();
  modal.hidden = false;
  const note = qs("#login-note");
  if (note) {
    note.textContent = "";
    note.classList.remove("is-error");
  }
}

function closeLogin(): void {
  const modal = qs<HTMLElement>("#login-modal");
  if (modal) modal.hidden = true;
}

function wireLogin(): void {
  qs("#login-close")?.addEventListener("click", () => closeLogin());
  qs("#login-skip")?.addEventListener("click", () => closeLogin());
  qs("#login-ms")?.addEventListener("click", () => void startBrowserLogin());
  qs("#login-modal")?.addEventListener("click", (e) => {
    if (e.target === e.currentTarget) closeLogin();
  });

  void cmd<string | null>("auth_client_id")
    .then((id) => {
      const codeBtn = qs<HTMLButtonElement>("#login-code");
      if (codeBtn) codeBtn.hidden = !id;
    })
    .catch(() => {});

  qs("#login-code")?.addEventListener("click", () => {
    closeLogin();
    void startSignin();
  });

  if (!isTauri) return;

  void listen<{ done: boolean }>("auth://browser", (e) => {
    if (!e.payload?.done) {
      const note = qs("#login-note");
      if (note) {
        note.textContent = "Login cancelado.";
        note.classList.add("is-error");
      }
      return;
    }
    void finishBrowserLogin();
  });
}

async function startBrowserLogin(): Promise<void> {
  const note = qs("#login-note");
  if (note) {
    note.textContent = "Abrindo o login da Microsoft…";
    note.classList.remove("is-error");
  }
  try {
    await cmd("auth_browser_open");
  } catch (err) {
    if (note) {
      note.textContent = String(err);
      note.classList.add("is-error");
    }
  }
}

async function finishBrowserLogin(): Promise<void> {
  const note = qs("#login-note");
  if (note) {
    note.textContent = "Confirmando a conta…";
    note.classList.remove("is-error");
  }
  const btn = qs<HTMLButtonElement>("#login-ms");
  if (btn) btn.disabled = true;
  try {
    const acc = await cmd<AccountView>("auth_browser_finish");
    await cmd("auth_set_default", { uuid: acc.uuid });
    await refreshAccounts();
    closeLogin();
    setStatus(`Conectado como ${acc.name}.`);
    toast(`Conectado como ${acc.name}.`);
  } catch (err) {
    if (note) {
      note.textContent = String(err);
      note.classList.add("is-error");
    }
  } finally {
    if (btn) btn.disabled = false;
  }
}

function openIdentityMenu(): void {
  const menu = qs<HTMLElement>("#identity-menu");
  const btn = qs<HTMLElement>("#identity-btn");
  if (!menu || !btn) return;
  menu.hidden = false;
  menu.classList.add("is-open");
  btn.setAttribute("aria-expanded", "true");

  // o hero recorta o conteúdo: o menu flutua ancorado no botão
  const r = btn.getBoundingClientRect();
  const width = Math.min(300, window.innerWidth - 24);
  menu.style.width = `${width}px`;
  menu.style.left = `${Math.max(12, Math.min(r.left, window.innerWidth - width - 12))}px`;
  const below = window.innerHeight - r.bottom;
  if (below < 280) {
    menu.style.top = `${Math.max(12, r.top - menu.offsetHeight - 8)}px`;
  } else {
    menu.style.top = `${r.bottom + 8}px`;
  }
}

function closeIdentityMenu(): void {
  const menu = qs<HTMLElement>("#identity-menu");
  const btn = qs<HTMLElement>("#identity-btn");
  if (!menu) return;
  menu.hidden = true;
  menu.classList.remove("is-open");
  btn?.setAttribute("aria-expanded", "false");
}

function stopSignin(): void {
  if (signinTimer !== null) {
    window.clearInterval(signinTimer);
    signinTimer = null;
  }
  const card = qs<HTMLElement>("#signin-card");
  if (card) card.hidden = true;
  const btn = qs<HTMLButtonElement>("#btn-signin");
  if (btn) btn.disabled = false;
}

async function startSignin(): Promise<void> {
  const note = qs("#acct-note");
  const btn = qs<HTMLButtonElement>("#btn-signin");
  const card = qs<HTMLElement>("#signin-card");
  if (btn) btn.disabled = true;
  closeIdentityMenu();
  if (card) card.hidden = false;
  try {
    const flow = await cmd<{
      device_code: string;
      user_code: string;
      verification_uri: string;
      interval: number;
    }>("auth_begin");
    const codeEl = qs("#acct-code-value");
    if (codeEl) codeEl.textContent = flow.user_code;
    qs("#btn-open-link")?.setAttribute("data-url", flow.verification_uri);
    if (note) note.textContent = "Digite o código no site da Microsoft. Esperando você confirmar…";
    void openUrl(flow.verification_uri);

    let busy = false;
    signinTimer = window.setInterval(() => {
      if (busy) return;
      busy = true;
      void (async () => {
        try {
          const res = await cmd<{ status: string; account?: AccountView }>("auth_poll", {
            deviceCode: flow.device_code,
          });
          if (res.status === "done" && res.account) {
            stopSignin();
            await refreshAccounts();
            if (note) note.textContent = `Bem-vindo, ${res.account.name}.`;
            setStatus(`Conectado como ${res.account.name}.`);
          }
        } catch (err) {
          stopSignin();
          if (note) note.textContent = String(err);
        } finally {
          busy = false;
        }
      })();
    }, Math.max(3, flow.interval) * 1000);
  } catch (err) {
    stopSignin();
    if (card) card.hidden = false;
    if (note) note.textContent = String(err);
  }
}

function setupIdentity(): void {
  qs("#identity-btn")?.addEventListener("click", (e) => {
    e.stopPropagation();
    const menu = qs<HTMLElement>("#identity-menu");
    if (menu?.hidden === false) closeIdentityMenu();
    else openIdentityMenu();
  });

  document.addEventListener("click", (e) => {
    const identity = qs("#identity");
    if (identity && !identity.contains(e.target as Node)) closeIdentityMenu();
  });
  window.addEventListener("resize", () => closeIdentityMenu());

  qs("#btn-signin")?.addEventListener("click", () => openLogin());
  qs("#btn-cancel-signin")?.addEventListener("click", () => {
    stopSignin();
    setStatus("Login cancelado.");
  });
  qs("#btn-open-link")?.addEventListener("click", (e) => {
    const url =
      (e.currentTarget as HTMLElement).getAttribute("data-url") || "https://microsoft.com/link";
    void openUrl(url);
  });
  qs("#acct-code-value")?.addEventListener("click", () => {
    const code = qs("#acct-code-value")?.textContent ?? "";
    if (code) {
      void navigator.clipboard.writeText(code);
      toast("Código copiado.");
    }
  });

  void refreshAccounts();
}

/* =====================================================================
 * ajustes
 * ===================================================================== */

async function initStore(): Promise<void> {
  if (isTauri) {
    try {
      store = await load("settings.json", { autoSave: true });
    } catch {
      store = null;
    }
  }
  const s = await loadSettings();

  const ram = qs<HTMLInputElement>("#ram-range");
  const ramVal = qs("#ram-val");
  if (ram && ramVal) {
    ram.value = String(s.ram);
    ramVal.textContent = `${s.ram} MB`;
    paintRange(ram);
    ram.addEventListener("input", () => {
      ramVal.textContent = `${ram.value} MB`;
      paintRange(ram);
      updateStats();
    });
    ram.addEventListener("change", () => {
      void store?.set("settings.ram", Number(ram.value));
      updateStats();
    });
  }

  const w = qs<HTMLInputElement>("#res-w");
  const h = qs<HTMLInputElement>("#res-h");
  if (w) w.value = String(s.resW);
  if (h) h.value = String(s.resH);

  const paintPresets = () => {
    const cw = Number(w?.value ?? 0);
    const ch = Number(h?.value ?? 0);
    qs("#res-presets")
      ?.querySelectorAll<HTMLElement>(".preset")
      .forEach((p) =>
        p.classList.toggle(
          "is-on",
          Number(p.dataset.w) === cw && Number(p.dataset.h) === ch,
        ),
      );
  };
  paintPresets();

  for (const [id, key] of [
    ["#res-w", "settings.res_w"],
    ["#res-h", "settings.res_h"],
  ] as const) {
    const el = qs<HTMLInputElement>(id);
    el?.addEventListener("change", () => {
      void store?.set(key, Math.max(64, Number(el.value) || 0));
      paintPresets();
    });
  }

  qs("#res-presets")?.addEventListener("click", (e) => {
    const p = (e.target as HTMLElement).closest<HTMLElement>("[data-w]");
    if (!p) return;
    if (w) w.value = p.dataset.w!;
    if (h) h.value = p.dataset.h!;
    void store?.set("settings.res_w", Number(p.dataset.w));
    void store?.set("settings.res_h", Number(p.dataset.h));
    paintPresets();
  });

  const jvm = qs<HTMLTextAreaElement>("#jvm-flags");
  if (jvm) {
    jvm.value = s.jvm;
    jvm.addEventListener("change", () => {
      void store?.set("settings.jvm", jvm.value);
    });
  }

  const closeOnPlay = qs<HTMLInputElement>("#close-on-play");
  if (closeOnPlay) {
    closeOnPlay.checked = s.closeOnPlay;
    closeOnPlay.addEventListener("change", () => {
      void store?.set("settings.close_on_play", closeOnPlay.checked);
    });
  }

  qs("#btn-restore")?.addEventListener("click", () => {
    void (async () => {
      await store?.set("settings.ram", DEFAULTS.ram);
      await store?.set("settings.res_w", DEFAULTS.resW);
      await store?.set("settings.res_h", DEFAULTS.resH);
      await store?.set("settings.jvm", DEFAULTS.jvm);
      if (ram && ramVal) {
        ram.value = String(DEFAULTS.ram);
        ramVal.textContent = `${DEFAULTS.ram} MB`;
        paintRange(ram);
      }
      if (w) w.value = String(DEFAULTS.resW);
      if (h) h.value = String(DEFAULTS.resH);
      if (jvm) jvm.value = DEFAULTS.jvm;
      paintPresets();
      updateStats();
      toast("Ajustes restaurados.");
    })();
  });

  /* client id do Azure (opcional) */
  const clientInput = qs<HTMLInputElement>("#client-id");
  try {
    const saved = await cmd<string | null>("auth_client_id");
    if (clientInput && saved) clientInput.value = saved;
  } catch {
    /* sem client id salvo ainda */
  }
  qs("#btn-save-client")?.addEventListener("click", () => {
    void (async () => {
      try {
        await cmd("auth_set_client_id", { id: clientInput?.value ?? "" });
        toast("Client ID salvo.");
      } catch (err) {
        toast(String(err), true);
      }
    })();
  });

  /* datas/caminhos -> explorer */
  document.querySelectorAll<HTMLElement>("[data-folder]").forEach((btn) => {
    btn.addEventListener("click", () => void openFolder(btn.dataset.folder ?? "minecraft"));
  });
}

async function openFolder(kind: string): Promise<void> {
  try {
    await cmd("open_folder", { kind });
  } catch (err) {
    toast(String(err), true);
  }
}

/* =====================================================================
 * bootstrap
 * ===================================================================== */

async function goToHome(): Promise<void> {
  const splash = qs<HTMLElement>("#splash");
  const home = qs<HTMLElement>("#home");
  if (!splash || !home) return;

  splash.classList.add("is-leaving");
  await wait(320);
  splash.hidden = true;
  qs<HTMLElement>("#nav")!.hidden = false;
  home.hidden = false;
  home.style.animation = "pane-in 0.4s var(--ease)";
}

window.addEventListener("DOMContentLoaded", () => {
  for (const sel of ["#splash-lockup", "#brand-lockup", "#hero-lockup"]) {
    const el = qs(sel);
    if (el) el.innerHTML = lockupRaw;
  }

  initTheme();
  void loadVersionBadge();
  setupNav();
  setupIdentity();
  setupLaunch();
  initVersionsPane();
  wireLogin();

  void (async () => {
    await initStore();
    initModsPane();
    // versões primeiro: o botão Jogar depende disso (rápido, só disco)
    await loadInstalledVersions();
    // sistema em paralelo (a varredura de Java pode demorar na 1ª vez)
    void refreshSystem();
    await refreshAccounts();
    await wireAccountSwitch();
    updateStats();
    if (accounts.length === 0) openLogin();
  })();

  // splash só depois dos dados básicos (parece instantâneo)
  void playSplash().then(() => void goToHome());

  // RAM atualiza sozinha (barra viva)
  window.setInterval(() => void refreshSystem(), 5000);

  // catálogo começa a carregar em segundo plano
  void loadCatalog();

  qs("#wc-minimize")?.addEventListener("click", () => void win?.minimize());
  qs("#wc-maximize")?.addEventListener("click", () => void win?.toggleMaximize());
  qs("#wc-close")?.addEventListener("click", () => {
    if (win) void win.hide();
  });
});
