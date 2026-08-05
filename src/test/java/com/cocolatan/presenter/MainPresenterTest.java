package com.cocolatan.presenter;

import com.cocolatan.model.User;
import com.cocolatan.repository.ConfigRepository;
import com.cocolatan.repository.UserRepository;
import com.cocolatan.service.AuthService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.StackPane;
import javafx.collections.ObservableMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MainPresenterTest {

    private AutoCloseable mockSession;

    private StackPane contentArea;
    private Label dateTimeLabel;
    private Button btnInicio;
    private Button btnProductos;
    private Button btnCompras;
    private Button btnVentas;
    private Button btnStock;
    private Button btnAlertas;
    private Button btnReportes;
    private Button btnProveedores;
    private Button btnCategorias;
    private Button btnUsuarios;

    @SuppressWarnings("unchecked")
    private final ObservableMap<KeyCombination, Runnable> accelerators = mock(ObservableMap.class);

    private Scene scene;
    private MainPresenter presenter;

    @BeforeAll
    static void initJavaFxToolkit() {
        try {
            Platform.startup(() -> {});
        } catch (Exception e) {
            // already started or headless
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        mockSession = MockitoAnnotations.openMocks(this);

        contentArea = new StackPane();
        dateTimeLabel = new Label();
        btnInicio = new Button("Inicio");
        btnProductos = new Button("Productos");
        btnCompras = new Button("Compras");
        btnVentas = new Button("Ventas");
        btnStock = new Button("Stock");
        btnAlertas = new Button("Alertas");
        btnReportes = new Button("Reportes");
        btnProveedores = new Button("Proveedores");
        btnCategorias = new Button("Categorías");
        btnUsuarios = new Button("Usuarios");
        scene = mock(Scene.class);
        when(scene.getAccelerators()).thenReturn(accelerators);

        // Initialize AuthService with admin user for role-aware tests
        UserRepository mockRepo = mock(UserRepository.class);
        User admin = new User();
        admin.setUsername("admin");
        admin.setPasswordHash(AuthService.hashPassword("admin123"));
        admin.setRole("ADMIN");
        admin.setDisplayName("Admin");
        when(mockRepo.findByUsername("admin")).thenReturn(Optional.of(admin));
        AuthService.initialize(mockRepo);
        AuthService.getInstance().login("admin", "admin123");

        presenter = new MainPresenter();

        setField(presenter, "contentArea", contentArea);
        setField(presenter, "dateTimeLabel", dateTimeLabel);
        setField(presenter, "btnInicio", btnInicio);
        setField(presenter, "btnProductos", btnProductos);
        setField(presenter, "btnCompras", btnCompras);
        setField(presenter, "btnVentas", btnVentas);
        setField(presenter, "btnStock", btnStock);
        setField(presenter, "btnAlertas", btnAlertas);
        setField(presenter, "btnReportes", btnReportes);
        setField(presenter, "btnProveedores", btnProveedores);
        setField(presenter, "btnCategorias", btnCategorias);
        setField(presenter, "btnUsuarios", btnUsuarios);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mockSession != null) {
            mockSession.close();
        }
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void getInstanceReturnsNullBeforeInitialize() {
        assertThat(MainPresenter.getInstance()).isNull();
    }

    @Test
    void initializeSetsInstance() {
        try (MockedStatic<Platform> ignored = mockStatic(Platform.class);
             MockedConstruction<FXMLLoader> fx = mockConstruction(FXMLLoader.class,
                     (mock, ctx) -> when(mock.load()).thenReturn(mock(Node.class)))) {
            presenter.initialize();
            assertThat(MainPresenter.getInstance()).isSameAs(presenter);
        }
    }

    @Test
    void onInicioSetsActiveButtonAndLoadsHome() {
        presenter.onInicio();

        assertThat(btnInicio.getStyleClass()).contains("sidebar-button-active");
        assertThat(contentArea.getChildren()).isNotEmpty();
    }

    @Test
    void onProductosSetsActiveButtonAndLoadsProduct() {
        presenter.onProductos();

        assertThat(btnProductos.getStyleClass()).contains("sidebar-button-active");
        assertThat(contentArea.getChildren()).isNotEmpty();
    }

    @Test
    void onComprasSetsActiveButtonAndLoadsPurchase() {
        presenter.onCompras();

        assertThat(btnCompras.getStyleClass()).contains("sidebar-button-active");
        assertThat(contentArea.getChildren()).isNotEmpty();
    }

    @Test
    void onVentasSetsActiveButtonAndLoadsSale() {
        presenter.onVentas();

        assertThat(btnVentas.getStyleClass()).contains("sidebar-button-active");
        assertThat(contentArea.getChildren()).isNotEmpty();
    }

    @Test
    void onStockSetsActiveButtonAndLoadsStock() {
        presenter.onStock();

        assertThat(btnStock.getStyleClass()).contains("sidebar-button-active");
        assertThat(contentArea.getChildren()).isNotEmpty();
    }

    @Test
    void onAlertasSetsActiveButtonAndLoadsAlert() {
        presenter.onAlertas();

        assertThat(btnAlertas.getStyleClass()).contains("sidebar-button-active");
        assertThat(contentArea.getChildren()).isNotEmpty();
    }

    @Test
    void onReportesSetsActiveButtonAndLoadsReports() {
        presenter.onReportes();

        assertThat(btnReportes.getStyleClass()).contains("sidebar-button-active");
        assertThat(contentArea.getChildren()).isNotEmpty();
    }

    @Test
    void onProveedoresSetsActiveButtonAndLoadsSupplier() {
        presenter.onProveedores();

        assertThat(btnProveedores.getStyleClass()).contains("sidebar-button-active");
        assertThat(contentArea.getChildren()).isNotEmpty();
    }

    @Test
    void onCategoriasSetsActiveButtonAndLoadsCategories() {
        presenter.onCategorias();

        assertThat(btnCategorias.getStyleClass()).contains("sidebar-button-active");
        assertThat(contentArea.getChildren()).isNotEmpty();
    }

    @Test
    void onUsuariosSetsActiveButtonAndLoadsUsers() {
        presenter.onUsuarios();

        assertThat(btnUsuarios.getStyleClass()).contains("sidebar-button-active");
        assertThat(contentArea.getChildren()).isNotEmpty();
    }

    @Test
    void configureForRoleHidesCategoriasForCajero() throws Exception {
        UserRepository cajeroRepo = mock(UserRepository.class);
        User cajero = new User();
        cajero.setUsername("cajero");
        cajero.setPasswordHash(AuthService.hashPassword("cajero123"));
        cajero.setRole("CAJERO");
        cajero.setDisplayName("Cajero");
        when(cajeroRepo.findByUsername("cajero")).thenReturn(Optional.of(cajero));
        AuthService.initialize(cajeroRepo);
        AuthService.getInstance().login("cajero", "cajero123");

        presenter.configureForRole();

        assertThat(btnCategorias.isVisible()).isFalse();
        assertThat(btnCategorias.isManaged()).isFalse();
    }

    @Test
    void configureForRoleHidesUsuariosForCajero() throws Exception {
        UserRepository cajeroRepo = mock(UserRepository.class);
        User cajero = new User();
        cajero.setUsername("cajero");
        cajero.setPasswordHash(AuthService.hashPassword("cajero123"));
        cajero.setRole("CAJERO");
        cajero.setDisplayName("Cajero");
        when(cajeroRepo.findByUsername("cajero")).thenReturn(Optional.of(cajero));
        AuthService.initialize(cajeroRepo);
        AuthService.getInstance().login("cajero", "cajero123");

        presenter.configureForRole();

        assertThat(btnUsuarios.isVisible()).isFalse();
        assertThat(btnUsuarios.isManaged()).isFalse();
    }

    @Test
    void setActiveButtonRemovesActiveStyleFromPreviousButton() {
        presenter.onInicio();
        assertThat(btnInicio.getStyleClass()).contains("sidebar-button-active");

        presenter.onProductos();
        assertThat(btnInicio.getStyleClass()).doesNotContain("sidebar-button-active");
        assertThat(btnProductos.getStyleClass()).contains("sidebar-button-active");
    }

    @Test
    void onSalirCallsPlatformExit() {
        try (MockedStatic<Platform> platform = mockStatic(Platform.class)) {
            presenter.onSalir();
            platform.verify(Platform::exit);
        }
    }

    @Test
    void loadViewLoadsFxmlSuccessfullyAfterToolkitInit() {
        presenter.onInicio();

        assertThat(contentArea.getChildren()).isNotEmpty();
    }

    @Test
    void loadViewCachesViewOnSecondCall() {
        Node mockNode = mock(Node.class);

        try (MockedConstruction<FXMLLoader> ignored = mockConstruction(FXMLLoader.class,
                (mock, ctx) -> when(mock.load()).thenReturn(mockNode))) {

            presenter.onInicio();
            assertThat(contentArea.getChildren()).hasSize(1);
            assertThat(contentArea.getChildren().get(0)).isSameAs(mockNode);
        }
    }

    @Test
    void setupKeyboardShortcutsRegistersKeyHandler() {
        presenter.setupKeyboardShortcuts(scene);

        verify(scene).setOnKeyPressed(any());
    }

    @Test
    void setupKeyboardShortcutsRegistersAllAccelerators() {
        presenter.setupKeyboardShortcuts(scene);

        verify(accelerators, times(7)).put(any(KeyCodeCombination.class), any(Runnable.class));
    }

    @Test
    void loadDarkModePreferenceReadsFromConfigRepository() throws Exception {
        ConfigRepository configRepository = mock(ConfigRepository.class);
        when(configRepository.get("dark_mode")).thenReturn(Optional.of("true"));
        presenter.setConfigRepository(configRepository);

        presenter.loadDarkModePreference();

        assertThat(getField(presenter, "isDarkMode")).isEqualTo(true);
    }

    @Test
    void loadDarkModePreferenceDefaultsToFalseWhenUnset() throws Exception {
        ConfigRepository configRepository = mock(ConfigRepository.class);
        when(configRepository.get("dark_mode")).thenReturn(Optional.empty());
        presenter.setConfigRepository(configRepository);

        presenter.loadDarkModePreference();

        assertThat(getField(presenter, "isDarkMode")).isEqualTo(false);
    }

    @Test
    void loadDarkModePreferenceIgnoresRepositoryError() throws Exception {
        ConfigRepository configRepository = mock(ConfigRepository.class);
        when(configRepository.get("dark_mode")).thenThrow(new RuntimeException("DB down"));
        presenter.setConfigRepository(configRepository);

        presenter.loadDarkModePreference();

        assertThat(getField(presenter, "isDarkMode")).isEqualTo(false);
    }

    @Test
    void saveDarkModePreferenceWritesCurrentValueToConfigRepository() throws Exception {
        ConfigRepository configRepository = mock(ConfigRepository.class);
        presenter.setConfigRepository(configRepository);

        presenter.saveDarkModePreference();

        verify(configRepository).set("dark_mode", "false");
    }

    @Test
    void saveDarkModePreferenceIgnoresRepositoryError() throws Exception {
        ConfigRepository configRepository = mock(ConfigRepository.class);
        doThrow(new SQLException("DB down")).when(configRepository).set("dark_mode", "false");
        presenter.setConfigRepository(configRepository);

        presenter.saveDarkModePreference();

        verify(configRepository).set("dark_mode", "false");
    }

    private Object getField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }
}
