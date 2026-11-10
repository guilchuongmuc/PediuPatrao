(() => {
    'use strict';

    const raiz = document.getElementById('dashboard');
    if (!raiz) {
        return;
    }

    const API = raiz.dataset.api;
    const URL_PEDIDOS = raiz.dataset.pedidos;
    const URL_LOGIN = raiz.dataset.login;
    const INTERVALO_ATUALIZACAO_MS = 60000;
    const CHAVE_AUTO = 'pediupatrao.dashboard.autoAtualizar';
    const GRAFICOS_DISPONIVEIS = typeof window.Chart !== 'undefined';

    const estilo = getComputedStyle(raiz);
    const cor = (nome, padrao) => estilo.getPropertyValue(nome).trim() || padrao;
    const CORES = {
        serie1: cor('--serie-1', '#2a78d6'),
        serie1Suave: cor('--serie-1-suave', '#86b6ef'),
        serie2: cor('--serie-2', '#eb6834'),
        textoPrimario: cor('--texto-primario', '#0b0b0b'),
        textoSecundario: cor('--texto-secundario', '#52514e'),
        grade: cor('--grade', '#e1e0d9'),
        eixo: cor('--eixo', '#c3c2b7'),
        superficie: cor('--superficie', '#ffffff'),
        neutro: '#898781'
    };

    const STATUS = {
        RECEBIDO: {descricao: 'Recebido', cor: '#6c757d', badge: 'bg-secondary'},
        EM_PREPARACAO: {descricao: 'Em preparação', cor: '#e0a800', badge: 'bg-warning text-dark'},
        PRONTO: {descricao: 'Pronto', cor: '#0aa2c0', badge: 'bg-info text-dark'},
        SAIU_PARA_ENTREGA: {descricao: 'Saiu para entrega', cor: '#0d6efd', badge: 'bg-primary'},
        RETIRADO: {descricao: 'Retirado', cor: '#0d6efd', badge: 'bg-primary'},
        FINALIZADO: {descricao: 'Finalizado', cor: '#198754', badge: 'bg-success'},
        CANCELADO: {descricao: 'Cancelado', cor: '#dc3545', badge: 'bg-danger'}
    };
    const ATENDIMENTO = {
        ENTREGA: {descricao: 'Entrega', curta: 'Entrega', cor: CORES.serie1},
        RETIRADA: {descricao: 'Retirada no balcão', curta: 'Retirada', cor: CORES.serie2},
        NAO_INFORMADO: {descricao: 'Não informado', curta: 'Não inf.', cor: CORES.neutro}
    };
    const STATUS_EM_ABERTO = new Set(['RECEBIDO', 'EM_PREPARACAO', 'PRONTO', 'SAIU_PARA_ENTREGA', 'RETIRADO']);
    const NOMES_AGRUPAMENTO = {DIA: 'dia', SEMANA: 'semana', MES: 'mês'};
    const NOMES_PARCIAL = {DIA: 'dia em andamento', SEMANA: 'semana incompleta', MES: 'mês incompleto'};

    const fmtMoeda = new Intl.NumberFormat('pt-BR', {style: 'currency', currency: 'BRL'});
    const fmtMoedaCompacta = new Intl.NumberFormat('pt-BR', {
        style: 'currency', currency: 'BRL', notation: 'compact', maximumFractionDigits: 1
    });
    const fmtInteiro = new Intl.NumberFormat('pt-BR');
    const fmtDecimal = new Intl.NumberFormat('pt-BR', {minimumFractionDigits: 1, maximumFractionDigits: 1});
    const fmtHora = new Intl.DateTimeFormat('pt-BR', {hour: '2-digit', minute: '2-digit', second: '2-digit'});
    const fmtDataHora = new Intl.DateTimeFormat('pt-BR', {
        day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit'
    });
    const fmtData = new Intl.DateTimeFormat('pt-BR', {day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'UTC'});

    const numero = valor => Number(valor ?? 0);
    const moeda = valor => fmtMoeda.format(numero(valor));
    const moedaCompacta = valor => fmtMoedaCompacta.format(numero(valor));
    const inteiro = valor => fmtInteiro.format(numero(valor));
    const percentual = valor => fmtDecimal.format(numero(valor)) + '%';
    const dataCurta = texto => texto ? fmtData.format(new Date(texto + 'T00:00:00Z')) : '—';
    const minutos = valor => valor == null ? '—' : fmtInteiro.format(Math.round(numero(valor))) + ' min';

    const campos = {
        periodo: document.getElementById('fPeriodo'),
        inicio: document.getElementById('fInicio'),
        fim: document.getElementById('fFim'),
        statusBotao: document.getElementById('fStatusBotao'),
        statusLista: document.getElementById('fStatusLista'),
        atendimento: document.getElementById('fAtendimento'),
        categoria: document.getElementById('fCategoria'),
        auto: document.getElementById('autoAtualizar'),
        alerta: document.getElementById('alertaFiltro'),
        ultimaAtualizacao: document.getElementById('ultimaAtualizacao')
    };

    const estado = {
        controladores: {},
        graficos: {},
        dados: {},
        temporizador: null,
        geracao: 0,
        ultimaAtualizacao: 0
    };

    class ErroApi extends Error {
        constructor(status, mensagem) {
            super(mensagem);
            this.name = 'ErroApi';
            this.status = status;
        }
    }

    const PAINEIS = {
        resumo: {caminho: '/resumo', render: renderResumo},
        vendas: {caminho: '/vendas', extras: () => ({agrupamento: valorRadio('agrupamento')}), render: renderVendas},
        status: {caminho: '/pedidos/status', render: renderStatus},
        ranking: {
            caminho: '/produtos/mais-vendidos',
            extras: () => ({criterio: valorRadio('criterio'), limite: 10}),
            render: renderRanking
        },
        categoria: {caminho: '/vendas/por-categoria', render: renderCategorias},
        atendimento: {caminho: '/vendas/por-atendimento', render: renderAtendimento},
        horario: {caminho: '/vendas/por-horario', render: renderHorarios, paineis: ['horario', 'diaSemana']},
        recentes: {caminho: '/pedidos/recentes', extras: () => ({limite: 10}), render: renderRecentes}
    };
    const ORIGEM_DO_PAINEL = {};
    Object.entries(PAINEIS).forEach(([chave, painel]) => {
        (painel.paineis || [chave]).forEach(p => {
            ORIGEM_DO_PAINEL[p] = chave;
        });
    });

    const pluginCrosshair = {
        id: 'crosshair',
        afterDatasetsDraw(chart) {
            const ativos = chart.tooltip ? chart.tooltip.getActiveElements() : [];
            if (!ativos.length) {
                return;
            }
            const x = ativos[0].element.x;
            const {top, bottom} = chart.chartArea;
            const ctx = chart.ctx;
            ctx.save();
            ctx.beginPath();
            ctx.moveTo(x, top);
            ctx.lineTo(x, bottom);
            ctx.lineWidth = 1;
            ctx.strokeStyle = CORES.eixo;
            ctx.stroke();
            ctx.restore();
        }
    };

    const pluginRotuloNaPonta = {
        id: 'rotuloNaPonta',
        afterDatasetsDraw(chart, argumentos, opcoes) {
            if (!opcoes || typeof opcoes.formatar !== 'function') {
                return;
            }
            const ctx = chart.ctx;
            ctx.save();
            ctx.font = `600 12px ${Chart.defaults.font.family}`;
            ctx.fillStyle = CORES.textoSecundario;
            ctx.textBaseline = 'middle';
            chart.getDatasetMeta(0).data.forEach((barra, indice) => {
                ctx.fillText(opcoes.formatar(indice), barra.x + 6, barra.y);
            });
            ctx.restore();
        }
    };

    function configurarChart() {
        if (!GRAFICOS_DISPONIVEIS) {
            return;
        }
        const reduzirMovimento = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        Chart.defaults.font.family = getComputedStyle(document.body).fontFamily;
        Chart.defaults.font.size = 12;
        Chart.defaults.color = CORES.textoSecundario;
        Chart.defaults.borderColor = CORES.grade;
        Chart.defaults.maintainAspectRatio = false;
        Chart.defaults.animation = reduzirMovimento ? false : {duration: 350};
    }

    function tooltipBase(extra) {
        return Object.assign({
            backgroundColor: '#ffffff',
            titleColor: CORES.textoSecundario,
            bodyColor: CORES.textoPrimario,
            footerColor: CORES.textoSecundario,
            borderColor: 'rgba(11, 11, 11, 0.15)',
            borderWidth: 1,
            padding: 10,
            cornerRadius: 6,
            displayColors: false,
            titleFont: {weight: 'normal'},
            bodyFont: {weight: '600', size: 13},
            footerFont: {weight: 'normal'}
        }, extra || {});
    }

    function eixoValores(formatar, extra) {
        return Object.assign({
            beginAtZero: true,
            border: {display: false},
            grid: {color: CORES.grade},
            ticks: {callback: valor => formatar(valor), maxTicksLimit: 6}
        }, extra || {});
    }

    function eixoCategorias(extra) {
        return Object.assign({
            grid: {display: false},
            border: {color: CORES.eixo},
            ticks: {maxRotation: 0, autoSkip: true}
        }, extra || {});
    }

    function desenhar(chave, idCanvas, config, descricao) {
        const canvas = document.getElementById(idCanvas);
        canvas.setAttribute('aria-label', descricao);
        if (!GRAFICOS_DISPONIVEIS) {
            return;
        }
        const existente = estado.graficos[chave];
        if (existente && existente.config.type === config.type) {
            existente.data.labels = config.data.labels;
            existente.data.datasets = config.data.datasets;
            existente.options = config.options;
            existente.update();
            return;
        }
        if (existente) {
            existente.destroy();
        }
        estado.graficos[chave] = new Chart(canvas, config);
    }

    function painel(chave) {
        return raiz.querySelector(`[data-painel="${chave}"]`);
    }

    function texto(id, valor) {
        const elemento = document.getElementById(id);
        if (elemento) {
            elemento.textContent = valor;
        }
    }

    function criar(tag, classe, conteudo) {
        const elemento = document.createElement(tag);
        if (classe) {
            elemento.className = classe;
        }
        if (conteudo !== undefined && conteudo !== null) {
            elemento.textContent = conteudo;
        }
        return elemento;
    }

    function celula(conteudo, classe) {
        return criar('td', classe, conteudo);
    }

    function alternarVazio(chave, vazio) {
        const elemento = painel(chave);
        if (!elemento) {
            return;
        }
        const aviso = elemento.querySelector('.painel-vazio');
        if (aviso) {
            aviso.classList.toggle('d-none', !vazio);
        }
        elemento.classList.toggle('sem-dados', vazio);
    }

    function preencherTabela(chave, cabecalhos, linhas) {
        const destino = painel(chave).querySelector('.tabela-dados');
        const tabela = criar('table', 'table table-sm mb-0');
        const cabeca = criar('thead');
        const linhaCabeca = criar('tr');
        cabecalhos.forEach((titulo, indice) => {
            const th = criar('th', indice > 0 ? 'text-end' : '', titulo);
            th.scope = 'col';
            linhaCabeca.append(th);
        });
        cabeca.append(linhaCabeca);
        const corpo = criar('tbody');
        linhas.forEach(valores => {
            const tr = criar('tr');
            valores.forEach((valor, indice) => tr.append(celula(valor, indice > 0 ? 'text-end' : '')));
            corpo.append(tr);
        });
        tabela.append(cabeca, corpo);
        destino.replaceChildren(tabela);
    }

    function mostrarErro(chave, erro) {
        const destino = painel(chave)?.querySelector('.painel-erro');
        if (!destino) {
            return;
        }
        const caixa = criar('div', 'alert alert-danger py-2 small d-flex flex-wrap align-items-center gap-2 mb-2');
        const icone = criar('i', 'bi bi-exclamation-octagon');
        icone.setAttribute('aria-hidden', 'true');
        const mensagem = criar('span', '', erro.message || 'Não foi possível carregar este painel.');
        const botao = criar('button', 'btn btn-sm btn-outline-danger ms-auto', 'Tentar novamente');
        botao.type = 'button';
        botao.addEventListener('click', () => atualizar([ORIGEM_DO_PAINEL[chave]]));
        caixa.append(icone, mensagem, botao);
        destino.replaceChildren(caixa);
        destino.classList.remove('d-none');
    }

    function esconderErro(chave) {
        const destino = painel(chave)?.querySelector('.painel-erro');
        if (destino) {
            destino.classList.add('d-none');
            destino.replaceChildren();
        }
    }

    function mostrarAlerta(mensagem) {
        campos.alerta.replaceChildren();
        const icone = criar('i', 'bi bi-exclamation-triangle me-2');
        icone.setAttribute('aria-hidden', 'true');
        campos.alerta.append(icone, document.createTextNode(mensagem));
        campos.alerta.classList.remove('d-none');
    }

    function esconderAlerta() {
        campos.alerta.classList.add('d-none');
        campos.alerta.replaceChildren();
    }

    function valorRadio(nome) {
        const marcado = raiz.querySelector(`input[name="${nome}"]:checked`);
        return marcado ? marcado.value : '';
    }

    function hoje() {
        const agora = new Date();
        return new Date(agora.getFullYear(), agora.getMonth(), agora.getDate());
    }

    function somarDias(data, dias) {
        const resultado = new Date(data);
        resultado.setDate(resultado.getDate() + dias);
        return resultado;
    }

    function iso(data) {
        return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}-${String(data.getDate()).padStart(2, '0')}`;
    }

    function aplicarPreset(valor) {
        const fim = hoje();
        let inicio;
        switch (valor) {
            case 'hoje':
                inicio = fim;
                break;
            case '7':
                inicio = somarDias(fim, -6);
                break;
            case '90':
                inicio = somarDias(fim, -89);
                break;
            case 'mes':
                inicio = new Date(fim.getFullYear(), fim.getMonth(), 1);
                break;
            case 'personalizado':
                return;
            default:
                inicio = somarDias(fim, -29);
        }
        campos.inicio.value = iso(inicio);
        campos.fim.value = iso(fim);
    }

    function statusSelecionados() {
        return Array.from(campos.statusLista.querySelectorAll('input:checked')).map(entrada => entrada.value);
    }

    function atualizarRotuloStatus() {
        const selecionados = statusSelecionados();
        if (selecionados.length === 0) {
            campos.statusBotao.textContent = 'Todos';
        } else if (selecionados.length === 1) {
            campos.statusBotao.textContent = STATUS[selecionados[0]]?.descricao || selecionados[0];
        } else {
            campos.statusBotao.textContent = `${selecionados.length} selecionados`;
        }
    }

    function parametrosFiltro() {
        const parametros = new URLSearchParams();
        parametros.set('inicio', campos.inicio.value);
        parametros.set('fim', campos.fim.value);
        const status = statusSelecionados();
        if (status.length) {
            parametros.set('status', status.join(','));
        }
        if (campos.atendimento.value) {
            parametros.set('tipoAtendimento', campos.atendimento.value);
        }
        if (campos.categoria.value) {
            parametros.set('categoria', campos.categoria.value);
        }
        return parametros;
    }

    function sincronizarUrl(parametros) {
        const estadoUrl = new URLSearchParams(parametros);
        estadoUrl.set('periodo', campos.periodo.value);
        if (campos.periodo.value !== 'personalizado') {
            estadoUrl.delete('inicio');
            estadoUrl.delete('fim');
        }
        history.replaceState(null, '', `${window.location.pathname}?${estadoUrl}`);
    }

    function validarPeriodoLocal() {
        if (!campos.inicio.value || !campos.fim.value) {
            return 'Informe a data inicial e a data final do período.';
        }
        if (campos.inicio.value > campos.fim.value) {
            return 'A data inicial não pode ser posterior à data final.';
        }
        return null;
    }

    async function buscar(caminho, consulta, sinal) {
        const url = `${API}${caminho}${consulta.toString() ? '?' + consulta : ''}`;
        let resposta;
        try {
            resposta = await fetch(url, {headers: {Accept: 'application/json'}, credentials: 'same-origin', signal: sinal});
        } catch (erro) {
            if (erro.name === 'AbortError') {
                throw erro;
            }
            throw new ErroApi(0, 'Não foi possível conectar ao servidor. Verifique a conexão e tente novamente.');
        }
        const ehJson = (resposta.headers.get('content-type') || '').includes('application/json');
        if (resposta.status === 401 || (resposta.redirected && !ehJson)) {
            window.location.assign(URL_LOGIN);
            throw new ErroApi(401, 'Sua sessão expirou. Entre novamente.');
        }
        let corpo = null;
        if (ehJson) {
            try {
                corpo = await resposta.json();
            } catch (erro) {
                corpo = null;
            }
        }
        if (!resposta.ok) {
            throw new ErroApi(resposta.status, corpo && corpo.mensagem
                    ? corpo.mensagem
                    : `A API respondeu com erro ${resposta.status}.`);
        }
        if (!corpo) {
            throw new ErroApi(resposta.status, 'A API retornou uma resposta em formato inesperado.');
        }
        return corpo;
    }

    async function carregarPainel(chave, parametros) {
        const definicao = PAINEIS[chave];
        if (estado.controladores[chave]) {
            estado.controladores[chave].abort();
        }
        const controlador = new AbortController();
        estado.controladores[chave] = controlador;
        const consulta = new URLSearchParams(parametros);
        Object.entries(definicao.extras ? definicao.extras() : {}).forEach(([nome, valor]) => consulta.set(nome, valor));
        const destinos = definicao.paineis || [chave];
        try {
            const dados = await buscar(definicao.caminho, consulta, controlador.signal);
            if (controlador.signal.aborted) {
                return 'abortado';
            }
            destinos.forEach(esconderErro);
            estado.dados[chave] = dados;
            definicao.render(dados);
            return 'ok';
        } catch (erro) {
            if (erro.name === 'AbortError') {
                return 'abortado';
            }
            if (erro.status === 400) {
                mostrarAlerta(erro.message);
                return 'filtro';
            }
            if (erro.status !== 401) {
                destinos.forEach(destino => mostrarErro(destino, erro));
            }
            return 'erro';
        } finally {
            if (estado.controladores[chave] === controlador) {
                delete estado.controladores[chave];
            }
        }
    }

    async function atualizar(chaves) {
        const alvo = chaves && chaves.length ? chaves : Object.keys(PAINEIS);
        if (campos.periodo.value !== 'personalizado') {
            aplicarPreset(campos.periodo.value);
        }
        const problema = validarPeriodoLocal();
        if (problema) {
            mostrarAlerta(problema);
            return;
        }
        esconderAlerta();
        const parametros = parametrosFiltro();
        sincronizarUrl(parametros);

        const geracao = ++estado.geracao;
        raiz.classList.add('atualizando');
        raiz.setAttribute('aria-busy', 'true');
        const resultados = await Promise.all(alvo.map(chave => carregarPainel(chave, parametros)));
        if (geracao !== estado.geracao) {
            return;
        }
        raiz.classList.remove('atualizando');
        raiz.removeAttribute('aria-busy');

        const falhas = resultados.filter(resultado => resultado === 'erro').length;
        if (resultados.some(resultado => resultado === 'ok')) {
            estado.ultimaAtualizacao = Date.now();
            const horario = fmtHora.format(new Date());
            campos.ultimaAtualizacao.textContent = falhas
                    ? `Atualizado às ${horario} (${falhas} painel(is) com falha)`
                    : `Atualizado às ${horario}`;
        } else if (falhas) {
            campos.ultimaAtualizacao.textContent = 'Não foi possível atualizar os dados';
        }
    }

    function delta(id, comparacao, subirEhBom, formatarAnterior) {
        const destino = document.getElementById(id);
        destino.replaceChildren();
        if (!comparacao || comparacao.variacaoPercentual == null) {
            destino.textContent = 'Sem base de comparação no período anterior';
            return;
        }
        const variacao = numero(comparacao.variacaoPercentual);
        const sentido = Math.sign(variacao);
        const classe = sentido === 0 ? 'delta-neutro'
                : (sentido > 0) === subirEhBom ? 'delta-positivo' : 'delta-negativo';
        const icone = criar('i', `bi ${sentido > 0 ? 'bi-arrow-up-right' : sentido < 0 ? 'bi-arrow-down-right' : 'bi-dash'}`);
        icone.setAttribute('aria-hidden', 'true');
        const destaque = criar('span', classe);
        destaque.append(icone, ` ${variacao > 0 ? '+' : ''}${fmtDecimal.format(variacao)}%`);
        destino.append(destaque, ` vs. período anterior (${formatarAnterior(comparacao.anterior)})`);
    }

    function renderResumo(resumo) {
        texto('kpiFaturamento', moeda(resumo.faturamento));
        texto('kpiPedidos', inteiro(resumo.totalPedidos));
        texto('kpiTicket', moeda(resumo.ticketMedio));
        texto('kpiCancelados', inteiro(resumo.pedidosCancelados));
        texto('kpiDescontos', moeda(resumo.descontosConcedidos));
        texto('kpiItens', inteiro(resumo.itensVendidos));
        delta('deltaFaturamento', resumo.comparacao.faturamento, true, moeda);
        delta('deltaPedidos', resumo.comparacao.totalPedidos, true, inteiro);
        delta('deltaTicket', resumo.comparacao.ticketMedio, true, moeda);
        delta('deltaCancelados', resumo.comparacao.pedidosCancelados, false, inteiro);
        texto('detalheCancelados', resumo.totalPedidos
                ? `${percentual(resumo.taxaCancelamento)} dos pedidos · ${moeda(resumo.valorCancelado)} não faturados`
                : 'Nenhum pedido no período');
        const bruto = numero(resumo.faturamento) + numero(resumo.descontosConcedidos);
        texto('detalheDescontos', numero(resumo.descontosConcedidos) > 0 && bruto > 0
                ? `${percentual(numero(resumo.descontosConcedidos) / bruto * 100)} do valor bruto dos pedidos`
                : 'Nenhum desconto concedido no período');
        texto('detalheItens', resumo.pedidosValidos
                ? `${fmtDecimal.format(resumo.itensVendidos / resumo.pedidosValidos)} itens por pedido válido`
                : 'Nenhum pedido válido no período');
        texto('descricaoPeriodo', `${dataCurta(resumo.periodo.inicio)} a ${dataCurta(resumo.periodo.fim)} `
                + `(${resumo.periodo.dias} dia${resumo.periodo.dias > 1 ? 's' : ''}) · comparado a `
                + `${dataCurta(resumo.periodoAnterior.inicio)} a ${dataCurta(resumo.periodoAnterior.fim)}`);
    }

    function descricaoParcial(ponto, serie) {
        if (serie.agrupamento === 'DIA') {
            return 'Dia em andamento';
        }
        const hoje = new Date().toLocaleDateString('sv-SE');
        const de = ponto.inicio < serie.periodo.inicio ? serie.periodo.inicio : ponto.inicio;
        const ate = ponto.fim > serie.periodo.fim ? serie.periodo.fim : ponto.fim;
        const emAndamento = de <= hoje && hoje <= ate;
        return `Parcial: considera ${dataCurta(de)} a ${dataCurta(emAndamento ? hoje : ate)}`
                + (emAndamento ? ' (em andamento)' : '');
    }

    function renderVendas(serie) {
        const pontos = serie.pontos;
        const unidade = NOMES_AGRUPAMENTO[serie.agrupamento] || 'período';
        const temVendas = pontos.some(ponto => ponto.pedidos > ponto.cancelados);
        alternarVazio('vendas', !temVendas);
        const melhor = pontos.reduce((maior, ponto) => numero(ponto.faturamento) > numero(maior.faturamento) ? ponto : maior,
                pontos[0] || {faturamento: 0});
        const total = pontos.reduce((soma, ponto) => soma + numero(ponto.faturamento), 0);
        const temParcial = pontos.some(ponto => ponto.parcial);
        const tracejado = (contexto, valor) =>
                pontos[contexto.p0DataIndex]?.parcial || pontos[contexto.p1DataIndex]?.parcial ? valor : undefined;
        texto('subtituloVendas', temVendas
                ? `Por ${unidade} · melhor ${unidade}: ${melhor.rotulo} (${moeda(melhor.faturamento)})`
                + (temParcial ? ` · tracejado: ${NOMES_PARCIAL[serie.agrupamento] || 'período incompleto'}` : '')
                : '');

        preencherTabela('vendas', [serie.agrupamento === 'SEMANA' ? 'Semana iniciada em' : 'Período', 'Pedidos',
            'Cancelados', 'Faturamento', 'Ticket médio'],
                pontos.map(p => [p.rotulo + (p.parcial ? ' (parcial)' : ''), inteiro(p.pedidos),
                    inteiro(p.cancelados), moeda(p.faturamento), moeda(p.ticketMedio)]));

        desenhar('vendas', 'graficoVendas', {
            type: 'line',
            data: {
                labels: pontos.map(p => p.rotulo),
                datasets: [{
                    label: 'Faturamento',
                    data: pontos.map(p => numero(p.faturamento)),
                    borderColor: CORES.serie1,
                    backgroundColor: 'rgba(42, 120, 214, 0.10)',
                    fill: 'origin',
                    borderWidth: 2,
                    borderJoinStyle: 'round',
                    borderCapStyle: 'round',
                    pointRadius: pontos.length === 1 ? 4 : 0,
                    pointHoverRadius: 5,
                    pointBackgroundColor: CORES.serie1,
                    pointBorderColor: CORES.superficie,
                    pointHoverBorderWidth: 2,
                    tension: 0,
                    segment: {
                        borderDash: contexto => tracejado(contexto, [6, 4])
                    }
                }]
            },
            options: {
                interaction: {mode: 'index', intersect: false},
                plugins: {
                    legend: {display: false},
                    tooltip: tooltipBase({
                        callbacks: {
                            title: itens => serie.agrupamento === 'SEMANA'
                                    ? `Semana iniciada em ${itens[0].label}` : itens[0].label,
                            label: contexto => moeda(contexto.parsed.y),
                            footer: itens => {
                                const ponto = pontos[itens[0].dataIndex];
                                const linhas = [`${inteiro(ponto.pedidos)} pedidos · ticket médio ${moeda(ponto.ticketMedio)}`];
                                if (ponto.cancelados) {
                                    linhas.push(`${inteiro(ponto.cancelados)} cancelado(s)`);
                                }
                                if (ponto.parcial) {
                                    linhas.push(descricaoParcial(ponto, serie));
                                }
                                return linhas;
                            }
                        }
                    })
                },
                scales: {
                    x: eixoCategorias({ticks: {maxRotation: 0, autoSkip: true, maxTicksLimit: 12}}),
                    y: eixoValores(moedaCompacta)
                }
            },
            plugins: [pluginCrosshair]
        }, `Gráfico de linha do faturamento por ${unidade}. Total de ${moeda(total)}; `
                + `maior valor em ${melhor.rotulo || '—'} com ${moeda(melhor.faturamento)}.`);
    }

    function renderStatus(situacao) {
        texto('statusAndamento', inteiro(situacao.emAndamento));
        texto('statusFinalizados', inteiro(situacao.finalizados));
        texto('statusCancelados', inteiro(situacao.cancelados));
        texto('subtituloStatus', `${inteiro(situacao.total)} pedido(s) registrados no período`);
        const lista = document.getElementById('listaStatus');
        lista.replaceChildren(...situacao.porStatus.map(item => {
            const info = STATUS[item.status] || {descricao: item.descricao, cor: CORES.neutro};
            const li = criar('li');
            const nome = criar('span', 'status-nome');
            const marca = criar('span', 'status-marca');
            marca.style.background = info.cor;
            nome.append(marca, document.createTextNode(item.descricao || info.descricao));
            const trilho = criar('div', 'trilho');
            const preenchimento = criar('span');
            preenchimento.style.width = `${Math.min(100, numero(item.percentual))}%`;
            preenchimento.style.background = info.cor;
            trilho.append(preenchimento);
            trilho.setAttribute('aria-hidden', 'true');
            const valor = criar('span', 'status-valor', `${inteiro(item.quantidade)} · ${percentual(item.percentual)}`);
            li.append(nome, trilho, valor);
            return li;
        }));
        texto('tempoPronto', minutos(situacao.temposMedios.ateProntoMinutos));
        texto('tempoSaida', minutos(situacao.temposMedios.ateSaidaMinutos));
        texto('tempoFinalizacao', minutos(situacao.temposMedios.ateFinalizacaoMinutos));
    }

    function renderRanking(ranking) {
        const porQuantidade = ranking.criterio === 'QUANTIDADE';
        const medida = produto => porQuantidade ? numero(produto.quantidade) : numero(produto.faturamento);
        const maximo = Math.max(1, ...ranking.produtos.map(medida));
        alternarVazio('ranking', ranking.produtos.length === 0);
        texto('subtituloRanking', ranking.produtos.length
                ? `Top ${ranking.produtos.length} de ${inteiro(ranking.produtosVendidos)} produtos vendidos · `
                + `por ${porQuantidade ? 'quantidade' : 'faturamento'} · pedidos cancelados não entram`
                : '');
        const corpo = document.getElementById('corpoRanking');
        corpo.replaceChildren(...ranking.produtos.map(produto => {
            const tr = criar('tr');
            const nome = criar('td');
            nome.append(criar('div', 'fw-semibold', produto.nome || '—'), criar('div', 'produto-categoria', produto.categoria));
            const barra = criar('td', 'coluna-barra');
            const trilho = criar('div', 'trilho');
            const preenchimento = criar('span');
            preenchimento.style.width = `${(medida(produto) / maximo) * 100}%`;
            preenchimento.style.background = CORES.serie1;
            trilho.append(preenchimento);
            trilho.setAttribute('aria-hidden', 'true');
            barra.append(trilho);
            tr.append(celula(`${produto.posicao}º`, 'ps-3 text-secondary'), nome, barra,
                    celula(inteiro(produto.quantidade), 'text-end' + (porQuantidade ? ' fw-semibold' : '')),
                    celula(moeda(produto.faturamento), 'text-end' + (porQuantidade ? '' : ' fw-semibold')),
                    celula(percentual(produto.participacao), 'text-end pe-3'));
            return tr;
        }));
    }

    function renderCategorias(resposta) {
        const categorias = resposta.categorias;
        alternarVazio('categoria', categorias.length === 0);
        preencherTabela('categoria', ['Categoria', 'Itens', 'Pedidos', 'Faturamento', 'Participação'],
                categorias.map(c => [c.categoria, inteiro(c.quantidade), inteiro(c.pedidos), moeda(c.faturamento),
                    percentual(c.participacao)]));
        document.getElementById('caixaCategoria').style.height = `${Math.max(140, categorias.length * 40 + 24)}px`;
        desenhar('categoria', 'graficoCategoria', {
            type: 'bar',
            data: {
                labels: categorias.map(c => c.categoria),
                datasets: [{
                    label: 'Faturamento',
                    data: categorias.map(c => numero(c.faturamento)),
                    backgroundColor: CORES.serie1,
                    hoverBackgroundColor: '#256abf',
                    borderRadius: 4,
                    borderSkipped: 'start',
                    maxBarThickness: 24
                }]
            },
            options: {
                indexAxis: 'y',
                layout: {padding: {right: 130}},
                plugins: {
                    legend: {display: false},
                    rotuloNaPonta: {
                        formatar: indice => `${moedaCompacta(categorias[indice].faturamento)} · `
                                + `${fmtDecimal.format(numero(categorias[indice].participacao))}%`
                    },
                    tooltip: tooltipBase({
                        callbacks: {
                            label: contexto => moeda(contexto.parsed.x),
                            footer: itens => {
                                const c = categorias[itens[0].dataIndex];
                                return [`${percentual(c.participacao)} do faturamento bruto`,
                                    `${inteiro(c.quantidade)} itens em ${inteiro(c.pedidos)} pedidos`];
                            }
                        }
                    })
                },
                scales: {
                    x: {display: false, beginAtZero: true},
                    y: eixoCategorias({border: {display: false}})
                }
            },
            plugins: [pluginRotuloNaPonta]
        }, 'Gráfico de barras do faturamento por categoria: ' + categorias
                .map(c => `${c.categoria} ${moeda(c.faturamento)} (${percentual(c.participacao)})`).join('; ') + '.');
    }

    function renderAtendimento(resposta) {
        const tipos = resposta.tipos.filter(t => t.tipo !== 'NAO_INFORMADO' || t.pedidos > 0);
        const total = tipos.reduce((soma, t) => soma + t.pedidos, 0);
        alternarVazio('atendimento', total === 0);
        const barra = document.getElementById('barraAtendimento');
        barra.replaceChildren(...tipos.filter(t => t.pedidos > 0).map(t => {
            const info = ATENDIMENTO[t.tipo] || ATENDIMENTO.NAO_INFORMADO;
            const segmento = criar('div');
            segmento.style.flex = `${t.pedidos} 1 0`;
            segmento.style.background = info.cor;
            segmento.title = `${t.descricao}: ${inteiro(t.pedidos)} pedidos (${percentual(t.participacaoPedidos)})`;
            if (numero(t.participacaoPedidos) >= 15) {
                segmento.textContent = `${info.curta} ${fmtDecimal.format(numero(t.participacaoPedidos))}%`;
            }
            return segmento;
        }));
        barra.setAttribute('aria-label', 'Participação por tipo de atendimento: ' + tipos
                .map(t => `${t.descricao} ${percentual(t.participacaoPedidos)} dos pedidos`).join('; ') + '.');
        const corpo = document.getElementById('corpoAtendimento');
        corpo.replaceChildren(...tipos.map(t => {
            const info = ATENDIMENTO[t.tipo] || ATENDIMENTO.NAO_INFORMADO;
            const tr = criar('tr');
            const nome = criar('td');
            const marca = criar('span', 'marca-serie');
            marca.style.background = info.cor;
            marca.setAttribute('aria-hidden', 'true');
            nome.append(marca, document.createTextNode(t.descricao));
            tr.append(nome, celula(`${inteiro(t.pedidos)} (${percentual(t.participacaoPedidos)})`, 'text-end'),
                    celula(moeda(t.faturamento), 'text-end'), celula(moeda(t.ticketMedio), 'text-end'));
            return tr;
        }));
    }

    function coresComDestaque(valores) {
        const maximo = Math.max(...valores);
        return valores.map(valor => valor === maximo && maximo > 0 ? CORES.serie1 : CORES.serie1Suave);
    }

    function renderHorarios(resposta) {
        const comPedidos = resposta.porHora.filter(h => h.pedidos > 0);
        const vazio = comPedidos.length === 0;
        alternarVazio('horario', vazio);
        alternarVazio('diaSemana', vazio);
        const primeira = vazio ? 10 : Math.min(...comPedidos.map(h => h.hora));
        const ultima = vazio ? 23 : Math.max(...comPedidos.map(h => h.hora));
        const horas = resposta.porHora.filter(h => h.hora >= primeira && h.hora <= ultima);
        const pico = resposta.porHora.find(h => h.hora === resposta.horaPico);
        texto('subtituloHorario', pico
                ? `Pedidos válidos · pico às ${pico.rotulo} com ${inteiro(pico.pedidos)} pedidos`
                : '');
        preencherTabela('horario', ['Horário', 'Pedidos', 'Faturamento'],
                horas.map(h => [h.rotulo, inteiro(h.pedidos), moeda(h.faturamento)]));
        const valoresHora = horas.map(h => h.pedidos);
        desenhar('horario', 'graficoHorario', {
            type: 'bar',
            data: {
                labels: horas.map(h => h.rotulo),
                datasets: [{
                    label: 'Pedidos',
                    data: valoresHora,
                    backgroundColor: coresComDestaque(valoresHora),
                    hoverBackgroundColor: '#256abf',
                    borderRadius: 4,
                    borderSkipped: 'start',
                    maxBarThickness: 24
                }]
            },
            options: {
                plugins: {
                    legend: {display: false},
                    tooltip: tooltipBase({
                        callbacks: {
                            title: itens => `Das ${itens[0].label} às ${String(horas[itens[0].dataIndex].hora + 1).padStart(2, '0')}h`,
                            label: contexto => `${inteiro(contexto.parsed.y)} pedidos`,
                            footer: itens => moeda(horas[itens[0].dataIndex].faturamento)
                        }
                    })
                },
                scales: {
                    x: eixoCategorias(),
                    y: eixoValores(inteiro, {ticks: {precision: 0, maxTicksLimit: 6, callback: valor => inteiro(valor)}})
                }
            }
        }, `Gráfico de colunas de pedidos por horário, das ${String(primeira).padStart(2, '0')}h às `
                + `${String(ultima).padStart(2, '0')}h. ${pico ? 'Pico às ' + pico.rotulo + '.' : ''}`);

        const dias = resposta.porDiaSemana;
        const valoresDia = dias.map(d => numero(d.mediaPedidos));
        const diaPico = dias.find(d => d.nome === resposta.diaPico);
        texto('subtituloDiaSemana', diaPico
                ? `Pedidos válidos · maior movimento: ${diaPico.nome} (média de ${fmtDecimal.format(numero(diaPico.mediaPedidos))} por dia)`
                : '');
        preencherTabela('diaSemana', ['Dia', 'Ocorrências no período', 'Pedidos', 'Média por dia', 'Faturamento'],
                dias.map(d => [d.nome, inteiro(d.ocorrencias), inteiro(d.pedidos), fmtDecimal.format(numero(d.mediaPedidos)),
                    moeda(d.faturamento)]));
        desenhar('diaSemana', 'graficoDiaSemana', {
            type: 'bar',
            data: {
                labels: dias.map(d => d.nome.slice(0, 3)),
                datasets: [{
                    label: 'Média de pedidos',
                    data: valoresDia,
                    backgroundColor: coresComDestaque(valoresDia),
                    hoverBackgroundColor: '#256abf',
                    borderRadius: 4,
                    borderSkipped: 'start',
                    maxBarThickness: 24
                }]
            },
            options: {
                plugins: {
                    legend: {display: false},
                    tooltip: tooltipBase({
                        callbacks: {
                            title: itens => dias[itens[0].dataIndex].nome,
                            label: contexto => `Média de ${fmtDecimal.format(contexto.parsed.y)} pedidos`,
                            footer: itens => {
                                const d = dias[itens[0].dataIndex];
                                return [`${inteiro(d.pedidos)} pedidos em ${inteiro(d.ocorrencias)} dia(s)`, moeda(d.faturamento)];
                            }
                        }
                    })
                },
                scales: {
                    x: eixoCategorias(),
                    y: eixoValores(valor => fmtDecimal.format(valor))
                }
            }
        }, 'Gráfico de colunas da média de pedidos por dia da semana: ' + dias
                .map(d => `${d.nome} ${fmtDecimal.format(numero(d.mediaPedidos))}`).join('; ') + '.');
    }

    function tempoEmAberto(total) {
        if (total < 60) {
            return `${total} min`;
        }
        if (total < 1440) {
            return `${Math.floor(total / 60)} h ${String(total % 60).padStart(2, '0')} min`;
        }
        const dias = Math.floor(total / 1440);
        return `${dias} dia${dias > 1 ? 's' : ''}`;
    }

    function renderRecentes(resposta) {
        alternarVazio('recentes', resposta.pedidos.length === 0);
        const corpo = document.getElementById('corpoRecentes');
        corpo.replaceChildren(...resposta.pedidos.map(pedido => {
            const tr = criar('tr');
            const numeroCelula = criar('td', 'ps-3 fw-semibold');
            const link = criar('a', '', pedido.numero != null ? `#${pedido.numero}` : 'Detalhes');
            link.href = `${URL_PEDIDOS}/${encodeURIComponent(pedido.id)}`;
            numeroCelula.append(link);
            const statusCelula = criar('td');
            const info = STATUS[pedido.status] || {badge: 'bg-secondary'};
            statusCelula.append(criar('span', `badge ${info.badge}`, pedido.statusDescricao || pedido.status || '—'));
            const aberto = criar('td', 'pe-3');
            if (pedido.minutosEmAberto == null || !STATUS_EM_ABERTO.has(pedido.status)) {
                aberto.textContent = '—';
                aberto.classList.add('text-secondary');
            } else if (pedido.minutosEmAberto > 60) {
                const alerta = criar('span', 'em-aberto-alerta');
                const icone = criar('i', 'bi bi-exclamation-triangle-fill me-1');
                icone.setAttribute('aria-hidden', 'true');
                alerta.append(icone, document.createTextNode(`${tempoEmAberto(pedido.minutosEmAberto)} · atrasado`));
                aberto.append(alerta);
            } else {
                aberto.textContent = tempoEmAberto(pedido.minutosEmAberto);
            }
            const atendimento = ATENDIMENTO[pedido.tipoAtendimento];
            tr.append(numeroCelula, celula(pedido.cliente || '—'), celula(atendimento ? atendimento.descricao : '—'),
                    statusCelula, celula(pedido.valorTotal != null ? moeda(pedido.valorTotal) : '—', 'text-end'),
                    celula(pedido.dataHoraEntrada ? fmtDataHora.format(new Date(pedido.dataHoraEntrada)) : '—'), aberto);
            return tr;
        }));
    }

    function montarFiltros(opcoes) {
        const status = opcoes && opcoes.status ? opcoes.status
                : Object.entries(STATUS).map(([valor, info]) => ({valor, descricao: info.descricao}));
        campos.statusLista.replaceChildren(...status.map(opcao => {
            const linha = criar('div', 'form-check');
            const entrada = criar('input', 'form-check-input');
            entrada.type = 'checkbox';
            entrada.value = opcao.valor;
            entrada.id = `status-${opcao.valor}`;
            const rotulo = criar('label', 'form-check-label small text-nowrap', opcao.descricao);
            rotulo.htmlFor = entrada.id;
            linha.append(entrada, rotulo);
            return linha;
        }));
        const tipos = opcoes && opcoes.tiposAtendimento ? opcoes.tiposAtendimento
                : Object.entries(ATENDIMENTO).filter(([valor]) => valor !== 'NAO_INFORMADO')
                        .map(([valor, info]) => ({valor, descricao: info.descricao}));
        tipos.forEach(opcao => {
            const item = criar('option', '', opcao.descricao);
            item.value = opcao.valor;
            campos.atendimento.append(item);
        });
        (opcoes && opcoes.categorias ? opcoes.categorias : []).forEach(categoria => {
            const item = criar('option', '', categoria);
            item.value = categoria;
            campos.categoria.append(item);
        });
    }

    function restaurarDaUrl() {
        const url = new URLSearchParams(window.location.search);
        const periodo = url.get('periodo');
        if (periodo && Array.from(campos.periodo.options).some(opcao => opcao.value === periodo)) {
            campos.periodo.value = periodo;
        }
        if (url.get('inicio') && url.get('fim')) {
            campos.periodo.value = periodo || 'personalizado';
            campos.inicio.value = url.get('inicio');
            campos.fim.value = url.get('fim');
        }
        if (campos.periodo.value !== 'personalizado' || !campos.inicio.value) {
            aplicarPreset(campos.periodo.value === 'personalizado' ? '30' : campos.periodo.value);
        }
        (url.get('status') || '').split(',').filter(Boolean).forEach(valor => {
            const entrada = document.getElementById(`status-${valor}`);
            if (entrada) {
                entrada.checked = true;
            }
        });
        atualizarRotuloStatus();
        const tipo = url.get('tipoAtendimento');
        if (tipo && Array.from(campos.atendimento.options).some(opcao => opcao.value === tipo)) {
            campos.atendimento.value = tipo;
        }
        const categoria = url.get('categoria');
        if (categoria && Array.from(campos.categoria.options).some(opcao => opcao.value === categoria)) {
            campos.categoria.value = categoria;
        }
    }

    function adiar(funcao, espera) {
        let temporizador = null;
        return (...argumentos) => {
            clearTimeout(temporizador);
            temporizador = setTimeout(() => funcao(...argumentos), espera);
        };
    }

    function lerPreferenciaAuto() {
        try {
            return window.localStorage.getItem(CHAVE_AUTO) !== 'false';
        } catch (erro) {
            return true;
        }
    }

    function salvarPreferenciaAuto(valor) {
        try {
            window.localStorage.setItem(CHAVE_AUTO, String(valor));
        } catch (erro) {
            return;
        }
    }

    function programarAtualizacao() {
        clearInterval(estado.temporizador);
        estado.temporizador = null;
        if (campos.auto.checked && document.visibilityState === 'visible') {
            estado.temporizador = setInterval(() => atualizar(), INTERVALO_ATUALIZACAO_MS);
        }
    }

    function registrarEventos() {
        const atualizarAdiado = adiar(() => atualizar(), 350);
        campos.periodo.addEventListener('change', () => {
            aplicarPreset(campos.periodo.value);
            atualizar();
        });
        [campos.inicio, campos.fim].forEach(campo => campo.addEventListener('change', () => {
            campos.periodo.value = 'personalizado';
            atualizarAdiado();
        }));
        campos.statusLista.addEventListener('change', () => {
            atualizarRotuloStatus();
            atualizarAdiado();
        });
        campos.atendimento.addEventListener('change', () => atualizar());
        campos.categoria.addEventListener('change', () => atualizar());
        document.getElementById('btnLimpar').addEventListener('click', () => {
            campos.periodo.value = '30';
            aplicarPreset('30');
            campos.statusLista.querySelectorAll('input').forEach(entrada => {
                entrada.checked = false;
            });
            atualizarRotuloStatus();
            campos.atendimento.value = '';
            campos.categoria.value = '';
            atualizar();
        });
        document.getElementById('filtros').addEventListener('submit', evento => {
            evento.preventDefault();
            atualizar();
        });
        document.getElementById('btnAtualizar').addEventListener('click', () => atualizar());
        raiz.querySelectorAll('input[name="agrupamento"]').forEach(opcao =>
                opcao.addEventListener('change', () => atualizar(['vendas'])));
        raiz.querySelectorAll('input[name="criterio"]').forEach(opcao =>
                opcao.addEventListener('change', () => atualizar(['ranking'])));
        raiz.querySelectorAll('.btn-tabela').forEach(botao => botao.addEventListener('click', () => {
            const cartao = botao.closest('.painel');
            const ativo = cartao.classList.toggle('mostrar-tabela');
            botao.setAttribute('aria-pressed', String(ativo));
        }));
        campos.auto.addEventListener('change', () => {
            salvarPreferenciaAuto(campos.auto.checked);
            programarAtualizacao();
        });
        document.addEventListener('visibilitychange', () => {
            if (document.visibilityState === 'visible' && campos.auto.checked
                    && Date.now() - estado.ultimaAtualizacao >= INTERVALO_ATUALIZACAO_MS) {
                atualizar();
            }
            programarAtualizacao();
        });
    }

    async function iniciar() {
        configurarChart();
        if (!GRAFICOS_DISPONIVEIS) {
            raiz.querySelectorAll('.painel .grafico').forEach(grafico => grafico.closest('.painel').classList.add('mostrar-tabela'));
            raiz.querySelectorAll('.btn-tabela').forEach(botao => botao.classList.add('d-none'));
        }
        campos.auto.checked = lerPreferenciaAuto();
        let opcoes = null;
        try {
            opcoes = await buscar('/filtros', new URLSearchParams(), undefined);
        } catch (erro) {
            if (erro.status !== 401) {
                mostrarAlerta(`Não foi possível carregar as opções de filtro: ${erro.message}`);
            }
        }
        montarFiltros(opcoes);
        restaurarDaUrl();
        registrarEventos();
        await atualizar();
        programarAtualizacao();
    }

    iniciar();
})();
