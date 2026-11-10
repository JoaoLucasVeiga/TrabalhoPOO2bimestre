# Perguntas possíveis sobre concorrência

Respostas curtas, para a defesa.

**1. O que é uma condição de corrida? Onde ela apareceria no chat?**
Duas threads mexem no mesmo dado ao mesmo tempo e o resultado depende de quem chega primeiro. No chat, aconteceria na lista de clientes: um cliente entrando enquanto outra thread percorre a lista.

**2. Como você protegeu a lista de clientes?**
`ClientRegistry` usa um lock privado em todo acesso. Quem precisa percorrer a lista (broadcast) trabalha em uma cópia, para não segurar o lock enquanto escreve nos sockets.

**3. Por que copiar a lista antes de percorrer?**
Evita `ConcurrentModificationException` e evita segurar o lock durante escrita de rede, que pode demorar.

**4. Duas pessoas enviam ao mesmo tempo. Todos veem a mesma ordem?**
Sim. As mensagens públicas passam por uma fila e uma única thread consumidora (`SequenciadorDeMensagens`) grava e transmite. A ordem gravada no banco é a ordem entregue a todos. Veja também a pergunta 17.

**5. Isso não vira um gargalo?**
Era, quando cada mensagem era gravada sozinha dentro do lock. Hoje a gravação é feita em lote por uma thread só: as mensagens que chegam enquanto o banco trabalha esperam juntas e são gravadas numa transação. Na carga de 50 clientes por socket, o tempo caiu de cerca de 3 s para 0,6 a 1 s. A ordem igual para todos continua garantida.

**6. Um cliente lento pode travar os outros?**
Não. Cada cliente tem fila de saída e thread de escrita. O broadcast só enfileira. Se a fila passa de 256 KB, o cliente é desconectado.

**7. Várias threads escrevendo no mesmo `PrintWriter`?**
`enviar` é `synchronized` e a escrita real é feita por uma única thread por cliente (a fila), então as linhas não se misturam.

**8. Para que servem `volatile` e `AtomicBoolean` no projeto?**
`volatile` garante que a mudança de um campo (por exemplo `ativo`, `sala`) seja vista por todas as threads. `AtomicBoolean` garante que o fechamento da conexão aconteça uma única vez.

**9. Pode haver deadlock?**
Não encontrei. A ordem dos locks é sempre `ChatService.ordem`, depois `ClientRegistry.lock` ou o monitor do cliente. Ninguém chama de volta na direção contrária, e os locks de log e exportação não seguram outros locks.

**10. Dois usuários tentam o mesmo nome ao mesmo tempo.**
Verificar e atribuir é uma operação só no `ClientRegistry`. No cadastro, o banco tem `UNIQUE` e o `INSERT OR IGNORE` é atômico.

**11. E o banco de dados com várias threads?**
Nunca compartilho `Connection`: cada operação abre e fecha a sua. O SQLite usa WAL (leitores não bloqueiam o escritor) e `busy_timeout` de 5 segundos.

**12. O que acontece se um cliente cai de repente?**
A leitura ou a escrita falha, o `ClienteHandler` encerra a conexão uma única vez, o cliente sai do registro e os outros recebem o aviso. O servidor continua.

**13. Por que uma thread por cliente e não um pool fixo?**
É o modelo pedido e é simples: a leitura bloqueia, então cada conexão precisa da sua thread. Uso um pool em cache de threads daemon. Para milhares de clientes valeria usar NIO.

**14. Como você testou concorrência sem `Thread.sleep`?**
Com `CountDownLatch` para largar as threads juntas, `join` e `awaitTermination` para esperar. Os testes usam 8 threads e `@RepeatedTest(10)`.

**15. Mostre a condição de corrida de verdade.**
`CorridaDeDados`: sem sincronização o total ficou entre 1,19 e 1,99 milhão de 2 milhões. Com `synchronized` e `AtomicLong` fechou 2.000.000 sempre.

**16. Qual risco ainda existe?**
A thread consumidora é única: se o banco ficar muito lento, a fila cresce e quem envia espera (no máximo 60 s, depois recebe um erro pedindo para tentar de novo). Também a senha sem TLS, que é segurança e não concorrência.

**17. Onde está o produtor-consumidor no projeto?**
No `SequenciadorDeMensagens`. As threads dos clientes são as produtoras: colocam a mensagem numa fila (`ConcurrentLinkedQueue`) e esperam o resultado. Uma única thread consumidora tira até 100 mensagens da fila, grava todas numa transação (`salvarTodas`) e transmite na ordem de gravação. Quem enviou só recebe a resposta depois de a mensagem estar gravada e transmitida; se o banco falhar, recebe o erro.

**18. Por que gravar em lote?**
Cada gravação no SQLite custa uma transação. Com um lote, o custo é dividido entre várias mensagens. Se o lote inteiro falhar, o sequenciador tenta cada mensagem sozinha, para que uma mensagem com problema não derrube as outras.

**19. A thread consumidora fica rodando o tempo todo?**
Não. Ela é criada quando chega mensagem e termina depois de 2 s ociosa. É uma thread daemon, então não impede o servidor de encerrar.

**20. O que é o heartbeat e por que ele existe?**
Sem ele, um cliente que some sem fechar a conexão (cabo desligado, rede caiu) continuaria na lista como online, porque o servidor só percebe a queda quando tenta escrever. O `MonitorDeConexoes` manda um `PING` a todos a cada 15 s. O cliente responde `/pong` sozinho. Quem fica 45 s (três PING) sem enviar nada é desconectado, sai do registro e os outros recebem o aviso.

**21. O heartbeat atrapalha as outras threads?**
Não. O monitor roda numa thread daemon própria e só enfileira o PING na fila de saída de cada cliente, como qualquer broadcast. Um cliente lento não trava o monitor, nem os demais.
