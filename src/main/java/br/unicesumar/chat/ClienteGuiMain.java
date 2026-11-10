package br.unicesumar.chat;

import br.unicesumar.chat.client.net.OpcoesDeConexao;
import br.unicesumar.chat.client.ui.AplicacaoGui;
import br.unicesumar.chat.client.ui.Tema;
import java.io.IOException;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Cliente gráfico (Swing + FlatLaf). A montagem das janelas está em {@link AplicacaoGui}. Aceita
 * {@code --tls[=certificado.crt]} para falar com um servidor iniciado com TLS.
 */
public final class ClienteGuiMain {

    private ClienteGuiMain() {
    }

    /** Ponto de entrada: tudo é montado na thread da interface (EDT). */
    public static void main(String[] args) {
        OpcoesDeConexao opcoes = OpcoesDeConexao.de(args);
        try {
            opcoes.validar();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Chat", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        SwingUtilities.invokeLater(() -> new AplicacaoGui(new Tema(Tema.Modo.CLARO),
                opcoes::conexao, () -> System.exit(0)).iniciar());
    }
}
