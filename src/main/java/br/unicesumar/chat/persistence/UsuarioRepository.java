package br.unicesumar.chat.persistence;

import br.unicesumar.chat.model.CredencialUsuario;
import java.util.Optional;

/** Contrato de persistência das credenciais de usuários. */
public interface UsuarioRepository {

    /**
     * Cadastra a credencial de forma atômica.
     *
     * @return {@code true} se cadastrou; {@code false} se o nome já existe
     *         (sem diferenciar maiúsculas)
     * @throws RepositorioException se a gravação falhar
     */
    boolean cadastrar(CredencialUsuario credencial);

    /** Busca pelo nome, sem diferenciar maiúsculas; vazio se não existir. */
    Optional<CredencialUsuario> buscarPorNome(String nome);

    /** Indica se já existe usuário com o nome, sem diferenciar maiúsculas. */
    boolean existe(String nome);
}
