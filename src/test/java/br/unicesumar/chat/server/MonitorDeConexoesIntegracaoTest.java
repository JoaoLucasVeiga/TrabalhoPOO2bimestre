package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.persistence.memory.MensagemRepositoryEmMemoria;
import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.protocol.Protocolo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Heartbeat (integração, sockets reais): o servidor manda PING; quem responde fica, quem some sem fechar
 * a conexão (cliente "zumbi") é removido, e o servidor e os outros clientes seguem funcionando.
 */
@Tag("integracao")
class MonitorDeConexoesIntegracaoTest {

    private static final Duration PING = Duration.ofMillis(100);
    private static final Duration LIMITE = Duration.ofMillis(700);

    private final ClientRegistry registry = new ClientRegistry();
    private final CountDownLatch desconexoes = new CountDownLatch(1);
    private Servidor servidor;

    private int subir() throws IOException {
        ChatService chat = new ChatService(new MensagemRepositoryEmMemoria(), registry, Clock.systemDefaultZone());
        ProcessadorChat real = new ProcessadorChat(chat, registry);
        ProcessadorEntrada observado = new ProcessadorEntrada() {
            @Override
            public void aoConectar(ClienteHandler origem) {
                real.aoConectar(origem);
            }

            @Override
            public void aoReceber(ClienteHandler origem, String linha) {
                real.aoReceber(origem, linha);
            }

            @Override
            public void aoDesconectar(ClienteHandler origem) {
                real.aoDesconectar(origem);
                desconexoes.countDown();
            }
        };
        servidor = new Servidor(0, registry, observado, PING, LIMITE);
        servidor.iniciar();
        return servidor.porta();
    }

    @AfterEach
    void parar() {
        if (servidor != null) {
            servidor.parar();
        }
    }

    private static Socket conectar(int porta) throws IOException {
        Socket s = new Socket(InetAddress.getLoopbackAddress(), porta);
        s.setSoTimeout(10_000);
        return s;
    }

    @Test
    void clienteQueNaoRespondeAoPingERemovidoEOQueRespondeContinua() throws Exception {
        int porta = subir();
        try (Socket zumbi = conectar(porta); Socket vivo = conectar(porta)) {
            // o zumbi só abre a conexão e nunca mais diz nada (nem responde ao PING)
            BufferedReader leitor = new BufferedReader(new InputStreamReader(vivo.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter escritor = new PrintWriter(vivo.getOutputStream(), true, StandardCharsets.UTF_8);
            AtomicInteger pings = new AtomicInteger();
            CountDownLatch viuMensagemDepois = new CountDownLatch(1);
            Thread resposta = new Thread(() -> {
                try {
                    String linha;
                    while ((linha = leitor.readLine()) != null) {
                        if (linha.equals("PING")) {
                            pings.incrementAndGet();
                            escritor.print("/pong\n");
                            escritor.flush();
                        } else if (linha.contains("depois da queda")) {
                            viuMensagemDepois.countDown();
                        }
                    }
                } catch (IOException e) {
                    // conexão fechada no fim do teste
                }
            });
            resposta.setDaemon(true);
            resposta.start();
            escritor.print(Protocolo.codificarEntrada("vivo") + "\n");
            escritor.flush();

            assertTrue(desconexoes.await(10, TimeUnit.SECONDS), "o cliente zumbi não foi removido");
            assertEquals(1, registry.listar().size(), "só o cliente que responde deve sobrar");
            assertTrue(pings.get() >= 1, "o servidor deveria ter enviado PING");

            escritor.print(Protocolo.codificarEntrada("mensagem depois da queda") + "\n");
            escritor.flush();
            assertTrue(viuMensagemDepois.await(10, TimeUnit.SECONDS), "o servidor parou de atender");
        }
    }

    @Test
    void intervalosInvalidosSaoRecusados() {
        ClientRegistry r = new ClientRegistry();
        ProcessadorEntrada p = new ProcessadorEntrada() {
            @Override
            public void aoConectar(ClienteHandler origem) {
            }

            @Override
            public void aoReceber(ClienteHandler origem, String linha) {
            }

            @Override
            public void aoDesconectar(ClienteHandler origem) {
            }
        };
        assertThrows(IllegalArgumentException.class, () -> new Servidor(0, r, p, Duration.ZERO, LIMITE));
        assertThrows(IllegalArgumentException.class,
                () -> new Servidor(0, r, p, Duration.ofSeconds(10), Duration.ofSeconds(5)));
    }
}
