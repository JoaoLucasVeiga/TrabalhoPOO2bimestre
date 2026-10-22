package br.unicesumar.chat.client.ui;

import br.unicesumar.chat.client.controller.ChatController;
import java.util.Objects;
import javax.swing.JFrame;

/** Janela da tela 1: conexão e login. A lógica fica no {@link PainelLogin} e no {@link FluxoDeLogin}. */
public class JanelaLogin extends JFrame {

    private static final long serialVersionUID = 1L;

    private final PainelLogin painel;
    private FluxoDeLogin fluxo;

    /**
     * Cria a janela.
     *
     * @param aoEntrar chamado (na EDT) quando o login der certo
     */
    public JanelaLogin(Tema tema, ChatController controlador, Runnable aoEntrar) {
        super("Chat Multiusuário - Entrar");
        Objects.requireNonNull(tema, "tema");
        this.painel = new PainelLogin(tema, (h, p, u, s, c) -> fluxo.entrar(h, p, u, s, c));
        this.fluxo = new FluxoDeLogin(painel, controlador, aoEntrar);
        setContentPane(painel);
        getRootPane().setDefaultButton(painel.botaoEntrar());
        setFocusTraversalPolicy(new OrdemDeFoco(painel.ordemDeFoco(), painel.componenteInicial()));
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(null);
    }

    /** Mostra um erro em linha (por exemplo, "a conexão caiu"). */
    public void mostrarErro(String texto) {
        painel.definirConectando(false, false);
        painel.mostrarErro(texto);
    }

    /** O painel de login. */
    public PainelLogin painel() {
        return painel;
    }
}
