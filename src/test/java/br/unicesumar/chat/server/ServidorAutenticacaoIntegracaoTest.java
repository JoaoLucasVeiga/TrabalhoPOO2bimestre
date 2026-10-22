package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.persistence.jdbc.JdbcMensagemRepository;
import br.unicesumar.chat.persistence.jdbc.JdbcUsuarioRepository;
import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.protocol.Protocolo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Testes de INTEGRAÇÃO: login de verdade com sockets reais e SQLite real em arquivo temporário. */
@Tag("integracao")
class ServidorAutenticacaoIntegracaoTest {

    private static final String SENHA = "senha-ultra-secreta-123";

    private static class Cliente implements AutoCloseable {
        final Socket socket;
        final BufferedReader entrada;
        final PrintWriter saida;

        Cliente(int porta) throws IOException {
            socket = new Socket(InetAddress.getLoopbackAddress(), porta);
            socket.setSoTimeout(10_000);
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            saida = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        }

        void enviar(String texto) {
            saida.print(Protocolo.codificarEntrada(texto) + "\n");
            saida.flush();
        }

        Evento ler() throws IOException {
            String linha = entrada.readLine();
            return linha == null ? null : Protocolo.decodificar(linha);
        }

        /** Lê eventos até o primeiro OK ou ERRO e o devolve (ignora aviso e histórico). */
        Evento respostaDoComando() throws IOException {
            Evento e = ler();
            while (!(e instanceof Evento.Ok) && !(e instanceof Evento.Erro)) {
                e = ler();
            }
            return e;
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    @TempDir
    Path pasta;

    private Servidor servidor;

    private Servidor subir() throws IOException {
        ConnectionFactory fabrica = ConnectionFactory.arquivo(pasta.resolve("chat.db"));
        fabrica.criarTabelas();
        ClientRegistry registry = new ClientRegistry();
        ChatService chat = new ChatService(new JdbcMensagemRepository(fabrica), registry,
                Clock.systemDefaultZone());
        AuthService auth = new AuthService(new JdbcUsuarioRepository(fabrica), 1000);
        servidor = new Servidor(0, registry,
                new ProcessadorChat(chat, new CommandService(chat, registry, auth)));
        servidor.iniciar();
        return servidor;
    }

    @AfterEach
    void parar() {
        if (servidor != null) {
            servidor.parar();
        }
    }

    @Test
    void contaCriadaContinuaValendoDepoisDeReiniciarOServidorEASenhaNuncaVaiParaOBanco() throws Exception {
        Servidor primeiro = subir();
        try (Cliente c = new Cliente(primeiro.porta())) {
            assertInstanceOf(Evento.Sistema.class, c.ler());
            c.enviar("/register ana " + SENHA);
            assertEquals(new Evento.Ok("Você entrou como ana na sala geral."), c.respostaDoComando());
            c.enviar("uma mensagem comum");
            assertInstanceOf(Evento.Msg.class, c.ler());
        }
        primeiro.parar();

        Servidor segundo = subir();
        try (Cliente c = new Cliente(segundo.porta())) {
            assertInstanceOf(Evento.Sistema.class, c.ler());
            c.enviar("/login ana senha-errada-000");
            assertInstanceOf(Evento.Erro.class, c.respostaDoComando());
            c.enviar("/login ANA " + SENHA);
            assertEquals(new Evento.Ok("Você entrou como ana na sala geral."), c.respostaDoComando());
        }
        confirmarQueASenhaNaoEstaEmLugarNenhumDoBanco();
    }

    private void confirmarQueASenhaNaoEstaEmLugarNenhumDoBanco() throws SQLException {
        ConnectionFactory fabrica = ConnectionFactory.arquivo(pasta.resolve("chat.db"));
        try (Connection c = fabrica.abrir(); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT nome, senha_hash, salt FROM usuario")) {
                assertTrue(rs.next());
                assertEquals("ana", rs.getString("nome"));
                assertFalse(rs.getString("senha_hash").contains(SENHA));
                assertTrue(rs.getString("senha_hash").startsWith("pbkdf2_sha256$"));
                assertFalse(rs.getString("salt").isBlank());
                assertFalse(rs.next());
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM mensagem WHERE conteudo LIKE '%secreta%' OR conteudo LIKE '/login%'"
                            + " OR conteudo LIKE '/register%'")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1)); // linhas de login/registro nunca viram mensagem
            }
        }
    }

    @Test
    void umUsuarioNaoPodeTerDuasSessoesAoMesmoTempo() throws Exception {
        Servidor s = subir();
        try (Cliente primeira = new Cliente(s.porta())) {
            primeira.ler();
            primeira.enviar("/register ana " + SENHA);
            assertInstanceOf(Evento.Ok.class, primeira.respostaDoComando());

            try (Cliente segunda = new Cliente(s.porta())) {
                segunda.ler();
                segunda.enviar("/login ana " + SENHA);
                Evento.Erro erro = assertInstanceOf(Evento.Erro.class, segunda.respostaDoComando());
                assertTrue(erro.texto().contains("outra sessão"));

                primeira.enviar("/quit");
                assertInstanceOf(Evento.Ok.class, primeira.respostaDoComando());
                assertNull(primeira.ler()); // o servidor fechou a conexão

                // a sessão da primeira ainda precisa ser removida: tenta até o servidor liberar o nome
                Evento resposta;
                do {
                    segunda.enviar("/login ana " + SENHA);
                    resposta = segunda.respostaDoComando();
                } while (resposta instanceof Evento.Erro e && e.texto().contains("outra sessão"));
                assertInstanceOf(Evento.Ok.class, resposta);
            }
        }
    }

    @Test
    void antesDeEntrarTextoLivreEComandosDoChatSaoRecusados() throws Exception {
        Servidor s = subir();
        try (Cliente c = new Cliente(s.porta())) {
            c.ler();
            c.enviar("ola, tem alguem ai?");
            assertInstanceOf(Evento.Erro.class, c.ler());
            c.enviar("/users");
            assertInstanceOf(Evento.Erro.class, c.ler());
            c.enviar("/help");
            assertInstanceOf(Evento.Sistema.class, c.ler());
        }
    }
}
