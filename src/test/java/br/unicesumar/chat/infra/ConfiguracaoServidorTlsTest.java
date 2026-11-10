package br.unicesumar.chat.infra;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Configuração do TLS no servidor.properties e no ambiente. */
class ConfiguracaoServidorTlsTest {

    @TempDir
    Path pasta;

    @Test
    void semConfiguracaoNaoHaSenhaDeTls() {
        ConfiguracaoServidor c = ConfiguracaoServidor.carregar(pasta.resolve("nao-existe.properties"), Map.of());
        assertTrue(c.tls().senha().isEmpty());
        assertEquals("chat-tls.p12", c.tls().keystore().toString());
        assertEquals("chat-tls.crt", c.tls().certificado().toString());
    }

    @Test
    void lerSenhaEArquivosDoProperties() throws Exception {
        Path arquivo = pasta.resolve("servidor.properties");
        Files.writeString(arquivo, "tls.senha=segredo123\ntls.keystore=meu.p12\ntls.certificado=meu.crt\n");
        ConfiguracaoServidor c = ConfiguracaoServidor.carregar(arquivo, Map.of());
        assertEquals("segredo123", c.tls().senha().orElseThrow());
        assertEquals("meu.p12", c.tls().keystore().toString());
        assertEquals("meu.crt", c.tls().certificado().toString());
    }

    @Test
    void ambienteTemPrioridadeSobreOArquivo() throws Exception {
        Path arquivo = pasta.resolve("servidor.properties");
        Files.writeString(arquivo, "tls.senha=do-arquivo\n");
        ConfiguracaoServidor c = ConfiguracaoServidor.carregar(arquivo, Map.of("CHAT_TLS_SENHA", "do-ambiente"));
        assertEquals("do-ambiente", c.tls().senha().orElseThrow());
    }

    @Test
    void senhaEmBrancoContaComoAusente() {
        ConfiguracaoServidor c = ConfiguracaoServidor.carregar(pasta.resolve("x.properties"),
                Map.of("CHAT_TLS_SENHA", "   "));
        assertTrue(c.tls().senha().isEmpty());
    }

    @Test
    void toStringNaoMostraASenha() {
        ConfiguracaoServidor c = ConfiguracaoServidor.carregar(pasta.resolve("x.properties"),
                Map.of("CHAT_TLS_SENHA", "segredo123"));
        assertFalse(c.tls().toString().contains("segredo123"));
    }
}
