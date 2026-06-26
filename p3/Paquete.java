import java.nio.ByteBuffer;

public class Paquete {

    // Tipos
    public static final byte CATALOGO   = 0;
    public static final byte PEDIR      = 1;
    public static final byte DATOS      = 2;
    public static final byte ACK        = 3;
    public static final byte FIN        = 4;

    public byte   tipo;
    public int    numSec;
    public int    total;
    public byte[] datos;

    public Paquete(byte tipo, int numSec, int total, byte[] datos) {
        this.tipo   = tipo;
        this.numSec = numSec;
        this.total  = total;
        this.datos  = datos == null ? new byte[0] : datos;
    }

    // Serializar a bytes para enviarlo por UDP
    public byte[] aBytes() {
        // tipo(1) + numSec(4) + total(4) + len(4) + datos
        ByteBuffer buf = ByteBuffer.allocate(13 + datos.length);
        buf.put(tipo);
        buf.putInt(numSec);
        buf.putInt(total);
        buf.putInt(datos.length);
        buf.put(datos);
        return buf.array();
    }

    // Deserializar bytes recibidos
    public static Paquete desdBytes(byte[] raw, int len) {
        ByteBuffer buf = ByteBuffer.wrap(raw, 0, len);
        byte  tipo   = buf.get();
        int   numSec = buf.getInt();
        int   total  = buf.getInt();
        int   dLen   = buf.getInt();
        byte[] datos = new byte[dLen];
        buf.get(datos);
        return new Paquete(tipo, numSec, total, datos);
    }
}
