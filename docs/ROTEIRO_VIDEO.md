# Roteiro do vídeo (até 3 minutos)

Preparação: apagar `chat.db`, abrir três terminais para os clientes (Ana, Bruno e Carla), um terminal para o servidor e uma janela com `mvn test` já executado. Falar devagar e mostrar a tela.

A ordem segue a rubrica.

## 0:00 a 0:20 — Apresentação

- "Chat multiusuário em Java: servidor com sockets, histórico em SQLite por JDBC."
- Mostrar a árvore de pacotes no editor.

## 0:20 a 1:00 — Servidor com vários clientes (1,3)

- Subir o servidor: `java -cp target/chat-multiusuario-1.0.0.jar br.unicesumar.chat.ServidorMain`.
- Conectar Ana, Bruno e Carla (`/register nome senha`). Mostrar os avisos de entrada nos três.
- Rodar `/users` em um deles: aparecem os três.
- Mostrar `Servidor.java` (laço de accept) e `ClienteHandler implements Runnable`: "uma thread por cliente".

## 1:00 a 1:35 — Broadcast (1,2)

- Ana, Bruno e Carla enviam mensagens quase ao mesmo tempo.
- Mostrar que os três terminais veem as mensagens na mesma ordem, no formato `[HH:mm] nome: texto`.
- Mandar uma privada: `/msg bruno oi`. Só o Bruno vê.
- Fechar o terminal da Carla de repente (Ctrl+C): o servidor continua e os outros recebem o aviso de saída.
- Mostrar `ClientRegistry.transmitir` (lock e cópia da lista).

## 1:35 a 2:10 — Persistência JDBC (1,0)

- Parar o servidor e subir de novo.
- Reconectar a Ana: o histórico aparece.
- Mostrar `JdbcMensagemRepository`: `PreparedStatement`, try-with-resources.
- Mostrar `sql/criar_tabelas.sql`.

## 2:10 a 2:30 — Pacotes (0,5)

- No editor: `model`, `persistence`, `server`, `client`.
- Dizer: "o model não conhece rede nem banco; o ChatService depende da interface, e o JDBC é injetado no main".

## 2:30 a 2:55 — Testes (0,5)

- Rodar `mvn test` (ou mostrar a saída já pronta): `BUILD SUCCESS` nos testes de concorrência e unitários.
- Abrir `ChatServiceConcorrenciaTest`: 8 threads, mesma ordem para todos.

## 2:55 a 3:00 — Fecho

- "Código, README, relatório e revisões estão no repositório."
