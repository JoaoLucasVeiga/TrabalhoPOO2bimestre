package br.unicesumar.chat.client.ui;

import java.util.List;

/**
 * Emojis do cliente gráfico: o catálogo do seletor e a regra da figurinha. Existe só na interface;
 * para o servidor, um emoji é texto comum (UTF-8) e conta para o limite de 500 caracteres.
 */
public final class Emojis {

    /** Até quantos emojis uma mensagem pode ter para ser mostrada como figurinha. */
    public static final int MAXIMO_NA_FIGURINHA = 3;

    private static final int JUNTADOR_ZWJ = 0x200D;
    private static final int SELETOR_DE_EMOJI = 0xFE0F;
    private static final int SELETOR_DE_TEXTO = 0xFE0E;
    private static final int ANEL_DO_TECLADO = 0x20E3;

    private static final List<String> CATALOGO = List.of(
            "😀", "😁", "😂", "🤣", "😊", "😍", "😘", "😎",
            "🤔", "😅", "😢", "😭", "😡", "😴", "🥳", "🤯",
            "😱", "🙄", "😬", "🤗", "😇", "🤩", "😏", "😜",
            "👍", "👎", "👏", "🙏", "💪", "👋", "🤝", "✌️",
            "❤️", "💔", "🔥", "✨", "🎉", "🎂", "💯", "⭐",
            "☕", "🍕", "🍔", "🍺", "⚽", "🎮", "🎵", "🚀",
            "✅", "❌", "⚠️", "💡", "📌", "📎", "💻", "📚");

    private Emojis() {
    }

    /** Emojis oferecidos no seletor (lista fixa, sem repetição). */
    public static List<String> catalogo() {
        return CATALOGO;
    }

    /**
     * Indica se o texto é só de emojis (de 1 a {@value #MAXIMO_NA_FIGURINHA}, espaços à parte) e deve ser
     * mostrado grande, como uma figurinha. Sequências (pele, família, bandeira) contam como um emoji.
     */
    public static boolean ehFigurinha(String texto) {
        if (texto == null || texto.isBlank()) {
            return false;
        }
        int emojis = 0;
        int indicadoresRegionais = 0;
        boolean anteriorJuntou = false;
        boolean temEmojiAntes = false;
        for (int i = 0; i < texto.length();) {
            int c = texto.codePointAt(i);
            i += Character.charCount(c);
            if (Character.isWhitespace(c)) {
                anteriorJuntou = false;
                indicadoresRegionais = 0;
                continue;
            }
            if (c == JUNTADOR_ZWJ) {
                if (!temEmojiAntes) {
                    return false;
                }
                anteriorJuntou = true;
            } else if (c == SELETOR_DE_EMOJI || c == SELETOR_DE_TEXTO || c == ANEL_DO_TECLADO
                    || Character.isEmojiModifier(c)) {
                if (!temEmojiAntes) {
                    return false;
                }
            } else if (c >= 0x1F1E6 && c <= 0x1F1FF) { // bandeira = duas letras regionais
                if (indicadoresRegionais % 2 == 0) {
                    emojis++;
                }
                indicadoresRegionais++;
                temEmojiAntes = true;
                anteriorJuntou = false;
            } else if (Character.isExtendedPictographic(c)) {
                if (!anteriorJuntou) {
                    emojis++;
                }
                anteriorJuntou = false;
                temEmojiAntes = true;
            } else {
                return false;
            }
        }
        return emojis >= 1 && emojis <= MAXIMO_NA_FIGURINHA;
    }
}
