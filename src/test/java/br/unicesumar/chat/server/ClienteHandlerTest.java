package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.protocol.Protocolo;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Testes unitários: o handler usa streams em memória, sem rede. */
class ClienteHandlerTest {

    /** Processador falso que registra tudo o que acontece. */
    static class Registro implements ProcessadorEntrada {
        final List<String> eventos = Collections.synchronizedList(new ArrayList<>());
        final List<String> linhas = Collections.synchronizedList(new ArrayList<>());
        RuntimeException falharNaLinha;

        @Override
        public void aoConectar(ClienteHandler origem) {
            eventos.add("conectou");
        }

        @Override
        public void aoReceber(ClienteHandler origem, String linha) {
            if (falharNaLinha != null && linhas.isEmpty()) {
                linhas.add(linha);
                throw falharNaLinha;
            }
            linhas.add(linha);
            eventos.add("recebeu");
        }

        @Override
        public void aoDesconectar(ClienteHandler origem) {
            eventos.add("desconectou");
        }
    }

    /** Fecha-recurso que conta quantas vezes foi fechado. */
    static class ContaFechamentos implements AutoCloseable {
        final AtomicInteger vezes = new AtomicInteger();

        @Override
        public void close() {
            vezes.incrementAndGet();
        }
    }

    private static InputStream entrada(String texto) {
        return new ByteArrayInputStream(texto.getBytes(StandardCharsets.UTF_8));
    }

    private static List<Evento> eventosEscritos(ByteArrayOutputStream saida) {
        String texto = saida.toString(StandardCharsets.UTF_8);
        List<Evento> lista = new ArrayList<>();
        for (String linha : texto.split("\n")) {
            if (!linha.isEmpty()) {
                lista.add(Protocolo.decodificar(linha));
            }
        }
        return lista;
    }

    @Test
    void chamaAoConectarERecebeLinhasEDepoisAoDesconectarNessaOrdem() {
        Registro registro = new Registro();
        ClienteHandler h = new ClienteHandler(entrada("um\ndois\n"),
                new ByteArrayOutputStream(), new ContaFechamentos(), registro);
        h.run();
        assertEquals(List.of("conectou", "recebeu", "recebeu", "desconectou"), registro.eventos);
    }

    @Test
    void entregaCadaLinhaAoProcessadorNaOrdem() {
        Registro registro = new Registro();
        new ClienteHandler(entrada("um\ndois\ntres\n"), new ByteArrayOutputStream(),
                new ContaFechamentos(), registro).run();
        assertEquals(List.of("um", "dois", "tres"), registro.linhas);
    }

    @Test
    void decodificaOsEscapesDaLinhaDeEntrada() {
        Registro registro = new Registro();
        new ClienteHandler(entrada("a\\pb\\nc\n"), new ByteArrayOutputStream(),
                new ContaFechamentos(), registro).run();
        assertEquals(List.of("a|b\nc"), registro.linhas);
    }

    @Test
    void leEntradaEmUtf8() {
        Registro registro = new Registro();
        new ClienteHandler(entrada("ação 😀\n"), new ByteArrayOutputStream(),
                new ContaFechamentos(), registro).run();
        assertEquals(List.of("ação 😀"), registro.linhas);
    }

    @Test
    void linhaComEscapeInvalidoGeraErroAoClienteEOHandlerContinua() {
        Registro registro = new Registro();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        new ClienteHandler(entrada("ruim\\x\nboa\n"), saida, new ContaFechamentos(), registro).run();
        List<Evento> escritos = eventosEscritos(saida);
        assertEquals(1, escritos.size());
        assertInstanceOf(Evento.Erro.class, escritos.get(0));
        assertEquals(List.of("boa"), registro.linhas);
    }

    @Test
    void enviarEscreveOEventoCodificadoTerminadoEmQuebraDeLinha() {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ClienteHandler h = new ClienteHandler(entrada(""), saida, new ContaFechamentos(),
                new Registro());
        assertTrue(h.enviar(new Evento.Ok("feito")));
        assertEquals("OK|feito\n", saida.toString(StandardCharsets.UTF_8));
    }

    @Test
    void enviarEscreveEmUtf8() {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ClienteHandler h = new ClienteHandler(entrada(""), saida, new ContaFechamentos(),
                new Registro());
        h.enviar(new Evento.Sistema("coração"));
        assertEquals("SISTEMA|coração\n", saida.toString(StandardCharsets.UTF_8));
    }

    @Test
    void enviarDevolveFalseEMarcaInativoQuandoAEscritaFalha() {
        OutputStream quebrado = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("conexão caiu");
            }
        };
        ClienteHandler h = new ClienteHandler(entrada(""), quebrado, new ContaFechamentos(),
                new Registro());
        assertTrue(h.ativo());
        assertFalse(h.enviar(new Evento.Ok("x")));
        assertFalse(h.ativo());
    }

    @Test
    void quedaAbruptaNaLeituraNaoPropagaExcecaoEChamaAoDesconectarUmaVez() {
        InputStream quebrado = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("connection reset");
            }
        };
        Registro registro = new Registro();
        ContaFechamentos fecha = new ContaFechamentos();
        ClienteHandler h = new ClienteHandler(quebrado, new ByteArrayOutputStream(), fecha, registro);
        assertDoesNotThrow(h::run);
        assertEquals(List.of("conectou", "desconectou"), registro.eventos);
        assertEquals(1, fecha.vezes.get());
        assertFalse(h.ativo());
    }

    @Test
    void excecaoDoProcessadorNaoDerrubaOHandlerEAvisaOCliente() {
        Registro registro = new Registro();
        registro.falharNaLinha = new IllegalStateException("bug no serviço");
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        new ClienteHandler(entrada("quebra\nseguinte\n"), saida, new ContaFechamentos(),
                registro).run();
        assertEquals(List.of("quebra", "seguinte"), registro.linhas);
        assertTrue(eventosEscritos(saida).stream().anyMatch(e -> e instanceof Evento.Erro));
        assertEquals("desconectou", registro.eventos.get(registro.eventos.size() - 1));
    }

    @Test
    void fechaAConexaoAoTerminarEApenasUmaVez() {
        ContaFechamentos fecha = new ContaFechamentos();
        ClienteHandler h = new ClienteHandler(entrada("a\n"), new ByteArrayOutputStream(), fecha,
                new Registro());
        h.run();
        h.encerrar();
        h.encerrar();
        assertEquals(1, fecha.vezes.get());
    }

    @Test
    void encerrarMarcaOHandlerComoInativo() {
        ClienteHandler h = new ClienteHandler(entrada(""), new ByteArrayOutputStream(),
                new ContaFechamentos(), new Registro());
        h.encerrar();
        assertFalse(h.ativo());
    }

    @Test
    void enviarDepoisDeEncerrarDevolveFalse() {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ClienteHandler h = new ClienteHandler(entrada(""), saida, new ContaFechamentos(),
                new Registro());
        h.encerrar();
        assertFalse(h.enviar(new Evento.Ok("tarde")));
        assertEquals(0, saida.size());
    }

    @Test
    void escritasConcorrentesNaoMisturamLinhas() throws Exception {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ClienteHandler h = new ClienteHandler(entrada(""), saida, new ContaFechamentos(),
                new Registro());
        int threads = 8;
        int porThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            int id = t;
            pool.execute(() -> {
                for (int i = 0; i < porThread; i++) {
                    h.enviar(new Evento.Sistema("t" + id + "-" + i + " " + "x".repeat(200)));
                }
            });
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        List<Evento> escritos = eventosEscritos(saida);
        assertEquals(threads * porThread, escritos.size());
        assertTrue(escritos.stream().allMatch(e -> e instanceof Evento.Sistema));
    }
}
