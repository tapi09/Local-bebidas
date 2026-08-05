package com.cocolatan.presenter;

import com.cocolatan.model.Product;
import com.cocolatan.service.AlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertPresenterTest {

    @Mock
    private AlertService alertService;

    private AlertPresenter presenter;

    @BeforeEach
    void setUp() {
        presenter = new AlertPresenter(alertService);
    }

    @Test
    void getAllAlertsDelegatesToService() {
        List<AlertService.Alert> alerts = Arrays.asList(
                createAlert("EXPIRED"),
                createAlert("LOW_STOCK")
        );
        when(alertService.getAllAlerts()).thenReturn(alerts);

        List<AlertService.Alert> result = presenter.getAllAlerts();

        assertThat(result).hasSize(2);
    }

    @Test
    void getExpiryAlertsDelegatesToService() {
        List<AlertService.Alert> alerts = Collections.singletonList(createAlert("EXPIRING_SOON"));
        when(alertService.getExpiryAlerts()).thenReturn(alerts);

        List<AlertService.Alert> result = presenter.getExpiryAlerts();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo("EXPIRING_SOON");
    }

    @Test
    void getLowStockAlertsDelegatesToService() {
        List<AlertService.Alert> alerts = Arrays.asList(
                createAlert("LOW_STOCK"),
                createAlert("OUT_OF_STOCK")
        );
        when(alertService.getLowStockAlerts()).thenReturn(alerts);

        List<AlertService.Alert> result = presenter.getLowStockAlerts();

        assertThat(result).hasSize(2);
    }

    @Test
    void getAlertCountReturnsTotal() {
        when(alertService.getAlertCount()).thenReturn(5);

        int count = presenter.getAlertCount();

        assertThat(count).isEqualTo(5);
    }

    @Test
    void dismissAlertSetsDismissedFlag() {
        AlertService.Alert alert = createAlert("LOW_STOCK");

        presenter.dismissAlert(alert);

        assertThat(alert.isDismissed()).isTrue();
    }

    @Test
    void getExpiryAlertCountReturnsCorrectCount() {
        List<AlertService.Alert> alerts = Arrays.asList(
                createAlert("EXPIRED"),
                createAlert("EXPIRING_SOON"),
                createAlert("EXPIRED")
        );
        when(alertService.getExpiryAlerts()).thenReturn(alerts);

        int count = presenter.getExpiryAlertCount();

        assertThat(count).isEqualTo(3);
    }

    @Test
    void getLowStockAlertCountReturnsCorrectCount() {
        List<AlertService.Alert> alerts = Arrays.asList(
                createAlert("LOW_STOCK"),
                createAlert("OUT_OF_STOCK")
        );
        when(alertService.getLowStockAlerts()).thenReturn(alerts);

        int count = presenter.getLowStockAlertCount();

        assertThat(count).isEqualTo(2);
    }

    @Test
    void getAlertHistoryDelegatesToService() {
        List<AlertService.Alert> history = Arrays.asList(
                createAlert("EXPIRED"),
                createAlert("LOW_STOCK")
        );
        when(alertService.getAlertHistory()).thenReturn(history);

        List<AlertService.Alert> result = presenter.getAlertHistory();

        assertThat(result).hasSize(2);
    }

    private AlertService.Alert createAlert(String type) {
        Product product = new Product();
        product.setId(1L);
        product.setName("Test Product");
        return new AlertService.Alert(type, product, "LOT-001", "22/07/2026", 5);
    }
}
