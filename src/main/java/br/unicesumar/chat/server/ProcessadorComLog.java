package br.unicesumar.chat.server;

import br.unicesumar.chat.infra.LogArquivo;
import java.util.Locale;
import java.util.Objects;

/**
 * Decora um {@link ProcessadorEntrada} registrando o que acontece no log do
 * servidor. Regras de privacidade: comandos aparecem só pelo nome (os argumentos,
 * como senhas e o texto de {@code /msg}, nunca são gravados); linhas digitadas
 * antes do login não são gravadas; mensagens privadas só existem como comando.
 * Só o texto de mensagens públicas é registrado.
 */
public class ProcessadorComLog implements ProcessadorEntrada {

    private static final int TAMANHO_MAXIMO_DO_COMANDO = 20;

    private final ProcessadorEntrada interno;
    private final LogArquivo log;

    /** Envolve o processador real. */
    public ProcessadorComLog(ProcessadorEntrada interno, LogArquivo log) {
        this.interno = Objects.requireNonNull(interno, "interno");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void aoConectar(ClienteHandler origem) {
        log.info("Conexão aberta.");
        interno.aoConectar(origem);
    }

    @Override
    public void aoReceber(ClienteHandler origem, String linha) {
        boolean jaEntrou = origem.usuario().isPresent();
        String quem = jaEntrou ? origem.usuario().get().nome() : "cliente sem login";
        if (CommandService.ehComando(linha)) {
            log.info(quem + " usou /" + nomeDoComando(linha));
        } else if (jaEntrou) {
            log.info("[" + origem.sala() + "] " + quem + ": " + linha.strip());
        }
        interno.aoReceber(origem, linha);
        if (!jaEntrou && origem.usuario().isPresent()) {
            log.info(origem.usuario().get().nome() + " entrou no chat");
        }
    }

    @Override
    public void aoDesconectar(ClienteHandler origem) {
        if (origem.usuario().isPresent()) {
            log.info(origem.usuario().get().nome() + " saiu do chat");
        } else {
            log.info("Conexão encerrada sem login.");
        }
        interno.aoDesconectar(origem);
    }

    private static String nomeDoComando(String linha) {
        String corpo = linha.strip().substring(1);
        int fim = 0;
        while (fim < corpo.length() && !Character.isWhitespace(corpo.charAt(fim))) {
            fim++;
        }
        String nome = corpo.substring(0, Math.min(fim, TAMANHO_MAXIMO_DO_COMANDO));
        return nome.isEmpty() ? "?" : nome.toLowerCase(Locale.ROOT);
    }
}
