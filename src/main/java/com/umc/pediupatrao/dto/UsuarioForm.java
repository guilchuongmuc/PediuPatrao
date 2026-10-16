package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.Perfil;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


public class UsuarioForm {

    @Pattern(regexp = "^[a-zA-Z0-9._-]{3,30}$",
            message = "O username deve ter de 3 a 30 caracteres (letras, números, ponto, hífen ou sublinhado).")
    private String username;

    @Size(max = 72, message = "A senha deve ter no máximo 72 caracteres.")
    private String password;

    @NotNull(message = "Selecione o perfil.")
    private Perfil perfil;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Perfil getPerfil() {
        return perfil;
    }

    public void setPerfil(Perfil perfil) {
        this.perfil = perfil;
    }
}
