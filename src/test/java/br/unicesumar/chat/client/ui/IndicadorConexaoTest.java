package br.unicesumar.chat.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.client.controller.EstadoConexao;
import br.unicesumar.chat.client.ui.Tema.Modo;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

/** Indicador desenhado em Java: círculo colorido SEMPRE acompanhado de texto (sem tela). */
class IndicadorConexaoTest {

    private final Tema tema = new Tema(Modo.CLARO);

    @Test
    void textoDeCadaEstado() {
        IndicadorConexao i = new IndicadorConexao(tema);
        i.definir(EstadoConexao.CONECTADO);
        assertEquals("Conectado", i.texto());
        i.definir(EstadoConexao.CONECTANDO);
        assertEquals("Conectando...", i.texto());
        i.definir(EstadoConexao.DESCONECTADO);
        assertEquals("Desconectado", i.texto());
    }

    @Test
    void comecaDesconectado() {
        assertEquals(EstadoConexao.DESCONECTADO, new IndicadorConexao(tema).estado());
    }

    @Test
    void corDoCirculoSegueOEstadoEATrocaComOTema() {
        IndicadorConexao i = new IndicadorConexao(tema);
        i.definir(EstadoConexao.CONECTADO);
        assertEquals(tema.paleta().sucesso(), i.corDoCirculo());
        i.definir(EstadoConexao.CONECTANDO);
        assertEquals(tema.paleta().atencao(), i.corDoCirculo());
        i.definir(EstadoConexao.DESCONECTADO);
        assertEquals(tema.paleta().erro(), i.corDoCirculo());
        tema.alternar();
        assertEquals(Tema.paleta(Modo.ESCURO).erro(), i.corDoCirculo());
    }

    @Test
    void asTresCoresSaoDiferentes() {
        IndicadorConexao i = new IndicadorConexao(tema);
        i.definir(EstadoConexao.CONECTADO);
        Color a = i.corDoCirculo();
        i.definir(EstadoConexao.CONECTANDO);
        Color b = i.corDoCirculo();
        i.definir(EstadoConexao.DESCONECTADO);
        Color c = i.corDoCirculo();
        assertNotEquals(a, b);
        assertNotEquals(b, c);
        assertNotEquals(a, c);
    }

    @Test
    void ocirculoEDesenhadoNaCorDoEstado() {
        IndicadorConexao i = new IndicadorConexao(tema);
        i.definir(EstadoConexao.CONECTADO);
        Dimension d = i.getPreferredSize();
        i.setSize(d);
        BufferedImage img = new BufferedImage(d.width, d.height, BufferedImage.TYPE_INT_ARGB);
        i.paint(img.createGraphics());
        Color noCentroDoCirculo = new Color(img.getRGB(Tema.espaco(1), d.height / 2), true);
        assertEquals(tema.paleta().sucesso().getRGB(), noCentroDoCirculo.getRGB());
    }

    @Test
    void otamanhoPreferidoCabeOTextoMaisLongo() {
        IndicadorConexao i = new IndicadorConexao(tema);
        i.definir(EstadoConexao.DESCONECTADO);
        int largura = i.getPreferredSize().width;
        i.definir(EstadoConexao.CONECTANDO);
        assertTrue(i.getPreferredSize().width >= largura - 4 || i.getPreferredSize().width > 60);
        assertTrue(i.getPreferredSize().height >= tema.fonteCorpo().getSize());
    }

    @Test
    void temNomeAcessivelComOEstado() {
        IndicadorConexao i = new IndicadorConexao(tema);
        i.definir(EstadoConexao.CONECTADO);
        assertTrue(i.getAccessibleContext().getAccessibleName().contains("Conectado"));
    }

    @Test
    void temaOuEstadoNulosLancamExcecao() {
        assertThrows(NullPointerException.class, () -> new IndicadorConexao(null));
        assertThrows(NullPointerException.class, () -> new IndicadorConexao(tema).definir(null));
    }
}
