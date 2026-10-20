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

    /** Recebe os serviços por injeção. */
    public ProcessadorChat(ChatService chat, ClientRegistry registry) {
        this.chat = Objects.requireNonNull(chat, "chat");
        Objects.requireNonNull(registry, "registry");
    }

    /** Pede o nome de usuário ao cliente que acabou de conectar. */
    @Override
    public void aoConectar(ClienteHandler origem) {
        origem.enviar(new Evento.Sistema(
                "Bem-vindo ao chat! Digite o seu nome de usuário para entrar."));
    }

    /** Identifica o cliente (primeira linha) ou envia a linha como mensagem. */
    @Override
    public void aoReceber(ClienteHandler origem, String linha) {
        try {
            if (origem.usuario().isEmpty()) {
                identificar(origem, linha.strip());
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
