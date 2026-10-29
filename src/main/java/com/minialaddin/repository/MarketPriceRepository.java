
package com.minialaddin.repository;

import com.minialaddin.model.MarketPrice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface MarketPriceRepository
        extends JpaRepository<MarketPrice, String> {

    @Modifying
    @Query(value = """
        INSERT INTO market_prices (
            symbol,
            price,
            updated_at
        )
        VALUES (
            :symbol,
            :price,
            clock_timestamp()
        )
        ON CONFLICT (symbol)
        DO UPDATE SET
            price = EXCLUDED.price,
            updated_at = clock_timestamp()
        """, nativeQuery = true)
    int upsertPrice(
            @Param("symbol") String symbol,
            @Param("price") BigDecimal price
    );
}
