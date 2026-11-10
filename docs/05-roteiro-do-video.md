# 5. Roteiro do vídeo (até 3 minutos)

O objetivo é mostrar o **dashboard funcionando** e **justificar as principais decisões** de análise e projeto.
A narração tem cerca de 420 palavras, o que dá aproximadamente 2min50s em ritmo normal.

## Preparação (antes de gravar)

1. Habilitar os dados de demonstração. No `application-local.properties`, definir `APP_DEMO_ENABLED=true`.
2. Subir a aplicação:

   ```bash
   .\mvnw.cmd spring-boot:run
   ```

3. Deixar abertos:
   - **Aba 1:** `http://localhost:8080/dashboard`, logado como `gerente`, com zoom do navegador em 90%.
   - **Aba 2:** `docs/04-analise-e-projeto.md` no GitHub, posicionada no diagrama de arquitetura.
   - **Terminal:** na pasta do projeto, com os comandos da cena 6 já digitados no histórico.
4. Desligar notificações e fechar outras abas.

## Cenas

| Tempo | Tela | Ação | Narração |
|---|---|---|---|
| **0:00–0:15** | Dashboard (topo) | Mostrar a tela parada | "Este é o Dashboard Administrativo do PediuPatrão. Ele mostra ao dono da pizzaria como estão as vendas e os pedidos, sem precisar abrir pedido por pedido." |
| **0:15–0:45** | Aba 2: diagrama de arquitetura | Apontar navegador → API → MongoDB | "A tela não recebe dados prontos: ela consome uma API REST que eu projetei em `/api/dashboard`. Separei um endpoint por painel, como resumo, vendas, status e ranking. Assim, eles carregam em paralelo e, se um falhar, os outros continuam. Toda a conta é feita no MongoDB, com pipelines de agregação: a aplicação nunca carrega a lista de pedidos para somar em Java. A API é só de leitura e liberada apenas para ADMIN e GERENTE, em duas camadas." |
| **0:45–1:15** | Dashboard: indicadores e gráfico | Passar o mouse sobre o gráfico; clicar em **Semana** | "No topo ficam os indicadores: faturamento, pedidos, ticket médio, cancelamentos e descontos, cada um comparado ao período anterior de mesma duração. Uma decisão importante: pedido cancelado não entra no faturamento. Ele aparece à parte, como receita perdida. No gráfico, posso ver por dia, semana ou mês. A semana atual fica tracejada porque está incompleta. Sem isso, pareceria uma queda de vendas." |
| **1:15–1:40** | Rolar: status, ranking, categorias, horários | Clicar em **Faturamento** no ranking; mostrar o pico das 19h | "Aqui está a situação dos pedidos, com o tempo médio até ficar pronto e até a saída. Depois vêm os produtos mais vendidos, por quantidade ou faturamento, as categorias, entrega contra retirada e os horários de pico. A média por dia da semana divide pelo número de vezes que cada dia aparece no período, para não favorecer o sábado só porque houve cinco sábados." |
| **1:40–2:05** | Filtros | Escolher **Pizza** e **Entrega**; mostrar a URL; colocar o início depois do fim | "Os filtros valem para todos os painéis e ficam na URL, então dá para compartilhar o link. Se eu coloco a data inicial depois da final, a tela avisa e mantém os dados anteriores. A API valida também e responde 400." |
| **2:05–2:20** | Topo do dashboard | Clicar em **Atualizar**; mostrar o switch e o ícone de tabela | "Os dados atualizam no botão ou automaticamente a cada 60 segundos, com pausa quando a aba fica escondida. Cada gráfico tem uma visão em tabela, por acessibilidade." |
| **2:20–2:40** | Terminal | Rodar os 3 comandos abaixo | "Pela própria API: o gerente recebe o resumo em JSON, o atendente recebe 403 e um período inválido recebe 400, com o campo errado indicado." |
| **2:40–2:55** | Terminal / GitHub | `git log --graph` e o resultado dos testes | "O projeto segue gitflow, com branches de feature para a API, a tela e a documentação, uma release 2.0.0 e a tag na main. São 153 testes automatizados, incluindo testes das agregações num MongoDB real." |
| **2:55–3:00** | Dashboard | Voltar à visão geral | "Este é o PediuPatrão 2.0. Obrigado!" |

### Comandos da cena 6 (terminal)

Substitua `SENHA` pela senha inicial dos usuários.

```bash
curl -s -u gerente:SENHA "http://localhost:8080/api/dashboard/resumo?inicio=2026-11-03&fim=2026-11-09"
```

```bash
curl -s -u atendente:SENHA "http://localhost:8080/api/dashboard/resumo"
```

```bash
curl -s -u gerente:SENHA "http://localhost:8080/api/dashboard/resumo?inicio=2026-11-09&fim=2026-11-03"
```

### Comando da cena 7

```bash
git log --oneline --graph --all -16
```

## Dicas de gravação

- Use 1920×1080 e aumente a fonte do terminal (18 pt ou mais).
- Grave cada cena separada e junte depois; fica mais fácil caber em 3 minutos.
- Se passar do tempo, corte primeiro a cena de atualização (2:05–2:20). As cenas de arquitetura e de decisões são as que o enunciado pede.
