package br.unicesumar.chat.infra;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Configuração do servidor: arquivo servidor.properties e variáveis de ambiente (sem segredos no código). */
class ConfiguracaoServidorTest {

    @TempDir
    Path pasta;

    private Path arquivo(String conteudo) throws IOException {
        Path p = pasta.resolve("servidor.properties");
        Files.writeString(p, conteudo, StandardCharsets.UTF_8);
        return p;
    }

    @Test
    void leOAdminDoArquivo() throws IOException {
        var config = ConfiguracaoServidor.carregar(
                arquivo("admin.usuario=chefe\nadmin.senha=segredo123\n"), Map.of());
        var admin = config.admin().orElseThrow();
        assertEquals("chefe", admin.nome());
        assertEquals("segredo123", admin.senha());
    }

    @Test
    void ambienteSobrescreveOArquivo() throws IOException {
        var config = ConfiguracaoServidor.carregar(
                arquivo("admin.usuario=chefe\nadmin.senha=doarquivo\n"),
                Map.of("CHAT_ADMIN_SENHA", "doambiente", "CHAT_ADMIN_USUARIO", "dono"));
        assertEquals("dono", config.admin().orElseThrow().nome());
        assertEquals("doambiente", config.admin().orElseThrow().senha());
    }

    @Test
    void usuarioNoArquivoESenhaNoAmbienteFuncionaJunto() throws IOException {
        var config = ConfiguracaoServidor.carregar(
                arquivo("admin.usuario=chefe\n"), Map.of("CHAT_ADMIN_SENHA", "doambiente"));
        assertEquals("chefe", config.admin().orElseThrow().nome());
        assertEquals("doambiente", config.admin().orElseThrow().senha());
    }

    @Test
    void semArquivoESemAmbienteNaoHaAdmin() {
        var config = ConfiguracaoServidor.carregar(pasta.resolve("nao-existe.properties"), Map.of());
        assertTrue(config.admin().isEmpty());
    }

    @Test
    void usuarioSemSenhaExplicaOQueFazer() throws IOException {
        Path p = arquivo("admin.usuario=chefe\n");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ConfiguracaoServidor.carregar(p, Map.of()));
        assertTrue(e.getMessage().contains("admin.senha"));
        assertFalse(e.getMessage().contains("chefe="));
    }

    @Test
    void senhaSemUsuarioExplicaOQueFazer() throws IOException {
        Path p = arquivo("admin.senha=segredo123\n");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ConfiguracaoServidor.carregar(p, Map.of()));
        assertTrue(e.getMessage().contains("admin.usuario"));
    }

    @Test
    void valoresEmBrancoContamComoAusentes() throws IOException {
        var config = ConfiguracaoServidor.carregar(
                arquivo("admin.usuario=\nadmin.senha=\n"), Map.of("CHAT_ADMIN_USUARIO", "  "));
        assertTrue(config.admin().isEmpty());
    }

    @Test
    void acentosNoArquivoSaoLidosEmUtf8() throws IOException {
        var config = ConfiguracaoServidor.carregar(
                arquivo("admin.usuario=chefe\nadmin.senha=sênha-ção\n"), Map.of());
        assertEquals("sênha-ção", config.admin().orElseThrow().senha());
    }

    @Test
    void toStringDaCredencialNuncaMostraASenha() throws IOException {
        var admin = ConfiguracaoServidor.carregar(
                arquivo("admin.usuario=chefe\nadmin.senha=segredo123\n"), Map.of()).admin().orElseThrow();
        assertFalse(admin.toString().contains("segredo123"));
        assertTrue(admin.toString().contains("chefe"));
    }
}
