package br.unicesumar.chat.client.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import br.unicesumar.chat.client.controller.ChatController;
import br.unicesumar.chat.client.controller.EstadoConexao;
import br.unicesumar.chat.client.controller.MensagemTela;
import br.unicesumar.chat.client.net.ConexaoFalsa;
import br.unicesumar.chat.client.ui.Tema.Modo;
import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.protocol.Mensagens;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Testes das JANELAS de verdade (JFrame e JDialog). Precisam de uma tela: ficam ignorados no modo
 * sem display e rodam com {@code xvfb-run -a mvn test}.
 */
@Tag("gui")
class JanelasGuiTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 22, 9, 5);

    private final Tema tema = new Tema(Modo.CLARO);
    private JanelaChat chat;
    private JanelaLogin login;

    @BeforeEach
    void preparar() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "sem display (rode com xvfb-run)");
    }

    @AfterEach
    void limpar() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            if (chat != null) {
                chat.dispose();
            }
            if (login != null) {
                login.dispose();
            }
        });
    }

    private void edt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    private void criarJanelas(AtomicInteger saidas, AtomicInteger fechamentos) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            chat = new JanelaChat(tema, saidas::incrementAndGet, fechamentos::incrementAndGet);
            ChatController c = new ChatController(chat, (h, p) -> new ConexaoFalsa());
            chat.definirControlador(c);
            login = new JanelaLogin(tema, c, () -> { });
        });
    }

    private static MensagemTela deOutro(String texto) {
        return new MensagemTela(MensagemTela.Tipo.PUBLICA, "bia", null, T, texto, false);
    }

    @Test
    void janelaDeChatTemTamanhoMinimoETituloChat() throws Exception {
        criarJanelas(new AtomicInteger(), new AtomicInteger());
        assertEquals("Chat", chat.getTitle());
        assertTrue(chat.getMinimumSize().width >= 800 && chat.getMinimumSize().height >= 560);
        assertTrue(chat.getWidth() >= 800 && chat.getHeight() >= 560);
    }

    @Test
    void titulloMostraNaoLidasSoComAJanelaSemFocoEZeraAoGanharFoco() throws Exception {
        criarJanelas(new AtomicInteger(), new AtomicInteger());
        chat.adicionar(deOutro("um"));
        chat.adicionar(deOutro("dois"));
        chat.adicionar(deOutro("tres"));
        edt();
        assertEquals("(3) Chat", chat.getTitle());
        assertEquals(3, chat.naoLidas());
        // o AWT descarta foco sintético em janela não visível: chama os ouvintes de foco da janela direto
        SwingUtilities.invokeAndWait(() -> {
            WindowEvent ganhou = new WindowEvent(chat, WindowEvent.WINDOW_GAINED_FOCUS);
            for (var ouvinte : chat.getWindowFocusListeners()) {
                ouvinte.windowGainedFocus(ganhou);
            }
        });
        assertEquals("Chat", chat.getTitle());
        assertEquals(0, chat.naoLidas());
    }

    @Test
    void minhasMensagensEAvisosNaoContamComoNaoLidos() throws Exception {
        criarJanelas(new AtomicInteger(), new AtomicInteger());
        chat.adicionar(new MensagemTela(MensagemTela.Tipo.PUBLICA, "ana", null, T, "minha", true));
        chat.adicionar(MensagemTela.sistema("bia entrou", T));
        edt();
        assertEquals("Chat", chat.getTitle());
    }

    @Test
    void botaoSairChamaOCallbackDeVoltarAoLoginEFecharChamaOCallbackDeEncerrar() throws Exception {
        AtomicInteger saidas = new AtomicInteger();
        AtomicInteger fechamentos = new AtomicInteger();
        criarJanelas(saidas, fechamentos);
        SwingUtilities.invokeAndWait(() -> chat.painel().botaoSair().doClick());
        aguardar(() -> saidas.get() == 1);
        SwingUtilities.invokeAndWait(() -> chat.dispatchEvent(new WindowEvent(chat, WindowEvent.WINDOW_CLOSING)));
        aguardar(() -> fechamentos.get() == 1);
        assertEquals(1, saidas.get());
    }

    private void aguardar(java.util.function.BooleanSupplier condicao) throws Exception {
        long fim = System.nanoTime() + 5_000_000_000L;
        while (!condicao.getAsBoolean() && System.nanoTime() < fim) {
            edt();
            Thread.yield();
        }
        assertTrue(condicao.getAsBoolean());
    }

    @Test
    void focoInicialDoChatEmCaixaDeMensagemEDoLoginEmUsuario() throws Exception {
        criarJanelas(new AtomicInteger(), new AtomicInteger());
        // desconectado a caixa de mensagem está desligada: o foco cai no primeiro componente disponível
        assertNotSame(chat.painel().entrada().campo(), chat.getFocusTraversalPolicy().getInitialComponent(chat));
        chat.mostrarConexao(EstadoConexao.CONECTADO);
        edt();
        assertSame(chat.painel().entrada().campo(), chat.getFocusTraversalPolicy().getInitialComponent(chat));
        assertSame(login.painel().campoUsuario(), login.getFocusTraversalPolicy().getInitialComponent(login));
    }

    @Test
    void enterNaTelaDeLoginAcionaEntrar() throws Exception {
        criarJanelas(new AtomicInteger(), new AtomicInteger());
        assertSame(login.painel().botaoEntrar(), login.getRootPane().getDefaultButton());
    }

    @Test
    void erroDeSessaoPerdidaAparecemNaTelaDeLoginEReativaOsBotoes() throws Exception {
        criarJanelas(new AtomicInteger(), new AtomicInteger());
        SwingUtilities.invokeAndWait(() -> login.mostrarErro("A conexão com o servidor foi encerrada."));
        assertTrue(login.painel().rotuloDeErro().isVisible());
        assertTrue(login.painel().botaoEntrar().isEnabled());
    }

    @Test
    void dialogoDeBuscaAbreNaSalaEFechaComEsc() throws Exception {
        criarJanelas(new AtomicInteger(), new AtomicInteger());
        SwingUtilities.invokeAndWait(() -> {
            DialogoBusca d = new DialogoBusca(chat, tema, new PainelBusca.Acoes() {
                @Override
                public void porUsuario(String nome) {
                }

                @Override
                public void porTexto(String trecho) {
                }

                @Override
                public void porPeriodo(String inicio, String fim) {
                }
            });
            d.abrir("java");
            assertTrue(d.isVisible());
            assertEquals("Buscar em #java", d.painel().titulo().getText());
            Object chave = d.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
            assertNotNull(chave);
            d.getRootPane().getActionMap().get(chave).actionPerformed(null);
            assertFalse(d.isVisible());
            d.dispose();
        });
    }

    @Test
    void ctrlFAbreODialogoDeBuscaQuandoConectado() throws Exception {
        ConexaoFalsa conexao = new ConexaoFalsa();
        conexao.receberEvento(new Evento.Sistema("Bem-vindo"))
                .receberEvento(new Evento.Ok(Mensagens.entrouComo("ana", "geral")));
        SwingUtilities.invokeAndWait(() -> chat = new JanelaChat(tema, () -> { }, () -> { }));
        ChatController c = new ChatController(chat, (h, p) -> conexao);
        chat.definirControlador(c);
        assertTrue(c.conectar("localhost", "5000", "ana", "senha123", false).sucesso());
        edt();
        SwingUtilities.invokeAndWait(() -> chat.painel().abrirBusca());
        assertNotNull(chat.painel().dialogoDeBusca());
        assertTrue(chat.painel().dialogoDeBusca().isVisible());
        SwingUtilities.invokeAndWait(() -> chat.painel().dialogoDeBusca().dispose());
        c.sair();
    }

    @Test
    void depoisDeTrocarOTemaAsListasContinuamSemBordaEOTextoDosBaloesSemRecuo() throws Exception {
        criarJanelas(new AtomicInteger(), new AtomicInteger());
        SwingUtilities.invokeAndWait(() -> {
            chat.adicionar(deOutro("uma mensagem"));
            chat.setVisible(true);
        });
        edt();
        SwingUtilities.invokeAndWait(() -> {
            tema.alternar();
            tema.instalarLookAndFeel();
        });
        edt();
        assertNull(chat.painel().mensagens().getBorder(), "borda da conversa");
        assertNull(chat.painel().lateral().rolagemDeSalas().getBorder(), "borda das salas");
        assertNull(chat.painel().lateral().rolagemDeUsuarios().getBorder(), "borda dos usuários");
        assertEquals(new java.awt.Insets(0, 0, 0, 0), chat.painel().mensagens().linha(0).margemDoTexto());
    }

    @Test
    void janelasDesenhamConteudoDeVerdade() throws Exception {
        criarJanelas(new AtomicInteger(), new AtomicInteger());
        SwingUtilities.invokeAndWait(() -> {
            chat.painel().mostrarConexao(EstadoConexao.CONECTADO);
            chat.pack();
            chat.setSize(new Dimension(1000, 680));
            chat.validate();
        });
        var imagem = LayoutDeTeste.desenhar(chat.getContentPane());
        java.util.Set<Integer> cores = new java.util.HashSet<>();
        for (int x = 0; x < imagem.getWidth(); x += 7) {
            for (int y = 0; y < imagem.getHeight(); y += 7) {
                cores.add(imagem.getRGB(x, y));
            }
        }
        assertTrue(cores.size() > 4, "a janela parece vazia: " + cores.size() + " cores");
        assertTrue(imagem.getWidth() > 700 && imagem.getHeight() > 400);
    }
}
