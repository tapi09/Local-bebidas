package com.cocolatan.presenter;

import com.cocolatan.model.Sale;
import com.cocolatan.repository.DatabaseManager;
import com.cocolatan.repository.SaleRepository;
import com.cocolatan.service.SalesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SaleHistoryPresenterTest {

    @Mock
    private DatabaseManager dbManager;

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private SalesService salesService;

    private SaleHistoryPresenter presenter;

    @BeforeEach
    void setUp() {
        presenter = new SaleHistoryPresenter(dbManager, saleRepository, salesService);
    }

    @Test
    @DisplayName("loadActiveSales devuelve lista del repositorio")
    void loadActiveSalesReturnsFromRepository() throws SQLException {
        Sale sale = new Sale();
        sale.setId(1L);
        sale.setChannel("IN");
        sale.setStatus("ACTIVE");

        when(saleRepository.findAllActive()).thenReturn(Collections.singletonList(sale));

        List<Sale> result = presenter.loadActiveSales();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("loadActiveSales lanza RuntimeException si SQLException")
    void loadActiveSalesThrowsOnSqlException() throws SQLException {
        when(saleRepository.findAllActive()).thenThrow(new SQLException("DB error"));

        assertThatThrownBy(() -> presenter.loadActiveSales())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al cargar historial");
    }

    @Test
    @DisplayName("cancelSale delega en SalesService")
    void cancelSaleDelegatesToService() {
        presenter.cancelSale(1L, "Motivo test");

        verify(salesService).cancelSale(1L, "Motivo test");
    }

    @Test
    @DisplayName("cancelSale con motivo vacío delega correctamente")
    void cancelSaleWithEmptyReasonDelegates() {
        presenter.cancelSale(2L, "");

        verify(salesService).cancelSale(2L, "");
    }
}
