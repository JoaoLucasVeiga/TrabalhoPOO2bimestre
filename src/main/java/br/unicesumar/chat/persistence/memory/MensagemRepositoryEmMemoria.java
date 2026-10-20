package br.unicesumar.chat.persistence.memory;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.persistence.MensagemRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Repositório em memória, thread-safe, usado nos testes unitários. */
public class MensagemRepositoryEmMemoria implements MensagemRepository {

    private final List<Mensagem> mensagens = new ArrayList<>();
    private long proximoId = 1;

    /** {@inheritDoc} Atribui ids sequenciais. */
    @Override
    public synchronized Mensagem salvar(Mensagem mensagem) {
        Objects.requireNonNull(mensagem, "mensagem");
        Mensagem salva = mensagem.comId(proximoId++);
        mensagens.add(salva);
        return salva;
    }

    /** {@inheritDoc} */
    @Override
    public synchronized List<Mensagem> buscarUltimas(String sala, int quantidade) {
        if (quantidade < 0) {
            throw new IllegalArgumentException("A quantidade não pode ser negativa.");
        }
        List<Mensagem> publicas = mensagens.stream()
                .filter(m -> !m.ehPrivada() && m.sala().equals(sala))
                .toList();
        int inicio = Math.max(0, publicas.size() - quantidade);
        return new ArrayList<>(publicas.subList(inicio, publicas.size()));
    }
}
