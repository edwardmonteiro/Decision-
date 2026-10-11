# LUMI Caminhos

Um novo jogo de desenhar rampas. O traço verde vira uma superfície física: a bolinha rola, cai, recebe vento e salta em molas para chegar à casa dourada.

Aplicativo independente do LUMI Travessias. Package Android `com.edward.caminhos`, versão `0.1.0` (versionCode 1). Pode ficar instalado ao lado de `com.edward.lumi`; não acessa nem modifica sua chave ou progresso.

## Jogar

1. Desenhe com o dedo abaixo da bolinha. A linha fica fixa no mundo.
2. Toque em **Soltar bolinha**. Gravidade e colisões acontecem localmente.
3. Alcance a casa: isso rende uma estrela e abre a próxima fase.
4. Pegue as duas estrelas do caminho para chegar a três por fase.
5. Desfaça um traço, apague ou ajuste o desenho preservado após a tentativa.

São 12 fases, 36 estrelas e quatro capítulos: primeiros traços, desvios, vento e saltos. A primeira fase mostra um caminho pontilhado; o botão **Dica** mostra uma solução possível. Após duas tentativas sem chegada, o guia aparece ao voltar ao desenho. Não há vidas ou perda de estrelas. A melhor pontuação fica salva. Uma tentativa parada por 20 segundos volta ao fluxo de ajuste; não há contagem regressiva na tela.

O ícone de alto-falante lê a dica usando a voz do Android, quando disponível. Sons sintetizados curtos marcam estrelas, molas e chegada; podem ser desligados. Não há gravação de voz, internet, conta ou chave. Esta versão não integra OpenAI: a proposta é reaproveitar o design e experimentar uma nova mecânica local. O Travessias mantém suas integrações.

## Implementação

- Android Java + WebView com origem local interceptada e assets empacotados; sem dependências de runtime externas.
- `physics.js`: círculo contra segmentos arredondados, gravidade, vento, impulso de mola; simulação fixa de 120 Hz e duas subetapas por passo. Limite de velocidade mantém o deslocamento por subetapa abaixo do raio, evitando atravessamentos em movimento comum.
- `levels.js`: fases autorais, geometrias, posições fixas das estrelas e caminhos de referência verificados por simulação. O caminho de referência é usado apenas para o guia, não substitui o desenho do jogador.
- `game.js`: desenho por Pointer Events, canvas com escala uniforme, áudio sintetizado, mapa, melhor pontuação e persistência em `lumi-caminhos-v1`.
- `MainActivity.java`: insets de barras/cutout, TTS pt-BR, vibração curta e pausa ao sair do app. Somente assets locais conhecidos são permitidos.
- Entradas de desenho são limitadas e validadas; um traço não pode começar nem atravessar a bolinha inicial. Pontos fora do tabuleiro são ignorados.
- Ao sair e voltar, a física fica pausada. Ao reabrir o aplicativo, restaura-se a fase e o desenho, com a bolinha no ponto inicial.

## Compilar

Requer Java 17, Android SDK platform 35+ e build tools, como no projeto principal.

```bash
export ANDROID_JAR=/caminho/android.jar
export BUILD_TOOLS=/caminho/build-tools
export LUMI_KEYSTORE=/caminho/privado/release.jks
export LUMI_STOREPASS='senha-no-seu-ambiente'
bash caminhos/scripts/build.sh
```

O alias do certificado é `lumi`. Nunca versionar certificado ou senha. Saída: `caminhos/build/LUMI-Caminhos-v0.1.0.apk`.

## Validação

```bash
node caminhos/tests/physics.test.cjs
```

Os testes verificam os 12 percursos completos e suas 24 estrelas colecionáveis, ausência de vitória sem desenho, gravidade, direção de rampas, contato com linha fina em alta velocidade, vento, mola e normalização do progresso.

QA de interface usa Playwright porque o plugin Browser não estava disponível. Cenário: abrir → desenhar usando ponteiro → soltar → alcançar casa → desbloquear → repetir. Inclui pausa, retorno do background, desistência de troca de fase, persistência após reload, dicas, som e viewports 360×640, 390×844, 1200×900 e 360×500. Scripts e capturas temporários ficam fora do repositório.

Limites: física lúdica de uma única bolinha, não um simulador científico. Desenhos podem criar bloqueios; **Ajustar desenho** reinicia a tentativa sem apagar os traços. Não houve validação em aparelho Android físico nesta entrega; TTS e instalação dependem do Android do aparelho.

## Referência visual e escolhas

Referência: tela de jogo do LUMI Travessias v0.5.0, já aprovada pelo usuário. Mantidos fundo `#07121e`, traços `#85f4cf`, estrelas `#ffe5a1`, marca espaçada, tipografia arredondada, mapa compacto e botão principal arredondado. Mantida a arte vetorial original por solicitação explícita de reaproveitar o desenho e as linhas; nenhuma imagem raster substitui o jogo. Esta é uma adaptação do mesmo design para outra mecânica, não uma cópia de layout: um único tabuleiro substitui a paisagem e o bloco separado de desenho.

Inventário intencional: bolinha com rosto, casa como alvo, estrelas, pedras com contorno, setas de vento e molas em zigue-zague. Esses elementos têm geometria vetorial correspondente à física. HUD, texto e controles permanecem nativos de HTML. Sem assets remotos ou placeholders. A maior mudança de hierarquia é reservar a maior parte da tela para desenhar diretamente no mundo.
