package br.unicesumar.chat.server;

/**
 * Porta entre a camada de rede e as regras do chat: o {@link ClienteHandler}
 * só lê e escreve linhas; quem decide o que elas significam é um processador.
 * Os métodos são chamados na thread do cliente, então a implementação é
 * compartilhada entre threads e precisa ser thread-safe.
 */
@FunctionalInterface
public interface ProcessadorEntrada {

    /** Chamado uma vez, quando a conexão é atendida. */
    default void aoConectar(ClienteHandler origem) {
    }

    /** Chamado para cada linha recebida, já decodificada. */
    void aoReceber(ClienteHandler origem, String linha);

    /** Chamado uma única vez, quando a conexão termina (normal ou abrupta). */
    default void aoDesconectar(ClienteHandler origem) {
    }
}
