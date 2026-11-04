package br.unicesumar.chat.protocol;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.protocol.Evento.Papel;
import org.junit.jupiter.api.Test;

/** Evento PAPEL: o servidor diz ao cliente se ele é administrador. */
class ProtocoloPapelTest {

    @Test
    void codificaAdministradorEUsuario() {
        assertEquals("PAPEL|admin", Protocolo.codificar(new Papel(true)));
        assertEquals("PAPEL|usuario", Protocolo.codificar(new Papel(false)));
    }

    @Test
    void decodificaOsDoisPapeis() {
        assertEquals(new Papel(true), Protocolo.decodificar("PAPEL|admin"));
        assertEquals(new Papel(false), Protocolo.decodificar("PAPEL|usuario"));
    }

    @Test
    void idaEVolta() {
        for (boolean admin : new boolean[] {true, false}) {
            assertEquals(new Papel(admin), Protocolo.decodificar(Protocolo.codificar(new Papel(admin))));
        }
    }

    @Test
    void papelDesconhecidoOuAusenteEhRecusado() {
        assertThrows(ProtocoloException.class, () -> Protocolo.decodificar("PAPEL|chefao"));
        assertThrows(ProtocoloException.class, () -> Protocolo.decodificar("PAPEL"));
        assertThrows(ProtocoloException.class, () -> Protocolo.decodificar("PAPEL|admin|extra"));
    }
}
