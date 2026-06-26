import java.io.*;
import java.net.*;
import java.util.*;
import javazoom.jl.player.Player;

public class Cliente {

    static final String SERVIDOR = "localhost";
    static final int PUERTO = 9876;
    static final int TIMEOUT = 2000;

    public static void main(String[] args) throws Exception {
        DatagramSocket socket = new DatagramSocket();
        InetAddress addr = InetAddress.getByName(SERVIDOR);
        socket.setSoTimeout(TIMEOUT);
        Scanner sc = new Scanner(System.in);

        //1-> Fase de catalogo
        Paquete req = new Paquete(Paquete.CATALOGO, 0, 0, new byte[0]);
        enviar(socket, req, addr, PUERTO);

        byte[] buf = new byte[65535];
        DatagramPacket dp = new DatagramPacket(buf, buf.length);
        socket.receive(dp);
        Paquete catalogo = Paquete.desdBytes(dp.getData(), dp.getLength());

        String[] canciones = new String(catalogo.datos).split("\n");
        System.out.println("=== Catálogo del Servidor ===");
        for (int i = 0; i < canciones.length; i++) {
            System.out.println(i + ". " + canciones[i]);
        }

        //Elige la canción
        System.out.print("\nNúmero de canción a reproducir: ");
        int eleccion = Integer.parseInt(sc.nextLine().trim());

        if (eleccion < 0 || eleccion >= canciones.length) {
            System.out.println("Selección inválida.");
            socket.close();
            return;
        }

        //Envia paquete de pedir
        Paquete pedir = new Paquete(Paquete.PEDIR, eleccion, 0, new byte[0]);
        enviar(socket, pedir, addr, PUERTO);
        System.out.println(
            "Descargando y reproduciendo '" + canciones[eleccion] + "'..."
        );

        //2-> Fase de PIPE | Se conecta el hilo de reproducción con el hilo de descarga
        PipedOutputStream pipOut = new PipedOutputStream();
        PipedInputStream pipIn = new PipedInputStream(pipOut);

        Thread hiloReproduccion = new Thread(() -> reproducir(pipIn)); //Hilo de Reproducción iniciato, antes rep ocurria al final
        hiloReproduccion.start();

        //3-> Fase de descarga, aqui ya no hay un hashmap para guardar los fragmentos, se envian al pipe
        int esperado = 0;

        while (true) {
            try {
                byte[] rbuf = new byte[65535];
                DatagramPacket rdp = new DatagramPacket(rbuf, rbuf.length);
                socket.receive(rdp);
                Paquete pkt = Paquete.desdBytes(rdp.getData(), rdp.getLength());

                if (pkt.tipo == Paquete.FIN) {
                    System.out.println(
                        "\nFIN recibido. Total paquetes: " + pkt.numSec
                    );
                    break;
                }

                if (pkt.tipo == Paquete.DATOS) {
                    System.out.print(
                        "\r  Recibido seq=" + pkt.numSec + "/" + (pkt.total - 1)
                    );

                    if (pkt.numSec == esperado) {
                        //Si es el paquete esperado entonces escribe en el pipe para que el hilo de reproducción consuma
                        pipOut.write(pkt.datos);

                        Paquete ack = new Paquete(
                            Paquete.ACK,
                            esperado,
                            0,
                            new byte[0]
                        );
                        enviar(socket, ack, addr, PUERTO);
                        esperado++;
                    } else {
                        System.out.print(" [fuera de orden, descartado]");
                    }
                }
            } catch (SocketTimeoutException e) {
                System.out.println("\nTimeout esperando datos.");
                break;
            }
        }
        //Se cierra el pipe, ya no hay paquetes para consumir, aqui ya no rearma la canción como antes
        pipOut.close();
        hiloReproduccion.join(); // esperar a que JLayer termine de decodificar

        socket.close();
        System.out.println("Reproducción finalizada.");
    }

    static void reproducir(InputStream stream) {
        //Ahora recibe lo que sale del pipe
        try {
            Player player = new Player(stream);
            player.play();
        } catch (Exception e) {
            System.err.println(
                "Error durante la reproducción: " + e.getMessage()
            );
        }
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
