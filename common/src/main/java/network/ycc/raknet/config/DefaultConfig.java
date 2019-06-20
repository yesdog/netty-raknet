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

    public static final RakNet.Magic DEFAULT_MAGIC = new DefaultMagic(new byte[] {
            (byte) 0x00, (byte) 0xff, (byte) 0xff, (byte) 0x00, (byte) 0xfe, (byte) 0xfe,
            (byte) 0xfe, (byte) 0xfe, (byte) 0xfd, (byte) 0xfd, (byte) 0xfd, (byte) 0xfd,
            (byte) 0x12, (byte) 0x34, (byte) 0x56, (byte) 0x78 });

    private static final RakNet.MetricsLogger DEFAULT_METRICS = new RakNet.MetricsLogger() {};
    private static final Random rnd = new Random();

    //TODO: add rest of ChannelOptions
    private volatile long serverId = rnd.nextLong();
    private volatile long clientId = rnd.nextLong();
    private volatile RakNet.MetricsLogger metrics = DEFAULT_METRICS;
    private volatile int mtu = 4096;
    private volatile long retryDelayNanos = TimeUnit.NANOSECONDS.convert(100, TimeUnit.MILLISECONDS);
    private volatile int maxPendingFrameSets = 1024;
    private volatile int defaultPendingFrameSets = 64;
    private volatile int maxQueuedBytes = 3 * 1024 * 1024;
    private volatile RakNet.Magic magic = DEFAULT_MAGIC;
    private volatile RakNet.Codec codec = DefaultCodec.INSTANCE;
    private volatile int protocolVersion = 9;

    protected final DescriptiveStatistics rttStats = new DescriptiveStatistics(32);

    public DefaultConfig(Channel channel) {
        super(channel);
        setRTTNanos(TimeUnit.NANOSECONDS.convert(400, TimeUnit.MILLISECONDS));
    }

    @Override
    @SuppressWarnings("deprecation")
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(
                super.getOptions(),
                RakNet.SERVER_ID, RakNet.CLIENT_ID, RakNet.METRICS, RakNet.MTU,
                RakNet.RTT, RakNet.PROTOCOL_VERSION, RakNet.MAGIC, RakNet.RETRY_DELAY_NANOS);
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
        } else if (option == RakNet.MTU) {
            mtu = (Integer) value;
        } else if (option == RakNet.RTT) {
            setRTTNanos((Long) value);
        } else if (option == RakNet.PROTOCOL_VERSION) {
            protocolVersion = (Integer) value;
        } else if (option == RakNet.MAGIC) {
            magic = (RakNet.Magic) value;
        } else if (option == RakNet.RETRY_DELAY_NANOS) {
            retryDelayNanos = (Long) value;
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
        } else if (option == RakNet.MTU) {
            return (T) (Integer) mtu;
        } else if (option == RakNet.RTT) {
            return (T) (Long) getRTTNanos();
        } else if (option == RakNet.PROTOCOL_VERSION) {
            return (T) (Integer) protocolVersion;
        } else if (option == RakNet.MAGIC) {
            return (T) magic;
        } else if (option == RakNet.RETRY_DELAY_NANOS) {
            return (T) (Long) retryDelayNanos;
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

    public int getMTU() {
        return mtu;
    }

    public void setMTU(int mtu) {
        this.mtu = mtu;
    }

    public long getRetryDelayNanos() {
        return retryDelayNanos;
    }

    public void setRetryDelayNanos(long retryDelayNanos) {
        this.retryDelayNanos = retryDelayNanos;
    }

    public long getRTTNanos() {
        final long rtt = (long) rttStats.getMean();
        return Math.max(rtt, 1);
    }

    public long getRTTStdDevNanos() {
        return (long) rttStats.getStandardDeviation(); //ns
    }

    public void setRTTNanos(long rtt) {
        rttStats.clear();
        rttStats.addValue(rtt);
    }

    public void updateRTTNanos(long rttSample) {
        rttStats.addValue(rttSample);
        metrics.measureRTTns(getRTTNanos());
        metrics.measureRTTnsStdDev(getRTTStdDevNanos());
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

    public int getMaxQueuedBytes() {
        return maxQueuedBytes;
    }

    public void setMaxQueuedBytes(int maxQueuedBytes) {
        this.maxQueuedBytes = maxQueuedBytes;
    }

    public RakNet.Magic getMagic() {
        return magic;
    }

    public void setMagic(RakNet.Magic magic) {
        this.magic = magic;
    }

    public RakNet.Codec getCodec() {
        return codec;
    }

    public void setCodec(RakNet.Codec codec) {
        this.codec = codec;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

}
