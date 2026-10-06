package it.univaq.se4gd.rec.marketplace.invoice

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.sql.Timestamp

@Component
class InvoiceService(val db: JdbcTemplate) {
    val consumptionHistoryRowMapper: RowMapper<ConsumptionHistory> = RowMapper<ConsumptionHistory> { resultSet: ResultSet, _: Int ->
        ConsumptionHistory(resultSet.getInt("sellerCommunityId"), resultSet.getInt("sellerHouseId"), resultSet.getInt("buyerCommunityId"), resultSet.getInt("buyerHouseId"), resultSet.getDouble("energyConsumed"), resultSet.getDouble("price"), resultSet.getTimestamp("consumptionTime"))
    }

    // Price is read straight from the stored column - it was locked in by ConsumptionService
    // at the moment the trade happened, so an invoice for a past period never changes just
    // because the live price has moved on since.
    fun getConsumptionHistory(communityId: Int, houseId: Int): List<ConsumptionHistory> {
        return db.query("select * from consumptions where buyerCommunityId=$communityId and buyerHouseId=$houseId ORDER BY consumptionTime DESC", consumptionHistoryRowMapper)
    }

    fun getCreditHistory(communityId: Int, houseId: Int): List<ConsumptionHistory> {
        return db.query("select * from consumptions where sellerCommunityId=$communityId and sellerHouseId=$houseId ORDER BY consumptionTime DESC", consumptionHistoryRowMapper)
    }

}

data class ConsumptionHistory(val sellerCommunityId: Int, val sellerHouseId: Int, val buyerCommunityId: Int, val buyerHouseId: Int, val units: Double, val price: Double, val consumedAt: Timestamp)

enum class Level(val next: Level?) {
    COMPANY(null), OTHER_COMMUNITY(COMPANY), SAME_COMMUNITY(OTHER_COMMUNITY),HOUSE(SAME_COMMUNITY)
}
