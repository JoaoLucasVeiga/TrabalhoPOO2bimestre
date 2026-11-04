package br.unicesumar.chat.persistence.memory;

import br.unicesumar.chat.persistence.BanimentoRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Banimentos em memória, thread-safe, usados nos testes unitários. */
public class BanimentoRepositoryEmMemoria implements BanimentoRepository {

    private final Map<String, String> porChave = new ConcurrentHashMap<>();

    private static String chave(String nome) {
        return nome.strip().toLowerCase(Locale.ROOT);
    }

    /** {@inheritDoc} Usa {@code putIfAbsent}, que é atômico. */
    @Override
    public boolean adicionar(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Informe o nome a banir.");
        }
        return porChave.putIfAbsent(chave(nome), nome.strip()) == null;
    }

    /** {@inheritDoc} */
    @Override
    public boolean remover(String nome) {
        return nome != null && !nome.isBlank() && porChave.remove(chave(nome)) != null;
    }

    /** {@inheritDoc} */
    @Override
    public boolean existe(String nome) {
        return nome != null && !nome.isBlank() && porChave.containsKey(chave(nome));
    }

    /** {@inheritDoc} */
    @Override
    public List<String> listar() {
        List<String> nomes = new ArrayList<>(porChave.values());
        nomes.sort(String.CASE_INSENSITIVE_ORDER);
        return nomes;
    }
}
