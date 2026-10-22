package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.memory.MensagemRepositoryEmMemoria;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.RepeatedTest;

/** Testes de concorrência (unitários: sem rede e sem banco; sem Thread.sleep). */
class ChatServiceConcorrenciaTest {

    private static final int THREADS = 8;
    private static final int POR_THREAD = 25;

    @RepeatedTest(10)
    void oitoThreadsEnviandoAoMesmoTempoNaoPerdemMensagensNemMudamAOrdemParaOsClientes()
            throws Exception {
        ClientRegistry registry = new ClientRegistry();
        MensagemRepository repo = new MensagemRepositoryEmMemoria();
        ChatService chat = new ChatService(repo, registry, Clock.systemDefaultZone());
        ClienteFalso x = ClienteFalso.identificado(registry, "xavier");
        ClienteFalso y = ClienteFalso.identificado(registry, "yara");
        ClienteFalso z = ClienteFalso.identificado(registry, "zeca");

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<?>> futuros = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            Usuario remetente = new Usuario("user" + t);
            int id = t;
            futuros.add(pool.submit(() -> {
                largada.await();
                for (int i = 0; i < POR_THREAD; i++) {
                    chat.enviarMensagem(remetente, "geral", "t" + id + "-m" + i);
                }
                return null;
            }));
        }
        largada.countDown();
        for (Future<?> f : futuros) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        int esperado = THREADS * POR_THREAD;
        assertEquals(esperado, repo.buscarUltimas("geral", esperado + 10).size());
        assertEquals(esperado, x.textos().size());
        assertEquals(esperado, x.textos().stream().distinct().count());
        assertEquals(x.textos(), y.textos());
        assertEquals(x.textos(), z.textos());
    }

    @RepeatedTest(10)
    void clientesEntrandoESaindoDuranteOEnvioNaoCausamExcecao() throws Exception {
        ClientRegistry registry = new ClientRegistry();
        ChatService chat = new ChatService(new MensagemRepositoryEmMemoria(), registry,
                Clock.systemDefaultZone());
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<?>> futuros = new ArrayList<>();
        for (int t = 0; t < 2; t++) {
            int id = t;
            futuros.add(pool.submit(() -> {
                largada.await();
                for (int i = 0; i < 50; i++) {
                    ClienteFalso c = ClienteFalso.novo();
                    registry.adicionar(c.handler);
                    registry.identificar(c.handler, new Usuario("c" + id + "x" + i));
                    registry.remover(c.handler);
                }
                return null;
            }));
        }
        for (int t = 0; t < 2; t++) {
            futuros.add(pool.submit(() -> {
                largada.await();
                for (int i = 0; i < 50; i++) {
                    chat.enviarMensagem(new Usuario("emissor"), "geral", "m" + i);
                }
                return null;
            }));
        }
        largada.countDown();
        for (Future<?> f : futuros) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(0, registry.quantidade());
    }
}
