package br.unicesumar.chat.server;

import br.unicesumar.chat.model.Usuario;
import br.unicesumar.chat.protocol.Evento;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Dono da coleção compartilhada de clientes conectados. Toda sincronização
 * da lista fica aqui: a coleção só é tocada dentro de {@code synchronized(lock)}
 * e quem percorre os clientes trabalha sobre uma cópia, então nunca ocorre
 * {@code ConcurrentModificationException}. A escrita na rede acontece fora do lock.
 */
public class ClientRegistry {

    private final Object lock = new Object();
    private final List<ClienteHandler> clientes = new ArrayList<>();

    /** Registra um cliente. Registrar o mesmo handler duas vezes não duplica. */
    public void adicionar(ClienteHandler cliente) {
        Objects.requireNonNull(cliente, "cliente");
        synchronized (lock) {
            if (!clientes.contains(cliente)) {
                clientes.add(cliente);
            }
        }
    }

    /** Remove um cliente; devolve {@code false} se ele não estava registrado. */
    public boolean remover(ClienteHandler cliente) {
        synchronized (lock) {
            return clientes.remove(cliente);
        }
    }

    /** Devolve uma cópia imutável dos clientes atuais (segura para percorrer). */
    public List<ClienteHandler> listar() {
        synchronized (lock) {
            return List.copyOf(clientes);
        }
    }

    /** Quantidade de clientes registrados. */
    public int quantidade() {
        synchronized (lock) {
            return clientes.size();
        }
    }

    /** Busca o cliente identificado com o nome, sem diferenciar maiúsculas. */
    public Optional<ClienteHandler> buscarPorUsuario(String nome) {
        if (nome == null) {
            return Optional.empty();
        }
        synchronized (lock) {
            return clientes.stream()
                    .filter(c -> c.usuario().map(u -> u.nome().equalsIgnoreCase(nome)).orElse(false))
                    .findFirst();
        }
    }

    /**
     * Associa um usuário a um cliente registrado, só se nenhum outro cliente já
     * usa o nome. A verificação e a atribuição acontecem sob o mesmo lock, para
     * duas threads não conseguirem o mesmo nome.
     *
     * @return {@code false} se outro cliente já usa o nome
     * @throws IllegalArgumentException se o cliente não está registrado
     */
    public boolean identificar(ClienteHandler cliente, Usuario usuario) {
        synchronized (lock) {
            if (!clientes.contains(cliente)) {
                throw new IllegalArgumentException("O cliente não está registrado.");
            }
            boolean emUso = clientes.stream()
                    .anyMatch(c -> c != cliente && c.usuario().map(usuario::equals).orElse(false));
            if (emUso) {
                return false;
            }
            cliente.definirUsuario(usuario);
            return true;
        }
    }

    /** Envia o evento a todos os clientes. Veja {@link #transmitir(Evento, Predicate)}. */
    public int transmitir(Evento evento) {
        return transmitir(evento, c -> true);
    }

    /**
     * Envia o evento aos clientes aprovados pelo filtro. Percorre uma cópia da
     * lista; clientes cuja escrita falha são encerrados e removidos, sem impedir
     * a entrega aos demais.
     *
     * @return quantos clientes receberam
     */
    public int transmitir(Evento evento, Predicate<ClienteHandler> filtro) {
        int entregues = 0;
        List<ClienteHandler> falhas = new ArrayList<>();
        for (ClienteHandler cliente : listar()) {
            if (!filtro.test(cliente)) {
                continue;
            }
            if (cliente.enviar(evento)) {
                entregues++;
            } else {
                falhas.add(cliente);
            }
        }
        for (ClienteHandler cliente : falhas) {
            remover(cliente);
            cliente.encerrar();
        }
        return entregues;
    }

    /** Encerra todas as conexões e esvazia o registro (usado ao parar o servidor). */
    public void encerrarTodos() {
        List<ClienteHandler> todos;
        synchronized (lock) {
            todos = List.copyOf(clientes);
            clientes.clear();
        }
        todos.forEach(ClienteHandler::encerrar);
    }
}
