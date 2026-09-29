package com.softwaredebebidas.model;

import java.util.List;

public class DailySalesDetailReport {

    private List<DailySalesDetailRow> rows;
    private double grandTotal;
    private String fromDate;
    private String toDate;

    public DailySalesDetailReport() {
    }

    public List<DailySalesDetailRow> getRows() {
        return rows;
    }

    public void setRows(List<DailySalesDetailRow> rows) {
        this.rows = rows;
    }

    public double getGrandTotal() {
        return grandTotal;
    }

    public void setGrandTotal(double grandTotal) {
        this.grandTotal = grandTotal;
    }

    public String getFromDate() {
        return fromDate;
    }

    public void setFromDate(String fromDate) {
        this.fromDate = fromDate;
    }

    public String getToDate() {
        return toDate;
    }

    public void setToDate(String toDate) {
        this.toDate = toDate;
    }
}