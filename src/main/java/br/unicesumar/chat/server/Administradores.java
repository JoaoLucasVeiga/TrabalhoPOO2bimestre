package br.unicesumar.chat.server;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Quem pode usar os comandos de administração. Pode ser uma lista de nomes (sem diferenciar
 * maiúsculas) ou "sem restrição", usada nos testes e no modo sem login, em que todos podem tudo.
 */
public final class Administradores {

    private final Set<String> nomes;
    private final boolean restrito;

    private Administradores(Set<String> nomes, boolean restrito) {
        this.nomes = nomes;
        this.restrito = restrito;
    }

    /**
     * Só os nomes da lista são administradores.
     *
     * @throws NullPointerException se a lista ou algum nome for nulo
     */
    public static Administradores de(Collection<String> nomes) {
        Objects.requireNonNull(nomes, "nomes");
        Set<String> copia = new HashSet<>();
        for (String nome : nomes) {
            copia.add(normalizar(Objects.requireNonNull(nome, "nome")));
        }
        return new Administradores(Set.copyOf(copia), true);
    }

    /** Ninguém é administrador: os comandos de administração ficam bloqueados para todos. */
    public static Administradores nenhum() {
        return new Administradores(Set.of(), true);
    }

    /** Sem restrição: todos contam como administradores (testes e modo sem login). */
    public static Administradores semRestricao() {
        return new Administradores(Set.of(), false);
    }

    /** Indica se há restrição, isto é, se só alguns nomes são administradores. */
    public boolean restrito() {
        return restrito;
    }

    /** Indica se o nome é de um administrador; nome nulo ou em branco nunca é, quando há restrição. */
    public boolean ehAdmin(String nome) {
        if (!restrito) {
            return true;
        }
        return nome != null && !nome.isBlank() && nomes.contains(normalizar(nome));
    }

    private static String normalizar(String nome) {
        return nome.strip().toLowerCase(Locale.ROOT);
    }
}
