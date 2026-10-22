package br.unicesumar.chat.infra;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogArquivoTest {

    private static final Clock RELOGIO =
            Clock.fixed(Instant.parse("2026-10-21T15:30:00Z"), ZoneOffset.UTC);

    @TempDir
    Path pasta;

    @Test
    void gravaUmaLinhaComDataNivelETexto() throws IOException {
        Path arquivo = pasta.resolve("servidor.log");
        LogArquivo log = new LogArquivo(arquivo, RELOGIO);
        log.info("ana entrou no chat");
        log.aviso("cliente lento");
        log.erro("falha no banco");
        assertEquals(List.of("2026-10-21 15:30:00 [INFO] ana entrou no chat",
                "2026-10-21 15:30:00 [AVISO] cliente lento",
                "2026-10-21 15:30:00 [ERRO] falha no banco"), Files.readAllLines(arquivo));
    }

    @Test
    void acrescentaSemApagarOQueJaExistia() throws IOException {
        Path arquivo = pasta.resolve("servidor.log");
        new LogArquivo(arquivo, RELOGIO).info("primeira execução");
        new LogArquivo(arquivo, RELOGIO).info("segunda execução");
        assertEquals(2, Files.readAllLines(arquivo).size());
    }

    @Test
    void criaAPastaDoArquivoSeNaoExistir() {
        Path arquivo = pasta.resolve("logs").resolve("servidor.log");
        new LogArquivo(arquivo, RELOGIO).info("oi");
        assertTrue(Files.exists(arquivo));
    }

    @Test
    void quebrasDeLinhaNoTextoNaoForjamLinhasNovasNoLog() throws IOException {
        Path arquivo = pasta.resolve("servidor.log");
        new LogArquivo(arquivo, RELOGIO).info("falso\n2026-01-01 00:00:00 [INFO] invasor\r\ntab\taqui");
        List<String> linhas = Files.readAllLines(arquivo);
        assertEquals(1, linhas.size());
        assertTrue(linhas.get(0).contains("invasor"));
    }

    @Test
    void preservaAcentos() throws IOException {
        Path arquivo = pasta.resolve("servidor.log");
        new LogArquivo(arquivo, RELOGIO).info("coração, ação");
        assertTrue(Files.readString(arquivo).contains("coração, ação"));
    }

    @Test
    void falhaAoGravarNaoDerrubaQuemChamaEOLogContinuaDepois() throws IOException {
        Path arquivo = pasta.resolve("servidor.log");
        Files.createDirectory(arquivo); // um diretório no lugar do arquivo: gravar vai falhar
        LogArquivo log = new LogArquivo(arquivo, RELOGIO);
        assertDoesNotThrow(() -> log.info("não consegue gravar"));
    }

    @Test
    void textoNuloViraTextoVazioSemExcecao() throws IOException {
        Path arquivo = pasta.resolve("servidor.log");
        assertDoesNotThrow(() -> new LogArquivo(arquivo, RELOGIO).info(null));
    }

    @Test
    void construtorRejeitaArgumentosNulos() {
        assertThrows(NullPointerException.class, () -> new LogArquivo(null, RELOGIO));
        assertThrows(NullPointerException.class, () -> new LogArquivo(pasta.resolve("x.log"), null));
    }

    @Test
    void oitoThreadsEscrevendoAoMesmoTempoNaoMisturamNemPerdemLinhas() throws Exception {
        Path arquivo = pasta.resolve("servidor.log");
        LogArquivo log = new LogArquivo(arquivo, RELOGIO);
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
                    log.info("t" + id + "-" + i + " " + "x".repeat(100));
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

        List<String> linhas = Files.readAllLines(arquivo);
        assertEquals(threads * porThread, linhas.size());
        assertTrue(linhas.stream().allMatch(l -> l.matches("2026-10-21 15:30:00 \\[INFO\\] t\\d-\\d+ x{100}")));
        assertEquals(threads * porThread, linhas.stream().distinct().count());
    }
}
