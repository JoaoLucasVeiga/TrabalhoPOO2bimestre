package br.unicesumar.chat.server;

import static br.unicesumar.chat.server.ArgumentosDeComando.indiceDoEspaco;

import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.protocol.Evento;
import java.util.Objects;

/** Entrada no chat com senha: {@code /register} e {@code /login}. Só existe no modo com autenticação. */
final class ComandosDeConta implements GrupoDeComandos {

    private final ChatService chat;
    private final ClientRegistry registry;
    private final AuthService auth;
    private final Administradores administradores;

    ComandosDeConta(ChatService chat, ClientRegistry registry, AuthService auth, Administradores administradores) {
        this.chat = Objects.requireNonNull(chat, "chat");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.auth = Objects.requireNonNull(auth, "auth");
        this.administradores = Objects.requireNonNull(administradores, "administradores");
    }

    @Override
    public void registrarEm(TabelaDeComandos tabela) {
        tabela.registrar("register", "/register nome senha", "Cria uma conta e entra no chat.", this::criarConta);
        tabela.registrar("login", "/login nome senha", "Entra no chat com uma conta existente.",
                this::entrarComConta);
    }

    private void criarConta(ClienteHandler origem, String argumentos) {
        exigirSemLogin(origem);
        String[] dados = nomeESenha(argumentos, "/register nome senha");
        Usuario usuario = auth.registrar(dados[0], dados[1]);
        chat.entrar(origem, usuario);
        informarPapel(origem, usuario);
    }

    private void entrarComConta(ClienteHandler origem, String argumentos) {
        exigirSemLogin(origem);
        String[] dados = nomeESenha(argumentos, "/login nome senha");
        Usuario usuario = auth.autenticar(dados[0], dados[1]);
        if (registry.buscarPorUsuario(usuario.nome()).isPresent()) {
            throw new IllegalArgumentException("O usuário '" + usuario.nome()
                    + "' já está conectado em outra sessão. Feche a outra sessão e tente de novo.");
        }
        chat.entrar(origem, usuario);
        informarPapel(origem, usuario);
    }

    /** Em servidor com restrição, diz ao cliente se ele é administrador (a interface esconde o resto). */
    private void informarPapel(ClienteHandler origem, Usuario usuario) {
        if (administradores.restrito()) {
            origem.enviar(new Evento.Papel(administradores.ehAdmin(usuario.nome())));
        }
    }

    private static void exigirSemLogin(ClienteHandler origem) {
        origem.usuario().ifPresent(u -> {
            throw new IllegalArgumentException("Você já está conectado como " + u.nome()
                    + ". Use /quit para sair e entrar com outra conta.");
        });
    }

    /** Separa "nome senha com espaços" em nome e senha (a senha é o resto da linha). */
    private static String[] nomeESenha(String argumentos, String uso) {
        int espaco = indiceDoEspaco(argumentos);
        String nome = espaco < 0 ? argumentos : argumentos.substring(0, espaco);
        String senha = espaco < 0 ? "" : argumentos.substring(espaco).strip();
        if (nome.isEmpty() || senha.isEmpty()) {
            throw new IllegalArgumentException("Uso: " + uso + ". Exemplo: " + uso.replace("nome", "ana")
                    .replace("senha", "minhasenha"));
        }
        return new String[] {nome, senha};
    }
}
