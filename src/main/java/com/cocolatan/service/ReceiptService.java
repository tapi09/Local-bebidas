package com.cocolatan.service;

import com.cocolatan.model.Product;
import com.cocolatan.model.Sale;
import com.cocolatan.model.SaleItem;
import com.cocolatan.repository.ProductRepository;

import java.util.List;

/**
 * Service for generating text receipts for completed sales.
 * Produces formatted receipts with store name, items, totals, channel, and payment info.
 */
public class ReceiptService {

    private static final String STORE_NAME = "Cocolatán - Central de Bebidas";
    private static final String DIVIDER = "================================";

    private final ProductRepository productRepository;

    public ReceiptService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Generates a formatted text receipt for a completed sale.
     *
     * @param sale  the completed sale
     * @param items the sale items
     * @return formatted receipt text
     */
    public String generateReceipt(Sale sale, List<SaleItem> items) {
        StringBuilder receipt = new StringBuilder();
        receipt.append(DIVIDER).append("\n");
        receipt.append("      ").append(STORE_NAME).append("\n");
        receipt.append(DIVIDER).append("\n");
        receipt.append("Fecha: ").append(sale.getSaleDate()).append("\n");
        receipt.append("Canal: ").append(formatChannel(sale.getChannel())).append("\n");
        receipt.append("Pago: ").append(formatPaymentMethod(sale.getPaymentMethod())).append("\n");
        receipt.append("--------------------------------\n");

        boolean hasDiscounts = false;
        for (SaleItem item : items) {
            String displayName = resolveProductDisplayName(item.getProductId());
            String line = String.format("%s x%d  $%,.2f  $%,.2f%n",
                    displayName, item.getQuantity(), item.getUnitPrice(), item.getSubtotal());
            receipt.append(line);
            if (!"NONE".equals(item.getDiscountType()) && item.getDiscount() > 0) {
                receipt.append(String.format("  Descuento: %.0f%%  -$%,.2f%n",
                        item.getDiscount(), item.getSubtotal() * item.getDiscount() / 100));
                hasDiscounts = true;
            }
        }

        receipt.append("--------------------------------\n");
        if (!"NONE".equals(sale.getDiscountType()) && sale.getDiscount() > 0) {
            receipt.append(String.format("Descuento venta: %.0f%%%n", sale.getDiscount()));
            hasDiscounts = true;
        }
        receipt.append(String.format("TOTAL: $%,.2f%n", sale.getTotalAmount()));
        receipt.append(DIVIDER).append("\n");
        receipt.append("¡Gracias por su compra!\n");

        return receipt.toString();
    }

    private String resolveProductDisplayName(Long productId) {
        try {
            return productRepository.findById(productId)
                    .map(product -> {
                        String name = product.getName();
                        String label = product.getHierarchyLabel();
                        return label.isEmpty() ? name : name + " — " + label;
                    })
                    .orElse("Producto #" + productId);
        } catch (Exception e) {
            return "Producto #" + productId;
        }
    }

    private String formatChannel(String channel) {
        return switch (channel) {
            case "IN" -> "Local";
            case "PEDIDOSYA" -> "PedidosYa";
            default -> channel;
        };
    }

    private String formatPaymentMethod(String method) {
        return switch (method) {
            case "CASH" -> "Efectivo";
            case "CREDIT_CARD" -> "Tarjeta de Crédito";
            case "DEBIT_CARD" -> "Tarjeta de Débito";
            case "TRANSFER" -> "Transferencia";
            case "MIXED" -> "Mixto";
            default -> method;
        };
    }
}
