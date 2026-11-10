package br.unicesumar.chat.persistence.memory;

import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.MensagemRepositorySalvarTodasContrato;

class MensagemRepositoryEmMemoriaSalvarTodasTest extends MensagemRepositorySalvarTodasContrato {
    @Override
    protected MensagemRepository criarRepositorio() {
        return new MensagemRepositoryEmMemoria();
    }
}
