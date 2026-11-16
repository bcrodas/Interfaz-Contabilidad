CREATE TABLE TipoCuenta(

id_tipo NUMBER PRIMARY KEY,

nombre VARCHAR2(50) NOT NULL

);
--Crear tabla Cuenta
CREATE TABLE Cuenta(

id_cuenta NUMBER PRIMARY KEY,

codigo VARCHAR2(20),

nombre VARCHAR2(100),

id_tipo NUMBER,

CONSTRAINT fk_tipo

FOREIGN KEY(id_tipo)

REFERENCES TipoCuenta(id_tipo)

);
--Crear tabls Cabecera del comprobante
CREATE TABLE CabeceraComprobante(

id_comprobante NUMBER PRIMARY KEY,

fecha DATE,

observaciones VARCHAR2(200)

);
--Crear tabla Detalle del comprobante
CREATE TABLE DetalleComprobante(

id_detalle NUMBER PRIMARY KEY,

id_comprobante NUMBER,

id_cuenta NUMBER,

debe NUMBER(10,2),

haber NUMBER(10,2),

CONSTRAINT fk_cabecera

FOREIGN KEY(id_comprobante)

REFERENCES CabeceraComprobante(id_comprobante),

CONSTRAINT fk_cuenta

FOREIGN KEY(id_cuenta)

REFERENCES Cuenta(id_cuenta)

);

SELECT table_name
FROM user_tables;

-- Insertar datos en tabla TipoCuenta

INSERT INTO TipoCuenta VALUES(1,'Activo');

INSERT INTO TipoCuenta VALUES(2,'Pasivo');

INSERT INTO TipoCuenta VALUES(3,'Capital');

INSERT INTO TipoCuenta VALUES(4,'Ingreso');

INSERT INTO TipoCuenta VALUES(5,'Egreso');

COMMIT;

-- Insertar datos en tabla Cuenta
INSERT INTO Cuenta VALUES(1,'1.1.1','Caja',1);

INSERT INTO Cuenta VALUES(2,'1.1.2','Banco',1);

INSERT INTO Cuenta VALUES(3,'2.1.1','Proveedores',2);

INSERT INTO Cuenta VALUES(4,'3.1.1','Capital Social',3);

INSERT INTO Cuenta VALUES(5,'4.1.1','Ventas',4);

INSERT INTO Cuenta VALUES(6,'5.1.1','Gastos',5);

COMMIT;

-- Verificar que todo quedó bien

SELECT * FROM TipoCuenta;
