package network.ycc.raknet.packet;

public interface Packet {

    default int sizeHint() {
        return 128;
    }

}
