# Cocolatan — Central de Bebidas

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

## Como Compilar

```bash
mvn clean package
```

Genera un fat JAR en `target/cocolatan-1.0.0-SNAPSHOT.jar` (via maven-shade-plugin).

## Como Testear

```bash
mvn clean test                    # Ejecutar tests
mvn test jacoco:report            # Tests + reporte de cobertura
```

## Estructura Rapida

```
src/main/java/com/cocolatan/
  CocolatanApp.java       - Entry point JavaFX
  model/                   - 13 POJOs (Product, Supplier, Purchase, Sale, User, etc.)
  repository/              - DatabaseManager + 10 repositorios JDBC
  service/                 - Logica de negocio (9 servicios)
  presenter/               - 12 presenters (logica de UI)
  view/                    - 11 controllers FXML
  util/                    - CurrencyFormatter, DateUtils, AlertService, PhotoUtils, etc.
src/main/resources/
  fxml/                    - 12 vistas FXML
  styles.css               - Estilos POS profesionales
src/test/java/             - 71 archivos de test
```

## Arquitectura

MVP (Model-View-Presenter) con SQLite embebida. Ver `ARCHITECTURE.md` para detalle completo.

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

## Licencia

Uso interno.