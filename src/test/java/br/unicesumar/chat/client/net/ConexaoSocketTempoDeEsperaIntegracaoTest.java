package br.unicesumar.chat.client.net;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.protocol.Evento;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Testes de INTEGRAÇÃO: servidor falso que aceita e fica em silêncio (sockets reais). */
@Tag("integracao")
class ConexaoSocketTempoDeEsperaIntegracaoTest {

    @Test
    void receberComTempoDeEsperaFalhaEmVezDeFicarPresoParaSempre() throws Exception {
        try (ServerSocket escuta = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            ConexaoSocket c = new ConexaoSocket("localhost", escuta.getLocalPort());
            c.abrir();
            c.definirTempoDeEspera(300);
            assertThrows(SocketTimeoutException.class, c::receber);
            c.fechar();
        }
    }

    @Test
    void depoisDoTempoDeEsperaZeroAConexaoVoltaAEsperarSemLimiteEAindaRecebe() throws Exception {
        try (ServerSocket escuta = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            CountDownLatch pode = new CountDownLatch(1);
            CompletableFuture<Void> servidor = CompletableFuture.runAsync(() -> {
                try (Socket s = escuta.accept();
                        PrintWriter out = new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8)) {
                    pode.await(10, TimeUnit.SECONDS);
                    out.print("SISTEMA|enfim\n");
                    out.flush();
                } catch (IOException | InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            });
            ConexaoSocket c = new ConexaoSocket("localhost", escuta.getLocalPort());
            c.abrir();
            c.definirTempoDeEspera(200);
            assertThrows(SocketTimeoutException.class, c::receber);
            c.definirTempoDeEspera(0);
            pode.countDown();
            assertEquals(new Evento.Sistema("enfim"), c.receber());
            servidor.get(10, TimeUnit.SECONDS);
            c.fechar();
        }
    }

    @Test
    void definirTempoDeEsperaAntesDeAbrirOuDepoisDeFecharNaoLancaExcecao() {
        ConexaoSocket c = new ConexaoSocket("localhost", 1);
        assertDoesNotThrow(() -> c.definirTempoDeEspera(100));
        assertDoesNotThrow(() -> new ConexaoFalsa().definirTempoDeEspera(100));
    }

    @Test
    void valorNegativoELancaExcecao() {
        assertThrows(IllegalArgumentException.class, () -> new ConexaoSocket("localhost", 1).definirTempoDeEspera(-1));
    }
}
