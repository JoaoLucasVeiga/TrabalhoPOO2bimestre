package br.unicesumar.chat.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.client.ui.Tema.Modo;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Painel de salas e de usuários online (sem tela). */
class PainelLateralTest {

    private final Tema tema = new Tema(Modo.CLARO);
    private final List<String> salasEntradas = new ArrayList<>();
    private final List<String> privadas = new ArrayList<>();
    private int novasSalas;
    private final PainelLateral painel = new PainelLateral(tema, salasEntradas::add, privadas::add, () -> novasSalas++);

    @Test
    void comecaSemSalasNemUsuarios() {
        assertTrue(painel.salas().isEmpty());
        assertTrue(painel.usuarios().isEmpty());
        assertEquals("ONLINE (0)", painel.tituloDosUsuarios());
    }

    @Test
    void mostraAsSalasERegistraAAtual() {
        painel.atualizarSalas(List.of("geral", "java"), "java");
        assertEquals(List.of("geral", "java"), painel.salas());
        assertEquals("java", painel.salaAtual());
    }

    @Test
    void salaAtualQueNaoVeioNaListaEIncluida() {
        painel.atualizarSalas(List.of("geral"), "nova");
        assertTrue(painel.salas().contains("nova"));
        assertEquals("nova", painel.salaAtual());
    }

    @Test
    void usuariosFicamEmOrdemAlfabeticaComOProprioPrimeiroEEmDestaque() {
        painel.atualizarUsuarios(List.of("caio", "Bia", "ana", "dani"), "ana");
        assertEquals(List.of("ana", "Bia", "caio", "dani"), painel.usuarios());
        painel.atualizarUsuarios(List.of("caio", "Bia", "dani", "zeca"), "zeca");
        assertEquals(List.of("zeca", "Bia", "caio", "dani"), painel.usuarios());
        assertTrue(painel.rotuloDoUsuario(0).contains("você"));
        assertFalse(painel.rotuloDoUsuario(1).contains("você"));
    }

    @Test
    void tituloMostraAQuantidadeOnline() {
        painel.atualizarUsuarios(List.of("ana", "bia", "caio"), "ana");
        assertEquals("ONLINE (3)", painel.tituloDosUsuarios());
    }

    @Test
    void comparaONomeDoProprioSemDiferenciarMaiusculas() {
        painel.atualizarUsuarios(List.of("Ana", "bia"), "ANA");
        assertEquals("Ana", painel.usuarios().get(0));
        assertTrue(painel.rotuloDoUsuario(0).contains("você"));
    }

    @Test
    void cliqueEmOutraSalaEntraNelaMasNaAtualNaoFazNada() {
        painel.atualizarSalas(List.of("geral", "java"), "geral");
        painel.acionarSala(1);
        painel.acionarSala(0);
        assertEquals(List.of("java"), salasEntradas);
    }

    @Test
    void duploCliqueEmOutroUsuarioAbreConversaPrivadaMasNoProprioNao() {
        painel.atualizarUsuarios(List.of("ana", "bia", "caio"), "ana");
        painel.acionarUsuario(0);
        painel.acionarUsuario(2);
        assertEquals(List.of("caio"), privadas);
    }

    @Test
    void indicesInvalidosSaoIgnorados() {
        painel.atualizarSalas(List.of("geral"), "geral");
        painel.atualizarUsuarios(List.of("ana"), "ana");
        painel.acionarSala(-1);
        painel.acionarSala(5);
        painel.acionarUsuario(-1);
        painel.acionarUsuario(9);
        assertTrue(salasEntradas.isEmpty());
        assertTrue(privadas.isEmpty());
    }

    @Test
    void botaoNovaSalaChamaOAcaoDeNovaSala() {
        painel.botaoNovaSala().doClick();
        painel.botaoNovaSala().doClick();
        assertEquals(2, novasSalas);
    }

    @Test
    void atualizarComListaVaziaLimpa() {
        painel.atualizarUsuarios(List.of("ana"), "ana");
        painel.atualizarUsuarios(List.of(), "ana");
        assertTrue(painel.usuarios().isEmpty());
        assertEquals("ONLINE (0)", painel.tituloDosUsuarios());
    }

    @Test
    void temDicasENomesAcessiveis() {
        assertNotNull(painel.botaoNovaSala().getToolTipText());
        assertNotNull(painel.listaDeSalas().getToolTipText());
        assertNotNull(painel.listaDeUsuarios().getToolTipText());
        assertTrue(painel.listaDeUsuarios().getToolTipText().toLowerCase().contains("duplo clique"));
        assertNotNull(painel.listaDeSalas().getAccessibleContext().getAccessibleName());
        assertNotNull(painel.listaDeUsuarios().getAccessibleContext().getAccessibleName());
    }

    @Test
    void fontesDosItensTemPeloMenos13() {
        assertTrue(painel.listaDeSalas().getFont().getSize() >= 13);
        assertTrue(painel.listaDeUsuarios().getFont().getSize() >= 13);
    }

    @Test
    void mudarOTemaTrocaAsCores() {
        tema.alternar();
        assertEquals(Tema.paleta(Modo.ESCURO).superficie(), painel.getBackground());
    }

    @Test
    void argumentosNulosLancamExcecao() {
        assertThrows(NullPointerException.class, () -> new PainelLateral(null, s -> { }, s -> { }, () -> { }));
        assertThrows(NullPointerException.class, () -> new PainelLateral(tema, null, s -> { }, () -> { }));
        assertThrows(NullPointerException.class, () -> new PainelLateral(tema, s -> { }, null, () -> { }));
        assertThrows(NullPointerException.class, () -> new PainelLateral(tema, s -> { }, s -> { }, null));
    }
}
