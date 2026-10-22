package br.unicesumar.chat.persistence;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.CredencialUsuario;
import br.unicesumar.chat.model.Usuario;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Contrato que toda implementação de {@link UsuarioRepository} deve cumprir. */
public abstract class UsuarioRepositoryContrato {

    /** Cria um repositório novo e vazio. */
    protected abstract UsuarioRepository criarRepositorio();

    private static CredencialUsuario cred(String nome) {
        return new CredencialUsuario(new Usuario(nome), "hash-" + nome, "salt-" + nome);
    }

    @Test
    void cadastrarUsuarioNovoDevolveTrue() {
        assertTrue(criarRepositorio().cadastrar(cred("ana")));
    }

    @Test
    void cadastrarNomeRepetidoDevolveFalse() {
        UsuarioRepository repo = criarRepositorio();
        repo.cadastrar(cred("ana"));
        assertFalse(repo.cadastrar(cred("ana")));
    }

    @Test
    void cadastrarNomeRepetidoIgnoraMaiusculas() {
        UsuarioRepository repo = criarRepositorio();
        repo.cadastrar(cred("ana"));
        assertFalse(repo.cadastrar(cred("ANA")));
    }

    @Test
    void cadastrarNaoSubstituiAOriginalQuandoDuplicado() {
        UsuarioRepository repo = criarRepositorio();
        repo.cadastrar(new CredencialUsuario(new Usuario("ana"), "hashA", "saltA"));
        repo.cadastrar(new CredencialUsuario(new Usuario("ANA"), "hashB", "saltB"));
        assertEquals("hashA", repo.buscarPorNome("ana").orElseThrow().senhaHash());
    }

    @Test
    void cadastrarNuloLancaExcecao() {
        UsuarioRepository repo = criarRepositorio();
        assertThrows(NullPointerException.class, () -> repo.cadastrar(null));
    }

    @Test
    void buscarPorNomeDevolveCredencialSalva() {
        UsuarioRepository repo = criarRepositorio();
        repo.cadastrar(new CredencialUsuario(new Usuario("Ana"), "h", "s"));
        CredencialUsuario c = repo.buscarPorNome("Ana").orElseThrow();
        assertEquals(new Usuario("Ana"), c.usuario());
        assertEquals("Ana", c.usuario().nome());
        assertEquals("h", c.senhaHash());
        assertEquals("s", c.salt());
    }

    @Test
    void buscarPorNomeIgnoraMaiusculas() {
        UsuarioRepository repo = criarRepositorio();
        repo.cadastrar(cred("ana"));
        assertTrue(repo.buscarPorNome("ANA").isPresent());
    }

    @Test
    void buscarPorNomeInexistenteDevolveVazio() {
        assertEquals(Optional.empty(), criarRepositorio().buscarPorNome("ninguem"));
    }

    @Test
    void buscarPorNomeNuloDevolveVazio() {
        assertTrue(criarRepositorio().buscarPorNome(null).isEmpty());
    }

    @Test
    void existeIgnoraMaiusculas() {
        UsuarioRepository repo = criarRepositorio();
        repo.cadastrar(cred("ana"));
        assertTrue(repo.existe("AnA"));
        assertFalse(repo.existe("bia"));
    }

    @Test
    void cadastrarMesmoNomeEmVariasThreadsTemUmUnicoVencedor() throws Exception {
        UsuarioRepository repo = criarRepositorio();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Callable<Boolean>> tarefas = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String nome = i % 2 == 0 ? "ana" : "ANA";
            tarefas.add(() -> repo.cadastrar(cred(nome)));
        }
        List<Future<Boolean>> futuros = pool.invokeAll(tarefas);
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        int vencedores = 0;
        for (Future<Boolean> f : futuros) {
            if (f.get()) {
                vencedores++;
            }
        }
        assertEquals(1, vencedores);
    }
}
