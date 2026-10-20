package br.unicesumar.chat.protocol;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.protocol.Evento.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventoTest {

    private static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 1, 9, 5);

    @Test
    void msgPodeSerCriadaAPartirDeUmaMensagemDeDominio() {
        Mensagem m = Mensagem.nova(new Usuario("ana"), "geral", "oi", HORA);
        assertEquals(new Msg("geral", "ana", HORA, "oi"), Msg.de(m));
    }

    @Test
    void camposNulosSaoRejeitados() {
        assertThrows(NullPointerException.class, () -> new Msg(null, "ana", HORA, "oi"));
        assertThrows(NullPointerException.class, () -> new Msg("geral", "ana", null, "oi"));
        assertThrows(NullPointerException.class, () -> new Sistema(null));
        assertThrows(NullPointerException.class, () -> new Erro(null));
        assertThrows(NullPointerException.class, () -> new Salas(null));
    }

    @Test
    void listasDosEventosSaoCopiasImutaveis() {
        List<String> origem = new ArrayList<>(List.of("geral"));
        Salas salas = new Salas(origem);
        origem.add("java");
        assertEquals(List.of("geral"), salas.nomes());
        assertThrows(UnsupportedOperationException.class, () -> salas.nomes().add("x"));
    }
}
