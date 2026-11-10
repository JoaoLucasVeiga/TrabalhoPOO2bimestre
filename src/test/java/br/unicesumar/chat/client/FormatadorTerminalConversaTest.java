package br.unicesumar.chat.client;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.protocol.Evento;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class FormatadorTerminalConversaTest {

    private static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 1, 9, 5, 30);

    @Test
    void conversasListaOsNomesOuDizQueNaoHaNenhuma() {
        assertEquals("Conversas privadas: bia, caio",
                FormatadorTerminal.formatar(new Evento.Conversas(List.of("bia", "caio"))));
        assertEquals("Nenhuma conversa privada.", FormatadorTerminal.formatar(new Evento.Conversas(List.of())));
    }

    @Test
    void conversaMostraCabecalhoMensagensERodape() {
        String texto = FormatadorTerminal.formatar(new Evento.Conversa("bia", List.of(
                new Evento.Privada("ana", "bia", HORA, "oi"), new Evento.Privada("bia", "ana", HORA, "oi!"))));
        assertTrue(texto.startsWith("--- Conversa com bia (2 mensagens) ---"));
        assertTrue(texto.contains("[09:05] (privada) ana -> bia: oi"));
        assertTrue(texto.endsWith("--- fim da conversa ---"));
    }

    @Test
    void conversaVaziaDizQueAindaNaoHaMensagens() {
        assertEquals("Você ainda não trocou mensagens com bia.",
                FormatadorTerminal.formatar(new Evento.Conversa("bia", List.of())));
    }
}
