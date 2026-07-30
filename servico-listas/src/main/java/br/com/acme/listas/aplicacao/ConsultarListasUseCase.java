package br.com.acme.listas.aplicacao;

import br.com.acme.listas.aplicacao.porta.RepositorioListas;
import br.com.acme.listas.dominio.ResultadoConsulta;
import org.springframework.stereotype.Service;

@Service
public class ConsultarListasUseCase {

    private final RepositorioListas repositorio;

    public ConsultarListasUseCase(RepositorioListas repositorio) {
        this.repositorio = repositorio;
    }

    public ResultadoConsulta executar(String cpf, String ip, String idDispositivo) {
        if (cpf == null && ip == null && idDispositivo == null) {
            throw new IllegalArgumentException("Informe ao menos uma variavel para consulta");
        }
        return repositorio.consultar(cpf, ip, idDispositivo);
    }
}
