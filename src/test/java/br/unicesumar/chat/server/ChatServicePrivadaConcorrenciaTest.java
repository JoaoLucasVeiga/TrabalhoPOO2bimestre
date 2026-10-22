package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.RepeatedTest;

/** Privadas concorrentes: nada se perde e nada vaza (sem Thread.sleep). */
class ChatServicePrivadaConcorrenciaTest {

    @RepeatedTest(10)
    void oitoThreadsEnviandoPrivadasAoMesmoTempoEntregamTudoSoAoDestinatario() throws Exception {
        ClientRegistry registry = new ClientRegistry();
        RepositorioEspiao repo = new RepositorioEspiao();
        ChatService chat = new ChatService(repo, registry, Clock.systemDefaultZone());
        int emissores = 8;
        int porEmissor = 25;
        List<ClienteFalso> remetentes = new ArrayList<>();
        for (int i = 0; i < emissores; i++) {
            remetentes.add(ClienteFalso.identificado(registry, "emissor" + i));
        }
        ClienteFalso alvo = ClienteFalso.identificado(registry, "alvo");
        ClienteFalso curioso = ClienteFalso.identificado(registry, "curioso");

        ExecutorService pool = Executors.newFixedThreadPool(emissores);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<?>> futuros = new ArrayList<>();
        for (int t = 0; t < emissores; t++) {
            ClienteFalso quem = remetentes.get(t);
            int id = t;
            futuros.add(pool.submit(() -> {
                largada.await();
                for (int i = 0; i < porEmissor; i++) {
                    chat.enviarPrivada(quem.handler, "alvo", "p" + id + "-" + i);
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

        int total = emissores * porEmissor;
        assertEquals(total, repo.salvas().size());
        assertEquals(total, alvo.eventos().size());
        assertEquals(total, alvo.eventos().stream().distinct().count());
        assertTrue(curioso.eventos().isEmpty());
        for (ClienteFalso r : remetentes) {
            assertEquals(porEmissor, r.eventos().size()); // cada um só vê as próprias confirmações
        }
    }
}
