package br.unicesumar.chat.client;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.protocol.Evento;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class FormatadorTerminalTest {

    private static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 1, 9, 5, 30);

    @Test
    void mensagemPublicaUsaOFormatoPadraoComHoraMinutoENome() {
        assertEquals("[09:05] ana: olá",
                FormatadorTerminal.formatar(new Evento.Msg("geral", "ana", HORA, "olá")));
    }

    @Test
    void formatoDaMensagemEIgualAoDoDominio() {
        Mensagem m = Mensagem.nova(new Usuario("ana"), "geral", "igual?", HORA);
        assertEquals(m.formatar(), FormatadorTerminal.formatar(Evento.Msg.de(m)));
    }

    @Test
    void mensagemPrivadaMostraRotuloRemetenteEDestinatario() {
        assertEquals("[09:05] (privada) ana -> bia: psiu",
                FormatadorTerminal.formatar(new Evento.Privada("ana", "bia", HORA, "psiu")));
    }

    @Test
    void avisoDoSistemaTemMarcador() {
        assertEquals("* bia entrou na sala geral.",
                FormatadorTerminal.formatar(new Evento.Sistema("bia entrou na sala geral.")));
    }

    @Test
    void avisoComVariasLinhasPreservaAsQuebras() {
        assertEquals("* Comandos:\n/help - ajuda",
                FormatadorTerminal.formatar(new Evento.Sistema("Comandos:\n/help - ajuda")));
    }

    @Test
    void okEErroTemRotulosDistintos() {
        assertEquals("[ok] feito", FormatadorTerminal.formatar(new Evento.Ok("feito")));
        assertEquals("[erro] falhou", FormatadorTerminal.formatar(new Evento.Erro("falhou")));
    }

    @Test
    void usuariosListaNomesComAQuantidade() {
        assertEquals("Online em geral (2): ana, bia",
                FormatadorTerminal.formatar(new Evento.Usuarios("geral", List.of("ana", "bia"))));
    }

    @Test
    void usuariosVazioDizQueNinguemEstaOnline() {
        assertEquals("Ninguém online em geral.",
                FormatadorTerminal.formatar(new Evento.Usuarios("geral", List.of())));
    }

    @Test
    void salasListaOsNomes() {
        assertEquals("Salas: geral, java",
                FormatadorTerminal.formatar(new Evento.Salas(List.of("geral", "java"))));
        assertEquals("Nenhuma sala.", FormatadorTerminal.formatar(new Evento.Salas(List.of())));
    }

    @Test
    void historicoTemCabecalhoMensagensERodape() {
        Evento.Historico h = new Evento.Historico(List.of(
                new Evento.Msg("geral", "ana", HORA, "um"),
                new Evento.Msg("geral", "bia", HORA.plusMinutes(1), "dois")));
        assertEquals("--- Histórico (2 mensagens) ---\n[09:05] ana: um\n[09:06] bia: dois\n"
                + "--- fim do histórico ---", FormatadorTerminal.formatar(h));
    }

    @Test
    void historicoComUmaMensagemUsaOSingular() {
        Evento.Historico h = new Evento.Historico(List.of(new Evento.Msg("geral", "ana", HORA, "um")));
        assertTrue(FormatadorTerminal.formatar(h).startsWith("--- Histórico (1 mensagem) ---"));
    }

    @Test
    void historicoVazioDizQueNaoHaMensagens() {
        assertEquals("Não há mensagens anteriores.",
                FormatadorTerminal.formatar(new Evento.Historico(List.of())));
    }

    @Test
    void eventoNuloLancaExcecao() {
        assertThrows(NullPointerException.class, () -> FormatadorTerminal.formatar(null));
    }
}
