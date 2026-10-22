package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.infra.LogArquivo;
import br.unicesumar.chat.persistence.memory.UsuarioRepositoryEmMemoria;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** O log do servidor nunca pode conter privadas, senhas nem argumentos de comandos. */
class ProcessadorComLogTest {

    @TempDir
    Path pasta;

    private final ClientRegistry registry = new ClientRegistry();
    private final ChatService chat = new ChatService(new RepositorioEspiao(), registry, Clock.systemDefaultZone());
    private final AuthService auth = new AuthService(new UsuarioRepositoryEmMemoria(), 1000);
    private ProcessadorComLog processador;
    private Path arquivoDeLog;

    private void iniciar() {
        arquivoDeLog = pasta.resolve("servidor.log");
        processador = new ProcessadorComLog(
                new ProcessadorChat(chat, new CommandService(chat, registry, auth)),
                new LogArquivo(arquivoDeLog, Clock.systemDefaultZone()));
    }

    private ClienteFalso conectar() {
        ClienteFalso c = ClienteFalso.novo();
        registry.adicionar(c.handler);
        processador.aoConectar(c.handler);
        return c;
    }

    private String log() throws IOException {
        return Files.exists(arquivoDeLog) ? Files.readString(arquivoDeLog) : "";
    }

    @Test
    void registraConexaoEntradaMensagemPublicaESaida() throws IOException {
        iniciar();
        ClienteFalso ana = conectar();
        processador.aoReceber(ana.handler, "/register ana minhasenha123");
        processador.aoReceber(ana.handler, "olá, pessoal");
        registry.remover(ana.handler);
        processador.aoDesconectar(ana.handler);
        String log = log();
        assertTrue(log.contains("Conexão aberta"), log);
        assertTrue(log.contains("ana entrou no chat"), log);
        assertTrue(log.contains("[geral] ana: olá, pessoal"), log);
        assertTrue(log.contains("ana saiu do chat"), log);
    }

    @Test
    void senhasNuncaVaoParaOLog() throws IOException {
        iniciar();
        ClienteFalso ana = conectar();
        processador.aoReceber(ana.handler, "/register ana minhasenha123");
        registry.remover(ana.handler);
        ClienteFalso volta = conectar();
        processador.aoReceber(volta.handler, "/login ana senhaerrada999");
        processador.aoReceber(volta.handler, "/login ana minhasenha123");
        String log = log();
        assertFalse(log.contains("minhasenha123"), log);
        assertFalse(log.contains("senhaerrada999"), log);
        assertTrue(log.contains("/login"), log); // o nome do comando aparece, os argumentos não
    }

    @Test
    void mensagemPrivadaNuncaTemOConteudoNemODestinatarioNoLog() throws IOException {
        iniciar();
        ClienteFalso ana = conectar();
        processador.aoReceber(ana.handler, "/register ana minhasenha123");
        ClienteFalso bia = conectar();
        processador.aoReceber(bia.handler, "/register bia outrasenha456");
        processador.aoReceber(ana.handler, "/msg bia segredo-ultra-privado");
        String log = log();
        assertFalse(log.contains("segredo-ultra-privado"), log);
        assertTrue(log.contains("ana usou /msg"), log);
    }

    @Test
    void argumentosDeQualquerComandoNaoSaoRegistrados() throws IOException {
        iniciar();
        ClienteFalso ana = conectar();
        processador.aoReceber(ana.handler, "/register ana minhasenha123");
        processador.aoReceber(ana.handler, "/buscar texto assunto-reservado");
        processador.aoReceber(ana.handler, "/join sala_secreta");
        String log = log();
        assertFalse(log.contains("assunto-reservado"), log);
        assertFalse(log.contains("sala_secreta"), log);
        assertTrue(log.contains("ana usou /buscar"), log);
        assertTrue(log.contains("ana usou /join"), log);
    }

    @Test
    void linhaAntesDoLoginNaoTemOConteudoRegistrado() throws IOException {
        iniciar();
        ClienteFalso c = conectar();
        processador.aoReceber(c.handler, "alguem digitou isto sem entrar");
        assertFalse(log().contains("alguem digitou isto"), log());
    }

    @Test
    void desconexaoSemLoginETambemRegistrada() throws IOException {
        iniciar();
        ClienteFalso c = conectar();
        registry.remover(c.handler);
        processador.aoDesconectar(c.handler);
        assertTrue(log().contains("Conexão encerrada sem login"), log());
    }

    @Test
    void errosDoProcessadorInternoSePropagamParaOHandlerTratar() {
        arquivoDeLog = pasta.resolve("servidor.log");
        ProcessadorEntrada quebrado = (origem, linha) -> {
            throw new IllegalStateException("bug");
        };
        ProcessadorComLog comLog = new ProcessadorComLog(quebrado, new LogArquivo(arquivoDeLog, Clock.systemDefaultZone()));
        ClienteFalso c = ClienteFalso.novo();
        assertThrows(IllegalStateException.class, () -> comLog.aoReceber(c.handler, "oi"));
    }

    @Test
    void construtorRejeitaArgumentosNulos() {
        assertThrows(NullPointerException.class,
                () -> new ProcessadorComLog(null, new LogArquivo(pasta.resolve("x.log"), Clock.systemDefaultZone())));
        assertThrows(NullPointerException.class,
                () -> new ProcessadorComLog((o, l) -> { }, null));
    }
}
