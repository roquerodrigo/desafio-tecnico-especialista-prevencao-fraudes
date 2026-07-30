package br.com.acme.analiserisco.infraestrutura.persistencia;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaixaScoreJpaRepository extends JpaRepository<FaixaScoreEntity, UUID> {

    List<FaixaScoreEntity> findAllByOrderByOrdemAsc();
}
