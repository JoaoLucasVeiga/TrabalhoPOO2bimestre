package br.unicesumar.chat;

import br.unicesumar.chat.client.ClienteChat;
import br.unicesumar.chat.client.net.OpcoesDeConexao;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Cliente de terminal. Uso: {@code ClienteTerminalMain [servidor] [porta] [--tls[=certificado.crt]]}
 * (padrão localhost 5000, sem TLS; com {@code --tls} confia no certificado {@code chat-tls.crt}).
 */
public final class ClienteTerminalMain {

    private ClienteTerminalMain() {
    }

    /** Conecta ao servidor e conversa pelo terminal. */
    public static void main(String[] args) {
        OpcoesDeConexao opcoes = OpcoesDeConexao.de(args);
        String host = opcoes.posicionais().size() > 0 ? opcoes.posicionais().get(0) : "localhost";
        int porta = opcoes.posicionais().size() > 1 ? Integer.parseInt(opcoes.posicionais().get(1)) : 5000;
        PrintStream tela = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        try {
            opcoes.validar();
            new ClienteChat(opcoes.conexao(host, porta), teclado, tela).executar();
        } catch (IOException e) {
            tela.println("[erro] " + e.getMessage());
            System.exit(1);
        }
    }
}
