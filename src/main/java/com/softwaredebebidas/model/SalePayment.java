package com.softwaredebebidas.model;

/**
 * One payment leg of a split-payment sale.
 * Maps to the 'sale_payments' table in SQLite.
 */
public class SalePayment {

    private final String paymentMethod;
    private final double amount;

    public SalePayment(String paymentMethod, double amount) {
        this.paymentMethod = paymentMethod;
        this.amount = amount;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public double getAmount() {
        return amount;
    }
}
