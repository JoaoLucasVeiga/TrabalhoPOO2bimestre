package br.unicesumar.chat.persistence.memory;

import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.MensagemRepositoryBuscaContrato;

class MensagemRepositoryEmMemoriaBuscaTest extends MensagemRepositoryBuscaContrato {
    @Override
    protected MensagemRepository criarRepositorio() {
        return new MensagemRepositoryEmMemoria();
    }
}
