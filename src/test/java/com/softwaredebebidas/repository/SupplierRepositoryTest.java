package com.softwaredebebidas.repository;

import com.softwaredebebidas.model.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierRepositoryTest {

    private DatabaseManager dbManager;
    private SupplierRepository repository;

    @BeforeEach
    void setUp() {
        dbManager = DatabaseManager.createInMemory();
        repository = new SupplierRepository(dbManager);
    }

    @AfterEach
    void tearDown() {
        dbManager.close();
    }

    @Test
    void saveReturnsId() throws SQLException {
        Supplier supplier = createSupplier("Distribuidora Norte");
        Long id = repository.save(supplier);
        assertThat(id).isNotNull();
        assertThat(id).isGreaterThan(0);
    }

    @Test
    void findByIdReturnsSavedSupplier() throws SQLException {
        Supplier supplier = createSupplier("Distribuidora Norte");
        Long id = repository.save(supplier);

        Optional<Supplier> found = repository.findById(id);

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Distribuidora Norte");
    }

    @Test
    void findByIdReturnsEmptyForNonexistent() throws SQLException {
        Optional<Supplier> found = repository.findById(999L);
        assertThat(found).isEmpty();
    }

    @Test
    void findAllReturnsAllSuppliers() throws SQLException {
        repository.save(createSupplier("Supplier A"));
        repository.save(createSupplier("Supplier B"));
        repository.save(createSupplier("Supplier C"));

        List<Supplier> suppliers = repository.findAll();

        assertThat(suppliers).hasSize(3);
    }

    @Test
    void findAllReturnsEmptyWhenNoSuppliers() throws SQLException {
        List<Supplier> suppliers = repository.findAll();
        assertThat(suppliers).isEmpty();
    }

    @Test
    void updateModifiesSupplier() throws SQLException {
        Supplier supplier = createSupplier("Original Name");
        Long id = repository.save(supplier);

        supplier.setId(id);
        supplier.setName("Updated Name");
        repository.update(supplier);

        Optional<Supplier> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Updated Name");
    }

    @Test
    void deleteRemovesSupplier() throws SQLException {
        Supplier supplier = createSupplier("To Delete");
        Long id = repository.save(supplier);

        repository.delete(id);

        Optional<Supplier> found = repository.findById(id);
        assertThat(found).isEmpty();
    }

    @Test
    void findForDropdownReturnsNameAndId() throws SQLException {
        repository.save(createSupplier("Distribuidora Norte"));
        repository.save(createSupplier("Bebidas del Sur"));

        List<Supplier> dropdown = repository.findForDropdown();

        assertThat(dropdown).hasSize(2);
        assertThat(dropdown).extracting(Supplier::getName)
                .containsExactlyInAnyOrder("Bebidas del Sur", "Distribuidora Norte");
    }

    private Supplier createSupplier(String name) {
        Supplier supplier = new Supplier();
        supplier.setName(name);
        supplier.setContact("Contact Person");
        supplier.setPhone("+54 261 555-1234");
        supplier.setEmail("contact@" + name.toLowerCase().replace(" ", "") + ".com");
        supplier.setAddress("Mendoza 1234");
        return supplier;
    }
}
