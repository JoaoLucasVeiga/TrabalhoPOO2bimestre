package br.unicesumar.chat.client.ui;

import br.unicesumar.chat.client.controller.ChatController;
import br.unicesumar.chat.client.controller.FabricaConexao;
import java.util.Objects;

/**
 * Monta o cliente gráfico: tela de login, janela do chat e o controlador que as liga. Ao entrar
 * mostra o chat; "Sair" e a perda da sessão voltam ao login; fechar a janela encerra o programa
 * (o que fazer ao encerrar é informado por quem cria, para poder ser testado).
 */
public class AplicacaoGui {

    private final Tema tema;
    private final FabricaConexao fabrica;
    private final Runnable aoEncerrar;
    private JanelaLogin login;
    private JanelaChat chat;
    private ChatController controlador;

    /**
     * Cria a aplicação (ainda sem janelas).
     *
     * @param aoEncerrar chamado na EDT quando o usuário fecha a janela do chat
     */
    public AplicacaoGui(Tema tema, FabricaConexao fabrica, Runnable aoEncerrar) {
        this.tema = Objects.requireNonNull(tema, "tema");
        this.fabrica = Objects.requireNonNull(fabrica, "fabrica");
        this.aoEncerrar = Objects.requireNonNull(aoEncerrar, "aoEncerrar");
    }

    /** Cria as janelas e mostra o login. Chame na EDT. */
    public void iniciar() {
        tema.instalarLookAndFeel();
        chat = new JanelaChat(tema, () -> login.setVisible(true), aoEncerrar);
        controlador = new ChatController(chat, fabrica);
        chat.definirControlador(controlador);
        login = new JanelaLogin(tema, controlador, () -> {
            login.setVisible(false);
            chat.abrir();
        });
        chat.aoSessaoPerdida(motivo -> {
            chat.setVisible(false);
            login.mostrarErro(motivo);
            login.setVisible(true);
        });
        login.setVisible(true);
    }

    /** Janela de login. */
    public JanelaLogin login() {
        return login;
    }

    /** Janela do chat. */
    public JanelaChat chat() {
        return chat;
    }

    /** Controlador da sessão. */
    public ChatController controlador() {
        return controlador;
    }
}
