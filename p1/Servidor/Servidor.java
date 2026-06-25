package Servidor;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Servidor {

    private static final int PUERTO_CONTROL = 7777; //Puerto para comandos
    private static final int PUERTO_DATOS = 7778; //Puerto para datos
    private static final String DIR_BASE = "./Servidor/Archivos";

    private static String dirActual = DIR_BASE;

    public static void main(String[] args) {
        new File(DIR_BASE).mkdirs();

        try (ServerSocket serverControl = new ServerSocket()) {
            serverControl.setReuseAddress(true);
            serverControl.bind(new InetSocketAddress(PUERTO_CONTROL));

            //Bucle para aceptar clientes
            while (true) {
                dirActual = DIR_BASE; //Siempre empezamos en la raiz
                refrescarConsola(
                    dirActual,
                    "ESPERANDO",
                    "Cliente...",
                    "0.0.0.0"
                );

                try (Socket socketCliente = serverControl.accept()) {
                    String clientIp = socketCliente
                        .getInetAddress()
                        .getHostAddress();

                    try (
                        BufferedReader in = new BufferedReader(
                            new InputStreamReader(
                                socketCliente.getInputStream()
                            )
                        );
                        PrintWriter out = new PrintWriter(
                            socketCliente.getOutputStream(),
                            true
                        )
                    ) {
                        // Handshake de control inicial
                        out.println("CONEXION_OK");
                        refrescarConsola(
                            dirActual,
                            "HANDSHAKE",
                            "CONEXION_OK",
                            clientIp
                        );

                        String linea; //Se construyen y se envian cadenas, despues se hace un split buscando espacios
                        while ((linea = in.readLine()) != null) {
                            String[] partes = linea.split(" ", 2);
                            String comando = partes[0].toUpperCase();
                            String argumento =
                                partes.length > 1 ? partes[1] : "";

                            if (comando.equals("EXIT")) {
                                //Si se recibe un exit entonces terminamos la conexion
                                out.println("Te la lavas!");
                                break;
                            }

                            switch (comando) {
                                case "LS":
                                    listarArchivos(argumento, out);
                                    break;
                                case "CD":
                                    cambiarDirectorio(argumento, out);
                                    break;
                                case "TOUCH":
                                    crearArchivoRemoto(argumento, out);
                                    break;
                                case "MKDIR":
                                    crearCarpetaRemota(argumento, out);
                                    break;
                                case "MV":
                                    renombrarElementoRemoto(argumento, out);
                                    break;
                                case "RM":
                                    eliminarArchivoRemoto(argumento, out);
                                    break;
                                case "RMDIR":
                                    eliminarCarpetaRemota(argumento, out);
                                    break;
                                case "DOWNLOAD":
                                    enviarElementoAlCliente(argumento, out);
                                    break;
                                case "UPLOAD":
                                    recibirElementoDelCliente(argumento, out);
                                    break;
                                default:
                                    out.println("ERROR: Comando invalido.");
                                    break;
                            }
                            refrescarConsola(
                                dirActual,
                                comando,
                                argumento,
                                clientIp
                            );
                        }
                    } catch (IOException e) {
                        System.out.println(
                            "Conexion con el cliente interrumpida de forma abrupta."
                        );
                    }

                    refrescarConsola(
                        dirActual,
                        "DESCONECTADO",
                        "Cierre de Socket",
                        clientIp
                    );
                } // Aqui se cierra automaticamente el socket del cliente actual antes de iterar
            }
        } catch (IOException e) {
            System.err.println(
                "Error critico en el ServerSocket: " + e.getMessage()
            );
        }
    }

    public static void refrescarConsola( //Para listar en la consola del server el directorio en su último estado
        String dirActual,
        String comando,
        String argumento,
        String clientIp
    ) {
        System.out.print("\033[H\033[2J");
        System.out.flush();

        System.out.println(
            "================================================================="
        );
        System.out.println("               SERVIDOR PRACTICA 1            ");
        System.out.println(
            "================================================================="
        );
        System.out.println("[CLIENTE]: " + clientIp);
        System.out.println("[ÚLTIMA ACCIÓN]: " + comando + " " + argumento);
        System.out.println("[RUTA ACTUAL REMOTA]: " + dirActual);
        System.out.println(
            "-----------------------------------------------------------------"
        );
        System.out.println("Contenido actual del directorio:");

        File carpeta = new File(dirActual);
        File[] lista = carpeta.listFiles();
        if (lista == null || lista.length == 0) {
            System.out.println("  (Carpeta vacía)");
        } else {
            for (File f : lista) {
                if (f.isDirectory()) {
                    System.out.println("  [DIR]  " + f.getName());
                } else {
                    System.out.println(
                        "  [FILE] " +
                            f.getName() +
                            " (" +
                            f.length() +
                            " bytes)"
                    );
                }
            }
        }
        System.out.println(
            "================================================================="
        );
    }

    // =========================================================================
    // METODOS OPERATIVOS DEL SERVIDOR (ESTÁTICOS FLATS)
    // =========================================================================

    private static void renombrarElementoRemoto(
        String argumento,
        PrintWriter out
    ) {
        String[] nombres = argumento.split(" ", 2);
        if (
            nombres.length < 2 || nombres[0].isEmpty() || nombres[1].isEmpty()
        ) {
            out.println(
                "ERROR: Sintaxis inválida. Uso: mv <nombre_actual> <nuevo_nombre>"
            );
            return;
        }

        File viejo = new File(dirActual, nombres[0]);
        File nuevo = new File(dirActual, nombres[1]);

        if (!viejo.exists()) {
            out.println("ERROR: El elemento de origen no existe.");
            return;
        }

        // Si el destino remoto es una carpeta, se recalcula el inodo objetivo dentro de ella
        if (nuevo.exists() && nuevo.isDirectory()) {
            nuevo = new File(nuevo, viejo.getName());
        }

        if (nuevo.exists()) {
            out.println("ERROR: El nombre de destino ya está ocupado.");
        } else if (viejo.renameTo(nuevo)) {
            out.println("OK: Elemento renombrado correctamente.");
        } else {
            out.println(
                "ERROR: No se pudo renombrar (posible problema de permisos)."
            );
        }
    }

    private static void crearArchivoRemoto(String nombre, PrintWriter out) {
        if (nombre.isEmpty()) {
            out.println("ERROR: Nombre vacío");
            return;
        }
        File f = new File(dirActual, nombre);
        try {
            if (f.createNewFile()) out.println(
                "OK: Archivo creado en servidor."
            );
            else out.println("ERROR: El archivo ya existe.");
        } catch (IOException e) {
            out.println("ERROR: " + e.getMessage());
        }
    }

    private static void crearCarpetaRemota(String nombre, PrintWriter out) {
        if (nombre.isEmpty()) {
            out.println("ERROR: Nombre vacío");
            return;
        }
        File f = new File(dirActual, nombre);
        if (f.mkdir()) out.println("OK: Carpeta creada en servidor.");
        else out.println("ERROR: No se pudo crear la carpeta.");
    }

    private static void eliminarArchivoRemoto(String nombre, PrintWriter out) {
        File f = new File(dirActual, nombre);
        if (f.exists() && !f.isDirectory() && f.delete()) out.println(
            "OK: Archivo eliminado."
        );
        else out.println("ERROR: No se pudo eliminar el archivo.");
    }

    private static void eliminarCarpetaRemota(String nombre, PrintWriter out) {
        File f = new File(dirActual, nombre);
        if (f.exists() && f.isDirectory() && f.delete()) out.println(
            "OK: Carpeta eliminada."
        );
        else out.println("ERROR: Carpeta no vacía o no existe.");
    }

    private static void listarArchivos(String argumento, PrintWriter out) {
        File carpeta;
        if (argumento.isEmpty()) {
            carpeta = new File(dirActual);
        } else {
            carpeta = new File(dirActual, argumento);
        }

        // Validamos que la carpeta remota solicitada realmente exista y sea un directorio
        if (!carpeta.exists() || !carpeta.isDirectory()) {
            out.println(
                "ERROR: El directorio especificado no existe o es invalido.;"
            );
            return;
        }

        File[] lista = carpeta.listFiles();
        if (lista == null || lista.length == 0) {
            out.println("Carpeta vacía.;");
        } else {
            StringBuilder sb = new StringBuilder();
            for (File f : lista) {
                sb.append(f.isDirectory() ? "[DIR] " : "[FILE] ")
                    .append(f.getName())
                    .append(";");
            }
            out.println(sb.toString());
        }
    }

    private static void cambiarDirectorio(String destino, PrintWriter out) {
        if (destino.equals("..")) {
            if (!dirActual.equals(DIR_BASE)) dirActual = new File(
                dirActual
            ).getParent();
        } else {
            File nuevoDir = new File(dirActual, destino);
            if (nuevoDir.exists() && nuevoDir.isDirectory()) {
                dirActual = nuevoDir.getAbsolutePath();
            } else {
                out.println("ERROR: Ruta inválida.");
                return;
            }
        }
        out.println("NUEVO_DIR: " + dirActual);
    }

    private static void enviarElementoAlCliente(
        String nombre,
        PrintWriter out
    ) {
        File file = new File(dirActual, nombre);
        if (!file.exists()) {
            out.println("ERROR: Inexistente");
            return;
        }

        boolean esCarpeta = file.isDirectory();
        File archivoAEnviar = file;

        if (esCarpeta) {
            archivoAEnviar = new File(dirActual, nombre + ".zip");
            try (
                ZipOutputStream zos = new ZipOutputStream(
                    new FileOutputStream(archivoAEnviar)
                )
            ) {
                comprimirCarpeta(file, file.getName(), zos);
            } catch (IOException e) {
                out.println("ERROR: Zip Fail");
                return;
            }
        }

        out.println("START_DOWNLOAD " + archivoAEnviar.length());

        try (ServerSocket serverDatos = new ServerSocket()) {
            serverDatos.setReuseAddress(true);
            serverDatos.bind(new InetSocketAddress(PUERTO_DATOS));
            try (
                Socket socketDatos = serverDatos.accept();
                FileInputStream fis = new FileInputStream(archivoAEnviar);
                OutputStream os = socketDatos.getOutputStream()
            ) {
                byte[] buffer = new byte[4096];
                int bytesLeidos;
                while ((bytesLeidos = fis.read(buffer)) != -1)
                    os.write(buffer, 0, bytesLeidos);
                os.flush();
            }
        } catch (IOException e) {
            System.err.println("Error datos: " + e.getMessage());
        } finally {
            if (esCarpeta && archivoAEnviar.exists()) archivoAEnviar.delete();
        }
    }

    private static void recibirElementoDelCliente(
        String datosComando,
        PrintWriter out
    ) {
        String[] tokens = datosComando.split(" ");
        String nombre = tokens[0];
        long tamano = Long.parseLong(tokens[1]);

        File destino = new File(dirActual, nombre);
        out.println("READY_TO_RECEIVE");

        try (ServerSocket serverDatos = new ServerSocket()) {
            serverDatos.setReuseAddress(true);
            serverDatos.bind(new InetSocketAddress(PUERTO_DATOS));
            try (
                Socket socketDatos = serverDatos.accept();
                InputStream is = socketDatos.getInputStream();
                FileOutputStream fos = new FileOutputStream(destino)
            ) {
                byte[] buffer = new byte[4096];
                int bytesLeidos;
                long totalRecibido = 0;
                while (
                    totalRecibido < tamano &&
                    (bytesLeidos = is.read(
                        buffer,
                        0,
                        (int) Math.min(buffer.length, tamano - totalRecibido)
                    )) != -1
                ) {
                    fos.write(buffer, 0, bytesLeidos);
                    totalRecibido += bytesLeidos;
                }
            }

            // Si lo que subio el cliente era una carpeta comprimida, la extraemos en el servidor
            if (nombre.endsWith(".zip")) {
                descomprimirCarpetaServidor(destino, dirActual);
                destino.delete(); // Borramos el zip temporal recibido
            }

            out.println("OK: Elemento subido.");
        } catch (IOException e) {
            out.println("ERROR: Fail");
        }
    }

    private static void comprimirCarpeta(
        File archivo,
        String rutaBase,
        ZipOutputStream zos
    ) throws IOException {
        if (archivo.isDirectory()) {
            for (File f : archivo.listFiles())
                comprimirCarpeta(f, rutaBase + "/" + f.getName(), zos);
        } else {
            try (FileInputStream fis = new FileInputStream(archivo)) {
                zos.putNextEntry(new ZipEntry(rutaBase));
                byte[] buffer = new byte[4096];
                int bytes;
                while ((bytes = fis.read(buffer)) != -1)
                    zos.write(buffer, 0, bytes);
                zos.closeEntry();
            }
        }
    }

    private static void descomprimirCarpetaServidor(
        File archivoZip,
        String destinoPath
    ) throws IOException {
        File destDir = new File(destinoPath);
        try (
            java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new FileInputStream(archivoZip)
            )
        ) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File file = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    file.getParentFile().mkdirs();
                    try (
                        BufferedOutputStream bos = new BufferedOutputStream(
                            new FileOutputStream(file)
                        )
                    ) {
                        byte[] buffer = new byte[4096];
                        int count;
                        while ((count = zis.read(buffer)) > 0) {
                            bos.write(buffer, 0, count);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }
}
