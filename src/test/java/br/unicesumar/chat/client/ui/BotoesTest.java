package br.unicesumar.chat.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import javax.swing.JButton;
import org.junit.jupiter.api.Test;

/** Estados padronizados de botão: ativo, desabilitado e carregando (sem tela). */
class BotoesTest {

    @Test
    void ativoHabilitaEMostraOTextoOriginal() {
        JButton b = new JButton("Entrar");
        Botoes.aplicar(b, EstadoBotao.ATIVO);
        assertTrue(b.isEnabled());
        assertEquals("Entrar", b.getText());
    }

    @Test
    void desabilitadoDesligaOBotaoMantendoOTexto() {
        JButton b = new JButton("Entrar");
        Botoes.aplicar(b, EstadoBotao.DESABILITADO);
        assertFalse(b.isEnabled());
        assertEquals("Entrar", b.getText());
    }

    @Test
    void carregandoDesligaOBotaoEMostraCarregando() {
        JButton b = new JButton("Buscar");
        Botoes.aplicar(b, EstadoBotao.CARREGANDO);
        assertFalse(b.isEnabled());
        assertEquals("Carregando...", b.getText());
    }

    @Test
    void voltarParaAtivoRestauraOTextoOriginalDepoisDeCarregar() {
        JButton b = new JButton("Buscar");
        Botoes.aplicar(b, EstadoBotao.CARREGANDO);
        Botoes.aplicar(b, EstadoBotao.CARREGANDO);
        Botoes.aplicar(b, EstadoBotao.ATIVO);
        assertEquals("Buscar", b.getText());
        assertTrue(b.isEnabled());
    }

    @Test
    void oTextoOriginalNaoSePerdeComVariasTrocasDeEstado() {
        JButton b = new JButton("Exportar");
        for (EstadoBotao e : new EstadoBotao[] {EstadoBotao.CARREGANDO, EstadoBotao.DESABILITADO,
                EstadoBotao.ATIVO, EstadoBotao.CARREGANDO, EstadoBotao.ATIVO}) {
            Botoes.aplicar(b, e);
        }
        assertEquals("Exportar", b.getText());
    }

    @Test
    void botaoNuloOuEstadoNuloLancaExcecao() {
        assertThrows(NullPointerException.class, () -> Botoes.aplicar(null, EstadoBotao.ATIVO));
        assertThrows(NullPointerException.class, () -> Botoes.aplicar(new JButton("x"), null));
    }
}
