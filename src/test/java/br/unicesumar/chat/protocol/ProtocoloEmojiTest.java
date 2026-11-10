package br.unicesumar.chat.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** Emojis são texto comum para o protocolo: vão e voltam sem mudar. */
class ProtocoloEmojiTest {

    @Test
    void emojisSobrevivemAoCodificarEDecodificar() {
        String texto = "oi 😀 👍🏽 ❤️ 👨‍👩‍👧 | fim";
        Evento original = new Evento.Msg("geral", "ana", LocalDateTime.of(2026, 10, 1, 9, 5, 30), texto);
        Evento volta = Protocolo.decodificar(Protocolo.codificar(original));
        assertEquals(original, volta);
    }

    @Test
    void entradaDoClienteComEmojiVoltaIgual() {
        String texto = "🎉🎉 parabéns";
        assertEquals(texto, Protocolo.decodificarEntrada(Protocolo.codificarEntrada(texto)));
    }
}
