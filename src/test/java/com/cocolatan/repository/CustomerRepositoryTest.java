package com.cocolatan.repository;

import com.cocolatan.model.Customer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CustomerRepository")
class CustomerRepositoryTest {

    private DatabaseManager dbManager;
    private CustomerRepository repository;

    @BeforeEach
    void setUp() {
        dbManager = DatabaseManager.createInMemory();
        repository = new CustomerRepository(dbManager);
    }

    @AfterEach
    void tearDown() {
        dbManager.close();
    }

    @Test
    @DisplayName("save() retorna ID generado para un cliente nuevo")
    void saveReturnsId() throws SQLException {
        Customer customer = createCustomer("Juan Pérez");
        Long id = repository.save(customer);

        assertThat(id).isNotNull();
        assertThat(id).isGreaterThan(0);
    }

    @Test
    @DisplayName("findById() retorna el cliente guardado")
    void findByIdReturnsSavedCustomer() throws SQLException {
        Customer customer = createCustomer("Juan Pérez");
        Long id = repository.save(customer);

        Optional<Customer> found = repository.findById(id);

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Juan Pérez");
    }

    @Test
    @DisplayName("findById() retorna empty para ID inexistente")
    void findByIdReturnsEmptyForNonexistent() throws SQLException {
        Optional<Customer> found = repository.findById(999L);

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("findAll() retorna todos los clientes ordenados por nombre")
    void findAllReturnsAllCustomers() throws SQLException {
        repository.save(createCustomer("Zulema López"));
        repository.save(createCustomer("Ana Gómez"));
        repository.save(createCustomer("Carlos Ruiz"));

        List<Customer> customers = repository.findAll();

        assertThat(customers).hasSize(3);
        assertThat(customers.get(0).getName()).isEqualTo("Ana Gómez");
        assertThat(customers.get(1).getName()).isEqualTo("Carlos Ruiz");
        assertThat(customers.get(2).getName()).isEqualTo("Zulema López");
    }

    @Test
    @DisplayName("findAll() retorna lista vacía cuando no hay clientes")
    void findAllReturnsEmptyWhenNoCustomers() throws SQLException {
        List<Customer> customers = repository.findAll();

        assertThat(customers).isEmpty();
    }

    @Test
    @DisplayName("update() modifica los campos del cliente")
    void updateModifiesCustomer() throws SQLException {
        Customer customer = createCustomer("Juan Pérez");
        Long id = repository.save(customer);

        customer.setId(id);
        customer.setName("Juan Pérez Modificado");
        customer.setPhone("+54 261 555-9999");
        customer.setEmail("juan@modificado.com");
        customer.setAddress("Nueva Dirección 456");
        repository.update(customer);

        Optional<Customer> found = repository.findById(id);
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Juan Pérez Modificado");
        assertThat(found.get().getPhone()).isEqualTo("+54 261 555-9999");
        assertThat(found.get().getEmail()).isEqualTo("juan@modificado.com");
        assertThat(found.get().getAddress()).isEqualTo("Nueva Dirección 456");
    }

    @Test
    @DisplayName("delete() elimina el cliente")
    void deleteRemovesCustomer() throws SQLException {
        Customer customer = createCustomer("Juan Pérez");
        Long id = repository.save(customer);

        repository.delete(id);

        Optional<Customer> found = repository.findById(id);
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("searchByName() encuentra clientes por búsqueda parcial")
    void searchByNameFindsMatchingCustomers() throws SQLException {
        repository.save(createCustomer("Juan Perez"));
        repository.save(createCustomer("Maria Garcia"));
        repository.save(createCustomer("Carlos Lopez"));

        List<Customer> results = repository.searchByName("perez");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Juan Perez");
    }

    @Test
    @DisplayName("searchByName() retorna vacío cuando no hay match")
    void searchByNameReturnsEmptyForNoMatch() throws SQLException {
        repository.save(createCustomer("Juan Perez"));

        List<Customer> results = repository.searchByName("ZZZZ");

        assertThat(results).isEmpty();
    }

    private Customer createCustomer(String name) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setPhone("+54 261 555-1234");
        customer.setEmail("email@" + name.toLowerCase().replace(" ", "") + ".com");
        customer.setAddress("Mendoza 1234");
        return customer;
    }
}
