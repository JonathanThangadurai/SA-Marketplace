package it.univaq.se4gd.rec.marketplace.consumption

import it.univaq.se4gd.rec.marketplace.domain.InventoryItem
import it.univaq.se4gd.rec.marketplace.pricing.PriceService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.sql.Timestamp

@Component
class ConsumptionService(val db: JdbcTemplate, val priceService: PriceService) {
    val inventoryItemRowMapper: RowMapper<InventoryItem> = RowMapper<InventoryItem> { resultSet: ResultSet, _: Int ->
        InventoryItem(resultSet.getInt("communityId"), resultSet.getInt("houseId"), resultSet.getDouble("energyProduced"), resultSet.getTimestamp("productionTime"))
    }

    val consumptionHistoryRowMapper: RowMapper<ConsumptionHistory> = RowMapper<ConsumptionHistory> { resultSet: ResultSet, _: Int ->
        ConsumptionHistory(resultSet.getInt("sellerCommunityId"), resultSet.getInt("sellerHouseId"), resultSet.getInt("buyerCommunityId"), resultSet.getInt("buyerHouseId"), resultSet.getDouble("energyConsumed"), resultSet.getDouble("price"), resultSet.getTimestamp("consumptionTime"))
    }

    @Transactional
    fun consume(communityId: Int, houseId: Int, energyNeed: Double): List<Consumption> {
        val consumptions = consume(communityId, houseId, energyNeed, Level.HOUSE)
        consumptions.filter { it.consumed > 0.0 }
                .forEach {
                    val price = priceFor(it.from, it.consumed)
                    db.update(
                            "insert into consumptions (buyerCommunityId, buyerHouseId, sellerCommunityId, sellerHouseId, energyConsumed, consumptionTime, price) values (?, ?, ?, ?, ?, ?, ?)",
                            communityId, houseId, it.communityId, it.houseId, it.consumed, Timestamp(System.currentTimeMillis()), price
                    )
                }
        return consumptions
    }

    fun getConsumptionHistory(communityId: Int, houseId: Int): List<ConsumptionHistory> {
        return db.query("select * from consumptions where buyerCommunityId=$communityId and buyerHouseId=$houseId ORDER BY consumptionTime DESC", consumptionHistoryRowMapper)
    }

    fun getCreditHistory(communityId: Int, houseId: Int): List<ConsumptionHistory> {
        return db.query("select * from consumptions where sellerCommunityId=$communityId and sellerHouseId=$houseId ORDER BY consumptionTime DESC", consumptionHistoryRowMapper)
    }

    /** Price locked in at the moment a trade happens - never recomputed later from the live price. */
    private fun priceFor(level: Level, units: Double): Double {
        return when (level) {
            Level.HOUSE -> 0.0
            Level.SAME_COMMUNITY -> priceService.sameCommunityPrice() * units
            Level.OTHER_COMMUNITY -> priceService.otherCommunityPrice() * units
            Level.COMPANY -> priceService.companyPrice() * units
        }
    }

    fun consume(communityId: Int, houseId: Int, energyNeed: Double, level: Level): List<Consumption> {
        if(level == Level.HOUSE) {
            val results = db.query("select * from inventory where houseId=$houseId and communityId=$communityId", inventoryItemRowMapper)
            val energyAvailable = results.firstOrNull()?.energyProduced ?: 0.0
            return if(energyAvailable > energyNeed) {
                val energyLeft = energyAvailable - energyNeed
                db.update("update inventory set energyProduced=$energyLeft where communityId=$communityId and houseId=$houseId")
                listOf(Consumption(communityId, houseId, energyNeed, Level.HOUSE))
            } else {
                val energyLeft = 0.0
                db.update("update inventory set energyProduced=$energyLeft where communityId=$communityId and houseId=$houseId")
                val communityEnergy = consume(communityId, houseId, energyNeed - energyAvailable, Level.SAME_COMMUNITY)
                communityEnergy + Consumption(communityId, houseId, energyAvailable, Level.HOUSE)
            }
        } else if (level == Level.SAME_COMMUNITY) {
            val results = db.query("select * from inventory where houseId!=$houseId and communityId=$communityId", inventoryItemRowMapper)
            var currentEnergyNeed = energyNeed
            val consumptions = mutableListOf<Consumption>()
            for(result in results) {
                if(!(currentEnergyNeed > 0.0)) {
                    break
                }
                val energyAvailable = result.energyProduced
                if(energyAvailable > currentEnergyNeed) {
                    val energyLeft = energyAvailable - currentEnergyNeed
                    db.update("update inventory set energyProduced=$energyLeft where communityId=${result.communityId} and houseId=${result.houseId}")
                    consumptions.add(Consumption(result.communityId, result.houseId, currentEnergyNeed, Level.SAME_COMMUNITY))
                    currentEnergyNeed = 0.0
                } else {
                    val energyLeft = 0.0
                    db.update("update inventory set energyProduced=$energyLeft where communityId=${result.communityId} and houseId=${result.houseId}")
                    currentEnergyNeed = energyNeed - energyAvailable
                    consumptions.add(Consumption(result.communityId, result.houseId, energyAvailable, Level.SAME_COMMUNITY))
                }
            }
            if(currentEnergyNeed > 0.0) {
                return consumptions + consume(communityId, houseId, currentEnergyNeed, Level.OTHER_COMMUNITY)
            }
            return consumptions
        } else if(level == Level.OTHER_COMMUNITY) {
            val results = db.query("select * from inventory where communityId!=$communityId", inventoryItemRowMapper)
            var currentEnergyNeed = energyNeed
            val consumptions = mutableListOf<Consumption>()
            for(result in results) {
                if(!(currentEnergyNeed > 0.0)) {
                    break
                }
                val energyAvailable = result.energyProduced
                if(energyAvailable > currentEnergyNeed) {
                    val energyLeft = energyAvailable - currentEnergyNeed
                    db.update("update inventory set energyProduced=$energyLeft where communityId=${result.communityId} and houseId=${result.houseId}")
                    consumptions.add(Consumption(result.communityId, result.houseId, currentEnergyNeed, Level.OTHER_COMMUNITY))
                    currentEnergyNeed = 0.0
                } else {
                    val energyLeft = 0.0
                    db.update("update inventory set energyProduced=$energyLeft where communityId=${result.communityId} and houseId=${result.houseId}")
                    currentEnergyNeed = energyNeed - energyAvailable
                    consumptions.add(Consumption(result.communityId, result.houseId, energyAvailable, Level.OTHER_COMMUNITY))
                }
            }
            if(currentEnergyNeed > 0.0) {
                return consumptions + consume(communityId, houseId, currentEnergyNeed, Level.COMPANY)
            }
            return consumptions
        }
        return listOf(Consumption(-1, -1, energyNeed, Level.COMPANY))
    }

}

data class ConsumptionHistory(val sellerCommunityId: Int, val sellerHouseId: Int, val buyerCommunityId: Int, val buyerHouseId: Int, val units: Double, val price: Double, val consumedAt: Timestamp)

data class Consumption(val communityId: Int, val houseId: Int, val consumed: Double, val from: Level)

enum class Level(val next: Level?) {
    COMPANY(null), OTHER_COMMUNITY(COMPANY), SAME_COMMUNITY(OTHER_COMMUNITY),HOUSE(SAME_COMMUNITY)
}