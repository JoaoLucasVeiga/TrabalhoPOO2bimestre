package br.unicesumar.chat.persistence.jdbc;

import br.unicesumar.chat.persistence.UsuarioRepository;
import br.unicesumar.chat.persistence.UsuarioRepositoryContrato;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;

/** Teste de INTEGRAÇÃO: usa SQLite real em arquivo temporário (não é teste unitário). */
@Tag("integracao")
class JdbcUsuarioRepositoryIntegracaoTest extends UsuarioRepositoryContrato {

    @TempDir
    Path pasta;

    @Override
    protected UsuarioRepository criarRepositorio() {
        ConnectionFactory fabrica =
                ConnectionFactory.arquivo(pasta.resolve("teste-" + System.nanoTime() + ".db"));
        fabrica.criarTabelas();
        return new JdbcUsuarioRepository(fabrica);
    }
}
