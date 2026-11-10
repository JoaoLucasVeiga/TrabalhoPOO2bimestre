package br.unicesumar.chat.client.net;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.protocol.Evento;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** A conexão responde ao PING do servidor sozinha e não entrega o PING a quem a usa. */
@Tag("integracao")
class ConexaoSocketPingTest {

    @Test
    void respondeComPongEEntregaSoOProximoEvento() throws Exception {
        try (ServerSocket servidor = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            CompletableFuture<String> recebidoPeloServidor = new CompletableFuture<>();
            Thread t = new Thread(() -> {
                try (Socket s = servidor.accept()) {
                    s.setSoTimeout(5000);
                    PrintWriter saida = new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8);
                    BufferedReader entrada = new BufferedReader(
                            new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    saida.print("PING\n");
                    saida.print("SISTEMA|depois do ping\n");
                    saida.flush();
                    recebidoPeloServidor.complete(entrada.readLine());
                } catch (IOException e) {
                    recebidoPeloServidor.completeExceptionally(e);
                }
            });
            t.setDaemon(true);
            t.start();
            ConexaoSocket conexao = new ConexaoSocket("localhost", servidor.getLocalPort());
            conexao.abrir();
            try {
                assertEquals(new Evento.Sistema("depois do ping"), conexao.receber());
                assertEquals("/pong", recebidoPeloServidor.get(5, TimeUnit.SECONDS));
            } finally {
                conexao.fechar();
            }
        }
    }
}
