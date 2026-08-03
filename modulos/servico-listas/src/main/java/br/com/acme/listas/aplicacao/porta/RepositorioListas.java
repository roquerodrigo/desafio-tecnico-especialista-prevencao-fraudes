package br.com.acme.listas.aplicacao.porta;

import br.com.acme.listas.dominio.EntradaLista;
import br.com.acme.listas.dominio.ResultadoConsulta;
import java.util.List;

public interface RepositorioListas {

    /**
     * Consulta as tres variaveis. A implementacao MUST resolver em uma unica ida ao armazenamento.
     */
    ResultadoConsulta consultar(String cpf, String ip, String idDispositivo);

    void gravar(List<EntradaLista> entradas);
}
