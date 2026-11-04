package br.unicesumar.chat.persistence.memory;

import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.MensagemRepositoryApagarContrato;

class MensagemRepositoryEmMemoriaApagarTest extends MensagemRepositoryApagarContrato {
    @Override
    protected MensagemRepository criarRepositorio() {
        return new MensagemRepositoryEmMemoria();
    }
}
