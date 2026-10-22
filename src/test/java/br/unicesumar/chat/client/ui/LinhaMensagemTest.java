package br.unicesumar.chat.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.client.controller.MensagemTela;
import br.unicesumar.chat.client.ui.LinhaMensagem.Alinhamento;
import br.unicesumar.chat.client.ui.Tema.Modo;
import br.unicesumar.chat.protocol.Evento;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Balões de mensagem: alinhamento, cores, cabeçalho e quebra de linha (sem tela). */
class LinhaMensagemTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 22, 9, 5);

    private final Tema tema = new Tema(Modo.CLARO);

    private MensagemTela publica(String de, String texto, boolean minha) {
        return new MensagemTela(MensagemTela.Tipo.PUBLICA, de, null, T, texto, minha);
    }

    private MensagemTela privada(String de, String para, String texto, boolean minha) {
        return new MensagemTela(MensagemTela.Tipo.PRIVADA, de, para, T, texto, minha);
    }

    @Test
    void asMinhasFicamADireitaAsDosOutrosAEsquerdaEOSistemaAoCentro() {
        assertEquals(Alinhamento.DIREITA, new LinhaMensagem(tema, publica("ana", "oi", true)).alinhamento());
        assertEquals(Alinhamento.ESQUERDA, new LinhaMensagem(tema, publica("bia", "oi", false)).alinhamento());
        assertEquals(Alinhamento.CENTRO,
                new LinhaMensagem(tema, MensagemTela.sistema("bia entrou", T)).alinhamento());
    }

    @Test
    void privadasTambemSeguemOLadoDeQuemEnviou() {
        assertEquals(Alinhamento.DIREITA, new LinhaMensagem(tema, privada("ana", "bia", "x", true)).alinhamento());
        assertEquals(Alinhamento.ESQUERDA, new LinhaMensagem(tema, privada("bia", "ana", "x", false)).alinhamento());
    }

    @Test
    void cabecalhoMostraRemetenteEHorarioHoraMinuto() {
        assertEquals("bia · 09:05", new LinhaMensagem(tema, publica("bia", "oi", false)).cabecalho());
        assertEquals("você · 09:05", new LinhaMensagem(tema, publica("ana", "oi", true)).cabecalho());
    }

    @Test
    void privadaTemORotuloPrivadaNoCabecalho() {
        String recebida = new LinhaMensagem(tema, privada("caio", "ana", "psiu", false)).cabecalho();
        assertTrue(recebida.contains("privada"), recebida);
        assertTrue(recebida.contains("caio"), recebida);
        String enviada = new LinhaMensagem(tema, privada("ana", "bia", "psiu", true)).cabecalho();
        assertTrue(enviada.contains("privada"), enviada);
        assertTrue(enviada.contains("bia"), enviada);
    }

    @Test
    void avisoDoSistemaNaoTemCabecalho() {
        assertEquals("", new LinhaMensagem(tema, MensagemTela.sistema("x", T)).cabecalho());
    }

    @Test
    void coresDoBalaoSeguemOTipoENoModoClaroEEscuro() {
        for (Modo modo : Modo.values()) {
            Tema t = new Tema(modo);
            Tema.Paleta p = t.paleta();
            LinhaMensagem minha = new LinhaMensagem(t, publica("ana", "x", true));
            LinhaMensagem outro = new LinhaMensagem(t, publica("bia", "x", false));
            LinhaMensagem priv = new LinhaMensagem(t, privada("bia", "ana", "x", false));
            assertEquals(p.balaoMeu(), minha.corDoBalao());
            assertEquals(p.textoBalaoMeu(), minha.corDoTexto());
            assertEquals(p.balaoOutro(), outro.corDoBalao());
            assertEquals(p.textoBalaoOutro(), outro.corDoTexto());
            assertEquals(p.balaoPrivado(), priv.corDoBalao());
            assertEquals(p.textoBalaoPrivado(), priv.corDoTexto());
        }
    }

    @Test
    void avisoDoSistemaUsaTextoSecundarioSemFundoDeBalao() {
        LinhaMensagem l = new LinhaMensagem(tema, MensagemTela.sistema("x", T));
        assertEquals(tema.paleta().textoSecundario(), l.corDoTexto());
        assertNull(l.corDoBalao());
    }

    @Test
    void mudarOTemaRecolore() {
        LinhaMensagem l = new LinhaMensagem(tema, publica("bia", "x", false));
        tema.alternar();
        l.aplicarTema();
        assertEquals(Tema.paleta(Modo.ESCURO).balaoOutro(), l.corDoBalao());
    }

    @Test
    void textoCurtoFazUmBalaoMaisEstreitoQueOLimite() {
        LinhaMensagem l = new LinhaMensagem(tema, publica("bia", "oi", false));
        l.definirLarguraMaxima(400);
        assertTrue(l.getPreferredSize().width < 200, "largura " + l.getPreferredSize().width);
    }

    @Test
    void textoLongoQuebraNaLarguraMaximaEFicaMaisAlto() {
        String longo = "palavra ".repeat(60);
        LinhaMensagem curta = new LinhaMensagem(tema, publica("bia", "oi", false));
        LinhaMensagem l = new LinhaMensagem(tema, publica("bia", longo, false));
        curta.definirLarguraMaxima(300);
        l.definirLarguraMaxima(300);
        Dimension d = l.getPreferredSize();
        assertTrue(d.width <= 300 + 2 * Tema.espaco(1), "largura " + d.width);
        assertTrue(d.height > 3 * curta.getPreferredSize().height, "altura " + d.height);
    }

    @Test
    void estreitarOLimiteRearrumaOTextoEmMaisLinhas() {
        String texto = "uma frase de tamanho razoável para quebrar em mais linhas quando estreitar";
        LinhaMensagem l = new LinhaMensagem(tema, publica("bia", texto, false));
        l.definirLarguraMaxima(600);
        int alturaLarga = l.getPreferredSize().height;
        l.definirLarguraMaxima(160);
        assertTrue(l.getPreferredSize().height > alturaLarga);
    }

    @Test
    void quebrasDeLinhaDoTextoSaoPreservadas() {
        LinhaMensagem uma = new LinhaMensagem(tema, publica("bia", "a", false));
        LinhaMensagem tres = new LinhaMensagem(tema, publica("bia", "a\nb\nc", false));
        uma.definirLarguraMaxima(400);
        tres.definirLarguraMaxima(400);
        assertTrue(tres.getPreferredSize().height > uma.getPreferredSize().height + 20);
    }

    @Test
    void palavraGiganteSemEspacosNaoEstouraOLimite() {
        LinhaMensagem l = new LinhaMensagem(tema, publica("bia", "x".repeat(400), false));
        l.definirLarguraMaxima(250);
        assertTrue(l.getPreferredSize().width <= 250 + 2 * Tema.espaco(1));
    }

    @Test
    void fontesSeguemOTema() {
        LinhaMensagem l = new LinhaMensagem(tema, publica("bia", "x", false));
        assertTrue(l.fonteDoTexto().getSize() >= 13);
        assertTrue(l.fonteDoCabecalho().getSize() < l.fonteDoTexto().getSize());
    }

    @Test
    void nomeAcessivelTemRemetenteETexto() {
        LinhaMensagem l = new LinhaMensagem(tema, publica("bia", "olá pessoal", false));
        String nome = l.getAccessibleContext().getAccessibleName();
        assertTrue(nome.contains("bia") && nome.contains("olá pessoal"), nome);
    }

    @Test
    void balaoEDesenhadoComACorDoTipo() {
        LinhaMensagem l = new LinhaMensagem(tema, publica("bia", "olá", false));
        l.definirLarguraMaxima(400);
        LayoutDeTeste.forcar(l, 500, l.getPreferredSize().height);
        BufferedImage img = LayoutDeTeste.desenhar(l);
        int x = Tema.espaco(1) + 3; // dentro do balão, na margem esquerda (fora do arredondado)
        int y = img.getHeight() / 2;
        assertEquals(tema.paleta().balaoOutro().getRGB(), new Color(img.getRGB(x, y), true).getRGB());
    }

    @Test
    void textoQueCabeNaLarguraMaximaNaoQuebraEmDuasLinhas() {
        LinhaMensagem umaLinha = new LinhaMensagem(tema, publica("bia", "oi", false));
        LinhaMensagem frase = new LinhaMensagem(tema,
                publica("bia", "Oi, pessoal! Alguém já terminou o trabalho de POO?", false));
        LinhaMensagem aviso = new LinhaMensagem(tema, MensagemTela.sistema("bia entrou na sala geral.", T));
        umaLinha.definirLarguraMaxima(600);
        frase.definirLarguraMaxima(600);
        aviso.definirLarguraMaxima(600);
        assertEquals(umaLinha.getPreferredSize().height, frase.getPreferredSize().height,
                "a frase cabe em 600 px e deveria ficar em uma linha");
        int alturaDeUmaLinhaDeAviso = new LinhaMensagem(tema, MensagemTela.sistema("x", T)).getPreferredSize().height;
        assertEquals(alturaDeUmaLinhaDeAviso, aviso.getPreferredSize().height,
                "o aviso do sistema cabe em uma linha");
    }

    @Test
    void oBalaoTemExatamenteALarguraDoTextoMaisAsMargensQuandoCabe() {
        LinhaMensagem l = new LinhaMensagem(tema, publica("bia", "uma frase curta mas com algum comprimento", false));
        l.definirLarguraMaxima(700);
        int largura = l.getPreferredSize().width;
        l.definirLarguraMaxima(900);
        assertEquals(largura, l.getPreferredSize().width, "com folga, a largura não depende do limite");
    }

    @Test
    void aMargemInternaDoTextoEZeroParaCabecalhoETextoFicaremAlinhados() {
        LinhaMensagem l = new LinhaMensagem(tema, publica("bia", "x", false));
        assertEquals(new java.awt.Insets(0, 0, 0, 0), l.margemDoTexto());
    }

    @Test
    void depoisDeReaplicarOTemaAMargemContinuaZero() {
        LinhaMensagem l = new LinhaMensagem(tema, publica("bia", "x", false));
        tema.alternar();
        tema.instalarLookAndFeel();
        l.aplicarTema();
        assertEquals(new java.awt.Insets(0, 0, 0, 0), l.margemDoTexto());
    }

    @Test
    void mensagemNulaOuTemaNuloLancaExcecao() {
        assertThrows(NullPointerException.class, () -> new LinhaMensagem(null, MensagemTela.sistema("x", T)));
        assertThrows(NullPointerException.class, () -> new LinhaMensagem(tema, null));
        assertThrows(IllegalArgumentException.class, () -> new LinhaMensagem(tema, MensagemTela.sistema("x", T))
                .definirLarguraMaxima(0));
        assertNotNull(new Evento.Ok("x"));
    }
}
