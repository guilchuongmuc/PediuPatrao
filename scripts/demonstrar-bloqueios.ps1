<#
.SYNOPSIS
    Demonstra que as permissões do PediuPatrão são aplicadas no BACKEND, chamando as APIs
    diretamente (sem a interface) com cada perfil e mostrando o código HTTP de cada tentativa.

.DESCRIPTION
    - Autentica via HTTP Basic com os usuários admin, gerente e atendente.
    - Obtém o token CSRF da sessão em /api/csrf (exigido em POST/PUT/DELETE).
    - Registra um pedido como ATENDENTE e tenta operações proibidas para cada perfil.
    Todas as tentativas negadas aparecem depois na tela Auditoria (operação "Tentativa de acesso não autorizado").

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\scripts\demonstrar-bloqueios.ps1
    powershell -ExecutionPolicy Bypass -File .\scripts\demonstrar-bloqueios.ps1 -BaseUrl http://localhost:8080
#>
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$Senha
)

$ErrorActionPreference = 'Stop'
if (-not $Senha) {
    $seguro = Read-Host -AsSecureString 'Senha dos usuários admin/gerente/atendente'
    $Senha = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($seguro))
}

$script:ok = 0
$script:falhas = 0

function New-Sessao([string]$usuario) {
    $basic = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${usuario}:$Senha"))
    $resp = Invoke-WebRequest -Uri "$BaseUrl/api/csrf" -UseBasicParsing -SessionVariable sessao `
        -Headers @{ Authorization = "Basic $basic" }
    $csrf = $resp.Content | ConvertFrom-Json
    return @{ Usuario = $usuario; Web = $sessao; Basic = $basic; Token = $csrf.token }
}

function Invoke-Api($sessao, [string]$metodo, [string]$caminho, $corpo = $null, [switch]$SemCsrf) {
    $headers = @{}
    $parametros = @{ Uri = "$BaseUrl$caminho"; Method = $metodo; UseBasicParsing = $true }
    if ($sessao) {
        $headers['Authorization'] = "Basic $($sessao.Basic)"
        $parametros['WebSession'] = $sessao.Web
        if ($SemCsrf) {
            # O PowerShell guarda cabeçalhos na WebSession; remove o token para simular a requisição sem CSRF
            $sessao.Web.Headers.Remove('X-CSRF-TOKEN') | Out-Null
        } else {
            $headers['X-CSRF-TOKEN'] = $sessao.Token
        }
    }
    $parametros['Headers'] = $headers
    if ($null -ne $corpo) {
        $parametros['Body'] = [Text.Encoding]::UTF8.GetBytes(($corpo | ConvertTo-Json -Depth 5))
        $parametros['ContentType'] = 'application/json; charset=utf-8'
    }
    try {
        $resp = Invoke-WebRequest @parametros
        $texto = [Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
        return @{ Status = [int]$resp.StatusCode; Corpo = $texto }
    } catch [System.Net.WebException] {
        $resposta = $_.Exception.Response
        if ($null -eq $resposta) { throw }
        # O PowerShell 5.1 às vezes já consumiu o corpo do erro e o expõe em ErrorDetails
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
            $texto = $_.ErrorDetails.Message
        } else {
            $leitor = New-Object IO.StreamReader($resposta.GetResponseStream(), [Text.Encoding]::UTF8)
            $texto = $leitor.ReadToEnd()
        }
        return @{ Status = [int]$resposta.StatusCode; Corpo = $texto }
    }
}

function Test-Regra([string]$descricao, $resultado, [int]$esperado) {
    $mensagem = ''
    if ($resultado.Status -ge 400) {
        try { $mensagem = ($resultado.Corpo | ConvertFrom-Json).mensagem } catch { }
    }
    if ($resultado.Status -eq $esperado) {
        $script:ok++
        Write-Host ("  [OK]    {0,-62} HTTP {1}  {2}" -f $descricao, $resultado.Status, $mensagem) -ForegroundColor Green
    } else {
        $script:falhas++
        Write-Host ("  [FALHA] {0,-62} HTTP {1} (esperado {2})  {3}" -f $descricao, $resultado.Status, $esperado, $mensagem) -ForegroundColor Red
    }
}

Write-Host "`nPediuPatrão - demonstração de autorização no backend ($BaseUrl)`n" -ForegroundColor Cyan

$atendente = New-Sessao 'atendente'
$gerente = New-Sessao 'gerente'
$admin = New-Sessao 'admin'

# ---------- Preparação: ATENDENTE registra um cliente e um pedido ----------
Write-Host 'Preparação (ATENDENTE)' -ForegroundColor Cyan
$cliente = Invoke-Api $atendente 'POST' '/api/clientes' @{
    nome = 'Cliente Script'; telefone = '(11) 97777-1234'; logradouro = 'Rua do Teste'; numero = '1'
    bairro = 'Centro'; cidade = 'Mogi das Cruzes'; estado = 'SP' }
Test-Regra 'ATENDENTE cadastra cliente' $cliente 201
$clienteId = ($cliente.Corpo | ConvertFrom-Json).id

$produtos = (Invoke-Api $atendente 'GET' '/api/produtos').Corpo | ConvertFrom-Json
$produto = $produtos | Where-Object { $_.ativo } | Select-Object -First 1

# Tenta forjar total e status: o servidor ignora e calcula pelos preços cadastrados
$pedido = Invoke-Api $atendente 'POST' '/api/pedidos' @{
    clienteId = $clienteId; tipoAtendimento = 'RETIRADA'; valorTotal = 0.01; status = 'FINALIZADO'
    itens = @(@{ produtoId = $produto.id; quantidade = 2 }) }
Test-Regra 'ATENDENTE registra pedido (total/status forjados ignorados)' $pedido 201
$p = $pedido.Corpo | ConvertFrom-Json
Write-Host ("          Pedido #{0}: status {1}, total calculado R$ {2:N2} (2 x {3:N2} - {4})" -f $p.numero, $p.status, $p.valorTotal, $produto.preco, $produto.nome)
$pedidoId = $p.id

# ---------- ATENDENTE ----------
Write-Host "`nATENDENTE: tentativas proibidas" -ForegroundColor Cyan
Test-Regra 'ATENDENTE cancela pedido' (Invoke-Api $atendente 'POST' "/api/pedidos/$pedidoId/cancelamento" @{ justificativa = 'Tentativa do atendente' }) 403
Test-Regra 'ATENDENTE aplica desconto' (Invoke-Api $atendente 'POST' "/api/pedidos/$pedidoId/desconto" @{ percentual = 10 }) 403
Test-Regra 'ATENDENTE lista usuários' (Invoke-Api $atendente 'GET' '/api/usuarios') 403
Test-Regra 'ATENDENTE cria usuário ADMIN (autoelevação)' (Invoke-Api $atendente 'POST' '/api/usuarios' @{ username = 'novoadmin'; password = 'SenhaForte#1'; perfil = 'ADMIN' }) 403
Test-Regra 'ATENDENTE consulta auditoria' (Invoke-Api $atendente 'GET' '/api/auditoria') 403
Test-Regra 'ATENDENTE pula etapa (RECEBIDO -> FINALIZADO)' (Invoke-Api $atendente 'PUT' "/api/pedidos/$pedidoId/status?status=FINALIZADO") 422

# ---------- ADMIN ----------
Write-Host "`nADMIN: consulta pedidos, mas não dá desconto nem cancela" -ForegroundColor Cyan
Test-Regra 'ADMIN consulta pedidos' (Invoke-Api $admin 'GET' '/api/pedidos') 200
Test-Regra 'ADMIN aplica desconto' (Invoke-Api $admin 'POST' "/api/pedidos/$pedidoId/desconto" @{ percentual = 10 }) 403
Test-Regra 'ADMIN cancela pedido' (Invoke-Api $admin 'POST' "/api/pedidos/$pedidoId/cancelamento" @{ justificativa = 'Tentativa do administrador' }) 403
Test-Regra 'ADMIN registra pedido' (Invoke-Api $admin 'POST' '/api/pedidos' @{ clienteId = $clienteId; tipoAtendimento = 'RETIRADA'; itens = @(@{ produtoId = $produto.id; quantidade = 1 }) }) 403
Test-Regra 'ADMIN exclui registro de auditoria' (Invoke-Api $admin 'DELETE' '/api/auditoria/qualquer') 403

# ---------- GERENTE ----------
Write-Host "`nGERENTE: desconto e cancelamento com regras" -ForegroundColor Cyan
Test-Regra 'GERENTE desconto de 25% (limite 20%)' (Invoke-Api $gerente 'POST' "/api/pedidos/$pedidoId/desconto" @{ percentual = 25 }) 400
Test-Regra 'GERENTE desconto de 10%' (Invoke-Api $gerente 'POST' "/api/pedidos/$pedidoId/desconto" @{ percentual = 10; justificativa = 'Demonstração' }) 200
Test-Regra 'GERENTE cancela sem justificativa' (Invoke-Api $gerente 'POST' "/api/pedidos/$pedidoId/cancelamento" @{ justificativa = '' }) 400
Test-Regra 'GERENTE cancela com justificativa' (Invoke-Api $gerente 'POST' "/api/pedidos/$pedidoId/cancelamento" @{ justificativa = 'Cliente desistiu do pedido' }) 200
Test-Regra 'GERENTE altera status após cancelamento' (Invoke-Api $gerente 'PUT' "/api/pedidos/$pedidoId/status?status=EM_PREPARACAO") 422
Test-Regra 'GERENTE altera cliente (só ATENDENTE)' (Invoke-Api $gerente 'PUT' "/api/clientes/$clienteId" @{ nome = 'X'; telefone = '(11) 90000-0000' }) 403
Test-Regra 'GERENTE lista usuários (só ADMIN)' (Invoke-Api $gerente 'GET' '/api/usuarios') 403
Test-Regra 'GERENTE consulta auditoria' (Invoke-Api $gerente 'GET' '/api/auditoria') 200

# ---------- Sem autenticação / sem CSRF ----------
Write-Host "`nSem autenticação e sem token CSRF" -ForegroundColor Cyan
Test-Regra 'Anônimo lista pedidos' (Invoke-Api $null 'GET' '/api/pedidos') 401
Test-Regra 'GERENTE sem token CSRF' (Invoke-Api $gerente 'POST' "/api/pedidos/$pedidoId/desconto" @{ percentual = 5 } -SemCsrf) 403

Write-Host ("`nResultado: {0} verificações OK, {1} falha(s)." -f $script:ok, $script:falhas) -ForegroundColor $(if ($script:falhas -eq 0) { 'Green' } else { 'Red' })
Write-Host 'Veja as tentativas negadas em Auditoria (perfil ADMIN ou GERENTE).'
