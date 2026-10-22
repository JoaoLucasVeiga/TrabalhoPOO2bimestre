package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.memory.MensagemRepositoryEmMemoria;
import br.unicesumar.chat.protocol.Evento;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.RepeatedTest;

/** Entrar enquanto outras threads enviam: sem mensagem perdida nem duplicada (sem Thread.sleep). */
class ChatServiceEntradaConcorrenciaTest {

    private static final int EMISSORES = 4;
    private static final int POR_EMISSOR = 30;

    @RepeatedTest(10)
    void clienteQueEntraNoMeioDoFluxoRecebeHistoricoMaisAoVivoSemBuracosNemRepeticoes()
            throws Exception {
        ClientRegistry registry = new ClientRegistry();
        MensagemRepository repo = new MensagemRepositoryEmMemoria();
        ChatService chat = new ChatService(repo, registry, Clock.systemDefaultZone());
        for (int i = 0; i < 5; i++) {
            chat.enviarMensagem(new Usuario("semente"), "geral", "antiga" + i);
        }
        ClienteFalso novo = ClienteFalso.novo();
        registry.adicionar(novo.handler);

        ExecutorService pool = Executors.newFixedThreadPool(EMISSORES + 1);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<?>> futuros = new ArrayList<>();
        for (int t = 0; t < EMISSORES; t++) {
            Usuario remetente = new Usuario("emissor" + t);
            int id = t;
            futuros.add(pool.submit(() -> {
                largada.await();
                for (int i = 0; i < POR_EMISSOR; i++) {
                    chat.enviarMensagem(remetente, "geral", "e" + id + "-" + i);
                }
                return null;
            }));
        }
        futuros.add(pool.submit(() -> {
            largada.await();
            chat.entrar(novo.handler, new Usuario("recemchegado"));
            return null;
        }));
        largada.countDown();
        for (Future<?> f : futuros) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        List<String> recebidas = new ArrayList<>();
        for (Evento e : novo.eventos()) {
            if (e instanceof Evento.Historico h) {
                h.mensagens().forEach(m -> recebidas.add(m.texto()));
            } else if (e instanceof Evento.Msg m) {
                recebidas.add(m.texto());
            }
        }
        List<String> todas = repo.buscarUltimas("geral", 1000).stream().map(Mensagem::conteudo).toList();
        assertEquals(5 + EMISSORES * POR_EMISSOR, todas.size());
        // o que o cliente viu tem de ser exatamente o final do histórico completo:
        // sem buraco (mensagem perdida na janela) e sem repetição (visto no histórico e ao vivo)
        assertFalse(recebidas.isEmpty());
        assertEquals(todas.subList(todas.size() - recebidas.size(), todas.size()), recebidas);
    }
}
