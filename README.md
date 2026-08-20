# LK Transportes — Painel Logístico

Plataforma de imersão para a transportadora virtual LK, que opera no
Euro Truck Simulator 2 (mapa RBR).

> Todos os documentos gerados são **fictícios, para roleplay**. Não há
> integração com SEFAZ nem qualquer validade fiscal.

## Stack
- **Frontend**: React 18 + TypeScript + Vite, CSS puro
- **Backend**: Java 21 + Spring Boot 3.3 (JPA, Validation)
- **Banco**: H2 em arquivo no desenvolvimento · PostgreSQL em produção

---

## Como rodar

### 1. Backend
Precisa de **Java 21**. O Maven vem junto pelo wrapper — não precisa instalar.
```bash
cd backend
./mvnw spring-boot:run
```
(No Windows, `mvnw spring-boot:run`. Quem já tem Maven pode usar `mvn` direto.)
Sobe em `http://localhost:8080`.

A cada boot ele semeia dados de desenvolvimento (2 usuários, 3 caminhões,
2 carretas, 3 postos, 2 oficinas, 3 empresas e 2 avisos).

> O perfil `dev` usa H2 **em memória** com `ddl-auto: create-drop`: o banco é
> recriado do zero a cada restart e **os dados não sobrevivem**. Os ids também
> mudam a cada boot — inclusive os tokens de telemetria.

Console do banco: `http://localhost:8080/h2-console`
(JDBC URL `jdbc:h2:mem:lktransportes`, usuário `sa`, senha vazia)

### 2. Frontend
Precisa de **Node 18+**.
```bash
cd frontend
npm install
npm run dev
```
Abre em `http://localhost:5173`.

> O frontend precisa do backend no ar. Se ele estiver desligado, a tela
> mostra "Não foi possível falar com o servidor".

---

## Fluxo completo (o que já funciona ponta a ponta)

1. **Nova viagem** → preenche rota, carga e equipamento → grava no banco
2. Aparece **"Viagem #NNNN criada"** com duas ações:
   - **Gerar documentação** → cria NF, CT-e e MDF-e e leva para a tela deles
   - **Iniciar viagem** → gera os documentos e coloca a viagem em andamento
3. **Viagem atual** (Modo Viagem) → registra eventos durante a viagem:
   - Abastecimento (com posto cadastrado, cálculo automático do total e
     **assinatura desenhada** — obrigatória)
   - Manutenção (oficina cadastrada + serviço em texto livre)
   - Pedágio, Multa (motivo livre, sem catálogo) e Ocorrência
4. A timeline e o total de despesas atualizam a cada evento
5. **Finalizar viagem** → registra observação e avaria, encerra
6. **Minhas viagens** → histórico com timeline completa, resumo financeiro
   por categoria e os documentos vinculados
7. **Documentos** → escolhe a viagem e vê NF / CT-e / MDF-e, com
   **Imprimir / PDF** (usa a impressão do navegador)

### As telas de etapa

Emitir documento, entrar numa demanda, entregar, liberar uma viagem, pagar um
acerto e emitir CNH passam por um overlay que mostra o que está acontecendo —
"Emitindo Nota Fiscal", "Emitindo CT-e", "Emitindo MDF-e" — com barra e
mensagem final. É ritmo, não espera: cada etapa dura 0,7s.

Duas regras que o `Processo` (`components/ui/Processo.tsx`) respeita, e que são o
motivo de ele existir em vez de um `setTimeout` solto:

- **A chamada real roda junto com a animação, e o sucesso só aparece quando as
  duas terminam.** Se a API recusar, o overlay corta na hora para o erro. Nunca
  se anuncia um documento que o servidor não emitiu.
- **Se o servidor demorar mais que a animação, a barra segura em 92%** na última
  etapa, em vez de fingir que acabou.

A chamada fica presa a um `ref` e dispara uma vez só. Sem isso o `StrictMode`
(que em desenvolvimento monta o componente duas vezes) criaria duas viagens e
pagaria dois acertos.

---

Regra ativa: **um motorista só pode ter uma viagem em andamento por vez**.
Tentar criar outra devolve erro explicando qual está aberta.

---

## Cadastros (menu Administração)
Motoristas, caminhões, carretas, postos, oficinas, empresas parceiras e avisos.
Os avisos aparecem no mural do painel — marcar "fixar" coloca em destaque no topo.

Em **Motoristas** dá para criar, editar (nome, e-mail, papel e troca de senha),
aprovar, bloquear/reativar e remover. Quem é cadastrado por ali já entra
aprovado — diferente de quem se cadastra sozinho pelo `/auth/cadastro`, que
fica pendente. Remover um motorista que já tem viagens é recusado com aviso.

---

## Acesso e privacidade

O login é por e-mail e senha e devolve um JWT válido por 7 dias, guardado no
navegador. Todo endpoint exige o token — as únicas exceções são `/auth/**` e
`/telemetria/ping` (esse é chamado pelo agente, que se identifica pelo token
próprio dele).

Contas semeadas em desenvolvimento: `admin@lk.com` (gestor) e
`motorista@lk.com` (motorista), senha `123456` nas duas.

**A regra é: cada motorista vê os próprios dados e nada dos outros.**
Quem decide isso é o `SessaoAtual.exigirDonoOuGestor(...)`, chamado em todo
endpoint que recebe um id na URL. Trocar o id no endereço devolve 403.

| | Motorista | Gestor |
|---|---|---|
| Própria viagem, telemetria, documentos | sim | sim |
| Viagem/telemetria de outro motorista | **não** | sim |
| Mural da empresa (`/viagens/empresa`) | sim | sim |
| Listar usuários, cadastros (criar/remover) | não | sim |
| Baixar o agente de telemetria de alguém | só o próprio | **nem do próprio time** |

A última linha é de propósito: o pacote do agente contém o token que autentica
os pings, então baixá-lo é o mesmo que pegar a credencial do motorista. Gestor
pode **regerar** o token de alguém (o que derruba o agente antigo), mas não
baixar.

O **mural da empresa** é a exceção combinada à regra de privacidade: ele mostra
as viagens de todo mundo, mas só o resumo (motorista, caminhão, rota, carga,
frete e despesas). Eventos, documentos e observações ficam fora — é o
`ViagemResumoResponse`, separado do `ViagemResponse` justamente por isso.

---

## Logística — demandas

O gestor publica uma **demanda**: a carga que a transportadora fechou com um
cliente. Os motoristas puxam viagens dela até a quantidade fechar.

**Quem decide é o gestor, não o sistema.** Ao criar a demanda ele define:

| Campo | Efeito |
|---|---|
| Produto, rota e empresas | Vão para a viagem prontos — o motorista não digita |
| Quantidade total | O quanto precisa ser entregue no todo |
| Frete por tonelada | Define o valor de cada viagem |
| Prazo de entrega | Vencido, a demanda aparece como atrasada |
| Caminhões permitidos | Vazio = qualquer um da frota |
| Tipos de reboque permitidos | Vazio = qualquer um; preenchido, exige carreta do tipo |

O motorista escolhe só **quanto vai levar** e com que equipamento — e mesmo aí,
o seletor já vem filtrado pelo que a demanda libera. Tentar burlar pela API
devolve erro explicando o porquê (`O caminhão LKT-3B02 não está liberado para
esta demanda.`).

### Como a demanda anda
**A demanda não é de ninguém — é da transportadora.** O motorista *inicia* a
demanda, roda uma viagem, e ao entregar o painel já oferece **"Próxima viagem
desta demanda"**: ele emenda quantas quiser sem voltar procurar o cartão. Vários
motoristas rodam a mesma demanda ao mesmo tempo, e ela só fecha quando a
quantidade contratada se completa.

Cada entrega abate a quantidade e a barra mostra o percentual e **quanto ainda
falta** — mas só entrega que **passou pela conferência** conta. Viagem retida não
abate; ela abate no momento em que o gestor liberar, junto com o frete. Sem essa
condição bastaria finalizar viagens que o jogo nunca confirmou para fechar um
contrato inteiro no papel.

Duas contas diferentes, de propósito:
- **saldo** — o que falta entregar
- **saldo disponível** — o saldo menos o peso reservado por viagens em curso
  **e pelas retidas esperando decisão**

É o segundo que a tela oferece. Sem ele, dois motoristas pegariam a mesma última
carga. A retida entra na mesma conta porque ainda não abateu: se saísse das duas,
a carga sumiria do controle e a demanda estouraria a quantidade contratada no dia
da liberação. Pelo mesmo motivo, um motorista com viagem em aberto (mesmo ainda
não iniciada) não pega outra: senão reservaria a demanda inteira sozinho.

> **É aqui que mora o valor do frete.** Como ele sai da tarifa da demanda e não
> de um campo que o motorista preenche, não há o que mentir sobre valor — a
> fraude sai do jogo antes de precisar conferir nada. A telemetria fica
> responsável só pelos fatos físicos (carga, peso, rota, distância, dano).

---

## Conferência — o bloqueio por divergência

Quando a viagem é finalizada, o sistema compara o que foi **declarado** com o
que o **jogo reportou**. Divergiu, a viagem fica `RETIDA`.

Retida significa: a viagem existe, aparece no histórico e no mural, mas **não
pontua no ranking, não pode ser paga e não abate a demanda** até um gestor
liberar. O motorista não fica impedido de rodar — a punição é no ganho, não na
operação.

O que segura a viagem:

| Situação | Por quê |
|---|---|
| Viagem sem telemetria nenhuma | Seria a maneira mais fácil de burlar: bastaria não abrir o agente |
| Jogo não confirmou distância | Idem — viagem que não andou não aconteceu |
| Carga declarada ≠ carga do jogo | Comparação ignora acento, caixa e pontuação |
| Peso fora de 5% do reportado | Tolerância para arredondamento do jogo |
| Rota divergente | Origem/destino declarados contra as cidades do jogo |
| Salto de posição | Teleporte ou reboque no meio da viagem |

Piloto automático e estacionamento automático **não** retêm — ficam só
registrados na telemetria da viagem, para o gestor olhar se quiser.

> **O valor do frete não é conferido aqui, e isso é de propósito.** Ele vem da
> tarifa da demanda, não de um campo que o motorista preenche — não há o que
> conferir. A conferência cuida só dos fatos físicos, que é o que a telemetria
> realmente sabe.

### Liberação
O menu **Conferência** (só gestor) lista a fila com o motivo de cada retenção.
Liberar exige uma justificativa e registra quem liberou e quando
(`liberadaPor`, `liberadaEm`, `observacaoLiberacao`) — a viagem passa de
`RETIDA` para `LIBERADA` e volta a contar. Liberar duas vezes é recusado.

---

## Mapa — provando que a viagem foi no RBR

A conferência prova que *uma* viagem aconteceu. Esta parte prova que aconteceu
**no mapa da transportadora**.

O sinal usado é o **id interno das cidades** que o jogo reporta (`citySrcId` /
`cityDstId`), não o nome: o RBR usa ids próprios (`rbr_sinop`), diferentes dos
da Europa base (`paris`) e de qualquer outro mod. Nome pode coincidir; id não.

Como não dá para embutir a lista de cidades do RBR no código — o mod muda, cada
servidor roda uma versão — o sistema **aprende**:

1. **Aprendendo** (padrão) — cada viagem com o agente ligado registra as cidades
   vistas e vai crescendo uma caixa de coordenadas. Nada é bloqueado.
2. O gestor confere a lista em **Conferência → Mapa da transportadora** e remove
   o que tiver entrado por engano.
3. **Trancar** — a partir daí, viagem cujas cidades não estão na lista, ou cuja
   posição saiu da área conhecida, é **retida** com o motivo:
   `Cidade de origem "paris" não pertence ao mapa da transportadora.`

Cidades novas do mod não entram sozinhas depois de trancado — é preciso voltar
ao modo aprendizado, de propósito: senão bastaria dirigir em outro mapa para
ensiná-lo ao sistema.

> A caixa de coordenadas tem folga de 20 km e só vale como reforço. O id da
> cidade é a prova forte; a área pega o caso de alguém dirigir numa região
> completamente diferente.

---

## Financeiro

O caixa da transportadora é um número só, e **todo movimento que o altera fica
no extrato** (`movimentos_caixa`) — é isso que permite explicar de onde veio e
para onde foi cada centavo.

Três tipos de movimento:

| Tipo | Quando acontece |
|---|---|
| **Frete** (entra) | Uma viagem passa a valer: aprovada na conferência, ou liberada pelo gestor |
| **Comissão** (sai) | Acerto pago a um motorista |
| **Ajuste** (entra ou sai) | Aporte ou retirada lançada à mão pelo gestor |

### A comissão
Percentual sobre o **frete menos as despesas** da viagem (abastecimento,
pedágio, multa, manutenção). Quem roda mal ganha menos, porque o custo sai da
mesma base.

```
base     = valor do frete − despesas da viagem   (nunca negativa)
comissão = base × percentual
```

O percentual é o da empresa (padrão 12%), ou o do motorista se ele tiver um
próprio (`POST /api/financeiro/percentual/{motoristaId}`).

### O acerto
Um pagamento fecha um conjunto de viagens de uma vez. Só entram viagens
**concluídas, com a conferência resolvida e ainda não pagas** — viagem retida
não aparece para acerto até o gestor liberar.

As viagens ficam amarradas ao pagamento (`viagens.pagamento_id`), então **não há
como pagar a mesma viagem duas vezes**: depois do acerto ela some da fila e uma
segunda tentativa devolve "não há viagem liberada para acertar".

O caixa **não fica negativo**: pagar ou retirar mais do que existe é recusado
com o saldo na mensagem, em vez de gravar um saldo negativo.

### Quem vê o quê
A mesma aba mostra coisas diferentes conforme o papel:
- **Gestor** — saldo, extrato, o que falta acertar com cada motorista (com a
  conta viagem a viagem) e os botões de pagar e lançar aporte/retirada.
- **Motorista** — só os próprios ganhos: a receber, já recebido e o histórico
  de acertos. `GET /financeiro/meus-ganhos/{outro}` devolve 403.

---

## Gestão, loja e créditos

### Central de gestão
O menu **Gestão** (só gestor) reúne num lugar só o que antes estava espalhado:
caixa, a pagar, demandas abertas e viagens retidas em números, mais o atalho
para Logística, Conferência, Financeiro, Loja, Cadastros e Habilitação. Os
menus originais continuam onde estavam — o hub é atalho, não substituição.

### Créditos do motorista
Cada motorista tem uma **carteira**. O acerto de comissão, além de sair do
caixa da empresa, agora **credita** essa carteira — antes o pagamento era só um
registro e o dinheiro não ficava com ninguém.

Toda mudança de saldo gera linha de extrato (`movimentos_carteira`): acerto,
compra ou ajuste lançado pela gestão. O saldo nunca fica negativo.

### Loja
Catálogo inteiramente definido pelo gestor: nome, descrição, categoria, preço,
estoque (vazio = ilimitado) e se está à venda.

> **A loja é de roleplay.** Comprar debita os créditos e registra a compra —
> não altera nada na operação por conta própria. O que cada item significa é
> combinado fora do sistema.

O motorista vê só o que dá para comprar; o gestor vê tudo, inclusive esgotado e
fora de venda. A compra guarda nome e preço do momento, então mudar o preço
depois não reescreve o histórico.

### Caminhão com dono
`Caminhao.dono` define de quem é o veículo:
- **Sem dono** — da empresa, qualquer motorista usa
- **Com dono** — só ele dirige; outro recebe `O caminhão LKT-2A19 é de outro motorista.`

Atribuir é `POST /api/caminhoes/{id}/dono` com o `motoristaId` (vazio devolve à
empresa). É assim que se limita quem roda com o quê — o motorista não escolhe
sozinho um caminhão que não é dele.

---

## Perfil do motorista

O menu **Meu perfil** guarda a ficha completa: identificação (nome civil,
nascimento, CPF/RG fictícios, filiação, naturalidade), contato e residência, e
os dados da vida na transportadora (apelido no jogo, Steam, Discord).

Guarda também **foto e assinatura**, que são o que a CNH vai usar depois. A
foto é reduzida no navegador para no máximo 400px antes de subir — sem isso uma
foto de celular vira alguns MB em base64. A assinatura reaproveita o mesmo
canvas do abastecimento (`SignaturePad`).

O selo no topo diz se já dá para emitir a CNH: hoje exige nome completo e data
de nascimento.

> O perfil vive em tabela própria (`perfis`), não em `usuarios`, de propósito:
> `Usuario` é serializado em listagens e nada disso pode vazar junto. Só o dono
> e um gestor leem — `GET /api/perfil/{usuarioId}` de outro devolve 403.

---

## Habilitação — CNH com prazo e pontos

Cada motorista tem uma CNH de roleplay, desenhada na tela com a **foto e a
assinatura do perfil** (puxadas de lá na hora, não copiadas — assim não
divergem quando o motorista trocar a foto).

### O que tira o motorista de circulação
| | |
|---|---|
| **Prazo** | Emissão vale 3 meses por padrão. Vencida, bloqueia. |
| **Pontos** | Começa com 20 e vai perdendo. Zerou, a carteira se suspende sozinha. |
| **Suspensão** | O gestor pode suspender à mão, com observação registrada. |

Os pontos saem no fim da viagem:

```
−5  a cada multa registrada
−3  a cada avaria detectada pela telemetria
```

**Sem CNH válida o motorista não pega carga.** É esse bloqueio que faz a
renovação importar — a mensagem diz o motivo exato (`CNH vencida em
2026-08-13.`, `CNH suspensa por pontuação zerada.`).

> Motorista sem CNH nenhuma também fica bloqueado, então o seed de
> desenvolvimento já emite carteira para os dois usuários — senão o ambiente
> nasceria travado.

### Gestão
Na aba **Habilitação**, o gestor escolhe de quem quer ver a carteira e pode:
- **Renovar** — devolve prazo e pontos cheios (é a mesma operação da emissão)
- **Devolver pontos** — reabilita sem mexer no prazo, para suspensão por pontos
- **Suspender** — decisão manual, com observação

O motorista vê só a própria carteira e a pontuação que sobrou. `GET
/api/cnh/{outro}` devolve 403. O botão Imprimir/PDF deixa só a carteira na
página.

---

## Telemetria (o jogo alimentando o painel)

O menu **Telemetria** liga o Euro Truck Simulator 2 à conta do motorista. Ele
baixa um agente já com o seu token dentro, deixa rodando enquanto joga, e o
painel passa a receber o estado do caminhão a cada 2 segundos.

### Como o motorista usa
1. Precisa do plugin **scs-sdk-plugin** no jogo — o arquivo `scs-telemetry.dll`
   em `Euro Truck Simulator 2\bin\win_x64\plugins`.
   ([releases](https://github.com/RenCloud/scs-sdk-plugin/releases))
2. Na tela Telemetria, clica em **Baixar LK-Telemetria.zip**
3. Descompacta e dá dois cliques em `LK-Telemetria.bat`
4. A tela acende sozinha quando o agente conecta

O agente é um script PowerShell (roda em qualquer Windows, sem instalar nada).
Ele **só lê** a memória compartilhada `Local\SCSTelemetry` que o plugin publica —
não altera o jogo nem o save. Para conferir a leitura sem o painel:
`LK-Telemetria.bat -Diagnostico`.

### O que a telemetria faz
- **Painel ao vivo** — velocidade, combustível, dano por componente, odômetro,
  carga, rota e empresas, direto do jogo
- **Alimenta a viagem** — distância percorrida, combustível gasto (descontando
  o que foi reabastecido) e dano acumulado, apurados pelo próprio jogo
- **Eventos automáticos** — abastecimento (fecha quando a bomba desliga, com os
  litros que entraram) e avaria (quando o dano sobe mais de 5 pontos) entram
  sozinhos na timeline, marcados com origem `TELEMETRIA`
- **Conferência** — registra uso de piloto automático e de estacionamento
  automático, saltos de posição (teleporte/reboque) e divergência entre a rota
  digitada e a que o jogo reporta

> O apurado pela telemetria fica em tabela separada (`telemetria_viagem`), longe
> do que o motorista declarou — é o que permite o gestor comparar os dois.

### Onde mexer
- Agente: `backend/src/main/resources/agente/lk-telemetria.ps1`
  (os offsets da memória seguem o `scs-telemetry-common.hpp` do plugin)
- Ingestão e regras: `TelemetriaService`
- Token do motorista: `POST /api/telemetria/pareamento/{motoristaId}`
  (gerar de novo invalida o pacote já baixado)

Em produção, apontar `lk.url-api` para o endereço público da API — é o que vai
dentro do `lk-telemetria.json` do pacote.

---

## O que ainda falta

- Cadastro pela tela de login (hoje quem se cadastra usa `POST /auth/cadastro`
  direto na API e espera um gestor aprovar)
- Recuperação de senha
- Preço do abastecimento detectado pela telemetria (o jogo não expõe o valor por
  litro, então o evento entra sem custo e precisa ser editado à mão)
- Comprovante de abastecimento no formato de notinha térmica
- Upload de screenshot em multas e ocorrências (o campo já existe no modelo)
- Testes automatizados

## Produção

### Opção A — tudo num servidor só (recomendado)

Uma VPS com Docker roda o sistema inteiro: banco, API e painel, com HTTPS.

> **Precisa ser VPS ou cloud server com acesso root.** Hospedagem compartilhada
> com cPanel (os planos baratos de HostGator, Hostinger e afins) **não roda
> Java** — lá só tem PHP. Na HostGator, Java existe a partir do VPS.

**Mínimo recomendado: 2 vCPU e 4 GB de RAM.** São três processos no mesmo
servidor (JVM, PostgreSQL e Caddy); com 2 GB roda apertado.

```bash
# na VPS, com Docker instalado
git clone <seu-repo> lk-transportes && cd lk-transportes
cp .env.exemplo .env
nano .env                      # domínio, senha do banco, JWT_SECRET
docker compose up -d --build
```

Sobem três containers:

| Container | O que faz | Exposto? |
|---|---|---|
| `banco` | PostgreSQL com volume persistente | **não** — só na rede interna |
| `api` | Spring Boot, schema migrado pelo Flyway | não |
| `painel` | Caddy: entrega o site e repassa `/api` | sim, portas 80 e 443 |

Três coisas que esse desenho resolve de graça:

**HTTPS automático.** O Caddy pede e renova o certificado Let's Encrypt sozinho
— basta o domínio apontar para a VPS. Não é luxo: o token do agente de
telemetria trafega nessas requisições.

**CORS deixa de existir.** Painel e API no mesmo domínio, o front chama `/api`
relativo. Sem origem cruzada, sem preflight, sem dor de cabeça.

**O banco não abre porta para a internet.** Só a API fala com ele, pela rede
interna do Docker.

#### Antes do DNS propagar
Para testar assim que subir, ponha `DOMINIO=:80` no `.env`. Roda em HTTP puro,
sem certificado. Depois que o domínio apontar para a VPS, troque para o domínio
real e rode `docker compose up -d --build` de novo.

#### Manutenção
```bash
docker compose logs -f api     # acompanhar a API
git pull && docker compose up -d --build   # atualizar
./backup.sh                    # backup do banco (guarda 14 dias)
```

O `backup.sh` serve para o cron — uma linha em `crontab -e`:
```
0 3 * * * cd /caminho/lk-transportes && ./backup.sh >> backup.log 2>&1
```

---

### Opção B — serviços gerenciados

### Onde cada parte roda
| Parte | Onde | Por quê |
|---|---|---|
| **Frontend** (React/Vite) | Vercel | É site estático — encaixa perfeito |
| **Backend** (Spring Boot) | **Não no Vercel** | Vercel não tem runtime Java, e o app é um servidor de processo longo com pool de conexões, não função |
| **Banco** (PostgreSQL) | Neon, Supabase, ou o do próprio host | Qualquer Postgres gerenciado serve |

Para o backend serve qualquer host que rode container: Railway, Render, Fly.io,
Koyeb ou um VPS. Já existe `backend/Dockerfile` pronto (build em duas etapas,
usuário não-root, `MaxRAMPercentage` para respeitar o limite do container).

### Variáveis de ambiente do backend
| Variável | Exemplo | Obrigatória |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://host:5432/lk` | sim |
| `DATABASE_USER` / `DATABASE_PASSWORD` | — | sim |
| `JWT_SECRET` | 64+ caracteres aleatórios | **sim — o app não sobe sem** |
| `ORIGENS_PERMITIDAS` | `https://painel.suaempresa.com` | sim |
| `URL_API` | `https://api.suaempresa.com/api` | sim |
| `GESTOR_INICIAL_EMAIL` / `GESTOR_INICIAL_SENHA` | — | só no primeiro boot |
| `PORT` | injetada pelo host | não |

`URL_API` é o endereço que vai **dentro do pacote do agente de telemetria** —
se estiver errado, o agente dos motoristas não acha o servidor.

### O primeiro acesso
O seed de dados é `@Profile("dev")` e **não roda em produção**: o banco sobe
vazio. Como aprovar um cadastro exige um gestor já existente, o primeiro precisa
nascer por fora — daí `GESTOR_INICIAL_EMAIL` e `GESTOR_INICIAL_SENHA`.

Esse runner só age com o banco vazio. Depois de entrar, troque a senha e
**remova `GESTOR_INICIAL_SENHA` das variáveis**.

### Schema (Flyway)
Em produção o Hibernate **não cria nada** (`ddl-auto: validate`). Quem cria é o
Flyway, a partir de `src/main/resources/db/migration`. O `validate` é a rede de
segurança: se banco e entidades divergirem, o app recusa subir em vez de rodar
torta.

`V1__schema_inicial.sql` tem as 25 tabelas e 29 chaves estrangeiras, gerado a
partir das próprias entidades. **A partir daqui, toda mudança de modelo entra
como migração nova** (`V2__...`), nunca editando a V1.

Dois perfis auxiliares ajudam nisso:
```bash
# Regera o DDL PostgreSQL a partir das entidades → target/schema-postgres.sql
mvn spring-boot:run -Dspring-boot.run.profiles=schemagen

# Roda a migração e o validate contra H2 em modo PostgreSQL.
# Se subir, schema e entidades concordam. (Não valida sintaxe própria do Postgres.)
mvn spring-boot:run -Dspring-boot.run.profiles=provateste
```

### Frontend no Vercel
`frontend/vercel.json` já traz o rewrite de SPA (sem ele, recarregar em
`/logistica` dá 404). A única variável é `VITE_API_URL`, apontando para a API
pública **com `/api` no fim**.

### Antes de abrir para os motoristas
- [ ] `JWT_SECRET` longo e aleatório, diferente de qualquer coisa versionada
- [ ] Senha do gestor inicial trocada e `GESTOR_INICIAL_SENHA` removida
- [ ] `ORIGENS_PERMITIDAS` com o domínio real (não `*`)
- [ ] HTTPS no backend — o token do agente trafega nele
- [ ] Backup do Postgres ligado
