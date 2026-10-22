package br.unicesumar.chat.server;

import static org.junit.jupiter.api.Assertions.*;

import br.unicesumar.chat.model.CredencialUsuario;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.RepositorioException;
import br.unicesumar.chat.persistence.UsuarioRepository;
import br.unicesumar.chat.persistence.memory.UsuarioRepositoryEmMemoria;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/** Regras de autenticação com repositório em memória (sem rede nem banco). Poucas iterações: rápido. */
class AuthServiceTest {

    private static final int ITERACOES = 1000;

    private final UsuarioRepositoryEmMemoria usuarios = new UsuarioRepositoryEmMemoria();
    private final AuthService auth = new AuthService(usuarios, ITERACOES);

    private CredencialUsuario guardada(String nome) {
        return usuarios.buscarPorNome(nome).orElseThrow();
    }

    // ---------- armazenamento da senha ----------

    @Test
    void ohashGuardadoNaoEIgualASenhaENaoAContem() {
        auth.registrar("ana", "minhasenha");
        CredencialUsuario c = guardada("ana");
        assertNotEquals("minhasenha", c.senhaHash());
        assertFalse(c.senhaHash().contains("minhasenha"));
        assertFalse(c.salt().contains("minhasenha"));
        assertFalse(c.toString().contains("minhasenha"));
    }

    @Test
    void ohashSegueOFormatoComAlgoritmoEIteracoes() {
        auth.registrar("ana", "minhasenha");
        assertTrue(guardada("ana").senhaHash().startsWith("pbkdf2_sha256$" + ITERACOES + "$"));
    }

    @Test
    void ohashEPbkdf2HmacSha256ComOSaltGuardado() throws Exception {
        auth.registrar("ana", "minhasenha");
        CredencialUsuario c = guardada("ana");
        byte[] salt = Base64.getDecoder().decode(c.salt());
        assertEquals(16, salt.length);
        byte[] esperado = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(new PBEKeySpec("minhasenha".toCharArray(), salt, ITERACOES, 256))
                .getEncoded();
        String guardado = c.senhaHash().split("\\$")[2];
        assertEquals(Base64.getEncoder().encodeToString(esperado), guardado);
    }

    @Test
    void cadaUsuarioTemUmSaltDiferenteEPortantoUmHashDiferenteMesmoComAMesmaSenha() {
        auth.registrar("ana", "senhaigual");
        auth.registrar("bia", "senhaigual");
        assertNotEquals(guardada("ana").salt(), guardada("bia").salt());
        assertNotEquals(guardada("ana").senhaHash(), guardada("bia").senhaHash());
    }

    @Test
    void osaltEAleatorioAMuitosRegistros() {
        java.util.Set<String> salts = new java.util.HashSet<>();
        for (int i = 0; i < 30; i++) {
            auth.registrar("user" + i, "senha-" + i);
            salts.add(guardada("user" + i).salt());
        }
        assertEquals(30, salts.size());
    }

    // ---------- registrar ----------

    @Test
    void registrarDevolveOUsuarioComONomeInformado() {
        assertEquals("Ana", auth.registrar("Ana", "minhasenha").nome());
    }

    @Test
    void registrarNomeJaCadastradoIgnorandoMaiusculasLancaErroENaoSubstituiAConta() {
        auth.registrar("ana", "senhaoriginal");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> auth.registrar("ANA", "outrasenha"));
        assertTrue(e.getMessage().contains("já está cadastrado"));
        assertTrue(e.getMessage().contains("/login"));
        assertEquals("ana", auth.autenticar("ana", "senhaoriginal").nome());
        assertThrows(IllegalArgumentException.class, () -> auth.autenticar("ana", "outrasenha"));
    }

    @Test
    void registrarComNomeInvalidoLancaErro() {
        assertThrows(IllegalArgumentException.class, () -> auth.registrar(null, "minhasenha"));
        assertThrows(IllegalArgumentException.class, () -> auth.registrar("a", "minhasenha"));
        assertThrows(IllegalArgumentException.class, () -> auth.registrar("ana maria", "minhasenha"));
        assertTrue(usuarios.buscarPorNome("a").isEmpty());
    }

    @Test
    void senhaComMenosDeSeisCaracteresELimiteExato() {
        assertThrows(IllegalArgumentException.class, () -> auth.registrar("ana", "12345"));
        assertEquals("ana", auth.registrar("ana", "123456").nome());
    }

    @Test
    void senhaComExatamente128CaracteresEAceitaE129Nao() {
        assertEquals("ana", auth.registrar("ana", "s".repeat(128)).nome());
        assertThrows(IllegalArgumentException.class, () -> auth.registrar("bia", "s".repeat(129)));
    }

    @Test
    void senhaNulaVaziaOuSoComEspacosELimiteRejeitada() {
        assertThrows(IllegalArgumentException.class, () -> auth.registrar("ana", null));
        assertThrows(IllegalArgumentException.class, () -> auth.registrar("ana", ""));
        assertThrows(IllegalArgumentException.class, () -> auth.registrar("ana", "        "));
        assertTrue(usuarios.buscarPorNome("ana").isEmpty());
    }

    @Test
    void mensagemDeErroDaSenhaDizOsLimites() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> auth.registrar("ana", "123"));
        assertTrue(e.getMessage().contains("6"));
        assertTrue(e.getMessage().contains("128"));
    }

    @Test
    void falhaDoRepositorioPropagaRepositorioException() {
        UsuarioRepository quebrado = new UsuarioRepository() {
            @Override
            public boolean cadastrar(CredencialUsuario credencial) {
                throw new RepositorioException("Não foi possível cadastrar o usuário.", null);
            }

            @Override
            public Optional<CredencialUsuario> buscarPorNome(String nome) {
                return Optional.empty();
            }

            @Override
            public boolean existe(String nome) {
                return false;
            }
        };
        AuthService servico = new AuthService(quebrado, ITERACOES);
        assertThrows(RepositorioException.class, () -> servico.registrar("ana", "minhasenha"));
    }

    // ---------- autenticar ----------

    @Test
    void loginCorretoDevolveOUsuarioComONomeOriginalDoCadastro() {
        auth.registrar("Ana", "minhasenha");
        assertEquals("Ana", auth.autenticar("Ana", "minhasenha").nome());
    }

    @Test
    void loginIgnoraMaiusculasNoNomeMasNaoNaSenha() {
        auth.registrar("Ana", "MinhaSenha");
        assertEquals("Ana", auth.autenticar("ANA", "MinhaSenha").nome());
        assertThrows(IllegalArgumentException.class, () -> auth.autenticar("ana", "minhasenha"));
    }

    @Test
    void loginComSenhaIncorretaLancaErroGenerico() {
        auth.registrar("ana", "minhasenha");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> auth.autenticar("ana", "errada123"));
        assertTrue(e.getMessage().contains("incorretos"));
    }

    @Test
    void usuarioInexistenteDaExatamenteAMesmaMensagemQueSenhaErrada() {
        auth.registrar("ana", "minhasenha");
        String senhaErrada = assertThrows(IllegalArgumentException.class,
                () -> auth.autenticar("ana", "errada123")).getMessage();
        String semUsuario = assertThrows(IllegalArgumentException.class,
                () -> auth.autenticar("fantasma", "qualquer1")).getMessage();
        assertEquals(senhaErrada, semUsuario);
    }

    @Test
    void loginComSenhaOuNomeNulosOuVaziosFalhaComAMesmaMensagem() {
        auth.registrar("ana", "minhasenha");
        String esperado = assertThrows(IllegalArgumentException.class,
                () -> auth.autenticar("ana", "errada123")).getMessage();
        assertEquals(esperado, assertThrows(IllegalArgumentException.class,
                () -> auth.autenticar("ana", null)).getMessage());
        assertEquals(esperado, assertThrows(IllegalArgumentException.class,
                () -> auth.autenticar("ana", "")).getMessage());
        assertEquals(esperado, assertThrows(IllegalArgumentException.class,
                () -> auth.autenticar(null, "minhasenha")).getMessage());
        assertEquals(esperado, assertThrows(IllegalArgumentException.class,
                () -> auth.autenticar("nome invalido", "minhasenha")).getMessage());
    }

    @Test
    void senhaComEspacosAcentosESimbolosFunciona() {
        String senha = "  minha senha: ação! 😀 | \\n  ";
        auth.registrar("ana", senha);
        assertEquals("ana", auth.autenticar("ana", senha).nome());
        assertThrows(IllegalArgumentException.class, () -> auth.autenticar("ana", senha.strip()));
    }

    @Test
    void senhaQueSoDifereNoUltimoCaractereFalha() {
        auth.registrar("ana", "minhasenha1");
        assertThrows(IllegalArgumentException.class, () -> auth.autenticar("ana", "minhasenha2"));
        assertThrows(IllegalArgumentException.class, () -> auth.autenticar("ana", "minhasenha"));
        assertThrows(IllegalArgumentException.class, () -> auth.autenticar("ana", "minhasenha11"));
    }

    @Test
    void hashGuardadoMalFormadoFazOLoginFalharSemExcecaoEstranha() {
        usuarios.cadastrar(new CredencialUsuario(new Usuario("ana"), "isso-nao-e-um-hash", "c2FsdA=="));
        usuarios.cadastrar(new CredencialUsuario(new Usuario("bia"), "pbkdf2_sha256$abc$AAAA", "c2FsdA=="));
        usuarios.cadastrar(new CredencialUsuario(new Usuario("caio"), "pbkdf2_sha256$1000$AAAA", "###"));
        for (String nome : List.of("ana", "bia", "caio")) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> auth.autenticar(nome, "qualquer123"), nome);
            assertTrue(e.getMessage().contains("incorretos"), nome);
        }
    }

    @Test
    void ohashGuardadoComOutroNumeroDeIteracoesAindaValida() {
        new AuthService(usuarios, 2000).registrar("ana", "minhasenha");
        assertEquals("ana", auth.autenticar("ana", "minhasenha").nome()); // auth usa 1000 ao registrar
    }

    // ---------- construção ----------

    @Test
    void construtorPadraoUsaIteracoesFortesEFunciona() {
        AuthService padrao = new AuthService(new UsuarioRepositoryEmMemoria());
        padrao.registrar("ana", "minhasenha");
        assertEquals("ana", padrao.autenticar("ana", "minhasenha").nome());
        assertTrue(AuthService.ITERACOES_PADRAO >= 100_000);
    }

    @Test
    void construtorRejeitaRepositorioNuloEIteracoesInvalidas() {
        assertThrows(NullPointerException.class, () -> new AuthService(null));
        assertThrows(IllegalArgumentException.class, () -> new AuthService(usuarios, 0));
        assertThrows(IllegalArgumentException.class, () -> new AuthService(usuarios, -5));
    }

    // ---------- concorrência ----------

    @RepeatedTest(10)
    void oitoThreadsRegistrandoOMesmoNomeTemUmUnicoVencedor() throws Exception {
        UsuarioRepositoryEmMemoria repo = new UsuarioRepositoryEmMemoria();
        AuthService servico = new AuthService(repo, ITERACOES);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger vencedores = new AtomicInteger();
        List<Future<?>> futuros = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String nome = i % 2 == 0 ? "ana" : "ANA";
            int id = i;
            futuros.add(pool.submit(() -> {
                largada.await();
                try {
                    servico.registrar(nome, "senha-numero-" + id);
                    vencedores.incrementAndGet();
                } catch (IllegalArgumentException esperado) {
                    // perdeu a corrida: nome já cadastrado
                }
                return null;
            }));
        }
        largada.countDown();
        for (Future<?> f : futuros) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(1, vencedores.get());
    }

    @RepeatedTest(5)
    void variosLoginsAoMesmoTempoDoMesmoUsuarioFuncionamTodos() throws Exception {
        auth.registrar("ana", "minhasenha");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<String>> futuros = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            futuros.add(pool.submit(() -> {
                largada.await();
                return auth.autenticar("ana", "minhasenha").nome();
            }));
        }
        largada.countDown();
        for (Future<String> f : futuros) {
            assertEquals("ana", f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
    }
}
