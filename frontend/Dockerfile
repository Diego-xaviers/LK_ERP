# Build do painel e entrega pelo Caddy — a imagem final não tem Node.
FROM node:20-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .

# A URL da API entra no bundle em tempo de build (é assim que o Vite funciona),
# por isso vem como argumento e não como variável de runtime.
ARG VITE_API_URL=/api
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# Caddy em vez de nginx: ele emite e renova o certificado HTTPS sozinho.
FROM caddy:2-alpine
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv
EXPOSE 80 443
