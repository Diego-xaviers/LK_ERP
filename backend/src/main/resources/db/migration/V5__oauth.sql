-- Discord OAuth: identificador estável do usuário no Discord
ALTER TABLE usuarios ADD COLUMN IF NOT EXISTS discord_id VARCHAR(20) UNIQUE;
