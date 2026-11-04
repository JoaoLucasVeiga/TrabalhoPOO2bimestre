package br.unicesumar.chat.persistence.memory;

import br.unicesumar.chat.model.Mensagem;
import br.unicesumar.chat.persistence.MensagemRepository;
import br.unicesumar.chat.persistence.ValidacaoBusca;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.function.Predicate;
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

    /** {@inheritDoc} */
    @Override
    public synchronized int apagarSala(String sala) {
        if (sala == null || sala.isBlank()) {
            throw new IllegalArgumentException("Informe a sala a limpar.");
        }
        int antes = mensagens.size();
        mensagens.removeIf(m -> m.destinatario().isEmpty() && m.sala().equals(sala));
        return antes - mensagens.size();
    }

    /** {@inheritDoc} */
    @Override
    public synchronized List<Mensagem> buscarPorUsuario(String sala, String remetente, int limite) {
        ValidacaoBusca.exigirSala(sala);
        ValidacaoBusca.exigirTexto(remetente, "O nome do usuário");
        ValidacaoBusca.exigirLimite(limite);
        return ultimasQue(sala, limite, m -> m.remetente().nome().equalsIgnoreCase(remetente));
    }

    /** {@inheritDoc} */
    @Override
    public synchronized List<Mensagem> buscarPorTexto(String sala, String trecho, int limite) {
        ValidacaoBusca.exigirSala(sala);
        ValidacaoBusca.exigirTexto(trecho, "O trecho de texto");
        ValidacaoBusca.exigirLimite(limite);
        String procurado = trecho.toLowerCase(Locale.ROOT);
        return ultimasQue(sala, limite, m -> m.conteudo().toLowerCase(Locale.ROOT).contains(procurado));
    }

    /** {@inheritDoc} */
    @Override
    public synchronized List<Mensagem> buscarPorPeriodo(String sala, LocalDateTime inicio,
            LocalDateTime fim, int limite) {
        ValidacaoBusca.exigirSala(sala);
        ValidacaoBusca.exigirPeriodo(inicio, fim);
        ValidacaoBusca.exigirLimite(limite);
        return ultimasQue(sala, limite, m -> !m.horario().isBefore(inicio) && !m.horario().isAfter(fim));
    }

    private List<Mensagem> ultimasQue(String sala, int limite, Predicate<Mensagem> criterio) {
        List<Mensagem> achadas = mensagens.stream()
                .filter(m -> !m.ehPrivada() && m.sala().equals(sala))
                .filter(criterio)
                .sorted(Comparator.comparing(Mensagem::horario)) // estável: empate fica na ordem de gravação
                .toList();
        int inicio = Math.max(0, achadas.size() - limite);
        return new ArrayList<>(achadas.subList(inicio, achadas.size()));
    }

    /** {@inheritDoc} */
    @Override
    public synchronized List<Mensagem> buscarConversa(String a, String b, int quantidade) {
        exigirNome(a, "O primeiro usuário");
        exigirNome(b, "O segundo usuário");
        if (quantidade < 0) {
            throw new IllegalArgumentException("A quantidade não pode ser negativa.");
        }
        List<Mensagem> conversa = mensagens.stream()
                .filter(m -> m.ehPrivada() && entre(m, a, b))
                .toList();
        int inicio = Math.max(0, conversa.size() - quantidade);
        return new ArrayList<>(conversa.subList(inicio, conversa.size()));
    }

    /** {@inheritDoc} */
    @Override
    public synchronized List<String> listarConversas(String usuario) {
        exigirNome(usuario, "O usuário");
        Map<String, String> nomePorChave = new LinkedHashMap<>();
        for (int i = mensagens.size() - 1; i >= 0; i--) { // da mais recente para a mais antiga
            Mensagem m = mensagens.get(i);
            if (!m.ehPrivada()) {
                continue;
            }
            String remetente = m.remetente().nome();
            String destinatario = m.destinatario().orElseThrow().nome();
            String outro;
            if (remetente.equalsIgnoreCase(usuario)) {
                outro = destinatario;
            } else if (destinatario.equalsIgnoreCase(usuario)) {
                outro = remetente;
            } else {
                continue;
            }
            nomePorChave.putIfAbsent(outro.toLowerCase(Locale.ROOT), outro);
        }
        return new ArrayList<>(nomePorChave.values());
    }

    private static boolean entre(Mensagem m, String a, String b) {
        String remetente = m.remetente().nome();
        String destinatario = m.destinatario().orElseThrow().nome();
        return remetente.equalsIgnoreCase(a) && destinatario.equalsIgnoreCase(b)
                || remetente.equalsIgnoreCase(b) && destinatario.equalsIgnoreCase(a);
    }

    private static void exigirNome(String nome, String quem) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException(quem + " é obrigatório.");
        }
    }
}
