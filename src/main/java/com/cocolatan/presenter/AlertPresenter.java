package com.cocolatan.presenter;

import com.cocolatan.service.AlertService;

import java.util.List;

/**
 * Presenter for the Alerts module.
 * Handles alert management, badge counts, dismissal.
 */
public class AlertPresenter {

    private final AlertService alertService;

    public AlertPresenter(AlertService alertService) {
        this.alertService = alertService;
    }

    /**
     * Returns all current alerts (expiry + low stock).
     */
    public List<AlertService.Alert> getAllAlerts() {
        return alertService.getAllAlerts();
    }

    /**
     * Returns expiry alerts only.
     */
    public List<AlertService.Alert> getExpiryAlerts() {
        return alertService.getExpiryAlerts();
    }

    /**
     * Returns low-stock alerts only.
     */
    public List<AlertService.Alert> getLowStockAlerts() {
        return alertService.getLowStockAlerts();
    }

    /**
     * Returns total alert count for badge display.
     */
    public int getAlertCount() {
        return alertService.getAlertCount();
    }

    /**
     * Dismisses an alert.
     */
    public void dismissAlert(AlertService.Alert alert) {
        alert.dismiss();
    }

    /**
     * Returns alert history.
     */
    public List<AlertService.Alert> getAlertHistory() {
        return alertService.getAlertHistory();
    }

    /**
     * Returns count of expiry alerts.
     */
    public int getExpiryAlertCount() {
        return getExpiryAlerts().size();
    }

    /**
     * Returns count of low-stock alerts.
     */
    public int getLowStockAlertCount() {
        return getLowStockAlerts().size();
    }
}
