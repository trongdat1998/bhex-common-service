package io.bhex.base.common.util;

import io.bhex.base.env.BhexEnv;
import io.prometheus.client.Counter;
import lombok.extern.slf4j.Slf4j;

/**
 * @Description:
 * @Date: 2019/2/3 上午10:19
 * @Author: liwei
 * @Copyright（C）: 2018 BlueHelix Inc. All rights reserved.
 */
public class PrometheusUtil {

    private static final BhexEnv bhexEnv = new BhexEnv();

    private static final Counter emailCounter = Counter.build()
            .namespace(bhexEnv.getKubeNameSpace() == null ? "bluehelix" : bhexEnv.getKubeNameSpace())
            .subsystem("controller")
            .name("email_data")
            .labelNames("channel","event", "status")
            .help("Total number of email data")
            .register();

    private static final Counter smsCounter = Counter.build()
            .namespace(bhexEnv.getKubeNameSpace() == null ? "bluehelix" : bhexEnv.getKubeNameSpace())
            .subsystem("controller")
            .name("sms_data")
            .labelNames("channel","event", "status")
            .help("Total number of sms data")
            .register();

    public static void emailSendCounter(String channel, int status){
        emailCounter.labels(channel, "request", status + "").inc();
    }

    public static void emailDeliveryCounter(String channel, int status){
        emailCounter.labels(channel, "deliver", status + "").inc();
    }

    public static void smsSendCounter(String channel, int status){
        smsCounter.labels(channel, "request", status + "").inc();
    }

    public static void smsDeliveryCounter(String channel, int status){
        smsCounter.labels(channel, "deliver", status + "").inc();
    }
}
