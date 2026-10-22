package br.unicesumar.chat.persistence;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Contrato que toda implementação de {@link MensagemRepository} deve cumprir.
 * Cada adaptador (memória e JDBC) estende esta classe, garantindo comportamento igual.
 */
public abstract class MensagemRepositoryContrato {

    protected static final Usuario ANA = new Usuario("ana");
    protected static final Usuario BIA = new Usuario("bia");
    protected static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 1, 9, 5, 30);

    /** Cria um repositório novo e vazio. */
    protected abstract MensagemRepository criarRepositorio();

    protected static Mensagem publica(String sala, String texto) {
        return Mensagem.nova(ANA, sala, texto, HORA);
    }

    @Test
    void salvarDevolveMensagemComIdGerado() {
        MensagemRepository repo = criarRepositorio();
        Mensagem salva = repo.salvar(publica("geral", "oi"));
        assertNotNull(salva.id());
        assertEquals("oi", salva.conteudo());
    }

    @Test
    void salvarGeraIdsDiferentes() {
        MensagemRepository repo = criarRepositorio();
        Long id1 = repo.salvar(publica("geral", "1")).id();
        Long id2 = repo.salvar(publica("geral", "2")).id();
        assertNotEquals(id1, id2);
    }

    @Test
    void salvarMensagemNulaLancaExcecao() {
        MensagemRepository repo = criarRepositorio();
        assertThrows(NullPointerException.class, () -> repo.salvar(null));
    }

    @Test
    void buscarUltimasEmRepositorioVazioDevolveListaVazia() {
        assertTrue(criarRepositorio().buscarUltimas("geral", 10).isEmpty());
    }

    @Test
    void buscarUltimasDevolveEmOrdemCronologica() {
        MensagemRepository repo = criarRepositorio();
        repo.salvar(publica("geral", "1"));
        repo.salvar(publica("geral", "2"));
        repo.salvar(publica("geral", "3"));
        List<String> textos = repo.buscarUltimas("geral", 10).stream()
                .map(Mensagem::conteudo).toList();
        assertEquals(List.of("1", "2", "3"), textos);
    }

    @Test
    void buscarUltimasLimitaAQuantidadeMantendoAsMaisRecentes() {
        MensagemRepository repo = criarRepositorio();
        for (int i = 1; i <= 5; i++) {
            repo.salvar(publica("geral", String.valueOf(i)));
        }
        List<String> textos = repo.buscarUltimas("geral", 2).stream()
                .map(Mensagem::conteudo).toList();
        assertEquals(List.of("4", "5"), textos);
    }

    @Test
    void buscarUltimasComQuantidadeExataDoTotalDevolveTudo() {
        MensagemRepository repo = criarRepositorio();
        repo.salvar(publica("geral", "1"));
        repo.salvar(publica("geral", "2"));
        assertEquals(2, repo.buscarUltimas("geral", 2).size());
    }

    @Test
    void buscarUltimasComZeroDevolveListaVazia() {
        MensagemRepository repo = criarRepositorio();
        repo.salvar(publica("geral", "1"));
        assertTrue(repo.buscarUltimas("geral", 0).isEmpty());
    }

    @Test
    void buscarUltimasComQuantidadeNegativaLancaExcecao() {
        MensagemRepository repo = criarRepositorio();
        assertThrows(IllegalArgumentException.class, () -> repo.buscarUltimas("geral", -1));
    }

    @Test
    void buscarUltimasFiltraPelaSala() {
        MensagemRepository repo = criarRepositorio();
        repo.salvar(publica("geral", "na geral"));
        repo.salvar(publica("java", "no java"));
        List<Mensagem> java = repo.buscarUltimas("java", 10);
        assertEquals(1, java.size());
        assertEquals("no java", java.get(0).conteudo());
    }

    @Test
    void buscarUltimasNuncaDevolveMensagemPrivada() {
        MensagemRepository repo = criarRepositorio();
        repo.salvar(publica("geral", "publica"));
        repo.salvar(Mensagem.privada(ANA, BIA, "geral", "segredo", HORA));
        List<String> textos = repo.buscarUltimas("geral", 10).stream()
                .map(Mensagem::conteudo).toList();
        assertEquals(List.of("publica"), textos);
    }

    @Test
    void salvarEBuscarPreservaTodosOsCampos() {
        MensagemRepository repo = criarRepositorio();
        Mensagem salva = repo.salvar(Mensagem.nova(ANA, "geral", "olá, ação", HORA));
        Mensagem lida = repo.buscarUltimas("geral", 1).get(0);
        assertEquals(salva.id(), lida.id());
        assertEquals(ANA, lida.remetente());
        assertEquals("olá, ação", lida.conteudo());
        assertEquals(HORA, lida.horario());
        assertEquals("geral", lida.sala());
        assertTrue(lida.destinatario().isEmpty());
    }

    @Test
    void preservaQuebraDeLinhaDelimitadorEAspasNoConteudo() {
        MensagemRepository repo = criarRepositorio();
        String texto = "linha1\nlinha2 | 'aspas' \"duplas\"; DROP TABLE mensagem;--";
        repo.salvar(publica("geral", texto));
        assertEquals(texto, repo.buscarUltimas("geral", 1).get(0).conteudo());
    }

    @Test
    void preservaMensagemComExatamente500Caracteres() {
        MensagemRepository repo = criarRepositorio();
        String texto = "z".repeat(500);
        repo.salvar(publica("geral", texto));
        assertEquals(texto, repo.buscarUltimas("geral", 1).get(0).conteudo());
    }

    @Test
    void salvarConcorrentementeNaoPerdeMensagensNemRepeteIds() throws Exception {
        MensagemRepository repo = criarRepositorio();
        int threads = 8;
        int porThread = 25;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Callable<Long>> tarefas = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            for (int i = 0; i < porThread; i++) {
                String texto = "t" + t + "-" + i;
                tarefas.add(() -> repo.salvar(publica("geral", texto)).id());
            }
        }
        List<Future<Long>> futuros = pool.invokeAll(tarefas);
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        Set<Long> ids = new HashSet<>();
        for (Future<Long> f : futuros) {
            ids.add(f.get());
        }
        assertEquals(threads * porThread, ids.size());
        assertEquals(threads * porThread, repo.buscarUltimas("geral", 1000).size());
    }
}
