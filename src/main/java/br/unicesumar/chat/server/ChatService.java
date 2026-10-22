package br.unicesumar.chat.server;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.protocol.Evento;
import br.unicesumar.chat.protocol.Mensagens;
import br.unicesumar.chat.model.Sala;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * Regras principais do chat: valida, persiste e transmite mensagens. Depende só
 * da interface {@link MensagemRepository}; não conhece SQL nem sockets.
 */
public class ChatService {

    /** Quantas mensagens recentes o cliente recebe ao entrar na sala. */
    public static final int HISTORICO_INICIAL = 20;

    /** Máximo de resultados de uma busca (as mais recentes). */
    public static final int BUSCA_MAXIMA = 50;

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
            cliente.enviar(new Evento.Ok(Mensagens.entrouComo(usuario.nome(), cliente.sala())));
            avisarSala(cliente, Mensagens.avisoDeEntrada(usuario.nome(), cliente.sala()));
        }
    }

    /**
     * Avisa quem está na sala que o cliente saiu. Deve ser chamado depois de o
     * cliente ser removido do registro. Cliente que nunca se identificou não gera aviso.
     */
    public void sair(ClienteHandler cliente) {
        cliente.usuario().ifPresent(usuario -> {
            synchronized (ordem) {
                avisarSala(cliente, Mensagens.avisoDeSaida(usuario.nome(), cliente.sala()));
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

    /**
     * Envia uma mensagem privada: valida, localiza o destinatário online, persiste,
     * entrega só a ele e confirma ao remetente com o mesmo evento. Se o destinatário
     * está offline nada é persistido. Se ele cair entre a localização e a entrega, o
     * remetente recebe erro e o registro fica gravado, sem ser exibido a ninguém.
     *
     * @return a mensagem salva, com id
     * @throws IllegalArgumentException se o remetente não se identificou, o destinatário
     *                                  não está online ou a mensagem for inválida
     */
    public Mensagem enviarPrivada(ClienteHandler origem, String nomeDestinatario, String texto) {
        Usuario remetente = origem.usuario().orElseThrow(() -> new IllegalArgumentException(
                "Informe o seu nome de usuário antes de enviar mensagens."));
        String procurado = nomeDestinatario == null ? "" : nomeDestinatario.strip();
        ClienteHandler destino = registry.buscarPorUsuario(procurado).orElseThrow(
                () -> new IllegalArgumentException("O usuário '" + procurado + "' não está online. "
                        + "Use /users para ver quem está na sala e tente de novo."));
        Usuario destinatario = destino.usuario().orElseThrow();
        Mensagem nova = Mensagem.privada(remetente, destinatario, origem.sala(), texto,
                LocalDateTime.now(relogio));
        Mensagem salva = repositorio.salvar(nova);
        Evento.Privada evento = new Evento.Privada(remetente.nome(), destinatario.nome(),
                salva.horario(), salva.conteudo());
        if (!destino.enviar(evento)) {
            registry.remover(destino);
            destino.encerrar();
            throw new IllegalArgumentException("O usuário '" + destinatario.nome()
                    + "' saiu do chat antes de receber a mensagem.");
        }
        origem.enviar(evento);
        return salva;
    }

    /**
     * Muda o cliente de sala: busca o histórico da nova sala, avisa a sala antiga,
     * troca, envia o histórico e o OK ao cliente e avisa a nova sala. Tudo sob o
     * lock de envio, como em {@link #entrar}.
     *
     * @throws IllegalArgumentException se o nome for inválido, o cliente não estiver
     *                                  identificado ou já estiver na sala
     */
    public void mudarDeSala(ClienteHandler cliente, String novaSala) {
        String nova = new Sala(novaSala).nome();
        Usuario usuario = cliente.usuario().orElseThrow(() -> new IllegalArgumentException(
                "Informe o seu nome de usuário antes de trocar de sala."));
        synchronized (ordem) {
            String antiga = cliente.sala();
            if (antiga.equals(nova)) {
                throw new IllegalArgumentException("Você já está na sala " + nova + ".");
            }
            List<Evento.Msg> historico = repositorio.buscarUltimas(nova, HISTORICO_INICIAL).stream()
                    .map(Evento.Msg::de).toList();
            avisarSala(cliente, Mensagens.avisoDeSaida(usuario.nome(), antiga));
            cliente.definirSala(nova);
            if (!historico.isEmpty()) {
                cliente.enviar(new Evento.Historico(historico));
            }
            cliente.enviar(new Evento.Ok(Mensagens.entrouNaSala(nova)));
            avisarSala(cliente, Mensagens.avisoDeEntrada(usuario.nome(), nova));
        }
    }

    /**
     * Busca mensagens públicas de um usuário na sala (as {@link #BUSCA_MAXIMA} mais recentes).
     *
     * @throws IllegalArgumentException se o nome estiver vazio
     */
    public List<Mensagem> buscarPorUsuario(String sala, String nome) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Informe o nome do usuário. Uso: /buscar usuario <nome>");
        }
        return repositorio.buscarPorUsuario(sala, nome.strip(), BUSCA_MAXIMA);
    }

    /**
     * Busca mensagens públicas da sala que contêm o trecho.
     *
     * @throws IllegalArgumentException se o trecho estiver vazio
     */
    public List<Mensagem> buscarPorTexto(String sala, String trecho) {
        if (trecho == null || trecho.isBlank()) {
            throw new IllegalArgumentException("Informe o texto a buscar. Uso: /buscar texto <trecho>");
        }
        return repositorio.buscarPorTexto(sala, trecho.strip(), BUSCA_MAXIMA);
    }

    /**
     * Busca mensagens públicas da sala entre dois dias, incluindo o dia inteiro do fim.
     *
     * @throws IllegalArgumentException se faltar data ou o início for depois do fim
     */
    public List<Mensagem> buscarPorPeriodo(String sala, LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null || inicio.isAfter(fim)) {
            throw new IllegalArgumentException("Período inválido: o início deve ser até o fim. "
                    + "Uso: /buscar periodo <AAAA-MM-DD> <AAAA-MM-DD>");
        }
        return repositorio.buscarPorPeriodo(sala, inicio.atStartOfDay(), fim.atTime(LocalTime.MAX),
                BUSCA_MAXIMA);
    }
}
