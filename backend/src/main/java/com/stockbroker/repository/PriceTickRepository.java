package com.stockbroker.repository;

import com.stockbroker.model.PriceTick;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceTickRepository extends JpaRepository<PriceTick, Long> {

    List<PriceTick> findBySymbolOrderByTickTimeDesc(String symbol, Pageable pageable);
}
