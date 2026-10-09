# Decision Flights · Android v0.1.3

Buscador minimalista de passagens com navegador hospedado na OpenAI. A interface
roda no celular; a navegação usa Agents com `computer_use`, um Environment
`openai_hosted` e Session persistente. Decisions compara as tarifas observadas.
GPT Live 1 recebe sua voz e conversa em português brasileiro. Sem servidor próprio, JEV, browser-use, anúncios ou tarifas de demonstração.

## Busca rápida, avançada e serviços visíveis · v0.1.3

A primeira viagem vai no `input` de `POST /agents/sessions`, sem leitura de
turnos vazios, espera do SSE ou segundo POST de mensagem. Com Vault salvo, esse
início usa uma chamada; sem Vault, duas. Isso reduz etapas do cliente, sem
prometer um tempo total de busca. O Vault é verificado no teste de conexão e
validado pela OpenAI ao criar a sessão, sem GET prévio a cada viagem.

Uma nova viagem reutiliza o navegador somente após confirmar sessão `idle`,
environment `connected` e mesmo modo de ferramentas. Caso contrário, encerra
ou limpa a sessão ausente e cria outra. Tarifas, imagem e contadores antigos
são apagados antes da nova tarefa. Criação sem resposta é recuperada por
metadata, incluindo o input inicial, sem duplicar sessão ou pedido.

- **Rápida:** até três ofertas verificadas em uma página, sem Web Search.
- **Avançada:** até seis ofertas, prioridade, teto em BRL por pessoa e voos
  diretos. Habilita `web_search` com contrato próprio de Agents. O agente pode
  usá-lo para contexto de rota/aeroporto; preços exigem verificação no navegador.
  Se não houve chamada, a tela informa **Disponível · não utilizado**.
- **Sua busca na OpenAI:** sandbox, operações `computer_use`, chamadas Web Search
  e comparação Decisions aparecem também junto aos resultados. Contadores vêm
  de IDs reais dos itens, com deduplicação entre SSE, polling e reabertura.

Filtros conhecidos são conferidos no cliente: escalas da ida, orçamento em
BRL com base de preço comparável e limite de ofertas. Valores em outras moedas
ou apenas de ida não satisfazem um teto para uma viagem completa de ida e volta.
Voz usa o modo e os filtros selecionados no formulário.

Decisions compara as ofertas depois da pesquisa; a navegação é feita pelo
agente com `computer_use`. As tarifas são publicadas antes de aguardar essa
comparação. Se a comparação falhar ou ficar sem confirmação ao reabrir o app,
as tarifas são preservadas e nenhuma nova chamada de ranking é disparada.

## Instalar e conectar

1. Instale `Decision-Flights-v0.1.3.apk` em Android 9 ou superior.
2. Abra a engrenagem e toque **Conectar OpenAI**. Insira sua chave no diálogo
   nativo protegido. A chave não passa pelo JavaScript.
3. **Conectar e testar** faz uma chamada real a Decisions e cria ou reutiliza um
   Vault na sua conta. A conta precisa ter acesso às APIs/modelos e saldo.
4. Escolha cidades, datas, adultos e classe; toque **Buscar passagens**.
   Autorize a origem pública do Google quando o navegador OpenAI solicitar.
5. Acompanhe a atividade e as telas do navegador. As ofertas aparecem somente
   após a conclusão da tarefa e validação do resultado estruturado.
6. Ajuste a viagem na mesma sessão ou abra a fonte para conferir a tarifa.
   O aplicativo pesquisa; a compra é feita pelo usuário no site.

O app é destinado ao uso pessoal com a chave da própria conta. A chave fica
criptografada via Android Keystore, com backups desativados. Distribuição com
uma chave compartilhada requer um serviço que mantenha essa chave fora do APK.

## Buscar por voz

Toque **Buscar por voz** → **Começar conversa**, permita o microfone e fale
origem, destino e datas. Bossa é a voz brasileira padrão; Tempo é a alternativa
masculina. A assistente pede os dados ausentes e deve confirmar a viagem antes
de chamar a busca. Você também pode pedir ajustes, andamento ou interrupção.
A busca continua no mesmo navegador OpenAI usado pela interface de texto.

O áudio trafega por WebRTC. O código nativo troca o SDP em
`POST /v1/live/sessions`; a chave nunca entra no JavaScript. A sessão usa
Responses delegation com quatro funções restritas: `search_flights`,
`refine_search`, `get_search_status` e `cancel_search`. Resultados de função
são enviados em `response.item.create`, seguidos de `response.create` somente
quando todas as chamadas da resposta estiverem concluídas. IDs de chamada
impedem a repetição de ações dentro da conversa.

As legendas ficam apenas em memória. O app informa que a voz é gerada por IA
e envia `store: false`; isso não altera a política de retenção da API.
É possível silenciar e reativar o microfone. Encerrar envia `session.close`
e recebe `session.closed` antes de liberar a conexão; ao sair do aplicativo,
o microfone é liberado imediatamente e o nativo solicita hangup. A sessão tem
limite de quatro minutos, também verificado pelo código nativo. IDs pendentes
permitem recuperar o encerramento depois de uma queda ou reinício.

A conta precisa ter acesso a `gpt-live-1`. Voz e backend têm cobrança de API.

## Correção da espera nesta atualização

A leitura de atividade passou a usar
`GET /agents/sessions/{session_id}/turns/{turn_id}/items`, com `order`, `limit` e
`after`. O parâmetro `turn_id` era enviado indevidamente na query de itens da
sessão. A resposta final precisa pertencer à tarefa raiz atual; mensagens de
outras tarefas e subagentes não viram tarifas. A leitura segue a paginação.

Em ajustes ou reutilização, o acompanhamento conecta o SSE antes do envio.
Na primeira busca, a viagem segue junto com a criação da sessão; o SSE conecta
logo após a resposta. O acompanhamento recupera
eventos perdidos por consulta. Erros HTTP, falhas de sessão, navegador ou
tarefa deixam de ser ignorados. Falha do stream não é tratada como falha da
busca: a mesma sessão continua sendo consultada. Falhas permanentes da
consulta suspendem tentativas automáticas; falhas transitórias usam espera
progressiva. O estado publicado pode ser lido sem bloquear atrás da rede.

A tela mostra a etapa confirmada, avisos persistentes, hora da consulta e
**Consultar andamento** durante a busca. Após 30 segundos sem nova atividade,
mostra orientação para conferir o andamento. O contador inclui a preparação
inicial; ele não mede apenas a navegação. Voz também recebe avisos de conexão.
Nenhuma recuperação cria outra sessão ou reenvia o pedido automaticamente.

## Correção anterior de Decisions

O teste de conexão e a comparação enviavam `label` nas opções de escolha.
A API exige `{value, description}`. Ambos os caminhos foram corrigidos e os
fixtures agora rejeitam o contrato antigo. Um HTTP 400 é mostrado como
requisição inválida; Configurações exibe serviço, campo, código e request ID
quando a OpenAI os fornece. Chaves e headers de autorização são removidos dos
diagnósticos; nenhuma requisição completa é armazenada.

## Integrações efetivas

| Recurso | Uso |
|---|---|
| Agents / Session | `gpt-6-astra`; criação com input inicial; reutilização de sessão idle e navegador conectado no mesmo modo; ajustes mantêm contexto |
| Environment | Desktop/navegador `openai_hosted`; rede restrita aos hosts necessários ao Google Flights |
| Vault | Vault real vinculado à sessão, inicialmente sem segredos: a busca pública não precisa de login |
| Decisions | `gpt-6-luna`, intenção no teste de conexão e escolha após as tarifas verificadas; estado, confiança e latência visíveis |
| Web Search | Ferramenta Agents em modo `live`, contexto baixo e domínios Google; disponível na busca avançada, uso mostrado apenas quando observado |
| GPT Live 1 | WebRTC direto com a OpenAI, vozes brasileiras Bossa e Tempo; `gpt-6-luna` interpreta pedidos e chama funções de busca |
| Streaming | Eventos SSE com atividade e captura de tela; consultas recuperam eventos perdidos |

As ofertas preservam moeda, base do preço, fonte, duração da ida, escalas e
bagagem explicitamente observada. Preços com moedas/bases diferentes são
agrupados, sem conversão inventada. Uma escolha com confiança abaixo de 70%
não recebe destaque. Se Decisions falhar, as tarifas verificadas continuam
disponíveis. CAPTCHA, login ou ausência de preço claro resultam em uma mensagem
honesta e nenhuma tarifa inventada.

O tempo exibido é medido no celular durante cada busca. Os marcos de ambiente,
primeira atividade e primeira chamada são horários de confirmação local,
não tempos internos do provedor. Eles podem se sobrepor e não devem ser somados.
A latência de Decisions mede a requisição completa, incluindo rede. O benchmark de sete segundos com
JEV não é uma garantia para esta implementação. Há cobrança de API e ambiente;
o app não inventa um custo em dólares. Os IDs dos serviços podem ser abertos diretamente pelo painel de andamento.

## Recuperação e encerramento

Sessão, tarefa e chave de idempotência são persistidas antes do envio. Se a
conexão cair, a sessão é consultada antes de repetir a mesma mensagem. Uma
criação sem confirmação é recuperada pela metadata da sessão; não é repetida
automaticamente. A interface oferece **Retomar envio na mesma sessão** quando
a mensagem continua sem confirmação.

**Interromper busca** cancela a tarefa. **Encerrar sessão hospedada** apaga a
sessão e libera seu ambiente. Trocar/desconectar a chave também encerra a sessão.
Há um limite de quatro minutos com cancelamento por melhor esforço enquanto o
processo Android estiver vivo; se o sistema encerrar o processo ou a conexão
ficar indisponível, confira e encerre a sessão no painel OpenAI. O Vault é
mantido para reutilização. Login, checkout e pagamento não são executados.

## Build e verificações

Java 17, Android SDK platform 35 ou superior e build-tools com aapt2, d8,
zipalign e apksigner. O projeto usa o mesmo fluxo sem Gradle do repositório.

```bash
export ANDROID_JAR=/path/to/android.jar
export BUILD_TOOLS=/path/to/build-tools
export FLIGHTS_KEYSTORE=/private/path/decision-flights.jks
export FLIGHTS_STOREPASS='<senha privada>'
bash flights/scripts/build.sh

export JSON_JAR=/path/to/json-java.jar
bash flights/scripts/test.sh

# Com Playwright instalado; opcionalmente informe um Chromium local.
CHROMIUM_EXECUTABLE_PATH=/path/to/chromium node flights/tests/ui-test.cjs
```

O alias da chave de assinatura é `flights`. Guarde o backup privado da assinatura
para instalar futuras versões como atualização. Nunca envie esse backup ao git.

Validação realizada: compilação e assinatura Android; 267 verificações de
contrato/ciclo de vida e 49 de GPT Live com fixtures; 81 verificações da
interface em Chromium local, com larguras 320, 360, 393, 540 e 1280 px.
Inclui consulta rejeitada, recuperação sem duplicação, paginação, falhas
de navegador/sessão, estado legível durante espera de rede, permissão negada,
transcrição segura, silêncio/reativação, despacho de
função duplicada, continuação do Responses, fechamento e saída durante handshake.
Os testes não são chamadas reais. Não havia chave de API disponível ao
desenvolvimento. Busca real, áudio em aparelho físico, latência e cobrança
precisam ser verificados na conta do usuário. O teste de conexão do APK usa
os serviços reais.

[Design e verificação visual](docs/design.md).

## Documentação OpenAI

- [Computer use e navegador hospedado](https://developers.openai.com/api/docs/guides/agents-api/tools/computer-use)
- [Configuração do agente](https://developers.openai.com/api/docs/guides/agents-api/configuration)
- [Sessions](https://developers.openai.com/api/docs/guides/agents-api/sessions)
- [Itens de uma tarefa](https://developers.openai.com/api/reference/resources/beta/subresources/agents/subresources/sessions/subresources/turns/subresources/items/methods/list)
- [Eventos e recuperação](https://developers.openai.com/api/docs/guides/agents-api/sessions/events)
- [Environment](https://developers.openai.com/api/docs/guides/agents-api/environments/openai-hosted)
- [Vaults](https://developers.openai.com/api/docs/guides/agents-api/tools/vaults)
- [Decisions](https://developers.openai.com/api/docs/guides/decisions)
- [GPT Live 1](https://developers.openai.com/api/docs/models/gpt-live-1)
- [Live por WebRTC](https://developers.openai.com/api/docs/guides/voice-webrtc?api=live)
- [Delegação e funções de Live](https://developers.openai.com/api/docs/guides/live-delegation)
- [Vozes brasileiras e ciclo de vida](https://developers.openai.com/api/docs/guides/live-conversations)

Este módulo é separado do jogo LUMI (`com.edward.flights`) e não substitui os
arquivos nem os dados do jogo existente.
