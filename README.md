# Astucy

Cliente Minecraft Java Edition (**mod Fabric `astucyc`**) + **launcher nativo** (Rust/eframe, sem WebView2 e sem servidor local).

Regras do projeto: tudo gerado aqui — **sem cópia de código, assets ou scripts de terceiros**. Projeto público, licença MIT.

## Estrutura

```
launcher/   Launcher nativo (Rust + eframe): perfis, mods, versões, conta Microsoft
mod/        Mod Fabric "astucyc": menu R-SHIFT, HUD editor, chat aprimorado
branding/   Artes e identidade (geradas no projeto)
docs/       Notas de arquitetura
```

## Build

### Launcher (nativo)
```bash
cd launcher/src-tauri
cargo build --release --bin launcher
# binário: launcher/src-tauri/target/release/launcher.exe
```

### Mod (Fabric)
```bash
cd mod/astucyc
./gradlew build            # Windows: gradlew.bat build
# jar: mod/astucyc/build/libs/astucyc-0.1.0.jar
```

## Mod no jogo

Copie o jar para o perfil:
```
%APPDATA%\.minecraft\astucy-profile\mods\astucyc-0.1.0.jar
```

> **Versões**: os módulos do Astucy (R-SHIFT, zoom, chat, HUD...) rodam em **26.x**
> (desofuscado). Versões antigas (1.7.10, 1.8.9, 1.12...) abrem **vanilla** com o
> launcher cuidando do Java certo, das libs/natives e da pasta de jogo separada.

Durante o jogo:
- `R-SHIFT` abre o menu do cliente (módulos, perfis, editor de HUD)
- HUD editor: tecla dedicada + `TAB` alterna entre módulos
- Chat: histórico infinito (rolando até o fim dele), pesquisa ao vivo: um clique abre a busca e os resultados filtram enquanto digita; `Ctrl+botão direito` copia a mensagem inteira
- Screenshot: `F2` salva e o **chat** recebe a linha da print com os botões clicáveis `Copy image` (colar como imagem) e `Link` (upload e link no clipboard)

## Segurança

- Login Microsoft por **device code** (OAuth2 → Xbox Live → XSTS → Minecraft Services). Só o ID de cliente público da Microsoft é embutido; o ID do Azure é configurável pelo usuário nos Ajustes e fica em disco, nunca no código.
- `refresh token` é gravado **cifrado com DPAPI**, amarrado ao usuário do Windows; nunca é logado nem sai do processo.
- Configurações/contas ficam fora do repositório (`%APPDATA%\com.astucy.launcher`), nada sensível versionado.

## Modo offline (pirata)

Escolha um nick e jogue sem conta (UUID offline v3 clássico). A popup de contas concentra ativação de conta Microsoft e nick offline.

## Avisos e isenção de responsabilidade

- Os mods disponíveis **não são publicados pelo Astucy**: vêm do Modrinth, hospedados por terceiros. O launcher só instala o que o usuário escolher — não revisa, publica ou modera esses arquivos. Instale apenas projetos de autores em que você confia.
- Não somos afiliados ao **Modrinth, FabricMC, Mojang ou Microsoft**. Minecraft é produto da Mojang Studios e sua marca é registrada pela Mojang; o nome é usado apenas de modo descritivo. Verifique sempre as licenças dos mods que você instala.
- Mods de terceiros podem rodar código com as permissões do jogo. Analise antes de instalar, prefira projetos conhecidos e confira a licença de cada um.

## Licença

Código sob licença [MIT](LICENSE). Mods de terceiros seguem as licenças de seus autores. Não afiliado à Mojang, Microsoft, FabricMC ou Modrinth.
