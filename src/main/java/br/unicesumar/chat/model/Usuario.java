package br.unicesumar.chat.model;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Entidade que representa um usuário do chat. A identidade é o nome, comparado
 * sem diferenciar maiúsculas de minúsculas. Imutável.
 */
public final class Usuario {

    /** Tamanho mínimo do nome. */
    public static final int NOME_MINIMO = 3;
    /** Tamanho máximo do nome. */
    public static final int NOME_MAXIMO = 20;

    private static final Pattern NOME_VALIDO = Pattern.compile("[A-Za-z0-9_]{3,20}");

    private final String nome;

    /**
     * Cria um usuário.
     *
     * @param nome de 3 a 20 caracteres, só letras sem acento, números e '_'
     * @throws IllegalArgumentException se o nome for inválido
     */
    public Usuario(String nome) {
        if (nome == null || !NOME_VALIDO.matcher(nome).matches()) {
            throw new IllegalArgumentException("Nome de usuário inválido. Use de " + NOME_MINIMO
                    + " a " + NOME_MAXIMO + " caracteres: letras sem acento, números ou '_'.");
        }
        this.nome = nome;
    }

    /** Devolve o nome com as maiúsculas originais. */
    public String nome() {
        return nome;
    }

    /** Dois usuários são iguais quando os nomes coincidem ignorando maiúsculas. */
    @Override
    public boolean equals(Object outro) {
        return outro instanceof Usuario u && nome.equalsIgnoreCase(u.nome);
    }

    @Override
    public int hashCode() {
        return nome.toLowerCase(Locale.ROOT).hashCode();
    }

    @Override
    public String toString() {
        return nome;
    }
}
