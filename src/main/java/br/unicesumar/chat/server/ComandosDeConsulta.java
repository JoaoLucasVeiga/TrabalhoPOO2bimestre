package br.unicesumar.chat.server;

import static br.unicesumar.chat.server.ArgumentosDeComando.quantidade;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.protocol.Mensagens;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Comandos de consulta ao que está na sala: {@code /users}, {@code /history} e {@code /buscar}. */
final class ComandosDeConsulta implements GrupoDeComandos {

    private final ChatService chat;
    private final ClientRegistry registry;

    ComandosDeConsulta(ChatService chat, ClientRegistry registry) {
        this.chat = Objects.requireNonNull(chat, "chat");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public void registrarEm(TabelaDeComandos tabela) {
        tabela.registrar("users", "/users", "Lista os usuários online na sua sala.", this::usuarios);
        tabela.registrar("history", "/history [quantidade]",
                "Mostra as últimas mensagens da sala (de 1 a " + CommandService.HISTORICO_MAXIMO + ").",
                this::historico);
        tabela.registrar("buscar", "/buscar usuario|texto|periodo ...",
                "Busca no histórico da sala (ex.: /buscar texto java).", this::buscar);
    }

    private void usuarios(ClienteHandler origem, String argumentos) {
        origem.enviar(new Evento.Usuarios(origem.sala(), registry.usuariosNaSala(origem.sala())));
    }

    private void historico(ClienteHandler origem, String argumentos) {
        int quantidade = argumentos.isEmpty() ? ChatService.HISTORICO_INICIAL
                : quantidade(argumentos, CommandService.HISTORICO_MAXIMO, ComandosDeConsulta::usoDoHistorico);
        List<Evento.Msg> mensagens = chat.buscarHistorico(origem.sala(), quantidade).stream()
                .map(Evento.Msg::de).toList();
        if (mensagens.isEmpty()) {
            origem.enviar(new Evento.Sistema(Mensagens.SEM_HISTORICO));
        } else {
            origem.enviar(new Evento.Historico(mensagens));
        }
    }

    private static IllegalArgumentException usoDoHistorico() {
        return new IllegalArgumentException("Uso: /history [quantidade], com a quantidade de 1 a "
                + CommandService.HISTORICO_MAXIMO + ". Exemplo: /history 10");
    }

    private void buscar(ClienteHandler origem, String argumentos) {
        String[] palavras = argumentos.isEmpty() ? new String[0] : argumentos.split("\\s+");
        if (palavras.length < 2) {
            throw usoDaBusca();
        }
        String tipo = palavras[0].toLowerCase(Locale.ROOT);
        String resto = argumentos.substring(palavras[0].length()).strip();
        List<Mensagem> achadas = switch (tipo) {
            case "usuario" -> palavras.length == 2 ? chat.buscarPorUsuario(origem.sala(), palavras[1])
                    : semResultado();
            case "texto" -> chat.buscarPorTexto(origem.sala(), resto);
            case "periodo" -> palavras.length == 3
                    ? chat.buscarPorPeriodo(origem.sala(), data(palavras[1]), data(palavras[2]))
                    : semResultado();
            default -> semResultado();
        };
        List<Evento.Msg> mensagens = achadas.stream().map(Evento.Msg::de).toList();
        if (mensagens.isEmpty()) {
            origem.enviar(new Evento.Sistema(Mensagens.SEM_RESULTADO_DE_BUSCA));
        } else {
            origem.enviar(new Evento.Historico(mensagens));
        }
    }

    private static List<Mensagem> semResultado() {
        throw usoDaBusca();
    }

    private static LocalDate data(String texto) {
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            throw usoDaBusca();
        }
    }

    private static IllegalArgumentException usoDaBusca() {
        return new IllegalArgumentException("Uso: /buscar usuario <nome> | /buscar texto <trecho> | "
                + "/buscar periodo <AAAA-MM-DD> <AAAA-MM-DD>. Exemplo: /buscar periodo 2026-10-01 2026-10-31");
    }
}
