package br.unicesumar.chat.server;

import br.unicesumar.chat.model.Sala;
import java.util.List;
import java.util.Objects;

/**
 * Regras de salas: entrar, voltar para a geral e listar as ativas. A sala de cada
 * cliente fica no {@link ClienteHandler} e a troca (histórico e avisos) é feita pelo
 * {@link ChatService}, sob o mesmo lock de envio, para não perder nem duplicar mensagens.
 * Uma sala existe enquanto tem alguém nela; a "geral" sempre existe.
 */
public class SalaService {

    private final ChatService chat;
    private final ClientRegistry registry;

    /** Recebe os serviços por injeção. */
    public SalaService(ChatService chat, ClientRegistry registry) {
        this.chat = Objects.requireNonNull(chat, "chat");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * Entra na sala (criando-a se estiver vazia). O nome vira minúsculo.
     *
     * @throws IllegalArgumentException se o nome for inválido ou o cliente já estiver nela
     */
    public void entrarSala(ClienteHandler cliente, String nome) {
        chat.mudarDeSala(cliente, nome);
    }

    /**
     * Sai da sala atual e volta para a "geral".
     *
     * @throws IllegalArgumentException se o cliente já está na "geral"
     */
    public void sairSala(ClienteHandler cliente) {
        if (cliente.sala().equals(Sala.PADRAO)) {
            throw new IllegalArgumentException("Você já está na sala " + Sala.PADRAO
                    + ". Use /join sala para entrar em outra sala.");
        }
        chat.mudarDeSala(cliente, Sala.PADRAO);
    }

    /** Salas ativas (com alguém), mais a "geral", em ordem alfabética. */
    public List<String> listarSalas() {
        return registry.salasAtivas();
    }
}
