# Software de Bebidas

Aplicación de gestión para negocios de bebidas. Sistema POS (Point of Sale) de escritorio con control de stock, compras, ventas, alertas de vencimiento y reportes.

## Cómo ingresar

Al iniciar por primera vez, el sistema crea un usuario administrador:

- **Usuario:** `admin`
- **Contraseña:** `admin123`

Por seguridad, el sistema obliga a cambiar esta contraseña en el primer inicio de sesión. Desde el módulo **Usuarios** se pueden crear más usuarios (roles Administrador y Cajero).

El nombre del negocio y el logo se configuran desde **Datos del negocio** (menú lateral, solo administradores). Si no se carga un logo, se muestra el nombre del negocio.

## Stack

| Tecnología | Versión |
|------------|---------|
| Java | 17 (LTS) |
| JavaFX | 21 |
| SQLite | 3.46.0 |
| Maven | 3.9+ |

## Cómo ejecutar

```bash
mvn clean javafx:run
```

## Cómo compilar e instalar

```bash
mvn clean package -Pjpackage
```

Genera un instalador `.exe` en `dist/installer/`. No requiere preinstalación de Java ni dependencias: es un ejecutable independiente que se puede copiar a un pendrive USB y ejecutar directamente en cualquier PC con Windows 10/11. Los datos se almacenan en `%APPDATA%\software-bebidas\software-bebidas.db`. En el primer inicio, si no se encuentran datos, la aplicación ofrece crear una base nueva o importar los datos de la carpeta de una instalación anterior.

## Cómo testear

```bash
mvn clean test                    # Ejecutar tests
mvn test jacoco:report            # Tests + reporte de cobertura
```

## Estructura rápida

```
src/main/java/com/softwaredebebidas/
  SoftwareDeBebidasApp.java       - Entry point JavaFX
  Launcher.java           - Entry point de empaquetado (jpackage)
  model/                   - 15 POJOs (Product, Supplier, Purchase, Sale, User, etc.)
  repository/              - DatabaseManager + 11 repositorios JDBC
  service/                 - Lógica de negocio (11 servicios)
  presenter/               - 14 presenters (lógica de UI)
  view/                    - 13 controllers FXML
  util/                    - CurrencyFormatter, DateUtils, AlertService, PhotoUtils, LogoUtils, AppDataDir, etc.
src/main/resources/
  fxml/                    - 14 vistas FXML
  styles.css / styles-dark.css - Estilos POS (claro/oscuro)
src/test/java/             - 103 clases de test
```

## Arquitectura

MVP (Model-View-Presenter) + SQLite embebida. Ver `ARCHITECTURE.md` para el detalle completo.

- **Despliegue USB/pendrive**: un solo `.exe`, sin preinstalación, datos en `%APPDATA%\software-bebidas`
- **Seguridad**: PreparedStatement (sin SQL injection), contraseñas con BCrypt (los hashes SHA-256 antiguos se migran en el inicio de sesión), sin conexión de red

## Módulos

- **Dashboard**: Resumen de stock, alertas y acceso rápido
- **Productos**: Catálogo CRUD con búsqueda por nombre/código de barras/categoría, fotos, jerarquía de categorías y subcategorías
- **Proveedores**: Gestión de distribuidores
- **Compras**: Registro de compras con lotes y vencimientos, stock automático, adjunto de foto de factura
- **Ventas (POS)**: Checkout con búsqueda rápida, escáner de código de barras, canal Local/PedidosYa, historial de ventas y anulación
- **Stock**: Dashboard en tiempo real, historial de movimientos, ajustes manuales
- **Alertas**: Vencimiento (7 días) y stock bajo (min_stock)
- **Reportes**: Margen, ventas por período, canales, rotación, valor de stock, productos más vendidos, ventas detalladas por día
- **Categorías**: Gestión de categorías y subcategorías del catálogo
- **Usuarios**: Gestión de usuarios del sistema (roles ADMIN/CAJERO), cambio de contraseña y protección del último administrador
- **Datos del negocio**: Nombre y logo del negocio (solo administradores)

## Agradecimientos

Este proyecto se desarrolló con asistencia de IA, dirigida y revisada por el autor.

- **[Alan Buscaglia](https://github.com/Alan-TheGentleman)** ([Gentleman Programming](https://github.com/Gentleman-Programming)), por [Gentle AI](https://github.com/Gentleman-Programming/gentle-ai) y [Engram](https://github.com/Gentleman-Programming/engram). Su flujo de desarrollo guiado por especificaciones (SDD), sus agentes de revisión y su memoria persistente estuvieron presentes desde el primer día y guiaron la mayor parte del proyecto.
- **[OpenCode](https://opencode.ai)**, el entorno en el que se construyó la base de la aplicación, entre junio y agosto de 2026.
- **[Claude Code](https://claude.com/claude-code)**, usado en las etapas posteriores de mejoras y presentación.

## Licencia

Software comercial. El código se publica solo para consulta y evaluación: se puede descargar, compilar y ejecutar localmente para evaluarlo, pero no usarlo en un negocio ni redistribuirlo sin un acuerdo con el autor. Ver [LICENSE](LICENSE).
