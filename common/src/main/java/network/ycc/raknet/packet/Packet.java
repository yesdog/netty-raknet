package network.ycc.raknet.packet;

public interface Packet {

    default int sizeHint() {
        return 128;
    }

    interface ClientIdConnection extends Packet {
        long getClientId();
    }

}
