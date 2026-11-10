package br.unicesumar.chat.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Catálogo de emojis e regra da figurinha (mensagem só de emojis). */
class EmojisTest {

    @Test
    void umEmojiSozinhoEFigurinha() {
        assertTrue(Emojis.ehFigurinha("😀"));
        assertTrue(Emojis.ehFigurinha("  👍  "));
    }

    @Test
    void ateTresEmojisSeguidosSaoFigurinha() {
        assertTrue(Emojis.ehFigurinha("😀😀😀"));
        assertTrue(Emojis.ehFigurinha("😀 🎉 🔥"));
    }

    @Test
    void maisDeTresEmojisNaoSaoFigurinha() {
        assertFalse(Emojis.ehFigurinha("😀😀😀😀"));
    }

    @Test
    void textoComEmojiNaoEFigurinha() {
        assertFalse(Emojis.ehFigurinha("oi 😀"));
        assertFalse(Emojis.ehFigurinha("a"));
        assertFalse(Emojis.ehFigurinha("123"));
    }

    @Test
    void vazioOuNuloNaoSaoFigurinha() {
        assertFalse(Emojis.ehFigurinha(""));
        assertFalse(Emojis.ehFigurinha("   "));
        assertFalse(Emojis.ehFigurinha(null));
    }

    @Test
    void emojiComVariacaoESequenciaContaComoUmSo() {
        assertTrue(Emojis.ehFigurinha("❤️"));
        assertTrue(Emojis.ehFigurinha("👍🏽"));
        assertTrue(Emojis.ehFigurinha("👨‍👩‍👧"));
    }

    @Test
    void catalogoTemEmojisSemRepeticaoENaoPodeSerAlterado() {
        var catalogo = Emojis.catalogo();
        assertTrue(catalogo.size() >= 40);
        assertEquals(catalogo.size(), catalogo.stream().distinct().count());
        assertTrue(catalogo.stream().allMatch(Emojis::ehFigurinha));
        assertThrows(UnsupportedOperationException.class, () -> catalogo.add("x"));
    }
}
