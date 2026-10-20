package br.unicesumar.chat.persistence.memory;

import br.unicesumar.chat.persistence.UsuarioRepository;
import br.unicesumar.chat.persistence.UsuarioRepositoryContrato;

class UsuarioRepositoryEmMemoriaTest extends UsuarioRepositoryContrato {
    @Override
    protected UsuarioRepository criarRepositorio() {
        return new UsuarioRepositoryEmMemoria();
    }
}
