CREATE TABLE IF NOT EXISTS inventory (
    communityId INT NOT NULL,
    houseId INT NOT NULL,
    energyProduced DECIMAL NOT NULL,
    productionTime TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (houseId, communityId)
);

CREATE TABLE IF NOT EXISTS productions (
    communityId INT NOT NULL,
    houseId INT NOT NULL,
    energyProduced DECIMAL NOT NULL,
    productionTime TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS consumptions (
    buyerCommunityId INT NOT NULL,
    buyerHouseId INT NOT NULL,
    sellerCommunityId INT NOT NULL,
    sellerHouseId INT NOT NULL,
    energyConsumed DECIMAL NOT NULL,
    consumptionTime TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- Price at the moment this trade happened. Historical trades must never be
    -- repriced using today's live price - see PriceService / ConsumptionService.
    price DECIMAL NOT NULL DEFAULT 0
);