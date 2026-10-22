package br.unicesumar.chat.client;

import br.unicesumar.chat.client.net.ConexaoChat;
import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.protocol.ProtocoloException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Objects;

/**
 * Cliente de terminal. Uma thread lê os eventos do servidor e os imprime; a
 * thread que chamou {@link #executar()} lê o teclado e envia. Fala com o
 * servidor só através de {@link ConexaoChat}: não conhece banco nem Repository.
 */
public class ClienteChat {

    private static final long PRAZO_PADRAO_MS = 3000;

    private final ConexaoChat conexao;
    private final BufferedReader teclado;
    private final PrintStream tela;
    private final long prazoMs;

    /** Cria o cliente com o prazo padrão de espera pela despedida do servidor. */
    public ClienteChat(ConexaoChat conexao, BufferedReader teclado, PrintStream tela) {
        this(conexao, teclado, tela, PRAZO_PADRAO_MS);
    }

    /**
     * Cria o cliente.
     *
     * @param prazoMs quanto esperar, ao sair, o servidor responder ao {@code /quit}
     */
    public ClienteChat(ConexaoChat conexao, BufferedReader teclado, PrintStream tela, long prazoMs) {
        this.conexao = Objects.requireNonNull(conexao, "conexao");
        this.teclado = Objects.requireNonNull(teclado, "teclado");
        this.tela = Objects.requireNonNull(tela, "tela");
        this.prazoMs = prazoMs;
    }

    /**
     * Conecta e conversa até o usuário sair ({@code /quit} ou fim do teclado) ou o
     * servidor encerrar. Fecha a conexão ao terminar.
     *
     * @throws IOException se não for possível conectar (mensagem diz o que fazer)
     */
    public void executar() throws IOException {
        conexao.abrir();
        Thread leitor = new Thread(this::receberEventos, "cliente-leitor");
        leitor.setDaemon(true);
        leitor.start();
        try {
            enviarDigitado();
            aguardar(leitor, prazoMs);
        } finally {
            conexao.fechar();
            aguardar(leitor, 1000);
        }
    }

    private void enviarDigitado() {
        try {
            String linha;
            while ((linha = teclado.readLine()) != null) {
                String limpa = linha.strip();
                if (limpa.isEmpty()) {
                    continue;
                }
                conexao.enviar(linha);
                if (limpa.equalsIgnoreCase("/quit")) {
                    return;
                }
            }
            conexao.enviar("/quit"); // fim do teclado: sai com educação
        } catch (IOException e) {
            tela.println("[erro] " + e.getMessage());
        }
    }

    private void receberEventos() {
        while (true) {
            try {
                Evento evento = conexao.receber();
                if (evento == null) {
                    tela.println("Conexão encerrada. Pressione Enter para sair.");
                    return;
                }
                tela.println(FormatadorTerminal.formatar(evento));
            } catch (ProtocoloException e) {
                tela.println("[erro] Mensagem do servidor ilegível: " + e.getMessage());
            } catch (IOException e) {
                tela.println("[erro] " + e.getMessage());
                return;
            }
        }
    }

    private static void aguardar(Thread thread, long ms) {
        try {
            thread.join(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
