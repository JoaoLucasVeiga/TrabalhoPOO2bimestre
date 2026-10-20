package br.unicesumar.chat.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Teste de INTEGRAÇÃO: abre SQLite real em arquivo temporário. */
@Tag("integracao")
class ConnectionFactoryIntegracaoTest {

    @TempDir
    Path pasta;

    @Test
    void criaAsTabelasUsuarioEMensagem() throws SQLException {
        ConnectionFactory fabrica = ConnectionFactory.arquivo(pasta.resolve("a.db"));
        fabrica.criarTabelas();
        try (Connection c = fabrica.abrir();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' "
                                + "AND name IN ('usuario','mensagem')")) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1));
        }
    }

    @Test
    void criarTabelasDuasVezesNaoFalha() {
        ConnectionFactory fabrica = ConnectionFactory.arquivo(pasta.resolve("b.db"));
        fabrica.criarTabelas();
        assertDoesNotThrow(fabrica::criarTabelas);
    }

    @Test
    void configuraModoWalEBusyTimeout() throws SQLException {
        ConnectionFactory fabrica = ConnectionFactory.arquivo(pasta.resolve("c.db"));
        try (Connection c = fabrica.abrir(); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("PRAGMA journal_mode")) {
                assertTrue(rs.next());
                assertEquals("wal", rs.getString(1));
            }
            try (ResultSet rs = st.executeQuery("PRAGMA busy_timeout")) {
                assertTrue(rs.next());
                assertEquals(5000, rs.getInt(1));
            }
        }
    }

    @Test
    void cadaChamadaDevolveUmaConexaoDiferente() throws SQLException {
        ConnectionFactory fabrica = ConnectionFactory.arquivo(pasta.resolve("d.db"));
        try (Connection c1 = fabrica.abrir(); Connection c2 = fabrica.abrir()) {
            assertNotSame(c1, c2);
        }
    }

    @Test
    void falhaAoAbrirEmPastaInexistenteLancaRepositorioException() {
        ConnectionFactory fabrica =
                ConnectionFactory.arquivo(pasta.resolve("nao/existe/x.db"));
        assertThrows(br.unicesumar.chat.persistence.RepositorioException.class, fabrica::abrir);
    }
}
