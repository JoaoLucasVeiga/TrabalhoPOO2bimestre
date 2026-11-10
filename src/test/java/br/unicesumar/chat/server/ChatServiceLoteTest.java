package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.RepositorioException;
import br.unicesumar.chat.persistence.memory.MensagemRepositoryEmMemoria;
import br.unicesumar.chat.protocol.Evento;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Produtor-consumidor: as threads dos clientes só enfileiram, e um único consumidor grava em lote e
 * transmite na ordem. A API continua síncrona (quem envia espera o resultado). Sem {@code Thread.sleep}.
 */
class ChatServiceLoteTest {

    /** Repositório que registra o tamanho de cada lote e segura o primeiro até ser liberado. */
    private static final class RepositorioDeLotes extends MensagemRepositoryEmMemoria {
        final List<Integer> tamanhos = Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch primeiroLoteIniciou = new CountDownLatch(1);
        final CountDownLatch liberarPrimeiroLote = new CountDownLatch(1);
        volatile String textoQueFalha;

        @Override
        public List<Mensagem> salvarTodas(List<Mensagem> mensagens) {
            tamanhos.add(mensagens.size());
            if (tamanhos.size() == 1) {
                primeiroLoteIniciou.countDown();
                try {
                    assertTrue(liberarPrimeiroLote.await(20, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (textoQueFalha != null && mensagens.stream().anyMatch(m -> m.conteudo().equals(textoQueFalha))) {
                throw new RepositorioException("falha simulada no lote", null);
            }
            return super.salvarTodas(mensagens);
        }

        @Override
        public synchronized Mensagem salvar(Mensagem mensagem) {
            if (mensagem.conteudo().equals(textoQueFalha)) {
                throw new RepositorioException("falha simulada", null);
            }
            return super.salvar(mensagem);
        }
    }

    private final ClientRegistry registry = new ClientRegistry();
    private final RepositorioDeLotes repo = new RepositorioDeLotes();
    private final ChatService chat = new ChatService(repo, registry, Clock.systemDefaultZone());
    private final ClienteFalso ouvinte = ClienteFalso.identificado(registry, "ouvinte");
    private ExecutorService executor;

    @AfterEach
    void fechar() {
        repo.liberarPrimeiroLote.countDown();
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /** Envia a primeira mensagem (que segura o consumidor) e enfileira as outras enquanto ele está ocupado. */
    private List<Future<Mensagem>> enviarEnquantoOConsumidorEstaOcupado(String... textos) throws Exception {
        executor = Executors.newFixedThreadPool(textos.length + 1);
        List<Future<Mensagem>> futuros = new ArrayList<>();
        Usuario quem = new Usuario("ouvinte");
        futuros.add(executor.submit(() -> chat.enviarMensagem(quem, "geral", "primeira")));
        assertTrue(repo.primeiroLoteIniciou.await(20, TimeUnit.SECONDS));
        for (String t : textos) {
            futuros.add(executor.submit(() -> chat.enviarMensagem(quem, "geral", t)));
        }
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (chat.mensagensNaFila() < textos.length && System.nanoTime() < limite) {
            Thread.onSpinWait();
        }
        assertEquals(textos.length, chat.mensagensNaFila(), "as mensagens deveriam estar esperando na fila");
        return futuros;
    }

    @Test
    void mensagensQueEsperamNaFilaSaoGravadasEmUmLoteSo() throws Exception {
        List<Future<Mensagem>> futuros = enviarEnquantoOConsumidorEstaOcupado("m1", "m2", "m3", "m4", "m5");
        repo.liberarPrimeiroLote.countDown();
        for (Future<Mensagem> f : futuros) {
            assertNotNull(f.get(20, TimeUnit.SECONDS));
        }
        assertEquals(List.of(1, 5), repo.tamanhos, "o segundo lote deveria juntar as 5 mensagens da fila");
        assertEquals(6, repo.buscarUltimas("geral", 10).size());
    }

    @Test
    void quemRecebeVeAMesmaOrdemDaQueFoiGravada() throws Exception {
        List<Future<Mensagem>> futuros = enviarEnquantoOConsumidorEstaOcupado("m1", "m2", "m3", "m4");
        repo.liberarPrimeiroLote.countDown();
        for (Future<Mensagem> f : futuros) {
            f.get(20, TimeUnit.SECONDS);
        }
        List<String> gravadas = repo.buscarUltimas("geral", 10).stream().map(Mensagem::conteudo).toList();
        List<String> recebidas = ouvinte.eventos().stream().filter(e -> e instanceof Evento.Msg)
                .map(e -> ((Evento.Msg) e).texto()).toList();
        assertEquals(gravadas, recebidas);
        assertEquals("primeira", recebidas.get(0));
    }

    @Test
    void quemEnviaSoRecebeOResultadoDepoisDeTransmitido() throws Exception {
        repo.liberarPrimeiroLote.countDown(); // sem segurar o lote
        Mensagem salva = chat.enviarMensagem(new Usuario("ouvinte"), "geral", "oi");
        assertNotNull(salva.id());
        assertTrue(ouvinte.eventos().stream().anyMatch(e -> e instanceof Evento.Msg m && m.texto().equals("oi")),
                "ao voltar de enviarMensagem, a transmissão já aconteceu");
        assertEquals(0, chat.mensagensNaFila());
    }

    @Test
    void falhaDeUmaMensagemDoLoteNaoDerrubaAsOutras() throws Exception {
        repo.textoQueFalha = "ruim";
        List<Future<Mensagem>> futuros = enviarEnquantoOConsumidorEstaOcupado("boa1", "ruim", "boa2");
        repo.liberarPrimeiroLote.countDown();
        assertNotNull(futuros.get(0).get(20, TimeUnit.SECONDS)); // "primeira"
        assertNotNull(futuros.get(1).get(20, TimeUnit.SECONDS)); // boa1
        ExecutionException erro = assertThrows(ExecutionException.class, () -> futuros.get(2).get(20, TimeUnit.SECONDS));
        assertInstanceOf(RepositorioException.class, erro.getCause());
        assertNotNull(futuros.get(3).get(20, TimeUnit.SECONDS)); // boa2
        List<String> gravadas = repo.buscarUltimas("geral", 10).stream().map(Mensagem::conteudo).toList();
        assertEquals(List.of("primeira", "boa1", "boa2"), gravadas);
        List<String> recebidas = ouvinte.eventos().stream().filter(e -> e instanceof Evento.Msg)
                .map(e -> ((Evento.Msg) e).texto()).toList();
        assertEquals(gravadas, recebidas, "só o que foi gravado é transmitido");
    }

    @Test
    void mensagemInvalidaNemChegaAFila() {
        repo.liberarPrimeiroLote.countDown();
        assertThrows(IllegalArgumentException.class,
                () -> chat.enviarMensagem(new Usuario("ouvinte"), "geral", "   "));
        assertEquals(0, chat.mensagensNaFila());
        assertTrue(repo.tamanhos.isEmpty());
    }
}
