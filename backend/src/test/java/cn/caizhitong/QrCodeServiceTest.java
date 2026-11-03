package cn.caizhitong;
import cn.caizhitong.service.QrCodeService;
import cn.caizhitong.service.BusinessException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class QrCodeServiceTest {
 @Test void unconfiguredOriginDoesNotBreakBusinessReadsButCannotGeneratePng() {
  var service=new QrCodeService("");
  assertThat(service.content("BAT-0000000001")).isNull();
  assertThatThrownBy(()->service.png("BAT-0000000001")).isInstanceOf(BusinessException.class).hasMessageContaining("PUBLIC_WEB_URL");
 }
 @Test void productionOriginCannotContainCredentialsOrQuery() {
  assertThatThrownBy(()->new QrCodeService("https://user:secret@example.com")).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->new QrCodeService("https://example.com/?token=secret")).isInstanceOf(IllegalArgumentException.class);
  assertThat(new QrCodeService("https://materials.example.com/").content("BAT-0000000001")).isEqualTo("https://materials.example.com/stock/batches/BAT-0000000001");
 }
}
