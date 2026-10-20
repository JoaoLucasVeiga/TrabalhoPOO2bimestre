package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Testes unitários da fila de escrita por cliente (destinos falsos, sem rede). */
class SaidaAssincronaTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static void escrever(SaidaAssincrona saida, String s) throws IOException {
        saida.write(bytes(s), 0, bytes(s).length);
    }

    @Test
    void entregaOsBytesAoDestinoNaOrdemEmQueForamEscritos() throws Exception {
        ByteArrayOutputStream destino = new ByteArrayOutputStream();
        SaidaAssincrona saida = new SaidaAssincrona(destino, 1024);
        escrever(saida, "um|");
        escrever(saida, "dois|");
        escrever(saida, "tres");
        saida.close();
        assertEquals("um|dois|tres", destino.toString(StandardCharsets.UTF_8));
    }

    @Test
    void escreverUmByteSoltoFunciona() throws Exception {
        ByteArrayOutputStream destino = new ByteArrayOutputStream();
        SaidaAssincrona saida = new SaidaAssincrona(destino, 1024);
        saida.write('x');
        saida.close();
        assertEquals("x", destino.toString(StandardCharsets.UTF_8));
    }

    @Test
    void copiaOArrayRecebidoPoisQuemEscreveReaproveitaOBuffer() throws Exception {
        DestinoTravavel destino = new DestinoTravavel();
        SaidaAssincrona saida = new SaidaAssincrona(destino, 1024);
        byte[] buffer = bytes("original");
        saida.write(buffer, 0, buffer.length);
        java.util.Arrays.fill(buffer, (byte) 'X'); // o chamador reutiliza o buffer
        destino.liberar();
        saida.close();
        assertEquals("original", destino.conteudo());
    }

    @Test
    void escreverNaoBloqueiaMesmoComODestinoTravado() throws Exception {
        DestinoTravavel destino = new DestinoTravavel();
        SaidaAssincrona saida = new SaidaAssincrona(destino, 1024);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<?> escrita = pool.submit(() -> {
            escrever(saida, "a".repeat(100));
            escrever(saida, "b".repeat(100));
            return null;
        });
        escrita.get(5, TimeUnit.SECONDS); // se bloqueasse, estouraria o tempo
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        destino.liberar();
        saida.close();
        assertEquals("a".repeat(100) + "b".repeat(100), destino.conteudo());
    }

    @Test
    void excederOLimiteLancaIOExceptionQueDizOQueAconteceu() throws Exception {
        DestinoTravavel destino = new DestinoTravavel();
        SaidaAssincrona saida = new SaidaAssincrona(destino, 100);
        escrever(saida, "a".repeat(60));
        IOException e = assertThrows(IOException.class, () -> escrever(saida, "b".repeat(60)));
        assertTrue(e.getMessage().toLowerCase().contains("lento"));
        assertTrue(saida.falhou());
        destino.liberar();
        saida.close();
    }

    @Test
    void depoisDeFalharPorLimiteTodasAsEscritasContinuamFalhando() throws Exception {
        DestinoTravavel destino = new DestinoTravavel();
        SaidaAssincrona saida = new SaidaAssincrona(destino, 10);
        assertThrows(IOException.class, () -> escrever(saida, "x".repeat(50)));
        destino.liberar();
        assertThrows(IOException.class, () -> escrever(saida, "y"));
        saida.close();
    }

    @Test
    void falhaDeEscritaNoDestinoFazAsProximasEscritasFalharem() throws Exception {
        OutputStream quebrado = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("conexão caiu");
            }
        };
        SaidaAssincrona saida = new SaidaAssincrona(quebrado, 1024);
        escrever(saida, "primeira");
        saida.close(); // espera a thread de escrita terminar
        assertTrue(saida.falhou());
        assertThrows(IOException.class, () -> escrever(saida, "segunda"));
    }

    @Test
    void escreverDepoisDeFecharLancaIOException() throws Exception {
        SaidaAssincrona saida = new SaidaAssincrona(new ByteArrayOutputStream(), 1024);
        saida.close();
        assertThrows(IOException.class, () -> escrever(saida, "tarde"));
    }

    @Test
    void fecharVariasVezesFechaODestinoUmaUnicaVez() throws Exception {
        DestinoTravavel destino = new DestinoTravavel();
        destino.liberar();
        SaidaAssincrona saida = new SaidaAssincrona(destino, 1024);
        saida.close();
        saida.close();
        assertEquals(1, destino.fechamentos.get());
    }

    @Test
    void fecharComODestinoTravadoRetornaAposOPrazoSemFicarPreso() throws Exception {
        DestinoTravavel destino = new DestinoTravavel();
        SaidaAssincrona saida = new SaidaAssincrona(destino, 1024, 50);
        escrever(saida, "pendente");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<?> fechamento = pool.submit(() -> {
            saida.close();
            return null;
        });
        fechamento.get(5, TimeUnit.SECONDS);
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(1, destino.fechamentos.get());
    }

    @Test
    void limiteInvalidoELancaExcecao() {
        assertThrows(IllegalArgumentException.class,
                () -> new SaidaAssincrona(new ByteArrayOutputStream(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new SaidaAssincrona(new ByteArrayOutputStream(), -5));
    }

    @Test
    void escritasConcorrentesChegamInteirasSemMisturar() throws Exception {
        ByteArrayOutputStream destino = new ByteArrayOutputStream();
        SaidaAssincrona saida = new SaidaAssincrona(destino, 1_000_000);
        int threads = 8;
        int porThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<?>> futuros = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int id = t;
            futuros.add(pool.submit(() -> {
                largada.await();
                for (int i = 0; i < porThread; i++) {
                    escrever(saida, "[" + id + ":" + "z".repeat(20) + "]");
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
        saida.close();
        String tudo = destino.toString(StandardCharsets.UTF_8);
        assertEquals(threads * porThread, tudo.split("\\]", -1).length - 1);
        assertTrue(tudo.matches("(\\[\\d:z{20}\\])+"));
    }
}
