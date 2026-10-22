package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.persistence.jdbc.JdbcMensagemRepository;
import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.protocol.Protocolo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Testes de INTEGRAÇÃO: sockets reais + SQLite real em arquivo temporário. */
@Tag("integracao")
class ServidorJdbcIntegracaoTest {

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
            return Protocolo.decodificar(entrada.readLine());
        }

        /** Próxima mensagem do chat, pulando os avisos SISTEMA de entrada e saída. */
        Evento.Msg lerMsg() throws IOException {
            Evento e = ler();
            while (e instanceof Evento.Sistema) {
                e = ler();
            }
            return assertInstanceOf(Evento.Msg.class, e);
        }

        void enviar(String texto) {
            saida.print(Protocolo.codificarEntrada(texto) + "\n");
            saida.flush();
        }

        /** Entra e devolve o que veio antes do OK (o histórico, se houver). */
        List<Evento.Msg> entrarComo(String nome) throws IOException {
            assertInstanceOf(Evento.Sistema.class, ler());
            enviar(nome);
            Evento e = ler();
            if (e instanceof Evento.Historico h) {
                assertInstanceOf(Evento.Ok.class, ler());
                return h.mensagens();
            }
            assertInstanceOf(Evento.Ok.class, e);
            return List.of();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    @TempDir
    Path pasta;

    private Servidor servidor;

    private Servidor subir(Path arquivoBanco) throws IOException {
        ConnectionFactory fabrica = ConnectionFactory.arquivo(arquivoBanco);
        fabrica.criarTabelas();
        ClientRegistry registry = new ClientRegistry();
        ChatService chat = new ChatService(new JdbcMensagemRepository(fabrica), registry,
                Clock.systemDefaultZone());
        servidor = new Servidor(0, registry, new ProcessadorChat(chat, registry));
        servidor.iniciar();
        return servidor;
    }

    @AfterEach
    void parar() {
        if (servidor != null) {
            servidor.parar();
        }
    }

    @Test
    void clienteQueConectaDepoisRecebeOHistoricoGravadoNoBanco() throws Exception {
        Servidor s = subir(pasta.resolve("chat.db"));
        try (Cliente ana = new Cliente(s.porta())) {
            ana.entrarComo("ana");
            ana.enviar("primeira | com delimitador");
            ana.enviar("segunda\ncom quebra");
            ana.enviar("terceira ação");
            for (int i = 0; i < 3; i++) {
                assertInstanceOf(Evento.Msg.class, ana.ler());
            }
        }
        try (Cliente bia = new Cliente(s.porta())) {
            List<Evento.Msg> historico = bia.entrarComo("bia");
            assertEquals(List.of("primeira | com delimitador", "segunda\ncom quebra", "terceira ação"),
                    historico.stream().map(Evento.Msg::texto).toList());
            assertEquals("ana", historico.get(0).remetente());
        }
    }

    @Test
    void historicoSobreviveAReiniciarOServidor() throws Exception {
        Path banco = pasta.resolve("persistente.db");
        Servidor primeiro = subir(banco);
        try (Cliente ana = new Cliente(primeiro.porta())) {
            ana.entrarComo("ana");
            ana.enviar("fica gravado");
            assertInstanceOf(Evento.Msg.class, ana.ler());
        }
        primeiro.parar();

        Servidor segundo = subir(banco);
        try (Cliente bia = new Cliente(segundo.porta())) {
            List<Evento.Msg> historico = bia.entrarComo("bia");
            assertEquals(1, historico.size());
            assertEquals("fica gravado", historico.get(0).texto());
        }
    }

    @Test
    void variosClientesEnviandoAoMesmoTempoEntregamTudoEGravam() throws Exception {
        Servidor s = subir(pasta.resolve("carga.db"));
        int clientes = 5;
        int porCliente = 10;
        List<Cliente> abertos = new java.util.ArrayList<>();
        try {
            for (int i = 0; i < clientes; i++) {
                Cliente c = new Cliente(s.porta());
                c.entrarComo("user" + i);
                abertos.add(c);
            }
            java.util.concurrent.ExecutorService pool =
                    java.util.concurrent.Executors.newFixedThreadPool(clientes);
            java.util.concurrent.CountDownLatch largada = new java.util.concurrent.CountDownLatch(1);
            List<java.util.concurrent.Future<?>> futuros = new java.util.ArrayList<>();
            for (int i = 0; i < clientes; i++) {
                Cliente c = abertos.get(i);
                int id = i;
                futuros.add(pool.submit(() -> {
                    largada.await();
                    for (int k = 0; k < porCliente; k++) {
                        c.enviar("u" + id + "-" + k);
                    }
                    int lidas = 0;
                    while (lidas < clientes * porCliente) {
                        c.lerMsg();
                        lidas++;
                    }
                    return null;
                }));
            }
            largada.countDown();
            for (java.util.concurrent.Future<?> f : futuros) {
                f.get(60, java.util.concurrent.TimeUnit.SECONDS);
            }
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            for (Cliente c : abertos) {
                c.close();
            }
        }
        try (Cliente novo = new Cliente(s.porta())) {
            List<Evento.Msg> historico = novo.entrarComo("novato");
            assertEquals(ChatService.HISTORICO_INICIAL, historico.size());
        }
    }
}
