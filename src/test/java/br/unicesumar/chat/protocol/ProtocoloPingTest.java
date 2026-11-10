package br.unicesumar.chat.protocol;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.protocol.Evento.Ping;
import org.junit.jupiter.api.Test;

/** Evento PING: o servidor pergunta "você ainda está aí?" e o cliente responde com /pong. */
class ProtocoloPingTest {

    @Test
    void codificaEDecodificaOPing() {
        assertEquals("PING", Protocolo.codificar(new Ping()));
        assertEquals(new Ping(), Protocolo.decodificar("PING"));
        assertEquals(new Ping(), Protocolo.decodificar(Protocolo.codificar(new Ping())));
    }

    @Test
    void pingComCamposExtrasEhRecusado() {
        assertThrows(ProtocoloException.class, () -> Protocolo.decodificar("PING|lixo"));
    }
}
