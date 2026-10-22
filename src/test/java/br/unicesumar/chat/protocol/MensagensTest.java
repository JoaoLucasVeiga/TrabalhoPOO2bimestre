package br.unicesumar.chat.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Textos que servidor e clientes precisam reconhecer: construir e ler têm de ser inversos. */
class MensagensTest {

    @Test
    void entradaNoChatIdaEVolta() {
        String texto = Mensagens.entrouComo("ana_01", "geral");
        assertEquals("Você entrou como ana_01 na sala geral.", texto);
        Mensagens.Entrada entrada = Mensagens.lerEntrada(texto).orElseThrow();
        assertEquals("ana_01", entrada.nome());
        assertEquals("geral", entrada.sala());
    }

    @Test
    void lerEntradaIgnoraOutrosTextos() {
        assertEquals(Optional.empty(), Mensagens.lerEntrada("Até logo!"));
        assertEquals(Optional.empty(), Mensagens.lerEntrada("Você entrou na sala java."));
        assertEquals(Optional.empty(), Mensagens.lerEntrada(""));
        assertEquals(Optional.empty(), Mensagens.lerEntrada(null));
    }

    @Test
    void mudancaDeSalaIdaEVolta() {
        String texto = Mensagens.entrouNaSala("java_8");
        assertEquals("Você entrou na sala java_8.", texto);
        assertEquals("java_8", Mensagens.lerMudancaDeSala(texto).orElseThrow());
    }

    @Test
    void lerMudancaDeSalaIgnoraOutrosTextos() {
        assertTrue(Mensagens.lerMudancaDeSala(Mensagens.entrouComo("ana", "geral")).isEmpty());
        assertTrue(Mensagens.lerMudancaDeSala("Histórico exportado.").isEmpty());
        assertTrue(Mensagens.lerMudancaDeSala(null).isEmpty());
    }

    @Test
    void avisosDePresencaSaoReconhecidos() {
        assertTrue(Mensagens.ehAvisoDePresenca(Mensagens.avisoDeEntrada("bia", "geral")));
        assertTrue(Mensagens.ehAvisoDePresenca(Mensagens.avisoDeSaida("bia", "geral")));
        assertEquals("bia entrou na sala geral.", Mensagens.avisoDeEntrada("bia", "geral"));
        assertEquals("bia saiu da sala geral.", Mensagens.avisoDeSaida("bia", "geral"));
    }

    @Test
    void outrosAvisosNaoSaoPresenca() {
        assertFalse(Mensagens.ehAvisoDePresenca("Comandos disponíveis:\n/join sala - entra em uma sala"));
        assertFalse(Mensagens.ehAvisoDePresenca(Mensagens.SEM_RESULTADO_DE_BUSCA));
        assertFalse(Mensagens.ehAvisoDePresenca("alguém entrou na sala, mas isto é uma frase solta"));
        assertFalse(Mensagens.ehAvisoDePresenca(null));
    }

    @Test
    void avisosSemResultadoTemTextoFixoEDiferente() {
        assertEquals("Nenhuma mensagem encontrada.", Mensagens.SEM_RESULTADO_DE_BUSCA);
        assertEquals("Não há mensagens anteriores nesta sala.", Mensagens.SEM_HISTORICO);
        assertNotEquals(Mensagens.SEM_RESULTADO_DE_BUSCA, Mensagens.SEM_HISTORICO);
    }
}
