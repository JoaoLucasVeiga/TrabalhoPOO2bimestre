package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.protocol.Protocolo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Testes de INTEGRAÇÃO: abrem sockets reais em loopback (não são unitários). */
@Tag("integracao")
class ServidorIntegracaoTest {

    /** Processador de teste: dá boas-vindas, responde OK a cada linha e conta desconexões. */
    static class ProcessadorDeTeste implements ProcessadorEntrada {
        final CountDownLatch desconexoes;
        final List<String> threads = Collections.synchronizedList(new ArrayList<>());

        ProcessadorDeTeste(int desconexoesEsperadas) {
            this.desconexoes = new CountDownLatch(desconexoesEsperadas);
        }

        @Override
        public void aoConectar(ClienteHandler origem) {
            threads.add(Thread.currentThread().getName());
            origem.enviar(new Evento.Sistema("bem-vindo"));
        }

        @Override
        public void aoReceber(ClienteHandler origem, String linha) {
            origem.enviar(new Evento.Ok("recebido: " + linha));
        }

        @Override
        public void aoDesconectar(ClienteHandler origem) {
            desconexoes.countDown();
        }
    }

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

        void enviar(String linha) {
            saida.print(Protocolo.codificarEntrada(linha) + "\n");
            saida.flush();
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private Servidor servidor;

    @AfterEach
    void parar() {
        if (servidor != null) {
            servidor.parar();
        }
    }

    private Servidor iniciar(ProcessadorEntrada processador) throws IOException {
        servidor = new Servidor(0, processador);
        servidor.iniciar();
        return servidor;
    }

    @Test
    void portaZeroEscolheUmaPortaLivre() throws IOException {
        iniciar(new ProcessadorDeTeste(0));
        assertTrue(servidor.porta() > 0);
        assertTrue(servidor.ativo());
    }

    @Test
    void atendeVariosClientesAoMesmoTempo() throws Exception {
        int n = 5;
        iniciar(new ProcessadorDeTeste(n));
        CountDownLatch todosConectados = new CountDownLatch(n);
        ExecutorService pool = Executors.newFixedThreadPool(n);
        List<Callable<String>> tarefas = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int id = i;
            tarefas.add(() -> {
                try (Cliente c = new Cliente(servidor.porta())) {
                    assertEquals(new Evento.Sistema("bem-vindo"), c.ler());
                    // só segue quando TODOS já estão conectados: prova a simultaneidade
                    todosConectados.countDown();
                    assertTrue(todosConectados.await(10, TimeUnit.SECONDS));
                    c.enviar("oi " + id);
                    return ((Evento.Ok) c.ler()).texto();
                }
            });
        }
        List<Future<String>> futuros = pool.invokeAll(tarefas);
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        for (int i = 0; i < n; i++) {
            assertEquals("recebido: oi " + i, futuros.get(i).get());
        }
    }

    @Test
    void cadaConexaoERecebidaEmUmaThreadDiferente() throws Exception {
        ProcessadorDeTeste processador = new ProcessadorDeTeste(3);
        iniciar(processador);
        try (Cliente a = new Cliente(servidor.porta());
                Cliente b = new Cliente(servidor.porta());
                Cliente c = new Cliente(servidor.porta())) {
            a.ler();
            b.ler();
            c.ler();
        }
        assertEquals(3, processador.threads.stream().distinct().count());
    }

    @Test
    void desconexaoAbruptaDeUmClienteNaoDerrubaOServidorNemOsOutros() throws Exception {
        ProcessadorDeTeste processador = new ProcessadorDeTeste(1);
        iniciar(processador);
        try (Cliente estavel = new Cliente(servidor.porta())) {
            estavel.ler();

            Cliente quedaBruta = new Cliente(servidor.porta());
            quedaBruta.ler();
            quedaBruta.socket.setSoLinger(true, 0); // fecha com RST, sem despedida
            quedaBruta.close();
            assertTrue(processador.desconexoes.await(10, TimeUnit.SECONDS));

            estavel.enviar("ainda aqui");
            assertEquals(new Evento.Ok("recebido: ainda aqui"), estavel.ler());
            try (Cliente novo = new Cliente(servidor.porta())) {
                assertEquals(new Evento.Sistema("bem-vindo"), novo.ler());
            }
        }
    }

    @Test
    void transportaTextoComDelimitadorQuebraDeLinhaEAcentos() throws Exception {
        iniciar(new ProcessadorDeTeste(1));
        try (Cliente c = new Cliente(servidor.porta())) {
            c.ler();
            c.enviar("a|b\nção");
            assertEquals(new Evento.Ok("recebido: a|b\nção"), c.ler());
        }
    }

    @Test
    void pararFechaOSocketDeEscutaENaoAceitaMaisConexoes() throws IOException {
        iniciar(new ProcessadorDeTeste(0));
        int porta = servidor.porta();
        servidor.parar();
        assertFalse(servidor.ativo());
        assertThrows(IOException.class, () -> new Socket(InetAddress.getLoopbackAddress(), porta));
    }

    @Test
    void pararDuasVezesNaoLancaExcecao() throws IOException {
        iniciar(new ProcessadorDeTeste(0));
        servidor.parar();
        assertDoesNotThrow(servidor::parar);
    }

    @Test
    void iniciarDuasVezesLancaIllegalState() throws IOException {
        iniciar(new ProcessadorDeTeste(0));
        assertThrows(IllegalStateException.class, servidor::iniciar);
    }

    @Test
    void portaOcupadaLancaIOExceptionComMensagemClara() throws IOException {
        iniciar(new ProcessadorDeTeste(0));
        Servidor outro = new Servidor(servidor.porta(), new ProcessadorDeTeste(0));
        IOException e = assertThrows(IOException.class, outro::iniciar);
        assertTrue(e.getMessage().contains(String.valueOf(servidor.porta())));
    }

    @Test
    void portaInvalidaERejeitadaNoConstrutor() {
        assertThrows(IllegalArgumentException.class, () -> new Servidor(-1, new ProcessadorDeTeste(0)));
        assertThrows(IllegalArgumentException.class, () -> new Servidor(70000, new ProcessadorDeTeste(0)));
    }
}
