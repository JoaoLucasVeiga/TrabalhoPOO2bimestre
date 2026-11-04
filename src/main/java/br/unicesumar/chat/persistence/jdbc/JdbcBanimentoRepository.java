package br.unicesumar.chat.persistence.jdbc;

import br.unicesumar.chat.persistence.BanimentoRepository;
import br.unicesumar.chat.persistence.RepositorioException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Implementação JDBC (SQLite) de {@link BanimentoRepository}. Todo SQL de banimentos fica aqui. */
public class JdbcBanimentoRepository implements BanimentoRepository {

    private static final Logger LOG = Logger.getLogger(JdbcBanimentoRepository.class.getName());

    // OR IGNORE + PRIMARY KEY COLLATE NOCASE: verificar e inserir é uma única operação atômica.
    private static final String INSERIR =
            "INSERT OR IGNORE INTO banimento (nome, banido_em) VALUES (?, datetime('now'))";
    private static final String REMOVER = "DELETE FROM banimento WHERE nome = ? COLLATE NOCASE";
    private static final String EXISTE = "SELECT 1 FROM banimento WHERE nome = ? COLLATE NOCASE";
    private static final String LISTAR = "SELECT nome FROM banimento ORDER BY nome COLLATE NOCASE";

    private final ConnectionFactory fabrica;

    /** Recebe a fábrica de conexões (injeção de dependência). */
    public JdbcBanimentoRepository(ConnectionFactory fabrica) {
        this.fabrica = Objects.requireNonNull(fabrica, "fabrica");
    }

    /** {@inheritDoc} */
    @Override
    public boolean adicionar(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Informe o nome a banir.");
        }
        try (Connection c = fabrica.abrir(); PreparedStatement ps = c.prepareStatement(INSERIR)) {
            ps.setString(1, nome.strip());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Falha ao banir: " + e.getMessage(), e);
            throw new RepositorioException("Não foi possível banir o usuário. Tente novamente em instantes.", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean remover(String nome) {
        if (nome == null || nome.isBlank()) {
            return false;
        }
        try (Connection c = fabrica.abrir(); PreparedStatement ps = c.prepareStatement(REMOVER)) {
            ps.setString(1, nome.strip());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Falha ao desbanir: " + e.getMessage(), e);
            throw new RepositorioException(
                    "Não foi possível tirar o banimento. Tente novamente em instantes.", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean existe(String nome) {
        if (nome == null || nome.isBlank()) {
            return false;
        }
        try (Connection c = fabrica.abrir(); PreparedStatement ps = c.prepareStatement(EXISTE)) {
            ps.setString(1, nome.strip());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Falha ao consultar banimento: " + e.getMessage(), e);
            throw new RepositorioException(
                    "Não foi possível consultar os banimentos. Tente novamente em instantes.", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<String> listar() {
        try (Connection c = fabrica.abrir();
                PreparedStatement ps = c.prepareStatement(LISTAR);
                ResultSet rs = ps.executeQuery()) {
            List<String> nomes = new ArrayList<>();
            while (rs.next()) {
                nomes.add(rs.getString("nome"));
            }
            return nomes;
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Falha ao listar banimentos: " + e.getMessage(), e);
            throw new RepositorioException(
                    "Não foi possível listar os banimentos. Tente novamente em instantes.", e);
        }
    }
}
