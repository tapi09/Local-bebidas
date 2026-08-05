package com.cocolatan.presenter;

import com.cocolatan.model.Sale;
import com.cocolatan.repository.ProductRepository;
import com.cocolatan.repository.SaleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomePresenterTest {

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private ProductRepository productRepository;

    private HomePresenter presenter;

    @BeforeEach
    void setUp() {
        presenter = new HomePresenter(saleRepository, productRepository);
    }

    @Test
    void getTodaySalesDelegatesToSaleRepository() throws SQLException {
        when(saleRepository.sumSalesForDate(anyString())).thenReturn(12500.0);

        double result = presenter.getTodaySales();

        assertThat(result).isEqualTo(12500.0);
        verify(saleRepository).sumSalesForDate(LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
    }

    @Test
    void getTodaySalesWrapsRepositoryError() throws SQLException {
        when(saleRepository.sumSalesForDate(anyString())).thenThrow(new SQLException("DB error"));

        assertThatThrownBy(() -> presenter.getTodaySales())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al obtener ventas del día");
    }

    @Test
    void getLowStockCountDelegatesToProductRepository() throws SQLException {
        when(productRepository.countLowStock()).thenReturn(3);

        int result = presenter.getLowStockCount();

        assertThat(result).isEqualTo(3);
        verify(productRepository).countLowStock();
    }

    @Test
    void getLowStockCountWrapsRepositoryError() throws SQLException {
        when(productRepository.countLowStock()).thenThrow(new SQLException("DB error"));

        assertThatThrownBy(() -> presenter.getLowStockCount())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al obtener conteo de stock bajo");
    }

    @Test
    void getOutOfStockCountDelegatesToProductRepository() throws SQLException {
        when(productRepository.countOutOfStock()).thenReturn(2);

        int result = presenter.getOutOfStockCount();

        assertThat(result).isEqualTo(2);
        verify(productRepository).countOutOfStock();
    }

    @Test
    void getOutOfStockCountReturnsZero() throws SQLException {
        when(productRepository.countOutOfStock()).thenReturn(0);

        int result = presenter.getOutOfStockCount();

        assertThat(result).isZero();
    }

    @Test
    void getOutOfStockCountWrapsRepositoryError() throws SQLException {
        when(productRepository.countOutOfStock()).thenThrow(new SQLException("DB error"));

        assertThatThrownBy(() -> presenter.getOutOfStockCount())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al obtener conteo de productos agotados");
    }

    @Test
    void getRecentSalesDelegatesToSaleRepository() throws SQLException {
        Sale s1 = new Sale();
        s1.setId(3L);
        s1.setTotalAmount(1500.0);
        Sale s2 = new Sale();
        s2.setId(2L);
        s2.setTotalAmount(2200.0);
        List<Sale> recent = List.of(s1, s2);
        when(saleRepository.findRecentActive(5)).thenReturn(recent);

        List<Sale> result = presenter.getRecentSales();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(3L);
        verify(saleRepository).findRecentActive(eq(5));
    }

    @Test
    void getRecentSalesReturnsEmptyList() throws SQLException {
        when(saleRepository.findRecentActive(5)).thenReturn(List.of());

        List<Sale> result = presenter.getRecentSales();

        assertThat(result).isEmpty();
    }

    @Test
    void getRecentSalesWrapsRepositoryError() throws SQLException {
        when(saleRepository.findRecentActive(5)).thenThrow(new SQLException("DB error"));

        assertThatThrownBy(() -> presenter.getRecentSales())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al obtener ventas recientes");
    }

    @Test
    void getActiveProductsCountDelegatesToProductRepository() throws SQLException {
        when(productRepository.countActiveProducts()).thenReturn(15);

        int result = presenter.getActiveProductsCount();

        assertThat(result).isEqualTo(15);
        verify(productRepository).countActiveProducts();
    }

    @Test
    void getActiveProductsCountReturnsZero() throws SQLException {
        when(productRepository.countActiveProducts()).thenReturn(0);

        int result = presenter.getActiveProductsCount();

        assertThat(result).isZero();
    }

    @Test
    void getActiveProductsCountWrapsRepositoryError() throws SQLException {
        when(productRepository.countActiveProducts()).thenThrow(new SQLException("DB error"));

        assertThatThrownBy(() -> presenter.getActiveProductsCount())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al obtener conteo de productos activos");
    }

    @Test
    void getAlertsCountDelegatesToProductRepository() throws SQLException {
        when(productRepository.countLowOrOutOfStock()).thenReturn(4);

        int result = presenter.getAlertsCount();

        assertThat(result).isEqualTo(4);
        verify(productRepository).countLowOrOutOfStock();
    }

    @Test
    void getAlertsCountReturnsZero() throws SQLException {
        when(productRepository.countLowOrOutOfStock()).thenReturn(0);

        int result = presenter.getAlertsCount();

        assertThat(result).isZero();
    }

    @Test
    void getAlertsCountWrapsRepositoryError() throws SQLException {
        when(productRepository.countLowOrOutOfStock()).thenThrow(new SQLException("DB error"));

        assertThatThrownBy(() -> presenter.getAlertsCount())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al obtener conteo de alertas");
    }
}