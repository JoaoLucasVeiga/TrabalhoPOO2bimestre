package br.unicesumar.chat.server;

/** Um comando de chat (ex.: {@code /users}). Cada comando delega ao serviço responsável. */
@FunctionalInterface
public interface Comando {

    /**
     * Executa o comando.
     *
     * @param origem     cliente que o digitou
     * @param argumentos o que veio depois do nome do comando, sem espaços nas pontas
     * @throws IllegalArgumentException se os argumentos forem inválidos (vira ERRO ao cliente)
     */
    void executar(ClienteHandler origem, String argumentos);
}
