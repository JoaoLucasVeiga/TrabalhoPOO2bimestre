package br.unicesumar.chat.persistence.jdbc;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.MensagemRepositoryContrato;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Teste de INTEGRAÇÃO: usa SQLite real em arquivo temporário (não é teste unitário). */
@Tag("integracao")
class JdbcMensagemRepositoryIntegracaoTest extends MensagemRepositoryContrato {

    @TempDir
    Path pasta;

    private ConnectionFactory fabrica;

    @Override
    protected MensagemRepository criarRepositorio() {
        fabrica = ConnectionFactory.arquivo(pasta.resolve("teste-" + System.nanoTime() + ".db"));
        fabrica.criarTabelas();
        return new JdbcMensagemRepository(fabrica);
    }

    @Test
    void mensagemPrivadaFicaGravadaComODestinatario() throws SQLException {
        MensagemRepository repo = criarRepositorio();
        repo.salvar(Mensagem.privada(ANA, BIA, "geral", "segredo", HORA));
        try (Connection c = fabrica.abrir();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT destinatario, horario FROM mensagem WHERE conteudo = ?")) {
            ps.setString(1, "segredo");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("bia", rs.getString("destinatario"));
                assertEquals("2026-10-01T09:05:30", rs.getString("horario"));
            }
        }
    }

    @Test
    void mensagemPublicaFicaGravadaComDestinatarioNulo() throws SQLException {
        MensagemRepository repo = criarRepositorio();
        repo.salvar(publica("geral", "aberta"));
        try (Connection c = fabrica.abrir();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT destinatario FROM mensagem WHERE conteudo = ?")) {
            ps.setString(1, "aberta");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertNull(rs.getString("destinatario"));
            }
        }
    }

    @Test
    void historicoSobreviveAReabrirOBanco() {
        Path arquivo = pasta.resolve("persistente.db");
        ConnectionFactory primeira = ConnectionFactory.arquivo(arquivo);
        primeira.criarTabelas();
        new JdbcMensagemRepository(primeira).salvar(publica("geral", "guardada"));

        ConnectionFactory segunda = ConnectionFactory.arquivo(arquivo);
        segunda.criarTabelas();
        assertEquals(1, new JdbcMensagemRepository(segunda).buscarUltimas("geral", 10).size());
    }
}
