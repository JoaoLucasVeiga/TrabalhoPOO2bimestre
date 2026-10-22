package br.unicesumar.chat.persistence.memory;

import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.MensagemRepositoryContrato;

class MensagemRepositoryEmMemoriaTest extends MensagemRepositoryContrato {
    @Override
    protected MensagemRepository criarRepositorio() {
        return new MensagemRepositoryEmMemoria();
    }
}
