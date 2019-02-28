package raknetserver.channel;

import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultChannelConfig;
import raknetserver.RakNetServer;

import java.util.Map;

public class RakNetChannelConfig extends DefaultChannelConfig {

    protected volatile RakNetServer.MetricsLogger metrics = RakNetServer.MetricsLogger.DEFAULT;
    protected volatile long serverId = 123456789L;
    protected volatile int userDataId = -1;

    protected RakNetChannelConfig(Channel channel) {
        super(channel);
    }

    @Override
    @SuppressWarnings("deprecation")
    public Map<ChannelOption<?>, Object> getOptions() {
        return getOptions(
                super.getOptions(),
                RakNetServer.SERVER_ID, RakNetServer.METRICS, RakNetServer.USER_DATA_ID);
    }

    @Override
    @SuppressWarnings("deprecation")
    public <T> boolean setOption(ChannelOption<T> option, T value) {
        if (option == RakNetServer.SERVER_ID) {
            serverId = (Long) value;
        } else if (option == RakNetServer.METRICS) {
            metrics = (RakNetServer.MetricsLogger) value;
        } else if (option == RakNetServer.USER_DATA_ID) {
            userDataId = (Integer) value;
        } else {
            return super.setOption(option, value);
        }
        return true;
    }

    @Override
    @SuppressWarnings({ "unchecked", "deprecation" })
    public <T> T getOption(ChannelOption<T> option) {
        if (option == RakNetServer.SERVER_ID) {
            return (T) (Long) serverId;
        } else if (option == RakNetServer.METRICS) {
            return (T) metrics;
        } else if (option == RakNetServer.USER_DATA_ID) {
            return (T) (Integer) userDataId;
        }
        return super.getOption(option);
    }

    public RakNetServer.MetricsLogger getMetrics() {
        return metrics;
    }

    public void setMetrics(RakNetServer.MetricsLogger metrics) {
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
}
