package br.unicesumar.chat.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.client.ui.Tema.Modo;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Avatar redondo com a inicial do nome (sem tela: desenha em uma imagem na memória). */
class AvatarIconTest {

    private static final int TAMANHO = 36;
    private final Tema tema = new Tema(Modo.ESCURO);

    private BufferedImage desenhar(AvatarIcon icone) {
        BufferedImage img = new BufferedImage(icone.getIconWidth(), icone.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            icone.paintIcon(null, g, 0, 0);
        } finally {
            g.dispose();
        }
        return img;
    }

    @Test
    void inicialEPrimeiraLetraEmMaiusculaOuInterrogacaoSemNome() {
        assertEquals("A", AvatarIcon.inicial("ana"));
        assertEquals("B", AvatarIcon.inicial("  bia"));
        assertEquals("?", AvatarIcon.inicial(""));
        assertEquals("?", AvatarIcon.inicial("   "));
        assertEquals("?", AvatarIcon.inicial(null));
    }

    @Test
    void mesmoNomeTemSempreAMesmaCorSemDiferenciarMaiusculas() {
        assertEquals(Tema.corDoAvatar("ana"), Tema.corDoAvatar("ana"));
        assertEquals(Tema.corDoAvatar("ana"), Tema.corDoAvatar("ANA"));
    }

    @Test
    void nomesDiferentesEspalhamAsCoresEElasSaemSoDaLista() {
        Set<Color> usadas = new HashSet<>();
        for (String nome : new String[] {"ana", "bia", "caio", "dani", "edu", "fabi", "gui", "hugo", "iris", "jo"}) {
            Color cor = Tema.corDoAvatar(nome);
            assertTrue(Tema.coresDeAvatar().contains(cor));
            usadas.add(cor);
        }
        assertTrue(usadas.size() >= 3, "as cores precisam se espalhar: " + usadas.size());
    }

    @Test
    void textoBrancoContrastaComTodasAsCoresDeAvatar() {
        for (Color cor : Tema.coresDeAvatar()) {
            assertTrue(Tema.contraste(Color.WHITE, cor) >= 4.5, "contraste baixo com " + cor);
        }
    }

    @Test
    void desenhaUmCirculoDaCorDoNomeComCantosTransparentes() {
        BufferedImage img = desenhar(new AvatarIcon("ana", TAMANHO, false, tema));
        assertEquals(Tema.corDoAvatar("ana").getRGB(), img.getRGB(TAMANHO / 2, 3));
        assertEquals(0, img.getRGB(0, 0) >>> 24, "o canto fica transparente");
    }

    @Test
    void indicadorDeOnlineAparecesoQuandoPedido() {
        Color verde = tema.paleta().sucesso();
        BufferedImage online = desenhar(new AvatarIcon("ana", TAMANHO, true, tema));
        BufferedImage offline = desenhar(new AvatarIcon("ana", TAMANHO, false, tema));
        assertEquals(verde.getRGB(), online.getRGB(30, 30));
        assertNotEquals(verde.getRGB(), offline.getRGB(30, 30));
    }

    @Test
    void tamanhoDoIconeEOPedidoEDeveSerPositivo() {
        AvatarIcon icone = new AvatarIcon("ana", 28, false, tema);
        assertEquals(28, icone.getIconWidth());
        assertEquals(28, icone.getIconHeight());
        assertThrows(IllegalArgumentException.class, () -> new AvatarIcon("ana", 0, false, tema));
    }
}
