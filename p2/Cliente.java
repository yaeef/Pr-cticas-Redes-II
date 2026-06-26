import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import javax.sound.sampled.*;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;

/**
 * CLIENTE UDP — Go-Back-N
 *
 * 1. Pide el catálogo al servidor y lo muestra.
 * 2. El usuario elige una canción.
 * 3. Descarga el archivo usando ventana deslizante Go-Back-N:
 *    - Acepta solo paquetes en orden (expectedSeq).
 *    - Si llega uno fuera de orden, lo descarta (no envía ACK).
 *    - El servidor detecta el timeout y reenvía desde donde se quedó.
 * 4. Ensambla el archivo y lo reproduce.
 */
public class Cliente {

    static final String SERVIDOR = "localhost";
    static final int PUERTO = 9876;
    static final int TIMEOUT = 2000;

    public static void main(String[] args) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        InetAddress addr = InetAddress.getByName(SERVIDOR);
        socket.setSoTimeout(TIMEOUT);

        Scanner sc = new Scanner(System.in);

        while (true) {
            limpiarPantalla();
            System.out.println("\n========== MENÚ CLIENTE ==========");
            System.out.println("1. Listar catálogo del servidor y descargar");
            System.out.println("2. Reproducir canción descargada localmente");
            System.out.println("3. Salir");
            System.out.print("Seleccione una opción: ");
            String opcion = sc.nextLine().trim();

            if (opcion.equals("1")) {
                // ── 1. Pedir catálogo ─────────────────────────────────────────────
                Paquete req = new Paquete(Paquete.CATALOGO, 0, 0, new byte[0]);
                enviar(socket, req, addr, PUERTO);

                byte[] buf = new byte[65535];
                DatagramPacket dp = new DatagramPacket(buf, buf.length);
                socket.receive(dp);
                Paquete catalogo = Paquete.desdBytes(
                    dp.getData(),
                    dp.getLength()
                );

                String[] canciones = new String(catalogo.datos).split("\n");
                System.out.println("\n=== Catálogo del Servidor ===");
                for (int i = 0; i < canciones.length; i++) System.out.println(
                    i + ". " + canciones[i]
                );

                // ── 2. El usuario elige ───────────────────────────────────────────
                System.out.print(
                    "\nNúmero de canción a descargar (-1 para regresar): "
                );
                int eleccion = Integer.parseInt(sc.nextLine().trim());

                if (eleccion == -1) {
                    System.out.println("Regresando al menú principal...");
                    continue; // Aborta la descarga y vuelve al inicio del while
                }

                if (eleccion < 0 || eleccion >= canciones.length) {
                    System.out.println("Selección inválida.");
                    continue;
                }

                // ── 3. Descargar con Go-Back-N ────────────────────────────────────
                Paquete pedir = new Paquete(
                    Paquete.PEDIR,
                    eleccion,
                    0,
                    new byte[0]
                );
                enviar(socket, pedir, addr, PUERTO);

                System.out.println(
                    "Descargando '" + canciones[eleccion] + "'..."
                );

                Map<Integer, byte[]> recibidos = new HashMap<>();
                int esperado = 0;
                int total = -1;

                while (true) {
                    try {
                        byte[] rbuf = new byte[65535];
                        DatagramPacket rdp = new DatagramPacket(
                            rbuf,
                            rbuf.length
                        );
                        socket.receive(rdp);
                        Paquete pkt = Paquete.desdBytes(
                            rdp.getData(),
                            rdp.getLength()
                        );

                        if (pkt.tipo == Paquete.FIN) {
                            total = pkt.numSec;
                            System.out.println(
                                "\nFIN recibido. Total paquetes: " + total
                            );
                            break;
                        }

                        if (pkt.tipo == Paquete.DATOS) {
                            System.out.print(
                                "\r  Recibido seq=" +
                                    pkt.numSec +
                                    "/" +
                                    (pkt.total - 1)
                            );

                            if (pkt.numSec == esperado) {
                                recibidos.put(pkt.numSec, pkt.datos);
                                Paquete ack = new Paquete(
                                    Paquete.ACK,
                                    esperado,
                                    0,
                                    new byte[0]
                                );
                                enviar(socket, ack, addr, PUERTO);
                                esperado++;
                            } else {
                                System.out.print(
                                    " [fuera de orden, descartado]"
                                );
                            }
                        }
                    } catch (SocketTimeoutException e) {
                        System.out.println("\nTimeout esperando datos.");
                        break;
                    }
                }

                // ── 4. Ensamblar archivo ──────────────────────────────────────────
                if (total < 0 || recibidos.size() < total) {
                    System.out.println(
                        "Descarga incompleta. No se pudo guardar el archivo."
                    );
                    continue;
                }

                new File("downloads").mkdirs();
                File archivo = new File("downloads/" + canciones[eleccion]);
                try (FileOutputStream fos = new FileOutputStream(archivo)) {
                    for (int i = 0; i < total; i++) fos.write(recibidos.get(i));
                }
                System.out.println(
                    "Guardado exitosamente en: " + archivo.getAbsolutePath()
                );
            } else if (opcion.equals("2")) {
                // ── 5. Menú de Reproducción Local ─────────────────────────────────
                File dir = new File("downloads");
                if (!dir.exists() || dir.list() == null) {
                    System.out.println(
                        "No se encontró la carpeta de descargas o está vacía."
                    );
                    continue;
                }

                String[] locales = dir.list((d, n) -> n.endsWith(".mp3"));
                if (locales == null || locales.length == 0) {
                    System.out.println(
                        "No hay archivos .mp3 disponibles para reproducir."
                    );
                    continue;
                }

                System.out.println("\n=== Canciones Descargadas ===");
                for (int i = 0; i < locales.length; i++) {
                    System.out.println(i + ". " + locales[i]);
                }

                // --- AQUÍ ES DONDE ENTRA EL CAMBIO DEL -1 ---
                System.out.print(
                    "\nNúmero de canción a reproducir (-1 para regresar): "
                );
                try {
                    int index = Integer.parseInt(sc.nextLine().trim());

                    if (index == -1) {
                        System.out.println("Regresando al menú principal...");
                        continue; // Aborta la reproducción y vuelve al inicio del while
                    }

                    if (index >= 0 && index < locales.length) {
                        File track = new File(dir, locales[index]);
                        reproducir(track);
                    } else {
                        System.out.println("Índice fuera de rango.");
                    }
                } catch (NumberFormatException e) {
                    System.out.println(
                        "Entrada inválida. Debe introducir un número entero."
                    );
                }
                // --------------------------------------------
            } else if (opcion.equals("3")) {
                System.out.println(
                    "Cerrando sockets y saliendo de la aplicación."
                );
                break;
            } else {
                System.out.println("Opción desconocida.");
            }
        }

        socket.close(); // Se ejecuta únicamente al romper el bucle con la opción 3
    }

    static void reproducir(File archivo) {
        System.out.println("Iniciando decodificación MPEG con JLayer...");
        try (FileInputStream fis = new FileInputStream(archivo)) {
            Player player = new Player(fis);
            System.out.println("Reproduciendo: " + archivo.getName());
            player.play(); // Llamada bloqueante
        } catch (FileNotFoundException e) {
            System.err.println(
                "Error: No se encontró el archivo ensamblado. " + e.getMessage()
            );
        } catch (JavaLayerException | IOException e) {
            System.err.println(
                "Excepción durante la reproducción del bitstream: " +
                    e.getMessage()
            );
        }
    }

    static void limpiarPantalla() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    static void enviar(
        DatagramSocket s,
        Paquete p,
        InetAddress addr,
        int puerto
    ) throws Exception {
        byte[] raw = p.aBytes();
        s.send(new DatagramPacket(raw, raw.length, addr, puerto));
    }
}
