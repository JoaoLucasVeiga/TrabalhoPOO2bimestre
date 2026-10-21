package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Usuario;
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

/** Clientes trocando de sala enquanto outros enviam: sem exceção e sem perder mensagens da sala. */
class SalaServiceConcorrenciaTest {

    @RepeatedTest(10)
    void quemFicaNaSalaRecebeTodasAsMensagensMesmoComOutrosTrocandoDeSala() throws Exception {
        ClientRegistry registry = new ClientRegistry();
        ChatService chat = new ChatService(new MensagemRepositoryEmMemoria(), registry,
                Clock.systemDefaultZone());
        SalaService salas = new SalaService(chat, registry);
        ClienteFalso observador = ClienteFalso.identificado(registry, "observador");
        List<ClienteFalso> viajantes = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            viajantes.add(ClienteFalso.identificado(registry, "viajante" + i));
        }
        int emissores = 3;
        int porEmissor = 20;
        ExecutorService pool = Executors.newFixedThreadPool(emissores + viajantes.size());
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<?>> futuros = new ArrayList<>();
        for (int t = 0; t < emissores; t++) {
            Usuario remetente = new Usuario("emissor" + t);
            int id = t;
            futuros.add(pool.submit(() -> {
                largada.await();
                for (int i = 0; i < porEmissor; i++) {
                    chat.enviarMensagem(remetente, "geral", "g" + id + "-" + i);
                    chat.enviarMensagem(remetente, "java", "j" + id + "-" + i);
                }
                return null;
            }));
        }
        for (ClienteFalso v : viajantes) {
            futuros.add(pool.submit(() -> {
                largada.await();
                for (int i = 0; i < 20; i++) {
                    salas.entrarSala(v.handler, "java");
                    salas.sairSala(v.handler);
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

        assertEquals(emissores * porEmissor, observador.textos().size());
        assertTrue(observador.textos().stream().allMatch(t -> t.startsWith("g")));
        assertEquals(observador.textos().size(), observador.textos().stream().distinct().count());
        assertEquals(1, salas.listarSalas().size()); // todos voltaram para a geral
    }
}
