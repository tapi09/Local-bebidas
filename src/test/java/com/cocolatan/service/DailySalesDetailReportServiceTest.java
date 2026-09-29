package com.cocolatan.service;

import com.cocolatan.model.DailySalesDetailReport;
import com.cocolatan.model.DailySalesDetailRow;
import com.cocolatan.model.Product;
import com.cocolatan.model.Sale;
import com.cocolatan.model.SaleItem;
import com.cocolatan.repository.DatabaseManager;
import com.cocolatan.repository.ProductRepository;
import com.cocolatan.repository.SaleRepository;
import com.cocolatan.repository.StockMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailySalesDetailReportServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private DatabaseManager dbManager;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(productRepository, saleRepository, stockMovementRepository, dbManager);
    }

    @Test
    void dailySalesDetailReturnsPerDayPerProductBreakdown() throws SQLException {
        Sale sale1 = createSale("2026-08-03", "IN", 1L);
        SaleItem item1 = createSaleItem(1L, 1L, 1L, 2, 600.0);

        Sale sale2 = createSale("2026-08-03", "IN", 2L);
        SaleItem item2 = createSaleItem(2L, 2L, 1L, 1, 600.0);
        SaleItem item3 = createSaleItem(3L, 2L, 2L, 3, 450.0);

        when(saleRepository.findAllByDateRange("2026-08-01", "2026-08-05")).thenReturn(Arrays.asList(sale1, sale2));
        when(saleRepository.findItemsBySaleIds(Arrays.asList(1L, 2L)))
                .thenReturn(Arrays.asList(item1, item2, item3));
        when(productRepository.findAllByIds(Arrays.asList(1L, 2L)))
                .thenReturn(Arrays.asList(createProduct(1L, "Coca-Cola 500ml"), createProduct(2L, "Sprite 500ml")));

        DailySalesDetailReport report = reportService.getDailySalesDetailReport("2026-08-01", "2026-08-05");

        assertThat(report.getRows()).hasSize(2);
        assertThat(report.getGrandTotal()).isEqualTo(3150.0);
    }

    @Test
    void dailySalesDetailReturnsEmptyForNoSales() throws SQLException {
        when(saleRepository.findAllByDateRange("2026-08-01", "2026-08-05")).thenReturn(Collections.emptyList());

        DailySalesDetailReport report = reportService.getDailySalesDetailReport("2026-08-01", "2026-08-05");

        assertThat(report.getRows()).isEmpty();
        assertThat(report.getGrandTotal()).isEqualTo(0.0);
    }

    @Test
    void dailySalesDetailGroupsByDateAndProduct() throws SQLException {
        Sale sale1 = createSale("2026-08-03", "IN", 1L);
        SaleItem item1 = createSaleItem(1L, 1L, 1L, 2, 600.0);

        when(saleRepository.findAllByDateRange("2026-08-01", "2026-08-05")).thenReturn(Collections.singletonList(sale1));
        when(saleRepository.findItemsBySaleIds(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonList(item1));
        when(productRepository.findAllByIds(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonList(createProduct(1L, "Coca-Cola 500ml")));

        DailySalesDetailReport report = reportService.getDailySalesDetailReport("2026-08-01", "2026-08-05");

        assertThat(report.getRows()).hasSize(1);
        DailySalesDetailRow row = report.getRows().get(0);
        assertThat(row.getDate()).isEqualTo("03/08/2026");
        assertThat(row.getProductName()).isEqualTo("Coca-Cola 500ml");
        assertThat(row.getQuantity()).isEqualTo(2);
        assertThat(row.getUnitPrice()).isEqualTo(600.0);
        assertThat(row.getLineTotal()).isEqualTo(1200.0);
    }

    @Test
    void dailySalesDetailShowsCancelledSaleAsOriginalPlusCancellation() throws SQLException {
        Sale active = createSale("2026-08-03", "IN", 1L);
        active.setStatus("ACTIVE");
        SaleItem activeItem = createSaleItem(1L, 1L, 1L, 4, 1300.0);

        Sale cancelled = createSale("2026-08-03", "IN", 2L);
        cancelled.setStatus("CANCELLED");
        SaleItem cancelledItem = createSaleItem(2L, 2L, 1L, 6, 1300.0);

        when(saleRepository.findAllByDateRange("2026-08-01", "2026-08-05")).thenReturn(Arrays.asList(active, cancelled));
        when(saleRepository.findItemsBySaleIds(Arrays.asList(1L, 2L)))
                .thenReturn(Arrays.asList(activeItem, cancelledItem));
        when(productRepository.findAllByIds(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonList(createProduct(1L, "Gaseosa")));

        DailySalesDetailReport report = reportService.getDailySalesDetailReport("2026-08-01", "2026-08-05");

        // Active sale is positive, cancelled sale splits into original + cancellation.
        assertThat(report.getRows()).hasSize(3);
        DailySalesDetailRow activeRow = report.getRows().get(0);
        assertThat(activeRow.getProductName()).isEqualTo("Gaseosa");
        assertThat(activeRow.getQuantity()).isEqualTo(4);
        assertThat(activeRow.getLineTotal()).isEqualTo(5200.0);

        DailySalesDetailRow originalRow = report.getRows().get(1);
        assertThat(originalRow.getProductName()).isEqualTo("Gaseosa (Venta anulada)");
        assertThat(originalRow.getQuantity()).isEqualTo(6);
        assertThat(originalRow.getLineTotal()).isEqualTo(7800.0);

        DailySalesDetailRow cancellationRow = report.getRows().get(2);
        assertThat(cancellationRow.getProductName()).isEqualTo("Gaseosa (Anulación)");
        assertThat(cancellationRow.getQuantity()).isEqualTo(-6);
        assertThat(cancellationRow.getLineTotal()).isEqualTo(-7800.0);

        // Net total only counts the active sale.
        assertThat(report.getGrandTotal()).isEqualTo(5200.0);
    }

    @Test
    void dailySalesDetailAccumulatesMixedPricesWithWeightedAverage() throws SQLException {
        Sale sale1 = createSale("2026-08-03", "IN", 1L);
        SaleItem item1 = createSaleItem(1L, 1L, 1L, 1, 100.0);

        Sale sale2 = createSale("2026-08-03", "IN", 2L);
        SaleItem item2 = createSaleItem(2L, 2L, 1L, 2, 80.0);

        when(saleRepository.findAllByDateRange("2026-08-01", "2026-08-05"))
                .thenReturn(Arrays.asList(sale1, sale2));
        when(saleRepository.findItemsBySaleIds(Arrays.asList(1L, 2L)))
                .thenReturn(Arrays.asList(item1, item2));
        when(productRepository.findAllByIds(Collections.singletonList(1L)))
                .thenReturn(Collections.singletonList(createProduct(1L, "Coca-Cola 500ml")));

        DailySalesDetailReport report = reportService.getDailySalesDetailReport("2026-08-01", "2026-08-05");

        assertThat(report.getRows()).hasSize(1);
        DailySalesDetailRow row = report.getRows().get(0);
        // Same product sold the same day at two different prices: quantities and
        // totals accumulate, unit price becomes the weighted average.
        assertThat(row.getQuantity()).isEqualTo(3);
        assertThat(row.getLineTotal()).isEqualTo(260.0);
        assertThat(row.getUnitPrice()).isCloseTo(260.0 / 3,
                org.assertj.core.data.Offset.offset(0.01));
    }

    private Sale createSale(String date, String channel, Long id) {
        Sale sale = new Sale();
        sale.setId(id);
        sale.setSaleDate(date);
        sale.setChannel(channel);
        sale.setTotalAmount(0.0);
        sale.setStatus("ACTIVE");
        return sale;
    }

    private SaleItem createSaleItem(Long id, Long saleId, Long productId, int quantity, double unitPrice) {
        SaleItem item = new SaleItem();
        item.setId(id);
        item.setSaleId(saleId);
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setUnitPrice(unitPrice);
        item.setSubtotal(quantity * unitPrice);
        return item;
    }

    private Product createProduct(Long id, String name) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        return p;
    }
}