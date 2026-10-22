package br.unicesumar.chat.infra;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Log do servidor em arquivo, uma linha por registro: {@code data [NÍVEL] texto}.
 * A escrita é {@code synchronized} (várias threads de cliente registram ao mesmo
 * tempo) e uma falha de gravação nunca derruba o servidor. Quem registra decide o
 * que entra: o log não recebe conteúdo de mensagens privadas nem senhas.
 */
public class LogArquivo {

    private static final Logger LOG = Logger.getLogger(LogArquivo.class.getName());
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path arquivo;
    private final Clock relogio;

    /** Cria o log; o arquivo e a pasta são criados na primeira gravação. */
    public LogArquivo(Path arquivo, Clock relogio) {
        this.arquivo = Objects.requireNonNull(arquivo, "arquivo");
        this.relogio = Objects.requireNonNull(relogio, "relogio");
    }

    /** Registra um fato normal. */
    public void info(String texto) {
        escrever("INFO", texto);
    }

    /** Registra algo que merece atenção, mas não é falha. */
    public void aviso(String texto) {
        escrever("AVISO", texto);
    }

    /** Registra uma falha. */
    public void erro(String texto) {
        escrever("ERRO", texto);
    }

    private synchronized void escrever(String nivel, String texto) {
        // caracteres de controle (inclusive \n e \r) viram espaço: ninguém forja linhas no log
        String limpo = texto == null ? "" : texto.replaceAll("\\p{Cntrl}", " ");
        String linha = LocalDateTime.now(relogio).format(DATA) + " [" + nivel + "] " + limpo + "\n";
        try {
            Path pasta = arquivo.toAbsolutePath().getParent();
            if (pasta != null) {
                Files.createDirectories(pasta);
            }
            try (BufferedWriter saida = Files.newBufferedWriter(arquivo, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                saida.write(linha);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Não foi possível gravar no log " + arquivo + ": " + e.getMessage(), e);
        }
    }
}
