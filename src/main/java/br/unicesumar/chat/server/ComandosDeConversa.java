package br.unicesumar.chat.server;

import static br.unicesumar.chat.server.ArgumentosDeComando.indiceDoEspaco;
import static br.unicesumar.chat.server.ArgumentosDeComando.nomeObrigatorio;
import static br.unicesumar.chat.server.ArgumentosDeComando.quantidade;

import br.unicesumar.chat.protocol.Evento;
import java.util.List;
import java.util.Objects;

/** Conversas privadas: {@code /msg}, {@code /conversas} e {@code /conversa}. */
final class ComandosDeConversa implements GrupoDeComandos {

    private static final int CONVERSA_PADRAO = 50;

    private final ChatService chat;

    ComandosDeConversa(ChatService chat) {
        this.chat = Objects.requireNonNull(chat, "chat");
    }

    @Override
    public void registrarEm(TabelaDeComandos tabela) {
        tabela.registrar("msg", "/msg usuario texto", "Envia uma mensagem privada a um usuário online.",
                this::privada);
        tabela.registrar("conversas", "/conversas", "Lista as suas conversas privadas.", this::conversas);
        tabela.registrar("conversa", "/conversa usuario [quantidade]",
                "Mostra a sua conversa privada com um usuário (de 1 a " + CommandService.HISTORICO_MAXIMO
                        + " mensagens).", this::conversa);
    }

    private void privada(ClienteHandler origem, String argumentos) {
        int espaco = indiceDoEspaco(argumentos);
        String nome = espaco < 0 ? argumentos : argumentos.substring(0, espaco);
        String texto = espaco < 0 ? "" : argumentos.substring(espaco).strip();
        if (nome.isEmpty() || texto.isEmpty()) {
            throw new IllegalArgumentException("Uso: /msg usuario texto. Exemplo: /msg ana Olá!");
        }
        chat.enviarPrivada(origem, nome, texto);
    }

    private void conversas(ClienteHandler origem, String argumentos) {
        if (!argumentos.isEmpty()) {
            throw new IllegalArgumentException("Uso: /conversas (sem argumentos).");
        }
        origem.enviar(new Evento.Conversas(chat.conversasDe(nomeObrigatorio(origem))));
    }

    private void conversa(ClienteHandler origem, String argumentos) {
        String[] partes = argumentos.isEmpty() ? new String[0] : argumentos.split("\\s+");
        if (partes.length < 1 || partes.length > 2) {
            throw usoDaConversa();
        }
        int quantidade = partes.length == 2
                ? quantidade(partes[1], CommandService.HISTORICO_MAXIMO, ComandosDeConversa::usoDaConversa)
                : CONVERSA_PADRAO;
        List<Evento.Privada> mensagens = chat.conversa(nomeObrigatorio(origem), partes[0], quantidade).stream()
                .map(m -> new Evento.Privada(m.remetente().nome(), m.destinatario().orElseThrow().nome(),
                        m.horario(), m.conteudo()))
                .toList();
        origem.enviar(new Evento.Conversa(partes[0], mensagens));
    }

    private static IllegalArgumentException usoDaConversa() {
        return new IllegalArgumentException("Uso: /conversa usuario [quantidade], com a quantidade de 1 a "
                + CommandService.HISTORICO_MAXIMO + ". Exemplo: /conversa ana 20");
    }
}
