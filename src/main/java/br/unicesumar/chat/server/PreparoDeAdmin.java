package br.unicesumar.chat.server;

import br.unicesumar.chat.infra.ConfiguracaoServidor.CredencialAdmin;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Prepara o administrador na partida do servidor, a partir da configuração (sem nada fixo no código). */
public final class PreparoDeAdmin {

    private PreparoDeAdmin() {
    }

    /**
     * Garante a conta do administrador configurado e devolve quem é administrador.
     *
     * @param admin conta configurada, se houver
     * @param auth  serviço de login, ou {@code null} no modo sem login (sem restrição)
     * @param aviso recebe os avisos para o log (nunca contêm a senha)
     * @return com login: só o admin configurado, ou ninguém se não houver configuração; sem login: sem restrição
     * @throws IllegalArgumentException se a senha ou o nome configurados forem inválidos
     */
    public static Administradores preparar(Optional<CredencialAdmin> admin, AuthService auth,
            Consumer<String> aviso) {
        Objects.requireNonNull(admin, "admin");
        Objects.requireNonNull(aviso, "aviso");
        if (auth == null) {
            return Administradores.semRestricao();
        }
        if (admin.isEmpty()) {
            aviso.accept("Nenhum administrador configurado: /exportar, /kick e /limpar ficam bloqueados. "
                    + "Para configurar, copie servidor.properties.exemplo para servidor.properties, "
                    + "preencha e reinicie o servidor.");
            return Administradores.nenhum();
        }
        CredencialAdmin credencial = admin.get();
        if (auth.garantirConta(credencial.nome(), credencial.senha())) {
            aviso.accept("Conta do administrador '" + credencial.nome() + "' criada.");
        } else {
            aviso.accept("A conta do administrador '" + credencial.nome()
                    + "' já existe; a senha não foi alterada.");
        }
        return Administradores.de(List.of(credencial.nome()));
    }
}
