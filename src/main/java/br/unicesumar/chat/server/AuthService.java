package br.unicesumar.chat.server;

import br.unicesumar.chat.model.CredencialUsuario;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.UsuarioRepository;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Cadastro e verificação de senhas. A senha nunca é guardada: só o hash
 * PBKDF2WithHmacSHA256 com salt aleatório, no formato
 * {@code pbkdf2_sha256$<iterações>$<hash em base64>}. A comparação é em tempo
 * constante e usuário inexistente dá a mesma resposta (e custa o mesmo tempo)
 * de senha errada, para não revelar quais nomes existem. Não tem estado mutável
 * próprio; é seguro entre threads. Só conhece a interface {@link UsuarioRepository}.
 */
public class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class.getName());

    /** Iterações padrão do PBKDF2. */
    public static final int ITERACOES_PADRAO = 210_000;
    /** Menor senha aceita, em caracteres. */
    public static final int SENHA_MINIMA = 6;
    /** Maior senha aceita, em caracteres. */
    public static final int SENHA_MAXIMA = 128;

    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final String PREFIXO = "pbkdf2_sha256";
    private static final int TAMANHO_SALT = 16;
    private static final int BITS_DO_HASH = 256;
    private static final int ITERACOES_MAXIMAS_ACEITAS = 10_000_000;
    private static final String LOGIN_INVALIDO =
            "Usuário ou senha incorretos. Confira os dados e tente de novo.";

    private final UsuarioRepository repositorio;
    private final int iteracoes;
    private final SecureRandom aleatorio = new SecureRandom();

    /** Cria o serviço com as iterações padrão. */
    public AuthService(UsuarioRepository repositorio) {
        this(repositorio, ITERACOES_PADRAO);
    }

    /**
     * Cria o serviço.
     *
     * @param iteracoes iterações do PBKDF2 usadas em novos cadastros (testes usam poucas)
     */
    public AuthService(UsuarioRepository repositorio, int iteracoes) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio");
        if (iteracoes < 1) {
            throw new IllegalArgumentException("O número de iterações deve ser pelo menos 1.");
        }
        this.iteracoes = iteracoes;
    }

    /**
     * Cria uma conta nova.
     *
     * @return o usuário cadastrado
     * @throws IllegalArgumentException se o nome ou a senha forem inválidos, ou o nome já existir
     *                                  (sem diferenciar maiúsculas)
     */
    public Usuario registrar(String nome, String senha) {
        Usuario usuario = new Usuario(nome);
        validarSenha(senha);
        byte[] salt = new byte[TAMANHO_SALT];
        aleatorio.nextBytes(salt);
        String hash = PREFIXO + "$" + iteracoes + "$"
                + Base64.getEncoder().encodeToString(derivar(senha, salt, iteracoes));
        CredencialUsuario credencial = new CredencialUsuario(usuario, hash,
                Base64.getEncoder().encodeToString(salt));
        if (!repositorio.cadastrar(credencial)) {
            throw new IllegalArgumentException("O nome '" + nome + "' já está cadastrado. "
                    + "Escolha outro nome ou entre com /login nome senha.");
        }
        return usuario;
    }

    /**
     * Confere nome e senha.
     *
     * @return o usuário, com as maiúsculas do cadastro
     * @throws IllegalArgumentException com a mesma mensagem para usuário inexistente,
     *                                  senha errada ou dados inválidos
     */
    public Usuario autenticar(String nome, String senha) {
        Optional<CredencialUsuario> credencial = nome == null
                ? Optional.empty() : repositorio.buscarPorNome(nome);
        boolean confere;
        if (credencial.isPresent()) {
            confere = verificar(senha, credencial.get());
        } else {
            gastarOMesmoTempo(senha);
            confere = false;
        }
        if (!confere) {
            throw new IllegalArgumentException(LOGIN_INVALIDO);
        }
        return credencial.orElseThrow().usuario();
    }

    private static void validarSenha(String senha) {
        if (senha == null || senha.isBlank()) {
            throw new IllegalArgumentException(
                    "A senha não pode ser vazia nem só espaços. Use de " + SENHA_MINIMA + " a "
                            + SENHA_MAXIMA + " caracteres.");
        }
        if (senha.length() < SENHA_MINIMA || senha.length() > SENHA_MAXIMA) {
            throw new IllegalArgumentException("A senha deve ter de " + SENHA_MINIMA + " a "
                    + SENHA_MAXIMA + " caracteres.");
        }
    }

    private boolean verificar(String senha, CredencialUsuario credencial) {
        if (senha == null || senha.isEmpty()) {
            return false;
        }
        try {
            String[] partes = credencial.senhaHash().split("\\$", -1);
            if (partes.length != 3 || !PREFIXO.equals(partes[0])) {
                return hashForaDoFormato(credencial);
            }
            int voltas = Integer.parseInt(partes[1]);
            if (voltas < 1 || voltas > ITERACOES_MAXIMAS_ACEITAS) {
                return hashForaDoFormato(credencial);
            }
            byte[] esperado = Base64.getDecoder().decode(partes[2]);
            byte[] salt = Base64.getDecoder().decode(credencial.salt());
            byte[] calculado = derivar(senha, salt, voltas);
            return MessageDigest.isEqual(esperado, calculado); // tempo constante
        } catch (IllegalArgumentException e) {
            return hashForaDoFormato(credencial);
        }
    }

    /** Registra o problema (só o dono do registro, nunca o hash nem a senha) e recusa o login. */
    private static boolean hashForaDoFormato(CredencialUsuario credencial) {
        LOG.log(Level.WARNING, "Hash ou salt de ''{0}'' está fora do formato esperado; login recusado.",
                credencial.usuario().nome());
        return false;
    }

    private void gastarOMesmoTempo(String senha) {
        if (senha != null && !senha.isEmpty()) {
            byte[] salt = new byte[TAMANHO_SALT];
            aleatorio.nextBytes(salt);
            derivar(senha, salt, iteracoes);
        }
    }

    private static byte[] derivar(String senha, byte[] salt, int voltas) {
        PBEKeySpec especificacao = new PBEKeySpec(senha.toCharArray(), salt, voltas, BITS_DO_HASH);
        try {
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(especificacao).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("O algoritmo " + ALGORITMO + " não está disponível.", e);
        } finally {
            especificacao.clearPassword();
        }
    }
}
