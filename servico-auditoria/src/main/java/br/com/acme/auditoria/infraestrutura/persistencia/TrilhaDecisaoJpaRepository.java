package br.com.acme.auditoria.infraestrutura.persistencia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrilhaDecisaoJpaRepository extends JpaRepository<TrilhaDecisaoEntity, UUID> {

    List<TrilhaDecisaoEntity> findByCpfOrderByOcorridoEmDesc(String cpf, Limit limite);

    Optional<TrilhaDecisaoEntity> findByIdCorrelacao(String idCorrelacao);

    boolean existsByIdCorrelacao(String idCorrelacao);
}
