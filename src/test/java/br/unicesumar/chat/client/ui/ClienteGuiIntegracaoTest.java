package br.unicesumar.chat.client.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import br.unicesumar.chat.client.controller.EstadoConexao;
import br.unicesumar.chat.client.net.ConexaoSocket;
import br.unicesumar.chat.client.ui.Tema.Modo;
import br.unicesumar.chat.persistence.memory.MensagemRepositoryEmMemoria;
import br.unicesumar.chat.persistence.memory.UsuarioRepositoryEmMemoria;
import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.server.AuthService;
import br.unicesumar.chat.server.ChatService;
import br.unicesumar.chat.server.ClientRegistry;
import br.unicesumar.chat.server.CommandService;
import br.unicesumar.chat.server.ProcessadorChat;
import br.unicesumar.chat.server.SalaService;
import br.unicesumar.chat.server.Servidor;
import java.awt.GraphicsEnvironment;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Ponta a ponta: servidor de verdade (sockets reais) + as duas janelas de verdade. Precisa de
 * tela: roda com {@code xvfb-run -a mvn test} e é ignorado sem display.
 */
@Tag("gui")
@Tag("integracao")
class ClienteGuiIntegracaoTest {

    private Servidor servidor;
    private AplicacaoGui app;
    private final AtomicInteger encerramentos = new AtomicInteger();

    @BeforeEach
    void subirServidorEApp() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "sem display (rode com xvfb-run)");
        ClientRegistry registry = new ClientRegistry();
        ChatService chat = new ChatService(new MensagemRepositoryEmMemoria(), registry, Clock.systemDefaultZone());
        AuthService auth = new AuthService(new UsuarioRepositoryEmMemoria(), 1000);
        servidor = new Servidor(0, registry, new ProcessadorChat(chat,
                new CommandService(chat, registry, new SalaService(chat, registry), auth)));
        servidor.iniciar();
        app = new AplicacaoGui(new Tema(Modo.CLARO), (h, p) -> new ConexaoSocket(h, p), encerramentos::incrementAndGet);
        SwingUtilities.invokeAndWait(app::iniciar);
    }

    @AfterEach
    void desligar() throws Exception {
        if (app != null) {
            SwingUtilities.invokeAndWait(() -> {
                app.chat().dispose();
                app.login().dispose();
            });
            app.controlador().sair();
        }
        if (servidor != null) {
            servidor.parar();
        }
    }

    private void esperar(BooleanSupplier condicao) throws Exception {
        long fim = System.nanoTime() + 10_000_000_000L;
        boolean[] ok = {false};
        while (!ok[0] && System.nanoTime() < fim) {
            SwingUtilities.invokeAndWait(() -> ok[0] = condicao.getAsBoolean());
            if (!ok[0]) {
                Thread.yield();
            }
        }
        assertTrue(ok[0], "a condição não foi atendida a tempo");
    }

    private void criarContaPelaTela(String usuario) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PainelLogin p = app.login().painel();
            p.campoServidor().setText("localhost");
            p.campoPorta().setText(String.valueOf(servidor.porta()));
            p.campoUsuario().setText(usuario);
            p.campoSenha().setText("senha123");
            p.botaoCriarConta().doClick();
        });
    }

    @Test
    void criarContaPelaTelaAbreOChatConectadoELoginSomeDaTela() throws Exception {
        criarContaPelaTela("ana");
        esperar(() -> app.chat().isVisible() && !app.login().isVisible());
        esperar(() -> app.chat().painel().indicador().estado() == EstadoConexao.CONECTADO);
        assertEquals("# geral", app.chat().painel().titulo().getText());
        assertTrue(app.chat().painel().entrada().campo().isEnabled());
    }

    @Test
    void conversaDeVerdadeComOutroClientePorSocketEAparecemOsBaloesEAListaDeOnline() throws Exception {
        criarContaPelaTela("ana");
        esperar(() -> app.chat().isVisible());

        ConexaoSocket bia = new ConexaoSocket("localhost", servidor.porta());
        bia.abrir();
        bia.enviar("/register bia senha123");
        Evento e;
        do {
            e = bia.receber();
        } while (!(e instanceof Evento.Ok));
        bia.enviar("oi, ana! sou a bia");

        esperar(() -> app.chat().painel().mensagens().quantidade() >= 1
                && app.chat().painel().mensagens().linha(app.chat().painel().mensagens().quantidade() - 1)
                        .mensagem().texto().equals("oi, ana! sou a bia"));
        esperar(() -> app.chat().painel().lateral().usuarios().contains("bia"));
        assertEquals("ana", app.chat().painel().lateral().usuarios().get(0)); // o próprio primeiro

        SwingUtilities.invokeAndWait(() -> {
            app.chat().painel().entrada().campo().setText("tudo bem, bia?");
            app.chat().painel().entrada().botaoEnviar().doClick();
        });
        Evento recebido;
        do {
            recebido = bia.receber();
        } while (!(recebido instanceof Evento.Msg m && m.texto().equals("tudo bem, bia?")));
        assertEquals("ana", ((Evento.Msg) recebido).remetente());
        esperar(() -> app.chat().painel().mensagens().quantidade() >= 3);
        bia.fechar();
    }

    @Test
    void senhaErradaMostraOErroEmLinhaNoLoginSemAbrirOChat() throws Exception {
        criarContaPelaTela("ana");
        esperar(() -> app.chat().isVisible());
        SwingUtilities.invokeAndWait(() -> app.chat().painel().botaoSair().doClick());
        esperar(() -> app.login().isVisible() && !app.chat().isVisible());

        SwingUtilities.invokeAndWait(() -> {
            app.login().painel().campoSenha().setText("senha-errada");
            app.login().painel().botaoEntrar().doClick();
        });
        esperar(() -> app.login().painel().rotuloDeErro().isVisible());
        assertTrue(app.login().painel().rotuloDeErro().getText().contains("incorretos"));
        assertFalse(app.chat().isVisible());
        assertTrue(app.login().painel().botaoEntrar().isEnabled());
    }

    @Test
    void servidorForaDoArMostraOErroQueDizOQueFazer() throws Exception {
        int portaLivre = servidor.porta();
        servidor.parar();
        criarContaPelaTelaNaPorta("ana", portaLivre);
        esperar(() -> app.login().painel().rotuloDeErro().isVisible());
        assertTrue(app.login().painel().rotuloDeErro().getText().contains("Verifique se o servidor está em execução"));
        assertTrue(app.login().painel().campoUsuario().isEnabled());
    }

    private void criarContaPelaTelaNaPorta(String usuario, int porta) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PainelLogin p = app.login().painel();
            p.campoPorta().setText(String.valueOf(porta));
            p.campoUsuario().setText(usuario);
            p.campoSenha().setText("senha123");
            p.botaoEntrar().doClick();
        });
    }

    @Test
    void servidorCaindoComOChatAbertoVoltaAoLoginComOMotivo() throws Exception {
        criarContaPelaTela("ana");
        esperar(() -> app.chat().isVisible());
        servidor.parar();
        esperar(() -> app.login().isVisible() && !app.chat().isVisible());
        assertTrue(app.login().painel().rotuloDeErro().isVisible());
        assertTrue(app.login().painel().rotuloDeErro().getText().contains("encerrada"));
    }

    @Test
    void fecharAJanelaDoChatEncerraOPrograma() throws Exception {
        criarContaPelaTela("ana");
        esperar(() -> app.chat().isVisible());
        SwingUtilities.invokeAndWait(() -> app.chat().dispatchEvent(
                new java.awt.event.WindowEvent(app.chat(), java.awt.event.WindowEvent.WINDOW_CLOSING)));
        esperar(() -> encerramentos.get() == 1);
    }
}
