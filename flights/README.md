# Decision Flights · Android v0.1.0

Buscador minimalista de passagens com navegador hospedado na OpenAI. A interface
roda no celular; a navegação usa Agents com `computer_use`, um Environment
`openai_hosted` e Session persistente. Decisions compara as tarifas observadas.
Sem servidor próprio, JEV, browser-use, anúncios ou tarifas de demonstração.

## Instalar e conectar

1. Instale `Decision-Flights-v0.1.0.apk` em Android 9 ou superior.
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

## Integrações efetivas

| Recurso | Uso |
|---|---|
| Agents / Session | `gpt-6-astra`, uma sessão por viagem; ajustes mantêm o contexto |
| Environment | Desktop/navegador `openai_hosted`; rede restrita aos hosts necessários ao Google Flights |
| Vault | Vault real vinculado à sessão, inicialmente sem segredos: a busca pública não precisa de login |
| Decisions | `gpt-6-luna`, intenção no teste de conexão e escolha entre até seis ofertas observadas |
| Streaming | Eventos SSE com atividade e captura de tela; consultas recuperam eventos perdidos |

As ofertas preservam moeda, base do preço, fonte, duração da ida, escalas e
bagagem explicitamente observada. Preços com moedas/bases diferentes são
agrupados, sem conversão inventada. Uma escolha com confiança abaixo de 70%
não recebe destaque. Se Decisions falhar, as tarifas verificadas continuam
disponíveis. CAPTCHA, login ou ausência de preço claro resultam em uma mensagem
honesta e nenhuma tarifa inventada.

O tempo exibido é medido durante cada busca. O benchmark de sete segundos com
JEV não é uma garantia para esta implementação. Há cobrança de API e ambiente;
o app não inventa um custo em dólares. Os IDs dos serviços ficam nas configurações.

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

Validação realizada: compilação Android, 47 verificações de contrato/ciclo de
vida com fixtures e 31 verificações da interface em Chromium local, em larguras
320, 360, 393, 540 e 1280 px. Os testes de contrato não são chamadas reais.
Não havia chave de API disponível ao desenvolvimento; a busca real, latência,
custo e instalação em aparelho físico ainda precisam ser verificados na conta
do usuário. O teste de conexão no APK usa os serviços reais.

[Design e verificação visual](docs/design.md).

## Documentação OpenAI

- [Computer use e navegador hospedado](https://developers.openai.com/api/docs/guides/agents-api/tools/computer-use)
- [Configuração do agente](https://developers.openai.com/api/docs/guides/agents-api/configuration)
- [Sessions](https://developers.openai.com/api/docs/guides/agents-api/sessions)
- [Environment](https://developers.openai.com/api/docs/guides/agents-api/environments/openai-hosted)
- [Vaults](https://developers.openai.com/api/docs/guides/agents-api/tools/vaults)
- [Decisions](https://developers.openai.com/api/docs/guides/decisions)

Este módulo é separado do jogo LUMI (`com.edward.flights`) e não substitui os
arquivos nem os dados do jogo existente.
