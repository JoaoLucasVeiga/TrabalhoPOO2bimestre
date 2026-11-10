package br.unicesumar.chat.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.client.ui.Tema.Modo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Lista de conversas privadas do painel lateral, com não lidas e conversa aberta (sem tela). */
class PainelLateralConversasTest {

    private final Tema tema = new Tema(Modo.CLARO);
    private final List<String> salasEntradas = new ArrayList<>();
    private final List<String> abertas = new ArrayList<>();
    private final PainelLateral painel = new PainelLateral(tema, salasEntradas::add, abertas::add, () -> { });

    @Test
    void comecaSemConversasComTituloProprio() {
        assertTrue(painel.conversas().isEmpty());
        assertEquals("CONVERSAS", painel.tituloDasConversas());
    }

    @Test
    void mostraAsConversasNaOrdemRecebida() {
        painel.atualizarConversas(List.of("caio", "bia"));
        assertEquals(List.of("caio", "bia"), painel.conversas());
    }

    @Test
    void cliqueEmUmaConversaAbreEla() {
        painel.atualizarConversas(List.of("caio", "bia"));
        painel.acionarConversa(1);
        assertEquals(List.of("bia"), abertas);
    }

    @Test
    void cliqueForaDaListaNaoFazNada() {
        painel.atualizarConversas(List.of("caio"));
        painel.acionarConversa(-1);
        painel.acionarConversa(5);
        assertTrue(abertas.isEmpty());
    }

    @Test
    void naoLidasAparecemNoRotuloEZeramQuandoSaemDoMapa() {
        painel.atualizarConversas(List.of("caio", "bia"));
        painel.atualizarNaoLidas(Map.of("bia", 3));
        assertEquals("caio", painel.rotuloDaConversa(0));
        assertEquals("bia (3)", painel.rotuloDaConversa(1));
        painel.atualizarNaoLidas(Map.of());
        assertEquals("bia", painel.rotuloDaConversa(1));
    }

    @Test
    void totalDeNaoLidasSomaTudo() {
        painel.atualizarNaoLidas(Map.of("bia", 3, "caio", 2));
        assertEquals(5, painel.totalDeNaoLidas());
    }

    @Test
    void conversaSelecionadaFicaMarcadaESaiAoSelecionarNada() {
        painel.atualizarConversas(List.of("caio", "bia"));
        painel.selecionarConversa("bia");
        assertEquals("bia", painel.conversaAtual());
        painel.selecionarConversa(null);
        assertNull(painel.conversaAtual());
    }

    @Test
    void conversaAbertaQueAindaNaoEstaNaListaEIncluida() {
        painel.atualizarConversas(List.of("caio"));
        painel.selecionarConversa("bia");
        assertEquals(List.of("caio", "bia"), painel.conversas());
    }

    @Test
    void cliqueNaSalaAtualDentroDeUmaConversaVoltaParaASala() {
        painel.atualizarSalas(List.of("geral"), "geral");
        painel.acionarSala(0);
        assertTrue(salasEntradas.isEmpty());
        painel.selecionarConversa("bia");
        painel.acionarSala(0);
        assertEquals(List.of("geral"), salasEntradas);
    }

    @Test
    void duploCliqueEmUsuarioContinuaAbrindoOPedidoDeConversa() {
        painel.atualizarUsuarios(List.of("ana", "bia"), "ana");
        painel.acionarUsuario(1);
        painel.acionarUsuario(0);
        assertEquals(List.of("bia"), abertas);
    }
}
