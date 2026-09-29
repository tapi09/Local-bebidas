package com.softwaredebebidas.service;

import com.softwaredebebidas.model.DailySalesDetailRow;
import com.softwaredebebidas.model.DailySalesDetailReport;
import com.softwaredebebidas.model.Product;
import com.softwaredebebidas.model.Sale;
import com.softwaredebebidas.model.SaleItem;
import com.softwaredebebidas.model.StockMovement;
import com.softwaredebebidas.repository.DatabaseManager;
import com.softwaredebebidas.repository.ProductRepository;
import com.softwaredebebidas.repository.SaleRepository;
import com.softwaredebebidas.repository.StockMovementRepository;
import com.softwaredebebidas.util.DateUtils;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for report generation: margin, sales by period, channel comparison,
 * stock rotation, and stock value.
 */
public class ReportService {

    // stock_movements.created_at is stored as ISO "yyyy-MM-dd HH:mm:ss" (SQLite
    // datetime('now','localtime')), NOT the dd/MM/yyyy user-facing format.
    private static final DateTimeFormatter ISO_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    private final StockMovementRepository stockMovementRepository;
    private final DatabaseManager dbManager;

    public ReportService(ProductRepository productRepository,
                         SaleRepository saleRepository,
                         StockMovementRepository stockMovementRepository,
                         DatabaseManager dbManager) {
        this.productRepository = productRepository;
        this.saleRepository = saleRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.dbManager = dbManager;
    }

    // ========================================
    // Margin Per Product Report
    // ========================================

    /**
     * Returns margin report for all active products, sorted by margin_percent descending.
     * Products with cost_price = 0 are flagged as zeroCost.
     */
    public List<MarginReport> getMarginReport() {
        try {
            List<Product> products = productRepository.findAllActive();
            List<MarginReport> reports = new ArrayList<>();

            for (Product product : products) {
                double cost = product.getCostPrice();
                double sale = product.getSalePrice();
                double margin = sale - cost;
                double marginPercent = (cost > 0) ? ((sale - cost) / cost) * 100.0 : 0.0;
                boolean zeroCost = (cost == 0);

                reports.add(new MarginReport(
                        product.getName(),
                        product.getHierarchyLabel(),
                        cost,
                        sale,
                        margin,
                        marginPercent,
                        zeroCost
                ));
            }

            reports.sort((a, b) -> Double.compare(b.getMarginPercent(), a.getMarginPercent()));
            return reports;
        } catch (SQLException e) {
            throw new RuntimeException("Error al generar reporte de márgenes", e);
        }
    }

    // ========================================
    // Sales by Period Report
    // ========================================

    /**
     * Returns sales-by-period report grouped by day, within the given date range.
     * Each row: period (date), totalRevenue, transactionCount, averageTicket.
     */
    public List<SalesPeriodReport> getSalesByPeriodReport(String fromDate, String toDate) {
        try {
            List<Sale> sales = saleRepository.findByDateRange(fromDate, toDate);

            // Group sales by date
            Map<String, List<Sale>> grouped = new LinkedHashMap<>();
            for (Sale sale : sales) {
                grouped.computeIfAbsent(sale.getSaleDate(), k -> new ArrayList<>()).add(sale);
            }

            List<SalesPeriodReport> reports = new ArrayList<>();
            for (Map.Entry<String, List<Sale>> entry : grouped.entrySet()) {
                List<Sale> daySales = entry.getValue();
                double totalRevenue = daySales.stream().mapToDouble(Sale::getTotalAmount).sum();
                int transactionCount = daySales.size();
                double averageTicket = (transactionCount > 0) ? totalRevenue / transactionCount : 0.0;

                reports.add(new SalesPeriodReport(
                        DateUtils.toDisplay(entry.getKey()),
                        totalRevenue,
                        transactionCount,
                        averageTicket
                ));
            }

            return reports;
        } catch (SQLException e) {
            throw new RuntimeException("Error al generar reporte de ventas por período", e);
        }
    }

    // ========================================
    // Channel Comparison Report
    // ========================================

    /**
     * Returns channel comparison report for the given date range.
     * Each row: channel, revenue, transactionCount, percentage.
     */
    public List<ChannelReport> getChannelComparisonReport(String fromDate, String toDate) {
        try {
            List<Sale> sales = saleRepository.findByDateRange(fromDate, toDate);

            // Group by channel
            Map<String, Double> revenueByChannel = new LinkedHashMap<>();
            Map<String, Integer> countByChannel = new LinkedHashMap<>();
            double totalRevenue = 0;

            for (Sale sale : sales) {
                String channel = sale.getChannel();
                revenueByChannel.merge(channel, sale.getTotalAmount(), Double::sum);
                countByChannel.merge(channel, 1, Integer::sum);
                totalRevenue += sale.getTotalAmount();
            }

            List<ChannelReport> reports = new ArrayList<>();
            for (Map.Entry<String, Double> entry : revenueByChannel.entrySet()) {
                String channel = entry.getKey();
                double revenue = entry.getValue();
                int count = countByChannel.getOrDefault(channel, 0);
                double percentage = (totalRevenue > 0) ? (revenue / totalRevenue) * 100.0 : 0.0;

                reports.add(new ChannelReport(channel, revenue, count, percentage));
            }

            return reports;
        } catch (SQLException e) {
            throw new RuntimeException("Error al generar reporte de comparación de canales", e);
        }
    }

    // ========================================
    // Daily Sales Detail Report
    // ========================================

    /**
     * Returns a daily sales detail report showing per-day, per-product breakdown.
     * A cancelled sale stays visible as its original positive line ("Venta anulada")
     * plus a separate negative cancellation line ("Anulación"), so the reader can
     * see both the original sale and its cancellation while the totals remain correct.
     * Each row: date, product name, quantity, unit price, line total.
     */
    public DailySalesDetailReport getDailySalesDetailReport(String fromDate, String toDate) {
        try {
            List<Sale> sales = saleRepository.findAllByDateRange(fromDate, toDate);

            List<Long> saleIds = sales.stream().map(Sale::getId).toList();
            List<SaleItem> allItems = saleIds.isEmpty()
                    ? new ArrayList<>()
                    : saleRepository.findItemsBySaleIds(saleIds);
            Map<Long, List<SaleItem>> itemsBySaleId = allItems.stream()
                    .collect(Collectors.groupingBy(SaleItem::getSaleId));
            Map<Long, String> productNames = resolveProductNames(allItems);

            Map<String, DailySalesDetailRow> grouped = new LinkedHashMap<>();
            double grandTotal = 0.0;

            for (Sale sale : sales) {
                List<SaleItem> items = itemsBySaleId.getOrDefault(sale.getId(), List.of());
                boolean isCancelled = "CANCELLED".equals(sale.getStatus());

                for (SaleItem item : items) {
                    String baseName = productNames.getOrDefault(item.getProductId(), "Producto #" + item.getProductId());

                    if (isCancelled) {
                        // The original sale stays visible as a positive line and the
                        // cancellation as a separate negative line; they net to zero.
                        accumulateRow(grouped, sale, baseName + " (Venta anulada)",
                                item.getQuantity(), item.getUnitPrice());
                        accumulateRow(grouped, sale, baseName + " (Anulación)",
                                -item.getQuantity(), item.getUnitPrice());
                    } else {
                        accumulateRow(grouped, sale, baseName, item.getQuantity(), item.getUnitPrice());
                    }
                }
            }

            List<DailySalesDetailRow> rows = new ArrayList<>(grouped.values());
            for (DailySalesDetailRow row : rows) {
                grandTotal += row.getLineTotal();
            }

            DailySalesDetailReport report = new DailySalesDetailReport();
            report.setRows(rows);
            report.setGrandTotal(grandTotal);
            report.setFromDate(DateUtils.toDisplay(fromDate));
            report.setToDate(DateUtils.toDisplay(toDate));
            return report;
        } catch (SQLException e) {
            throw new RuntimeException("Error al generar reporte de ventas detalladas", e);
        }
    }

    /**
     * Aggregates a quantity into the per-day, per-product row. Rows with the same
     * date and product name share one accumulated row (quantity may be negative).
     */
    private void accumulateRow(Map<String, DailySalesDetailRow> grouped, Sale sale,
                               String productName, int quantity, double unitPrice) {
        String key = sale.getSaleDate() + "|" + productName;
        DailySalesDetailRow row = grouped.computeIfAbsent(key, k -> {
            DailySalesDetailRow r = new DailySalesDetailRow();
            r.setDate(DateUtils.toDisplay(sale.getSaleDate()));
            r.setProductName(productName);
            return r;
        });
        row.setQuantity(row.getQuantity() + quantity);
        row.setLineTotal(row.getLineTotal() + quantity * unitPrice);
        row.setUnitPrice(row.getQuantity() != 0 ? row.getLineTotal() / row.getQuantity() : unitPrice);
    }

    /**
     * Resolves the product name map for all distinct product ids referenced by
     * the given sale items, using a single batched query. Missing or unreadable
     * products fall back to the "Producto #id" label.
     */
    private Map<Long, String> resolveProductNames(List<SaleItem> items) {
        Map<Long, String> names = new HashMap<>();
        List<Long> productIds = items.stream().map(SaleItem::getProductId).distinct().toList();
        if (productIds.isEmpty()) {
            return names;
        }
        try {
            for (Product product : productRepository.findAllByIds(productIds)) {
                names.put(product.getId(), product.getName());
            }
        } catch (SQLException e) {
            // Fall back to the default "Producto #id" label for every item.
        }
        return names;
    }

    // ========================================
    // Stock Rotation Report
    // ========================================

    /**
     * Returns stock rotation report for all active products.
     * rotation = units_sold / average_stock for the given period.
     */
    public List<RotationReport> getRotationReport(String fromDate, String toDate) {
        try {
            List<Product> products = productRepository.findAllActive();
            LocalDate start = LocalDate.parse(fromDate, DateTimeFormatter.ISO_LOCAL_DATE);
            LocalDate end = LocalDate.parse(toDate, DateTimeFormatter.ISO_LOCAL_DATE);

            List<Long> productIds = products.stream().map(Product::getId).toList();
            Map<Long, Integer> currentStocks = productIds.isEmpty()
                    ? new HashMap<>()
                    : stockMovementRepository.computeCurrentStocks(productIds);
            Map<Long, List<StockMovement>> movementsByProduct = productIds.isEmpty()
                    ? new HashMap<>()
                    : stockMovementRepository.findByProductIds(productIds).stream()
                            .collect(Collectors.groupingBy(StockMovement::getProductId));

            List<RotationReport> reports = new ArrayList<>();

            for (Product product : products) {
                List<StockMovement> movements = movementsByProduct.getOrDefault(product.getId(), List.of());

                int unitsSold = 0;
                int netMovement = 0;

                for (StockMovement m : movements) {
                    LocalDate movDate;
                    try {
                        movDate = LocalDate.parse(m.getCreatedAt(), ISO_DATETIME_FORMATTER);
                    } catch (Exception e) {
                        continue;
                    }
                    if (!movDate.isBefore(start) && !movDate.isAfter(end)) {
                        if ("EXIT".equals(m.getMovementType())) {
                            unitsSold += m.getQuantity();
                            netMovement -= m.getQuantity();
                        } else if ("ENTRY".equals(m.getMovementType()) || "ADJUSTMENT".equals(m.getMovementType())) {
                            netMovement += m.getQuantity();
                        }
                    }
                }

                // Compute average stock: (opening + closing) / 2
                // closing = opening + netMovement; average = opening + netMovement/2
                // We approximate opening as current_stock - netMovement
                int currentStock = currentStocks.getOrDefault(product.getId(), 0);
                int openingStock = currentStock - netMovement;
                double averageStock = (openingStock + currentStock) / 2.0;
                if (averageStock < 0) {
                    averageStock = 0;
                }

                double rotation = (averageStock > 0) ? unitsSold / averageStock : 0.0;

                reports.add(new RotationReport(
                        product.getName(),
                        product.getHierarchyLabel(),
                        unitsSold,
                        (int) Math.round(averageStock),
                        rotation
                ));
            }

            return reports;
        } catch (SQLException e) {
            throw new RuntimeException("Error al generar reporte de rotación de stock", e);
        }
    }

    // ========================================
    // Stock Value Report
    // ========================================

    /**
     * Returns stock value report for all active products.
     * totalValue = currentStock * costPrice.
     */
    public List<StockValueReport> getStockValueReport() {
        try {
            List<Product> products = productRepository.findAllActive();
            List<Long> productIds = products.stream().map(Product::getId).toList();
            Map<Long, Integer> stockMap = productIds.isEmpty()
                    ? new HashMap<>()
                    : stockMovementRepository.computeCurrentStocks(productIds);

            List<StockValueReport> reports = new ArrayList<>();

            for (Product product : products) {
                int currentStock = stockMap.getOrDefault(product.getId(), 0);
                double totalValue = currentStock * product.getCostPrice();

                reports.add(new StockValueReport(
                        product.getName(),
                        product.getHierarchyLabel(),
                        currentStock,
                        product.getCostPrice(),
                        totalValue
                ));
            }

            return reports;
        } catch (SQLException e) {
            throw new RuntimeException("Error al generar reporte de valor de stock", e);
        }
    }

    /**
     * Returns the total stock value across all active products.
     */
    public double getStockValueReportTotal() {
        return getStockValueReport().stream()
                .mapToDouble(StockValueReport::getTotalValue)
                .sum();
    }

    // ========================================
    // Report DTOs
    // ========================================

    public static class MarginReport {
        private final String productName;
        private final String category;
        private final double costPrice;
        private final double salePrice;
        private final double margin;
        private final double marginPercent;
        private final boolean zeroCost;

        public MarginReport(String productName, String category, double costPrice,
                            double salePrice, double margin, double marginPercent, boolean zeroCost) {
            this.productName = productName;
            this.category = category;
            this.costPrice = costPrice;
            this.salePrice = salePrice;
            this.margin = margin;
            this.marginPercent = marginPercent;
            this.zeroCost = zeroCost;
        }

        public String getProductName() { return productName; }
        public String getCategory() { return category; }
        public double getCostPrice() { return costPrice; }
        public double getSalePrice() { return salePrice; }
        public double getMargin() { return margin; }
        public double getMarginPercent() { return marginPercent; }
        public boolean isZeroCost() { return zeroCost; }
    }

    public static class SalesPeriodReport {
        private final String period;
        private final double totalRevenue;
        private final int transactionCount;
        private final double averageTicket;

        public SalesPeriodReport(String period, double totalRevenue, int transactionCount, double averageTicket) {
            this.period = period;
            this.totalRevenue = totalRevenue;
            this.transactionCount = transactionCount;
            this.averageTicket = averageTicket;
        }

        public String getPeriod() { return period; }
        public double getTotalRevenue() { return totalRevenue; }
        public int getTransactionCount() { return transactionCount; }
        public double getAverageTicket() { return averageTicket; }
    }

    public static class ChannelReport {
        private final String channel;
        private final double revenue;
        private final int transactionCount;
        private final double percentage;

        public ChannelReport(String channel, double revenue, int transactionCount, double percentage) {
            this.channel = channel;
            this.revenue = revenue;
            this.transactionCount = transactionCount;
            this.percentage = percentage;
        }

        public String getChannel() { return channel; }
        public double getRevenue() { return revenue; }
        public int getTransactionCount() { return transactionCount; }
        public double getPercentage() { return percentage; }
    }

    public static class RotationReport {
        private final String productName;
        private final String category;
        private final int unitsSold;
        private final int averageStock;
        private final double rotation;

        public RotationReport(String productName, String category, int unitsSold,
                              int averageStock, double rotation) {
            this.productName = productName;
            this.category = category;
            this.unitsSold = unitsSold;
            this.averageStock = averageStock;
            this.rotation = rotation;
        }

        public String getProductName() { return productName; }
        public String getCategory() { return category; }
        public int getUnitsSold() { return unitsSold; }
        public int getAverageStock() { return averageStock; }
        public double getRotation() { return rotation; }
    }

    public static class StockValueReport {
        private final String productName;
        private final String category;
        private final int currentStock;
        private final double costPrice;
        private final double totalValue;

        public StockValueReport(String productName, String category, int currentStock,
                                double costPrice, double totalValue) {
            this.productName = productName;
            this.category = category;
            this.currentStock = currentStock;
            this.costPrice = costPrice;
            this.totalValue = totalValue;
        }

        public String getProductName() { return productName; }
        public String getCategory() { return category; }
        public int getCurrentStock() { return currentStock; }
        public double getCostPrice() { return costPrice; }
        public double getTotalValue() { return totalValue; }
    }

    // ========================================
    // Top Sellers Report
    // ========================================

    /**
     * Returns top-selling products by quantity sold, for a given limit.
     * Each row: productName, totalQuantity, totalSales.
     */
    public List<TopSellerReport> getTopSellersReport(int limit) {
        String sql = "SELECT p.name, SUM(si.quantity) as total_qty, SUM(si.quantity * si.unit_price) as total_sales "
                + "FROM sale_items si "
                + "JOIN products p ON si.product_id = p.id "
                + "JOIN sales s ON si.sale_id = s.id "
                + "WHERE s.status = 'ACTIVE' "
                + "GROUP BY si.product_id "
                + "ORDER BY total_qty DESC LIMIT ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<TopSellerReport> reports = new ArrayList<>();
                while (rs.next()) {
                    reports.add(new TopSellerReport(
                            rs.getString("name"),
                            rs.getInt("total_qty"),
                            rs.getDouble("total_sales")
                    ));
                }
                return reports;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al generar reporte de productos más vendidos", e);
        }
    }

    public static class TopSellerReport {
        private final String productName;
        private final int totalQuantity;
        private final double totalSales;

        public TopSellerReport(String productName, int totalQuantity, double totalSales) {
            this.productName = productName;
            this.totalQuantity = totalQuantity;
            this.totalSales = totalSales;
        }

        public String getProductName() { return productName; }
        public int getTotalQuantity() { return totalQuantity; }
        public double getTotalSales() { return totalSales; }
    }
}
