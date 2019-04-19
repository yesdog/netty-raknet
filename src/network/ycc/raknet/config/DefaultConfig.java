package network.ycc.raknet.config;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultChannelConfig;

import network.ycc.raknet.RakNet;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class DefaultConfig extends DefaultChannelConfig implements RakNet.Config {

    private static final Random rnd = new Random();

    //TODO: add rest of ChannelOptions
    protected volatile long serverId = rnd.nextLong();
    protected volatile long clientId = rnd.nextLong();
    protected volatile RakNet.MetricsLogger metrics = RakNet.MetricsLogger.DEFAULT;
    protected volatile int userDataId = -1;
    protected volatile int mtu = 1500;
    protected volatile long retryDelay = TimeUnit.NANOSECONDS.convert(50, TimeUnit.MILLISECONDS);
    protected volatile int maxPendingFrameSets = 1024;
    protected volatile int defaultPendingFrameSets = 64;

    protected final DescriptiveStatistics stats = new DescriptiveStatistics(32);

    public DefaultConfig(Channel channel) {
        super(channel);
        setRTT(TimeUnit.NANOSECONDS.convert(400, TimeUnit.MILLISECONDS));
    }

    @Override
    @SuppressWarnings("deprecation")
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(
                super.getOptions(),
                RakNet.SERVER_ID, RakNet.CLIENT_ID, RakNet.METRICS,
                RakNet.USER_DATA_ID, RakNet.MTU, RakNet.RTT);
    }

    @Override
    @SuppressWarnings("deprecation")
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        if (option == RakNet.SERVER_ID) {
            serverId = (Long) value;
        } else if (option == RakNet.CLIENT_ID) {
            clientId = (Long) value;
        } else if (option == RakNet.METRICS) {
            metrics = (RakNet.MetricsLogger) value;
        } else if (option == RakNet.USER_DATA_ID) {
            userDataId = (Integer) value;
        } else if (option == RakNet.MTU) {
            mtu = (Integer) value;
        } else if (option == RakNet.RTT) {
            setRTT((Long) value);
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
        } else if (option == RakNet.CLIENT_ID) {
            return (T) (Long) clientId;
        } else if (option == RakNet.METRICS) {
            return (T) metrics;
        } else if (option == RakNet.USER_DATA_ID) {
            return (T) (Integer) userDataId;
        } else if (option == RakNet.MTU) {
            return (T) (Integer) mtu;
        } else if (option == RakNet.RTT) {
            return (T) (Long) getRTT();
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

    public long getClientId() {
        return clientId;
    }

    public void setClientId(long clientId) {
        this.clientId = clientId;
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

    public long getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(long retryDelay) {
        this.retryDelay = retryDelay;
    }

    public long getRTT() {
        return (long) stats.getMean(); //ns
    }

    public long getRTTStdDev() {
        return (long) stats.getStandardDeviation(); //ns
    }

    public void setRTT(long rtt) {
        stats.clear();
        stats.addValue(rtt);
    }

    public void updateRTT(long rttSample) {
        stats.addValue(rttSample);
        metrics.measureRTTns(getRTT());
        metrics.measureRTTnsStdDev(getRTTStdDev());
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
