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

/** Exportação para um arquivo ESCOLHIDO pelo usuário (botão Exportar da interface). */
class ExportadorHistoricoParaTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-21T15:30:00Z"), ZoneOffset.UTC);

    @TempDir
    Path pasta;

    @Test
    void gravaCabecalhoELinhasNoArquivoEscolhido() throws IOException {
        Path destino = pasta.resolve("meu-historico.txt");
        Path gravado = ExportadorHistorico.exportarPara(destino, "geral", List.of("[09:05] ana: olá"), RELOGIO);
        assertEquals(destino, gravado);
        assertEquals("Histórico da sala geral\nExportado em 2026-10-21 15:30:00\n\n[09:05] ana: olá\n",
                Files.readString(destino));
    }

    @Test
    void criaAsPastasDoCaminhoSeNaoExistirem() throws IOException {
        Path destino = pasta.resolve("a").resolve("b").resolve("saida.txt");
        ExportadorHistorico.exportarPara(destino, "geral", List.of("x"), RELOGIO);
        assertTrue(Files.exists(destino));
    }

    @Test
    void substituiOConteudoDeUmArquivoQueOUsuarioEscolheuSobrescrever() throws IOException {
        Path destino = Files.writeString(pasta.resolve("saida.txt"), "conteúdo antigo bem comprido ".repeat(20));
        ExportadorHistorico.exportarPara(destino, "geral", List.of("novo"), RELOGIO);
        String conteudo = Files.readString(destino);
        assertTrue(conteudo.contains("novo"));
        assertFalse(conteudo.contains("antigo"));
    }

    @Test
    void salaInvalidaOuArgumentosNulosLancamExcecao() {
        Path destino = pasta.resolve("saida.txt");
        assertThrows(IllegalArgumentException.class,
                () -> ExportadorHistorico.exportarPara(destino, "../x", List.of(), RELOGIO));
        assertThrows(NullPointerException.class,
                () -> ExportadorHistorico.exportarPara(null, "geral", List.of(), RELOGIO));
        assertThrows(NullPointerException.class,
                () -> ExportadorHistorico.exportarPara(destino, "geral", null, RELOGIO));
        assertThrows(NullPointerException.class,
                () -> ExportadorHistorico.exportarPara(destino, "geral", List.of(), null));
        assertFalse(Files.exists(destino));
    }

    @Test
    void destinoQueEUmaPastaLancaIOException() throws IOException {
        Path umaPasta = Files.createDirectory(pasta.resolve("sou-uma-pasta"));
        assertThrows(IOException.class,
                () -> ExportadorHistorico.exportarPara(umaPasta, "geral", List.of("x"), RELOGIO));
    }

    @Test
    void oitoThreadsGravandoNoMesmoArquivoDeixamSempreUmConteudoIntegro() throws Exception {
        Path destino = pasta.resolve("disputado.txt");
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<?>> futuros = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int id = t;
            futuros.add(pool.submit(() -> {
                largada.await();
                List<String> linhas = new ArrayList<>();
                for (int i = 0; i < 300; i++) {
                    linhas.add("t" + id + " linha " + i);
                }
                return ExportadorHistorico.exportarPara(destino, "geral", linhas, RELOGIO);
            }));
        }
        largada.countDown();
        for (Future<?> f : futuros) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        List<String> linhas = Files.readAllLines(destino);
        assertEquals(3 + 300, linhas.size());
        String dono = linhas.get(3).split(" ")[0];
        assertTrue(linhas.subList(3, linhas.size()).stream().allMatch(l -> l.startsWith(dono + " ")));
    }
}
