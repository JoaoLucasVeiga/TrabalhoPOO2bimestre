package br.unicesumar.chat.protocol;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.protocol.Evento.Conversa;
import br.unicesumar.chat.protocol.Evento.Conversas;
import br.unicesumar.chat.protocol.Evento.Privada;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Eventos CONVERSAS (lista de interlocutores) e CONVERSA (histórico de uma conversa privada). */
class ProtocoloConversaTest {

    private static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 1, 9, 5, 30);

    @Test
    void conversasIdaEVolta() {
        Evento e = new Conversas(List.of("bia", "caio"));
        assertEquals("CONVERSAS|bia|caio", Protocolo.codificar(e));
        assertEquals(e, Protocolo.decodificar(Protocolo.codificar(e)));
    }

    @Test
    void conversasVaziaIdaEVolta() {
        Evento e = new Conversas(List.of());
        assertEquals(e, Protocolo.decodificar(Protocolo.codificar(e)));
    }

    @Test
    void conversaIdaEVoltaComTextoComSeparadorEAcento() {
        Evento e = new Conversa("bia", List.of(
                new Privada("ana", "bia", HORA, "oi | tudo bem?"),
                new Privada("bia", "ana", HORA.plusMinutes(1), "tudo ótimo\nobrigada")));
        assertEquals(e, Protocolo.decodificar(Protocolo.codificar(e)));
    }

    @Test
    void conversaVaziaIdaEVolta() {
        Evento e = new Conversa("bia", List.of());
        assertEquals("CONVERSA|bia", Protocolo.codificar(e));
        assertEquals(e, Protocolo.decodificar("CONVERSA|bia"));
    }

    @Test
    void conversaSemInterlocutorOuComCamposQuebradosEhRecusada() {
        assertThrows(ProtocoloException.class, () -> Protocolo.decodificar("CONVERSA"));
        assertThrows(ProtocoloException.class, () -> Protocolo.decodificar("CONVERSA|bia|ana|bia|2026-10-01T09:05:30"));
    }
}
