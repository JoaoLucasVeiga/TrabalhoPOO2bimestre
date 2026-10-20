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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Testes de INTEGRAÇÃO: quedas abruptas reais (RST) com o servidor completo. */
@Tag("integracao")
class ServidorDesconexaoIntegracaoTest {

    private static class Cliente implements AutoCloseable {
        final Socket socket;
        final BufferedReader entrada;
        final PrintWriter saida;

        Cliente(int porta) throws IOException {
            socket = new Socket(InetAddress.getLoopbackAddress(), porta);
            socket.setSoTimeout(15_000);
            entrada = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            saida = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        }

        Evento ler() throws IOException {
            return Protocolo.decodificar(entrada.readLine());
        }

        void enviar(String texto) {
            saida.print(Protocolo.codificarEntrada(texto) + "\n");
            saida.flush();
        }

        void entrarComo(String nome) throws IOException {
            assertInstanceOf(Evento.Sistema.class, ler());
            enviar(nome);
            assertInstanceOf(Evento.Ok.class, ler());
        }

        void cairDeRepente() throws IOException {
            socket.setSoLinger(true, 0);
            socket.close();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private final ClientRegistry registry = new ClientRegistry();
    private CountDownLatch desconexoes;
    private Servidor servidor;

    private int subir(int desconexoesEsperadas) throws IOException {
        desconexoes = new CountDownLatch(desconexoesEsperadas);
        ChatService chat = new ChatService(new MensagemRepositoryEmMemoria(), registry,
                Clock.systemDefaultZone());
        ProcessadorChat real = new ProcessadorChat(chat, registry);
        servidor = new Servidor(0, registry, new ProcessadorEntrada() {
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
        });
        servidor.iniciar();
        return servidor.porta();
    }

    @AfterEach
    void parar() {
        if (servidor != null) {
            servidor.parar();
        }
    }

    @Test
    void variosClientesCaemDeRepenteEnquantoOsOutrosConversamSemPerderMensagens() throws Exception {
        int caem = 4;
        int ficam = 3;
        int porCliente = 15;
        int porta = subir(caem);
        List<Cliente> quedas = new ArrayList<>();
        List<Cliente> sobreviventes = new ArrayList<>();
        try {
            for (int i = 0; i < caem; i++) {
                Cliente c = new Cliente(porta);
                c.entrarComo("cai" + i);
                quedas.add(c);
            }
            for (int i = 0; i < ficam; i++) {
                Cliente c = new Cliente(porta);
                c.entrarComo("fica" + i);
                sobreviventes.add(c);
            }

            ExecutorService pool = Executors.newFixedThreadPool(ficam + caem);
            CountDownLatch largada = new CountDownLatch(1);
            List<Future<Integer>> leituras = new ArrayList<>();
            for (int i = 0; i < ficam; i++) {
                Cliente c = sobreviventes.get(i);
                int id = i;
                leituras.add(pool.submit(() -> {
                    largada.await();
                    for (int k = 0; k < porCliente; k++) {
                        c.enviar("fica" + id + "-" + k);
                    }
                    int lidas = 0;
                    while (lidas < ficam * porCliente) {
                        assertInstanceOf(Evento.Msg.class, c.ler());
                        lidas++;
                    }
                    return lidas;
                }));
            }
            List<Future<?>> queda = new ArrayList<>();
            for (Cliente c : quedas) {
                queda.add(pool.submit(() -> {
                    largada.await();
                    c.cairDeRepente();
                    return null;
                }));
            }
            largada.countDown();
            for (Future<?> f : queda) {
                f.get(30, TimeUnit.SECONDS);
            }
            for (Future<Integer> f : leituras) {
                assertEquals(ficam * porCliente, f.get(60, TimeUnit.SECONDS));
            }
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

            assertTrue(desconexoes.await(15, TimeUnit.SECONDS));
            assertEquals(ficam, registry.quantidade());
            assertTrue(servidor.ativo());
        } finally {
            for (Cliente c : sobreviventes) {
                c.close();
            }
        }
    }

    @Test
    void quedaNoMeioDaConexaoAntesDeInformarONomeNaoDeixaRastro() throws Exception {
        int porta = subir(1);
        Cliente c = new Cliente(porta);
        assertInstanceOf(Evento.Sistema.class, c.ler());
        c.cairDeRepente();
        assertTrue(desconexoes.await(10, TimeUnit.SECONDS));
        assertEquals(0, registry.quantidade());
        try (Cliente outro = new Cliente(porta)) {
            outro.entrarComo("novo");
        }
    }

    @Test
    void clienteQueFechaSoOLadoDeEscritaEDetectadoEDesconectado() throws Exception {
        int porta = subir(1);
        try (Cliente c = new Cliente(porta)) {
            c.entrarComo("ana");
            c.socket.shutdownOutput();
            assertTrue(desconexoes.await(10, TimeUnit.SECONDS));
            assertEquals(0, registry.quantidade());
        }
    }
}
