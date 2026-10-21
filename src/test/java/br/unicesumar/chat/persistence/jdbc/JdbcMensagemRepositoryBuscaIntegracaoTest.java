package br.unicesumar.chat.persistence.jdbc;

import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.MensagemRepositoryBuscaContrato;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;

/** Teste de INTEGRAÇÃO: SQLite real em arquivo temporário. */
@Tag("integracao")
class JdbcMensagemRepositoryBuscaIntegracaoTest extends MensagemRepositoryBuscaContrato {

    @TempDir
    Path pasta;

    @Override
    protected MensagemRepository criarRepositorio() {
        ConnectionFactory fabrica =
                ConnectionFactory.arquivo(pasta.resolve("busca-" + System.nanoTime() + ".db"));
        fabrica.criarTabelas();
        return new JdbcMensagemRepository(fabrica);
    }
}
