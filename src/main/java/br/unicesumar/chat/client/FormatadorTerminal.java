package br.unicesumar.chat.client;

import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.protocol.Evento.*;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.stream.Collectors;

/** Converte os eventos do servidor em texto legível para o terminal. */
public final class FormatadorTerminal {

    private static final DateTimeFormatter HORA_MINUTO = DateTimeFormatter.ofPattern("HH:mm");

    private FormatadorTerminal() {
    }

    /** Formata um evento. Mensagens seguem o padrão {@code [HH:mm] nome: texto}. */
    public static String formatar(Evento evento) {
        Objects.requireNonNull(evento, "evento");
        return switch (evento) {
            case Msg m -> "[" + m.horario().format(HORA_MINUTO) + "] " + m.remetente() + ": " + m.texto();
            case Privada p -> "[" + p.horario().format(HORA_MINUTO) + "] (privada) " + p.de() + " -> "
                    + p.para() + ": " + p.texto();
            case Sistema s -> "* " + s.aviso();
            case Ok o -> "[ok] " + o.texto();
            case Erro e -> "[erro] " + e.texto();
            case Usuarios u -> u.nomes().isEmpty()
                    ? "Ninguém online em " + u.sala() + "."
                    : "Online em " + u.sala() + " (" + u.nomes().size() + "): " + String.join(", ", u.nomes());
            case Salas s -> s.nomes().isEmpty() ? "Nenhuma sala." : "Salas: " + String.join(", ", s.nomes());
            case Historico h -> historico(h);
        };
    }

    private static String historico(Historico h) {
        if (h.mensagens().isEmpty()) {
            return "Não há mensagens anteriores.";
        }
        int n = h.mensagens().size();
        String linhas = h.mensagens().stream().map(FormatadorTerminal::formatar)
                .collect(Collectors.joining("\n"));
        return "--- Histórico (" + n + (n == 1 ? " mensagem" : " mensagens") + ") ---\n" + linhas
                + "\n--- fim do histórico ---";
    }
}
