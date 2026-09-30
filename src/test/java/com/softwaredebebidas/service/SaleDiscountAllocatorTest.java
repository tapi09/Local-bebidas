package com.softwaredebebidas.service;

import com.softwaredebebidas.model.SaleItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class SaleDiscountAllocatorTest {

    private static final double EPS = 0.0001;

    @Test
    @DisplayName("sin descuento: cada línea conserva su monto bruto")
    void noDiscountKeepsGrossAmounts() {
        List<SaleItem> items = List.of(item(2, 600.0), item(3, 450.0));

        List<Double> net = SaleDiscountAllocator.allocateNet(items, 2550.0);

        assertThat(net).hasSize(2);
        assertThat(net.get(0)).isCloseTo(1200.0, offset(EPS));
        assertThat(net.get(1)).isCloseTo(1350.0, offset(EPS));
    }

    @Test
    @DisplayName("porcentaje sobre varios productos: reparto proporcional")
    void percentageDiscountIsAllocatedProportionally() {
        // gross 1200 + 1350 = 2550, 20% off -> net 2040
        List<SaleItem> items = List.of(item(2, 600.0), item(3, 450.0));

        List<Double> net = SaleDiscountAllocator.allocateNet(items, 2040.0);

        assertThat(net.get(0)).isCloseTo(960.0, offset(EPS));
        assertThat(net.get(1)).isCloseTo(1080.0, offset(EPS));
        assertThat(sumCents(net)).isEqualTo(204000L);
    }

    @Test
    @DisplayName("monto fijo sobre varios productos: reparto proporcional")
    void fixedDiscountIsAllocatedProportionally() {
        // gross 1000 + 3000 = 4000, fixed 400 off -> net 3600
        List<SaleItem> items = List.of(item(1, 1000.0), item(1, 3000.0));

        List<Double> net = SaleDiscountAllocator.allocateNet(items, 3600.0);

        assertThat(net.get(0)).isCloseTo(900.0, offset(EPS));
        assertThat(net.get(1)).isCloseTo(2700.0, offset(EPS));
    }

    @Test
    @DisplayName("el resto de redondeo va a la línea de mayor bruto y la suma es exacta")
    void roundingRemainderGoesToLargestLine() {
        // 3 equal lines, net 100.00 -> 33.33 each, 0.01 remainder to the first line.
        List<SaleItem> items = List.of(item(1, 50.0), item(1, 50.0), item(1, 50.0));

        List<Double> net = SaleDiscountAllocator.allocateNet(items, 100.0);

        assertThat(net.get(0)).isCloseTo(33.34, offset(EPS));
        assertThat(net.get(1)).isCloseTo(33.33, offset(EPS));
        assertThat(net.get(2)).isCloseTo(33.33, offset(EPS));
        assertThat(sumCents(net)).isEqualTo(10000L);
    }

    @Test
    @DisplayName("el resto negativo también se absorbe en la línea mayor")
    void negativeRemainderGoesToLargestLine() {
        // Shares round to 6.67 + 20.00 + 6.67 = 33.34, one cent over the total.
        List<SaleItem> items = List.of(item(1, 10.0), item(1, 30.0), item(1, 10.0));

        List<Double> net = SaleDiscountAllocator.allocateNet(items, 33.33);

        assertThat(sumCents(net)).isEqualTo(3333L);
        // The largest line (index 1) absorbs any remainder.
        assertThat(net.get(1)).isGreaterThan(net.get(0));
    }

    @Test
    @DisplayName("una sola línea recibe el total neto completo")
    void singleLineGetsWholeNetTotal() {
        List<Double> net = SaleDiscountAllocator.allocateNet(List.of(item(3, 333.33)), 800.0);

        assertThat(net).hasSize(1);
        assertThat(net.get(0)).isCloseTo(800.0, offset(EPS));
    }

    @Test
    @DisplayName("total neto cero (100% de descuento): todas las líneas en cero")
    void zeroNetTotalYieldsZeroLines() {
        List<Double> net = SaleDiscountAllocator.allocateNet(List.of(item(2, 600.0), item(1, 100.0)), 0.0);

        assertThat(net).containsExactly(0.0, 0.0);
    }

    @Test
    @DisplayName("bruto total cero: sin división por cero, líneas en cero")
    void zeroGrossSumYieldsZeroLines() {
        List<Double> net = SaleDiscountAllocator.allocateNet(List.of(item(1, 0.0), item(2, 0.0)), 50.0);

        assertThat(net).containsExactly(0.0, 0.0);
    }

    @Test
    @DisplayName("lista vacía devuelve lista vacía")
    void emptyItemsYieldsEmptyList() {
        assertThat(SaleDiscountAllocator.allocateNet(Collections.emptyList(), 100.0)).isEmpty();
    }

    private static long sumCents(List<Double> amounts) {
        return amounts.stream().mapToLong(v -> Math.round(v * 100.0)).sum();
    }

    private static SaleItem item(int quantity, double unitPrice) {
        SaleItem item = new SaleItem();
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        item.setSubtotal(quantity * unitPrice);
        return item;
    }
}
