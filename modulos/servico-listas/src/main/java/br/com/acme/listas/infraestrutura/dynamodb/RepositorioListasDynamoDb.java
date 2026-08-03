package br.com.acme.listas.infraestrutura.dynamodb;

import br.com.acme.listas.aplicacao.porta.RepositorioListas;
import br.com.acme.listas.dominio.EntradaLista;
import br.com.acme.listas.dominio.Pertinencia;
import br.com.acme.listas.dominio.ResultadoConsulta;
import br.com.acme.listas.dominio.ResultadoVariavel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ReadBatch;
import org.springframework.stereotype.Repository;

/**
 * Acesso as listas no DynamoDB.
 *
 * <p>A consulta das tres variaveis usa <b>um unico {@code BatchGetItem}</b> sobre as tres tabelas.
 * O {@code RequestItems} do DynamoDB aceita multiplas tabelas na mesma requisicao — e por isso a
 * modelagem em tabelas separadas nao custa round-trips extras. Complexidade: <b>O(1)</b> por item,
 * 1 ida a rede.
 *
 * <p>A expiracao e filtrada na leitura, sempre. A AWS documenta que o expurgo por TTL ocorre
 * "within a few days" da expiracao e recomenda filtrar itens expirados; confiar apenas no TTL
 * deixaria entradas vencidas ainda bloqueando transacoes legitimas.
 */
@Repository
public class RepositorioListasDynamoDb implements RepositorioListas {

    private final DynamoDbEnhancedClient cliente;
    private final DynamoDbTable<ItemListaCpf> tabelaCpf;
    private final DynamoDbTable<ItemListaIp> tabelaIp;
    private final DynamoDbTable<ItemListaDispositivo> tabelaDispositivo;

    public RepositorioListasDynamoDb(DynamoDbEnhancedClient cliente) {
        this.cliente = cliente;
        this.tabelaCpf = cliente.table(NomesTabelas.LISTAS_CPF, TableSchema.fromBean(ItemListaCpf.class));
        this.tabelaIp = cliente.table(NomesTabelas.LISTAS_IP, TableSchema.fromBean(ItemListaIp.class));
        this.tabelaDispositivo = cliente.table(NomesTabelas.LISTAS_DISPOSITIVO,
                TableSchema.fromBean(ItemListaDispositivo.class));
    }

    @Override
    public ResultadoConsulta consultar(String cpf, String ip, String idDispositivo) {
        Instant momento = Instant.now();

        BatchGetItemEnhancedRequest.Builder requisicao = BatchGetItemEnhancedRequest.builder();
        List<ReadBatch> lotes = new ArrayList<>();

        if (cpf != null) {
            lotes.add(ReadBatch.builder(ItemListaCpf.class)
                    .mappedTableResource(tabelaCpf)
                    .addGetItem(Key.builder().partitionValue(cpf).build())
                    .build());
        }
        if (ip != null) {
            lotes.add(ReadBatch.builder(ItemListaIp.class)
                    .mappedTableResource(tabelaIp)
                    .addGetItem(Key.builder().partitionValue(ip).build())
                    .build());
        }
        if (idDispositivo != null) {
            lotes.add(ReadBatch.builder(ItemListaDispositivo.class)
                    .mappedTableResource(tabelaDispositivo)
                    .addGetItem(Key.builder().partitionValue(idDispositivo).build())
                    .build());
        }

        if (lotes.isEmpty()) {
            return new ResultadoConsulta(null, null, null);
        }

        ItemListaCpf itemCpf = null;
        ItemListaIp itemIp = null;
        ItemListaDispositivo itemDispositivo = null;

        var resultado = cliente.batchGetItem(requisicao.readBatches(lotes).build());
        for (var pagina : resultado) {
            if (cpf != null) {
                itemCpf = primeiro(pagina.resultsForTable(tabelaCpf), itemCpf);
            }
            if (ip != null) {
                itemIp = primeiro(pagina.resultsForTable(tabelaIp), itemIp);
            }
            if (idDispositivo != null) {
                itemDispositivo = primeiro(pagina.resultsForTable(tabelaDispositivo), itemDispositivo);
            }
        }

        return new ResultadoConsulta(
                cpf == null ? null : montarCpf(cpf, itemCpf, momento),
                ip == null ? null : montarIp(ip, itemIp, momento),
                idDispositivo == null ? null : montarDispositivo(idDispositivo, itemDispositivo, momento));
    }

    private <T> T primeiro(List<T> encontrados, T atual) {
        return encontrados.isEmpty() ? atual : encontrados.get(0);
    }

    private ResultadoVariavel montarCpf(String valor, ItemListaCpf item, Instant momento) {
        if (item == null) {
            return ResultadoVariavel.ausente(valor);
        }
        return ResultadoVariavel.de(valor,
                paraDominio(item.getPermissiva()),
                paraDominio(item.getRestritiva()),
                momento);
    }

    private ResultadoVariavel montarIp(String valor, ItemListaIp item, Instant momento) {
        if (item == null) {
            return ResultadoVariavel.ausente(valor);
        }
        return ResultadoVariavel.de(valor, null, paraDominio(item.getRestritiva()), momento);
    }

    private ResultadoVariavel montarDispositivo(String valor, ItemListaDispositivo item, Instant momento) {
        if (item == null) {
            return ResultadoVariavel.ausente(valor);
        }
        return ResultadoVariavel.de(valor, null, paraDominio(item.getRestritiva()), momento);
    }

    private Pertinencia paraDominio(ItemPertinencia item) {
        return item == null ? null : item.paraDominio();
    }

    @Override
    public void gravar(List<EntradaLista> entradas) {
        for (EntradaLista entrada : entradas) {
            switch (entrada.tipo()) {
                case CPF -> gravarCpf(entrada);
                case IP -> gravarIp(entrada);
                case DISPOSITIVO -> gravarDispositivo(entrada);
            }
        }
    }

    private void gravarCpf(EntradaLista entrada) {
        ItemListaCpf item = new ItemListaCpf();
        item.setCpf(entrada.valor());
        item.setPermissiva(ItemPertinencia.de(entrada.permissiva()));
        item.setRestritiva(ItemPertinencia.de(entrada.restritiva()));
        tabelaCpf.putItem(item);
    }

    private void gravarIp(EntradaLista entrada) {
        ItemListaIp item = new ItemListaIp();
        item.setIp(entrada.valor());
        item.setRestritiva(ItemPertinencia.de(entrada.restritiva()));
        item.setExpiraEm(entrada.expiraEm() == null ? null : entrada.expiraEm().getEpochSecond());
        tabelaIp.putItem(item);
    }

    private void gravarDispositivo(EntradaLista entrada) {
        ItemListaDispositivo item = new ItemListaDispositivo();
        item.setIdDispositivo(entrada.valor());
        item.setRestritiva(ItemPertinencia.de(entrada.restritiva()));
        tabelaDispositivo.putItem(item);
    }
}
