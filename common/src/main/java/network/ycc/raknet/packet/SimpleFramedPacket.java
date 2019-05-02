package network.ycc.raknet.packet;

public abstract class SimpleFramedPacket extends SimplePacket implements FramedPacket {

    protected FramedPacket.Reliability reliability = FramedPacket.Reliability.RELIABLE_ORDERED;
    protected int orderChannel = 0;

    public Reliability getReliability() {
        return reliability;
    }

    public void setReliability(FramedPacket.Reliability reliability) {
        this.reliability = reliability;
    }

    public int getOrderChannel() {
        return orderChannel;
    }

    public void setOrderChannel(int orderChannel) {
        this.orderChannel = orderChannel;
    }

}
