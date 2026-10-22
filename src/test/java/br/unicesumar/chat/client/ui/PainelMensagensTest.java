package br.unicesumar.chat.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.client.controller.MensagemTela;
import br.unicesumar.chat.client.ui.Tema.Modo;
import java.awt.Point;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Lista de mensagens com rolagem automática só quando o usuário já está no fim (sem tela). */
class PainelMensagensTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 22, 9, 5);

    private final Tema tema = new Tema(Modo.CLARO);
    private final PainelMensagens painel = new PainelMensagens(tema);

    private static MensagemTela msg(String de, String texto) {
        return new MensagemTela(MensagemTela.Tipo.PUBLICA, de, null, T, texto, false);
    }

    private static List<MensagemTela> muitas(int n) {
        List<MensagemTela> l = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            l.add(msg("bia", "mensagem número " + i));
        }
        return l;
    }

    private void arrumar() {
        LayoutDeTeste.forcar(painel, 500, 300);
    }

    private int fimDoConteudo() {
        return painel.getViewport().getView().getHeight();
    }

    private int baseDaJanela() {
        return painel.getViewport().getViewPosition().y + painel.getViewport().getHeight();
    }

    @Test
    void comecaVazio() {
        assertEquals(0, painel.quantidade());
        assertTrue(painel.estaNoFim());
    }

    @Test
    void adicionarCriaUmaLinhaPorMensagemNaOrdem() {
        painel.adicionar(msg("ana", "um"));
        painel.adicionar(msg("bia", "dois"));
        assertEquals(2, painel.quantidade());
        assertEquals("um", painel.linha(0).mensagem().texto());
        assertEquals("dois", painel.linha(1).mensagem().texto());
    }

    @Test
    void substituirTrocaTudoELimparComListaVazia() {
        painel.substituir(muitas(5));
        assertEquals(5, painel.quantidade());
        painel.substituir(muitas(2));
        assertEquals(2, painel.quantidade());
        painel.substituir(List.of());
        assertEquals(0, painel.quantidade());
    }

    @Test
    void conteudoCurtoCabeNaJanelaEEstaNoFim() {
        painel.substituir(muitas(2));
        arrumar();
        assertTrue(painel.estaNoFim());
    }

    @Test
    void depoisDeSubstituirComMuitasMensagensAJanelaFicaNoFim() {
        painel.substituir(muitas(60));
        arrumar();
        assertTrue(fimDoConteudo() > 300, "conteúdo " + fimDoConteudo());
        assertTrue(painel.estaNoFim());
        assertEquals(fimDoConteudo(), baseDaJanela());
    }

    @Test
    void novaMensagemComOUsuarioNoFimContinuaNoFim() {
        painel.substituir(muitas(60));
        arrumar();
        painel.adicionar(msg("bia", "mais uma"));
        arrumar();
        assertTrue(painel.estaNoFim());
        assertEquals(fimDoConteudo(), baseDaJanela());
    }

    @Test
    void novaMensagemComOUsuarioLendoAsAntigasNaoMoveAJanela() {
        painel.substituir(muitas(60));
        arrumar();
        painel.getViewport().setViewPosition(new Point(0, 100));
        assertFalse(painel.estaNoFim());
        painel.adicionar(msg("bia", "chegou agora"));
        arrumar();
        assertEquals(100, painel.getViewport().getViewPosition().y);
        assertFalse(painel.estaNoFim());
    }

    @Test
    void voltarAoFimManualmenteReativaARolagemAutomatica() {
        painel.substituir(muitas(60));
        arrumar();
        painel.getViewport().setViewPosition(new Point(0, 0));
        painel.adicionar(msg("bia", "x"));
        arrumar();
        painel.rolarParaOFim();
        arrumar();
        assertTrue(painel.estaNoFim());
        painel.adicionar(msg("bia", "y"));
        arrumar();
        assertTrue(painel.estaNoFim());
    }

    @Test
    void inserirNoInicioMantemAMesmaMensagemNaPosicaoRelativaDaJanela() {
        painel.substituir(muitas(60));
        arrumar();
        painel.getViewport().setViewPosition(new Point(0, 400));
        int antes = fimDoConteudo();
        painel.inserirNoInicio(muitas(10));
        arrumar();
        int acrescimo = fimDoConteudo() - antes;
        assertTrue(acrescimo > 0);
        assertEquals(400 + acrescimo, painel.getViewport().getViewPosition().y);
        assertEquals(70, painel.quantidade());
    }

    @Test
    void inserirNoInicioPoeAsNovasAntesDasExistentes() {
        painel.adicionar(msg("bia", "recente"));
        painel.inserirNoInicio(List.of(msg("ana", "antiga")));
        assertEquals("antiga", painel.linha(0).mensagem().texto());
        assertEquals("recente", painel.linha(1).mensagem().texto());
    }

    @Test
    void inserirListaVaziaNaoFazNada() {
        painel.adicionar(msg("bia", "x"));
        painel.inserirNoInicio(List.of());
        assertEquals(1, painel.quantidade());
    }

    @Test
    void osBaloesNaoPassamDe70PorCentoDaLarguraDaJanela() {
        painel.substituir(List.of(msg("bia", "palavra ".repeat(80))));
        arrumar();
        int larguraDaJanela = painel.getViewport().getWidth();
        int larguraDoBalao = painel.linha(0).getPreferredSize().width;
        assertTrue(larguraDoBalao <= (int) (larguraDaJanela * 0.7) + 2 * Tema.espaco(1) + 2,
                "balão " + larguraDoBalao + " janela " + larguraDaJanela);
    }

    @Test
    void estreitarAJanelaRearrumaOsBaloesEmMaisAltura() {
        painel.substituir(List.of(msg("bia", "palavra ".repeat(40))));
        LayoutDeTeste.forcar(painel, 800, 300);
        int alturaLarga = painel.linha(0).getPreferredSize().height;
        LayoutDeTeste.forcar(painel, 300, 300);
        assertTrue(painel.linha(0).getPreferredSize().height > alturaLarga);
    }

    @Test
    void mudarOTemaRecoloreAsLinhasExistentes() {
        painel.adicionar(msg("bia", "x"));
        tema.alternar();
        assertEquals(Tema.paleta(Modo.ESCURO).balaoOutro(), painel.linha(0).corDoBalao());
    }

    @Test
    void linhaForaDoIntervaloLancaExcecao() {
        assertThrows(IndexOutOfBoundsException.class, () -> painel.linha(0));
    }

    @Test
    void argumentosNulosLancamExcecao() {
        assertThrows(NullPointerException.class, () -> new PainelMensagens(null));
        assertThrows(NullPointerException.class, () -> painel.adicionar(null));
        assertThrows(NullPointerException.class, () -> painel.substituir(null));
        assertThrows(NullPointerException.class, () -> painel.inserirNoInicio(null));
    }

    @Test
    void rolagemPorRoletaTemPassoConfortavel() {
        assertTrue(painel.getVerticalScrollBar().getUnitIncrement() >= 16);
    }
}
