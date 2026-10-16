# PediuPatrão — Sistema de Pedidos da Pizzaria

Evolução do projeto **PediuPatrão** (Spring Boot + Spring Security + Thymeleaf + MongoDB) com:

- **três perfis de acesso** (ADMIN, GERENTE e ATENDENTE) com autorização aplicada no backend;
- **pedidos com itens, valores calculados no servidor, responsáveis e data/hora de entrada e saída**;
- **fluxo de status controlado** (RECEBIDO → EM PREPARAÇÃO → PRONTO → SAIU PARA ENTREGA/RETIRADO → FINALIZADO);
- **desconto e cancelamento exclusivos do GERENTE**;
- **auditoria persistente no MongoDB**, somente leitura e com verificação de integridade.

---

## 1. Tecnologias

| Item | Versão |
|---|---|
| Java | 21 |
| Spring Boot | 3.4.4 (Web, Data MongoDB, Security, Thymeleaf, Validation) |
| Thymeleaf Extras Spring Security | 6 |
| MongoDB | 8.0 local **ou** MongoDB Atlas |
| Testes | JUnit 5, Mockito, Spring Security Test (MockMvc) |
| Interface | Bootstrap 5.3 + Bootstrap Icons |

---

## 2. Como executar

### 2.1 Pré-requisitos

- **JDK 21** (a variável `JAVA_HOME` deve apontar para ele).
- **MongoDB**, em uma destas opções:
  - **Local** (recomendado para desenvolvimento e para a demonstração): instale o MongoDB Community Server.
    No Windows: `winget install MongoDB.Server --version 8.0.23`. Ele roda como serviço em `127.0.0.1:27017`.
  - **Atlas**: use a URI do cluster. O IP da máquina precisa estar liberado em *Network Access* no Atlas.
    Se não estiver, a conexão falha com `SSLException: Received fatal alert: internal_error`.

### 2.2 Configuração do banco (sem credenciais no código)

A URI **não fica mais gravada** no `application.properties`. Ela vem de:

1. variáveis de ambiente `MONGODB_URI` e `MONGODB_DATABASE`, **ou**
2. o arquivo `application-local.properties` na raiz do projeto, que é **ignorado pelo git**:

```bash
copy application-local.properties.example application-local.properties
```

```properties
# application-local.properties
MONGODB_URI=mongodb://127.0.0.1:27017
MONGODB_DATABASE=pediupatrao
```

Sem nenhuma configuração, o padrão é `mongodb://127.0.0.1:27017` com o banco `pediupatrao`.

### 2.3 Executar

```bash
.\mvnw.cmd spring-boot:run
```

Também é possível abrir o projeto no NetBeans/IntelliJ e executar `PizzariaPedidosApplication`, com o diretório de trabalho na raiz do projeto.

Acesse **http://localhost:8080**.

### 2.4 Usuários iniciais

Na primeira execução, o `DataInitializer` cria **um usuário de cada perfil** (se ainda não existirem) e um **cardápio básico com 6 produtos**:

| Usuário | Perfil |
|---|---|
| `admin` | ADMIN |
| `gerente` | GERENTE |
| `atendente` | ATENDENTE |

A senha inicial dos três usuários vem da propriedade `app.seed.senha-inicial` (padrão de desenvolvimento: `Pizza@2026`).
Para usar outra senha, defina a variável `APP_SEED_SENHA` antes da primeira execução. A senha nunca é escrita no log.

---

## 3. Requisitos implementados

### RF01 — Controle de usuários e permissões

| Perfil | O que pode fazer |
|---|---|
| **ADMIN** | Administrar usuários e atribuir perfis; consultar pedidos; visualizar a auditoria. |
| **GERENTE** | Consultar pedidos e avançar o fluxo; **aplicar descontos**; **cancelar pedidos**; consultar clientes (e excluir clientes sem pedidos); manter o cardápio; visualizar a auditoria. |
| **ATENDENTE** | Cadastrar e atualizar clientes; registrar pedidos; acompanhar e avançar o andamento dos pedidos. |

- O `UsuarioDetailsService` atribui `ROLE_ADMIN`, `ROLE_GERENTE` ou `ROLE_ATENDENTE` conforme o perfil gravado. Usuários sem perfil válido ficam **desabilitados**.
- A autorização é aplicada no backend em **duas camadas**:
  1. **Regras de URL** no `SecurityConfig` (telas e APIs). Rotas não mapeadas são negadas (`denyAll`).
  2. **`@PreAuthorize` nos serviços** (`@EnableMethodSecurity`). Por exemplo, `PedidoService.aplicarDesconto` e `cancelarPedido` exigem `hasRole('GERENTE')`; o ADMIN também é bloqueado.
- **Proteção contra autoelevação e atribuição indevida de privilégios:**
  - não existe mais cadastro público (`/api/usuarios` era `permitAll`): só o ADMIN cria usuários ou altera perfis;
  - o ADMIN **não pode alterar o próprio perfil** nem **excluir a própria conta**;
  - o sistema sempre mantém **ao menos um ADMIN**;
  - o formulário e a API recebem um DTO (`UsuarioForm`) sem `id`, o que impede *mass assignment*; o username é imutável;
  - ao alterar perfil ou senha, ou ao excluir um usuário, as **sessões ativas dele são encerradas** (`SessionRegistry`), e o usuário precisa entrar de novo;
  - as respostas da API **não expõem a senha nem o hash** (`UsuarioResponse`).

### RF02 — Evolução do cadastro de pedidos

`Pedido` passou a registrar:

- cliente (id + nome copiado), tipo de atendimento (ENTREGA/RETIRADA) e endereço de entrega copiado do cadastro;
- **itens** (`ItemPedido`: produto, nome, tipo, preço unitário, quantidade, subtotal);
- subtotal, percentual de desconto, valor do desconto e **valor total**;
- **data/hora de entrada** e **responsável pelo registro**;
- número sequencial (`#1`, `#2`…), histórico de status e versão (controle de concorrência otimista).

**Cálculo no servidor:** a requisição (`PedidoForm`) só contém cliente, tipo de atendimento, observações e itens com `produtoId` e `quantidade`.
O preço vem do cadastro do produto, e o servidor calcula `subtotal`, `desconto` e `total`.
Campos como `valorTotal`, `status` ou `responsavelEntrada` enviados no JSON ou no formulário são **ignorados**.
Validações: ao menos 1 item, quantidade de 1 a 50, até 30 itens, produto existente e **ativo**, endereço obrigatório para entrega e itens repetidos somados.

### RF03 — Entrada, saída e transições

```
RECEBIDO ──► EM_PREPARACAO ──► PRONTO ──┬──► SAIU_PARA_ENTREGA ──┐
   │              │              │      │    (tipo ENTREGA)      ├──► FINALIZADO
   │              │              │      └──► RETIRADO ───────────┘
   │              │              │           (tipo RETIRADA)
   └──────────────┴──────────────┴──► CANCELADO  (somente GERENTE, com justificativa)
```

- As transições ficam em `StatusPedido.proximosNoFluxo()` e são validadas no `PedidoService.atualizarStatus()`.
- **Bloqueios:** não é possível pular etapas (ex.: finalizar um pedido em preparação), voltar etapas, usar uma saída incompatível com o tipo de atendimento ou alterar qualquer coisa depois do cancelamento. Cancelar pelo fluxo comum também não é aceito.
- **Entrada:** `dataHoraEntrada` e `responsavelEntrada` são gravados no registro.
- **Saída:** em SAIU_PARA_ENTREGA ou RETIRADO, o sistema grava **obrigatoriamente** `dataHoraSaida` e `responsavelSaida`. A finalização também registra data/hora e responsável.
- Cada mudança entra no **histórico** do pedido (linha do tempo na tela de detalhe) e na auditoria.
- Itens e cliente só podem ser alterados enquanto o pedido está RECEBIDO.

### RF04 — Cancelamentos e descontos (exclusivos do GERENTE)

- **Desconto:** percentual de **0 a 20%** (no máximo 2 casas decimais), calculado sobre o subtotal. Ele não acumula: um novo desconto substitui o anterior, e 0 remove o desconto. Só é aceito **antes da saída** (RECEBIDO, EM PREPARAÇÃO ou PRONTO) e em pedidos não cancelados. Registra responsável, data/hora e motivo (opcional).
- **Cancelamento:** exige **justificativa de 10 a 500 caracteres** e só é aceito antes da saída. **Preserva os dados históricos** (itens, valores, responsáveis) e **impede alterações posteriores**.
- As duas operações são auditadas com usuário, perfil, data/hora e valores anteriores e novos (status, percentual, desconto e total).

### RF05 — Auditoria de pedidos e clientes

Coleção `auditoria` no MongoDB (`RegistroAuditoria`). Cada evento registra:

| Campo | Exemplo |
|---|---|
| Usuário / perfil | `atendente` / `ATENDENTE` |
| Data e hora | `15/10/2026 — 23:51:19` |
| Operação | `ALTERACAO_CLIENTE` (Alteração de cliente) |
| Entidade / id | `CLIENTE` / `6ad190ec…` |
| Campos alterados | `logradouro: Rua das Flores → Av. Brasil` |
| Justificativa | (quando exigida, ex.: cancelamento) |

**Eventos auditados:** inclusão e alteração de pedidos, mudanças de status (com saída e finalização), descontos, cancelamentos, inclusão, alteração e exclusão de clientes (endereço e telefone) e de produtos, administração de usuários e **tentativas de acesso não autorizado** (perfil sem permissão ou token CSRF inválido).

**Consulta restrita, integridade e proteção de dados:**

- a consulta (tela **Auditoria** e `GET /api/auditoria`) é exclusiva de **ADMIN e GERENTE**, com filtros por entidade, operação, usuário, id e período;
- **não há alteração nem exclusão:** o `RegistroAuditoriaRepository` só declara `insert` e consultas, a entidade é imutável (sem setters), e qualquer `POST/PUT/DELETE` em `/api/auditoria` é negado (403);
- **encadeamento por hash SHA-256:** cada registro guarda o hash do anterior e uma sequência única. O botão **Verificar integridade** (`GET /api/auditoria/integridade`) recalcula a cadeia e aponta registros alterados, removidos ou inseridos diretamente no banco;
- **senhas e tokens nunca são gravados:** campos de credenciais viram `[PROTEGIDO]`, e o log da aplicação traz apenas metadados (sem valores);
- **minimização de dados pessoais:** o telefone é gravado mascarado (`(11) *****-4321`); a auditoria de pedidos registra só o nome do cliente.

---

## 4. Segurança aplicada

- **CSRF ativo** em todos os formulários (o `th:action` inclui o token) e nas chamadas `fetch`, que leem o token das meta tags do layout e o enviam no cabeçalho `X-CSRF-TOKEN`. O logout é feito por `POST`.
- **APIs** respondem `401` (não autenticado) e `403` (sem permissão) em **JSON**. As telas redirecionam para o login ou mostram a página de erro.
- **HTTP Basic** habilitado para testar as APIs via `curl`/PowerShell. O CSRF continua exigido; o token é obtido em `GET /api/csrf`.
- Senhas com **BCrypt** e no mínimo 8 caracteres; cookie de sessão `HttpOnly` e `SameSite=Lax`.
- Correção de **XSS** na lista de clientes (dados escapados antes de entrar no HTML).
- URI do MongoDB fora do código-fonte (`application-local.properties` no `.gitignore`).

---

## 5. API REST

| Método | Rota | Perfis |
|---|---|---|
| GET | `/api/pedidos`, `/api/pedidos/{id}` | ADMIN, GERENTE, ATENDENTE |
| POST | `/api/pedidos` | GERENTE, ATENDENTE |
| PUT | `/api/pedidos/{id}` (itens, somente RECEBIDO) | GERENTE, ATENDENTE |
| PUT | `/api/pedidos/{id}/status?status=PRONTO` | GERENTE, ATENDENTE |
| POST | `/api/pedidos/{id}/desconto` `{"percentual":10,"justificativa":"..."}` | **GERENTE** |
| POST | `/api/pedidos/{id}/cancelamento` `{"justificativa":"..."}` | **GERENTE** |
| GET | `/api/clientes`, `/api/clientes/{id}` | GERENTE, ATENDENTE |
| POST / PUT | `/api/clientes`, `/api/clientes/{id}` | ATENDENTE |
| DELETE | `/api/clientes/{id}` (sem pedidos) | GERENTE |
| GET | `/api/produtos`, `/api/produtos/{id}` | GERENTE, ATENDENTE |
| POST / PUT / DELETE | `/api/produtos/**` | GERENTE |
| GET / POST / PUT / DELETE | `/api/usuarios/**` | **ADMIN** |
| GET | `/api/auditoria`, `/api/auditoria/{id}`, `/api/auditoria/integridade` | ADMIN, GERENTE |
| GET | `/api/csrf` | autenticado |

Exemplo com `curl` (Git Bash). O token CSRF fica ligado à sessão do cookie:

```bash
curl -u gerente:SENHA -c cookies.txt http://localhost:8080/api/csrf
```

```bash
curl -u atendente:SENHA -b cookies.txt -H "X-CSRF-TOKEN: TOKEN" -H "Content-Type: application/json" -X POST http://localhost:8080/api/pedidos/ID/cancelamento -d "{\"justificativa\":\"teste de bloqueio\"}"
```

Resposta esperada para o atendente: `403 {"erro":"Acesso negado","mensagem":"O perfil ATENDENTE não tem permissão para esta operação."}`.

---

## 6. Testes

```bash
.\mvnw.cmd test
```

São **83 testes automatizados**, e nenhum precisa de banco de dados:

| Classe | O que verifica |
|---|---|
| `StatusPedidoTest` | Transições válidas e inválidas, saída conforme o tipo, bloqueio após cancelamento |
| `PedidoServiceTest` | Cálculo no servidor, validações de itens, fluxo completo com responsáveis, limite de 20%, justificativa, preservação dos dados no cancelamento, auditoria |
| `ClienteServiceTest` | Valores anteriores e novos de endereço, telefone mascarado, id forjado ignorado, exclusão bloqueada |
| `UsuarioServiceTest` | BCrypt, senha fora da auditoria, autoelevação, último ADMIN, encerramento de sessões |
| `AuditoriaServiceTest` | Usuário/perfil/data, encadeamento de hash, detecção de adulteração e remoção |
| `AutorizacaoServicosTest` | `@PreAuthorize` nos serviços (2ª camada) |
| `SegurancaHttpTest` | 401/403 nas APIs e telas, CSRF, cadastro de usuário não público, auditoria somente leitura |

### Script de demonstração dos bloqueios (aplicação rodando)

```bash
powershell -ExecutionPolicy Bypass -File .\scripts\demonstrar-bloqueios.ps1
```

O script autentica como `atendente`, `gerente` e `admin`, registra um pedido e mostra o código HTTP de cada tentativa (permitida ou bloqueada).
Ao final, as tentativas negadas aparecem na tela **Auditoria**.

---

## 7. Principais alterações em relação ao projeto original

**Novos componentes:** `Perfil`, `StatusPedido`, `TipoAtendimento`, `ItemPedido`, `HistoricoStatus`, `RegistroAuditoria`, `AlteracaoCampo`, `TipoOperacao`; `AuditoriaService`, `SequenciaService`, `RegistroAuditoriaRepository`; DTOs (`PedidoForm`, `DescontoForm`, `CancelamentoForm`, `UsuarioForm`, `UsuarioResponse`…); `ApiExceptionHandler` e `WebControllerAdvice`; handlers de segurança (`AcessoNegadoHandler`, `AutenticacaoEntryPoint`); controladores de tela em `controller/web`; telas `pedidos/form`, `pedidos/detalhe` e `auditoria/lista`.

**Classes revisadas:** `SecurityConfig`, `UsuarioDetailsService`, `UsuarioService`, `Pedido`, `PedidoService`, `ClienteService`, `ProdutoService`, `DataInitializer`, `MongoInitConfig`, controladores REST e formulários.

**Correções de problemas existentes:**

- `/api/usuarios` era público e devolvia o hash das senhas;
- a role era fixa (`USER`) e o CSRF estava desativado;
- a tela **Produtos** não abria (não era um fragmento do layout) e seus botões chamavam rotas inexistentes;
- o HTML da sidebar estava mal formado e havia links para rotas inexistentes;
- XSS na lista de clientes;
- a edição de cliente apagava o campo legado `endereco`;
- credenciais do MongoDB estavam no código; dependências não usadas (Lombok, JPA) foram removidas;
- os templates não usados (`pedidos.html`, `produto-form.html`, `sidebar_1.html`) e a rota `/teste` foram removidos.

**Compatibilidade com dados antigos:** conversores de leitura transformam status antigos (`EM_PREPARO`, `ENTREGUE`…) e a role `USER`. Pedidos antigos sem itens continuam visíveis.

---

## 8. Roteiro sugerido para o vídeo

1. **Login com os três perfis**, mostrando que o menu e a home mudam (ADMIN: Pedidos, Auditoria, Usuários; GERENTE: + Clientes e Produtos; ATENDENTE: sem Auditoria e Usuários).
2. **ATENDENTE:** cadastrar um cliente e **registrar um pedido** com 2 itens. Mostrar o total calculado, a data/hora de entrada e o responsável. Avançar para *Em preparação* e *Pronto*. Alterar o **endereço e o telefone** do cliente.
3. **Bloqueio no backend:** com o ATENDENTE logado, abrir o console do navegador (F12) e executar o comando abaixo, que deve retornar `403`. Em seguida, rodar o script `scripts\demonstrar-bloqueios.ps1`.

   ```javascript
   fetch('/api/pedidos/ID/cancelamento', {method:'POST', headers: csrfHeaders({'Content-Type':'application/json'}), body: JSON.stringify({justificativa:'teste de bloqueio'})}).then(r => r.json()).then(console.log)
   ```

4. **GERENTE:** aplicar **10% de desconto**; tentar 25% (rejeitado). **Cancelar** outro pedido com justificativa e mostrar que ele não aceita mais alterações. No primeiro pedido, avançar para **Saiu para entrega** (data/hora e responsável da saída) e **Finalizado**. Mostrar que desconto e cancelamento deixam de aparecer e são recusados depois da saída.
5. **ADMIN:** mostrar que consulta pedidos, mas recebe `403` ao tentar desconto ou cancelamento. Criar um usuário e tentar alterar o **próprio perfil** (bloqueado).
6. **Auditoria** (ADMIN ou GERENTE): filtrar pelo pedido e pelo cliente e mostrar usuário, perfil, data/hora e valores anteriores e novos (incluindo `logradouro: Rua das Flores → Av. Brasil`), a justificativa do cancelamento e as tentativas negadas. Clicar em **Verificar integridade**.

---

## 9. Limitações conhecidas

- A gravação da entidade e a do registro de auditoria são operações separadas (sem transação multi-documento), para manter compatibilidade com MongoDB *standalone*. Em produção com *replica set*, o recomendado é usar `MongoTransactionManager`.
- O registro de sessões (`SessionRegistryImpl`) é em memória, adequado para uma única instância da aplicação.
- O encadeamento de hashes **detecta** adulterações feitas diretamente no banco, mas não as impede. Para isso, o usuário do banco da aplicação deveria ter apenas permissão de *insert/find* na coleção `auditoria`.
