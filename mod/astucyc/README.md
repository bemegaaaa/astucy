# astucyc — módulos internos do Astucy

Mod Fabric client-side. Fonte de verdade da UI: `docs/rshift-menu-spec.md`.

## O que já existe (código)

| Peça | Arquivo | Estado |
|---|---|---|
| Entrypoint + keybinds (R-SHIFT / editor) | `AstucyClient.java` | pronto |
| 30 módulos do bucket A (registro + render) | `module/Modules.java` | pronto (estados mock nos trackers) |
| Módulo base (posição normalizada, escala, opacidade, cor, fundo, sombra) | `module/HudModule.java` | pronto |
| Menu R-SHIFT (categorias, busca, tiles, configurações) | `gui/RShiftScreen.java` | pronto |
| Editor de HUD (drag + snapping 8px, scroll escala, reset) | `gui/HudEditorScreen.java` | pronto |
| Persistência `config/astucyc.json` (escrita atômica) | `config/AstucyConfig.java` | pronto |
| Renders especiais (Keystrokes, ArmorStatus) | dentro de `Modules.java` | prontos |
| Estado real (mixins: hit_color, hurt_cam, 1.7 visuals…) | — | **F5** (trackers/mock por enquanto) |

## Build (entra na F5)

O projeto está sem `build.gradle` de propósito: o loom precisa ser **pinado**
para a versão exata do Minecraft (26.1/26.2) + Fabric API que o launch engine
do Astucy instalar. Passos da F5:

1. Instalar JDK 21+ (Temurin) — não está na máquina ainda.
2. `gradle init` com `fabric-loom` + mappings (yarn para 26.x) + `fabric-api`.
3. `gradlew build` → `astucyc-0.1.0.jar` → vai para o pack do Astucy.

Mapeamento do código: nomes **yarn** (`MinecraftClient`, `DrawContext`,
`KeyBinding`). Se o projeto pinar mojmap, o rename é mecânico (IDE refactor).

## Regras (não negociáveis)

- **Nenhum asset de terceiros**: ícones, texturas e fontes são todos desenhados
  por código ou da nossa identidade (Íris). Anatomia do menu replicada por
  comportamento, não por bytes.
- **Sempre client-sided**: nada manda pacote pro servidor; compatível com
  anti-cheats de servidor (não interfere em hit/knockback).
- **Sem Entity X-Ray** ou qualquer módulo de vantagem — regra permanente.
- Configurações de HUD ficam em `.minecraft/config/astucyc.json` — o
  launcher lê/grava o mesmo contrato (futuramente sincroniza com
  `settings.json` do launcher).
