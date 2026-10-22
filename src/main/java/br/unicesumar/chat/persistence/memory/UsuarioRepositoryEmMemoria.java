package br.unicesumar.chat.persistence.memory;

import br.unicesumar.chat.model.CredencialUsuario;
import br.unicesumar.chat.persistence.UsuarioRepository;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Repositório de usuários em memória, thread-safe, usado nos testes unitários. */
public class UsuarioRepositoryEmMemoria implements UsuarioRepository {

    private final Map<String, CredencialUsuario> porNome = new ConcurrentHashMap<>();

    private static String chave(String nome) {
        return nome.toLowerCase(Locale.ROOT);
    }

    /** {@inheritDoc} Usa {@code putIfAbsent}, que é atômico. */
    @Override
    public boolean cadastrar(CredencialUsuario credencial) {
        Objects.requireNonNull(credencial, "credencial");
        return porNome.putIfAbsent(chave(credencial.usuario().nome()), credencial) == null;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<CredencialUsuario> buscarPorNome(String nome) {
        return nome == null ? Optional.empty() : Optional.ofNullable(porNome.get(chave(nome)));
    }

    /** {@inheritDoc} */
    @Override
    public boolean existe(String nome) {
        return buscarPorNome(nome).isPresent();
    }
}
