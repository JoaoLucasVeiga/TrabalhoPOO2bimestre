# Perguntas possíveis sobre concorrência

Respostas curtas, para a defesa.

**1. O que é uma condição de corrida? Onde ela apareceria no chat?**
Duas threads mexem no mesmo dado ao mesmo tempo e o resultado depende de quem chega primeiro. No chat, aconteceria na lista de clientes: um cliente entrando enquanto outra thread percorre a lista.

**2. Como você protegeu a lista de clientes?**
`ClientRegistry` usa um lock privado em todo acesso. Quem precisa percorrer a lista (broadcast) trabalha em uma cópia, para não segurar o lock enquanto escreve nos sockets.

**3. Por que copiar a lista antes de percorrer?**
Evita `ConcurrentModificationException` e evita segurar o lock durante escrita de rede, que pode demorar.

**4. Duas pessoas enviam ao mesmo tempo. Todos veem a mesma ordem?**
Sim. `ChatService` serializa "salvar e transmitir" com um lock. A ordem gravada no banco é a ordem entregue a todos.

**5. Isso não vira um gargalo?**
Pode, porque a gravação fica dentro do lock. Foi decisão consciente: a ordem igual para todos é o requisito. O custo está registrado na revisão.

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
A gravação dentro do lock de ordem e a falta de keep-alive. Também a senha sem TLS, que é segurança e não concorrência.
