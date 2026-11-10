# 2. API do Dashboard — documentação dos endpoints

API REST **somente leitura** que alimenta o dashboard. Os dados são calculados no MongoDB, por agregação sobre a coleção `pedidos`, no momento da requisição.

- **URL base:** `http://localhost:8080/api/dashboard`
- **Formato:** JSON (UTF-8)
- **Métodos:** apenas `GET`
- **Perfis:** `ADMIN` e `GERENTE`

Os exemplos de resposta abaixo são **respostas reais** da API, obtidas com os dados de demonstração (ver [README](../README.md#25-dados-de-demonstração)) e o período fixo de 03/11/2026 a 09/11/2026.

## Sumário

| # | Método | Rota | O que retorna | Requisito |
|---|---|---|---|---|
| 1 | GET | [`/filtros`](#21-get-filtros) | Opções dos filtros, período padrão e limites | RF05 |
| 2 | GET | [`/resumo`](#22-get-resumo) | Indicadores gerais comparados ao período anterior | RF01 |
| 3 | GET | [`/vendas`](#23-get-vendas) | Série de faturamento por dia, semana ou mês | RF02 |
| 4 | GET | [`/vendas/por-categoria`](#24-get-vendaspor-categoria) | Faturamento por categoria de produto | RF02 |
| 5 | GET | [`/vendas/por-atendimento`](#25-get-vendaspor-atendimento) | Entrega × retirada | RF02 |
| 6 | GET | [`/vendas/por-horario`](#26-get-vendaspor-horario) | Pedidos por hora do dia e média por dia da semana | RF02 |
| 7 | GET | [`/pedidos/status`](#27-get-pedidosstatus) | Quantidade por status e tempos médios | RF03 |
| 8 | GET | [`/pedidos/recentes`](#28-get-pedidosrecentes) | Últimos pedidos com tempo em aberto | RF03 |
| 9 | GET | [`/produtos/mais-vendidos`](#29-get-produtosmais-vendidos) | Ranking de produtos | RF04 |

---

## 2.0 Convenções

### Autenticação

A API usa a **mesma autenticação do sistema**:

- **Sessão (cookie `JSESSIONID`):** é o que o dashboard usa, depois do login em `/login`.
- **HTTP Basic:** para testes com `curl`, PowerShell ou Postman, por exemplo `curl -u gerente:SENHA ...`.

| Situação | Resposta |
|---|---|
| Sem login | `401 Não autenticado` |
| Perfil `ATENDENTE` | `403 Acesso negado` |
| `POST`, `PUT` ou `DELETE` | `403 Acesso negado` (sem token CSRF ou rota sem permissão) |

### Parâmetros de filtro (comuns aos endpoints 2 a 9)

Todos são opcionais e vão na *query string*.

| Parâmetro | Tipo | Padrão | Regras | Exemplo |
|---|---|---|---|---|
| `inicio` | data `AAAA-MM-DD` | 29 dias antes de `fim` | Não pode ser posterior a `fim` | `inicio=2026-11-03` |
| `fim` | data `AAAA-MM-DD` | hoje | O período (`inicio` a `fim`, inclusive) tem no máximo **366 dias** | `fim=2026-11-09` |
| `status` | lista de `StatusPedido` | todos | Pode repetir: `status=FINALIZADO&status=CANCELADO`. Aceita `RECEBIDO`, `EM_PREPARACAO`, `PRONTO`, `SAIU_PARA_ENTREGA`, `RETIRADO`, `FINALIZADO` e `CANCELADO` | `status=FINALIZADO` |
| `tipoAtendimento` | `ENTREGA` \| `RETIRADA` | ambos | | `tipoAtendimento=ENTREGA` |
| `categoria` | texto | todas | Até 60 caracteres, sem diferenciar maiúsculas de minúsculas. Seleciona os pedidos com **ao menos um item** da categoria. No ranking e no gráfico por categoria, considera só os itens dessa categoria | `categoria=Pizza` |

Regras de período:
- Só `inicio` informado: vai de `inicio` até hoje.
- Só `fim` informado: os 30 dias que terminam em `fim`.
- O **período anterior** usado nas comparações tem o mesmo número de dias e termina na véspera de `inicio`. Ele mantém os demais filtros.

**Pedidos cancelados:**
- `/resumo`, `/vendas`, `/pedidos/status` e `/pedidos/recentes` **contam** os cancelados, que aparecem em campos próprios.
- O faturamento **nunca** inclui pedidos cancelados.
- `/vendas/por-categoria`, `/vendas/por-atendimento`, `/vendas/por-horario`, `/produtos/mais-vendidos` e os tempos médios consideram **apenas pedidos não cancelados**, mesmo que `status=CANCELADO` seja enviado.

### Tipos dos valores

| Tipo | Representação | Exemplo |
|---|---|---|
| Dinheiro | número com até 2 casas, em reais | `8406.46` |
| Percentual | número de 0 a 100 com até 2 casas | `10.42` (10,42%) |
| Data | `AAAA-MM-DD` | `2026-11-03` |
| Data e hora | `AAAA-MM-DDThh:mm:ss` no horário da pizzaria (America/Sao_Paulo) | `2026-11-09T19:58:31` |
| `variacaoPercentual` | `null` quando o valor anterior é zero (sem base de comparação) | `null` |

### Formato de erro

Todos os erros têm o mesmo corpo:

```json
{
  "timestamp": "2026-11-10T17:34:06.2736533",
  "status": 400,
  "erro": "Parâmetro inválido",
  "mensagem": "A data inicial não pode ser posterior à data final.",
  "caminho": "/api/dashboard/resumo",
  "campos": { "inicio": "A data inicial não pode ser posterior à data final." }
}
```

| Campo | Descrição |
|---|---|
| `status` | Código HTTP |
| `erro` | Categoria do erro |
| `mensagem` | Texto para exibir ao usuário; o dashboard mostra exatamente esta mensagem |
| `caminho` | Rota chamada |
| `campos` | Presente nos erros de parâmetro: nome do parâmetro → motivo |

### Catálogo de erros

| HTTP | `erro` | Quando acontece | Exemplo de `mensagem` / `campos` |
|---|---|---|---|
| 400 | `Parâmetro inválido` | `inicio` posterior a `fim` | `{"inicio": "A data inicial não pode ser posterior à data final."}` |
| 400 | `Parâmetro inválido` | Período maior que 366 dias | `{"fim": "O período consultado não pode passar de 366 dias."}` |
| 400 | `Parâmetro inválido` | `categoria` com mais de 60 caracteres | `{"categoria": "A categoria deve ter no máximo 60 caracteres."}` |
| 400 | `Parâmetro inválido` | `limite` fora de 1 a 50 | `{"limite": "O parâmetro limite deve estar entre 1 e 50."}` |
| 400 | `Dados inválidos` | Data fora do formato `AAAA-MM-DD` | `{"inicio": "Valor inválido: 09/11/2026"}` |
| 400 | `Dados inválidos` | `status` ou `tipoAtendimento` desconhecido | `{"status": "Valor inválido: ENTREGUE"}` |
| 400 | `Requisição inválida` | `agrupamento` ou `criterio` desconhecido | `{"agrupamento": "Valor inválido: ANO"}` |
| 401 | `Não autenticado` | Sem sessão e sem HTTP Basic | `Autenticação necessária para acessar este recurso.` |
| 403 | `Acesso negado` | Perfil `ATENDENTE` | `O perfil ATENDENTE não tem permissão para esta operação.` |
| 403 | `Acesso negado` | Método de escrita (`POST`...) | `Token CSRF ausente ou inválido.` |
| 500 | `Internal Server Error` | Falha inesperada, como o MongoDB fora do ar | Resposta padrão do Spring Boot (`timestamp`, `status`, `error`, `path`), sem *stack trace* nem detalhes internos |

Os erros `403` também ficam registrados na **auditoria** do sistema como tentativa de acesso não autorizado, com usuário, perfil e rota.
Diante de um `500` ou de falha de rede, o dashboard avisa só no painel afetado, com o botão **Tentar novamente**. As mensagens são "A API respondeu com erro 500." e "Não foi possível conectar ao servidor…".

---

## 2.1 `GET /filtros`

Retorna as opções dos filtros, para que a tela não tenha valores fixos. Também traz o período padrão e o intervalo de datas com pedidos. **Não recebe parâmetros.**

**Requisição**

```bash
curl -u gerente:SENHA "http://localhost:8080/api/dashboard/filtros"
```

**Resposta `200`**

```json
{
  "status": [
    { "valor": "RECEBIDO", "descricao": "Recebido" },
    { "valor": "EM_PREPARACAO", "descricao": "Em preparação" },
    { "valor": "PRONTO", "descricao": "Pronto" },
    { "valor": "SAIU_PARA_ENTREGA", "descricao": "Saiu para entrega" },
    { "valor": "RETIRADO", "descricao": "Retirado" },
    { "valor": "FINALIZADO", "descricao": "Finalizado" },
    { "valor": "CANCELADO", "descricao": "Cancelado" }
  ],
  "tiposAtendimento": [
    { "valor": "ENTREGA", "descricao": "Entrega" },
    { "valor": "RETIRADA", "descricao": "Retirada no balcão" }
  ],
  "categorias": ["Adicional", "Bebida", "Esfiha", "Pizza", "Porção"],
  "agrupamentos": [
    { "valor": "DIA", "descricao": "Dia" },
    { "valor": "SEMANA", "descricao": "Semana" },
    { "valor": "MES", "descricao": "Mês" }
  ],
  "criteriosRanking": [
    { "valor": "QUANTIDADE", "descricao": "Quantidade vendida" },
    { "valor": "FATURAMENTO", "descricao": "Faturamento" }
  ],
  "periodoPadrao": { "inicio": "2026-10-12", "fim": "2026-11-10", "dias": 30 },
  "primeiroPedido": "2026-08-13",
  "ultimoPedido": "2026-11-10",
  "periodoMaximoDias": 366,
  "limiteMaximo": 50
}
```

| Campo | Descrição |
|---|---|
| `categorias` | Categorias do cardápio e dos itens já vendidos, sem repetição e em ordem alfabética |
| `periodoPadrao` | Período usado quando `inicio` e `fim` não são enviados |
| `primeiroPedido` / `ultimoPedido` | Datas do pedido mais antigo e do mais recente (`null` se não houver pedidos) |

**Erros:** `401` e `403`.

---

## 2.2 `GET /resumo`

Indicadores gerais do período (RF01) e comparação com o período anterior de mesma duração.

**Parâmetros:** os [filtros comuns](#parâmetros-de-filtro-comuns-aos-endpoints-2-a-9).

**Requisição**

```bash
curl -u gerente:SENHA "http://localhost:8080/api/dashboard/resumo?inicio=2026-11-03&fim=2026-11-09"
```

**Resposta `200`**

```json
{
  "periodo": { "inicio": "2026-11-03", "fim": "2026-11-09", "dias": 7 },
  "periodoAnterior": { "inicio": "2026-10-27", "fim": "2026-11-02", "dias": 7 },
  "faturamento": 8406.46,
  "totalPedidos": 96,
  "pedidosValidos": 86,
  "pedidosCancelados": 10,
  "taxaCancelamento": 10.42,
  "valorCancelado": 1039.36,
  "ticketMedio": 97.75,
  "descontosConcedidos": 79.44,
  "itensVendidos": 458,
  "comparacao": {
    "faturamento": { "anterior": 9156.93, "variacaoPercentual": -8.2 },
    "totalPedidos": { "anterior": 100.0, "variacaoPercentual": -4.0 },
    "ticketMedio": { "anterior": 99.53, "variacaoPercentual": -1.79 },
    "pedidosCancelados": { "anterior": 8.0, "variacaoPercentual": 25.0 }
  }
}
```

| Campo | Descrição |
|---|---|
| `faturamento` | Soma de `valorTotal` (com desconto) dos pedidos **não cancelados** |
| `totalPedidos` | Todos os pedidos do período, cancelados incluídos |
| `pedidosValidos` | `totalPedidos − pedidosCancelados` |
| `taxaCancelamento` | `pedidosCancelados ÷ totalPedidos × 100` |
| `valorCancelado` | Soma de `valorTotal` dos pedidos cancelados (receita perdida) |
| `ticketMedio` | `faturamento ÷ pedidosValidos` (0 se não houver pedidos válidos) |
| `descontosConcedidos` | Soma do desconto dos pedidos não cancelados |
| `itensVendidos` | Soma das quantidades dos itens dos pedidos não cancelados |
| `comparacao.*.anterior` | Mesmo indicador no `periodoAnterior` |
| `comparacao.*.variacaoPercentual` | `(atual − anterior) ÷ anterior × 100`; `null` se `anterior` for 0 |

**Exemplo com filtros:** pedidos de entrega, com pizza, finalizados ou cancelados.

```bash
curl -u gerente:SENHA "http://localhost:8080/api/dashboard/resumo?inicio=2026-11-03&fim=2026-11-09&status=FINALIZADO&status=CANCELADO&tipoAtendimento=ENTREGA&categoria=Pizza"
```

```json
{
  "faturamento": 5285.55, "totalPedidos": 54, "pedidosValidos": 49, "pedidosCancelados": 5,
  "taxaCancelamento": 9.26, "valorCancelado": 597.06, "ticketMedio": 107.87,
  "descontosConcedidos": 47.35, "itensVendidos": 252,
  "comparacao": { "faturamento": { "anterior": 5537.95, "variacaoPercentual": -4.56 }, "...": "..." }
}
```

**Erros:** `400` (filtros), `401` e `403`.

---

## 2.3 `GET /vendas`

Série temporal de faturamento para o gráfico de linha (RF02). Ela inclui **todos** os dias, semanas ou meses do período, inclusive os sem vendas.

**Parâmetros:** [filtros comuns](#parâmetros-de-filtro-comuns-aos-endpoints-2-a-9) e mais:

| Parâmetro | Valores | Padrão | Descrição |
|---|---|---|---|
| `agrupamento` | `DIA`, `SEMANA` ou `MES` | `DIA` | `SEMANA` começa na segunda-feira; `MES` começa no dia 1 |

**Requisição**

```bash
curl -u gerente:SENHA "http://localhost:8080/api/dashboard/vendas?inicio=2026-10-08&fim=2026-11-10&agrupamento=SEMANA"
```

**Resposta `200`**

```json
{
  "periodo": { "inicio": "2026-10-08", "fim": "2026-11-10", "dias": 34 },
  "agrupamento": "SEMANA",
  "pontos": [
    { "inicio": "2026-10-05", "fim": "2026-10-11", "rotulo": "05/10", "parcial": true,
      "pedidos": 70, "cancelados": 1, "faturamento": 6946.24, "ticketMedio": 100.67 },
    { "inicio": "2026-10-12", "fim": "2026-10-18", "rotulo": "12/10", "parcial": false,
      "pedidos": 106, "cancelados": 5, "faturamento": 9468.25, "ticketMedio": 93.75 },
    { "inicio": "2026-10-19", "fim": "2026-10-25", "rotulo": "19/10", "parcial": false,
      "pedidos": 95, "cancelados": 8, "faturamento": 8272.14, "ticketMedio": 95.08 },
    { "inicio": "2026-10-26", "fim": "2026-11-01", "rotulo": "26/10", "parcial": false,
      "pedidos": 99, "cancelados": 8, "faturamento": 8941.73, "ticketMedio": 98.26 },
    { "inicio": "2026-11-02", "fim": "2026-11-08", "rotulo": "02/11", "parcial": false,
      "pedidos": 95, "cancelados": 8, "faturamento": 8618.36, "ticketMedio": 99.06 },
    { "inicio": "2026-11-09", "fim": "2026-11-15", "rotulo": "09/11", "parcial": true,
      "pedidos": 8, "cancelados": 2, "faturamento": 653.3, "ticketMedio": 108.88 }
  ]
}
```

| Campo | Descrição |
|---|---|
| `pontos[].inicio` / `fim` | Primeiro e último dia do grupo (dia, semana ou mês) |
| `pontos[].rotulo` | Rótulo pronto para o eixo: `dd/MM` (dia e semana) ou `nov/2026` (mês) |
| `pontos[].parcial` | `true` quando o grupo está **incompleto**: começa antes de `inicio`, termina depois de `fim` ou contém o dia de hoje. Só os dias dentro do filtro são somados. No exemplo, a semana 05/10 considera apenas os dias 08 a 11/10 |
| `pontos[].pedidos` / `cancelados` | Pedidos do grupo (todos) e quantos foram cancelados |
| `pontos[].faturamento` | Faturamento do grupo (sem os cancelados) |
| `pontos[].ticketMedio` | `faturamento ÷ (pedidos − cancelados)` |

Com `agrupamento=DIA`, cada ponto tem `inicio = fim`. Exemplo: `{"inicio":"2026-11-03","fim":"2026-11-03","rotulo":"03/11","parcial":false,"pedidos":8,"cancelados":0,"faturamento":603.2,"ticketMedio":75.4}`.

**Erros:** `400` (filtros ou `agrupamento` inválido, por exemplo `ANO`), `401` e `403`.

---

## 2.4 `GET /vendas/por-categoria`

Faturamento bruto por categoria de produto (RF02). Considera só os pedidos não cancelados e segue a ordem decrescente de faturamento.

**Parâmetros:** os [filtros comuns](#parâmetros-de-filtro-comuns-aos-endpoints-2-a-9).

**Requisição**

```bash
curl -u gerente:SENHA "http://localhost:8080/api/dashboard/vendas/por-categoria?inicio=2026-11-03&fim=2026-11-09"
```

**Resposta `200`**

```json
{
  "periodo": { "inicio": "2026-11-03", "fim": "2026-11-09", "dias": 7 },
  "faturamentoTotal": 8485.9,
  "categorias": [
    { "categoria": "Pizza", "quantidade": 115, "pedidos": 74, "faturamento": 5859.8, "participacao": 69.05 },
    { "categoria": "Esfiha", "quantidade": 243, "pedidos": 29, "faturamento": 1619.9, "participacao": 19.09 },
    { "categoria": "Bebida", "quantidade": 81, "pedidos": 67, "faturamento": 708.0, "participacao": 8.34 },
    { "categoria": "Porção", "quantidade": 8, "pedidos": 8, "faturamento": 199.2, "participacao": 2.35 },
    { "categoria": "Adicional", "quantidade": 11, "pedidos": 11, "faturamento": 99.0, "participacao": 1.17 }
  ]
}
```

| Campo | Descrição |
|---|---|
| `faturamentoTotal` | Soma dos **subtotais dos itens**, antes do desconto. Por isso é maior que o `faturamento` do `/resumo`: `8485.90 − 79.44 de descontos = 8406.46` |
| `categorias[].quantidade` | Unidades vendidas |
| `categorias[].pedidos` | Pedidos distintos com ao menos um item da categoria |
| `categorias[].participacao` | `faturamento ÷ faturamentoTotal × 100` |

O desconto é dado sobre o pedido inteiro, e não por item. Por isso, a análise por categoria e por produto usa o valor bruto dos itens, sem ratear o desconto.

**Erros:** `400` (filtros), `401` e `403`.

---

## 2.5 `GET /vendas/por-atendimento`

Comparativo entre entrega e retirada no balcão (RF02). Considera só os pedidos não cancelados. Os dois tipos sempre aparecem, mesmo com zero.

**Parâmetros:** os [filtros comuns](#parâmetros-de-filtro-comuns-aos-endpoints-2-a-9).

**Requisição**

```bash
curl -u gerente:SENHA "http://localhost:8080/api/dashboard/vendas/por-atendimento?inicio=2026-11-03&fim=2026-11-09"
```

**Resposta `200`**

```json
{
  "periodo": { "inicio": "2026-11-03", "fim": "2026-11-09", "dias": 7 },
  "tipos": [
    { "tipo": "ENTREGA", "descricao": "Entrega", "pedidos": 60, "faturamento": 5970.39, "ticketMedio": 99.51,
      "participacaoPedidos": 69.77, "participacaoFaturamento": 71.02 },
    { "tipo": "RETIRADA", "descricao": "Retirada no balcão", "pedidos": 26, "faturamento": 2436.07, "ticketMedio": 93.7,
      "participacaoPedidos": 30.23, "participacaoFaturamento": 28.98 }
  ]
}
```

| Campo | Descrição |
|---|---|
| `tipos[].tipo` | `ENTREGA`, `RETIRADA` ou `NAO_INFORMADO` (pedidos antigos sem o campo) |
| `participacaoPedidos` / `participacaoFaturamento` | Percentual sobre o total de pedidos e sobre o faturamento |

Com `tipoAtendimento=ENTREGA`, apenas o tipo escolhido é retornado.

**Erros:** `400` (filtros), `401` e `403`.

---

## 2.6 `GET /vendas/por-horario`

Distribuição dos pedidos não cancelados pelas 24 horas do dia e pelos dias da semana (RF02). Mostra os horários de pico e os dias de maior movimento.

**Parâmetros:** os [filtros comuns](#parâmetros-de-filtro-comuns-aos-endpoints-2-a-9).

**Requisição**

```bash
curl -u gerente:SENHA "http://localhost:8080/api/dashboard/vendas/por-horario?inicio=2026-11-03&fim=2026-11-09"
```

**Resposta `200`** (o vetor `porHora` sempre tem 24 posições, de `0` a `23`; aqui aparecem só algumas)

```json
{
  "periodo": { "inicio": "2026-11-03", "fim": "2026-11-09", "dias": 7 },
  "porHora": [
    { "hora": 0, "rotulo": "00h", "pedidos": 0, "faturamento": 0.0 },
    { "hora": 11, "rotulo": "11h", "pedidos": 4, "faturamento": 327.8 },
    { "hora": 12, "rotulo": "12h", "pedidos": 9, "faturamento": 755.0 },
    { "hora": 19, "rotulo": "19h", "pedidos": 21, "faturamento": 2286.45 },
    { "hora": 20, "rotulo": "20h", "pedidos": 14, "faturamento": 1383.0 },
    { "hora": 21, "rotulo": "21h", "pedidos": 15, "faturamento": 1493.77 },
    { "hora": 23, "rotulo": "23h", "pedidos": 2, "faturamento": 128.8 }
  ],
  "porDiaSemana": [
    { "dia": 1, "nome": "Segunda-feira", "ocorrencias": 1, "pedidos": 5, "mediaPedidos": 5.0, "faturamento": 444.7 },
    { "dia": 2, "nome": "Terça-feira", "ocorrencias": 1, "pedidos": 8, "mediaPedidos": 8.0, "faturamento": 603.2 },
    { "dia": 3, "nome": "Quarta-feira", "ocorrencias": 1, "pedidos": 10, "mediaPedidos": 10.0, "faturamento": 875.21 },
    { "dia": 4, "nome": "Quinta-feira", "ocorrencias": 1, "pedidos": 8, "mediaPedidos": 8.0, "faturamento": 619.9 },
    { "dia": 5, "nome": "Sexta-feira", "ocorrencias": 1, "pedidos": 16, "mediaPedidos": 16.0, "faturamento": 1570.44 },
    { "dia": 6, "nome": "Sábado", "ocorrencias": 1, "pedidos": 22, "mediaPedidos": 22.0, "faturamento": 2306.91 },
    { "dia": 7, "nome": "Domingo", "ocorrencias": 1, "pedidos": 17, "mediaPedidos": 17.0, "faturamento": 1986.1 }
  ],
  "horaPico": 19,
  "diaPico": "Sábado"
}
```

| Campo | Descrição |
|---|---|
| `porHora[].hora` | Hora local da entrada do pedido (0 a 23) |
| `porDiaSemana[].dia` | 1 = segunda … 7 = domingo (ISO 8601) |
| `porDiaSemana[].ocorrencias` | Quantas vezes aquele dia da semana aparece no período |
| `porDiaSemana[].mediaPedidos` | `pedidos ÷ ocorrencias`, com 1 casa |
| `horaPico` / `diaPico` | Hora com mais pedidos e dia com maior média; `null` sem pedidos |

**Erros:** `400` (filtros), `401` e `403`.

---

## 2.7 `GET /pedidos/status`

Situação dos pedidos do período (RF03): quantidade, percentual e valor por status, além dos tempos médios de atendimento.

**Parâmetros:** os [filtros comuns](#parâmetros-de-filtro-comuns-aos-endpoints-2-a-9).

**Requisição**

```bash
curl -u gerente:SENHA "http://localhost:8080/api/dashboard/pedidos/status?inicio=2026-11-03&fim=2026-11-09"
```

**Resposta `200`**

```json
{
  "periodo": { "inicio": "2026-11-03", "fim": "2026-11-09", "dias": 7 },
  "total": 96,
  "emAndamento": 0,
  "finalizados": 86,
  "cancelados": 10,
  "porStatus": [
    { "status": "RECEBIDO", "descricao": "Recebido", "quantidade": 0, "percentual": 0.0, "valor": 0.0 },
    { "status": "EM_PREPARACAO", "descricao": "Em preparação", "quantidade": 0, "percentual": 0.0, "valor": 0.0 },
    { "status": "PRONTO", "descricao": "Pronto", "quantidade": 0, "percentual": 0.0, "valor": 0.0 },
    { "status": "SAIU_PARA_ENTREGA", "descricao": "Saiu para entrega", "quantidade": 0, "percentual": 0.0, "valor": 0.0 },
    { "status": "RETIRADO", "descricao": "Retirado", "quantidade": 0, "percentual": 0.0, "valor": 0.0 },
    { "status": "FINALIZADO", "descricao": "Finalizado", "quantidade": 86, "percentual": 89.58, "valor": 8406.46 },
    { "status": "CANCELADO", "descricao": "Cancelado", "quantidade": 10, "percentual": 10.42, "valor": 1039.36 }
  ],
  "temposMedios": {
    "ateProntoMinutos": 30.2,
    "ateSaidaMinutos": 39.7,
    "ateFinalizacaoMinutos": 59.7
  }
}
```

| Campo | Descrição |
|---|---|
| `emAndamento` | Soma dos status Recebido, Em preparação, Pronto, Saiu para entrega e Retirado |
| `porStatus` | Os 7 status, sempre na ordem do fluxo. Status legados são somados ao status atual equivalente |
| `porStatus[].valor` | Soma do `valorTotal` dos pedidos naquele status |
| `temposMedios.ateProntoMinutos` | Média, em minutos, entre a entrada e a mudança para `PRONTO` (pelo histórico) |
| `temposMedios.ateSaidaMinutos` | Média entre a entrada e `dataHoraSaida` (saiu para entrega ou retirado) |
| `temposMedios.ateFinalizacaoMinutos` | Média entre a entrada e `dataHoraFinalizacao` |

Os tempos médios consideram só os pedidos não cancelados que já passaram pela etapa; ficam `null` quando não há nenhum.

**Erros:** `400` (filtros), `401` e `403`.

---

## 2.8 `GET /pedidos/recentes`

Últimos pedidos que atendem aos filtros, do mais novo para o mais antigo (RF03).

**Parâmetros:** [filtros comuns](#parâmetros-de-filtro-comuns-aos-endpoints-2-a-9) e mais:

| Parâmetro | Tipo | Padrão | Regras |
|---|---|---|---|
| `limite` | inteiro | `10` | De 1 a 50 |

**Requisição**

```bash
curl -u gerente:SENHA "http://localhost:8080/api/dashboard/pedidos/recentes?inicio=2026-11-03&fim=2026-11-09&limite=3"
```

**Resposta `200`**

```json
{
  "limite": 3,
  "pedidos": [
    { "id": "6af37b8e5269625023ec523f", "numero": 1107, "cliente": "Diego Souza", "tipoAtendimento": "RETIRADA",
      "status": "FINALIZADO", "statusDescricao": "Finalizado", "valorTotal": 93.3,
      "dataHoraEntrada": "2026-11-09T19:58:31", "minutosEmAberto": null },
    { "id": "6af37b8e5269625023ec523e", "numero": 1106, "cliente": "Gabriela Rocha", "tipoAtendimento": "ENTREGA",
      "status": "FINALIZADO", "statusDescricao": "Finalizado", "valorTotal": 111.8,
      "dataHoraEntrada": "2026-11-09T19:42:49", "minutosEmAberto": null },
    { "id": "6af37b8e5269625023ec523d", "numero": 1105, "cliente": "Paula Cristina Dias", "tipoAtendimento": "ENTREGA",
      "status": "FINALIZADO", "statusDescricao": "Finalizado", "valorTotal": 118.8,
      "dataHoraEntrada": "2026-11-09T14:44:53", "minutosEmAberto": null }
  ]
}
```

| Campo | Descrição |
|---|---|
| `id` | Identificador do pedido; o dashboard monta o link `/pedidos/{id}` |
| `cliente` | Somente o nome; telefone e endereço não são expostos |
| `minutosEmAberto` | Minutos desde a entrada, **só para pedidos em andamento**; `null` para finalizados e cancelados. O dashboard destaca como "atrasado" acima de 60 min |

**Erros:** `400` (filtros ou `limite` fora de 1 a 50), `401` e `403`.

---

## 2.9 `GET /produtos/mais-vendidos`

Ranking de produtos por quantidade ou faturamento (RF04). Considera só os itens de pedidos não cancelados.

**Parâmetros:** [filtros comuns](#parâmetros-de-filtro-comuns-aos-endpoints-2-a-9) e mais:

| Parâmetro | Valores | Padrão | Descrição |
|---|---|---|---|
| `criterio` | `QUANTIDADE` ou `FATURAMENTO` | `QUANTIDADE` | Ordenação do ranking; o empate é decidido pelo outro critério |
| `limite` | 1 a 50 | `10` | Tamanho do ranking |

**Requisição**

```bash
curl -u admin:SENHA "http://localhost:8080/api/dashboard/produtos/mais-vendidos?inicio=2026-11-03&fim=2026-11-09&criterio=FATURAMENTO&limite=5"
```

**Resposta `200`**

```json
{
  "periodo": { "inicio": "2026-11-03", "fim": "2026-11-09", "dias": 7 },
  "criterio": "FATURAMENTO",
  "limite": 5,
  "produtosVendidos": 16,
  "produtos": [
    { "posicao": 1, "produtoId": "6ad2a78fce8673156a042297", "nome": "Pizza Calabresa", "categoria": "Pizza",
      "quantidade": 29, "pedidos": 24, "faturamento": 1447.1, "participacao": 17.05 },
    { "posicao": 2, "produtoId": "6af37b8d5269625023ec4ddb", "nome": "Pizza Frango com Catupiry", "categoria": "Pizza",
      "quantidade": 20, "pedidos": 16, "faturamento": 1058.0, "participacao": 12.47 },
    { "posicao": 3, "produtoId": "6ad2a78fce8673156a042298", "nome": "Pizza Mussarela", "categoria": "Pizza",
      "quantidade": 23, "pedidos": 22, "faturamento": 1035.0, "participacao": 12.2 },
    { "posicao": 4, "produtoId": "6af37b8d5269625023ec4ddd", "nome": "Pizza Quatro Queijos", "categoria": "Pizza",
      "quantidade": 14, "pedidos": 13, "faturamento": 796.6, "participacao": 9.39 },
    { "posicao": 5, "produtoId": "6ad2a78fce8673156a042299", "nome": "Pizza Portuguesa", "categoria": "Pizza",
      "quantidade": 14, "pedidos": 13, "faturamento": 768.6, "participacao": 9.06 }
  ]
}
```

| Campo | Descrição |
|---|---|
| `produtosVendidos` | Total de produtos diferentes vendidos no período, mesmo os fora do ranking |
| `produtos[].pedidos` | Pedidos distintos que tinham o produto |
| `produtos[].participacao` | Participação no total do critério, considerando **todos** os produtos e não só os do ranking |

O nome e a categoria vêm da cópia gravada nos itens dos pedidos; se o produto foi renomeado, vale o nome do pedido mais recente. Assim, produtos já retirados do cardápio continuam aparecendo no ranking histórico.

**Erros:** `400` (filtros, `criterio` inválido ou `limite` fora de 1 a 50), `401` e `403`.

---

## 2.10 Exemplo completo com sessão (como o dashboard faz)

```bash
curl -u gerente:SENHA -c cookies.txt -s -o /dev/null http://localhost:8080/api/csrf
```

```bash
curl -b cookies.txt "http://localhost:8080/api/dashboard/resumo?inicio=2026-11-03&fim=2026-11-09"
```

No navegador, o dashboard chama os endpoints com `fetch(url, {credentials: 'same-origin', headers: {Accept: 'application/json'}})`, todos em paralelo. Os filtros da tela viram a mesma *query string* descrita acima.
