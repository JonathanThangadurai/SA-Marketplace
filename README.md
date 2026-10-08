# SA-Marketplace

Fork of [Apoorvanp/SA-Marketplace](https://github.com/Apoorvanp/SA-Marketplace).

## Origin: the Beehive project

This is the marketplace service from **Beehive**, a Renewable Energy Community (REC)
system designed and prototyped in December 2022 for the CINI Challenge by team
**Bumble Bees** (University of L'Aquila): Apoorva Nalini Pradeep Kumar, Rosheen Naeem,
Niurguiana Borisova, and Jonathan Thangadurai Selvaraj. The companion
[SA-Runner](https://github.com/JonathanThangadurai/SA-Runner) is the "Energy production
simulation service" and "Energy consumption simulation service" described in that
deliverable; this repo is the Production/Consumption/Inventory/Invoice microservices.

Beehive's own design-decision log made a specific, deliberate choice about how internal
trades should be priced:

> **Con#5: Which trading model to use inside the renewable energy communities?**
> ... **Regulated cost model** (ACCEPTED): the cost of trading energy is predetermined
> by the energy market price of the country. ... ensures a stable and predictable price
> for renewable energy, which can help encourage investment.

The prototype never actually implemented that - wiring up a real market price feed was
explicitly out of scope for the deliverable:

> For the future work in deliverable 2, ... we will add pricing information for the
> buying and selling of energy.

So `ConsumptionService`/`InvoiceService` shipped with `SAME_COMMUNITY_PRICE` /
`OTHER_COMMUNITY_PRICE` / `COMPANY_PRICE` = flat `1.0` / `2.0` / `3.0` - a structural
placeholder for "regulated by the market price," not the thing itself.

## This fork: building the future work

`eu-live-pricing` is that future work, built for real:

- **[eu-power-poc](https://github.com/JonathanThangadurai/eu-power-poc)** - a live
  pipeline for the Netherlands' real day-ahead electricity price (via EnergyZero's
  public API), companion to a US/CAISO version
  ([us-power-poc](https://github.com/JonathanThangadurai/us-power-poc)) built alongside
  it. This is the "energy market price of the country" Beehive's own design called for.
- **`PriceService`** (`src/main/kotlin/.../pricing/PriceService.kt`) replaces the three
  flat constants with that live price, scaled so the REC's core incentive - local trade
  cheaper than the grid - holds by construction rather than by coincidence (see Pricing
  table below). Falls back to a fixed price if the feed is ever unreachable, so a trade
  never fails outright over it.
- **Trades are priced at the moment they happen, not whenever the invoice is read.**
  With flat constants this distinction never mattered; once the price moves, it does -
  an invoice for a past trade must not silently change amount because the market moved
  since. `consumptions` gained a `price` column for exactly this.
- **[SA-Runner](https://github.com/JonathanThangadurai/SA-Runner)**'s `solar-and-demand-response`
  branch replaces every synthetic production/consumption value with a real one: 36 real
  households' actual metered solar output and usage (Ausgrid's public "Solar home electricity
  data," CC BY 3.0 AU), replayed half-hour by half-hour instead of a random draw or a formula -
  see that repo's README for the full provenance and the season-shift that keeps a real Sydney
  household's data consistent with the Netherlands' actual seasons. Consumption also responds to
  the live NL price on top of that real baseline - genuine demand response, not an ML model, but
  driven by the same real signal.

## Installation Steps
```shell
./gradlew clean build
docker build -t marketplace:1.0 .
docker compose up -d
```

`docker-compose.yaml` expects `eu-power-poc` (see its own README) to be running on the host at
`:8001` - the Marketplace container reaches it at `http://host.docker.internal:8001`. If that
service isn't reachable, `PriceService` logs a warning and falls back to a fixed price
(`rec.price.fallback-eur-per-kwh`, default 0.10) instead of failing every transaction.

### Running without Docker

```shell
SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/marketplace" \
SPRING_DATASOURCE_USERNAME="postgres" \
SPRING_DATASOURCE_PASSWORD="postgres" \
EU_PRICE_API_URL="http://localhost:8001" \
./gradlew bootRun
```

### Pricing

| Tier | Factor applied to the live NL price |
|---|---|
| Same house | free (0) |
| Same community | 0.5x |
| Other community | 0.75x |
| Company (grid fallback) | 1.0x |

The relative ordering (local trade always cheaper than the grid) is preserved by construction, so
the REC's core incentive still holds - the factors just now scale with a real market signal instead
of being arbitrary flat numbers.
