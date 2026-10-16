package com.umc.pediupatrao.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document(collection = "usuarios") // Define a coleção no MongoDB
public class Usuario {
    @Id
    private String id;
    private String username; // Nome de usuário para login (imutável após o cadastro)

    @JsonIgnore
    private String password; // Senha criptografada (BCrypt) — nunca exposta em JSON

    @Field("role")           // Mantém o nome do campo usado pelas versões anteriores
    private Perfil perfil;   // ADMIN, GERENTE ou ATENDENTE

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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
