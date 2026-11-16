# Interfaz de Contabilidad

Interfaz web en Angular para consultar la actividad financiera, administrar cuentas y tipos de cuenta, registrar comprobantes y revisar estados financieros.

## Funcionalidades

- Resumen con ingresos, gastos, utilidad y estado del balance.
- Comprobantes con fecha, observaciones y movimientos por cuenta.
- Validación de que el Debe y el Haber coincidan antes de guardar.
- Búsqueda de cabeceras y detalles, filtros por fechas y operaciones de alta, edición y eliminación para comprobantes, cuentas y tipos de cuenta.
- Inicio de sesión, administración de usuarios y reportes calculados a partir de los movimientos.
- Balance general y estado de resultados con opción de impresión.

La interfaz requiere que el servidor Java y Oracle estén configurados; ya no usa los registros de demostración como datos contables.

## Preparación de Oracle

El esquema exportado de Oracle está en [Oraclecontabilidad.sql](Oraclecontabilidad.sql).
Se ha preparado [database/prepare_oracle.sql](database/prepare_oracle.sql) para agregar
secuencias, naturaleza y clasificación de los tipos de cuenta y una tabla separada
para usuarios de la aplicación. Este script **no se ha ejecutado ni probado contra
la base de datos**. No conecta la aplicación y no crea un usuario administrador.

Antes de ejecutarlo, haz una copia de seguridad, revisa el script y detén todas las
aplicaciones que escriban en las tablas. Conéctate como `CONTABILIDAD` en SQL Developer
y ejecútalo como script (F5). Las secuencias parten por encima del mayor identificador
existente; escribir en las tablas durante la preparación podría causar colisiones.
Oracle confirma automáticamente las instrucciones DDL, por lo que un error puede
dejar cambios parcialmente aplicados: no vuelvas a ejecutarlo sin revisar el estado.
Los tipos de cuenta existentes quedan sin naturaleza ni clasificación; deben
revisarse y completarse antes de calcular reportes. No almacenes contraseñas en
archivos SQL ni en el repositorio.

## Servidor Java

El proyecto [backend](backend) contiene la API Java para autenticación, usuarios,
cuentas, tipos de cuenta y comprobantes. Los detalles del comprobante y su
cabecera se guardan en una sola transacción. Las claves y los códigos de cuenta
se generan en el servidor. Los reportes usan los movimientos contables guardados.

Utiliza Java 17 o superior y Maven. En una terminal local configura
`ORACLE_USERNAME=CONTABILIDAD`, `ORACLE_PASSWORD` y, si es necesario,
`ORACLE_URL` (por defecto `jdbc:oracle:thin:@//localhost:1521/FREEPDB1`).
No escribas contraseñas en archivos ni las compartas en el chat.

1. Haz una copia de seguridad y revisa `database/prepare_oracle.sql`.
2. Conéctate como `CONTABILIDAD` en SQL Developer y ejecuta el script con F5.
   No se ha ejecutado ni validado contra tu base real. Oracle puede confirmar
   cada DDL antes de que termine el script; si falla, revisa el esquema antes
   de volver a intentarlo.
3. En los tipos de cuenta existentes, entra a **Plan de cuentas → Tipos de
   cuenta → Editar** y asigna explícitamente su naturaleza y clasificación.
   Los reportes se bloquean mientras falte esa configuración.
4. Con las variables Oracle definidas en la terminal, crea el primer administrador
   (la contraseña se solicita de forma interactiva y no se guarda en el código):

```powershell
mvn -f backend\pom.xml compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java "-Dexec.mainClass=ec.contabilidad.CreateAdministrator"
```

5. Inicia el servidor Java en una terminal:

```powershell
mvn -f backend\pom.xml spring-boot:run
```

6. En otra terminal inicia Angular y abre `http://localhost:4200/`:

```powershell
npm start
```

La interfaz redirige `/api` al servidor local en el puerto 8080. No uses la
aplicación con datos reales hasta aplicar la migración y validar la conexión,
las cuentas, los códigos generados y los reportes con el responsable contable.

## Desarrollo

Instala una versión de Node.js compatible con Angular 21 y las dependencias del proyecto:

```bash
npm install
npm start
```

La aplicación estará disponible en `http://localhost:4200/`.

## Pruebas

```bash
npm run build
npm test -- --watch=false
mvn -f backend\pom.xml test
```

Las pruebas Java se ejecutan contra H2 en modo de compatibilidad; no sustituyen
una prueba de conexión ni una validación de DDL con Oracle.
