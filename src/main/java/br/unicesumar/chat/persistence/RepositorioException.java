package br.unicesumar.chat.persistence;

/** Falha de infraestrutura ao acessar o armazenamento (ex.: banco indisponível). */
public class RepositorioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Cria a exceção com a mensagem e a causa original. */
    public RepositorioException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
