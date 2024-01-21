package io.bhex.base.common.service.impl;

import com.google.common.base.Strings;
import io.bhex.base.common.entity.EmailMxInfo;
import io.bhex.base.common.mapper.EmailMxInfoMapper;
import io.bhex.base.common.service.RouterConfigService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.net.InetAddress;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EmailMxInfoService {

    @Autowired
    private EmailMxInfoMapper emailMxInfoMapper;

    @Autowired
    private RouterConfigService routerConfigService;


    private static Map<String, List<EmailMxInfo>> mxInfoMap = new HashMap<>();

    @PostConstruct
    @Scheduled(initialDelay = 10_000, fixedRate = 120_000)
    private void loadMxInfos(){
        List<EmailMxInfo> mxInfoList = emailMxInfoMapper.selectAll();
        Set<String> blackIps = routerConfigService.getEmailMxipBlackList();
        for (String mxIp : blackIps) {
            boolean r = mxInfoList.stream().anyMatch(m -> m.getMxIp().equals(mxIp) && m.getStatus() == 1); //存在还可用的黑明单ip
            if (r) {
                int row = emailMxInfoMapper.disableMxIp(mxIp, new Timestamp(System.currentTimeMillis()));
                log.info("disable mxip:{} rows number:{}", mxIp, row);
                mxInfoList.forEach(m -> {
                    if (m.getMxIp().equals(mxIp)) {
                        m.setStatus(0);
                    }
                });
            }
        }
        mxInfoMap = mxInfoList.stream().collect(Collectors.groupingBy(EmailMxInfo::getDomain));
    }

    public boolean mxIpInBlackList(String email) {
        String domain = email.split("@")[1].toLowerCase();
        List<EmailMxInfo> list = mxInfoMap.get(domain);

        if (!CollectionUtils.isEmpty(list)) {
            boolean existed = list.stream().anyMatch(m -> m.getStatus() == 0);
            if (existed) {
                log.warn("email:{} in mxip blacklist", email);
            }
            return existed;
        }


        log.info("domain:{} not existed", domain);
        List<DTO> mxInfos = getEmailMxs(domain);
        for(DTO d : mxInfos) {
            Set<String> blackIps = routerConfigService.getEmailMxipBlackList();
            boolean inBlackList = blackIps.contains(d.getMxIp());
            EmailMxInfo emailMxInfo = new EmailMxInfo();
            emailMxInfo.setDomain(domain);
            emailMxInfo.setMxAddress(d.getMxAddress());
            emailMxInfo.setMxIp(d.getMxIp());
            emailMxInfo.setStatus(inBlackList ? 0 : 1);
            emailMxInfo.setCreated(new Timestamp(System.currentTimeMillis()));
            emailMxInfo.setUpdated(new Timestamp(System.currentTimeMillis()));
            emailMxInfoMapper.insertSelective(emailMxInfo);
            if (inBlackList) {
                loadMxInfos();
                log.warn("email:{} in mxip blacklist, new domain", email);
                return true;
            } else {
                log.info("domain:{} not in blacklist", domain);
            }
        }

        return false;
    }





    public static void main(String[] args) throws Exception{
        String[] domains = new String[] { "amyalysonfans.com"};
        for (String domain :domains) {
            List<DTO> list = new EmailMxInfoService().getEmailMxs(domain);
            for (DTO dto : list) {
                System.out.println("Insert into tb_email_mx_info values(null,'"+domain+"','"+dto.getMxIp()+"','"+dto.getMxAddress()+"',1,'2019-11-20 12:12:12','2019-11-20 12:12:12');");
            }

        }

    }



    private List<DTO> getEmailMxs(String domain) {
        List<DTO> emailInfos = new ArrayList<>();
        try {
            for (String mailHost: lookupMailHosts(domain)) {
                for(String ip : getIpList(domain, mailHost)) {
                    DTO dto = new DTO();
                    dto.setDomain(domain);
                    dto.setMxAddress(mailHost);
                    dto.setMxIp(!StringUtils.isEmpty(ip) ? ip : mailHost);
                    emailInfos.add(dto);
                }
            }
        } catch (NamingException e) {
            log.warn("ERROR: No DNS record for '" + domain + "'");
        }
        return emailInfos;
    }

    // returns a String array of mail exchange servers (mail hosts)
    //     sorted from most preferred to least preferred
    private String[] lookupMailHosts(String domainName) throws NamingException {
        InitialDirContext iDirC = new InitialDirContext();
        Attributes attributes = iDirC.getAttributes("dns:/" + domainName, new String[] {"MX"});
        Attribute attributeMX = attributes.get("MX");
        if (attributeMX == null) {
            log.warn("domain:{} no mx record", domainName);
            return (new String[] { domainName });
        }

        // split MX RRs into Preference Values(pvhn[0]) and Host Names(pvhn[1])
        String[][] pvhn = new String[attributeMX.size()][2];
        for (int i = 0; i < attributeMX.size(); i++) {
            pvhn[i] = ("" + attributeMX.get(i)).split("\\s+");
        }

        Arrays.sort(pvhn, (o1, o2) -> (Integer.parseInt(o1[0]) - Integer.parseInt(o2[0])));

        String[] sortedHostNames = new String[pvhn.length];
        for (int i = 0; i < pvhn.length; i++) {
            sortedHostNames[i] = pvhn[i][1].endsWith(".") ?
                    pvhn[i][1].substring(0, pvhn[i][1].length() - 1) : pvhn[i][1];
        }
        return sortedHostNames;
    }

    private static List<String> getIpList(String domain, String mxAddress) {

        List<String> ipList = new ArrayList<>();
        try {
            InetAddress inetAddress = InetAddress.getByName(mxAddress);

            // show the Internet Address as name/address
            //System.out.println(inetAddress.getHostName() + "/" + inetAddress.getHostAddress());
            // get the default initial Directory Context
            InitialDirContext iDirC = new InitialDirContext();
            // get the DNS records for inetAddress
            Attributes attributes = iDirC.getAttributes("dns:/" + inetAddress.getHostName());
            // get an enumeration of the attributes and print them out
            NamingEnumeration attributeEnumeration = attributes.getAll();
            //System.out.println("-- DNS INFORMATION --");
            while (attributeEnumeration.hasMore()) {
                String s =attributeEnumeration.next().toString();
                log.info("domain:{} mxAddress:{} ip:{}", domain, mxAddress, s);
                if (!s.startsWith("A:")) {
                    continue;
                }
                ipList.addAll(Arrays.asList(s.replaceAll(" ", "").split(":")[1].split(",")));
            }
            attributeEnumeration.close();

            if (CollectionUtils.isEmpty(ipList)) {
                ipList.add(Strings.nullToEmpty(inetAddress.getHostAddress()));
            }
        }
        catch (Exception exception) {
            log.warn("getIpList ERROR: No Internet Address for '" + mxAddress + "'", exception);
        }

        return ipList;
    }

    @Data
    private static class DTO {
        private String domain;
        private String mxAddress;
        private String mxIp;
    }


//    public List<DTO> getEmailMxs(String domain) {
//        List<DTO> items = new ArrayList<>();
//        String result = executeLinuxCmd("dig -t mx " + domain + " +short");
//        log.info("dig mx :{}", result);
//        if (StringUtils.isEmpty(result)) {
//            return new ArrayList<>();
//        }
//        Arrays.stream(result.split("\n")).forEach(a -> {
//            String d = a.split(" ")[1];
//            String ipStrs = executeLinuxCmd("dig " + d + " +short");
//            Arrays.stream(ipStrs.split("\n")).forEach(ipStr -> {
//                DTO dto = new DTO();
//                dto.setDomain(domain);
//                dto.setMxAddress(d);
//                dto.setMxIp(ipStr);
//                items.add(dto);
//            });
//        });
//        return items;
//    }
//
//    public String executeLinuxCmd(String cmd) {
//        Runtime run = Runtime.getRuntime();
//        try {
//            Process process = run.exec(cmd);
//            String line;
//            BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
//            StringBuffer out = new StringBuffer();
//            while ((line = stdoutReader.readLine()) != null) {
//                out.append(line).append("\n");
//            }
//            try {
//                process.waitFor();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            process.destroy();
//            return out.toString();
//        } catch (IOException e) {
//            log.info("cmd:{}", cmd, e);
//        }
//        return null;
//    }
}
