package br.unicesumar.chat.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.client.controller.MensagemTela;
import br.unicesumar.chat.client.controller.MensagemTela.Tipo;
import br.unicesumar.chat.client.ui.Tema.Modo;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

/** Mensagem em bloco (avatar, nome e hora, texto) e agrupamento de mensagens seguidas da mesma pessoa. */
class LinhaMensagemBlocoTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 22, 9, 5);

    private final Tema tema = new Tema(Modo.ESCURO);

    private static MensagemTela publica(String de, LocalDateTime hora, String texto, boolean minha) {
        return new MensagemTela(Tipo.PUBLICA, de, null, hora, texto, minha);
    }

    private static MensagemTela publica(String de, String texto) {
        return publica(de, T, texto, false);
    }

    private static MensagemTela sistema(String texto) {
        return MensagemTela.sistema(texto, T);
    }

    private static List<JLabel> rotulos(Component c) {
        List<JLabel> achados = new ArrayList<>();
        if (c instanceof JLabel l) {
            achados.add(l);
        }
        if (c instanceof Container container) {
            for (Component filho : container.getComponents()) {
                achados.addAll(rotulos(filho));
            }
        }
        return achados;
    }

    // ---------- cabeçalho e avatar ----------

    @Test
    void cabecalhoTemONomeEAHoraAteNasMinhasMensagens() {
        assertEquals("bia · 09:05", new LinhaMensagem(tema, publica("bia", "oi")).cabecalho());
        assertEquals("ana · 09:05", new LinhaMensagem(tema, publica("ana", T, "oi", true)).cabecalho());
    }

    @Test
    void privadaTemOMesmoCabecalhoDeUmaMensagemComum() {
        MensagemTela privada = new MensagemTela(Tipo.PRIVADA, "bia", "ana", T, "psiu", false);
        assertEquals("bia · 09:05", new LinhaMensagem(tema, privada).cabecalho());
    }

    @Test
    void avisoDoSistemaNaoTemCabecalhoNemAvatar() {
        LinhaMensagem l = new LinhaMensagem(tema, sistema("bia entrou na sala geral."));
        assertEquals("", l.cabecalho());
        assertFalse(l.temAvatar());
    }

    @Test
    void avisoDoSistemaTemRespiroEmCimaEEmBaixo() {
        LinhaMensagem l = new LinhaMensagem(tema, sistema("bia entrou na sala geral."));
        l.definirLarguraMaxima(500);
        assertTrue(l.getPreferredSize().height >= l.fonteDoCabecalho().getSize() + 2 * Tema.espaco(1));
    }

    @Test
    void primeiraMensagemDoBlocoMostraOAvatarComAInicialDoRemetente() {
        LinhaMensagem l = new LinhaMensagem(tema, publica("bia", "oi"));
        assertTrue(l.temAvatar());
        assertTrue(rotulos(l).stream().anyMatch(r -> r.getIcon() instanceof AvatarIcon && r.isVisible()));
    }

    @Test
    void continuacaoNaoMostraAvatarNemCabecalhoMasMantemOTexto() {
        LinhaMensagem l = new LinhaMensagem(tema, publica("bia", "segunda"));
        l.definirContinuacao(true);
        assertTrue(l.continuacao());
        assertFalse(l.temAvatar());
        assertTrue(rotulos(l).stream().noneMatch(r -> r.getIcon() instanceof AvatarIcon && r.isVisible()));
        assertEquals("segunda", l.mensagem().texto());
    }

    @Test
    void continuacaoEMaisBaixaQueOPrimeiroDoBloco() {
        LinhaMensagem primeira = new LinhaMensagem(tema, publica("bia", "um"));
        LinhaMensagem seguinte = new LinhaMensagem(tema, publica("bia", "dois"));
        seguinte.definirContinuacao(true);
        primeira.definirLarguraMaxima(600);
        seguinte.definirLarguraMaxima(600);
        assertTrue(seguinte.getPreferredSize().height < primeira.getPreferredSize().height);
    }

    // ---------- agrupamento ----------

    @Test
    void mesmaPessoaEmAteCincoMinutosAgrupa() {
        MensagemTela a = publica("bia", T, "um", false);
        assertTrue(LinhaMensagem.agrupa(a, publica("bia", T.plusMinutes(5), "dois", false)));
        assertTrue(LinhaMensagem.agrupa(a, publica("BIA", T.plusMinutes(1), "dois", false)));
    }

    @Test
    void pessoaDiferenteOuMuitoTempoDepoisOuSistemaNaoAgrupa() {
        MensagemTela a = publica("bia", T, "um", false);
        assertFalse(LinhaMensagem.agrupa(a, publica("caio", T.plusMinutes(1), "dois", false)));
        assertFalse(LinhaMensagem.agrupa(a, publica("bia", T.plusMinutes(6), "dois", false)));
        assertFalse(LinhaMensagem.agrupa(a, sistema("aviso")));
        assertFalse(LinhaMensagem.agrupa(sistema("aviso"), a));
        assertFalse(LinhaMensagem.agrupa(null, a));
    }

    @Test
    void mensagemAnteriorNoTempoNaoAgrupa() {
        assertFalse(LinhaMensagem.agrupa(publica("bia", T, "um", false), publica("bia", T.minusMinutes(1), "dois", false)));
    }

    // ---------- tamanho ----------

    @Test
    void aLinhaOcupaALarguraDisponivelInteira() {
        LinhaMensagem l = new LinhaMensagem(tema, publica("bia", "oi"));
        l.definirLarguraMaxima(640);
        assertEquals(640, l.getPreferredSize().width);
    }

    @Test
    void textoLongoQuebraEFicaMaisAltoEEstreitarRearruma() {
        String longo = "palavra ".repeat(60);
        LinhaMensagem curta = new LinhaMensagem(tema, publica("bia", "oi"));
        LinhaMensagem l = new LinhaMensagem(tema, publica("bia", longo));
        curta.definirLarguraMaxima(400);
        l.definirLarguraMaxima(400);
        assertTrue(l.getPreferredSize().height > 3 * curta.getPreferredSize().height);
        int larga = l.getPreferredSize().height;
        l.definirLarguraMaxima(220);
        assertTrue(l.getPreferredSize().height > larga);
    }

    @Test
    void quebrasDeLinhaDoTextoSaoPreservadas() {
        LinhaMensagem uma = new LinhaMensagem(tema, publica("bia", "a"));
        LinhaMensagem tres = new LinhaMensagem(tema, publica("bia", "a\nb\nc"));
        uma.definirLarguraMaxima(400);
        tres.definirLarguraMaxima(400);
        assertTrue(tres.getPreferredSize().height > uma.getPreferredSize().height + 20);
    }

    @Test
    void palavraGiganteSemEspacosNaoEstouraALargura() {
        LinhaMensagem l = new LinhaMensagem(tema, publica("bia", "x".repeat(500)));
        l.definirLarguraMaxima(300);
        Dimension d = l.getPreferredSize();
        assertEquals(300, d.width);
        assertTrue(d.height > 40);
    }

    @Test
    void larguraMaximaPrecisaSerPositiva() {
        assertThrows(IllegalArgumentException.class,
                () -> new LinhaMensagem(tema, publica("bia", "oi")).definirLarguraMaxima(0));
    }

    // ---------- cores, fontes e acessibilidade ----------

    @Test
    void nomeUsaDestaqueNasMinhasETextoNasDosOutros() {
        Tema.Paleta p = tema.paleta();
        assertEquals(p.textoDestaque(), new LinhaMensagem(tema, publica("ana", T, "oi", true)).corDoNome());
        assertEquals(p.texto(), new LinhaMensagem(tema, publica("bia", "oi")).corDoNome());
    }

    @Test
    void textoUsaACorDoTemaEOAvisoDoSistemaUsaTextoSecundario() {
        Tema.Paleta p = tema.paleta();
        assertEquals(p.texto(), new LinhaMensagem(tema, publica("bia", "oi")).corDoTexto());
        assertEquals(p.textoSecundario(), new LinhaMensagem(tema, sistema("aviso")).corDoTexto());
    }

    @Test
    void mudarOTemaRecolore() {
        LinhaMensagem l = new LinhaMensagem(tema, publica("bia", "oi"));
        java.awt.Color antes = l.corDoTexto();
        tema.alternar();
        l.aplicarTema();
        assertNotEquals(antes, l.corDoTexto());
        assertEquals(tema.paleta().texto(), l.corDoTexto());
    }

    @Test
    void fontesSeguemOTemaEAMargemDoTextoEZero() {
        LinhaMensagem l = new LinhaMensagem(tema, publica("bia", "oi"));
        assertEquals(tema.fonteCorpo(), l.fonteDoTexto());
        assertEquals(tema.fontePequena(), l.fonteDoCabecalho());
        assertEquals(new java.awt.Insets(0, 0, 0, 0), l.margemDoTexto());
    }

    @Test
    void nomeAcessivelTemRemetenteHoraETexto() {
        String nome = new LinhaMensagem(tema, publica("bia", "oi, ana")).getAccessibleContext().getAccessibleName();
        assertTrue(nome.contains("bia"));
        assertTrue(nome.contains("09:05"));
        assertTrue(nome.contains("oi, ana"));
    }

    @Test
    void mensagemOuTemaNulosLancamExcecao() {
        assertThrows(NullPointerException.class, () -> new LinhaMensagem(null, publica("bia", "oi")));
        assertThrows(NullPointerException.class, () -> new LinhaMensagem(tema, null));
    }

    @Test
    void figurinhaUsaFonteMaiorQueOTextoNormal() {
        LinhaMensagem figurinha = new LinhaMensagem(tema, publica("ana", "😀"));
        LinhaMensagem normal = new LinhaMensagem(tema, publica("ana", "oi"));
        assertTrue(figurinha.fonteDoTexto().getSize() >= 2 * normal.fonteDoTexto().getSize());
        assertTrue(figurinha.getPreferredSize().height > normal.getPreferredSize().height);
    }

    @Test
    void textoMisturadoComEmojiNaoViraFigurinha() {
        LinhaMensagem misturada = new LinhaMensagem(tema, publica("ana", "oi 😀"));
        LinhaMensagem normal = new LinhaMensagem(tema, publica("ana", "oi"));
        assertEquals(normal.fonteDoTexto().getSize(), misturada.fonteDoTexto().getSize());
    }

    @Test
    void figurinhaContinuaGrandeDepoisDeTrocarOTema() {
        LinhaMensagem figurinha = new LinhaMensagem(tema, publica("ana", "👍"));
        int antes = figurinha.fonteDoTexto().getSize();
        figurinha.aplicarTema();
        assertEquals(antes, figurinha.fonteDoTexto().getSize());
    }
}
