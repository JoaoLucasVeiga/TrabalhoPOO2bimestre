package br.unicesumar.chat.server;

import br.unicesumar.chat.protocol.Evento;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

    private static final Pattern QUANTIDADE = Pattern.compile("\\d{1,4}");

    private record Registro(String uso, String descricao, Comando comando) {
    }

    private final ChatService chat;
    private final ClientRegistry registry;
    private final Map<String, Registro> comandos = new LinkedHashMap<>();

    /** Recebe os serviços por injeção e registra os comandos básicos. */
    public CommandService(ChatService chat, ClientRegistry registry) {
        this.chat = Objects.requireNonNull(chat, "chat");
        this.registry = Objects.requireNonNull(registry, "registry");
        registrar("help", "/help", "Mostra esta lista de comandos.", this::ajuda);
        registrar("users", "/users", "Lista os usuários online na sua sala.", this::usuarios);
        registrar("history", "/history [quantidade]",
                "Mostra as últimas mensagens da sala (de 1 a " + HISTORICO_MAXIMO + ").",
                this::historico);
        registrar("quit", "/quit", "Sai do chat.", this::sair);
    }

    private void registrar(String nome, String uso, String descricao, Comando comando) {
        comandos.put(nome, new Registro(uso, descricao, comando));
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
            origem.enviar(new Evento.Sistema("Não há mensagens anteriores nesta sala."));
        } else {
            origem.enviar(new Evento.Historico(mensagens));
        }
    }

    private static IllegalArgumentException usoDoHistorico() {
        return new IllegalArgumentException("Uso: /history [quantidade], com a quantidade de 1 a "
                + HISTORICO_MAXIMO + ". Exemplo: /history 10");
    }

    private void sair(ClienteHandler origem, String argumentos) {
        origem.enviar(new Evento.Ok("Até logo!"));
        origem.encerrar();
    }
}
