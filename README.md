# Ruta 40 bebidas

Aplicacion de gestion para un negocio de bebidas en Mendoza, Argentina. Sistema POS (Point of Sale) de escritorio con control de stock, compras, ventas, alertas de vencimiento y reportes.

## Stack

| Tecnologia | Version |
|------------|---------|
| Java | 17 (LTS) |
| JavaFX | 21 |
| SQLite | 3.46.0 |
| Maven | 3.9+ |

## Como Ejecutar

```bash
mvn clean javafx:run
```

## Como Compilar e Instalar

```bash
mvn clean package -Pjpackage
```

Genera un instalador `.exe` en `dist/installer/`. No requiere pre-instalacion de Java ni dependencias — es un ejecutable standalone que se puede copiar a un pendrive USB y ejecutar directamente en cualquier PC con Windows 10/11. Los datos se almacenan en `%APPDATA%\Cocolatan\softwaredebebidas.db`.

## Como Testear

```bash
mvn clean test                    # Ejecutar tests
mvn test jacoco:report            # Tests + reporte de cobertura
```

## Estructura Rapida

```
src/main/java/com/softwaredebebidas/
  SoftwareDeBebidasApp.java       - Entry point JavaFX
  Launcher.java           - Entry point de empaquetado (jpackage)
  model/                   - 13 POJOs (Product, Supplier, Purchase, Sale, User, etc.)
  repository/              - DatabaseManager + 10 repositorios JDBC
  service/                 - Logica de negocio (10 servicios)
  presenter/               - 13 presenters (logica de UI)
  view/                    - 12 controllers FXML
  util/                    - CurrencyFormatter, DateUtils, AlertService, PhotoUtils, StockRisk, etc.
src/main/resources/
  fxml/                    - 13 vistas FXML
  styles.css / styles-dark.css - Estilos POS (claro/oscuro)
src/test/java/             - 97 clases de test
```

## Arquitectura

MVP (Model-View-Presenter) + SQLite embebida. Ver `ARCHITECTURE.md` para detalle completo.

- **Despliegue USB/pendrive**: single `.exe`, sin pre-instalacion, datos en `%APPDATA%\Cocolatan`
- **Seguridad**: PreparedStatement (sin SQL injection), contrasenas con BCrypt (hashes SHA-256 legacy se migran en el login), sin conexion de red

## Modulos

- **Dashboard**: Resumen de stock, alertas y acceso rapido
- **Productos**: Catalogo CRUD con busqueda por nombre/barcode/categoria, fotos, jerarquia de categorias y subcategorias
- **Proveedores**: Gestion de distribuidores
- **Compras**: Registro de compras con lotes y vencimientos, stock automatico, adjunto de foto de factura
- **Ventas (POS)**: Checkout con busqueda rapida, escaner de codigo de barras, canal Local/PedidosYa, historial de ventas y anulacion
- **Stock**: Dashboard en tiempo real, historial de movimientos, ajustes manuales
- **Alertas**: Vencimiento (7 dias) y stock bajo (min_stock)
- **Reportes**: Margen, ventas por periodo, canales, rotacion, valor de stock, productos mas vendidos, ventas detalladas por dia
- **Categorias**: Gestion de categorias y subcategorias del catalogo
- **Usuarios**: Gestion de usuarios del sistema (roles ADMIN/CAJERO), cambio de contrasena y proteccion del ultimo admin

## Licencia

Uso interno.