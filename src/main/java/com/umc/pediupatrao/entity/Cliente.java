package com.umc.pediupatrao.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "clientes")
public class Cliente {

    @Id
    private String id;

    @NotBlank(message = "Informe o nome do cliente.")
    @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
    private String nome;

    @NotBlank(message = "Informe o telefone do cliente.")
    @Pattern(regexp = "^\\(?\\d{2}\\)?\\s?\\d{4,5}-?\\d{4}$",
            message = "Telefone inválido. Use o formato (11) 99999-9999.")
    private String telefone;

    // Endereço completo
    @Pattern(regexp = "^$|^\\d{5}-?\\d{3}$", message = "CEP inválido. Use o formato 00000-000.")
    private String cep;

    @Size(max = 150, message = "O logradouro deve ter no máximo 150 caracteres.")
    private String logradouro;

    @Size(max = 10, message = "O número deve ter no máximo 10 caracteres.")
    private String numero;

    @Size(max = 60, message = "O complemento deve ter no máximo 60 caracteres.")
    private String complemento;

    @Size(max = 80, message = "O bairro deve ter no máximo 80 caracteres.")
    private String bairro;

    @Size(max = 80, message = "A cidade deve ter no máximo 80 caracteres.")
    private String cidade;

    @Pattern(regexp = "^$|^[A-Za-z]{2}$", message = "Informe a UF com 2 letras.")
    private String estado;

    // Mantido para compatibilidade com dados antigos
    private String endereco;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public String getCep() { return cep; }
    public void setCep(String cep) { this.cep = cep; }

    public String getLogradouro() { return logradouro; }
    public void setLogradouro(String logradouro) { this.logradouro = logradouro; }

    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }

    public String getComplemento() { return complemento; }
    public void setComplemento(String complemento) { this.complemento = complemento; }

    public String getBairro() { return bairro; }
    public void setBairro(String bairro) { this.bairro = bairro; }

    public String getCidade() { return cidade; }
    public void setCidade(String cidade) { this.cidade = cidade; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getEndereco() { return endereco; }
    public void setEndereco(String endereco) { this.endereco = endereco; }

    /** Endereço em uma linha; usa o campo legado quando o endereço completo não foi informado. */
    @JsonIgnore
    @Transient
    public String getEnderecoFormatado() {
        String completo = Stream.of(logradouro, numero, complemento, bairro, cidade, estado)
                .filter(parte -> parte != null && !parte.isBlank())
                .collect(Collectors.joining(", "));
        if (!completo.isBlank()) {
            return cep != null && !cep.isBlank() ? completo + " - CEP " + cep : completo;
        }
        return endereco != null && !endereco.isBlank() ? endereco : null;
    }
}
