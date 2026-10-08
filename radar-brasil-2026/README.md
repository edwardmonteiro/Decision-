# Pulso Brasil — Radar Eleitoral 2026

Android APK independente com interface minimalista, atualização da apuração e consulta aos resultados do TSE. Não é afiliado ao Tribunal Superior Eleitoral.

## Funcionalidades da versão 1

- Contagem regressiva para 25 de outubro de 2026, às 17h em Brasília.
- Consulta da Presidência: Brasil ou unidades federativas, 1º e 2º turnos.
- Consulta dos resultados estaduais para governador.
- Interface Android nativa com WebView, layout responsivo em português.
- Atualização periódica de 30 segundos **somente com a tela de apuração aberta** e depois do início previsto da divulgação; atualização manual antes e depois.
- Último resultado salvo localmente para consulta offline, identificado como cache.
- Sem chave OpenAI, sem conta, sem coleta de informações pessoais.
- Fonte oficial visível e botão para abrir o portal do TSE.

## Fontes oficiais

A página do TSE publica JSON EA20 em `https://resultados.tse.jus.br/oficial/ele2026/{eleicao}/dados/{uf}/{uf}-c{cargo}-e{eleicao}-u.json`.

Eleição federal presidencial: **6257 / 6258** (1º/2º turno); eleição estadual: **6259 / 6260**. Cargos: Presidente **0001**, Governador **0003**.

**Importante:** se o TSE alterar o layout dos arquivos durante o segundo turno, adaptar o parser antes da eleição. A tela avisa explicitamente quando nenhum dado oficial pode ser carregado.

## Obter APK no GitHub

1. Abra [Actions](https://github.com/edwardmonteiro/Decision-/actions/workflows/pulso-brasil-2026-apk.yml).
2. Abra uma execução com nome *Pulso Brasil 2026 APK* na branch `radar-brasil-2026`.
3. Baixe o artefato `PulsoBrasil-2026-APK` (ZIP).
4. Extraia e instale `PulsoBrasil-2026.apk` em seu Android.

> Se a execução ainda não aparecer ou falhar, o arquivo APK não está disponível. O fluxo exige GitHub Actions habilitado neste repositório. Este projeto não foi compilado no ambiente de criação.

## Desenvolvimento

```bash
cd radar-brasil-2026
gradle assembleDebug
```

Exige JDK 17, Android SDK 35 e Gradle 8.10.2. O workflow instala tudo no GitHub Actions.

## Limitações / v2

- O app atual não faz push em background nem envia notificações.
- Histórico cronológico, mapa vetorial por estado, painel de pesquisas e acompanhamento político antes da eleição ainda são trabalhos futuros.
- O parser EA20 precisa ser confrontado com arquivos reais atuais do TSE e testado em APK físico, especialmente antes da abertura da apuração do segundo turno.
- Sem suporte a município na versão 1.
- Nunca exibir dados simulados como votos reais.
