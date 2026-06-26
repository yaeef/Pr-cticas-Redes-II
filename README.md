# Repository de Prácticas - Aplicaciones para Comunicaciones en Red

Este repositorio contiene el desarrollo de las prácticas de programación orientadas a la arquitectura de sistemas distribuidos, protocolos de la capa de aplicación y sockets avanzados. Cada proyecto aborda problemas de concurrencia, optimización de recursos y diseño de protocolos a nivel de red y aplicación.

---

## 📌 Datos Institucionales

- **Institución:** Instituto Politécnico Nacional (IPN)
- **Escuela:** Escuela Superior de Cómputo (ESCOM)
- **Materia:** Aplicaciones para Comunicaciones en Red
- **Profesor:** Moreno Cervantes Axel Ernesto
- **Alumno:** Erreguin Franco Yair Alejandro
- **Boleta:** 2022630121

---

## 📁 Estructura del Repositorio

El repositorio se organiza por proyectos o prácticas individuales. Cada directorio incluye el código fuente correspondiente al Cliente y al Servidor, además de los archivos de configuración requeridos:

```text
├── p1/            # Fundamentos de Sockets TCP
├── p2/            # Fundamentos de Sockets UDP, control de error y ventana deslizante.
├── p3/            # Uso de Sockets UDP para el streaming de archivos mediante hilos y tuberias. 
├── p4/            # Formato de petición y respuesta GET; Uso de Web Crawling
├── p5/            # Chat multiusuario con socket no bloqueante con Java NIO (TCP)
├── reportes/      # Reporte de cada práctica terminada.
└── tareas/        # Tareas entregadas
```

## 🚀 Instrucciones de Ejecución General

### Para Compilar (Java):

Abre una terminal dentro de la carpeta de la práctica correspondiente y compila ambos componentes:

```bash
javac NombreServer.java NombreClient.java
```

### Para Ejecutar

1. **Iniciar el Servidor:** Levanta primero el nodo central que escuchará las peticiones en el puerto configurado.

```bash
java NombreServer
```

2. **Iniciar los Clientes:** Abre terminales adicionales por cada usuario independiente que desees simular en la red local.

```bash
java NombreClient
```

## 📄 Licencia

Este repositorio fue desarrollado estrictamente con fines académicos para la evaluación de la materia.
