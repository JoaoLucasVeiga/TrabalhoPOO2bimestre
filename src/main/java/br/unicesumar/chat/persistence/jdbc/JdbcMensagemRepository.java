package br.unicesumar.chat.persistence.jdbc;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.RepositorioException;
import br.unicesumar.chat.persistence.ValidacaoBusca;
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
    private static final String APAGAR_SALA =
            "DELETE FROM mensagem WHERE sala = ? AND destinatario IS NULL";
    private static final String ULTIMAS_PUBLICAS =
            "SELECT id, remetente, destinatario, sala, conteudo, horario FROM ("
                    + "SELECT * FROM mensagem WHERE sala = ? AND destinatario IS NULL "
                    + "ORDER BY id DESC LIMIT ?) ORDER BY id ASC";

    // Conversa privada entre dois usuários, nos dois sentidos, em ordem de gravação.
    private static final String CONVERSA =
            "SELECT id, remetente, destinatario, sala, conteudo, horario FROM ("
                    + "SELECT * FROM mensagem WHERE destinatario IS NOT NULL AND "
                    + "((remetente = ? COLLATE NOCASE AND destinatario = ? COLLATE NOCASE) OR "
                    + "(remetente = ? COLLATE NOCASE AND destinatario = ? COLLATE NOCASE)) "
                    + "ORDER BY id DESC LIMIT ?) ORDER BY id ASC";
    // Um interlocutor por linha, da conversa mais recente para a mais antiga.
    private static final String CONVERSAS =
            "SELECT contraparte FROM ("
                    + "SELECT contraparte, MAX(id) AS ultimo FROM ("
                    + "SELECT id, CASE WHEN remetente = ? COLLATE NOCASE THEN destinatario ELSE remetente END "
                    + "AS contraparte FROM mensagem WHERE destinatario IS NOT NULL "
                    + "AND (remetente = ? COLLATE NOCASE OR destinatario = ? COLLATE NOCASE)"
                    + ") GROUP BY lower(contraparte)) ORDER BY ultimo DESC";

    // Cada busca é um SQL fixo (nada é concatenado); os valores entram só por parâmetros.
    private static final String POR_USUARIO =
            "SELECT id, remetente, destinatario, sala, conteudo, horario FROM ("
                    + "SELECT * FROM mensagem WHERE sala = ? AND destinatario IS NULL "
                    + "AND remetente = ? COLLATE NOCASE ORDER BY horario DESC, id DESC LIMIT ?) "
                    + "ORDER BY horario ASC, id ASC";
    private static final String POR_TEXTO =
            "SELECT id, remetente, destinatario, sala, conteudo, horario FROM ("
                    + "SELECT * FROM mensagem WHERE sala = ? AND destinatario IS NULL "
                    + "AND instr(lower(conteudo), lower(?)) > 0 ORDER BY horario DESC, id DESC LIMIT ?) "
                    + "ORDER BY horario ASC, id ASC";
    private static final String POR_PERIODO =
            "SELECT id, remetente, destinatario, sala, conteudo, horario FROM ("
                    + "SELECT * FROM mensagem WHERE sala = ? AND destinatario IS NULL "
                    + "AND horario >= ? AND horario <= ? ORDER BY horario DESC, id DESC LIMIT ?) "
                    + "ORDER BY horario ASC, id ASC";

    @FunctionalInterface
    private interface Parametros {
        void definir(PreparedStatement ps) throws SQLException;
    }

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

    /** {@inheritDoc} Uma conexão e uma transação para o lote inteiro: um só {@code commit} (um só fsync). */
    @Override
    public List<Mensagem> salvarTodas(List<Mensagem> mensagens) {
        Objects.requireNonNull(mensagens, "mensagens");
        mensagens.forEach(m -> Objects.requireNonNull(m, "mensagem"));
        if (mensagens.isEmpty()) {
            return List.of();
        }
        try (Connection c = fabrica.abrir()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(INSERIR, Statement.RETURN_GENERATED_KEYS)) {
                List<Mensagem> salvas = new ArrayList<>(mensagens.size());
                for (Mensagem m : mensagens) {
                    ps.setString(1, m.remetente().nome());
                    ps.setString(2, m.destinatario().map(Usuario::nome).orElse(null));
                    ps.setString(3, m.sala());
                    ps.setString(4, m.conteudo());
                    ps.setString(5, m.horario().format(ISO));
                    ps.executeUpdate();
                    try (ResultSet chaves = ps.getGeneratedKeys()) {
                        if (!chaves.next()) {
                            throw new RepositorioException("O banco não devolveu o id da mensagem.", null);
                        }
                        salvas.add(m.comId(chaves.getLong(1)));
                    }
                }
                c.commit();
                return salvas;
            } catch (SQLException | RuntimeException e) {
                c.rollback(); // o lote é tudo ou nada
                throw e;
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Falha ao salvar o lote de mensagens: " + e.getMessage(), e);
            throw new RepositorioException(
                    "Não foi possível salvar as mensagens. Tente novamente em instantes.", e);
        }
    }

    /** {@inheritDoc} Só apaga as públicas ({@code destinatario IS NULL}). */
    @Override
    public int apagarSala(String sala) {
        if (sala == null || sala.isBlank()) {
            throw new IllegalArgumentException("Informe a sala a limpar.");
        }
        try (Connection c = fabrica.abrir();
                PreparedStatement ps = c.prepareStatement(APAGAR_SALA)) {
            ps.setString(1, sala);
            return ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Falha ao apagar o histórico da sala: " + e.getMessage(), e);
            throw new RepositorioException(
                    "Não foi possível apagar o histórico da sala. Tente novamente em instantes.", e);
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

    /** {@inheritDoc} */
    @Override
    public List<Mensagem> buscarPorUsuario(String sala, String remetente, int limite) {
        ValidacaoBusca.exigirSala(sala);
        ValidacaoBusca.exigirTexto(remetente, "O nome do usuário");
        ValidacaoBusca.exigirLimite(limite);
        return consultar(POR_USUARIO, ps -> {
            ps.setString(1, sala);
            ps.setString(2, remetente);
            ps.setInt(3, limite);
        });
    }

    /** {@inheritDoc} {@code instr} faz a comparação literal: {@code %} e {@code _} não são curingas. */
    @Override
    public List<Mensagem> buscarPorTexto(String sala, String trecho, int limite) {
        ValidacaoBusca.exigirSala(sala);
        ValidacaoBusca.exigirTexto(trecho, "O trecho de texto");
        ValidacaoBusca.exigirLimite(limite);
        return consultar(POR_TEXTO, ps -> {
            ps.setString(1, sala);
            ps.setString(2, trecho);
            ps.setInt(3, limite);
        });
    }

    /** {@inheritDoc} O horário é gravado em ISO-8601, que ordena igual ao texto. */
    @Override
    public List<Mensagem> buscarPorPeriodo(String sala, LocalDateTime inicio, LocalDateTime fim,
            int limite) {
        ValidacaoBusca.exigirSala(sala);
        ValidacaoBusca.exigirPeriodo(inicio, fim);
        ValidacaoBusca.exigirLimite(limite);
        return consultar(POR_PERIODO, ps -> {
            ps.setString(1, sala);
            ps.setString(2, inicio.format(ISO));
            ps.setString(3, fim.format(ISO));
            ps.setInt(4, limite);
        });
    }

    private List<Mensagem> consultar(String sql, Parametros parametros) {
        try (Connection c = fabrica.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            parametros.definir(ps);
            try (ResultSet rs = ps.executeQuery()) {
                List<Mensagem> resultado = new ArrayList<>();
                while (rs.next()) {
                    resultado.add(converter(rs));
                }
                return resultado;
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Falha na busca de mensagens: " + e.getMessage(), e);
            throw new RepositorioException(
                    "Não foi possível fazer a busca. Tente novamente em instantes.", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<Mensagem> buscarConversa(String a, String b, int quantidade) {
        ValidacaoBusca.exigirTexto(a, "O primeiro usuário");
        ValidacaoBusca.exigirTexto(b, "O segundo usuário");
        if (quantidade < 0) {
            throw new IllegalArgumentException("A quantidade não pode ser negativa.");
        }
        return consultar(CONVERSA, ps -> {
            ps.setString(1, a);
            ps.setString(2, b);
            ps.setString(3, b);
            ps.setString(4, a);
            ps.setInt(5, quantidade);
        });
    }

    /** {@inheritDoc} */
    @Override
    public List<String> listarConversas(String usuario) {
        ValidacaoBusca.exigirTexto(usuario, "O usuário");
        try (Connection c = fabrica.abrir(); PreparedStatement ps = c.prepareStatement(CONVERSAS)) {
            ps.setString(1, usuario);
            ps.setString(2, usuario);
            ps.setString(3, usuario);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> nomes = new ArrayList<>();
                while (rs.next()) {
                    nomes.add(rs.getString(1));
                }
                return nomes;
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Falha ao listar conversas: " + e.getMessage(), e);
            throw new RepositorioException(
                    "Não foi possível listar as conversas. Tente novamente em instantes.", e);
        }
    }
}
