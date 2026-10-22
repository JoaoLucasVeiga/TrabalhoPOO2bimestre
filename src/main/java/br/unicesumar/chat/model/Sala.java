package br.unicesumar.chat.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Raiz do agregado: uma sala com suas mensagens públicas. Imutável; adicionar
 * uma mensagem devolve uma nova sala.
 */
public final class Sala {

    /** Nome da sala em que todos entram ao conectar. */
    public static final String PADRAO = "geral";

    private static final Pattern NOME_VALIDO = Pattern.compile("[a-z0-9_]{1,20}");

    private final String nome;
    private final List<Mensagem> mensagens;

    /**
     * Cria uma sala vazia.
     *
     * @param nome de 1 a 20 caracteres (letras sem acento, números, '_'); vira minúsculo
     */
    public Sala(String nome) {
        this(normalizar(nome), List.of());
    }

    private Sala(String nome, List<Mensagem> mensagens) {
        this.nome = nome;
        this.mensagens = List.copyOf(mensagens);
    }

    private static String normalizar(String nome) {
        String minusculo = nome == null ? null : nome.toLowerCase(Locale.ROOT);
        if (minusculo == null || !NOME_VALIDO.matcher(minusculo).matches()) {
            throw new IllegalArgumentException("Nome de sala inválido. Use de 1 a 20 caracteres: "
                    + "letras sem acento, números ou '_', sem espaços.");
        }
        return minusculo;
    }

    /** Devolve a sala padrão ("geral"), vazia. */
    public static Sala padrao() {
        return new Sala(PADRAO);
    }

    /** Nome da sala, em minúsculas. */
    public String nome() {
        return nome;
    }

    /** Mensagens da sala, em ordem cronológica; lista imutável. */
    public List<Mensagem> mensagens() {
        return mensagens;
    }

    /**
     * Devolve uma nova sala com a mensagem acrescentada.
     *
     * @throws IllegalArgumentException se a mensagem for privada ou de outra sala
     */
    public Sala adicionar(Mensagem mensagem) {
        if (mensagem.ehPrivada()) {
            throw new IllegalArgumentException(
                    "Mensagem privada não pertence a uma sala e não pode ser adicionada a ela.");
        }
        if (!nome.equals(mensagem.sala())) {
            throw new IllegalArgumentException("A mensagem é da sala '" + mensagem.sala()
                    + "', não da sala '" + nome + "'.");
        }
        List<Mensagem> novas = new ArrayList<>(mensagens);
        novas.add(mensagem);
        return new Sala(nome, novas);
    }

    /**
     * Devolve as últimas mensagens da sala, da mais antiga para a mais recente.
     *
     * @param quantidade quantas devolver; zero devolve lista vazia
     * @throws IllegalArgumentException se for negativa
     */
    public List<Mensagem> ultimas(int quantidade) {
        if (quantidade < 0) {
            throw new IllegalArgumentException("A quantidade não pode ser negativa.");
        }
        int inicio = Math.max(0, mensagens.size() - quantidade);
        return mensagens.subList(inicio, mensagens.size());
    }

    /** Salas são iguais quando têm o mesmo nome. */
    @Override
    public boolean equals(Object outro) {
        return outro instanceof Sala s && nome.equals(s.nome);
    }

    @Override
    public int hashCode() {
        return nome.hashCode();
    }

    @Override
    public String toString() {
        return nome;
    }
}
