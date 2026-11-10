package br.unicesumar.chat.server;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Tabela dos comandos disponíveis, na ordem em que foram registrados. É montada uma vez, no
 * construtor do {@link CommandService}, e depois só é lida, por isso pode ser usada por várias threads.
 */
final class TabelaDeComandos {

    /** Um comando registrado: nome, texto de uso, descrição para o {@code /help} e o código a executar. */
    record Entrada(String nome, String uso, String descricao, Comando comando) {
    }

    private final Map<String, Entrada> entradas = new LinkedHashMap<>();

    /**
     * Registra um comando.
     *
     * @throws IllegalStateException se já existe um comando com esse nome (erro de programação)
     */
    void registrar(String nome, String uso, String descricao, Comando comando) {
        Objects.requireNonNull(nome, "nome");
        Objects.requireNonNull(uso, "uso");
        Objects.requireNonNull(descricao, "descricao");
        Objects.requireNonNull(comando, "comando");
        String chave = nome.toLowerCase(Locale.ROOT);
        if (entradas.containsKey(chave)) {
            throw new IllegalStateException("O comando '" + chave + "' já foi registrado.");
        }
        entradas.put(chave, new Entrada(chave, uso, descricao, comando));
    }

    /** Procura o comando pelo nome, sem diferenciar maiúsculas. */
    Optional<Entrada> buscar(String nome) {
        return Optional.ofNullable(entradas.get(nome.toLowerCase(Locale.ROOT)));
    }

    /** Todos os comandos, na ordem de registro; a coleção não pode ser alterada. */
    Collection<Entrada> todas() {
        return Collections.unmodifiableCollection(entradas.values());
    }
}
