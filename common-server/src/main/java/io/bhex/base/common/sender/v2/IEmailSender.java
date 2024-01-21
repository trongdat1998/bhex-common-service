package io.bhex.base.common.sender.v2;

import io.bhex.base.common.SimpleMailRequest;
import io.bhex.base.common.util.SenderDTO;

import java.io.IOException;

public interface IEmailSender {
    boolean send(Long orgId, SenderDTO senderDTO, SimpleMailRequest request) throws IOException;
}
