package com.cocolatan.presenter;

import com.cocolatan.model.Sale;
import com.cocolatan.repository.DatabaseManager;
import com.cocolatan.repository.SaleRepository;
import com.cocolatan.service.SalesService;

import java.sql.SQLException;
import java.util.List;

/**
 * Presenter for the Sale History view.
 * Loads active sales and handles cancellation requests.
 */
public class SaleHistoryPresenter {

    private final SaleRepository saleRepository;
    private final SalesService salesService;

    public SaleHistoryPresenter(DatabaseManager dbManager,
                                SaleRepository saleRepository,
                                SalesService salesService) {
        this.saleRepository = saleRepository;
        this.salesService = salesService;
    }

    /**
     * Loads all active (non-cancelled) sales sorted by date descending.
     */
    public List<Sale> loadActiveSales() {
        try {
            return saleRepository.findAllActive();
        } catch (SQLException e) {
            throw new RuntimeException("Error al cargar historial de ventas", e);
        }
    }

    /**
     * Cancels a sale with the given reason.
     * Reverts stock movements and marks the sale as CANCELLED.
     */
    public void cancelSale(Long saleId, String reason) {
        salesService.cancelSale(saleId, reason);
    }

    /**
     * Retrieves the receipt text for a given sale.
     *
     * @param saleId the sale ID
     * @return the receipt text, or null if not found
     */
    public String getReceiptText(Long saleId) {
        return salesService.getReceiptText(saleId);
    }
}
