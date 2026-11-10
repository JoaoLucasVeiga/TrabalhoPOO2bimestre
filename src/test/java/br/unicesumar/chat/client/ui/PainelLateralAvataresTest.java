package br.unicesumar.chat.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.client.ui.Tema.Modo;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import javax.swing.JList;
import org.junit.jupiter.api.Test;

/** Coluna da esquerda (salas, conversas e cartão do usuário) e coluna da direita (membros) com avatares. */
class PainelLateralAvataresTest {

    private final Tema tema = new Tema(Modo.ESCURO);
    private final PainelLateral painel = new PainelLateral(tema, s -> { }, n -> { }, () -> { });

    private static List<JLabel> rotulos(Component c) {
        List<JLabel> achados = new ArrayList<>();
        if (c instanceof JLabel l) {
            achados.add(l);
        }
        if (c instanceof Container container) {
            for (Component filho : container.getComponents()) {
                achados.addAll(rotulos(filho));
            }
        }
        return achados;
    }

    /** O que a célula mostra naquele momento (o renderizador reaproveita o componente entre células). */
    private record Retrato(List<String> textos, boolean temAvatar, java.awt.Color fundo) {
    }

    private static Retrato celula(JList<String> lista, int indice) {
        Component c = lista.getCellRenderer().getListCellRendererComponent(lista,
                lista.getModel().getElementAt(indice), indice, false, false);
        List<JLabel> rs = rotulos(c);
        return new Retrato(rs.stream().map(JLabel::getText).filter(t -> t != null && !t.isBlank()).toList(),
                rs.stream().anyMatch(l -> l.getIcon() instanceof AvatarIcon), c.getBackground());
    }

    // ---------- colunas ----------

    @Test
    void osMembrosFicamEmUmaColunaPropriaForaDoPainelDaEsquerda() {
        JList<String> membros = painel.listaDeUsuarios();
        assertTrue(javax.swing.SwingUtilities.isDescendingFrom(membros, painel.colunaDosMembros()));
        assertFalse(javax.swing.SwingUtilities.isDescendingFrom(membros, painel));
        assertNull(painel.colunaDosMembros().getParent());
    }

    @Test
    void salasEConversasFicamNoPainelDaEsquerda() {
        assertTrue(javax.swing.SwingUtilities.isDescendingFrom(painel.listaDeSalas(), painel));
        assertTrue(javax.swing.SwingUtilities.isDescendingFrom(painel.listaDeConversas(), painel));
    }

    // ---------- cartão do usuário ----------

    @Test
    void cartaoDoUsuarioMostraONomeEOStatus() {
        assertEquals("Desconectado", painel.statusDoCartao());
        painel.definirUsuarioAtual("ana");
        painel.definirConectado(true);
        assertEquals("ana", painel.nomeDoCartao());
        assertEquals("Conectado", painel.statusDoCartao());
        painel.definirConectado(false);
        assertEquals("Desconectado", painel.statusDoCartao());
    }

    @Test
    void cartaoDoUsuarioTemAvatar() {
        painel.definirUsuarioAtual("ana");
        assertTrue(rotulos(painel).stream().anyMatch(l -> l.getIcon() instanceof AvatarIcon));
    }

    // ---------- avatares nas listas ----------

    @Test
    void cadaMembroApareceComAvatarENomeComVoceNoProprio() {
        painel.atualizarUsuarios(List.of("ana", "bia"), "ana");
        Retrato proprio = celula(painel.listaDeUsuarios(), 0);
        assertTrue(proprio.temAvatar());
        assertTrue(proprio.textos().contains("ana (você)"));
        Retrato outro = celula(painel.listaDeUsuarios(), 1);
        assertTrue(outro.textos().contains("bia"));
    }

    @Test
    void cadaConversaApareceComAvatarENomeESoMostraOEmblemaComNaoLidas() {
        painel.atualizarConversas(List.of("caio", "bia"));
        painel.atualizarNaoLidas(Map.of("bia", 2));
        Retrato semNaoLidas = celula(painel.listaDeConversas(), 0);
        assertTrue(semNaoLidas.temAvatar());
        assertEquals(List.of("caio"), semNaoLidas.textos());
        Retrato comNaoLidas = celula(painel.listaDeConversas(), 1);
        assertEquals(List.of("bia", "2"), comNaoLidas.textos());
    }

    @Test
    void salaAtualFicaDestacadaComFundoEMarcadorDeConversaNaoDestacaASala() {
        painel.atualizarSalas(List.of("geral", "java"), "geral");
        java.awt.Color fundoDaAtual = celula(painel.listaDeSalas(), 0).fundo();
        java.awt.Color fundoDaOutra = celula(painel.listaDeSalas(), 1).fundo();
        assertNotEquals(fundoDaAtual, fundoDaOutra);
        painel.selecionarConversa("bia");
        assertEquals(fundoDaOutra, celula(painel.listaDeSalas(), 0).fundo());
    }
}
