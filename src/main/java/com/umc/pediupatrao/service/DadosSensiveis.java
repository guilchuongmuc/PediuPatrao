package com.umc.pediupatrao.service;

import java.util.regex.Pattern;

/** Regras de proteção de dados aplicadas aos registros de auditoria. */
public final class DadosSensiveis {

    /** Substitui o valor de campos de credenciais: senhas e tokens nunca são gravados. */
    public static final String VALOR_PROTEGIDO = "[PROTEGIDO]";

    private static final Pattern CAMPO_DE_CREDENCIAL =
            Pattern.compile("(?i).*(senha|password|token|secret|credencial|hash).*");

    private DadosSensiveis() {
    }

    public static boolean isCampoDeCredencial(String campo) {
        return campo != null && CAMPO_DE_CREDENCIAL.matcher(campo).matches();
    }

    /**
     * Minimização de dados: mantém apenas DDD e os 4 últimos dígitos do telefone.
     * Ex.: (11) 98765-4321 -> (11) *****-4321
     */
    public static String mascararTelefone(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            return telefone;
        }
        String digitos = telefone.replaceAll("\\D", "");
        if (digitos.length() < 8) {
            return "****";
        }
        String finais = digitos.substring(digitos.length() - 4);
        String ddd = digitos.length() >= 10 ? "(" + digitos.substring(0, 2) + ") " : "";
        return ddd + "*****-" + finais;
    }
}
