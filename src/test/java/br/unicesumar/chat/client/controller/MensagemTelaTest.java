package br.unicesumar.chat.client.controller;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.client.controller.MensagemTela.Tipo;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.protocol.Evento;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MensagemTelaTest {

    private static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 1, 9, 5, 30);

    @Test
    void mensagemPublicaDeOutroUsuarioNaoEMinha() {
        MensagemTela m = MensagemTela.publica(new Evento.Msg("geral", "bia", HORA, "oi"), "ana");
        assertEquals(Tipo.PUBLICA, m.tipo());
        assertEquals("bia", m.remetente());
        assertNull(m.destinatario());
        assertEquals("oi", m.texto());
        assertEquals(HORA, m.horario());
        assertFalse(m.minha());
    }

    @Test
    void mensagemPublicaMinhaIgnoraMaiusculas() {
        MensagemTela m = MensagemTela.publica(new Evento.Msg("geral", "Ana", HORA, "oi"), "ANA");
        assertTrue(m.minha());
    }

    @Test
    void mensagemPrivadaGuardaDestinatarioEMarcaAsMinhas() {
        MensagemTela enviada = MensagemTela.privada(new Evento.Privada("ana", "bia", HORA, "psiu"), "ana");
        MensagemTela recebida = MensagemTela.privada(new Evento.Privada("bia", "ana", HORA, "oi"), "ana");
        assertEquals(Tipo.PRIVADA, enviada.tipo());
        assertEquals("bia", enviada.destinatario());
        assertTrue(enviada.minha());
        assertFalse(recebida.minha());
    }

    @Test
    void avisoDoSistemaNaoTemRemetenteNemEMinha() {
        MensagemTela m = MensagemTela.sistema("bia entrou na sala geral.", HORA);
        assertEquals(Tipo.SISTEMA, m.tipo());
        assertNull(m.remetente());
        assertFalse(m.minha());
        assertEquals("bia entrou na sala geral.", m.texto());
    }

    @Test
    void formatoSegueOPadraoHoraMinutoNomeETexto() {
        MensagemTela m = MensagemTela.publica(new Evento.Msg("geral", "ana", HORA, "olá"), "bia");
        assertEquals("[09:05] ana: olá", m.formatar());
    }

    @Test
    void formatoEIgualAoDoDominio() {
        Mensagem dominio = Mensagem.nova(new Usuario("ana"), "geral", "igual?", HORA);
        MensagemTela tela = MensagemTela.publica(Evento.Msg.de(dominio), "bia");
        assertEquals(dominio.formatar(), tela.formatar());
    }

    @Test
    void mensagensComOsMesmosDadosSaoIguais() {
        Evento.Msg e = new Evento.Msg("geral", "ana", HORA, "oi");
        assertEquals(MensagemTela.publica(e, "bia"), MensagemTela.publica(e, "bia"));
    }

    @Test
    void camposObrigatoriosNaoPodemSerNulos() {
        assertThrows(NullPointerException.class, () -> MensagemTela.sistema(null, HORA));
        assertThrows(NullPointerException.class, () -> MensagemTela.sistema("x", null));
        assertThrows(NullPointerException.class,
                () -> MensagemTela.publica(new Evento.Msg("geral", "ana", HORA, "oi"), null));
    }
}
