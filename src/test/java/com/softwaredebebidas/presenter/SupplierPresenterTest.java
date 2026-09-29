package com.softwaredebebidas.presenter;

import com.softwaredebebidas.model.Supplier;
import com.softwaredebebidas.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
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
class SupplierPresenterTest {

    @Mock
    private SupplierRepository supplierRepository;

    private SupplierPresenter presenter;

    @BeforeEach
    void setUp() {
        presenter = new SupplierPresenter(supplierRepository);
    }

    // --- loadSuppliers ---

    @Test
    void loadSuppliersReturnsList() throws SQLException {
        List<Supplier> suppliers = Arrays.asList(createSupplier("Dist A"), createSupplier("Dist B"));
        when(supplierRepository.findAll()).thenReturn(suppliers);

        List<Supplier> result = presenter.loadSuppliers();

        assertThat(result).hasSize(2);
        verify(supplierRepository).findAll();
    }

    @Test
    void loadSuppliersReturnsEmptyList() throws SQLException {
        when(supplierRepository.findAll()).thenReturn(Collections.emptyList());

        List<Supplier> result = presenter.loadSuppliers();

        assertThat(result).isEmpty();
    }

    @Test
    void loadSuppliersThrowsRuntimeExceptionOnSqlException() throws SQLException {
        when(supplierRepository.findAll()).thenThrow(new SQLException("DB error"));

        assertThatThrownBy(() -> presenter.loadSuppliers())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al cargar proveedores");
    }

    // --- saveSupplier ---

    @Test
    void saveSupplierReturnsSavedSupplierWithGeneratedId() throws SQLException {
        Supplier supplier = createSupplier("Dist A");
        when(supplierRepository.save(any(Supplier.class))).thenReturn(42L);

        Supplier result = presenter.saveSupplier(supplier);

        assertThat(result.getId()).isEqualTo(42L);
        assertThat(supplier.getId()).isEqualTo(42L);
        verify(supplierRepository).save(supplier);
    }

    @Test
    void saveSupplierPreservesFieldsOnReturn() throws SQLException {
        Supplier supplier = createSupplier("Dist A");
        when(supplierRepository.save(any(Supplier.class))).thenReturn(99L);

        Supplier result = presenter.saveSupplier(supplier);

        assertThat(result.getName()).isEqualTo("Dist A");
        assertThat(result.getId()).isEqualTo(99L);
    }

    @Test
    void saveSupplierThrowsRuntimeExceptionOnSqlException() throws SQLException {
        when(supplierRepository.save(any(Supplier.class))).thenThrow(new SQLException("DB error"));

        assertThatThrownBy(() -> presenter.saveSupplier(new Supplier()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al guardar proveedor");
    }

    // --- updateSupplier ---

    @Test
    void updateSupplierDelegatesToRepository() throws SQLException {
        Supplier supplier = createSupplier("Dist A");
        supplier.setId(1L);

        presenter.updateSupplier(supplier);

        verify(supplierRepository).update(supplier);
    }

    @Test
    void updateSupplierThrowsRuntimeExceptionOnSqlException() throws SQLException {
        doThrow(new SQLException("DB error")).when(supplierRepository).update(any(Supplier.class));

        assertThatThrownBy(() -> presenter.updateSupplier(new Supplier()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al actualizar proveedor");
    }

    // --- deleteSupplier ---

    @Test
    void deleteSupplierDelegatesToRepository() throws SQLException {
        presenter.deleteSupplier(1L);

        verify(supplierRepository).delete(1L);
    }

    @Test
    void deleteSupplierThrowsRuntimeExceptionOnSqlException() throws SQLException {
        doThrow(new SQLException("DB error")).when(supplierRepository).delete(1L);

        assertThatThrownBy(() -> presenter.deleteSupplier(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Error al eliminar proveedor");
    }

    private Supplier createSupplier(String name) {
        Supplier supplier = new Supplier();
        supplier.setName(name);
        supplier.setContact("Contacto");
        supplier.setPhone("123456789");
        supplier.setEmail("test@test.com");
        return supplier;
    }
}
