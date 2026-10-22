package br.unicesumar.chat.infra;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Gravação de .txt em pasta temporária (só sistema de arquivos; sem rede nem banco). */
class ExportadorHistoricoTest {

    private static final Clock RELOGIO =
            Clock.fixed(Instant.parse("2026-10-21T15:30:00Z"), ZoneOffset.UTC);

    @TempDir
    Path pasta;

    @Test
    void gravaCabecalhoELinhasEmUtf8NoArquivoComNomeDaSalaEDaHora() throws IOException {
        Path exportacoes = pasta.resolve("exportacoes");
        Path arquivo = new ExportadorHistorico(exportacoes, RELOGIO)
                .exportar("geral", List.of("[09:05] ana: olá, ação", "[09:06] bia: oi | tudo"));
        assertEquals(exportacoes.resolve("geral-20261021-153000.txt"), arquivo);
        assertEquals("Histórico da sala geral\nExportado em 2026-10-21 15:30:00\n\n"
                + "[09:05] ana: olá, ação\n[09:06] bia: oi | tudo\n", Files.readString(arquivo));
    }

    @Test
    void criaAPastaSeNaoExistir() throws IOException {
        Path exportacoes = pasta.resolve("a").resolve("b");
        new ExportadorHistorico(exportacoes, RELOGIO).exportar("geral", List.of("x"));
        assertTrue(Files.isDirectory(exportacoes));
    }

    @Test
    void listaVaziaGeraArquivoSoComCabecalho() throws IOException {
        Path arquivo = new ExportadorHistorico(pasta, RELOGIO).exportar("geral", List.of());
        assertEquals("Histórico da sala geral\nExportado em 2026-10-21 15:30:00\n\n",
                Files.readString(arquivo));
    }

    @Test
    void exportarNoMesmoSegundoNaoSobrescreveOArquivoAnterior() throws IOException {
        ExportadorHistorico exportador = new ExportadorHistorico(pasta, RELOGIO);
        Path primeiro = exportador.exportar("geral", List.of("primeira"));
        Path segundo = exportador.exportar("geral", List.of("segunda"));
        assertNotEquals(primeiro, segundo);
        assertEquals("geral-20261021-153000-2.txt", segundo.getFileName().toString());
        assertTrue(Files.readString(primeiro).contains("primeira"));
        assertTrue(Files.readString(segundo).contains("segunda"));
    }

    @Test
    void nomeDaSalaInvalidoOuPerigosoELancaExcecaoENaoGravaNada() throws IOException {
        ExportadorHistorico exportador = new ExportadorHistorico(pasta, RELOGIO);
        for (String ruim : new String[] {null, "", "  ", "../fora", "a/b", "a\\b", "sala com espaco",
                "a".repeat(21)}) {
            assertThrows(IllegalArgumentException.class, () -> exportador.exportar(ruim, List.of("x")), ruim);
        }
        try (Stream<Path> arquivos = Files.list(pasta)) {
            assertEquals(0, arquivos.count());
        }
    }

    @Test
    void linhasNulasLancamExcecao() {
        assertThrows(NullPointerException.class,
                () -> new ExportadorHistorico(pasta, RELOGIO).exportar("geral", null));
    }

    @Test
    void pastaImpossivelDeCriarLancaIOException() throws IOException {
        Path umArquivo = Files.writeString(pasta.resolve("arquivo.txt"), "oi");
        ExportadorHistorico exportador = new ExportadorHistorico(umArquivo.resolve("sub"), RELOGIO);
        assertThrows(IOException.class, () -> exportador.exportar("geral", List.of("x")));
    }

    @Test
    void construtorRejeitaArgumentosNulos() {
        assertThrows(NullPointerException.class, () -> new ExportadorHistorico(null, RELOGIO));
        assertThrows(NullPointerException.class, () -> new ExportadorHistorico(pasta, null));
    }

    @Test
    void oitoThreadsExportandoAoMesmoTempoGeramOitoArquivosIntegrosSemSobrescrever() throws Exception {
        ExportadorHistorico exportador = new ExportadorHistorico(pasta, RELOGIO);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Path>> futuros = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            int id = t;
            futuros.add(pool.submit(() -> {
                largada.await();
                List<String> linhas = new ArrayList<>();
                for (int i = 0; i < 200; i++) {
                    linhas.add("t" + id + " linha " + i);
                }
                return exportador.exportar("geral", linhas);
            }));
        }
        largada.countDown();
        Set<Path> arquivos = new HashSet<>();
        for (Future<Path> f : futuros) {
            arquivos.add(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(threads, arquivos.size());
        for (Path arquivo : arquivos) {
            List<String> linhas = Files.readAllLines(arquivo);
            assertEquals(3 + 200, linhas.size());
            String dono = linhas.get(3).split(" ")[0]; // todas as linhas do arquivo são da mesma thread
            assertTrue(linhas.subList(3, linhas.size()).stream().allMatch(l -> l.startsWith(dono + " ")));
        }
    }
}
