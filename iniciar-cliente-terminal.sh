#!/bin/sh
cd "$(dirname "$0")" || exit 1
sh ./preparar.sh || exit 1
exec java -cp target/chat-multiusuario-1.0.0.jar br.unicesumar.chat.ClienteTerminalMain localhost 5000
