package br.unicesumar.chat.persistence;

import java.util.List;

/** Contrato de persistência dos nomes banidos. Não conhece JDBC nem rede. A comparação ignora maiúsculas. */
public interface BanimentoRepository {

    /**
     * Bane o nome.
     *
     * @return {@code true} se baniu agora; {@code false} se já estava banido
     * @throws IllegalArgumentException se o nome for nulo ou em branco
     * @throws RepositorioException se a gravação falhar
     */
    boolean adicionar(String nome);

    /**
     * Tira o banimento.
     *
     * @return {@code true} se removeu; {@code false} se o nome não estava banido
     * @throws RepositorioException se a operação falhar
     */
    boolean remover(String nome);

    /** Indica se o nome está banido; nome nulo ou em branco nunca está. */
    boolean existe(String nome);

    /** Nomes banidos em ordem alfabética (sem diferenciar maiúsculas). */
    List<String> listar();
}
