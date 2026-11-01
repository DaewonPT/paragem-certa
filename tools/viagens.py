"""Corre as 10 viagens de teste com o mesmo pedido que a app faz ao Transitous e preenche a coluna
"Paragem Certa" de docs/testes/viagens.md. A coluna "Referência" (outro planeador, à mesma hora)
preenche-se à mão e é mantida quando o script volta a correr.

Uso: python tools/viagens.py [AAAA-MM-DD]   (por omissão, o próximo dia útil)
Só biblioteca padrão; um pedido de cada vez, com o User-Agent da app.
"""
import datetime as dt
import json
import pathlib
import sys
import time
import urllib.request
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

BASE = "https://api.transitous.org"
AGENTE = "ParagemCerta/0.2.0 (contact@pixellusitano.com)"


def fuso_lisboa(dia):
    """Europe/Lisbon; no Windows o Python não traz o tzdata e usa-se a regra da UE (verão do último domingo
    de março ao último domingo de outubro). shortcut: ignora a hora exata da mudança, só conta o dia."""
    try:
        return ZoneInfo("Europe/Lisbon")
    except ZoneInfoNotFoundError:
        def ultimo_domingo(mes):
            d = dt.date(dia.year, mes, 31)
            return d - dt.timedelta(days=(d.weekday() + 1) % 7)
        return dt.timezone(dt.timedelta(hours=1 if ultimo_domingo(3) <= dia < ultimo_domingo(10) else 0))
DOC = pathlib.Path(__file__).resolve().parent.parent / "docs" / "testes" / "viagens.md"

# (origem, lat, lon, destino, lat, lon, hora, chegar às, filtro)
VIAGENS = [
    ("Cais do Sodré", 38.7058, -9.1446, "Gare do Oriente", 38.7678, -9.0990, "08:30", False, ""),
    ("Almada (centro)", 38.6790, -9.1569, "Marquês de Pombal", 38.7253, -9.1500, "08:00", False, ""),
    ("Sintra (estação)", 38.7997, -9.3866, "Rossio", 38.7139, -9.1394, "09:00", False, ""),
    ("Cascais (estação)", 38.7008, -9.4189, "Mosteiro dos Jerónimos", 38.6979, -9.2068, "10:00", False, ""),
    ("Amadora (estação)", 38.7584, -9.2394, "Saldanha", 38.7347, -9.1449, "09:00", True, ""),
    ("Barreiro (terminal)", 38.6556, -9.0792, "Cais do Sodré", 38.7058, -9.1446, "08:45", True, ""),
    ("Odivelas", 38.7934, -9.1735, "Cidade Universitária", 38.7517, -9.1586, "08:15", False, "acessível"),
    ("Seixal (centro)", 38.6406, -9.1031, "Entrecampos", 38.7448, -9.1484, "07:45", False, "menos transbordos"),
    ("Loures (centro)", 38.8309, -9.1685, "Aeroporto", 38.7685, -9.1286, "11:00", False, ""),
    ("Costa da Caparica", 38.6446, -9.2365, "Praça de Espanha", 38.7375, -9.1597, "18:00", False, ""),
]

FILTROS = {"acessível": "&pedestrianProfile=WHEELCHAIR", "menos transbordos": "&maxTransfers=1", "": ""}


def url(v, dia):
    _, la1, lo1, _, la2, lo2, hora, chegar, filtro = v
    h, m = map(int, hora.split(":"))
    quando = dt.datetime.combine(dia, dt.time(h, m), fuso_lisboa(dia)).astimezone(dt.timezone.utc)
    return (f"{BASE}/api/v6/plan?fromPlace={la1},{lo1}&toPlace={la2},{lo2}"
            f"&time={quando.strftime('%Y-%m-%dT%H:%M:%SZ')}&arriveBy={'true' if chegar else 'false'}"
            f"&numItineraries=5{FILTROS[filtro]}")


def mais_rapida(resposta):
    """(minutos, transbordos) do itinerário mais curto, sem os que têm etapas canceladas, como a app."""
    bons = [i for i in resposta.get("itineraries", []) if i.get("legs") and not any(l.get("cancelled") for l in i["legs"])]
    if not bons:
        return None
    i = min(bons, key=lambda i: dt.datetime.fromisoformat(i["endTime"]) - dt.datetime.fromisoformat(i["startTime"]))
    segundos = (dt.datetime.fromisoformat(i["endTime"]) - dt.datetime.fromisoformat(i["startTime"])).total_seconds()
    return round(segundos / 60), i.get("transfers", 0)


def referencias():
    """Coluna "Referência" já preenchida, por número da viagem."""
    if not DOC.exists():
        return {}
    ref = {}
    for linha in DOC.read_text(encoding="utf-8").splitlines():
        c = [x.strip() for x in linha.strip().strip("|").split("|")]
        if len(c) >= 7 and c[0].isdigit():
            ref[int(c[0])] = c[-1]
    return ref


def proximo_dia_util(hoje):
    d = hoje + dt.timedelta(days=1)
    while d.weekday() >= 5:
        d += dt.timedelta(days=1)
    return d


def main():
    dia = dt.date.fromisoformat(sys.argv[1]) if len(sys.argv) > 1 else proximo_dia_util(dt.date.today())
    ref = referencias()
    linhas = []
    for n, v in enumerate(VIAGENS, 1):
        pedido = urllib.request.Request(url(v, dia), headers={"User-Agent": AGENTE})
        try:
            with urllib.request.urlopen(pedido, timeout=30) as r:
                res = mais_rapida(json.load(r))
            pc = "sem ligações" if res is None else f"{res[0]} min, {res[1]} transb."
        except Exception as e:  # um erro numa viagem não impede as outras
            pc = f"erro ({type(e).__name__})"
        origem, _, _, destino, _, _, hora, chegar, filtro = v
        quando = ("chegar às " if chegar else "partir às ") + hora
        linhas.append(f"| {n} | {origem} | {destino} | {quando} | {filtro or '—'} | {pc} | {ref.get(n, '')} |")
        print(linhas[-1])
        time.sleep(1)

    DOC.parent.mkdir(parents=True, exist_ok=True)
    DOC.write_text(
        "# Viagens de teste — rotas\n\n"
        f"Dia útil: {dia.isoformat()} (horas de Lisboa). A coluna Paragem Certa é a ligação mais rápida do "
        "pedido que a app faz, preenchida por `python tools/viagens.py`. A coluna Referência preenche-se à mão "
        "com outro planeador, à mesma hora, e fica guardada quando o script volta a correr.\n\n"
        "| # | Origem | Destino | Hora | Filtro | Paragem Certa (min, transbordos) | Referência (min) |\n"
        "|---|---|---|---|---|---|---|\n" + "\n".join(linhas) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
