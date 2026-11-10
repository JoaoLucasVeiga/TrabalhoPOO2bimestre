package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Regra de arquitetura ("sem God Classes"): nenhuma classe do pacote {@code server} passa de 300 linhas.
 * Quando uma classe cresce além disso, é sinal de que ela ganhou responsabilidade demais e deve ser dividida.
 */
class TamanhoDasClassesTest {

    private static final int LIMITE_DE_LINHAS = 300;
    private static final Path PASTA = Path.of("src", "main", "java", "br", "unicesumar", "chat", "server");

    @Test
    void nenhumaClasseDoServidorPassaDoLimiteDeLinhas() throws IOException {
        List<String> grandes = new ArrayList<>();
        try (Stream<Path> arquivos = Files.list(PASTA)) {
            for (Path arquivo : arquivos.filter(p -> p.toString().endsWith(".java")).toList()) {
                long linhas = Files.readAllLines(arquivo).size();
                if (linhas > LIMITE_DE_LINHAS) {
                    grandes.add(arquivo.getFileName() + " (" + linhas + " linhas)");
                }
            }
        }
        assertTrue(grandes.isEmpty(), "Classes acima de " + LIMITE_DE_LINHAS + " linhas: " + grandes);
    }
}
