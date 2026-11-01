# Marca — Paragem Certa

- **Data:** 01/11/2026
- **Estúdio:** Pixel Lusitano (Alize.On.Me)

## 1. Nome

**Paragem Certa.** É descritivo e diz a promessa: avisar a pessoa para sair na paragem certa.

- Não se parece com o nome da app de referência, nem no som, nem na escrita, nem no sentido.
- Diz-se e escreve-se à primeira, em português de Portugal.
- Em português do Brasil diz-se "parada", por isso o nome também marca o público português.

### Verificações

Estas verificações são uma triagem e não substituem um parecer jurídico. Antes de gastar dinheiro no nome (por exemplo, registo de marca), um advogado de marcas deve fazer uma pesquisa completa.

| Verificação | Onde | Resultado |
|---|---|---|
| Domínio `.com` | RDAP Verisign | **Livre** (404 — sem registo), 01/11/2026 |
| Domínio `.app` | RDAP Google Registry | **Livre** (404 — sem registo), 01/11/2026 |
| Domínio `.pt` | dns.pt | **Por fazer** (o RDAP do .pt não respondeu); verificar em dns.pt |
| Google Play | pesquisa "Paragem Certa" | **Nenhuma app com o nome**, 01/11/2026 (só se viram os primeiros resultados) |
| App Store | pesquisa do nome exato | Por fazer (não há versão iPhone prevista) |
| Marca na UE | EUIPO eSearch / TMview, classes 9 e 42 | Por fazer |
| Marca em Portugal | INPI (inpi.justica.gov.pt) | Por fazer |
| Marca mundial | WIPO Global Brand Database | Por fazer |
| Redes sociais | Instagram, X, TikTok, GitHub `paragemcerta` | Por fazer |
| Web | pesquisa "Paragem Certa" + transportes | Por fazer (o motor de pesquisa bloqueou o robô) |

## 2. Paleta

Estilo **Azulejo**: azul-cobalto e amarelo de elétrico, uma família de cor diferente do laranja da app de referência.

- Os tokens estão em `replica/design/tokens.json`, e os valores na app em `app/src/main/kotlin/.../ui/tema/Cores.kt`.
- A verificação de contraste (WCAG 2.2) deu **21 pares e 0 falhas AA**.
- Correção feita a 01/11/2026: o cinzento do texto secundário no modo claro passou de `#6B7A90` (4,36:1) para `#627189` (4,95:1).
- As cores da marca de referência não estão em `brand.json`, porque o site não as declara nas meta-tags. A Paragem Certa nunca usou laranja.

## 3. Instruções para o logótipo

Estão completas em `docs/logotipo.md`. Em resumo:

- **Ideia:** uma paragem de autocarro com o friso de azulejo, a "paragem certa" encontrada.
- **Tipo:** símbolo + nome por extenso, com o símbolo sozinho no ícone da app.
- **Tem de funcionar:**
  - a 16 px e a 48 dp;
  - como ícone de 1024 × 1024 sem transparência;
  - em monocromático (ícones temáticos do Android).
- **Entregáveis:**
  - SVG do símbolo e do logótipo horizontal;
  - ícone adaptativo (frente e fundo);
  - ícone de 512 × 512 para a Play Store;
  - imagem de destaque de 1024 × 500 para a Play Store;
  - imagem para redes sociais de 1200 × 630.
- **Não pode parecer-se com a marca de referência:** nada de laranja, nenhuma forma, par de cores ou truque de letra em comum. Antes de aprovar, pôr os esboços ao lado do logótipo deles e comparar.

## 4. Tom de voz

**Três palavras:**

- **Próxima, não familiar demais:** trata por "tu" e fala como alguém que conhece a cidade, mas sem gíria nem piadas.
- **Clara, não técnica:** diz o que interessa para apanhar o autocarro ("chega em 3 min", "+4") e esconde os termos dos sistemas.
- **Tranquila, não alarmista:** os problemas dizem-se com calma e com o que fazer a seguir, sem pontos de exclamação nem culpas.

**Faz e não faz:**

| Faz | Não faz |
|---|---|
| "Não há chegadas nas próximas 2 horas" | "Erro: lista vazia" |
| "Sem ligação — atualizado há 4 min" | "Falha de rede! Tente novamente." |
| "Toca na ☆ de uma paragem para a veres aqui" | "Adicione favoritos para personalizar a sua experiência" |
| "Prepara-te: sais na próxima" | "ATENÇÃO: a sua paragem aproxima-se!!!" |
| "Não foi possível carregar a linha. Tenta outra vez mais tarde." | "Ocorreu um erro inesperado (código 503)." |

**As 10 frases mais vistas:** todas foram escritas de raiz e já seguem este tom. Ficam como estão.

1. "Para onde vais?" — pesquisa no topo do mapa
2. "Perto de ti" — painel
3. "Toca na ★ de uma paragem para a veres aqui" — painel sem favoritas
4. "Não há chegadas nas próximas 2 horas" — paragem vazia
5. "Sem ligação — atualizado há X min" — sem rede
6. "Tempo real indisponível — atualizado há X min" — fonte em baixo
7. "a horas", "+3", "−2 adiantado", "🕒 horário" — estado do atraso
8. "A Paragem Certa usa a tua localização para mostrar as paragens e as chegadas mais próximas…" — primeira abertura
9. "Há uma versão nova (0.1.1)" — atualização
10. "Reportar erro" e "Enviar opinião" — contacto

## 5. Varredura

O comando foi `python sweep.py . --config replica/brand.json`, a procurar "Moovit", "Tranzmate", `moovit.com` e `moovitapp.com`.

- **`app/`, `core/` e `.github/`: limpos.** O mesmo vale para `README.md`, `PATCHNOTES.md` e os textos da app (`strings.xml`).
- **Houve 17 resultados, todos em documentos de planeamento:**
  - a especificação (14);
  - o plano das fases 0-1 (2);
  - as instruções do logótipo (1).
- Estes documentos nomeiam a app de referência para descrever a recriação limpa ("nada copiado"). Têm o mesmo estatuto que `replica/` e não vão para a app nem para a ficha da loja.

**A rever à vista antes da Play Store:**
- o ícone definitivo;
- as capturas da loja;
- o texto da ficha;
- a imagem de destaque.
