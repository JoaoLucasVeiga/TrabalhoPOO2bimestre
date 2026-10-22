package br.unicesumar.chat.infra;

import br.unicesumar.chat.model.Sala;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Grava o histórico de uma sala em um arquivo .txt. O método de gravação é
 * {@code synchronized}: escolher um nome livre e criar o arquivo é uma etapa só,
 * então duas exportações simultâneas nunca escrevem no mesmo arquivo.
 */
public class ExportadorHistorico {

    private static final DateTimeFormatter NOME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter CABECALHO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path pasta;
    private final Clock relogio;

    /**
     * Cria o exportador.
     *
     * @param pasta  onde os arquivos são gravados (criada se não existir)
     * @param relogio fonte da data e hora usadas no nome e no cabeçalho
     */
    public ExportadorHistorico(Path pasta, Clock relogio) {
        this.pasta = Objects.requireNonNull(pasta, "pasta");
        this.relogio = Objects.requireNonNull(relogio, "relogio");
    }

    /**
     * Grava as linhas em {@code <sala>-aaaaMMdd-HHmmss.txt}. Se o nome já existir,
     * acrescenta {@code -2}, {@code -3}... e nunca sobrescreve.
     *
     * @param sala   nome da sala (letras sem acento, números, '_'; vira minúsculo)
     * @param linhas já formatadas, uma por linha do arquivo
     * @return o arquivo criado
     * @throws IllegalArgumentException se o nome da sala for inválido
     * @throws IOException              se a pasta ou o arquivo não puderem ser gravados
     */
    public synchronized Path exportar(String sala, List<String> linhas) throws IOException {
        Objects.requireNonNull(linhas, "linhas");
        String nomeDaSala = new Sala(sala).nome(); // valida: sem barras, espaços nem "..", até 20 caracteres
        Files.createDirectories(pasta);
        LocalDateTime agora = LocalDateTime.now(relogio);
        String base = nomeDaSala + "-" + agora.format(NOME);
        Path arquivo = pasta.resolve(base + ".txt");
        for (int n = 2; Files.exists(arquivo); n++) {
            arquivo = pasta.resolve(base + "-" + n + ".txt");
        }
        try (BufferedWriter saida = Files.newBufferedWriter(arquivo, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW)) {
            saida.write("Histórico da sala " + nomeDaSala + "\n");
            saida.write("Exportado em " + agora.format(CABECALHO) + "\n\n");
            for (String linha : linhas) {
                saida.write(linha + "\n");
            }
        }
        return arquivo;
    }

    /**
     * Grava as linhas no arquivo que o usuário escolheu (substitui o conteúdo se ele já existir; a
     * confirmação é responsabilidade de quem escolhe o arquivo). Cria as pastas que faltarem.
     * Estático e {@code synchronized}: duas gravações no mesmo arquivo nunca se misturam.
     *
     * @param sala nome da sala, só para o cabeçalho (mesma validação de {@link #exportar})
     * @throws IllegalArgumentException se o nome da sala for inválido
     * @throws IOException              se o arquivo não puder ser gravado
     */
    public static synchronized Path exportarPara(Path arquivo, String sala, List<String> linhas, Clock relogio)
            throws IOException {
        Objects.requireNonNull(arquivo, "arquivo");
        Objects.requireNonNull(linhas, "linhas");
        Objects.requireNonNull(relogio, "relogio");
        String nomeDaSala = new Sala(sala).nome();
        Path pasta = arquivo.toAbsolutePath().getParent();
        if (pasta != null) {
            Files.createDirectories(pasta);
        }
        LocalDateTime agora = LocalDateTime.now(relogio);
        try (BufferedWriter saida = Files.newBufferedWriter(arquivo, StandardCharsets.UTF_8)) {
            saida.write("Histórico da sala " + nomeDaSala + "\n");
            saida.write("Exportado em " + agora.format(CABECALHO) + "\n\n");
            for (String linha : linhas) {
                saida.write(linha + "\n");
            }
        }
        return arquivo;
    }
}
