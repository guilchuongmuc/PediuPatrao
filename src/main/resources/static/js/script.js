// Cabeçalhos com o token CSRF (lido das meta tags do layout) para fetch que altera dados
function csrfHeaders(extra) {
    const token = document.querySelector('meta[name="_csrf"]');
    const header = document.querySelector('meta[name="_csrf_header"]');
    const headers = Object.assign({}, extra || {});
    if (token && header) {
        headers[header.content] = token.content;
    }
    return headers;
}

// Escapa texto antes de inserir em HTML (evita XSS em tabelas montadas via JavaScript)
function escapeHtml(valor) {
    const mapa = {'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'};
    return String(valor ?? '').replace(/[&<>"']/g, c => mapa[c]);
}

// Alternar submenu
function toggleSubmenu(event, element) {
    event.preventDefault();
    element.parentElement.classList.toggle('open');
}

// Alternar sidebar em telas pequenas
document.addEventListener('DOMContentLoaded', () => {
    const botao = document.querySelector('.toggle-btn');
    const sidebar = document.getElementById('sidebar');
    if (botao && sidebar) {
        botao.addEventListener('click', () => sidebar.classList.toggle('active'));
    }
});
