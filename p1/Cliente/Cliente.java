package Cliente;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Cliente {

    private static final String HOST_SERVIDOR = "localhost";
    private static final int PORT_CONTROL = 7777;
    private static final int PORT_DATOS = 7778;
    private static final String DIR_BASE_LOCAL = "./Cliente/Archivos";

    private static String dirLocalActual = DIR_BASE_LOCAL;

    public static void main(String[] args) {
        File dirLocal = new File(DIR_BASE_LOCAL);
        dirLocal.mkdirs();
        dirLocalActual = dirLocal.getAbsolutePath();

        Scanner sc = new Scanner(System.in);
        System.out.println(
            "Conectando a " + HOST_SERVIDOR + ":" + PORT_CONTROL + "..."
        );

        try (Socket socketControl = new Socket(HOST_SERVIDOR, PORT_CONTROL)) {
            BufferedReader in = new BufferedReader(
                new InputStreamReader(socketControl.getInputStream())
            );
            PrintWriter out = new PrintWriter(
                socketControl.getOutputStream(),
                true
            );

            System.out.println("Handshake de control: " + in.readLine());

            while (true) {
                System.out.println("\n[LOCAL]:");
                System.out.print("p1-cli> ");
                String entrada = sc.nextLine();
                if (entrada.trim().isEmpty()) continue;

                String[] partes = entrada.split(" ", 2);
                String comando = partes[0].toLowerCase();
                String argumento = partes.length > 1 ? partes[1] : "";

                if (comando.equals("exit")) {
                    out.println("EXIT");
                    break;
                }

                switch (comando) {
                    // =========================================================
                    // OPERACIONES REMOTAS (Hacia el Servidor vía TCP)
                    // =========================================================
                    case "ls":
                        if (argumento.isEmpty()) {
                            out.println("LS");
                        } else {
                            out.println("LS " + argumento);
                        }
                        System.out.println(
                            "\n[SERVER]:\n" + in.readLine().replace(";", "\n")
                        );
                        break;
                    case "cd":
                        if (argumento.isEmpty()) {
                            System.out.println("Uso: cd <directorio>");
                            break;
                        }
                        out.println("CD " + argumento);
                        System.out.println("[SERVER]: " + in.readLine());
                        break;
                    case "touch":
                        if (argumento.isEmpty()) {
                            System.out.println("Uso: touch <archivo>");
                            break;
                        }
                        out.println("TOUCH " + argumento);
                        System.out.println("[SERVER]: " + in.readLine());
                        break;
                    case "mkdir":
                        if (argumento.isEmpty()) {
                            System.out.println("Uso: mkdir <carpeta>");
                            break;
                        }
                        out.println("MKDIR " + argumento);
                        System.out.println("[SERVER]: " + in.readLine());
                        break;
                    case "mv":
                        if (argumento.isEmpty() || !argumento.contains(" ")) {
                            System.out.println(
                                "Uso: mv <nombre_actual> <nuevo_nombre>"
                            );
                            break;
                        }
                        out.println("MV " + argumento);
                        System.out.println("[SERVER]: " + in.readLine());
                        break;
                    case "rm":
                        if (argumento.isEmpty()) {
                            System.out.println("Uso: rm <archivo>");
                            break;
                        }
                        out.println("RM " + argumento);
                        System.out.println("[SERVER]: " + in.readLine());
                        break;
                    case "rmdir":
                        if (argumento.isEmpty()) {
                            System.out.println("Uso: rmdir <carpeta>");
                            break;
                        }
                        out.println("RMDIR " + argumento);
                        System.out.println("[SERVER]: " + in.readLine());
                        break;
                    // =========================================================
                    // OPERACIONES LOCALES (Espejo - Ejecutadas en el Cliente)
                    // =========================================================
                    case "lls":
                        File folder;
                        if (argumento.isEmpty()) {
                            folder = new File(dirLocalActual);
                        } else {
                            folder = new File(dirLocalActual, argumento);
                        }

                        // Validamos que la carpeta local realmente exista antes de listar
                        if (!folder.exists() || !folder.isDirectory()) {
                            System.out.println(
                                "[Local Error]: El directorio especificado no existe o es inválido."
                            );
                            break;
                        }

                        File[] lista = folder.listFiles();
                        System.out.println("\n[Local]:");
                        if (lista == null || lista.length == 0) {
                            System.out.println("  Carpeta vacía.");
                        } else {
                            for (File f : lista) {
                                System.out.println(
                                    f.isDirectory()
                                        ? "  [DIR]  " + f.getName()
                                        : "  [FILE] " + f.getName()
                                );
                            }
                        }
                        break;
                    case "lcd":
                        if (argumento.isEmpty()) {
                            System.out.println("Uso: lcd <directorio>");
                            break;
                        }
                        if (argumento.equals("..")) {
                            if (
                                !dirLocalActual.equals(
                                    new File(DIR_BASE_LOCAL).getAbsolutePath()
                                )
                            ) {
                                dirLocalActual = new File(
                                    dirLocalActual
                                ).getParent();
                            }
                        } else {
                            File target = new File(dirLocalActual, argumento);
                            if (
                                target.exists() && target.isDirectory()
                            ) dirLocalActual = target.getAbsolutePath();
                            else System.out.println(
                                "[LOCAL Error]: Ruta inválida."
                            );
                        }
                        break;
                    case "ltouch":
                        if (argumento.isEmpty()) {
                            System.out.println("Uso: ltouch <archivo>");
                            break;
                        }
                        File fl = new File(dirLocalActual, argumento);
                        try {
                            if (fl.createNewFile()) System.out.println(
                                "[LOCAL]: Archivo vacío creado."
                            );
                            else System.out.println(
                                "[LOCAL Error]: El archivo ya existe."
                            );
                        } catch (IOException e) {
                            System.out.println(
                                "[LOCAL Error]: " + e.getMessage()
                            );
                        }
                        break;
                    case "lmkdir":
                        if (argumento.isEmpty()) {
                            System.out.println("Uso: lmkdir <carpeta>");
                            break;
                        }
                        File dl = new File(dirLocalActual, argumento);
                        if (dl.mkdir()) System.out.println(
                            "[LOCAL]: Carpeta creada con éxito."
                        );
                        else System.out.println(
                            "[LOCAL Error]: No se pudo crear."
                        );
                        break;
                    case "lmv": {
                        String[] targets = argumento.split(" ", 2);
                        if (
                            argumento.isEmpty() ||
                            targets.length < 2 ||
                            targets[0].isEmpty() ||
                            targets[1].isEmpty()
                        ) {
                            System.out.println(
                                "[Local Error]: Uso: lmv <nombre_actual> <nuevo_nombre_o_carpeta>"
                            );
                            break;
                        }

                        File viejoLocal = new File(dirLocalActual, targets[0]);
                        File nuevoLocal = new File(dirLocalActual, targets[1]);

                        if (!viejoLocal.exists()) {
                            System.out.println(
                                "[Local Error]: El elemento de origen no existe."
                            );
                            break;
                        }

                        // Si el destino es una carpeta local, recalculamos la ruta para meter el archivo dentro
                        if (nuevoLocal.exists() && nuevoLocal.isDirectory()) {
                            nuevoLocal = new File(
                                nuevoLocal,
                                viejoLocal.getName()
                            );
                        }

                        if (nuevoLocal.exists()) {
                            System.out.println(
                                "[Local Error]: El nombre de destino ya está ocupado."
                            );
                        } else if (viejoLocal.renameTo(nuevoLocal)) {
                            System.out.println(
                                "[Local]: Elemento movido/renombrado con éxito."
                            );
                        } else {
                            System.out.println(
                                "[Local Error]: Fallo interno al renombrar."
                            );
                        }
                        break;
                    }
                    case "lrm":
                        if (argumento.isEmpty()) {
                            System.out.println("Uso: lrm <archivo>");
                            break;
                        }
                        File fileDel = new File(dirLocalActual, argumento);
                        if (
                            fileDel.exists() &&
                            !fileDel.isDirectory() &&
                            fileDel.delete()
                        ) System.out.println("[LOCAL]: Archivo eliminado.");
                        else System.out.println(
                            "[LOCAL Error]: Archivo inválido."
                        );
                        break;
                    case "lrmdir":
                        if (argumento.isEmpty()) {
                            System.out.println("Uso: lrmdir <carpeta>");
                            break;
                        }
                        File dirDel = new File(dirLocalActual, argumento);
                        if (
                            dirDel.exists() &&
                            dirDel.isDirectory() &&
                            dirDel.delete()
                        ) System.out.println("[LOCAL]: Directorio eliminado.");
                        else System.out.println(
                            "[LOCAL Error]: Carpeta no vacía o inexistente."
                        );
                        break;
                    // =========================================================
                    // TRANSFERENCIA DE DATOS
                    // =========================================================
                    case "get":
                        if (argumento.isEmpty()) {
                            System.out.println("Uso: get <elemento>");
                            break;
                        }
                        out.println("DOWNLOAD " + argumento);
                        String resDown = in.readLine();
                        if (resDown.startsWith("START_DOWNLOAD")) {
                            long tamano = Long.parseLong(resDown.split(" ")[1]);
                            try {
                                Thread.sleep(80);
                            } catch (InterruptedException e) {}

                            try (
                                Socket socketDatos = new Socket(
                                    HOST_SERVIDOR,
                                    PORT_DATOS
                                );
                                InputStream is = socketDatos.getInputStream();
                                FileOutputStream fos = new FileOutputStream(
                                    new File(
                                        dirLocalActual,
                                        argumento.contains(".")
                                            ? argumento
                                            : argumento + ".zip"
                                    )
                                )
                            ) {
                                byte[] buffer = new byte[4096];
                                int bytesLeidos;
                                long total = 0;
                                while (
                                    total < tamano &&
                                    (bytesLeidos = is.read(
                                        buffer,
                                        0,
                                        (int) Math.min(
                                            buffer.length,
                                            tamano - total
                                        )
                                    )) != -1
                                ) {
                                    fos.write(buffer, 0, bytesLeidos);
                                    total += bytesLeidos;
                                }
                                System.out.println(
                                    "[Datos]: Descarga finalizada (" +
                                        total +
                                        " bytes)."
                                );
                            }
                        } else System.out.println(resDown);
                        break;
                    case "put":
                        if (argumento.isEmpty()) {
                            System.out.println("Uso: put <archivo>");
                            break;
                        }
                        File fileLocal = new File(dirLocalActual, argumento);
                        if (!fileLocal.exists() || fileLocal.isDirectory()) {
                            System.out.println(
                                "Error: Archivo local no encontrado en la ruta actual."
                            );
                            break;
                        }
                        out.println(
                            "UPLOAD " +
                                fileLocal.getName() +
                                " " +
                                fileLocal.length()
                        );
                        if (in.readLine().equals("READY_TO_RECEIVE")) {
                            try {
                                Thread.sleep(80);
                            } catch (InterruptedException e) {}
                            try (
                                Socket socketDatos = new Socket(
                                    HOST_SERVIDOR,
                                    PORT_DATOS
                                );
                                FileInputStream fis = new FileInputStream(
                                    fileLocal
                                );
                                OutputStream os = socketDatos.getOutputStream()
                            ) {
                                byte[] buffer = new byte[4096];
                                int bytesLeidos;
                                while ((bytesLeidos = fis.read(buffer)) != -1)
                                    os.write(buffer, 0, bytesLeidos);
                                os.flush();
                            }
                            System.out.println("[Server]: " + in.readLine());
                        }
                        break;
                    default:
                        System.out.println("Comando inválido.");
                }
            }
        } catch (IOException e) {
            System.err.println(
                "Desconexión del canal de control: " + e.getMessage()
            );
        }
    }
}
