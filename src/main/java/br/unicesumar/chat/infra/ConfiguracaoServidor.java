package br.unicesumar.chat.infra;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Configuração do servidor lida de um arquivo {@code servidor.properties} e de variáveis de
 * ambiente (que têm prioridade). Serve para guardar a conta do administrador fora do código e
 * do repositório.
 */
public final class ConfiguracaoServidor {

    /** Variável de ambiente com o usuário administrador. */
    public static final String AMBIENTE_USUARIO = "CHAT_ADMIN_USUARIO";
    /** Variável de ambiente com a senha do administrador. */
    public static final String AMBIENTE_SENHA = "CHAT_ADMIN_SENHA";

    private static final String CHAVE_USUARIO = "admin.usuario";
    private static final String CHAVE_SENHA = "admin.senha";

    /**
     * Conta do administrador.
     *
     * @param nome  nome de usuário
     * @param senha senha em texto, só para criar a conta (nunca aparece em {@code toString})
     */
    public record CredencialAdmin(String nome, String senha) {
        /** Valida que nome e senha foram informados. */
        public CredencialAdmin {
            Objects.requireNonNull(nome, "nome");
            Objects.requireNonNull(senha, "senha");
        }

        @Override
        public String toString() {
            return "CredencialAdmin[nome=" + nome + ", senha=***]";
        }
    }

    private final Optional<CredencialAdmin> admin;

    private ConfiguracaoServidor(Optional<CredencialAdmin> admin) {
        this.admin = admin;
    }

    /**
     * Lê a configuração. Arquivo ausente não é erro. Valores em branco contam como ausentes.
     *
     * @param arquivo  arquivo {@code .properties} (UTF-8)
     * @param ambiente variáveis de ambiente (passe {@code System.getenv()})
     * @throws IllegalArgumentException se só o usuário ou só a senha do admin foi informado
     * @throws IllegalStateException    se o arquivo existe mas não pode ser lido
     */
    public static ConfiguracaoServidor carregar(Path arquivo, Map<String, String> ambiente) {
        Objects.requireNonNull(arquivo, "arquivo");
        Objects.requireNonNull(ambiente, "ambiente");
        Properties propriedades = new Properties();
        if (Files.isRegularFile(arquivo)) {
            try (Reader leitor = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
                propriedades.load(leitor);
            } catch (IOException e) {
                throw new IllegalStateException("Não foi possível ler o arquivo " + arquivo
                        + ". Confira se ele existe e se você tem permissão de leitura.", e);
            }
        }
        String usuario = valor(ambiente.get(AMBIENTE_USUARIO), propriedades.getProperty(CHAVE_USUARIO));
        String senha = valor(ambiente.get(AMBIENTE_SENHA), propriedades.getProperty(CHAVE_SENHA));
        if (usuario == null && senha == null) {
            return new ConfiguracaoServidor(Optional.empty());
        }
        if (senha == null) {
            throw new IllegalArgumentException("Falta a senha do administrador. Defina " + CHAVE_SENHA
                    + " no servidor.properties ou a variável " + AMBIENTE_SENHA + ".");
        }
        if (usuario == null) {
            throw new IllegalArgumentException("Falta o usuário do administrador. Defina " + CHAVE_USUARIO
                    + " no servidor.properties ou a variável " + AMBIENTE_USUARIO + ".");
        }
        return new ConfiguracaoServidor(Optional.of(new CredencialAdmin(usuario, senha)));
    }

    /** Conta do administrador, se estiver configurada. */
    public Optional<CredencialAdmin> admin() {
        return admin;
    }

    /** O valor do ambiente tem prioridade; em branco vira {@code null}. */
    private static String valor(String ambiente, String arquivo) {
        String escolhido = ambiente != null && !ambiente.isBlank() ? ambiente : arquivo;
        if (escolhido == null) {
            return null;
        }
        String limpo = escolhido.strip();
        return limpo.isEmpty() ? null : limpo;
    }
}
