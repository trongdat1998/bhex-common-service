package io.bhex.base.common.service.impl;

import com.amazonaws.regions.Regions;
import io.bhex.base.common.config.AwsSmsProperties;
import io.bhex.base.common.config.ContainerConfig;
import io.bhex.base.common.mapper.SmsDeliveryRecordMapper;
import io.bhex.base.common.util.MaskUtil;
import io.bhex.base.common.util.awss3.AwsObjectStorage;
import io.bhex.base.common.util.awss3.ObjectMetadata;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

/**
 * @Description:
 * @Date: 2018/12/6 下午4:46
 * @Author: liwei
 * @Copyright（C）: 2018 BlueHelix Inc. All rights reserved.
 */
@Slf4j
@Service
public class AwsSmsReportService {

    @Autowired
    private SmsDeliveryRecordMapper smsDeliveryRecordMapper;
    @Autowired
    private AwsSmsProperties awsSmsProperties;

    private static int totalSmsItems = 0;
    private static int successSmsItems = 0;

    //@Scheduled(initialDelay = 600_000, fixedRate=21600_000)
    private void loadAwsReports(){
        String container = ContainerConfig.getContainer();
        log.info("start load aws yesterday report");
        totalSmsItems = 0;
        successSmsItems = 0;
        String clientRegion = "ap-northeast-1";
        String bucketName = "BHEX".equals(container.toUpperCase()) ? "sms.account.bhex.com" : "bhop-sms-daily-usage";
        Regions[] regions = new Regions[]{
                Regions.US_EAST_1,
                Regions.US_WEST_2,
                Regions.AP_NORTHEAST_1,
                Regions.AP_SOUTHEAST_1,
                Regions.AP_SOUTHEAST_2,
                Regions.EU_WEST_1
        };
        Date date = DateTime.now().plusDays(-1).toDate();
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");


        String dateStr = dateFormat.format(date);
        List<String> list = Arrays.stream(regions)
                .map(region ->  "SMSUsageReports/" + region.getName() + "/" + dateStr)
                .collect(Collectors.toList());


        List<AwsSmsProperties.SmsConfig> configs = awsSmsProperties.getConfigs(container);
        for(AwsSmsProperties.SmsConfig config : configs) {
            AwsObjectStorage storage = new AwsObjectStorage(list, bucketName,
                    config.getAccessKeyId(), config.getSecretKey(),clientRegion);
            for(String prefixUrl : list){
                try{
                    Iterator<ObjectMetadata> iterator = storage.listObjectMetadata(prefixUrl);
                    while (iterator.hasNext()){
                        ObjectMetadata metadata = iterator.next();
                        deal(storage, metadata.key());
                    }
                }
                catch (Exception e){
                    log.warn("request:{} error", prefixUrl, e);
                }
            }
            storage.shutdown();
        }


        log.info("total:{} success:{} successRatio:{}", totalSmsItems, successSmsItems,
                totalSmsItems > 0 ? (successSmsItems*100/totalSmsItems+"%") : "Nan");
    }




    //
//    PublishTimeUTC,MessageId,DestinationPhoneNumber,MessageType,DeliveryStatus,PriceInUSD,PartNumber,TotalParts
//2018-12-05T00:35:58.017Z,783454f3-77d9-57e3-a99a-00726877a4f5,+8618591727856,Transactional,Message has been accepted by phone carrier,0.01531,1,1
    public void deal(AwsObjectStorage storage, String key) {
        log.info("load report:{}", key);
        byte[] bytes = storage.downloadObject(key);
        GZIPInputStream inputStream = null;
        BufferedReader br = null;
        try {
            inputStream = new GZIPInputStream(new ByteArrayInputStream(bytes));
            int count = 0;
            String line;
            br = new BufferedReader(new InputStreamReader(inputStream));
            while ((line = br.readLine()) != null) {
                count++;
                if (count == 1) {
                    continue;
                }
                totalSmsItems++;
                String[] arr = line.split(",");
                String messageId = arr[1];
                String deliveryStatus = arr[4].startsWith("Message has been accepted by phone") ? "SUCCESS" : arr[4];
                String price = arr[5];
                log.info("{} {} {} {}", MaskUtil.maskMobile(arr[2]), messageId, deliveryStatus, price);
                if(deliveryStatus.equals("SUCCESS")){
                    successSmsItems++;
                }

                smsDeliveryRecordMapper.updateAwsInfo(messageId, deliveryStatus, new BigDecimal(price));
            }
        } catch (IOException e) {
            log.warn("download aws sms report error", e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    log.warn("close inputstream error", e);
                }
            }
            if(br != null){
                try {
                    br.close();
                } catch (IOException e) {
                    log.warn("close BufferedReader error", e);
                }
            }
        }
    }
}
