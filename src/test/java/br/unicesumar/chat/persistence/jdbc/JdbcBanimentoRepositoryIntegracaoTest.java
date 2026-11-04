package br.unicesumar.chat.persistence.jdbc;

import br.unicesumar.chat.persistence.BanimentoRepository;
import br.unicesumar.chat.persistence.BanimentoRepositoryContrato;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;

/** Teste de INTEGRAÇÃO: SQLite real em arquivo temporário. */
@Tag("integracao")
class JdbcBanimentoRepositoryIntegracaoTest extends BanimentoRepositoryContrato {

    @TempDir
    Path pasta;

    @Override
    protected BanimentoRepository criarRepositorio() {
        ConnectionFactory fabrica =
                ConnectionFactory.arquivo(pasta.resolve("bans-" + System.nanoTime() + ".db"));
        fabrica.criarTabelas();
        return new JdbcBanimentoRepository(fabrica);
    }
}
