package raknet.config;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultChannelConfig;

import raknet.RakNet;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DefaultConfig extends DefaultChannelConfig implements RakNet.Config {

    //TODO: add rest of ChannelOptions
    protected volatile long serverId = 1L;
    protected volatile RakNet.MetricsLogger metrics = RakNet.MetricsLogger.DEFAULT;
    protected volatile int userDataId = -1;
    protected volatile int mtu = 1500;
    protected volatile long rtt = TimeUnit.NANOSECONDS.convert(400, TimeUnit.MILLISECONDS);
    protected volatile int rttWeight = 8;
    protected volatile int maxPendingFrameSets = DEFAULT_MAX_PENDING_FRAME_SETS;
    protected volatile int defaultPendingFrameSets = DEFAULT_DEFAULT_PENDING_FRAME_SETS;

    public DefaultConfig(Channel channel) {
        super(channel);
    }

    @Override
    @SuppressWarnings("deprecation")
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(
                super.getOptions(),
                RakNet.SERVER_ID, RakNet.METRICS, RakNet.USER_DATA_ID, RakNet.MTU, RakNet.RTT);
    }

    @Override
    @SuppressWarnings("deprecation")
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        if (option == RakNet.SERVER_ID) {
            serverId = (Long) value;
        } else if (option == RakNet.METRICS) {
            metrics = (RakNet.MetricsLogger) value;
        } else if (option == RakNet.USER_DATA_ID) {
            userDataId = (Integer) value;
        } else if (option == RakNet.MTU) {
            mtu = (Integer) value;
        } else if (option == RakNet.RTT) {
            rtt = (Long) value;
        } else {
            return super.setOption(option, value);
        }
        return true;
    }

    @Override
    @SuppressWarnings({ "unchecked", "deprecation" })
    public <T> T getOption(ChannelOption<T> option) {
        if (option == RakNet.SERVER_ID) {
            return (T) (Long) serverId;
        } else if (option == RakNet.METRICS) {
            return (T) metrics;
        } else if (option == RakNet.USER_DATA_ID) {
            return (T) (Integer) userDataId;
        } else if (option == RakNet.MTU) {
            return (T) (Integer) mtu;
        } else if (option == RakNet.RTT) {
            return (T) (Long) rtt;
        }
        return super.getOption(option);
    }

    public RakNet.MetricsLogger getMetrics() {
        return metrics;
    }

    public void setMetrics(RakNet.MetricsLogger metrics) {
        this.metrics = metrics;
    }

    public long getServerId() {
        return serverId;
    }

    public void setServerId(long serverId) {
        this.serverId = serverId;
    }

    public int getUserDataId() {
        return userDataId;
    }

    public void setUserDataId(int userDataId) {
        this.userDataId = userDataId;
    }

    public int getMTU() {
        return mtu;
    }

    public void setMTU(int mtu) {
        this.mtu = mtu;
    }

    public long getRTT() {
        return rtt;
    }

    public void setRTT(long rtt) {
        this.rtt = rtt;
    }

    public void updateRTT(long rttSample) {
        rtt = (rtt * (rttWeight - 1) + rttSample) / rttWeight;
        metrics.measureRTTns(rtt);
    }

    public int getMaxPendingFrameSets() {
        return maxPendingFrameSets;
    }

    public void setMaxPendingFrameSets(int maxPendingFrameSets) {
        this.maxPendingFrameSets = maxPendingFrameSets;
    }

    public int getDefaultPendingFrameSets() {
        return defaultPendingFrameSets;
    }

    public void setDefaultPendingFrameSets(int defaultPendingFrameSets) {
        this.defaultPendingFrameSets = defaultPendingFrameSets;
    }

}
