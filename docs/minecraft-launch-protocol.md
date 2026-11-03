# Protocolo de launch Minecraft Java (26.x) + Fabric — verificado (Fase 0)

Fontes primárias **fetched live nesta descoberta**: piston-meta/launchermeta (version manifest v2,
version JSONs 26.1/26.2/26.3, asset index, catálogo de runtimes Java), meta.fabricmc.net,
maven.fabricmc.net, fonte Apache-2.0 do Fabric Loader (Knot/MinecraftGameProvider/SystemProperties),
minecraft.wiki (autenticação). **Nenhum código proprietário de clients de terceiros foi lido.**
Itens `UNVERIFIED` explícitos ao final.

## 1. Manifest de versões

```
GET https://piston-meta.mojang.com/mc/game/version_manifest_v2.json
```
```json
{ "latest": { "release": "26.3", "snapshot": "26.4-snapshot-5" },
  "versions": [ { "id":"26.3", "type":"release", "complianceLevel":1,
                  "url":"https://piston-meta.mojang.com/v1/packages/<sha1>/26.3.json",
                  "sha1":"61ff..f1318ed" } ] }
```
- URL do version JSON é content-addressed; **validar sha1 do JSON** antes de usar.

## 2. Version JSON (26.3 — valores verbatim)

- `mainClass`: `net.minecraft.client.main.Main` (vanilla).
- `downloads.client`: `https://piston-data.mojang.com/v1/objects/e877b6a07acd633fb3bb475002175cec036e7b87/client.jar` (41.483.720 B, sha1 igual à URL).
- `assetIndex`: id `34` (26.1→`30`, 26.2→`32`); índice tem **apenas `objects`** (sem `virtual`/`map_to_resources`).
- `javaVersion`: `{ "component": "java-runtime-epsilon", "majorVersion": 25 }` (26.1/26.2/26.3 idênticos) → **Java 25 mínimo**.
- `logging.client`: `argument = -Dlog4j.configurationFile=${path}`; XML em
  `https://piston-data.mojang.com/v1/objects/39384bd14c0606d812afec88d8aff595b2587dd9/client-1.21.2.xml`.
- `libraries`: 114 entradas; formato moderno **sem `downloads.classifiers`/`extract`**:
  ```json
  { "name": "org.lwjgl:lwjgl:3.4.3:natives-windows",
    "rules": [ { "action": "allow", "os": { "name": "windows" } } ],
    "downloads": { "artifact": { "url": "https://libraries.minecraft.net/org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3-natives-windows.jar", "size": ..., "sha1": ... } } }
  ```
  Regras: avaliar primeira regra que casa, default `disallow`; há gates por `arch` (ex.: `-Xss1M` só x86).
  LWJGL **3.4.3**; `jinput` sumiu nas 26.x; `jtracy-1.14.38-natives-windows` presente.

### args JVM (26.3, verbatim)
```
[-XstartOnFirstThread]                    (só osx)
-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump  (só windows)
[-Xss1M]                                  (só arch x86)
-XX:StackShadowPages=32
--enable-native-access=ALL-UNNAMED
--add-exports java.base/jdk.internal.misc=ALL-UNNAMED
-Djava.library.path=${natives_directory}/java
-Djna.tmpdir=${natives_directory}/jna
-Dorg.lwjgl.system.SharedLibraryExtractPath=${natives_directory}/lwjgl
-Dio.netty.native.workdir=${natives_directory}/netty
-Dminecraft.launcher.brand=${launcher_name}
-Dminecraft.launcher.version=${launcher_version}
-cp ${classpath}
```
Diferenças por versão:
| | 26.1 | 26.2 | 26.3 |
|---|---|---|---|
| assetIndex | 30 | 32 | 34 |
| natives (-D) | **flat** `${natives_directory}` | subdirs `{java,jna,lwjgl,netty}` | subdirs |
| extras | `--sun-misc-unsafe-memory-access=allow` | idem | **sem** esse flag; adiciona `StackShadowPages=32` + `--add-exports jdk.internal.misc` |
→ **Regra de ouro**: seguir o `arguments.jvm` do JSON da versão literalmente; não hardcodar layout.

### args game (idênticos nas três versões)
```
--username ${auth_player_name} --version ${version_name} --gameDir ${game_directory}
--assetsDir ${assets_root} --assetIndex ${assets_index_name}
--uuid ${auth_uuid} --accessToken ${auth_access_token}
--clientId ${clientid} --xuid ${auth_xuid} --versionType ${version_type}
```
+ opcionais gated: `--demo`, `--width/--height`, Quick Play (`--quickPlay*`).

## 3. Assets

- Índice: `objects: { "icons/icon_16x16.png": { "hash": "...", "size": 781 } }` (índice 34: 5147 objetos, totalSize ~484,9 MB).
- URL: `https://resources.download.minecraft.net/<hash[0:2]>/<hash>` (HEAD 200, image/png verificado).
- Baixar apenas objetos do assetIndex usado; store compartilhado global por hash
  (contrato `<assetsRoot>/<indexId>/<name>` = prática Prism/launcher oficial; `UNVERIFIED` byte-exato → testar no P3).
- Acessos: `--assetsDir <assetsRoot> --assetIndex <id>`.

## 4. Java runtime

Catálogo: `https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json`
→ chaves de OS `windows-x64|windows-arm64|...`, componente `java-runtime-epsilon`
(= 25.0.1 para windows-x64, verificado). Cada entrada → `manifest.url` com árvore de arquivos:
`files: { <relpath>: { type: file|directory, executable: bool, downloads: { raw|lzma: {url,size,sha1} } } }`.
Qualquer JDK 25+ (Microsoft OpenJDK, Zulu) também serve.

## 5. Fabric (pipeline 26.x)

Endpoints verificados:
```
GET https://meta.fabricmc.net/v2/versions/loader                  (lista loader)
GET https://meta.fabricmc.net/v2/versions/loader/{mcTag}          (loaders x versão; p/ 26.3: stable 0.19.5)
GET https://meta.fabricmc.net/v2/versions/intermediary/{mcTag}    (26.x → version "0.0.0" = SEM intermediary)
GET https://maven.fabricmc.net/...                                (jars: loader, asm 9.10.1*, sponge-mixin 0.17.5+mixin.0.8.7)
```
- **26.x NÃO é obfuscado e NÃO usa launchwrapper.** `profile/json` **404 para 26.x** — o launcher
  monta o JSON de launch a partir de `launcherMeta` (version 2).
- `launcherMeta: { min_java_version:8, mainClass:{client:"net.fabricmc.loader.impl.launch.knot.KnotClient", server:"...KnotServer"},
  libraries: { common, client, [development(skipoks)] } }` — URLs `<base><group-as-path>/<arte>/<v>/< arte>-<v>[-<class>].jar`.
- O jar do **fabric-loader NÃO vem listado** — adicionar manualmente
  `https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar`.
- Classpath: loader jar primeiro → libs `common`+`client` (asm 5 jars + sponge-mixin) → libs vanilla por rules → `client.jar`.
  **Knot rejeita wildcard `-cp` (`cp/*`)** — listar cada jar.
- Entry final: `net.fabricmc.loader.impl.launch.knot.KnotClient <args game...>`; sem args JVM extras de produção.
  Defaults internos: sem `--accessToken` → "FabricMC" (Nunca usar assim — exigir token real);
  `versionType` reescrito para `<você>/Fabric` via `processArgumentMap`.
- Props de produção (SystemProperties.java): `-Dfabric.addMods=path1;path2|Método@file`, `-Dfabric.modsFolder=path`,
  `-Dfabric.gameJarPath(.client)=`, `-Dfabric.gameVersion=`. NUNCA `-Dfabric.development=true` em produção.

## 6. Autenticação (fluxo legal, NÃO implementado por mim/manualmente)

Chain (endpoints verbatim em minecraft.wiki):
1. Azure app (`consumers` tenant, scope `XboxLive.signin` + `offline_access`) — **nova app precisa de aprovação
   Minecraft-API (`aka.ms/mce-reviewappid`) ou `login_with_xbox` retorna 403** → item de lead time crítico.
2. Device code: `POST login.microsoftonline.com/consumers/oauth2/v2.0/devicecode` → poll em `.../token`.
3. XBL: `POST user.auth.xboxlive.com/user/authenticate` `{RpsTicket:"d=<ms>", RelyingParty:"http://auth.xboxlive.com"}` → token+uhs.
4. XSTS: `POST xsts.auth.xboxlive.com/xsts/authorize` `{SandboxId:"RETAIL", UserTokens:[xbl], RelyingParty:"rp://api.minecraftservices.com/"}` → xsts (tratar XErr 2148916238 etc.).
5. MC token: `POST api.minecraftservices.com/authentication/login_with_xbox` `{identityToken:"XBL3.0 x=<uhs>;<xsts>"}` → JWT **24 h**.
6. Ownership (obrigatório p/ legalidade): `GET /entitlements/mcstore` exige `product_minecraft`+`game_minecraft` (JWT assinado; chave pública pública).
7. Profile: `GET /minecraft/profile` → id/name → `--uuid`/`--username`.
- Token via argv `--accessToken` é a ÚNICA rota documentada (**não existe stdin/env** no vanilla).
  Mitigações: nunca logar argv; token 24h derivado por launch do refresh token; redigir crash reports.

## 7. Windows / natives (26.x)

- **Sem extração de DLL pelo launcher**: jars `natives-windows` ficam no classpath;
  LWJGL extrai em `-Dorg.lwjgl.system.SharedLibraryExtractPath`, JNA em `-Djna.tmpdir`,
  netty em `-Dio.netty.native.workdir`. Criar `<natives>/{java,jna,lwjgl,netty}` vazio adiantado (26.2/26.3).
- 26.1: flat `${natives_directory}` (as quatro flags apontam pro mesmo dir).

## ÍtENS UNVERIFIED (testar no P3)

1. Layout real `<assetsRoot>/<indexId>/<path>` nas 26.x (contra vanilla byte-exato).
2. `--versionType` efeito exato na janela/título; tarefa do taskbar vem do ícone embarcado do client.jar.
3. Combinações entre `CREATE_NO_WINDOW`(0x08000000)/`CREATE_BREAKAWAY_FROM_JOB` no spawn Win32.
4. Necesidade de extrair `jtracy natives` em `<natives>/java` (assumido que não).
5. Semantics exata de `TerminatedPayload.code` no Windows (esperar `Some(code)`).
6. Rotação/chave `keyId` dos JWT de entitlement — implementar verificação de assinatura com chave publica publicado.
7. Mínimo fabric-loader por versão: pegar o primeiro `stable` listado no meta por MC tag (26.3: 0.19.5).

## Anti-padrões

- ❌ Usar `profile/json` da meta com 26.x (404; era do intermediary).
- ❌ Assumir layout flat de natives em 26.2/26.3.
- ❌ Wildcard em `-cp` (Knot rejeita).
- ❌ `-Dfabric.development=true` em produção.
- ❌ Baixar/bundlar client.jar/assets de fontes третьа — só CDNs da Mojang, autenticado.
- ❌ Logar/redirecionar argv contendo token; nos logs do launcher redigir `--accessToken`, `--uuid`, `clientId`, `xuid`, `username`.
