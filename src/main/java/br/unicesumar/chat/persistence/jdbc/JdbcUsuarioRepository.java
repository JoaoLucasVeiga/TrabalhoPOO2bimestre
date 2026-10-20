package br.unicesumar.chat.persistence.jdbc;

import br.unicesumar.chat.model.CredencialUsuario;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.RepositorioException;
import br.unicesumar.chat.persistence.UsuarioRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Implementação JDBC (SQLite) de {@link UsuarioRepository}. Todo SQL de usuários fica aqui. */
public class JdbcUsuarioRepository implements UsuarioRepository {

    private static final Logger LOG = Logger.getLogger(JdbcUsuarioRepository.class.getName());

    // OR IGNORE + UNIQUE COLLATE NOCASE: a checagem e a inserção são uma única operação atômica.
    private static final String INSERIR =
            "INSERT OR IGNORE INTO usuario (nome, senha_hash, salt) VALUES (?, ?, ?)";
    private static final String BUSCAR =
            "SELECT nome, senha_hash, salt FROM usuario WHERE nome = ? COLLATE NOCASE";

    private final ConnectionFactory fabrica;

    /** Recebe a fábrica de conexões (injeção de dependência). */
    public JdbcUsuarioRepository(ConnectionFactory fabrica) {
        this.fabrica = Objects.requireNonNull(fabrica, "fabrica");
    }

    /** {@inheritDoc} */
    @Override
    public boolean cadastrar(CredencialUsuario credencial) {
        Objects.requireNonNull(credencial, "credencial");
        try (Connection c = fabrica.abrir(); PreparedStatement ps = c.prepareStatement(INSERIR)) {
            ps.setString(1, credencial.usuario().nome());
            ps.setString(2, credencial.senhaHash());
            ps.setString(3, credencial.salt());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Falha ao cadastrar usuário: " + e.getMessage(), e);
            throw new RepositorioException(
                    "Não foi possível cadastrar o usuário. Tente novamente em instantes.", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Optional<CredencialUsuario> buscarPorNome(String nome) {
        if (nome == null) {
            return Optional.empty();
        }
        try (Connection c = fabrica.abrir(); PreparedStatement ps = c.prepareStatement(BUSCAR)) {
            ps.setString(1, nome);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new CredencialUsuario(new Usuario(rs.getString("nome")),
                        rs.getString("senha_hash"), rs.getString("salt")));
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Falha ao buscar usuário: " + e.getMessage(), e);
            throw new RepositorioException(
                    "Não foi possível consultar o usuário. Tente novamente em instantes.", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean existe(String nome) {
        return buscarPorNome(nome).isPresent();
    }
}
