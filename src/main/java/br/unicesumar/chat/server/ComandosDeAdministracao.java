package br.unicesumar.chat.server;

import static br.unicesumar.chat.server.ArgumentosDeComando.indiceDoEspaco;
import static br.unicesumar.chat.server.ArgumentosDeComando.nomeDe;
import static br.unicesumar.chat.server.ArgumentosDeComando.umNome;

import br.unicesumar.chat.infra.ExportadorHistorico;
import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.protocol.Evento;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Comandos que só o administrador usa: {@code /exportar}, {@code /kick}, {@code /limpar},
 * {@code /ban}, {@code /unban} e {@code /bans}. Sem restrição de administradores, só o
 * {@code /exportar} existe e vale para todos (comportamento do modo sem login).
 */
final class ComandosDeAdministracao implements GrupoDeComandos {

    private static final Logger LOG = Logger.getLogger(ComandosDeAdministracao.class.getName());

    private final ChatService chat;
    private final ClientRegistry registry;
    private final AuthService auth;
    private final ExportadorHistorico exportador;
    private final Administradores administradores;

    ComandosDeAdministracao(ChatService chat, ClientRegistry registry, AuthService auth,
            ExportadorHistorico exportador, Administradores administradores) {
        this.chat = Objects.requireNonNull(chat, "chat");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.auth = auth;
        this.exportador = exportador;
        this.administradores = Objects.requireNonNull(administradores, "administradores");
    }

    @Override
    public void registrarEm(TabelaDeComandos tabela) {
        String soAdmin = administradores.restrito() ? " (somente administrador)" : "";
        if (exportador != null) {
            tabela.registrar("exportar", "/exportar",
                    "Grava o histórico da sala atual em um arquivo .txt no servidor." + soAdmin, this::exportar);
        }
        if (administradores.restrito() && auth != null && auth.suportaBanimento()) {
            tabela.registrar("ban", "/ban usuario",
                    "Bane um usuário: remove do chat e impede de entrar (somente administrador).", this::banir);
            tabela.registrar("unban", "/unban usuario", "Tira o banimento de um usuário (somente administrador).",
                    this::desbanir);
            tabela.registrar("bans", "/bans", "Lista os usuários banidos (somente administrador).",
                    this::listarBanidos);
        }
        if (administradores.restrito()) {
            tabela.registrar("kick", "/kick usuario", "Remove um usuário do chat (somente administrador).",
                    this::remover);
            tabela.registrar("limpar", "/limpar", "Apaga o histórico da sala atual (somente administrador).",
                    this::limpar);
        }
    }

    /** Recusa o comando se quem o pediu não for administrador. */
    private void exigirAdmin(ClienteHandler origem, String comando) {
        if (!administradores.ehAdmin(nomeDe(origem))) {
            throw new IllegalArgumentException("Somente o administrador pode usar " + comando
                    + ". Peça a quem administra o servidor.");
        }
    }

    private void exportar(ClienteHandler origem, String argumentos) {
        exigirAdmin(origem, "/exportar");
        if (!argumentos.isEmpty()) {
            throw new IllegalArgumentException(
                    "Uso: /exportar (sem argumentos). O histórico da sua sala atual é gravado em um arquivo .txt.");
        }
        List<Mensagem> mensagens = chat.buscarHistorico(origem.sala(), CommandService.EXPORTACAO_MAXIMA);
        if (mensagens.isEmpty()) {
            throw new IllegalArgumentException("Não há mensagens para exportar nesta sala.");
        }
        try {
            Path arquivo = exportador.exportar(origem.sala(),
                    mensagens.stream().map(Mensagem::formatar).toList());
            String limite = mensagens.size() == CommandService.EXPORTACAO_MAXIMA
                    ? " (limitado às últimas " + CommandService.EXPORTACAO_MAXIMA + " mensagens)" : "";
            origem.enviar(new Evento.Ok("Histórico exportado para o arquivo " + arquivo.getFileName()
                    + " na pasta de exportações do servidor" + limite + "."));
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Falha ao exportar o histórico: " + e.getMessage(), e);
            throw new IllegalArgumentException("Não foi possível gravar o arquivo de exportação. "
                    + "Avise quem administra o servidor para verificar o espaço em disco e as permissões.");
        }
    }

    private void remover(ClienteHandler origem, String argumentos) {
        exigirAdmin(origem, "/kick");
        if (argumentos.isEmpty() || indiceDoEspaco(argumentos) >= 0) {
            throw new IllegalArgumentException("Uso: /kick usuario. Exemplo: /kick ana");
        }
        String meuNome = nomeDe(origem);
        if (argumentos.equalsIgnoreCase(meuNome)) {
            throw new IllegalArgumentException("Você não pode remover a si mesmo. Use /quit para sair.");
        }
        if (administradores.ehAdmin(argumentos)) {
            throw new IllegalArgumentException("Não é possível remover outro administrador.");
        }
        ClienteHandler alvo = registry.buscarPorUsuario(argumentos).orElseThrow(() ->
                new IllegalArgumentException("O usuário '" + argumentos
                        + "' não está online. Use /users para ver quem está conectado."));
        alvo.enviar(new Evento.Erro("Você foi removido do chat pelo administrador " + meuNome + "."));
        alvo.encerrar();
        origem.enviar(new Evento.Ok("O usuário " + argumentos + " foi removido do chat."));
    }

    private void banir(ClienteHandler origem, String argumentos) {
        exigirAdmin(origem, "/ban");
        String nome = umNome(argumentos, "/ban usuario. Exemplo: /ban ana");
        String meuNome = nomeDe(origem);
        if (nome.equalsIgnoreCase(meuNome)) {
            throw new IllegalArgumentException("Você não pode banir a si mesmo.");
        }
        if (administradores.ehAdmin(nome)) {
            throw new IllegalArgumentException("Não é possível banir um administrador.");
        }
        if (!auth.banir(nome)) {
            throw new IllegalArgumentException("O usuário '" + nome
                    + "' já está banido. Use /bans para ver a lista.");
        }
        Optional<ClienteHandler> alvo = registry.buscarPorUsuario(nome);
        alvo.ifPresent(a -> {
            a.enviar(new Evento.Erro("Você foi banido pelo administrador " + meuNome + "."));
            a.encerrar();
        });
        origem.enviar(new Evento.Ok("O usuário " + nome + " foi banido"
                + (alvo.isPresent() ? " e removido do chat." : ".")));
    }

    private void desbanir(ClienteHandler origem, String argumentos) {
        exigirAdmin(origem, "/unban");
        String nome = umNome(argumentos, "/unban usuario. Exemplo: /unban ana");
        if (!auth.desbanir(nome)) {
            throw new IllegalArgumentException("O usuário '" + nome
                    + "' não está banido. Use /bans para ver a lista.");
        }
        origem.enviar(new Evento.Ok("O usuário " + nome + " pode entrar de novo."));
    }

    private void listarBanidos(ClienteHandler origem, String argumentos) {
        exigirAdmin(origem, "/bans");
        List<String> banidos = auth.listarBanidos();
        origem.enviar(new Evento.Sistema(banidos.isEmpty() ? "Nenhum usuário banido."
                : "Banidos (" + banidos.size() + "): " + String.join(", ", banidos)));
    }

    private void limpar(ClienteHandler origem, String argumentos) {
        exigirAdmin(origem, "/limpar");
        if (!argumentos.isEmpty()) {
            throw new IllegalArgumentException(
                    "Uso: /limpar (sem argumentos). O histórico da sua sala atual é apagado.");
        }
        int apagadas = chat.limparSala(origem.sala(), nomeDe(origem));
        origem.enviar(new Evento.Ok(apagadas + (apagadas == 1 ? " mensagem apagada" : " mensagens apagadas")
                + " da sala " + origem.sala() + "."));
    }
}
