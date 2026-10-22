package br.unicesumar.chat.client.net;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.client.ClienteChat;
import br.unicesumar.chat.persistence.memory.MensagemRepositoryEmMemoria;
import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.server.ChatService;
import br.unicesumar.chat.server.ClientRegistry;
import br.unicesumar.chat.server.ProcessadorChat;
import br.unicesumar.chat.server.Servidor;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Testes de INTEGRAÇÃO: sockets reais em loopback. */
@Tag("integracao")
class ConexaoSocketIntegracaoTest {

    private static final String LOCAL = "localhost";

    @Test
    void recebeEventosDoServidorDecodificados() throws Exception {
        try (ServerSocket escuta = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            CompletableFuture<Void> servidor = CompletableFuture.runAsync(() -> {
                try (Socket s = escuta.accept();
                        PrintWriter out = new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8)) {
                    out.print("SISTEMA|oi \\p mundo\n");
                    out.flush();
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
            ConexaoSocket c = new ConexaoSocket(LOCAL, escuta.getLocalPort());
            c.abrir();
            assertTrue(c.aberta());
            assertEquals(new Evento.Sistema("oi | mundo"), c.receber());
            assertNull(c.receber()); // servidor fechou
            servidor.get(10, TimeUnit.SECONDS);
            c.fechar();
            assertFalse(c.aberta());
        }
    }

    @Test
    void enviaTextoCodificadoComEscapeEUmaLinhaPorEnvio() throws Exception {
        try (ServerSocket escuta = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            CompletableFuture<String> lido = CompletableFuture.supplyAsync(() -> {
                try (Socket s = escuta.accept();
                        BufferedReader in = new BufferedReader(
                                new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
                    return in.readLine() + "||" + in.readLine();
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
            ConexaoSocket c = new ConexaoSocket(LOCAL, escuta.getLocalPort());
            c.abrir();
            c.enviar("a|b\nção");
            c.enviar("/users");
            c.fechar();
            assertEquals("a\\pb\\nção||/users", lido.get(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void servidorForaDoArLancaIOExceptionQueDizOQueFazer() throws IOException {
        int portaLivre;
        try (ServerSocket s = new ServerSocket(0)) {
            portaLivre = s.getLocalPort();
        }
        ConexaoSocket c = new ConexaoSocket(LOCAL, portaLivre);
        IOException e = assertThrows(IOException.class, c::abrir);
        assertTrue(e.getMessage().contains(LOCAL + ":" + portaLivre));
        assertTrue(e.getMessage().contains("Verifique se o servidor está em execução"));
        assertFalse(c.aberta());
    }

    @Test
    void depoisDeFecharEnviarFalhaEReceberDevolveNull() throws Exception {
        try (ServerSocket escuta = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            ConexaoSocket c = new ConexaoSocket(LOCAL, escuta.getLocalPort());
            c.abrir();
            c.fechar();
            c.fechar(); // idempotente
            assertThrows(IOException.class, () -> c.enviar("x"));
            assertNull(c.receber());
        }
    }

    @Test
    void clienteDeTerminalCompletoConversaComOServidorReal() throws Exception {
        ClientRegistry registry = new ClientRegistry();
        ChatService chat = new ChatService(new MensagemRepositoryEmMemoria(), registry,
                Clock.systemDefaultZone());
        Servidor servidor = new Servidor(0, registry, new ProcessadorChat(chat, registry));
        servidor.iniciar();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            PrintStream tela = new PrintStream(bytes, true, StandardCharsets.UTF_8);
            ClienteChat cliente = new ClienteChat(new ConexaoSocket(LOCAL, servidor.porta()),
                    new BufferedReader(new StringReader("ana\nolá | pessoal\n/users\n/quit\n")), tela);
            cliente.executar();
            String saida = bytes.toString(StandardCharsets.UTF_8);
            assertTrue(saida.contains("* Bem-vindo ao chat!"), saida);
            assertTrue(saida.contains("[ok] Você entrou como ana na sala geral."), saida);
            assertTrue(saida.matches("(?s).*\\[\\d\\d:\\d\\d\\] ana: olá \\| pessoal.*"), saida);
            assertTrue(saida.contains("Online em geral (1): ana"), saida);
            assertTrue(saida.contains("[ok] Até logo!"), saida);
            assertTrue(saida.contains("Conexão encerrada"), saida);
        } finally {
            servidor.parar();
        }
    }
}
