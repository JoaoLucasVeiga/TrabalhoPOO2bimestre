package br.unicesumar.chat.client.controller;

import br.unicesumar.chat.protocol.Evento;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Uma linha da conversa, no formato que a tela precisa (sem protocolo e sem Swing).
 *
 * @param tipo         pública, privada ou aviso do sistema
 * @param remetente    quem enviou ({@code null} em avisos do sistema)
 * @param destinatario para quem, nas privadas ({@code null} nas demais)
 * @param horario      momento da mensagem
 * @param texto        conteúdo
 * @param minha        se foi enviada pelo usuário desta janela
 */
public record MensagemTela(Tipo tipo, String remetente, String destinatario, LocalDateTime horario,
        String texto, boolean minha) {

    /** Tipo de linha da conversa. */
    public enum Tipo { PUBLICA, PRIVADA, SISTEMA }

    private static final DateTimeFormatter HORA_MINUTO = DateTimeFormatter.ofPattern("HH:mm");

    /** Valida os campos obrigatórios. */
    public MensagemTela {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(horario, "horario");
        Objects.requireNonNull(texto, "texto");
    }

    /** Mensagem pública recebida do servidor. */
    public static MensagemTela publica(Evento.Msg evento, String usuarioAtual) {
        Objects.requireNonNull(usuarioAtual, "usuarioAtual");
        return new MensagemTela(Tipo.PUBLICA, evento.remetente(), null, evento.horario(), evento.texto(),
                evento.remetente().equalsIgnoreCase(usuarioAtual));
    }

    /** Mensagem privada (enviada ou recebida). */
    public static MensagemTela privada(Evento.Privada evento, String usuarioAtual) {
        Objects.requireNonNull(usuarioAtual, "usuarioAtual");
        return new MensagemTela(Tipo.PRIVADA, evento.de(), evento.para(), evento.horario(), evento.texto(),
                evento.de().equalsIgnoreCase(usuarioAtual));
    }

    /** Aviso do sistema (entrada, saída, ajuda). */
    public static MensagemTela sistema(String texto, LocalDateTime horario) {
        return new MensagemTela(Tipo.SISTEMA, null, null, horario, texto, false);
    }

    /** Formato padrão: {@code [HH:mm] nome: texto}. */
    public String formatar() {
        return "[" + horario.format(HORA_MINUTO) + "] " + remetente + ": " + texto;
    }
}
