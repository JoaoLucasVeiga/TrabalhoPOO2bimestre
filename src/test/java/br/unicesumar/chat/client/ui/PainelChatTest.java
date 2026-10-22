package br.unicesumar.chat.client.ui;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.client.controller.ChatController;
import br.unicesumar.chat.client.controller.EstadoConexao;
import br.unicesumar.chat.client.controller.MensagemTela;
import br.unicesumar.chat.client.net.ConexaoFalsa;
import br.unicesumar.chat.client.ui.Tema.Modo;
import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.protocol.Mensagens;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Janela principal sem a moldura: o PainelChat com o controlador de verdade e uma ConexaoFalsa (sem tela). */
class PainelChatTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 22, 9, 5);

    @TempDir
    Path pasta;

    private final Tema tema = new Tema(Modo.CLARO);
    private final ConexaoFalsa conexao = new ConexaoFalsa();
    private PainelChat painel;
    private ChatController controller;
    private VerificadorDeEdt verificador;

    @BeforeEach
    void preparar() throws Exception {
        verificador = VerificadorDeEdt.instalar();
        SwingUtilities.invokeAndWait(() -> painel = new PainelChat(tema));
        controller = new ChatController(painel, (h, p) -> conexao);
        painel.definirControlador(controller);
    }

    @AfterEach
    void limpar() {
        controller.sair();
        verificador.remover();
    }

    private void edt() throws Exception {
        SwingUtilities.invokeAndWait(() -> { });
    }

    /** Espera (com yield, sem sleep) até a condição valer, olhando sempre pela EDT. */
    private void esperar(BooleanSupplier condicao) throws Exception {
        long fim = System.nanoTime() + 5_000_000_000L;
        boolean[] ok = {false};
        while (!ok[0] && System.nanoTime() < fim) {
            SwingUtilities.invokeAndWait(() -> ok[0] = condicao.getAsBoolean());
            if (!ok[0]) {
                Thread.yield();
            }
        }
        assertTrue(ok[0], "a condição não foi atendida a tempo");
    }

    private void entrar(Evento... depoisDoOk) throws Exception {
        conexao.receberEvento(new Evento.Sistema("Bem-vindo"))
                .receberEvento(new Evento.Historico(List.of(new Evento.Msg("geral", "bia", T, "oi, ana"))))
                .receberEvento(new Evento.Ok(Mensagens.entrouComo("ana", "geral")));
        for (Evento e : depoisDoOk) {
            conexao.receberEvento(e);
        }
        assertTrue(controller.conectar("localhost", "5000", "ana", "senha123", false).sucesso());
        edt();
    }

    // ---------- estado inicial e conexão ----------

    @Test
    void comecaDesconectadoComEntradaDesligada() throws Exception {
        edt();
        assertEquals(EstadoConexao.DESCONECTADO, painel.indicador().estado());
        assertFalse(painel.entrada().campo().isEnabled());
    }

    @Test
    void depoisDoLoginMostraConectadoSalaGeralEOHistorico() throws Exception {
        entrar();
        assertEquals(EstadoConexao.CONECTADO, painel.indicador().estado());
        assertTrue(painel.entrada().campo().isEnabled());
        assertEquals("# geral", painel.titulo().getText());
        assertEquals(1, painel.mensagens().quantidade());
        assertEquals("oi, ana", painel.mensagens().linha(0).mensagem().texto());
    }

    @Test
    void listasDeUsuariosESalasChegamDoServidorEAparecemNoPainelLateralENoTopo() throws Exception {
        entrar(new Evento.Usuarios("geral", List.of("bia", "ana", "caio")),
                new Evento.Salas(List.of("geral", "java")));
        esperar(() -> painel.lateral().salas().size() == 2 && painel.lateral().usuarios().size() == 3);
        assertEquals(List.of("ana", "bia", "caio"), painel.lateral().usuarios());
        assertEquals(List.of("geral", "java"), painel.lateral().salas());
        assertEquals("3 online: ana, bia, caio", painel.resumoDeUsuarios().getText());
        assertEquals("geral", painel.lateral().salaAtual());
    }

    @Test
    void resumoDeMuitosUsuariosEncurtaComMais() throws Exception {
        entrar(new Evento.Usuarios("geral", List.of("ana", "b1", "b2", "b3", "b4", "b5", "b6", "b7")));
        esperar(() -> painel.lateral().usuarios().size() == 8);
        assertEquals("8 online: ana, b1, b2, b3, b4 e mais 3", painel.resumoDeUsuarios().getText());
    }

    @Test
    void resumoComUmUsuarioESemNinguem() throws Exception {
        entrar(new Evento.Usuarios("geral", List.of("ana")));
        esperar(() -> painel.lateral().usuarios().size() == 1);
        assertEquals("1 online: ana", painel.resumoDeUsuarios().getText());
    }

    // ---------- mensagens ----------

    @Test
    void mensagemRecebidaApareceNaConversa() throws Exception {
        entrar(new Evento.Msg("geral", "bia", T.plusMinutes(1), "tudo bem?"));
        esperar(() -> painel.mensagens().quantidade() == 2);
        assertEquals("tudo bem?", painel.mensagens().linha(1).mensagem().texto());
    }

    @Test
    void sairDaConexaoDesligaAEntradaEAvisaASessaoPerdida() throws Exception {
        AtomicReference<String> motivo = new AtomicReference<>();
        painel.aoSessaoPerdida(motivo::set);
        entrar();
        conexao.encerrarPeloServidor();
        esperar(() -> motivo.get() != null);
        assertEquals(EstadoConexao.DESCONECTADO, painel.indicador().estado());
        assertFalse(painel.entrada().campo().isEnabled());
        assertTrue(motivo.get().contains("encerrada"));
        assertTrue(painel.faixaDeAviso().isVisible());
    }

    @Test
    void avisoNovoDeOutroUsuarioChamaOCallbackDeNaoLidasMasMinhasEAvisosNao() throws Exception {
        AtomicInteger naoLidas = new AtomicInteger();
        painel.aoMensagemDeOutro(naoLidas::incrementAndGet);
        entrar(new Evento.Msg("geral", "bia", T, "uma"), new Evento.Msg("geral", "ana", T, "minha"),
                new Evento.Sistema("bia saiu da sala geral."), new Evento.Privada("caio", "ana", T, "psiu"));
        esperar(() -> painel.mensagens().quantidade() == 5);
        assertEquals(2, naoLidas.get()); // a de bia e a privada de caio
    }

    @Test
    void historicoInicialNaoContaComoNaoLido() throws Exception {
        AtomicInteger naoLidas = new AtomicInteger();
        painel.aoMensagemDeOutro(naoLidas::incrementAndGet);
        entrar();
        assertEquals(0, naoLidas.get());
    }

    // ---------- faixa de aviso ----------

    @Test
    void erroApareceNaFaixaNaCorDeErroEAvisoNaCorSecundaria() throws Exception {
        entrar(new Evento.Erro("A mensagem está vazia. Digite um texto para enviar."));
        esperar(() -> painel.faixaDeAviso().isVisible());
        assertEquals("A mensagem está vazia. Digite um texto para enviar.", painel.faixaDeAviso().getText());
        assertEquals(tema.paleta().erro(), painel.faixaDeAviso().getForeground());
        painel.mostrarAviso("Histórico exportado.");
        edt();
        assertEquals("Histórico exportado.", painel.faixaDeAviso().getText());
        assertEquals(tema.paleta().textoSecundario(), painel.faixaDeAviso().getForeground());
    }

    @Test
    void faixaComecaEscondida() throws Exception {
        edt();
        assertFalse(painel.faixaDeAviso().isVisible());
    }

    // ---------- ações ----------

    @Test
    void enviarPelaCaixaDeTextoMandaOTextoAoServidor() throws Exception {
        entrar();
        painel.entrada().campo().setText("olá, pessoal");
        painel.entrada().acionarEnvio();
        esperar(() -> conexao.enviadas().contains("olá, pessoal"));
    }

    @Test
    void botaoHistoricoPedeAsAnterioresFicaCarregandoEVoltaQuandoChegam() throws Exception {
        entrar();
        painel.botaoHistorico().doClick();
        assertEquals("Carregando...", painel.botaoHistorico().getText());
        esperar(() -> conexao.enviadas().contains("/history 100"));
        conexao.receberEvento(new Evento.Historico(List.of(new Evento.Msg("geral", "bia", T.minusHours(1), "antiga"),
                new Evento.Msg("geral", "bia", T, "oi, ana"))));
        esperar(() -> painel.mensagens().quantidade() == 2);
        assertEquals("antiga", painel.mensagens().linha(0).mensagem().texto());
        assertEquals("Histórico", painel.botaoHistorico().getText());
        assertTrue(painel.botaoHistorico().isEnabled());
    }

    @Test
    void historicoSemNadaMaisAntigoVoltaOBotaoEMostraAviso() throws Exception {
        entrar();
        painel.botaoHistorico().doClick();
        esperar(() -> conexao.enviadas().contains("/history 100"));
        conexao.receberEvento(new Evento.Historico(List.of(new Evento.Msg("geral", "bia", T, "oi, ana"))));
        esperar(() -> painel.botaoHistorico().isEnabled());
        assertEquals("Não há mensagens mais antigas.", painel.faixaDeAviso().getText());
    }

    @Test
    void cliqueEmOutraSalaEntraNelaEOHistoricoDaNovaSalaSubstituiAConversa() throws Exception {
        entrar(new Evento.Salas(List.of("geral", "java")));
        esperar(() -> painel.lateral().salas().size() == 2);
        painel.lateral().acionarSala(1);
        esperar(() -> conexao.enviadas().contains("/join java"));
        conexao.receberEvento(new Evento.Historico(List.of(new Evento.Msg("java", "caio", T, "bem-vinda ao java"))))
                .receberEvento(new Evento.Ok(Mensagens.entrouNaSala("java")));
        esperar(() -> "# java".equals(painel.titulo().getText()));
        assertEquals(1, painel.mensagens().quantidade());
        assertEquals("bem-vinda ao java", painel.mensagens().linha(0).mensagem().texto());
    }

    @Test
    void novaSalaPedeONomeEEntraNela() throws Exception {
        painel.definirSolicitadorDeSala(() -> "  Estudos ");
        entrar();
        painel.lateral().botaoNovaSala().doClick();
        esperar(() -> conexao.enviadas().contains("/join Estudos"));
    }

    @Test
    void novaSalaCanceladaOuVaziaNaoEnviaNada() throws Exception {
        entrar();
        int antes = conexao.enviadas().size();
        painel.definirSolicitadorDeSala(() -> null);
        painel.lateral().botaoNovaSala().doClick();
        painel.definirSolicitadorDeSala(() -> "   ");
        painel.lateral().botaoNovaSala().doClick();
        edt();
        assertEquals(antes, conexao.enviadas().size());
    }

    @Test
    void duploCliqueEmUsuarioPreencheOCampoComMsgEFocaNoFim() throws Exception {
        entrar(new Evento.Usuarios("geral", List.of("ana", "bia")));
        esperar(() -> painel.lateral().usuarios().size() == 2);
        painel.lateral().acionarUsuario(1);
        assertEquals("/msg bia ", painel.entrada().campo().getText());
        assertEquals("/msg bia ".length(), painel.entrada().campo().getCaretPosition());
    }

    @Test
    void exportarGravaNoArquivoEscolhidoEAvisa() throws Exception {
        Path destino = pasta.resolve("saida.txt");
        painel.definirSeletorDeArquivo(sugestao -> Optional.of(destino));
        entrar();
        painel.botaoExportar().doClick();
        esperar(() -> Files.exists(destino) && painel.faixaDeAviso().isVisible());
        assertTrue(Files.readString(destino).contains("[09:05] bia: oi, ana"));
        assertTrue(painel.faixaDeAviso().getText().contains("saida.txt"));
    }

    @Test
    void exportarComOSeletorCanceladoNaoFazNada() throws Exception {
        painel.definirSeletorDeArquivo(sugestao -> Optional.empty());
        entrar();
        painel.botaoExportar().doClick();
        edt();
        assertFalse(painel.faixaDeAviso().isVisible());
    }

    @Test
    void exportarSemMensagensMostraErroQueDizOQueFazer() throws Exception {
        painel.definirSeletorDeArquivo(sugestao -> Optional.of(pasta.resolve("vazio.txt")));
        conexao.receberEvento(new Evento.Sistema("Bem-vindo"))
                .receberEvento(new Evento.Ok(Mensagens.entrouComo("ana", "geral")));
        controller.conectar("localhost", "5000", "ana", "senha123", false);
        edt();
        painel.botaoExportar().doClick();
        esperar(() -> painel.faixaDeAviso().isVisible());
        assertTrue(painel.faixaDeAviso().getText().contains("Não há mensagens"));
        assertFalse(Files.exists(pasta.resolve("vazio.txt")));
    }

    @Test
    void sugestaoDeNomeDoArquivoTemASalaEAExtensaoTxt() throws Exception {
        AtomicReference<String> sugerido = new AtomicReference<>();
        painel.definirSeletorDeArquivo(s -> {
            sugerido.set(s);
            return Optional.empty();
        });
        entrar();
        painel.botaoExportar().doClick();
        edt();
        assertEquals("historico-geral.txt", sugerido.get());
    }

    @Test
    void botaoSairChamaOCallbackEBotaoTemaAlternaOModo() throws Exception {
        AtomicInteger saidas = new AtomicInteger();
        painel.aoSair(saidas::incrementAndGet);
        painel.botaoSair().doClick();
        assertEquals(1, saidas.get());
        assertEquals(Modo.CLARO, tema.modo());
        painel.botaoTema().doClick();
        assertEquals(Modo.ESCURO, tema.modo());
        assertTrue(painel.botaoTema().getText().toLowerCase().contains("claro"));
        painel.botaoTema().doClick();
        assertEquals(Modo.CLARO, tema.modo());
        assertTrue(painel.botaoTema().getText().toLowerCase().contains("escuro"));
    }

    @Test
    void acoesSemControladorMostramErroEmVezDeFalhar() throws Exception {
        SwingUtilities.invokeAndWait(() -> painel = new PainelChat(tema));
        assertFalse(painel.botaoHistorico().isEnabled()); // desconectado: os botões ficam desligados
        SwingUtilities.invokeAndWait(painel::abrirBusca); // mas o atalho Ctrl+F continua existindo
        assertTrue(painel.faixaDeAviso().isVisible());
        assertTrue(painel.faixaDeAviso().getText().contains("não está conectado"));
    }

    // ---------- atalhos, foco e acessibilidade ----------

    @Test
    void atalhosCtrlFCtrlQEstaoRegistradosParaAJanelaToda() throws Exception {
        edt();
        var mapa = painel.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        assertNotNull(mapa.get(javax.swing.KeyStroke.getKeyStroke("control F")));
        assertNotNull(mapa.get(javax.swing.KeyStroke.getKeyStroke("control Q")));
    }

    @Test
    void ctrlQChamaOMesmoCaminhoDoBotaoSair() throws Exception {
        AtomicInteger saidas = new AtomicInteger();
        painel.aoSair(saidas::incrementAndGet);
        var mapa = painel.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        Object chave = mapa.get(javax.swing.KeyStroke.getKeyStroke("control Q"));
        painel.getActionMap().get(chave).actionPerformed(null);
        assertEquals(1, saidas.get());
    }

    @Test
    void ordemDeTabEhLogicaEComecaNaCaixaDeMensagem() throws Exception {
        edt();
        List<java.awt.Component> ordem = painel.ordemDeFoco();
        assertSame(painel.entrada().campo(), painel.componenteInicial());
        assertTrue(ordem.indexOf(painel.botaoHistorico()) < ordem.indexOf(painel.botaoBuscar()));
        assertTrue(ordem.indexOf(painel.botaoSair()) < ordem.indexOf(painel.lateral().listaDeSalas()));
        assertTrue(ordem.indexOf(painel.lateral().listaDeUsuarios()) < ordem.indexOf(painel.entrada().campo()));
        assertTrue(ordem.indexOf(painel.entrada().campo()) < ordem.indexOf(painel.entrada().botaoEnviar()));
    }

    @Test
    void todosOsBotoesTemDicaENomeAcessivel() throws Exception {
        edt();
        for (var b : List.of(painel.botaoHistorico(), painel.botaoBuscar(), painel.botaoExportar(),
                painel.botaoSair(), painel.botaoTema())) {
            assertNotNull(b.getToolTipText(), b.getText());
            assertNotNull(b.getAccessibleContext().getAccessibleName(), b.getText());
            assertTrue(b.getFont().getSize() >= 13);
        }
        assertTrue(painel.botaoBuscar().getToolTipText().contains("Ctrl+F"));
        assertTrue(painel.botaoSair().getToolTipText().contains("Ctrl+Q"));
    }

    @Test
    void barraDeFerramentasOcupaALarguraTodaDaJanela() throws Exception {
        edt();
        LayoutDeTeste.forcar(painel, 1000, 680);
        assertEquals(1000, painel.barra().getWidth());
        assertEquals(1000, painel.topo().getWidth());
    }

    @Test
    void osBotoesDaBarraTemAparenciaDeBotao() throws Exception {
        edt();
        for (var b : List.of(painel.botaoHistorico(), painel.botaoBuscar(), painel.botaoExportar(), painel.botaoSair())) {
            assertEquals("roundRect", b.getClientProperty("JButton.buttonType"), b.getText());
        }
    }

    // ---------- nada fora da EDT ----------

    @Test
    void ochecadorDeEdtRealmenteDetectaAlteracaoForaDaEdt() throws Exception {
        Thread t = new Thread(() -> new javax.swing.JLabel().setText("fora da edt"));
        t.start();
        t.join();
        assertFalse(verificador.violacoes().isEmpty());
    }

    @Test
    void chamarAViewDeOutraThreadNuncaMexeEmComponenteForaDaEdt() throws Exception {
        entrar();
        Thread rede = new Thread(() -> {
            painel.definirUsuario("ana");
            painel.mostrarConexao(EstadoConexao.CONECTANDO);
            painel.mostrarConexao(EstadoConexao.CONECTADO);
            painel.adicionar(MensagemTela.sistema("aviso", T));
            painel.adicionar(new MensagemTela(MensagemTela.Tipo.PUBLICA, "bia", null, T, "oi", false));
            painel.substituirHistorico(List.of(MensagemTela.sistema("x", T)), "geral");
            painel.inserirAnteriores(List.of(MensagemTela.sistema("y", T)));
            painel.atualizarUsuarios("geral", List.of("ana", "bia"));
            painel.atualizarSalas(List.of("geral", "java"), "geral");
            painel.mostrarResultadoBusca(List.of());
            painel.mostrarErroDeBusca("erro");
            painel.mostrarErro("erro");
            painel.mostrarAviso("aviso");
            painel.sessaoEncerrada("caiu");
        }, "thread-de-rede");
        rede.start();
        rede.join();
        edt();
        assertEquals(List.of(), verificador.violacoes());
    }

    @Test
    void fluxoCompletoComEventosDaThreadDeLeituraNaoViolaAEdt() throws Exception {
        entrar(new Evento.Usuarios("geral", List.of("ana", "bia")), new Evento.Salas(List.of("geral")),
                new Evento.Msg("geral", "bia", T, "uma"), new Evento.Privada("bia", "ana", T, "psiu"),
                new Evento.Sistema("bia saiu da sala geral."), new Evento.Erro("algo"));
        esperar(() -> painel.mensagens().quantidade() >= 4 && painel.faixaDeAviso().isVisible());
        SwingUtilities.invokeAndWait(() -> {
            painel.entrada().campo().setText("resposta");
            painel.entrada().acionarEnvio();
        });
        esperar(() -> conexao.enviadas().contains("resposta"));
        assertEquals(List.of(), verificador.violacoes());
    }
}
