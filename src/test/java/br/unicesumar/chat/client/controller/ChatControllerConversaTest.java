package br.unicesumar.chat.client.controller;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.client.net.ConexaoFalsa;
import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.protocol.Mensagens;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Conversas privadas no controlador (sem rede e sem Swing): contexto sala x conversa, não lidas, envio. */
class ChatControllerConversaTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 22, 9, 1);

    private final ChatViewGravadora tela = new ChatViewGravadora();
    private final ConexaoFalsa conexao = new ConexaoFalsa();
    private ChatController controller;

    private ChatController logado(ChatViewGravadora view, boolean comConversas) {
        conexao.receberEvento(new Evento.Sistema("Bem-vindo ao chat!"))
                .receberEvento(new Evento.Ok(Mensagens.entrouComo("ana", "geral")));
        controller = new ChatController(view.view(), (h, p) -> conexao, RELOGIO, false);
        if (comConversas) {
            controller.habilitarConversas();
        }
        assertTrue(controller.conectar("localhost", "5000", "ana", "senha123", false).sucesso());
        view.limpar();
        return controller;
    }

    private ChatController logado() {
        return logado(tela, true);
    }

    private static Evento.Privada privada(String de, String para, String texto) {
        return new Evento.Privada(de, para, T, texto);
    }

    @SuppressWarnings("unchecked")
    private static <E> E argumento(ChatViewGravadora.Chamada c, int i) {
        return (E) c.argumentos().get(i);
    }

    // ---------- login e lista ----------

    @Test
    void depoisDoLoginPedeAsConversasQuandoATelaSuporta() {
        logado();
        assertTrue(conexao.enviadas().contains("/conversas"));
    }

    @Test
    void semHabilitarNaoPedeConversasEMantemAPrivadaNaTela() {
        ChatViewGravadora semSuporte = new ChatViewGravadora();
        logado(semSuporte, false);
        assertFalse(conexao.enviadas().contains("/conversas"));
        controller.tratar(privada("bia", "ana", "oi"));
        assertEquals(1, semSuporte.de("adicionar").size());
        assertTrue(semSuporte.de("atualizarConversas").isEmpty());
    }

    @Test
    void listaDeConversasDoServidorVaiParaATela() {
        logado();
        controller.tratar(new Evento.Conversas(List.of("bia", "caio")));
        assertEquals(List.of("bia", "caio"), argumento(tela.ultima("atualizarConversas"), 0));
    }

    // ---------- abrir conversa ----------

    @Test
    void abrirConversaPedeOHistoricoMostraATelaVaziaELimpaONaoLido() {
        logado();
        controller.tratar(privada("bia", "ana", "oi"));
        controller.abrirConversa("bia");
        assertEquals("bia", controller.conversaAtual());
        assertTrue(conexao.enviadas().contains("/conversa bia 50"));
        assertEquals("bia", tela.ultima("mostrarConversa").argumentos().get(0));
        assertEquals(List.of(), argumento(tela.ultima("mostrarConversa"), 1));
        Map<String, Integer> naoLidas = argumento(tela.ultima("atualizarNaoLidas"), 0);
        assertFalse(naoLidas.containsKey("bia"));
    }

    @Test
    void historicoDaConversaChegandoSubstituiATelaComAsMensagensMarcadasComoMinhasOuNao() {
        logado();
        controller.abrirConversa("bia");
        controller.tratar(new Evento.Conversa("bia", List.of(privada("ana", "bia", "oi"), privada("bia", "ana", "oi!"))));
        List<MensagemTela> msgs = argumento(tela.ultima("mostrarConversa"), 1);
        assertEquals(2, msgs.size());
        assertTrue(msgs.get(0).minha());
        assertFalse(msgs.get(1).minha());
    }

    @Test
    void historicoDeOutraConversaQueNaoEAAtualEIgnorado() {
        logado();
        controller.abrirConversa("bia");
        tela.limpar();
        controller.tratar(new Evento.Conversa("caio", List.of(privada("caio", "ana", "velha"))));
        assertTrue(tela.de("mostrarConversa").isEmpty());
    }

    @Test
    void naoAbreConversaConsigoMesmoEmBrancoOuDesconectado() {
        logado();
        controller.abrirConversa("ANA");
        controller.abrirConversa("  ");
        assertEquals(2, tela.de("mostrarErro").size());
        assertNull(controller.conversaAtual());
        controller.sair();
        tela.limpar();
        controller.abrirConversa("bia");
        assertEquals(1, tela.de("mostrarErro").size());
        assertNull(controller.conversaAtual());
    }

    @Test
    void semHabilitarNaoAbreConversa() {
        ChatViewGravadora semSuporte = new ChatViewGravadora();
        logado(semSuporte, false);
        controller.abrirConversa("bia");
        assertNull(controller.conversaAtual());
        assertFalse(conexao.enviadas().stream().anyMatch(l -> l.startsWith("/conversa ")));
    }

    // ---------- enviar ----------

    @Test
    void textoNaConversaVaiComoMsgParaOInterlocutor() {
        logado();
        controller.abrirConversa("bia");
        controller.enviar("tudo bem?");
        assertTrue(conexao.enviadas().contains("/msg bia tudo bem?"));
    }

    @Test
    void comandosContinuamSendoComandosDentroDaConversa() {
        logado();
        controller.abrirConversa("bia");
        controller.enviar("/users");
        assertTrue(conexao.enviadas().contains("/users"));
        assertFalse(conexao.enviadas().stream().anyMatch(l -> l.startsWith("/msg bia /users")));
    }

    @Test
    void forDeConversaOTextoVaiParaASalaComoAntes() {
        logado();
        controller.enviar("oi sala");
        assertTrue(conexao.enviadas().contains("oi sala"));
    }

    // ---------- mensagens chegando ----------

    @Test
    void privadaDoInterlocutorNaConversaAbertaApareceSemContarComoNaoLida() {
        logado();
        controller.abrirConversa("bia");
        tela.limpar();
        controller.tratar(privada("bia", "ana", "oi"));
        assertEquals(1, tela.de("adicionar").size());
        Map<String, Integer> naoLidas = argumento(tela.ultima("atualizarNaoLidas"), 0);
        assertTrue(naoLidas.isEmpty());
    }

    @Test
    void privadaDeOutraPessoaNaoApareceNaTelaMasViraConversaENaoLida() {
        logado();
        controller.abrirConversa("bia");
        tela.limpar();
        controller.tratar(privada("caio", "ana", "ei"));
        assertTrue(tela.de("adicionar").isEmpty());
        assertEquals(List.of("caio", "bia"), argumento(tela.ultima("atualizarConversas"), 0));
        Map<String, Integer> naoLidas = argumento(tela.ultima("atualizarNaoLidas"), 0);
        assertEquals(1, naoLidas.get("caio"));
    }

    @Test
    void privadaRecebidaNaSalaVaiParaAListaEContaNaoLidaSemAparecerNoChatDaSala() {
        logado();
        controller.tratar(privada("bia", "ana", "oi"));
        controller.tratar(privada("bia", "ana", "oi de novo"));
        assertTrue(tela.de("adicionar").isEmpty());
        Map<String, Integer> naoLidas = argumento(tela.ultima("atualizarNaoLidas"), 0);
        assertEquals(2, naoLidas.get("bia"));
    }

    @Test
    void privadaQueEuMandeiNaoContaComoNaoLidaMasEntraNaLista() {
        logado();
        controller.tratar(privada("ana", "bia", "oi"));
        assertEquals(List.of("bia"), argumento(tela.ultima("atualizarConversas"), 0));
        Map<String, Integer> naoLidas = argumento(tela.ultima("atualizarNaoLidas"), 0);
        assertTrue(naoLidas.isEmpty());
    }

    @Test
    void mensagemPublicaDuranteAConversaFicaGuardadaEVoltaQuandoSeVoltaParaASala() {
        logado();
        controller.abrirConversa("bia");
        tela.limpar();
        controller.tratar(new Evento.Msg("geral", "caio", T, "ola sala"));
        assertTrue(tela.de("adicionar").isEmpty());
        controller.voltarParaSala();
        assertNull(controller.conversaAtual());
        List<MensagemTela> noPalco = argumento(tela.ultima("substituirHistorico"), 0);
        assertEquals("ola sala", noPalco.get(noPalco.size() - 1).texto());
        assertEquals("geral", tela.ultima("substituirHistorico").argumentos().get(1));
    }

    @Test
    void avisosDeEntradaESaidaDaSalaNaoAparecemNaConversa() {
        logado();
        controller.abrirConversa("bia");
        tela.limpar();
        controller.tratar(new Evento.Sistema("caio entrou na sala geral."));
        assertTrue(tela.de("adicionar").isEmpty());
    }

    // ---------- voltar para a sala ----------

    @Test
    void clicarNaSalaAtualDentroDaConversaVoltaSemPedirJoin() {
        logado();
        controller.abrirConversa("bia");
        controller.entrarSala("geral");
        assertNull(controller.conversaAtual());
        assertFalse(conexao.enviadas().contains("/join geral"));
        assertEquals(1, tela.de("substituirHistorico").size());
    }

    @Test
    void entrarEmOutraSalaSoSaiDaConversaQuandoOServidorConfirma() {
        logado();
        controller.abrirConversa("bia");
        controller.entrarSala("java");
        assertEquals("bia", controller.conversaAtual());
        assertTrue(conexao.enviadas().contains("/join java"));
        controller.tratar(new Evento.Historico(List.of()));
        controller.tratar(new Evento.Ok(Mensagens.entrouNaSala("java")));
        assertNull(controller.conversaAtual());
    }

    @Test
    void historicoAnteriorEBuscaNaoValemNaConversa() {
        logado();
        controller.abrirConversa("bia");
        tela.limpar();
        controller.carregarHistoricoAnterior();
        assertEquals(1, tela.de("mostrarAviso").size());
        assertFalse(conexao.enviadas().stream().anyMatch(l -> l.startsWith("/history")));
    }
}
