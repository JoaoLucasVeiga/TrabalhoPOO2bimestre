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
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Teste de CARGA (integração): 50 clientes reais por socket, todos enviando ao mesmo tempo, com o banco
 * SQLite de verdade. Prova que nenhuma mensagem se perde nem se duplica, que a ordem é a mesma para todos
 * e que tudo foi gravado. Sem {@code Thread.sleep}: a largada e a espera usam latches e {@code Future}.
 */
@Tag("integracao")
class CargaIntegracaoTest {

    private static final int CLIENTES = 50;
    private static final int MENSAGENS_POR_CLIENTE = 20;
    private static final int TOTAL = CLIENTES * MENSAGENS_POR_CLIENTE;

    @TempDir
    Path pasta;

    private final ClientRegistry registry = new ClientRegistry();
    private Servidor servidor;
    private ExecutorService executor;

    /** Um cliente por socket: lê as linhas do servidor e envia texto codificado. */
    private static final class Cliente implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader entrada;
        private final PrintWriter saida;

        Cliente(int porta) throws IOException {
            socket = new Socket(InetAddress.getLoopbackAddress(), porta);
            socket.setSoTimeout(60_000);
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
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

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    @AfterEach
    void parar() {
        if (executor != null) {
            executor.shutdownNow();
        }
        if (servidor != null) {
            servidor.parar();
        }
    }

    @Test
    @Timeout(120)
    void cinquentaClientesEnviandoAoMesmoTempoNaoPerdemNemDuplicamNemEmbaralham() throws Exception {
        ConnectionFactory fabrica = ConnectionFactory.arquivo(pasta.resolve("carga.db"));
        fabrica.criarTabelas();
        JdbcMensagemRepository repositorio = new JdbcMensagemRepository(fabrica);
        ChatService chat = new ChatService(repositorio, registry, Clock.systemDefaultZone());
        servidor = new Servidor(0, registry, new ProcessadorChat(chat, registry));
        servidor.iniciar();
        int porta = servidor.porta();

        executor = Executors.newFixedThreadPool(CLIENTES);
        CountDownLatch todosEntraram = new CountDownLatch(CLIENTES);
        CountDownLatch largada = new CountDownLatch(1);
        long[] inicio = new long[1];

        List<Future<List<String>>> resultados = new ArrayList<>();
        for (int i = 0; i < CLIENTES; i++) {
            final String nome = String.format("c%02d", i);
            resultados.add(executor.submit(() -> {
                try (Cliente cliente = new Cliente(porta)) {
                    cliente.ler(); // boas-vindas
                    cliente.enviar(nome);
                    cliente.ler(); // OK da entrada
                    todosEntraram.countDown();
                    largada.await();
                    for (int m = 1; m <= MENSAGENS_POR_CLIENTE; m++) {
                        cliente.enviar(String.format("%s:%03d", nome, m));
                    }
                    List<String> recebidas = new ArrayList<>(TOTAL);
                    while (recebidas.size() < TOTAL) {
                        Evento e = cliente.ler();
                        assertNotNull(e, nome + " perdeu a conexão com " + recebidas.size() + " mensagens");
                        if (e instanceof Evento.Msg msg) {
                            recebidas.add(msg.texto());
                        }
                    }
                    return recebidas;
                }
            }));
        }
        assertTrue(todosEntraram.await(60, TimeUnit.SECONDS), "nem todos os clientes entraram");
        inicio[0] = System.nanoTime();
        largada.countDown();

        List<List<String>> porCliente = new ArrayList<>();
        for (Future<List<String>> f : resultados) {
            porCliente.add(f.get(90, TimeUnit.SECONDS));
        }
        Duration duracao = Duration.ofNanos(System.nanoTime() - inicio[0]);

        // 1) cada cliente recebeu exatamente TOTAL mensagens, sem repetir nenhuma
        Set<String> esperadas = new HashSet<>();
        for (int i = 0; i < CLIENTES; i++) {
            for (int m = 1; m <= MENSAGENS_POR_CLIENTE; m++) {
                esperadas.add(String.format("c%02d:%03d", i, m));
            }
        }
        for (List<String> recebidas : porCliente) {
            assertEquals(TOTAL, recebidas.size());
            assertEquals(esperadas, new HashSet<>(recebidas), "mensagem perdida, duplicada ou corrompida");
        }
        // 2) todos veem exatamente a mesma ordem
        List<String> referencia = porCliente.get(0);
        for (List<String> recebidas : porCliente) {
            assertEquals(referencia, recebidas, "ordens diferentes entre clientes");
        }
        // 3) a ordem de cada remetente é preservada dentro da ordem global
        for (int i = 0; i < CLIENTES; i++) {
            String prefixo = String.format("c%02d:", i);
            List<String> doRemetente = referencia.stream().filter(t -> t.startsWith(prefixo)).toList();
            for (int m = 0; m < doRemetente.size(); m++) {
                assertEquals(String.format("%s%03d", prefixo, m + 1), doRemetente.get(m));
            }
        }
        // 4) tudo foi gravado no banco, na mesma ordem
        List<String> gravadas = repositorio.buscarUltimas("geral", TOTAL).stream()
                .map(m -> m.conteudo()).toList();
        assertEquals(referencia, gravadas, "o banco não bate com o que foi transmitido");

        double segundos = Math.max(duracao.toMillis() / 1000.0, 0.001);
        System.out.printf("CARGA: %d clientes x %d mensagens = %d mensagens, %d entregas em %.2f s "
                + "(%.0f mensagens/s gravadas, %.0f entregas/s)%n", CLIENTES, MENSAGENS_POR_CLIENTE, TOTAL,
                (long) TOTAL * CLIENTES, segundos, TOTAL / segundos, (double) TOTAL * CLIENTES / segundos);
    }
}
