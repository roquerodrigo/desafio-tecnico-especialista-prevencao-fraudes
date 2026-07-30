package br.com.acme.listas.dominio;

/**
 * Resultado da consulta das tres variaveis de uma transacao. Variavel nao informada vem nula.
 */
public record ResultadoConsulta(
        ResultadoVariavel cpf,
        ResultadoVariavel ip,
        ResultadoVariavel dispositivo) {
}
