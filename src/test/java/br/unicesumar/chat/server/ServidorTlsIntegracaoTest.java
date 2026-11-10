package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.client.net.ConexaoSocket;
import br.unicesumar.chat.infra.CertificadoTls;
import br.unicesumar.chat.protocol.Evento;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Path;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Servidor e cliente do projeto conversando por TLS (integração, sockets reais). */
@Tag("integracao")
class ServidorTlsIntegracaoTest {

    private static final char[] SENHA = "senha-de-teste".toCharArray();

    @TempDir
    Path pasta;

    private Servidor servidor;

    @AfterEach
    void parar() {
        if (servidor != null) {
            servidor.parar();
        }
    }

    private int subir(SSLContext contexto) throws IOException {
        ProcessadorEntrada eco = (origem, linha) -> origem.enviar(new Evento.Ok("eco " + linha));
        servidor = new Servidor(0, eco).comTls(contexto.getServerSocketFactory());
        servidor.iniciar();
        return servidor.porta();
    }

    @Test
    void clienteComTlsEnviaERecebePeloServidorComTls() throws Exception {
        Path p12 = pasta.resolve("s.p12");
        Path crt = pasta.resolve("s.crt");
        CertificadoTls.garantir(p12, crt, SENHA);
        int porta = subir(CertificadoTls.contextoDoServidor(p12, SENHA));

        ConexaoSocket conexao = new ConexaoSocket("localhost", porta,
                CertificadoTls.contextoDoCliente(crt).getSocketFactory());
        conexao.abrir();
        try {
            conexao.enviar("oi 😀");
            assertEquals(new Evento.Ok("eco oi 😀"), conexao.receber());
        } finally {
            conexao.fechar();
        }
    }

    @Test
    void clienteSemTlsNaoConsegueFalarComServidorComTls() throws Exception {
        Path p12 = pasta.resolve("s.p12");
        CertificadoTls.garantir(p12, pasta.resolve("s.crt"), SENHA);
        int porta = subir(CertificadoTls.contextoDoServidor(p12, SENHA));

        ConexaoSocket conexao = new ConexaoSocket("localhost", porta);
        conexao.abrir();
        try {
            conexao.definirTempoDeEspera(5000);
            conexao.enviar("oi");
            Evento resposta = null;
            try {
                resposta = conexao.receber();
            } catch (IOException esperado) {
                // o servidor derruba a conexão que não fala TLS
            }
            assertNull(resposta, "texto puro não pode ser entendido por um servidor com TLS");
        } finally {
            conexao.fechar();
        }
    }

    @Test
    void servidorSemTlsContinuaIgual() throws Exception {
        ProcessadorEntrada eco = (origem, linha) -> origem.enviar(new Evento.Ok("eco " + linha));
        servidor = new Servidor(0, eco);
        servidor.iniciar();
        ConexaoSocket conexao = new ConexaoSocket("localhost", servidor.porta());
        conexao.abrir();
        try (Socket ignorado = new Socket()) {
            conexao.enviar("oi");
            assertEquals(new Evento.Ok("eco oi"), conexao.receber());
        } finally {
            conexao.fechar();
        }
    }
}
