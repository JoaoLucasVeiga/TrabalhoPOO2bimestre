package br.unicesumar.chat.server;

import br.unicesumar.chat.protocol.Evento;
import java.util.Objects;

/** Comandos que não dependem de nenhum serviço: {@code /help} (lista a própria tabela) e {@code /quit}. */
final class ComandosBasicos {

    private final TabelaDeComandos tabela;

    ComandosBasicos(TabelaDeComandos tabela) {
        this.tabela = Objects.requireNonNull(tabela, "tabela");
    }

    /** Registra o {@code /help}. */
    void registrarAjuda(TabelaDeComandos destino) {
        destino.registrar("help", "/help", "Mostra esta lista de comandos.", this::ajuda);
    }

    /** Registra o {@code /quit}. */
    void registrarSaida(TabelaDeComandos destino) {
        destino.registrar("quit", "/quit", "Sai do chat.", this::sair);
    }

    private void ajuda(ClienteHandler origem, String argumentos) {
        StringBuilder sb = new StringBuilder("Comandos disponíveis:");
        for (TabelaDeComandos.Entrada e : tabela.todas()) {
            sb.append('\n').append(e.uso()).append(" - ").append(e.descricao());
        }
        origem.enviar(new Evento.Sistema(sb.toString()));
    }

    private void sair(ClienteHandler origem, String argumentos) {
        origem.enviar(new Evento.Ok("Até logo!"));
        origem.encerrar();
    }
}
