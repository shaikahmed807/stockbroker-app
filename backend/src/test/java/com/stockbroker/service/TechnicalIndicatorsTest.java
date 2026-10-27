package com.stockbroker.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TechnicalIndicators – pure indicator maths")
class TechnicalIndicatorsTest {

    private static List<Double> rising(int n) {
        List<Double> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) list.add((double) i);
        return list;
    }

    private static List<Double> falling(int n) {
        List<Double> list = rising(n);
        Collections.reverse(list);
        return list;
    }

    private static List<Double> flat(int n, double value) {
        return new ArrayList<>(Collections.nCopies(n, value));
    }

    @Nested
    @DisplayName("SMA / EMA")
    class MovingAverages {

        @Test
        void smaOfLastNValues() {
            assertEquals(8.0, TechnicalIndicators.sma(rising(10), 5), 1e-9);  // (6+7+8+9+10)/5
        }

        @Test
        void smaReturnsNullWhenNotEnoughData() {
            assertNull(TechnicalIndicators.sma(rising(3), 5));
        }

        @Test
        void smaWithExactlyPeriodValues() {
            assertEquals(3.0, TechnicalIndicators.sma(rising(5), 5), 1e-9);
        }

        @Test
        void emaOfLinearSeriesLagsPriceByConstant() {
            // seed = avg(1..5) = 3, k = 1/3 -> each step EMA = price - 2
            assertEquals(8.0, TechnicalIndicators.ema(rising(10), 5), 1e-9);
        }

        @Test
        void emaOfFlatSeriesEqualsThePrice() {
            assertEquals(250.0, TechnicalIndicators.ema(flat(30, 250.0), 20), 1e-9);
        }

        @Test
        void emaReturnsNullWhenNotEnoughData() {
            assertNull(TechnicalIndicators.ema(rising(4), 5));
        }

        @Test
        void emaSeriesMarksWarmUpSlotsAsNaN() {
            double[] series = TechnicalIndicators.emaSeries(rising(6), 3);
            assertNotNull(series);
            assertTrue(Double.isNaN(series[0]));
            assertTrue(Double.isNaN(series[1]));
            assertEquals(2.0, series[2], 1e-9);
        }
    }

    @Nested
    @DisplayName("RSI")
    class Rsi {

        @Test
        void rsiIs100WhenPricesOnlyRise() {
            assertEquals(100.0, TechnicalIndicators.rsi(rising(30), 14), 1e-9);
        }

        @Test
        void rsiIsZeroWhenPricesOnlyFall() {
            assertEquals(0.0, TechnicalIndicators.rsi(falling(30), 14), 1e-9);
        }

        @Test
        void rsiIsNullWithoutEnoughData() {
            assertNull(TechnicalIndicators.rsi(rising(14), 14));
        }

        @Test
        void rsiStaysBetween0And100ForChoppyPrices() {
            List<Double> choppy = new ArrayList<>();
            for (int i = 0; i < 40; i++) choppy.add(100 + (i % 2 == 0 ? 1.5 : -1.0) * (i % 5));
            double rsi = TechnicalIndicators.rsi(choppy, 14);
            assertTrue(rsi >= 0 && rsi <= 100, "RSI out of range: " + rsi);
        }
    }

    @Nested
    @DisplayName("MACD and Bollinger Bands")
    class MacdAndBollinger {

        @Test
        void macdPositiveInUptrend() {
            double[] macd = TechnicalIndicators.macd(rising(60), 12, 26, 9);
            assertNotNull(macd);
            assertTrue(macd[0] > 0);
            assertEquals(macd[0] - macd[1], macd[2], 1e-9, "histogram = macd - signal");
        }

        @Test
        void macdNegativeInDowntrend() {
            double[] macd = TechnicalIndicators.macd(falling(60), 12, 26, 9);
            assertNotNull(macd);
            assertTrue(macd[0] < 0);
        }

        @Test
        void macdNullWithoutSlowPlusSignalPoints() {
            assertNull(TechnicalIndicators.macd(rising(34), 12, 26, 9));
        }

        @Test
        void bollingerBandsAreSymmetric() {
            double[] bb = TechnicalIndicators.bollinger(rising(20), 20, 2);
            assertNotNull(bb);
            assertEquals(10.5, bb[1], 1e-9);
            assertEquals(bb[0] - bb[1], bb[1] - bb[2], 1e-9);
        }

        @Test
        void bollingerCollapsesOnFlatPrices() {
            double[] bb = TechnicalIndicators.bollinger(flat(25, 99.0), 20, 2);
            assertArrayEquals(new double[]{99.0, 99.0, 99.0}, bb, 1e-9);
        }

        @Test
        void bollingerNullWithoutEnoughData() {
            assertNull(TechnicalIndicators.bollinger(rising(10), 20, 2));
        }
    }

    @Nested
    @DisplayName("Volatility, momentum, min / max")
    class Others {

        @Test
        void volatilityIsZeroOnFlatPrices() {
            assertEquals(0.0, TechnicalIndicators.volatility(flat(30, 50.0), 20), 1e-12);
        }

        @Test
        void volatilityIsPositiveOnMovingPrices() {
            List<Double> p = List.of(100.0, 101.0, 99.0, 102.0, 98.0, 103.0);
            assertTrue(TechnicalIndicators.volatility(p, 60) > 0);
        }

        @Test
        void volatilityNullWithTooFewReturns() {
            assertNull(TechnicalIndicators.volatility(List.of(1.0, 2.0), 60));
        }

        @Test
        void momentumPercent() {
            assertEquals(100.0, TechnicalIndicators.momentum(List.of(5.0, 7.0, 10.0), 2), 1e-9);
        }

        @Test
        void momentumNullWithoutEnoughData() {
            assertNull(TechnicalIndicators.momentum(List.of(5.0, 7.0), 2));
        }

        @Test
        void minAndMaxUseOnlyTheLookbackWindow() {
            List<Double> p = List.of(5.0, 3.0, 9.0, 1.0, 7.0);
            assertEquals(1.0, TechnicalIndicators.min(p, 3), 1e-9);
            assertEquals(9.0, TechnicalIndicators.max(p, 3), 1e-9);
            assertEquals(7.0, TechnicalIndicators.max(p, 1), 1e-9);
        }

        @Test
        void minAndMaxOfEmptyListAreZero() {
            assertEquals(0.0, TechnicalIndicators.min(List.of(), 10), 1e-9);
            assertEquals(0.0, TechnicalIndicators.max(List.of(), 10), 1e-9);
        }
    }
}
