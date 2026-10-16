package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.StatusPedido;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

/**
 * Conversores de leitura para documentos gravados pela versão anterior do sistema
 * (status em texto livre e role "USER"), evitando erros ao listar dados antigos.
 */
@Configuration
public class MongoConfig {

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(new StatusPedidoLegadoConverter(), new PerfilLegadoConverter()));
    }

    @ReadingConverter
    static class StatusPedidoLegadoConverter implements Converter<String, StatusPedido> {
        @Override
        public StatusPedido convert(String valor) {
            return StatusPedido.fromLegado(valor);
        }
    }

    @ReadingConverter
    static class PerfilLegadoConverter implements Converter<String, Perfil> {
        @Override
        public Perfil convert(String valor) {
            return Perfil.fromLegado(valor);
        }
    }
}
