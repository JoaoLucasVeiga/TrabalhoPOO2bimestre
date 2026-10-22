package br.unicesumar.chat.client.controller;

import br.unicesumar.chat.client.net.ConexaoChat;

/** Cria a conexão para um servidor (real em produção, falsa nos testes). */
@FunctionalInterface
public interface FabricaConexao {

    /** Cria uma conexão ainda fechada para o servidor indicado. */
    ConexaoChat criar(String host, int porta);
}
