package br.unicesumar.chat.server;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.protocol.Evento;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Regras principais do chat: valida, persiste e transmite mensagens. Depende só
 * da interface {@link MensagemRepository}; não conhece SQL nem sockets.
 */
public class ChatService {

    private final MensagemRepository repositorio;
    private final ClientRegistry registry;
    private final Clock relogio;
    /** Garante que salvar + transmitir é uma etapa só: todos veem a mesma ordem. */
    private final Object ordem = new Object();

    /** Recebe as dependências por injeção. */
    public ChatService(MensagemRepository repositorio, ClientRegistry registry, Clock relogio) {
        this.repositorio = Objects.requireNonNull(repositorio, "repositorio");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.relogio = Objects.requireNonNull(relogio, "relogio");
    }

    /**
     * Valida, salva e transmite uma mensagem pública a todos os clientes
     * identificados na sala (inclusive o remetente).
     *
     * @return a mensagem salva, com id
     * @throws IllegalArgumentException se a mensagem for inválida (nada é salvo nem enviado)
     */
    public Mensagem enviarMensagem(Usuario remetente, String sala, String texto) {
        Mensagem nova = Mensagem.nova(remetente, sala, texto, relogio);
        synchronized (ordem) {
            Mensagem salva = repositorio.salvar(nova);
            registry.transmitir(Evento.Msg.de(salva),
                    c -> c.usuario().isPresent() && c.sala().equals(salva.sala()));
            return salva;
        }
    }

    /** Devolve as últimas mensagens públicas da sala, da mais antiga para a mais recente. */
    public List<Mensagem> buscarHistorico(String sala, int quantidade) {
        return repositorio.buscarUltimas(sala, quantidade);
    }
}
