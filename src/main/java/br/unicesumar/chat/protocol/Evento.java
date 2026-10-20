package br.unicesumar.chat.protocol;

import br.unicesumar.chat.model.Mensagem;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Eventos enviados do servidor para o cliente. Todos imutáveis. */
public sealed interface Evento {

    /**
     * Mensagem pública de uma sala.
     *
     * @param sala      sala de destino
     * @param remetente nome de quem enviou
     * @param horario   momento do envio
     * @param texto     conteúdo
     */
    record Msg(String sala, String remetente, LocalDateTime horario, String texto)
            implements Evento {
        /** Valida que nenhum campo é nulo. */
        public Msg {
            Objects.requireNonNull(sala, "sala");
            Objects.requireNonNull(remetente, "remetente");
            Objects.requireNonNull(horario, "horario");
            Objects.requireNonNull(texto, "texto");
        }

        /** Converte uma mensagem de domínio em evento. */
        public static Msg de(Mensagem m) {
            return new Msg(m.sala(), m.remetente().nome(), m.horario(), m.conteudo());
        }
    }

    /**
     * Mensagem privada.
     *
     * @param de      remetente
     * @param para    destinatário
     * @param horario momento do envio
     * @param texto   conteúdo
     */
    record Privada(String de, String para, LocalDateTime horario, String texto)
            implements Evento {
        /** Valida que nenhum campo é nulo. */
        public Privada {
            Objects.requireNonNull(de, "de");
            Objects.requireNonNull(para, "para");
            Objects.requireNonNull(horario, "horario");
            Objects.requireNonNull(texto, "texto");
        }
    }

    /** Aviso do sistema (entrada, saída etc.). */
    record Sistema(String aviso) implements Evento {
        /** Valida que o aviso não é nulo. */
        public Sistema {
            Objects.requireNonNull(aviso, "aviso");
        }
    }

    /** Usuários conectados em uma sala. */
    record Usuarios(String sala, List<String> nomes) implements Evento {
        /** Valida e copia a lista de forma imutável. */
        public Usuarios {
            Objects.requireNonNull(sala, "sala");
            nomes = List.copyOf(nomes);
        }
    }

    /** Salas existentes. */
    record Salas(List<String> nomes) implements Evento {
        /** Copia a lista de forma imutável. */
        public Salas {
            nomes = List.copyOf(nomes);
        }
    }

    /** Mensagens recuperadas do histórico, da mais antiga para a mais recente. */
    record Historico(List<Msg> mensagens) implements Evento {
        /** Copia a lista de forma imutável. */
        public Historico {
            mensagens = List.copyOf(mensagens);
        }
    }

    /** Confirmação de que um pedido foi atendido. */
    record Ok(String texto) implements Evento {
        /** Valida que o texto não é nulo. */
        public Ok {
            Objects.requireNonNull(texto, "texto");
        }
    }

    /** Falha: diz o que aconteceu e o que fazer. */
    record Erro(String texto) implements Evento {
        /** Valida que o texto não é nulo. */
        public Erro {
            Objects.requireNonNull(texto, "texto");
        }
    }
}
