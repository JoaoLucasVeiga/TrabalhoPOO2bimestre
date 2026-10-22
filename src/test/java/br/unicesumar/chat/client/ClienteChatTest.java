package br.unicesumar.chat.client;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.client.net.ConexaoFalsa;
import br.unicesumar.chat.protocol.Evento;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Testes unitários do cliente de terminal: conexão falsa, teclado e tela em memória. */
class ClienteChatTest {

    private static final LocalDateTime HORA = LocalDateTime.of(2026, 10, 1, 9, 5);

    private final ByteArrayOutputStream bytesDaTela = new ByteArrayOutputStream();
    private final PrintStream tela = new PrintStream(bytesDaTela, true, StandardCharsets.UTF_8);

    private ClienteChat cliente(ConexaoFalsa conexao, String digitado) {
        return new ClienteChat(conexao, new BufferedReader(new StringReader(digitado)), tela, 200);
    }

    private String saida() {
        return bytesDaTela.toString(StandardCharsets.UTF_8);
    }

    @Test
    void mostraOsEventosDoServidorFormatadosNaOrdem() throws IOException {
        ConexaoFalsa conexao = new ConexaoFalsa()
                .receberEvento(new Evento.Sistema("Digite seu nome."))
                .receberEvento(new Evento.Msg("geral", "ana", HORA, "oi"))
                .receberEvento(new Evento.Erro("algo"))
                .encerrarPeloServidor();
        cliente(conexao, "").executar();
        String saida = saida();
        assertTrue(saida.indexOf("* Digite seu nome.") < saida.indexOf("[09:05] ana: oi"));
        assertTrue(saida.indexOf("[09:05] ana: oi") < saida.indexOf("[erro] algo"));
    }

    @Test
    void enviaCadaLinhaDigitadaAoServidorNaOrdem() throws IOException {
        ConexaoFalsa conexao = new ConexaoFalsa().encerrarPeloServidor();
        cliente(conexao, "ana\nolá | mundo\n/users\n").executar();
        assertEquals(List.of("ana", "olá | mundo", "/users", "/quit"), conexao.enviadas());
    }

    @Test
    void quitDigitadoEnviaEParaDeLerOTeclado() throws IOException {
        ConexaoFalsa conexao = new ConexaoFalsa().encerrarPeloServidor();
        cliente(conexao, "oi\n/quit\nnunca enviada\n").executar();
        assertEquals(List.of("oi", "/quit"), conexao.enviadas());
    }

    @Test
    void quitIgnoraMaiusculasEEspacos() throws IOException {
        ConexaoFalsa conexao = new ConexaoFalsa().encerrarPeloServidor();
        cliente(conexao, "  /QUIT  \nnunca enviada\n").executar();
        assertEquals(1, conexao.enviadas().size());
    }

    @Test
    void fimDoTecladoEnviaQuitAutomaticamenteParaSairComEducacao() throws IOException {
        ConexaoFalsa conexao = new ConexaoFalsa().encerrarPeloServidor();
        cliente(conexao, "ana\n").executar();
        assertEquals(List.of("ana", "/quit"), conexao.enviadas());
    }

    @Test
    void naoEnviaLinhaVaziaDoTeclado() throws IOException {
        ConexaoFalsa conexao = new ConexaoFalsa().encerrarPeloServidor();
        cliente(conexao, "\n   \noi\n/quit\n").executar();
        assertEquals(List.of("oi", "/quit"), conexao.enviadas());
    }

    @Test
    void servidorEncerrandoAConexaoAvisaOUsuarioEFechaALigacao() throws IOException {
        ConexaoFalsa conexao = new ConexaoFalsa().encerrarPeloServidor();
        cliente(conexao, "").executar();
        assertTrue(saida().contains("Conexão encerrada"));
        assertFalse(conexao.aberta());
    }

    @Test
    void falhaAoAbrirPropagaAIOExceptionComAMensagemDoAdaptador() {
        ConexaoFalsa conexao = new ConexaoFalsa()
                .falharAoAbrirCom(new IOException("Não foi possível conectar a localhost:5000."));
        IOException e = assertThrows(IOException.class, () -> cliente(conexao, "").executar());
        assertTrue(e.getMessage().contains("localhost:5000"));
    }

    @Test
    void falhaAoEnviarMostraErroEEncerra() throws IOException {
        ConexaoFalsa conexao = new ConexaoFalsa().falharAoEnviarCom(new IOException("pipe quebrado"))
                .encerrarPeloServidor();
        cliente(conexao, "oi\noutra\n").executar();
        assertTrue(saida().contains("[erro]"));
        assertFalse(conexao.aberta());
    }

    @Test
    void linhaForaDoProtocoloMostraErroEContinuaLendo() throws IOException {
        ConexaoFalsa conexao = new ConexaoFalsa().receberLinhaInvalida("lixo")
                .receberEvento(new Evento.Sistema("depois do lixo")).encerrarPeloServidor();
        cliente(conexao, "").executar();
        assertTrue(saida().contains("[erro]"));
        assertTrue(saida().contains("* depois do lixo"));
    }

    @Test
    void semFimDoServidorOClienteFechaAConexaoAoTerminarEnaoFicaPreso() throws IOException {
        ConexaoFalsa conexao = new ConexaoFalsa(); // o servidor nunca encerra
        cliente(conexao, "oi\n").executar();
        assertFalse(conexao.aberta());
    }

    @Test
    void construtorRejeitaDependenciasNulas() {
        assertThrows(NullPointerException.class,
                () -> new ClienteChat(null, new BufferedReader(new StringReader("")), tela));
        assertThrows(NullPointerException.class,
                () -> new ClienteChat(new ConexaoFalsa(), null, tela));
        assertThrows(NullPointerException.class,
                () -> new ClienteChat(new ConexaoFalsa(), new BufferedReader(new StringReader("")), null));
    }
}
