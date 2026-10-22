package br.unicesumar.chat.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.client.controller.ChatController;
import br.unicesumar.chat.client.controller.ChatView;
import br.unicesumar.chat.client.net.ConexaoFalsa;
import br.unicesumar.chat.client.ui.Tema.Modo;
import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.protocol.Mensagens;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Liga o painel de login ao controlador: conectar em segundo plano, resultado na EDT (sem tela). */
class FluxoDeLoginTest {

    private final Tema tema = new Tema(Modo.CLARO);
    private final ConexaoFalsa conexao = new ConexaoFalsa();
    private final AtomicReference<Boolean> viewNaEdt = new AtomicReference<>();
    private final ChatView viewIgnorando = (ChatView) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] {ChatView.class}, (p, m, a) -> null);
    private final ChatController controller = new ChatController(viewIgnorando, (h, p) -> conexao);
    private final CountDownLatch entrou = new CountDownLatch(1);
    private final CountDownLatch tentativaConcluida = new CountDownLatch(1);
    private final AtomicInteger entradas = new AtomicInteger();
    private final PainelLogin painel = new PainelLogin(tema, (h, p, u, s, c) -> { });
    private final FluxoDeLogin fluxo = new FluxoDeLogin(painel, controller, () -> {
        viewNaEdt.set(SwingUtilities.isEventDispatchThread());
        entradas.incrementAndGet();
        entrou.countDown();
    });

    {
        fluxo.aoConcluirTentativa(tentativaConcluida::countDown);
    }

    private void esperarEdt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    void loginComSucessoChamaOCallbackNaEdtEReativaOPainel() throws Exception {
        conexao.receberEvento(new Evento.Sistema("Bem-vindo"))
                .receberEvento(new Evento.Ok(Mensagens.entrouComo("ana", "geral")));
        SwingUtilities.invokeAndWait(() -> fluxo.entrar("localhost", "5000", "ana", "senha123", false));
        assertTrue(entrou.await(5, TimeUnit.SECONDS));
        aguardarPainelLivre();
        assertEquals(true, viewNaEdt.get());
        assertEquals(1, entradas.get());
        assertTrue(painel.botaoEntrar().isEnabled());
        assertFalse(painel.rotuloDeErro().isVisible());
        controller.sair();
    }

    @Test
    void falhaMostraOErroEmLinhaEReativaOPainelSemEntrar() throws Exception {
        conexao.falharAoAbrirCom(new IOException(
                "Não foi possível conectar a localhost:5000. Verifique se o servidor está em execução."));
        SwingUtilities.invokeAndWait(() -> fluxo.entrar("localhost", "5000", "ana", "senha123", false));
        aguardarPainelLivre();
        assertEquals(0, entradas.get());
        assertTrue(painel.rotuloDeErro().isVisible());
        assertTrue(painel.rotuloDeErro().getText().contains("Verifique se o servidor está em execução"));
        assertTrue(painel.botaoEntrar().isEnabled());
    }

    @Test
    void erroDoServidorNoLoginApareceNoPainel() throws Exception {
        conexao.receberEvento(new Evento.Sistema("Bem-vindo")).receberEvento(
                new Evento.Erro("Usuário ou senha incorretos. Confira os dados e tente de novo."));
        SwingUtilities.invokeAndWait(() -> fluxo.entrar("localhost", "5000", "ana", "errada", false));
        aguardarPainelLivre();
        assertTrue(painel.rotuloDeErro().getText().contains("incorretos"));
        assertEquals(0, entradas.get());
    }

    @Test
    void camposInvalidosMostramErroSemMexerNaRede() throws Exception {
        SwingUtilities.invokeAndWait(() -> fluxo.entrar("", "5000", "ana", "senha123", false));
        aguardarPainelLivre();
        assertTrue(painel.rotuloDeErro().isVisible());
        assertTrue(conexao.enviadas().isEmpty());
    }

    @Test
    void criarContaUsaORegister() throws Exception {
        conexao.receberEvento(new Evento.Sistema("Bem-vindo"))
                .receberEvento(new Evento.Ok(Mensagens.entrouComo("ana", "geral")));
        SwingUtilities.invokeAndWait(() -> fluxo.entrar("localhost", "5000", "ana", "senha123", true));
        assertTrue(entrou.await(5, TimeUnit.SECONDS));
        assertEquals("/register ana senha123", conexao.enviadas().get(0));
        controller.sair();
    }

    @Test
    void oPainelEntraNoEstadoConectandoImediatamenteAoPedir() throws Exception {
        conexao.falharAoAbrirCom(new IOException("x"));
        AtomicReference<String> statusLogoDepois = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            fluxo.entrar("localhost", "5000", "ana", "senha123", false);
            statusLogoDepois.set(painel.rotuloDeStatus().getText());
        });
        assertEquals("Conectando...", statusLogoDepois.get());
        aguardarPainelLivre();
    }

    /** Espera a tentativa terminar: o gancho roda na EDT depois de o painel ser atualizado. */
    private void aguardarPainelLivre() throws Exception {
        assertTrue(tentativaConcluida.await(5, TimeUnit.SECONDS));
    }
}
