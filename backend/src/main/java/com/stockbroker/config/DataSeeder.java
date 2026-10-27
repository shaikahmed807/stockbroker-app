package com.stockbroker.config;

import com.stockbroker.model.Stock;
import com.stockbroker.model.User;
import com.stockbroker.repository.StockRepository;
import com.stockbroker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds the stock universe and a demo account on first start.
 * Base prices are illustrative starting points for the simulator, not real quotes.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final StockRepository stockRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(StockRepository stockRepository, UserRepository userRepository,
                      PasswordEncoder passwordEncoder) {
        this.stockRepository = stockRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (stockRepository.count() == 0) {
            List<Stock> stocks = List.of(
                    new Stock("RELIANCE", "Reliance Industries Ltd", "Energy", 2950.00),
                    new Stock("TCS", "Tata Consultancy Services Ltd", "Information Technology", 4100.00),
                    new Stock("INFY", "Infosys Ltd", "Information Technology", 1850.00),
                    new Stock("HDFCBANK", "HDFC Bank Ltd", "Banking", 1700.00),
                    new Stock("ICICIBANK", "ICICI Bank Ltd", "Banking", 1250.00),
                    new Stock("SBIN", "State Bank of India", "Banking", 820.00),
                    new Stock("BHARTIARTL", "Bharti Airtel Ltd", "Telecom", 1600.00),
                    new Stock("ITC", "ITC Ltd", "FMCG", 480.00),
                    new Stock("LT", "Larsen & Toubro Ltd", "Construction", 3600.00),
                    new Stock("TATAMOTORS", "Tata Motors Ltd", "Automobile", 980.00),
                    new Stock("WIPRO", "Wipro Ltd", "Information Technology", 540.00),
                    new Stock("TATASTEEL", "Tata Steel Ltd", "Metals", 160.00));
            stockRepository.saveAll(stocks);
            log.info("Seeded {} stocks", stocks.size());
        }

        if (!userRepository.existsByEmailIgnoreCase("demo@stockbroker.com")) {
            User demo = new User();
            demo.setFullName("Demo Trader");
            demo.setEmail("demo@stockbroker.com");
            demo.setPassword(passwordEncoder.encode("Demo@1234"));
            userRepository.save(demo);
            log.info("Created demo user demo@stockbroker.com / Demo@1234");
        }
    }
}
