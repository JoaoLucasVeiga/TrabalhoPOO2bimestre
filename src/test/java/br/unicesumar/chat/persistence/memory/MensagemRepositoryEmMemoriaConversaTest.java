package br.unicesumar.chat.persistence.memory;

import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.MensagemRepositoryConversaContrato;

class MensagemRepositoryEmMemoriaConversaTest extends MensagemRepositoryConversaContrato {
    @Override
    protected MensagemRepository criarRepositorio() {
        return new MensagemRepositoryEmMemoria();
    }
}
