package br.unicesumar.chat.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.client.ui.Tema.Modo;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Rodapé com campo, contador x/500 e botão Enviar (sem tela). */
class PainelEntradaTest {

    private final Tema tema = new Tema(Modo.CLARO);
    private final List<String> enviados = new ArrayList<>();
    private final PainelEntrada painel = new PainelEntrada(tema, enviados::add);

    @Test
    void comecaVazioComEnviarDesabilitadoEContadorZerado() {
        assertEquals("", painel.campo().getText());
        assertFalse(painel.botaoEnviar().isEnabled());
        assertEquals("0/500", painel.contador().getText());
    }

    @Test
    void digitarHabilitaEnviarEAtualizaOContador() {
        painel.campo().setText("olá");
        assertTrue(painel.botaoEnviar().isEnabled());
        assertEquals("3/500", painel.contador().getText());
    }

    @Test
    void soEspacosNaoHabilitamEnviar() {
        painel.campo().setText("     ");
        assertFalse(painel.botaoEnviar().isEnabled());
        assertEquals("5/500", painel.contador().getText());
    }

    @Test
    void apagarOTextoDesabilitaEnviarDeNovo() {
        painel.campo().setText("oi");
        painel.campo().setText("");
        assertFalse(painel.botaoEnviar().isEnabled());
    }

    @Test
    void enviarEntregaOTextoSemEspacosNasPontasELimpaOCampo() {
        painel.campo().setText("  olá, pessoal  ");
        painel.acionarEnvio();
        assertEquals(List.of("olá, pessoal"), enviados);
        assertEquals("", painel.campo().getText());
        assertEquals("0/500", painel.contador().getText());
    }

    @Test
    void enviarComCampoVazioNaoFazNada() {
        painel.acionarEnvio();
        painel.campo().setText("   ");
        painel.acionarEnvio();
        assertTrue(enviados.isEmpty());
    }

    @Test
    void enterNoCampoEOBotaoUsamAMesmaAcao() {
        painel.campo().setText("pelo enter");
        painel.campo().postActionEvent();
        painel.campo().setText("pelo botao");
        painel.botaoEnviar().doClick();
        assertEquals(List.of("pelo enter", "pelo botao"), enviados);
    }

    @Test
    void campoAceitaExatamente500CaracteresEPortaAlemSaoCortados() {
        painel.campo().setText("x".repeat(500));
        assertEquals(500, painel.campo().getText().length());
        assertEquals("500/500", painel.contador().getText());
        painel.campo().setText("y".repeat(600));
        assertEquals(500, painel.campo().getText().length());
        assertEquals("500/500", painel.contador().getText());
    }

    @Test
    void digitarMaisUmCaractereComOCampoCheioNaoAltera() throws Exception {
        painel.campo().setText("x".repeat(500));
        painel.campo().getDocument().insertString(500, "z", null);
        assertEquals(500, painel.campo().getText().length());
        painel.campo().getDocument().insertString(10, "abc", null);
        assertEquals(500, painel.campo().getText().length());
    }

    @Test
    void contadorViraAlertaPertoDoLimiteEVoltaAoNormal() {
        Tema.Paleta p = tema.paleta();
        painel.campo().setText("x".repeat(PainelEntrada.LIMITE_DE_ALERTA - 1));
        assertEquals(p.textoSecundario(), painel.contador().getForeground());
        painel.campo().setText("x".repeat(PainelEntrada.LIMITE_DE_ALERTA));
        assertEquals(p.erro(), painel.contador().getForeground());
        painel.campo().setText("x");
        assertEquals(p.textoSecundario(), painel.contador().getForeground());
    }

    @Test
    void alertaComecaEm450() {
        assertEquals(450, PainelEntrada.LIMITE_DE_ALERTA);
    }

    @Test
    void desabilitarOPainelDesligaCampoEBotaoERestauraDepois() {
        painel.campo().setText("oi");
        painel.definirHabilitado(false);
        assertFalse(painel.campo().isEnabled());
        assertFalse(painel.botaoEnviar().isEnabled());
        painel.definirHabilitado(true);
        assertTrue(painel.campo().isEnabled());
        assertTrue(painel.botaoEnviar().isEnabled()); // ainda há texto
    }

    @Test
    void habilitarComCampoVazioMantemEnviarDesabilitado() {
        painel.definirHabilitado(false);
        painel.definirHabilitado(true);
        assertFalse(painel.botaoEnviar().isEnabled());
    }

    @Test
    void temDicasDeFerramentaENomesAcessiveis() {
        assertNotNull(painel.campo().getToolTipText());
        assertNotNull(painel.botaoEnviar().getToolTipText());
        assertNotNull(painel.contador().getToolTipText());
        assertNotNull(painel.campo().getAccessibleContext().getAccessibleName());
        assertNotNull(painel.botaoEnviar().getAccessibleContext().getAccessibleName());
    }

    @Test
    void fonteDoCampoNaoEMenorQue13() {
        assertTrue(painel.campo().getFont().getSize() >= 13);
    }

    @Test
    void mudarOTemaAtualizaAsCores() {
        tema.alternar();
        assertEquals(Tema.paleta(Modo.ESCURO).fundo(), painel.getBackground());
    }

    @Test
    void ouvinteNuloLancaExcecao() {
        assertThrows(NullPointerException.class, () -> new PainelEntrada(tema, null));
        assertThrows(NullPointerException.class, () -> new PainelEntrada(null, s -> { }));
    }
}
