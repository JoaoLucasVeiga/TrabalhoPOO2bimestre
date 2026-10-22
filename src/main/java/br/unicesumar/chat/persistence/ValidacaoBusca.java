package br.unicesumar.chat.persistence;

import java.time.LocalDateTime;

/** Validações comuns às implementações de busca de {@link MensagemRepository}. */
public final class ValidacaoBusca {

    private ValidacaoBusca() {
    }

    /** A sala é obrigatória. */
    public static void exigirSala(String sala) {
        if (sala == null || sala.isBlank()) {
            throw new IllegalArgumentException("Informe a sala da busca.");
        }
    }

    /** O texto é obrigatório e não pode ser só espaços. */
    public static void exigirTexto(String valor, String descricao) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(descricao + " é obrigatório. Informe um valor para buscar.");
        }
    }

    /** O limite deve ser pelo menos 1. */
    public static void exigirLimite(int limite) {
        if (limite < 1) {
            throw new IllegalArgumentException("O limite de resultados deve ser pelo menos 1.");
        }
    }

    /** As duas datas são obrigatórias e o início não pode vir depois do fim. */
    public static void exigirPeriodo(LocalDateTime inicio, LocalDateTime fim) {
        if (inicio == null || fim == null) {
            throw new IllegalArgumentException("Informe o início e o fim do período.");
        }
        if (inicio.isAfter(fim)) {
            throw new IllegalArgumentException("O início do período não pode ser depois do fim.");
        }
    }
}
