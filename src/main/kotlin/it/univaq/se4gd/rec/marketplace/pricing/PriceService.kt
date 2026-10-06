package it.univaq.se4gd.rec.marketplace.pricing

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForObject
import java.time.Instant

data class CurrentPriceResponse(
        val country: String,
        val hour_start_utc: String,
        val price_excl_vat_eur_per_kwh: Double,
        val price_incl_vat_eur_per_kwh: Double?
)

/**
 * Fetches the live NL day-ahead price from the eu-power-poc companion service
 * (https://github.com/JonathanThangadurai/eu-power-poc) and turns it into the
 * marketplace's three pricing tiers.
 *
 * Replaces the old fixed constants (SAME_COMMUNITY_PRICE/OTHER_COMMUNITY_PRICE/
 * COMPANY_PRICE = 1.0/2.0/3.0) with a single live number, scaled so the relative
 * ordering that makes a REC worth joining is preserved by construction: trading
 * within your own community is always cheapest, buying from "the company" (grid
 * fallback) is always full price - the absolute scale just now tracks the real
 * market instead of being arbitrary.
 *
 * A short in-memory cache avoids hammering the price API on every request (the
 * SA-Runner load generator calls consume/produce every couple of seconds). If the
 * price API is unreachable, the last known good price is reused (re-cached for
 * another window, so a sustained outage doesn't retry on every single call); the
 * fixed fallback price only kicks in if no price has ever been fetched at all.
 */
@Component
class PriceService(
        @Value("\${rec.price.api-url:http://localhost:8001}") private val priceApiUrl: String,
        @Value("\${rec.price.cache-seconds:60}") private val cacheSeconds: Long,
        @Value("\${rec.price.fallback-eur-per-kwh:0.10}") private val fallbackPrice: Double
) {
    private val log = LoggerFactory.getLogger(PriceService::class.java)
    private val restTemplate = RestTemplate()

    @Volatile
    private var cachedPrice: Double? = null

    @Volatile
    private var cachedAt: Instant = Instant.EPOCH

    private fun livePriceEurPerKwh(): Double {
        val now = Instant.now()
        val cached = cachedPrice
        if (cached != null && now.isBefore(cachedAt.plusSeconds(cacheSeconds))) {
            return cached
        }
        return try {
            val response = restTemplate.getForObject<CurrentPriceResponse>("$priceApiUrl/price/current")
            val price = response.price_excl_vat_eur_per_kwh
            cachedPrice = price
            cachedAt = now
            price
        } catch (ex: Exception) {
            val stale = cachedPrice
            // Re-stamp cachedAt even on failure so a sustained outage retries at most
            // once per cache window, not on every single call.
            cachedAt = now
            if (stale != null) {
                log.warn("Could not reach EU price API at {}, reusing last known price {}: {}", priceApiUrl, stale, ex.message)
                stale
            } else {
                log.warn("Could not reach EU price API at {}, no price ever fetched - using fixed fallback {}: {}", priceApiUrl, fallbackPrice, ex.message)
                fallbackPrice
            }
        }
    }

    fun sameCommunityPrice(): Double = livePriceEurPerKwh() * SAME_COMMUNITY_FACTOR

    fun otherCommunityPrice(): Double = livePriceEurPerKwh() * OTHER_COMMUNITY_FACTOR

    fun companyPrice(): Double = livePriceEurPerKwh() * COMPANY_FACTOR

    companion object {
        const val SAME_COMMUNITY_FACTOR = 0.5
        const val OTHER_COMMUNITY_FACTOR = 0.75
        const val COMPANY_FACTOR = 1.0
    }
}
