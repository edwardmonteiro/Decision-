# LUMI Traçado

Jogo Android de corrida vista de cima. Você desenha uma volta na pista; o carrinho usa esse traçado para dirigir. Curvas diferentes mudam a trajetória e o tempo. O modo Drift reduz a aderência lateral: a carroceria vira antes de a velocidade se alinhar, produzindo derrapagem, marcas de pneus e fumaça discreta.

Aplicativo independente: `com.edward.tracado`, versão `0.1.0`, versionCode 1. Instala ao lado de LUMI Travessias e LUMI Caminhos. Não modifica os outros jogos.

## Jogabilidade

1. Comece na bandeira, junto do carro. Desenhe uma volta dentro da pista, em qualquer sentido.
2. Você pode levantar o dedo e continuar da ponta dourada. **Desfazer** remove o último trecho.
3. Feche o traçado na bandeira. Escolha **Passeio**, **Corrida** ou **Drift**.
4. Toque em **Dar a volta**. O carro dirige seguindo sua linha, com curvas mais lentas e perda de velocidade ao sair na grama.
5. Termine uma volta para abrir a próxima pista. Tempos ficam registrados separadamente por pista e modo.

São seis circuitos autorais: oval, curvas abertas, chicane, curva fechada, sequência de curvas e circuito final. O jogo registra tempo ativo da volta, porcentagem de tempo na pista e segundos em drift. Pausas não entram no cronômetro. Uma tentativa sem completar a volta em 60 segundos volta ao ajuste do traçado. Não há vidas, compras ou perda de recordes.

O botão **Guia** mostra o caminho central pontilhado. Nas configurações, **Experimentar um traçado guia** fornece uma linha pronta como recurso opcional; substituir um desenho existente exige confirmação. As tentativas comuns usam o desenho do jogador. Desenhos abertos, atalhos pelo miolo e voltas iniciadas longe da bandeira não podem iniciar uma corrida.

## Arquitetura

- Android Java + WebView; HTML, CSS, JavaScript e ícone vetorial empacotados no APK.
- `tracks.js`: seis circuitos e normalização de recordes/pistas desbloqueadas.
- `race.js`: geometria, validação contínua dos segmentos do desenho, curvas suaves, controlador de direção, velocidade lateral e aferição de volta. Passos fixos de 1/120 s.
- `game.js`: desenho com Pointer Events, vários trechos por volta, interface, renderização canvas, sons sintetizados, pausa, mapa e persistência em `lumi-tracado-v1`.
- `MainActivity.java`: barras e recortes do Android, TTS opcional em português, vibração curta e pausa ao sair do app. Origem `https://tracado.local` atendida somente pelos assets permitidos.

O carro não é movido diretamente ao longo de uma animação predefinida. A direção mira um ponto adiante na linha do jogador; a carroceria muda de orientação e a velocidade é integrada separadamente. A aderência lateral é menor no modo Drift. Checkpoints e chegada são aferidos pela posição efetiva do veículo na pista, não pelo índice de um desenho.

Trata-se de física arcade, não de um simulador automotivo científico. Um traçado muito fechado pode sair da pista e perder velocidade. A linha desenhada recebe uma suavização pequena para reduzir tremores do dedo, apenas quando todos os segmentos resultantes continuam dentro da pista.

Jogo offline, sem permissão de internet, conta ou chave. Não há API OpenAI nesta versão. Leitura de dica usa o TTS do Android, quando a voz estiver disponível. Som do motor e sinais de checkpoint são sintetizados localmente e podem ser desligados. Os outros jogos mantêm suas próprias integrações.

## Compilação

Requer Java 17, Android platform 35+ e build tools, como o projeto principal. Use o certificado privado já configurado no ambiente; nunca versionar senha ou keystore.

```bash
export ANDROID_JAR=/caminho/android.jar
export BUILD_TOOLS=/caminho/build-tools
export LUMI_KEYSTORE=/caminho/privado/release.jks
export LUMI_STOREPASS='senha-no-seu-ambiente'
bash tracado/scripts/build.sh
```

Alias do certificado: `lumi`. Saída: `tracado/build/LUMI-Tracado-v0.1.0.apk`.

## Validação

```bash
node tracado/tests/race.test.cjs
```

Testes determinísticos verificam os seis circuitos nos três modos e dois sentidos: 36 voltas completas com 108 passagens efetivas por checkpoint. Cobrem aderência e drift, mudança de tempo e trajetória entre linhas deslocadas, rota incompleta, dados inválidos, atalhos, origem incorreta, limite de uma volta e progressão.

QA de interface com Playwright (plugin Browser indisponível) percorre desenho → validação → corrida → resultado → desbloqueio. Usa eventos reais de ponteiro para completar as seis pistas, repete uma volta no sentido inverso e testa múltiplos trechos, restauração, pausa, retorno do background, recordes separados, mapa, desfazer, apagar, guia e som. O relógio do navegador é controlado para acelerar a validação; a aplicação executa seus passos normais de física.

Viewports: 360×640, 390×844, 1200×900 e 360×500. Capturas e scripts temporários ficam fora do repositório. A instalação em aparelho Android físico, a saída de áudio e o TTS nativo ainda precisam de validação no dispositivo.

## Design preservado

Referência aprovada: LUMI Travessias e LUMI Caminhos, com fundo `#07121e`, linhas verdes `#85f4cf`, detalhes dourados `#ffe5a1`, tipografia arredondada e botões grandes. Por solicitação do usuário, a arte continua vetorial: pista, carro, faixas, pneus e fumaça são desenhados por código. Não há sprites raster substituindo o visual de linhas.

Mudanças intencionais da nova mecânica: câmera fixa de cima; pista ocupando o tabuleiro; tempo e drift no topo; três modos; mapa de circuitos com miniaturas vetoriais; resultado com tempo e estatísticas. Nenhuma informação técnica aparece na experiência de jogar. O layout respeita barras e recortes do Android, e preferências de movimento reduzido desativam fumaça e o guia animado sem alterar a física.
