package br.unicesumar.chat.infra;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** TLS opcional (integração): usa o keytool do JDK e sockets reais em loopback. */
@Tag("integracao")
class CertificadoTlsIntegracaoTest {

    private static final char[] SENHA = "senha-de-teste".toCharArray();

    @TempDir
    Path pasta;

    private Path p12() {
        return pasta.resolve("chat-tls.p12");
    }

    private Path crt() {
        return pasta.resolve("chat-tls.crt");
    }

    @Test
    void garantirCriaOKeystoreEOCertificadoQuandoNaoExistem() throws IOException {
        CertificadoTls.garantir(p12(), crt(), SENHA);
        assertTrue(Files.size(p12()) > 0);
        assertTrue(Files.readString(crt()).startsWith("-----BEGIN CERTIFICATE-----"));
    }

    @Test
    void garantirNaoSobrescreveUmKeystoreQueJaExiste() throws IOException {
        CertificadoTls.garantir(p12(), crt(), SENHA);
        byte[] antes = Files.readAllBytes(p12());
        CertificadoTls.garantir(p12(), crt(), SENHA);
        assertArrayEquals(antes, Files.readAllBytes(p12()));
    }

    @Test
    void garantirRecriaOCertificadoSeSoEleSumiu() throws IOException {
        CertificadoTls.garantir(p12(), crt(), SENHA);
        Files.delete(crt());
        CertificadoTls.garantir(p12(), crt(), SENHA);
        assertTrue(Files.exists(crt()));
    }

    @Test
    void senhaErradaDoKeystoreDaMensagemClara() throws IOException {
        CertificadoTls.garantir(p12(), crt(), SENHA);
        IOException erro = assertThrows(IOException.class,
                () -> CertificadoTls.contextoDoServidor(p12(), "outra-senha".toCharArray()));
        assertTrue(erro.getMessage().contains("senha"));
    }

    @Test
    void clienteQueConfiaNoCertificadoConversaComOServidorPorTls() throws Exception {
        CertificadoTls.garantir(p12(), crt(), SENHA);
        SSLContext servidor = CertificadoTls.contextoDoServidor(p12(), SENHA);
        SSLContext cliente = CertificadoTls.contextoDoCliente(crt());
        try (SSLServerSocket escuta = (SSLServerSocket) servidor.getServerSocketFactory().createServerSocket(0)) {
            CompletableFuture<String> recebido = CompletableFuture.supplyAsync(() -> {
                try (SSLSocket s = (SSLSocket) escuta.accept()) {
                    byte[] buffer = s.getInputStream().readNBytes(4);
                    return new String(buffer, java.nio.charset.StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
            try (SSLSocket s = (SSLSocket) cliente.getSocketFactory().createSocket("localhost", escuta.getLocalPort())) {
                s.getOutputStream().write("ping".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                s.getOutputStream().flush();
                assertEquals("ping", recebido.get(10, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void clienteQueNaoConfiaNoCertificadoRecusaOServidor() throws Exception {
        CertificadoTls.garantir(p12(), crt(), SENHA);
        Path outraPasta = Files.createDirectory(pasta.resolve("outro"));
        CertificadoTls.garantir(outraPasta.resolve("a.p12"), outraPasta.resolve("a.crt"), SENHA);
        SSLContext servidor = CertificadoTls.contextoDoServidor(p12(), SENHA);
        SSLContext desconfiado = CertificadoTls.contextoDoCliente(outraPasta.resolve("a.crt"));
        try (SSLServerSocket escuta = (SSLServerSocket) servidor.getServerSocketFactory().createServerSocket(0)) {
            CompletableFuture.runAsync(() -> {
                try (SSLSocket s = (SSLSocket) escuta.accept()) {
                    s.startHandshake();
                } catch (IOException e) {
                    // esperado: o cliente desiste do handshake
                }
            });
            try (SSLSocket s = (SSLSocket) desconfiado.getSocketFactory().createSocket("localhost",
                    escuta.getLocalPort())) {
                assertThrows(SSLHandshakeException.class, s::startHandshake);
            }
        }
    }

    @Test
    void certificadoAusenteNoClienteDizOQueFazer() {
        IOException erro = assertThrows(IOException.class, () -> CertificadoTls.contextoDoCliente(crt()));
        assertTrue(erro.getMessage().contains("chat-tls.crt"));
    }
}
