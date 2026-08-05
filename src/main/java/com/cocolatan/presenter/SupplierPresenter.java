package com.cocolatan.presenter;

import com.cocolatan.model.Supplier;
import com.cocolatan.repository.SupplierRepository;

import java.sql.SQLException;
import java.util.List;

/**
 * Presenter for the Supplier management module.
 * Handles CRUD logic and delegates to SupplierRepository.
 */
public class SupplierPresenter {

    private final SupplierRepository supplierRepository;

    public SupplierPresenter(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    /**
     * Loads all suppliers from the repository.
     */
    public List<Supplier> loadSuppliers() {
        try {
            return supplierRepository.findAll();
        } catch (SQLException e) {
            throw new RuntimeException("Error al cargar proveedores", e);
        }
    }

    /**
     * Saves a new supplier.
     * @return the saved Supplier with generated ID
     */
    public Supplier saveSupplier(Supplier supplier) {
        try {
            Long savedId = supplierRepository.save(supplier);
            supplier.setId(savedId);
            return supplier;
        } catch (SQLException e) {
            throw new RuntimeException("Error al guardar proveedor", e);
        }
    }

    /**
     * Updates an existing supplier.
     */
    public void updateSupplier(Supplier supplier) {
        try {
            supplierRepository.update(supplier);
        } catch (SQLException e) {
            throw new RuntimeException("Error al actualizar proveedor", e);
        }
    }

    /**
     * Deletes a supplier by ID.
     */
    public void deleteSupplier(Long id) {
        try {
            supplierRepository.delete(id);
        } catch (SQLException e) {
            throw new RuntimeException("Error al eliminar proveedor", e);
        }
    }

}
