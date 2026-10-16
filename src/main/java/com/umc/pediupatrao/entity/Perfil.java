package com.umc.pediupatrao.entity;

/**
 * Perfis de acesso do sistema. O nome do enum é usado como role do Spring Security
 * (ex.: GERENTE -> ROLE_GERENTE).
 */
public enum Perfil {

    ADMIN("Administrador", "Administra usuários e perfis, consulta pedidos e visualiza a auditoria."),
    GERENTE("Gerente", "Gerencia pedidos, aplica descontos, cancela pedidos, consulta clientes e visualiza a auditoria."),
    ATENDENTE("Atendente", "Cadastra e atualiza clientes, registra pedidos e acompanha seu andamento.");

    private final String descricao;
    private final String responsabilidades;

    Perfil(String descricao, String responsabilidades) {
        this.descricao = descricao;
        this.responsabilidades = responsabilidades;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getResponsabilidades() {
        return responsabilidades;
    }

    public String getAuthority() {
        return "ROLE_" + name();
    }

    /**
     * Converte valores gravados por versões anteriores do sistema (ex.: "USER", "ROLE_ADMIN").
     * Valores desconhecidos retornam null, deixando o usuário sem perfil (login bloqueado).
     */
    public static Perfil fromLegado(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.trim().toUpperCase();
        if (normalizado.startsWith("ROLE_")) {
            normalizado = normalizado.substring(5);
        }
        if (normalizado.equals("USER")) {
            return ATENDENTE;
        }
        try {
            return Perfil.valueOf(normalizado);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
