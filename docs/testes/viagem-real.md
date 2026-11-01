# Viagem real — validação da fase 3

A fase 3 só fica fechada depois de fazeres estas duas viagens (spec, secção 11). Em ambas o ecrã fica desligado, e uma delas tem um transbordo.

| # | Viagem | O que mostra |
|---|---|---|
| 1 | Autocarro da Carris Metropolitana, com várias paragens | O caminho com GPS: "O 736 chega em 4 min" à espera, "Prepara-te" e "Sai agora" pela posição. |
| 2 | Metro, com um transbordo, parte dela no subterrâneo | O caminho sem GPS: "Posição estimada pelo horário" e avisos pelo horário. |

## Antes de sair

- Instala a build de depuração (`./gradlew :app:assemblePessoalDebug`, o APK fica em `app/build/outputs/apk/pessoal/debug/`). Só ela grava o `gps.csv`; o APK do GitHub não grava.
- O Android não instala a de depuração por cima da de lançamento (assinaturas diferentes): desinstala a outra primeiro, o que apaga os dados desse telemóvel, ou usa outro telemóvel.
- Na primeira viagem, a app pede por ordem a localização precisa, as notificações, tirar a app da poupança de bateria e avisa se o volume do alarme estiver a 0.
- Escolhe a rota em Rotas, toca em "Começar viagem", confirma que o ecrã da viagem mostra a primeira etapa e desliga o ecrã. Durante a viagem não voltes a abrir a app.
- O telemóvel pode estar em silêncio: o aviso toca pelo volume do alarme.

## O que conta como passar

Em cada etapa em veículo:

- "Prepara-te" toca e vibra com o ecrã desligado antes de o veículo chegar à penúltima paragem, uma só vez, com o nome da paragem onde sais.
- "Sai agora" toca antes de o veículo parar no destino, uma só vez. Nunca antes de o veículo sair da penúltima paragem.
- Num transbordo, "Sai agora" diz "e apanha o N" e a viagem passa sozinha à etapa seguinte.
- No fim, o último "Sai agora" toca e a notificação fixa desaparece sozinha.

Na viagem 2, sem GPS, os avisos vêm pelo horário. Se apanhares um comboio mais tarde do que o previsto, o aviso pode chegar uma estação mais cedo (aceite na 0.3.0): regista-o, não é falha.

É falha um aviso que não toca com o ecrã desligado, que toca depois de passares a paragem, que toca duas vezes ou que diz a paragem errada. Anota a hora, a paragem e o que o telemóvel mostrava.

## Guardar a gravação

Cada viagem deixa a gravação do GPS em `files/viagem/gps.csv` (uma linha `s,lat,lon,acc` por posição). Serve para o teste `gravacoesReais` do `:core` repetir a viagem no PC.

Copia-a logo que chegares, antes de tocares em "Parar" ou "Descartar" (apagam o plano da viagem) e antes de começares outra viagem (apaga o `gps.csv`). Com o telemóvel por USB e a depuração USB ligada, no Git Bash (o `>` do PowerShell 5 muda a codificação do ficheiro), a partir da pasta `Paragem Certa`:

```
mkdir -p gravacoes
adb exec-out run-as pt.pixellusitano.paragemcerta.pessoal cat files/viagem/gps.csv > gravacoes/AAAA-MM-DD-autocarro.csv
adb exec-out run-as pt.pixellusitano.paragemcerta.pessoal cat files/viagem/trip-plan.json > gravacoes/AAAA-MM-DD-autocarro-plano.json
```

- O nome é livre, mas o `.csv` e o `-plano.json` têm de ter o mesmo.
- `Paragem Certa/gravacoes/` fica ao lado de `codigo/`, fora do repositório. As gravações têm os sítios por onde andaste: nunca entram no git nem no GitHub.
- Depois, `./gradlew :core:test --tests '*ViagemTest*'` corre as gravações. Se uma falhar, é um resultado: não a alteres para o teste passar.
