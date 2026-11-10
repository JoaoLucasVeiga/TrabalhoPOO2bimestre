package br.unicesumar.chat.server;

import br.unicesumar.chat.infra.ExportadorHistorico;
import br.unicesumar.chat.protocol.Evento;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Identifica o comando digitado (linhas que começam com {@code /}) e o entrega ao grupo de comandos
 * responsável. Não há sequência de if/else: os comandos ficam numa {@link TabelaDeComandos} montada no
 * construtor e nunca mais alterada, por isso a leitura por várias threads é segura. Cada assunto tem a
 * sua classe ({@link ComandosDeConta}, {@link ComandosDeSala}, {@link ComandosDeConsulta},
 * {@link ComandosDeConversa}, {@link ComandosDeAdministracao}). Não guarda estado de conexão.
 */
public class CommandService {

    /** Maior quantidade aceita em {@code /history}. */
    public static final int HISTORICO_MAXIMO = 100;

    /** Máximo de mensagens gravadas por {@code /exportar} (as mais recentes). */
    public static final int EXPORTACAO_MAXIMA = 1000;

    /** Comandos permitidos a quem ainda não entrou (só no modo com autenticação). */
    private static final Set<String> SEM_LOGIN = Set.of("help", "quit", "register", "login");

    private final AuthService auth;
    private final TabelaDeComandos tabela = new TabelaDeComandos();

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
        this(chat, registry, salas, auth, exportador, Administradores.semRestricao());
    }

    /**
     * Recebe todos os serviços e a lista de administradores. Com restrição, {@code /exportar},
     * {@code /kick} e {@code /limpar} só funcionam para administradores; sem restrição, os comandos
     * se comportam como antes (todos podem exportar e {@code /kick} e {@code /limpar} não existem).
     *
     * @param auth            serviço de autenticação, ou {@code null} para o modo sem login
     * @param exportador      grava o {@code /exportar}, ou {@code null} para não oferecer o comando
     * @param administradores quem pode usar os comandos de administração
     */
    public CommandService(ChatService chat, ClientRegistry registry, SalaService salas,
            AuthService auth, ExportadorHistorico exportador, Administradores administradores) {
        Objects.requireNonNull(chat, "chat");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(salas, "salas");
        Objects.requireNonNull(administradores, "administradores");
        this.auth = auth;
        ComandosBasicos basicos = new ComandosBasicos(tabela);
        if (auth != null) {
            new ComandosDeConta(chat, registry, auth, administradores).registrarEm(tabela);
        }
        basicos.registrarAjuda(tabela);
        new ComandosDeConsulta(chat, registry).registrarEm(tabela);
        new ComandosDeConversa(chat).registrarEm(tabela);
        new ComandosDeSala(salas).registrarEm(tabela);
        new ComandosDeAdministracao(chat, registry, auth, exportador, administradores).registrarEm(tabela);
        basicos.registrarSaida(tabela);
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
        int espaco = ArgumentosDeComando.indiceDoEspaco(corpo);
        String nome = espaco < 0 ? corpo : corpo.substring(0, espaco);
        String argumentos = espaco < 0 ? "" : corpo.substring(espaco).strip();
        if (nome.isEmpty()) {
            origem.enviar(new Evento.Erro(
                    "Digite o nome do comando depois da barra. Use /help para ver a lista."));
            return;
        }
        var entrada = tabela.buscar(nome);
        if (entrada.isEmpty()) {
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
            entrada.get().comando().executar(origem, argumentos);
        } catch (IllegalArgumentException e) {
            origem.enviar(new Evento.Erro(e.getMessage()));
        }
    }
}
