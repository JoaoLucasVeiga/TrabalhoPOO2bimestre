package br.unicesumar.chat.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.client.ui.Tema.Modo;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPasswordField;
import org.junit.jupiter.api.Test;

/** Tela de conexão e login (sem tela): campos, botões, estado "Conectando..." e erro em linha. */
class PainelLoginTest {

    private final Tema tema = new Tema(Modo.CLARO);
    private final List<String> pedidos = new ArrayList<>();
    private final PainelLogin painel = new PainelLogin(tema,
            (host, porta, usuario, senha, criar) -> pedidos.add(String.join("|", host, porta, usuario, senha,
                    String.valueOf(criar))));

    @Test
    void comecaComServidorLocalhostPorta5000EResto() {
        assertEquals("localhost", painel.campoServidor().getText());
        assertEquals("5000", painel.campoPorta().getText());
        assertEquals("", painel.campoUsuario().getText());
        assertEquals("", new String(painel.campoSenha().getPassword()));
        assertTrue(painel.botaoEntrar().isEnabled());
        assertTrue(painel.botaoCriarConta().isEnabled());
        assertFalse(painel.rotuloDeErro().isVisible());
        assertEquals("", painel.rotuloDeStatus().getText());
    }

    @Test
    void asenhaEUmCampoDeSenhaComEcoOculto() {
        assertInstanceOf(JPasswordField.class, painel.campoSenha());
        assertNotEquals(0, painel.campoSenha().getEchoChar());
    }

    @Test
    void entrarEnviaOsQuatroCamposEOFlagDeLogin() {
        painel.campoUsuario().setText("ana");
        painel.campoSenha().setText("minhasenha");
        painel.botaoEntrar().doClick();
        assertEquals(List.of("localhost|5000|ana|minhasenha|false"), pedidos);
    }

    @Test
    void criarContaEnviaOFlagDeCadastro() {
        painel.campoUsuario().setText("ana");
        painel.campoSenha().setText("minhasenha");
        painel.botaoCriarConta().doClick();
        assertEquals(List.of("localhost|5000|ana|minhasenha|true"), pedidos);
    }

    @Test
    void enterEmQualquerCampoConfirmaComoEntrar() {
        painel.campoUsuario().setText("ana");
        painel.campoSenha().setText("minhasenha");
        painel.campoSenha().postActionEvent();
        assertEquals(1, pedidos.size());
        assertTrue(pedidos.get(0).endsWith("|false"));
    }

    @Test
    void duranteAConexaoTudoFicaDesabilitadoComTextoConectando() {
        painel.campoUsuario().setText("ana");
        painel.campoSenha().setText("x");
        painel.botaoEntrar().doClick();
        assertEquals("Conectando...", painel.rotuloDeStatus().getText());
        for (var campo : List.of(painel.campoServidor(), painel.campoPorta(), painel.campoUsuario(), painel.campoSenha())) {
            assertFalse(campo.isEnabled());
        }
        assertFalse(painel.botaoEntrar().isEnabled());
        assertFalse(painel.botaoCriarConta().isEnabled());
        assertEquals("Carregando...", painel.botaoEntrar().getText());
        assertEquals("Criar conta", painel.botaoCriarConta().getText());
    }

    @Test
    void ocliqueDeCriarContaMarcaESseBotaoComoCarregando() {
        painel.campoUsuario().setText("ana");
        painel.campoSenha().setText("x");
        painel.botaoCriarConta().doClick();
        assertEquals("Carregando...", painel.botaoCriarConta().getText());
        assertEquals("Entrar", painel.botaoEntrar().getText());
    }

    @Test
    void naoEnviaUmSegundoPedidoEnquantoConecta() {
        painel.campoUsuario().setText("ana");
        painel.campoSenha().setText("x");
        painel.botaoEntrar().doClick();
        painel.campoSenha().postActionEvent();
        painel.campoUsuario().postActionEvent();
        assertEquals(1, pedidos.size());
    }

    @Test
    void terminarDeConectarReativaTudoELimpaOStatus() {
        painel.campoUsuario().setText("ana");
        painel.campoSenha().setText("x");
        painel.botaoEntrar().doClick();
        painel.definirConectando(false, false);
        assertTrue(painel.campoServidor().isEnabled());
        assertTrue(painel.campoSenha().isEnabled());
        assertTrue(painel.botaoEntrar().isEnabled());
        assertEquals("Entrar", painel.botaoEntrar().getText());
        assertEquals("", painel.rotuloDeStatus().getText());
    }

    @Test
    void erroApareceEmLinhaNaCorDeErroEMantemOsCamposPreenchidos() {
        painel.campoUsuario().setText("ana");
        painel.mostrarErro("Não foi possível conectar a localhost:5000. Verifique se o servidor está em execução.");
        assertTrue(painel.rotuloDeErro().isVisible());
        assertTrue(painel.rotuloDeErro().getText().contains("Verifique se o servidor está em execução"));
        assertEquals(tema.paleta().erro(), painel.rotuloDeErro().getForeground());
        assertEquals("ana", painel.campoUsuario().getText());
    }

    @Test
    void umNovoPedidoLimpaOErroAnterior() {
        painel.mostrarErro("falhou");
        painel.campoUsuario().setText("ana");
        painel.campoSenha().setText("x");
        painel.botaoEntrar().doClick();
        assertFalse(painel.rotuloDeErro().isVisible());
    }

    @Test
    void limparErroEscondeOErro() {
        painel.mostrarErro("falhou");
        painel.limparErro();
        assertFalse(painel.rotuloDeErro().isVisible());
    }

    @Test
    void focoInicialEmUsuarioEOrdemDeTabELogica() {
        assertSame(painel.campoUsuario(), painel.componenteInicial());
        assertEquals(List.of(painel.campoServidor(), painel.campoPorta(), painel.campoUsuario(), painel.campoSenha(),
                painel.botaoCriarConta(), painel.botaoEntrar()), painel.ordemDeFoco());
    }

    @Test
    void camposTemDicasENomesAcessiveisEFonteLegivel() {
        for (var c : List.of(painel.campoServidor(), painel.campoPorta(), painel.campoUsuario(), painel.campoSenha())) {
            assertNotNull(c.getToolTipText());
            assertNotNull(c.getAccessibleContext().getAccessibleName());
            assertTrue(c.getFont().getSize() >= 13);
        }
        assertNotNull(painel.botaoEntrar().getToolTipText());
        assertNotNull(painel.botaoCriarConta().getToolTipText());
    }

    @Test
    void rotulosEstaoLigadosAosCampos() {
        assertSame(painel.campoUsuario(), painel.rotuloDe(painel.campoUsuario()).getLabelFor());
        assertSame(painel.campoSenha(), painel.rotuloDe(painel.campoSenha()).getLabelFor());
    }

    @Test
    void mudarOTemaRecolore() {
        tema.alternar();
        assertEquals(Tema.paleta(Modo.ESCURO).fundo(), painel.getBackground());
    }

    @Test
    void argumentosNulosLancamExcecao() {
        assertThrows(NullPointerException.class, () -> new PainelLogin(null, (h, p, u, s, c) -> { }));
        assertThrows(NullPointerException.class, () -> new PainelLogin(tema, null));
        assertThrows(NullPointerException.class, () -> painel.mostrarErro(null));
    }
}
