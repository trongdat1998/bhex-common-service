package io.bhex.base.common.sender.v2;

import io.bhex.base.common.SimpleSMSRequest;
import io.bhex.base.common.util.SenderDTO;

import java.io.IOException;

public interface ISmsSender {
    boolean send(Long orgId, SenderDTO senderDTO, SimpleSMSRequest request) throws IOException;
}
