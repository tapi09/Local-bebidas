# Manual de Uso — Software de Bebidas

## Requisitos del Sistema

- **Sistema Operativo**: Windows 10 o superior (64 bits)
- **Java**: JDK 17 o superior (incluido en el instalador)
- **Resolución de pantalla**: Mínimo 1280x768
- **Base de datos**: SQLite (incluida, no requiere instalación)

## Instalación

1. Ejecutar el instalador `Software de Bebidas-X.X.X.exe`
2. Seguir los pasos del asistente
3. Elegir directorio de instalación (recomendado: `C:\Program Files\Software de Bebidas`)
4. Marcar "Crear acceso directo en el escritorio"
5. Finalizar la instalación

La base de datos se crea automáticamente en `%APPDATA%\software-bebidas` al primer inicio. Esa misma carpeta contiene los respaldos (`backups/`), los logs (`logs/`), las exportaciones (`exports/`), las fotos de productos y el logo del negocio.

## Primer Inicio

Al abrir Software de Bebidas por primera vez:

1. La aplicación crea la base de datos vacía automáticamente
2. Ingresar con el usuario `admin` y la contraseña `admin123`
3. El sistema obliga a cambiar esa contraseña en el primer inicio de sesión
4. Se muestra la pantalla de inicio con el panel principal
5. Configurar el nombre y el logo del negocio en **Datos del negocio**
6. Configurar productos, proveedores y clientes antes de operar

---

## Pantalla Principal (Inicio)

El panel de inicio muestra:

- **Reloj**: Fecha y hora actual actualizadas en tiempo real
- **Resumen rápido**: Totales del día (ventas, productos bajos de stock)
- **Barra lateral**: Navegación entre módulos

### Atajos de Teclado

| Tecla | Acción |
|-------|--------|
| `F2` | Ventas (POS) |
| `F3` | Productos |
| `F4` | Stock |
| `F5` | Compras |
| `F6` | Reportes |
| `F7` | Alertas |
| `F8` | Proveedores |
| `Esc` | Salir de la aplicación |

---

## Módulos

### Productos

Gestión del catálogo de productos.

- **Agregar producto**: Completar nombre, categoría, presentación, precios de costo y venta, código de barras (opcional) y stock mínimo
- **Editar producto**: Seleccionar de la tabla y modificar campos
- **Buscar**: Por nombre, categoría o código de barras
- **Foto**: Se puede asignar una foto a cada producto

**Campos del producto**:
- Nombre (obligatorio)
- Categoría (obligatorio)
- Presentación (ej: "Botella 500ml", "Lata 473ml")
- Precio de costo
- Precio de venta (Local)
- Precio PedidosYa (si aplica)
- Código de barras (único, opcional)
- Stock mínimo (para alertas)
- SKU (se genera automáticamente)
- Foto (opcional)

### Datos del negocio

Solo para administradores. Se abre desde el botón **Datos del negocio** del menú lateral (o haciendo clic en el nombre del negocio en la barra superior).

- **Nombre del negocio**: se muestra en el menú lateral, la barra superior, la pantalla de inicio de sesión y los comprobantes. Por defecto es "Mi negocio".
- **Logo**: elegir una imagen PNG o JPG con **Elegir logo...**; se muestra una vista previa antes de guardar. **Quitar logo** vuelve a mostrar el nombre del negocio.
- Al guardar, los cambios se aplican de inmediato, sin reiniciar la aplicación.

### Ventas (POS)

Punto de venta para registrar ventas.

**Flujo de una venta**:

1. **Buscar producto**: Escribir en el campo de búsqueda o escanear código de barras
2. **Seleccionar cantidad**: Usar los botones +/− o escribir directamente
3. **Agregar al carrito**: Click en "Agregar al Carrito"
4. **Seleccionar canal**: "Local" o "PedidosYa" (cambia el precio automáticamente)
5. **Seleccionar método de pago**: Efectivo, Tarjeta Crédito, Débito, Transferencia
6. **Cliente**: Opcional — buscar y seleccionar cliente
7. **Descuento**: Opcional — porcentaje o monto fijo
8. **Completar venta**: Click en "Completar Venta" → genera comprobante

**Anular una venta**:

1. Click en "Historial de Ventas" (botón arriba a la derecha en POS)
2. Seleccionar la venta a anular
3. Click en "Anular Venta Seleccionada"
4. Confirmar la operación
5. Ingresar motivo de anulación (opcional)
6. La venta se marca como CANCELADA y el stock se restaura automáticamente

### Compras

Registro de compras a proveedores.

- **Nueva compra**: Seleccionar proveedor, fecha, agregar productos con cantidad y costo unitario
- **Adjuntar factura**: Se puede adjuntar una foto de la factura o remito del proveedor
- **Nota de crédito**: lotes, fechas de vencimiento (opcional)
- La compra actualiza automáticamente:
  - Stock de los productos (ENTRY)
  - Precio de costo promedio
  - Movimientos de stock

**Adjuntar foto de factura**:

1. En el formulario de nueva compra, hacer clic en "Adjuntar Factura"
2. Seleccionar la imagen de la factura (JPG o PNG, máximo 10 MB)
3. Se muestra una vista previa de la imagen adjunta
4. La foto se guarda junto con la compra y se puede ver al editar una compra existente
5. Para eliminar la foto, usar el botón de limpiar en el formulario

### Stock

Gestión y consulta de stock.

- **Stock actual**: Tabla con todos los productos y su stock disponible
- **Ajuste de stock**:
  - Positivo: Ingreso por ajuste (inventario)
  - Negativo: Egreso por ajuste (rotura, pérdida, vencimiento)
  - **Doble confirmación**: Los ajustes requieren dos confirmaciones para evitar errores
  - **Validación**: No permite stock negativo — si el stock actual es insuficiente para un egreso, se rechaza

### Alertas

Alertas automáticas del sistema.

- **Stock bajo**: Productos por debajo del mínimo configurado
- **Productos sin stock**: Agotados
- **Dashboard**: Resumen visual del estado del stock

Las alertas se actualizan automáticamente al navegar al módulo.

### Proveedores

Gestión de proveedores.

- **Agregar**: Nombre, contacto, teléfono, email, dirección
- **Editar**: Modificar datos del proveedor
- **Buscar**: Por nombre

### Reportes

Generación de reportes y exportación.

- **Margen por Producto**: Márgenes de ganancia por producto (costo, venta, margen %)
- **Ventas por Período**: Totales diarios/semanales de ventas (ingresos, transacciones, ticket promedio)
- **Ventas Detalladas por Día**: Desglose por día y producto — qué se vendió, cuántas unidades y a qué precio
- **Comparación de Canales**: Rendimiento Local vs PedidosYa
- **Rotación de Stock**: Cuántas veces se agotó el stock de cada producto
- **Productos Más Vendidos**: Ranking por cantidad vendida
- **Valor de Stock**: Valor total del inventario por producto
- **Exportar CSV**: Cada reporte se puede exportar a CSV (compatible con Excel)

Los archivos CSV se guardan en el directorio `exports/` dentro de `%APPDATA%\software-bebidas`.

**Reporte Ventas Detalladas por Día**:

1. Seleccionar "Ventas Detalladas por Día" en el combo de tipo de reporte
2. Elegir el rango de fechas (o usar un preset: Hoy, Esta Semana, Este Mes, Últimos 30 Días)
3. Hacer clic en "Generar"
4. Se muestra una tabla con: Fecha, Producto, Cantidad, Precio Unitario, Total Línea
5. Se muestra el Total General al pie de la tabla
6. Para exportar a CSV, hacer clic en "Exportar CSV"

---

## Funcionalidades del Sistema

### Modo Oscuro

- Botón "Modo Oscuro" en la barra lateral
- Alterna entre tema claro y oscuro
- La preferencia se guarda automáticamente

### Backup Automático

- La aplicación realiza backups automáticos de la base de datos
- **Frecuencia**: Cada 30 minutos
- **Formato**: copia de la base de datos con marca de tiempo (`softwaredebebidas_backup_YYYYMMDD_HHmmss.db`)
- **Retención**: Se mantienen los últimos 10 backups
- **Ubicación**: Directorio `backups/` dentro de `%APPDATA%\software-bebidas`
- Los backups se pueden copiar manualmente como respaldo adicional

### Logs

- La aplicación registra eventos en archivos de log
- **Ubicación**: Directorio `logs/` dentro de `%APPDATA%\software-bebidas`
- **Formato**: `softwaredebebidas-N.log` (N = número de rotación)
- **Rotación**: Cada archivo hasta 10MB, máximo 5 archivos
- **Nivel**: Captura desde INFO hasta SEVERE

---

## Solución de Problemas

### La aplicación no inicia

1. Verificar que Java 17+ esté instalado
2. Revisar los logs en `logs/softwaredebebidas-0.log`
3. Borrar el archivo `software-bebidas.db` para reiniciar la base de datos (se pierden datos)
4. Reinstalar la aplicación

### Error "Base de datos bloqueada"

- Cerrar todas las instancias de Software de Bebidas
- Verificar que no haya procesos bloqueando el archivo .db
- Reintentar

### Error al exportar CSV

- Verificar que el directorio `exports/` exista y tenga permisos de escritura
- Cerrar el archivo CSV si está abierto en Excel antes de exportar

---

## Mantenimiento

### Backup Manual

Para hacer un backup manual:
1. Cerrar la aplicación
2. Copiar el archivo `software-bebidas.db` a una ubicación segura
3. Incluir también el directorio `backups/` si se desea conservar el historial

### Restauración de Backup

1. Cerrar la aplicación
2. Reemplazar `software-bebidas.db` con el backup deseado
3. Iniciar la aplicación

### Limpieza de Logs

Los logs se rotan automáticamente. Para limpieza manual, borrar archivos viejos del directorio `logs/`.
