# Arquitectura de Software de Bebidas

## Stack Tecnológico

| Tecnología | Versión | Propósito |
|------------|---------|-----------|
| Java | 17 (LTS) | Lenguaje base, tipo seguro, records, patrones switch |
| JavaFX | 21 | UI desktop moderna con CSS, FXML declarativo y Scene Builder |
| SQLite | 3.46.0 (via sqlite-jdbc) | Base de datos embebida zero-config |
| Maven | 3.9+ | Build, dependencias, plugins, ciclo de vida |
| JUnit 5 | 5.10.2 | Testing unitario |
| Mockito | 5.11.0 | Mocks para testing |
| AssertJ | 3.24.2 | Assertions fluidas |
| JaCoCo | 0.8.12 | Cobertura de código |

## Patrón MVP (Model-View-Presenter)

El proyecto utiliza **Model-View-Presenter** como patrón arquitectónico, una variante de MVC optimizada para testabilidad.

### Diagrama de Comunicación

```
+-----------------+    eventos FXML     +------------------+     llama      +------------------+
|                 | onAction, key events |                  |--------------->|                  |
|   VIEW          |--------------------->|   PRESENTER      |               |   SERVICE        |
| (Controller     |                      | (lógica de UI,   |<---------------| (lógica de       |
|  FXML)          |<---------------------|  estado,         |  data/result   |  negocio)        |
|                 |  actualiza UI beans  |  delegación)     |               |                  |
+-----------------+                      +------------------+               +--------+---------+
                                                                                      |
                                                                               llama   |
                                                                                      v
                                                                               +------------------+
                                                                               |   REPOSITORY     |
                                                                               |   (JDBC raw)     |
                                                                               +--------+---------+
                                                                                        |
                                                                                        | SQL
                                                                                        v
                                                                               +------------------+
                                                                               |   SQLite DB      |
                                                                               |   (software-bebidas.db)  |
                                                                               +------------------+
```

### Flujo de Datos

1. **View** captura eventos del usuario (click en botón, escritura en campo, selección de tabla)
2. **View** delega al **Presenter** llamando un método público
3. **Presenter** ejecuta lógica de negocio: valida datos, decide que acción tomar
4. **Presenter** llama al **Service** correspondiente para operaciones de dominio
5. **Service** invoca métodos del **Repository** para persistencia
6. **Repository** ejecuta SQL via JDBC con PreparedStatement
7. Los resultados fluyen de vuelta: Repository -> Service -> Presenter -> View (actualizando la UI)

### Responsabilidades por Capa

| Capa | Responsabilidad | NO Responsabilidad |
|------|-----------------|--------------------|
| **View** (FXML Controller) | Injection de componentes FXML, configuración de tablas/columnas, binding de datos | Lógica de negocio, acceso a datos, navegación |
| **Presenter** | Estado de la UI, validación de entrada, delegación a servicios, manejo de errores de UI | Acceso directo a BD, lógica de negocio compleja |
| **Service** | Lógica de dominio, reglas de negocio, orquestación | Estado de UI, acceso directo a BD |
| **Repository** | CRUD SQL, transacciones, mapeo ResultSet a POJO | Lógica de negocio |
| **Model** (POJO) | Datos puros, getters/setters | Ninguna lógica |

## Justificación de Tecnologías

### JavaFX sobre Swing
- CSS moderno permite UI profesional tipo POS (kiosk)
- FXML + Scene Builder separan diseño de lógica
- Propiedades observables facilitan binding
- Mejor soporte para pantalla táctil

### SQLite sobre H2 / Derby
- Zero configuración: un solo archivo `.db`
- Backup simple: copiar archivo
- Battle-tested para desktop single-user
- WAL mode para concurrencia lectura/escritura

### MVP sobre MVC clásico
- Presenter es testeable sin UI (Mockito para mocks de vista)
- FXML Controller queda extremadamente delgado
- Estado de UI encapsulado en el Presenter

### Stock Calculado sobre Columna Almacenada
- Fuente única de verdad: `stock_movements`
- Auditoría completa: cada movimiento tiene tipo, referencia, fecha
- Sin bugs de sincronización entre columna y movimientos

## Estructura del Proyecto

```
software-bebidas/
├── pom.xml                                      -- Dependencias y build
├── ARCHITECTURE.md                              -- Este documento
├── README.md                                    -- Documentación principal
│
├── src/main/java/com/softwaredebebidas/
│   ├── SoftwareDeBebidasApp.java                        -- Entry point JavaFX
│   │
│   ├── model/                                   -- 15 POJOs (datos puros)
│   │   ├── Category.java                        -- id, name, sortOrder, active
│   │   ├── Subcategory.java                     -- id, categoryId, name, sortOrder, active
│   │   ├── Product.java                         -- id, name, category, presentation, costPrice, salePrice, supplierId, barcode, minStock, active, sku, photoPath, categoryId, subcategoryId, pedidosyaPrice
│   │   ├── Supplier.java                        -- id, name, contact, phone, email, address
│   │   ├── Purchase.java                        -- id, supplierId, invoiceRef, purchaseDate, subtotal, taxAmount, totalAmount, notes, invoicePhotoPath
│   │   ├── PurchaseItem.java                    -- id, purchaseId, productId, quantity, unitCost, lotNumber, expiryDate
│   │   ├── Sale.java                            -- id, saleDate, channel, paymentMethod, customerId, discount, discountType, totalAmount, status, cancelledAt, cancellationReason, receiptText
│   │   ├── SaleItem.java                        -- id, saleId, productId, quantity, unitPrice, discount, discountType, subtotal
│   │   ├── StockMovement.java                   -- id, productId, movementType, quantity, referenceType, referenceId, notes
│   │   ├── Customer.java                        -- id, name, phone, email, address
│   │   ├── User.java                            -- id, username, passwordHash, role, displayName
│   │   ├── DailySalesDetailRow.java             -- date, productName, quantity, unitPrice, lineTotal (DTO para reporte)
│   │   └── DailySalesDetailReport.java          -- rows, grandTotal, fromDate, toDate (DTO para reporte)
│   │
│   ├── repository/                              -- 11 repositorios + DatabaseManager
│   │   ├── DatabaseManager.java                 -- Singleton: conexión, schema, WAL, foreign keys, 8 migraciones
│   │   ├── ProductRepository.java               -- CRUD + búsqueda por nombre/barcode/categoría
│   │   ├── SupplierRepository.java              -- CRUD + dropdown
│   │   ├── PurchaseRepository.java              -- Save con items (transacción), historial, invoice_photo_path
│   │   ├── SaleRepository.java                  -- Save con items (transacción), historial
│   │   ├── StockMovementRepository.java         -- Insert, query, computeCurrentStock
│   │   ├── CustomerRepository.java              -- CRUD + búsqueda por nombre
│   │   ├── CategoryRepository.java              -- CRUD categorías
│   │   ├── SubcategoryRepository.java           -- CRUD subcategorías
│   │   ├── ConfigRepository.java                -- Key-value app_config (modo oscuro, nombre del negocio, etc.)
│   │   └── UserRepository.java                  -- CRUD usuarios + autenticación
│   │
│   ├── service/                                 -- 11 servicios de negocio
│   │   ├── InventoryService.java                -- Stock, status, low-stock, expiry, adjustments
│   │   ├── SalesService.java                    -- Creación de venta, validación stock/vencimiento
│   │   ├── PurchaseService.java                 -- Orquestación de compras (transacción: compra + items + stock + costo)
│   │   ├── ReportService.java                   -- Márgenes, ventas por período, canales, rotación, valor stock, ventas detalladas por día
│   │   ├── AlertService.java                    -- Alertas de vencimiento y stock bajo
│   │   ├── ReceiptService.java                  -- Generación de texto para ticket
│   │   ├── AuthService.java                     -- Autenticación de usuarios
│   │   ├── BackupScheduler.java                 -- Backup automático programado
│   │   ├── BackupService.java                   -- Lógica de backup/restore
│   │   └── CsvService.java                      -- Exportación de reportes a CSV
│   │
│   ├── presenter/                               -- 14 presenters
│   │   ├── MainPresenter.java                   -- Navegación, badge de alertas, reloj
│   │   ├── ProductPresenter.java                -- CRUD productos, búsqueda, validación
│   │   ├── SupplierPresenter.java               -- CRUD proveedores
│   │   ├── PurchasePresenter.java               -- Compra con items, stock auto-increment
│   │   ├── SalePresenter.java                   -- POS, carrito, búsqueda rápida, checkout
│   │   ├── StockPresenter.java                  -- Dashboard stock, movimientos, ajustes
│   │   ├── AlertPresenter.java                  -- Alertas, badge count, dismissal
│   │   ├── ReportPresenter.java                 -- Reportes, selección de tipo, rango fechas, CSV
│   │   ├── HomePresenter.java                   -- Dashboard de inicio
│   │   ├── CategoryPresenter.java               -- CRUD categorías/subcategorías
│   │   ├── LoginPresenter.java                  -- Login de usuarios
│   │   ├── BusinessSettingsPresenter.java       -- Datos del negocio: nombre y logo (solo admin)
│   │   ├── SaleHistoryPresenter.java            -- Historial de ventas, anulación
│   │   └── UserPresenter.java                   -- CRUD usuarios, roles, protección del último admin
│   │
│   ├── view/                                    -- 13 controllers FXML
│   │   ├── HomeController.java                  -- Dashboard principal
│   │   ├── ProductController.java               -- Catálogo CRUD
│   │   ├── SupplierController.java              -- Gestión de proveedores
│   │   ├── PurchaseController.java              -- Entrada de compras + adjunto de factura
│   │   ├── SaleController.java                  -- POS
│   │   ├── StockController.java                 -- Dashboard stock
│   │   ├── AlertController.java                 -- Panel de alertas
│   │   ├── ReportController.java                -- Reportes (7 tipos incluyendo ventas detalladas)
│   │   ├── CategoriesController.java            -- Gestión de categorías
│   │   ├── LoginController.java                 -- Pantalla de login
│   │   ├── BusinessSettingsController.java      -- Datos del negocio: nombre, logo, vista previa
│   │   ├── SaleHistoryController.java           -- Historial y anulación de ventas
│   │   └── UserController.java                  -- Gestión de usuarios
│   │
│   └── util/                                    -- 12 utilidades
│       ├── CurrencyFormatter.java               -- Formato ARS $XX.XXX,XX
│       ├── DateUtils.java                       -- Parse/format DD/MM/YYYY
│       ├── AlertService.java                    -- Diálogos JavaFX (error, warning, info, confirmación)
│       ├── PhotoUtils.java                      -- Gestión de fotos (productos + facturas)
│       ├── HierarchyLabel.java                  -- Formato de etiqueta jerarquía (Categoría - Subcategoría)
│       ├── IntegrityChecker.java                -- Verificación de integridad de datos
│       ├── LoggingConfig.java                   -- Configuración de logs y rotación
│       ├── Refreshable.java                     -- Interfaz para vistas que soportan refresh
│       ├── StockRisk.java                       -- Reglas de stock bajo/vencimiento compartidas
│       ├── AppDataDir.java                      -- Directorio de datos (%APPDATA%/software-bebidas) y nombres de archivos
│       ├── LogoUtils.java                       -- Guarda, quita y carga el logo del negocio (<datos>/logo/logo.png)
│       └── VersionInfo.java                     -- Version/build desde version.properties
│
├── src/main/resources/
│   ├── fxml/                                    -- 14 vistas FXML
│   │   ├── main.fxml                            -- Layout principal (sidebar + content)
│   │   ├── home.fxml                            -- Dashboard inicio
│   │   ├── product.fxml                         -- Catálogo
│   │   ├── supplier.fxml                        -- Proveedores
│   │   ├── purchase.fxml                        -- Compras + adjunto de factura
│   │   ├── sale.fxml                            -- POS
│   │   ├── stock.fxml                           -- Stock
│   │   ├── alert.fxml                           -- Alertas
│   │   ├── reports.fxml                         -- Reportes (7 tipos)
│   │   ├── categories.fxml                      -- Gestión de categorías
│   │   ├── login.fxml                           -- Login
│   │   ├── business.fxml                        -- Datos del negocio
│   │   ├── sale-history.fxml                    -- Historial de ventas
│   │   └── user.fxml                            -- Gestión de usuarios
│   │
│   └── styles.css                               -- Sistema de diseño POS profesional
│
└── src/test/java/com/softwaredebebidas/                 -- 103 archivos de test
    ├── model/                                   -- Tests de POJOs
    ├── repository/                              -- Tests de repositorios (incluyendo integración)
    ├── service/                                 -- Tests de servicios
    ├── presenter/                               -- Tests de presenters
    ├── util/                                    -- Tests de utilidades
    └── AppTest.java                             -- Smoke test
```

## Modelo de Datos

### Ubicación de la Base de Datos

La base de datos SQLite se almacena en `%APPDATA%\software-bebidas\software-bebidas.db`.
Al ejecutar el `.exe` empaquetado con jpackage, la aplicación escribe en esa ruta sin necesidad de pre-instalación.

`AppDataDir` es la única fuente de verdad de este directorio: la base de datos, el archivo de bloqueo de instancia única (`software-bebidas.lock`), los respaldos, las exportaciones, los logs, las fotos y el logo se resuelven a partir de `AppDataDir.getBaseDir()`. Si `APPDATA` no esta definida se usa una carpeta local `data`.

### Diagrama Entidad-Relación

```
suppliers 1---* products
categories 1---* subcategories
categories 1---* products (via category_id)
subcategories 1---* products (via subcategory_id)
products  1---* purchase_items
purchases 1---* purchase_items
products  1---* sale_items
sales     1---* sale_items
products  1---* stock_movements
customers 1---* sales (opcional)
users     ---   (autenticación)
```

### Tablas SQLite (14 principales)

| Tabla | Columnas Clave | FK |
|-------|---------------|----|
| **suppliers** | id, name, contact, phone, email, address | -- |
| **categories** | id, name, sort_order, active | -- |
| **subcategories** | id, category_id, name, sort_order, active | category_id -> categories(id) |
| **products** | id, name, category, presentation, cost_price, sale_price, supplier_id, barcode, min_stock, active, sku, photo_path, category_id, subcategory_id, sale_price_pedidosya | supplier_id -> suppliers(id), category_id -> categories(id), subcategory_id -> subcategories(id) |
| **purchases** | id, supplier_id, invoice_ref, purchase_date, subtotal, tax_amount, total_amount, notes, invoice_photo_path | supplier_id -> suppliers(id) |
| **purchase_items** | id, purchase_id, product_id, quantity, unit_cost, lot_number, expiry_date | purchase_id -> purchases(id) CASCADE, product_id -> products(id) |
| **sales** | id, sale_date, channel (IN/PEDIDOSYA), payment_method (CASH/CREDIT_CARD/DEBIT_CARD/TRANSFER/MIXED), customer_id, discount, discount_type, total_amount, status, cancelled_at, cancellation_reason, receipt_text | customer_id -> customers(id) |
| **sale_items** | id, sale_id, product_id, quantity, unit_price, discount, discount_type, subtotal | sale_id -> sales(id) CASCADE, product_id -> products(id) |
| **stock_movements** | id, product_id, movement_type (ENTRY/EXIT/ADJUSTMENT), quantity, reference_type, reference_id, notes | product_id -> products(id) |
| **customers** | id, name, phone, email, address | -- |
| **users** | id, username, password_hash, role (ADMIN/CAJERO), display_name | -- |
| **app_config** | key (PK), value | -- |
| **schema_version** | versión (PK), applied_at | -- |
| **register_sessions** | id, register_name, open_date, close_date, status (OPEN/CLOSED), initial_cash, expected_cash, actual_cash, created_at, closed_at | -- |

### Regla de Stock

El stock NO se almacena en una columna. Se calcula en tiempo real desde `stock_movements`:

```sql
current_stock = SUM(CASE WHEN movement_type IN ('ENTRY','ADJUSTMENT') THEN quantity ELSE 0 END)
              - SUM(CASE WHEN movement_type = 'EXIT' THEN quantity ELSE 0 END)
```

### Índices

17 índices en total para optimizar consultas frecuentes: categoría, barcode, barcode NOCASE, activo, fechas, tipo movimiento, canal de venta, sale_items sale_id, purchase_items purchase_id, register_sessions status y fecha.

### Migraciones (v1 a v8)

| Versión | Cambios |
|---------|---------|
| v1 | Schema inicial: suppliers, products, purchases, purchase_items, sales, sale_items, stock_movements, customers, app_config, schema_version |
| v2 | SKU y precio PedidosYa en products |
| v3 | Foto de producto (photo_path en products) |
| v4 | Anulación de ventas (status, cancelled_at, cancellation_reason) |
| v5 | Texto de comprobante (receipt_text en sales) |
| v6 | Jerarquía categorías/subcategorías (category_id, subcategory_id en products; categories, subcategories) |
| v7 | Índices: barcode NOCASE, sale_items sale_id, purchase_items purchase_id |
| v8 | Foto de factura (invoice_photo_path en purchases), tabla register_sessions |

## Flujos Principales

### Flujo de Compra (con Foto de Factura)

```
Usuario -> PurchaseController.onAttachInvoice()
    -> FileChooser (filtro JPG/PNG, max 10MB)
    -> PhotoUtils.copyInvoiceImage() -> purchase-invoices/uuid.jpg
    -> Almacena invoicePhotoPath en el controller

Usuario -> PurchaseController.onSavePurchase()
    -> PurchasePresenter.savePurchase(purchase, items)
    -> PurchaseRepository.saveWithItems() (transacción: purchase + items)
    -> StockMovementRepository.insert() (ENTRY por cada item)
    -> ProductRepository.updateCostPrice() (actualiza precio de costo)
    -> La foto se guarda como invoice_photo_path en la tabla purchases
```

### Flujo de Venta

```
Usuario -> SaleController -> SalePresenter.addToCart() (valida stock)
    -> SalePresenter.completeSale()
    -> SalesService.createSale() (valida stock y vencimiento)
    -> SaleRepository.saveWithItems() (transacción: sale + items)
    -> StockMovementRepository.insert() (EXIT por cada item)
    -> ReceiptService.generateReceipt() (texto del ticket)
```

### Flujo de Stock

```
StockController -> StockPresenter.getDashboardData()
    -> ProductRepository.findAllActive()
    -> StockMovementRepository.computeCurrentStock() (por cada producto)
    -> Presenta tabla con estado OK/LOW/OUT

Ajuste manual:
StockPresenter.createAdjustment()
    -> StockMovementRepository.insert() (ADJUSTMENT o EXIT)
```

### Flujo de Alertas

```
AlertController.initialize() -> AlertPresenter.getExpiryAlerts()
    -> AlertService.getExpiryAlerts()
    -> ProductRepository.findAllActive()
    -> PurchaseRepository.findItemsByProductId()
    -> Filtra: expiry_date <= now (EXPIRED) o <= now+7d (EXPIRING_SOON)

AlertPresenter.getLowStockAlerts()
    -> AlertService.getLowStockAlerts()
    -> InventoryService.getCurrentStock() <= min_stock
    -> Marca: stock=0 (OUT_OF_STOCK CRITICAL), 0<stock<=min_stock (LOW_STOCK WARNING)
```

### Flujo de Reportes

```
ReportController -> ReportPresenter.generateReport()
    -> ReportService.getMarginReport() | getSalesByPeriodReport()
    | getChannelComparisonReport() | getRotationReport() | getStockValueReport()
    | getTopSellersReport() | getDailySalesDetailReport()
    -> ProductRepository | SaleRepository | StockMovementRepository
    -> Cálculos: margen %, revenue, tickets, rotación, valor inventario, detalle por producto
```

### Flujo de Reporte Ventas Detalladas por Dia

```
ReportController -> ReportPresenter.generateDailySalesDetail()
    -> ReportService.getDailySalesDetailReport(fromDate, toDate)
    -> SaleRepository.findByDateRange() (obtiene ventas del período)
    -> SaleRepository.findItemsBySaleId() (obtiene items de cada venta)
    -> Agrupación por día + producto: cantidad, precio unitario, total línea
    -> Exportación CSV via ReportPresenter.exportDailySalesDetailCSV()
```

### Flujo de Fotos de Factura

```
PurchaseController.onAttachInvoice()
    -> FileChooser (filtro: JPG, JPEG, PNG)
    -> Validación: máximo 10 MB
    -> PhotoUtils.copyInvoiceImage(sourceFile)
        -> PhotoUtils.ensureInvoiceDir() (crea purchase-invoices/ si no existe)
        -> Copia archivo con nombre UUID al directorio purchase-invoices/
        -> Retorna ruta relativa "purchase-invoices/uuid.ext"
    -> Almacena invoicePhotoPath en el controller

PurchaseController.onSavePurchase()
    -> purchase.setInvoicePhotoPath(invoicePhotoPath)
    -> PurchaseRepository.saveWithItems() (incluye invoice_photo_path en INSERT)

PurchaseController.onPurchaseLoaded(purchase)
    -> Si invoicePhotoPath no es null/vacío:
        -> PhotoUtils.resolvePath() convierte ruta relativa a absoluta
        -> Muestra preview de imagen en imgInvoicePreview
        -> Muestra nombre del archivo en lblPhotoName

PurchaseController.deleteInvoicePhoto()
    -> Limpia imgInvoicePreview (imagen y visibilidad)
    -> Limpia lblPhotoName
```

## Navegación (StackPane)

```layout
+--------------------------------------------------------------+
| Mi negocio (nombre configurable)                  DD/MM/YYYY HH:MM     |
+----------+---------------------------------------------------+
| Productos |                                                   |
| Compras   |            Content Area (StackPane)               |
| Ventas    |                                                   |
| Stock     |           Solo una vista a la vez                 |
| Alertas   |                                                   |
| Reportes  |                                                   |
| Proveed.  |                                                   |
| Categorías|                                                   |
| Usuarios  |                                                   |
| Datos del |                                                   |
|  negocio  |                                                   |
| [Salir]   |                                                   |
+----------+---------------------------------------------------+
```

- Sidebar izquierdo: 210px, botones con iconos
- Content area: StackPane, swap de FXML via MainPresenter.loadView()
- Cache de vistas: HashMap<fxmlPath, Node> evita recargas
- Keyboard shortcuts: F2-F8 para módulos, Ctrl+1-7, ESC para salir

## Estrategia de Testing

| Capa | Enfoque | Herramientas |
|------|---------|-------------|
| **Unit - Model** | Constructores, getters/setters, toString | JUnit 5 |
| **Unit - Repository** | CRUD con SQLite in-memory (`jdbc:sqlite::memory:`) | JUnit 5 |
| **Unit - Service** | Business rules con mocks de repositorios | Mockito, AssertJ |
| **Unit - Presenter** | Estado UI, delegación con mocks de servicios/views | Mockito, AssertJ |
| **Unit - Util** | Formateo, parsing, diálogos | JUnit 5 |
| **Integration** | Flujos completos (purchase -> stock -> sale -> report) con SQLite in-memory | JUnit 5 |

**Nota**: Los controladores FXML (view) y util.AlertService no son testeables sin TestFX (JavaFX toolkit).

## Manejo de Errores

| Capa | Estrategia |
|------|-----------|
| **Repository** | SQLException propagada al Service |
| **Service** | RuntimeException con mensaje en español |
| **Presenter** | Catch + AlertService.showErrorDialog() + log WARNING |
| **App global** | Thread.setDefaultUncaughtExceptionHandler -> log SEVERE + dialog |

## Formato de Moneda y Fechas

- **Moneda**: ARS $XX.XXX,XX (punto como separador de miles, coma decimal)
- **Fechas**: DD/MM/YYYY (formato argentino)
- **UI**: Labels en español
- **Código**: Comentarios y código en inglés

## ADRs (Architecture Decisión Records)

### ADR-001: MVP sobre MVC
- **Estado**: Aceptado
- **Contexto**: Necesitamos testear lógica de negocio sin levantar JavaFX
- **Decisión**: MVP con Presenters testeables via Mockito
- **Consecuencia**: FXML controllers quedan con < 50 líneas de lógica

### ADR-002: SQLite sobre H2
- **Estado**: Aceptado
- **Contexto**: App desktop single-user para un negocio de bebidas, backup manual
- **Decisión**: SQLite con WAL mode y foreign keys
- **Consecuencia**: Sin servidor, sin configuración, backup = copiar archivo

### ADR-003: Stock Calculado sobre Columna
- **Estado**: Aceptado
- **Contexto**: Necesitamos auditoría completa de movimientos
- **Decisión**: stock_movements como única fuente de verdad
- **Consecuencia**: Consulta SUM ligera, sin sync bugs

### ADR-004: StackPane sobre TabPane
- **Estado**: Aceptado
- **Contexto**: Módulos independientes sin estado compartido
- **Decisión**: StackPane con swap de FXML via MainPresenter
- **Consecuencia**: Cache de vistas, clean module isolation

### ADR-005: Error Handling por Capas
- **Estado**: Aceptado
- **Contexto**: Usuario final no técnico necesita mensajes claros
- **Decisión**: Presenter captura y muestra diálogo en español; capas inferiores lanzan excepciones
- **Consecuencia**: Logs detallados para developer, diálogo amigable para usuario

### ADR-006: Strict TDD
- **Estado**: Aceptado
- **Contexto**: Proyecto con especificaciones detalladas
- **Decisión**: Red-Green-Refactor obligatorio para toda funcionalidad
- **Consecuencia**: 103 archivos de test, cobertura ~58% (sin contar JavaFX no testeable)

### ADR-007: Fotos de Factura en Directorio Aparte
- **Estado**: Aceptado
- **Contexto**: Necesitamos adjuntar facturas de compra sin mezclar con fotos de productos
- **Decisión**: Subdirectorio `purchase-invoices/` bajo el directorio base de la app, manejado por PhotoUtils con métodos dedicados (`copyInvoiceImage`, `getInvoicePhotosDir`, `ensureInvoiceDir`)
- **Consecuencia**: Separación limpia de archivos de productos y facturas, misma lógica de UUID-based naming y eliminación

### ADR-008: Register Sessions Groundwork
- **Estado**: Aceptado (groundwork)
- **Contexto**: Se necesita sistema de caja (apertura/cierre) para pedidos ya y local físico
- **Decisión**: Crear tabla `register_sessions` con campos pre-cargados para apertura/cierre futuro (expected_cash, actual_cash, close_date, status OPEN/CLOSED), pero sin implementar la lógica de apertura/cierre aún
- **Consecuencia**: La tabla y sus índices están listos; el model/repository/controller/presenter/FXML se implementarán cuando se active la funcionalidad de caja

## Build System

- **Maven** para compilación, dependencias y empaquetado
- **jpackage** (perfil `-Pjpackage`) genera un `.exe` standalone para Windows
- El instalador se produce en `dist/installer/` y no requiere pre-instalación de Java
- La aplicación se puede desplegar en un pendrive USB y ejecutar directamente

- **Código**: Inglés (clases, métodos, variables, comentarios)
- **UI**: Español (labels, diálogos, mensajes de error)
- **Testing**: Tests en inglés, mensajes de assertion en inglés
- **Commits**: Conventional Commits en inglés
- **Moneda**: Formato ARS con CurrencyFormatter
- **Fechas**: DD/MM/YYYY con DateUtils
- **SQL**: Nombres de tabla/columna en lowercase con snake_case

## Requisitos del Sistema

- **OS**: Windows 10/11 (build con classifier `win` para JavaFX)
- **Java**: JDK 17+ (LTS)
- **RAM**: 256 MB mínimo
- **Disco**: 50 MB para la aplicación + tamaño de la BD
- **Pantalla**: 1024x768 mínimo (diseñado para 1100x680)