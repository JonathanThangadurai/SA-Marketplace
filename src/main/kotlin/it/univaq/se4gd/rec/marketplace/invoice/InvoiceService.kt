package it.univaq.se4gd.rec.marketplace.invoice

import it.univaq.se4gd.rec.marketplace.pricing.PriceService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.sql.Timestamp

@Component
class InvoiceService(val db: JdbcTemplate, val priceService: PriceService) {
    val consumptionHistoryRowMapper: RowMapper<ConsumptionHistory> = RowMapper<ConsumptionHistory> { resultSet: ResultSet, _: Int ->
        ConsumptionHistory(resultSet.getInt("sellerCommunityId"), resultSet.getInt("sellerHouseId"), resultSet.getInt("buyerCommunityId"), resultSet.getInt("buyerHouseId"), resultSet.getDouble("energyConsumed"), resultSet.getDouble("energyConsumed"), resultSet.getTimestamp("consumptionTime"))
    }

    fun getConsumptionHistory(communityId: Int, houseId: Int): List<ConsumptionHistory> {
        val consumptionHistories = db.query("select * from consumptions where buyerCommunityId=$communityId and buyerHouseId=$houseId ORDER BY consumptionTime DESC", consumptionHistoryRowMapper)
        return updatePrice(communityId, houseId, consumptionHistories)
    }

    fun getCreditHistory(communityId: Int, houseId: Int): List<ConsumptionHistory> {
        val creditHistories = db.query("select * from consumptions where sellerCommunityId=$communityId and sellerHouseId=$houseId ORDER BY consumptionTime DESC", consumptionHistoryRowMapper)
        return updateCredit(communityId, houseId, creditHistories)
    }

    private fun updatePrice(communityId: Int, houseId: Int, histories: List<ConsumptionHistory>): List<ConsumptionHistory> {
        return histories.map {
            it.copy(price = getPrice(communityId, houseId, it.sellerCommunityId, it.sellerHouseId, it.units))
        }
    }

    private fun updateCredit(communityId: Int, houseId: Int, histories: List<ConsumptionHistory>): List<ConsumptionHistory> {
        return histories.map {
            it.copy(price = getPrice(communityId, houseId, it.buyerCommunityId, it.buyerHouseId, it.units))
        }
    }

    private fun getPrice(communityId: Int, houseId: Int, transactingCommunityId: Int, transactingHouseId: Int, units: Double): Double {
        return if(communityId == transactingCommunityId && houseId == transactingHouseId) {
            0.0
        } else if(communityId == transactingCommunityId) {
            priceService.sameCommunityPrice() * units
        } else if(transactingCommunityId != -1) {
            priceService.otherCommunityPrice() * units
        } else {
            priceService.companyPrice() * units
        }
    }

}

data class ConsumptionHistory(val sellerCommunityId: Int, val sellerHouseId: Int, val buyerCommunityId: Int, val buyerHouseId: Int, val units: Double, val price: Double, val consumedAt: Timestamp)

enum class Level(val next: Level?) {
    COMPANY(null), OTHER_COMMUNITY(COMPANY), SAME_COMMUNITY(OTHER_COMMUNITY),HOUSE(SAME_COMMUNITY)
}