package br.unicesumar.chat.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Component;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

/** Ordem de Tab explícita e lógica. */
class OrdemDeFocoTest {

    private final JTextField a = new JTextField();
    private final JTextField b = new JTextField();
    private final JButton c = new JButton("c");
    private final JPanel raiz = new JPanel();
    private final OrdemDeFoco ordem = new OrdemDeFoco(List.of(a, b, c), b);

    @Test
    void tabAvancaNaOrdemEDaAVoltaNoFim() {
        assertSame(b, ordem.getComponentAfter(raiz, a));
        assertSame(c, ordem.getComponentAfter(raiz, b));
        assertSame(a, ordem.getComponentAfter(raiz, c));
    }

    @Test
    void shiftTabVoltaNaOrdemEDaAVoltaNoInicio() {
        assertSame(a, ordem.getComponentBefore(raiz, b));
        assertSame(c, ordem.getComponentBefore(raiz, a));
    }

    @Test
    void primeiroUltimoEInicialSaoOsEsperados() {
        assertSame(a, ordem.getFirstComponent(raiz));
        assertSame(c, ordem.getLastComponent(raiz));
        assertSame(b, ordem.getDefaultComponent(raiz));
        assertSame(b, ordem.getInitialComponent(null)); // o argumento não é usado
    }

    @Test
    void componentesDesabilitadosOuInvisiveisSaoPulados() {
        b.setEnabled(false);
        assertSame(c, ordem.getComponentAfter(raiz, a));
        assertSame(a, ordem.getComponentBefore(raiz, c));
        assertSame(a, ordem.getDefaultComponent(raiz)); // o inicial está desabilitado: cai no primeiro
        c.setVisible(false);
        assertSame(a, ordem.getComponentAfter(raiz, a));
    }

    @Test
    void componenteForaDaListaVaiParaOPrimeiro() {
        Component fora = new JButton("fora");
        assertSame(a, ordem.getComponentAfter(raiz, fora));
        assertSame(c, ordem.getComponentBefore(raiz, fora));
    }

    @Test
    void nenhumDisponivelDevolveNulo() {
        a.setEnabled(false);
        b.setEnabled(false);
        c.setEnabled(false);
        assertNull(ordem.getFirstComponent(raiz));
        assertNull(ordem.getComponentAfter(raiz, a));
        assertNull(ordem.getDefaultComponent(raiz));
    }

    @Test
    void listaVaziaOuNulaLancaExcecao() {
        assertThrows(IllegalArgumentException.class, () -> new OrdemDeFoco(List.of(), null));
        assertThrows(NullPointerException.class, () -> new OrdemDeFoco(null, null));
    }
}
