package br.unicesumar.chat.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Índices do SQLite: as consultas de conversa privada não podem varrer a tabela inteira. */
@Tag("integracao")
class IndicesDoBancoIntegracaoTest {

    @TempDir
    Path pasta;

    private ConnectionFactory fabrica() {
        ConnectionFactory f = ConnectionFactory.arquivo(pasta.resolve("indices.db"));
        f.criarTabelas();
        return f;
    }

    private List<String> plano(String sql, String... parametros) throws SQLException {
        try (Connection c = fabrica().abrir(); PreparedStatement ps = c.prepareStatement("EXPLAIN QUERY PLAN " + sql)) {
            for (int i = 0; i < parametros.length; i++) {
                ps.setString(i + 1, parametros[i]);
            }
            List<String> linhas = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    linhas.add(rs.getString("detail"));
                }
            }
            return linhas;
        }
    }

    @Test
    void existeIndiceParaAsConversasPrivadas() throws SQLException {
        try (Connection c = fabrica().abrir(); PreparedStatement ps = c.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'idx_mensagem_conversa'");
                ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next(), "falta o índice idx_mensagem_conversa");
        }
    }

    @Test
    void consultaDeConversaUsaOIndiceEmVezDeVarrerATabela() throws SQLException {
        List<String> linhas = plano("SELECT id FROM mensagem WHERE destinatario IS NOT NULL "
                + "AND remetente = ? COLLATE NOCASE AND destinatario = ? COLLATE NOCASE ORDER BY id DESC LIMIT 50",
                "ana", "bia");
        assertTrue(linhas.stream().anyMatch(l -> l.contains("idx_mensagem_conversa")),
                "plano sem o índice: " + linhas);
        assertTrue(linhas.stream().noneMatch(l -> l.startsWith("SCAN mensagem")), "varreu a tabela: " + linhas);
    }

    @Test
    void historicoDaSalaContinuaUsandoOIndiceDaSala() throws SQLException {
        List<String> linhas = plano("SELECT id FROM mensagem WHERE sala = ? AND destinatario IS NULL "
                + "ORDER BY id DESC LIMIT 20", "geral");
        assertTrue(linhas.stream().anyMatch(l -> l.contains("idx_mensagem_sala")), "plano: " + linhas);
    }

    @Test
    void criarAsTabelasDeNovoNaoQuebraNemDuplicaOsIndices() {
        ConnectionFactory f = fabrica();
        assertDoesNotThrow(f::criarTabelas);
    }
}
