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

    /** Quantas mensagens recentes o cliente recebe ao entrar na sala. */
    public static final int HISTORICO_INICIAL = 20;

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

    /**
     * Identifica o cliente e lhe envia o histórico recente da sala (se houver) e o
     * OK de boas-vindas. Buscar o histórico, identificar e enviar acontecem sob o
     * mesmo lock de {@link #enviarMensagem}: nenhuma mensagem fica entre o
     * histórico e o broadcast ao vivo, então nada se perde nem chega duplicado.
     *
     * @throws IllegalArgumentException se o nome já está em uso ou o cliente não está registrado
     */
    public void entrar(ClienteHandler cliente, Usuario usuario) {
        synchronized (ordem) {
            List<Evento.Msg> historico = repositorio
                    .buscarUltimas(cliente.sala(), HISTORICO_INICIAL).stream()
                    .map(Evento.Msg::de).toList();
            if (!registry.identificar(cliente, usuario)) {
                throw new IllegalArgumentException(
                        "O nome '" + usuario.nome() + "' já está em uso. Escolha outro nome.");
            }
            if (!historico.isEmpty()) {
                cliente.enviar(new Evento.Historico(historico));
            }
            cliente.enviar(new Evento.Ok(
                    "Você entrou como " + usuario.nome() + " na sala " + cliente.sala() + "."));
            avisarSala(cliente, usuario.nome() + " entrou na sala " + cliente.sala() + ".");
        }
    }

    /**
     * Avisa quem está na sala que o cliente saiu. Deve ser chamado depois de o
     * cliente ser removido do registro. Cliente que nunca se identificou não gera aviso.
     */
    public void sair(ClienteHandler cliente) {
        cliente.usuario().ifPresent(usuario -> {
            synchronized (ordem) {
                avisarSala(cliente, usuario.nome() + " saiu da sala " + cliente.sala() + ".");
            }
        });
    }

    private void avisarSala(ClienteHandler origem, String aviso) {
        registry.transmitir(new Evento.Sistema(aviso),
                c -> c != origem && c.usuario().isPresent() && c.sala().equals(origem.sala()));
    }

    /** Devolve as últimas mensagens públicas da sala, da mais antiga para a mais recente. */
    public List<Mensagem> buscarHistorico(String sala, int quantidade) {
        return repositorio.buscarUltimas(sala, quantidade);
    }
}
