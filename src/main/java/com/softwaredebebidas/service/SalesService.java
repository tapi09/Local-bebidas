package com.softwaredebebidas.service;

import com.softwaredebebidas.model.Product;
import com.softwaredebebidas.model.Sale;
import com.softwaredebebidas.model.SaleItem;
import com.softwaredebebidas.model.StockMovement;
import com.softwaredebebidas.repository.DatabaseManager;
import com.softwaredebebidas.repository.ProductRepository;
import com.softwaredebebidas.repository.SaleRepository;
import com.softwaredebebidas.repository.StockMovementRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for sale operations: sale creation, stock/expiry validation, receipt generation.
 * Orchestrates the full sale flow including stock decrement.
 */
public class SalesService {

    private static final Logger LOGGER = Logger.getLogger(SalesService.class.getName());

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_ONLY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final DatabaseManager databaseManager;
    private final SaleRepository saleRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final ReceiptService receiptService;

    public SalesService(SaleRepository saleRepository,
                        StockMovementRepository stockMovementRepository,
                        ProductRepository productRepository,
                        InventoryService inventoryService,
                        ReceiptService receiptService,
                        DatabaseManager databaseManager) {
        this.saleRepository = saleRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.receiptService = receiptService;
        this.databaseManager = databaseManager;
    }

    /**
     * Creates a sale with stock validation and expiry check.
     * Creates stock_movements EXIT for each item.
     * Updates denormalized current_stock column in products table.
     * The entire operation (sale + stock movements + stock cache) is wrapped in a single
     * SQL transaction for atomicity.
     *
     * @return true if sale was created successfully
     * @throws ValidationException if stock is insufficient or product is expired
     */
    public boolean createSale(Sale sale, List<SaleItem> items) {
        if (items == null || items.isEmpty()) {
            throw new ValidationException("Debe agregar al menos un producto a la venta.");
        }

        Connection conn = databaseManager.getConnection();
        boolean originalAutoCommit = true;
        try {
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            // Validate stock and expiry for each item
            for (SaleItem item : items) {
                if (!inventoryService.validateStock(item.getProductId(), item.getQuantity())) {
                    int available = inventoryService.getAvailableStock(item.getProductId());
                    Product product = productRepository.findById(item.getProductId()).orElse(null);
                    String productName = product != null ? product.getName() : "Producto #" + item.getProductId();
                    throw new ValidationException(
                            "Stock insuficiente para " + productName + ". Disponible: " + available);
                }
            }

            // Set sale date if not set (ISO-8601 format)
            if (sale.getSaleDate() == null || sale.getSaleDate().isEmpty()) {
                sale.setSaleDate(LocalDateTime.now().format(DATE_ONLY_FORMATTER));
            }

            Long saleId = saleRepository.saveWithItems(conn, sale, items);
            sale.setId(saleId);

            // Create stock movements EXIT for each item and update denormalized current_stock
            for (SaleItem item : items) {
                StockMovement movement = new StockMovement();
                movement.setProductId(item.getProductId());
                movement.setMovementType("EXIT");
                movement.setQuantity(item.getQuantity());
                movement.setReferenceType("SALE");
                movement.setReferenceId(saleId);
                movement.setNotes("Venta #" + saleId);
                stockMovementRepository.insert(conn, movement);

                // Update denormalized current_stock cache (negative delta for sale)
                productRepository.updateStock(conn, item.getProductId(), -item.getQuantity());
            }

            conn.commit();

            // Save receipt text (best-effort — sale already committed)
            try {
                String receiptText = receiptService.generateReceipt(sale, items);
                saleRepository.updateReceipt(conn, saleId, receiptText);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to save receipt for sale #" + saleId, e);
            }

            return true;
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ex) { /* ignore */ }
            throw new RuntimeException("Error al procesar venta", e);
        } finally {
            try { conn.setAutoCommit(originalAutoCommit); } catch (SQLException e) { /* ignore */ }
        }
    }

    /**
     * Cancels a sale by reverting stock movements and marking the sale as CANCELLED.
     * Creates StockMovement(ENTRY) for each item sold and updates sale status.
     * Updates denormalized current_stock column in products table.
     * The entire operation is wrapped in a single SQL transaction for atomicity.
     * The sale re-read and the CANCELLED status check happen INSIDE the transaction
     * using the transactional connection, so concurrent double-cancellation attempts
     * are serialized: only the first creates stock movements, the second rolls back
     * and throws IllegalStateException.
     *
     * @param saleId the ID of the sale to cancel
     * @param reason the reason for cancellation
     * @throws RuntimeException        if sale not found or a SQL error occurs
     * @throws IllegalStateException   if the sale is already cancelled (idempotent)
     */
    public void cancelSale(Long saleId, String reason) {
        Connection conn = databaseManager.getConnection();
        boolean originalAutoCommit = true;
        try {
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false); // Transaction START

            // Transactional re-read: sale must exist and not be already cancelled
            Sale sale = saleRepository.findById(conn, saleId)
                    .orElseThrow(() -> {
                        try { conn.rollback(); } catch (SQLException ex) { /* ignore */ }
                        return new RuntimeException("Venta no encontrada: " + saleId);
                    });

            if ("CANCELLED".equals(sale.getStatus())) {
                try { conn.rollback(); } catch (SQLException ex) { /* ignore */ }
                throw new IllegalStateException("Sale already cancelled");
            }

            List<SaleItem> items = saleRepository.findItemsBySaleId(conn, saleId);

            // Revert stock: create ENTRY movements for each item sold
            for (SaleItem item : items) {
                StockMovement entry = new StockMovement();
                entry.setProductId(item.getProductId());
                entry.setMovementType("ENTRY");
                entry.setQuantity(item.getQuantity());
                entry.setReferenceType("SALE");
                entry.setReferenceId(saleId);
                String notes = "Devolución por cancelación #" + saleId;
                if (reason != null && !reason.trim().isEmpty()) {
                    notes += " — " + reason.trim();
                }
                entry.setNotes(notes);
                stockMovementRepository.insert(conn, entry);

                // Update denormalized current_stock cache (positive delta for cancellation)
                productRepository.updateStock(conn, item.getProductId(), item.getQuantity());
            }

            // Update sale status
            String now = LocalDateTime.now().format(DATE_FORMATTER);
            saleRepository.updateStatus(conn, saleId, "CANCELLED", now, reason);

            conn.commit(); // Transaction END
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ex) { /* ignore */ }
            throw new RuntimeException("Error al anular venta #" + saleId, e);
        } finally {
            try { conn.setAutoCommit(originalAutoCommit); } catch (SQLException e) { /* ignore */ }
        }
    }

    /**
     * Generates a text receipt for a completed sale.
     * Delegates to ReceiptService.
     */
    public String generateReceipt(Sale sale, List<SaleItem> items) {
        return receiptService.generateReceipt(sale, items);
    }

    /**
     * Retrieves the saved receipt text for a given sale.
     *
     * @return the receipt text, or null if not found or no receipt saved
     */
    public String getReceiptText(Long saleId) {
        try {
            return saleRepository.findById(saleId)
                    .map(Sale::getReceiptText)
                    .orElse(null);
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener comprobante de venta #" + saleId, e);
        }
    }

    /**
     * Exception thrown for business validation errors.
     */
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }
}
