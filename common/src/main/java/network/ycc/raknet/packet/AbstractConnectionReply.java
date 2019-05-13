package network.ycc.raknet.packet;

import network.ycc.raknet.RakNet;

public abstract class AbstractConnectionReply extends SimplePacket {

    protected static final boolean NEEDS_SECURITY = false;

    protected RakNet.Magic magic;
    protected int mtu;
    protected long serverId;

    public AbstractConnectionReply() {}

    public AbstractConnectionReply(RakNet.Magic magic, int mtu, long serverId) {
        this.magic = magic;
        this.mtu = mtu;
        this.serverId = serverId;
    }

    public RakNet.Magic getMagic() {
        return magic;
    }

    public void setMagic(RakNet.Magic magic) {
        this.magic = magic;
    }

    public int getMtu() {
        return mtu;
    }

    public void setMtu(int mtu) {
        this.mtu = mtu;
    }

    public long getServerId() {
        return serverId;
    }

    public void setServerId(long serverId) {
        this.serverId = serverId;
    }

}
