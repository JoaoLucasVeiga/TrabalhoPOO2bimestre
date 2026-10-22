package br.unicesumar.chat.server;

import br.unicesumar.chat.infra.ExportadorHistorico;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.protocol.Mensagens;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Identifica o comando digitado (linhas que começam com {@code /}) e o entrega
 * ao serviço responsável. Não há sequência de if/else: os comandos ficam numa
 * tabela montada no construtor e nunca mais alterada, por isso a leitura por
 * várias threads é segura. Não guarda estado de conexão.
 */
public class CommandService {

    /** Maior quantidade aceita em {@code /history}. */
    public static final int HISTORICO_MAXIMO = 100;

    /** Máximo de mensagens gravadas por {@code /exportar} (as mais recentes). */
    public static final int EXPORTACAO_MAXIMA = 1000;

    private static final Logger LOG = Logger.getLogger(CommandService.class.getName());

    /** Comandos permitidos a quem ainda não entrou (só no modo com autenticação). */
    private static final Set<String> SEM_LOGIN = Set.of("help", "quit", "register", "login");

    private static final Pattern QUANTIDADE = Pattern.compile("\\d{1,4}");

    private record Registro(String uso, String descricao, Comando comando) {
    }

    private final ChatService chat;
    private final ClientRegistry registry;
    private final SalaService salas;
    private final AuthService auth;
    private final ExportadorHistorico exportador;
    private final Map<String, Registro> comandos = new LinkedHashMap<>();

    /** Cria o serviço sem autenticação e com o {@link SalaService} padrão. */
    public CommandService(ChatService chat, ClientRegistry registry) {
        this(chat, registry, new SalaService(chat, registry), null);
    }

    /** Cria o serviço sem autenticação, com o {@link SalaService} informado. */
    public CommandService(ChatService chat, ClientRegistry registry, SalaService salas) {
        this(chat, registry, salas, null);
    }

    /** Cria o serviço com autenticação obrigatória e o {@link SalaService} padrão. */
    public CommandService(ChatService chat, ClientRegistry registry, AuthService auth) {
        this(chat, registry, new SalaService(chat, registry), Objects.requireNonNull(auth, "auth"));
    }

    /**
     * Recebe os serviços por injeção e registra os comandos.
     *
     * @param auth serviço de autenticação, ou {@code null} para o modo sem login
     */
    public CommandService(ChatService chat, ClientRegistry registry, SalaService salas,
            AuthService auth) {
        this(chat, registry, salas, auth, null);
    }

    /**
     * Recebe todos os serviços por injeção e registra os comandos.
     *
     * @param auth       serviço de autenticação, ou {@code null} para o modo sem login
     * @param exportador grava o {@code /exportar}, ou {@code null} para não oferecer o comando
     */
    public CommandService(ChatService chat, ClientRegistry registry, SalaService salas,
            AuthService auth, ExportadorHistorico exportador) {
        this.chat = Objects.requireNonNull(chat, "chat");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.salas = Objects.requireNonNull(salas, "salas");
        this.auth = auth;
        this.exportador = exportador;
        if (auth != null) {
            registrar("register", "/register nome senha", "Cria uma conta e entra no chat.",
                    this::criarConta);
            registrar("login", "/login nome senha", "Entra no chat com uma conta existente.",
                    this::entrarComConta);
        }
        registrar("help", "/help", "Mostra esta lista de comandos.", this::ajuda);
        registrar("users", "/users", "Lista os usuários online na sua sala.", this::usuarios);
        registrar("history", "/history [quantidade]",
                "Mostra as últimas mensagens da sala (de 1 a " + HISTORICO_MAXIMO + ").",
                this::historico);
        registrar("msg", "/msg usuario texto", "Envia uma mensagem privada a um usuário online.",
                this::privada);
        registrar("buscar", "/buscar usuario|texto|periodo ...",
                "Busca no histórico da sala (ex.: /buscar texto java).", this::buscar);
        registrar("join", "/join sala", "Entra em uma sala (cria se ainda não existir).", this::entrarSala);
        registrar("leave", "/leave", "Sai da sala atual e volta para a geral.", this::sairSala);
        registrar("rooms", "/rooms", "Lista as salas ativas.", this::listarSalas);
        if (exportador != null) {
            registrar("exportar", "/exportar", "Grava o histórico da sala atual em um arquivo .txt no servidor.",
                    this::exportar);
        }
        registrar("quit", "/quit", "Sai do chat.", this::sair);
    }

    private void registrar(String nome, String uso, String descricao, Comando comando) {
        comandos.put(nome, new Registro(uso, descricao, comando));
    }

    /** Indica se é preciso entrar (com /login ou /register) antes de usar o chat. */
    public boolean exigeAutenticacao() {
        return auth != null;
    }

    /** Indica se a linha é um comando (começa com {@code /}, ignorando espaços iniciais). */
    public static boolean ehComando(String linha) {
        return linha != null && linha.stripLeading().startsWith("/");
    }

    /**
     * Executa o comando da linha. Comando desconhecido ou argumentos inválidos geram
     * {@code ERRO} ao cliente.
     *
     * @throws IllegalArgumentException se a linha não começa com {@code /}
     */
    public void executar(ClienteHandler origem, String linha) {
        if (!ehComando(linha)) {
            throw new IllegalArgumentException("Um comando começa com '/'. Use /help para ver a lista.");
        }
        String corpo = linha.strip().substring(1);
        int espaco = indiceDoEspaco(corpo);
        String nome = espaco < 0 ? corpo : corpo.substring(0, espaco);
        String argumentos = espaco < 0 ? "" : corpo.substring(espaco).strip();
        if (nome.isEmpty()) {
            origem.enviar(new Evento.Erro(
                    "Digite o nome do comando depois da barra. Use /help para ver a lista."));
            return;
        }
        Registro registro = comandos.get(nome.toLowerCase(Locale.ROOT));
        if (registro == null) {
            origem.enviar(new Evento.Erro("Comando desconhecido: /" + nome
                    + ". Digite /help para ver os comandos disponíveis."));
            return;
        }
        if (auth != null && origem.usuario().isEmpty() && !SEM_LOGIN.contains(nome.toLowerCase(Locale.ROOT))) {
            origem.enviar(new Evento.Erro("Você precisa entrar antes de usar o chat. "
                    + "Use /login nome senha ou crie uma conta com /register nome senha."));
            return;
        }
        try {
            registro.comando().executar(origem, argumentos);
        } catch (IllegalArgumentException e) {
            origem.enviar(new Evento.Erro(e.getMessage()));
        }
    }

    private static int indiceDoEspaco(String texto) {
        for (int i = 0; i < texto.length(); i++) {
            if (Character.isWhitespace(texto.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private void ajuda(ClienteHandler origem, String argumentos) {
        StringBuilder sb = new StringBuilder("Comandos disponíveis:");
        for (Registro r : comandos.values()) {
            sb.append('\n').append(r.uso()).append(" - ").append(r.descricao());
        }
        origem.enviar(new Evento.Sistema(sb.toString()));
    }

    private void usuarios(ClienteHandler origem, String argumentos) {
        origem.enviar(new Evento.Usuarios(origem.sala(), registry.usuariosNaSala(origem.sala())));
    }

    private void historico(ClienteHandler origem, String argumentos) {
        int quantidade = ChatService.HISTORICO_INICIAL;
        if (!argumentos.isEmpty()) {
            if (!QUANTIDADE.matcher(argumentos).matches()) {
                throw usoDoHistorico();
            }
            quantidade = Integer.parseInt(argumentos);
            if (quantidade < 1 || quantidade > HISTORICO_MAXIMO) {
                throw usoDoHistorico();
            }
        }
        List<Evento.Msg> mensagens = chat.buscarHistorico(origem.sala(), quantidade).stream()
                .map(Evento.Msg::de).toList();
        if (mensagens.isEmpty()) {
            origem.enviar(new Evento.Sistema(Mensagens.SEM_HISTORICO));
        } else {
            origem.enviar(new Evento.Historico(mensagens));
        }
    }

    private static IllegalArgumentException usoDoHistorico() {
        return new IllegalArgumentException("Uso: /history [quantidade], com a quantidade de 1 a "
                + HISTORICO_MAXIMO + ". Exemplo: /history 10");
    }

    private void criarConta(ClienteHandler origem, String argumentos) {
        exigirSemLogin(origem);
        String[] dados = nomeESenha(argumentos, "/register nome senha");
        Usuario usuario = auth.registrar(dados[0], dados[1]);
        chat.entrar(origem, usuario);
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

    private void exportar(ClienteHandler origem, String argumentos) {
        if (!argumentos.isEmpty()) {
            throw new IllegalArgumentException(
                    "Uso: /exportar (sem argumentos). O histórico da sua sala atual é gravado em um arquivo .txt.");
        }
        List<Mensagem> mensagens = chat.buscarHistorico(origem.sala(), EXPORTACAO_MAXIMA);
        if (mensagens.isEmpty()) {
            throw new IllegalArgumentException("Não há mensagens para exportar nesta sala.");
        }
        try {
            Path arquivo = exportador.exportar(origem.sala(),
                    mensagens.stream().map(Mensagem::formatar).toList());
            String limite = mensagens.size() == EXPORTACAO_MAXIMA
                    ? " (limitado às últimas " + EXPORTACAO_MAXIMA + " mensagens)" : "";
            origem.enviar(new Evento.Ok("Histórico exportado para o arquivo " + arquivo.getFileName()
                    + " na pasta de exportações do servidor" + limite + "."));
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Falha ao exportar o histórico: " + e.getMessage(), e);
            throw new IllegalArgumentException("Não foi possível gravar o arquivo de exportação. "
                    + "Avise quem administra o servidor para verificar o espaço em disco e as permissões.");
        }
    }

    private void privada(ClienteHandler origem, String argumentos) {
        int espaco = indiceDoEspaco(argumentos);
        String nome = espaco < 0 ? argumentos : argumentos.substring(0, espaco);
        String texto = espaco < 0 ? "" : argumentos.substring(espaco).strip();
        if (nome.isEmpty() || texto.isEmpty()) {
            throw new IllegalArgumentException("Uso: /msg usuario texto. Exemplo: /msg ana Olá!");
        }
        chat.enviarPrivada(origem, nome, texto);
    }

    private void buscar(ClienteHandler origem, String argumentos) {
        String[] palavras = argumentos.isEmpty() ? new String[0] : argumentos.split("\\s+");
        if (palavras.length < 2) {
            throw usoDaBusca();
        }
        String tipo = palavras[0].toLowerCase(Locale.ROOT);
        String resto = argumentos.substring(palavras[0].length()).strip();
        List<Mensagem> achadas = switch (tipo) {
            case "usuario" -> palavras.length == 2 ? chat.buscarPorUsuario(origem.sala(), palavras[1])
                    : semResultado();
            case "texto" -> chat.buscarPorTexto(origem.sala(), resto);
            case "periodo" -> palavras.length == 3
                    ? chat.buscarPorPeriodo(origem.sala(), data(palavras[1]), data(palavras[2]))
                    : semResultado();
            default -> semResultado();
        };
        List<Evento.Msg> mensagens = achadas.stream().map(Evento.Msg::de).toList();
        if (mensagens.isEmpty()) {
            origem.enviar(new Evento.Sistema(Mensagens.SEM_RESULTADO_DE_BUSCA));
        } else {
            origem.enviar(new Evento.Historico(mensagens));
        }
    }

    private static List<Mensagem> semResultado() {
        throw usoDaBusca();
    }

    private static LocalDate data(String texto) {
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            throw usoDaBusca();
        }
    }

    private static IllegalArgumentException usoDaBusca() {
        return new IllegalArgumentException("Uso: /buscar usuario <nome> | /buscar texto <trecho> | "
                + "/buscar periodo <AAAA-MM-DD> <AAAA-MM-DD>. Exemplo: /buscar periodo 2026-10-01 2026-10-31");
    }

    private void entrarSala(ClienteHandler origem, String argumentos) {
        if (argumentos.isEmpty()) {
            throw new IllegalArgumentException("Uso: /join sala. Exemplo: /join java");
        }
        salas.entrarSala(origem, argumentos);
    }

    private void sairSala(ClienteHandler origem, String argumentos) {
        salas.sairSala(origem);
    }

    private void listarSalas(ClienteHandler origem, String argumentos) {
        origem.enviar(new Evento.Salas(salas.listarSalas()));
    }

    private void sair(ClienteHandler origem, String argumentos) {
        origem.enviar(new Evento.Ok("Até logo!"));
        origem.encerrar();
    }
}
