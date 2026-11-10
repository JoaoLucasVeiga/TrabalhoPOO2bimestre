package br.unicesumar.chat.server;

import br.unicesumar.chat.protocol.Evento;
import java.util.Objects;

/** Comandos de sala: {@code /join}, {@code /leave} e {@code /rooms}. */
final class ComandosDeSala implements GrupoDeComandos {

    private final SalaService salas;

    ComandosDeSala(SalaService salas) {
        this.salas = Objects.requireNonNull(salas, "salas");
    }

    @Override
    public void registrarEm(TabelaDeComandos tabela) {
        tabela.registrar("join", "/join sala", "Entra em uma sala (cria se ainda não existir).", this::entrar);
        tabela.registrar("leave", "/leave", "Sai da sala atual e volta para a geral.", this::sair);
        tabela.registrar("rooms", "/rooms", "Lista as salas ativas.", this::listar);
    }

    private void entrar(ClienteHandler origem, String argumentos) {
        if (argumentos.isEmpty()) {
            throw new IllegalArgumentException("Uso: /join sala. Exemplo: /join java");
        }
        salas.entrarSala(origem, argumentos);
    }

    private void sair(ClienteHandler origem, String argumentos) {
        salas.sairSala(origem);
    }

    private void listar(ClienteHandler origem, String argumentos) {
        origem.enviar(new Evento.Salas(salas.listarSalas()));
    }
}
