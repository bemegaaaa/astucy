# Menu R-SHIFT — especificação (clean-room)

> Regra do projeto: design 100% autoral. Estrutura, comportamento e render são criados do zero aqui;
> nenhum asset, textura, ícone, fonte ou byte de clients de terceiros entra no projeto.
> Alvo: `astucyc` (mod Fabric interno do Astucy). Este documento é a fonte de verdade da UI.

## 1. Abertura

| Ação | Comportamento |
|---|---|
| **R-SHIFT** (Right Shift) | abre/fecha a tela de módulos sobre o jogo (pause não é disparado em multiplayer) |
| **ESC** | fecha e salva |
| Dupla tecla configurável | abre direto no modo edição de HUD |
| Em menus/vanilla screens | R-SHIFT é ignorado (só abre com o jogo em foco) |

## 2. Anatomia da tela (igual à referência, layout otimizado)

```
┌────────────────────────────────────────────────────────────────┐
│  [busca instantânea...]                        Astucy  □ fechar│
├──────────┬─────────────────────────────────────┬───────────────┤
│ Categorias│  Grade de módulos (tiles)           │ Configurações │
│  Todos    │  ┌───────┐ ┌───────┐ ┌───────┐     │ do módulo     │
│  Combate  │  │[i]    │ │[i]    │ │[i]    │     │               │
│  Informação│ │ Nome  │ │ Nome  │ │ Nome  │     │ Escala  ▬▬●─  │
│  Visão    │  │ ●on   │ │ ○off  │ │ ●on   │     │ Opacid. ▬●──  │
│           │  └───────┘ └───────┘ └───────┘     │ Cor  [■]      │
│  ———     │  ┌───────┐ ┌───────┐ ┌───────┐     │ Fundo  ●      │
│  Presets  │  │ ...   │ │ ...   │ │ ...   │     │ Sombra ●      │
│  [PvP]    │                                     │ [Editar pos.] │
│  [Mínimo] │                                     │ [Resetar]     │
└──────────┴─────────────────────────────────────┴───────────────┘
```

- **Sidebar**: categorias (Todos / Combate / Informação / Visão) + **presets de layout** (PvP, Gravação, Mínimo).
- **Busca**: filtra por nome/descrição enquanto digita (otimização nossa; a referência filtra só no Enter).
- **Tiles**: ícone próprio (desenhado por código — nenhum asset de terceiros), nome, interruptor on/off; clique no corpo seleciona, clique no interruptor liga/desliga.
- **Painel de configurações**: aparece com o módulo selecionado; sliders, cor, toggles, keybind e botão "Editar posição".
- **Barra inferior**: atalho do editor + nota "tudo client-sided".

## 3. Configurações por módulo (mesma matriz da referência)

| Controle | Tipo | Faixa | Nota |
|---|---|---|---|
| Ligar/Desligar | toggle | — | estado do módulo |
| Posição | drag no editor | 0–1 (normalizado) | **otimização**: coordenadas normalizadas resolvem qualquer resolução; a referência usa px e quebra ao trocar de resolução |
| Escala | slider | 0.5–3.0 | aplica no tamanho do render |
| Opacidade | slider | 0.1–1.0 | alpha do módulo |
| Cor do texto/seções | color | ARGB | presets da paleta Íris + livre |
| Fundo | toggle + cor/alpha | — | caixa atrás do módulo |
| Sombra de texto | toggle | — | — |
| Tecla (keybind) | captura | GLFW keys | só em módulos com ação (Zoom, Toggle Sneak…) |
| Resetar | botão | — | volta ao default do módulo |

## 4. Modo edição de HUD ("Editar posição")

- Fundo escurecido; os módulos **ativos** viram peças arrastáveis com retângulo de seleção.
- **Drag**: move com o mouse; **snapping 8px** + guias quando alinha com a borda/outro módulo (otimização nossa).
- **Scroll** sobre a peça: escala 5% por passo.
- **Botão direito**: reset da peça; **ESC**: salva e volta ao menu.
- Grade opcional (tecla G) para posicionamento fino.

## 5. Módulos embutidos (bucket A — 30, sem remoção)

**Combate (16)**: ArmorStatus · Keystrokes · CPS · Combo · Cooldowns · Hit Color · Hitbox ·
Reach Display · PvP Info · TNT Countdown · Totem Counter · Kill Sounds · Knockback Trainer ·
Hurt Cam · Damage Tint · Visuais 1.7

**Informação (10)**: Coordenadas · FPS · Memória · Ping · Scoreboard · Direção · Saturação ·
Efeitos · Contador de poções · Time Changer

**Visão & controle (4)**: Crosshair · Attack Indicator · Zoom · Toggle Sneak

Camada 2 (bucket B, 35 módulos) entra por atualização do mod — lista no launcher.

## 6. Persistência

- Arquivo: `.minecraft/config/astucyc.json` (Gson, versionado com `"version": 1`).
- Escrito a cada alteração e no fechamento; lido no boot do cliente.
- Schema por módulo: `enabled, x, y, scale, opacity, color, bg, bgColor, textShadow, keybind`.
- Presets gravam o mapa inteiro; "Restaurar padrão" recria o arquivo.

## 7. Otimizações nossas (diferenças da referência)

1. Coordenadas normalizadas (independente de resolução/escala GUI).
2. Busca instantânea + fuzzy (digita "ar" → ArmorStatus).
3. Snapping com guias no editor de HUD.
4. Presets de layout (PvP / Gravação / Mínimo) em 1 clique.
5. Render por código (DrawContext) — zero assets externos, startup sem I/O de textura.
6. Menu fecha com ESC e grava atomicamente (tmp + rename).
