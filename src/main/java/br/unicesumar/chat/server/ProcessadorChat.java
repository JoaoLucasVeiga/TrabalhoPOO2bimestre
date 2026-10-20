package br.unicesumar.chat.server;

import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.RepositorioException;
import br.unicesumar.chat.protocol.Evento;
import java.util.Objects;

/**
 * Liga a camada de rede ({@link ProcessadorEntrada}) às regras do chat. Enquanto
 * a autenticação não existe, a primeira linha de cada cliente é o nome de usuário;
 * depois disso, cada linha é uma mensagem. Compartilhado entre threads, mas sem
 * estado próprio: o estado fica no {@link ClientRegistry}.
 */
public class ProcessadorChat implements ProcessadorEntrada {

    private final ChatService chat;
    private final CommandService comandos;

    /** Cria o processador com o {@link CommandService} padrão (comandos básicos). */
    public ProcessadorChat(ChatService chat, ClientRegistry registry) {
        this(chat, new CommandService(chat, registry));
    }

    /** Recebe os serviços por injeção. */
    public ProcessadorChat(ChatService chat, CommandService comandos) {
        this.chat = Objects.requireNonNull(chat, "chat");
        this.comandos = Objects.requireNonNull(comandos, "comandos");
    }

    /** Pede o nome de usuário ao cliente que acabou de conectar. */
    @Override
    public void aoConectar(ClienteHandler origem) {
        origem.enviar(new Evento.Sistema(
                "Bem-vindo ao chat! Digite o seu nome de usuário para entrar."));
    }

    /** Identifica o cliente (primeira linha), executa um comando ({@code /...}) ou envia uma mensagem. */
    @Override
    public void aoReceber(ClienteHandler origem, String linha) {
        try {
            if (origem.usuario().isEmpty()) {
                identificar(origem, linha.strip());
            } else if (CommandService.ehComando(linha)) {
                comandos.executar(origem, linha);
            } else {
                chat.enviarMensagem(origem.usuario().get(), origem.sala(), linha);
            }
        } catch (IllegalArgumentException | RepositorioException e) {
            origem.enviar(new Evento.Erro(e.getMessage()));
        }
    }

    private void identificar(ClienteHandler origem, String nome) {
        chat.entrar(origem, new Usuario(nome));
    }
}
