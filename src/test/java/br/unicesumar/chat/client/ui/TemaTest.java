package br.unicesumar.chat.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.client.ui.Tema.Modo;
import br.unicesumar.chat.client.ui.Tema.Paleta;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

/** Tema: só cores, fontes e espaçamentos. Roda sem tela (java.awt.Color não precisa de display). */
class TemaTest {

    private static final double CONTRASTE_MINIMO = 4.5;

    @Test
    void contrasteDePretoComBrancoE21EDeCorIgualE1() {
        assertEquals(21.0, Tema.contraste(Color.BLACK, Color.WHITE), 0.01);
        assertEquals(1.0, Tema.contraste(Color.RED, Color.RED), 0.0001);
    }

    @Test
    void contrasteNaoDependeDaOrdemDasCores() {
        Color a = new Color(0x1D4ED8);
        Color b = new Color(0xFFFFFF);
        assertEquals(Tema.contraste(a, b), Tema.contraste(b, a), 1e-9);
    }

    private record Par(String nome, Color texto, Color fundo) {
    }

    private static List<Par> paresDeTextoEFundo(Paleta p) {
        List<Par> pares = new ArrayList<>();
        pares.add(new Par("texto/fundo", p.texto(), p.fundo()));
        pares.add(new Par("texto/superficie", p.texto(), p.superficie()));
        pares.add(new Par("secundario/fundo", p.textoSecundario(), p.fundo()));
        pares.add(new Par("secundario/superficie", p.textoSecundario(), p.superficie()));
        pares.add(new Par("sobreDestaque/destaque", p.textoSobreDestaque(), p.destaque()));
        pares.add(new Par("textoDestaque/fundo", p.textoDestaque(), p.fundo()));
        pares.add(new Par("textoDestaque/superficie", p.textoDestaque(), p.superficie()));
        pares.add(new Par("balaoMeu", p.textoBalaoMeu(), p.balaoMeu()));
        pares.add(new Par("balaoOutro", p.textoBalaoOutro(), p.balaoOutro()));
        pares.add(new Par("balaoPrivado", p.textoBalaoPrivado(), p.balaoPrivado()));
        pares.add(new Par("erro/fundo", p.erro(), p.fundo()));
        pares.add(new Par("erro/superficie", p.erro(), p.superficie()));
        pares.add(new Par("sucesso/fundo", p.sucesso(), p.fundo()));
        pares.add(new Par("sucesso/superficie", p.sucesso(), p.superficie()));
        pares.add(new Par("atencao/fundo", p.atencao(), p.fundo()));
        pares.add(new Par("atencao/superficie", p.atencao(), p.superficie()));
        return pares;
    }

    @Test
    void todosOsParesTextoFundoTemContrasteMinimoDe4e5NosDoisModos() {
        for (Modo modo : Modo.values()) {
            for (Par par : paresDeTextoEFundo(Tema.paleta(modo))) {
                double c = Tema.contraste(par.texto(), par.fundo());
                assertTrue(c >= CONTRASTE_MINIMO, modo + " " + par.nome() + " tem contraste " + c);
            }
        }
    }

    @Test
    void ocontadorEmAlertaEStatusUsamCoresQueContrastamComOFundo() {
        for (Modo modo : Modo.values()) {
            Paleta p = Tema.paleta(modo);
            assertTrue(Tema.contraste(p.erro(), p.fundo()) >= CONTRASTE_MINIMO, modo.toString());
            assertTrue(Tema.contraste(p.atencao(), p.fundo()) >= CONTRASTE_MINIMO, modo.toString());
            assertTrue(Tema.contraste(p.sucesso(), p.fundo()) >= CONTRASTE_MINIMO, modo.toString());
        }
    }

    @Test
    void ostresEstadosDaConexaoTemCoresDiferentesEntreSi() {
        for (Modo modo : Modo.values()) {
            Paleta p = Tema.paleta(modo);
            assertNotEquals(p.sucesso(), p.atencao());
            assertNotEquals(p.atencao(), p.erro());
            assertNotEquals(p.sucesso(), p.erro());
        }
    }

    @Test
    void ahUmaUnicaCorDeDestaqueEOBalaoDasMinhasMensagensUsaEla() {
        for (Modo modo : Modo.values()) {
            Paleta p = Tema.paleta(modo);
            assertEquals(p.destaque(), p.balaoMeu());
        }
    }

    @Test
    void asMensagensPrivadasTemUmaCorPropriaDiferenteDosOutrosBaloes() {
        for (Modo modo : Modo.values()) {
            Paleta p = Tema.paleta(modo);
            assertNotEquals(p.balaoPrivado(), p.balaoOutro());
            assertNotEquals(p.balaoPrivado(), p.balaoMeu());
        }
    }

    @Test
    void osDoisModosTemFundosDiferentesEOEscuroEMaisEscuroQueOClaro() {
        Paleta claro = Tema.paleta(Modo.CLARO);
        Paleta escuro = Tema.paleta(Modo.ESCURO);
        assertNotEquals(claro.fundo(), escuro.fundo());
        assertTrue(brilho(escuro.fundo()) < brilho(claro.fundo()));
    }

    private static int brilho(Color c) {
        return c.getRed() + c.getGreen() + c.getBlue();
    }

    @Test
    void fonteDoCorpoTemPeloMenos13PontosEATituloEMaiorQueOCorpo() {
        Tema tema = new Tema(Modo.CLARO);
        assertTrue(tema.fonteCorpo().getSize() >= 13);
        assertTrue(tema.fontePequena().getSize() < tema.fonteCorpo().getSize());
        assertTrue(tema.fontePequena().getSize() >= 11);
        assertTrue(tema.fonteTitulo().getSize() > tema.fonteCorpo().getSize());
    }

    @Test
    void espacamentosSaoMultiplosDaGradeDe8Pixels() {
        assertEquals(8, Tema.GRADE);
        assertEquals(0, Tema.espaco(0));
        assertEquals(8, Tema.espaco(1));
        assertEquals(24, Tema.espaco(3));
        assertThrows(IllegalArgumentException.class, () -> Tema.espaco(-1));
    }

    @Test
    void alternarTrocaOModoENotificaCadaOuvinteUmaVez() {
        Tema tema = new Tema(Modo.CLARO);
        int[] chamadas = {0, 0};
        tema.aoMudar(() -> chamadas[0]++);
        tema.aoMudar(() -> chamadas[1]++);
        assertEquals(Modo.CLARO, tema.modo());
        tema.alternar();
        assertEquals(Modo.ESCURO, tema.modo());
        assertArrayEquals(new int[] {1, 1}, chamadas);
        tema.alternar();
        assertEquals(Modo.CLARO, tema.modo());
        assertArrayEquals(new int[] {2, 2}, chamadas);
    }

    @Test
    void apaletaDoTemaAcompanhaOModoAtual() {
        Tema tema = new Tema(Modo.CLARO);
        assertEquals(Tema.paleta(Modo.CLARO), tema.paleta());
        tema.alternar();
        assertEquals(Tema.paleta(Modo.ESCURO), tema.paleta());
    }

    @Test
    void construtorRejeitaModoNulo() {
        assertThrows(NullPointerException.class, () -> new Tema(null));
        assertThrows(NullPointerException.class, () -> new Tema(Modo.CLARO).aoMudar(null));
    }

    @Test
    void instalarOLookAndFeelUsaFlatLafClaroOuEscuroConformeOModo() {
        Tema tema = new Tema(Modo.CLARO);
        tema.instalarLookAndFeel();
        assertTrue(UIManager.getLookAndFeel().getName().contains("Light"), UIManager.getLookAndFeel().getName());
        tema.alternar();
        tema.instalarLookAndFeel();
        assertTrue(UIManager.getLookAndFeel().getName().contains("Dark"), UIManager.getLookAndFeel().getName());
    }
}
