package br.unicesumar.chat.server;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.memory.MensagemRepositoryEmMemoria;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Repositório de teste: guarda em memória e registra TUDO que foi salvo (inclusive privadas). */
final class RepositorioEspiao implements MensagemRepository {

    private final MensagemRepositoryEmMemoria interno = new MensagemRepositoryEmMemoria();
    private final List<Mensagem> salvas = Collections.synchronizedList(new ArrayList<>());

    List<Mensagem> salvas() {
        synchronized (salvas) {
            return new ArrayList<>(salvas);
        }
    }

    @Override
    public Mensagem salvar(Mensagem mensagem) {
        Mensagem salva = interno.salvar(mensagem);
        salvas.add(salva);
        return salva;
    }

    @Override
    public List<Mensagem> buscarUltimas(String sala, int quantidade) {
        return interno.buscarUltimas(sala, quantidade);
    }

    @Override
    public List<Mensagem> buscarPorUsuario(String sala, String remetente, int limite) {
        return interno.buscarPorUsuario(sala, remetente, limite);
    }

    @Override
    public List<Mensagem> buscarPorTexto(String sala, String trecho, int limite) {
        return interno.buscarPorTexto(sala, trecho, limite);
    }

    @Override
    public List<Mensagem> buscarPorPeriodo(String sala, LocalDateTime inicio, LocalDateTime fim,
            int limite) {
        return interno.buscarPorPeriodo(sala, inicio, fim, limite);
    }
}
