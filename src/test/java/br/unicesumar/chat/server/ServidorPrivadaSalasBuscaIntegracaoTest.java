package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.persistence.jdbc.ConnectionFactory;
import br.unicesumar.chat.persistence.jdbc.JdbcMensagemRepository;
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
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Testes de INTEGRAÇÃO ponta a ponta: sockets reais, SQLite real, comandos de verdade. */
@Tag("integracao")
class ServidorPrivadaSalasBuscaIntegracaoTest {

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
            return Protocolo.decodificar(entrada.readLine());
        }

        /** Próximo evento que não seja aviso de sistema. */
        Evento lerSemAvisos() throws IOException {
            Evento e = ler();
            while (e instanceof Evento.Sistema) {
                e = ler();
            }
            return e;
        }

        /** Lê eventos, ignorando avisos de sistema que não sejam o esperado, até achar o esperado. */
        Evento lerAte(Evento esperado) throws IOException {
            Evento e = ler();
            while (!e.equals(esperado)) {
                assertInstanceOf(Evento.Sistema.class, e, "evento inesperado antes da resposta");
                e = ler();
            }
            return e;
        }

        /** Entra com o nome e consome tudo até o OK. */
        void entrarComo(String nome) throws IOException {
            assertInstanceOf(Evento.Sistema.class, ler());
            enviar(nome);
            Evento e = ler();
            while (!(e instanceof Evento.Ok)) {
                e = ler();
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    @TempDir
    Path pasta;

    private Servidor servidor;

    private int subir() throws IOException {
        ConnectionFactory fabrica = ConnectionFactory.arquivo(pasta.resolve("chat.db"));
        fabrica.criarTabelas();
        ClientRegistry registry = new ClientRegistry();
        ChatService chat = new ChatService(new JdbcMensagemRepository(fabrica), registry,
                Clock.systemDefaultZone());
        servidor = new Servidor(0, registry, new ProcessadorChat(chat, registry));
        servidor.iniciar();
        return servidor.porta();
    }

    @AfterEach
    void parar() {
        if (servidor != null) {
            servidor.parar();
        }
    }

    @Test
    void privadaChegaSoAoDestinatarioENuncaApareceParaTerceirosNemNoHistoricoNemNaBusca() throws Exception {
        int porta = subir();
        try (Cliente ana = new Cliente(porta); Cliente bia = new Cliente(porta); Cliente caio = new Cliente(porta)) {
            ana.entrarComo("ana");
            bia.entrarComo("bia");
            caio.entrarComo("caio");

            ana.enviar("/msg bia segredo123");
            Evento.Privada paraBia = assertInstanceOf(Evento.Privada.class, bia.lerSemAvisos());
            Evento.Privada confirmacao = assertInstanceOf(Evento.Privada.class, ana.lerSemAvisos());
            assertEquals("segredo123", paraBia.texto());
            assertEquals(paraBia, confirmacao);

            caio.enviar("ola publica"); // o próximo evento de caio é esta mensagem, nunca a privada
            Evento.Msg publica = assertInstanceOf(Evento.Msg.class, caio.lerSemAvisos());
            assertEquals("ola publica", publica.texto());

            ana.enviar("/msg zeca oi");
            Evento e = ana.lerSemAvisos();
            if (e instanceof Evento.Msg) { // a pública de caio também chega à ana
                e = ana.lerSemAvisos();
            }
            Evento.Erro erro = assertInstanceOf(Evento.Erro.class, e);
            assertTrue(erro.texto().contains("não está online"));
        }
        try (Cliente duda = new Cliente(porta)) {
            assertInstanceOf(Evento.Sistema.class, duda.ler());
            duda.enviar("duda");
            Evento.Historico historico = assertInstanceOf(Evento.Historico.class, duda.ler());
            assertEquals(List.of("ola publica"), historico.mensagens().stream().map(Evento.Msg::texto).toList());
            assertInstanceOf(Evento.Ok.class, duda.ler());

            duda.enviar("/buscar texto segredo123");
            // avisos de saída dos outros podem chegar antes: lê até a resposta do comando
            assertEquals(new Evento.Sistema("Nenhuma mensagem encontrada."),
                    duda.lerAte(new Evento.Sistema("Nenhuma mensagem encontrada.")));
        }
    }

    @Test
    void salasIsolamAsMensagensEOComandoRoomsListaAsAtivas() throws Exception {
        int porta = subir();
        try (Cliente ana = new Cliente(porta); Cliente bia = new Cliente(porta)) {
            ana.entrarComo("ana");
            bia.entrarComo("bia");

            ana.enviar("/join java");
            Evento.Ok ok = assertInstanceOf(Evento.Ok.class, ana.lerSemAvisos());
            assertEquals("Você entrou na sala java.", ok.texto());

            bia.enviar("so na geral");
            assertEquals("so na geral", ((Evento.Msg) bia.lerSemAvisos()).texto());
            ana.enviar("so no java");
            assertEquals("so no java", ((Evento.Msg) ana.lerSemAvisos()).texto());

            bia.enviar("/rooms");
            Evento.Salas salas = assertInstanceOf(Evento.Salas.class, bia.lerSemAvisos());
            assertEquals(List.of("geral", "java"), salas.nomes());

            // cada um continua sem receber a mensagem da sala do outro
            ana.enviar("/users");
            Evento.Usuarios usuarios = assertInstanceOf(Evento.Usuarios.class, ana.lerSemAvisos());
            assertEquals("java", usuarios.sala());
            assertEquals(List.of("ana"), usuarios.nomes());
        }
    }

    @Test
    void buscaPorUsuarioTextoEPeriodoFuncionaPeloSocketComSqlReal() throws Exception {
        int porta = subir();
        try (Cliente ana = new Cliente(porta); Cliente bia = new Cliente(porta)) {
            ana.entrarComo("ana");
            bia.entrarComo("bia");
            ana.enviar("estudando Java 100%");
            bia.enviar("tomando café");
            ana.lerSemAvisos();
            ana.lerSemAvisos();

            ana.enviar("/buscar usuario BIA");
            assertEquals(List.of("tomando café"), textos(ana.lerSemAvisos()));
            ana.enviar("/buscar texto java");
            assertEquals(List.of("estudando Java 100%"), textos(ana.lerSemAvisos()));
            ana.enviar("/buscar texto %");
            assertEquals(List.of("estudando Java 100%"), textos(ana.lerSemAvisos()));
            String hoje = java.time.LocalDate.now().toString();
            ana.enviar("/buscar periodo " + hoje + " " + hoje);
            assertEquals(2, ((Evento.Historico) ana.lerSemAvisos()).mensagens().size());
            ana.enviar("/buscar periodo 2020-01-01 2020-01-31");
            assertEquals(new Evento.Sistema("Nenhuma mensagem encontrada."),
                    ana.lerAte(new Evento.Sistema("Nenhuma mensagem encontrada.")));
        }
    }

    private static List<String> textos(Evento e) {
        return assertInstanceOf(Evento.Historico.class, e).mensagens().stream().map(Evento.Msg::texto).toList();
    }
}
