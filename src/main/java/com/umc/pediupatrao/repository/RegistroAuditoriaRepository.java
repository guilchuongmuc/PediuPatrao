package com.umc.pediupatrao.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

import com.umc.pediupatrao.entity.RegistroAuditoria;


public interface RegistroAuditoriaRepository extends Repository<RegistroAuditoria, String> {

    <S extends RegistroAuditoria> S insert(S registro);

    Optional<RegistroAuditoria> findById(String id);

    Optional<RegistroAuditoria> findTopByOrderBySequenciaDesc();

    List<RegistroAuditoria> findAllByOrderBySequenciaAsc();

    long count();
}
