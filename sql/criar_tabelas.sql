-- Script de criação das tabelas do Chat Multiusuário (SQLite).
-- O programa executa este mesmo script ao iniciar (ConnectionFactory).

CREATE TABLE IF NOT EXISTS usuario (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    nome       TEXT NOT NULL UNIQUE COLLATE NOCASE,
    senha_hash TEXT NOT NULL,
    salt       TEXT NOT NULL
);

-- destinatario NULL = mensagem pública; horario em ISO-8601.
CREATE TABLE IF NOT EXISTS mensagem (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    remetente    TEXT NOT NULL,
    destinatario TEXT,
    sala         TEXT NOT NULL,
    conteudo     TEXT NOT NULL,
    horario      TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_mensagem_sala ON mensagem (sala, id);
