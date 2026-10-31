# Brønnplan

[![CI](https://github.com/eskmag/bronnplan/actions/workflows/ci.yml/badge.svg)](https://github.com/eskmag/bronnplan/actions/workflows/ci.yml)

Beregner hvor dyp en energibrønn må være for at en væske/vann-varmepumpe skal fungere gjennom hele
anleggets levetid, og viser hvordan svaret er kommet fram.

## Problemet

En energibrønn som er for grunn, blir kaldere år for år. Etter noen år mister varmepumpen effekt eller stopper.
En brønn som er for dyp, koster unødvendig mye, siden boring ofte koster flere hundre kroner per meter.
Dimensjonering gjøres i dag ofte med tommelfingerregler eller i lukkede verktøy der antakelsene ikke vises.

Brønnplan regner den minste dybden som holder kollektorvæsken over en valgt minstetemperatur (for eksempel
−3 °C) i hele perioden (for eksempel 25 år). Alle formler, antakelser og avvik er dokumentert, slik at
beregningen kan etterprøves.

## Status

| Del | Status |
| --- | --- |
| Beregningskjerne (`core`, ren Java) | **Ferdig:** linjekilde, månedlig last med effekttopp, superposisjon og dimensjonering i lukket form |
| Validering mot uavhengig Python-referanse | **Ferdig** for E1, linjekilden og dimensjoneringen |
| REST-API (Spring Boot, PostgreSQL), jobbkø for simuleringer | Under arbeid |
| Webgrensesnitt (Angular) med graf over fluidtemperatur | Under arbeid |
| PDF-rapport med inndata, antakelser og hash av inndata | Under arbeid |

## Eksempel

Enebolig i Bergen: 20 000 kWh/år varmebehov, 8 kW dimensjonerende effekt, SCOP 3,5 og COP 3,0 ved
dimensjonerende effekt. Granitt med λ = 3,0 W/(m·K), T₀ = 7,5 °C og R_b = 0,10 m·K/W. Kravet er T_min = −3 °C
over 25 år.

```java
var inputs = new SizingInputs(
        new GroundProperties(3.0, 2.2e6, 7.5),        // λ [W/(m·K)], ρc [J/(m³·K)], T₀ [°C]
        new Borehole(0.0575, 0.10),                   // r_b [m], R_b [m·K/W]
        new BuildingLoad(20_000, 8.0, 3.5, 3.0,       // kWh/år, kW, SCOP, COP ved topp
                MonthlyProfile.bergenDegreeDays()),
        DesignCriteria.defaults());                   // −3 °C, 25 år
var result = Sizer.requiredDepth(inputs);
```

```
Nødvendig dybde:            148,7 m
Laveste fluidtemperatur:    −3,00 °C  (januar år 25, under effekttoppen)
Advarsler:                  ingen
```

Laveste fluidtemperatur per år ved 148,7 m:

| År | 1 | 5 | 10 | 25 |
| --- | --- | --- | --- | --- |
| T_f, min [°C] | 1,39 | 0,67 | 0,44 | −3,00 ¹ |

¹ Effekttoppen på 6 timer legges bare inn i siste år, der berget er kaldest. Den er derfor det
dimensjonerende punktet.

Følsomhet for bergets varmeledningsevne, med samme hus:

| λ [W/(m·K)] | 2,5 | 3,0 | 3,5 | 4,0 |
| --- | --- | --- | --- | --- |
| Nødvendig dybde [m] | 165 | 149 | 136 | 127 |

## Modellen

Kort fortalt (fullstendig med kilder i [`docs/model.md`](docs/model.md)):

1. **Last fra bygg til bakke:** `Q_ground = Q_heat · (1 − 1/SCOP)`. Den fordeles på måneder etter graddager for
   Bergen (normaler 1991–2020). Effekttoppen er `P_heat · (1 − 1/COP)` i 6 timer i kaldeste måned i siste år.
2. **Uendelig linjekilde (Kelvin):** `G(t) = E1(r_b² / (4αt)) / (4πλ)`. E1 er implementert selv, med rekke for
   små x og kjedebrøk for store x.
3. **Temporal superposisjon:** `S(t) = Σ (Q_i − Q_{i−1}) · G(t − t_i)` over månedlige laststeg.
4. **Fluidtemperatur:** `T_f(t) = T₀ − (Q(t)·R_b + S(t)) / H`.
5. **Dimensjonering i lukket form:** `T_f` er lineær i `1/H`, så dybden er
   `H = max_t [Q(t)·R_b + S(t)] / (T₀ − T_min)`, uten iterasjon. Bisection brukes i testene som uavhengig
   kryssjekk, og blir hovedløseren når endelig brønnlengde gjør problemet ikke-lineært.

## Validering

Kort fortalt (detaljer og tabeller i [`docs/validation.md`](docs/validation.md)):

| Hva | Referanse | Målt maks avvik |
| --- | --- | --- |
| E1(x), 206 punkter i [1e-6, 50] | `scipy.special.exp1` | 2,8·10⁻¹⁵ relativt |
| Linjekilde, 36 kombinasjoner | SciPy-skript | under 1e-10 relativt |
| Dimensjonering, 4 scenarioer | uavhengig NumPy/SciPy-implementasjon | 5,2·10⁻¹⁶ relativt |
| Lukket form mot bisection | testkode | under 1e-6 m |

Referansene lages av [`tools/validation/generate_references.py`](tools/validation/generate_references.py), og
CSV-filene er committet, så CI trenger ikke Python. Et publisert regneeksempel er **ikke** gjenskapt ennå.

## Kjør

Krever Java 25 og Maven 3.9.12 eller nyere.

```bash
mvn -B verify          # bygger og kjører alle tester (349)
```

Reprodusere referansene:

```bash
cd tools/validation
python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
.venv/bin/python generate_references.py
```

`docker compose up` starter PostgreSQL for API-et som er under arbeid. Beregningskjernen trenger den ikke.

## Kjente begrensninger

- **Uendelig linjekilde:** endelig brønnlengde er ikke modellert. Over 25 år overvurderer det temperaturfallet,
  så resultatet er på den sikre siden (for dyp brønn). Størrelsen er ikke tallfestet ennå.
- **Én brønn,** homogent berg, ingen grunnvannsstrøm, konstant borehullsresistans og ingen påvirkning fra naboanlegg.
- **Månedsoppløsning** med 730 timer per måned og én effekttopp på 6 timer. Timesoppløsning krever
  lastaggregering eller FFT og er planlagt.
- **Bare oppvarming:** ingen kjøling eller frikjøling.
- Brønner over 300 m gir advarsel om å vurdere flere brønner.

## Struktur

```
core/                      beregningskjerne, ren Java uten rammeverk
  math/                    E1
  model/                   berg, borehull, last, månedsprofil, lastplan, linjekilde
  sizing/                  superposisjon, fluidtemperatur, dimensjonering
  units/                   enhetsomregning
tools/validation/          Python-referanser (NumPy/SciPy)
docs/model.md              formler, enheter, kilder og gyldighet
docs/validation.md         validering med målt avvik
```
