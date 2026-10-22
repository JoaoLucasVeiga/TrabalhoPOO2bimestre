package br.unicesumar.chat.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.client.controller.MensagemTela;
import br.unicesumar.chat.client.ui.Tema.Modo;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Conteúdo do diálogo de busca: abas, resultados em tabela e estados dos botões (sem tela). */
class PainelBuscaTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 1, 9, 5);

    private final Tema tema = new Tema(Modo.CLARO);
    private final List<String> pedidos = new ArrayList<>();
    private final PainelBusca painel = new PainelBusca(tema, new PainelBusca.Acoes() {
        @Override
        public void porUsuario(String nome) {
            pedidos.add("usuario:" + nome);
        }

        @Override
        public void porTexto(String trecho) {
            pedidos.add("texto:" + trecho);
        }

        @Override
        public void porPeriodo(String inicio, String fim) {
            pedidos.add("periodo:" + inicio + ".." + fim);
        }
    });

    private static MensagemTela msg(String de, String texto) {
        return new MensagemTela(MensagemTela.Tipo.PUBLICA, de, null, T, texto, false);
    }

    @Test
    void temTresAbasNaOrdemUsuarioTextoPeriodo() {
        assertEquals(3, painel.abas().getTabCount());
        assertEquals("Usuário", painel.abas().getTitleAt(0));
        assertEquals("Texto", painel.abas().getTitleAt(1));
        assertEquals("Período", painel.abas().getTitleAt(2));
    }

    @Test
    void buscarNaAbaDeUsuarioChamaPorUsuario() {
        painel.abas().setSelectedIndex(0);
        painel.campoUsuario().setText("  bia ");
        painel.buscarNaAbaAtual();
        assertEquals(List.of("usuario:  bia "), pedidos);
    }

    @Test
    void buscarNaAbaDeTextoChamaPorTexto() {
        painel.abas().setSelectedIndex(1);
        painel.campoTexto().setText("java");
        painel.buscarNaAbaAtual();
        assertEquals(List.of("texto:java"), pedidos);
    }

    @Test
    void buscarNaAbaDePeriodoChamaPorPeriodoComAsDuasDatas() {
        painel.abas().setSelectedIndex(2);
        painel.campoInicio().setText("2026-10-01");
        painel.campoFim().setText("2026-10-31");
        painel.buscarNaAbaAtual();
        assertEquals(List.of("periodo:2026-10-01..2026-10-31"), pedidos);
    }

    @Test
    void enterNosCamposEOBotaoDeCadaAbaFazemABusca() {
        painel.campoUsuario().setText("bia");
        painel.campoUsuario().postActionEvent();
        painel.mostrarResultado(List.of());
        painel.abas().setSelectedIndex(1);
        painel.campoTexto().setText("x");
        painel.botaoDaAba(1).doClick();
        assertEquals(List.of("usuario:bia", "texto:x"), pedidos);
    }

    @Test
    void duranteABuscaOsBotoesFicamCarregandoEAStatusDizBuscando() {
        painel.campoUsuario().setText("bia");
        painel.buscarNaAbaAtual();
        for (int i = 0; i < 3; i++) {
            assertFalse(painel.botaoDaAba(i).isEnabled());
            assertEquals("Carregando...", painel.botaoDaAba(i).getText());
        }
        assertEquals("Buscando...", painel.status().getText());
    }

    @Test
    void naoDisparaUmaSegundaBuscaEnquantoUmaEstaEmAndamento() {
        painel.campoUsuario().setText("bia");
        painel.buscarNaAbaAtual();
        painel.buscarNaAbaAtual();
        painel.campoUsuario().postActionEvent();
        assertEquals(1, pedidos.size());
    }

    @Test
    void resultadoPreencheATabelaEVoltaOsBotoes() {
        painel.campoUsuario().setText("bia");
        painel.buscarNaAbaAtual();
        painel.mostrarResultado(List.of(msg("bia", "oi"), msg("ana", "tudo bem?")));
        assertEquals(2, painel.linhasDaTabela());
        assertEquals("01/10 09:05", painel.valorNaTabela(0, 0));
        assertEquals("bia", painel.valorNaTabela(0, 1));
        assertEquals("oi", painel.valorNaTabela(0, 2));
        assertEquals("2 resultados", painel.status().getText());
        assertTrue(painel.botaoDaAba(0).isEnabled());
        assertEquals("Buscar", painel.botaoDaAba(0).getText());
    }

    @Test
    void umResultadoUsaOSingular() {
        painel.mostrarResultado(List.of(msg("bia", "oi")));
        assertEquals("1 resultado", painel.status().getText());
    }

    @Test
    void semResultadosEsvaziaATabelaEDizNenhumResultado() {
        painel.mostrarResultado(List.of(msg("bia", "oi")));
        painel.mostrarResultado(List.of());
        assertEquals(0, painel.linhasDaTabela());
        assertEquals("Nenhum resultado.", painel.status().getText());
    }

    @Test
    void erroMostraAMensagemNaCorDeErroEVoltaOsBotoes() {
        painel.campoTexto().setText("x");
        painel.abas().setSelectedIndex(1);
        painel.buscarNaAbaAtual();
        painel.mostrarErro("Use datas no formato AAAA-MM-DD.");
        assertEquals("Use datas no formato AAAA-MM-DD.", painel.status().getText());
        assertEquals(tema.paleta().erro(), painel.status().getForeground());
        assertTrue(painel.botaoDaAba(1).isEnabled());
    }

    @Test
    void statusVoltaParaACorNormalDepoisDeUmResultado() {
        painel.mostrarErro("falhou");
        painel.mostrarResultado(List.of(msg("a", "b")));
        assertEquals(tema.paleta().textoSecundario(), painel.status().getForeground());
    }

    @Test
    void aTabelaNaoPodeSerEditada() {
        painel.mostrarResultado(List.of(msg("bia", "oi")));
        assertFalse(painel.tabela().isCellEditable(0, 2));
        assertEquals(3, painel.tabela().getColumnCount());
        assertEquals("Hora", painel.tabela().getColumnName(0));
        assertEquals("Usuário", painel.tabela().getColumnName(1));
        assertEquals("Mensagem", painel.tabela().getColumnName(2));
    }

    @Test
    void salaApareceNoTitulo() {
        painel.definirSala("java");
        assertEquals("Buscar em #java", painel.titulo().getText());
    }

    @Test
    void fontesTemPeloMenos13EOsCamposTemDica() {
        assertTrue(painel.campoUsuario().getFont().getSize() >= 13);
        assertTrue(painel.tabela().getFont().getSize() >= 13);
        assertNotNull(painel.campoUsuario().getToolTipText());
        assertNotNull(painel.campoTexto().getToolTipText());
        assertNotNull(painel.campoInicio().getToolTipText());
        assertTrue(painel.campoInicio().getToolTipText().contains("AAAA-MM-DD"));
    }

    @Test
    void limparRestauraEstadoInicialDaTelaDeResultados() {
        painel.mostrarResultado(List.of(msg("bia", "oi")));
        painel.limparResultados();
        assertEquals(0, painel.linhasDaTabela());
        assertEquals("", painel.status().getText());
    }

    @Test
    void argumentosNulosLancamExcecao() {
        assertThrows(NullPointerException.class, () -> new PainelBusca(null, new PainelBusca.Acoes() {
            @Override
            public void porUsuario(String n) {
            }

            @Override
            public void porTexto(String t) {
            }

            @Override
            public void porPeriodo(String i, String f) {
            }
        }));
        assertThrows(NullPointerException.class, () -> new PainelBusca(tema, null));
        assertThrows(NullPointerException.class, () -> painel.mostrarResultado(null));
        assertThrows(NullPointerException.class, () -> painel.mostrarErro(null));
    }
}
