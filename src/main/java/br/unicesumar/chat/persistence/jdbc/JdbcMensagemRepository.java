package br.unicesumar.chat.persistence.jdbc;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.RepositorioException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Implementação JDBC (SQLite) de {@link MensagemRepository}. Todo SQL de mensagens fica aqui. */
public class JdbcMensagemRepository implements MensagemRepository {

    private static final Logger LOG = Logger.getLogger(JdbcMensagemRepository.class.getName());
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final String INSERIR =
            "INSERT INTO mensagem (remetente, destinatario, sala, conteudo, horario) "
                    + "VALUES (?, ?, ?, ?, ?)";
    private static final String ULTIMAS_PUBLICAS =
            "SELECT id, remetente, destinatario, sala, conteudo, horario FROM ("
                    + "SELECT * FROM mensagem WHERE sala = ? AND destinatario IS NULL "
                    + "ORDER BY id DESC LIMIT ?) ORDER BY id ASC";

    private final ConnectionFactory fabrica;

    /** Recebe a fábrica de conexões (injeção de dependência). */
    public JdbcMensagemRepository(ConnectionFactory fabrica) {
        this.fabrica = Objects.requireNonNull(fabrica, "fabrica");
    }

    /** {@inheritDoc} Abre e fecha uma conexão própria nesta chamada. */
    @Override
    public Mensagem salvar(Mensagem mensagem) {
        Objects.requireNonNull(mensagem, "mensagem");
        try (Connection c = fabrica.abrir();
                PreparedStatement ps = c.prepareStatement(INSERIR, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, mensagem.remetente().nome());
            ps.setString(2, mensagem.destinatario().map(Usuario::nome).orElse(null));
            ps.setString(3, mensagem.sala());
            ps.setString(4, mensagem.conteudo());
            ps.setString(5, mensagem.horario().format(ISO));
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                if (!chaves.next()) {
                    throw new RepositorioException("O banco não devolveu o id da mensagem.", null);
                }
                return mensagem.comId(chaves.getLong(1));
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Falha ao salvar mensagem: " + e.getMessage(), e);
            throw new RepositorioException(
                    "Não foi possível salvar a mensagem. Tente novamente em instantes.", e);
        }
    }

    /** {@inheritDoc} Filtra {@code destinatario IS NULL}: privadas nunca aparecem. */
    @Override
    public List<Mensagem> buscarUltimas(String sala, int quantidade) {
        if (quantidade < 0) {
            throw new IllegalArgumentException("A quantidade não pode ser negativa.");
        }
        try (Connection c = fabrica.abrir();
                PreparedStatement ps = c.prepareStatement(ULTIMAS_PUBLICAS)) {
            ps.setString(1, sala);
            ps.setInt(2, quantidade);
            try (ResultSet rs = ps.executeQuery()) {
                List<Mensagem> resultado = new ArrayList<>();
                while (rs.next()) {
                    resultado.add(converter(rs));
                }
                return resultado;
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Falha ao buscar mensagens: " + e.getMessage(), e);
            throw new RepositorioException(
                    "Não foi possível carregar o histórico. Tente novamente em instantes.", e);
        }
    }

    private static Mensagem converter(ResultSet rs) throws SQLException {
        String destinatario = rs.getString("destinatario");
        return Mensagem.restaurada(
                rs.getLong("id"),
                new Usuario(rs.getString("remetente")),
                destinatario == null ? null : new Usuario(destinatario),
                rs.getString("sala"),
                rs.getString("conteudo"),
                LocalDateTime.parse(rs.getString("horario"), ISO));
    }
}
