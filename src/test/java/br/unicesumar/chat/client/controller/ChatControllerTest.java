package br.unicesumar.chat.client.controller;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.client.controller.MensagemTela.Tipo;
import br.unicesumar.chat.client.net.ConexaoFalsa;
import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.protocol.Mensagens;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Controlador testado com ConexaoFalsa e ChatViewFalsa: sem rede e sem Swing. */
class ChatControllerTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime T1 = LocalDateTime.of(2026, 10, 22, 9, 1);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 10, 22, 9, 2);
    private static final LocalDateTime T3 = LocalDateTime.of(2026, 10, 22, 9, 3);
    private static final LocalDateTime T4 = LocalDateTime.of(2026, 10, 22, 9, 4);

    @TempDir
    Path pasta;

    private final ChatViewFalsa view = new ChatViewFalsa();
    private final AtomicInteger conexoesCriadas = new AtomicInteger();
    private ConexaoFalsa conexao = new ConexaoFalsa();
    private ChatController controller;

    private static Evento.Msg msg(String de, LocalDateTime hora, String texto) {
        return new Evento.Msg("geral", de, hora, texto);
    }

    private static MensagemTela tela(String de, LocalDateTime hora, String texto, String eu) {
        return MensagemTela.publica(msg(de, hora, texto), eu);
    }

    private ChatController criar(boolean leitor) {
        controller = new ChatController(view, (host, porta) -> {
            conexoesCriadas.incrementAndGet();
            return conexao;
        }, RELOGIO, leitor);
        return controller;
    }

    /** Roteiro do servidor para um login bem-sucedido, com eventos opcionais antes do OK. */
    private void roteiroDeLogin(String nome, Evento... antesDoOk) {
        conexao.receberEvento(new Evento.Sistema("Bem-vindo ao chat!"));
        for (Evento e : antesDoOk) {
            conexao.receberEvento(e);
        }
        conexao.receberEvento(new Evento.Ok(Mensagens.entrouComo(nome, "geral")));
    }

    private ChatController logado(Evento... historico) {
        roteiroDeLogin("ana", historico);
        criar(false);
        ResultadoConexao r = controller.conectar("localhost", "5000", "ana", "senha123", false);
        assertTrue(r.sucesso(), r.erro());
        return controller;
    }

    // ---------- conectar ----------

    @Test
    void loginComSucessoDevolveOkEConfiguraUsuarioSalaEEstado() {
        roteiroDeLogin("Ana");
        criar(false);
        ResultadoConexao r = controller.conectar("localhost", "5000", "ana", "senha123", false);
        assertTrue(r.sucesso());
        assertEquals("Ana", r.usuario()); // nome com as maiúsculas do cadastro, vindo do servidor
        assertEquals("Ana", controller.usuario());
        assertEquals("geral", controller.salaAtual());
        assertTrue(controller.conectado());
        assertEquals(List.of("Ana"), view.usuarios);
        assertEquals(EstadoConexao.CONECTADO, view.estados.get(view.estados.size() - 1));
    }

    @Test
    void loginEnviaOComandoLoginEPedeAsListasDeUsuariosESalas() {
        logado();
        assertEquals(List.of("/login ana senha123", "/users", "/rooms"), conexao.enviadas());
    }

    @Test
    void criarContaEnviaORegister() {
        roteiroDeLogin("ana");
        criar(false);
        controller.conectar("localhost", "5000", "ana", "senha123", true);
        assertEquals("/register ana senha123", conexao.enviadas().get(0));
    }

    @Test
    void senhaComEspacosVaiInteiraNoComando() {
        roteiroDeLogin("ana");
        criar(false);
        controller.conectar("localhost", "5000", "ana", "minha senha forte", false);
        assertEquals("/login ana minha senha forte", conexao.enviadas().get(0));
    }

    @Test
    void historicoRecebidoNoLoginVaiParaAViewDepoisDoLoginComAsMinhasMarcadas() {
        logado(new Evento.Historico(List.of(msg("bia", T1, "oi"), msg("ana", T2, "olá"))));
        assertEquals(1, view.historicos.size());
        assertEquals(List.of(tela("bia", T1, "oi", "ana"), tela("ana", T2, "olá", "ana")), view.historicos.get(0));
        assertTrue(view.historicos.get(0).get(1).minha());
        assertEquals("geral", view.salasDoHistorico.get(0));
    }

    @Test
    void semHistoricoAViewRecebeUmaListaVaziaParaLimparATela() {
        logado();
        assertEquals(List.of(List.of()), view.historicos);
    }

    @Test
    void erroDoServidorNoLoginViraFalhaFechaAConexaoENaoConfiguraAView() {
        conexao.receberEvento(new Evento.Sistema("Bem-vindo")).receberEvento(
                new Evento.Erro("Usuário ou senha incorretos. Confira os dados e tente de novo."));
        criar(false);
        ResultadoConexao r = controller.conectar("localhost", "5000", "ana", "errada", false);
        assertFalse(r.sucesso());
        assertTrue(r.erro().contains("incorretos"));
        assertFalse(conexao.aberta());
        assertFalse(controller.conectado());
        assertTrue(view.usuarios.isEmpty());
    }

    @Test
    void servidorEncerrandoNoMeioDoLoginViraFalhaComOQueFazer() {
        conexao.receberEvento(new Evento.Sistema("Bem-vindo")).encerrarPeloServidor();
        criar(false);
        ResultadoConexao r = controller.conectar("localhost", "5000", "ana", "senha123", false);
        assertFalse(r.sucesso());
        assertTrue(r.erro().contains("encerrou a conexão"));
        assertFalse(conexao.aberta());
    }

    @Test
    void servidorQueNaoResponderAcabaEmFalhaSemTravar() {
        conexao = new ConexaoFalsa() {
            @Override
            public Evento receber() throws IOException {
                throw new SocketTimeoutException("Read timed out");
            }
        };
        criar(false);
        ResultadoConexao r = controller.conectar("localhost", "5000", "ana", "senha123", false);
        assertFalse(r.sucesso());
        assertTrue(r.erro().contains("não respondeu"));
        assertFalse(conexao.aberta());
    }

    @Test
    void oLoginUsaUmTempoDeEsperaEDepoisVoltaAEsperarSemLimite() {
        List<Integer> tempos = new ArrayList<>();
        conexao = new ConexaoFalsa() {
            @Override
            public void definirTempoDeEspera(int milissegundos) {
                tempos.add(milissegundos);
            }
        };
        roteiroDeLogin("ana");
        criar(false);
        controller.conectar("localhost", "5000", "ana", "senha123", false);
        assertEquals(List.of(ChatController.TEMPO_DO_LOGIN_MS, 0), tempos);
    }

    @Test
    void falhaAoAbrirDevolveAMensagemDoAdaptadorSemMudarONome() {
        conexao.falharAoAbrirCom(new IOException(
                "Não foi possível conectar a localhost:5000. Verifique se o servidor está em execução."));
        criar(false);
        ResultadoConexao r = controller.conectar("localhost", "5000", "ana", "senha123", false);
        assertFalse(r.sucesso());
        assertTrue(r.erro().contains("localhost:5000"));
        assertTrue(r.erro().contains("Verifique se o servidor está em execução"));
    }

    @Test
    void camposInvalidosFalhamAntesDeQualquerConexao() {
        criar(false);
        for (String[] c : new String[][] {
                {"", "5000", "ana", "senha123"}, {"  ", "5000", "ana", "senha123"},
                {"localhost", "", "ana", "senha123"}, {"localhost", "abc", "ana", "senha123"},
                {"localhost", "0", "ana", "senha123"}, {"localhost", "65536", "ana", "senha123"},
                {"localhost", "-1", "ana", "senha123"}, {"localhost", "5000", "", "senha123"},
                {"localhost", "5000", "  ", "senha123"}, {"localhost", "5000", "ana", ""},
                {"localhost", "5000", null, "senha123"}, {"localhost", "5000", "ana", null}}) {
            ResultadoConexao r = controller.conectar(c[0], c[1], c[2], c[3], false);
            assertFalse(r.sucesso(), String.join("|", String.valueOf(c[0]), c[1], String.valueOf(c[2])));
            assertFalse(r.erro().isBlank());
        }
        assertEquals(0, conexoesCriadas.get());
    }

    @Test
    void portaEHostComEspacosNasPontasSaoAceitos() {
        roteiroDeLogin("ana");
        criar(false);
        assertTrue(controller.conectar("  localhost ", " 5000 ", " ana ", "senha123", false).sucesso());
        assertEquals("/login ana senha123", conexao.enviadas().get(0));
    }

    @Test
    void conectarDeNovoEnquantoJaEstaConectadoFalha() {
        logado();
        ResultadoConexao r = controller.conectar("localhost", "5000", "ana", "senha123", false);
        assertFalse(r.sucesso());
        assertTrue(r.erro().contains("já está conectado"));
        assertEquals(1, conexoesCriadas.get());
    }

    // ---------- eventos recebidos ----------

    @Test
    void mensagemPublicaDeOutroAparecePorUmaDaViewEMinhaVemMarcada() {
        logado();
        controller.tratar(msg("bia", T1, "oi"));
        controller.tratar(msg("ANA", T2, "eu mesma"));
        assertEquals(List.of(tela("bia", T1, "oi", "ana"), tela("ANA", T2, "eu mesma", "ana")), view.mensagens);
        assertFalse(view.mensagens.get(0).minha());
        assertTrue(view.mensagens.get(1).minha());
    }

    @Test
    void mensagemPrivadaApareceComoPrivadaComDestinatario() {
        logado();
        controller.tratar(new Evento.Privada("bia", "ana", T1, "psiu"));
        MensagemTela m = view.mensagens.get(0);
        assertEquals(Tipo.PRIVADA, m.tipo());
        assertEquals("bia", m.remetente());
        assertEquals("ana", m.destinatario());
        assertFalse(m.minha());
    }

    @Test
    void avisoDoSistemaApareceComoSistemaComOHorarioDoRelogio() {
        logado();
        controller.tratar(new Evento.Sistema("Comandos disponíveis:\n/help"));
        MensagemTela m = view.mensagens.get(0);
        assertEquals(Tipo.SISTEMA, m.tipo());
        assertEquals(LocalDateTime.of(2026, 10, 22, 10, 0), m.horario());
        assertFalse(conexao.enviadas().contains("/users") && conexao.enviadas().size() > 3);
    }

    @Test
    void avisoDeEntradaOuSaidaApareceEPedeAListaDeUsuariosDeNovo() {
        logado();
        int antes = conexao.enviadas().size();
        controller.tratar(new Evento.Sistema(Mensagens.avisoDeEntrada("bia", "geral")));
        assertEquals(1, view.mensagens.size());
        assertEquals("/users", conexao.enviadas().get(antes));
        controller.tratar(new Evento.Sistema(Mensagens.avisoDeSaida("bia", "geral")));
        assertEquals(antes + 2, conexao.enviadas().size());
    }

    @Test
    void listaDeUsuariosESalasVaoParaAView() {
        logado();
        controller.tratar(new Evento.Usuarios("geral", List.of("ana", "bia")));
        controller.tratar(new Evento.Salas(List.of("geral", "java")));
        assertEquals(List.of("ana", "bia"), view.listasDeUsuarios.get(0));
        assertEquals("geral", view.usuariosDaSala.get(0));
        assertEquals(List.of("geral", "java"), view.listasDeSalas.get(0));
        assertEquals("geral", view.salasAtuais.get(0));
    }

    @Test
    void erroSemPedidoPendenteVaiParaOErroDaTela() {
        logado();
        controller.tratar(new Evento.Erro("A mensagem está vazia."));
        assertEquals(List.of("A mensagem está vazia."), view.erros);
        assertTrue(view.errosDeBusca.isEmpty());
    }

    @Test
    void okGenericoComoOdaExportacaoViraAviso() {
        logado();
        controller.tratar(new Evento.Ok("Histórico exportado para o arquivo x.txt."));
        assertEquals(List.of("Histórico exportado para o arquivo x.txt."), view.avisos);
    }

    // ---------- enviar ----------

    @Test
    void enviarMandaOTextoSemEspacosNasPontas() {
        logado();
        controller.enviar("  olá, pessoal  ");
        assertEquals("olá, pessoal", conexao.enviadas().get(conexao.enviadas().size() - 1));
    }

    @Test
    void enviarTextoVazioOuSoComEspacosNaoFazNada() {
        logado();
        int antes = conexao.enviadas().size();
        controller.enviar("");
        controller.enviar("   ");
        controller.enviar(null);
        assertEquals(antes, conexao.enviadas().size());
        assertTrue(view.erros.isEmpty());
    }

    @Test
    void enviarMensagemCom500CaracteresPassaECom501EBarrada() {
        logado();
        int antes = conexao.enviadas().size();
        controller.enviar("x".repeat(500));
        assertEquals(antes + 1, conexao.enviadas().size());
        controller.enviar("x".repeat(501));
        assertEquals(antes + 1, conexao.enviadas().size());
        assertTrue(view.erros.get(0).contains("500"));
    }

    @Test
    void enviarSemEstarConectadoMostraErro() {
        criar(false);
        controller.enviar("oi");
        assertTrue(view.erros.get(0).contains("desconectado"));
    }

    @Test
    void falhaAoEnviarMarcaDesconectadoEMostraErro() {
        logado();
        conexao.falharAoEnviarCom(new IOException("Falha ao enviar. A conexão com o servidor foi perdida."));
        controller.enviar("oi");
        assertFalse(controller.conectado());
        assertEquals(EstadoConexao.DESCONECTADO, view.estados.get(view.estados.size() - 1));
        assertTrue(view.erros.get(0).contains("perdida"));
    }

    @Test
    void comandosDigitadosNaCaixaDeTextoSaoEncaminhados() {
        logado();
        controller.enviar("/msg bia oi");
        controller.enviar("/users");
        List<String> enviadas = conexao.enviadas();
        assertEquals("/msg bia oi", enviadas.get(enviadas.size() - 2));
        assertEquals("/users", enviadas.get(enviadas.size() - 1));
    }

    @Test
    void joinDigitadoNaCaixaDeTextoUsaOCaminhoDeTrocaDeSala() {
        logado();
        controller.enviar("/join java");
        controller.tratar(new Evento.Historico(List.of(new Evento.Msg("java", "bia", T1, "no java"))));
        controller.tratar(new Evento.Ok(Mensagens.entrouNaSala("java")));
        assertEquals("java", controller.salaAtual());
        assertEquals(2, view.historicos.size()); // o do login e o da sala nova
    }

    @Test
    void leaveEHistoryDigitadosViramAsAcoesCertas() {
        logado();
        controller.entrarSala("java");
        controller.tratar(new Evento.Ok(Mensagens.entrouNaSala("java")));
        controller.enviar("/leave");
        assertEquals("/leave", conexao.enviadas().get(conexao.enviadas().size() - 1));
        controller.enviar("/history");
        assertEquals("/history 100", conexao.enviadas().get(conexao.enviadas().size() - 1));
    }

    // ---------- histórico anterior ----------

    @Test
    void carregarAnterioresInsereSoAsMaisAntigasQueAindaNaoEstaoNaTela() {
        logado(new Evento.Historico(List.of(msg("bia", T3, "tres"), msg("ana", T4, "quatro"))));
        controller.carregarHistoricoAnterior();
        assertEquals("/history 100", conexao.enviadas().get(conexao.enviadas().size() - 1));
        controller.tratar(new Evento.Historico(List.of(msg("bia", T1, "um"), msg("ana", T2, "dois"),
                msg("bia", T3, "tres"), msg("ana", T4, "quatro"))));
        assertEquals(List.of(List.of(tela("bia", T1, "um", "ana"), tela("ana", T2, "dois", "ana"))), view.anteriores);
    }

    @Test
    void semMensagensMaisAntigasAvisaEmVezDeInserirNada() {
        logado(new Evento.Historico(List.of(msg("bia", T1, "um"))));
        controller.carregarHistoricoAnterior();
        controller.tratar(new Evento.Historico(List.of(msg("bia", T1, "um"))));
        assertTrue(view.anteriores.isEmpty());
        assertEquals(List.of("Não há mensagens mais antigas."), view.avisos);
    }

    @Test
    void servidorSemHistoricoTambemViraAvisoENaoMensagemDeSistema() {
        logado();
        controller.carregarHistoricoAnterior();
        controller.tratar(new Evento.Sistema(Mensagens.SEM_HISTORICO));
        assertEquals(List.of("Não há mensagens mais antigas."), view.avisos);
        assertTrue(view.mensagens.isEmpty());
    }

    @Test
    void depoisDeCarregarAnterioresOutroPedidoNaoReaproveitaORespostaAntiga() {
        logado(new Evento.Historico(List.of(msg("bia", T3, "tres"))));
        controller.carregarHistoricoAnterior();
        controller.tratar(new Evento.Historico(List.of(msg("bia", T2, "dois"), msg("bia", T3, "tres"))));
        controller.carregarHistoricoAnterior();
        controller.tratar(new Evento.Historico(List.of(msg("bia", T1, "um"), msg("bia", T2, "dois"),
                msg("bia", T3, "tres"))));
        assertEquals(2, view.anteriores.size());
        assertEquals(List.of(tela("bia", T1, "um", "ana")), view.anteriores.get(1));
    }

    // ---------- busca ----------

    @Test
    void buscarPorUsuarioEnviaOComandoERespostaVaiParaOResultado() {
        logado();
        controller.buscarPorUsuario("  bia ");
        assertEquals("/buscar usuario bia", conexao.enviadas().get(conexao.enviadas().size() - 1));
        controller.tratar(new Evento.Historico(List.of(msg("bia", T1, "oi"))));
        assertEquals(List.of(List.of(tela("bia", T1, "oi", "ana"))), view.resultadosDeBusca);
        assertTrue(view.mensagens.isEmpty());
    }

    @Test
    void buscarPorTextoMandaOTrechoInteiroComEspacos() {
        logado();
        controller.buscarPorTexto("estudar java");
        assertEquals("/buscar texto estudar java", conexao.enviadas().get(conexao.enviadas().size() - 1));
    }

    @Test
    void buscarPorPeriodoValidaEMandaAsDuasDatas() {
        logado();
        controller.buscarPorPeriodo("2026-10-01", " 2026-10-31 ");
        assertEquals("/buscar periodo 2026-10-01 2026-10-31", conexao.enviadas().get(conexao.enviadas().size() - 1));
    }

    @Test
    void buscaSemResultadoDevolveListaVaziaEnaoViraAvisoDeSistema() {
        logado();
        controller.buscarPorTexto("nada");
        controller.tratar(new Evento.Sistema(Mensagens.SEM_RESULTADO_DE_BUSCA));
        assertEquals(List.of(List.of()), view.resultadosDeBusca);
        assertTrue(view.mensagens.isEmpty());
    }

    @Test
    void erroDoServidorNaBuscaVaiParaOErroDaBuscaENaoParaOErroGeral() {
        logado();
        controller.buscarPorUsuario("bia");
        controller.tratar(new Evento.Erro("Uso: /buscar usuario <nome> | ..."));
        assertEquals(1, view.errosDeBusca.size());
        assertTrue(view.erros.isEmpty());
    }

    @Test
    void camposDeBuscaInvalidosMostramErroSemEnviarNada() {
        logado();
        int antes = conexao.enviadas().size();
        controller.buscarPorUsuario("  ");
        controller.buscarPorUsuario(null);
        controller.buscarPorTexto("");
        controller.buscarPorPeriodo("", "2026-10-31");
        controller.buscarPorPeriodo("2026-10-01", "ontem");
        controller.buscarPorPeriodo("2026-13-45", "2026-14-01");
        controller.buscarPorPeriodo("2026-10-31", "2026-10-01");
        assertEquals(antes, conexao.enviadas().size());
        assertEquals(7, view.errosDeBusca.size());
        assertTrue(view.errosDeBusca.stream().noneMatch(String::isBlank));
    }

    @Test
    void buscaSemEstarConectadoMostraErroDeBusca() {
        criar(false);
        controller.buscarPorTexto("java");
        assertEquals(1, view.errosDeBusca.size());
        assertTrue(view.errosDeBusca.get(0).contains("desconectado"));
    }

    // ---------- salas ----------

    @Test
    void entrarNaSalaEnviaJoinEAplicaOHistoricoSoQuandoOOkChega() {
        logado();
        controller.entrarSala("Java");
        assertEquals("/join Java", conexao.enviadas().get(conexao.enviadas().size() - 1));
        controller.tratar(new Evento.Historico(List.of(new Evento.Msg("java", "bia", T1, "no java"))));
        assertEquals(1, view.historicos.size()); // ainda só o do login: espera o OK da troca
        controller.tratar(new Evento.Ok(Mensagens.entrouNaSala("java")));
        assertEquals(2, view.historicos.size());
        assertEquals(List.of(MensagemTela.publica(new Evento.Msg("java", "bia", T1, "no java"), "ana")),
                view.historicos.get(1));
        assertEquals("java", view.salasDoHistorico.get(1));
        assertEquals("java", controller.salaAtual());
    }

    @Test
    void trocarDeSalaSemHistoricoLimpaATelaEPedeAsListasDeNovo() {
        logado();
        int antes = conexao.enviadas().size();
        controller.entrarSala("vazia");
        controller.tratar(new Evento.Ok(Mensagens.entrouNaSala("vazia")));
        assertEquals(List.of(), view.historicos.get(1));
        List<String> novas = conexao.enviadas().subList(antes, conexao.enviadas().size());
        assertEquals(List.of("/join vazia", "/users", "/rooms"), novas);
    }

    @Test
    void erroAoTrocarDeSalaMostraOErroEDescartaOHistoricoBufferizado() {
        logado();
        controller.entrarSala("ruim");
        controller.tratar(new Evento.Historico(List.of(new Evento.Msg("ruim", "bia", T1, "x"))));
        controller.tratar(new Evento.Erro("Nome de sala inválido."));
        assertEquals(List.of("Nome de sala inválido."), view.erros);
        assertEquals("geral", controller.salaAtual());
        assertEquals(1, view.historicos.size());
        // um Ok de troca posterior não reaproveita o histórico descartado
        controller.entrarSala("outra");
        controller.tratar(new Evento.Ok(Mensagens.entrouNaSala("outra")));
        assertEquals(List.of(), view.historicos.get(1));
    }

    @Test
    void entrarNaSalaAtualOuComNomeVazioMostraErroSemEnviarNada() {
        logado();
        int antes = conexao.enviadas().size();
        controller.entrarSala("geral");
        controller.entrarSala("GERAL");
        controller.entrarSala("  ");
        controller.entrarSala(null);
        assertEquals(antes, conexao.enviadas().size());
        assertEquals(4, view.erros.size());
    }

    @Test
    void sairDaSalaNaGeralMostraErroEEmOutraSalaEnviaLeave() {
        logado();
        int antes = conexao.enviadas().size();
        controller.sairDaSala();
        assertEquals(antes, conexao.enviadas().size());
        assertEquals(1, view.erros.size());
        controller.entrarSala("java");
        controller.tratar(new Evento.Ok(Mensagens.entrouNaSala("java")));
        controller.sairDaSala();
        assertEquals("/leave", conexao.enviadas().get(conexao.enviadas().size() - 1));
        controller.tratar(new Evento.Ok(Mensagens.entrouNaSala("geral")));
        assertEquals("geral", controller.salaAtual());
    }

    // ---------- sair, exportar, listas ----------

    @Test
    void sairEnviaQuitFechaAConexaoEMarcaDesconectadoSemMostrarSessaoEncerrada() {
        logado();
        controller.sair();
        assertEquals("/quit", conexao.enviadas().get(conexao.enviadas().size() - 1));
        assertFalse(conexao.aberta());
        assertFalse(controller.conectado());
        assertEquals(EstadoConexao.DESCONECTADO, view.estados.get(view.estados.size() - 1));
        assertTrue(view.encerramentos.isEmpty());
        controller.sair(); // idempotente
    }

    @Test
    void atualizarListasPedeUsuariosESalas() {
        logado();
        controller.atualizarListas();
        List<String> e = conexao.enviadas();
        assertEquals(List.of("/users", "/rooms"), e.subList(e.size() - 2, e.size()));
    }

    @Test
    void exportarGravaSoMensagensPublicasDaSalaNoFormatoPadrao() throws IOException {
        logado(new Evento.Historico(List.of(msg("bia", T1, "um"))));
        controller.tratar(msg("ana", T2, "dois"));
        controller.tratar(new Evento.Privada("bia", "ana", T3, "segredo"));
        controller.tratar(new Evento.Sistema("bia saiu"));
        Path arquivo = controller.exportar(pasta.resolve("saida.txt"));
        assertEquals(pasta.resolve("saida.txt"), arquivo);
        String conteudo = Files.readString(arquivo);
        assertTrue(conteudo.startsWith("Histórico da sala geral\n"));
        assertTrue(conteudo.endsWith("[09:01] bia: um\n[09:02] ana: dois\n"), conteudo);
        assertFalse(conteudo.contains("segredo"));
        assertFalse(conteudo.contains("saiu"));
    }

    @Test
    void exportarSemMensagensPublicasLancaErroQueDizOQueFazer() {
        logado();
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> controller.exportar(pasta.resolve("vazio.txt")));
        assertTrue(e.getMessage().contains("Não há mensagens"));
        assertFalse(Files.exists(pasta.resolve("vazio.txt")));
    }

    @Test
    void exportarDepoisDeCarregarAnterioresIncluiTodasEmOrdem() throws IOException {
        logado(new Evento.Historico(List.of(msg("bia", T3, "tres"))));
        controller.carregarHistoricoAnterior();
        controller.tratar(new Evento.Historico(List.of(msg("bia", T1, "um"), msg("bia", T3, "tres"))));
        String conteudo = Files.readString(controller.exportar(pasta.resolve("tudo.txt")));
        assertTrue(conteudo.endsWith("[09:01] bia: um\n[09:03] bia: tres\n"), conteudo);
    }

    @Test
    void trocarDeSalaZeraOQueSeriaExportado() throws IOException {
        logado(new Evento.Historico(List.of(msg("bia", T1, "da geral"))));
        controller.entrarSala("java");
        controller.tratar(new Evento.Ok(Mensagens.entrouNaSala("java")));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> controller.exportar(pasta.resolve("java.txt")));
        assertTrue(e.getMessage().contains("Não há mensagens"));
    }

    @Test
    void construtorRejeitaArgumentosNulos() {
        assertThrows(NullPointerException.class, () -> new ChatController(null, (h, p) -> conexao));
        assertThrows(NullPointerException.class, () -> new ChatController(view, null));
    }
}
