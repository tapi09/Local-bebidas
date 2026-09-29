package com.softwaredebebidas.view;

import com.softwaredebebidas.model.User;
import com.softwaredebebidas.presenter.UserPresenter;
import com.softwaredebebidas.repository.DatabaseManager;
import com.softwaredebebidas.repository.UserRepository;
import com.softwaredebebidas.service.AuthService;
import com.softwaredebebidas.util.AlertService;
import com.softwaredebebidas.util.Refreshable;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the User management view (admin only).
 * Handles CRUD operations, password resets and the admin-role protection rules.
 */
public class UserController implements Refreshable {

    private static final Logger LOGGER = Logger.getLogger(UserController.class.getName());

    @FXML
    private TableView<User> userTable;

    @FXML
    private TableColumn<User, String> colUsername;

    @FXML
    private TableColumn<User, String> colDisplayName;

    @FXML
    private TableColumn<User, String> colRole;

    @FXML
    private TableColumn<User, String> colCreatedAt;

    @FXML
    private Button btnNew;

    @FXML
    private Button btnEdit;

    @FXML
    private Button btnChangePassword;

    @FXML
    private Button btnMasterKey;

    @FXML
    private Button btnDelete;

    private UserPresenter presenter;
    private ObservableList<User> userData;

    @FXML
    public void initialize() {
        DatabaseManager dbManager = com.softwaredebebidas.SoftwareDeBebidasApp.getDatabaseManager();
        presenter = new UserPresenter(new UserRepository(dbManager), AuthService.getInstance());

        setupTableColumns();
        loadUsers();
    }

    private void setupTableColumns() {
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colDisplayName.setCellValueFactory(new PropertyValueFactory<>("displayName"));
        colRole.setCellValueFactory(cellData -> new SimpleStringProperty(roleDisplay(cellData.getValue().getRole())));
        colCreatedAt.setCellValueFactory(new PropertyValueFactory<>("createdAt"));
    }

    /**
     * Maps the stored role code to its Spanish label shown in the UI.
     */
    static String roleDisplay(String role) {
        if ("ADMIN".equals(role)) {
            return "Administrador";
        }
        if ("CAJERO".equals(role)) {
            return "Cajero";
        }
        return "";
    }

    private void loadUsers() {
        try {
            List<User> users = presenter.loadUsers();
            userData = FXCollections.observableArrayList(users);
            userTable.setItems(userData);
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "No se pudieron cargar los usuarios", e);
            AlertService.showErrorDialog("Error", "No se pudieron cargar los usuarios.");
        }
    }

    @FXML
    private void onNew() {
        Dialog<NewUserForm> dialog = new Dialog<>();
        dialog.setTitle("Nuevo Usuario");
        dialog.setHeaderText("Ingrese los datos del nuevo usuario");

        ButtonType saveButtonType = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField usernameField = new TextField();
        usernameField.setPromptText("Nombre de usuario");
        TextField displayNameField = new TextField();
        displayNameField.setPromptText("Nombre a mostrar");
        ComboBox<String> roleField = new ComboBox<>();
        roleField.getItems().addAll("ADMIN", "CAJERO");
        roleField.setConverter(roleConverter());
        roleField.setValue("CAJERO");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Contraseña");
        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Confirmar contraseña");

        grid.add(new Label("Usuario:"), 0, 0);
        grid.add(usernameField, 1, 0);
        grid.add(new Label("Nombre:"), 0, 1);
        grid.add(displayNameField, 1, 1);
        grid.add(new Label("Rol:"), 0, 2);
        grid.add(roleField, 1, 2);
        grid.add(new Label("Contraseña:"), 0, 3);
        grid.add(passwordField, 1, 3);
        grid.add(new Label("Confirmar:"), 0, 4);
        grid.add(confirmField, 1, 4);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                return new NewUserForm(usernameField.getText(), displayNameField.getText(),
                        roleField.getValue(), passwordField.getText(), confirmField.getText());
            }
            return null;
        });

        dialog.showAndWait().ifPresent(form -> {
            try {
                presenter.createUser(form.username(), form.password(), form.confirm(), form.role(), form.displayName());
                AlertService.showInfoDialog("Éxito",
                        "Usuario creado correctamente. Deberá cambiar su contraseña en el primer inicio de sesión.");
                loadUsers();
            } catch (IllegalArgumentException e) {
                AlertService.showErrorDialog("Error de Validación", e.getMessage());
            } catch (RuntimeException e) {
                LOGGER.log(Level.SEVERE, "No se pudo crear el usuario", e);
                AlertService.showErrorDialog("Error", e.getMessage());
            }
        });
    }

    @FXML
    private void onEdit() {
        User selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertService.showWarningDialog("Seleccionar Usuario", "Seleccione un usuario para editar.");
            return;
        }

        Dialog<EditUserForm> dialog = new Dialog<>();
        dialog.setTitle("Editar Usuario");
        dialog.setHeaderText("Editar datos de " + selected.getUsername());

        ButtonType saveButtonType = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField displayNameField = new TextField(selected.getDisplayName());
        ComboBox<String> roleField = new ComboBox<>();
        roleField.getItems().addAll("ADMIN", "CAJERO");
        roleField.setConverter(roleConverter());
        roleField.setValue(selected.getRole());

        grid.add(new Label("Nombre:"), 0, 0);
        grid.add(displayNameField, 1, 0);
        grid.add(new Label("Rol:"), 0, 1);
        grid.add(roleField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                return new EditUserForm(displayNameField.getText(), roleField.getValue());
            }
            return null;
        });

        dialog.showAndWait().ifPresent(form -> {
            try {
                selected.setDisplayName(form.displayName());
                selected.setRole(form.role());
                presenter.updateUser(selected);
                AlertService.showInfoDialog("Éxito", "Usuario actualizado correctamente.");
                loadUsers();
            } catch (IllegalArgumentException e) {
                AlertService.showErrorDialog("Error de Validación", e.getMessage());
            } catch (RuntimeException e) {
                LOGGER.log(Level.SEVERE, "No se pudo actualizar el usuario", e);
                AlertService.showErrorDialog("Error", "No se pudo actualizar el usuario.");
            }
        });
    }

    @FXML
    private void onChangePassword() {
        User selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertService.showWarningDialog("Seleccionar Usuario", "Seleccione un usuario para cambiar la contraseña.");
            return;
        }

        Dialog<PasswordResetForm> dialog = new Dialog<>();
        dialog.setTitle("Cambiar Contraseña");
        dialog.setHeaderText("Nueva contraseña para " + selected.getUsername());

        ButtonType saveButtonType = new ButtonType("Cambiar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Contraseña nueva");
        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Confirmar contraseña");

        grid.add(new Label("Contraseña nueva:"), 0, 0);
        grid.add(passwordField, 1, 0);
        grid.add(new Label("Confirmar:"), 0, 1);
        grid.add(confirmField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                return new PasswordResetForm(passwordField.getText(), confirmField.getText());
            }
            return null;
        });

        dialog.showAndWait().ifPresent(form -> {
            try {
                presenter.changePassword(selected.getId(), form.password(), form.confirm());
                AlertService.showInfoDialog("Éxito",
                        "Contraseña cambiada. El usuario deberá cambiarla en su próximo inicio de sesión.");
            } catch (IllegalArgumentException e) {
                AlertService.showErrorDialog("Error de Validación", e.getMessage());
            } catch (RuntimeException e) {
                LOGGER.log(Level.SEVERE, "No se pudo cambiar la contraseña", e);
                AlertService.showErrorDialog("Error", "No se pudo cambiar la contraseña.");
            }
        });
    }

    @FXML
    private void onDelete() {
        User selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertService.showWarningDialog("Seleccionar Usuario", "Seleccione un usuario para eliminar.");
            return;
        }
        boolean confirmed = AlertService.showConfirmDialog(
                "Confirmar Eliminación",
                "¿Está seguro que desea eliminar el usuario: " + selected.getUsername() + "?"
        );
        if (confirmed) {
            try {
                presenter.deleteUser(selected.getId());
                AlertService.showInfoDialog("Éxito", "Usuario eliminado correctamente.");
                loadUsers();
            } catch (IllegalArgumentException e) {
                AlertService.showErrorDialog("Error de Validación", e.getMessage());
            } catch (RuntimeException e) {
                LOGGER.log(Level.SEVERE, "No se pudo eliminar el usuario", e);
                AlertService.showErrorDialog("Error", "No se pudo eliminar el usuario.");
            }
        }
    }

    /**
     * Opens the provider master key dialog. Shows whether recovery is currently
     * configured and lets the admin change the key (current + new + confirm).
     */
    @FXML
    private void onChangeMasterKey() {
        boolean configured = presenter.isRecoveryConfigured();

        Dialog<MasterKeyForm> dialog = new Dialog<>();
        dialog.setTitle("Clave de Recuperación");
        dialog.setHeaderText("Cambie la clave de recuperación de contraseñas.");

        ButtonType saveButtonType = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        Label statusLabel = new Label(configured ? "Configurada" : "No configurada");
        PasswordField currentField = new PasswordField();
        currentField.setPromptText("Clave de recuperación actual");
        currentField.setDisable(!configured);
        PasswordField newField = new PasswordField();
        newField.setPromptText("Clave de recuperación nueva");
        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Confirmar clave nueva");

        grid.add(new Label("Estado:"), 0, 0);
        grid.add(statusLabel, 1, 0);
        grid.add(new Label("Clave actual:"), 0, 1);
        grid.add(currentField, 1, 1);
        grid.add(new Label("Clave nueva:"), 0, 2);
        grid.add(newField, 1, 2);
        grid.add(new Label("Confirmar:"), 0, 3);
        grid.add(confirmField, 1, 3);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                return new MasterKeyForm(currentField.getText(), newField.getText(), confirmField.getText());
            }
            return null;
        });

        dialog.showAndWait().ifPresent(form -> {
            try {
                presenter.changeMasterKey(form.currentKey(), form.newKey(), form.confirm());
                AlertService.showInfoDialog("Éxito", "Clave de recuperación actualizada correctamente.");
            } catch (IllegalArgumentException e) {
                AlertService.showErrorDialog("Error de Validación", e.getMessage());
            } catch (RuntimeException e) {
                LOGGER.log(Level.SEVERE, "No se pudo actualizar la clave de recuperación", e);
                AlertService.showErrorDialog("Error", "No se pudo actualizar la clave de recuperación.");
            }
        });
    }

    private StringConverter<String> roleConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(String role) {
                return roleDisplay(role);
            }

            @Override
            public String fromString(String display) {
                if ("Administrador".equals(display)) {
                    return "ADMIN";
                }
                if ("Cajero".equals(display)) {
                    return "CAJERO";
                }
                return display;
            }
        };
    }

    @Override
    public void refresh() {
        loadUsers();
    }

    private record NewUserForm(String username, String displayName, String role, String password, String confirm) {
    }

    private record EditUserForm(String displayName, String role) {
    }

    private record PasswordResetForm(String password, String confirm) {
    }

    private record MasterKeyForm(String currentKey, String newKey, String confirm) {
    }
}
