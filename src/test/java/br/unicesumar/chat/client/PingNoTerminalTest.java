package br.unicesumar.chat.client;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.protocol.Evento;
import org.junit.jupiter.api.Test;

/** O PING é assunto da conexão: o terminal não imprime nada por ele. */
class PingNoTerminalTest {

    @Test
    void formatadorDevolveTextoVazioParaPing() {
        assertEquals("", FormatadorTerminal.formatar(new Evento.Ping()));
    }
}
