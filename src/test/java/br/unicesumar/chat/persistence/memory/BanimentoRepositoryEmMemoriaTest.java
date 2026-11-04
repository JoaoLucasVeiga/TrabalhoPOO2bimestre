package br.unicesumar.chat.persistence.memory;

import br.unicesumar.chat.persistence.BanimentoRepository;
import br.unicesumar.chat.persistence.BanimentoRepositoryContrato;

class BanimentoRepositoryEmMemoriaTest extends BanimentoRepositoryContrato {
    @Override
    protected BanimentoRepository criarRepositorio() {
        return new BanimentoRepositoryEmMemoria();
    }
}
