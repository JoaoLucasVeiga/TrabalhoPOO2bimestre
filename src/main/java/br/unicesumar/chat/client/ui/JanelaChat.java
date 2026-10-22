package br.unicesumar.chat.client.ui;

import br.unicesumar.chat.client.controller.ChatController;
import br.unicesumar.chat.client.controller.ChatView;
import br.unicesumar.chat.client.controller.EstadoConexao;
import br.unicesumar.chat.client.controller.MensagemTela;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Janela principal do chat: a moldura do {@link PainelChat}. Mostra no título quantas mensagens
 * não lidas chegaram enquanto a janela estava sem foco ("(3) Chat"). Implementa {@link ChatView}
 * repassando ao painel, que leva tudo para a EDT.
 */
public class JanelaChat extends JFrame implements ChatView {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(JanelaChat.class.getName());
    private static final String TITULO = "Chat";

    private final PainelChat painel;
    private final transient Runnable aoSairParaLogin;
    private final transient Runnable aoFechar;
    private transient ChatController controlador;
    private int naoLidas;

    /**
     * Cria a janela (chame na EDT).
     *
     * @param aoSairParaLogin chamado na EDT depois de "Sair": volta para a tela de login
     * @param aoFechar        chamado na EDT depois de fechar a janela: encerra o programa
     */
    public JanelaChat(Tema tema, Runnable aoSairParaLogin, Runnable aoFechar) {
        super(TITULO);
        this.aoSairParaLogin = Objects.requireNonNull(aoSairParaLogin, "aoSairParaLogin");
        this.aoFechar = Objects.requireNonNull(aoFechar, "aoFechar");
        this.painel = new PainelChat(Objects.requireNonNull(tema, "tema"));
        setContentPane(painel);
        painel.aoSair(() -> sair(false));
        painel.aoMensagemDeOutro(this::contarNaoLida);
        setFocusTraversalPolicy(new OrdemDeFoco(painel.ordemDeFoco(), painel.componenteInicial()));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                sair(true);
            }
        });
        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                zerarNaoLidas();
            }
        });
        setMinimumSize(new Dimension(800, 560));
        setSize(1000, 680);
        setLocationRelativeTo(null);
    }

    /** Define o controlador (criado depois da janela, porque ele precisa dela como view). */
    public void definirControlador(ChatController controlador) {
        this.controlador = controlador;
        painel.definirControlador(controlador);
    }

    /** O que fazer quando a sessão termina sem o usuário pedir (recebe o motivo). */
    public void aoSessaoPerdida(Consumer<String> acao) {
        painel.aoSessaoPerdida(acao);
    }

    /** O painel com o conteúdo. */
    public PainelChat painel() {
        return painel;
    }

    /** Quantidade de mensagens não lidas no título. */
    public int naoLidas() {
        return naoLidas;
    }

    private void sair(boolean fechando) {
        ChatController c = controlador;
        Runnable depois = fechando ? aoFechar : aoSairParaLogin;
        if (c == null) {
            depois.run();
            return;
        }
        Segundo.plano(c::sair, () -> {
            setVisible(false);
            depois.run();
        }, falha -> {
            LOG.log(Level.WARNING, "Falha ao sair: " + falha.getMessage(), falha);
            setVisible(false);
            depois.run();
        });
    }

    private void contarNaoLida() {
        if (!isActive()) {
            naoLidas++;
            atualizarTitulo();
        }
    }

    private void zerarNaoLidas() {
        naoLidas = 0;
        atualizarTitulo();
    }

    private void atualizarTitulo() {
        setTitle(naoLidas > 0 ? "(" + naoLidas + ") " + TITULO : TITULO);
    }

    // ------------------------------------------------------------------ ChatView

    @Override
    public void definirUsuario(String nome) {
        painel.definirUsuario(nome);
    }

    @Override
    public void mostrarConexao(EstadoConexao estado) {
        painel.mostrarConexao(estado);
    }

    @Override
    public void adicionar(MensagemTela mensagem) {
        painel.adicionar(mensagem);
    }

    @Override
    public void substituirHistorico(List<MensagemTela> mensagens, String sala) {
        painel.substituirHistorico(mensagens, sala);
    }

    @Override
    public void inserirAnteriores(List<MensagemTela> anteriores) {
        painel.inserirAnteriores(anteriores);
    }

    @Override
    public void atualizarUsuarios(String sala, List<String> nomes) {
        painel.atualizarUsuarios(sala, nomes);
    }

    @Override
    public void atualizarSalas(List<String> salas, String atual) {
        painel.atualizarSalas(salas, atual);
    }

    @Override
    public void mostrarResultadoBusca(List<MensagemTela> resultados) {
        painel.mostrarResultadoBusca(resultados);
    }

    @Override
    public void mostrarErroDeBusca(String texto) {
        painel.mostrarErroDeBusca(texto);
    }

    @Override
    public void mostrarErro(String texto) {
        painel.mostrarErro(texto);
    }

    @Override
    public void mostrarAviso(String texto) {
        painel.mostrarAviso(texto);
    }

    @Override
    public void sessaoEncerrada(String motivo) {
        painel.sessaoEncerrada(motivo);
    }

    /** Mostra a janela e leva o foco para a caixa de mensagem. */
    public void abrir() {
        setVisible(true);
        SwingUtilities.invokeLater(() -> painel.componenteInicial().requestFocusInWindow());
    }
}
