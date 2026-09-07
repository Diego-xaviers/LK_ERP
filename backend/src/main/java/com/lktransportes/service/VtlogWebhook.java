package com.lktransportes.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class VtlogWebhook {
    private final String segredo;
    public VtlogWebhook(@Value("${lk.vtlog-webhook-secret:}") String segredo) { this.segredo = segredo; }
    public void validar(byte[] corpo, String assinatura) {
        if (segredo == null || segredo.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Assinatura do webhook não configurada.");
        if (corpo.length > 2_000_000) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
        try {
            String hex = assinatura == null ? "" : assinatura.replaceFirst("^sha256=", "");
            if (!hex.matches("[a-fA-F0-9]{64}")) throw new IllegalArgumentException();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            if (!MessageDigest.isEqual(mac.doFinal(corpo), HexFormat.of().parseHex(hex))) throw new IllegalArgumentException();
        } catch (Exception e) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Assinatura inválida."); }
    }
}
