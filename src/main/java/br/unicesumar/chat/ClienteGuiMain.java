package br.unicesumar.chat;

import br.unicesumar.chat.client.net.ConexaoSocket;
import br.unicesumar.chat.client.ui.AplicacaoGui;
import br.unicesumar.chat.client.ui.Tema;
import javax.swing.SwingUtilities;

/** Cliente gráfico (Swing + FlatLaf). A montagem das janelas está em {@link AplicacaoGui}. */
public final class ClienteGuiMain {

    private ClienteGuiMain() {
    }

    /** Ponto de entrada: tudo é montado na thread da interface (EDT). */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AplicacaoGui(new Tema(Tema.Modo.CLARO),
                (host, porta) -> new ConexaoSocket(host, porta), () -> System.exit(0)).iniciar());
    }
}
