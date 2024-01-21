package io.bhex.base.common.util;

import com.google.common.base.Throwables;
import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import io.prometheus.client.Summary;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Response;

import java.io.IOException;

@Slf4j
public class OkHttpPrometheusInterceptor implements Interceptor {

    public static double[] SERVICE_TIME_BUCKETS = new double[]{
            .1, 0.25, .5, .75,
            1.0, 2.5, 5.0, 7.5,
            10.0, 25.0, 50, 75,
            100, 200, 500, 1000, 2000, 10000
    };

    public static final Counter STARTED_COUNTER = Counter.build()
            .namespace("broker")
            .subsystem("okhttp_post")
            .name("request_total")
            .labelNames("addr", "path")
            .help("Total number of okhttp request started")
            .register();

    public static final Summary COMPLETED_SUMMARY = Summary.build()
            .namespace("broker")
            .subsystem("okhttp_post")
            .name("summary_latency_milliseconds")
            .labelNames("addr", "path", "status", "exception")
            .help("Summary of okhttp response latency (in milliseconds) for completed call.")
            .register();

    public static final Histogram HISTOGRAM_LATENCY_MILLISECONDS = Histogram.build()
            .namespace("broker")
            .subsystem("okhttp_post")
            .name("histogram_latency_milliseconds")
            .labelNames("addr", "path", "status")
            .help("Histogram of okhttp response latency (in milliseconds) for completed invoke.")
            .buckets(SERVICE_TIME_BUCKETS)
            .register();

    private OkHttpPrometheusInterceptor() {
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        final String addr = chain.request().url().host();
        final String path = chain.request().url().encodedPath();
        final String method = chain.request().method();
        STARTED_COUNTER.labels(addr, path).inc();
        final long beginTime = System.currentTimeMillis();
        Throwable throwable = null;
        Integer status = null;
        try {
            Response response = chain.proceed(chain.request());
            status = response == null ? null : response.code();
            return response;
        } catch (Throwable th) {
            throwable = th;
            Throwables.throwIfUnchecked(th);
            Throwables.throwIfInstanceOf(th, IOException.class);
            throw th;
        } finally {
            long now = System.currentTimeMillis();
            COMPLETED_SUMMARY.labels(addr, path,
                    status == null ? "none" : String.valueOf(status),
                    throwable == null ? "none" : throwable.getClass().getName())
                    .observe((now - beginTime) / 1.000D);
            this.HISTOGRAM_LATENCY_MILLISECONDS.labels(addr, path, status == null ? "none" : String.valueOf(status))
                    .observe((now - beginTime) / 1.000D);
            log.info("{}{}{} time:{}ms", chain.request().isHttps() ? "https://" : "http://" ,addr, path, now - beginTime);
        }
    }

    private static final OkHttpPrometheusInterceptor INSTANCE = new OkHttpPrometheusInterceptor();

    public static OkHttpPrometheusInterceptor getInstance() {
        return INSTANCE;
    }

}
