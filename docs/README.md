# Documentação — Dashboard Administrativo (PediuPatrão 2.0)

| # | Documento | Conteúdo |
|---|---|---|
| 1 | [Requisitos](01-requisitos.md) | Contexto, atores, **requisitos funcionais** RF01 a RF07 com critérios de aceitação e **requisitos não funcionais** |
| 2 | [API do dashboard](02-api-dashboard.md) | Os 9 endpoints: **parâmetros, exemplos de requisição e resposta e possíveis erros** |
| 3 | [Evidências de testes](03-evidencias-de-testes.md) | 153 testes automatizados, 24 chamadas `curl`, consistência entre endpoints, desempenho e capturas do **dashboard funcionando** |
| 4 | [Análise e projeto](04-analise-e-projeto.md) | Arquitetura, agregações no MongoDB, definições de negócio, decisões de visualização, segurança e gitflow |

## Pasta `evidencias/`

| Arquivo | Origem |
|---|---|
| `testes-automatizados.txt` | Saída do `./mvnw test` (resumo por classe) |
| `telas/*.png` | Capturas do dashboard no Microsoft Edge, com os dados de demonstração |

## Reproduzindo

```bash
.\mvnw.cmd test
```

```bash
.\mvnw.cmd spring-boot:run
```

Com `APP_DEMO_ENABLED=true` no `application-local.properties`, a base é preenchida com cerca de 1.100 pedidos de demonstração. Depois, acesse `http://localhost:8080/dashboard` como `gerente`.
