package br.unicesumar.chat.client.controller;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.client.net.ConexaoFalsa;
import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.protocol.Mensagens;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Testes com a thread de leitura de verdade (ConexaoFalsa e ChatViewFalsa; espera por monitor, sem sleep). */
class ChatControllerLeitorTest {

    private static final LocalDateTime T1 = LocalDateTime.of(2026, 10, 22, 9, 1);

    private final ChatViewFalsa view = new ChatViewFalsa();
    private final ConexaoFalsa conexao = new ConexaoFalsa();
    private final ChatController controller = new ChatController(view, (h, p) -> conexao);

    private void entrar() {
        conexao.receberEvento(new Evento.Sistema("Bem-vindo")).receberEvento(
                new Evento.Ok(Mensagens.entrouComo("ana", "geral")));
        assertTrue(controller.conectar("localhost", "5000", "ana", "senha123", false).sucesso());
    }

    @Test
    void eventosQueChegamDepoisDoLoginSaoTratadosPelaThreadDeLeitura() throws Exception {
        entrar();
        conexao.receberEvento(new Evento.Msg("geral", "bia", T1, "oi"));
        assertTrue(view.aguardar(() -> view.mensagens.size() == 1));
        assertEquals("oi", view.mensagens.get(0).texto());
        controller.sair();
    }

    @Test
    void servidorEncerrandoAConexaoAvisaAViewEMarcaDesconectado() throws Exception {
        entrar();
        conexao.encerrarPeloServidor();
        assertTrue(view.aguardar(() -> !view.encerramentos.isEmpty()));
        assertFalse(controller.conectado());
        assertEquals(EstadoConexao.DESCONECTADO, view.estados.get(view.estados.size() - 1));
        assertTrue(view.encerramentos.get(0).contains("encerrada"));
    }

    @Test
    void linhaForaDoProtocoloViraAvisoENaoMataALeitura() throws Exception {
        entrar();
        conexao.receberLinhaInvalida("lixo").receberEvento(new Evento.Msg("geral", "bia", T1, "depois"));
        assertTrue(view.aguardar(() -> view.mensagens.size() == 1));
        assertFalse(view.avisos.isEmpty());
        controller.sair();
    }

    @Test
    void excecaoNaViewNaoMataALeitura() throws Exception {
        entrar();
        view.falharAoAdicionar = new IllegalStateException("bug na tela");
        conexao.receberEvento(new Evento.Msg("geral", "bia", T1, "primeira"))
                .receberEvento(new Evento.Msg("geral", "bia", T1, "segunda"));
        assertTrue(view.aguardar(() -> view.mensagens.size() == 1));
        assertEquals("segunda", view.mensagens.get(0).texto());
        controller.sair();
    }

    @Test
    void sairNaoDisparaSessaoEncerradaMesmoComALeituraTerminando() throws Exception {
        entrar();
        controller.sair();
        assertTrue(controller.aguardarFimDaLeitura(5000)); // a leitura terminou de verdade
        assertTrue(view.encerramentos.isEmpty());
    }

    @Test
    void oitoThreadsEnviandoAoMesmoTempoNaoPerdemNenhumaMensagem() throws Exception {
        entrar();
        int threads = 8;
        int porThread = 25;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<?>> futuros = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int id = t;
            futuros.add(pool.submit(() -> {
                largada.await();
                for (int i = 0; i < porThread; i++) {
                    controller.enviar("t" + id + "-" + i);
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
        long enviadasDoTeste = conexao.enviadas().stream().filter(l -> l.matches("t\\d-\\d+")).count();
        assertEquals(threads * porThread, enviadasDoTeste);
        controller.sair();
    }
}
