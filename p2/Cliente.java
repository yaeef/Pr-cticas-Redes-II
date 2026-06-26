import java.io.*;
import java.net.*;
import java.util.*;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;

public class Cliente {

    static final String SERVIDOR = "localhost";
    static final int PUERTO = 9876;
    static final int TIMEOUT = 2000;

    static DatagramSocket socket;
    static InetAddress addr;

    public static void main(String[] args) throws Exception {
        socket = new DatagramSocket();
        addr = InetAddress.getByName(SERVIDOR);
        socket.setSoTimeout(TIMEOUT);

        Scanner sc = new Scanner(System.in);

        //1-> Fase de catalogo
        enviar(new Paquete(Paquete.CATALOGO, 0, 0, new byte[0]));
        String[] canciones = new String(recibir().datos).split("\n");

        System.out.println("=== Catálogo ===");
        for (int i = 0; i < canciones.length; i++) System.out.println(
            i + ". " + canciones[i]
        );

        System.out.print("Canción: ");
        int idx = Integer.parseInt(sc.nextLine().trim());

        //2-> Fase de descarga
        enviar(new Paquete(Paquete.PEDIR, idx, 0, new byte[0]));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int esperado = 0,
            total = -1;

        while (true) {
            try {
                Paquete pkt = recibir();
                if (pkt.tipo == Paquete.FIN) {
                    total = pkt.numSec;
                    break;
                }
                if (pkt.tipo == Paquete.DATOS && pkt.numSec == esperado) {
                    baos.write(pkt.datos);
                    enviar(
                        new Paquete(Paquete.ACK, esperado++, 0, new byte[0])
                    );
                    System.out.printf("\r  %d/%d", esperado, pkt.total);
                }
            } catch (SocketTimeoutException e) {
                System.out.println("\nTimeout.");
                break;
            }
        }

        //3-> Fase de rearmado
        new File("downloads").mkdirs();
        File archivo = new File("downloads/" + canciones[idx]);
        try (var fos = new FileOutputStream(archivo)) {
            fos.write(baos.toByteArray());
        }
        System.out.println("\nGuardado: " + archivo.getName());

        //4-> Fase de reproducción
        System.out.println("Reproduciendo...");
        try (var fis = new FileInputStream(archivo)) {
            new Player(fis).play();
        } catch (IOException | JavaLayerException e) {
            System.err.println("Error: " + e.getMessage());
        }

        socket.close();
    }

    static void enviar(Paquete p) throws Exception {
        byte[] raw = p.aBytes();
        socket.send(new DatagramPacket(raw, raw.length, addr, PUERTO));
    }

    static Paquete recibir() throws Exception {
        byte[] buf = new byte[65535];
        DatagramPacket dp = new DatagramPacket(buf, buf.length);
        socket.receive(dp);
        return Paquete.desdBytes(dp.getData(), dp.getLength());
    }
}
