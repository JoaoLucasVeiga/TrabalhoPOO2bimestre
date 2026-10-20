package br.unicesumar.chat.persistence.jdbc;

import br.unicesumar.chat.persistence.RepositorioException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sqlite.SQLiteConfig;

/**
 * Cria conexões JDBC com o SQLite. Cada chamada a {@link #abrir()} devolve uma
 * conexão nova, que o chamador fecha com try-with-resources: conexões nunca são
 * compartilhadas entre threads.
 */
public class ConnectionFactory {

    private static final Logger LOG = Logger.getLogger(ConnectionFactory.class.getName());
    private static final String SCRIPT = "/sql/criar_tabelas.sql";

    private final String url;
    private final SQLiteConfig config;

    private ConnectionFactory(String url) {
        this.url = url;
        this.config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setBusyTimeout(5000);
    }

    /** Cria uma fábrica para o arquivo de banco informado (ex.: {@code chat.db}). */
    public static ConnectionFactory arquivo(Path arquivo) {
        return new ConnectionFactory("jdbc:sqlite:" + arquivo.toAbsolutePath());
    }

    /**
     * Abre uma conexão nova com WAL e busy_timeout de 5 segundos.
     *
     * @throws RepositorioException se o banco não puder ser aberto
     */
    public Connection abrir() {
        try {
            return config.createConnection(url);
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Não foi possível abrir o banco: " + e.getMessage(), e);
            throw new RepositorioException(
                    "Não foi possível abrir o banco de dados. Verifique se a pasta existe e "
                            + "se há permissão de escrita.", e);
        }
    }

    /** Cria as tabelas (se não existirem) executando {@code sql/criar_tabelas.sql}. */
    public void criarTabelas() {
        String script = lerScript().lines()
                .filter(l -> !l.strip().startsWith("--"))
                .reduce("", (a, b) -> a + b + "\n");
        try (Connection c = abrir(); Statement st = c.createStatement()) {
            for (String comando : script.split(";")) {
                if (!comando.isBlank()) {
                    st.execute(comando.strip());
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Falha ao criar as tabelas: " + e.getMessage(), e);
            throw new RepositorioException("Falha ao criar as tabelas do banco.", e);
        }
    }

    private String lerScript() {
        try (InputStream in = ConnectionFactory.class.getResourceAsStream(SCRIPT)) {
            if (in == null) {
                throw new RepositorioException("Script " + SCRIPT + " não encontrado no classpath.",
                        null);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Falha ao ler o script SQL: " + e.getMessage(), e);
            throw new RepositorioException("Falha ao ler o script SQL.", e);
        }
    }
}
