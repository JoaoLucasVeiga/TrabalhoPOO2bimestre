package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.CredencialUsuario;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.memory.UsuarioRepositoryEmMemoria;
import br.unicesumar.chat.protocol.Evento;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Modo com autenticação: /register e /login pelo processador (clientes falsos, sem rede). */
class ProcessadorChatAutenticacaoTest {

    private final ClientRegistry registry = new ClientRegistry();
    private final RepositorioEspiao repo = new RepositorioEspiao();
    private final UsuarioRepositoryEmMemoria usuarios = new UsuarioRepositoryEmMemoria();
    private final ChatService chat = new ChatService(repo, registry, Clock.systemDefaultZone());
    private final AuthService auth = new AuthService(usuarios, 1000);
    private final ProcessadorChat processador =
            new ProcessadorChat(chat, new CommandService(chat, registry, auth));

    private ClienteFalso conectar() {
        ClienteFalso c = ClienteFalso.novo();
        registry.adicionar(c.handler);
        processador.aoConectar(c.handler);
        return c;
    }

    private static Evento ultimo(ClienteFalso c) {
        List<Evento> e = c.eventos();
        return e.get(e.size() - 1);
    }

    private ClienteFalso logado(String nome, String senha) {
        ClienteFalso c = conectar();
        processador.aoReceber(c.handler, "/register " + nome + " " + senha);
        assertTrue(c.handler.usuario().isPresent());
        return c;
    }

    // ---------- antes de entrar ----------

    @Test
    void aoConectarExplicaComoCriarContaOuEntrar() {
        ClienteFalso c = conectar();
        String aviso = ((Evento.Sistema) c.eventos().get(0)).aviso();
        assertTrue(aviso.contains("/register"));
        assertTrue(aviso.contains("/login"));
    }

    @Test
    void textoLivreAntesDeEntrarGeraErroENaoViraMensagemNemNome() {
        ClienteFalso c = conectar();
        processador.aoReceber(c.handler, "ana");
        Evento.Erro e = assertInstanceOf(Evento.Erro.class, ultimo(c));
        assertTrue(e.texto().contains("/login"));
        assertTrue(c.handler.usuario().isEmpty());
        assertTrue(repo.salvas().isEmpty());
    }

    @Test
    void comandosQueExigemContaSaoRecusadosAntesDeEntrar() {
        for (String comando : List.of("/users", "/history", "/msg bia oi", "/join java", "/leave",
                "/rooms", "/buscar texto oi")) {
            ClienteFalso c = conectar();
            processador.aoReceber(c.handler, comando);
            Evento.Erro e = assertInstanceOf(Evento.Erro.class, ultimo(c), comando);
            assertTrue(e.texto().contains("/login"), comando);
            assertTrue(c.handler.usuario().isEmpty(), comando);
        }
    }

    @Test
    void helpEQuitFuncionamAntesDeEntrar() {
        ClienteFalso c = conectar();
        processador.aoReceber(c.handler, "/help");
        assertInstanceOf(Evento.Sistema.class, ultimo(c));
        assertTrue(((Evento.Sistema) ultimo(c)).aviso().contains("/login"));
        processador.aoReceber(c.handler, "/quit");
        assertFalse(c.handler.ativo());
    }

    // ---------- /register ----------

    @Test
    void registerCriaAContaEJaEntraNoChat() {
        ClienteFalso c = conectar();
        processador.aoReceber(c.handler, "/register ana minhasenha");
        assertEquals(new Usuario("ana"), c.handler.usuario().orElseThrow());
        assertEquals(new Evento.Ok("Você entrou como ana na sala geral."), ultimo(c));
        assertTrue(usuarios.existe("ana"));
    }

    @Test
    void registerGuardaSoOHashNuncaASenha() {
        logado("ana", "minhasenha");
        CredencialUsuario c = usuarios.buscarPorNome("ana").orElseThrow();
        assertFalse(c.senhaHash().contains("minhasenha"));
        assertFalse(c.salt().contains("minhasenha"));
    }

    @Test
    void registerNaoTransformaALinhaEmMensagemDoChat() {
        ClienteFalso ana = logado("ana", "minhasenha");
        assertTrue(repo.salvas().isEmpty());
        assertTrue(ana.textos().isEmpty());
    }

    @Test
    void aSenhaNuncaEEcoadaEmNenhumEventoEnviadoAoCliente() {
        ClienteFalso c = conectar();
        processador.aoReceber(c.handler, "/register ana senhaSuperSecreta");
        processador.aoReceber(c.handler, "/register ana senhaSuperSecreta");
        for (Evento e : c.eventos()) {
            assertFalse(e.toString().contains("senhaSuperSecreta"), e.toString());
        }
        ClienteFalso outro = conectar();
        processador.aoReceber(outro.handler, "/login ana senhaSuperErrada");
        for (Evento e : outro.eventos()) {
            assertFalse(e.toString().contains("senhaSuperErrada"), e.toString());
        }
    }

    @Test
    void registerComNomeJaCadastradoGeraErroEOClienteContinuaSemEntrar() {
        logado("ana", "minhasenha");
        ClienteFalso outro = conectar();
        processador.aoReceber(outro.handler, "/register ANA outrasenha");
        Evento.Erro e = assertInstanceOf(Evento.Erro.class, ultimo(outro));
        assertTrue(e.texto().contains("já está cadastrado"));
        assertTrue(outro.handler.usuario().isEmpty());
    }

    @Test
    void registerComNomeOuSenhaInvalidosGeraErroEnaoCriaConta() {
        ClienteFalso c = conectar();
        processador.aoReceber(c.handler, "/register a minhasenha");
        assertInstanceOf(Evento.Erro.class, ultimo(c));
        processador.aoReceber(c.handler, "/register ana 123");
        assertInstanceOf(Evento.Erro.class, ultimo(c));
        assertFalse(usuarios.existe("ana"));
        assertFalse(usuarios.existe("a"));
        assertTrue(c.handler.usuario().isEmpty());
    }

    @Test
    void registerSemArgumentosMostraOUso() {
        for (String ruim : List.of("/register", "/register ana", "/register ana   ")) {
            ClienteFalso c = conectar();
            processador.aoReceber(c.handler, ruim);
            Evento.Erro e = assertInstanceOf(Evento.Erro.class, ultimo(c), ruim);
            assertTrue(e.texto().contains("/register nome senha"), ruim);
        }
    }

    // ---------- /login ----------

    @Test
    void loginCorretoIdentificaOClienteComONomeOriginalDoCadastro() {
        ClienteFalso primeira = logado("Ana", "minhasenha");
        registry.remover(primeira.handler); // sessão única: a anterior precisa ter saído
        ClienteFalso c = conectar();
        processador.aoReceber(c.handler, "/login ANA minhasenha");
        assertEquals("Ana", c.handler.usuario().orElseThrow().nome());
    }

    @Test
    void loginRecebeOHistoricoDaSalaAntesDoOk() {
        ClienteFalso ana = logado("ana", "minhasenha");
        processador.aoReceber(ana.handler, "recado antigo");
        registry.remover(ana.handler);
        ClienteFalso volta = conectar();
        processador.aoReceber(volta.handler, "/login ana minhasenha");
        Evento.Historico h = assertInstanceOf(Evento.Historico.class, volta.eventos().get(1));
        assertEquals("recado antigo", h.mensagens().get(0).texto());
        assertInstanceOf(Evento.Ok.class, ultimo(volta));
    }

    @Test
    void loginComSenhaErradaGeraErroGenericoENaoIdentifica() {
        logado("ana", "minhasenha");
        registry.remover(registry.buscarPorUsuario("ana").orElseThrow());
        ClienteFalso c = conectar();
        processador.aoReceber(c.handler, "/login ana errada123");
        Evento.Erro e = assertInstanceOf(Evento.Erro.class, ultimo(c));
        assertTrue(e.texto().contains("incorretos"));
        assertTrue(c.handler.usuario().isEmpty());
    }

    @Test
    void loginDeUsuarioInexistenteDaAMesmaMensagemDeSenhaErrada() {
        logado("ana", "minhasenha");
        registry.remover(registry.buscarPorUsuario("ana").orElseThrow());
        ClienteFalso erradaSenha = conectar();
        processador.aoReceber(erradaSenha.handler, "/login ana errada123");
        ClienteFalso semUsuario = conectar();
        processador.aoReceber(semUsuario.handler, "/login fantasma errada123");
        assertEquals(ultimo(erradaSenha), ultimo(semUsuario));
    }

    @Test
    void loginComSenhaContendoEspacosFunciona() {
        logado("ana", "minha senha forte");
        registry.remover(registry.buscarPorUsuario("ana").orElseThrow());
        ClienteFalso c = conectar();
        processador.aoReceber(c.handler, "/login ana minha senha forte");
        assertTrue(c.handler.usuario().isPresent());
    }

    @Test
    void loginSemArgumentosMostraOUso() {
        for (String ruim : List.of("/login", "/login ana")) {
            ClienteFalso c = conectar();
            processador.aoReceber(c.handler, ruim);
            Evento.Erro e = assertInstanceOf(Evento.Erro.class, ultimo(c), ruim);
            assertTrue(e.texto().contains("/login nome senha"), ruim);
        }
    }

    @Test
    void loginNaoViraMensagemDoChat() {
        logado("ana", "minhasenha");
        registry.remover(registry.buscarPorUsuario("ana").orElseThrow());
        ClienteFalso c = conectar();
        processador.aoReceber(c.handler, "/login ana minhasenha");
        assertTrue(repo.salvas().isEmpty());
    }

    // ---------- sessão única ----------

    @Test
    void usuarioJaConectadoNaoPodeEntrarDeNovoEASessaoOriginalNaoEAfetada() {
        ClienteFalso primeira = logado("ana", "minhasenha");
        int eventosAntes = primeira.eventos().size();
        ClienteFalso segunda = conectar();
        processador.aoReceber(segunda.handler, "/login ana minhasenha");
        Evento.Erro e = assertInstanceOf(Evento.Erro.class, ultimo(segunda));
        assertTrue(e.texto().contains("outra sessão"));
        assertTrue(segunda.handler.usuario().isEmpty());
        assertSame(primeira.handler, registry.buscarPorUsuario("ana").orElseThrow());
        assertEquals(eventosAntes, primeira.eventos().size());
    }

    @Test
    void depoisQueASessaoAnteriorSaiOUsuarioPodeEntrarDeNovo() {
        ClienteFalso primeira = logado("ana", "minhasenha");
        registry.remover(primeira.handler);
        ClienteFalso segunda = conectar();
        processador.aoReceber(segunda.handler, "/login ana minhasenha");
        assertTrue(segunda.handler.usuario().isPresent());
    }

    @Test
    void sessaoUnicaTambemValeParaMaiusculasDiferentes() {
        logado("ana", "minhasenha");
        ClienteFalso segunda = conectar();
        processador.aoReceber(segunda.handler, "/login ANA minhasenha");
        assertInstanceOf(Evento.Erro.class, ultimo(segunda));
        assertTrue(segunda.handler.usuario().isEmpty());
    }

    @Test
    void quemJaEntrouNaoPodeUsarRegisterNemLoginDeNovo() {
        ClienteFalso ana = logado("ana", "minhasenha");
        processador.aoReceber(ana.handler, "/login ana minhasenha");
        Evento.Erro e1 = assertInstanceOf(Evento.Erro.class, ultimo(ana));
        assertTrue(e1.texto().contains("já está conectado"));
        processador.aoReceber(ana.handler, "/register bia outrasenha");
        assertInstanceOf(Evento.Erro.class, ultimo(ana));
        assertFalse(usuarios.existe("bia"));
        assertEquals(new Usuario("ana"), ana.handler.usuario().orElseThrow());
    }

    // ---------- depois de entrar tudo continua igual ----------

    @Test
    void depoisDeEntrarMensagensEComandosFuncionamNormalmente() {
        ClienteFalso ana = logado("ana", "minhasenha");
        ClienteFalso bia = logado("bia", "outrasenha");
        processador.aoReceber(ana.handler, "olá, bia");
        processador.aoReceber(ana.handler, "/msg bia segredo");
        processador.aoReceber(bia.handler, "/users");
        assertEquals(List.of("olá, bia"), bia.textos());
        assertTrue(bia.eventos().stream().anyMatch(e -> e instanceof Evento.Privada));
        assertTrue(bia.eventos().stream().anyMatch(e -> e instanceof Evento.Usuarios));
    }

    @Test
    void avisosDeEntradaChegamAosOutrosAoFazerLogin() {
        ClienteFalso ana = logado("ana", "minhasenha");
        logado("bia", "outrasenha");
        assertTrue(ana.eventos().contains(new Evento.Sistema("bia entrou na sala geral.")));
    }

    @Test
    void modoAntigoSemAutenticacaoContinuaFuncionando() {
        ProcessadorChat legado = new ProcessadorChat(chat, registry);
        ClienteFalso c = ClienteFalso.novo();
        registry.adicionar(c.handler);
        legado.aoReceber(c.handler, "caio");
        assertEquals(new Usuario("caio"), c.handler.usuario().orElseThrow());
    }

    @Test
    void historicoOuBuscasNuncaTrazemLinhasDeLoginOuRegistro() {
        ClienteFalso ana = logado("ana", "minhasenha");
        processador.aoReceber(ana.handler, "mensagem normal");
        for (Mensagem m : repo.salvas()) {
            assertFalse(m.conteudo().contains("minhasenha"));
            assertFalse(m.conteudo().startsWith("/"));
        }
        assertEquals(LocalDateTime.class, repo.salvas().get(0).horario().getClass());
    }
}
