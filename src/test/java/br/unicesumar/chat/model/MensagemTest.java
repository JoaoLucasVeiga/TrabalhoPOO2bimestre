package br.unicesumar.chat.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MensagemTest {

    private static final Usuario ANA = new Usuario("ana");
    private static final Usuario BIA = new Usuario("bia");
    private static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 1, 9, 5, 30);

    private static Mensagem publica(String conteudo) {
        return Mensagem.nova(ANA, "geral", conteudo, HORA);
    }

    @Test
    void criaMensagemPublicaComTodosOsCampos() {
        Mensagem m = publica("olá");
        assertEquals(ANA, m.remetente());
        assertEquals("olá", m.conteudo());
        assertEquals(HORA, m.horario());
        assertEquals("geral", m.sala());
        assertNull(m.id());
    }

    @Test
    void mensagemPublicaNaoTemDestinatario() {
        Mensagem m = publica("oi");
        assertTrue(m.destinatario().isEmpty());
        assertFalse(m.ehPrivada());
    }

    @Test
    void mensagemPrivadaTemDestinatario() {
        Mensagem m = Mensagem.privada(ANA, BIA, "geral", "psiu", HORA);
        assertEquals(BIA, m.destinatario().orElseThrow());
        assertTrue(m.ehPrivada());
    }

    @Test
    void rejeitaMensagemPrivadaParaSiMesmo() {
        assertThrows(IllegalArgumentException.class,
                () -> Mensagem.privada(ANA, new Usuario("ANA"), "geral", "oi", HORA));
    }

    @Test
    void rejeitaRemetenteNulo() {
        assertThrows(IllegalArgumentException.class,
                () -> Mensagem.nova(null, "geral", "oi", HORA));
    }

    @Test
    void rejeitaConteudoNulo() {
        assertThrows(IllegalArgumentException.class, () -> publica(null));
    }

    @Test
    void rejeitaConteudoVazio() {
        assertThrows(IllegalArgumentException.class, () -> publica(""));
    }

    @Test
    void rejeitaConteudoSoComEspacosOuQuebrasDeLinha() {
        assertThrows(IllegalArgumentException.class, () -> publica("   \n\t "));
    }

    @Test
    void aceitaConteudoComExatamente500Caracteres() {
        String texto = "x".repeat(500);
        assertEquals(texto, publica(texto).conteudo());
    }

    @Test
    void rejeitaConteudoComMaisDe500Caracteres() {
        assertThrows(IllegalArgumentException.class, () -> publica("x".repeat(501)));
    }

    @Test
    void espacosNasPontasNaoContamNoLimiteENaoSaoGuardados() {
        String texto = "x".repeat(500);
        assertEquals(texto, publica("  " + texto + "  ").conteudo());
    }

    @Test
    void mensagemDeErroDoLimiteDizOQueFazer() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> publica("x".repeat(501)));
        assertTrue(e.getMessage().contains("500"));
    }

    @Test
    void rejeitaHorarioNulo() {
        assertThrows(IllegalArgumentException.class,
                () -> Mensagem.nova(ANA, "geral", "oi", (LocalDateTime) null));
    }

    @Test
    void rejeitaSalaNulaOuVazia() {
        assertThrows(IllegalArgumentException.class, () -> Mensagem.nova(ANA, null, "oi", HORA));
        assertThrows(IllegalArgumentException.class, () -> Mensagem.nova(ANA, " ", "oi", HORA));
    }

    @Test
    void criaMensagemUsandoRelogioInjetado() {
        Clock relogio = Clock.fixed(Instant.parse("2026-10-01T12:30:00Z"), ZoneOffset.UTC);
        Mensagem m = Mensagem.nova(ANA, "geral", "oi", relogio);
        assertEquals(LocalDateTime.of(2026, 10, 1, 12, 30), m.horario());
    }

    @Test
    void formataComHoraMinutoRemetenteETexto() {
        assertEquals("[09:05] ana: olá", publica("olá").formatar());
    }

    @Test
    void formataUsandoOsDoisDigitosDaHoraEDoMinuto() {
        Mensagem m = Mensagem.nova(ANA, "geral", "x", LocalDateTime.of(2026, 1, 1, 0, 0));
        assertEquals("[00:00] ana: x", m.formatar());
    }

    @Test
    void formataPreservandoAcentosENomeComMaiuscula() {
        Mensagem m = Mensagem.nova(new Usuario("Ana"), "geral", "ação éção", HORA);
        assertEquals("[09:05] Ana: ação éção", m.formatar());
    }

    @Test
    void comIdDevolveNovaMensagemSemAlterarAOriginal() {
        Mensagem original = publica("oi");
        Mensagem comId = original.comId(7L);
        assertNull(original.id());
        assertEquals(7L, comId.id());
        assertEquals(original.conteudo(), comId.conteudo());
        assertEquals(original.horario(), comId.horario());
        assertEquals(original.sala(), comId.sala());
    }

    @Test
    void comIdMantemODestinatarioDaPrivada() {
        Mensagem m = Mensagem.privada(ANA, BIA, "geral", "oi", HORA).comId(1L);
        assertEquals(BIA, m.destinatario().orElseThrow());
    }

    @Test
    void rejeitaIdZeroOuNegativo() {
        assertThrows(IllegalArgumentException.class, () -> publica("oi").comId(0L));
        assertThrows(IllegalArgumentException.class, () -> publica("oi").comId(-1L));
    }

    @Test
    void mensagensComOsMesmosValoresSaoIguais() {
        assertEquals(publica("oi"), publica("oi"));
        assertEquals(publica("oi").hashCode(), publica("oi").hashCode());
    }

    @Test
    void mensagensComConteudoDiferenteSaoDiferentes() {
        assertNotEquals(publica("oi"), publica("tchau"));
    }
}
