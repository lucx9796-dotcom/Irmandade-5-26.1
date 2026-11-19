# Port para Minecraft 26.2

Feito sobre o port 26.1 (que veio de `imdd-fase3-corrigido.zip`, 1.21.11/Yarn). NÃO foi compilado (sem rede nem jar do
jogo no ambiente do port), então o primeiro `./gradlew build` pode apontar nomes a ajustar.

## Build
- JDK 25. Gradle 9.5.1, Loom `net.fabricmc.fabric-loom` 1.17-SNAPSHOT.
- minecraft 26.2, Fabric Loader 0.19.5, Fabric API 0.160.0+26.2 (confira em https://fabricmc.net/develop).
- fabric.mod.json: minecraft `~26.2`, java `>=25`.

## Mudanças do 26.1 -> 26.2
- `EntityType.LIGHTNING_BOLT` -> `EntityTypes.LIGHTNING_BOLT` (Expurgo).
- `ChatFormatting` foi esvaziado: o roxo-claro da mensagem do baú do fim agora usa `Style.withColor(0xFF55FF)`.
- `Gui` reorganizado: o overlay de debug fica em `Hud` -> `mc.gui.hud.getDebugOverlay()` (marca d'água do F3).
- Magma Congelado: a camada translúcida agora vem do modelo (`"force_translucent": true` nos 4 `frosted_magma_*.json`);
  a reflexão em TranslucentBlocks virou só reforço (a API de camada do Fabric pode ter sumido).
- Correção que vale desde o 26.1: `Style.withUnderline` -> `withUnderlined` (TabText). O zip 26.1 anterior tinha esse erro de compilação.

## ATENÇÃO — conferir no primeiro teste (nomes assumidos)
1. **Lua vermelha/gigante do Expurgo** (`SkyRenderingMixin`): alvo `SkyRenderer.renderMoon`. O SkyRenderer mudou várias vezes
   (renderSkyDisc, renderSunriseAndSunset...). Se o nome não bater, o mixin é ignorado em silêncio (require = 0).
2. **Armadura invisível**: `AvatarRenderer.extractRenderState`.
3. **Animação do totem**: `GameRenderer.displayItemActivation` (existe no 26.2; sai do GameRenderer só no 26.3).
4. **/desaparecer** (seletores e sugestões): `EntitySelector.findPlayers/findEntities`, `CommandSourceStack.getOnlinePlayerNames`,
   `ClientSuggestionProvider.getOnlinePlayerNames`, `ClientPacketListener.getOnlinePlayerIds`.
5. Marca d'água do F3: usa `DebugScreenEntryList.isF3Visible/isF3Enabled` por reflexão; se não achar, mostra sempre que o overlay aparece.
6. `/time set 18000` (world clocks), definições das relíquias, `/summon minecraft:mannequin`, `ResourceKey.identifier()`,
   `MinecraftServer.getCurrentSmoothedTickTime()` e `ClientboundTabListPacket` continuam assumidos, como no 26.1.
7. Só li parte do primer de migração 26.1 -> 26.2 (as listas de "mudanças menores" ficaram de fora).
