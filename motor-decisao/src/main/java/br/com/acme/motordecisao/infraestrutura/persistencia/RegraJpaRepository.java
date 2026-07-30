package br.com.acme.motordecisao.infraestrutura.persistencia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegraJpaRepository extends JpaRepository<RegraEntity, UUID> {

    List<RegraEntity> findByTipoTransacaoAndAtivaTrue(String tipoTransacao);

    List<RegraEntity> findByAtivaTrue();

    Optional<RegraEntity> findByTipoTransacaoAndChave(String tipoTransacao, String chave);
}
