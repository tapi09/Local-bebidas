# Arquitectura de Software de bebidas — Central de Bebidas

## Stack Tecnologico

| Tecnologia | Version | Proposito |
|------------|---------|-----------|
| Java | 17 (LTS) | Lenguaje base, tipo seguro, records, patrones switch |
| JavaFX | 21 | UI desktop moderna con CSS, FXML declarativo y Scene Builder |
| SQLite | 3.46.0 (via sqlite-jdbc) | Base de datos embebida zero-config |
| Maven | 3.9+ | Build, dependencias, plugins, ciclo de vida |
| JUnit 5 | 5.10.2 | Testing unitario |
| Mockito | 5.11.0 | Mocks para testing |
| AssertJ | 3.24.2 | Assertions fluidas |
| JaCoCo | 0.8.12 | Cobertura de codigo |

## Patron MVP (Model-View-Presenter)

El proyecto utiliza **Model-View-Presenter** como patron arquitectonico, una variante de MVC optimizada para testabilidad.

### Diagrama de Comunicacion

```
+-----------------+    eventos FXML     +------------------+     llama      +------------------+
|                 | onAction, key events |                  |--------------->|                  |
|   VIEW          |--------------------->|   PRESENTER      |               |   SERVICE        |
| (Controller     |                      | (logica de UI,   |<---------------| (logica de       |
|  FXML)          |<---------------------|  estado,         |  data/result   |  negocio)        |
|                 |  actualiza UI beans  |  delegacion)     |               |                  |
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
                                                                               |   (softwaredebebidas.db)  |
                                                                               +------------------+
```

### Flujo de Datos

1. **View** captura eventos del usuario (click en boton, escritura en campo, seleccion de tabla)
2. **View** delega al **Presenter** llamando un metodo publico
3. **Presenter** ejecuta logica de negocio: valida datos, decide que accion tomar
4. **Presenter** llama al **Service** correspondiente para operaciones de dominio
5. **Service** invoca metodos del **Repository** para persistencia
6. **Repository** ejecuta SQL via JDBC con PreparedStatement
7. Los resultados fluyen de vuelta: Repository -> Service -> Presenter -> View (actualizando la UI)

### Responsabilidades por Capa

| Capa | Responsabilidad | NO Responsabilidad |
|------|-----------------|--------------------|
| **View** (FXML Controller) | Injection de componentes FXML, configuracion de tablas/columnas, binding de datos | Logica de negocio, acceso a datos, navegacion |
| **Presenter** | Estado de la UI, validacion de entrada, delegacion a servicios, manejo de errores de UI | Acceso directo a BD, logica de negocio compleja |
| **Service** | Logica de dominio, reglas de negocio, orquestacion | Estado de UI, acceso directo a BD |
| **Repository** | CRUD SQL, transacciones, mapeo ResultSet a POJO | Logica de negocio |
| **Model** (POJO) | Datos puros, getters/setters | Ninguna logica |

## Justificacion de Tecnologias

### JavaFX sobre Swing
- CSS moderno permite UI profesional tipo POS (kiosk)
- FXML + Scene Builder separan diseno de logica
- Propiedades observables facilitan binding
- Mejor soporte para pantalla tactil

### SQLite sobre H2 / Derby
- Zero configuracion: un solo archivo `.db`
- Backup simple: copiar archivo
- Battle-tested para desktop single-user
- WAL mode para concurrencia lectura/escritura

### MVP sobre MVC clasico
- Presenter es testeable sin UI (Mockito para mocks de vista)
- FXML Controller queda extremadamente delgado
- Estado de UI encapsulado en el Presenter

### Stock Calculado sobre Columna Almacenada
- Fuente unica de verdad: `stock_movements`
- Auditoria completa: cada movimiento tiene tipo, referencia, fecha
- Sin bugs de sincronizacion entre columna y movimientos

## Estructura del Proyecto

```
softwaredebebidas/
├── pom.xml                                      -- Dependencias y build
├── ARCHITECTURE.md                              -- Este documento
├── README.md                                    -- Documentacion principal
├── COVERAGE-REPORT.md                           -- Reporte de cobertura
├── JAVADOC-GUIDE.md                             -- Guia de Javadoc
│
├── src/main/java/com/softwaredebebidas/
│   ├── SoftwareDeBebidasApp.java                        -- Entry point JavaFX
│   │
│   ├── model/                                   -- 13 POJOs (datos puros)
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
│   ├── repository/                              -- 10 repositorios + DatabaseManager
│   │   ├── DatabaseManager.java                 -- Singleton: conexion, schema, WAL, foreign keys, 8 migraciones
│   │   ├── ProductRepository.java               -- CRUD + busqueda por nombre/barcode/categoria
│   │   ├── SupplierRepository.java              -- CRUD + dropdown
│   │   ├── PurchaseRepository.java              -- Save con items (transaccion), historial, invoice_photo_path
│   │   ├── SaleRepository.java                  -- Save con items (transaccion), historial
│   │   ├── StockMovementRepository.java         -- Insert, query, computeCurrentStock
│   │   ├── CustomerRepository.java              -- CRUD + busqueda por nombre
│   │   ├── CategoryRepository.java              -- CRUD categorias
│   │   ├── SubcategoryRepository.java           -- CRUD subcategorias
│   │   ├── ConfigRepository.java                -- Key-value app_config
│   │   └── UserRepository.java                  -- CRUD usuarios + autenticacion
│   │
│   ├── service/                                 -- 10 servicios de negocio
│   │   ├── InventoryService.java                -- Stock, status, low-stock, expiry, adjustments
│   │   ├── SalesService.java                    -- Creacion de venta, validacion stock/vencimiento
│   │   ├── PurchaseService.java                 -- Orquestacion de compras (transaccion: compra + items + stock + costo)
│   │   ├── ReportService.java                   -- Margenes, ventas por periodo, canales, rotacion, valor stock, ventas detalladas por dia
│   │   ├── AlertService.java                    -- Alertas de vencimiento y stock bajo
│   │   ├── ReceiptService.java                  -- Generacion de texto para ticket
│   │   ├── AuthService.java                     -- Autenticacion de usuarios
│   │   ├── BackupScheduler.java                 -- Backup automatico programado
│   │   ├── BackupService.java                   -- Logica de backup/restore
│   │   └── CsvService.java                      -- Exportacion de reportes a CSV
│   │
│   ├── presenter/                               -- 13 presenters
│   │   ├── MainPresenter.java                   -- Navegacion, badge de alertas, reloj
│   │   ├── ProductPresenter.java                -- CRUD productos, busqueda, validacion
│   │   ├── SupplierPresenter.java               -- CRUD proveedores
│   │   ├── PurchasePresenter.java               -- Compra con items, stock auto-increment
│   │   ├── SalePresenter.java                   -- POS, carrito, busqueda rapida, checkout
│   │   ├── StockPresenter.java                  -- Dashboard stock, movimientos, ajustes
│   │   ├── AlertPresenter.java                  -- Alertas, badge count, dismissal
│   │   ├── ReportPresenter.java                 -- Reportes, seleccion de tipo, rango fechas, CSV
│   │   ├── HomePresenter.java                   -- Dashboard de inicio
│   │   ├── CategoryPresenter.java               -- CRUD categorias/subcategorias
│   │   ├── LoginPresenter.java                  -- Login de usuarios
│   │   ├── SaleHistoryPresenter.java            -- Historial de ventas, anulacion
│   │   └── UserPresenter.java                   -- CRUD usuarios, roles, proteccion del ultimo admin
│   │
│   ├── view/                                    -- 12 controllers FXML
│   │   ├── HomeController.java                  -- Dashboard principal
│   │   ├── ProductController.java               -- Catalogo CRUD
│   │   ├── SupplierController.java              -- Gestion de proveedores
│   │   ├── PurchaseController.java              -- Entrada de compras + adjunto de factura
│   │   ├── SaleController.java                  -- POS
│   │   ├── StockController.java                 -- Dashboard stock
│   │   ├── AlertController.java                 -- Panel de alertas
│   │   ├── ReportController.java                -- Reportes (7 tipos incluyendo ventas detalladas)
│   │   ├── CategoriesController.java            -- Gestion de categorias
│   │   ├── LoginController.java                 -- Pantalla de login
│   │   ├── SaleHistoryController.java           -- Historial y anulacion de ventas
│   │   └── UserController.java                  -- Gestion de usuarios
│   │
│   └── util/                                    -- 11 utilidades
│       ├── CurrencyFormatter.java               -- Formato ARS $XX.XXX,XX
│       ├── DateUtils.java                       -- Parse/format DD/MM/YYYY
│       ├── AlertService.java                    -- Dialogos JavaFX (error, warning, info, confirmacion)
│       ├── PhotoUtils.java                      -- Gestion de fotos (productos + facturas)
│       ├── HierarchyLabel.java                  -- Formato de etiqueta jerarquia (Categoria - Subcategoria)
│       ├── IntegrityChecker.java                -- Verificacion de integridad de datos
│       ├── LoggingConfig.java                   -- Configuracion de logs y rotacion
│       ├── Refreshable.java                     -- Interfaz para vistas que soportan refresh
│       ├── StockRisk.java                       -- Reglas de stock bajo/vencimiento compartidas
│       ├── LogoUtils.java                       -- Ubicacion y seed del logo de marca (%APPDATA%/Cocolatan/logo)
│       └── VersionInfo.java                     -- Version/build desde version.properties
│
├── src/main/resources/
│   ├── fxml/                                    -- 13 vistas FXML
│   │   ├── main.fxml                            -- Layout principal (sidebar + content)
│   │   ├── home.fxml                            -- Dashboard inicio
│   │   ├── product.fxml                         -- Catalogo
│   │   ├── supplier.fxml                        -- Proveedores
│   │   ├── purchase.fxml                        -- Compras + adjunto de factura
│   │   ├── sale.fxml                            -- POS
│   │   ├── stock.fxml                           -- Stock
│   │   ├── alert.fxml                           -- Alertas
│   │   ├── reports.fxml                         -- Reportes (7 tipos)
│   │   ├── categories.fxml                      -- Gestion de categorias
│   │   ├── login.fxml                           -- Login
│   │   ├── sale-history.fxml                    -- Historial de ventas
│   │   └── user.fxml                            -- Gestion de usuarios
│   │
│   └── styles.css                               -- Sistema de diseno POS profesional
│
└── src/test/java/com/softwaredebebidas/                 -- 82 archivos de test
    ├── model/                                   -- Tests de POJOs
    ├── repository/                              -- Tests de repositorios (incluyendo integracion)
    ├── service/                                 -- Tests de servicios
    ├── presenter/                               -- Tests de presenters
    ├── util/                                    -- Tests de utilidades
    └── AppTest.java                             -- Smoke test
```

## Modelo de Datos

### Ubicacion de la Base de Datos

La base de datos SQLite se almacena en `%APPDATA%\Cocolatan\softwaredebebidas.db`.
Al ejecutar el `.exe` empaquetado con jpackage, la aplicacion escribe en esa ruta sin necesidad de pre-instalacion.

### Diagrama Entidad-Relacion

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
users     ---   (autenticacion)
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
| **schema_version** | version (PK), applied_at | -- |
| **register_sessions** | id, register_name, open_date, close_date, status (OPEN/CLOSED), initial_cash, expected_cash, actual_cash, created_at, closed_at | -- |

### Regla de Stock

El stock NO se almacena en una columna. Se calcula en tiempo real desde `stock_movements`:

```sql
current_stock = SUM(CASE WHEN movement_type IN ('ENTRY','ADJUSTMENT') THEN quantity ELSE 0 END)
              - SUM(CASE WHEN movement_type = 'EXIT' THEN quantity ELSE 0 END)
```

### Indices

17 indices en total para optimizar consultas frecuentes: categoria, barcode, barcode NOCASE, activo, fechas, tipo movimiento, canal de venta, sale_items sale_id, purchase_items purchase_id, register_sessions status y fecha.

### Migraciones (v1 a v8)

| Version | Cambios |
|---------|---------|
| v1 | Schema inicial: suppliers, products, purchases, purchase_items, sales, sale_items, stock_movements, customers, app_config, schema_version |
| v2 | SKU y precio PedidosYa en products |
| v3 | Foto de producto (photo_path en products) |
| v4 | Anulacion de ventas (status, cancelled_at, cancellation_reason) |
| v5 | Texto de comprobante (receipt_text en sales) |
| v6 | Jerarquia categorias/subcategorias (category_id, subcategory_id en products; categories, subcategories) |
| v7 | Indices: barcode NOCASE, sale_items sale_id, purchase_items purchase_id |
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
    -> PurchaseRepository.saveWithItems() (transaccion: purchase + items)
    -> StockMovementRepository.insert() (ENTRY por cada item)
    -> ProductRepository.updateCostPrice() (actualiza precio de costo)
    -> La foto se guarda como invoice_photo_path en la tabla purchases
```

### Flujo de Venta

```
Usuario -> SaleController -> SalePresenter.addToCart() (valida stock)
    -> SalePresenter.completeSale()
    -> SalesService.createSale() (valida stock y vencimiento)
    -> SaleRepository.saveWithItems() (transaccion: sale + items)
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
    -> Calculos: margen %, revenue, tickets, rotacion, valor inventario, detalle por producto
```

### Flujo de Reporte Ventas Detalladas por Dia

```
ReportController -> ReportPresenter.generateDailySalesDetail()
    -> ReportService.getDailySalesDetailReport(fromDate, toDate)
    -> SaleRepository.findByDateRange() (obtiene ventas del periodo)
    -> SaleRepository.findItemsBySaleId() (obtiene items de cada venta)
    -> Agrupacion por dia + producto: cantidad, precio unitario, total linea
    -> Exportacion CSV via ReportPresenter.exportDailySalesDetailCSV()
```

### Flujo de Fotos de Factura

```
PurchaseController.onAttachInvoice()
    -> FileChooser (filtro: JPG, JPEG, PNG)
    -> Validacion: maximo 10 MB
    -> PhotoUtils.copyInvoiceImage(sourceFile)
        -> PhotoUtils.ensureInvoiceDir() (crea purchase-invoices/ si no existe)
        -> Copia archivo con nombre UUID al directorio purchase-invoices/
        -> Retorna ruta relativa "purchase-invoices/uuid.ext"
    -> Almacena invoicePhotoPath en el controller

PurchaseController.onSavePurchase()
    -> purchase.setInvoicePhotoPath(invoicePhotoPath)
    -> PurchaseRepository.saveWithItems() (incluye invoice_photo_path en INSERT)

PurchaseController.onPurchaseLoaded(purchase)
    -> Si invoicePhotoPath no es null/vacio:
        -> PhotoUtils.resolvePath() convierte ruta relativa a absoluta
        -> Muestra preview de imagen en imgInvoicePreview
        -> Muestra nombre del archivo en lblPhotoName

PurchaseController.deleteInvoicePhoto()
    -> Limpia imgInvoicePreview (imagen y visibilidad)
    -> Limpia lblPhotoName
```

## Navegacion (StackPane)

```layout
+--------------------------------------------------------------+
| Software de bebidas - Central de Bebidas          DD/MM/YYYY HH:MM     |
+----------+---------------------------------------------------+
| Productos |                                                   |
| Compras   |            Content Area (StackPane)               |
| Ventas    |                                                   |
| Stock     |           Solo una vista a la vez                 |
| Alertas   |                                                   |
| Reportes  |                                                   |
| Proveed.  |                                                   |
| Categorias|                                                   |
| Usuarios  |                                                   |
| [Salir]   |                                                   |
+----------+---------------------------------------------------+
```

- Sidebar izquierdo: 210px, botones con iconos
- Content area: StackPane, swap de FXML via MainPresenter.loadView()
- Cache de vistas: HashMap<fxmlPath, Node> evita recargas
- Keyboard shortcuts: F2-F8 para modulos, Ctrl+1-7, ESC para salir

## Estrategia de Testing

| Capa | Enfoque | Herramientas |
|------|---------|-------------|
| **Unit - Model** | Constructores, getters/setters, toString | JUnit 5 |
| **Unit - Repository** | CRUD con SQLite in-memory (`jdbc:sqlite::memory:`) | JUnit 5 |
| **Unit - Service** | Business rules con mocks de repositorios | Mockito, AssertJ |
| **Unit - Presenter** | Estado UI, delegacion con mocks de servicios/views | Mockito, AssertJ |
| **Unit - Util** | Formateo, parsing, dialogos | JUnit 5 |
| **Integration** | Flujos completos (purchase -> stock -> sale -> report) con SQLite in-memory | JUnit 5 |

**Nota**: Los controladores FXML (view) y util.AlertService no son testeables sin TestFX (JavaFX toolkit).

## Manejo de Errores

| Capa | Estrategia |
|------|-----------|
| **Repository** | SQLException propagada al Service |
| **Service** | RuntimeException con mensaje en espanol |
| **Presenter** | Catch + AlertService.showErrorDialog() + log WARNING |
| **App global** | Thread.setDefaultUncaughtExceptionHandler -> log SEVERE + dialog |

## Formato de Moneda y Fechas

- **Moneda**: ARS $XX.XXX,XX (punto como separador de miles, coma decimal)
- **Fechas**: DD/MM/YYYY (formato argentino)
- **UI**: Labels en espanol
- **Codigo**: Comentarios y codigo en ingles

## ADRs (Architecture Decision Records)

### ADR-001: MVP sobre MVC
- **Estado**: Aceptado
- **Contexto**: Necesitamos testear logica de negocio sin levantar JavaFX
- **Decision**: MVP con Presenters testeables via Mockito
- **Consecuencia**: FXML controllers quedan con < 50 lineas de logica

### ADR-002: SQLite sobre H2
- **Estado**: Aceptado
- **Contexto**: App desktop single-user en Mendoza, backup manual
- **Decision**: SQLite con WAL mode y foreign keys
- **Consecuencia**: Sin servidor, sin configuracion, backup = copiar archivo

### ADR-003: Stock Calculado sobre Columna
- **Estado**: Aceptado
- **Contexto**: Necesitamos auditoria completa de movimientos
- **Decision**: stock_movements como unica fuente de verdad
- **Consecuencia**: Consulta SUM ligera, sin sync bugs

### ADR-004: StackPane sobre TabPane
- **Estado**: Aceptado
- **Contexto**: Modulos independientes sin estado compartido
- **Decision**: StackPane con swap de FXML via MainPresenter
- **Consecuencia**: Cache de vistas, clean module isolation

### ADR-005: Error Handling por Capas
- **Estado**: Aceptado
- **Contexto**: Usuario final no tecnico necesita mensajes claros
- **Decision**: Presenter captura y muestra dialogo en espanol; capas inferiores lanzan excepciones
- **Consecuencia**: Logs detallados para developer, dialogo amigable para usuario

### ADR-006: Strict TDD
- **Estado**: Aceptado
- **Contexto**: Proyecto con especificaciones detalladas
- **Decision**: Red-Green-Refactor obligatorio para toda funcionalidad
- **Consecuencia**: 82 archivos de test, cobertura ~58% (sin contar JavaFX no testeable)

### ADR-007: Fotos de Factura en Directorio Aparte
- **Estado**: Aceptado
- **Contexto**: Necesitamos adjuntar facturas de compra sin mezclar con fotos de productos
- **Decision**: Subdirectorio `purchase-invoices/` bajo el directorio base de la app, manejado por PhotoUtils con metodos dedicados (`copyInvoiceImage`, `getInvoicePhotosDir`, `ensureInvoiceDir`)
- **Consecuencia**: Separacion limpia de archivos de productos y facturas, misma logica de UUID-based naming y eliminacion

### ADR-008: Register Sessions Groundwork
- **Estado**: Aceptado (groundwork)
- **Contexto**: Se necesita sistema de caja (apertura/cierre) para pedidos ya y local fisico
- **Decision**: Crear tabla `register_sessions` con campos pre-cargados para apertura/cierre futuro (expected_cash, actual_cash, close_date, status OPEN/CLOSED), pero sin implementar la logica de apertura/cierre aun
- **Consecuencia**: La tabla y sus indices estan listos; el model/repository/controller/presenter/FXML se implementaran cuando se active la funcionalidad de caja

## Build System

- **Maven** para compilacion, dependencias y empaquetado
- **jpackage** (perfil `-Pjpackage`) genera un `.exe` standalone para Windows
- El instalador se produce en `dist/installer/` y no requiere pre-instalacion de Java
- La aplicacion se puede desplegar en un pendrive USB y ejecutar directamente

- **Codigo**: Ingles (clases, metodos, variables, comentarios)
- **UI**: Espanol (labels, dialogos, mensajes de error)
- **Testing**: Tests en ingles, mensajes de assertion en ingles
- **Commits**: Conventional Commits en ingles
- **Moneda**: Formato ARS con CurrencyFormatter
- **Fechas**: DD/MM/YYYY con DateUtils
- **SQL**: Nombres de tabla/columna en lowercase con snake_case

## Requisitos del Sistema

- **OS**: Windows 10/11 (build con classifier `win` para JavaFX)
- **Java**: JDK 17+ (LTS)
- **RAM**: 256 MB minimo
- **Disco**: 50 MB para la aplicacion + tamano de la BD
- **Pantalla**: 1024x768 minimo (disenado para 1100x680)