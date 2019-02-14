package raknetserver.packet;

public abstract class SimpleFramedPacket extends SimplePacket implements FramedPacket {

    protected Reliability reliability = Reliability.RELIABLE_ORDERED;
    protected int orderChannel = 0;

    public Reliability getReliability() {
        return reliability;
    }

    public void setReliability(Reliability reliability) {
        this.reliability = reliability;
    }

    public int getOrderChannel() {
        return orderChannel;
    }

    public void setOrderChannel(int orderChannel) {
        this.orderChannel = orderChannel;
    }

}
