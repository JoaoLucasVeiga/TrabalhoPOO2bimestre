package br.unicesumar.chat.client;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.client.net.ConexaoSocket;
import br.unicesumar.chat.persistence.memory.MensagemRepositoryEmMemoria;
import br.unicesumar.chat.persistence.memory.UsuarioRepositoryEmMemoria;
import br.unicesumar.chat.server.AuthService;
import br.unicesumar.chat.server.ChatService;
import br.unicesumar.chat.server.ClientRegistry;
import br.unicesumar.chat.server.CommandService;
import br.unicesumar.chat.server.ProcessadorChat;
import br.unicesumar.chat.server.SalaService;
import br.unicesumar.chat.server.Servidor;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** A demonstração de mensagens simultâneas contra um servidor real (sockets em loopback). */
@Tag("integracao")
@Timeout(120)
class DemonstracaoSimultaneaIntegracaoTest {

    private static final String SENHA = "senha-de-teste";

    private Servidor servidor;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final PrintStream saida = new PrintStream(bytes, true, StandardCharsets.UTF_8);

    @AfterEach
    void parar() {
        if (servidor != null) {
            servidor.parar();
        }
    }

    private int subir(boolean comAutenticacao) throws Exception {
        ClientRegistry registry = new ClientRegistry();
        AuthService auth = comAutenticacao ? new AuthService(new UsuarioRepositoryEmMemoria(), 1000) : null;
        ChatService chat = new ChatService(new MensagemRepositoryEmMemoria(), registry, Clock.systemDefaultZone(),
                auth == null ? nome -> false : auth::contaExiste);
        CommandService comandos = new CommandService(chat, registry, new SalaService(chat, registry), auth);
        servidor = new Servidor(0, registry, new ProcessadorChat(chat, comandos));
        servidor.iniciar();
        return servidor.porta();
    }

    @Test
    void servidorComLoginTodosRecebemTudoNaMesmaOrdem() throws Exception {
        int porta = subir(true);
        DemonstracaoSimultanea.Resultado r = DemonstracaoSimultanea.executar(ConexaoSocket::new, "localhost", porta,
                6, 15, "geral", SENHA, saida);
        assertTrue(r.ok(), bytes.toString(StandardCharsets.UTF_8));
        assertEquals(90, r.esperadas());
        assertEquals(6, r.recebidasPorCliente().size());
        assertTrue(r.recebidasPorCliente().stream().allMatch(n -> n == 90));
        assertTrue(r.mesmaOrdem());
        assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("SIM"));
    }

    @Test
    void servidorSemLoginTambemFunciona() throws Exception {
        int porta = subir(false);
        DemonstracaoSimultanea.Resultado r = DemonstracaoSimultanea.executar(ConexaoSocket::new, "localhost", porta,
                4, 10, "geral", SENHA, saida);
        assertTrue(r.ok(), bytes.toString(StandardCharsets.UTF_8));
        assertEquals(40, r.esperadas());
    }

    @Test
    void rodarDuasVezesNoMesmoServidorReaproveitaAsContas() throws Exception {
        int porta = subir(true);
        assertTrue(DemonstracaoSimultanea.executar(ConexaoSocket::new, "localhost", porta, 3, 5, "geral", SENHA, saida).ok());
        assertTrue(DemonstracaoSimultanea.executar(ConexaoSocket::new, "localhost", porta, 3, 5, "geral", SENHA, saida).ok());
    }

    @Test
    void funcionaEmOutraSala() throws Exception {
        int porta = subir(true);
        assertTrue(DemonstracaoSimultanea.executar(ConexaoSocket::new, "localhost", porta, 3, 5, "teste", SENHA, saida).ok());
    }

    @Test
    void servidorForaDoArFalhaComMensagemClara() {
        IllegalStateException erro = assertThrows(IllegalStateException.class, () -> DemonstracaoSimultanea.executar(
                ConexaoSocket::new, "localhost", 1, 2, 2, "geral", SENHA, saida));
        assertTrue(erro.getMessage().contains("servidor"));
    }

    @Test
    void argumentosInvalidosSaoRecusados() {
        assertThrows(IllegalArgumentException.class, () -> DemonstracaoSimultanea.executar(
                ConexaoSocket::new, "localhost", 5000, 1, 5, "geral", SENHA, saida));
        assertThrows(IllegalArgumentException.class, () -> DemonstracaoSimultanea.executar(
                ConexaoSocket::new, "localhost", 5000, 3, 0, "geral", SENHA, saida));
    }
}
