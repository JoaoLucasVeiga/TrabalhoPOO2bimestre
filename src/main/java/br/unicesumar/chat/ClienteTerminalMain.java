package br.unicesumar.chat;

import br.unicesumar.chat.client.ClienteChat;
import br.unicesumar.chat.client.net.ConexaoSocket;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/** Cliente de terminal. Uso: {@code ClienteTerminalMain [servidor] [porta]} (padrão localhost 5000). */
public final class ClienteTerminalMain {

    private ClienteTerminalMain() {
    }

    /** Conecta ao servidor e conversa pelo terminal. */
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int porta = args.length > 1 ? Integer.parseInt(args[1]) : 5000;
        PrintStream tela = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        try {
            new ClienteChat(new ConexaoSocket(host, porta), teclado, tela).executar();
        } catch (IOException e) {
            tela.println("[erro] " + e.getMessage());
            System.exit(1);
        }
    }
}
