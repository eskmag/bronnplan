# Validering

Hvordan beregningskjernen er kontrollert, og hvor stort avviket er. Formlene står i `docs/model.md`.

Referansene er regnet uavhengig av Java med NumPy/SciPy i `tools/validation/generate_references.py`.
CSV-filene ligger i `core/src/test/resources/reference/`, så testene (og CI) trenger ikke Python. Kjør skriptet
på nytt for å reprodusere dem.

## Oversikt

| Hva | Referanse | Caser | Krav | Målt maks avvik | Test |
| --- | --- | --- | --- | --- | --- |
| E1(x), x ∈ [1e-6, 50] | `scipy.special.exp1` | 206 | relativ < 1e-8 | 2,8·10⁻¹⁵ | `ExponentialIntegralTest` |
| Linjekilde ΔT og T_f | SciPy-skript | 36 | relativ < 1e-10 | under kravet | `LineSourceTest` |
| Dimensjonering, lukket form | NumPy/SciPy-skript | 4 | relativ < 1e-8 | 5,2·10⁻¹⁶ | `SizerReferenceTest` |
| Lukket form mot bisection | bisection i testkode | 1 | 1e-6 m | under kravet | `SizerTest` |
| Ingen kaldere punkt mellom stegslutter | tett tidsgitter, 20 punkter per steg | 1 | 1e-9 °C | under kravet | `SizerTest` |

## 1. E1 mot SciPy

206 verdier, logaritmisk fordelt over [1e-6, 50], med ekstra punkter rundt overgangen ved x = 1 mellom rekke og
kjedebrøk. Største relative avvik er 2,8·10⁻¹⁵ (ved x = 1,001). I tillegg testes E1(0,1), E1(1) og E1(10)
mot kjente verdier.

## 2. Linjekilden mot SciPy

36 kombinasjoner: λ ∈ {2,5; 3,0; 4,0} W/(m·K), q ∈ {20; 35} W/m, t ∈ {1 time; 30 døgn; 25 år} og
r ∈ {0,0575; 1,0} m. Både `ΔT` og `T_f` sjekkes mot kravet om relativ feil under 1e-10.

## 3. Dimensjonering mot Python-referanse

Python-skriptet bygger lastplanen, superponerer og regner lukket form på egen hånd. Begge sider bruker samme
graddagsprofil (9 desimaler), 730 timer per måned og en topp på 6 timer.

| Scenario | Q_heat [kWh/år] | P_heat [kW] | SCOP / COP_topp | λ | T₀ [°C] | R_b | År | H Python [m] | H Java [m] | Relativt avvik |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Enebolig, Bergen | 20 000 | 8,0 | 3,5 / 3,0 | 3,0 | 7,5 | 0,10 | 25 | 148,73 | 148,73 | 0 |
| Dårlig berg, høy last | 35 000 | 14,0 | 3,2 / 2,8 | 2,5 | 6,5 | 0,12 | 25 | 327,23 | 327,23 | 5,2·10⁻¹⁶ |
| Stor enebolig | 45 000 | 12,0 | 4,0 / 3,2 | 3,0 | 7,5 | 0,08 | 25 | 261,00 | 261,00 | 4,4·10⁻¹⁶ |
| Enebolig, 10 år | 20 000 | 8,0 | 3,5 / 3,0 | 3,0 | 7,5 | 0,10 | 10 | 144,72 | 144,72 | 3,9·10⁻¹⁶ |

Alle har T_min = −3 °C, ρc = 2,2 MJ/(m³·K) og r_b = 0,0575 m. I alle scenarioene er minimum i januar i siste år.

**Hva dette viser og ikke viser:** Java-koden regner det modellen sier, med avvik på maskinpresisjonsnivå.
Det sier ingenting om at modellen er riktig for et virkelig borehull. Det krever et publisert regneeksempel eller måledata, se §4.

## 4. Ikke gjort ennå

- **Publisert regneeksempel:** Et fullstendig publisert eksempel med alle inndata (for eksempel fra ASHRAE eller
  Kavanaugh & Rafferty) er ikke gjenskapt ennå. Det er det viktigste neste steget for valideringen.
- **Endelig brønnlengde:** Hvor mye den uendelige linjekilden overvurderer temperaturfallet etter 25 år er ikke
  tallfestet.
