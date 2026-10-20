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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Testes de INTEGRAÇÃO: servidor completo com sockets reais em loopback. */
@Tag("integracao")
class ServidorBroadcastIntegracaoTest {

    private static class Cliente implements AutoCloseable {
        final Socket socket;
        final BufferedReader entrada;
        final PrintWriter saida;

        Cliente(int porta) throws IOException {
            socket = new Socket(InetAddress.getLoopbackAddress(), porta);
            socket.setSoTimeout(10_000);
            entrada = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            saida = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        }

        Evento ler() throws IOException {
            String linha = entrada.readLine();
            return linha == null ? null : Protocolo.decodificar(linha);
        }

        void enviar(String texto) {
            saida.print(Protocolo.codificarEntrada(texto) + "\n");
            saida.flush();
        }

        /** Envia o nome e consome a boas-vindas e a confirmação. */
        void entrarComo(String nome) throws IOException {
            assertInstanceOf(Evento.Sistema.class, ler());
            enviar(nome);
            assertInstanceOf(Evento.Ok.class, ler());
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private final ClientRegistry registry = new ClientRegistry();
    private final CountDownLatch desconexoes = new CountDownLatch(1);
    private Servidor servidor;

    private int subir() throws IOException {
        ChatService chat = new ChatService(new MensagemRepositoryEmMemoria(), registry,
                Clock.systemDefaultZone());
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
        servidor = new Servidor(0, registry, observado);
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
    void tresClientesTrocamMensagensPorBroadcast() throws Exception {
        int porta = subir();
        try (Cliente ana = new Cliente(porta);
                Cliente bia = new Cliente(porta);
                Cliente caio = new Cliente(porta)) {
            ana.entrarComo("ana");
            bia.entrarComo("bia");
            caio.entrarComo("caio");

            ana.enviar("olá a todos | com delimitador");
            bia.enviar("oi ana");

            for (Cliente c : new Cliente[] {ana, bia, caio}) {
                Evento.Msg primeira = assertInstanceOf(Evento.Msg.class, c.ler());
                assertEquals("ana", primeira.remetente());
                assertEquals("olá a todos | com delimitador", primeira.texto());
                Evento.Msg segunda = assertInstanceOf(Evento.Msg.class, c.ler());
                assertEquals("bia", segunda.remetente());
            }
        }
    }

    @Test
    void depoisQueUmClienteSaiOsOutrosContinuamTrocandoMensagens() throws Exception {
        int porta = subir();
        try (Cliente ana = new Cliente(porta); Cliente bia = new Cliente(porta)) {
            ana.entrarComo("ana");
            bia.entrarComo("bia");
            Cliente caio = new Cliente(porta);
            caio.entrarComo("caio");
            assertEquals(3, registry.quantidade());

            caio.socket.setSoLinger(true, 0);
            caio.close();
            assertTrue(desconexoes.await(10, TimeUnit.SECONDS));
            assertEquals(2, registry.quantidade());

            ana.enviar("continua");
            assertEquals("continua", ((Evento.Msg) bia.ler()).texto());
            assertEquals("continua", ((Evento.Msg) ana.ler()).texto());
        }
    }

    @Test
    void nomeEmUsoNaoEPermitidoEOClientePodeTentarOutro() throws Exception {
        int porta = subir();
        try (Cliente ana = new Cliente(porta); Cliente outra = new Cliente(porta)) {
            ana.entrarComo("ana");
            assertInstanceOf(Evento.Sistema.class, outra.ler());
            outra.enviar("ANA");
            assertInstanceOf(Evento.Erro.class, outra.ler());
            outra.enviar("ana2");
            assertInstanceOf(Evento.Ok.class, outra.ler());
        }
    }

    @Test
    void pararOServidorEncerraAsConexoesDosClientes() throws Exception {
        int porta = subir();
        try (Cliente ana = new Cliente(porta)) {
            ana.entrarComo("ana");
            servidor.parar();
            assertNull(ana.ler());
            assertEquals(0, registry.quantidade());
        }
    }
}
