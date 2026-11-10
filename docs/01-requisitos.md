# 1. Requisitos do Dashboard Administrativo

Documento de requisitos do **Dashboard Administrativo** do PediuPatrão (versão 2.0.0).

## 1.1 Contexto e objetivo

O responsável pela pizzaria precisa acompanhar o desempenho das vendas, ver como estão os pedidos e identificar tendências da operação sem abrir pedido por pedido.
O dashboard é uma tela do próprio sistema (`/dashboard`) que consome uma **API REST de leitura** (`/api/dashboard/**`), calculada direto no MongoDB a partir da coleção `pedidos`.

### Atores

| Ator | Perfil | Uso do dashboard |
|---|---|---|
| Dono / administrador | `ADMIN` | Acompanha os indicadores e as tendências |
| Gerente da loja | `GERENTE` | Acompanha as vendas e os pedidos em andamento e reage a atrasos e cancelamentos |
| Atendente | `ATENDENTE` | **Sem acesso**: o dashboard mostra faturamento e dados gerenciais |

### Fonte dos dados

Todos os números vêm dos pedidos já registrados pelo sistema: itens, valores, desconto, status, histórico de status, data e hora de entrada, saída e finalização, e tipo de atendimento.
Não existe uma base paralela nem dados copiados: a API agrega os pedidos no momento da consulta.

---

## 1.2 Requisitos funcionais

Cada requisito traz os **critérios de aceitação** e onde foi implementado (endpoint, componente da tela e testes).

### RF01 — Indicadores gerais de vendas

O dashboard deve mostrar, para o período filtrado, os principais números da operação, cada um comparado com o período anterior de mesma duração.

| Indicador | Regra de cálculo |
|---|---|
| Faturamento | Soma do `valorTotal` dos pedidos **não cancelados** (já com desconto) |
| Pedidos | Quantidade de pedidos registrados no período (inclui os cancelados) |
| Ticket médio | Faturamento ÷ pedidos não cancelados |
| Cancelamentos | Quantidade, taxa (% dos pedidos) e valor que deixou de ser faturado |
| Descontos concedidos | Soma dos descontos dos pedidos não cancelados e % sobre o valor bruto |
| Itens vendidos | Soma das quantidades dos itens dos pedidos não cancelados e média por pedido |

Critérios de aceitação:
- [x] Cada indicador mostra a variação percentual em relação ao período anterior equivalente. Por exemplo, para 7 dias a comparação é com os 7 dias imediatamente anteriores.
- [x] Quando o período anterior não tem valor, a variação aparece como "sem base de comparação", sem divisão por zero nem "∞%".
- [x] Os pedidos cancelados não entram no faturamento, no ticket médio nem nos itens vendidos, e aparecem à parte.
- [x] A cor da variação segue o significado: aumento de cancelamentos é vermelho, aumento de faturamento é verde. A seta e o sinal acompanham a cor, para que a cor nunca seja a única pista.

Implementação: `GET /api/dashboard/resumo` · cartões de indicadores · `DashboardServiceTest.resumo*`, `DashboardRepositoryMongoTest.totais*`.

### RF02 — Análise de vendas (gráficos)

| Gráfico | Pergunta que responde | Endpoint |
|---|---|---|
| Faturamento no período (linha) por **dia, semana ou mês** | As vendas estão subindo ou caindo? | `GET /vendas` |
| Faturamento por categoria (barras horizontais) | O que mais gera receita: pizza, esfiha ou bebida? | `GET /vendas/por-categoria` |
| Entrega × retirada (barra segmentada e tabela) | Qual canal tem mais pedidos e maior ticket? | `GET /vendas/por-atendimento` |
| Pedidos por horário (colunas, pico destacado) | Em que horário reforçar a equipe? | `GET /vendas/por-horario` |
| Média de pedidos por dia da semana (colunas) | Qual o dia de maior movimento? | `GET /vendas/por-horario` |

Critérios de aceitação:
- [x] A série temporal inclui os dias, semanas ou meses **sem vendas** com valor zero, para não "pular" datas.
- [x] A semana começa na segunda-feira, e os rótulos usam o formato brasileiro (`03/11`, `nov/2026`).
- [x] Grupos incompletos aparecem **tracejados**, para não parecer uma queda de vendas. É o caso da semana cortada pelo filtro ou do dia e da semana ainda em andamento.
- [x] A média por dia da semana divide pelo número de vezes que aquele dia ocorreu no período. Sem isso, um período com cinco sábados e quatro segundas favoreceria o sábado.
- [x] Cada gráfico tem tooltip com o valor exato e um botão que troca o gráfico por uma **tabela** com os mesmos dados.

Implementação: `DashboardService.vendas`, `vendasPorCategoria`, `vendasPorAtendimento`, `vendasPorHorario` · `DashboardRepository.serie`, `horarios` etc. · testes `serie*`, `horarios*`, `categorias*` e `atendimento*`.

### RF03 — Situação dos pedidos

Critérios de aceitação:
- [x] Mostra a quantidade e o percentual de pedidos **por status**: Recebido, Em preparação, Pronto, Saiu para entrega, Retirado, Finalizado e Cancelado. Os status sem pedidos aparecem com zero.
- [x] Resume os pedidos em **andamento**, **finalizados** e **cancelados**.
- [x] Mostra o **tempo médio** desde a entrada até o pedido ficar pronto, até a saída e até a finalização. O tempo é calculado pelo histórico de status e pelas datas de saída e finalização.
- [x] Lista os **pedidos mais recentes** com cliente, atendimento, status, valor, entrada e tempo em aberto. Pedidos abertos há mais de 60 minutos ganham destaque, com ícone e texto além da cor, e cada número abre o detalhe do pedido.
- [x] Pedidos gravados com status antigos (por exemplo, `ENTREGUE`) são convertidos para o status atual equivalente.

Implementação: `GET /api/dashboard/pedidos/status`, `GET /api/dashboard/pedidos/recentes` · painéis "Situação dos pedidos" e "Pedidos recentes" · testes `situacao*`, `recentes*`, `temposMedios*` e `contagemPorStatus*`.

### RF04 — Produtos mais vendidos

Critérios de aceitação:
- [x] O ranking pode ser ordenado por **quantidade vendida** ou por **faturamento**.
- [x] Mostra posição, produto, categoria, quantidade, faturamento, número de pedidos e **participação** sobre o total do período. A participação considera todos os produtos, não só os exibidos.
- [x] O tamanho do ranking é configurável (`limite`, de 1 a 50; a tela usa 10). A tela também informa quantos produtos diferentes foram vendidos.
- [x] Itens de pedidos cancelados não entram no ranking.

Implementação: `GET /api/dashboard/produtos/mais-vendidos` · painel "Produtos mais vendidos" · testes `ranking*` e `vendasPorProduto*`.

### RF05 — Filtros de consulta

| Filtro | Opções |
|---|---|
| Período | Hoje, últimos 7, 30 ou 90 dias, mês atual ou personalizado (datas de início e fim) |
| Status | Seleção múltipla dos 7 status |
| Atendimento | Entrega ou retirada no balcão |
| Categoria | Categorias que existem no cardápio (pizza, esfiha, bebida…) |

Critérios de aceitação:
- [x] Os filtros ficam numa única linha acima dos gráficos e se aplicam a **todos** os painéis ao mesmo tempo.
- [x] Os filtros ficam na **URL** (`/dashboard?periodo=30&categoria=Pizza`). Assim, um link compartilhado ou a página recarregada mantém a mesma visão.
- [x] O período padrão é dos últimos 30 dias, e o máximo consultável é de 366 dias.
- [x] Datas inválidas ou com início depois do fim são barradas na tela, com aviso, e também na API (resposta `400`).
- [x] As opções de status, atendimento e categoria vêm da própria API (`GET /filtros`), sem valores fixos no front-end.
- [x] O botão "Limpar filtros" volta ao padrão.

Implementação: `FiltroDashboardParams` e `DashboardService.resolverFiltro` · `GET /api/dashboard/filtros` · testes `periodoPadrao*`, `rejeita*`, `filtro*` e `filtrosDeStatusEAtendimento`.

### RF06 — Integração com a API

Critérios de aceitação:
- [x] A tela não recebe dados prontos do servidor: todos os painéis são carregados por `fetch` em `/api/dashboard/**`, em JSON.
- [x] Cada painel tem a **própria requisição**. Assim, os painéis carregam em paralelo e a falha em um não derruba os outros.
- [x] A autenticação é a mesma sessão do sistema. Se a sessão expira (`401`), o usuário é levado ao login.
- [x] Os erros da API seguem um formato único (`status`, `erro`, `mensagem`, `caminho` e `campos`), e a tela mostra a `mensagem` recebida.
- [x] Quando os filtros mudam, as requisições antigas ainda pendentes são canceladas (`AbortController`), para que uma resposta atrasada nunca sobrescreva uma mais nova.

Implementação: `static/js/dashboard.js` (`buscar`, `carregarPainel` e `atualizar`) · `DashboardController` · `DashboardControllerTest`.

### RF07 — Atualização e tratamento de dados

Atualização:
- [x] O botão **Atualizar** recarrega todos os painéis.
- [x] A **atualização automática a cada 60 s** pode ser ligada e desligada, e a preferência fica salva no navegador. Ela pausa quando a aba fica oculta e volta ao reabrir.
- [x] A tela mostra o horário da última atualização ("Atualizado às 17:31:27").
- [x] Durante a recarga, os dados anteriores continuam visíveis em opacidade reduzida, e a tela não pisca em branco.

Tratamento de dados:
- [x] Os valores monetários são guardados como texto no MongoDB e convertidos para decimal na agregação (`$convert`, com valor 0 quando nulo ou inválido). O arredondamento usa `BigDecimal` com 2 casas (`HALF_UP`).
- [x] Os horários são agrupados no **fuso da pizzaria**: um pedido às 23h30 não "vira" o dia seguinte por causa do UTC.
- [x] Os status antigos são normalizados, e os pedidos antigos sem tipo de atendimento aparecem como "Não informado".
- [x] As lacunas da série são preenchidas com zero, e os grupos incompletos são sinalizados (campo `parcial`).
- [x] Os painéis mostram estados de **carregando**, **sem dados** ("Nenhuma venda no período selecionado") e **erro**, com o botão "Tentar novamente".
- [x] Se a biblioteca de gráficos não carregar, por exemplo sem internet para a CDN, o dashboard continua funcionando com as tabelas.

Implementação: `DashboardRepository` (pipelines de agregação) · `DashboardService` · `dashboard.js` · testes `*Vazio*`, `serieDiariaRespeitaOFusoDaPizzaria`, `situacaoAgrupaStatusLegado*` e `atendimentoNaoInformado*`.

---

## 1.3 Requisitos não funcionais

| Código | Categoria | Requisito | Como foi atendido | Evidência |
|---|---|---|---|---|
| RNF01 | Segurança | Somente **ADMIN** e **GERENTE** acessam a tela e a API do dashboard | Regra de URL no `SecurityConfig` e `@PreAuthorize` em todos os métodos do `DashboardService` (duas camadas) | `DashboardControllerTest`, `DashboardAutorizacaoTest`, `DashboardWebControllerTest` |
| RNF02 | Segurança | A API é somente leitura e não aceita escrita | Só `GET` é liberado; `POST`, `PUT` e `DELETE` caem no `denyAll`, e sem token CSRF a resposta é `403` | `DashboardControllerTest.dashboardEhSomenteLeitura` e evidência E-21 |
| RNF03 | Privacidade | Expor só o necessário | "Pedidos recentes" mostra o nome do cliente, sem telefone e sem endereço | `PedidosRecentesResponse` |
| RNF04 | Desempenho | Cada endpoint responde em menos de 500 ms com o período máximo | Agregação no banco (`$match` → `$group`/`$facet`) e índices `ix_dataHoraEntrada` e `ix_status_dataHoraEntrada`. Medido: **6 a 13 ms** com 366 dias e 1.100 pedidos | [Evidências §3.4](03-evidencias-de-testes.md#34-desempenho) |
| RNF05 | Desempenho | Limitar o custo de cada consulta | Período máximo de 366 dias, ranking de no máximo 50 itens e lista de recentes de no máximo 50 | `rejeita*` e evidências E-16 e E-18 |
| RNF06 | Confiabilidade | Os números devem ser os mesmos entre os painéis | Os endpoints usam o mesmo filtro e a mesma regra de "pedido válido". A soma das séries é igual ao resumo | [Evidências §3.3](03-evidencias-de-testes.md#33-consistência-entre-endpoints) |
| RNF07 | Confiabilidade | Valores monetários exatos | `BigDecimal` no Java e `decimal128` na agregação, nunca `double` para dinheiro | `DashboardRepositoryMongoTest` |
| RNF08 | Usabilidade | Interface em português, com formatos brasileiros | `Intl.NumberFormat('pt-BR')` para moeda, números e datas | Capturas de tela |
| RNF09 | Usabilidade | Funcionar no celular | Layout responsivo testado em 375 px de largura, sem rolagem horizontal da página | Captura 13 |
| RNF10 | Acessibilidade | A informação não pode depender só da cor | Status com rótulo, deltas com seta e sinal, visão em tabela de cada gráfico, `aria-live` na atualização e `aria-label` nos gráficos | Capturas 05 e 08 |
| RNF11 | Acessibilidade | Cores distinguíveis para daltonismo | Paleta validada para contraste e simulação de daltonismo (azul `#2a78d6` e laranja `#eb6834`), e um único eixo por gráfico | [Análise e projeto §4.6](04-analise-e-projeto.md#46-front-end) |
| RNF12 | Manutenibilidade | Código em camadas e testável | Controller → Service → Repository, DTOs `record` imutáveis e **153 testes automatizados** | [Evidências §3.1](03-evidencias-de-testes.md#31-testes-automatizados) |
| RNF13 | Portabilidade | Rodar com MongoDB local ou Atlas e sem build de front-end | JavaScript puro, Chart.js 4.4.7 via CDN e conexão por variável de ambiente | README |
| RNF14 | Versionamento | Seguir o gitflow | Branches `feature/*` → `develop` → `release/2.0.0` → `main`, com a tag `v2.0.0` | [Análise e projeto §4.8](04-analise-e-projeto.md#48-gitflow) |
