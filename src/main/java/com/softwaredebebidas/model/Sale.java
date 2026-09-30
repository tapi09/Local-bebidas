package com.softwaredebebidas.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Sale entity representing a completed sale transaction.
 * Maps to the 'sales' table in SQLite.
 */
public class Sale {

    private Long id;
    private String saleDate;
    private String channel;
    private String paymentMethod;
    private Long customerId;
    private double discount;
    private String discountType;
    private double totalAmount;
    private String createdAt;
    private String status;
    private String cancelledAt;
    private String cancellationReason;
    private String receiptText;

    // Transient split-payment state (not mapped in SELECTs).
    // Request: set by the UI; a split is requested when splitSecondMethod != null,
    // with splitFirstAmount being the amount paid with the sale's paymentMethod.
    private String splitSecondMethod;
    private Double splitFirstAmount;
    // Result: filled by SaleRepository.saveWithItems when a split is persisted.
    private List<SalePayment> payments = new ArrayList<>();

    public Sale() {
        this.discount = 0;
        this.discountType = "NONE";
        this.status = "ACTIVE";
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSaleDate() {
        return saleDate;
    }

    public void setSaleDate(String saleDate) {
        this.saleDate = saleDate;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public double getDiscount() {
        return discount;
    }

    public void setDiscount(double discount) {
        this.discount = discount;
    }

    public String getDiscountType() {
        return discountType;
    }

    public void setDiscountType(String discountType) {
        this.discountType = discountType;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(String cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public String getReceiptText() {
        return receiptText;
    }

    public void setReceiptText(String receiptText) {
        this.receiptText = receiptText;
    }

    public String getSplitSecondMethod() {
        return splitSecondMethod;
    }

    public void setSplitSecondMethod(String splitSecondMethod) {
        this.splitSecondMethod = splitSecondMethod;
    }

    public Double getSplitFirstAmount() {
        return splitFirstAmount;
    }

    public void setSplitFirstAmount(Double splitFirstAmount) {
        this.splitFirstAmount = splitFirstAmount;
    }

    public List<SalePayment> getPayments() {
        return payments;
    }

    public void setPayments(List<SalePayment> payments) {
        this.payments = payments;
    }
}
