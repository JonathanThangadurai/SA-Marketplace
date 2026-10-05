# SA-Marketplace

Fork of [Apoorvanp/SA-Marketplace](https://github.com/Apoorvanp/SA-Marketplace) (CINI Challenge:
Renewable Energy Community marketplace). This fork's `eu-live-pricing` branch replaces the original
fixed pricing constants (`SAME_COMMUNITY_PRICE`/`OTHER_COMMUNITY_PRICE`/`COMPANY_PRICE` = 1.0/2.0/3.0)
with a live NL day-ahead electricity price pulled from
[eu-power-poc](https://github.com/JonathanThangadurai/eu-power-poc), so internal community trades
are priced as a discount off a real market price instead of an arbitrary number - see
`src/main/kotlin/it/univaq/se4gd/rec/marketplace/pricing/PriceService.kt`.

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
