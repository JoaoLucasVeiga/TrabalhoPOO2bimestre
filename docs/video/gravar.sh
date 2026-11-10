#!/bin/bash
# Regrava o vídeo de demonstração (docs/video/demonstracao.mp4) sem tela física.
# Precisa de: Java 21, ffmpeg, Xvfb (xvfb-run) e do projeto compilado (./mvnw -q test-compile).
# Uso (na raiz do projeto):  xvfb-run -a -s "-screen 0 2160x1250x24" bash docs/video/gravar.sh
set -e
RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$RAIZ"
TMP="$(mktemp -d)"
./mvnw -B -q test-compile dependency:build-classpath -Dmdep.outputFile="$TMP/cp.txt" -Dmdep.includeScope=test
CP="$RAIZ/target/test-classes:$RAIZ/target/classes:$(cat "$TMP/cp.txt")"
ffmpeg -y -loglevel error -f x11grab -framerate 12 -video_size 2160x1250 -i "$DISPLAY" \
  -vf scale=1920:-2 -c:v libx264 -preset veryfast -crf 30 -pix_fmt yuv420p "$TMP/demonstracao.mp4" &
FF=$!
sleep 1
java -Dfile.encoding=UTF-8 -cp "$CP" br.unicesumar.chat.demo.Gravacao "$TMP/trabalho"
kill -INT "$FF"; wait "$FF" || true
mv "$TMP/demonstracao.mp4" "$RAIZ/docs/video/demonstracao.mp4"
rm -rf "$TMP"
echo "Gravado: docs/video/demonstracao.mp4"
