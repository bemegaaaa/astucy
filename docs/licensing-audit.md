# Astucy — Licensing & Distribution Audit

Date: 2026-10-22 — Scope: `.mrpack` format, per-mod licenses, Mojang EULA/Usage Guidelines, Hypixel policy, launcher precedents.

**Rule in force:** no third-party client proprietary code/assets/jars are downloaded or redistributed. Licensing is resolved from Modrinth/GitHub public project pages.

---

## 1. Sources consulted

| Source | Used for |
|---|---|
| https://support.modrinth.com/en/articles/8802351-modrinth-modpack-format-mrpack | mrpack spec (primary) |
| https://api.modrinth.com/v2/project/{slug} — sodium, lithium, ferrite-core, entityculling, immediatelyfast, moreculling, badoptimizations, reeses-sodium-options, sodium-extra, cit-resewn, mouse-tweaks, cloth-config, fabric-api, modmenu, placeholder-api, rrls, iris, fresh-animations, fresh-animations-extensions, fa-player-extension, in-game-account-switcher, cit-resewn-continuation, ias, entity-xray (search) | ids, licenses, loaders, project-body permission text |
| https://github.com/CaffeineMC/sodium/blob/dev/LICENSE.md (raw) | PolyForm Shield full text |
| https://github.com/tr7zw/EntityCulling/blob/1.18/LICENSE-EntityCulling (raw) | tr7zw Protective License text |
| https://api.github.com/repos/{FabricMC/fabric-loader, PolyMC/PolyMC, ATLauncher/ATLauncher, PrismLauncher/PrismLauncher, MultiMC/Launcher, dima-dencep/rrls} | launcher/mod licenses |
| https://support.hypixel.net/hc/en-us/articles/6472550754962-Hypixel-Allowed-Modifications | server policy |
| https://www.minecraft.net/en-us/eula and https://www.minecraft.net/en-us/usage-guidelines | Mojang rules (text retrieved via search-indexed copies; direct fetch timed out) |
| https://www.curseforge.com/minecraft/mc-mods/entityxray | Entity X-Ray nature |

---

## 2. Concrete findings

### 2.1 `.mrpack` format (from Modrinth support article — authoritative)

- ZIP, MIME `application/x-modrinth-modpack+zip`, metadata at `modrinth.index.json` (UTF-8, zip root).
- Top-level keys: `formatVersion` (number, currently **1**), `game` (`"minecraft"`), `versionId`, `name`, `summary` (optional), `files[]`, `dependencies{}`.
- Each `files[]` entry:
  - `path` — relative to instance dir. **Must not** contain `..`, a leading `/`/`\`, or a drive prefix (`C:/`) — security validation required in importers.
  - `hashes` — **MUST contain `sha1` and `sha512`**; other hashes optional/ignored.
  - `env` (optional) — `{ "client": ..., "server": ... }`, each `required` | `optional` | `unsupported`.
  - `downloads[]` — HTTPS URLs; URIs must be RFC 3986-clean; follow ≥3 redirects. Modrinth upload whitelist: `cdn.modrinth.com`, `github.com`, `raw.githubusercontent.com`, `gitlab.com`.
  - `fileSize` (optional).
- `dependencies` — object of ID→version strings, e.g. `{"minecraft": "...", "fabric-loader": "..."}`.
- `overrides/` dir → copied to instance root on install. (`server-overrides/` exists in ecosystem tooling for dedicated-server-only files — verify against the app if used.)
- **There is NO `links` field in the current spec** (checked the article; its absence is also consistent with third-party exporters that mirror the key set `formatVersion,game,versionId,name,summary,files,dependencies`). If a prior draft reference mentioned `links.file`, it is not part of the format today.

### 2.2 Mod table

| Mod | Modrinth id / slug | License (SPDX as declared) | Loaders | Redistribute via launcher download (Modrinth CDN)? | Notes |
|---|---|---|---|---|---|
| Sodium | `AANobbMI` / `sodium` | **LicenseRef-Polyform-Shield-1.0.0** | fabric, neoforge, quilt | ⚠️ REQUIRES LEGAL DECISION | Noncompete: "Any purpose is a permitted purpose, except for providing any product that competes with the software..."; "Goods and services compete even when they provide functionality through different kinds of interfaces... If you market a product as a practical substitute for the software... it definitely competes." An FPS/optimized client may be deemed a competing product (this is why some clients don't ship Sodium). If excluded by interpretation, either get written permission from CaffeineMC or swap to an alternative rendering stack. At minimum: never re-host the jar; download from Modrinth CDN + pass through license notice. |
| Lithium | `gvQqBUqZ` / `lithium` | LGPL-3.0-only | fabric, neoforge, quilt | ✅ | Attribution; LGPL applies to the mod artifact only. |
| FerriteCore | `uXXizFIs` / `ferrite-core` | MIT | fabric, forge, neoforge, quilt | ✅ | |
| Entity Culling | `NNAgCjsB` / `entityculling` | LicenseRef-tr7zw-Protective-License | fabric, forge, neoforge, quilt | ✅ (Modrinth/CF-hosted packs) | Project body: "Feel free to use this mod in your Modrinth and CurseForge-hosted modpacks or YouTube videos without asking for permission. **Do not redistribute the JAR files anywhere else!**" → download from CDN, never bundle/re-host. License itself forbids commercial advantage/monetary compensation from the software. |
| ImmediatelyFast | `5ZwdcRci` / `immediatelyfast` | LGPL-3.0-or-later | fabric, forge, neoforge, quilt | ✅ | |
| More Culling | `51shyZVL` / `moreculling` | GPL-3.0-only | fabric, neoforge, quilt | ✅ | GPL applies to the mod file; runtime download fine. |
| BadOptimizations | `g96Z4WVZ` / `badoptimizations` | MIT | fabric, forge, neoforge | ✅ | |
| Reese's Sodium Options | `Bh37bMuy` / `reeses-sodium-options` | MIT | fabric, neoforge, quilt | ✅ | Depends on Sodium (same PolyForm risk propagates transitively only if bundling — not for reference-download packs, but note the icon/branding and dependencies). |
| Sodium Extra | `PtjYWJkn` / `sodium-extra` | LGPL-3.0-only | fabric, neoforge, quilt | ✅ | Body: "include it in your mod pack as long as you provide attribution!" |
| CIT Resewn | `otVJckYQ` / `cit-resewn` | MIT | **fabric only** | ✅ | Original by SHsuperCM; stale on new MC versions. |
| CIT Resewn Continuation | `8auYYPlH` / `cit-resewn-continuation` | MIT | fabric, **neoforge** | ✅ | The actual fork: EthanVisagie/CIT-Resewn-Continuation (GitHub, 1.21.x). "Modpack Use Welcome" badge. Prefer this for current MC versions. |
| Mouse Tweaks | `aC3cM3Vq` / `mouse-tweaks` | BSD-3-Clause | fabric, forge, modloader, neoforge | ✅ | |
| Cloth Config API | `9s6osm5g` / `cloth-config` | LGPL-3.0-only | fabric, forge, neoforge | ✅ | |
| Fabric API | `P7dR8mSH` / `fabric-api` | Apache-2.0 | **fabric** | ✅ | |
| Mod Menu | `mOgUt4GM` / `modmenu` | MIT | fabric, quilt | ✅ | |
| Text Placeholder API | `eXts2L7r` / `placeholder-api` | LGPL-3.0-only | fabric, quilt | ✅ | Fabric-origin library. |
| Remove Reloading Screen (RRLS) | `ZP7xHXtw` / `rrls` | **OSL-3.0** | fabric, forge, neoforge, quilt | ✅ with conditions | Maintainer: dima-dencep. Body: pack use OK with attribution; **"Can I re-host/upload this mod somewhere else? No."** → CDN-reference only. OSL-3.0 is OSI-approved but not copyleft-compatible with GPL linking models; fine for aggregation. |
| In-Game Account Switcher (IAS) | `cudtvDnd` / `in-game-account-switcher` | LGPL-3.0-or-later | fabric, forge, neoforge, quilt | ✅ | Source: The-Fireplace-Minecraft-Mods/In-Game-Account-Switcher. ⚠ Privacy note: handles account credentials; only works with accounts the user owns — read repo `docs/TERMS.md`/`docs/CRYPT.md` before shipping. |
| Entity X-Ray | ❌ **not on Modrinth** | CurseForge `entityxray` (Fu4th, MIT, project 1369366) | fabric/quilt | — | **REPORT-ONLY verdict: cheat-adjacent ESP.** CF description: "See entities through walls...", glow-through-walls, "entity xray / entity ESP", works "where allowed". Exclusion is the right call — see Hypixel §2.4 (aesthetic mods "must not... allow them to see around or over objects they normally wouldn't"; xray = banned advantage). **EXCLUDE.** |
| Iris Shaders | `YL57xq9U` / `iris` | LGPL-3.0-only | fabric, neoforge, quilt | ✅ | License-clean; excluded from base pack by design is fine as *optional* component (Hypixel: shaders allowed as aesthetic). |
| Fresh Animations (resource pack) | `50dA9Sha` / `fresh-animations` | LicenseRef-See-Terms-of-Use-in-Description (custom ToS) | resource pack (`minecraft`) | ⚠ Reference-download only | FreshLX ToS: "include this resource pack in your mod pack or server" — allowed; but "**YOU MAY NOT redistribute this resource pack**" and no unedited assets in other packs w/o permission. Do NOT put the zip in `overrides/`; use a `downloads[]` entry pointing at the official Modrinth file + credit. Also needs EMF (Entity Model Features) or OptiFine on the client. |
| Fresh Animations: Extensions ("FA+") | `YAVTU8mK` / `fresh-animations-extensions`; player ext `TAIMVZCL` / `fa-player-extension` | **LicenseRef-All-Rights-Reserved** | resource packs | ⚠ Restricted | Official FreshLX packs; same ToS family (credit + pack inclusion, no redistribution/re-host). Note some FA+ content is gated behind paid Ko-fi early-access tiers — only reference *publicly downloadable* versions. |
| Fabric Loader | (github.com/FabricMC/fabric-loader) | **Apache-2.0** (verified) | loader | ✅ | Free to bundle/loader-embed by Astucy itself. |

**Loader-agnostic summary (Fabric-only exclusivity):** Sodium-ecosystem mods are now mostly tri-loader (fabric/neoforge/quilt); still **Fabric-only**: `fabric-api`, `modmenu`, `placeholder-api`, original `cit-resewn` (Continuation adds neoforge), `fresh-animations`/FA+ (packs, no loader). If Astucy is Fabric-based, none of these block you.

### 2.3 Mojang EULA / Usage Guidelines (launchers)

Verified stances — quotes:

- Distribution prohibition: "you must not distribute anything we've made unless we specifically agree to it. By 'distribute anything we've made' what we mean is: give copies of our game software or content to anyone else; make commercial use of anything we've made; try to make money from anything we've made; or let other people get access to anything we've made in a way that is unfair or unreasonable." (EULA)
- The rule that forces runtime download: "In order to ensure the integrity of our games, we need all game downloads and updates to come from a source that we authorize." (EULA) + Usage Guidelines: "**Do not redistribute our games or any alterations of our games or game files**."
- Mods: "Basically, Mods are okay to distribute; hacked versions or **Modded Versions** of the game client or server software are not okay to distribute." A mod is "an original plug-in that extends the functionality of Minecraft but that doesn't contain a substantial part of our copyrightable code or content. You may not distribute any Modded Versions of our game or software."
- Branding/naming: "Do not do anything or include anything that makes people think that what you are sharing could be interpreted as official or approved by, endorsed by, associated with, supported by, or connected to us"; and the mandated disclaimer: "**Prominently include the disclaimer similar to the following: 'NOT AN OFFICIAL MINECRAFT [PRODUCT/SERVICE/EVENT/etc.]. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT' on your product, listing, description, website/webpage, and all other related materials.**"
- Sources: https://www.minecraft.net/en-us/eula and https://www.minecraft.net/en-us/usage-guidelines.

Practical implication for Astucy: never ship a `client.jar`/libraries. After the user signs in with **their own** Microsoft account (OAuth), download everything from Mojang endpoints (`piston-meta.mojang.com`, `libraries.minecraft.net`, `resources.download.minecraft.net`) — this is the established compliant pattern; name "Astucy ... for Minecraft" without implying officialness, and show the required disclaimer.

### 2.4 Hypixel policy (https://support.hypixel.net/hc/en-us/articles/6472550754962)

- Allowed categories: **Client Performance Improvement** (FPS), **Aesthetic** ("must not change the properties of blocks (e.g. make non-transparent blocks transparent) or change the player's perspective"), **Cosmetic HUD** ("without adding extra information which would normally be unavailable... mini-maps, other player health/armor indicators, player distance/range, etc. are not"), **Brightness & Gamma**.
- Disallowed (default): "any modification which provides any significant advantage to the players in any of our games"; automation "strictly disallowed"; and "modifications that alter the way in which your Minecraft client interacts with and communicates with our server are also strictly disallowed."
- Modpacks/clients: permitted "**provided the included modifications are permitted on our server by themselves**" — "please check that each individual feature... is permitted."
- Verbatim policy mapping: performance mods incl. Sodium-family ✅; Iris/shaders ✅ (aesthetic); Fresh Animations ✅ (aesthetic, no perspective change); JEM/entity-culling ✅; IAS ✅ (account mgmt, no gameplay change); entity x-ray/ESP ❌ (perspective advantage — confirms exclusion); anything sending custom packets to the server ❌.

### 2.5 Open-source launcher precedents (models, not code)

- **Prism Launcher** — GPL-3.0 (verified via GitHub API). Auth: Microsoft account OAuth (device-code flow) with the user's own MSA. Java: system JVM auto-discovery + guided download of a runtime. Updates: built-in updater with release channels (stable/beta/devel) off GitHub releases.
- **ATLauncher** — GPL-3.0 (verified). Auth: Microsoft OAuth login in-app. Java: scans the machine, can fetch Mojang-vendored runtimes. Updates: in-app update channel + per-pack incremental versioning.
- **MultiMC lineage** — original MultiMC repo is license-flagged `NOASSERTION` (custom terms); PolyMC (`GPL-3.0`, verified) forked it, and Prism forked PolyMC. Same auth/Java/update pattern as above. None of them ships Mojang jars — they all fetch from Mojang's CDN post-authentication.

---

## 3. Copy-ready `modrinth.index.json` skeleton (real field names)

```json
{
  "formatVersion": 1,
  "game": "minecraft",
  "versionId": "astucy-v1.0.0",
  "name": "Astucy",
  "summary": "Astucy — performance-optimized Minecraft client pack",
  "files": [
    {
      "path": "mods/sodium-mc1.21.11-fabric-0.7.1.jar",
      "hashes": {
        "sha1": "cc297357ff0031f805a744ca3a1378a112c2ddf4",
        "sha512": "d0760a2df6f123fb3546080a85f3a44608e1f8ad9f9f7c57b5380cf72235ad380a5bbd494263639032d63bb0f0c9e0847a62426a6028a73a4b4c8e7734b4e8f5"
      },
      "env": { "client": "required", "server": "unsupported" },
      "downloads": [
        "https://cdn.modrinth.com/data/AANobbMI/versions/xxx/sodium-mc1.21.11-fabric-0.7.1.jar"
      ],
      "fileSize": 1500000
    },
    {
      "path": "resourcepacks/FreshAnimations_v1.9.zip",
      "hashes": { "sha1": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "sha512": "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e" },
      "env": { "client": "required", "server": "unsupported" },
      "downloads": [ "https://cdn.modrinth.com/data/50dA9Sha/versions/xxx/FreshAnimations_v1.9.zip" ],
      "fileSize": 9000000
    }
  ],
  "dependencies": {
    "minecraft": "1.21.11",
    "fabric-loader": "0.18.4"
  }
}
```

Ship as `astucy.mrpack` = ZIP containing `modrinth.index.json` + `overrides/` (config, options.txt, etc.). Update deltas = new `versionId` with only changed `files[]` entries; the launcher ingests via Modrinth's install semantics (verify sha1+sha512, safe path check, follow redirects).

---

## 4. Confidence & gaps

**High confidence:** every license value in the table was read live from the Modrinth API this session; mrpack schema from the official support article; Hypixel policy quoted from the support article; PolyForm-Shield and tr7zw license texts read in full; Prism/ATLauncher/PolyMC/Fabric-loader licenses verified via GitHub API.

**Gaps / follow-ups:**
1. **Sodium risk** — PolyForm-Shield's Noncompete is the single largest open legal question for a "performance client" product; recommendation stands: obtain written permission from CaffeineMC, or treat Sodium as reference-download-only and keep Astucy's positioning neutral (launcher+pack, not "a replacement for Sodium"). Not legal advice.
2. **minecraft.net direct fetch timed out** — EULA/Usage-Guidelines quotes were captured verbatim from search-indexed copies of those exact pages; re-check the live pages when finalizing docs (URLs same).
3. **`links` field** — does not exist in the current spec; nothing to implement. Confirm no internal doc assumed it.
4. **MultiMC license** — `NOASSERTION` on GitHub (custom document); not fetched verbatim this session. Only affects precedent notes, not Astucy's obligations.
5. **Fresh Animations / FA+** — redistribution wording is custom ToS (not a machine-readable SPDX). Reference-download + credit is the safe path; prefer a quick OK from FreshLX before launch. FA+ partial early-access tiers → only wire publicly released versions.
6. **IAS** — verify the shipped version's credential handling (TERMS/CRYPT docs) satisfies your privacy policy before bundling the reference.
7. **Entity Culling "hosted modpacks" wording** — its explicit permission names Modrinth/CurseForge-hosted packs; if Astucy distributes `.mrpack` files from its own site rather than via Modrinth, prefer distribution through Modrinth (or ask tr7zw) to stay squarely within the granted term.
