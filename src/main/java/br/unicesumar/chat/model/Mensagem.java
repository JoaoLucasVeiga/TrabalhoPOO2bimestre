package br.unicesumar.chat.model;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;

/**
 * Objeto de valor imutável que representa uma mensagem do chat, pública
 * (sem destinatário) ou privada. Não sabe se salvar nem se enviar.
 */
public final class Mensagem {

    /** Tamanho máximo do conteúdo, em caracteres. */
    public static final int LIMITE_CONTEUDO = 500;

    private static final DateTimeFormatter HORA_MINUTO = DateTimeFormatter.ofPattern("HH:mm");

    private final Long id;
    private final Usuario remetente;
    private final Usuario destinatario;
    private final String sala;
    private final String conteudo;
    private final LocalDateTime horario;

    private Mensagem(Long id, Usuario remetente, Usuario destinatario, String sala,
            String conteudo, LocalDateTime horario) {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("O id da mensagem deve ser positivo.");
        }
        if (remetente == null) {
            throw new IllegalArgumentException("A mensagem precisa de um remetente.");
        }
        if (destinatario != null && destinatario.equals(remetente)) {
            throw new IllegalArgumentException(
                    "Não é possível enviar mensagem privada para você mesmo. Escolha outro usuário.");
        }
        if (sala == null || sala.isBlank()) {
            throw new IllegalArgumentException("A mensagem precisa de uma sala.");
        }
        if (conteudo == null || conteudo.isBlank()) {
            throw new IllegalArgumentException("A mensagem está vazia. Digite um texto para enviar.");
        }
        String limpo = conteudo.strip();
        if (limpo.length() > LIMITE_CONTEUDO) {
            throw new IllegalArgumentException("A mensagem tem " + limpo.length()
                    + " caracteres e o limite é " + LIMITE_CONTEUDO + ". Encurte o texto.");
        }
        if (horario == null) {
            throw new IllegalArgumentException("A mensagem precisa de um horário.");
        }
        this.id = id;
        this.remetente = remetente;
        this.destinatario = destinatario;
        this.sala = sala;
        this.conteudo = limpo;
        this.horario = horario;
    }

    /** Cria uma mensagem pública ainda sem id. */
    public static Mensagem nova(Usuario remetente, String sala, String conteudo,
            LocalDateTime horario) {
        return new Mensagem(null, remetente, null, sala, conteudo, horario);
    }

    /** Cria uma mensagem pública com o horário lido do relógio informado. */
    public static Mensagem nova(Usuario remetente, String sala, String conteudo, Clock relogio) {
        return nova(remetente, sala, conteudo, LocalDateTime.now(relogio));
    }

    /** Cria uma mensagem privada ainda sem id. */
    public static Mensagem privada(Usuario remetente, Usuario destinatario, String sala,
            String conteudo, LocalDateTime horario) {
        if (destinatario == null) {
            throw new IllegalArgumentException("A mensagem privada precisa de um destinatário.");
        }
        return new Mensagem(null, remetente, destinatario, sala, conteudo, horario);
    }

    /** Recria uma mensagem já persistida, com o id gerado pelo banco. */
    public static Mensagem restaurada(long id, Usuario remetente, Usuario destinatario,
            String sala, String conteudo, LocalDateTime horario) {
        return new Mensagem(id, remetente, destinatario, sala, conteudo, horario);
    }

    /** Devolve uma cópia desta mensagem com o id informado. */
    public Mensagem comId(long novoId) {
        return new Mensagem(novoId, remetente, destinatario, sala, conteudo, horario);
    }

    /** Id gerado na persistência; {@code null} se ainda não foi salva. */
    public Long id() {
        return id;
    }

    /** Quem enviou. */
    public Usuario remetente() {
        return remetente;
    }

    /** Destinatário, vazio quando a mensagem é pública. */
    public Optional<Usuario> destinatario() {
        return Optional.ofNullable(destinatario);
    }

    /** Sala em que a mensagem foi enviada. */
    public String sala() {
        return sala;
    }

    /** Texto, sem espaços nas pontas. */
    public String conteudo() {
        return conteudo;
    }

    /** Momento do envio. */
    public LocalDateTime horario() {
        return horario;
    }

    /** Indica se a mensagem tem destinatário. */
    public boolean ehPrivada() {
        return destinatario != null;
    }

    /** Formato padrão de exibição: {@code [HH:mm] nome: texto}. */
    public String formatar() {
        return "[" + horario.format(HORA_MINUTO) + "] " + remetente.nome() + ": " + conteudo;
    }

    @Override
    public boolean equals(Object outro) {
        return outro instanceof Mensagem m
                && Objects.equals(id, m.id)
                && remetente.equals(m.remetente)
                && Objects.equals(destinatario, m.destinatario)
                && sala.equals(m.sala)
                && conteudo.equals(m.conteudo)
                && horario.equals(m.horario);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, remetente, destinatario, sala, conteudo, horario);
    }

    @Override
    public String toString() {
        return formatar();
    }
}
