package com.cocolatan.presenter;

import com.cocolatan.model.Sale;
import com.cocolatan.repository.ProductRepository;
import com.cocolatan.repository.SaleRepository;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Presenter for the dashboard home view. Thin delegator: only translates
 * repository results into UI-facing primitives. No SQL or JDBC lives here —
 * all data access stays in the repository layer.
 */
public class HomePresenter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int RECENT_SALES_LIMIT = 5;

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;

    public HomePresenter(SaleRepository saleRepository, ProductRepository productRepository) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
    }

    public double getTodaySales() {
        try {
            return saleRepository.sumSalesForDate(LocalDate.now().format(DATE_FORMATTER));
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener ventas del día", e);
        }
    }

    public int getLowStockCount() {
        try {
            return productRepository.countLowStock();
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener conteo de stock bajo", e);
        }
    }

    public int getOutOfStockCount() {
        try {
            return productRepository.countOutOfStock();
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener conteo de productos agotados", e);
        }
    }

    public int getActiveProductsCount() {
        try {
            return productRepository.countActiveProducts();
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener conteo de productos activos", e);
        }
    }

    public int getAlertsCount() {
        try {
            return productRepository.countLowOrOutOfStock();
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener conteo de alertas", e);
        }
    }

    public List<Sale> getRecentSales() {
        try {
            return saleRepository.findRecentActive(RECENT_SALES_LIMIT);
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener ventas recientes", e);
        }
    }
}