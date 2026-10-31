# Fagmodell

Denne fila beskriver hver formel som er implementert i `core`, med enheter, kilde og hvor den er testet.
Alle størrelser regnes internt i SI-enheter: meter, sekunder, watt, kelvin (temperaturer i °C).

## 1. Eksponentialintegralet E1

```
E1(x) = ∫ₓ^∞ e^(−u) / u du,   x > 0
```

**Implementasjon:** `core/.../math/ExponentialIntegral.java`

| Område | Metode | Formel |
| --- | --- | --- |
| 0 < x ≤ 1 | Potensrekke | `E1(x) = −γ − ln x − Σ_{k≥1} (−x)^k / (k·k!)` |
| x > 1 | Kjedebrøk, modifisert Lentz | `E1(x) = e^(−x) · 1/(x+1 − 1²/(x+3 − 2²/(x+5 − …)))` |

`γ = 0,5772156649015329` (Euler–Mascheronis konstant). Begge løkkene stopper når neste ledd endrer resultatet
med mindre enn 1e-16 relativt.

**Hvorfor delt ved x = 1:** Rekka konvergerer raskt og uten merkbar kansellering for små x. Kjedebrøken
konvergerer raskt for store x. Den asymptotiske rekka er bevisst ikke brukt: den divergerer, så
nøyaktigheten ved moderate x er begrenset.

**Kilder:** Abramowitz & Stegun, *Handbook of Mathematical Functions*, 5.1.11 (rekke) og 5.1.22 (kjedebrøk);
Press et al., *Numerical Recipes*, 3. utg., §6.3.

**Validering:** `ExponentialIntegralTest` mot 206 verdier fra `scipy.special.exp1` for x ∈ [1e-6, 50],
med ekstra punkter rundt overgangen ved x = 1. Kravet er relativ feil < 1e-8. Målt maksimal relativ
feil er **2,8·10⁻¹⁵** (ved x = 1,001).

## 2. Linjekildemodellen (Kelvin)

Uendelig lang linjekilde med konstant varmeuttak `q` per meter i homogent berg, fra tid `t = 0`.

```
α        = λ / (ρc)
G(r, t)  = 1/(4πλ) · E1( r² / (4αt) )
ΔT(r, t) = q · G(r, t)
T_f(t)   = T₀ − q · ( R_b + G(r_b, t) )
```

| Symbol | Kode | Enhet | Betydning |
| --- | --- | --- | --- |
| λ | `GroundProperties.conductivity` | W/(m·K) | Bergets varmeledningsevne |
| ρc | `GroundProperties.volumetricHeatCapacity` | J/(m³·K) | Volumetrisk varmekapasitet |
| T₀ | `GroundProperties.undisturbedTemperature` | °C | Uforstyrret temperatur i berget |
| α | `GroundProperties.diffusivity()` | m²/s | Termisk diffusivitet |
| r_b | `Borehole.radius` | m | Borehullsradius |
| R_b | `Borehole.thermalResistance` | m·K/W | Effektiv borehullsresistans, fra fluid til borehullsvegg |
| q | parameter `q` | W/m | Varmeuttak per meter brønn |
| r | parameter `r` | m | Avstand fra linjekilden |
| t | parameter `t` | s | Tid siden uttaket startet |
| G | `LineSource.responseFactor` | m·K/W | Temperaturfall per W/m uttak |
| T_f | `LineSource.fluidTemperature` | °C | Middeltemperatur i kollektorvæsken |

**Fortegn:** Positiv `q` betyr at varme hentes ut av berget, så positiv `ΔT` er et temperaturfall.

**Implementasjon:** `core/.../model/LineSource.java`, `GroundProperties.java`, `Borehole.java`.
`G` er eksponert separat fordi temporal superposisjon (neste steg) summerer `G` for lasttrinn.

**Kilder:** Carslaw & Jaeger, *Conduction of Heat in Solids*, 2. utg., §10.4. Ingersoll & Plass (1948)
for anvendelse på grunnvarme.

**Validering:** `LineSourceTest` mot et SciPy-referanseskript (`tools/validation/generate_references.py`)
for 36 kombinasjoner: λ ∈ {2,5; 3,0; 4,0} W/(m·K), q ∈ {20; 35} W/m, t ∈ {1 time; 30 døgn; 25 år},
r ∈ {0,0575; 1,0} m. Kravet er relativ feil < 1e-10 for både `ΔT` og `T_f`. I tillegg finnes
egenskapstester: lineær i q, økende med t, avtakende med r, lavere ΔT ved høyere λ over lang tid,
og samsvar med den logaritmiske tilnærmingen `q/(4πλ)·(ln(4αt/r²) − γ)` etter 25 år.

**Gyldighet:** Linjekilden overser borehullets radielle utstrekning og varmekapasitet. Den er derfor
unøyaktig de første timene, typisk når `t < 5 r_b²/α` (Eskilson 1987). For r_b = 0,0575 m og
α ≈ 1,4·10⁻⁶ m²/s blir det ca. 3,4 timer. Med månedsoppløsning er dette uten betydning.
Grensen er implementert som `LineSource.validityTime(ground, r) = 5r²/α` og `LineSource.isValid(ground, r, t)`.
Den brukes til advarsler, ikke til å stoppe beregningen. Effekttoppen på 6 timer (§3.3) ligger over grensen.
Den overser også endelig brønndybde, som får betydning etter flere tiår for grunne brønner.
Det siste er **ikke tallfestet ennå** og må vurderes i `docs/validation.md`.

## 3. Last fra bygg til bakke

### 3.1 Omregning med varmefaktor

Varmepumpen leverer `Q_heat` til bygget. Kompressoren står for `Q_heat/COP` av dette, og resten hentes fra berget.

```
Q_ground = Q_heat · (1 − 1/SCOP)        årlig energi     [kWh]
P_ground = P_heat · (1 − 1/COP_peak)    effekt i toppen  [W]
```

| Symbol | Kode | Enhet | Betydning |
| --- | --- | --- | --- |
| Q_heat | `BuildingLoad.annualHeatDemandKWh` | kWh/år | Varme levert av varmepumpen |
| P_heat | `BuildingLoad.peakHeatPowerKW` | kW | Varmeeffekt ved dimensjonerende forhold |
| SCOP | `BuildingLoad.scop` | – | Årsvarmefaktor, > 1 |
| COP_peak | `BuildingLoad.copPeak` | – | Varmefaktor ved dimensjonerende forhold, > 1 |
| Q_ground | `LoadConverter.groundEnergy` | kWh/år | Varme hentet fra berget |
| P_ground | `LoadConverter.groundPeakPower` | W | Effekt hentet fra berget i toppen |

`COP_peak` er et eget felt fordi COP er lavere ved dimensjonerende effekt enn over året. Høyere COP gir *større*
uttak fra berget, så å bruke SCOP i toppen ville vært litt konservativt.
⚠ **Typisk standardverdi for `COP_peak` er ikke fastsatt.** Den må hentes fra produsentdata før den vises som
standard i UI.

**Implementasjon:** `core/.../model/LoadConverter.java`, `BuildingLoad.java`.
**Kilde:** Energibalanse for varmepumpen, se f.eks. ASHRAE Handbook – HVAC Applications, kap. «Geothermal Energy».
**Test:** `LoadConverterTest`, `BuildingLoadTest`. Eksempel: 20 000 kWh med SCOP 3,5 gir 14 286 kWh fra berget.

### 3.2 Månedsprofil (graddager, Bergen)

Årlig energi fordeles på måneder etter andelen graddager:

```
GD_m = d_m · max(0, 17 °C − T_m)        f_m = GD_m / Σ GD
```

der `d_m` er antall døgn i måneden og `T_m` er månedsnormalen. Basis 17 °C er vanlig i Norge for graddagstall.

| Mnd | T_m [°C] | f_m [–] |
| --- | --- | --- |
| jan | 2,6 | 0,139535 |
| feb | 2,4 | 0,127782 |
| mar | 3,8 | 0,127907 |
| apr | 6,7 | 0,096587 |
| mai | 10,1 | 0,066860 |
| jun | 13,0 | 0,037509 |
| jul | 15,3 | 0,016473 |
| aug | 15,1 | 0,018411 |
| sep | 12,3 | 0,044074 |
| okt | 8,6 | 0,081395 |
| nov | 5,4 | 0,108777 |
| des | 3,1 | 0,134690 |

**Kilde:** Månedsnormaler for middeltemperatur, Bergen – Florida (SN50540), normalperiode 1991–2020,
Norsk klimaservicesenter (seklima.met.no). Verifisert mot seKlima 29.10.2026, avvik under 0,2 °C.
Tabellen `MonthlyProfile` genereres av `tools/validation/generate_references.py`.

**Forenklinger:** Graddager er regnet fra månedsmiddel, ikke fra døgnmiddel, og det er ingen grense for
fyringssesong. Derfor får også sommermånedene en liten andel. Det tar delvis hensyn til varmtvann, men varmtvann
er ikke modellert eksplisitt.

**Implementasjon:** `core/.../model/MonthlyProfile.java`. Index 0 er januar. Profilen valideres: 12 verdier,
hver ≥ 0, og summen er 1 ± 1e-6. `coldestMonth()` er måneden med størst andel.
**Test:** `MonthlyProfileTest`.

### 3.3 Lastplan og effekttopp

Lasten er stykkevis konstant. Den har ett steg per måned over hele perioden, og hvert steg varer til neste
starter:

```
t_k   = k · 730 h,                k = 0 … 12·år − 1
P_k   = Q_ground · f_(k mod 12) / 730 h
```

**730 timer per måned** (8760 h / 12) gir like lange steg. Feilen i månedsmiddeleffekt er inntil ca. 6 %
for februar (672 h), mens energien per år er eksakt.

**Effekttopp:** I kaldeste måned i siste år legges et ekstra steg inn de siste 6 timene av måneden:

```
t_peak = slutten av måneden − 6 h
P_peak = max(P_k, P_ground)
```

- *Plassering i siste år og på slutten av måneden:* da har berget vært kjølt ned lengst, og `T_f` blir lavest.
- *Erstatter månedsmiddelet:* dimensjonerende effekt er den totale lasten i de 6 timene. Månedsmiddelet består
  av den samme driften, så å legge dem sammen ville telle lasten to ganger. I tre-puls-metoden er pulsene
  inkrementelle (`q_a`, `q_m − q_a`, `q_h − q_m`), slik at effekten under toppen er `q_h`. Er `P_ground`
  lavere enn månedsmiddelet, beholdes middelet.
- *Betydning:* En tidligere versjon la toppen oppå middelet. For scenarioet «Stor enebolig» i
  `docs/validation.md` (januar 6,5 kW, topp 8,3 kW fra berget) ga det 359 m mot 261 m, altså 38 % for dyp brønn.

**Implementasjon:** `core/.../model/LoadSchedule.java`, `LoadStep.java`, `core/.../units/Units.java`.
**Kilde:** Kavanaugh & Rafferty, *Geothermal Heating and Cooling: Design of Ground-Source Heat Pump Systems*
(ASHRAE, 2014), for tre-puls-metoden med årlig, månedlig og topp-last. Inkrementell form: Philippe, Bernier &
Marchio (2010), *Validity ranges of three analytical solutions to heat transfer in the vicinity of single
boreholes*, Geothermics 39. ⚠ Henvisningen til den inkrementelle formen er ikke kontrollert mot artikkelen.
**Test:** `LoadScheduleTest`: energien summert over et år er lik `Q_ground`, toppen varer 6 timer i riktig måned,
toppen erstatter middelet (ikke summen), og starttidene er strengt voksende. `UnitsTest` dekker omregningene.

### 3.4 Dimensjoneringskriterier

`DesignCriteria(minFluidTempC, years)` har standardverdien −3 °C over 25 år (`DesignCriteria.defaults()`).
Kravet `T_min < T₀` sjekkes i `SizingInputs`, fordi det trenger både kriteriet og berget.

## 4. Superposisjon og dimensjonering

### 4.1 Temporal superposisjon

Lasten er stykkevis konstant (§3.3). Responsen fra hvert lasttrinn summeres:

```
S(t) = Σ_{i: t_i < t} (Q_i − Q_{i−1}) · G(r_b, t − t_i)      [m·K]
```

`Q_i` er effekten fra berget for hele brønnen [W], og `Q_{−1} = 0`. Delt på dybden `H` gir `S` et temperaturfall i kelvin.

**Konvensjon:** Steg i gjelder på `(t_i, t_{i+1}]`. Ved slutten av et steg gjelder steget fortsatt, og neste
steg har ikke startet. Derfor evalueres `G` aldri ved tid null, og serien ved månedsslutt er entydig.

**Implementasjon:** `core/.../sizing/Superposition.java` (`powerAt`, `responseAt`).
**Kilde:** Eskilson (1987), temporal superposisjon av trinnresponser. Carslaw & Jaeger for linjekilden.
**Test:** `SuperpositionTest`: ett steg er lik linjekilden, to steg regnet for hånd, konvensjonen ved
`t = t_{i+1}`, linearitet, null last og at berget henter seg inn etter at lasten slås av.

**Ytelse:** Én evaluering er O(n). Hele serien er O(n²), men med 301 steg er det ca. 45 000 E1-kall.
Timesoppløsning (~219 000 steg) krever lastaggregering eller FFT (ikke implementert).

### 4.2 Fluidtemperatur

```
L(t)   = Q(t)·R_b + S(t)            [m·K]
T_f(t) = T₀ − L(t) / H              [°C]
```

`T_f` er lineær i `1/H`. Det er dette som gir dimensjoneringen en lukket form.

**Implementasjon:** `core/.../sizing/FluidTemperature.java` (`loadTerm`, `at`, `series`). Serien har ett punkt
ved slutten av hvert steg: hver månedsslutt og slutten av effekttoppen (301 punkter for 25 år).
**Test:** `FluidTemperatureTest`: konstant last er lik `LineSource.fluidTemperature`, null last gir `T₀`,
fallet er omvendt proporsjonalt med `H`, og det kaldeste punktet er effekttoppen.

### 4.3 Dimensjonering (lukket form)

Minste dybde `H` slik at `T_f(t) ≥ T_min` over hele perioden:

```
H = max_t L(t) / (T₀ − T_min)
```

Maksimum tas over slutten av alle steg. Innenfor et steg er `Q` konstant. Etter en lastøkning vokser
responsen mot slutten av steget, og etter en lastreduksjon henter berget seg inn. Det er ikke bevist for alle
lastformer, så `SizerTest` sjekker det mot et tett tidsgitter med 20 punkter per steg.

`Sizer` returnerer `SizingResult(requiredDepthM, minFluidTempC, monthOfMinimum, series, warnings, modelVersion)`.
`minFluidTempC` er lik `T_min` per konstruksjon. `monthOfMinimum` er 0-basert over perioden, så 288 er
januar i år 25.

**Inndatakrav:** `T_min < T₀` (`SizingInputs`). Ellers finnes ingen løsning.

**Advarsler** (stopper ikke beregningen):
- `H > 300 m`: kjernen modellerer én brønn. Anbefal flere brønner.
- Korteste laststeg er kortere enn gyldighetsgrensen `5·r_b²/α` (§2). Normalt er det effekttoppen på 6 t mot
  ca. 3,4 t, så advarselen slår bare til ved uvanlige verdier.

**Implementasjon:** `core/.../sizing/Sizer.java`, `SizingInputs.java`, `SizingResult.java`.
**Test:** `SizerTest`: H gir nøyaktig `T_min` (toleranse 1e-9), bisection på H som uavhengig kryssjekk
(1e-6 m), tett tidsgitter, egenskaper (dypere brønn gir varmere fluid, høyere λ gir grunnere brønn, lengre
periode og større topp gir dypere brønn) og advarsler. `SizerReferenceTest` mot Python, se `docs/validation.md`.

**Når lukket form ikke lenger holder:** Med endelig brønnlengde (g-funksjoner) eller flere brønner er ikke
`T_f` lenger lineær i `1/H`. Da blir bisection hovedløseren.

### 4.4 Kjente begrensninger

- Uendelig linjekilde: endelig brønnlengde er ikke modellert. Over 25 år overvurderer det temperaturfallet,
  altså konservativt (gir for dyp brønn). Størrelsen er ikke tallfestet ennå.
- Én brønn, homogent berg, ingen grunnvannsstrøm og konstant `R_b`.
- Månedsoppløsning med 730 timer per måned og én effekttopp på 6 timer.
- Ingen kjøling eller frikjøling.
