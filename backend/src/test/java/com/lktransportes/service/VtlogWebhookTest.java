package com.lktransportes.service;
import org.junit.jupiter.api.Test;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import static org.assertj.core.api.Assertions.*;
class VtlogWebhookTest {
    @Test void validaBytesOriginaisERecusaAlteracao() throws Exception {
        byte[] body="{\"drivers\":[]}".getBytes(StandardCharsets.UTF_8);
        Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec("test-only".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        String assinatura=HexFormat.of().formatHex(mac.doFinal(body));
        var webhook=new VtlogWebhook("test-only");
        assertThatCode(()->webhook.validar(body,"sha256="+assinatura)).doesNotThrowAnyException();
        assertThatThrownBy(()->webhook.validar("{}".getBytes(StandardCharsets.UTF_8),assinatura)).hasMessageContaining("401");
        assertThatThrownBy(()->webhook.validar(body,null)).hasMessageContaining("401");
        assertThatThrownBy(()->new VtlogWebhook("").validar(body,assinatura)).hasMessageContaining("503");
    }
}
