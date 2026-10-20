package br.unicesumar.chat.model;

/**
 * Dados de acesso guardados para um usuário: nunca a senha, só o hash e o salt.
 *
 * @param usuario   dono da credencial
 * @param senhaHash hash da senha (texto codificado)
 * @param salt      sal aleatório usado no hash (texto codificado)
 */
public record CredencialUsuario(Usuario usuario, String senhaHash, String salt) {

    /** Valida que nenhum campo é nulo ou vazio. */
    public CredencialUsuario {
        if (usuario == null) {
            throw new IllegalArgumentException("A credencial precisa de um usuário.");
        }
        if (senhaHash == null || senhaHash.isBlank()) {
            throw new IllegalArgumentException("A credencial precisa do hash da senha.");
        }
        if (salt == null || salt.isBlank()) {
            throw new IllegalArgumentException("A credencial precisa do salt.");
        }
    }
}
