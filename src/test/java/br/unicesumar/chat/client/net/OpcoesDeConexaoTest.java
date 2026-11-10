package br.unicesumar.chat.client.net;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Argumentos de linha de comando dos clientes: posicionais e --tls. */
class OpcoesDeConexaoTest {

    @Test
    void semArgumentosNaoUsaTls() {
        OpcoesDeConexao o = OpcoesDeConexao.de(new String[0]);
        assertTrue(o.posicionais().isEmpty());
        assertFalse(o.usaTls());
    }

    @Test
    void separaPosicionaisDeOpcoes() {
        OpcoesDeConexao o = OpcoesDeConexao.de(new String[] {"servidor", "5001", "--tls"});
        assertEquals(List.of("servidor", "5001"), o.posicionais());
        assertTrue(o.usaTls());
        assertEquals(Path.of("chat-tls.crt"), o.certificado());
    }

    @Test
    void tlsComCaminhoDoCertificado() {
        OpcoesDeConexao o = OpcoesDeConexao.de(new String[] {"--tls=/tmp/meu.crt", "host"});
        assertTrue(o.usaTls());
        assertEquals(Path.of("/tmp/meu.crt"), o.certificado());
        assertEquals(List.of("host"), o.posicionais());
    }

    @Test
    void tlsSemCaminhoDepoisDoIgualUsaOPadrao() {
        assertEquals(Path.of("chat-tls.crt"), OpcoesDeConexao.de(new String[] {"--tls="}).certificado());
    }

    @Test
    void semTlsConexaoEValidaSemCertificado() throws IOException {
        OpcoesDeConexao o = OpcoesDeConexao.de(new String[] {"localhost"});
        o.validar();
        assertNotNull(o.conexao("localhost", 5000));
    }

    @Test
    void comTlsEUmCertificadoAusenteValidarDizOQueFazer() {
        OpcoesDeConexao o = OpcoesDeConexao.de(new String[] {"--tls=nao-existe-mesmo.crt"});
        IOException erro = assertThrows(IOException.class, o::validar);
        assertTrue(erro.getMessage().contains("nao-existe-mesmo.crt"));
    }

    @Test
    void comTlsEUmCertificadoAusenteConexaoFalhaComMensagemClara() {
        OpcoesDeConexao o = OpcoesDeConexao.de(new String[] {"--tls=nao-existe-mesmo.crt"});
        IllegalStateException erro = assertThrows(IllegalStateException.class, () -> o.conexao("localhost", 5000));
        assertTrue(erro.getMessage().contains("nao-existe-mesmo.crt"));
    }
}
