package raknetserver.utils;

import raknetserver.RakNetServer;

public final class DefaultMetrics implements RakNetServer.Metrics {

    @Override
    public void incrOutPacket(int n) {}

    @Override
    public void incrInPacket(int n) {}

    @Override
    public void incrJoin(int n) {}

    @Override
    public void incrSend(int n) {}

    @Override
    public void incrResend(int n) {}

    @Override
    public void incrAckSend(int n) {}

    @Override
    public void incrNackSend(int n) {}

    @Override
    public void incrAckRecv(int n) {}

    @Override
    public void incrNackRecv(int n) {}

}
