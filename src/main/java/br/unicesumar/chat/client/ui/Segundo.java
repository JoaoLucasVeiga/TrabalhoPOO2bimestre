package br.unicesumar.chat.client.ui;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

/**
 * Executa trabalho de rede fora da thread da interface (EDT) usando {@link SwingWorker}: a tarefa
 * roda em segundo plano e o resultado, ou a falha, volta sempre na EDT.
 */
public final class Segundo {

    private Segundo() {
    }

    /**
     * Roda a tarefa em segundo plano.
     *
     * @param aoConcluir recebe o resultado, na EDT
     * @param aoFalhar   recebe a causa original da falha, na EDT
     */
    public static <T> void plano(Callable<T> tarefa, Consumer<T> aoConcluir, Consumer<Throwable> aoFalhar) {
        Objects.requireNonNull(tarefa, "tarefa");
        Objects.requireNonNull(aoConcluir, "aoConcluir");
        Objects.requireNonNull(aoFalhar, "aoFalhar");
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return tarefa.call();
            }

            @Override
            protected void done() {
                try {
                    aoConcluir.accept(get());
                } catch (ExecutionException e) {
                    aoFalhar.accept(e.getCause() == null ? e : e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    aoFalhar.accept(e);
                }
            }
        }.execute();
    }

    /** Versão para tarefas sem resultado. */
    public static void plano(Runnable tarefa, Runnable aoConcluir, Consumer<Throwable> aoFalhar) {
        Objects.requireNonNull(tarefa, "tarefa");
        Objects.requireNonNull(aoConcluir, "aoConcluir");
        plano(() -> {
            tarefa.run();
            return null;
        }, resultado -> aoConcluir.run(), aoFalhar);
    }
}
