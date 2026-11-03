# Astucy — Logo Guidelines (compacta, v1.0 — 2026-10-22)

## 1. A marca
- **Ideia**: a **Íris** — três pétalas grossas em rotação; o client abre como um diafragma/portal girando. Símbolo geométrico construído (arcos exatos, fendas a 120°), sem letra, sem orbe, sem pena.
- **Versões**: horizontal (símbolo + ASTUCY) · empilhado · só símbolo · só wordmark
- **Arquivos**: `branding/kit/*.svg` (masters) · `astucy-*.svg|png` (variantes) · `favicon.ico`, `site.webmanifest`, `head-snippet.html` (web) · `slides/*.png` (board)

## 2. Espaço de segurança
Zona limpa de **1 × raio interno da Íris** (no lockup: a altura das maiúsculas do wordmark) em todos os lados. A zona escala com o logo.

## 3. Tamanhos mínimos
| Versão | Tela | Uso |
|---|---|---|
| Horizontal lockup | **96 px de largura** | titlebar do launcher, header do site |
| Símbolo (padrão) | 24 px | HUD/UI do jogo |
| Símbolo (corte favicon) | **16 px** | favicon, taskbar, janelas pequenas (use `favicon.svg`) |

## 4. Cor
| Nome | HEX | Uso |
|---|---|---|
| Preto profundo | `#0A0E12` | fundo escuro padrão (UI, app icon tile) |
| Branco | `#FFFFFF` | inversed sobre preto |
| **Esmeralda elétrica** (acento) | `#00E5A0` | acento único: splash, botão primário, substring no terminal |
| Esmeralda leve (para fundo claro) | `#00B87E` | variação acessível sobre branco |
| Cinza técnico | `#66707C` | texto secundário |

**Pares aprovados**: símbolo preto sobre branco · **símbolo branco sobre `#0A0E12`** (padrão do launcher) · acento `#00E5A0` apenas como assinatura (splash/FAVOR), nunca como cor cheia da marca.
A versão 1-cor é sempre preta ou branca — a Íris NÃO fica toda em acento (o acento é detalhe).

## 5. Tipografia
- Wordmark: **Inter** ExtraBold (780) outline em path — contemplando `opsz 32`; NUNCA digite o wordmark (use o master).
- Headlines UI: Inter 800/700 · corpo: Inter 400/500 · fallback: `Inter, "Segoe UI", system-ui, sans-serif`
- Licença: SIL OFL 1.1 (aprove uso em marca).

## 6. Motion identity (a marca PODE girar)
- **Rotação da Íris é o gesto único da marca**: 120° por "clique" de progresso (boot do launcher, download, instalação do pack).
- Direção SEMPRE horário; easing rápido no início e amortecido no fim; nunca girar contínuo infinito (só sob estado "carregando").
- No HUD do jogo (mod `astucyc`), o gesto de rotação pertence aos ícones derivados de HUD — não ao símbolo em si dentro do jogo.

## 7. Proibições
Não esticar nem achatar · não recolorir fora da paleta · não girar (exceto o gesto de motion da §6) · não adicionar sombra/outline/gradiente · não rearranjar o lockup · não aplicar o acento como fundo inteiro da marca · não usar koin ícones genéricos de "seta de velocidade" derivados · **não** misturar com assets de outros clients (logo, cosmeticos, HUDs de clients de terceiros).

## 8. Disclaimer obrigatório (EULA Mojang)
Todo material público (site, README, launcher "Sobre", vídeo) inclui, em destaque:
> *"NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT."*

## 9. Contato
Dúvidas de uso: abertura de issue no repositório oficial do Astucy. Masters: `astucy/branding/kit/`. Rationale e testes: `branding/concepts/` + `slides/`.
Nota: cuidados de marca aplicáveis (o nome 'Minecraft' é marca da Mojang e é usado apenas de modo descritivo).
